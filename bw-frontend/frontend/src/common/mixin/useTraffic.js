import {ref, onUnmounted} from 'vue'
import {traffic} from '../../config/pinia/index.js'
import ElectronMorse from '../utils/ElectronMorse'
import {PubSub} from '../utils/PubSub'

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
  let down = null, up = null, clock = null
  let downReceivedAt = null
  const reset = () => { down = null; up = null; handKeyDown.value = false }
  onUnmounted(reset)
  const receive = data => {
    if (data.t !== 0 || ![0, 1].includes(data.k)) return
    if (!enabled()) { reset(); return }
    const time = Number(data.d)
    if (!Number.isFinite(time)) return
    if (clock !== data.timeSource) { reset(); clock = data.timeSource }
    if (data.k === 0) {
      if (down !== null) {
        // Ignore repeated down edges; recover a missing up on the next fresh press.
        if (time - down <= Math.max(2000, lineLimit() * 8)) return
        console.warn('Hand key missing release; recovering on fresh press')
        reset()
      }
      handKeyDown.value = true
      down = time
      downReceivedAt = data.receivedAt
      if (up !== null && time >= up) {
        const gapTime = Object.freeze([up, time])
        report(2, time - up)
        events.emit({code: -1, diffTime: Object.freeze([]), gapTime, receivedAt: data.receivedAt, timeSource: data.timeSource})
      }
      up = null
      return
    }
    handKeyDown.value = false
    const start = down
    down = null
    if (start === null || time < start) { up = null; return }
    const duration = time - start
    if (duration <= 10) return
    up = time
    const code = duration <= threshold() ? 0 : 1
    report(duration > lineLimit() ? 11 : code, duration)
    events.emit({code, diffTime: Object.freeze([start, time]), gapTime: Object.freeze([]), receivedAt: data.receivedAt, startedAt: downReceivedAt, timeSource: data.timeSource})
  }
  return {handKeyDown, onKey: events.onKey, receive, reset}
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
