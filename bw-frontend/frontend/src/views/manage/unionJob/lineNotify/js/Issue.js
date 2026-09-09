import { ref, reactive, onMounted, nextTick, onUnmounted } from 'vue'
import useTraffic from '../../../../../common/mixin/useTraffic'
import { deepClone } from '../../../../../common/utils/Utils.js'
import { useRouter, useRoute } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import { apiSimulationRouterRoomChannels, apiSimulationRouterRoomDetail, apiSimulationRouterSendFinish, getRoomUserList, apiSimulationRouterFindPage } from '../../../../../common/api/UserApi'
import { wsUrl } from '../../../../../common/http/endpoint.js'

export default function issue(trainData, trainTimeRef) {
  onMounted(() => {
    getApiRoomUserList()
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
    if (ws) {
      ws.close()
    }
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

  const activeSend = ref(null)
  const activePutAway = ref(false)
  const init = () => {
    const roomId = route.query.id
    if (trainData.value.userStatus == 1) return //房间状态为1表示已完成
    ws = new WebSocket(wsUrl(`/simulation/${userId.id}/${roomId}`))
    ws.onopen = function onopen() {
      sendUserList.value.forEach(item => {
        if (item.id == trainData.value.trainId) {
          item.socketStatus = 1
          sendUserList.value.forEach(item => {
            if (item.id == trainData.value.trainId) {
              trainData.value.userSocketStatus = item.socketStatus
              trainData.value.userStatus = item.status
            }
          })
        }
      })
    }

    ws.onmessage = function message(res) {
      const data = JSON.parse(res.data)
      const newData = JSON.parse(data.data)
      if (data.code == -1) return
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
        getRoomUserList(route.query.id).then(res => {
          if (res.code == 200) {
            res.data.sendUserList.forEach(item => {
              if (item.id == trainData.value.trainId) {
                trainData.value.userSocketStatus = item.socketStatus
                trainData.value.userStatus = item.status
              }
            })
            res.data.receiveUserList.forEach(item => {
              item.activeState = false
            })
            sendUserList.value = res.data.sendUserList
            putAwayUserList.value = res.data.receiveUserList
          }
        })
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
        apiSimulationRouterRoomDetail({
          roomId: route.query.id
        }).then(res => {
          trainData.value['stats'] = res.data.stats
          trainData.value.currPatKeyIndex = -1
        })
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
    }
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

  const getApiRoomUserList = () => {
    getRoomUserList(route.query.id).then(res => {
      if (res.code == 200) {
        res.data.sendUserList.forEach(item => {
          if (item.id == trainData.value.trainId) {
            trainData.value.userSocketStatus = item.socketStatus
            trainData.value.userStatus = item.status
          }
        })
        res.data.receiveUserList.forEach(item => {
          item.activeState = false
        })
        sendUserList.value = res.data.sendUserList
        putAwayUserList.value = res.data.receiveUserList
        //查询房间详情
        getRoomDetails()
      }
    })
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

  const getRoomDetails = () => {
    apiSimulationRouterRoomDetail({
      roomId: route.query.id
    }).then(res => {
      trainData.value.type = res.data.bwType
      if (res.data.bwType == 2) {
        trainData.value.codeSort = true
      }
      trainData.value.userStatus = res.data.currentUserStatus
      trainData.value.currentUserChannel = res.data.currentUserChannel
      trainData.value.trainId = res.data.currentUserId
      trainData.value['stats'] = res.data.stats
      if(res.data.isCable===1){
        trainData.value.telegraph.allPage = res.data.pageCount
      }else {
        trainData.value.telegraph.allPage = Math.ceil(res.data.bwCount / 100)
      }

      trainData.value.totalTime = res.data.totalTime
      //判断是否是教员
      let user
      if (route.query.roomState == 'createUser') {
        user = sendUserList.value[0].id
      } else {
        user = userId.id
      }
      apiSimulationRouterFindPage({
        pageNumber: 1,
        roomId: route.query.id,
        userId: user
      }).then(res => {
        if (res.code == 200) {
          trainData.value.telegraph.curr = res.data.pageVos
        }
      })
      if (trainData.value.telegraph.allPage > 1) {
        apiSimulationRouterFindPage({
          pageNumber: 2,
          roomId: route.query.id,
          userId: user
        }).then(res => {
          if (res.code == 200) {
            trainData.value.telegraph.nextCurr = res.data.pageVos
          }
        })
      }

      if (res.data.stats == 1 && trainTimeRef.value) {
        if (res.data.totalTime && res.data.totalTime != '') {
          trainData.value.duration = parseInt(res.data.totalTime)
        } else {
          trainData.value.duration = 0
        }
        trainTime()
      } else if (res.data.stats == 2 && trainData.value.totalTime && trainData.value.totalTime > 0) {
        nextTick(() => {
          if (trainTimeRef.value) {
            trainTimeRef.value.autoSetTimeAdd(trainData.value.totalTime)
          }
        })
      }
      if (!ws) {
        init()
      }
    })
  }

  // 训练用时
  const autoTime = ref(null)
  // const pageCodes = ref([])
  const trainTime = () => {
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

  const handlePageTurn = num => {
    if (num == 'prev') {
      curr.value--
    } else {
      curr.value++
    }
    apiSimulationRouterFindPage({
      pageNumber: curr.value,
      roomId: route.query.id,
      userId: sendId.value
    }).then(res => {
      if (res.code == 200) {
        a.value = res.data
      }
    })
  }

  const handleActivePutAway = (item, index) => {
    putAwayUserList.value
      .filter(a => a.id != item.id)
      .forEach(b => {
        b.activeState = false
      })
    sendId.value = item.id
    if (route.query.roomState != 'createUser') return
    if (!item.status) return //判断是否训练完毕
    apiSimulationRouterFindPage({
      pageNumber: 1,
      roomId: route.query.id,
      userId: sendId.value
    }).then(res => {
      if (res.code == 200) {
        a.value = res.data
        item.activeState = !item.activeState
        activePutAway.value = item.activeState
        all.value = trainData.value.telegraph.allPage > item.existPageNumber ? trainData.value.telegraph.allPage : item.existPageNumber
      }
    })
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
            setTimeout(() => {
              getApiRoomUserList()
            }, 500)
          }
        })
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
              }
            })
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
    curr
  }
}
