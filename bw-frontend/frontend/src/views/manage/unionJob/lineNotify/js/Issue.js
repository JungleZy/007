import { ref, reactive, onMounted, nextTick, onUnmounted } from 'vue'
import useTraffic from '../../../../../common/mixin/useTraffic'
import { deepClone } from '../../../../../common/utils/Utils.js'
import { useRouter, useRoute } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import { apiSimulationRouterRoomChannels, apiSimulationRouterRoomDetail, apiSimulationRouterSendFinish, getRoomUserList, apiSimulationRouterFindPage } from '../../../../../common/api/UserApi'
import { wsUrl } from '../../../../../common/http/endpoint.js'
import SocketConnection from '../../../../../common/ws/SocketConnection.js'

export default function issue(trainData, trainTimeRef) {
  onMounted(() => {
    init()
    getApiRoomUserList()
    document.addEventListener('visibilitychange', handleVisibilityChange)
    const dom = document.getElementById('patValBoxRef')
    if (dom) {
      dom.addEventListener('mousewheel', e => {
        if (e.deltaY > 0) {
          dom.scrollLeft += 100
        } else {
          dom.scrollLeft -= 100
        }
      })
      dom.addEventListener('mousewheel', e => {
        if (e.deltaY > 0) {
          dom.scrollLeft += 100
        } else {
          dom.scrollLeft -= 100
        }
      })
    }
  })
  onUnmounted(() => {
    disposed = true
    clearTimeout(recoveryTimer)
    clearInterval(autoTime.value)
    document.removeEventListener('visibilitychange', handleVisibilityChange)
    ws.close()
  })
  const route = useRoute()
  const lssuedText = ref('')
  // const currPatKeyIndex = ref(1)
  const sendUserList = ref([])
  const putAwayUserList = ref([])
  const channels = ref([])
  const buttonState = ref(true)

  const userId = JSON.parse(localStorage.getItem('userInfo'))
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
        console.log(putAwayUserList.value)
        console.log( newData.body.id)
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

  //分页
  const switchTelegram = num => {
    if (num == 1) {
      if (trainData.value.telegraph.prev == trainData.value.telegraph.allPage) {
        message.error('已经是最后一页了')
        return
      }
      trainData.value.telegraph.prev++
      trainData.value.telegraph.preCurr = deepClone(trainData.value.telegraph.curr)
      trainData.value.telegraph.curr = deepClone(trainData.value.telegraph.nextCurr)
    } else {
      if (trainData.value.telegraph.prev == 1) {
        message.error('已经是第一页了')
        return
      }
      trainData.value.telegraph.prev--
      trainData.value.telegraph.nextCurr = deepClone(trainData.value.telegraph.curr)
      trainData.value.telegraph.curr = deepClone(trainData.value.telegraph.preCurr)
    }
    getSimulationRouterFindPage(num)
  }

  //获取指定分页低报
  const getSimulationRouterFindPage = num => {
    let number = trainData.value.telegraph.prev + num
    if (number > trainData.value.telegraph.allPage || number < 1) return
    let user
    if (route.query.roomState == 'createUser') {
      user = sendUserList.value[0].id
    } else {
      user = userId.id
    }
    apiSimulationRouterFindPage({
      pageNumber: number,
      roomId: route.query.id,
      userId: user
    }).then(res => {
      if (res.code != 200) return
      if (num == 1) {
        trainData.value.telegraph.nextCurr = res.data.pageVos
      } else {
        trainData.value.telegraph.preCurr = res.data.pageVos
      }
    })
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

  const patValBoxRef = ref(null)
  let gap, diff
  let time1 = null //按下
  let time2 = null //抬起

  const patStandard = ref({
    dot: 60,
    line: 160,
    codeGap: 60,
    wordGap: 160,
    groupGap: 5000
  })
  const initFloat = ref(50) // 计算拍发基准值的浮动百分比

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
    const user = route.query.roomState == 'createUser' ? sendUserList.value[0]?.id : userId.id
    if (!user) return
    const page = Math.min(trainData.value.telegraph.prev, Math.max(1, trainData.value.telegraph.allPage))
    const current = requireData(await apiSimulationRouterFindPage({ pageNumber: page, roomId: route.query.id, userId: user }))
    if (disposed) return
    trainData.value.telegraph.prev = page
    trainData.value.telegraph.curr = current.pageVos
    if (page < trainData.value.telegraph.allPage) {
      const next = requireData(await apiSimulationRouterFindPage({ pageNumber: page + 1, roomId: route.query.id, userId: user }))
      if (!disposed) trainData.value.telegraph.nextCurr = next.pageVos
    }
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
    if (route.query.roomState != 'createUser' || item.status != 1) return
    sendId.value = item.id
    curr.value = 1
    a.value = null
    activePutAway.value = true
    putAwayUserList.value.forEach(user => { user.activeState = user.id == item.id })
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
  const overBulletin = () => {
    Modal.confirm({
      content: '确定要结束训练吗?',
      onOk() {
        //清楚定时器
        clearInterval(autoTime.value)
        const fromData = new FormData()
        fromData.append('roomId', route.query.id)
        const obj = {
          topic: 'play',
          body: {
            type: 4,
            value: '结束'
          }
        }
        ws.send(JSON.stringify(obj))
        apiSimulationRouterSendFinish(fromData).then(res => {
          if (res.code == 200) {
            trainData.value.userStatus = res.data.currentUserStatus
            getApiRoomUserList()
          } else {
            recoveryError.value = res.message || '结束训练失败，请重试'
          }
        }).catch(() => { recoveryError.value = '结束训练失败，请重试' })
      }
    })
  }

  //开始发报
  const handleHandKeysData = data => {
    if (trainData.value.currPatKeyIndex == -1) {
      if (trainData.value.stats != 1) {
        return
      }
      trainData.value.currPatKeyIndex++
    }
    if (time2 != null) {
      gap = data.d - time2
    }
    if (data.k == 0) {
      time1 = data.d
      time2 = null
    } else {
      time2 = data.d
    }
    if (data.k == 0) {
      time1 = data.d
      if (gap > patStandard.value.wordGap * (1 + initFloat.value / 100)) {
        trainData.value.currPatKeyIndex++
        if (trainData.value.currPatKeyIndex == trainData.value.telegraph.curr.length) {
          trainData.value.currPatKeyIndex = -1
          if (trainData.value.telegraph.prev < trainData.value.telegraph.allPage) {
            switchTelegram(1)
          } else {
            message.success('发报结束')
            trainData.value.currPatKeyIndex = 100
            //清楚定时器
            clearInterval(autoTime.value)
            const obj = {
              topic: 'play',
              body: {
                type: 4,
                value: '结束'
              }
            }
            ws.send(JSON.stringify(obj))
            const fromData = new FormData()
            fromData.append('roomId', route.query.id)
            apiSimulationRouterSendFinish(fromData).then(res => {
              if (res.code == 200) {
                trainData.value.userStatus = res.data.currentUserStatus
                getApiRoomUserList()
              } else {
                recoveryError.value = res.message || '结束训练失败，请重试'
              }
            }).catch(() => { recoveryError.value = '结束训练失败，请重试' })
          }
        }
      }
    } else if (data.k == 1) {
      // time2=data.d
    }
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

    if (time1 !== null && time2 != null && data.k == 1) {
      diff = time2 - time1
      trainData.value.patCodeLog.push({
        code: 0,
        diff: diff,
        gap: gap
      })
      nextTick(() => {
        const dom = document.getElementById('patValBoxRef')
        dom.scrollLeft = dom.scrollWidth
      })
    }
  }
  const {} = useTraffic(true, handleHandKeysData)

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
