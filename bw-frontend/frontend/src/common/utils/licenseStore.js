import {ipcRenderer, ipcApi} from '../../electron/index'

/**
 * 授权信息的多副本存储。
 *
 * 三个副本：
 *  - machine：Win `%ProgramData%\NipTrafficSystem\license.json`
 *             Linux `/var/lib/nip-traffic-system/license.json`  —— 权威，跨 OS 账户 / 跨提权
 *  - user   ：`app.getPath('userData')/license.json`            —— machine 不可写时兜底
 *  - indexed：IndexedDB `WisdomJ233` 的 '1' / '2'               —— 缓存；浏览器部署时的唯一副本
 *
 * 读：三处并读，任一命中即用，随后回写补齐其余副本（自愈）。
 *    升级后首次启动时，存量机器只有 indexed 有数据，自愈会把它钉到 machine / user 上，
 *    这正是「存量首启迁移」。
 * 写：并发写三处，≥1 成功即算成功。
 *
 * 关键约定：「读不出来」与「确实没有」必须区分。
 *   - 全部副本都读不出来 -> 抛错 -> 上层显示 storage_error（不销毁任何数据）
 *   - 全部副本都干净地返回空 -> 返回 null -> 上层判未授权
 */

const KEY_LICENSE = '1'
const KEY_MACHINE = '2'
const STORAGE_TIMEOUT_MS = 8000
const STORAGE_RETRY = 3

const isEE = () => !!(ipcRenderer && ipcRenderer.isEE && ipcRenderer.ipc)

const withTimeout = (promise, ms, label) => new Promise((resolve, reject) => {
	const timer = setTimeout(() => reject(new Error(`storage timeout: ${label}`)), ms)
	Promise.resolve(promise).then(
		(v) => {
			clearTimeout(timer)
			resolve(v)
		},
		(e) => {
			clearTimeout(timer)
			reject(e)
		}
	)
})

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

const localforageRef = () => {
	if (typeof localforage === 'undefined' || !localforage) {
		throw new Error('localforage 未加载')
	}
	return localforage
}

const readIdbKey = async (key) => {
	let lastErr
	for (let i = 0; i < STORAGE_RETRY; i++) {
		try {
			return await withTimeout(localforageRef().getItem(key), STORAGE_TIMEOUT_MS, `getItem(${key})`)
		} catch (e) {
			lastErr = e
			if (i < STORAGE_RETRY - 1) await sleep(200 * Math.pow(2, i))
		}
	}
	throw lastErr
}

const describe = (e) => (e && e.message) ? e.message : String(e)

// -------- indexed 副本（沿用原有的两键结构，保证与旧版本互相兼容） --------

const readIndexed = async () => {
	try {
		const machine = await readIdbKey(KEY_MACHINE)
		const license = await readIdbKey(KEY_LICENSE)
		if (!machine || !machine.machineCode) {
			return {source: '浏览器存储', data: null, error: null}
		}
		return {
			source: '浏览器存储',
			data: {
				machineCode: machine.machineCode,
				license: (license && license.license) || '',
				licenseTime: Number(license && license.licenseTime) || 0,
				duration: Number(license && license.duration) || 0,
				updatedAt: Number(license && license.updatedAt) || 0
			},
			error: null
		}
	} catch (e) {
		return {source: '浏览器存储', data: null, error: describe(e)}
	}
}

const writeIndexed = async (record) => {
	try {
		const lf = localforageRef()
		await withTimeout(lf.setItem(KEY_MACHINE, {machineCode: record.machineCode}), STORAGE_TIMEOUT_MS, 'setItem(2)')
		await withTimeout(lf.setItem(KEY_LICENSE, {
			license: record.license,
			licenseTime: record.licenseTime,
			duration: record.duration,
			updatedAt: record.updatedAt
		}), STORAGE_TIMEOUT_MS, 'setItem(1)')
		return {source: '浏览器存储', ok: true, error: null}
	} catch (e) {
		return {source: '浏览器存储', ok: false, error: describe(e)}
	}
}

const clearIndexed = async () => {
	try {
		const lf = localforageRef()
		await withTimeout(lf.removeItem(KEY_LICENSE), STORAGE_TIMEOUT_MS, 'removeItem(1)')
		await withTimeout(lf.removeItem(KEY_MACHINE), STORAGE_TIMEOUT_MS, 'removeItem(2)')
		return {source: '浏览器存储', ok: true, error: null}
	} catch (e) {
		return {source: '浏览器存储', ok: false, error: describe(e)}
	}
}

// -------- machine / user 副本（经主进程） --------

const invoke = (route, args) => withTimeout(
	ipcRenderer.ipc.invoke(route, args),
	STORAGE_TIMEOUT_MS,
	route
)

