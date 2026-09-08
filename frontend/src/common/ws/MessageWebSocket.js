import {onMounted, onUnmounted, ref, watch} from 'vue'
import {v1} from 'uuid-umd'
import {traffic} from '../../config/pinia/index.js'
import {useDocumentVisibility} from '@vueuse/core'
import {ipcRenderer, ipcApi} from '../../electron/index'
import WebSerial from '../utils/WebSerial.js'
import {message} from 'ant-design-vue'

const trafficStore = traffic.useTrafficStore()
const trafficDataStore = traffic.useTrafficDataStore()
let webSerial
webSerial = new WebSerial(9600)

let visibility = useDocumentVisibility()
const msgCode = {
	all: 0,
	volume: 1,
	fre: 2
}
watch(useDocumentVisibility(), () => {
	visibility = useDocumentVisibility()
})
let ws
export default function messageWebSocket(type, reset) {
	const wsFlag = ref(true)
	const volume = ref(0)
	const fre = ref(1200)
	onMounted(() => {
		window.addEventListener('beforeunload', (e) => {
			if (!ipcRenderer.isEE) {
				webSerial.close()
			}
		})
	})
	onUnmounted(() => {
		wsFlag.value = false
		if (ws) {
			ws.close()
		}
		ws = null
	})
	const num = ref(0)
	const connect = (reset) => {
		webSerial.init((res) => {
			// console.log(res)
			// if(res.code === -1){
			// 	message.error('串口连接失败！')
			// }
			if (res.code === 0 || res.code === 1 || res.code === -1) {
				trafficStore.$patch({
					linkStatus: false,
					devStatus: false
				})
				if (res.code === -1) {
					if (num.value < 4) {
						num.value++
					}
					// webSerial.close().then()
				}
			} else if (res.code === 10) {
				trafficStore.$patch({
					linkStatus: true,
					devStatus: true
				})
			} else {
				if (visibility.value === 'visible') {
					// console.log('拍发消息',res.data)
					num.value = 0
					trafficDataStore.$patch({
						message: res.data
					})
				}
			}
		}, reset)
	}
	//有连接记录直接连接
	const localSerial = localStorage.getItem('serial')
	if (type === 'reset' || localSerial !== null) {
		connect(reset)
	}
	const ws_connect = async () => {
		ws = new WebSocket('ws://localhost:18765/echo?username=' + v1())
		ws.onopen = (e) => {
			trafficStore.$patch({
				linkStatus: true
			})
		}
		ws.onerror = (e) => {
		}
		ws.onclose = (e) => {
			trafficStore.$patch({
				linkStatus: false,
				devStatus: false
			})
			if (wsFlag.value) {
				reconnect()
			}
		}
		ws.onmessage = (e) => {
			handleMsg(JSON.parse(e.data).data)
		}
	}
	const reconnect = () => {
		setTimeout(() => {
			ws_connect().then()
		}, 3000)
	}
	const handleMsg = (data) => {
		if (data.status !== undefined) {
			if (data.status) {
				sendMag({type: msgCode.all, fre: fre.value, volume: volume.value}) // 初始化报训终端软件音频，
			}
			trafficStore.$patch({
				devStatus: data.status
			})
		} else {
			trafficDataStore.$patch({
				message: data
			})
		}
	}
	const sendMag = (msg) => {
		ws.send(JSON.stringify(msg))
	}
}
