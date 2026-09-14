/**
 * E2E 夹具：真实后端会话、真实训练房间、真实前端静态服务、浏览器注入脚本。
 * 前置（不自动拉起，缺了就明确跳过并打印怎么起）：
 *   1. MySQL（容器 mysql-project006）；
 *   2. 后端（默认 http://localhost:18001/api，可用 KEYSIM_API 覆盖）；
 *   3. 前端构建产物 bw-frontend/frontend/dist（npm run build）。
 */
import {createServer} from 'node:http'
import {readFile} from 'node:fs/promises'
import {existsSync} from 'node:fs'
import {extname, join, normalize} from 'node:path'
import {fileURLToPath} from 'node:url'
import {createSession, login, DEFAULT_BASE} from '../src/sinks/rest.mjs'

export const ROOT = fileURLToPath(new URL('../../', import.meta.url))
export const DIST = join(ROOT, 'bw-frontend/frontend/dist')
export const API = process.env.KEYSIM_API ?? DEFAULT_BASE
export const ADMIN = {
  userAccount: process.env.KEYSIM_ADMIN ?? 'admin',
  password: process.env.KEYSIM_ADMIN_PASSWORD ?? '123456a'
}
export const STUDENT = {
  userAccount: process.env.KEYSIM_STUDENT ?? 'keysimstu',
  password: process.env.KEYSIM_STUDENT_PASSWORD ?? 'keysim123',
  userName: '模拟器学员',
  idCard: '600107199910155586'
}
/** 类型 0（手键）评分规则，skew/scale 与模拟器节拍模型对齐。 */
export const HANDKEY_RULE_TYPE = 0

export async function prerequisites() {
  const missing = []
  try {
    const response = await fetch(`${API.replace(/\/api$/, '')}/q/openapi`, {signal: AbortSignal.timeout(3000)})
    if (!response.ok) missing.push(`后端 ${API} 返回 ${response.status}`)
  } catch (error) {
    missing.push(`后端 ${API} 不可达（${error.message}）：先起 MySQL 再跑 java -Dquarkus.profile=dev -Dquarkus.http.port=18001 -jar backend/target/quarkus-app/quarkus-run.jar`)
  }
  if (!existsSync(join(DIST, 'index.html'))) missing.push(`缺少前端产物 ${DIST}：先在 bw-frontend/frontend 跑 npm run build`)
  return missing
}

export const adminSession = () => login({base: API, ...ADMIN, deviceId: `keysim-admin-${process.pid}`})

/** 学员账号：先注册，账号已存在就直接登录（signin 是匿名端点）。 */
export async function studentSession() {
  await fetch(`${API}/user/signin`, {
    method: 'POST',
    headers: {'content-type': 'application/json'},
    body: JSON.stringify({...STUDENT, userSex: 1})
  }).catch(() => {})
  const response = await fetch(`${API}/user/login`, {
    method: 'POST',
    headers: {'content-type': 'application/json'},
    body: JSON.stringify({userAccount: STUDENT.userAccount, password: STUDENT.password, deviceId: `keysim-student-${process.pid}`})
  })
  const envelope = await response.json()
  if (envelope.code !== 200) throw new Error(`学员登录失败：${envelope.code} ${envelope.message}`)
  return {
    session: createSession({base: API, token: envelope.data.token, deviceId: envelope.data.deviceId, user: envelope.data.user}),
    payload: envelope.data
  }
}

export async function handkeyRuleId(session) {
  const envelope = await session.call('/gradingRule/getGradingRuleListByType', {body: {type: HANDKEY_RULE_TYPE}})
  const rules = Array.isArray(envelope.data) ? envelope.data : envelope.data?.rows ?? []
  if (!rules.length) throw new Error('没有类型 0 的评分规则，无法建训')
  return rules[0].id
}

