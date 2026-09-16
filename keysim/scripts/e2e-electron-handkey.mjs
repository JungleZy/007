#!/usr/bin/env node
/**
 * keysim × Electron 手键拍发端到端自动化。
 *
 * 全链路：keysim 控制台(18700) 虚拟串口注入 → Electron(源码 dev 模式) 学员页
 *         → WebSerial 生产解码路径 → 后端(18001) uploadResult/finish 结算 → 成绩断言。
 *
 * 前置（脚本只检查，不代起）：
 *   1. MySQL + 后端：cd backend && ./mvnw quarkus:dev          （18001）
 *   2. keysim 控制台：keysim serve                             （18700）
 *   3. vite dev：cd bw-frontend/frontend && npm run dev -- --host 127.0.0.1 （18000）
 *   4. 测试账号已入库（见文末 SEED_SQL 或 docs 中的回放记录）。
 *
 * 用法：
 *   node keysim/scripts/e2e-electron-handkey.mjs [--rate 120] [--groups 100] [--alphabet short|letter]
 *   环境变量：KEYSIM_ELECTRON=…/electron 可执行文件路径
 *            KEYSIM_CDP_PORT=33422  KEYSIM_KEEP_APP=1（结束后保留窗口）
 *
 * 回归基线（2026-09-16 修复后必须保持通过）：
 *   - 字码报(letter)：H→I / I·I·I / F→M 曾触发控制符碰撞误判（翻页/改错），
 *     修复=多截图案每截必须独立成组 + 删除两截变体（handKeyTrain.js patterns + groupEnd 门控）。
 *   - 数码报(short)@120 字/分：曾因 patStandard.js 的 60ms 夹值在第一次成组后全部粘连，
 *     修复=删除该夹值（与 postJob 域同名文件对齐）。
 */
import { spawn } from 'node:child_process'
import { setTimeout as delay } from 'node:timers/promises'
import path from 'node:path'

const args = Object.fromEntries(process.argv.slice(2).map((v, i, a) => v.startsWith('--') ? [v.slice(2), a[i + 1] && !a[i + 1].startsWith('--') ? a[i + 1] : true] : null).filter(Boolean))

const RATE = Number(args.rate || 120)
const GROUPS = Number(args.groups || 100)          // 每页组数
const PAGES = Number(args.pages || 1)              // 页数；>1 时逐页拍发并验证翻页符路径
const ALPHABET = args.alphabet || 'short'          // short=数码报 type 0；letter=字码报 type 1
const TRAIN_TYPE = ALPHABET === 'letter' ? 1 : 0
const CDP_PORT = Number(process.env.KEYSIM_CDP_PORT || 33422)
const BACKEND = 'http://localhost:18001'
const KEYSIM = 'http://127.0.0.1:18700'
const VITE = 'http://localhost:18000'
const TEACHER = { account: 'keysimteacher', password: 'keysim123', id: 'keysim-teacher-001', deviceId: 'keysim-teacher-dev' }
const STUDENT = { account: 'keysimstudent', password: 'keysim123', id: 'keysim-student-001', deviceId: 'keysim-student-dev' }
const RULE_ID = '6c407aee-92b4-4c41-842f-22da80c48053' // 手键评分规则（skew 51, wpm base 70）

const fail = (msg) => { console.error(`✗ ${msg}`); process.exit(1) }
const ok = (msg) => console.log(`✓ ${msg}`)
const step = (msg) => console.log(`→ ${msg}`)

async function rest(path, { method = 'GET', token, deviceId, body } = {}) {
  const res = await fetch(`${BACKEND}/api${path}`, {
    method,
    headers: { 'content-type': 'application/json', ...(token ? { token, deviceId } : {}) },
    body: body ? JSON.stringify(body) : undefined
  })
  const envelope = await res.json()
  if (envelope.code !== 200) throw new Error(`${method} ${path} → code ${envelope.code}: ${envelope.message}`)
  return envelope.data
}

