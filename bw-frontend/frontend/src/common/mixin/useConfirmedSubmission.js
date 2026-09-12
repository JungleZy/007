import {onUnmounted, ref} from 'vue'
import {Modal} from 'ant-design-vue'

// One confirmation boundary per mounted training attempt. Never replay into a new login.
export default function useConfirmedSubmission() {
  const token = localStorage.getItem('token')
  const deviceId = localStorage.getItem('deviceId')
  const busy = ref(false)
  const error = ref('')
  let disposed = false
  const config = {expectedToken: token, expectedDeviceId: deviceId, skipErrorToast: true}
  let pending = null
  const queued = []
  let runGeneration = 0
  const ensureSession = () => {
    if (disposed || !token || localStorage.getItem('token') !== token || localStorage.getItem('deviceId') !== deviceId) {
      throw new Error('登录状态已改变，请重新进入训练；不会跨账号重发数据')
    }
  }
  const request = async action => {
    ensureSession()
    const generation = runGeneration
    const response = await action(config)
    ensureSession()
    if (generation !== runGeneration) throw new Error('提交已被用户取消')
    if (response?.code !== 200) throw new Error(response?.message || '提交未获确认，请重试')
    return response
  }
  const retry = async () => {
    if ((!pending && !queued.length) || busy.value) return false
    const generation = ++runGeneration
    busy.value = true
    error.value = ''
    try {
      ensureSession()
      if (pending) await pending(request)
      pending = null
      busy.value = false
      while (queued.length && !pending && !disposed) {
        queued[0]()
        queued.shift()
      }
      return true
    } catch (cause) {
      if (!disposed && generation === runGeneration) {
        error.value = cause.message || '提交失败，请重试'
        Modal.confirm({title: '训练记录需要重试', content: error.value, okText: '重试', cancelText: '保留数据', onOk: retry})
      }
      return false
    } finally {
      if (generation === runGeneration) busy.value = false
    }
  }
  const run = operation => {
    if (error.value && queued.length && !pending) return retry()
    if (!pending) pending = operation
    return retry()
  }
  const saveSnapshot = (key, value) => {
    ensureSession()
    localStorage.setItem(key, JSON.stringify({token, deviceId, value}))
  }
  const loadSnapshot = key => {
    const text = localStorage.getItem(key)
    if (!text) return null
    const saved = JSON.parse(text)
    return saved.token === token && saved.deviceId === deviceId ? saved.value : null
  }
  const clearSnapshot = key => localStorage.removeItem(key)
  const defer = (action, first = false) => {
    if (disposed) return true
    if (!pending && !error.value) return false
    if (first) queued.unshift(action)
    else queued.push(action)
    return true
  }
  const cancel = () => {
    ensureSession()
    runGeneration++
    pending = null
    queued.length = 0
    error.value = ''
    busy.value = false
  }
  onUnmounted(() => { disposed = true; queued.length = 0; pending = null })
  return {run, defer, retry, cancel, busy, error, saveSnapshot, loadSnapshot, clearSnapshot, pending: () => pending !== null || Boolean(error.value)}
}
