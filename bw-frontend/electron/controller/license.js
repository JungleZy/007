const {ipcMain, app} = require('electron')
const fs = require('fs')
const fsp = fs.promises
const path = require('path')
const {execFile} = require('child_process')
const context = require('../core/node_core_ctx')

/**
 * 授权信息的机器级 / 用户级文件副本。
 *
 * 背景：原实现把授权状态唯一地存在渲染进程的 IndexedDB（file:// 源）里，
 * 该位置在 Electron 下极易丢失（配额驱逐、LevelDB 损坏、换 OS 账户 / 提权后
 * userData 路径变化），导致「未到期却要求重新授权」。
 *
 * 这里提供两个更稳的副本：
 *  - M（机器级）：跨 OS 账户、跨提权共享，解决「普通用户授权后用管理员身份运行就丢」
 *  - U（用户级）：M 不可写时兜底
 *
 * 注意：M 副本对本机所有用户可写（Windows 授予 BUILTIN\Users 完全控制，
 * Linux 目录 0777）。授权在本项目中已确认非安全边界，此处以可用性优先。
 */

const WIN_DIR_NAME = 'NipTrafficSystem'
const LINUX_MACHINE_DIR = '/var/lib/nip-traffic-system'
const FILE_NAME = 'license.json'

let winAclTried = false

function machineDir() {
	if (process.platform === 'win32') {
		const base = process.env.ProgramData || process.env.ALLUSERSPROFILE
		return base ? path.join(base, WIN_DIR_NAME) : null
	}
	if (process.platform === 'linux') {
		return LINUX_MACHINE_DIR
	}
	return null
}

function userDir() {
	try {
		return app.getPath('userData')
	} catch (e) {
		return null
	}
}

function filePathOf(dir) {
	return dir ? path.join(dir, FILE_NAME) : null
}

function describe(e) {
	return `${(e && e.code) || ''} ${(e && e.message) || e}`.trim()
}

// Windows：用 SID 而非组名授权，避免中文 / 国产化 Windows 上组名本地化导致失败。
// *S-1-5-32-545 = BUILTIN\Users
function grantWindowsAcl(dir) {
	return new Promise((resolve) => {
		if (process.platform !== 'win32' || winAclTried) return resolve()
		winAclTried = true
		try {
			execFile(
				'icacls',
				[dir, '/grant', '*S-1-5-32-545:(OI)(CI)F', '/T', '/C', '/Q'],
				{windowsHide: true, timeout: 8000},
				() => resolve()
			)
		} catch (e) {
			resolve()
		}
	})
}

async function relaxPermissions(dir, fp, isMachine) {
	if (!isMachine) return
	if (process.platform === 'win32') {
		await grantWindowsAcl(dir)
		return
	}
	try {
		await fsp.chmod(dir, 0o777)
	} catch (e) { /* 目录多半由 deb postinst 建好，失败可忽略 */ }
	try {
		await fsp.chmod(fp, 0o666)
	} catch (e) { /* ignore */ }
}

/**
 * @returns {{data: object|null, error: string|null}}
 *   error 为 null 且 data 为 null 表示「确实没有」；error 非 null 表示「读不出来」。
 *   这两者必须区分：前者是未授权，后者是存储故障，不能当成未授权处理。
 */
async function readFrom(dir) {
	const fp = filePathOf(dir)
	if (!fp) return {data: null, error: null}
	try {
		const raw = await fsp.readFile(fp, 'utf-8')
		const data = JSON.parse(raw)
		if (!data || typeof data !== 'object') {
			return {data: null, error: 'invalid record'}
		}
		return {data, error: null}
	} catch (e) {
		if (e && e.code === 'ENOENT') return {data: null, error: null}
		return {data: null, error: describe(e)}
	}
}

// 先写临时文件再 rename，rename 在同一文件系统上是原子的，避免断电时把文件写成半截。
async function writeTo(dir, record, isMachine) {
	const fp = filePathOf(dir)
	if (!fp) return {ok: false, error: 'path unavailable'}
	const tmp = `${fp}.tmp`
	try {
		await fsp.mkdir(dir, {recursive: true})
		await fsp.writeFile(tmp, JSON.stringify(record), 'utf-8')
		await fsp.rename(tmp, fp)
		await relaxPermissions(dir, fp, isMachine)
		return {ok: true, error: null}
	} catch (e) {
		try {
			await fsp.unlink(tmp)
		} catch (ignore) { /* ignore */ }
		return {ok: false, error: describe(e)}
	}
}

async function clearFrom(dir) {
	const fp = filePathOf(dir)
	if (!fp) return {ok: true, error: null}
	try {
		await fsp.unlink(fp)
		return {ok: true, error: null}
	} catch (e) {
		if (e && e.code === 'ENOENT') return {ok: true, error: null}
		return {ok: false, error: describe(e)}
	}
}

ipcMain.handle('controller.license.read', async () => {
	const [machine, user] = await Promise.all([readFrom(machineDir()), readFrom(userDir())])
	return {
		machine,
		user,
		paths: {machine: filePathOf(machineDir()), user: filePathOf(userDir())}
	}
})

ipcMain.handle('controller.license.write', async (event, record) => {
	const [machine, user] = await Promise.all([
		writeTo(machineDir(), record, true),
		writeTo(userDir(), record, false)
	])
	return {machine, user}
})

// 历史遗留：启用 MAC 绑定的版本把授权写在 bin/nip.db 的
// _id:5（授权码）/ _id:6（设备码）/ _id:7（MAC）。相关代码已删除，
// 但存量机器的文件里仍有残留，且旧版本会用它把已删除的授权"补回来"，
// 因此清除授权时必须一并抹掉。
// 注意：_id:1/2/3 是 http 配置、后端地址、串口号，不能整个删文件。
const LEGACY_IDS = [5, 6, 7]

async function clearLegacyDb() {
	// context.db 由 core/index.js 的 loadConfig() 创建，晚于本模块加载，故此处惰性访问
	if (!context || !context.db) return {ok: true, error: null, skipped: true}
	try {
		for (const id of LEGACY_IDS) {
			await context.db.remove({_id: id})
		}
		// NeDB 的 remove 只是追加删除标记，compaction 后原始行才会真正从文件里消失，
		// 否则用户直接看文件仍会看到旧的授权码，造成"没删掉"的误判。
		try {
			if (typeof context.db.compactDatafileAsync === 'function') {
				await context.db.compactDatafileAsync()
			}
		} catch (e) { /* compaction 失败不影响删除结果 */ }
		return {ok: true, error: null}
	} catch (e) {
		return {ok: false, error: describe(e)}
	}
}

ipcMain.handle('controller.license.clear', async () => {
	const [machine, user, legacy] = await Promise.all([
		clearFrom(machineDir()),
		clearFrom(userDir()),
		clearLegacyDb()
	])
	return {machine, user, legacy}
})

module.exports = {machineDir, userDir}
