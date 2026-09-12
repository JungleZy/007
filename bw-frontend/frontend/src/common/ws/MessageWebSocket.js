import {onMounted, onUnmounted, ref} from 'vue'
import {v1} from 'uuid-umd'
import {traffic} from '../../config/pinia/index.js'
import {ipcRenderer} from '../../electron/index'
import WebSerial from '../utils/WebSerial.js'

const trafficStore = traffic.useTrafficStore()
import {publishTrafficFrame} from '../mixin/useTraffic'
const webSerial = new WebSerial(9600)
const msgCode = {all: 0, volume: 1, fre: 2}
let ws = null
let reconnectTimer = null
let reconnectAttempt = 0
let lifecycleGeneration = 0
let active = false
let beforeUnloadInstalled = false


const clearReconnectTimer = () => {
	if (reconnectTimer !== null) {
		clearTimeout(reconnectTimer)
		reconnectTimer = null
	}
}

const closeSocket = () => {
	if (!ws) return
	const socket = ws
	ws = null
	// Detach callbacks before close: browsers may deliver a late close event.
	socket.onopen = null
	socket.onerror = null
	socket.onclose = null
	socket.onmessage = null
	if (socket.readyState === WebSocket.OPEN || socket.readyState === WebSocket.CONNECTING) {
		socket.close()
	}
}

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
	clearReconnectTimer()
	reconnectAttempt = 0
	closeSocket()
	removeBeforeUnload()
	if (!ipcRenderer.isEE) {
		try {
			await webSerial.close()
		} catch (e) {
      console.error('Serial shutdown failed', e)
		}
	}
}

export default function messageWebSocket(type, reset) {
	active = true
	lifecycleGeneration++
	const generation = lifecycleGeneration
  clearReconnectTimer()
  closeSocket()
	const wsFlag = ref(true)
	const volume = ref(0)
	const fre = ref(1200)
	if (!beforeUnloadInstalled && typeof window !== 'undefined') {
		window.addEventListener('beforeunload', handleBeforeUnload)
		beforeUnloadInstalled = true
	}
	onMounted(() => {})
	onUnmounted(() => {
		wsFlag.value = false
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
  if (!ipcRenderer.isEE && (type === 'reset' || localSerial !== null)) connect(reset).catch(error => {
    trafficStore.$patch({linkStatus: false, devStatus: false})
    console.error('Serial reconnect failed', error)
  })

	const ws_connect = async () => {
		if (!active || generation !== lifecycleGeneration) return
		clearReconnectTimer()
		const socket = new WebSocket('ws://localhost:18765/echo?username=' + v1())
		ws = socket
		socket.onopen = () => {
			if (socket !== ws || !active || generation !== lifecycleGeneration) return
			reconnectAttempt = 0
			trafficStore.$patch({linkStatus: true})
		}
		socket.onerror = () => {}
		socket.onclose = () => {
			if (socket !== ws) return
			ws = null
			trafficStore.$patch({linkStatus: false, devStatus: false})
			if (active && generation === lifecycleGeneration) reconnect()
		}
		socket.onmessage = (e) => {
			if (socket !== ws || !active || generation !== lifecycleGeneration) return
			try {
				const parsed = JSON.parse(e.data)
				if (parsed && parsed.data !== undefined) handleMsg(parsed.data)
			} catch (err) {
        console.error('Malformed bridge frame', err)
			}
		}
	}
	const reconnect = () => {
		if (!active || generation !== lifecycleGeneration || reconnectTimer !== null) return
		const delay = Math.min(30000, 1000 * (2 ** Math.min(reconnectAttempt++, 5)))
		const jitter = Math.floor(Math.random() * Math.max(250, delay * 0.2))
		reconnectTimer = setTimeout(() => {
			reconnectTimer = null
			ws_connect().catch(() => reconnect())
		}, Math.min(30000, delay + jitter))
	}
	const handleMsg = (data) => {
		try {
			if (data.status !== undefined) {
				if (data.status) sendMag({type: msgCode.all, fre: fre.value, volume: volume.value})
				trafficStore.$patch({devStatus: data.status})
      } else publishTrafficFrame(data)
    } catch (e) { console.error('Bridge frame delivery failed', e) }
	}
	const sendMag = (msg) => {
		if (ws && ws.readyState === WebSocket.OPEN) ws.send(JSON.stringify(msg))
	}
  if (ipcRenderer.isEE) ws_connect()
}
