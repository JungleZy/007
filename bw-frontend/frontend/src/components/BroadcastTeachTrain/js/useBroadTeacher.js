import { message, Modal } from 'ant-design-vue'
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { getRoomDetail } from '../../../common/api/broaddcastTeacheingApi'
import { useRoute } from 'vue-router'
import Voice from '../../../common/utils/MorseVoice'
import { findUserPageBaoWenInfo } from '../../../common/api/UnionApi'
import useMorse from '../../../common/mixin/useMorse'
import operationMorseVoice from "../../../common/utils/voice/operationMorseVoice";
import {PubSub} from "../../../common/utils/PubSub";
import {findHeader} from "../../../common/api/ReceiveApi";

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
  const {operation} = operationMorseVoice()
  const allCode = [] //播报
  // 查看成员上传的成绩
  const takeCheck = (item, index) => {
    if (trainData.value.status < 2) return false
    check.value = check.value == null ? index : null
    if (check.value == null) return false
    result.value.curr = 1
    result.value.existPage = trainData.value.pag
    if (item.existPageNumber > trainData.value.pag) {
      result.value.existPage = item.existPageNumber
    }
    result.value.user = item
    findUserTrainBaoWenInfo(item.id, 1)
    if (result.value.existPage > 1 && result.value.curr < result.value.existPage) {
      findUserTrainBaoWenInfo(item.id, 2)
    }
  }
  /**
   * 根据页码查询学员的报文信息
   * @param userId
   * @param pag
   */
  const findUserTrainBaoWenInfo = (userId, pag) => {
    findUserPageBaoWenInfo({
      roomId: Number(route.query.id),
      userId: userId,
      pageNumber: pag
    }).then(res => {
      if (res.code === 200) {
        result.value.res[pag + ''] = res.data
      }
    })
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
    if (!result.value.res[result.value.curr + 1 + ''] && result.value.curr < result.value.existPage) {
      findUserTrainBaoWenInfo(result.value.user.id, result.value.curr + 1)
    }
  }
  const stopIndex = 0
  let selfId = ref()
  const dotTime = ref(80)
  selfId.value = JSON.parse(localStorage.getItem('userInfo')).id
  let socket
  const storage = ref({
    playPage: 1,
    totalTime: 0,
    prevCode:"",
    codeIndex:0
  })
  const activeIndex = ref(null)
  let prevCode = null //上一个字符，包含空格、开始等符号
  let codeIndex = 0 // 当前字符下标
  //注册WebSocket
  const login = () => {
    if (trainData.value.status == 2) {
      return
    }
    socket = new WebSocket(window.wsUrl + '/simulation/' + selfId.value + '/' + roomId)
    socket.onopen = e => {
      console.log('开始侦听')
    }
    socket.onerror = e => {
      // console.log(e)
    }
    socket.onclose = e => {
      // console.log(e)
      socket = null
      // login()
    }
    socket.onmessage = e => {
      let data = JSON.parse(JSON.parse(e.data).data)
      if (data.type == 'online') {
        trainData.value.receiveUser.forEach(item => {
          if (item.id == data.id) {
            item.status = 2
          }
        })
      } else if (data.type == 'result') {
        trainData.value.receiveUser.forEach(item => {
          if (item.id == data.id) {
            item.userStatus = 1
            item.existPageNumber = data.existPage
          }
        })
      } else {
        if (data.id) {
          //人员上线
          trainData.value.receiveUser.forEach(item => {
            if (item.id == data.id) {
              item.status = data.type
            }
          })
        }
      }
    }
  }

  setTimeout(()=>{
    PubSub.subscribe('receiveProcessData',res=>{
      if(res.status==="progress"&&prevCode!==res.key&&isheader.value===false){
        prevCode = res.key
        codeIndex++
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
  },2000)
  onBeforeUnmount(() => {
    PubSub.unsubscribe('receiveProcessData')
    if (trainData.value.status < 2) {
      storage.value.playPage = trainData.value.currPag
      storage.value.totalTime = trainData.value.totalTime
      storage.value.prevCode = prevCode
      storage.value.codeIndex = codeIndex
      window.localStorage.setItem('broad' + roomId, JSON.stringify(storage.value))
    }
    if (socket) {
      socket.close()
      socket = null
    }
  })
  window.onbeforeunload = () => {
    if (trainData.value.status < 2) {
      storage.value.playCodeIndex = trainData.value.playCodeIndex
      storage.value.playKeyIndex = trainData.value.currIndex
      storage.value.playPage = trainData.value.currPag
      storage.value.totalTime = trainData.value.totalTime
      window.localStorage.setItem('broad' + roomId, JSON.stringify(storage.value))
    }
    voice.clear()
    if (socket) {
      socket.close()
      socket = null
    }
  }
  let roomId
  onMounted(async () => {
    if (route.query.id && route.query.id !== '') {
      roomId = route.query.id
      if (window.localStorage.getItem('broad' + roomId)) {
        storage.value = JSON.parse(window.localStorage.getItem('broad' + roomId))
        codeIndex = storage.value.codeIndex
        prevCode = storage.value.prevCode
        console.log(storage.value);
      }
      await findTrainDataInfo()
      login()
    }
  })
  const frequency = ref(1000)
  const symbol = ref({
    start: [1, 0, 0, 0, 1],
    end: [0, 1, 0, 1, 0]
  })
  let voice = new Voice({ fre: frequency.value })
  let isStop = ref(false)

  // 暂停、继续
  const stop = (e,type) => {
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
  const continuePlay = (type)=>{
    if(type===0){
      //重新播放
      playVoice()
      codeIndex = 0
      prevCode = null
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
        trainData.value.playStatus = 0
        trainData.value['status'] = trainData.value.stats
        trainData.value['pag'] = Math.ceil(trainData.value.bwCount / 100)
        trainData.value['currPag'] = window.localStorage.getItem('broad' + roomId) ? storage.value.playPage : 1
        isStop.value = true
        //回显时间
        if (trainData.value.stats == 2) {
          trainData.value.totalTime = trainData.value.totalTime != null ? trainData.value.totalTime : 0
        } else {
          trainData.value.totalTime = window.localStorage.getItem('broad' + roomId) ? storage.value.totalTime : 0
        }
        trainData.value['currIndex'] = window.localStorage.getItem('broad' + roomId) ? storage.value.playKeyIndex : 0
        trainData.value['playCodeIndex'] = window.localStorage.getItem('broad' + roomId) ? storage.value.playCodeIndex : 0
        if (Number(trainData.value.mainSignal) < 40) {
          lowSpeedTime(Number(trainData.value.mainSignal))
        } else {
          let type = trainData.value.bwType == 1 ? 'short' : trainData.value.bwType == 2 ? 'long' : trainData.value.bwType == 3 ? 'letter' : 'mix'
          let cri = ((400 / (trainData.value.mainSignal*1)) * 60 * 1000) / dots[type]
         setTimeout(()=>{
           operation({type:'changeCriterion',data:cri*window.audioSpeedDeviation})
         },1000)
        }

        findTrainBaoWenInfo(selfId.value, trainData.value['currPag'])
        // if (trainData.value['pag'] > 1 && trainData.value['currPag'] < trainData.value['pag']) {
        //   setTimeout(() => {
        //     findTrainBaoWenInfo(selfId.value, trainData.value['currPag'] + 1)
        //   }, 500)
        // }
        if (countDown.value) {
          countDown.value.autoSetTimeAdd(trainData.value.totalTime)
        }
        if(res.data.stats===1){
          isRefresh.value = true
        }
      }
    })
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
  const openTrainInfo = () => {
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
