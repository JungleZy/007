/**
 * 内核级虚拟串口后端。
 *
 * 为什么需要它：PTY（/dev/pts/N）只是字符设备，内核不把它注册成 udev 的 tty 设备，
 * 而 Chromium 的 Web Serial 是按 udev tty 设备枚举的 —— 所以 PTY 永远进不了浏览器的
 * 串口选择框。要让"浏览器和桌面程序都能直接选中这个串口"，必须让内核造出一个真 tty：
 *
 *  - linux-gadget : dummy_hcd + g_serial（或 usb_f_acm）。内核会先虚拟出一个 USB 主控，
 *                   再在上面挂一个 USB CDC-ACM 串口，得到 /dev/ttyGS0。
 *                   对系统来说和插了一根 USB 转串口线没有区别，udev 有记录，
 *                   因此浏览器选择框能看到，桌面程序也能看到。
 *  - linux-tty0tty: 外部 DKMS 模块，造 /dev/tnt0..7 成对虚拟串口（tnt0 <-> tnt1）。
 *                   keysim 持一端，被测程序开另一端。
 *  - windows-com0com: Windows 上的成对虚拟 COM 口（COM90 <-> COM91），
 *                   Chromium 与桌面程序都能直接选。
 *
 * 本模块只做三件事：探测、按需加载/创建、交出"我们该写哪个设备"。
 * 没有条件时不假装成功，把缺什么、装什么原样报上去。
 */
import {access, constants, readdir} from 'node:fs/promises'
import {spawn} from 'node:child_process'
import {platform, release} from 'node:os'

const run = (command, args, {timeout = 20000} = {}) => new Promise(resolve => {
  const child = spawn(command, args, {stdio: ['ignore', 'pipe', 'pipe']})
  let stdout = ''
  let stderr = ''
  const timer = setTimeout(() => { child.kill('SIGTERM'); resolve({ok: false, stdout, stderr: `${command} 超时`}) }, timeout)
  child.stdout.on('data', chunk => { stdout += chunk })
  child.stderr.on('data', chunk => { stderr += chunk })
  child.on('error', error => { clearTimeout(timer); resolve({ok: false, stdout, stderr: error.message}) })
  child.on('close', code => { clearTimeout(timer); resolve({ok: code === 0, stdout, stderr}) })
})

const exists = async path => { try { await access(path, constants.F_OK); return true } catch { return false } }
const isRoot = () => typeof process.getuid === 'function' && process.getuid() === 0
const isWsl = () => /microsoft|wsl/i.test(release())

const hasModule = async name => (await run('modinfo', [name], {timeout: 5000})).ok
const moduleLoaded = async name => {
  const result = await run('sh', ['-c', `grep -q "^${name} " /proc/modules`], {timeout: 3000})
  return result.ok
}

/** 提权前缀：root 直接跑；否则 pkexec（会弹系统授权框）。都没有就报不可用。 */
async function elevator() {
  if (isRoot()) return {ok: true, wrap: (command, args) => [command, args]}
  const pkexec = await run('sh', ['-c', 'command -v pkexec'], {timeout: 3000})
  if (pkexec.ok && (process.env.DISPLAY || process.env.WAYLAND_DISPLAY)) {
    return {ok: true, wrap: (command, args) => ['pkexec', [command, ...args]]}
  }
  return {ok: false, reason: isRoot() ? null : '需要 root 或可弹框的 pkexec 才能加载内核模块'}
}

const GADGET_DEVICE = '/dev/ttyGS0'
const TTY0TTY_PAIR = ['/dev/tnt0', '/dev/tnt1']

/** USB gadget：内核造一个虚拟 USB 主控 + CDC-ACM 串口，等于插了一根 USB 串口线。 */
const gadget = {
  id: 'linux-gadget',
  title: 'USB gadget（dummy_hcd + g_serial）',
  /** keysim 写这一端；被测程序在浏览器/桌面里选中的就是同一个 /dev/ttyGS0。 */
  ours: GADGET_DEVICE,
  theirs: GADGET_DEVICE,
  async probe() {
    if (platform() !== 'linux') return {available: false, reason: '仅 Linux'}
    const [dummy, serial] = await Promise.all([hasModule('dummy_hcd'), hasModule('g_serial')])
    if (!dummy || !serial) {
      return {
        available: false,
        reason: `内核未提供 ${!dummy ? 'dummy_hcd' : ''}${!dummy && !serial ? ' 与 ' : ''}${!serial ? 'g_serial' : ''} 模块`,
        install: isWsl()
          ? 'WSL2 默认内核没编 USB gadget：需自建内核（CONFIG_USB_DUMMY_HCD=m、CONFIG_USB_G_SERIAL=m）并在 .wslconfig 里 kernel= 指向它'
          : '安装内核附加模块包后重试，如 Debian/Ubuntu：sudo apt install linux-modules-extra-$(uname -r)'
      }
    }
    const elevation = await elevator()
    if (!elevation.ok) return {available: false, reason: elevation.reason}
    return {available: true, reason: null}
  },
  async start() {
    const elevation = await elevator()
    if (!elevation.ok) return {ok: false, error: elevation.reason}
    for (const module of ['dummy_hcd', 'g_serial']) {
      if (await moduleLoaded(module)) continue
      const [command, args] = elevation.wrap('modprobe', [module])
      const result = await run(command, args, {timeout: 60000})
      if (!result.ok) return {ok: false, error: `加载 ${module} 失败：${(result.stderr || '').trim() || '未知错误'}`}
    }
    for (let attempt = 0; attempt < 20; attempt++) {
      if (await exists(GADGET_DEVICE)) return {ok: true, ours: GADGET_DEVICE, theirs: GADGET_DEVICE}
      await new Promise(resolve => setTimeout(resolve, 100))
    }
    return {ok: false, error: `模块已加载但没出现 ${GADGET_DEVICE}`}
  },
  async stop() {
    const elevation = await elevator()
    if (!elevation.ok) return {ok: false, error: elevation.reason}
    const [command, args] = elevation.wrap('modprobe', ['-r', 'g_serial'])
    const result = await run(command, args, {timeout: 30000})
    return result.ok ? {ok: true} : {ok: false, error: (result.stderr || '').trim()}
  }
}