const readFiles = async () => {
	if (!isEE()) return []
	try {
		const r = await invoke(ipcApi.ipcApiRoute.licenseRead)
		return [
			{source: '机器级文件', data: r.machine.data, error: r.machine.error},
			{source: '用户级文件', data: r.user.data, error: r.user.error}
		]
	} catch (e) {
		const err = describe(e)
		return [
			{source: '机器级文件', data: null, error: err},
			{source: '用户级文件', data: null, error: err}
		]
	}
}

const writeFiles = async (record) => {
	if (!isEE()) return []
	try {
		const r = await invoke(ipcApi.ipcApiRoute.licenseWrite, record)
		return [
			{source: '机器级文件', ok: r.machine.ok, error: r.machine.error},
			{source: '用户级文件', ok: r.user.ok, error: r.user.error}
		]
	} catch (e) {
		const err = describe(e)
		return [
			{source: '机器级文件', ok: false, error: err},
			{source: '用户级文件', ok: false, error: err}
		]
	}
}

const clearFiles = async () => {
	if (!isEE()) return []
	try {
		const r = await invoke(ipcApi.ipcApiRoute.licenseClear)
		const out = [
			{source: '机器级文件', ok: r.machine.ok, error: r.machine.error},
			{source: '用户级文件', ok: r.user.ok, error: r.user.error}
		]
		if (r.legacy) {
			out.push({source: '历史遗留(nip.db)', ok: r.legacy.ok, error: r.legacy.error})
		}
		return out
	} catch (e) {
		const err = describe(e)
		return [
			{source: '机器级文件', ok: false, error: err},
			{source: '用户级文件', ok: false, error: err}
		]
	}
}

// -------- 对外接口 --------

/**
 * @returns {Promise<{record: object|null, results: Array}>}
 * @throws  所有副本均不可读时抛出（上层据此进入 storage_error，绝不当作未授权）
 */
export async function readRecord() {
	const results = [...(await readFiles()), await readIndexed()]
	const withData = results.filter((r) => r.data && r.data.machineCode)

	if (!withData.length) {
		const failed = results.filter((r) => r.error)
		if (failed.length && failed.length === results.length) {
			const err = new Error(failed.map((f) => `${f.source}: ${f.error}`).join('; '))
			err.results = results
			throw err
		}
		return {record: null, results}
	}

	// sort 是稳定的，results 已按 machine -> user -> indexed 排列，
	// 因此 updatedAt 相同时优先级为 machine > user > indexed。
	const winner = withData.slice().sort((a, b) => (b.data.updatedAt || 0) - (a.data.updatedAt || 0))[0].data

	// 运行时长取各副本最大值，但只在「同一份授权」内合并，避免跨授权污染。
	let duration = 0
	for (const r of withData) {
		if (r.data.machineCode === winner.machineCode && r.data.license === winner.license) {
			duration = Math.max(duration, Number(r.data.duration) || 0)
		}
	}

	return {
		record: {
			machineCode: winner.machineCode,
			license: winner.license || '',
			licenseTime: Number(winner.licenseTime) || 0,
			duration,
			updatedAt: Number(winner.updatedAt) || 0
		},
		results
	}
}

/**
 * 并发写三处，≥1 成功即成功。
 * @throws 全部失败时抛出
 */
export async function writeRecord(record) {
	const payload = {
		v: 1,
		machineCode: record.machineCode,
		license: record.license || '',
		licenseTime: Number(record.licenseTime) || 0,
		duration: Number(record.duration) || 0,
		updatedAt: Date.now()
	}
	const outcomes = [...(await writeFiles(payload)), await writeIndexed(payload)]
	if (!outcomes.some((o) => o.ok)) {
		const err = new Error(outcomes.map((o) => `${o.source}: ${o.error}`).join('; '))
		err.outcomes = outcomes
		throw err
	}
	return {record: payload, outcomes}
}

export async function clearRecord() {
	const outcomes = [...(await clearFiles()), await clearIndexed()]
	if (!outcomes.some((o) => o.ok)) {
		const err = new Error(outcomes.map((o) => `${o.source}: ${o.error}`).join('; '))
		err.outcomes = outcomes
		throw err
	}
	return outcomes
}

/**
 * 把权威记录回写到与之不一致的副本上（含存量首启迁移）。
 * 只读路径的补偿动作，失败不影响授权判定。
 */
export function selfHeal(record, results) {
	const stale = results.some((r) => {
		if (r.error) return true
		const d = r.data
		if (!d) return true
		return d.machineCode !== record.machineCode
			|| d.license !== record.license
			|| (Number(d.duration) || 0) !== record.duration
	})
	if (!stale) return Promise.resolve(null)
	return writeRecord(record).catch(() => null)
}
