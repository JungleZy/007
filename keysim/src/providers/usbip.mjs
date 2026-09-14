/**
 * USB/IP 后端：普通用户权限跑设备模拟器（rust/keysim-usbip），
 * 只把"挂上 USB 总线"这一步交给装一次的 root 助手（bin/keysim-attachd.py）。
 * 挂上之后内核真的多出一个 USB 串口（/dev/ttyACM*，有 udev 记录），
 * 浏览器的串口选择框与桌面程序的串口列表都能直接选中它。
 */
import {spawn} from 'node:child_process'
import {access, constants, readdir} from 'node:fs/promises'
import {connect} from 'node:net'
import {fileURLToPath} from 'node:url'

const CRATE = fileURLToPath(new URL('../../rust/keysim-usbip/', import.meta.url))
const BINARY = `${CRATE}target/release/keysim-usbip`
export const HELPER_SOCKET = process.env.KEYSIM_ATTACHD_SOCKET ?? '/run/keysim/keysim-attachd.sock'
export const INSTALLER = fileURLToPath(new URL('../../bin/install-root-helper.sh', import.meta.url))

const exists = async path => { try { await access(path, constants.F_OK); return true } catch { return false } }

/** 跟 root 助手说一句话；助手不在就明确报"没装"。 */
export function askHelper(request, {socketPath = HELPER_SOCKET, timeout = 20000} = {}) {
  return new Promise(resolve => {
    const socket = connect(socketPath)
    let raw = ''
    const finish = result => {
      socket.destroy()
      clearTimeout(timer)
      resolve(result)
    }
    const timer = setTimeout(() => finish({ok: false, error: `root 助手无响应（${timeout}ms）`}), timeout)
    socket.on('error', error => finish({
      ok: false,
      error: error.code === 'ENOENT' || error.code === 'ECONNREFUSED'
        ? `root 助手未安装（${socketPath}）：先执行一次 sudo ${INSTALLER}`
        : `root 助手连接失败：${error.message}`
    }))
    socket.on('connect', () => socket.write(`${JSON.stringify(request)}\n`))
    socket.on('data', chunk => {
      raw += chunk
      if (!raw.includes('\n')) return
      try { finish(JSON.parse(raw.trim())) } catch (error) { finish({ok: false, error: `助手响应不是 JSON：${error.message}`}) }
    })
    socket.on('close', () => finish({ok: false, error: 'root 助手提前关闭了连接'}))
  })
}

async function cargoAvailable() {
  return new Promise(resolve => {
    const child = spawn('sh', ['-c', 'command -v cargo'], {stdio: 'ignore'})
    child.on('error', () => resolve(false))
    child.on('close', code => resolve(code === 0))
  })
}

/** 缺二进制就现编（零依赖 crate，编译只需本地 registry 缓存）。 */
export async function buildEmulator({onLog = () => {}} = {}) {
  if (await exists(BINARY)) return {ok: true, binary: BINARY, built: false}
  if (!(await cargoAvailable())) return {ok: false, error: '没有 cargo，无法编译 keysim-usbip（也可自行提供 target/release/keysim-usbip）'}
  onLog('正在编译 keysim-usbip（首次约数秒）…')
  const result = await new Promise(resolve => {
    const child = spawn('cargo', ['build', '--release'], {cwd: CRATE, stdio: ['ignore', 'pipe', 'pipe']})
    let stderr = ''
    child.stderr.on('data', chunk => { stderr += chunk })
    child.on('error', error => resolve({ok: false, error: error.message}))
    child.on('close', code => resolve(code === 0 ? {ok: true} : {ok: false, error: stderr.trim().split('\n').slice(-6).join('\n')}))
  })
  if (!result.ok) return {ok: false, error: `编译 keysim-usbip 失败：${result.error}`}
  return {ok: true, binary: BINARY, built: true}
}

/** 一次性安装 root 助手：root 直接跑，否则 pkexec 弹框；都不行就把命令交回去。 */
export async function installHelper() {
  const command = `bash ${INSTALLER}`
  if (typeof process.getuid === 'function' && process.getuid() === 0) {
    return runShell(command)
  }
  const pkexec = await new Promise(resolve => {
    const child = spawn('sh', ['-c', 'command -v pkexec'], {stdio: 'ignore'})
    child.on('error', () => resolve(false))
    child.on('close', code => resolve(code === 0))
  })
  if (pkexec && (process.env.DISPLAY || process.env.WAYLAND_DISPLAY)) {
    return runShell(`pkexec bash ${INSTALLER}`)
  }
  return {ok: false, error: '没有 root，也没有可弹框的 pkexec', command: `sudo bash ${INSTALLER}`}
}

