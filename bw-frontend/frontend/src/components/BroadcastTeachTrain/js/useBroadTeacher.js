import { message, Modal } from 'ant-design-vue'
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { getRoomDetail } from '../../../common/api/broaddcastTeacheingApi'
import { useRoute } from 'vue-router'
import { findUserPageBaoWenInfo } from '../../../common/api/UnionApi'
import useMorse from '../../../common/mixin/useMorse'
import operationMorseVoice from "../../../common/utils/voice/operationMorseVoice";
import {PubSub} from "../../../common/utils/PubSub";
import {findHeader} from "../../../common/api/ReceiveApi";
import {calculateTiming} from '../../../common/utils/voice/MorseVoiceHighPerformance'
import SocketConnection from '../../../common/ws/SocketConnection'
import useSimulationRecovery from './useSimulationRecovery'

export default function useBroadTeacher(countDown) {
  const trainTimeRef = ref(null)
  const route = useRoute()
  const trainData = ref({
    status: 0
  })
  const allBaoWen = ref({})
  const { dots, morseCode, scatter } = useMorse()
  const isRefresh = ref(false)//是否刷新
  let check = ref(null)
  let result = ref({
    user: null,
    curr: 1,
    existPage: 0,
    res: {}
  })
  const maskShow = ref(true)
  const header = ref("")
  const isheader = ref(false)
  const {operation, ensureReady} = operationMorseVoice()
  const allCode = [] //播报
  // 查看成员上传的成绩
  const takeCheck = (item, index) => {
    if (trainData.value.status < 2) return false
    check.value = check.value === index ? null : index
    if (check.value == null) return false
    result.value.res = {}
    result.value.curr = 1
    result.value.existPage = trainData.value.pag
    if (item.existPageNumber > trainData.value.pag) {
      result.value.existPage = item.existPageNumber
    }
    result.value.user = item
    refresh()
  }
  /**
   * 根据页码查询学员的报文信息
   * @param userId
   * @param pag
   */
  const findUserTrainBaoWenInfo = (userId, pag) => {
    return findUserPageBaoWenInfo({
      roomId: Number(route.query.id),
      userId: userId,
      pageNumber: pag
    }).then(res => {
      if (res.code !== 200) throw new Error(res.msg || '读取学员答案失败')
      if (result.value.user?.id === userId) result.value.res[pag + ''] = res.data
    }).catch(error => { recoveryError.value = error.message; throw error })
  }
  /**
   * 弹窗切换分页
   * @param type
   */
  const modelPageTurn = type => {
    if (type == 'next') {
      if (result.value.curr == result.value.existPage) {
        return false
      }
      result.value.curr++
    } else {
      if (result.value.curr == 1) {
        return false
      }
      result.value.curr--
    }
    refresh()
  }
  const stopIndex = 0
  let selfId = ref()
  selfId.value = JSON.parse(localStorage.getItem('userInfo')).id
  let socket = new SocketConnection()
  let initialized = false
  const { refresh, stop: stopRecovery, recoveryError, socketStatus, onState } = useSimulationRecovery(async () => {
    if (!initialized) {
      await findTrainDataInfo()
      initialized = true
    } else {
      const response = await getRoomDetail({ roomId: Number(roomId) })
      if (response.code !== 200) throw new Error(response.msg || '读取训练详情失败')
      const data = response.data
      trainData.value.receiveUser = data.receiveUser
      trainData.value.status = data.stats
      trainData.value.stats = data.stats
      trainData.value.pag = data.isCable == 1 ? Number(data.pageCount) : Math.ceil(data.bwCount / 100)
      if (data.stats == 2) trainData.value.totalTime = data.totalTime
    }
    if (check.value !== null && result.value.user) {
      const user = trainData.value.receiveUser.find(item => item.id === result.value.user.id)
      if (!user) throw new Error('该学员已不在训练中')
      result.value.user = user
      result.value.existPage = Math.max(trainData.value.pag, Number(user.existPageNumber) || 0)
      result.value.curr = Math.max(1, Math.min(result.value.curr, result.value.existPage))
      result.value.res = {}
      await findUserTrainBaoWenInfo(user.id, result.value.curr)
    }
  }, () => !initialized || trainData.value.status != 2 || trainData.value.receiveUser?.some(user => user.userStatus != 1))
  const storage = ref({
    playPage: 1,
    totalTime: 0,
    codeIndex:0
  })
  const activeIndex = ref(null)
  let codeIndex = 0 // 已进入字符在原始allCode中的一基位置；恢复时重播该字符
  //注册WebSocket
  const login = () => {
    if (!socket) return
    socket.connect(window.wsUrl + '/simulation/' + selfId.value + '/' + roomId, event => {
      try {
        const envelope = JSON.parse(event.data)
        if (envelope.code === -1) throw new Error(envelope.msg || '连接被拒绝')
        const data = typeof envelope.data === 'string' ? JSON.parse(envelope.data) : envelope.data
        if (data.type === 'result' || data.type == 4) {
          if (data.roomId == null || Number(data.roomId) === Number(roomId)) refresh()
        } else if (data.id) {
          trainData.value.receiveUser?.forEach(item => {
            if (item.id === data.id) item.status = data.type === 'online' ? 2 : data.type
          })
        }
      } catch (error) { recoveryError.value = error.message || '训练通知解析失败' }
    }, refresh, onState)
  }

    const audioSubscription = PubSub.subscribe('receiveProcessData',res=>{
      if(res.status === 'progress' && !isheader.value && Number.isInteger(res.sourceIndex)){
        codeIndex = res.sourceIndex + 1
      }
      if (res.status==='finish') {
        if(isheader.value&&trainData.value.status < 2){
          isheader.value = false
          playVoice()
          return
        }
        message.success('播报完毕！')
        activeIndex.value = res
      }
    })
  onBeforeUnmount(() => {
    window.removeEventListener('beforeunload', beforeUnload)
    stopRecovery()
    clearInterval(autoTime.value)
    PubSub.unsubscribe(audioSubscription)
    if (trainData.value.status < 2) {
      storage.value.playPage = trainData.value.currPag
      storage.value.totalTime = trainData.value.totalTime
      storage.value.codeIndex = codeIndex
      window.localStorage.setItem('broad' + roomId, JSON.stringify(storage.value))
    }
    if (socket) {
      socket.close()
      socket = null
    }
  })
  const beforeUnload = () => {
    stopRecovery()
    if (trainData.value.status < 2) {
      storage.value.playCodeIndex = trainData.value.playCodeIndex
      storage.value.playKeyIndex = trainData.value.currIndex
      storage.value.playPage = trainData.value.currPag
      storage.value.totalTime = trainData.value.totalTime
      storage.value.codeIndex = codeIndex
      window.localStorage.setItem('broad' + roomId, JSON.stringify(storage.value))
    }
    operation({type: 'stop'})
    if (socket) {
      socket.close()
      socket = null
    }
  }
  window.addEventListener('beforeunload', beforeUnload)
  let roomId
  onMounted(async () => {
    if (route.query.id && route.query.id !== '') {
      roomId = route.query.id
      if (window.localStorage.getItem('broad' + roomId)) {
        storage.value = JSON.parse(window.localStorage.getItem('broad' + roomId))
        codeIndex = Number(storage.value.codeIndex) || 0
      }
      await refresh()
      login()
    }
  })
  const frequency = ref(1000)
  const symbol = ref({
    start: [1, 0, 0, 0, 1],
    end: [0, 1, 0, 1, 0]
  })
  let isStop = ref(false)

  // 暂停、继续
  const stop = async (e,type) => {
    if (!e && !await ensureReady()) return
    isStop.value = !isStop.value
    if (e) {
      //暂停
      let data = {
        type: 2
      }
      socket.send(JSON.stringify(data))
      storage.value.totalTime = trainData.value.totalTime
      clearInterval(autoTime.value)
      autoTime.value = null
      operation({type:'pause'})
    } else {
      //继续
      let data = {
        type: 3
      }
      socket.send(JSON.stringify(data))
      trainTime()
      // if(isRefresh.value){
      //   isRefresh.value = false
      //   playVoice()
      // }else {
      //   operation({type:'continue'})
      // }
      operation({type:'continue'})
    }
  }
  //退出页面重新进入
  const continuePlay = async (type)=>{
    if (!await ensureReady()) return
    if(type===0){
      //重新播放
      playVoice()
      codeIndex = 0
    }else {
      //断点继续
      playVoice('continue')
    }
    isStop.value = !isStop.value
    maskShow.value = false
    let data = {type: 3}
    socket.send(JSON.stringify(data))
    trainTime()
  }
  const playVoice = (type) => {
    if(type === 'continue' && codeIndex > 0){
      isheader.value = false
      const sourceOffset = codeIndex - 1
      operation({type: 'message', data: {
        numType: trainData.value.bwType == 1 ? 'short' : 'mix',
        data: allCode.slice(sourceOffset),
        sourceOffset
      }})
    }else {
      if(isheader.value){
        operation({type:'message',data:{
            numType:'long',
            data:header.value
          }})
      }
      else {
        operation({type:"message",data:{
            numType:trainData.value.bwType == 1 ? 'short' : 'mix',
            data:allCode
          }})
      }
    }

  }
  const findTrainDataInfo = () => {
    return getRoomDetail({
      roomId: Number(roomId)
    }).then(async res => {
      if (res.code !== 200) throw new Error(res.msg || '读取训练详情失败')
      if (res.code === 200) {
        const headerResponse = await findHeader(route.query.id)
        if (headerResponse.code !== 200) throw new Error(headerResponse.msg || '读取报头失败')
        isheader.value = headerResponse.data != null && !(storage.value.codeIndex > 0)
        header.value = headerResponse.data?.content || ''
        trainData.value = res.data
        trainData.value.playStatus = 0
        trainData.value['status'] = trainData.value.stats
        trainData.value['pag'] = res.data.isCable == 1 ? Number(res.data.pageCount) : Math.ceil(trainData.value.bwCount / 100)
        trainData.value['currPag'] = res.data.stats == 2 ? 1 : (storage.value.playPage || 1)
        isStop.value = true
        //回显时间
        if (trainData.value.stats == 2) {
          trainData.value.totalTime = trainData.value.totalTime != null ? trainData.value.totalTime : 0
        } else {
          trainData.value.totalTime = window.localStorage.getItem('broad' + roomId) ? storage.value.totalTime : 0
        }
        trainData.value['currIndex'] = window.localStorage.getItem('broad' + roomId) ? storage.value.playKeyIndex : 0
        trainData.value['playCodeIndex'] = window.localStorage.getItem('broad' + roomId) ? storage.value.playCodeIndex : 0
        applyAudioTiming(Number(trainData.value.mainSignal))

        allCode.length = 0
        await findTrainBaoWenInfo(selfId.value, 1)
        if (countDown.value) {
          countDown.value.autoSetTimeAdd(trainData.value.totalTime)
        }
        if(res.data.stats===1){
          isRefresh.value = true
        }
      }
    })
  }
  const applyAudioTiming = rate => {
    const type = trainData.value.bwType == 1 ? 'short' : trainData.value.bwType == 2 ? 'long' : trainData.value.bwType == 3 ? 'letter' : 'mix'
    operation({type: 'configure', data: {...calculateTiming({rate, type, lowRate: rate < 35}), frequency: frequency.value, volume: 1, model: true}})
  }
  /**
   * 根据页码查询用户的报文信息
   * @param userId
   * @param pag
   */
  const findTrainBaoWenInfo = (userId, pag) => {
    return findUserPageBaoWenInfo({
      roomId: Number(route.query.id),
      userId: userId,
      pageNumber: pag
    }).then(res => {
      if (res.code !== 200) throw new Error(res.msg || '读取报底失败')
      if (res.code === 200) {
        allBaoWen.value[pag + ''] = res.data
        if (pag == trainData.value['currPag']) {
          trainData.value.content = res.data.pageVos
        }
        res.data.pageVos.forEach((item,index)=>{
          if(index===0&&trainData.value.isStartSign===1){
            allCode.push('#')
            allCode.push(' ')
          }
          allCode.push(...item.key.split(''))
          allCode.push(' ')
        })
        if(pag<trainData.value['pag']){
          allCode.push('/')
          allCode.push(' ')
          return findTrainBaoWenInfo(userId,pag+1)
        }else {
          message.success({content:'报底加载完毕！',key:'noticeOk'})
          if(trainData.value.isStartSign===0){
            allCode.unshift(' ')
            allCode.unshift('#')
          }
          allCode.push('!')
        }
        console.log(allCode)
        // handlePlayCodeData(pag, res.data)
      }
    })
  }
  const handlePlayCodeData = (pag, _data) => {
    if (pag == 1) {
      _data.pageCode.push(...symbol.value.start)
    }
    _data.pageCode.push(4)
    let keyArr = [],
        codeArr = [],
        type = trainData.value.bwType == 1 ? 'short' : 'mix'
    _data.pageVos.map((item, x) => {
      keyArr = item.key.split('')
      keyArr.map((key, k) => {
        codeArr = morseCode[type][key].value.split('').map(c => Number(c))
        _data.pageCode.push(...codeArr)
        if (k < keyArr.length - 1) {
          _data.pageCode.push(2)
        }
      })
      if (x < _data.pageVos.length - 1) {
        _data.pageCode.push(3)
      }
    })
    if (pag == trainData.value['pag']) {
      _data.pageCode.push(4)
      _data.pageCode.push(...symbol.value.end)
    }
  }
  const autoTime = ref(null)
  const trainTime = () => {
    autoTime.value = setInterval(() => {
      trainData.value.totalTime++
      if (countDown.value) {
        countDown.value.autoSetTimeAdd(trainData.value.totalTime)
      }
    }, 1000)
  }
  /**
   /**
   * 切换分页
   * @param type
   */
  const pageTurn = type => {
    if (type == 'next') {
      if (trainData.value.currPag == trainData.value.pag) {
        return false
      }
      trainData.value.currPag++
    } else {
      if (trainData.value.currPag == 1) {
        return false
      }
      trainData.value.currPag--
    }
    if (allBaoWen.value[trainData.value.currPag + '']) {
      trainData.value.content = allBaoWen.value[trainData.value.currPag + ''].pageVos
    }
    if (!allBaoWen.value[trainData.value.currPag + 1 + ''] && trainData.value.currPag < trainData.value.pag) {
      findTrainBaoWenInfo(selfId.value, trainData.value.currPag + 1)
    }
  }

  /**
   * 开启训练
   */
  const openTrainInfo = async () => {
    if (!await ensureReady()) return
    maskShow.value = false
    let data = {
      type: 1
    }
    socket.send(JSON.stringify(data))
    trainData.value.status = 1
    isStop.value = false
    trainData.value.playCodeIndex = 0
    trainTime()
    playVoice()
  }
  /**
   * 关闭训练
   */
  const closeTrainInfo = () => {
    let data = {
      type: 4,
      count: trainData.value.totalTime
    }
    socket.send(JSON.stringify(data))
    trainData.value.status = 2
    clearInterval(autoTime.value)
    operation({type:'stop'})
    window.localStorage.removeItem('broad' + roomId)
  }
  return {
    recoveryError,
    socketStatus,
    trainTimeRef,
    trainData,
    openTrainInfo,
    closeTrainInfo,
    pageTurn,
    trainTime,
    stop,
    isStop,
    check,
    takeCheck,
    result,
    allBaoWen,
    maskShow,
    modelPageTurn,
    continuePlay
  }
}
