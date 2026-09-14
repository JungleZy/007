/**
 * 虚拟串口设备：真在系统里创建一个 PTY 字符设备（/dev/pts/N），
 * 对外就是"插了一个串口"——任何按路径打开串口的程序都能读到字节流。
 *
 * 能力边界（实测，不是推断）：
 * - 按路径打开串口的客户端（串口调试助手、pyserial、minicom、cat，以及桌面模式下
 *   真正去读串口的仓外桥接程序）：可用。
 * - 桌面壳的串口下拉列表：它执行 `ls /dev/tty*` 且只保留匹配 /USB\d+/ 的项
 *   （bw-frontend/electron/serial/nativeSerialPort.js:42-45），所以要进那个列表，
 *   得有一个 /dev/ttyUSBn 符号链接指向本设备；创建 /dev 下的链接需要 root，
 *   没有权限时这里会把现成的命令报上去，由使用者自己执行一次。
 * - Chromium 的 Web Serial：按 udev 设备枚举，PTY 不是 udev 设备，枚举不到；
 *   Web 模式仍走 keysim 的注入通道。
 */
import {spawn} from 'node:child_process'
import {access, constants, lstat, symlink, unlink} from 'node:fs/promises'
import {fileURLToPath} from 'node:url'
import {homedir} from 'node:os'
import {join} from 'node:path'

const HELPER = fileURLToPath(new URL('../../bin/ptybridge.py', import.meta.url))

export const DEFAULT_LINKS = [join(homedir(), '.keysim', 'ttyKEYSIM0')]
/** 桌面壳只认这个名字形态，见上文能力边界。 */
export const SYSTEM_LINK = '/dev/ttyUSB0'

const run = (command, args, {timeout = 60000} = {}) => new Promise(resolve => {
  const child = spawn(command, args, {stdio: ['ignore', 'pipe', 'pipe']})
  let stderr = ''
  const timer = setTimeout(() => { child.kill('SIGTERM'); resolve({ok: false, error: `${command} 超时`}) }, timeout)
  child.stderr.on('data', chunk => { stderr += chunk })
  child.on('error', error => { clearTimeout(timer); resolve({ok: false, error: error.message}) })
  child.on('close', code => { clearTimeout(timer); resolve({ok: code === 0, error: code === 0 ? null : (stderr.trim() || `${command} 退出码 ${code}`)}) })
})

const hasGraphicalSession = () => Boolean(process.env.DISPLAY || process.env.WAYLAND_DISPLAY)

/** 有没有可用的图形提权通道（与桌面壳授权串口的做法一致：pkexec）。 */
export async function probeElevation() {
  if (process.getuid && process.getuid() === 0) return {available: true, kind: 'root', reason: null}
  const which = await run('sh', ['-c', 'command -v pkexec'], {timeout: 3000})
  if (!which.ok) return {available: false, kind: null, reason: '没有 pkexec，无法弹出系统授权框'}
  if (!hasGraphicalSession()) return {available: false, kind: 'pkexec', reason: '当前没有图形会话（DISPLAY/WAYLAND_DISPLAY 为空），pkexec 无法弹框'}
  return {available: true, kind: 'pkexec', reason: null}
}

/**
 * 把设备接进系统串口列表：在 /dev 下建一个 ttyUSBn 符号链接。
 * 先直接建（root 时即成），否则走 pkexec 弹一次系统授权框——不需要用户去复制命令。
 */
export async function linkIntoSystem(devicePath, target = SYSTEM_LINK) {
  if (!devicePath) return {ok: false, error: '虚拟串口设备未开启'}
  try {
    await lstat(target).then(() => unlink(target)).catch(() => {})
    await symlink(devicePath, target)
    return {ok: true, target, elevated: false}
  } catch (error) {
    if (error.code !== 'EACCES' && error.code !== 'EPERM' && error.code !== 'EROFS') {
      return {ok: false, error: `${target}：${error.message}`}
    }
  }
  const elevation = await probeElevation()
  if (!elevation.available) {
    return {ok: false, error: elevation.reason, hint: `sudo ln -sfn ${devicePath} ${target}`}
  }
  const result = await run('pkexec', ['ln', '-sfn', devicePath, target])
  if (!result.ok) return {ok: false, error: `系统授权未通过：${result.error}`, hint: `sudo ln -sfn ${devicePath} ${target}`}
  return {ok: true, target, elevated: true}
}

