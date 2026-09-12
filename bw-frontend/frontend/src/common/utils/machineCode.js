import {ipcRenderer, ipcApi} from '../../electron/index'

/**
 * 设备码的生成与比对。
 *
 * 外形与原随机 UUID 完全一致：8 组 4 位十六进制（32 个 hex 字符 + 7 个连字符），
 * 因此发号器零改动。内容分为两代：
 *   v1：Math.random() 随机 UUID（存量机器 / 浏览器部署）
 *   v2：4 槽硬件因子（见 electron/controller/fingerprint.js）
 *
 * 比对策略（matchMachineCode）：
 *   1. 先做精确匹配 —— 覆盖 v1 存量，以及硬件完全没变的 v2
 *   2. 再做槽位阈值匹配 —— 覆盖 v2 的硬件漂移（换硬盘 / 重装系统等）
 *
 * v1 随机码之间、v1 与 v2 之间，单槽碰撞概率为 2^-32，阈值匹配不会产生误判，
 * 因此两代可以共用同一个比对函数。
 */

const SENTINEL = '00000000'
const SLOT_LEN = 8
const SLOT_COUNT = 4

export function normalizeCode(code) {
	return String(code == null ? '' : code).replace(/-/g, '').trim().toLowerCase()
}

export function formatCode(hex32) {
	const n = normalizeCode(hex32)
	return n.length === SLOT_LEN * SLOT_COUNT ? n.match(/.{1,4}/g).join('-') : n
}

function slotsOf(code) {
	const n = normalizeCode(code)
	if (n.length !== SLOT_LEN * SLOT_COUNT) return null
	const out = []
	for (let i = 0; i < SLOT_COUNT; i++) {
		out.push(n.slice(i * SLOT_LEN, (i + 1) * SLOT_LEN))
	}
	return out
}

/**
 * 需要命中的槽位数。
 * 可比对槽位 >= 3 时要求 2 个（换硬盘、重装系统仍可用）；
 * 可比对槽位 <= 2 时降为 1 个 —— 否则 Linux 非 root 场景（通常只有
 * machine-id + 磁盘序列号两个因子）会失去全部容差，重装系统即失效。
 */
function required(comparable) {
	return comparable >= 3 ? 2 : 1
}

/**
 * @param licensedCode 授权码里绑定的设备码（AES 明文的第 1 段）
 * @param currentCode  本机当前设备码
 */
export function matchMachineCode(licensedCode, currentCode) {
	const a = normalizeCode(licensedCode)
	const b = normalizeCode(currentCode)
	if (!a || !b) return false
	if (a === b) return true

	const sa = slotsOf(a)
	const sb = slotsOf(b)
	if (!sa || !sb) return false

	let comparable = 0
	let hit = 0
	for (let i = 0; i < SLOT_COUNT; i++) {
		if (sa[i] === SENTINEL || sb[i] === SENTINEL) continue
		comparable++
		if (sa[i] === sb[i]) hit++
	}
	if (!comparable) return false
	return hit >= required(comparable)
}

// Web 没有硬件接口；Electron 读取失败必须显式交给调用者处理。
export const isDesktop = !!ipcRenderer.isEE || window.location.protocol === 'file:'

export const identityScope = isDesktop
	? '桌面端离线授权使用本机授权副本，可从机器级、用户级文件或浏览器存储恢复；后端登录凭证与离线授权相互独立。'
	: 'Web 设备标识只在当前浏览器配置与站点 origin（协议、主机、端口）内保存，不是物理机器标识。清除站点数据、换浏览器配置或更换 origin 后可能需要重新登录和授权，不能自动跨站点恢复。'

export async function readHardwareCode(force) {
	if (!isDesktop) return null
	if (!ipcRenderer.ipc) throw new Error('桌面硬件接口不可用，请重启软件或联系管理员；不会改用浏览器随机标识')
	const result = await ipcRenderer.ipc.invoke(ipcApi.ipcApiRoute.licenseFingerprint, {force: !!force})
	if (!result || !result.code) {
		throw new Error('未能取得足够的本机硬件因子，请检查系统权限并联系管理员；不会改用浏览器随机标识')
	}
	return result
}

// 仅在登录时调用；请求仍使用服务器签发会话中的 deviceId。
export async function readLoginDeviceId() {
	if (isDesktop) return (await readHardwareCode()).code
	try {
		const storage = window.localStorage
		const key = 'loginDeviceId'
		let id = storage.getItem(key)
		if (!id) {
			// 旧登录保存的指纹只用于首轮兼容迁移，不再重新采集。
			id = storage.getItem('deviceId')
			if (!id) {
				const bytes = window.crypto.getRandomValues(new Uint8Array(16))
				id = Array.from(bytes, (value) => value.toString(16).padStart(2, '0')).join('')
			}
		}
		storage.setItem(key, id)
		if (storage.getItem(key) !== id) throw new Error('设备标识写入未持久化')
		return id
	} catch (error) {
		console.error('[login] 设备标识存储不可用', error)
		throw new Error('无法读取或保存当前站点的登录设备标识，请检查浏览器存储权限后重试；未清除旧记录，也未使用临时标识登录')
	}
}
