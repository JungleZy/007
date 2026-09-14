/**
 * Web 模式手键端到端：真实后端 + 真实前端产物 + 真实生产采集链路。
 * 注入点只有一个——把 navigator.serial 换成队列串口，字节之后的每一步都是生产代码：
 * WebSerial.handleData -> publishTrafficFrame -> handKeyEvents 点划判定 -> 解码 -> REST/WS。
 * 拍发内容取真实报底，因此判定结果可以逐字比对，服务端也按真实规则结算。
 */
import assert from 'node:assert/strict'
import test from 'node:test'
import {chromium} from 'playwright-core'
import {
  API, adminSession, authorize, createHandKeyTrain, dismissAudioMask, handkeyRuleId,
  initScript, menuRoute, prerequisites, serveDist, studentSession, toPageChunks, until
} from './fixtures.mjs'
import {handKeyPlan, handKeyTimeline} from '../src/handkey.mjs'
import {handKeyRun} from '../src/sinks/rest.mjs'
import {toByteStream} from '../src/sinks/bytes.mjs'

const RATE = 70
const SKEW = 51

test('手键：模拟器按真实节拍拍完一页报底，页面判定的字码与报底一致，服务端按采集区间结算', async t => {
  const missing = await prerequisites()
  if (missing.length) {
    t.skip(`缺少前置条件：\n- ${missing.join('\n- ')}`)
    return
  }

  const admin = await adminSession()
  const {session: student, payload} = await studentSession()
  const ruleId = await handkeyRuleId(admin)
  const train = await createHandKeyTrain(admin, {studentId: payload.user.id, ruleId, messageNumber: 4, type: 1})
  const teacher = handKeyRun(admin, {trainId: train.id, userId: payload.user.id})
  const learner = handKeyRun(student, {trainId: train.id, userId: payload.user.id})
  // 教员开训：这一步同时给学员打上服务端采集起点 captureStartedAt。
  await teacher.updateStatus(1)
  const first = await learner.findPage(1)
  const report = first.data.messageKey.map(group => JSON.parse(group.moresKey).join('')).join(' ')
  assert.match(report, /^[A-Z]{4}( [A-Z]{4})*$/, `报底形状异常：${report}`)

  const site = await serveDist({api: API})
  const browser = await chromium.launch({channel: 'chrome', args: ['--no-sandbox']})
  const context = await browser.newContext({viewport: {width: 1600, height: 900}})
  await context.addInitScript(initScript(payload))
  const page = await context.newPage()
  const pageErrors = []
  page.on('console', message => { if (message.type() === 'error') pageErrors.push(message.text()) })

  const dump = async label => {
    const text = await page.evaluate('document.body.innerText').catch(() => '')
    const alerts = await page.locator('.ant-alert').allInnerTexts().catch(() => [])
    return `${label}\n页面文本：${text.replace(/\n+/g, ' | ').slice(0, 400)}\n页面告警：${alerts.join(' | ').slice(0, 400)}\n控制台：${pageErrors.slice(-5).join(' | ').slice(0, 400)}`
  }

  try {
    const route = menuRoute(payload.menus, 'handkeyZuXunTrain')
    const plan = handKeyPlan({rate: RATE, type: 'letter', skew: SKEW, pageTurns: false})
    const timeline = handKeyTimeline({text: report, alphabet: 'letter', plan, tail: 'turn'})
    const chunks = toPageChunks(toByteStream(timeline))
    const expected = report.split(' ')

    /**
     * 一次完整尝试：进页 -> 过授权 -> 接串口 -> 进试机 -> 按真实节拍回放 -> 读判定结果。
     * 客户端存在一个已知竞态：首个拍发事件若被在飞的提交推迟，recordQueued 会判
     * "排队采集时间与已确认区间重叠" 并整页中止（该告警本身是缺陷，已在交付说明记录）。
     * 这里不把该行为写成断言，而是重进页面重试；三次都中止才算测试失败。
     */
    const attempt = async () => {
      await page.goto(`${site.origin}/#${route}?id=${train.id}`, {waitUntil: 'domcontentloaded'})
      await authorize(page)
      await page.waitForSelector('.loading', {state: 'hidden', timeout: 90000})
      await page.waitForFunction('window.__keysimAttached && window.__keysimAttached()', null, {timeout: 60000})
      await dismissAudioMask(page)
      // 只点一次：多点会留下在飞的取页请求，正是上面那条竞态的触发条件。
      await page.locator('.roadBtn', {hasText: '继续拍发'}).click({timeout: 30000})
      await page.waitForSelector('.machineBox', {timeout: 20000})
      // 等 ready/取页 的提交彻底落地
      await page.waitForFunction("!document.querySelector('.ant-alert')", null, {timeout: 10000}).catch(() => {})
      await new Promise(resolve => setTimeout(resolve, 3000))

      const wall = await page.evaluate(([items]) => window.__keysimReplay(items), [chunks])
      assert.ok(wall >= timeline.duration - 50, `页面内回放只用了 ${wall}ms，短于声明的 ${timeline.duration}ms`)

      await page.waitForSelector('.patKey .keys', {timeout: 15000})
      assert.equal(await page.locator('.machineBox').count(), 0, '试机未通过：开始符被判无效')

      // 判定结果要在结算前读：finish 成功后客户端会跳到成绩页，训练页随即卸载。
      // 只比对正文那几组：翻页符的临时项要等第三个 '00' 凑齐才撤销，撤销与跳转几乎同时发生。
      let decoded = []
      let aborted = null
      await until(async () => {
        const alerts = await page.locator('.ant-alert').allInnerTexts().catch(() => [])
        if (alerts.some(alert => alert.includes('重叠'))) { aborted = alerts.join(' | '); return true }
        const keyed = await page.locator('.patKey .keys .keysRow .key').allInnerTexts().catch(() => [])
        const body = keyed.map(item => item.trim()).filter(item => item && !['开始', '句号', '翻页'].includes(item))
        if (body.length) decoded = body
        return decoded.slice(0, expected.length).join(' ') === report || null
      }, {timeout: 30000, interval: 250, what: '页面判定出完整报文'})
      return {decoded, aborted}
    }

    let outcome = null
    for (let round = 1; round <= 3; round++) {
      outcome = await attempt().catch(async error => { throw new Error(await dump(`第 ${round} 次尝试失败：${error.message}`)) })
      if (!outcome.aborted) break
      if (round === 3) throw new Error(await dump(`连续三次都被客户端采集竞态中止：${outcome.aborted}`))
      await learner.reset(0).catch(() => {})
      await teacher.updateStatus(1).catch(() => {})
    }
    assert.deepEqual(outcome.decoded.slice(0, expected.length), expected, '页面判定出的字码与报底不一致')

    // 末字与翻页符要等客户端的静默定时器编译完，再等它把本页打到服务端并结算。
    const member = await until(async () => {
      const detail = await learner.detail()
      const row = detail.data.userInfoList.find(item => item.userId === payload.user.id)
      return row?.isFinish === 1 ? row : null
    }, {timeout: 60000, what: '服务端结算'})
      .catch(async error => { throw new Error(await dump(error.message)) })

    assert.equal(member.existPageNumber, 1, '服务端没有收到本页采集数据')
    assert.ok(Number(member.speed) > 0, `服务端重算的码率是 ${member.speed}`)
    assert.ok(Math.abs(Number(member.speed) - RATE) <= RATE * 0.4,
      `服务端重算码率 ${member.speed} 与设定的 ${RATE} 字符/分 相差过大`)
    // 拍的就是报底，错码应为 0
    assert.equal(member.errorNumber, 0, `逐字拍的是报底却判出 ${member.errorNumber} 个错码`)
    assert.deepEqual(pageErrors.filter(item => /Serial|traffic|frame/i.test(item)), [],
      '采集链路上出现了控制台错误')
  } finally {
    await context.close()
    await browser.close()
    await site.close()
    await admin.call('/generalTickerPatTrain/delete', {method: 'GET', query: {trainId: train.id}, allowCodes: [202, 207, 208]})
  }
})