async function keysim(path, body) {
  const res = await fetch(`${KEYSIM}${path}`, {
    method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(body || {})
  })
  return res.json()
}

async function preflight() {
  try { await fetch(`${BACKEND}/api/`) } catch { fail(`后端不可达（${BACKEND}），先起 ./mvnw quarkus:dev`) }
  const ks = await keysim('/api/state').catch(() => null)
  if (!ks?.ok) fail(`keysim 控制台不可达（${KEYSIM}），先起 keysim serve`)
  try { const r = await fetch(VITE); if (!r.ok) throw 0 } catch { fail(`vite dev 不可达（${VITE}）`) }
  ok('后端 / keysim / vite 均可达')
}

async function login({ account, password, deviceId }) {
  const data = await rest('/user/login', { method: 'POST', body: { userAccount: account, password, deviceId } })
  return data.token
}

async function createTrain(token) {
  const data = await rest('/generalTickerPatTrain/add', {
    method: 'POST', token, deviceId: TEACHER.deviceId,
    body: {
      name: `keysim自动化-${ALPHABET}-${Date.now()}`, isCable: 0, type: TRAIN_TYPE, trainType: 0,
      codeSort: false, isRandom: true, messageNumber: GROUPS * PAGES, ruleId: RULE_ID, isAverage: false,
      userId: [STUDENT.id]
    }
  })
  ok(`训练已创建 id=${data.id}（${ALPHABET === 'short' ? '数码报' : '字码报'} ${GROUPS} 组 × ${PAGES} 页）`)
  return data.id
}

async function pageText(token, trainId, floorNumber = 1) {
  const data = await rest('/generalTickerPatTrain/findPage', {
    method: 'POST', token, deviceId: TEACHER.deviceId,
    body: { id: trainId, userId: STUDENT.id, floorNumber }
  })
  return data.messageKey.map(g => JSON.parse(g.moresKey).join('')).join(' ')
}