/** tty0tty：成对虚拟串口，一端给 keysim，一端给被测程序。 */
const tty0tty = {
  id: 'linux-tty0tty',
  title: 'tty0tty（成对虚拟串口 /dev/tnt0 <-> /dev/tnt1）',
  ours: TTY0TTY_PAIR[0],
  theirs: TTY0TTY_PAIR[1],
  async probe() {
    if (platform() !== 'linux') return {available: false, reason: '仅 Linux'}
    if (!(await hasModule('tty0tty'))) {
      return {
        available: false,
        reason: '未安装 tty0tty 内核模块',
        install: '需要内核头文件与 dkms：sudo apt install dkms linux-headers-$(uname -r)，再按 tty0tty 项目说明 dkms install'
      }
    }
    if (await exists(TTY0TTY_PAIR[0])) return {available: true, reason: null}
    const elevation = await elevator()
    return elevation.ok ? {available: true, reason: null} : {available: false, reason: elevation.reason}
  },
  async start() {
    if (!(await exists(TTY0TTY_PAIR[0]))) {
      const elevation = await elevator()
      if (!elevation.ok) return {ok: false, error: elevation.reason}
      const [command, args] = elevation.wrap('modprobe', ['tty0tty'])
      const result = await run(command, args, {timeout: 60000})
      if (!result.ok) return {ok: false, error: `加载 tty0tty 失败：${(result.stderr || '').trim()}`}
    }
    if (!(await exists(TTY0TTY_PAIR[0]))) return {ok: false, error: `模块已加载但没出现 ${TTY0TTY_PAIR[0]}`}
    return {ok: true, ours: TTY0TTY_PAIR[0], theirs: TTY0TTY_PAIR[1]}
  },
  async stop() { return {ok: true} }
}

/** com0com：Windows 上的成对虚拟 COM 口。 */
const com0com = {
  id: 'windows-com0com',
  title: 'com0com（成对虚拟 COM 口）',
  ours: 'CNCA90',
  theirs: 'COM91',
  async setupc() {
    for (const base of [process.env['ProgramFiles(x86)'], process.env.ProgramFiles]) {
      if (!base) continue
      const candidate = `${base}\\com0com\\setupc.exe`
      if (await exists(candidate)) return candidate
    }
    return null
  },
  async probe() {
    if (platform() !== 'win32') return {available: false, reason: '仅 Windows'}
    const tool = await this.setupc()
    if (!tool) {
      return {
        available: false,
        reason: '未检测到 com0com',
        install: '安装 com0com（https://sourceforge.net/projects/com0com/），它会提供成对虚拟 COM 口；浏览器与桌面程序都能直接选中'
      }
    }
    return {available: true, reason: null}
  },
  async start() {
    const tool = await this.setupc()
    if (!tool) return {ok: false, error: '未检测到 com0com'}
    const result = await run(tool, ['install', `PortName=${this.ours}`, `PortName=${this.theirs}`], {timeout: 120000})
    if (!result.ok) return {ok: false, error: (result.stderr || result.stdout || '').trim() || 'setupc install 失败' }
    return {ok: true, ours: `\\\\.\\${this.ours}`, theirs: this.theirs}
  },
  async stop() {
    const tool = await this.setupc()
    if (!tool) return {ok: true}
    const result = await run(tool, ['remove', '0'], {timeout: 120000})
    return result.ok ? {ok: true} : {ok: false, error: (result.stderr || '').trim()}
  }
}

export const BACKENDS = [gadget, tty0tty, com0com]

/**
 * 逐个探测内核级后端。返回顺序即优先级：能用的排前面。
 * 结果里带 install 字段的，是"装了这个前置就能用"的可执行建议。
 */
export async function probeKernelBackends() {
  const results = []
  for (const backend of BACKENDS) {
    const probe = await backend.probe()
    results.push({
      id: backend.id,
      title: backend.title,
      theirs: backend.theirs,
      available: probe.available,
      reason: probe.reason ?? null,
      install: probe.install ?? null
    })
  }
  return results
}

/** 启动第一个可用的内核级后端；没有可用的就返回 null，由调用方退回 PTY。 */
export async function startKernelSerial({prefer = null} = {}) {
  const ordered = prefer ? [...BACKENDS].sort((a, b) => (a.id === prefer ? -1 : b.id === prefer ? 1 : 0)) : BACKENDS
  const attempts = []
  for (const backend of ordered) {
    const probe = await backend.probe()
    if (!probe.available) {
      attempts.push({id: backend.id, error: probe.reason, install: probe.install ?? null})
      continue
    }
    const started = await backend.start()
    if (!started.ok) {
      attempts.push({id: backend.id, error: started.error})
      continue
    }
    return {
      id: backend.id,
      title: backend.title,
      ours: started.ours,
      theirs: started.theirs,
      stop: () => backend.stop(),
      attempts
    }
  }
  return {id: null, attempts}
}
