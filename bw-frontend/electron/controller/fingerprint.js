const {ipcMain} = require('electron')
const fsp = require('fs').promises
const path = require('path')
const crypto = require('crypto')
const {execFile} = require('child_process')
const {machineDir} = require('./license')

/**
 * 硬件设备指纹。
 *
 * 约束：发号器不可改动。它接收「设备码字符串」并输出
 * AES-ECB(设备码 + ':' + 时间戳[+ ':' + 天数], 'wisdom23')，不关心字符串内容。
 * 因此这里只改「设备码怎么算」，保持与原随机 UUID 完全相同的外形
 * （8 组 4 位十六进制 = 32 个 hex 字符），发号器零改动。
 *
 * 32 个 hex 字符 = 4 个槽位 × 8 位，每槽 = sha256(因子) 前 4 字节：
 *   槽1 主机标识   Win: MachineGuid        Linux: /etc/machine-id
 *   槽2 整机 UUID  Win: Win32_ComputerSystemProduct.UUID
 *                  Linux: /sys/class/dmi/id/product_uuid（root，或安装时快照）
 *   槽3 磁盘序列号 Win: Win32_DiskDrive.SerialNumber
 *                  Linux: /dev/disk/by-id 首个非可移动物理盘
 *   槽4 整机序列号 Win: Win32_BIOS.SerialNumber
 *                  Linux: product_serial / board_serial（root，或安装时快照）
 * 取不到的因子填哨兵 00000000，比对时跳过。
 *
 * 明确不采集：
 *   - MAC 地址：网卡插拔 / WiFi 开关 / 虚拟网卡都会变（原 system.js:102-127 的坑）
 *   - CPU ID：同批次整机的 ProcessorId 常完全相同，会造成跨机误判
 */

const SENTINEL = '00000000'

// 白牌机 / 组装机的 BIOS 常返回这些占位串，跨机器完全相同，必须剔除，
// 否则会把大量不同机器判成同一台。
const JUNK = new Set([
	'', '0', '00', '000', 'none', 'null', 'n/a', 'na', 'unknown', 'default string',
	'to be filled by o.e.m.', 'to be filled by o.e.m', 'system serial number',
	'chassis serial number', 'not specified', 'not available', 'invalid',
	'0000000000000000', 'ffffffff-ffff-ffff-ffff-ffffffffffff',
	'00000000-0000-0000-0000-000000000000'
])

function hash8(value) {
	if (value == null) return SENTINEL
	const s = String(value).trim()
	if (!s || JUNK.has(s.toLowerCase())) return SENTINEL
	if (/^0+$/.test(s.replace(/[-\s]/g, ''))) return SENTINEL
	return crypto.createHash('sha256').update(s).digest('hex').slice(0, 8)
}

function formatCode(hex32) {
	return hex32.match(/.{1,4}/g).join('-')
}

function run(cmd, args, timeout) {
	return new Promise((resolve) => {
		try {
			execFile(
				cmd,
				args,
				{windowsHide: true, timeout: timeout || 10000, encoding: 'utf8', maxBuffer: 1024 * 1024},
				(err, stdout) => resolve(err ? null : String(stdout || ''))
			)
		} catch (e) {
			resolve(null)
		}
	})
}

async function readText(p) {
	try {
		const t = await fsp.readFile(p, 'utf-8')
		return t.trim() || null
	} catch (e) {
		return null
	}
}

// -------- Windows --------

const PS_SCRIPT = [
	"$ErrorActionPreference='SilentlyContinue';",
	"$p=Get-CimInstance Win32_ComputerSystemProduct;",
	"$b=Get-CimInstance Win32_BIOS;",
	"$d=Get-CimInstance Win32_DiskDrive|Sort-Object Index|Select-Object -First 1;",
	"Write-Output ('uuid='+$p.UUID);",
	"Write-Output ('bios='+$b.SerialNumber);",
	"Write-Output ('disk='+$d.SerialNumber);"
].join('')

function pickLine(text, key) {
	if (!text) return null
	const m = text.match(new RegExp('^\\s*' + key + '\\s*=\\s*(.+)$', 'im'))
	return m ? m[1].trim() : null
}

