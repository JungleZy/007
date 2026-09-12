import { onMounted, onUnmounted, ref, watch, onBeforeUnmount, createVNode } from 'vue'
import { saveReceiveBasicTrain } from '../../../../../../common/api/TelegramApi'
import { findPrevReceiveTrainInfo, getPreKochStageArray, updatePreKochStageArray } from '../../../../../../common/api/ReceiveApi'
import { Modal } from 'ant-design-vue'
import { ExclamationCircleOutlined, QuestionCircleOutlined } from '@ant-design/icons-vue'
import operationMorseVoice from "../../../../../../common/utils/voice/operationMorseVoice";
import {PubSub} from "../../../../../../common/utils/PubSub";
import {calculateTiming} from '../../../../../../common/utils/voice/MorseVoiceHighPerformance'

export default function kochTrain(wpmTOmm) {
  const validTime = ref([0, 0, 0, 5, 0, 0])
  const validTimeNumber = ref(0)
  const trainTimer = ref(null)
  const phrase = ref([])
  const setStage = ref([])
  const currStage = ref(0)
  const setStageId = ref(null)
  const stageVisible = ref(false)
  const stageLoading = ref(false)
  const stage = ref(1)
  const ring = ref('1')
  const showDelay = ref(true)
  const voicePlaying = ref(false)
  const codeBodyRef = ref(null)
  const wpms = ref([])
  const wpms2 = ref([])
  const signalStrength = ref([
    { label: '一', value: 0.165 },
    { label: '二', value: 0.33 },
    { label: '三', value: 0.495 },
    { label: '四', value: 0.66 },
    { label: '五', value: 0.825 },
    { label: '六', value: 0.99 }
  ])
  const checkedWpm = ref(20)
  if (wpmTOmm.value) {
    checkedWpm.value = 60
  }
  const checkedSignalStrength = ref(0.99)
  const duration = ref(0)
  const elapsed = ref([])
  const cacheElapsed = ref([])
  const startDelay = ref(1)
  const displayDelay = ref(0)
  const showTextTime = ref(0)
  const groupLength = ref(4)
  const startNumber = ref({ old: 0, new: 0 })
  const startDelayTimer = ref(null)
  const waitTime = ref(1)
  const waitTimer = ref(null)
  const groupLenOptions = ref([
    {
      label: '随机长度',
      value: 0
    },
    {
      label: '标准长度',
      value: 4
    }
  ])
  const fileUrl = ref(window.fileUrl + '/006/code/')
  const displayDelayOptions = ref([
    {
      label: '0秒',
      value: 0
    },
    {
      label: '1秒',
      value: 1
    },
    {
      label: '2秒',
      value: 2
    },
    {
      label: '3秒',
      value: 3
    },
    {
      label: '4秒',
      value: 4
    },
    {
      label: '5秒',
      value: 5
    },
    {
      label: '6秒',
      value: 6
    },
    {
      label: '7秒',
      value: 7
    },
    {
      label: '8秒',
      value: 8
    },
    {
      label: '9秒',
      value: 9
    }
  ])
  const fre = ref(1000)
  const noises = ref([
    { label: '关', value: 0 },
    { label: '一', value: 0.2 },
    { label: '二', value: 0.4 },
    { label: '三', value: 0.6 },
    { label: '四', value: 0.8 },
    { label: '五', value: 1.0 }
  ])
  const checkedNoise = ref(0)
  const NUM_TYPE = {
    LONG: 'long',
    SHORT: 'short'
  }
  let noiseAudio = null
  let activeIndex = -1
  let playbackGeneration = 0
  const displayTimers = new Set()
  const {operation, ensureReady} = operationMorseVoice()
  const audioSubscription = PubSub.subscribe('receiveProcessData', res => {
    if (!voicePlaying.value) return
    if (res.status === 'progress' && res.i !== activeIndex) {
      activeIndex = res.i
      cacheElapsed.value.push(res.key)
      handleElapsed(res.key)
    } else if (res.status === 'finish') {
      createCode()
    }
  })
  onMounted(() => {
    for (let i = 0; i < 14; i++) {
      wpms.value.push({
        label: 15 + i * 5,
        value: 15 + i * 5
      })
      wpms2.value.push({
        label: 40 + i * 10,
        value: 40 + i * 10
      })
    }
    getStageAll()
    findPrevReceiveTrainInfo({ type: 22 }).then(res => {
      if (res.code === 200) {
        if (res.data) {
          stage.value = res.data.schedule ? res.data.schedule : 1
          ring.value = res.data.mark ? res.data.mark : '1'
        }
      }
    })
  })
  onBeforeUnmount(() => {
    if (voicePlaying.value) stopTrain()
    else playbackGeneration++
  })
  onUnmounted(() => {
    PubSub.unsubscribe(audioSubscription);
    clearInterval(trainTimer.value)
    if (noiseAudio != null) {
      noiseAudio.close()
    }
  })
  watch([checkedWpm, wpmTOmm], () => {
    const timing = calculateTiming({rate: checkedWpm.value, unit: wpmTOmm.value ? 'characters' : 'wpm', type: 'mix'})
    operation({type: 'configure', data: timing})
  }, {immediate: true})
  watch(checkedSignalStrength, () => {
    operation({type:'changeVolume',data:checkedSignalStrength.value})
  },{immediate:true})
  watch(checkedNoise, () => {
    if (noiseAudio != null) {
      closeNoise()
      makeNoise()
    }
  })
  watch(fre, () => {
    operation({type:'changeFrequency',data:fre.value})
  },{immediate:true})
  watch(displayDelay, () => {
    showTextTime.value = displayDelay.value
    waitTime.value = displayDelay.value + startDelay.value
  })
  const initTrainTiming = () => {
    let time = 5 * 60 * 1000
    trainTimer.value = setInterval(() => {
      time -= 1000
      validTimeNumber.value += 1000
      handleValidTime(time)
      if (time <= duration.value) {
        clearInterval(trainTimer.value)
        voicePlaying.value = false
        stopTrain()
      }
    }, 1000)
  }
  const handleValidTime = time => {
    time = parseInt(time)
    let h = parseInt(time / (60 * 60 * 1000)),
      m = parseInt((time - h * 60 * 60 * 1000) / (60 * 1000)),
      s = parseInt((time - h * 60 * 60 * 1000 - m * 60 * 1000) / 1000),
      t = '000500'
    h = h < 10 ? '0' + h : h + ''
    m = m < 10 ? '0' + m : m + ''
    s = s < 10 ? '0' + s : s + ''
    t = h + m + s
    validTime.value = t.split('').map(num => parseInt(num))
  }
  const startTrain = async () => {
    const generation = ++playbackGeneration
    if (!await ensureReady() || generation !== playbackGeneration) return
    voicePlaying.value = true
    validTime.value = [0, 0, 0, 5, 0, 0]
    showTextTime.value = displayDelay.value
    waitTime.value = displayDelay.value + startDelay.value
    elapsed.value = []
    cacheElapsed.value = []
    startDelayTimer.value = setTimeout(() => {
      if (!voicePlaying.value || generation !== playbackGeneration) return
      initTrainTiming()
      if (createCode()) makeNoise()
    }, startDelay.value * 1000)

    if (waitTime.value > 0) {
      waitTimer.value = setInterval(() => {
        waitTime.value--
        if (waitTime.value <= 0) {
          clearInterval(waitTimer.value)
          startNumber.value.new++
        }
      }, 1000)
    }
  }
  const stopTrain = () => {
    playbackGeneration++
    voicePlaying.value = false
    clearInterval(trainTimer.value)
    clearInterval(waitTimer.value)
    clearTimeout(startDelayTimer.value)
    for (const timer of displayTimers) clearTimeout(timer)
    displayTimers.clear()
    if (noiseAudio) closeNoise()
    operation({type:'stop'})
    showTextTime.value = 0
    startNumber.value.old = startNumber.value.new
    elapsed.value = [...cacheElapsed.value]
    saveReceiveBasicTrain({
      type: 22,
      validTime: validTimeNumber.value.toString(),
      schedule: stage.value,
      mark: ring.value
    }).then()
  }
  const nextTrain = () => {
    Modal.confirm({
      title: () => '当前阶段正确率是否已经达到或高于90%？',
      icon: () => createVNode(QuestionCircleOutlined),
      okText: () => '是的,我已达到90%正确率',
      cancelText: () => '我还需努力',
      onOk() {
        if (voicePlaying.value) return false
        if (stage.value === phrase.value.length) {
          stage.value = 1
          ring.value = Number(ring.value) + 1 + ''
        } else {
          stage.value++
        }
      },
      onCancel() {}
    })
  }
  const lastTrain = () => {
    if (voicePlaying.value || stage.value <= 1) return false
    stage.value--
  }
  const makeNoise = () => {
    noiseAudio = new (AudioContext || window.webkitAudioContext)()
    const bufferSize = noiseAudio.sampleRate * 5
    const buffer = noiseAudio.createBuffer(1, bufferSize, noiseAudio.sampleRate)
    const data = buffer.getChannelData(0)
    for (let i = 0; i < bufferSize; i++) {
      data[i] = Math.random() * 2 - 1
    }
    const noise = noiseAudio.createBufferSource()
    noise.buffer = buffer
    noise.loop = true
    const bandpass = noiseAudio.createBiquadFilter()
    bandpass.type = 'lowshelf'
    bandpass.frequency.value = 750
    const gain = noiseAudio.createGain()
    gain.gain.value = checkedNoise.value
    noise.connect(gain).connect(bandpass).connect(noiseAudio.destination)
    noise.start()
  }
  const closeNoise = () => {
    noiseAudio.close()
    noiseAudio = null
  }
  const createCode = () => {
    if (!voicePlaying.value) return false
    const stageCode = phrase.value[stage.value - 1]
    const count = groupLength.value === 0 ? randomNum(1, 8) : 4
    const codes = []
    for (let i = 0; i < count; i++) codes.push(stageCode[randomNum(0, stageCode.length - 1)])
    codes.push(' ')
    activeIndex = -1
    const accepted = operation({type: 'message', data: {numType: 'long', data: codes}})
    if (!accepted) stopTrain()
    return accepted
  }
  const handleElapsed = value => {
    const generation = playbackGeneration
    const timer = setTimeout(() => {
      displayTimers.delete(timer)
      if (voicePlaying.value && generation === playbackGeneration && startNumber.value.old < startNumber.value.new) {
        elapsed.value.push(value)
        if (codeBodyRef.value) codeBodyRef.value.scrollTop = codeBodyRef.value.scrollHeight
      }
    }, showTextTime.value * 1000)
    displayTimers.add(timer)
  }
  const randomNum = (min, max) => {
    min = Math.ceil(min)
    max = Math.floor(max)
    return Math.floor(Math.random() * (max - min + 1)) + min
  }
  const switchDelay = () => {
    showDelay.value = !showDelay.value
    anime({ targets: ['.showDelay'], duration: 100, left: showDelay.value ? 51 : 0 })
    if (!showDelay.value) {
      showTextTime.value = 0
    } else {
      showTextTime.value = displayDelay.value
    }
  }
  const getStageAll = () => {
    getPreKochStageArray().then(res => {
      if (res.code == 200) {
        phrase.value = JSON.parse(res.data.stageArray)
        setStage.value = JSON.parse(res.data.stageArray)
        setStageId.value = res.data.id
      }
    })
  }
  const saveStageInfo = () => {
    stageLoading.value = true
    currStage.value = 0
    setStage.value = setStage.value.filter(item => item.length > 0)
    updatePreKochStageArray({
      id: setStageId.value,
      stageArray: JSON.stringify(setStage.value)
    }).then(res => {
      stageLoading.value = false
      if (res.code == 200) {
        stageVisible.value = false
        phrase.value = JSON.parse(res.data.stageArray)
        setStageId.value = res.data.id
      }
    })
  }

  return {
    codeBodyRef,
    fileUrl,
    validTime,
    stage,
    wpms,
    wpms2,
    signalStrength,
    checkedWpm,
    checkedSignalStrength,
    elapsed,
    startDelay,
    showDelay,
    displayDelay,
    displayDelayOptions,
    fre,
    noises,
    checkedNoise,
    voicePlaying,
    groupLength,
    groupLenOptions,
    phrase,
    setStage,
    currStage,
    stageVisible,
    stageLoading,
    waitTime,
    switchDelay,
    startTrain,
    stopTrain,
    nextTrain,
    lastTrain,
    saveStageInfo
  }
}
