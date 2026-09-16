#!/usr/bin/env node
/**
 * keysim × Electron 手键拍发端到端自动化。
 *
 * 全链路：keysim 控制台(18700) 虚拟串口注入 → Electron 学员页
 *         → WebSerial 生产解码路径 → 后端(18001) uploadResult/finish 结算 → 成绩断言。
 *
 * 前置（脚本只检查，不代起）：
 *   1. MySQL + 后端：cd backend && ./mvnw quarkus:dev          （18001）
 *   2. keysim 控制台：keysim serve                             （18700）
 *   3. vite dev：cd bw-frontend/frontend && npm run dev -- --host 127.0.0.1 （18000）
 *      （--packaged 时不需要 vite）
 *   4. 测试账号已入库（见文末 SEED_SQL）。
 *
 * 用法：
 *   单次：node keysim/scripts/e2e-electron-handkey.mjs [--rate 120] [--groups 100]
 *         [--pages 1] [--alphabet short|letter] [--packaged] [--fault dupDown,...]
 *   矩阵：node keysim/scripts/e2e-electron-handkey.mjs --matrix [speed|fault|all]
 *         [--only speed-120,fault-combo] [--packaged]
 *   环境变量：KEYSIM_ELECTRON=…/electron 可执行文件路径
 *            KEYSIM_CDP_PORT=33422  KEYSIM_KEEP_APP=1（结束后保留窗口）
 *
 * 矩阵的预期口径（keysim faults.rs 与前端一一对应的故障语义）：
 *   - 速度扫描：可行速域内全部满分正确率（60/90/120/180/240 字/分）。
 *   - dupDown    → 容忍：重复按下落在 max(2000, lineLimit*8) 窗口内被忽略（useTraffic.js:44）。
 *   - microPress → 容忍：≤10ms 按压被直接丢弃（useTraffic.js:64）。
 *   - unknownByte→ 容忍：未知字节告警跳过、帧流不失步（WebSerial.js:86-89）。
 *   - missingUp  → 劣化：缺抬起的按压与下一元素合并成划 → 错码扣分，但训练必须完成。
 */
import { spawn } from 'node:child_process'
import { setTimeout as delay } from 'node:timers/promises'
import path from 'node:path'

const args = Object.fromEntries(process.argv.slice(2).map((v, i, a) => v.startsWith('--') ? [v.slice(2), a[i + 1] && !a[i + 1].startsWith('--') ? a[i + 1] : true] : null).filter(Boolean))

const RATE = Number(args.rate || 120)
const GROUPS = Number(args.groups || 100)          // 每页组数
const PAGES = Number(args.pages || 1)              // 页数；>1 时逐页拍发并验证翻页符路径
const ALPHABET = args.alphabet || 'short'          // short=数码报 type 0；letter=字码报 type 1
const CDP_PORT = Number(process.env.KEYSIM_CDP_PORT || 33422)
const BACKEND = 'http://localhost:18001'
const KEYSIM = 'http://127.0.0.1:18700'
const VITE = 'http://localhost:18000'
const TEACHER = { account: 'keysimteacher', password: 'keysim123', id: 'keysim-teacher-001', deviceId: 'keysim-teacher-dev' }
const STUDENT = { account: 'keysimstudent', password: 'keysim123', id: 'keysim-student-001', deviceId: 'keysim-student-dev' }
const RULE_ID = '6c407aee-92b4-4c41-842f-22da80c48053' // 手键评分规则（skew 51, wpm base 70）

// 矩阵场景：kind=speed 速度扫描；kind=fault 故障组合。expect=perfect|degraded
const SCENARIOS = [
  { name: 'speed-60',  kind: 'speed', rate: 60,  alphabet: 'short', expect: 'perfect' },
  { name: 'speed-90',  kind: 'speed', rate: 90,  alphabet: 'short', expect: 'perfect' },
  { name: 'speed-120', kind: 'speed', rate: 120, alphabet: 'short', expect: 'perfect' },
  { name: 'speed-180', kind: 'speed', rate: 180, alphabet: 'short', expect: 'perfect' },
  { name: 'speed-240', kind: 'speed', rate: 240, alphabet: 'short', expect: 'perfect' },
  { name: 'fault-dupDown',     kind: 'fault', rate: 120, alphabet: 'short', fault: 'dupDown',     expect: 'perfect'  },
  { name: 'fault-microPress',  kind: 'fault', rate: 120, alphabet: 'short', fault: 'microPress',  expect: 'perfect'  },
  { name: 'fault-unknownByte', kind: 'fault', rate: 120, alphabet: 'short', fault: 'unknownByte', expect: 'perfect'  },
  { name: 'fault-missingUp',   kind: 'fault', rate: 120, alphabet: 'short', fault: 'missingUp',   expect: 'degraded' },
  { name: 'fault-combo',       kind: 'fault', rate: 120, alphabet: 'short', fault: 'dupDown,missingUp,microPress,unknownByte', expect: 'degraded' },
]