function runShell(command) {
  return new Promise(resolve => {
    const child = spawn('sh', ['-c', command], {stdio: ['ignore', 'pipe', 'pipe']})
    let stdout = ''
    let stderr = ''
    child.stdout.on('data', chunk => { stdout += chunk })
    child.stderr.on('data', chunk => { stderr += chunk })
    child.on('error', error => resolve({ok: false, error: error.message, command}))
    child.on('close', code => resolve(code === 0
      ? {ok: true, output: stdout.trim().split('\n').slice(-4).join('\n')}
      : {ok: false, error: (stderr || stdout).trim().split('\n').slice(-4).join('\n'), command}))
  })
}

export async function probeUsbip() {
  const [binary, helper] = await Promise.all([exists(BINARY), askHelper({op: 'probe'})])
  return {
    binaryBuilt: binary,
    cargo: await cargoAvailable(),
    helper,
    installer: INSTALLER
  }
}

/** 新出现的 ttyACM* 就是我们的设备；attach 后内核枚举需要一点时间。 */
async function waitForTty(before, {timeout = 8000} = {}) {
  const deadline = Date.now() + timeout
  while (Date.now() < deadline) {
    const now = new Set((await readdir('/dev').catch(() => [])).filter(name => /^ttyACM\d+$/.test(name)))
    for (const name of now) if (!before.has(name)) return `/dev/${name}`
    await new Promise(resolve => setTimeout(resolve, 200))
  }
  return null
}

/**
 * 启动设备：编译（如需）→ 跑模拟器 → 让 root 助手把它挂上总线 → 等 /dev/ttyACM* 出现。
 * 返回 write()/close() 与被测程序应选的设备路径。
 */
export async function startUsbipDevice({onLog = () => {}, onRx = () => {}} = {}) {
  const build = await buildEmulator({onLog})
  if (!build.ok) return {ok: false, error: build.error}

  const before = new Set((await readdir('/dev').catch(() => [])).filter(name => /^ttyACM\d+$/.test(name)))
  const child = spawn(build.binary, ['--port', '0'], {stdio: ['pipe', 'pipe', 'pipe']})
  let buffer = ''
  let ready = null
  const readyPromise = new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error('模拟器启动超时')), 10000)
    child.stdout.on('data', chunk => {
      buffer += chunk
      let index
      while ((index = buffer.indexOf('\n')) >= 0) {
        const line = buffer.slice(0, index).trim()
        buffer = buffer.slice(index + 1)
        if (!line) continue
        let event
        try { event = JSON.parse(line) } catch { continue }
        if (event.type === 'ready') { ready = event; clearTimeout(timer); resolve(event) }
        else if (event.type === 'rx') onRx(event.hex)
        else if (event.type === 'attached') onLog('内核已接管 USB/IP 连接')
        else if (event.type === 'detached') onLog('内核已断开 USB/IP 连接')
        else if (event.type === 'log') onLog(`模拟器：${event.message}`)
      }
    })
    child.on('error', error => { clearTimeout(timer); reject(error) })
    child.on('close', code => { clearTimeout(timer); reject(new Error(`模拟器退出（code=${code}）`)) })
  })
  child.stderr.on('data', chunk => onLog(`模拟器 stderr：${String(chunk).trim()}`))

  try {
    await readyPromise
  } catch (error) {
    child.kill('SIGTERM')
    return {ok: false, error: error.message}
  }

  const attached = await askHelper({op: 'attach', port: ready.usbipPort, busid: ready.busid})
  if (!attached.ok) {
    child.kill('SIGTERM')
    return {ok: false, error: attached.error, needsHelper: true}
  }

  const tty = await waitForTty(before)
  if (!tty) {
    await askHelper({op: 'detach', vhciPort: attached.vhciPort})
    child.kill('SIGTERM')
    return {ok: false, error: '已 attach 但没有出现 /dev/ttyACM*（cdc-acm 是否可用？）'}
  }

  return {
    ok: true,
    device: tty,
    vhciPort: attached.vhciPort,
    usbipPort: ready.usbipPort,
    write(bytes) {
      const payload = Buffer.from(bytes)
      const header = Buffer.alloc(4)
      header.writeUInt32BE(payload.length, 0)
      return child.stdin.write(Buffer.concat([header, payload]))
    },
    async close() {
      await askHelper({op: 'detach', vhciPort: attached.vhciPort}).catch(() => {})
      await new Promise(resolve => {
        if (child.exitCode !== null) return resolve()
        child.once('close', resolve)
        child.stdin.end()
        child.kill('SIGTERM')
        setTimeout(resolve, 1500)
      })
    }
  }
}