/** 建一个手键组训：创建者是教员（createUser），列出的用户是 role=0 学员。 */
export async function createHandKeyTrain(admin, {studentId, ruleId, messageNumber = 4, type = 1}) {
  const envelope = await admin.call('/generalTickerPatTrain/add', {
    body: {
      name: `keysim-${Date.now()}`,
      isCable: 0,
      startPage: 1,
      type,
      trainType: 0,
      codeSort: false,
      isRandom: true,
      isAverage: false,
      messageNumber,
      ruleId,
      userId: [studentId]
    }
  })
  return envelope.data
}

/** 从登录返回的菜单树推出页面路由，不写死菜单结构。 */
export function menuRoute(menus, leafPath) {
  const walk = (nodes, prefix) => {
    for (const node of nodes ?? []) {
      const path = `${prefix}/${node.path}`
      if (node.path === leafPath) return path
      const hit = walk(node.children, path)
      if (hit) return hit
    }
    return null
  }
  const route = walk(menus, '')
  if (!route) throw new Error(`菜单里没有 ${leafPath}，该角色无权访问此页`)
  return `/preview${route}`
}

const MIME = {
  '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.mjs': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8', '.json': 'application/json; charset=utf-8', '.svg': 'image/svg+xml',
  '.png': 'image/png', '.jpg': 'image/jpeg', '.jpeg': 'image/jpeg', '.gif': 'image/gif', '.ico': 'image/x-icon',
  '.woff': 'font/woff', '.woff2': 'font/woff2', '.ttf': 'font/ttf', '.eot': 'application/vnd.ms-fontobject',
  '.mp3': 'audio/mpeg', '.wav': 'audio/wav', '.map': 'application/json; charset=utf-8'
}

/**
 * 零依赖静态服务：直接喂 dist，hash 路由不需要 history 回退。
 * /runtime-config.js 按本次测试的后端地址现生成 —— 这正是 Web 部署唯一该改的文件
 * （public/runtime-config.js 注释所述）；注入 window.serverConfig 会被该文件覆盖，无效。
 */
export function serveDist({root = DIST, host = '127.0.0.1', api = API} = {}) {
  const backend = new URL(api.replace(/\/api$/, ''))
  const backendHost = `${backend.hostname}:${backend.port || 80}`
  const runtimeConfig = `window.serverConfig = ${JSON.stringify({
    httpUrl: backendHost, wsUrl: backendHost, fileUrl: backendHost,
    ueditorUrl: backendHost, ocrUrl: backendHost, mqttUrl: backend.hostname, mqttWsUrl: ''
  })};`
  const server = createServer(async (request, response) => {
    const requested = decodeURIComponent(new URL(request.url, 'http://localhost').pathname)
    if (requested === '/runtime-config.js') {
      response.writeHead(200, {'content-type': MIME['.js']})
      response.end(runtimeConfig)
      return
    }
    const relative = normalize(requested === '/' ? '/index.html' : requested).replace(/^(\.\.[/\\])+/, '')
    const file = join(root, relative)
    try {
      const body = await readFile(file)
      response.writeHead(200, {'content-type': MIME[extname(file)] ?? 'application/octet-stream'})
      response.end(body)
    } catch {
      response.writeHead(404, {'content-type': 'text/plain; charset=utf-8'})
      response.end('not found')
    }
  })
  return new Promise(resolve => server.listen(0, host, () => {
    const {port} = server.address()
    resolve({origin: `http://${host}:${port}`, port, close: () => new Promise(done => server.close(done))})
  }))
}

/**
 * 注入脚本：写登录态（同 useLogin.js:112-123 的键名），并把 navigator.serial 换成队列串口。
 * 后端地址由 serveDist 现生成的 runtime-config.js 决定，不在这里注入。
 * 真实生产代码路径完全不变：字节 -> WebSerial.handleData -> publishTrafficFrame -> 手键判定。
 */
