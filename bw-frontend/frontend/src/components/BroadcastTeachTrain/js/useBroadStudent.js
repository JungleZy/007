import { message, Modal } from 'ant-design-vue'
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { getRoomDetail } from '../../../common/api/broaddcastTeacheingApi'
import { partTimeFormatInfo } from '../../../common/utils/Utils.js'
import { useRoute } from 'vue-router'
import { findUserPageBaoWenInfo, uploadUnionTrainResult } from '../../../common/api/UnionApi'
import useMorse from '../../../common/mixin/useMorse'
// import {partTimeFormatInfo} from "../../../common/utils/Utils";
import operationMorseVoice from "../../../common/utils/voice/operationMorseVoice";
import {PubSub} from "../../../common/utils/PubSub";
import {findHeader} from "../../../common/api/ReceiveApi";
import {calculateTiming} from '../../../common/utils/voice/MorseVoiceHighPerformance'
import SocketConnection from '../../../common/ws/SocketConnection'
import useSimulationRecovery from './useSimulationRecovery'

export default function useBroadStudent() {
  let selfId = ref()
  const step = ref(1)
  let result = ref({})
  selfId.value = JSON.parse(localStorage.getItem('userInfo')).id
  const route = useRoute()
  const { dots, morseCode, scatter } = useMorse()
  const frequency = ref(1000)
  const isRefresh = ref(false)//是否刷新
  const isFirst = ref(true) //是否第一次进入页面，播放音频
  const storage = ref({
    codeIndex:0,
    totalTime: 0,
    status: 0,
    visible: false
  })
  const allBaoWen = ref({})
  const newUser = ref(true)
  const scoreData = ref({})
  const header = ref("")
  const isheader = ref(false)
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
      trainData.value.pag = Math.max(data.isCable == 1 ? Number(data.pageCount) : Math.ceil(data.bwCount / 100), Number(data.existPageNumber) || 0)
      const user = data.receiveUser.find(item => item.id === selfId.value)
      step.value = user?.userStatus == 1 ? 2 : 1
      result.value = user || {}
      if (data.stats == 2) {
        storage.value.status = 0
        clearInterval(trainTimer.value)
        operation({type: 'stop'})
      }
    }
    if (step.value == 2) {
      trainData.value.currPag = Math.max(1, Math.min(trainData.value.currPag || 1, trainData.value.pag))
      const page = trainData.value.currPag
      const response = await findUserPageBaoWenInfo({ roomId: Number(roomId), userId: selfId.value, pageNumber: page })
      if (response.code !== 200) throw new Error(response.msg || '读取提交答案失败')
      if (trainData.value.currPag === page) allBaoWen.value = { [page]: response.data }
    }
  }, () => !initialized || trainData.value.status != 2 || step.value != 2)
  let isOnline = ref(false)
  let roomId
  const trainTimer = ref(null)
  const symbol = ref({
    start: [1, 0, 0, 0, 1],
    end: [0, 1, 0, 1, 0]
  })
  const allCode = [] //播报
  let codeIndex = 0 // 已进入字符在原始allCode中的一基位置；恢复时重播该字符
  const {operation, ensureReady} = operationMorseVoice()
    const audioSubscription = PubSub.subscribe('receiveProcessData',res=>{
      if(res.status === 'progress' && !isheader.value && Number.isInteger(res.sourceIndex)){
        codeIndex = res.sourceIndex + 1
      }
      if (res.status==='finish') {
        if(isheader.value&&trainData.value.status < 2){
          isheader.value = false
          playCodeInfo()
          return
        }
        message.success('播报完毕！')
        trainData.value.currPag = 1
        trainData.value.status = 2
        storage.value.status = 0
        clearInterval(trainTimer.value)
      }
    })
  onMounted(async () => {
    if (route.query.id && route.query.id != '') {
      roomId = route.query.id
      if (window.localStorage.getItem('broad' + roomId)) {
        storage.value = JSON.parse(window.localStorage.getItem('broad' + roomId))
        newUser.value = false
        codeIndex = Number(storage.value.codeIndex) || 0

      }
      await refresh()
      login()
    }
  })

  onBeforeUnmount(() => {
    window.removeEventListener('beforeunload', beforeUnload)
    stopRecovery()
    PubSub.unsubscribe(audioSubscription)
    if (trainData.value.status == 1) {
      storage.value.visible = true
      storage.value.codeIndex = codeIndex
      window.localStorage.setItem('broad' + roomId, JSON.stringify(storage.value))
    }
    clearInterval(trainTimer.value)
    if (socket) {
      socket.close()
      socket = null
    }
  })
  const beforeUnload = () => {
    stopRecovery()
    if (trainData.value.status == 1) {
      storage.value.visible = true
      storage.value.playCodeIndex = trainData.value.playCodeIndex
      storage.value.playPage = trainData.value.currPag
      storage.value.codeIndex = codeIndex
      window.localStorage.setItem('broad' + roomId, JSON.stringify(storage.value))
    }
    clearInterval(trainTimer.value)
    if (socket) {
      socket.close()
      socket = null
    }
  }
  window.addEventListener('beforeunload', beforeUnload)

  const login = () => {
    if (!socket) return
    socket.connect(`${window.wsUrl}/simulation/` + selfId.value + '/' + roomId, e => {
      let d
      try {
        const envelope = JSON.parse(e.data)
        if (envelope.code === -1) throw new Error(envelope.msg || '连接被拒绝')
        d = typeof envelope.data === 'string' ? JSON.parse(envelope.data) : envelope.data
        if (!d || typeof d !== 'object') throw new Error('训练通知格式无效')
      } catch (error) {
        recoveryError.value = error.message || '训练通知解析失败'
        return
      }
      if (d.type === 'result') { refresh(); return }
      if (trainData.value.status < 2) {
        if (d.type == 1) {
          //开始
          message.success('考官已开始')
          trainData.value.status = 1
          storage.value.status = 1
          trainTimer.value = setInterval(() => {
            storage.value.totalTime++
          }, 1000)
          playCodeInfo()
        } else if (d.type == 2) {
          message.error('考官暂停')
          clearInterval(trainTimer.value)
          storage.value.status = 0
          operation({type:'pause'})
        } else if (d.type == 3) {
          message.success('考官继续')
          storage.value.status = 1
          if (!storage.value.visible) {
            trainTimer.value = setInterval(() => {
              storage.value.totalTime++
            }, 1000)
            if(codeIndex===0){
              trainData.value.status = 1
              storage.value.status = 1
              trainTimer.value = setInterval(() => {
                storage.value.totalTime++
              }, 1000)
              playCodeInfo()
              isRefresh.value = false
            }else {
              if(isFirst.value){
                playCodeInfo("continue")
              }else {
                operation({type:'continue'})
              }
            }
            // playCodeInfo()
            // operation({type:'continue'})
          }
        } else if (d.type == 4) {
          message.error('考官结束')
          trainData.value.currPag = 1
          trainData.value.status = 2
          storage.value.status = 0
          clearInterval(trainTimer.value)
          operation({type:'stop'})
        }
      }
    }, refresh, onState)
  }

  const handleScoreData = res => {
    res.map((pag, p) => {
      if (!scoreData.value.keyBody[p]) {
        scoreData.value.keyBody[p] = []
      }
      pag.map((row, r) => {
        row.map((col, c) => {
          if (!scoreData.value.keyBody[p][r * 10 + c] && r < 10 && c < 10) {
            scoreData.value.keyBody[p][r * 10 + c] = { key: ['--'], copyKey: [] }
          }
          if (r < 10 && c < 10) {
            scoreData.value.keyBody[p][r * 10 + c].copyKey = col.split('')
          }
        })
      })
    })
  }

  const fillInTrainResult = res => {
    let arr,
        _res = []
    res.map(pag => {
      arr = []
      pag.map(row => {
        row.map(col => {
          arr.push(col[0])
        })
      })
      _res.push(JSON.stringify(arr))
    })
    return uploadUnionTrainResult({
      roomId: roomId,
      contentValue: _res
    }).then(res => {
      if (res.code !== 200) throw new Error(res.msg || '提交答案失败')
      return refresh()
    }).catch(error => {
      recoveryError.value = error.message || '提交答案失败'
      message.error(recoveryError.value)
    })
  }

  const playCodeInfo = (type) => {
    if (!operation({type: 'ready'})) {
      operation({type: 'message', data: {data: []}})
      clearInterval(trainTimer.value)
      storage.value.visible = true
      return false
    }
    isFirst.value = false
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
  const readyForTest = () => {
    let data = {
      type: 'online'
    }
    isOnline.value = true
    socket.send(JSON.stringify(data))
  }
  const trainTimeRef = ref(null)
  const trainData = ref({
    status: 0
  })
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
        trainData.value['status'] = trainData.value.stats
        if (trainData.value.status == 1) {
          storage.value.visible = true
          storage.value.status = trainData.value.playStatus
        }
        trainData.value['pag'] = Math.max(res.data.isCable == 1 ? Number(res.data.pageCount) : Math.ceil(res.data.bwCount / 100), Number(res.data.existPageNumber) || 0)
        trainData.value['currPag'] = res.data.stats == 2 ? 1 : (storage.value.playPage || 1)
        trainData.value['playCodeIndex'] = window.localStorage.getItem('broad' + roomId) ? storage.value.playCodeIndex : 0
        trainData.value['duration'] = 0

        applyAudioTiming(Number(trainData.value.mainSignal))

        allCode.length = 0
        await findTrainBaoWenInfo(selfId.value, 1)
        trainData.value.receiveUser.forEach(item => {
          if (item.id == selfId.value) {
            if (item.userStatus == 1) {
              step.value = 2
              result.value = item
            }
          }
        })
      }
    })
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
    refresh()
  }

  const applyAudioTiming = rate => {
    const type = trainData.value.bwType == 1 ? 'short' : trainData.value.bwType == 2 ? 'long' : trainData.value.bwType == 3 ? 'letter' : 'mix'
    operation({type: 'configure', data: {...calculateTiming({rate, type, lowRate: rate < 35}), frequency: frequency.value, volume: 1, model: true}})
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

  /**
   * 学员回来继续抄收
   * @param codeIndex
   * @param currPag
   */
  const againPlayCode = async (type) => {
    if (!await ensureReady()) return
    storage.value.visible = false
    readyForTest()
    if (storage.value.status == 1) {
      trainTimer.value = setInterval(() => {
        storage.value.totalTime++
      }, 1000)
      if(type===0){
        codeIndex = 0
        playCodeInfo()
      }else {
        playCodeInfo('continue')
      }
    }
  }

  return {
    recoveryError,
    socketStatus,
    handleScoreData,
    findTrainDataInfo,
    trainData,
    isOnline,
    readyForTest,
    step,
    result,
    storage,
    newUser,
    fillInTrainResult,
    againPlayCode,
    allBaoWen,
    pageTurn
  }
}
