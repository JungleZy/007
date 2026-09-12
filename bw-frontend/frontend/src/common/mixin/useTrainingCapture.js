import {ref} from 'vue'

export default function useTrainingCapture() {
  const metadata = ref(null)
  let anchor = 0
  let opened = null
  let intervals = []
  let carriedMs = 0
  let confirmedMillis = 0
  let queuedSpan = null
  let queuedStored = false
  const intervalMillis = () => intervals.reduce((total, interval) => total + interval.endedMs - interval.startedMs, 0)
  const now = () => Math.max(0, Math.floor(metadata.value.serverElapsedMs + performance.now() - anchor))
  const bind = data => {
    if (data.protocolVersion !== 1 || !Number.isInteger(data.attempt) || !Number.isFinite(data.serverElapsedMs)) {
      throw new Error('服务器未返回有效采集时间轴，请重新取页')
    }
    carriedMs = metadata.value?.attempt === data.attempt ? carriedMs + Math.max(0, intervalMillis() - confirmedMillis) : 0
    metadata.value = data
    anchor = performance.now()
    opened = null
    intervals = (data.savedCaptureIntervals || []).map(interval => ({startedMs: interval.startedMs, endedMs: interval.endedMs}))
    confirmedMillis = intervalMillis()
    queuedSpan = null
    queuedStored = false
  }
  const open = (receivedAt = performance.now()) => {
    if (!metadata.value) throw new Error('尚未同步采集时间轴，请重新进入训练')
    if (opened === null) opened = Math.max(Math.floor(metadata.value.serverElapsedMs), intervals.at(-1)?.endedMs ?? 0, Math.floor(metadata.value.serverElapsedMs + receivedAt - anchor))
  }
  const close = () => {
    if (opened === null) return
    const endedMs = Math.ceil(metadata.value.serverElapsedMs + performance.now() - anchor)
    if (endedMs > opened) intervals.push({startedMs: opened, endedMs})
    opened = null
  }
  const snapshot = () => {
    close()
    if (!metadata.value) throw new Error('尚未同步采集时间轴')
    return {attempt: metadata.value.attempt, captureIntervals: intervals.map(interval => ({...interval}))}
  }
  const stamp = (startedAt, receivedAt) => {
    if (!metadata.value) throw new Error('尚未同步采集时间轴')
    return Object.freeze({
      startedMs: Math.max(0, Math.floor(metadata.value.serverElapsedMs + startedAt - anchor)),
      endedMs: Math.max(0, Math.ceil(metadata.value.serverElapsedMs + receivedAt - anchor))
    })
  }
  const recordQueued = span => {
    if (!queuedSpan) {
      const previousEnd = intervals.at(-1)?.endedMs ?? 0
      if (span.startedMs < previousEnd - 1) throw new Error('排队采集时间与已确认区间重叠，请保留记录并重新同步')
      queuedSpan = {startedMs: Math.max(previousEnd, span.startedMs), endedMs: Math.max(previousEnd, span.endedMs)}
    } else {
      queuedSpan.endedMs = Math.max(queuedSpan.endedMs, span.endedMs)
    }
    if (!queuedStored && queuedSpan.endedMs > queuedSpan.startedMs) {
      intervals.push(queuedSpan)
      queuedStored = true
    }
    if (opened !== null) opened = Math.max(opened, queuedSpan.endedMs)
  }
  const between = (start, end) => {
    if (!Number.isFinite(start) || end <= start) return 0
    let duration = 0
    for (const interval of intervals) duration += Math.max(0, Math.min(end, interval.endedMs) - Math.max(start, interval.startedMs))
    if (opened !== null) duration += Math.max(0, end - Math.max(start, opened))
    return duration
  }
  const elapsed = () => carriedMs + Math.max(0, intervalMillis() - confirmedMillis) + (opened === null ? 0 : Math.max(0, now() - opened))
  return {metadata, bind, open, close, snapshot, elapsed, stamp, recordQueued, between}
}
