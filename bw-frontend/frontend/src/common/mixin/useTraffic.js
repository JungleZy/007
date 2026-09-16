import {ref, onUnmounted} from 'vue'
import {traffic} from '../../config/pinia/index.js'
import ElectronMorse from '../utils/ElectronMorse'
import {PubSub} from '../utils/PubSub'
import {createHandKeyDecoder} from '../utils/handKeyDecoder.js'


const trafficStore = traffic.useTrafficStore()
const trafficAudioStore = traffic.useTrafficAudioStore()
const frameTopic = 'traffic:frame'

export function publishTrafficFrame(data) {
  const receivedAt = data.receivedAt ?? performance.now()
  const frame = Object.freeze({...data,
    d: Array.isArray(data.d) ? Object.freeze([...data.d]) : data.d,
    receivedAt, timeSource: data.timeSource ?? (typeof data.d === 'number' ? 'device-reported' : 'js-receive')
  })
  PubSub.publishSync(frameTopic, frame)
}

export function keyEvents() {
  const handlers = new Set()
  onUnmounted(() => handlers.clear())
  return {
    onKey(handler) { handlers.add(handler); return () => handlers.delete(handler) },
    emit(event) { for (const handler of handlers) handler(Object.freeze(event)) }
  }
}

export function handKeyEvents(enabled, threshold, lineLimit, report = () => {}) {
  const events = keyEvents()
  const handKeyDown = ref(false)
  const decoder = createHandKeyDecoder({
    enabled, threshold, lineLimit, report,
    emit: event => events.emit(event),
    onPressChange: pressed => { handKeyDown.value = pressed }
  })
  onUnmounted(decoder.reset)
  return {handKeyDown, onKey: events.onKey, receive: decoder.receive, reset: decoder.reset}
}

export function electronicKeyEvents() {
  const events = keyEvents()
  const connection = useTraffic(false, data => {
    if (data.t !== 1 || !Array.isArray(data.d)) return
    for (const code of data.d) events.emit({code: String(code), receivedAt: data.receivedAt, timeSource: data.timeSource})
  })
  return {...connection, onKey: events.onKey}
}

export default function useTraffic(openVoice = false, handleHandKeysData = false) {
  const wsOnline = ref(trafficStore.linkStatus)
  const devOnline = ref(trafficStore.devStatus)
  const {changeCriterion, clear, changePattern, voiceCode} = ElectronMorse()
  const unsubscribeStatus = trafficStore.$subscribe((mutation, state) => {
    wsOnline.value = state.linkStatus
    devOnline.value = state.devStatus
    if (handleHandKeysData && openVoice && devOnline.value) trafficAudioStore.$patch({audioStatus: true})
  }, {flush: 'sync'})
  let token
  if (handleHandKeysData) {
    if (openVoice && wsOnline.value && devOnline.value) trafficAudioStore.$patch({audioStatus: true})
    token = PubSub.subscribe(frameTopic, handleHandKeysData)
  }
  onUnmounted(() => {
    unsubscribeStatus()
    if (token) PubSub.unsubscribe(token)
    clear()
    if (openVoice && trafficAudioStore.audioStatus) trafficAudioStore.$patch({audioStatus: false})
  })
  return {wsOnline, devOnline, changeCriterion, changePattern, voiceCode}
}
