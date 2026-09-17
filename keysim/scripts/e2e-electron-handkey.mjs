#!/usr/bin/env node
/**
 * keysim × Electron 手键拍发端到端自动化（双域）。
 *
 * 域：--domain zuxun（默认，综合组训 generalTickerPatTrain，教员建训+房间 WS）
 *     --domain postJob（岗位训练-发报训练 postTelegramTrain，学员自助开始练习）
 *
 * 全链路：keysim 控制台(18700) 虚拟串口注入 → Electron 学员页
 *         → WebSerial 生产解码路径 → 后端(18001) 结算 → detail 权威断言。
 *
 * 前置（脚本只检查，不代起）：
 *   1. MySQL + 后端：cd backend && ./mvnw quarkus:dev          （18001）
 *   2. keysim 控制台：keysim serve                             （18700）
 *   3. vite dev：cd bw-frontend/frontend && npm run dev -- --host 127.0.0.1 （18000）
 *      （--packaged 时不需要 vite）
 *   4. 测试账号已入库（见文末 SEED_SQL）。
 *
 * 用法：
 *   单次：node keysim/scripts/e2e-electron-handkey.mjs [--domain postJob] [--rate 120]
 *         [--groups 100] [--pages 1] [--alphabet short|letter] [--style machine|human]
 *         [--jitter 0.12] [--fault dupDown,...] [--packaged]
 *   矩阵：node keysim/scripts/e2e-electron-handkey.mjs --matrix [speed|fault|chaos|all]
 *         [--domain postJob] [--only name1,name2] [--packaged]
 *
 * 预期口径：perfect=满分容忍；degraded=劣化扣分但必须完成；low=大幅错码但完成；
 *           stall=训练无法结束（开始符不过/无开始符时产品无退出路径——如实记录）。
 */
import { spawn } from 'node:child_process'
import { setTimeout as delay } from 'node:timers/promises'
import path from 'node:path'

const args = Object.fromEntries(process.argv.slice(2).map((v, i, a) => v.startsWith('--') ? [v.slice(2), a[i + 1] && !a[i + 1].startsWith('--') ? a[i + 1] : true] : null).filter(Boolean))

const DOMAIN = args.domain || 'zuxun'                 // zuxun=综合组训；postJob=岗位训练
const RATE = Number(args.rate || 120)
const GROUPS = Number(args.groups || (DOMAIN === 'postJob' ? 50 : 100))
const PAGES = Number(args.pages || 1)
const ALPHABET = args.alphabet || 'short'
const CDP_PORT = Number(process.env.KEYSIM_CDP_PORT || 33422)
const BACKEND = 'http://localhost:18001'
const KEYSIM = 'http://127.0.0.1:18700'
const VITE = 'http://localhost:18000'
const TEACHER = { account: 'keysimteacher', password: 'keysim123', id: 'keysim-teacher-001', deviceId: 'keysim-teacher-dev' }
const STUDENT = { account: 'keysimstudent', password: 'keysim123', id: 'keysim-student-001', deviceId: 'keysim-student-dev' }
const RULE_ID = '6c407aee-92b4-4c41-842f-22da80c48053' // 手键评分规则（skew 51, wpm base 70）

