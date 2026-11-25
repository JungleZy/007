const {ipcMain} = require('electron')
const {execFile} = require('child_process')
const {machineDir, userDir} = require('./license')
const {MIN_FACTORS, collectLinux, ensureInstallId, assemble} = require('../shared/hardware-factors')

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
 *                  Linux: /dev/disk/by-id 首个非可移动物理盘；没有时读
 *                  /sys/block 下的序列号，再退到 wwn 条目
 *   槽4 整机序列号 Win: Win32_BIOS.SerialNumber
 *                  Linux: product_serial / board_serial（root，或安装时快照）；
 *                  无 DMI 的设备树主板读 devicetree serial-number
 * 取不到的因子填哨兵 00000000，比对时跳过。
 * 硬件因子不足 2 个时，用机器级安装标识顶替空槽（见 fingerprint()）。
 * 因子清洗、Linux 采集与槽位组装在 ../shared/hardware-factors.js。
 *
 * 明确不采集：
 *   - MAC 地址：网卡插拔 / WiFi 开关 / 虚拟网卡都会变（原 system.js:102-127 的坑）
 *   - CPU ID：同批次整机的 ProcessorId 常完全相同，会造成跨机误判
 */

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

// -------- 对外 --------

let cached = null

async function fingerprint(force) {
	if (cached && !force) return cached
	let raw = {primary: null, systemUuid: null, diskSerial: null, boardSerial: null}
	try {
		if (process.platform === 'win32') raw = await collectWindows()
		else if (process.platform === 'linux') raw = await collectLinux({root: '/', machineDir: machineDir()})
	} catch (e) {
		// 采集失败一律降级为哨兵，绝不因此阻塞授权流程
	}
	// 至少要有 2 个因子才敢用作设备码。硬件因子不足时（无 DMI 的国产主板、
	// 虚拟机、未带 hwid 快照的安装包等）用持久化的机器级安装标识顶替空槽，
	// 保证设备码稳定且不再把用户挡在登录外；它不是浏览器随机标识，重启不变。
	let result = assemble(raw)
	let installIdError = null
	if (result.hardware < MIN_FACTORS) {
		const install = await ensureInstallId([machineDir(), userDir()])
		installIdError = install.error
		result = assemble(raw, install.id)
	}
	cached = {...result, installIdError, platform: process.platform}
	return cached
}

ipcMain.handle('controller.license.fingerprint', async (event, args) => fingerprint(!!(args && args.force)))

module.exports = {fingerprint}
