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

export default function useBroadStudent() {
  let selfId = ref()
  const step = ref(1)
  let result = ref({})
  selfId.value = JSON.parse(localStorage.getItem('userInfo')).id
  const route = useRoute()
  const { dots, morseCode, scatter } = useMorse()
  const frequency = ref(1000)
  const dotTime = ref(80)
  const isRefresh = ref(false)//是否刷新
  const isFirst = ref(true) //是否第一次进入页面，播放音频
  const storage = ref({
    prevCode:"",
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
  let socket
  let isOnline = ref(false)
  let roomId
  const trainTimer = ref(null)
  const symbol = ref({
    start: [1, 0, 0, 0, 1],
    end: [0, 1, 0, 1, 0]
  })
  const allCode = [] //播报
  let prevCode = null //上一个字符，包含空格、开始等符号
  let codeIndex = 0 // 当前字符下标
  const {operation} = operationMorseVoice()
  setTimeout(()=>{
    PubSub.subscribe('receiveProcessData',res=>{
      if(res.status==="progress"&&prevCode!==res.key&&isheader.value===false){
        prevCode = res.key
        codeIndex++
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
  },2000)
  onMounted(async () => {
    if (route.query.id && route.query.id != '') {
      roomId = route.query.id
      if (window.localStorage.getItem('broad' + roomId)) {
        storage.value = JSON.parse(window.localStorage.getItem('broad' + roomId))
        newUser.value = false
        codeIndex = storage.value.codeIndex
        prevCode = storage.value.prevCode

      }
      await findTrainDataInfo()
    }
  })

  onBeforeUnmount(() => {
    PubSub.unsubscribe('receiveProcessData')
    if (trainData.value.status == 1) {
      storage.value.visible = true
      storage.value.prevCode = prevCode
      storage.value.codeIndex = codeIndex
      window.localStorage.setItem('broad' + roomId, JSON.stringify(storage.value))
    }
    clearInterval(trainTimer.value)
    if (socket) {
      socket.close()
      socket = null
    }
  })
  window.onbeforeunload = () => {
    if (trainData.value.status == 1) {
      storage.value.visible = true
      storage.value.playCodeIndex = trainData.value.playCodeIndex
      storage.value.playPage = trainData.value.currPag
      window.localStorage.setItem('broad' + roomId, JSON.stringify(storage.value))
    }
    clearInterval(trainTimer.value)
    if (socket) {
      socket.close()
      socket = null
    }
  }

  const login = () => {
    if (socket) return false
    socket = new WebSocket(`${window.wsUrl}/simulation/` + selfId.value + '/' + roomId)
    socket.onopen = e => {}
    socket.onerror = e => {}
    socket.onclose = e => {
      socket = null
      // login()
    }
    socket.onmessage = e => {
      let d = JSON.parse(JSON.parse(e.data).data)
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
    }
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
    uploadUnionTrainResult({
      userId: selfId.value,
      roomId: roomId,
      contentValue: _res
    }).then(res => {
      step.value = 2
      let data = {
        type: 'result',
        existPage: _res.length
      }
      socket.send(JSON.stringify(data))
      findTrainBaoWenInfo(selfId.value, trainData.value['currPag'])
      if (trainData.value['pag'] > 1 && trainData.value['currPag'] < trainData.value['pag']) {
        setTimeout(() => {
          findTrainBaoWenInfo(selfId.value, trainData.value['currPag'] + 1)
        }, 500)
      }
    })
  }

  const playCodeInfo = (type) => {
    isFirst.value = false
    if(type==='continue'&&storage.value.codeIndex>0){
      let newAllCode = JSON.parse(JSON.stringify(allCode));
      let code
      if(storage.value.codeIndex>0){
        code = newAllCode.splice(storage.value.codeIndex-1,newAllCode.length)
      }
      operation({type:"message",data:{
          numType:trainData.value.bwType == 1 ? 'short' : 'mix',
          data:code
        }})
    }else {
      if(isheader.value){
        console.log("播放报头")
        operation({type:'message',data:{
            numType:'long',
            data:header.value
          }})
      }
      else {
        console.log("播放报文")
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
    getRoomDetail({
      roomId: Number(roomId)
    }).then(res => {
      if (res.code === 200) {
        findHeader(route.query.id).then(res=>{
          if(res.data!==null){
            isheader.value = true
            header.value = res.data.content
            if(storage.value.codeIndex>0){
              isheader.value = false
            }
          }
        })
        trainData.value = res.data
        trainData.value['status'] = trainData.value.stats
        if (trainData.value.status == 1) {
          storage.value.visible = true
          storage.value.status = trainData.value.playStatus
        }
        trainData.value['pag'] = Math.ceil(trainData.value.bwCount / 100)
        if (trainData.value.existPageNumber != null && trainData.value.existPageNumber > Math.ceil(trainData.value.bwCount / 100)) {
          trainData.value['pag'] = trainData.value.existPageNumber
        }
        trainData.value['currPag'] = window.localStorage.getItem('broad' + roomId) ? storage.value.playPage : 1
        trainData.value['playCodeIndex'] = window.localStorage.getItem('broad' + roomId) ? storage.value.playCodeIndex : 0
        trainData.value['duration'] = 0

        if (Number(trainData.value.mainSignal) < 40) {
          lowSpeedTime(Number(trainData.value.mainSignal))
        } else {
          let type = trainData.value.bwType == 1 ? 'short' : trainData.value.bwType == 2 ? 'long' : trainData.value.bwType == 3 ? 'letter' : 'mix'
          let cri = ((400 / (trainData.value.mainSignal*1)) * 60 * 1000) / dots[type]
          setTimeout(()=>{
            operation({type:'changeCriterion',data:cri*window.audioSpeedDeviation})
          },1000)
          // operation({type:'changeCriterion',data:countCriterion(Number(trainData.value.mainSignal))})
        }

        findTrainBaoWenInfo(selfId.value, trainData.value['currPag'])
        // if (trainData.value['pag'] > 1 && trainData.value['currPag'] < trainData.value['pag']) {
        //   setTimeout(() => {
        //     findTrainBaoWenInfo(selfId.value, trainData.value['currPag'] + 1)
        //   }, 500)
        // }
        // handlePlayCodeData();//处理为页面数据；
        trainData.value.receiveUser.forEach(item => {
          if (item.id == selfId.value) {
            if (item.userStatus == 1) {
              step.value = 2
              result.value = item
            } else {
              login()
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
    findUserPageBaoWenInfo({
      roomId: Number(route.query.id),
      userId: userId,
      pageNumber: pag
    }).then(res => {
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
          findTrainBaoWenInfo(userId,pag+1)
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
    if (!allBaoWen.value[trainData.value.currPag + 1 + ''] && trainData.value.currPag < trainData.value.pag) {
      findTrainBaoWenInfo(selfId.value, trainData.value.currPag + 1)
    }
  }

  /**
   * 计算码率的点标准时长
   * @param rate
   * @returns {number}
   */
  const countCriterion = rate => {
    let criterion = 0
    let type = trainData.value.bwType == 1 ? 'short' : trainData.value.bwType == 2 ? 'long' : trainData.value.bwType == 3 ? 'letter' : 'mix'
    criterion = parseInt(((400 / rate) * 60 * 1000) / dots[type])
    return criterion
  }

  const lowSpeedTime = rate => {
    let type = trainData.value.bwType == 1 ? 'short' : trainData.value.bwType == 2 ? 'long' : trainData.value.bwType == 3 ? 'letter' : 'mix'
    let ms = dotTime.value
    let ml = ((400 / rate) * 60 * 1000) / dots[type]
    let pr1 = (((ml - ms) * scatter[type].d) / scatter[type].l + ml * 3) / ml
    let pr2 = (((ml - ms) * scatter[type].d) / scatter[type].w + ml * 3) / ml
    let pr3 = (((ml - ms) * scatter[type].d) / scatter[type].g + ml * 5) / ml
    operation({type:'changeRatio',data:{
        dot: 1,
        dash: Number(pr1.toFixed(2)),
        gap: 1,
        word: Number(pr2.toFixed(2)),
        suite: Number(pr3.toFixed(2)),
        leaf: Number(((pr3 / 5) * 7).toFixed(2))
      }})
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
  const againPlayCode = (type) => {
    storage.value.visible = false
    readyForTest()
    if (storage.value.status == 1) {
      trainTimer.value = setInterval(() => {
        storage.value.totalTime++
      }, 1000)
      if(type===0){
        codeIndex = 0
        prevCode = null
        playCodeInfo()
      }else {
        playCodeInfo('continue')
      }
    }
  }

  return {
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
