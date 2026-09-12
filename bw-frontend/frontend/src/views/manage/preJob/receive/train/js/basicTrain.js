import {ref, onMounted, onUnmounted, watch, onBeforeUnmount, createVNode, inject} from 'vue'
import { useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import { PubSub } from '../../../../../../common/utils/PubSub'
import { deepClone } from '../../../../../../common/utils/Utils.js'
import useMorse from '../../../../../../common/mixin/useMorse.js'
import { saveReceiveBasicTrain } from '../../../../../../common/api/TelegramApi'
import operationMorseVoice from "../../../../../../common/utils/voice/operationMorseVoice";
import {calculateTiming} from '../../../../../../common/utils/voice/MorseVoiceHighPerformance'
export default function telegramList(wpmTOmm) {
  const downTimeRef = ref(null)
  const validTime = ref([0, 0, 0, 0, 0, 0])
  const validTimeNumber = ref(0)
  const speedRate = ref(60)
  const audioVolume = ref(60)
  const downTime = ref(false)
  const trainTimer = ref(null)
  const hearPlayVoice = ref(false)
  const loop = ref(false)
  const short = ref(false)
  const hideCode = ref(false)
  const { morseCode } = useMorse()
  const currKeyIndex = ref(null)
  const playType = ref(null)
  const broadcastKey = ref(null)
  const identify = ref({ index: -1, status: 'info' })
  const waitTimer = ref(null)
  const keyArray = ref({
    list: [],
    flow: [],
    index: 0,
    switchCode: 0
  })
  /** 播报配置 */
  const frequency = ref(1000)
  const freqGather = ref({
    gather: [
      { name: '舒适', type: 'cozy', min: 500, max: 3000, step: 50 },
      { name: '低频', type: 'low', min: 100, max: 500, step: 20 },
      { name: '高频', type: 'high', min: 3000, max: 20000, step: 500 }
    ],
    curr: { name: '舒适', type: 'cozy', min: 500, max: 3000, step: 100 }
  })
  const disturbList = ref([
    {
      type: 1,
      name: '白噪音',
      url: '/006/noise/noise0.wav',
      sound: 0,
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
  const checkedDisturb = ref([])
  const disturbVol = ref(60)
  const playStatus = ref('')
  let loopData = null
  let playbackGeneration = 0
  const {operation, ensureReady} = operationMorseVoice()
  onMounted(() => {
    initTrainTiming()
    changeCode(0)
  })
  const audioSubscription = PubSub.subscribe('receiveProcessData',res=>{
    keyArray.value.index=res.j
    if(playStatus.value==='播报'){
      currKeyIndex.value=res.i
    }
    if (res.status==='finish') {
      if( playStatus.value === "听报识别"){
          identify.value.index = -1
          identify.value.status = 'info'
          hearPlayVoice.value = false
        return
      }
      currKeyIndex.value = null
      keyArray.value.index = 0
      if (loop.value&&playType.value!==null) {
        waitTimer.value = setTimeout(() => {
          waitTimer.value = null
          if (loop.value && playType.value !== null && loopData) playVoice(loopData[0], loopData[1], '播报')
        }, parseInt((15 * 1000) / speedRate.value))
      } else {
        playType.value = null
      }
    }
  })

  watch(frequency, () => {
    operation({type:'changeFrequency',data:parseFloat(frequency.value)})
  },{immediate:true})
  watch(audioVolume, () => {
    operation({type:'changeVolume',data:parseFloat(audioVolume.value)/100})
  },{immediate:true})
  watch(disturbVol, () => {
    disturbList.value.map(item => {
      if (item.ctx && item.gain) {
        item.gain.gain.value = Number(disturbVol.value / 100)
      }
    })
  })
  watch([speedRate, wpmTOmm, () => keyArray.value.switchCode], () => {
    operation({type: 'configure', data: calculateTiming({rate: speedRate.value, unit: wpmTOmm.value ? 'characters' : 'wpm', type: keyArray.value.switchCode == 2 ? 'letter' : keyArray.value.switchCode == 1 ? 'short' : 'long'})})
  }, {immediate: true})
  onBeforeUnmount(() => {
    saveReceiveBasicTrain({
      type: 21,
      validTime: validTimeNumber.value.toString()
    }).then()
  })
  onUnmounted(() => {
    clearInterval(trainTimer.value)
    clearTimeout(waitTimer.value)
    PubSub.unsubscribe(audioSubscription)
    operation({type:'stop'})
    disturbList.value.map(item => {
      if (item.ctx) {
        changeAudioPlay(item, false)
      }
    })
  })

  /**
   * 播报
   */
  const playVoice = async (key, i,type=false) => {
    const generation = playbackGeneration
    if (!await ensureReady() || generation !== playbackGeneration) return
    loopData = [key, i]
    let code
    if(Array.isArray(key)){
      code = key
    }else {
      code = (key+"").split('')
    }
    if(type!=='播报'){
      currKeyIndex.value = i
    }
    operation({type:'message',data:{
        numType: keyArray.value.switchCode==1?'short':'long',
        data: code
      }})
  }
  /**
   * 处理训练用时
   */
  const handleValidTime = time => {
    time = parseInt(time)
    let h = parseInt(time / (60 * 60 * 1000)),
      m = parseInt((time - h * 60 * 60 * 1000) / (60 * 1000)),
      s = parseInt((time - h * 60 * 60 * 1000 - m * 60 * 1000) / 1000),
      t = '000000'
    h = h < 10 ? '0' + h : h + ''
    m = m < 10 ? '0' + m : m + ''
    s = s < 10 ? '0' + s : s + ''
    t = h + m + s
    validTime.value = t.split('').map(num => parseInt(num))
  }

  /**
   * 播报字码音频
   */
  const playVoiceInfo = (item, i) => {
    if (playType.value === 'hear') {
      if (identify.value.status !== 'info') return false
      identify.value.index = i
      if (item.key === broadcastKey.value.key) {
        identify.value.status = 'success'
        setTimeout(() => {
          identityInfo()
        }, 1000)
      } else {
        identify.value.status = 'error'
        // let arr = broadcastKey.value.value.split('').map(v => Number(v))
        setTimeout(() => {
          hearPlayVoice.value = true
          operation({type:'message',data:{
              numType: keyArray.value.switchCode==1?'short':'long',
              data: [broadcastKey.value.key]
            }})
        }, 1000)
      }
    }
    else {
      playType.value = null
      playStatus.value = ''
      if (waitTimer.value) {
        clearTimeout(waitTimer.value)
      }
      playVoice(item.key, i)
    }
  }

  /**
   * 顺序播报字码音频
   */
  const queuePlayVoiceInfo = type => {
    keyArray.value.flow = []
    currKeyIndex.value = 0
    playType.value = type
    identify.value.index = -1
    identify.value.status = 'info'
    const code=[]
    if (type === '') {
      keyArray.value.list.map(item => {
        code.push(item.key)
        item.value.split('').map(v => {
          keyArray.value.flow.push(Number(v))
        })
        keyArray.value.flow.push(2)
      })
    }
    else if (type === '0') {
      for (let i in keyArray.value.list) {
        if (i >= 10) break
        keyArray.value.list[i].value.split('').map(v => {
          keyArray.value.flow.push(Number(v))
        })
        keyArray.value.flow.push(2)
      }
    }
    else if (type === 'a') {
      currKeyIndex.value = 10
      for (let i in keyArray.value.list) {
        if (i >= 10) {
          keyArray.value.list[i].value.split('').map(v => {
            keyArray.value.flow.push(Number(v))
          })
          keyArray.value.flow.push(2)
        }
      }
    }

    if (waitTimer.value) {
      clearTimeout(waitTimer.value)
    }
    if (type === 'hear') {
      currKeyIndex.value = null
      loop.value = false
      anime({ targets: ['.loop'], duration: 100, left: 0 })
        identityInfo()
    } else {
      playStatus.value = '播报'
      playVoice(code, currKeyIndex.value,'播报')
    }
  }

  /**
   * 听报识别
   */
  const identityInfo = async () => {
    if (!await ensureReady()) return
    let len = keyArray.value.switchCode == 2 ? 26 : 10,
      arr,
      bcIndex = parseInt(Math.random() * len)
    broadcastKey.value = keyArray.value.list[bcIndex]
    // arr = broadcastKey.value.value.split('').map(v => Number(v))
    hearPlayVoice.value = true
    playStatus.value = "听报识别"
    operation({type:'message',data:{
        numType: keyArray.value.switchCode==1?'short':'long',
        data: [broadcastKey.value.key]
      }})
  }

  /**
   * 试听
   */
  const auditionInfo = async () => {
    if (!await ensureReady()) return
    operation({type:'message',data:{
        numType: keyArray.value.switchCode==1?'short':'long',
        data: ['5','0']
      }})
  }

  /**
   * 训练计时
   */
  const initTrainTiming = () => {
    let time = 0,
      arr = ['0', '1', '2', '3', '4', '5', '6', '7', '8', '9']
    validTimeNumber.value = 0
    trainTimer.value = setInterval(() => {
      time += 1000
      validTimeNumber.value += 1000
      handleValidTime(time)
    }, 1000)
    keyArray.value.list = []
    for (let key in morseCode.mix) {
      keyArray.value.list.push(deepClone(morseCode.mix[key]))
    }
  }

  /**
   * 开启循环播报
   */
  const switchLoop = type => {
    if (type === 3) {
      hideCode.value = !hideCode.value
      anime({ targets: ['.hideCode'], duration: 100, left: hideCode.value ? 51 : 0 })
    }
    if (type === 1) {
      loop.value = !loop.value
      anime({ targets: ['.loop'], duration: 100, left: loop.value ? 51 : 0 })
    }
    if (type === 2) {
      playType.value = null
      currKeyIndex.value = null
      keyArray.value.index = 0
      short.value = !short.value
      anime({ targets: ['.short'], duration: 100, left: short.value ? 51 : 0 })
      keyArray.value.list = []
      for (let key in morseCode.mix) {
        if (short.value && morseCode.short[key]) {
          keyArray.value.list.push(deepClone(morseCode.short[key]))
        } else {
          keyArray.value.list.push(deepClone(morseCode.mix[key]))
        }
      }
    }
  }

  /**
   * 停止练习
   */
  const stopTrain = () => {
    playbackGeneration++
    clearTimeout(waitTimer.value)
    waitTimer.value = null
    loopData = null
    operation({type:'stop'})
    playType.value = null
    currKeyIndex.value = null
    keyArray.value.index = 0
  }

  /**
   * 切换播报类型
   * @param e
   */
  const changeCode = e => {
    stopTrain()
    keyArray.value.switchCode = e
    keyArray.value.list = []
    switch (e) {
      case 0:
        for (let key in morseCode.long) {
          keyArray.value.list.push(deepClone(morseCode.long[key]))
        }
        break
      case 1:
        for (let key in morseCode.short) {
          keyArray.value.list.push(deepClone(morseCode.short[key]))
        }
        break
      case 2:
        for (let key in morseCode.letter) {
          keyArray.value.list.push(deepClone(morseCode.letter[key]))
        }
        break
    }
  }

  /**
   * 停止所有干扰
   */
  const onOffDisturb = () => {
    disturbList.value.map(item => {
      if (item.ctx) {
        changeAudioPlay(item, false)
      }
    })
    checkedDisturb.value = []
  }

  /**
   * 改变干扰选中
   * @param item
   */
  const changeDisturbInfo = item => {
    if (checkedDisturb.value.indexOf(item.type) == -1) {
      changeAudioPlay(item, true)
    } else {
      changeAudioPlay(item, false)
    }
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
          item.gain.gain.value = Number(disturbVol.value / 100)
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

  return {
    downTimeRef,
    hearPlayVoice,
    validTime,
    frequency,
    freqGather,
    audioVolume,
    downTime,
    speedRate,
    loop,
    short,
    hideCode,
    currKeyIndex,
    keyArray,
    playType,
    identify,
    disturbList,
    checkedDisturb,
    disturbVol,
    auditionInfo,
    playVoiceInfo,
    switchLoop,
    queuePlayVoiceInfo,
    stopTrain,
    changeCode,
    onOffDisturb,
    changeDisturbInfo
  }
}
