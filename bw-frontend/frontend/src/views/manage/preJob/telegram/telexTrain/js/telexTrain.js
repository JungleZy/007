import {onUnmounted, onMounted, ref, createVNode, nextTick} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import {findTexPatTrainById, saveTexPatTrain} from '../../../../../../common/api/TelegramApi.js'
import {PubSub} from '../../../../../../common/utils/PubSub.js'
import {Modal} from 'ant-design-vue'
import {ExclamationCircleOutlined} from '@ant-design/icons-vue'
  

export default function telexTrain(countDown) {
  onMounted(() => {
    init()
    window.addEventListener('keydown', onkeydown)
    nextTick(() => {
      // document.getElementsByClassName('isfocusInput')[0].focus()
    })
  })
  PubSub.subscribe('send_telexTrainPage', e => {
    if (trainData.value.status === 1) {
      Modal.confirm({
        class: 'init_modal_style',
        content: '当前练习还未结束，是否结束练习？',
        icon: () => createVNode(ExclamationCircleOutlined),
        okType: 'danger',
        okText: () => '结束',
        cancelText: () => '取消',
        maskClosable: true,
        onOk: () => {
          changeFocus(message.value[activeIndex.value])
          endExerciseInfo(3)
        }
      })
    } else {
      PubSub.publish('callback_closeTelexTrainPage', true)
    }
  })
  onUnmounted(() => {
    PubSub.unsubscribe('send_telexTrainPage')
  })
  const activeMessage = ref({
    text: ''
  })
  let isFirst = 0 //判断是否第一次按键，第一次就开始练习
  const activeIndex = ref(0)
  const isfocus = ref(true)
  const message = ref(null)
  const autoTime = ref(null)
  const route = useRoute()
  const router = useRouter()
  const correct = ref(0)
  const trainData = ref({})
  //统计时间
  const nowTime = ref({})
  const init = () => {
    findTexPatTrainById({ id: route.query.id }).then(res => {
      if (!res || res.code !== 200 || !res.data || !res.data.content) {
        Modal.error({ content: res && (res.code === 202 || res.code === 207 || res.code === 208) ? '训练已失效或已结束' : '训练加载失败，请重试' })
        return
      }
      const data = res.data
      data.speed = Number(data.speed)
      data.errorNumber = Number(data.errorNumber)
      try {
        message.value = JSON.parse(data.content)
      } catch {
        Modal.error({ content: '训练报文无效，请重试' })
        return
      }
      activeIndex.value = message.value.findIndex(item => item.isFocus == false)
      if (activeIndex.value < 0) activeIndex.value = message.value.length ? message.value.length - 1 : 0
      activeMessage.value = message.value[activeIndex.value] || { text: '', value: '' }
      trainData.value = data
      trainData.value.duration = 0
      trainData.value.numbs = 0
      if (trainData.value.accuracy > 0) {
        const correctn = trainData.value.accuracy / (trainData.value.accuracy + trainData.value.errorNumber)
        correct.value = Number((correctn * 100).toFixed(2))
      }
      computationTime2(trainData.value.duration)
    }).catch(() => Modal.error({ content: '训练加载失败，请重试' }))
  }
  const onkeydown = () => {
    if (isFirst == 0) {
      document.getElementsByClassName('isfocusInput')[0].focus()
      isFirst = 1
      if (trainData.value.status == 0 || trainData.value.status == 2) {
        beginTrain()
      } else if (trainData.value.status == 1) {
        trainTime()
      }
      window.removeEventListener('keydown', onkeydown)
    }
  }

  //保存训练类容
  const saveTest = status => {
    const extendBy = status === 1 && Array.isArray(message.value) && message.value.length > 0 && message.value.every(item => item.isFocus === true) ? 100 : 0
    const payload = { ...trainData.value, content: JSON.stringify(message.value || []), status, extendBy }
    return saveTexPatTrain(payload).then(res => {
      if (!res || res.code !== 200) {
        Modal.error({ content: res && (res.code === 202 || res.code === 207 || res.code === 208) ? '训练状态已变化，请重新进入' : '训练保存失败，请重试' })
        return false
      }
      trainData.value = { ...trainData.value, ...res.data }
      if (res.data && res.data.content) message.value = JSON.parse(res.data.content)
      if (status === 3) PubSub.publish('callback_closeTelexTrainPage', true)
      return true
    }).catch(() => { Modal.error({ content: '训练保存失败，请重试' }); return false })
  }
  // 训练用时
  const trainTime = () => {
    autoTime.value = setInterval(() => {
      trainData.value.duration++
      // countDown.value.autoSetTimeAdd(trainData.value.duration)
      computationTime2(trainData.value.duration)
      if (trainData.value.accuracy != 0 || trainData.value.errorNumber != 0) {
        trainData.value.speed = Number((((trainData.value.errorNumber + trainData.value.accuracy) * 4) / (trainData.value.duration / 60)).toFixed(2))
      }
    }, 1000)
  }
  const keyCodeDown = (v, item, index) => {
    if (v.key == 'Tab' || v.keyCode == 32) {
      if (v.preventDefault) {
        v.preventDefault()
      } else {
        window.event.returnValue == false
      }
    } else {
      activeMessage.value.isFocus = true
    }
    const inputAll = document.querySelectorAll('.editDiv')
    const activeElement = document.activeElement
    for (let i in inputAll) {
      if (inputAll[i] == activeElement) {
        if (v.keyCode == 32 && i % 10 != 9 && activeIndex.value < message.value.length - 1) {
          //空格下个input获取焦点
          activeMessage.value.isFocus = true
          inputAll[index].innerHTML = inputAll[index].innerHTML.toUpperCase()
          activeMessage.value.value = inputAll[index].innerHTML.toUpperCase()
          changeFocus(message.value[activeIndex.value])
          inputAll[index + 1].focus()
          activeIndex.value = index + 1
          activeMessage.value = message.value[activeIndex.value]
        } else if (v.keyCode == 13 && i % 10 == 9 && activeIndex.value < message.value.length - 1) {
          //回车换行
          activeMessage.value.isFocus = true
          changeFocus(message.value[activeIndex.value])
          inputAll[Number(i) + 1].focus()
          activeIndex.value = index + 1
          activeMessage.value = message.value[activeIndex.value]
        }
      }
    }
  }
  const keyCodeDown2 = v => {
    if (isFirst == 0) {
      return false
    }
    if (v.key == 'Tab' || v.keyCode == 32) {
      if (v.preventDefault) {
        v.preventDefault()
      } else {
        window.event.returnValue == false
      }
    }
    activeMessage.value.isFocus = true
    const scrol = document.getElementsByClassName('scorebox')
    if (activeIndex.value % 10 == 9 && v.keyCode == 32 && activeIndex.value > 20) {
      scrol[0].scrollTop += 98
    }
    if (activeIndex.value > 0 && (activeIndex.value + 1) % 10 == 0) {
      if (v.keyCode == 13 && activeIndex.value < message.value.length - 1) {
        changeFocus(activeMessage.value)
        activeIndex.value++
        activeMessage.value = message.value[activeIndex.value]
      }
    } else {
      if (v.keyCode == 32 && activeIndex.value < message.value.length - 1) {
        changeFocus(activeMessage.value)
        activeIndex.value++
        activeMessage.value = message.value[activeIndex.value]
      }
    }

    // new typed('.isfocusInput',{
    //   strings:[activeMessage.value.value],
    //   typeSpeed:30,
    //   loop:false,
    //   startDelay:300
    // })
  }
  const generateMessage = (keyboard, num) => {
    for (let i = 0; i < 100; i++) {
      let ct = ''
      for (let j = 0; j < 4; j++) {
        const mat = Math.floor(Math.random() * num)
        ct += keyboard[mat].text2 ? keyboard[mat].text2 : keyboard[mat].text
      }
      message.value.push({
        value: '',
        text: ct.toString(),
        type: true,
        isFocus: false
      })
    }
  }
  const changeSwitch = item => {
    isfocus.value = item
    if (item) {
      nextTick(() => {
        document.getElementsByClassName('isfocusInput')[0].focus()
        // new typed('.isfocusInput',{
        //   strings:['activeMessage.value.value'],
        //   typeSpeed:30,
        //   loop:false,
        //   startDelay:300
        // })
      })
    } else {
      nextTick(() => {
        document.querySelectorAll('.editDiv')[activeIndex.value].focus()
        document.querySelectorAll('.isfocusInput')[0].focus()
      })
    }
  }
  //改变专注模式值
  const selectMessage = (item, index) => {
    if (trainData.value.status != 1) {
      return false
    }
    if (isfocus.value) {
      activeIndex.value = index
      activeMessage.value = item
    }
  }
  //改变输入框值
  const changeMessage = v => {
    if (isFirst == 0) {
      v.value = ''
    } else {
      v.value = v.value.toUpperCase()
    }
  }
  const divChange = (e, v) => {
    v.value = e.target.innerHTML
  }
  //输入框失去焦点
  const changeFocus = input => {
    trainData.value.errorNumber = 0
    trainData.value.accuracy = 0
    if (input.text == input.value.trim()) {
      input.type = true
    } else {
      input.type = false
    }
    message.value.forEach(lineMessage => {
      if (lineMessage.text != lineMessage.value && lineMessage.isFocus) {
        trainData.value.errorNumber++
      } else if (lineMessage.text == lineMessage.value) {
        trainData.value.accuracy++
      }
    })
    if (trainData.value.accuracy == 0) {
      correct.value = 0
    } else {
      const correctn = trainData.value.accuracy / (trainData.value.accuracy + trainData.value.errorNumber)
      correct.value = Number((correctn * 100).toFixed(2))
    }
  }
  //获取焦点
  const getFocus = input => {
    // input.isFocus = true
  }
  //选择报文（卡片）
  const selectCard = index => {
    if (trainData.value.status == 1) {
      activeIndex.value = index
      document.querySelectorAll('.editDiv')[activeIndex.value].focus()
      document.querySelectorAll('.isfocusInput')[0].focus()
    }
  }
  //开始训练
  const beginTrain = () => {
    saveTest(1).then(ok => {
      if (!ok) return
      trainData.value.status = 1
      trainTime()
      nextTick(() => {
        document.querySelectorAll('.editDiv')[activeIndex.value]?.focus()
        document.querySelectorAll('.isfocusInput')[0]?.focus()
      })
    })
  }
  //结束训练
  const endExerciseInfo = () => {
    clearInterval(autoTime.value)
    trainData.value.errorNumber = 0
    trainData.value.accuracy = 0
    message.value.forEach(lineMessage => {
      if (lineMessage.text != lineMessage.value && lineMessage.isFocus) {
        trainData.value.errorNumber++
      }
      if (lineMessage.text == lineMessage.value && lineMessage.isFocus) {
        trainData.value.accuracy++
      }
    })
    const correctn = trainData.value.accuracy / (trainData.value.accuracy + trainData.value.errorNumber)
    correct.value = Number((correctn * 100).toFixed(2))
    saveTest(3)
    // isfocus.value = false
  }
  const goback = () => {
    router.go(-1)
  }
  const computationTime2 = total => {
    let hour
    let min
    let sec
    let day
    let h
    let m
    let s
    hour = Math.floor((total / 60 / 60) % 24)
    min = Math.floor((total / 60) % 60)
    sec = Math.floor(total % 60)
    day = Math.floor(total / 60 / 60 / 24)
    // 计算总小时数
    hour = hour + day * 24
    if (hour < 10 && hour >= 0) {
      h = '0' + hour
    } else {
      h = hour.toString()
    }
    if (min < 10 && min >= 0) {
      m = '0' + min
    } else {
      m = min.toString()
    }
    if (sec < 10 && sec >= 0) {
      s = '0' + sec
    } else {
      s = sec.toString()
    }
    nowTime.value.h1 = h.substring(0, 1) * 1
    nowTime.value.h2 = h.substring(1, 2) * 1
    nowTime.value.m1 = m.substring(0, 1) * 1
    nowTime.value.m2 = m.substring(1, 2) * 1
    nowTime.value.s1 = s.substring(0, 1) * 1
    nowTime.value.s2 = s.substring(1, 2) * 1
  }
  return {
    message,
    trainData,
    correct,
    activeMessage,
    isfocus,
    activeIndex,
    nowTime,
    divChange,
    selectCard,
    keyCodeDown,
    keyCodeDown2,
    changeSwitch,
    selectMessage,
    goback,
    endExerciseInfo,
    changeMessage,
    changeFocus,
    beginTrain,
    getFocus
  }
}
