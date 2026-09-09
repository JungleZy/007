import {computed, h, ref, watch} from 'vue'
import {AES, enc, mode, pad} from 'crypto-js'
import {message, Modal} from "ant-design-vue";
import {useClipboard} from '@vueuse/core'
import {clearRecord, readRecord, selfHeal, writeRecord} from './licenseStore'
import {matchMachineCode, readHardwareCode} from './machineCode'

const testCode = 'wjkj2025~'
const aseKey = 'wisdom23'
const parse1 = enc.Utf8.parse(aseKey)
const parse2 = {
	mode: mode.ECB,
	padding: pad.Pkcs7
}

const DEFAULT_DAYS = 30
// 到期语义为「累计运行时长」：deadline = 天数 * 86400（秒）
const SECONDS_PER_DAY = 24 * 60 * 60

// 内存计数间隔 10s，每 30 个间隔（5 分钟）才落盘一次。
// 原实现每 10s 写一次 IndexedDB（一天 8640 次），是 LevelDB 长期脏、
// 异常退出即损坏的主因。降频后崩溃最多少记 5 分钟，方向对用户有利。
const TICK_MS = 10 * 1000
const FLUSH_EVERY_TICKS = 30

export default function VerifyLicense() {
	const pc = ["o", "l", "L", "i", "I"]
	const uploadInput = ref(null)
	const licenseCode = ref('')
	// checking | authorized | unauthorized | storage_error
	const licenseState = ref('checking')
	// 兼容原有模板用法
	const isPass = computed(() => licenseState.value === 'authorized')
	const storageError = ref('')
	const usage = ref({used: 0, total: 0})
	// 本机硬件设备码（Electron 且采集到 >=2 个因子时才有值）
	let hardware = null
	const tips = ref({
		title: '未查询到相关授权信息或设备已重置，请进行授权',
		code: '',
		codeTips: '设备码获取中...'
	})
	const {copy, copied} = useClipboard({source: tips.value.code})
	watch(copied, () => {
		if (copied.value) {
			message.success("设备码复制成功!")
		}
	})
	const triggerFileUpload = () => {
		uploadInput.value.click()
	}
	const handleFileUpload = (event) => {
		parseFileContent(event.target.files[0])
	}

	async function parseFileContent(file) {
		if (!file) return;
		const reader = new FileReader();
		reader.onload = function (e) {
			const content = e.target.result;
			licenseCode.value = content.split('\n')
		};
		reader.readAsText(file);
	}

	const judgeString = (c) => {
		const r = Math.random() * 16 | 0
		const v = c === 'x' ? r : (r & 0x3 | 0x8)
		const s = v.toString(16)
		if (pc.includes(s)) {
			return judgeString(c)
		}
		return s
	}
	const generateUUID = () => `xxxx-xxxx-xxxx-xxxx-xxxx-xxxx-xxxx-xxxx`.replace(/[xy]/g, (c) => judgeString(c))

	const setCode = (code) => {
		tips.value.code = code
		tips.value.codeTips = '设备码获取成功'
	}

	const toUnauthorized = (title) => {
		tips.value.title = title || '未查询到相关授权信息或设备已重置，请进行授权'
		licenseState.value = 'unauthorized'
	}

	const toStorageError = (err) => {
		console.error('[license] 授权信息读取失败', err)
		storageError.value = (err && err.message) ? err.message : String(err)
		licenseState.value = 'storage_error'
	}

	// -------- 运行时长心跳 --------
	let tickTimer = null
	let ticksSinceFlush = 0
	let activeLicense = null

	const stopHeartbeat = () => {
		if (tickTimer) {
			clearTimeout(tickTimer)
			tickTimer = null
		}
	}

	const flush = (record) => {
		writeRecord(record).catch((e) => console.warn('[license] 运行时长落盘失败', e))
	}

	const onExpired = () => {
		// 必须先切状态再弹窗。若把 toUnauthorized 放在 onOk 里，用户按 ESC
		// 关掉弹窗时 onOk 不会触发，授权到期后仍能继续使用。
		// 弹窗在这里只承担"告知原因"的职责，关不关都不影响门闸。
		toUnauthorized('本次授权的可用运行时长已用尽，请重新授权')
		Modal.info({
			title: () => '授权已到期',
			content: () => '本次授权的可用运行时长已用尽，请重新授权。',
			okText: () => '知道了'
		})
	}

	// 计数器式累加（+= 固定间隔），不使用墙钟差值。
	// 墙钟差值在休眠唤醒后会一次性暴增，直接制造提前过期；计数器式只会少算，方向对用户有利。
	const startHeartbeat = (record, deadline) => {
		stopHeartbeat()
		activeLicense = record
		ticksSinceFlush = 0
		const step = () => {
			tickTimer = setTimeout(() => {
				record.duration = (Number(record.duration) || 0) + TICK_MS / 1000
				usage.value = {used: record.duration, total: deadline}
				ticksSinceFlush++
				if (record.duration > deadline) {
					flush(record)
					stopHeartbeat()
					onExpired()
					return
				}
				if (ticksSinceFlush >= FLUSH_EVERY_TICKS) {
					ticksSinceFlush = 0
					flush(record)
				}
				step()
			}, TICK_MS)
		}
		step()
	}

	if (typeof window !== 'undefined') {
		const flushNow = () => {
			if (activeLicense) flush(activeLicense)
		}
		window.addEventListener('beforeunload', flushNow)
		document.addEventListener('visibilitychange', () => {
			if (document.visibilityState === 'hidden') flushNow()
		})
	}

	// -------- 校验主流程 --------
	const bootstrap = async () => {
		licenseState.value = 'checking'
		let read
		try {
			read = await readRecord()
		} catch (e) {
			// 「读不到」不等于「没授权」：不销毁任何数据，不重生设备码
			toStorageError(e)
			return
		}

		// 硬件设备码：取不到（浏览器部署 / 采集失败）时回退随机码，不阻塞流程
		hardware = await readHardwareCode()

		const record = read.record

		// 需要重新授权时对外展示的设备码。
		// 已授权机器不动其原有设备码；一旦进入未授权状态，就切换到硬件设备码，
		// 这样用户申请的下一张授权码自然就是硬件绑定的，无需任何人工换发流程。
		const pendingCode = (hardware && hardware.code)
			|| (record && record.machineCode)
			|| generateUUID()

		if (!record || !record.machineCode) {
			// 所有副本都干净地返回空 —— 首次运行。只补设备码。
			setCode(pendingCode)
			try {
				await writeRecord({machineCode: pendingCode, license: '', licenseTime: 0, duration: 0})
			} catch (e) {
				console.warn('[license] 设备码写入失败，本次仅保存在内存中', e)
			}
			toUnauthorized()
			return
		}
		setCode(record.machineCode)

		if (!record.license) {
			selfHeal(record, read.results)
			setCode(pendingCode)
			toUnauthorized()
			return
		}

		// 万能码：与原实现一致，跳过解密与设备码比对，且不启动运行时长心跳
		if (record.license === testCode) {
			licenseState.value = 'authorized'
			selfHeal(record, read.results)
			return
		}

		// 校验不通过时统一切到 pendingCode（硬件设备码优先），
		// 用户据此申请的新授权码即为硬件绑定。
		const fail = (title) => {
			setCode(pendingCode)
			toUnauthorized(title)
		}

		let plain = ''
		try {
			plain = AES.decrypt(record.license, parse1, parse2).toString(enc.Utf8)
		} catch (e) {
			plain = ''
		}
		if (!plain) {
			fail('授权信息已损坏，请重新授权')
			return
		}

		const info = plain.split(':')
		if (info.length !== 2 && info.length !== 3) {
			fail('授权信息已损坏，请重新授权')
			return
		}
		// 与已存设备码（v1 存量）或本机硬件设备码（v2）任一匹配即可
		const bound = matchMachineCode(info[0], record.machineCode)
			|| (hardware && matchMachineCode(info[0], hardware.code))
		if (!bound) {
			fail('授权码与本机设备码不匹配，请重新授权')
			return
		}

		const days = info.length === 2 ? DEFAULT_DAYS : Number(info[2])
		if (!Number.isFinite(days) || days <= 0) {
			fail('授权信息已损坏，请重新授权')
			return
		}
		const deadline = days * SECONDS_PER_DAY

		// 运行时长完整性钳制：累计运行时长不可能超过「自签发以来的墙钟时长」。
		// 防止某个存储副本被写坏成大值而导致提前过期。
		let used = Number(record.duration) || 0
		if (used < 0) used = 0
		const issuedAt = Number(info[1])
		if (Number.isFinite(issuedAt) && issuedAt > 0) {
			const wall = Math.floor((Date.now() - issuedAt) / 1000)
			if (wall >= 0) used = Math.min(used, wall)
		}

		if (used > deadline) {
			fail('本次授权的可用运行时长已用尽，请重新授权')
			return
		}

		const next = {
			machineCode: record.machineCode,
			license: record.license,
			licenseTime: Number.isFinite(issuedAt) ? Date.now() - issuedAt : record.licenseTime,
			duration: used
		}
		usage.value = {used, total: deadline}

		// 校验已通过 —— 先放行。副本回写（含存量首启迁移）失败只告警，
		// 不再因写盘失败而判未授权。
		licenseState.value = 'authorized'
		selfHeal(next, read.results)
		startHeartbeat(next, deadline)
	}

	const retry = () => {
		storageError.value = ''
		bootstrap()
	}

	// -------- 隐藏入口：清除本机授权信息 --------
	// 打包版没有开发者工具，售后与测试需要一个在「已授权」状态下也能用的清除入口，
	// 因此提供两种触发方式：
	//   1. 授权页 / 错误页上连击 Logo 5 次（这两个页面下 Logo 可见）
	//   2. 任意界面按 Ctrl+Alt+Shift+L（已授权时授权页不渲染，只能靠快捷键）
	const purging = ref(false)

	const purgeLicense = () => {
		if (purging.value) return
		Modal.confirm({
			title: () => '确认清除本机授权信息？',
			content: () => '将清除机器级文件、用户级文件、浏览器存储三处副本，以及历史遗留记录（bin/nip.db）。清除后需要重新授权，操作不可撤销。',
			okText: () => '确认清除',
			okType: 'danger',
			cancelText: () => '取消',
			async onOk() {
				purging.value = true
				stopHeartbeat()
				activeLicense = null
				try {
					const outcomes = await clearRecord()
					Modal.success({
						title: () => '授权信息已清除',
						content: () => h('div', outcomes.map((o) => h(
							'div',
							{style: o.ok ? '' : 'color:#d4380d'},
							`${o.source}：${o.ok ? '已清除' : '失败 ' + (o.error || '')}`
						))),
						okText: () => '重新载入',
						onOk() {
							location.reload()
						}
					})
				} catch (e) {
					purging.value = false
					console.error('[license] 清除授权信息失败', e)
					message.error('清除失败：' + ((e && e.message) || e))
				}
			}
		})
	}

	let logoClicks = 0
	let logoTimer = null
	const onLogoClick = () => {
		logoClicks++
		clearTimeout(logoTimer)
		logoTimer = setTimeout(() => {
			logoClicks = 0
		}, 1500)
		if (logoClicks >= 5) {
			logoClicks = 0
			purgeLicense()
		}
	}

	if (typeof window !== 'undefined') {
		window.addEventListener('keydown', (e) => {
			if (e.ctrlKey && e.altKey && e.shiftKey && (e.key === 'l' || e.key === 'L')) {
				e.preventDefault()
				purgeLicense()
			}
		})
	}

	// 硬件设备码环境下，设备码由本机硬件算出，「刷新」只是重新采集，不销毁任何数据。
	// 无硬件指纹时（浏览器部署 / 采集失败）才保留原来的破坏性重置，且必须显式确认。
	// 自动重置（原 handleCode(null) 路径）已彻底移除。
	const resetCode = async () => {
		if (hardware) {
			const fresh = await readHardwareCode(true)
			if (fresh && fresh.code) {
				hardware = fresh
				setCode(fresh.code)
				message.success('设备码已刷新!')
			} else {
				message.error('设备码刷新失败，请重试!')
			}
			return
		}
		Modal.confirm({
			title: () => '确认重置设备码？',
			content: () => '重置后当前授权将立即失效，需要用新的设备码重新申请授权码。',
			okText: () => '确认重置',
			cancelText: () => '取消',
			async onOk() {
				stopHeartbeat()
				activeLicense = null
				try {
					await clearRecord()
					const code = generateUUID()
					setCode(code)
					await writeRecord({machineCode: code, license: '', licenseTime: 0, duration: 0})
					toUnauthorized()
				} catch (e) {
					message.error('设备码重置失败，请重试!')
					console.error('[license] 设备码重置失败', e)
				}
			}
		})
	}

	const generateLicense = () => {
		const nowTime = new Date().getTime()
		const ls = tips.value.code + ':' + nowTime
		licenseCode.value = AES.encrypt(ls, parse1, parse2).toString()
	}

	const submitLicense = async () => {
		licenseCode.value = document.getElementById('licenseCodeDiv').innerText.replace(/\s+/g, '').replace(/<[^>]+>/g, '')
		if (!licenseCode.value) {
			message.error("授权失败,请输入授权码!")
			return
		}
		if (licenseCode.value !== testCode) {
			try {
				const encrypt = AES.decrypt(licenseCode.value, parse1, parse2).toString(enc.Utf8)
				const info = encrypt.split(':')
				if (info.length < 2 || info.length > 3) {
					message.error("授权失败,授权码错误!")
					return
				}
				if (!matchMachineCode(info[0], tips.value.code)) {
					message.error("授权失败,授权码错误!")
					return
				}
			} catch (e) {
				message.error("授权失败,授权码错误!")
				return
			}
		}
		try {
			// 原实现此处调用 localforage.clear()，会连带清空 autoLoginInfo / cool 等业务数据。
			// 改为只覆盖授权记录本身，并同时写入三个副本。
			await writeRecord({
				machineCode: tips.value.code,
				license: licenseCode.value,
				licenseTime: 0,
				duration: 0
			})
			message.success("授权成功,3秒后自动刷新授权信息!")
			setTimeout(() => {
				location.reload()
			}, 3000)
		} catch (e) {
			console.error('[license] 授权信息写入失败', e)
			message.error("授权失败!")
		}
	}

	bootstrap()

	return {
		licenseCode,
		isPass,
		licenseState,
		storageError,
		usage,
		tips,
		uploadInput,
		copy,
		retry,
		onLogoClick,
		purgeLicense,
		resetCode,
		submitLicense,
		generateLicense,
		triggerFileUpload,
		handleFileUpload
	}
}
