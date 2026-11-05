import {ref, onMounted, nextTick, onUnmounted} from 'vue'
import useTraffic from '../../../../../common/mixin/useTraffic'
import {useRoute} from 'vue-router'
import {message, Modal} from 'ant-design-vue'
import {apiSimulationRouterRoomDetail, apiSimulationRouterSendFinish, getRoomUserList, apiSimulationRouterFindPage} from '../../../../../common/api/UserApi'
import {wsUrl} from '../../../../../common/http/endpoint.js'
import SocketConnection from '../../../../../common/ws/SocketConnection.js'
import usePatRecord from './usePatRecord'
import {createLineHandControl} from './handControl.js'
import {decodePatRecord} from './decodePatRecord.js'

export default function issue(trainData, trainTimeRef) {
  onMounted(() => {
    init()
    getApiRoomUserList()
    document.addEventListener('visibilitychange', handleVisibilityChange)
  })
  onUnmounted(() => {
    disposed = true
    clearTimeout(recoveryTimer)
    clearInterval(autoTime.value)
    handControl.reset()
    document.removeEventListener('visibilitychange', handleVisibilityChange)
    ws.close()
  })
  const route = useRoute()
  const lssuedText = ref('')
  // const currPatKeyIndex = ref(1)
  const sendUserList = ref([])
  const putAwayUserList = ref([])
  const buttonState = ref(true)

  const userId = JSON.parse(localStorage.getItem('userInfo'))
  const patRecord = usePatRecord(trainData, route.query.id, userId.id)
  let capturePage = Number(trainData.value.telegraph.prev) || 1
  const handControl = createLineHandControl({
    getPage: () => capturePage,
    onStroke: stroke => patRecord.append(stroke),
    onControl: action => handleHandControl(action)
  })
  const sendLessudTextList = ref([])
  const disturbVolume = ref(60)
  const checkedDisturb = ref([])
  const disturbList = ref([
    { type: 1, name: '白噪音', checked: false },
    { type: 2, name: '俄语', checked: false },
    { type: 3, name: '日语', checked: false },
    { type: 4, name: '英语', checked: false },
    { type: 5, name: '战场音', checked: false },
    { type: 6, name: '防空警报', checked: false }
  ])
  const a = ref([])
  const all = ref(0)
  const curr = ref(1)
  const sendId = ref(null)
  const details = ref(null)
  const recoveryError = ref('')
  const connectionState = ref('connecting')
  const ws = new SocketConnection()
  let disposed = false
  let recovering = false
  let recoveryPending = false
  let recoveryTimer = null
  let finishPending = false
  let loaded = false
  const waitingForResults = () => !loaded || trainData.value.stats != 2 || putAwayUserList.value.some(item => item.status != 1)
  const scheduleRecovery = () => {
    clearTimeout(recoveryTimer)
    if (!disposed && !document.hidden && (waitingForResults() || recoveryError.value)) {
      recoveryTimer = setTimeout(getApiRoomUserList, 5000)
    }
  }
  const handleVisibilityChange = () => {
    clearTimeout(recoveryTimer)
    if (!document.hidden) getApiRoomUserList()
  }
  const requireData = res => {
    if (res?.code != 200 || !res.data) throw new Error(res?.message || '训练结果读取失败，请重试')
    return res.data
  }

  const activeSend = ref(null)
  const activePutAway = ref(false)
  const init = () => {
    const roomId = route.query.id
    ws.connect(wsUrl(`/simulation/${userId.id}/${roomId}`), res => {
      let newData
      try {
        const data = JSON.parse(res.data)
        if (data.code == -1) return
        newData = typeof data.data === 'string' ? JSON.parse(data.data) : data.data
        if (!newData || typeof newData !== 'object') return
      } catch {
        recoveryError.value = '收到无效训练消息，正在通过服务器恢复'
        getApiRoomUserList()
        return
      }
      if (newData.topic == 'result' || newData.type == 'result') {
        if (newData.roomId == null || String(newData.roomId) === String(roomId)) getApiRoomUserList()
        return
      }
      if (!newData.body && !['begin', 'end', 'over'].includes(newData.topic)) return
      if (newData.topic == 'begin') {
        trainTime()
        trainData.value.stats = 1
      }
      else if (newData.topic == 'ready') {
        //准备
        putAwayUserList.value.forEach(item => {
          if (item.id == newData.body.id) {
            item.socketStatus = 2
          }
        })
        sendUserList.value.forEach(item => {
          if (item.id == newData.body.id) {
            item.socketStatus = 2
          }
        })
      }
      else if (newData.topic == 'over') {
        getApiRoomUserList()
      }
      else if (newData.topic == 'online') {
        //上线
        putAwayUserList.value.forEach(item => {
          if (item.id == newData.body.id) {
            item.socketStatus = 1
          }
        })
        sendUserList.value.forEach(item => {
          if (item.id == newData.body.id) {
            item.socketStatus = 1
          }
        })
      }
      else if (newData.topic == 'offline') {
        putAwayUserList.value.forEach(item => {
          if (item.id == newData.body.id) {
            item.socketStatus = 0
          }
        })
        sendUserList.value.forEach(item => {
          if (item.id == newData.body.id) {
            item.socketStatus = 0
          }
        })
      }
      else if (newData.topic == 'end') {
        clearInterval(autoTime.value)
        handControl.reset()
        trainData.value.currPatKeyIndex = -1
        getApiRoomUserList()
      }
      else if (newData.topic == 'play' && newData.body.type == 2) {
        sendLessudTextList.value.push(newData.body.value)
      }
      else if (newData.topic == 'change') {
        putAwayUserList.value.forEach(item => {
          if (item.id == newData.body.id) {
            item.channel = 0
            item.channel = newData.body.val
          }
        })
      }
    }, () => getApiRoomUserList(), state => {
      connectionState.value = state
    })
  }

  const pageCache = new Map()
  const pageRequests = new Map()
  let pageTarget = null
  let pageRequest = 0
  const fetchTelegramPage = (page, user) => {
    const key = `${user}:${page}`
    if (pageCache.has(key)) return Promise.resolve(pageCache.get(key))
    if (pageRequests.has(key)) return pageRequests.get(key)
    const request = apiSimulationRouterFindPage({pageNumber: page, roomId: route.query.id, userId: user})
      .then(response => {
        const data = requireData(response)
        if (!Array.isArray(data.pageVos)) throw new Error('报底数据格式错误')
        pageCache.set(key, data)
        return data
      }).finally(() => pageRequests.delete(key))
    pageRequests.set(key, request)
    return request
  }
  const showTelegramPage = async page => {
    const user = route.query.roomState == 'createUser' ? sendUserList.value[0]?.id : userId.id
    if (!user || page < 1 || page > Math.max(1, trainData.value.telegraph.allPage)) return
    pageTarget = page
    const request = ++pageRequest
    const data = await fetchTelegramPage(page, user)
    if (disposed || request !== pageRequest) return
    trainData.value.telegraph.prev = page
    trainData.value.telegraph.curr = data.pageVos
    trainData.value.telegraph.preCurr = pageCache.get(`${user}:${page - 1}`)?.pageVos || []
    trainData.value.telegraph.nextCurr = pageCache.get(`${user}:${page + 1}`)?.pageVos || []
    if (page < trainData.value.telegraph.allPage) {
      fetchTelegramPage(page + 1, user).then(next => {
        if (!disposed && request === pageRequest) trainData.value.telegraph.nextCurr = next.pageVos
      }).catch(() => { /* 预取失败不影响当前页，需要翻页时会重新请求并显示失败原因。 */ })
    }
  }
  const switchTelegram = async num => {
    const page = (pageTarget ?? trainData.value.telegraph.prev) + num
    if (page < 1 || page > trainData.value.telegraph.allPage) {
      message.error(num > 0 ? '已经是最后一页了' : '已经是第一页了')
      return
    }
    try { await showTelegramPage(page) }
    catch (error) { recoveryError.value = error.message; scheduleRecovery() }
  }

  const getApiRoomUserList = async () => {
    if (disposed || document.hidden) return
    if (recovering) {
      recoveryPending = true
      return
    }
    recovering = true
    clearTimeout(recoveryTimer)
    try {
      const roster = requireData(await getRoomUserList(route.query.id))
      if (disposed) return
      sendUserList.value = roster.sendUserList || []
      if (sendUserList.value.some(item => item.id === userId.id)) await patRecord.restore()
      handControl.seed(trainData.value.patCodeLog)
      putAwayUserList.value = (roster.receiveUserList || []).map(item => ({ ...item, activeState: activePutAway.value && item.id == sendId.value }))
      await getRoomDetails()
      if (disposed) return
      loaded = true
      recoveryError.value = ''
    } catch (error) {
      if (!disposed) recoveryError.value = error.message || '训练结果读取失败，请重试'
    } finally {
      recovering = false
      if (recoveryPending && !disposed && !document.hidden) {
        recoveryPending = false
        getApiRoomUserList()
      } else {
        scheduleRecovery()
      }
    }
  }

  const changeDisturbInfo = item => {
    let obj
    if (item) {
      item.checked = !item.checked
      obj = {
        topic: 'play',
        body: {
          type: 5,
          _type: item.type,
          value: item.checked,
          volume: disturbVolume.value
        }
      }
      ws.send(JSON.stringify(obj))
    } else if (disturbList.value.some(item => item.checked)) {
      obj = {
        topic: 'play',
        body: {
          type: 6,
          value: disturbVolume.value
        }
      }
      ws.send(JSON.stringify(obj))
    }
  }

  //下发文书
  const sendLessudText = () => {
    const obj = {
      topic: 'play',
      body: {
        type: 2,
        value: lssuedText.value
      }
    }
    ws.send(JSON.stringify(obj)) //按下事件
    lssuedText.value = ''
  }

  const getRoomDetails = async () => {
    const data = requireData(await apiSimulationRouterRoomDetail({ roomId: route.query.id }))
    if (disposed) return
    details.value = data
    trainData.value.type = data.bwType
    trainData.value.codeSort = data.bwType == 2
    trainData.value.userStatus = data.currentUserStatus
    trainData.value.currentUserChannel = data.currentUserChannel
    trainData.value.trainId = data.currentUserId
    trainData.value.stats = data.stats
    trainData.value.telegraph.allPage = data.isCable == 1 ? Number(data.pageCount || 0) : Math.ceil(Number(data.bwCount || 0) / 100)
    if (data.stats != 1 || data.currentUserStatus == 1) handControl.reset()
    if (!loaded && data.stats == 1 && trainData.value.patCodeLog.length) {
      const last = decodePatRecord(trainData.value.patCodeLog, {bwType: data.bwType}).at(-1)
      capturePage = Math.min(Math.max(1, trainData.value.telegraph.allPage), (last?.pageNumber || 1) + (last?.control === 'turn' ? 1 : 0))
      pageTarget = capturePage
    }
    trainData.value.totalTime = data.totalTime
    clearInterval(autoTime.value)
    trainData.value.duration = Number(data.totalTime || 0)
    if (data.stats == 1) trainTime()
    else nextTick(() => trainTimeRef.value?.autoSetTimeAdd(trainData.value.duration))
    if (data.stats == 2 && route.query.roomState == 'createUser' && !activePutAway.value) {
      const submitted = putAwayUserList.value.find(item => item.status == 1)
      if (submitted) {
        sendId.value = submitted.id
        curr.value = 1
        activePutAway.value = true
        submitted.activeState = true
      }
    }
    const selected = putAwayUserList.value.find(item => item.id == sendId.value)
    if (activePutAway.value && selected?.status == 1) {
      all.value = Math.max(trainData.value.telegraph.allPage, Number(selected.existPageNumber || 0))
      await loadResultPage(Math.min(curr.value, Math.max(1, all.value)), selected.id)
      return
    }
    await showTelegramPage(Math.min(pageTarget ?? trainData.value.telegraph.prev, Math.max(1, trainData.value.telegraph.allPage)))
  }

  // 训练用时
  const autoTime = ref(null)
  // const pageCodes = ref([])
  const trainTime = () => {
    clearInterval(autoTime.value)
    autoTime.value = setInterval(() => {
      trainData.value.duration++
      nextTick(() => {
        if (trainTimeRef.value) {
          trainTimeRef.value.autoSetTimeAdd(trainData.value.duration)
        }
      })
    }, 1000)
  }
  //准备训练
  const handleStartTrain = () => {
    buttonState.value = false
    sendUserList.value.forEach(item => {
      if (item.id == userId.id) {
        item.socketStatus = 2
      }
    })

    const obj = {
      topic: 'ready',
      body: {
        type: 2,
        id: userId.id,
        value: '准备'
      }
    }
    ws.send(JSON.stringify(obj))
  }

  let resultRequest = 0
  const loadResultPage = async (page, selectedId) => {
    const request = ++resultRequest
    const data = requireData(await apiSimulationRouterFindPage({ pageNumber: page, roomId: route.query.id, userId: selectedId }))
    if (disposed || request !== resultRequest || selectedId != sendId.value) return
    curr.value = page
    a.value = data
  }
  const handlePageTurn = async direction => {
    const page = curr.value + (direction == 'prev' ? -1 : 1)
    if (page < 1 || page > all.value) return
    try {
      await loadResultPage(page, sendId.value)
      recoveryError.value = ''
    } catch (error) {
      recoveryError.value = error.message
      scheduleRecovery()
    }
  }
  const handleActivePutAway = async item => {
    if (trainData.value.stats != 2 || item.status != 1) return
    sendId.value = item.id
    curr.value = 1
    a.value = null
    activePutAway.value = true
    putAwayUserList.value.forEach(user => { user.activeState = user.id == item.id })
    await getApiRoomUserList()
  }

  const showOwnRecord = async () => {
    ++resultRequest
    sendId.value = null
    activePutAway.value = false
    a.value = null
    putAwayUserList.value.forEach(user => { user.activeState = false })
    await getApiRoomUserList()
  }

  //开始训练
  const handleBeginTrain = () => {
    const obj = {
      topic: 'begin',
      body: {
        type: 10,
        value: '开始'
      }
    }
    trainTime()
    trainData.value.stats = 1
    ws.send(JSON.stringify(obj))
  }
  //结束训练
  const handleEndTrain = () => {
    disturbList.value.forEach(item => {
      if (item.checked) {
        changeDisturbInfo(item)
      }
    })

    const obj = {
      topic: 'end',
      body: {
        type: 11,
        value: '结束'
      }
    }
    clearInterval(autoTime.value)
    trainData.value.stats = 2
    ws.send(JSON.stringify(obj))
  }
  //结束训练
  const finishSending = async showSuccess => {
    if (finishPending) return
    finishPending = true
    handControl.reset()
    try {
      if (!await patRecord.flush()) throw new Error(patRecord.recordError.value || '拍发记录尚未保存，请重试')
      const res = await apiSimulationRouterSendFinish(route.query.id)
      if (res?.code != 200 || !res.data) throw new Error(res?.message || '结束训练失败，请重试')
      clearInterval(autoTime.value)
      trainData.value.userStatus = res.data.currentUserStatus
      trainData.value.currPatKeyIndex = 100
      recoveryError.value = ''
      getApiRoomUserList()
      ws.send(JSON.stringify({
        topic: 'play',
        body: { type: 4, value: '结束' }
      }))
      if (showSuccess) message.success('发报结束')
    } catch (error) {
      recoveryError.value = error.message || '结束训练失败，请重试'
    } finally {
      finishPending = false
    }
  }

  //结束训练
  const overBulletin = () => {
    Modal.confirm({
      content: '确定要结束训练吗?',
      onOk() {
        finishSending(false)
      }
    })
  }

  const handleHandControl = action => {
    if (disposed || finishPending || trainData.value.stats != 1 || trainData.value.userStatus == 1) return
    if (action === 'turn') {
      if (capturePage < trainData.value.telegraph.allPage) {
        capturePage++
        trainData.value.currPatKeyIndex = 0
        showTelegramPage(capturePage).catch(error => {
          recoveryError.value = error.message || '下一页报底读取失败，请重试'
          scheduleRecovery()
        })
      } else finishSending(true)
    } else if (action === 'end') finishSending(true)
  }

  // 原始边沿持续转发，分页符识别和报底请求不会阻塞下一页的采集。
  const handleHandKeysData = data => {
    if (!Number.isFinite(data.d) || (data.k != 0 && data.k != 1)) return
    if (finishPending || trainData.value.userStatus == 1 || trainData.value.stats != 1
        || !sendUserList.value.some(item => item.id === userId.id) || !patRecord.recordReady.value) return
    handControl.receive(data)
    if (finishPending || trainData.value.userStatus == 1) return
    if (data.k == 0) {
      const obj = {
        topic: 'play',
        body: {
          type: 1,
          value: '0'
        }
      }
      ws.send(JSON.stringify(obj)) //按下事件
    } else {
      const obj = {
        topic: 'play',
        body: {
          type: 1,
          value: '1'
        }
      }
      ws.send(JSON.stringify(obj)) //抬起事件
    }
  }
  useTraffic(true, handleHandKeysData)

  return {
    trainData,
    lssuedText,
    sendLessudText,
    getRoomDetails,
    sendUserList,
    putAwayUserList,
    sendLessudTextList,
    disturbVolume,
    checkedDisturb,
    disturbList,
    buttonState,
    handleStartTrain,
    handleBeginTrain,
    overBulletin,
    handleEndTrain,
    changeDisturbInfo,
    switchTelegram,
    activePutAway,
    activeSend,
    handleActivePutAway,
    showOwnRecord,
    recordError: patRecord.recordError,
    savePatRecord: patRecord.flush,
    handlePageTurn,
    a,
    all,
    details,
    recoveryError,
    connectionState,
    getApiRoomUserList,
    curr
  }
}
