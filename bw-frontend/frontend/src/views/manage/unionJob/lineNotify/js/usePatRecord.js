import {ref, onUnmounted} from 'vue'
import {getLinePatRecord, saveLinePatRecord} from '../../../../../common/api/UserApi'

export default function usePatRecord(trainData, roomId, userId) {
  const recordError = ref('')
  const recordReady = ref(false)
  const key = `linePat:${roomId}:${userId}`
  let savedCount = 0
  let uploading = null
  let timer = null
  let disposed = false
  const same = (a, b) => a.diff === b.diff && a.gap === b.gap && a.pageNumber === b.pageNumber
  const requireRecords = response => {
    if (response?.code !== 200 || !Array.isArray(response.data)) throw new Error(response?.message || '拍发记录读取或保存失败')
    return response.data
  }
  const cache = () => {
    localStorage.setItem(key, JSON.stringify(trainData.value.patCodeLog))
  }
  const schedule = () => {
    if (!disposed && timer === null) timer = setTimeout(() => { timer = null; flush() }, 5000)
  }
  const flush = async () => {
    if (!recordReady.value) return false
    if (uploading) return uploading.then(ok => ok ? flush() : false)
    if (savedCount >= trainData.value.patCodeLog.length) return true
    const snapshot = trainData.value.patCodeLog.map(stroke => ({...stroke}))
    uploading = (async () => {
      try {
        const saved = requireRecords(await saveLinePatRecord(roomId, snapshot))
        if (saved.slice(0, Math.min(saved.length, trainData.value.patCodeLog.length)).some((stroke, index) => !same(stroke, trainData.value.patCodeLog[index]))) {
          throw new Error('拍发记录存在冲突，请保留缓存并联系管理员')
        }
        savedCount = saved.length
        if (saved.length >= trainData.value.patCodeLog.length) trainData.value.patCodeLog = saved
        recordError.value = ''
        if (savedCount >= trainData.value.patCodeLog.length) localStorage.removeItem(key)
        else cache()
        return true
      } catch (error) {
        recordError.value = error.message || '拍发记录保存失败，请重试'
        return false
      } finally {
        uploading = null
        if (savedCount < trainData.value.patCodeLog.length) schedule()
      }
    })()
    return uploading
  }
  const restore = async () => {
    if (recordReady.value) return
    try {
      const saved = requireRecords(await getLinePatRecord(roomId))
      const cached = JSON.parse(localStorage.getItem(key) || '[]')
      if (!Array.isArray(cached) || cached.slice(0, Math.min(saved.length, cached.length)).some((stroke, index) => !same(stroke, saved[index]))) {
        throw new Error('本机拍发记录与服务器记录不一致，请保留缓存并联系管理员')
      }
      trainData.value.patCodeLog = cached.length > saved.length ? cached : saved
      savedCount = saved.length
      recordReady.value = true
      recordError.value = ''
      if (savedCount < trainData.value.patCodeLog.length) schedule()
    } catch (error) {
      recordError.value = error.message || '拍发记录读取失败，请重试'
      throw error
    }
  }
  const append = stroke => {
    if (!recordReady.value) return
    trainData.value.patCodeLog.push(stroke)
    try { cache() } catch { recordError.value = '本机缓存写入失败，请重试保存拍发记录' }
    schedule()
  }
  const beforeUnload = () => {
    if (recordReady.value && savedCount < trainData.value.patCodeLog.length) {
      try { cache() } catch { /* 页面内的保存失败提示仍保留。 */ }
    }
  }
  window.addEventListener('beforeunload', beforeUnload)
  onUnmounted(() => {
    disposed = true
    clearTimeout(timer)
    window.removeEventListener('beforeunload', beforeUnload)
    beforeUnload()
    flush()
  })
  return {recordError, recordReady, restore, flush, append}
}
