import { onBeforeUnmount, ref } from 'vue'

// REST is authoritative; socket frames only request another read.
export default function useSimulationRecovery(load, waiting) {
  const recoveryError = ref('')
  const socketStatus = ref('connecting')
  let active = true
  let inFlight = null
  let timer = null
  let requested = false
  const schedule = () => {
    clearTimeout(timer)
    timer = null
    if (active && !document.hidden && (waiting() || recoveryError.value)) timer = setTimeout(refresh, 5000)
  }
  const refresh = () => {
    if (!active || document.hidden) return Promise.resolve()
    if (inFlight) {
      requested = true
      return inFlight
    }
    clearTimeout(timer)
    inFlight = Promise.resolve().then(load).then(() => {
      if (active) recoveryError.value = ''
    }).catch(error => {
      if (active) recoveryError.value = error?.message || '读取训练结果失败，请检查连接'
    }).finally(() => {
      inFlight = null
      if (requested && active && !document.hidden) {
        requested = false
        refresh()
      } else schedule()
    })
    return inFlight
  }
  const visibilityChanged = () => {
    clearTimeout(timer)
    timer = null
    if (!document.hidden) refresh()
  }
  document.addEventListener('visibilitychange', visibilityChanged)
  const stop = () => {
    active = false
    clearTimeout(timer)
    document.removeEventListener('visibilitychange', visibilityChanged)
  }
  onBeforeUnmount(stop)
  return { refresh, stop, recoveryError, socketStatus, onState: state => { socketStatus.value = state } }
}
