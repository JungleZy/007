import { ref, onMounted, onBeforeUnmount, onUnmounted, watch, createVNode, inject, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import { getReceiveTrainDetails, startReceivePostTrain, endReceivePostTrain, resetReceivePostTrain, apiPostTickerTapeTrainFindPage,findHeader } from '../../../../../../common/api/ReceiveApi.js'
import { PubSub } from '../../../../../../common/utils/PubSub'
import { ExclamationCircleOutlined } from '@ant-design/icons-vue'
import { deepClone } from '../../../../../../common/utils/Utils.js'
import { all } from 'ramda'
import operationMorseVoice from "../../../../../../common/utils/voice/operationMorseVoice";
import {calculateTiming} from '../../../../../../common/utils/voice/MorseVoiceHighPerformance'

export default function telegramList(wpmTOmm) {
  const receiveBgRef = ref(null)
  const trainTimeRef = ref(null)
  const trainTimer = ref(null)
  const route = useRoute()
  const receiveData = ref({})
  const audioCodeType = () => Number(receiveData.value.type) === 1 ? 'letter' : Number(receiveData.value.type) === 2 ? 'mix' : Number(receiveData.value.codeShort) === 0 ? 'short' : 'long'
  // let voicePlayData = ref([]);
  const audioVolume = ref(100)
  const broadcastFinished = ref(false)
  const symbol = ref({
    start: [1, 0, 0, 0, 1],
    end: [0, 1, 0, 1, 0]
  })
  /** 播报配置 */
  const frequency = ref(1000)
  const freqGather = ref({
    gather: [
      { name: '舒适', type: 'cozy', min: 500, max: 3000, step: 50, default: 1000 },
      { name: '低频', type: 'low', min: 100, max: 500, step: 20, default: 300 },
      { name: '高频', type: 'high', min: 3000, max: 20000, step: 500, default: 5000 }
    ],
    curr: { name: '舒适', type: 'cozy', min: 500, max: 3000, step: 100 }
  })
  const audioData = ref([])
  const audioSpeed = ref(60)
  const audioDataNext = ref([])
  const allPage = ref(0)
  let pageNumber = ref(0)
  const active = ref(0)
  const audioTest = ref(false)
  let isActive = ref(0) //判断何时添加开始结束
  let listAll = ref([]) //所有播放流数组
  const disturbList = ref([
    {
      type: 1,
      name: '白噪音',
      url: '/006/noise/noise0.wav',
      ctx: null,
      xhr: null,
      gain: null,
      source: null
    },
    {
      type: 2,
      name: '俄语',
      url: '/006/noise/noise1.wav',
      ctx: null,
      xhr: null,
      gain: null,
      source: null
    },
    {
      type: 3,
      name: '日语',
      url: '/006/noise/noise2.wav',
      ctx: null,
      xhr: null,
      gain: null,
      source: null
    },
    {
      type: 4,
      name: '英语',
      url: '/006/noise/noise3.wav',
      ctx: null,
      xhr: null,
      gain: null,
      source: null
    },
    {
      type: 5,
      name: '战场音',
      url: '/006/noise/noise4.wav',
      ctx: null,
      xhr: null,
      gain: null,
      source: null
    },
    {
      type: 6,
      name: '防空警报',
      url: '/006/noise/noise5.wav',
      ctx: null,
      xhr: null,
      gain: null,
      source: null
    }
  ])
  const disturbVol = ref(60)
  const header = ref("")
  const isheader = ref(false)
  const router = useRouter()
  const drillPath = ref('')
  router.getRoutes().forEach(r => {
    if (r.name === 'ReceivePostResult') {
      drillPath.value = r.path
    }
  })

  const {operation, ensureReady} = operationMorseVoice()
  let audioSubscription
  PubSub.subscribe('send_receiveTrainPage', e => {
    if (receiveData.value.status === 1) {
      Modal.confirm({
        class: 'init_modal_style',
        content: '当前训练还未结束，是否结束训练？',
        icon: () => createVNode(ExclamationCircleOutlined),
        okType: 'danger',
        okText: () => '结束',
        cancelText: () => '取消',
        maskClosable: true,
        onOk: () => {
          endTrainInfo('go')
        }
      })
    } else {
      PubSub.publish('callback_receiveTrainPage', true)
    }
  })
  let timer = null
  let disposed = false
  let contentReady = false
  let pageLoadGeneration = 0
  const beforeUnload = () => {
    if (receiveData.value.status === 1) resetTrainInfo()
  }
  onMounted(() => {
     audioSubscription = PubSub.subscribe('receiveProcessData',res=>{
       if (res.status === 'start') timer = null
       if(res.type === 'playing' && res.status === 'progress' && !isheader.value && !audioTest.value && res.codeLength - res.i === 50 && isActive.value < allPage.value){
         if(timer !== res.codeLength){
           timer = res.codeLength
           getPageList('add')
         }
       }
       if (res.status==='finish') {
         if(audioTest.value){
           //试听
           audioTest.value = false
         }else {
           if(isheader.value){
             isheader.value = false
             playVoiceInfo()
             return
           }
           broadcastFinished.value = true
           if (receiveBgRef.value) {
             receiveBgRef.value.pause()
             receiveBgRef.value.currentTime = 4.7
           }
           disturbList.value.map(item => {
             if (item.ctx) {
               changeAudioPlay(item, false)
             }
           })
         }
       }
     })
    if (route.query.id && route.query.id !== '') {
      getTelegramTrainInfo()
    }
    window.addEventListener('beforeunload', beforeUnload)
    if (receiveBgRef.value) {
      receiveBgRef.value.currentTime = 4.7
    }
  })

  watch(frequency, () => {
    operation({type:'changeFrequency',data:frequency.value})
  },{immediate:true})

  watch(audioVolume, () => {
    operation({type:'changeVolume',data:audioVolume.value/100})
  },{immediate:true})

  watch(disturbVol, () => {
    disturbList.value.map(item => {
      if (item.ctx && item.gain) {
        item.gain.gain.value = Number(disturbVol.value/100);
      }
    })
  });

  onBeforeUnmount(() => {
    disposed = true
    clearInterval(trainTimer.value)
    window.removeEventListener('beforeunload', beforeUnload)
    disturbList.value.map(item => {
      if (item.ctx) {
        changeAudioPlay(item, false)
      }
    })
  })
  onUnmounted(() => {
    PubSub.unsubscribe('send_receiveTrainPage')
    PubSub.unsubscribe(audioSubscription)
  })

  /**
   * 获取收报训练详情
   */
  const getTelegramTrainInfo = () => {
    getReceiveTrainDetails({
      id: route.query.id
    }).then(async res => {
      if (disposed) return
      if (res.code === 200) {
        const headerResult = await findHeader(route.query.id)
        if (disposed) return
        if (headerResult.code !== 200) throw new Error(headerResult.message || '报头加载失败')
        header.value = headerResult.data?.content || ''
        isheader.value = !!header.value
        res.data.codeMessageBody.map(item => {
          item.key = JSON.parse(item.key)
          item.value = JSON.parse(item.value)
        })
        res.data.disturb = JSON.parse(res.data.disturb)
        receiveData.value = res.data
        receiveData.value.status = receiveData.value.status===1?0:receiveData.value.status
        audioSpeed.value = receiveData.value.rate
        receiveData.value['disturbText'] = disturbList.value.filter(item => res.data.disturb.indexOf(item.type) > -1).map(item => item.name)
        changeRate()
        if(res.data.isCable===1){
          allPage.value = res.data.pageNumber
        } else {
          allPage.value = Math.ceil(receiveData.value.totalNumber / 100)
        }

        //获取首保训练分页并转码
        if(res.data.status===0){
          if (!await getPageList(isActive.value)) return
        }
        if (res.data.status >= 2) {
          timeAreaShow(parseInt(res.data.validTime * 1000))
        }
        contentReady = true
      } else {
        message.error(res.message)
      }
    }).catch(error => { if (!disposed) message.error(error.message) })
  }
  const pageData = ref([])
  let allCode = []
  //分页列表
  const getPageList = async (type) => {
    const generation = pageLoadGeneration
    const page = isActive.value + 1
    try {
      const res = await apiPostTickerTapeTrainFindPage({pageNumber: page, trainId: route.query.id})
      if (disposed || generation !== pageLoadGeneration) return false
      if (res.code !== 200) throw new Error(res.message || '报文加载失败，请重试')
      const code = []
      const rows = res.data.messageBody.map((item, index) => {
        if (index === 0 && (receiveData.value.isStartSign === 1 || page === 1)) code.push('#', ' ')
        code.push(...item.key, ' ')
        return [...item.key]
      })
      if (page < allPage.value) code.push('/', ' ')
      else code.push('!')
      isActive.value = page
      pageData.value.push(rows)
      allCode = code
      if (type === 'add') {
        operation({type: 'addCode', data: {data: code, numType: audioCodeType()}})
      }
      return true
    } catch (error) {
      if (!disposed) message.error(error.message)
      return false
    }
  }


  /**
   * 训练时间转换显示
   */
  const initTrainTimeInfo = () => {
    let time = 0
    trainTimer.value = setInterval(() => {
      time += 1000
      timeAreaShow(time)
    }, 1000)
  }

  /**
   * 时间区域显示
   */
  const timeAreaShow = t => {
    let h, m, s
    h = Math.floor((t / 1000 / 60 / 60) % 24)
    m = Math.floor((t / 1000 / 60) % 60)
    s = Math.floor((t / 1000) % 60)
    h = h > 9 ? h + '' : '0' + h
    m = m > 9 ? m + '' : '0' + m
    s = s > 9 ? s + '' : '0' + s
    trainTimeRef.value.h = h
    trainTimeRef.value.m = m
    trainTimeRef.value.s = s
  }

  /**
   * 听报字码转换成播放数据流
   */
  const handleVoicePlayCode = codeBox => {
    let voicePlayData = []
    if (isActive.value == 1) {
      symbol.value.start.map(c => {
        voicePlayData.push(c)
      })
      voicePlayData.push(4)
    }
    codeBox.map((item, x) => {
      item.value.map((c, y) => {
        c.map((k, z) => {
          voicePlayData.push(parseInt(k))
          if (((x + 1) % 100 === 0 || x === codeBox.length - 1) && y === item.value.length - 1 && z === c.length - 1) {
            voicePlayData.push(4)
          } else if (y === item.value.length - 1 && z === c.length - 1) {
            voicePlayData.push(3)
          } else if (z === c.length - 1) {
            voicePlayData.push(2)
          }
        })
      })
    })
    if (isActive.value == allPage.value) {
      symbol.value.end.map(c => {
        voicePlayData.push(c)
      })
    }
    listAll.value.push(voicePlayData)
  }

  /**
   * 开始播放数据流
   */
  const playVoiceInfo = () => {
    if(isheader.value){
      operation({type:'message',data:{
          numType:'long',
          data:header.value
        }})
    }else {
      operation({type:'message',data:{
          numType:audioCodeType(),
          data:allCode
        }})
    }
  }

  // 输出中间件code 测试使用
  const showCode = ()=>{
    operation({type:'addCode',data:{
        data:''
      }})
  }

  /**
   * 试听
   */
  const auditionInfo = async () => {
    if (!await ensureReady()) return
    audioTest.value = true
    operation({type:'message',data:{
        numType:'short',
        data:['5',' ','8']
      }})
  }

  /**
   * 开始训练
   */
  const startTrainInfo = async () => {
    if (!await ensureReady()) return
    if (!changeRate()) return
    if (!contentReady || !allCode.length) {
      message.warning('报文尚未加载完成，请稍后重试')
      return
    }
    startReceivePostTrain({
      id: receiveData.value.id
    }).then(res => {
      if (disposed) return
      if (res.code === 200) {
        if (receiveBgRef.value) {
          receiveBgRef.value.play()
        }
        receiveData.value.status = 1
        disturbList.value.map(item => {
          if (receiveData.value.disturb.indexOf(item.type) > -1) {
            changeAudioPlay(item, true)
          }
        })
        playVoiceInfo()
        initTrainTimeInfo()
      } else {
        message.error(res.message)
      }
    })
  }

  /**
   * 结束训练
   * @param go
   */
  const endTrainInfo = go => {
    clearInterval(trainTimer.value)
    endReceivePostTrain({
      id: receiveData.value.id
    }).then(res => {
      if (res.code === 200) {
        if (receiveBgRef.value) {
          receiveBgRef.value.pause()
          receiveBgRef.value.currentTime = 4.7
        }
        receiveData.value.status = 2
        disturbList.value.map(item => {
          if (item.ctx) {
            changeAudioPlay(item, false)
          }
        })
        operation({type:'stop'})
        pageLoadGeneration++
        if (go === 'go') {
          PubSub.publish('callback_receiveTrainPage', true)
        }
      } else {
        message.error(res.message)
      }
    })
  }

  /**
   * 重置训练
   */
  const resetTrainInfo = () => {
    clearInterval(trainTimer.value)
    resetReceivePostTrain({
      id: receiveData.value.id
    }).then(res => {
      if (res.code === 200) {
        if (receiveBgRef.value) {
          receiveBgRef.value.pause()
          receiveBgRef.value.currentTime = 4.7
        }
        receiveData.value.status = 0
        operation({type:'stop'})
        pageLoadGeneration++
        disturbList.value.map(item => {
          if (item.ctx) {
            changeAudioPlay(item, false)
          }
        })
      } else {
        message.error(res.message)
      }
    })
  }

  /**
   * 重听
   */
  const hardHearingInfo = async () => {
    if (!await ensureReady()) return
    operation({type: 'stop'})
    pageLoadGeneration++
    isheader.value = !!header.value
    isActive.value = 0
    pageNumber.value = 0
    pageData.value = []
    audioData.value = []
    listAll.value = []
    if (!await getPageList(0)) return
    playVoiceInfo()
    broadcastFinished.value = false
    if (receiveBgRef.value) receiveBgRef.value.play()
  }

  /**
   * 上传电报纸
   */
  const updateTelegraph = type => {
    router.push({ path: drillPath.value, query: { id: receiveData.value.id, type: type } })
  }

  /**
   * 开始干扰/停止干扰
   * @param item
   * @param status
   */
  const changeAudioPlay = (item, status) => {
    if (status) {
      item.ctx = new (AudioContext || window.webkitAudioContext)()
      item.xhr = new XMLHttpRequest()
      item.xhr.open('GET', window.fileUrl + item.url, true)
      item.xhr.responseType = 'arraybuffer'
      item.xhr.onload = () => {
        item.ctx.decodeAudioData(item.xhr.response, buffer => {
          item.source = item.ctx.createBufferSource()
          item.source.buffer = buffer
          item.source.loop = true
          item.gain = item.ctx.createGain()
          item.gain.gain.value = Number(audioVolume.value / 100)
          item.gain.connect(item.ctx.destination)
          item.source.connect(item.gain)
          item.source.start(0)
        })
      }
      item.xhr.send()
    } else {
      item.source.stop(0)
      item.ctx = null
      item.xhr = null
      item.source = null
      item.gain = null
    }
  }
  //改变播放码率
  const changeRate = () => {
    try {
      operation({type: 'configure', data: {...calculateTiming({rate: audioSpeed.value, type: audioCodeType(), unit: wpmTOmm.value ? 'characters' : 'wpm', lowRate: Number(receiveData.value.isLowRate) === 1, ratio: {dash: receiveData.value.ratio ?? 3}}), frequency: Number(frequency.value), volume: audioVolume.value / 100, model: true}})
      return true
    } catch (error) {
      message.error(error.message)
      return false
    }
  }
  return {
    receiveBgRef,
    receiveData,
    frequency,
    freqGather,
    audioVolume,
    trainTimeRef,
    broadcastFinished,
    audioSpeed,
    disturbVol,
    pageData,
    allPage,
    showCode,
    changeRate,
    auditionInfo,
    startTrainInfo,
    endTrainInfo,
    hardHearingInfo,
    updateTelegraph
  }
}
