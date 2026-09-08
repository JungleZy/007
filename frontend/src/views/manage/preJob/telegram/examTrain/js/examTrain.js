import { message, Modal } from 'ant-design-vue'
import { ExclamationCircleOutlined } from '@ant-design/icons-vue'
import { onUnmounted, onMounted, ref, onBeforeUnmount, createVNode, nextTick, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import useControl from './useControl.js'
import { codeInKey, codeOnKey } from './keyCode.js'
import { findExamPatTrainById, beginExamTrainInfo, stopExamTrainInfo, goTopExamTrainInfo, endExamTrainInfo } from '../../../../../../common/api/examApi.js'
import { PubSub } from '../../../../../../common/utils/PubSub.js'
import useMorse from '../../../../../../common/mixin/useMorse'

export default function telexTrain() {
  const activeMessage = ref({ text: '' })
  const nowTime = ref({ h1: 0, h2: 0, m1: 0, m2: 0, s1: 0, s2: 0 })
  const activeIndex = ref(0)
  const isfocus = ref(true)
  const messageData = ref(null)
  const autoTime = ref(null)
  const route = useRoute()
  const router = useRouter()
  const {baseCode} = useMorse();
  const correct = ref(0)
  const trainData = ref({})
  const { wsOnline, devOnline, patKey, changeCriterion } = useControl()
  const queryType = ref(0);
  const isModify = ref(false);
  const isPatF1 = ref(true);
  const isPatF2 = ref(false);
  const isPatF3 = ref(false);
  const patNumber = ref(0)
  const pauseDuration = ref(800)
  let codeNumber = 0

  PubSub.subscribe('send_examTrainPage', e => {
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
          changeFocus(messageData.value[activeIndex.value])
          endExamTrain()
        }
      })
    } else {
      PubSub.publish('callback_closeExamTrainPage', true)
    }
  })

  onMounted(() => {
    if (route.query.id && route.query.id !== '') {
      setTimeout(() => {
        findExamPatTrainById({ id: route.query.id }).then(res => {
          if (res.code === 200) {
            let data = res.data
            data.speed = Number(data.speed)
            data.errorNumber = Number(data.errorNumber)
            data.duration = Number(data.duration)
            data.accuracy = data.accuracy ? Number(data.accuracy) : 0
            messageData.value = JSON.parse(data.content)
            activeIndex.value = messageData.value.findIndex(item => !item.isFocus)
            activeMessage.value = messageData.value[activeIndex.value]
            trainData.value = data
            queryType.value = data.messageType
            timeAreaShow(trainData.value.duration * 1000)
            if (data.status === 2) {
              goToExamTrain()
            } else if (data.status === 1) {
              trainTime()
            }
          }
        })
      })
    }
  })

  onUnmounted(() => {
    PubSub.unsubscribe('send_examTrainPage')
  })

  onBeforeUnmount(() => {
    clearInterval(autoTime.value)
  })

  watch(patKey, () => {
    if (patKey.value) {
      if (isfocus.value) {
        // 专注模式
        patFocusExamKeyCode()
      } else {
        patExamKeyCode()
      }
      changeCriterion(trainData.value.speed)
    }
  })

  /**
   * 练习计时统计
   */
  const trainTime = () => {
    autoTime.value = setInterval(() => {
      trainData.value.duration++
      timeAreaShow(trainData.value.duration * 1000)
      if (patNumber.value > 0) {
        // trainData.value.speed = Number(((patNumber.value * 4) / (trainData.value.duration / 60)).toFixed(2))
        trainData.value.speed = Number(((codeNumber) / (trainData.value.duration / 60)).toFixed(2))
      }
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
    nowTime.value.h1 = h.substring(0, 1) * 1
    nowTime.value.h2 = h.substring(1, 2) * 1
    nowTime.value.m1 = m.substring(0, 1) * 1
    nowTime.value.m2 = m.substring(1, 2) * 1
    nowTime.value.s1 = s.substring(0, 1) * 1
    nowTime.value.s2 = s.substring(1, 2) * 1
  }

  /**
   * 开始练习
   */
  const beginExamTrain = () => {
    if (!wsOnline.value) {
      message.error('报训软件未连接!')
      return false
    }
    if (!devOnline.value) {
      message.error('电子键设备未连接！')
      return false
    }
    beginExamTrainInfo({ id: route.query.id }).then(res => {
      if (res.code === 200) {
        trainData.value.status = 1
        trainTime()
        nextTick(() => {
          document.getElementsByTagName('input')[activeIndex.value].focus()
          // console.log('已开始练习！')
        })
      }
    })
  }

  /**
   * 暂停练习
   */
  const stopExamTrain = () => {
    clearInterval(autoTime.value)
    stopExamTrainInfo({
      id: route.query.id,
      duration: Number(trainData.value.duration) * 1000,
      errorNumber: trainData.value.errorNumber,
      accuracy: trainData.value.accuracy,
      speed: trainData.value.speed,
      content: JSON.stringify(messageData.value)
    }).then(res => {
      if (res.code === 200) {
        trainData.value.status = 2
        PubSub.publish('callback_closeExamTrainPage', true)
        // console.log('已暂停练习！')
      }
    })
  }

  /**
   * 继续练习
   */
  const goToExamTrain = () => {
    goTopExamTrainInfo({ id: route.query.id }).then(res => {
      if (res.code === 200) {
        trainData.value.status = 1
        trainTime()
        // console.log('已继续练习！')
      }
    })
  }

  /**
   * 结束练习
   */
  const endExamTrain = () => {
    clearInterval(autoTime.value)
    trainData.value.errorNumber = 0
    trainData.value.accuracy = 0
    correct.value = 0
    messageData.value.forEach(lineMessage => {
      if (lineMessage.text !== lineMessage.value && lineMessage.isFocus) {
        trainData.value.errorNumber++
      }
      if (lineMessage.text === lineMessage.value && lineMessage.isFocus) {
        correct.value++
      }
    })
    if (correct.value > 0 || trainData.value.errorNumber > 0) {
      trainData.value.accuracy = Number(((correct.value / (correct.value + trainData.value.errorNumber)) * 100).toFixed(2))
    } else {
      trainData.value.accuracy = 0
    }
    // isfocus.value = false;
    endExamTrainInfo({
      id: route.query.id,
      duration: Number(trainData.value.duration) * 1000,
      errorNumber: trainData.value.errorNumber,
      accuracy: trainData.value.accuracy,
      speed: trainData.value.speed,
      totalNumber: trainData.value.totalNumber,
      content: JSON.stringify(messageData.value)
    }).then(res => {
      if (res.code === 200) {
        // trainData.value.status = 3;
        PubSub.publish('callback_closeExamTrainPage', true)
        // console.log('已结束练习！')
      }
    })
  }

  /**
   * 拍发电子键键入情况
   */
  const patExamKeyCode = () => {
    if (Number(patKey.value) < 20 || Number(patKey.value) > 41) {
      messageData.value[activeIndex.value].value += '#'
      // console.log('键入无效！')
    } else {
      messageData.value[activeIndex.value].value += codeInKey[patKey.value].text
    }
    activeMessage.value.isFocus = true
    let inputAll = document.getElementsByTagName('input')
    if (patKey.value === '41' && activeIndex.value < messageData.value.length - 1) {
      activeMessage.value.isFocus = true
      inputAll[activeIndex.value + 1].focus()
      activeIndex.value++
      activeMessage.value = messageData.value[activeIndex.value]
    }
    patKey.value = null
  }

  /**
   * 拍发电子键专注模式键入情况
   */
  let cacheCode = [],timer = null;
  const patFocusExamKeyCode = () => {
    if (Number(patKey.value) == 12) {
      isPatF1.value = true;
      isPatF2.value = false;
      return false;
    }
    if (Number(patKey.value) == 13) {
      isPatF1.value = false;
      isPatF2.value = true;
      return false;
    }
    if (Number(patKey.value) == 14) {
      isPatF3.value = true;
      return false;
    }
    if (Number(patKey.value) == 44&&isPatF1.value) {
      trainData.value.status = 1;
      patNumber.value ++
      trainTime();
      nextTick(()=>{
        document.getElementsByTagName('input')[activeIndex.value].focus();
      })
      activeIndex.value = 0;
      return false;
    }
    if (trainData.value.status == 0) {
      message.error('请拍发开始符号！')
      return false;
    }
    if (trainData.value.status == 3) {
      message.error('您已结束拍发！')
      return false;
    }
    if (Number(patKey.value) == 45&&isPatF1.value) {
      return false
      trainData.value.status = 3;
      endExamTrain();
      return false;
    }
    if (Number(patKey.value) == 43&&isPatF1.value) {
      if (isModify.value || activeIndex.value == 0) {
        return false;
      } else {
        messageData.value[activeIndex.value].value = ''
        activeIndex.value--;
        activeMessage.value = messageData.value[activeIndex.value]
        changeFocus(messageData.value[activeIndex.value]);
        isModify.value = true;
      }
    }
    if (timer) {
      clearTimeout(timer)
      timer = null
    }

    if (Number(patKey.value) < 20) {
      messageData.value[activeIndex.value].value += '#';
      // console.log('键入无效！')
    } else if (Number(patKey.value) <= 45 && Number(patKey.value) != 41) {
      if (isPatF1.value || isPatF3.value) {
        patNumber.value ++
        codeNumber++
        messageData.value[activeIndex.value].value += codeInKey[patKey.value].text;
        cacheCode = []
      } else {
        if (Number(patKey.value) < 30) {
          if (cacheCode.length > 0) {
            patNumber.value ++
            cacheCode.push(...codeInKey[patKey.value]._code)
            codeNumber++
            messageData.value[activeIndex.value].value += codeOnKey[cacheCode.join('')]??'#'
            cacheCode = []
          } else {
            codeNumber++
            messageData.value[activeIndex.value].value += '#'
          }
        } else {
          if (cacheCode.length > 0) {
            patNumber.value ++
            codeNumber++
            messageData.value[activeIndex.value].value += codeOnKey[cacheCode.join('')]??'#'
          }
          cacheCode = []
          cacheCode.push(...codeInKey[patKey.value]._code)
          timer = setTimeout(() => {
            patNumber.value ++
            messageData.value[activeIndex.value].value +=codeOnKey[cacheCode.join('')]??'#'
            cacheCode = []
            clearTimeout(timer)
            timer = null
          },pauseDuration.value)
        }
      }
    }
    activeMessage.value.isFocus = true
    let scrol = document.getElementsByClassName('scorebox')
    if (activeIndex.value % 10 === 9 && patKey.value === '41' && activeIndex.value > 20) {
      scrol[0].scrollTop += 98
    }
    if (patKey.value === '41' && activeIndex.value < messageData.value.length - 1) {
      if (isPatF3.value) {
        endExamTrain();
        // router.go(-1)
        return false;
      }
      isModify.value = false;
      changeFocus(messageData.value[activeIndex.value])
      activeIndex.value++
      activeMessage.value = messageData.value[activeIndex.value]
    }
    if (activeIndex.value === trainData.value.totalNumber - 3) {
      trainData.value.totalNumber += 100
      generateMessage()
      nextTick(()=>{
        scrol[0].scrollTop += 98*3
      })
    }
    patKey.value = null;
    isPatF3.value = false;
  }

  /**
   * 生成随机报文
   */
  const generateMessage = () => {
    for (let i = 0; i < 100; i++) {
      let ct = ''
      for (let j = 0; j < 4; j++) {
        if (queryType.value == 0) {
          ct+= Math.floor(Math.random()*10)
        } else if (queryType.value == 1) {
          ct+= baseCode['A_Z'][Math.floor(Math.random()*26)]
        } else {
          ct+= baseCode['mix'][Math.floor(Math.random()*36)]
        }
      }
      messageData.value.push({ value: '', text: ct, type: true, isFocus: false })
    }
  }

  /**
   * 切换拍发模式
   * @param item
   */
  const changeSwitch = item => {
    isfocus.value = item
    if (item) {
      nextTick(() => {
        document.getElementsByClassName('isfocusInput')[0].focus()
      })
    } else {
      nextTick(() => {
        document.getElementsByTagName('input')[activeIndex.value].focus()
      })
    }
  }

  /**
   * 改变专注模式值
   * @param item
   * @param index
   */
  const selectMessage = (item, index) => {
    if (isfocus.value) {
      activeIndex.value = index
      activeMessage.value = item
    }
  }

  /**
   * 改变输入框值
   * @param v
   */
  const changeMessage = v => {
    v.value = v.value.toUpperCase()
  }

  /**
   * 输入框失去焦点
   * @param input
   */
  const changeFocus = input => {
    // trainData.value.errorNumber = 0
    // trainData.value.accuracy = 0
    // correct.value = 0
    // input.type = input.text === input.value
    // console.log(messageData.value, '111111111111111')
    // messageData.value.forEach(lineMessage => {
    //   if (lineMessage.text !== lineMessage.value && lineMessage.isFocus) {
    //     trainData.value.errorNumber++
    //   } else if (lineMessage.text === lineMessage.value) {
    //     correct.value++
    //   }
    // })
    // if (correct.value > 0 || trainData.value.errorNumber > 0) {
    //   trainData.value.accuracy = Number(((correct.value / (correct.value + trainData.value.errorNumber)) * 100).toFixed(2))
    // } else {
    //   trainData.value.accuracy = 0
    // }
    input.type = input.text === input.value
    if (input.type) {
      correct.value++
    } else {
      trainData.value.errorNumber++
    }
    if (correct.value > 0 || trainData.value.errorNumber > 0) {
      trainData.value.accuracy = Number(((correct.value / (correct.value + trainData.value.errorNumber)) * 100).toFixed(2))
    } else {
      trainData.value.accuracy = 0
    }
  }

  /**
   * 选择报文（卡片）
   * @param index
   */
  const selectCard = index => {
    if (trainData.value.status === 1) {
      activeIndex.value = index
      document.getElementsByTagName('input')[activeIndex.value].focus()
    }
  }

  return {
    wsOnline,
    devOnline,
    messageData,
    nowTime,
    trainData,
    activeMessage,
    isfocus,
    activeIndex,
    pauseDuration,
    selectCard,
    changeSwitch,
    selectMessage,
    endExamTrain,
    changeMessage,
    changeFocus,
    beginExamTrain
  }
}