// 组训矩阵
const SCENARIOS_ZUXUN = [
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

// 岗位训练矩阵：手感 × 速度 × 故障 × 乱拍
const SCENARIOS_POSTJOB = [
  { name: 'base-120',       kind: 'speed', rate: 120, alphabet: 'short', groups: 100, expect: 'perfect' },
  { name: 'style-human-90', kind: 'speed', rate: 90,  alphabet: 'short', style: 'human', expect: 'perfect' },
  { name: 'style-fatigue-120', kind: 'speed', rate: 120, alphabet: 'short', style: 'fatigue', expect: 'perfect' },
  { name: 'style-human-150', kind: 'speed', rate: 150, alphabet: 'short', style: 'human', expect: 'perfect' },
  { name: 'speed-60',       kind: 'speed', rate: 60,  alphabet: 'short', expect: 'perfect' },
  { name: 'speed-180',      kind: 'speed', rate: 180, alphabet: 'short', expect: 'perfect' },
  { name: 'fault-dupDown',     kind: 'fault', rate: 120, alphabet: 'short', fault: 'dupDown',     expect: 'perfect'  },
  { name: 'fault-microPress',  kind: 'fault', rate: 120, alphabet: 'short', fault: 'microPress',  expect: 'perfect'  },
  { name: 'fault-unknownByte', kind: 'fault', rate: 120, alphabet: 'short', fault: 'unknownByte', expect: 'perfect'  },
  { name: 'fault-missingUp',   kind: 'fault', rate: 120, alphabet: 'short', fault: 'missingUp',   expect: 'degraded' },
  { name: 'fault-combo',       kind: 'fault', rate: 120, alphabet: 'short', fault: 'dupDown,missingUp,microPress,unknownByte', expect: 'degraded' },
  // 乱拍：全文拍错（每组首两字符互换，节拍完美）→ 应大幅错码但完成
  { name: 'chaos-wrongText',   kind: 'chaos', rate: 120, alphabet: 'short', corrupt: 'swap', expect: 'low' },
  // 乱拍：不拍开始符直接拍正文 → 开始符校验永远不过，产品应如实停留（无退出路径）
  { name: 'chaos-noStart',     kind: 'chaos', rate: 120, alphabet: 'short', preamble: false, expect: 'stall' },
  // 乱拍：抖动 ±40%（force 跳过 keysim 自检）：点划比必跌破 2 倍、一致性必超 ±51%，开始符校验应持续失败
  { name: 'chaos-jitter40',    kind: 'chaos', rate: 120, alphabet: 'short', style: 'human', jitter: 0.4, force: true, expect: 'stall' },
]
const SCENARIOS = DOMAIN === 'postJob' ? SCENARIOS_POSTJOB : SCENARIOS_ZUXUN

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

async function createTrain(owner, { alphabet, groups, pages }) {
  if (DOMAIN === 'postJob') {
    // 岗位训练是属主域：非创建者读 detail 直接 207。后端单会话模型下同账号两会话互踢，
    // 所以建训/取报文/detail 一律复用页面里学员登录后的同一对 token+deviceId
    const data = await rest('/postTelegramTrain/save', {
      method: 'POST', token: owner.token, deviceId: owner.deviceId,
      body: {
        name: `keysim岗位-${alphabet}-${Date.now()}`, isCable: 0, cableId: null, startPage: 1,
        type: alphabet === 'letter' ? 1 : 0, codeSort: false, isAverage: false, isRandom: true,
        messageNumber: groups * pages, ruleId: RULE_ID, messageBody: []
      }
    })
    return data.id
  }
  const data = await rest('/generalTickerPatTrain/add', {
    method: 'POST', token: owner.token, deviceId: owner.deviceId,
    body: {
      name: `keysim自动化-${alphabet}-${Date.now()}`, isCable: 0, type: alphabet === 'letter' ? 1 : 0, trainType: 0,
      codeSort: false, isRandom: true, messageNumber: groups * pages, ruleId: RULE_ID, isAverage: false,
      userId: [STUDENT.id]
    }
  })
  return data.id
}

async function pageText(owner, trainId, floorNumber = 1) {
  const data = DOMAIN === 'postJob'
    ? await rest('/postTelegramTrain/findMessageBody', { method: 'POST', token: owner.token, deviceId: owner.deviceId, body: { id: trainId, floorNumber } })
    : await rest('/generalTickerPatTrain/findPage', { method: 'POST', token: owner.token, deviceId: owner.deviceId, body: { id: trainId, userId: STUDENT.id, floorNumber } })
  return data.messageKey.map(g => JSON.parse(g.moresKey).join('')).join(' ')
}

/** 每组首两字符互换：节拍完美的「乱拍」 */
function corruptSwap(text) {
  return text.split(' ').map(g => g.length > 1 ? g[1] + g[0] + g.slice(2) : g).join(' ')
}

async function trainDetail(owner, trainId) {
  if (DOMAIN === 'postJob') {
    const data = await rest('/postTelegramTrain/detail', { method: 'POST', token: owner.token, deviceId: owner.deviceId, body: { id: trainId } })
    return { finished: data.status === 2, score: data.score, accuracy: data.accuracy, speed: data.speed, errors: data.errorNumber ?? 0, lack: data.lack ?? 0, validTime: data.validTime, raw: data }
  }
  const data = await rest('/generalTickerPatTrain/detail', { method: 'POST', token: owner.token, deviceId: owner.deviceId, body: { id: trainId, uid: STUDENT.id } })
  const me = data.userInfoList.find(u => u.userId === STUDENT.id) || {}
  return { finished: me.isFinish === 1, score: me.score, accuracy: me.accuracy, speed: me.speed, errors: me.errorNumber ?? 0, lack: me.lack ?? 0, validTime: me.validTime, raw: me }
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

const PAGE_PATH = DOMAIN === 'postJob'
  ? { train: '#/preview/basicSkill/postJob/telegram/handKeyPostJobTrain', list: '#/preview/basicSkill/postJob/telegram/handKeyPostJob' }
  : { train: '#/preview/networkUsing/equipmentNetwork/handkeyZuXunTrain', list: '#/preview/networkUsing/equipmentNetwork/handkeyZuXunList' }
const storeOnline = `(() => { const p = document.querySelector('#app').__vue_app__.config.globalProperties.$pinia; let o = {}; p._s.forEach((s, id) => { if (id === 'traffic') o = { link: s.linkStatus, dev: s.devStatus } }); return o })()`

/** 进学员训练页（同一路由 query 变化不重挂载，先回列表再进），并确保串口在线 */
async function enterTrainPage(cdp, trainId) {
  const readyWait = DOMAIN === 'postJob'
    ? `new Promise(res => { const t = setInterval(() => { if ([...document.querySelectorAll('.start')].some(b => b.innerText.includes('开始训练'))) { clearInterval(t); res(true) } }, 300); setTimeout(() => { clearInterval(t); res(false) }, 20000) })`
    : `new Promise(res => { const t = setInterval(() => { if ([...document.querySelectorAll('.roadBtn')].some(b => b.innerText.includes('准备拍发'))) { clearInterval(t); res(true) } }, 300); setTimeout(() => { clearInterval(t); res(false) }, 20000) })`
  for (let attempt = 0; attempt < 2; attempt++) {
    await cdp.eval(`location.hash = '${PAGE_PATH.list}'; true`)
    await delay(1800)
    await cdp.eval(`location.hash = '${PAGE_PATH.train}?id=${trainId}'; true`)
    await cdp.eval(readyWait)
    const serial = await cdp.eval(storeOnline)
    if (serial.dev) break
    if (attempt === 1) {
      const trace = await cdp.eval(`(window.__serialTrace || []).slice(-20)`)
      console.error('  串口链路追踪：', JSON.stringify(trace))
      throw new Error('串口未连接（traffic.devStatus=false，重试后仍不通）')
    }
    await delay(2000)
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
    await cdp.eval(`location.hash = '${PAGE_PATH.list}'; true`)
    await delay(1500)
    await cdp.eval(`location.hash = '${PAGE_PATH.train}?id=${trainId}'; true`)
    await delay(4000)
    const st = await cdp.eval(storeOnline)
    if (!st.dev) throw new Error(`串口重连失败（${probeOk}，导航重连后 devStatus=false）`)
  }
}

/** 学员侧开始训练：组训走教员房间广播，岗位走页面[开始训练]按钮 */
async function beginTrain(cdp, teacherToken, trainId) {
  if (DOMAIN === 'postJob') {
    // 等待[开始训练]按钮渲染（detail 是异步加载；探针重导航后不能假定已就绪）
    await cdp.eval(`new Promise((res, rej) => { const t = setInterval(() => { if ([...document.querySelectorAll('.start')].some(b => b.innerText.includes('开始训练'))) { clearInterval(t); res(true) } }, 300); setTimeout(() => rej(new Error('开始训练按钮等待超时')), 20000) })`)
    await cdp.eval(`(() => { const b = [...document.querySelectorAll('.start')].find(b => b.innerText.includes('开始训练')); if (!b) throw new Error('开始训练按钮不存在'); b.click(); return true })()`)
  } else {
    await cdp.eval(`[...document.querySelectorAll('.roadBtn')].find(b => b.innerText.includes('准备拍发')).click(); true`)
    await delay(1000)
    await rest('/socket/generalTickerPatTrain/updateTrainStatus', { method: 'POST', token: teacherToken, deviceId: TEACHER.deviceId, body: { trainId, status: 1 } })
    const room = new WebSocket(`ws://localhost:18001/generalTickerPat/${TEACHER.id}/${trainId}/1?token=${encodeURIComponent(teacherToken)}&deviceId=${TEACHER.deviceId}`)
    await new Promise((res, rej) => { room.onopen = res; room.onerror = () => rej(new Error('教员房间 WS 连接失败')) })
    await delay(800) // 等学员 WS 入房（学员进页时 connectWebsocket）
    for (let attempt = 0; attempt < 3; attempt++) {
      room.send(JSON.stringify({ topic: 'begin' }))
      const begun = await cdp.eval(`new Promise(res => { const t = setInterval(() => { if (document.body.innerText.includes('拍发开始符号')) { clearInterval(t); res(true) } }, 300); setTimeout(() => { clearInterval(t); res(false) }, 4000) })`)
      if (begun) { room.close(); return }
    }
    room.close()
    throw new Error('未进入开始符阶段（begin 广播 3 次均未生效）')
  }
  // 岗位：begin 由页面自己调 REST，等开始符阶段出现即可
  await cdp.eval(`new Promise((res, rej) => { const t = setInterval(() => { if (document.body.innerText.includes('拍发开始符号')) { clearInterval(t); res(true) } }, 300); setTimeout(() => rej(new Error('未进入开始符阶段')), 15000) })`)
}

/** 跑一个完整训练场景，返回服务端权威成绩（stall 场景返回未结算现场） */
async function runScenario(cdp, teacherToken, scenario) {
  const { name, rate, alphabet, fault, pages = 1, groups = GROUPS, style = 'machine', jitter, skewGate, preamble = true, corrupt, force } = scenario
  const owner = DOMAIN === 'postJob' ? scenario._session : { token: teacherToken, deviceId: TEACHER.deviceId }
  // 1) 建训、取报文
  const trainId = await createTrain(owner, { alphabet, groups, pages })
  const pageTexts = []
  for (let p = 1; p <= pages; p++) pageTexts.push(await pageText(owner, trainId, p))

  // 2) 逐页拍发计划（非末页翻页符收尾；翻页后新页要重拍开始符，keysim 每页自带）
  const pagePlans = pageTexts.map((text, i) => ({
    key: 'hand', text: corrupt === 'swap' ? corruptSwap(text) : text, alphabet, rate, style,
    ...(jitter !== undefined ? { jitter } : {}),
    skew: skewGate ?? 51, preamble,
    ...(force ? { force: true } : {}),
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
  console.log(`→ [${name}] 训练 ${trainId}：${previews[0].chars} 字 × ${pages} 页 / ${rate} 字/分 / 约 ${Math.round(totalDuration / 1000)}s${style !== 'machine' ? ` / ${style}${jitter !== undefined ? '±' + jitter * 100 + '%' : ''}` : ''}${fault ? ` / 故障 ${fault}` : ''}${corrupt ? ' / 乱拍' : ''}${preamble ? '' : ' / 无开始符'}`)

  // 3) 进训练页并开始
  await enterTrainPage(cdp, trainId)
  await beginTrain(cdp, teacherToken, trainId)

  // 4) 逐页拍发
  for (let i = 0; i < pagePlans.length; i++) {
    const send = await keysim('/api/send', pagePlans[i])
    if (!send.ok) throw new Error(`keysim 第 ${i + 1} 页拍发失败：${send.error}`)
    if (i + 1 < pagePlans.length) {
      const flipped = await cdp.eval(`new Promise(res => { const t = setInterval(() => { const n = document.querySelector('.pag .curr .num'); if (n && n.innerText.trim() === '${i + 2}') { clearInterval(t); res(true) } }, 500); setTimeout(() => { clearInterval(t); res(false) }, ${Math.round(previews[i].duration) + 30000}) })`)
      if (!flipped) throw new Error(`第 ${i + 1} 页拍完后未翻到第 ${i + 2} 页（翻页符未被识别）`)
      console.log(`  [${name}] 已翻到第 ${i + 2} 页`)
    }
  }

  // 5) 等结算（以后端 detail 为权威）；stall 场景等拍完后观察 45s 要求“未结算”
  const settleDeadline = Date.now() + totalDuration + 90000
  const stallObserveMs = 45000
  const t0 = Date.now()
  let lastFloor = ''
  for (;;) {
    const d = await trainDetail(owner, trainId)
    if (d.finished) {
      return { name, trainId, score: d.score, accuracy: d.accuracy, speed: d.speed, errors: d.errors, lack: d.lack, validTime: d.validTime }
    }
    if (scenario.expect === 'stall' && Date.now() - t0 > totalDuration + stallObserveMs) {
      const page = await cdp.eval(`(() => { const t = document.body.innerText; return { startPhase: t.includes('拍发开始符号') || t.includes('试机操作'), err: (t.match(/拍发[^\\n]*错误[^\\n]*/g) || []).slice(0, 2) } })()`).catch(() => null)
      return { name, trainId, stalled: true, page, validTime: Math.round((Date.now() - t0) / 1000) }
    }
    if (scenario.expect !== 'stall' && Date.now() > settleDeadline) {
      throw new Error(`结算超时（detail 未完成，lastFloor=${lastFloor}）`)
    }
    await delay(5000)
  }
}

/** 结果是否满足场景预期 */
function checkExpect(result, scenario) {
  if (result.stalled) return scenario.expect === 'stall' ? null : '应结算但训练未结束'
  if (scenario.expect === 'stall') return `应无法结束但已结算：score=${result.score}`
  if (scenario.expect === 'perfect') {
    if (result.accuracy !== '100.00' || result.lack !== 0) return `应满分容忍：accuracy=${result.accuracy} lack=${result.lack} errors=${result.errors}`
  } else if (scenario.expect === 'degraded') {
    if (result.accuracy === '100.00' || result.errors === 0) return `应劣化扣分（故障未生效？）：accuracy=${result.accuracy} errors=${result.errors}`
  } else if (scenario.expect === 'low') {
    const acc = parseFloat(result.accuracy)
    if (!(acc < 20)) return `乱拍应大幅错码：accuracy=${result.accuracy}`
  }
  return null
}

async function main() {
  await preflight()
  const teacherToken = await login(TEACHER)
  const cdp = await setupOnce()
  // 后端单会话模型下复用页面学员会话（另起 REST 登录会把页面会话踢成 206）
  const pageSession = DOMAIN === 'postJob'
    ? await cdp.eval(`({ token: localStorage.getItem('token'), deviceId: localStorage.getItem('deviceId') })`)
    : null

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
      name: 'single', rate: RATE, alphabet: ALPHABET, pages: PAGES, groups: GROUPS,
      style: args.style || 'machine',
      ...(args.jitter !== undefined ? { jitter: Number(args.jitter) } : {}),
      ...(args.skewGate !== undefined ? { skewGate: Number(args.skewGate) } : {}),
      ...(args.noStart ? { preamble: false } : {}),
      ...(args.corrupt ? { corrupt: args.corrupt } : {}),
      fault: args.fault || null,
      expect: args.expect || (args.fault ? (String(args.fault).includes('missingUp') ? 'degraded' : 'perfect') : 'perfect')
    }]
  }

  const results = []
  const violations = []
  for (const scenario of scenarios) {
    try {
      const result = await runScenario(cdp, teacherToken, { ...scenario, _session: pageSession })
      const violation = checkExpect(result, scenario)
      results.push({ ...result, expect: scenario.expect, pass: !violation })
      if (result.stalled) {
        console.log(`  [${result.name}] 训练未结束（符合 stall 预期）：页面仍停在开始符/试机阶段=${result.page?.startPhase}，${result.validTime}s 无结算${violation ? `  ✗ ${violation}` : ''}`)
      } else {
        console.log(`  [${result.name}] score=${result.score} accuracy=${result.accuracy}% speed=${result.speed}字/分 errors=${result.errors} lack=${result.lack} validTime=${result.validTime}s${violation ? `  ✗ ${violation}` : ''}`)
      }
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
      console.log(`  ${r.pass ? '✓' : '✗'} ${r.name.padEnd(20)} ${r.error ? r.error : r.stalled ? `未结束（stall）` : `score=${r.score} accuracy=${r.accuracy}% speed=${r.speed} errors=${r.errors} lack=${r.lack}`}`)
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
