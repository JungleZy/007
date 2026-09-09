import { onMounted, onUnmounted, ref, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import PublicSocket from '../../../../../../../common/ws/PublicSocket.js'
import {
  getDatagramZuXunPageNumber,
  uploadDatagramResult,
  finishDatagramZuXun,
  startTrainUser
} from '../../../../../../../common/api/datagramZuXun.js'

export default function (trainData, loading, emits) {
  const {ws_connect, sendMessage, closeWebSocket} = PublicSocket()
  const router = useRouter()
  const userInfo = JSON.parse(localStorage.getItem('userInfo'))
  const currPatKeyIndex = ref(0)
  const trainTimeRef = ref(null)
  const patUser = ref({})
  const readyPat = ref(false)
  let isFirstKey = true
  let enterTimer = 0
  let pageTime = 0
  const scorePath = ref('')
  const trainTimer = ref(null)
  const pageCodes = ref([])
  const patWsData = ref({topic: 'pat', id: userInfo.id, log: {key: '', time: 0}})
  router.getRoutes().forEach(route => {
    if (route.name === 'datagramZuXunTrain') scorePath.value = route.path
  })

  const connectWebsocket = () => {
    const url = `/generalTelexPatTrain/${userInfo.id}/${trainData.value.trainId}`
    ws_connect(url, receiveWebSocketMessage)
  }

  const cutTime = ref(5)
  const cutTimer = ref(null)
  const receiveWebSocketMessage = e => {
    const data = JSON.parse(JSON.parse(e.data).data)
    if (data.topic === 'online') {
      message.success('教员已回来！')
    } else if (data.topic === 'offline') {
      message.error('教员已离开！')
    } else if (data.topic === 'begin') {
      message.success('教员已开始训练，准备开始训练！')
      cutTimer.value = setInterval(() => {
        cutTime.value--
        if (cutTime.value === 0) {
          clearInterval(cutTimer.value)
          cutTimer.value = 'begin'
          clearInterval(trainTimer.value)
          initTrainTimeInfo()
          startTrain()
          trainData.value.floorNow = 1
          trainData.value.status = 1
          trainData.value.process = 1
          trainData.value.validTime = 0
        }
      }, 1000)
    } else if (data.topic === 'end') {
      trainData.value.status = 2
      clearInterval(trainTimer.value)
      if (patUser.value.isFinish !== 1) {
        handlerSubmit('end')
      } else {
        router.push({path: scorePath.value, query: {id: trainData.value.trainId, status: 2}})
        emits('changeStatus')
      }
    }
  }

  const startTrain = () => {
    nextTick(() => document.getElementsByTagName('textarea')[0]?.focus())
    window.addEventListener('keydown', keyCodeup)
  }

  let lastTime = null
  const keyCodeup = event => {
    const now = Date.now()
    patWsData.value.log.key = event.key
    patWsData.value.log.time = lastTime === null ? 0 : now - lastTime
    lastTime = now
    sendMessage(patWsData.value)
    if (event.keyCode === 13) enterTimer++
    else enterTimer = 0
    if (event.code === 'F4') switchTelegram('prev')
    if (event.code === 'F5') {
      event.preventDefault?.()
      switchTelegram('next')
    }
    if (isFirstKey) {
      isFirstKey = false
      startTrainUser(trainData.value.trainId)
    }
  }

  const textareaChange = () => {
    if (pageCodes.value[currPatKeyIndex.value]) {
      pageCodes.value[currPatKeyIndex.value] = pageCodes.value[currPatKeyIndex.value].toUpperCase()
    }
    if (enterTimer === 2) {
      switchTelegram('next')
      enterTimer = 0
    }
  }

  const switchTelegram = type => {
    handlerSubmit()
    if (type === 'prev' && trainData.value.floorNow <= 1) {
      message.error('已经是第一页！')
      return false
    }
    if (type === 'next' && trainData.value.floorNow >= trainData.value.pag) {
      message.error('已经是最后一页！')
      return false
    }
    if (type === 'prev') trainData.value.floorNow--
    if (type === 'next') {
      trainData.value.floorNow++
      currPatKeyIndex.value++
      pageCodes.value[currPatKeyIndex.value] = ''
    }
    if (!trainData.value.telegraph[trainData.value.floorNow + 1] && trainData.value.floorNow < trainData.value.pag) {
      getPostTrainKeyInfo(trainData.value.floorNow + 1)
    }
  }

  const getPostTrainKeyInfo = page => {
    getDatagramZuXunPageNumber({
      trainId: trainData.value.trainId,
      userId: userInfo.id,
      pageNumber: page
    }).then(res => {
      if (res.code === 200) {
        res.data.messageContent.forEach(item => {
          item.key = JSON.parse(item.key)
          item.value = []
        })
        trainData.value.telegraph[page - 1] = res.data.messageContent.filter(item => item.sort > -1)
      }
    })
  }

  const handlerSubmit = type => {
    pageTime = pageTime === 0 ? trainData.value.duration : trainData.value.duration - pageTime
    const value = pageCodes.value[currPatKeyIndex.value]
    if (!value) return
    const groups = value.split(' ')
    const speed = (groups.length / (pageTime / 60)).toFixed(1)
    uploadDatagramResult({
      trainId: trainData.value.trainId,
      patValue: value,
      pageNumber: trainData.value.floorNow,
      validTime: pageTime,
      speed: Number(speed)
    }).then(res => {
      if (res.code === 200 && type === 'end') finishTrainInfo()
    })
  }

  const finishTrainInfo = () => {
    patUser.value.isFinish = 1
    sendMessage({topic: 'finish', id: userInfo.id})
    loading.value = true
    finishDatagramZuXun({trainId: trainData.value.trainId, userId: userInfo.id}).then(res => {
      loading.value = false
      if (res.code !== 200) {
        message.error(res.message)
        return
      }
      router.push({path: scorePath.value, query: {id: trainData.value.trainId, status: 2}})
      emits('changeStatus')
    })
  }

  const readyTrainPat = type => {
    readyPat.value = true
    if (type === 1) {
      const saved = JSON.parse(window.localStorage.getItem('datagramZuXun' + trainData.value.trainId))
      if (saved) {
        trainData.value.floorNow = saved.patPage
        trainData.value.validTime = saved.time
        trainData.value.speed = saved.speed
        currPatKeyIndex.value = saved.patKeyIndex
      }
    } else {
      trainData.value.floorNow = 1
      currPatKeyIndex.value = 0
      trainData.value.validTime = 0
      trainData.value.errorNumber = 0
      trainData.value.accuracy = '0'
      trainData.value.speed = 0
      if (trainData.value.status === 1) {
        trainData.value.process = 1
        cutTimer.value = 'begin'
      }
    }
    startTrain()
    nextTick(() => document.getElementsByTagName('textarea')[0]?.focus())
    sendMessage({topic: 'ready', id: userInfo.id})
  }

  const timeAreaShow = t => {
    const h = Math.floor(t / 1000 / 60 / 60 % 24)
    const m = Math.floor(t / 1000 / 60 % 60)
    const s = Math.floor(t / 1000 % 60)
    trainTimeRef.value.h = h > 9 ? `${h}` : `0${h}`
    trainTimeRef.value.m = m > 9 ? `${m}` : `0${m}`
    trainTimeRef.value.s = s > 9 ? `${s}` : `0${s}`
  }

  const initTrainTimeInfo = () => {
    if (!trainData.value.validTime) trainData.value.validTime = 0
    trainTimer.value = setInterval(() => {
      trainData.value.validTime += 1
      let codeLength = 0
      pageCodes.value.forEach(item => { codeLength += item.replaceAll(' ', '').length })
      trainData.value.speed = (codeLength / (trainData.value.validTime / 60) / 4).toFixed(1) * 1
      timeAreaShow(trainData.value.validTime * 1000)
    }, 1000)
  }

  onUnmounted(() => {
    window.removeEventListener('keydown', keyCodeup)
    clearInterval(trainTimer.value)
    closeWebSocket()
  })

  return {
    timeAreaShow,
    connectWebsocket,
    initTrainTimeInfo,
    patUser,
    readyPat,
    getPostTrainKeyInfo,
    readyTrainPat,
    currPatKeyIndex,
    pageCodes,
    textareaChange,
    switchTelegram,
    cutTime,
    cutTimer,
    trainTimeRef,
    endTrain: () => handlerSubmit('end')
  }
}
