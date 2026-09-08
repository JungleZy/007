import { ref, onMounted, onUnmounted, watch, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import Voice from '../../../../../../common/utils/MorseVoice.js'
import { keyCode } from './termData.js'
import { savePreTermTrainTotal, getTermListData } from '../../../../../../common/api/TelegramApi.js'
import useMorse from '../../../../../../common/mixin/useMorse'

export default function termTrain(wpmTOmm) {
  const trainData = ref({
    status: 0, // 0：未开始，1: 正在练习，2： 暂停，3：结束
    type: 'single',
    sort: 0,
    gap: 560,
    prefix: '',
    read: true,
    playTerm: [],
    playIndex: 0
  })
  const term = ref({
    single: null,
    frase: null
  })
  const { dots } = useMorse()
  const fileUrl = ref(window.fileUrl)
  const validTimes = ref([0, 0, 0, 0, 0, 0])
  const termTimer = ref(null)
  const totalime = ref(0)
  const wpms = ref([])
  const noises = ref([
    { label: '关', value: 0 },
    { label: '一', value: 0.2 },
    { label: '二', value: 0.4 },
    { label: '三', value: 0.6 },
    { label: '四', value: 0.8 },
    { label: '五', value: 1.0 }
  ])
  const gapList = ref([
    { text: '默认', value: 560 },
    { text: '1秒', value: 1000 },
    { text: '2秒', value: 2000 }
  ])
  const disturbList = ref([
    {
      type: 1,
      name: '白噪音',
      url: '/006/noise/noise0.wav',
      prevPlay: false,
      play: false,
      mute: false,
      volume: 0,
      playVolume: 50,
      loading: false,
      ctx: null,
      xhr: null,
      gain: null,
      source: null
    },
    {
      type: 2,
      name: '俄语',
      url: '/006/noise/noise1.wav',
      prevPlay: false,
      play: false,
      mute: false,
      volume: 0,
      playVolume: 50,
      loading: false,
      ctx: null,
      xhr: null,
      gain: null,
      source: null
    },
    {
      type: 3,
      name: '日语',
      url: '/006/noise/noise2.wav',
      prevPlay: false,
      play: false,
      mute: false,
      volume: 0,
      playVolume: 50,
      loading: false,
      ctx: null,
      xhr: null,
      gain: null,
      source: null
    },
    {
      type: 4,
      name: '英语',
      url: '/006/noise/noise3.wav',
      prevPlay: false,
      play: false,
      mute: false,
      volume: 0,
      playVolume: 50,
      loading: false,
      ctx: null,
      xhr: null,
      gain: null,
      source: null
    },
    {
      type: 5,
      name: '战场音',
      url: '/006/noise/noise4.wav',
      prevPlay: false,
      play: false,
      mute: false,
      volume: 0,
      playVolume: 50,
      loading: false,
      ctx: null,
      xhr: null,
      gain: null,
      source: null
    },
    {
      type: 6,
      name: '防空警报',
      url: '/006/noise/noise5.wav',
      prevPlay: false,
      play: false,
      mute: false,
      volume: 0,
      playVolume: 50,
      loading: false,
      ctx: null,
      xhr: null,
      gain: null,
      source: null
    }
  ])
  const prefixList = ref(['', 'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z'])
  const frequency = ref(1000)
  const rateWpm = ref(60)
  const volume = ref(100)
  let voice = new Voice({ fre: trainData.value.fre })
  const router = useRouter()
  const drillPath = ref('')
  router.getRoutes().forEach(r => {
    if (r.name === 'DittoTerm') {
      drillPath.value = r.path
    }
  })

  onMounted(() => {
    for (let i = 0; i < 12; i++) {
      wpms.value.push({
        label: 40 + i * 5,
        value: 40 + i * 5
      })
    }
    getTermListDataInfo('single')
  })

  watch(frequency, () => {
    voice.changeFre(frequency.value)
  })

  watch(rateWpm, () => {
    if (wpmTOmm.value) {
      voice.changeCriterion(parseInt(((400 / rateWpm.value) * 60 * 1000) / dots['mix']))
    } else {
      voice.changeCriterion(parseInt(1200 / rateWpm.value))
    }
  })

  watch(volume, () => {
    voice.changeVolume(volume.value * 100)
  })

  onBeforeUnmount(() => {
    disturbList.value.map(item => {
      if (item.play) {
        item.prevPlay = item.play
        changeAudioPlay(item, false)
      }
    })
    if (trainData.value.status === 1 || trainData.value.status === 2) {
      voice.clear()
      endTrain()
    }
  })

  /**
   * 获取通报用语数据
   * @param type
   */
  const getTermListDataInfo = type => {
    getTermListData({ type: type === 'single' ? 0 : 1 }).then(res => {
      if (res.code === 200) {
        term.value[type] = res.data
        trainData.value.type = type
        if (type === 'single') {
          changeSort(trainData.value.sort)
          changePrefix(res.data)
        } else {
          trainData.value.playTerm = res.data
          changeSort(trainData.value.sort)
        }
      }
    })
  }

  /**
   * 播报数据转换
   * @param type
   */
  const handleVoiceData = type => {
    if (term.value[type] === null) {
      getTermListDataInfo(type)
    } else {
      trainData.value.type = type
      if (trainData.value.type === 'single') {
        changeSort(trainData.value.sort)
        changePrefix(term.value[type])
      } else {
        trainData.value.playTerm = term.value[type]
        changeSort(trainData.value.sort)
      }
    }
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
    validTimes.value = t.split('').map(num => parseInt(num))
  }

  /**
   * 处理练习进程
   * @param type
   */
  const handleTrain = type => {
    switch (type) {
      case 0:
        trainData.value.status = 1
        startTrain()
        break
      case 1:
        trainData.value.status = 2
        break
      case 2:
        trainData.value.status = 1
        keyTransCode()
        break
      case 3:
        trainData.value.status = 3
        voice.clear()
        endTrain()
        break
      case 4:
        clearInterval(termTimer.value)
        voice.clear()
        router.push({ path: drillPath.value })
        break
    }
  }

  /**
   * 改变顺序
   */
  const changeSort = num => {
    trainData.value.sort = num
    switch (num) {
      case 0:
        trainData.value.playIndex = 0
        break
      case 1:
        trainData.value.playIndex = trainData.value.playTerm.length - 1
        break
      case 2:
        trainData.value.playIndex = parseInt(Math.random() * trainData.value.playTerm.length)
        break
    }
  }

  /**
   * 开始练习
   */
  const startTrain = () => {
    disturbList.value.map(item => {
      if (item.prevPlay) {
        // item.play = item.prevPlay
        changeAudioPlay(item, true)
      }
    })
    termTimer.value = setInterval(() => {
      totalime.value += 1000
      handleValidTime(totalime.value)
    }, 1000)
    keyTransCode()
  }

  /**
   * 练习字码转换成播报电码
   */
  const keyTransCode = () => {
    let keys = trainData.value.playTerm[trainData.value.playIndex].key.split(''),
      arr = []
    keys.map((key, k) => {
      if (key === ' ') {
        arr.push(3)
      } else {
        arr.push(...keyCode[key])
        if (keys[k + 1] && keys[k + 1] !== ' ') {
          arr.push(2)
        }
      }
    })
    playVoice(arr)
  }

  /**
   * 播报电码
   * @param arr
   */
  const playVoice = arr => {
    voice.clear(() => {
      voice.play(arr, res => {
        if (res === arr.length - 1) {
          switch (trainData.value.sort) {
            case 0:
              trainData.value.playIndex++
              break
            case 1:
              trainData.value.playIndex--
              break
            case 2:
              trainData.value.playIndex = parseInt(Math.random() * trainData.value.playTerm.length)
              break
          }

          if (trainData.value.status === 1 && trainData.value.playIndex >= 0 && trainData.value.playIndex < trainData.value.playTerm.length) {
            setTimeout(() => {
              if (trainData.value.status === 1) {
                keyTransCode()
              }
            }, trainData.value.gap)
          }
          if (trainData.value.playIndex >= trainData.value.playTerm.length) {
            trainData.value.status = 3
          }
          if (trainData.value.status === 3) {
            endTrain()
          }
        }
      })
    })
  }

  /**
   * 用语隐藏/显示
   */
  const switchRead = () => {
    trainData.value.read = !trainData.value.read
    anime({ targets: ['.showRead'], duration: 100, left: trainData.value.read ? 51 : 0 })
  }

  /**
   * 选择单词用语字头
   * @param list
   */
  const changePrefix = list => {
    if (list === 'prefix') {
      list = term.value[trainData.value.type]
    }
    trainData.value.playTerm = []
    list.map(item => {
      if (trainData.value.prefix === '' || trainData.value.prefix === item.key.split('')[0]) {
        trainData.value.playTerm.push(item)
      }
    })
  }

  /**
   * 结束练习
   */
  const endTrain = () => {
    clearInterval(termTimer.value)
    savePreTermTrainTotal({ type: 0, totalTime: totalime.value }).then(res => {
      if (res.code === 200) {
        trainData.value.status = 0
        if (trainData.value.sort == 0) {
          trainData.value.playIndex = 0
        } else {
          trainData.value.playIndex = trainData.value.playTerm.length - 1
        }
        disturbList.value.map(item => {
          item.mute = false
          if (item.play) {
            // item.prevPlay = item.play
            changeAudioPlay(item, false)
          }
        })
      }
    })
  }

  /**
   * 开始干扰/停止干扰
   * @param item
   * @param status
   */
  const changeAudioPlay = (item, status) => {
    item.play = status
    if (status) {
      item.loading = true
      item.ctx = new (AudioContext || window.webkitAudioContext)()
      item.xhr = new XMLHttpRequest()
      item.xhr.open('GET', fileUrl.value + item.url, true)
      item.xhr.responseType = 'arraybuffer'
      item.xhr.onload = () => {
        item.volume = item.playVolume
        item.ctx.decodeAudioData(item.xhr.response, buffer => {
          item.source = item.ctx.createBufferSource()
          item.source.buffer = buffer
          item.source.loop = true
          item.gain = item.ctx.createGain()
          item.gain.gain.value = Number(item.playVolume / 100)
          item.gain.connect(item.ctx.destination)
          item.source.connect(item.gain)
          item.source.start(0)
          item.loading = false
        })
      }
      item.xhr.send()
    } else {
      item.volume = 0
      item.source.stop(0)
      item.ctx = null
      item.xhr = null
      item.source = null
      item.gain = null
    }
  }

  /**
   * 控制干扰音量
   * @param item
   */
  const changeVolume = item => {
    item.mute = false
    item.playVolume = item.volume
    if (item.gain) {
      item.gain.gain.value = Number(item.playVolume / 100)
    }
  }

  /**
   * 控制干扰音量静音
   * @param item
   */
  const changeMute = item => {
    item.mute = !item.mute
    if (item.mute) {
      item.volume = 0
      if (item.gain) {
        item.gain.gain.value = 0
      }
    } else {
      item.volume = item.playVolume
      if (item.gain) {
        item.gain.gain.value = Number(item.playVolume / 100)
      }
    }
  }

  return {
    fileUrl,
    trainData,
    validTimes,
    wpms,
    noises,
    gapList,
    prefixList,
    frequency,
    rateWpm,
    volume,
    disturbList,
    handleTrain,
    handleVoiceData,
    switchRead,
    changeAudioPlay,
    changeVolume,
    changeMute,
    changeSort,
    changePrefix
  }
}
