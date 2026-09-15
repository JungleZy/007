import { computed, nextTick, onMounted, onBeforeUnmount, ref } from 'vue'
import { onBeforeRouteLeave, useRoute } from 'vue-router'
import { getById, begin, hanziFinish, hanziPause, goTo } from '../../../../../../../common/api/TelegramApi.js'
import { Modal } from 'ant-design-vue'
import codebook from '../../../../../../../common/utils/entering-codebook.json'

export default function parctice() {
  const route = useRoute()
  const id = route.query.id
  const draftKey = `entering-exercise:${id}`
  const submissionKey = `${draftKey}:submission`
  const message = ref(null)
  const trainData = ref({ status: 0, duration: 0, correctNum: 0, errorNum: 0, speed: 0, accuracy: 0 })
  const activeIndex = ref(0)
  const inputIndex = ref(0)
  const isfocus = ref(true)
  const busy = ref(false)
  const ready = ref(false)
  const practiceArea = ref(null)
  const pendingKey = ref(null)
  const pendingSave = ref(null)
  const terminalConflict = ref(false)
  const uncertain = computed(() => pendingSave.value !== null && !terminalConflict.value)
  const activeMessage = computed(() => message.value?.[activeIndex.value] || { font: '', pys: [] })
  const small = computed(() => Math.max(0, activeIndex.value - 100))
  const big = computed(() => Math.max(200, activeIndex.value + 100))
  const nowTime = computed(() => {
    const total = trainData.value.duration || 0
    const h = String(Math.floor(total / 3600)).padStart(2, '0')
    const m = String(Math.floor(total / 60) % 60).padStart(2, '0')
    const s = String(total % 60).padStart(2, '0')
    return { h1: +h[0], h2: +h[1], m1: +m[0], m2: +m[1], s1: +s[0], s2: +s[1] }
  })
  let timer
  let starting
  let draftWarning = false
  let savedAnswers
  let disposed = false
  const focusPractice = () => nextTick(() => practiceArea.value?.focus())
  const showError = error => Modal.error({ content: error?.message || '网络连接失败，输入已保留，请重试' })
  const request = async promise => {
    const response = await promise
    if (response?.code !== 200) throw new Error(response?.message || '训练请求失败')
    return response.data
  }
  const answers = () => JSON.stringify(message.value.map(row => ({ value: row.value })))
  const retain = () => {
    try {
      sessionStorage.setItem(draftKey, answers())
      if (pendingSave.value) sessionStorage.setItem(submissionKey, JSON.stringify(pendingSave.value))
      else sessionStorage.removeItem(submissionKey)
    } catch (error) {
      if (!draftWarning) {
        draftWarning = true
        Modal.error({ content: '浏览器无法暂存输入，请勿刷新或关闭页面；请联网保存后退出' })
      }
    }
  }
  const updateSpeed = () => {
    trainData.value.speed = Math.floor((trainData.value.correctNum + trainData.value.errorNum) * 60 / Math.max(1, trainData.value.duration))
  }
  const statistics = () => {
    let correct = 0
    let errors = 0
    for (const row of message.value) {
      const code = row.pys.map(letter => letter.py).join('')
      row.pys.forEach((letter, index) => { letter.trueOrfalse = index < row.value.length ? row.value[index] === letter.py : null })
      row.isFocus = row.value.length === code.length
      row.trueOrfalse = row.isFocus ? row.value === code : null
      if (row.isFocus) row.trueOrfalse ? correct++ : errors++
    }
    trainData.value.correctNum = correct
    trainData.value.errorNum = errors
    trainData.value.accuracy = correct + errors ? Math.round(correct * 10000 / (correct + errors)) / 100 : 0
    updateSpeed()
  }
  const restoreCursor = () => {
    const index = message.value.findIndex(row => row.value.length < row.pys.length)
    activeIndex.value = index < 0 ? message.value.length - 1 : index
    inputIndex.value = activeMessage.value.value?.length || 0
  }
  const startTimer = () => {
    clearInterval(timer)
    timer = setInterval(() => { trainData.value.duration++; updateSpeed() }, 1000)
  }
  const consumeKey = key => {
    if (key === 'Backspace') {
      if (!activeMessage.value.value.length && activeIndex.value > 0) activeIndex.value--
      activeMessage.value.value = activeMessage.value.value.slice(0, -1)
    } else {
      if (activeMessage.value.value.length === activeMessage.value.pys.length) {
        if (activeIndex.value === message.value.length - 1) return
        activeIndex.value++
      }
      activeMessage.value.value += key
    }
    inputIndex.value = activeMessage.value.value.length
    statistics()
    retain()
  }
  const ensureStarted = async () => {
    if (trainData.value.status === 1) return
    if (starting) return starting
    busy.value = true
    starting = request((trainData.value.status === 3 ? goTo : begin)({ id })).then(() => {
      if (disposed) return
      trainData.value.status = 1
      startTimer()
      if (pendingKey.value !== null) {
        const key = pendingKey.value
        pendingKey.value = null
        consumeKey(key)
      }
      focusPractice()
    }).finally(() => { starting = null; busy.value = false })
    return starting
  }
  const resumeTest = async () => {
    if (!ready.value || busy.value || uncertain.value) return
    try { await ensureStarted() } catch (error) { showError(error) }
  }
  const keyCodeDown = event => {
    if (!ready.value || busy.value || uncertain.value || pendingKey.value !== null || ![0, 1].includes(trainData.value.status)) return
    if (event.ctrlKey || event.metaKey || event.altKey || event.target?.closest('button,input,textarea,[role="dialog"]')) return
    let key = event.key
    if (key === 'Process') {
      key = /^Key[A-Z]$/.test(event.code) ? event.code.slice(3).toLowerCase()
        : codebook.keyboard.processKeys[event.code]?.[event.shiftKey ? 1 : 0]
    } else if (key === 'Enter') key = '\n'
    if (!key || key !== 'Backspace' && key.length !== 1) return
    key = codebook.keyboard.inputKeys[key] || key
    event.preventDefault()
    if (trainData.value.status === 0) {
      // The initiating key is not an answer until the server acknowledges the clock.
      pendingKey.value = key
      ensureStarted().catch(showError)
      return
    }
    consumeKey(key)
  }
  const serverAnswers = data => JSON.stringify(JSON.parse(data.content).map(row => ({ value: row.value })))
  const acceptSaved = data => {
    clearInterval(timer)
    trainData.value = data
    message.value = JSON.parse(data.content)
    savedAnswers = answers()
    pendingSave.value = null
    terminalConflict.value = false
    retain()
    restoreCursor()
  }
  const reconcile = data => {
    const same = serverAnswers(data) === pendingSave.value.content
    const acknowledged = data.status === 2 || !pendingSave.value.finish && data.status === 3
    if (same && acknowledged) {
      acceptSaved(data)
      return true
    }
    if (data.status === 2) {
      trainData.value = data
      terminalConflict.value = true
      // Keep the local submission for recovery, but do not retry an immutable result.
      return false
    }
    // An active read cannot prove that an earlier timed-out write will never commit.
    // Keep the exact submission frozen until an idempotent retry is acknowledged.
    trainData.value = data
    savedAnswers = serverAnswers(data)
    return false
  }
  const confirmOutcome = async () => {
    try {
      const data = await request(getById({ id }))
      if (!disposed) return reconcile(data)
    } catch (error) { showError(error) }
    return false
  }
  const confirmWrite = async () => {
    if (busy.value || !uncertain.value) return false
    busy.value = true
    try { return await confirmOutcome() } finally { busy.value = false }
  }
  const retryWrite = async () => {
    if (busy.value || !uncertain.value) return false
    busy.value = true
    const snapshot = pendingSave.value
    try {
      const data = await request((snapshot.finish ? hanziFinish : hanziPause)({ id, content: snapshot.content }))
      if (disposed) return false
      acceptSaved(data)
      return true
    } catch (error) {
      showError(error)
      return await confirmOutcome()
    } finally { busy.value = false }
  }
  const save = async finish => {
    if (!ready.value || busy.value || uncertain.value) return false
    if (trainData.value.status === 2) return true
    if (trainData.value.status === 0 || trainData.value.status === 3 && answers() !== savedAnswers) {
      Modal.error({ content: '请先确认开始或继续训练，再保存输入' })
      return false
    }
    pendingSave.value = { finish, content: answers() }
    clearInterval(timer)
    retain()
    return retryWrite()
  }
  const saveTest = () => save(true)
  const stopTest = () => save(false)
  onBeforeRouteLeave(async () => {
    if (terminalConflict.value) return true
    if (busy.value || uncertain.value || pendingKey.value !== null) return false
    if (!ready.value || trainData.value.status === 2) return true
    if (trainData.value.status === 0 && message.value.every(row => !row.value)) return true
    return stopTest()
  })
  onMounted(async () => {
    window.addEventListener('keydown', keyCodeDown)
    try {
      const data = await request(getById({ id }))
      if (disposed) return
      trainData.value = data
      message.value = JSON.parse(data.content)
      if (data.status !== 2 && data.protocolVersion !== 1) throw new Error('旧版练习请重新创建训练')
      savedAnswers = answers()
      const submission = sessionStorage.getItem(submissionKey)
      if (submission) {
        pendingSave.value = JSON.parse(submission)
        if (typeof pendingSave.value.finish !== 'boolean' || typeof pendingSave.value.content !== 'string') throw new Error('待确认的保存记录无效')
      }
      if (data.status === 2 && !pendingSave.value) return
      try {
        const stored = sessionStorage.getItem(draftKey)
        if (stored) {
          const draft = JSON.parse(stored)
          if (!Array.isArray(draft) || draft.length !== message.value.length || draft.some((row, index) => typeof row.value !== 'string' || row.value.length > message.value[index].pys.length)) {
            throw new Error('暂存输入与训练题面不匹配，请勿继续录入')
          }
          draft.forEach((row, index) => { message.value[index].value = row.value })
        }
      } catch (error) {
        showError(error)
        return
      }
      restoreCursor()
      ready.value = true
      if (pendingSave.value) {
        if (pendingSave.value.content !== answers()) throw new Error('待确认记录与草稿不一致，请保留页面并联系管理员')
        reconcile(data)
      } else {
        statistics()
        if (data.status === 1) startTimer()
      }
      focusPractice()
    } catch (error) { showError(error) }
  })
  onBeforeUnmount(() => {
    disposed = true
    window.removeEventListener('keydown', keyCodeDown)
    clearInterval(timer)
  })
  return { message, trainData, activeMessage, isfocus, activeIndex, inputIndex, big, small, nowTime, busy, ready,
    practiceArea, pendingKey, uncertain, terminalConflict, confirmWrite, retryWrite, saveTest, stopTest, resumeTest }
}
