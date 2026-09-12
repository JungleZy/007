import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import {
  getTelexTrainByID, postTelexPatTrain, endTelexPatTrain,
  apiPostTelexPatTrainGetPage, apiPostTelexPatTrainFinishPage,
  pausePostTelexPatTrain, resumePostTelexPatTrain, resetPostTelexPatTrain
} from '../../../../../common/api/TelegramApi.js'

// The same snapshot owns the body, attempt and capture intervals until acknowledged.
export default function usePageSubmission({ id, trainData, page, loading, countDown, getText, restoreText, showPage, onFinished, countdown }) {
  const busy = ref(false)
  const failure = ref('')
  const authBlocked = ref(false)
  const ready = ref(false)
  const stopped = ref(false)
  const expired = ref(false)
  const inputLocked = computed(() => !ready.value || busy.value || !!failure.value || stopped.value || expired.value || authBlocked.value || trainData.value.status !== 1)
  const session = { skipErrorToast: true }
  let sessionError = null
  try {
    session.expectedToken = window.localStorage.getItem('token')
    session.expectedDeviceId = window.localStorage.getItem('deviceId')
  } catch (error) {
    sessionError = new Error('无法读取登录凭证，请恢复浏览器存储权限并重新打开训练页')
    sessionError.sessionChanged = true
  }
  const confirmed = new Map()
  const captures = new Map()
  let attempt = 0
  let pending = null
  let retryOperation = null
  let collectedMs = 0
  let captureStart = null
  let anchorElapsed = 0
  let anchorLocal = performance.now()
  let deadlineOffset = null
  let pausedRemaining = null
  let deadlineTriggered = false
  let disposed = false
  const serverNow = () => Math.floor(anchorElapsed + performance.now() - anchorLocal)
  const captureEnd = () => Math.max(0, deadlineOffset === null ? serverNow() : Math.min(serverNow(), deadlineOffset))
  const elapsedMilliseconds = () => collectedMs + (captureStart === null ? 0 : Math.max(0, captureEnd() - captureStart))
  const elapsed = () => Math.floor(elapsedMilliseconds() / 1000)
  const closeCapture = () => {
    if (captureStart !== null) {
      const end = captureEnd()
      if (end > captureStart) {
        const intervals = captures.get(page.value.current) || []
        intervals.push({ startedMs: captureStart, endedMs: end })
        captures.set(page.value.current, intervals)
        collectedMs += end - captureStart
      }
      captureStart = null
    }
    trainData.value.duration = elapsed()
  }
  const canCapture = () => {
    if (trainData.value.status === 1 && deadlineOffset !== null && serverNow() >= deadlineOffset) {
      closeCapture()
      expired.value = true
    }
    return !inputLocked.value
  }
  const resumeCapture = () => {
    if (!disposed && !inputLocked.value && captureStart === null && (deadlineOffset === null || serverNow() < deadlineOffset)) captureStart = serverNow()
  }
  const assertSession = () => {
    if (sessionError) throw sessionError
    if (disposed || authBlocked.value || !session.expectedToken || !session.expectedDeviceId ||
        window.localStorage.getItem('token') !== session.expectedToken || window.localStorage.getItem('deviceId') !== session.expectedDeviceId) {
      const error = new Error('登录会话已失效或变更，禁止跨会话发送本次采集数据')
      error.sessionChanged = true
      throw error
    }
  }
  const request = async (api, data) => {
    assertSession()
    const result = await api(data, session)
    assertSession()
    if (result?.code !== 200) {
      const error = new Error(result?.message || '服务器未确认请求')
      error.sessionChanged = [203, 204, 206, 401, 403].includes(result?.code)
      throw error
    }
    return result.data
  }
  const execute = async operation => {
    if (busy.value || disposed || authBlocked.value) return false
    closeCapture()
    busy.value = true
    loading.value = true
    failure.value = ''
    retryOperation = operation
    try {
      await operation()
      retryOperation = null
      return true
    } catch (error) {
      authBlocked.value = !!(error.sessionChanged || [401, 403].includes(error.response?.status))
      failure.value = authBlocked.value
        ? `${error.message}。请重新登录后从训练列表核对进度；不会跨会话重传。`
        : `${error.message || '网络请求失败'}。原内容、页码和采集区间已保留，采集已暂停，请重试；截止后可核对已结算结果。`
      message.error(failure.value)
      return false
    } finally {
      busy.value = false
      loading.value = false
      resumeCapture()
    }
  }
  const retry = () => retryOperation && execute(retryOperation)
  const readPage = number => request(apiPostTelexPatTrainGetPage, { trainId: id, pageNumber: number })
  const readDetail = () => request(getTelexTrainByID, { id })
  const applyClock = detail => {
    if (!Number.isInteger(detail.serverElapsedMs) || detail.serverElapsedMs < 0) throw new Error('服务端未返回有效采集时钟')
    anchorElapsed = detail.serverElapsedMs
    anchorLocal = performance.now()
    trainData.value.status = detail.status
    trainData.value.attempt = detail.attempt
    trainData.value.protocolVersion = detail.protocolVersion
    pausedRemaining = detail.status === 2 ? detail.remainingMs : null
    deadlineOffset = detail.remainingMs === null || detail.remainingMs === undefined ? null : anchorElapsed + detail.remainingMs
    expired.value = detail.remainingMs !== null && detail.remainingMs !== undefined && detail.remainingMs <= 0
    if (countdown) {
      countdown.enabled.value = detail.countdownSeconds !== null && detail.countdownSeconds !== undefined
      if (countdown.enabled.value) countdown.duration.value = detail.countdownSeconds / 60
    }
  }
  const sameIntervals = (left, right) => Array.isArray(right) && left.length === right.length &&
    left.every((value, index) => value.startedMs === right[index].startedMs && value.endedMs === right[index].endedMs)
  const snapshotMatches = (snapshot, remote) => remote.submitted === true && remote.attempt === snapshot.attempt &&
    remote.codeAll === snapshot.patValue && sameIntervals(snapshot.captureIntervals, remote.captureIntervals)
  const snapshot = number => Object.freeze({
    trainId: id, pageNumber: number, patValue: getText(number), attempt,
    captureIntervals: Object.freeze((captures.get(number) || []).map(value => Object.freeze({ ...value })))
  })
  const acknowledge = () => {
    confirmed.set(pending.payload.pageNumber, pending.payload)
    pending = null
  }
  const submitPending = async () => {
    if (!pending) return
    if (pending.attempted && snapshotMatches(pending.payload, await readPage(pending.payload.pageNumber))) {
      acknowledge()
      return
    }
    pending.attempted = true
    await request(apiPostTelexPatTrainFinishPage, pending.payload)
    acknowledge()
  }
  const submitSnapshot = async payload => {
    await submitPending()
    const saved = confirmed.get(payload.pageNumber)
    if (saved && saved.patValue === payload.patValue && sameIntervals(saved.captureIntervals, payload.captureIntervals)) return
    pending = { payload, attempted: false }
    await submitPending()
  }
  const hydrate = async detail => {
    if (detail.status === 3) {
      attempt = detail.attempt
      await request(endTelexPatTrain, { id, attempt })
      trainData.value.status = 3
      await onFinished()
      return
    }
    if (detail.protocolVersion !== 1 && detail.status !== 3) throw new Error('旧训练缺少原始采集时间轴，不能继续或重新计分，请从列表新建训练')
    page.value.pageAll = detail.isCable === 1 ? detail.pageNumber : Math.ceil(detail.groupNumber / 100)
    if (!Number.isInteger(page.value.pageAll) || page.value.pageAll < 1) throw new Error('训练总页数无效')
    attempt = detail.attempt
    confirmed.clear()
    captures.clear()
    collectedMs = 0
    for (let number = 1; number <= page.value.pageAll; number++) {
      restoreText(number, '')
      const remote = await readPage(number)
      if (!remote.submitted) continue
      if (remote.attempt !== attempt || !Array.isArray(remote.captureIntervals)) throw new Error(`第${number}页原始时间轴或轮次缺失`)
      restoreText(number, remote.codeAll)
      captures.set(number, remote.captureIntervals.map(value => ({ ...value })))
      collectedMs += remote.captureIntervals.reduce((sum, value) => sum + value.endedMs - value.startedMs, 0)
      confirmed.set(number, snapshot(number))
    }
    Object.assign(trainData.value, {
      id: detail.id, groupNumber: detail.groupNumber, errorNumber: detail.errorNumber,
      accuracy: Number(detail.accuracy), speed: Number(detail.totalSpeed || detail.speed || 0), validTime: detail.validTime,
      duration: elapsed()
    })
    page.value.current = 1
    while (confirmed.has(page.value.current) && page.value.current < page.value.pageAll) page.value.current++
    const remote = await readPage(page.value.current)
    showPage(page.value.current, remote.pageVo)
    // Re-anchor after hydration requests rather than counting their transport time as capture.
    const current = await readDetail()
    if (current.attempt !== attempt) throw new Error('训练轮次已变更，请重新打开训练')
    applyClock(current)
    ready.value = true
  }
  const initialise = () => execute(async () => hydrate(await readDetail()))
  const startTest = () => {
    if (!ready.value || busy.value || trainData.value.status !== 0 || failure.value) return Promise.resolve(false)
    const minutes = countdown?.enabled.value ? Number(countdown.duration.value) : null
    if (minutes !== null && (!Number.isInteger(minutes) || minutes < 1 || minutes > 1440)) {
      message.error('倒计时时长必须为1至1440之间的整数分钟')
      return Promise.resolve(false)
    }
    const payload = Object.freeze({ id, attempt, countdownSeconds: minutes === null ? null : minutes * 60 })
    return execute(async () => {
      const detail = await request(postTelexPatTrain, payload)
      if (detail.attempt !== attempt) throw new Error('训练轮次已变更，请重新打开训练')
      applyClock(detail)
    })
  }
  const move = direction => {
    if (!ready.value || busy.value || failure.value || stopped.value || expired.value) return
    const target = page.value.current + direction
    if (target < 1 || target > page.value.pageAll) {
      message.info(direction > 0 ? '已是最后一页，请点击结束练习提交' : '已是第一页')
      return
    }
    let payload
    return execute(async () => {
      payload ||= snapshot(page.value.current)
      if (trainData.value.status === 1 || trainData.value.status === 2) await submitSnapshot(payload)
      const remote = await readPage(target)
      showPage(target, remote.pageVo)
      page.value.current = target
    })
  }
  const endTest = () => {
    if (!ready.value || failure.value || busy.value || ![1, 2].includes(trainData.value.status)) return
    stopped.value = true
    let payload
    const finishPayload = Object.freeze({ id, attempt })
    return execute(async () => {
      payload ||= snapshot(page.value.current)
      await submitSnapshot(payload)
      for (let number = 1; number <= page.value.pageAll; number++) {
        if (!confirmed.has(number)) await submitSnapshot(snapshot(number))
      }
      if (confirmed.size !== page.value.pageAll) throw new Error('尚有页面未确认，不能结束训练')
      await request(endTelexPatTrain, finishPayload)
      trainData.value.status = 3
      await onFinished()
    })
  }
  const pauseTest = () => {
    if (!canCapture()) return
    let payload
    const lifecycle = Object.freeze({ id, attempt })
    return execute(async () => {
      payload ||= snapshot(page.value.current)
      await submitSnapshot(payload)
      try {
        applyClock(await request(pausePostTelexPatTrain, lifecycle))
      } catch (error) {
        if (error.sessionChanged || [401, 403].includes(error.response?.status)) throw error
        const current = await readDetail()
        if (current.attempt !== attempt || !(current.status === 2 || (current.status === 1 && current.remainingMs === 0))) throw error
        applyClock(current)
        message.info(current.status === 2 ? '服务端已确认暂停' : '已到截止时间，输入冻结并转入补交')
      }
    })
  }
  const resumeTest = () => {
    if (busy.value || failure.value || trainData.value.status !== 2) return
    const payload = Object.freeze({ id, attempt })
    return execute(async () => applyClock(await request(resumePostTelexPatTrain, payload)))
  }
  const resetTest = () => {
    if (!ready.value || busy.value || authBlocked.value) return
    stopped.value = true
    const payload = Object.freeze({ id, attempt })
    return execute(async () => {
      let detail = await readDetail()
      if (detail.attempt !== payload.attempt + 1 || detail.status !== 0) detail = await request(resetPostTelexPatTrain, payload)
      pending = null
      stopped.value = false
      expired.value = false
      deadlineTriggered = false
      await hydrate(detail)
    })
  }
  const checkResult = async () => {
    const originalRetry = retryOperation
    const completed = await execute(async () => {
      const detail = await readDetail()
      if (detail.status !== 3) throw new Error('服务端尚未完成结算，请重试原提交或稍后核对')
      if (detail.attempt !== attempt) throw new Error('训练轮次已变更，请从列表查看新轮次')
      await request(endTelexPatTrain, { id, attempt })
      message.info('已读取服务端正式结算结果；补交窗口后未收到的内容不会改写成绩')
      trainData.value.status = 3
      await onFinished()
    })
    if (!completed && originalRetry) retryOperation = originalRetry
    return completed
  }
  const refreshClock = () => {
    if (document.hidden || !ready.value || busy.value || failure.value || authBlocked.value || ![1, 2].includes(trainData.value.status)) return
    execute(async () => {
      const current = await readDetail()
      if (current.attempt !== attempt) throw new Error('训练轮次已变更，请重新打开训练')
      applyClock(current)
      if (current.status === 3) {
        stopped.value = true
        message.warning('服务端已完成结算，请核对正式结果；未收到的本地内容不会改写成绩')
      }
    })
  }
  onMounted(() => {
    document.addEventListener('visibilitychange', refreshClock)
    window.addEventListener('focus', refreshClock)
  })
  const timer = setInterval(() => {
    trainData.value.duration = elapsed()
    const remaining = trainData.value.status === 2 ? pausedRemaining : deadlineOffset === null ? null : Math.max(0, deadlineOffset - serverNow())
    countDown.value?.autoSetTimeAdd(remaining === null ? elapsed() : Math.ceil(remaining / 1000))
    if (remaining !== null && remaining <= 0 && trainData.value.status === 1) {
      closeCapture()
      expired.value = true
      if (!deadlineTriggered && !busy.value && !failure.value) {
        deadlineTriggered = true
        endTest()
      }
    }
  }, 250)
  onBeforeUnmount(() => {
    closeCapture()
    disposed = true
    clearInterval(timer)
    document.removeEventListener('visibilitychange', refreshClock)
    window.removeEventListener('focus', refreshClock)
  })
  return { busy, failure, authBlocked, ready, inputLocked, canCapture, expired, retry, initialise, startTest, endTest,
    pauseTest, resumeTest, resetTest, checkResult, next: () => move(1), prev: () => move(-1), elapsed, elapsedMilliseconds }
}