const fail = (msg) => { console.error(`✗ ${msg}`); process.exit(1) }
const ok = (msg) => console.log(`✓ ${msg}`)

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
  if (!args.packaged) {
    try { const r = await fetch(VITE); if (!r.ok) throw 0 } catch { fail(`vite dev 不可达（${VITE}）`) }
  }
  ok('后端 / keysim / vite 均可达')
}

async function login({ account, password, deviceId }) {
  const data = await rest('/user/login', { method: 'POST', body: { userAccount: account, password, deviceId } })
  return data.token
}

async function createTrain(token, { alphabet, groups, pages }) {
  const data = await rest('/generalTickerPatTrain/add', {
    method: 'POST', token, deviceId: TEACHER.deviceId,
    body: {
      name: `keysim自动化-${alphabet}-${Date.now()}`, isCable: 0, type: alphabet === 'letter' ? 1 : 0, trainType: 0,
      codeSort: false, isRandom: true, messageNumber: groups * pages, ruleId: RULE_ID, isAverage: false,
      userId: [STUDENT.id]
    }
  })
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

/** 一次性环境：拉起 Electron、注入虚拟串口、学员登录、预置串口选择 */
async function setupOnce() {
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

  // 预置串口选择并刷新：NipSerial.onMounted → linkPort → messageWebSocket('reset') 自动连接
  await cdp.eval(`localStorage.setItem('serial', 'KEYSIM-VIRTUAL'); location.reload(); true`)
  await delay(6000)
  return cdp
}

const TRAIN_PATH = '#/preview/networkUsing/equipmentNetwork/handkeyZuXunTrain'
const LIST_PATH = '#/preview/networkUsing/equipmentNetwork/handkeyZuXunList'
const storeOnline = `(() => { const p = document.querySelector('#app').__vue_app__.config.globalProperties.$pinia; let o = {}; p._s.forEach((s, id) => { if (id === 'traffic') o = { link: s.linkStatus, dev: s.devStatus } }); return o })()`
const readyBtnWait = `new Promise(res => { const t = setInterval(() => { if ([...document.querySelectorAll('.roadBtn')].some(b => b.innerText.includes('准备拍发'))) { clearInterval(t); res(true) } }, 300); setTimeout(() => { clearInterval(t); res(false) }, 20000) })`

/** 进学员训练页（同一路由 query 变化不重挂载，先回列表再进），并确保串口在线 */
async function enterTrainPage(cdp, trainId) {
  await cdp.eval(`location.hash = '${LIST_PATH}'; true`)
  await delay(1800)
  await cdp.eval(`location.hash = '${TRAIN_PATH}?id=${trainId}'; true`)
  await cdp.eval(readyBtnWait)
  let serial = await cdp.eval(storeOnline)
  if (!serial.dev) {
    // vite 冷编译等时序问题：重进一次训练页（NipSerial 重新挂载触发自动连接），仍不通则带诊断失败
    await delay(2000)
    await cdp.eval(`location.hash = '${LIST_PATH}'; true`)
    await delay(1500)
    await cdp.eval(`location.hash = '${TRAIN_PATH}?id=${trainId}'; true`)
    await cdp.eval(readyBtnWait)
    await delay(1500)
    serial = await cdp.eval(storeOnline)
    if (!serial.dev) {
      const trace = await cdp.eval(`(window.__serialTrace || []).slice(-20)`)
      console.error('  串口链路追踪：', JSON.stringify(trace))
      throw new Error('串口未连接（traffic.devStatus=false，重试后仍不通）')
    }
  }
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
    await cdp.eval(`location.hash = '${LIST_PATH}'; true`)
    await delay(1500)
    await cdp.eval(`location.hash = '${TRAIN_PATH}?id=${trainId}'; true`)
    await delay(4000)
    const st = await cdp.eval(storeOnline)
    if (!st.dev) throw new Error(`串口重连失败（${probeOk}，导航重连后 devStatus=false）`)
  }
}