async function collectWindows() {
	const out = {primary: null, systemUuid: null, diskSerial: null, boardSerial: null}

	const reg = await run('reg', [
		'query', 'HKLM\\SOFTWARE\\Microsoft\\Cryptography', '/v', 'MachineGuid', '/reg:64'
	], 8000)
	if (reg) {
		const m = reg.match(/MachineGuid\s+REG_SZ\s+([^\r\n]+)/i)
		if (m) out.primary = m[1].trim()
	}

	const ps = await run('powershell', [
		'-NoProfile', '-NonInteractive', '-ExecutionPolicy', 'Bypass', '-Command', PS_SCRIPT
	], 20000)
	out.systemUuid = pickLine(ps, 'uuid')
	out.boardSerial = pickLine(ps, 'bios')
	out.diskSerial = pickLine(ps, 'disk')

	// PowerShell 不可用（老系统 / 被策略禁用）时回退 wmic；
	// wmic 在新版 Windows 11 上已移除，两者互为补充。
	if (!out.systemUuid) {
		out.systemUuid = pickLine(await run('wmic', ['csproduct', 'get', 'uuid', '/value'], 10000), 'UUID')
	}
	if (!out.boardSerial) {
		out.boardSerial = pickLine(await run('wmic', ['bios', 'get', 'serialnumber', '/value'], 10000), 'SerialNumber')
	}
	if (!out.diskSerial) {
		out.diskSerial = pickLine(
			await run('wmic', ['diskdrive', 'where', 'Index=0', 'get', 'serialnumber', '/value'], 10000),
			'SerialNumber'
		)
	}
	return out
}

// -------- Linux --------

// 安装时由 deb postinst（root 身份）落盘的 DMI 快照。
// /sys/class/dmi/id/product_uuid 等在内核中是 0400，普通用户读不到；
// 有了这份快照，非 root 运行也能拿到槽2 / 槽4，可比对因子从 2 个提升到 4 个。
async function readHwidSnapshot() {
	const dir = machineDir()
	if (!dir) return {}
	const txt = await readText(path.join(dir, 'hwid'))
	if (!txt) return {}
	const out = {}
	txt.split(/\r?\n/).forEach((line) => {
		const i = line.indexOf('=')
		if (i > 0) out[line.slice(0, i).trim()] = line.slice(i + 1).trim()
	})
	return out
}

async function linuxDiskSerial() {
	try {
		const names = await fsp.readdir('/dev/disk/by-id')
		const cands = names.filter((n) => !n.includes('-part') && !n.startsWith('wwn-') && !n.startsWith('usb-'))
		for (const prefix of ['nvme-', 'ata-', 'scsi-', 'mmc-']) {
			const hit = cands.filter((n) => n.startsWith(prefix)).sort()
			if (hit.length) return hit[0]
		}
		const rest = cands.sort()
		return rest.length ? rest[0] : null
	} catch (e) {
		return null
	}
}

async function collectLinux() {
	const snap = await readHwidSnapshot()
	return {
		primary: await readText('/etc/machine-id') || await readText('/var/lib/dbus/machine-id'),
		systemUuid: snap.product_uuid || await readText('/sys/class/dmi/id/product_uuid'),
		diskSerial: await linuxDiskSerial(),
		boardSerial: snap.product_serial || snap.board_serial
			|| await readText('/sys/class/dmi/id/product_serial')
			|| await readText('/sys/class/dmi/id/board_serial')
	}
}

// -------- 对外 --------

let cached = null

async function fingerprint(force) {
	if (cached && !force) return cached
	let raw = {primary: null, systemUuid: null, diskSerial: null, boardSerial: null}
	try {
		if (process.platform === 'win32') raw = await collectWindows()
		else if (process.platform === 'linux') raw = await collectLinux()
	} catch (e) {
		// 采集失败一律降级为哨兵，绝不因此阻塞授权流程
	}
	const slots = [hash8(raw.primary), hash8(raw.systemUuid), hash8(raw.diskSerial), hash8(raw.boardSerial)]
	const available = slots.filter((s) => s !== SENTINEL).length
	cached = {
		slots,
		available,
		// 至少要有 2 个真实因子才敢用作设备码，避免在采集全失败的机器上
		// 每次启动都给出不同/无意义的设备码。
		code: available >= 2 ? formatCode(slots.join('')) : null,
		platform: process.platform
	}
	return cached
}

ipcMain.handle('controller.license.fingerprint', async (event, args) => fingerprint(!!(args && args.force)))

module.exports = {fingerprint}
