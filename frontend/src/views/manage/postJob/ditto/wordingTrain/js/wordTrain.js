import { ref, onMounted, onUnmounted, nextTick, onBeforeUnmount, createVNode } from 'vue'
import { wordinngDetails, wordinngBegin, wordinngFinish } from '../../../../../../common/api/postWording'
import { useRouter, useRoute } from 'vue-router'
import MorseVoice from '../../../../../../common/utils/MorseVoice'
import { PubSub } from '../../../../../../common/utils/PubSub'
import { Modal } from 'ant-design-vue'
import { ExclamationCircleOutlined } from '@ant-design/icons-vue'
import useMorse from '../../../../../../common/mixin/useMorse.js'
import { apiPostTrainGlobalRuleType } from '../../../../../../common/api/postWording'

export default function wordingTrain(countDown) {
  onMounted(() => {
    PubSub.subscribe('send_wordingTrainPage', e => {
      if (trainData.value.status === 1) {
        Modal.confirm({
          class: 'init_modal_style',
          content: '当前训练还未结束，是否结束训练？',
          icon: () => createVNode(ExclamationCircleOutlined),
          okType: 'danger',
          okText: () => '结束',
          cancelText: () => '取消',
          maskClosable: true,
          onOk: () => {
            finish('go')
          }
        })
      } else {
        PubSub.publish('callback_closeWordingTrainPage', true)
      }
    })
    init()
  })
  const router = useRouter()
  const route = useRoute()
  let focusIndex = 0
  const showResultModal = ref(false)
  let morseVoice = new MorseVoice()
  const cutDown = ref(null)
  const inputIndex = ref(0) //当前输入的卡片下标
  const { dots } = useMorse()
  const gradeTypeList = ref([])
  const trainData = ref({
    status: 0,
    data: [],
    index: 0,
    title: '',
    type: 0,
    duration: 0,
    number: 0,
    audioState: true
  })
  let time = null
  //干扰项目
  const disturbList = ref([
    {
      type: 1,
      name: '白噪音',
      url: '/006/noise/noise0.wav',
      prevPlay: false,
      play: false,
      mute: false,
      ref: 'disturb1Ref',
      volume: 20,
      playVolume: 50
    },
    {
      type: 2,
      name: '俄语',
      url: '/006/noise/noise1.wav',
      prevPlay: false,
      play: false,
      mute: false,
      ref: 'disturb2Ref',
      volume: 20,
      playVolume: 50
    },
    {
      type: 3,
      name: '日语',
      url: '/006/noise/noise2.wav',
      prevPlay: false,
      play: false,
      mute: false,
      ref: 'disturb3Ref',
      volume: 20,
      playVolume: 50
    },
    {
      type: 4,
      name: '英语',
      url: '/006/noise/noise3.wav',
      prevPlay: false,
      play: false,
      mute: false,
      ref: 'disturb4Ref',
      volume: 20,
      playVolume: 50
    },
    {
      type: 5,
      name: '战场音',
      url: '/006/noise/noise4.wav',
      prevPlay: false,
      play: false,
      mute: false,
      ref: 'disturb5Ref',
      volume: 20,
      playVolume: 50
    },
    {
      type: 6,
      name: '防空警报',
      url: '/006/noise/noise5.wav',
      prevPlay: false,
      play: false,
      mute: false,
      ref: 'disturb6Ref',
      volume: 20,
      playVolume: 50
    }
  ])
  const init = () => {
    wordinngDetails({ id: route.query.id }).then(res => {
      const data = JSON.parse(res.data.content)
      trainData.value.data = []
      trainData.value.type = res.data.type
      trainData.value.trainType = res.data.trainType
      trainData.value.title = res.data.name
      // trainData.value.status = res.data.status
      trainData.value.number = res.data.number
      trainData.value.passNumber = res.data.passNumber
      trainData.value.errorNumber = res.data.errorNumber
      trainData.value.accuracy = parseInt(res.data.accuracy)
      trainData.value.duration = res.data.duration
      trainData.value.score = res.data.score ? res.data.score : 0
      countDown.value.autoSetTimeAdd(trainData.value.duration)
      getGradeTypeList({ type: 0 }, trainData.value.accuracy)
      if (res.data.status == 2) {
        trainData.value.data = data
        trainData.value.status = res.data.status
        showResultModal.value = true
      } else {
        window.addEventListener('keydown', keyDown)
        for (let v of data) {
          v.answer = { key: '', value: '' }
          trainData.value.data.push(v)
        }
      }
      //改变播放码率
      morseVoice.changeCriterion(parseInt(((400 / res.data.speed) * 60 * 1000) / dots['mix']))
      //过滤干扰项
      if (res.data.disturb.length > 0) {
        disturbList.value = disturbList.value.filter(item => {
          return res.data.disturb.some(i => i == item.type)
        })
      } else {
        disturbList.value = []
      }
    })
  }
  //获取列表
  const getGradeTypeList = (data, number) => {
    const num = number ?? 0
    apiPostTrainGlobalRuleType(data).then(res => {
      const list = res.data ?? []
      gradeTypeList.value = list
        .map(item => {
          const obj = {
            ...item,
            start: Number(item.accuracy.split('~')[0]),
            end: Number(item.accuracy.split('~')[1])
          }
          return obj
        })
        .filter(item => {
          return item.start <= num && item.end >= num
        })
    })
  }

  //开始训练
  const start = () => {
    window.addEventListener('keydown', keyDown)
    window.onbeforeunload = function (e) {
      e.returnValue = false
    }
    wordinngBegin({ id: route.query.id }).then(res => {
      trainData.value.status = 1
      //第一个卡片输入框获取焦点
      nextTick(() => {
        if (trainData.value.trainType == 1 && trainData.value.type == 1) {
          if (document.querySelectorAll('.activeCard .inputs')[1]) {
            document.querySelectorAll('.activeCard .inputs')[1].focus()
          }
        } else {
          if (document.querySelectorAll('.activeCard .inputs')[0]) {
            document.querySelectorAll('.activeCard .inputs')[0].focus()
          }
        }
      })
      cutDown.valueOf = setInterval(() => {
        trainData.value.duration++
        countDown.value.autoSetTimeAdd(trainData.value.duration)
      }, 1000)
      if (trainData.value.trainType == 0) {
        palyAudio()
      }
    })
  }
  //开始播报
  const palyAudio = () => {
    //开始播报
    playCode()
    //开启干扰
    changeAudioPlay(true)

  }
  //播报电码
  const playCode = () => {
    morseVoice.clear(() => {
      const str = morseVoice.getCode(trainData.value.data[trainData.value.index].key)
      morseVoice.play(str, res => {
        if (res == str.length - 1 && trainData.value.index <= trainData.value.data.length - 1) {
          time = setTimeout(() => {
            nextTick(() => {
              if (trainData.value.index == trainData.value.data.length - 1) {
                trainData.value.audioState = false
                morseVoice.clear()
                clearInterfere()
              } else {
                trainData.value.index++
                playCode()
              }
            })
          }, 2000)
        }
      })
    })
  }
  //结束训练
  const finish = go => {
    let errorNumber = 0
    let passNumber = 0
    trainData.value.data.forEach(item => {
      switch (trainData.value.trainType) {
        case 0:
          if (item.key == item.answer.key.trim() && item.value.trim() == item.answer.value) {
            passNumber++
            item.trueOrfalse = true
          } else {
            errorNumber++
            item.trueOrfalse = false
          }
          break
        case 1:
          if (trainData.value.type == 0 && item.key.trim() == item.answer.key.trim()) {
            passNumber++
            item.trueOrfalse = true
          } else if (trainData.value.type == 1 && item.value.trim() == item.answer.value.trim()) {
            passNumber++
            item.trueOrfalse = true
          } else {
            errorNumber++
            item.trueOrfalse = false
          }
          break
      }
    })
    let accuracy = passNumber > 0 ? ((passNumber / (passNumber + errorNumber)) * 100).toFixed(2) : ''
    let score = passNumber > 0 ? ((passNumber / trainData.value.number) * 100).toFixed(1) : 0

    const data = {
      content: JSON.stringify(trainData.value.data),
      id: route.query.id,
      score: Number(score),
      errorNumber,
      accuracy,
      passNumber
    }
    trainData.value.errorNumber = errorNumber
    trainData.value.accuracy = accuracy
    trainData.value.passNumber = passNumber
    trainData.value.score = score
    wordinngFinish(data).then(res => {
      showResultModal.value = true
      morseVoice.clear()
      clearInterval(cutDown.valueOf)
      clearTimeout(time)
      init()
      trainData.value.status = 2
      if (go == 'go') {
        PubSub.publish('callback_closeWordingTrainPage', true)
      }
      window.onbeforeunload = () => {}
      clearInterfere()
    })
  }
  //清除干扰
  const clearInterfere = () => {
    disturbList.value.forEach(item => {
      item.source.stop ? item.source.stop() : ''
    })
  }
  const keyDown = e => {
    if (e.key == 'Tab') {
      if (e.preventDefault) {
        e.preventDefault()
      } else {
        window.event.returnValue == false
      }
      inputIndex.value++
      if (trainData.value.trainType == 1) {
        trainData.value.index++
      }
      nextTick(() => {
        if (trainData.value.trainType == 1) {
          if (document.querySelectorAll('.activeCard .inputs')[1]) {
            document.querySelectorAll('.activeCard .inputs')[1].focus()
          }
        }
        if (document.querySelectorAll('.activeCard .inputs')[0]) {
          document.querySelectorAll('.activeCard .inputs')[0].focus()
        }
      })
    }
    if (e.key == 'ArrowDown') {
      if (e.preventDefault) {
        e.preventDefault()
      } else {
        window.event.returnValue == false
      }
      if (document.querySelectorAll('.activeCard .inputs')[1]) {
        document.querySelectorAll('.activeCard .inputs')[1].focus()
      }
    }
    if (e.key == 'ArrowUp') {
      if (e.preventDefault) {
        e.preventDefault()
      } else {
        window.event.returnValue == false
      }
      if (document.querySelectorAll('.activeCard .inputs')[0]) {
        document.querySelectorAll('.activeCard .inputs')[0].focus()
      }
    }
  }
  //开始干扰
  const changeAudioPlay = status => {
    disturbList.value.forEach((item, index) => {
      var ctx = new AudioContext()
      var url = fileUrl + item.url
      let request = new XMLHttpRequest()
      request.open('GET', url, true)
      request.responseType = 'arraybuffer'
      request.onload = () => {
        let arrayBuffer = request.response
        ctx.decodeAudioData(arrayBuffer, buffer => {
          item.source = ctx.createBufferSource()
          item.source.loop = true
          item.source.buffer = buffer
          item.source.connect(ctx.destination)
          item.source.ga
          item.source.start(0)
        })
      }
      request.send()
    })
  }
  //控制干扰音量
  const changeVolume = item => {
    item.playVolume = item.volume
    document.getElementById(item.ref).volume = Number(item.playVolume / 100)
  }
  onUnmounted(() => {
    morseVoice.clear()
    clearTimeout(time)
    clearInterval(cutDown.valueOf)
    window.removeEventListener('keydown', keyDown)
    PubSub.unsubscribe('send_wordingTrainPage')
  })
  return {
    start,
    trainData,
    inputIndex,
    finish,
    disturbList,
    changeVolume,
    changeAudioPlay,
    showResultModal,
    gradeTypeList
  }
}
