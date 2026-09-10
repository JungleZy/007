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

/**
 * 读取本机硬件设备码。浏览器部署或采集失败时返回 null，
 * 上层据此回退到 v1 随机码，绝不因取不到指纹而阻塞授权。
 */
export async function readHardwareCode(force) {
	if (!(ipcRenderer && ipcRenderer.isEE && ipcRenderer.ipc)) return null
	try {
		const r = await ipcRenderer.ipc.invoke(ipcApi.ipcApiRoute.licenseFingerprint, {force: !!force})
		if (!r || !r.code) return null
		return r
	} catch (e) {
		console.warn('[license] 硬件设备码读取失败，回退随机设备码', e)
		return null
	}
}