/** 跑一个完整训练场景，返回服务端权威成绩 */
async function runScenario(cdp, teacherToken, scenario) {
  const { name, rate, alphabet, fault, pages = 1, groups = GROUPS } = scenario
  // 1) 建训、取报文
  const trainId = await createTrain(teacherToken, { alphabet, groups, pages })
  const pageTexts = []
  for (let p = 1; p <= pages; p++) pageTexts.push(await pageText(teacherToken, trainId, p))

  // 2) 逐页拍发计划（非末页翻页符收尾；翻页后新页要重拍开始符，keysim 每页自带）
  const pagePlans = pageTexts.map((text, i) => ({
    key: 'hand', text, alphabet, rate, skew: 51, style: 'machine',
    tail: i + 1 < pages ? 'turn' : 'end', sink: 'frames',
    ...(fault ? { faults: fault.split(',') } : {})
  }))
  let totalDuration = 0
  const previews = []
  for (let i = 0; i < pagePlans.length; i++) {
    const pv = await keysim('/api/preview', pagePlans[i])
    if (!pv.ok) throw new Error(`keysim 预览拒绝（${name} 第 ${i + 1} 页）：${pv.error}`)
    previews.push(pv)
    totalDuration += pv.duration
  }
  console.log(`→ [${name}] 训练 ${trainId}：${previews[0].chars} 字 × ${pages} 页 / ${rate} 字/分 / 约 ${Math.round(totalDuration / 1000)}s${fault ? ` / 故障 ${fault}` : ''}`)

  // 3) 进训练页、学员准备、教员开始
  await enterTrainPage(cdp, trainId)
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

  // 4) 逐页拍发
  for (let i = 0; i < pagePlans.length; i++) {
    const send = await keysim('/api/send', pagePlans[i])
    if (!send.ok) throw new Error(`keysim 第 ${i + 1} 页拍发失败：${send.error}`)
    if (i + 1 < pagePlans.length) {
      // 翻页：上一页翻页符触发 turn，前端取下一页并把「当前页」翻到 i+2
      const flipped = await cdp.eval(`new Promise(res => { const t = setInterval(() => { const n = document.querySelector('.pag .curr .num'); if (n && n.innerText.trim() === '${i + 2}') { clearInterval(t); res(true) } }, 500); setTimeout(() => { clearInterval(t); res(false) }, ${Math.round(previews[i].duration) + 30000}) })`)
      if (!flipped) throw new Error(`第 ${i + 1} 页拍完后未翻到第 ${i + 2} 页（翻页符未被识别）`)
      console.log(`  [${name}] 已翻到第 ${i + 2} 页`)
    }
  }

  // 5) 等学员端自动结算（结束符 → autoEnd → uploadResult → finish → 成绩页）
  const waitMs = totalDuration + 90000
  const probe = `(() => { const t = document.body.innerText; return {
    done: location.hash.includes('status=2') || t.includes('正确率'),
    keys: document.querySelectorAll('.patKey .keys .key').length,
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
      if (++evalErrors >= 5) throw new Error(`页面探针连续失败：${e.message}`)
      await delay(2000)
      continue
    }
    if (p.done) break
    if (p.keys !== lastKeys) { lastKeys = p.keys; lastMove = Date.now() }
    if (p.submissionError) throw new Error(`页面提交失败（应重试/上报）：${p.submissionError}`)
    if (Date.now() - lastMove > 90000) {
      const ks = await keysim('/api/state')
      throw new Error(`拍发停滞 90s：keys=${p.keys} startSymbolPhase=${p.startSymbolPhase} keysimReplay=${JSON.stringify(ks.state.replay ?? null)} injectClients=${ks.state.injectClients}`)
    }
    if (Date.now() - t0 > waitMs) throw new Error(`结算超时（keys=${p.keys} underway=${p.underway}）`)
    await delay(5000)
  }

  // 6) 后端权威断言
  const detail = await rest('/generalTickerPatTrain/detail', { method: 'POST', token: teacherToken, deviceId: TEACHER.deviceId, body: { id: trainId, uid: STUDENT.id } })
  const me = detail.userInfoList.find(u => u.userId === STUDENT.id)
  room.close()
  if (!me || me.isFinish !== 1) throw new Error(`后端未结算：${JSON.stringify(me)}`)
  return {
    name, trainId,
    score: me.score, accuracy: me.accuracy, speed: me.speed,
    errors: me.errorNumber, lack: me.lack, validTime: me.validTime
  }
}

/** 结果是否满足场景预期 */
function checkExpect(result, scenario) {
  if (scenario.expect === 'perfect') {
    if (result.accuracy !== '100.00' || result.lack !== 0) {
      return `应满分容忍：accuracy=${result.accuracy} lack=${result.lack} errors=${result.errors}`
    }
  } else if (scenario.expect === 'degraded') {
    if (result.accuracy === '100.00' || result.errors === 0) {
      return `应劣化扣分（故障未生效？）：accuracy=${result.accuracy} errors=${result.errors}`
    }
  }
  return null
}

async function main() {
  await preflight()
  const teacherToken = await login(TEACHER)
  const cdp = await setupOnce()

  let scenarios
  if (args.matrix) {
    const kind = args.matrix === true ? 'all' : args.matrix
    scenarios = SCENARIOS.filter(s => kind === 'all' || s.kind === kind)
    if (args.only) {
      const wanted = new Set(String(args.only).split(','))
      scenarios = scenarios.filter(s => wanted.has(s.name))
    }
    if (!scenarios.length) fail('矩阵没有匹配的场景')
  } else {
    scenarios = [{
      name: 'single', rate: RATE, alphabet: ALPHABET, pages: PAGES,
      fault: args.fault || null,
      expect: args.fault ? (String(args.fault).includes('missingUp') ? 'degraded' : 'perfect') : 'perfect'
    }]
  }

  const results = []
  const violations = []
  for (const scenario of scenarios) {
    try {
      const result = await runScenario(cdp, teacherToken, scenario)
      const violation = checkExpect(result, scenario)
      results.push({ ...result, expect: scenario.expect, pass: !violation })
      console.log(`  [${result.name}] score=${result.score} accuracy=${result.accuracy}% speed=${result.speed}字/分 errors=${result.errors} lack=${result.lack} validTime=${result.validTime}s${violation ? `  ✗ ${violation}` : ''}`)
      if (violation) violations.push(`${result.name}: ${violation}`)
    } catch (e) {
      results.push({ name: scenario.name, expect: scenario.expect, pass: false, error: e.message })
      violations.push(`${scenario.name}: ${e.message}`)
      console.error(`  [${scenario.name}] ✗ ${e.message}`)
    }
  }

  if (scenarios.length > 1) {
    console.log('\n场景汇总：')
    for (const r of results) {
      console.log(`  ${r.pass ? '✓' : '✗'} ${r.name.padEnd(20)} ${r.error ? r.error : `score=${r.score} accuracy=${r.accuracy}% speed=${r.speed} errors=${r.errors} lack=${r.lack}`}`)
    }
  }
  if (violations.length) fail(`${violations.length} 个场景未达预期：\n- ${violations.join('\n- ')}`)
  ok(`全部 ${results.length} 个场景通过`)
  if (!process.env.KEYSIM_KEEP_APP) process.exit(0)
}

main().catch(e => fail(e.message))

/* SEED_SQL（一次性准备测试账号，口令均为 keysim123）：
-- pbkdf2 哈希可用任意 PBKDF2-HMAC-SHA256(600000) 工具现算
INSERT INTO t_user (id,user_account,password,user_name,user_sex,status,bday,eday) VALUES
 ('keysim-teacher-001','keysimteacher','<pbkdf2>','键测教员',0,0,'2020-01-01','2099-12-31'),
 ('keysim-student-001','keysimstudent','<pbkdf2>','键测学员',0,0,'2020-01-01','2099-12-31');
INSERT INTO t_user_role (id,user_id,role_id) VALUES ('ksur-001','keysim-teacher-001','2'),('ksur-002','keysim-student-001','2');
*/