/** 极简 CDP 客户端（Node 内建 WebSocket） */
class Cdp {
  static async connect(port, urlMatch) {
    for (let i = 0; i < 60; i++) {
      try {
        const targets = await (await fetch(`http://127.0.0.1:${port}/json`)).json()
        const page = targets.find(t => t.type === 'page' && t.url.includes(urlMatch))
        if (page) {
          const ws = new WebSocket(page.webSocketDebuggerUrl)
          await new Promise((res, rej) => { ws.onopen = res; ws.onerror = rej })
          return new Cdp(ws)
        }
      } catch { /* 未就绪 */ }
      await delay(1000)
    }
    fail(`CDP 目标 ${urlMatch} 未出现（端口 ${port}）`)
  }
  constructor(ws) { this.ws = ws; this.id = 0; this.pending = new Map(); ws.onmessage = e => {
    const msg = JSON.parse(e.data)
    if (msg.id && this.pending.has(msg.id)) { const { res, rej } = this.pending.get(msg.id); this.pending.delete(msg.id); msg.error ? rej(new Error(msg.error.message)) : res(msg.result) }
  } }
  call(method, params = {}) {
    const id = ++this.id
    return new Promise((res, rej) => { this.pending.set(id, { res, rej }); this.ws.send(JSON.stringify({ id, method, params })) })
  }
  async eval(expression) {
    const r = await this.call('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true })
    if (r.exceptionDetails) throw new Error(`页面内执行失败: ${JSON.stringify(r.exceptionDetails.exception?.description || r.exceptionDetails.text).slice(0, 300)}`)
    return r.result.value
  }
}

async function launchElectron() {
  // --packaged：测打包产物（out/linux-unpacked，file:// 加载）；默认测源码 dev 模式（vite:18000）
  const packaged = !!args.packaged
  const bin = process.env.KEYSIM_ELECTRON || (packaged
    ? path.resolve('bw-frontend/out/linux-unpacked/nip-traffic-system')
    : path.resolve('bw-frontend/node_modules/.bin/electron'))
  const argv = packaged
    ? ['--no-sandbox', `--remote-debugging-port=${CDP_PORT}`]
    : [path.resolve('bw-frontend'), '--no-sandbox', `--remote-debugging-port=${CDP_PORT}`]
  const child = spawn(bin, argv, { stdio: 'ignore', detached: true })
  child.unref()
  process.on('exit', () => { if (!process.env.KEYSIM_KEEP_APP) try { process.kill(-child.pid) } catch {} })
  ok(`Electron 已拉起（${packaged ? '打包产物' : 'dev'}，pid ${child.pid}，CDP :${CDP_PORT}）`)
}

async function main() {
  await preflight()

  // 1) 教员侧：登录、建训、取报文
  const teacherToken = await login(TEACHER)
  const trainId = await createTrain(teacherToken)
  const pageTexts = []
  for (let p = 1; p <= PAGES; p++) pageTexts.push(await pageText(teacherToken, trainId, p))

  // 2) keysim 侧：可行性预览
  // 逐页计划：非末页用翻页符收尾（翻页后新页要重新拍开始符，keysim 每页都带开始符）
  const pagePlans = pageTexts.map((text, i) => ({
    key: 'hand', text, alphabet: ALPHABET, rate: RATE, skew: 51, style: 'machine',
    tail: i + 1 < PAGES ? 'turn' : 'end', sink: 'frames'
  }))
  const plan = pagePlans[0]
  const preview = await keysim('/api/preview', plan)
  for (const p of pagePlans.slice(1)) {
    const pv = await keysim('/api/preview', p)
    if (!pv.ok) fail(`keysim 预览拒绝（第 ${pagePlans.indexOf(p) + 1} 页）：${pv.error}`)
    preview.duration += pv.duration
  }
  if (!preview.ok) fail(`keysim 预览拒绝：${preview.error}`)
  ok(`拍发计划：${preview.chars} 字 / ${preview.rate} ${preview.rateUnit} / 约 ${Math.round(preview.duration / 1000)}s`)

  // 3) Electron 侧：拉起、注入虚拟串口、学员登录
  await launchElectron()
  const cdp = await Cdp.connect(CDP_PORT, args.packaged ? 'index.html' : 'localhost:18000')
  // 裸 CDP 下 Page.addScriptToEvaluateOnNewDocument 需先 Page.enable，否则注册被静默忽略（实测 Electron 124 踩中）
  await cdp.call('Page.enable')

  const inject = await (await fetch(`${KEYSIM}/inject.js`)).text()
  await cdp.call('Page.addScriptToEvaluateOnNewDocument', { source: inject })
  // 串口链路探针：记录 getPorts/open 调用与 console 错误，失败诊断全靠它
  await cdp.call('Page.addScriptToEvaluateOnNewDocument', { source: `(() => {
    window.__serialTrace = []
    const trace = (...a) => window.__serialTrace.push([Date.now() % 100000, ...a.map(String).map(s => s.slice(0, 120))])
    for (const m of ['error', 'warn']) { const o = console[m]; console[m] = (...a) => { trace(m, ...a); o.apply(console, a) } }
    const timer = setInterval(() => {
      if (!window.__keysimSerial) return
      clearInterval(timer)
      const oGet = navigator.serial.getPorts.bind(navigator.serial)
      navigator.serial.getPorts = async () => { trace('getPorts'); return oGet() }
      const oOpen = window.__keysimSerial.port.open
      window.__keysimSerial.port.open = async (...a) => { trace('port.open'); return oOpen(...a) }
    }, 50)
  })()` })
  ok('虚拟串口注入已注册（document-start 级）')
  // 先显式回登录页：路由守卫会清掉旧会话，确保登录表单出现
  await cdp.eval(`location.hash = '#/login'; true`)
  await cdp.eval(`new Promise((res, rej) => { const t = setInterval(() => { if (document.querySelector('.loginBtn')) { clearInterval(t); res(true) } }, 300); setTimeout(() => rej(new Error('登录页未出现')), 15000) })`)


  await cdp.eval(`(() => {
    const setVal = (el, v) => { Object.getOwnPropertyDescriptor(el.constructor.prototype, 'value').set.call(el, v); el.dispatchEvent(new Event('input', { bubbles: true })); el.dispatchEvent(new Event('change', { bubbles: true })) }
    const inputs = [...document.querySelectorAll('input')]
    setVal(inputs.find(i => i.placeholder && i.placeholder.includes('用户名')), '${STUDENT.account}')
    setVal(inputs.find(i => i.type === 'password'), '${STUDENT.password}')
    document.querySelector('.loginBtn').click()
    return true
  })()`)
  await cdp.eval(`new Promise((res, rej) => { const t = setInterval(() => { if (location.hash.includes('/preview')) { clearInterval(t); res(true) } }, 300); setTimeout(() => rej(new Error('登录跳转超时')), 15000) })`)
  ok('学员已登录')

  // 4) 预置串口选择并刷新：NipSerial.onMounted → linkPort → messageWebSocket('reset') 自动连接
  await cdp.eval(`localStorage.setItem('serial', 'KEYSIM-VIRTUAL'); location.reload(); true`)
  await delay(6000)

  // 5) 进学员训练页（同一路由 query 变化不重挂载，先回列表再进）
  await cdp.eval(`location.hash = '#/preview/networkUsing/equipmentNetwork/handkeyZuXunList'; true`)
  await delay(2500)
  await cdp.eval(`location.hash = '#/preview/networkUsing/equipmentNetwork/handkeyZuXunTrain?id=${trainId}'; true`)
  await cdp.eval(`new Promise((res, rej) => { const t = setInterval(() => { if ([...document.querySelectorAll('.roadBtn')].some(b => b.innerText.includes('准备拍发'))) { clearInterval(t); res(true) } }, 300); setTimeout(() => rej(new Error('训练页就绪超时')), 15000) })`)
  let serial = await cdp.eval(`(() => { const p = document.querySelector('#app').__vue_app__.config.globalProperties.$pinia; let o = {}; p._s.forEach((s, id) => { if (id === 'traffic') o = { link: s.linkStatus, dev: s.devStatus } }); return o })()`)
  if (!serial.dev) {
    // vite 冷编译等时序问题：重进一次训练页（NipSerial 重新挂载触发自动连接），仍不通则带诊断失败
    await delay(3000)
    await cdp.eval(`location.hash = '#/preview/networkUsing/equipmentNetwork/handkeyZuXunList'; true`)
    await delay(1500)
    await cdp.eval(`location.hash = '#/preview/networkUsing/equipmentNetwork/handkeyZuXunTrain?id=${trainId}'; true`)
    await cdp.eval(`new Promise(res => { const t = setInterval(() => { if ([...document.querySelectorAll('.roadBtn')].some(b => b.innerText.includes('准备拍发'))) { clearInterval(t); res(true) } }, 300); setTimeout(() => { clearInterval(t); res(false) }, 15000) })`)
    await delay(2000)
    serial = await cdp.eval(`(() => { const p = document.querySelector('#app').__vue_app__.config.globalProperties.$pinia; let o = {}; p._s.forEach((s, id) => { if (id === 'traffic') o = { link: s.linkStatus, dev: s.devStatus } }); return o })()`)
    if (!serial.dev) {
      const trace = await cdp.eval(`(window.__serialTrace || []).slice(-20)`)
      console.error('  串口链路追踪：', JSON.stringify(trace))
      fail(`串口未连接（traffic.devStatus=false，重试后仍不通）`)
    }
  }
  ok('训练页就绪，虚拟串口在线')
  // 串口自愈重连 + 通帧探针。dev 模式走模块 import（生产同源入口 messageWebSocket，
  // 与 NipSerial「重连」同路径）；打包态模块已打包不可按路径 import，退回
  // 列表↔训练页往返强制 NipSerial 重挂载（其 onMounted 自动重连）。
  const probeOk = await cdp.eval(`(async () => {
    try {
      const { PubSub } = await import('/src/common/utils/PubSub.js')
      let frames = 0
      PubSub.subscribe('traffic:frame', () => frames++)
      const mw = (await import('/src/common/ws/MessageWebSocket.js')).default
      mw('reset')
      await new Promise(r => setTimeout(r, 1500))
      window.__keysimSerial.bytes([1, 0])
      await new Promise(r => setTimeout(r, 200))
      window.__keysimSerial.bytes([2, 0, 0])
      await new Promise(r => setTimeout(r, 800))
      return frames >= 2 ? true : 'no-frames'
    } catch (e) { return 'no-modules' }
  })()`)
  if (probeOk !== true) {
    await cdp.eval(`location.hash = '#/preview/networkUsing/equipmentNetwork/handkeyZuXunList'; true`)
    await delay(1500)
    await cdp.eval(`location.hash = '#/preview/networkUsing/equipmentNetwork/handkeyZuXunTrain?id=${trainId}'; true`)
    await delay(4000)
    const st = await cdp.eval(`(() => { const p = document.querySelector('#app').__vue_app__.config.globalProperties.$pinia; let o = {}; p._s.forEach((s, id) => { if (id === 'traffic') o = { dev: s.devStatus } }); return o })()`)
    if (!st.dev) fail(`串口重连失败（${probeOk}，导航重连后 devStatus=false）`)
  }
  ok('串口通帧验证通过')


  // 6) 学员准备 → 教员开始 → 房间广播 begin
  await cdp.eval(`[...document.querySelectorAll('.roadBtn')].find(b => b.innerText.includes('准备拍发')).click(); true`)
  await delay(1000)
  await rest('/socket/generalTickerPatTrain/updateTrainStatus', { method: 'POST', token: teacherToken, deviceId: TEACHER.deviceId, body: { trainId, status: 1 } })
  const room = new WebSocket(`ws://localhost:18001/generalTickerPat/${TEACHER.id}/${trainId}/1?token=${encodeURIComponent(teacherToken)}&deviceId=${TEACHER.deviceId}`)
  await new Promise((res, rej) => { room.onopen = res; room.onerror = () => rej(new Error('教员房间 WS 连接失败')) })
  await delay(800) // 等学员 WS 入房（学员进页时 connectWebsocket）
  // 房间 WS 的 begin 与学员入房存在竞态：学员端未进「拍发开始符号」阶段就重发
  for (let attempt = 0; attempt < 3; attempt++) {
    room.send(JSON.stringify({ topic: 'begin' }))
    const begun = await cdp.eval(`new Promise(res => { const t = setInterval(() => { if (document.body.innerText.includes('拍发开始符号')) { clearInterval(t); res(true) } }, 300); setTimeout(() => { clearInterval(t); res(false) }, 4000) })`)
    if (begun) break
    if (attempt === 2) throw new Error('未进入开始符阶段（begin 广播 3 次均未生效）')
  }
  ok('训练已开始，等待拍发开始符')

  // 7) keysim 逐页拍发（每页：开始符 + 正文 + 翻页/结束符；翻页后新页要重拍开始符，keysim 每页自带）
  const sendPage = async (i) => {
    const send = await keysim('/api/send', pagePlans[i])
    if (!send.ok) fail(`keysim 第 ${i + 1} 页拍发失败：${send.error}`)
    ok(`keysim 拍发中…（第 ${i + 1}/${PAGES} 页）`)
  }
  await sendPage(0)
  // 翻页：上一页翻页符触发 turn，前端取下一页并把「当前页」翻到 i+1
  for (let i = 1; i < PAGES; i++) {
    const flipped = await cdp.eval(`new Promise(res => { const t = setInterval(() => { const n = document.querySelector('.pag .curr .num'); if (n && n.innerText.trim() === '${i + 1}') { clearInterval(t); res(true) } }, 500); setTimeout(() => { clearInterval(t); res(false) }, ${Math.round(preview.duration / PAGES) + 30000}) })`)
    if (!flipped) fail(`第 ${i} 页拍完后未翻到第 ${i + 1} 页（翻页符未被识别）`)
    ok(`已翻到第 ${i + 1} 页`)
    await sendPage(i)
  }

  // 8) 等学员端自动结算（结束符 → autoEnd → uploadResult → finish → 成绩页）
  // 轮询页面进度：拍发记录数应持续增长；停滞超 90s 带现场诊断失败
  const waitMs = preview.duration + 90000
  const probe = `(() => { const t = document.body.innerText; return {
    done: location.hash.includes('status=2') || t.includes('正确率'),
    keys: document.querySelectorAll('.patKey .keys .key').length,
    patLog: document.querySelectorAll('._top .vals .val').length,
    startSymbolPhase: t.includes('拍发开始符号'),
    submissionError: (document.querySelector('.ant-alert-error .ant-alert-message') || {}).innerText || null,
    underway: t.includes('本轮正在进行') } })()`
  const t0 = Date.now()
  let lastKeys = -1, lastMove = Date.now()
  let evalErrors = 0
  for (;;) {
    let p
    try {
      p = await cdp.eval(probe)
      evalErrors = 0
    } catch (e) {
      // 页面跳转成绩页的瞬间 eval 会撞上上下文销毁；连续失败才是真故障
      if (++evalErrors >= 5) fail(`页面探针连续失败：${e.message}`)
      await delay(2000)
      continue
    }
    console.log(`  [poll] keys=${p.keys} patLog=${p.patLog} start=${p.startSymbolPhase} underway=${p.underway}`)
    if (p.done) break
    // patLog 展示窗封顶 70 会饱和，keys（已译字码行）持续增长，用 keys 判活
    if (p.keys !== lastKeys) { lastKeys = p.keys; lastMove = Date.now() }
    if (p.submissionError) fail(`页面提交失败（应重试/上报）：${p.submissionError}`)
    if (Date.now() - lastMove > 90000) {
      const ks = await keysim('/api/state')
      fail(`拍发停滞 90s：keys=${p.keys} patLog=${p.patLog} startSymbolPhase=${p.startSymbolPhase} keysimReplay=${JSON.stringify(ks.state.replay ?? null)} injectClients=${ks.state.injectClients}`)
    }
    if (Date.now() - t0 > waitMs) fail(`结算超时（keys=${p.keys} underway=${p.underway}）`)
    await delay(5000)
  }

  // 9) 后端断言：服务器权威成绩
  const detail = await rest('/generalTickerPatTrain/detail', { method: 'POST', token: teacherToken, deviceId: TEACHER.deviceId, body: { id: trainId, uid: STUDENT.id } })
  const me = detail.userInfoList.find(u => u.userId === STUDENT.id)
  if (!me || me.isFinish !== 1) fail(`后端未结算：${JSON.stringify(me)}`)
  console.log(`  成绩：score=${me.score} accuracy=${me.accuracy}% speed=${me.speed}字/分 errors=${me.errorNumber} lack=${me.lack} validTime=${me.validTime}s`)
  if (ALPHABET === 'short' && me.accuracy !== '100.00') fail(`数码报应满分正确率，实际 ${me.accuracy}`)
  ok(`端到端通过：训练 ${trainId} 结算完成`)

  room.close()
  if (!process.env.KEYSIM_KEEP_APP) process.exit(0)
}

main().catch(e => fail(e.message))

/* SEED_SQL（一次性准备测试账号，口令均为 keysim123）：
-- pbkdf2 哈希可用 keysim/scripts 或任意 PBKDF2-HMAC-SHA256(600000) 工具现算
INSERT INTO t_user (id,user_account,password,user_name,user_sex,status,bday,eday) VALUES
 ('keysim-teacher-001','keysimteacher','<pbkdf2>','键测教员',0,0,'2020-01-01','2099-12-31'),
 ('keysim-student-001','keysimstudent','<pbkdf2>','键测学员',0,0,'2020-01-01','2099-12-31');
INSERT INTO t_user_role (id,user_id,role_id) VALUES ('ksur-001','keysim-teacher-001','2'),('ksur-002','keysim-student-001','2');
*/
