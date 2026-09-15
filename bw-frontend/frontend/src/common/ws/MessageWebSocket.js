import {onUnmounted, ref} from 'vue'
import {traffic} from '../../config/pinia/index.js'
import WebSerial from '../utils/WebSerial.js'

const trafficStore = traffic.useTrafficStore()
import {publishTrafficFrame} from '../mixin/useTraffic'
const webSerial = new WebSerial(9600)
let lifecycleGeneration = 0
let active = false
let beforeUnloadInstalled = false

const removeBeforeUnload = () => {
	if (beforeUnloadInstalled && typeof window !== 'undefined') {
		window.removeEventListener('beforeunload', handleBeforeUnload)
		beforeUnloadInstalled = false
	}
}

async function handleBeforeUnload() {
	await shutdownMessageWebSocket()
}

export async function shutdownMessageWebSocket() {
	active = false
	lifecycleGeneration++
	removeBeforeUnload()
	try {
		await webSerial.close()
	} catch (e) {
		console.error('Serial shutdown failed', e)
	}
}

// 键数据只有一条通路：Web Serial。浏览器与 Electron 桌面壳同走 navigator.serial
// （Electron 是 Chromium 内核；主进程 electron/serial/index.js 注册了
// select-serial-port/权限处理器，requestPort 由主进程按已存口名自动应答）。
// 历史上的 ws://localhost:18765/echo 桥接是 TrafficService 时代的遗留，
// 该服务退役后仓内无人服务此端口，已随本次清理删除。
export default function messageWebSocket(type, reset) {
	active = true
	lifecycleGeneration++
	const generation = lifecycleGeneration
	if (!beforeUnloadInstalled && typeof window !== 'undefined') {
		window.addEventListener('beforeunload', handleBeforeUnload)
		beforeUnloadInstalled = true
	}
	onUnmounted(() => {
		shutdownMessageWebSocket()
	})
	const num = ref(0)
	const connect = async (reset) => {
		await webSerial.close()
		if (!active || generation !== lifecycleGeneration) return
		webSerial.init((res) => {
			if (!active || generation !== lifecycleGeneration) return
			try {
				if (res.code === 0 || res.code === 1 || res.code === -1) {
					trafficStore.$patch({linkStatus: false, devStatus: false})
					if (res.code === -1 && num.value < 4) num.value++
				} else if (res.code === 10) {
					trafficStore.$patch({linkStatus: true, devStatus: true})
				} else {
					num.value = 0
					publishTrafficFrame(res.data)
				}
			} catch (e) {
				console.error('Serial frame delivery failed', e)
			}
		}, reset)
	}
	const localSerial = localStorage.getItem('serial')
	if (type === 'reset' || localSerial !== null) connect(reset).catch(error => {
		trafficStore.$patch({linkStatus: false, devStatus: false})
		console.error('Serial reconnect failed', error)
	})
}
