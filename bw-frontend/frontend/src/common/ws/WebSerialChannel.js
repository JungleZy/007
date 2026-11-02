import {ref} from 'vue'
import {traffic} from '../../config/pinia/index.js'
import WebSerial from '../utils/WebSerial.js'

const trafficStore = traffic.useTrafficStore()
import {publishTrafficFrame} from '../mixin/useTraffic'
const webSerial = new WebSerial(9600)
let lifecycleGeneration = 0
let active = false
let beforeUnloadInstalled = false
let keysimLoadTask = null
let selectionGeneration = 0
export const serialSource = ref('')
export const serialConnecting = ref(false)
export const serialNotice = ref('')
const savedSource = () => sessionStorage.getItem('serialSource')

function nativeSerial() {
	const simulator = window.__keysimSerial
	if (!simulator) return navigator.serial
	if ('nativeSerial' in simulator) return simulator.nativeSerial
	// 注入器可能覆盖 navigator 自身的属性；用 Navigator 的原生 getter
	// 获取真实接口，并保留 navigator 作为接收者，避免 Illegal invocation。
	const serial = Reflect.get(Object.getPrototypeOf(navigator), 'serial', navigator)
	return serial === navigator.serial ? null : serial
}

// 只有模拟器实际注入过的标签页才恢复虚拟串口；serialChrome 仍只是重连偏好。
const keysimOrigin = () => typeof sessionStorage === 'undefined' ? null : sessionStorage.getItem('keysimSerialOrigin')

async function restoreKeysimSerial() {
	if (typeof window === 'undefined' || window.__keysimSerial || !keysimOrigin()) return
	if (!keysimLoadTask) {
		keysimLoadTask = new Promise((resolve, reject) => {
			const script = document.createElement('script')
			script.src = `${keysimOrigin()}/inject.js`
			script.onload = () => { script.remove(); resolve() }
			script.onerror = () => { script.remove(); reject(new Error('虚拟串口恢复失败')) }
			document.head.appendChild(script)
		}).finally(() => { keysimLoadTask = null })
	}
	await keysimLoadTask
}

function handleKeysimReady() {
  if (!active || keysimLoadTask || serialConnecting.value || savedSource() === 'real' || !window.__keysimSerial) return
	if (webSerial.port === window.__keysimSerial.port && webSerial.reader) return
	webSerialChannel('reset')
}

const removeBeforeUnload = () => {
	if (beforeUnloadInstalled && typeof window !== 'undefined') {
		window.removeEventListener('beforeunload', handleBeforeUnload)
		window.removeEventListener('keysim-serial-ready', handleKeysimReady)
		beforeUnloadInstalled = false
	}
}

async function handleBeforeUnload() {
	await shutdownWebSerialChannel()
}

export async function shutdownWebSerialChannel() {
  active = false
  lifecycleGeneration++
  selectionGeneration++
  serialConnecting.value = false
	removeBeforeUnload()
	try {
    await webSerial.close()
    trafficStore.$patch({linkStatus: false, devStatus: false})
	} catch (e) {
		console.error('Serial shutdown failed', e)
	}
}

// 键数据只有一条通路：Web Serial。浏览器与 Electron 桌面壳同走 navigator.serial
// （Electron 是 Chromium 内核；主进程 electron/serial/index.js 注册了
// select-serial-port/权限处理器，requestPort 由主进程按已存口名自动应答）。
// 历史上的 ws://localhost:18765/echo 桥接是 TrafficService 时代的遗留，
// 该服务退役后仓内无人服务此端口，已随本次清理删除。
/**
 * 打开/重置串口通道。
 *
 * 注意：本函数**不再**自行 onUnmounted 注册清理 —— 它会被放在 await 之后调用
 * （NipSerial.vue 的 linkPort 走 ipc.invoke 取结果后才重置通道），
 * 那时已无 active component instance，Vue 会丢掉钩子、清理永不执行（串口不关）。
 * 清理改由调用方在 setup 同步期注册：`onUnmounted(() => shutdownWebSerialChannel())`。
 */
export default function webSerialChannel(type, reset, source) {
  active = true
  const generation = lifecycleGeneration
  const selection = ++selectionGeneration
	if (!beforeUnloadInstalled && typeof window !== 'undefined') {
		window.addEventListener('beforeunload', handleBeforeUnload)
		window.addEventListener('keysim-serial-ready', handleKeysimReady)
		beforeUnloadInstalled = true
	}
  const connect = async (reset) => {
    serialConnecting.value = true
    serialNotice.value = ''
    const selectedSource = source || (reset === 'resetLine' ? 'real' : savedSource())
      || (keysimOrigin() || window.__keysimSerial ? 'virtual' : 'real')
    if (selectedSource === 'virtual') await restoreKeysimSerial()
    if (!active || generation !== lifecycleGeneration || selection !== selectionGeneration) return
    const simulator = window.__keysimSerial
    if (selectedSource === 'virtual' && !simulator) throw new Error('虚拟设备未就绪，请先启动并连接模拟器')
    // 虚拟设备直接使用注入器公开的 port，不依赖其覆盖后的 navigator.serial。
    const serial = selectedSource === 'virtual'
      ? {getPorts: async () => [simulator.port]}
      : nativeSerial()
		// 保持点击到 requestPort 的同步调用链；选定设备后由 init 关闭旧串口。
		await webSerial.init((res) => {
			if (!active || generation !== lifecycleGeneration) return
			try {
        if (res.code === 0 || res.code === 1 || res.code === -1) {
          trafficStore.$patch({linkStatus: false, devStatus: false})
        } else if (res.code === 10) {
          trafficStore.$patch({linkStatus: true, devStatus: true})
          if (!res.error) {
            serialSource.value = selectedSource
            sessionStorage.setItem('serialSource', selectedSource)
          }
        } else {
          publishTrafficFrame(res.data)
        }
        if (res.error && res.error.name !== 'NotFoundError') serialNotice.value = res.error.message
			} catch (e) {
				console.error('Serial frame delivery failed', e)
			}
    }, selectedSource === 'virtual' ? undefined : reset,
    () => active && generation === lifecycleGeneration && selection === selectionGeneration, serial || null)
	}
	const localSerial = localStorage.getItem('serial')
	if (type === 'reset' || localSerial !== null || localStorage.getItem('serialChrome') === 'true'
    || savedSource() || keysimOrigin() || (typeof window !== 'undefined' && window.__keysimSerial)) return connect(reset).catch(error => {
    if (!active || generation !== lifecycleGeneration || selection !== selectionGeneration) return
    serialNotice.value = error.message
    console.error('Serial reconnect failed', error)
  }).finally(() => {
    if (selection === selectionGeneration) serialConnecting.value = false
  })
}