/** 移除系统串口列表里的链接（只删指向本设备的那个，不碰真实硬件）。 */
export async function unlinkFromSystem(devicePath, target = SYSTEM_LINK) {
  try {
    const info = await lstat(target)
    if (!info.isSymbolicLink()) return {ok: false, error: `${target} 不是符号链接，拒绝删除`}
  } catch {
    return {ok: true, target, removed: false}
  }
  try {
    await unlink(target)
    return {ok: true, target, removed: true}
  } catch (error) {
    if (error.code !== 'EACCES' && error.code !== 'EPERM') return {ok: false, error: error.message}
  }
  const elevation = await probeElevation()
  if (!elevation.available) return {ok: false, error: elevation.reason}
  const result = await run('pkexec', ['rm', '-f', target])
  return result.ok ? {ok: true, target, removed: true, elevated: true} : {ok: false, error: result.error}
}

/** 探测本机是否具备创建虚拟串口设备的条件。 */
export async function probeDevice() {
  if (process.platform === 'win32') {
    return {available: false, reason: 'Windows 需要 com0com 之类的虚拟串口驱动，PTY 方案不适用'}
  }
  try {
    await access('/dev/ptmx', constants.R_OK | constants.W_OK)
  } catch {
    return {available: false, reason: '/dev/ptmx 不可读写，内核未启用 PTY 或权限不足'}
  }
  const python = await new Promise(resolve => {
    const probe = spawn('python3', ['-c', 'import pty, tty, select; print("ok")'], {stdio: ['ignore', 'pipe', 'ignore']})
    let output = ''
    probe.stdout.on('data', chunk => { output += chunk })
    probe.on('error', () => resolve(false))
    probe.on('close', () => resolve(output.trim() === 'ok'))
  })
  if (!python) return {available: false, reason: '缺少 python3（标准库 pty/tty/select 即可，无需第三方包）'}
  return {available: true, reason: null}
}

/**
 * 开一个虚拟串口设备。links 是要创建的符号链接路径；创建失败只告警，不影响设备本身。
 * onEvent 会收到 {type:'device'|'rx'|'warn'|'error'}。
 */
export function openDevice({links = DEFAULT_LINKS, onEvent = () => {}} = {}) {
  return new Promise((resolve, reject) => {
    const child = spawn('python3', [HELPER, ...links], {stdio: ['pipe', 'pipe', 'pipe']})
    let settled = false
    let buffer = ''
    const info = {path: null, links: [], warnings: []}

    child.stdout.on('data', chunk => {
      buffer += chunk
      let index
      while ((index = buffer.indexOf('\n')) >= 0) {
        const line = buffer.slice(0, index).trim()
        buffer = buffer.slice(index + 1)
        if (!line) continue
        let event
        try { event = JSON.parse(line) } catch { continue }
        if (event.type === 'device') {
          info.path = event.path
          info.links = event.links
          if (!settled) {
            settled = true
            resolve(handle)
          }
        }
        if (event.type === 'warn' || event.type === 'error') info.warnings.push(event.message)
        onEvent(event)
      }
    })
    child.stderr.on('data', chunk => onEvent({type: 'error', message: String(chunk).trim()}))
    child.on('error', error => { if (!settled) { settled = true; reject(error) } })
    child.on('close', code => {
      if (!settled) { settled = true; reject(new Error(`虚拟串口进程退出（code=${code}）`)) }
      else onEvent({type: 'warn', message: `虚拟串口进程已退出（code=${code}）`})
    })

    const handle = {
      get path() { return info.path },
      get links() { return info.links },
      get warnings() { return info.warnings },
      /** 设备"收到"字节：写进 PTY 主端，对端（真串口客户端）就读到它。 */
      write(bytes) {
        const payload = Buffer.from(bytes)
        const header = Buffer.alloc(4)
        header.writeUInt32BE(payload.length, 0)
        return child.stdin.write(Buffer.concat([header, payload]))
      },
      /** 没有 root 时给出的一次性命令，执行后桌面壳的串口列表就能看到它。 */
      systemLinkHint(link = SYSTEM_LINK) {
        return `sudo ln -sfn ${info.path} ${link}`
      },
      close() {
        return new Promise(done => {
          if (child.exitCode !== null) return done()
          child.once('close', () => done())
          child.stdin.end()
          child.kill('SIGTERM')
        })
      }
    }
  })
}