export function initScript(loginPayload) {
  return `(() => {
    const payload = ${JSON.stringify(loginPayload)};
    localStorage.setItem('deviceId', payload.deviceId);
    localStorage.setItem('userInfo', JSON.stringify(payload.user));
    localStorage.setItem('userRole', JSON.stringify(payload.role));
    localStorage.setItem('userRouter', JSON.stringify(payload.menus));
    localStorage.setItem('token', payload.token);
    // NipSerial 只在这个标记存在时于 Web 模式接串口（NipSerial.vue:88-89）
    localStorage.setItem('serialChrome', 'true');

    let controller = null;
    let stream = null;
    const port = {
      open: async () => { stream = new ReadableStream({start(c) { controller = c }}); },
      close: async () => { controller = null; stream = null; },
      get readable() { return stream },
      writable: null,
      getInfo: () => ({usbVendorId: 0, usbProductId: 0})
    };
    Object.defineProperty(navigator, 'serial', {
      configurable: true,
      value: {
        getPorts: async () => [port],
        requestPort: async () => port,
        addEventListener() {}, removeEventListener() {}
      }
    });
    window.__keysimAttached = () => controller !== null;
    window.__keysimPush = bytes => { controller.enqueue(new Uint8Array(bytes)) };
    window.__keysimReplay = async (chunks, speed = 1) => {
      const started = performance.now();
      for (const chunk of chunks) {
        const wait = chunk.at / speed - (performance.now() - started);
        if (wait > 0) await new Promise(resolve => setTimeout(resolve, wait));
        controller.enqueue(new Uint8Array(chunk.bytes));
      }
      return performance.now() - started;
    };
  })()`
}

/**
 * 离线授权闸门：Web 模式唯一副本是 IndexedDB WisdomJ233 的 '1'/'2' 两键
 * （licenseStore.js:10,22-23）。写产品自带的万能码后重载即过闸
 * （VerifyLicense.js:8,241 跳过解密与设备码比对），不伪造任何加密授权。
 * 应用自身的 bootstrap 也会写一条空授权，所以写两次跨过这次竞争，并最多重试三轮。
 */
export async function authorize(page, {attempts = 3} = {}) {
  const write = `(async () => {
    await localforage.setItem('2', {machineCode: 'keysim-machine'})
    await localforage.setItem('1', {license: 'wjkj2025~', licenseTime: 0, duration: 0})
  })()`
  for (let attempt = 0; attempt < attempts; attempt++) {
    await page.waitForFunction('typeof localforage !== "undefined"', null, {timeout: 60000})
    await page.evaluate(write)
    await new Promise(resolve => setTimeout(resolve, 1200))
    await page.evaluate(write)
    await page.reload({waitUntil: 'domcontentloaded'})
    await page.waitForFunction('typeof localforage !== "undefined"', null, {timeout: 60000})
    await new Promise(resolve => setTimeout(resolve, 1200))
    if (!(await page.locator('#licenseCodeDiv').count())) return
  }
  throw new Error('离线授权闸门未通过：写入万能码后仍停在授权页')
}

/** 音频未就绪时会有一层 .maskBG 盖住整页并拦掉点击，必须先点掉它。 */
export async function dismissAudioMask(page) {
  const mask = page.locator('.maskBG')
  if (await mask.count()) await mask.first().click({timeout: 10000}).catch(() => {})
}

/** 轮询等待条件成立；用于等客户端把翻页/结算真正打到服务端。 */
export async function until(probe, {timeout = 30000, interval = 400, what = '条件'} = {}) {
  const deadline = Date.now() + timeout
  for (;;) {
    const value = await probe()
    if (value) return value
    if (Date.now() > deadline) throw new Error(`等待${what}超时（${timeout}ms）`)
    await new Promise(resolve => setTimeout(resolve, interval))
  }
}

/** 字节 sink 的结果转成可结构化克隆的形状，交给页面内调度器。 */
export const toPageChunks = chunks => chunks.map(chunk => ({at: chunk.at, bytes: [...chunk.bytes]}))
