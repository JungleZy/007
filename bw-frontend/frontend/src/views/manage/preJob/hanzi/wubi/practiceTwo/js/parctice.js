import { onUnmounted, onMounted, ref, onBeforeUnmount, createVNode, nextTick, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getById, begin, hanziFinish, hanziPause, goTo } from '../../../../../../../common/api/TelegramApi.js'
import { PubSub } from '../../../../../../../common/utils/PubSub.js'
import { Modal } from 'ant-design-vue'
import { ExclamationCircleOutlined } from '@ant-design/icons-vue'
import Homophone from '../../../../../../../common/utils/Homophone'
  
import fontBank from '../../../../../../../common/utils/fontBank'

export default function parctice(countDown) {
  onMounted(() => {
    init()
  })
  // PubSub.subscribe('send_hanziPage', (e)=>{
  //   if (trainData.value.status === 1) {
  //     Modal.confirm({
  //       class: 'init_modal_style',
  //       content: '当前练习还未结束，是否暂停练习？',
  //       icon: () => createVNode(ExclamationCircleOutlined),
  //       okType: 'danger',
  //       okText: () => '暂停',
  //       cancelText: () => '取消',
  //       maskClosable: true,
  //       onOk: () => {
  //         stopTest()
  //       }
  //     })
  //   }else {
  //     PubSub.publish('callback_closehanziPage', true);
  //   }
  // });
  const activeMessage = ref({
    text: ''
  })
  const activeIndex = ref(0) //选中的卡片下标
  const inputIndex = ref(0) //光标所在位置的下标
  const isfocus = ref(true)
  const message = ref(null)
  const autoTime = ref(null)
  const small = ref(0)
  const big = ref(200)
  const route = useRoute()
  const router = useRouter()
  const queryID = route.query.id
  const correct = ref(0)
  let isFirst = 0 //判断是否第一次按键
  const trainData = ref({})
  //统计时间
  const nowTime = ref({})
  const init = () => {
    computationTime2(0)
    getById({ id: route.query.id }).then(res => {
      trainData.value.duration = res.data.duration
      trainData.value.accuracy = parseInt(res.data.accuracy)
      trainData.value.speed = res.data.speed
      trainData.value.errorNum = res.data.errorNum
      trainData.value.correctNum = res.data.correctNum
      trainData.value.status = res.data.status
      trainData.value.duration = res.data.duration
      trainData.value.type = res.data.type
      const fonts = []
      if (res.data.type == 4) {
        const hp = new Homophone()
        const arr = JSON.parse(res.data.content)[0].split('')
        for (let v of arr) {
          const py = hp.convertPY(v)
          if (py != '') {
            const pys = []
            for (let p of py) {
              pys.push({
                trueOrfalse: null,
                py: p
              })
            }
            fonts.push({
              font: v,
              pys: pys
            })
          } else {
            fonts.push({
              font: v,
              pys: [{ trueOrfalse: null, isfocus: null, py: v }]
            })
          }
        }
        trainData.value.message = fonts
      } else if (res.data.type != 0) {
        const font_Bnak = fontBank.font.split(';')
        const codes = fontBank.code.split(';')
        const hp = new Homophone()
        const pystr = JSON.parse(res.data.content)
        pystr.forEach(item => {
          const arr = item.split('')
          let code = ''
          for (let v of arr) {
            const index = font_Bnak.lastIndexOf(v)
            code += codes[index]
          }
          const font = item
          // const py = hp.convertPYs(font)[0]
          const pys = []
          for (let v of code) {
            pys.push({
              trueOrfalse: null,
              py: v
            })
          }
          fonts.push({
            pys,
            font,
            trueOrfalse: null
          })
        })
      } else if (res.data.type == 0) {
        const fontAll = JSON.parse(res.data.content)
        fontAll.forEach(item => {
          const font = item.font
          const pys = []
          for (let v of item.pys) {
            pys.push({
              trueOrfalse: null,
              py: v
            })
          }
          fonts.push({
            pys: pys,
            font: font,
            trueOrfalse: null
          })
        })
      }
      message.value = fonts
      activeMessage.value = fonts[0]
    })
    window.addEventListener('keydown', keyCodeDown)
  }
  //统计正确，错误，码率
  const statistics = () => {
    let errorNum = 0
    let correctNum = 0
    message.value.forEach(item => {
      if (item.isFocus) {
        switch (item.trueOrfalse) {
          case true:
            correctNum++
            break
          case false:
            errorNum++
            break
        }
      }
    })
    trainData.value.accuracy = correctNum != 0 ? ((correctNum / (errorNum + correctNum)) * 100).toFixed(2) : 0
    trainData.value.errorNum = errorNum
    trainData.value.correctNum = correctNum
  }
  //组装保存数据
  const garde = () => {
    const trueOrfalse = activeMessage.value.pys.some(p => p.trueOrfalse == false)
    if (trueOrfalse) {
      activeMessage.value.trueOrfalse = false
    } else {
      activeMessage.value.trueOrfalse = true
    }
    statistics()
    const data = {
      id: queryID,
      duration: trainData.value.duration,
      accuracy: trainData.value.accuracy,
      speed: trainData.value.speed,
      correctNum: trainData.value.correctNum,
      errorNum: trainData.value.errorNum,
      content: JSON.stringify(message.value)
    }
    return data
  }
  //保存训练类容
  const saveTest = () => {
    hanziFinish(garde()).then(res => {
      clearInterval(autoTime.value)
      trainData.value.status = 2
    })
  }
  //暂停训练
  const stopTest = () => {
    hanziPause(garde()).then(res => {
      // PubSub.publish('callback_closehanziPage', true);
    })
  }
  // 训练用时
  const trainTime = () => {
    autoTime.value = setInterval(() => {
      trainData.value.duration++
      // countDown.value.autoSetTimeAdd(trainData.value.duration)
      computationTime2(trainData.value.duration)
      if (trainData.value.correctNum != 0 || trainData.value.errorNum != 0) {
        trainData.value.speed = Number(((trainData.value.errorNum + trainData.value.correctNum) / (trainData.value.duration / 60)).toFixed(2))
      }
    }, 1000)
  }
  const keyCodeDown = v => {
    if (isFirst == 0) {
      begin({ id: route.query.id }).then(r => {
        trainData.value.status = 1
        trainTime()
      })
      isFirst = 1
    }
    if (v.preventDefault) {
      v.preventDefault()
    } else {
      window.event.returnValue == false
    }

    if (big.value - activeIndex.value > 200) {
      small.value = activeIndex.value
      big.value = small.value + 200
      nextTick(() => {
        const scrol = document.getElementsByClassName('scorebox')
        scrol[0].scrollTop = 0
      })
    }
    if (v.keyCode == 8 && inputIndex.value == 0 && activeIndex.value != 0) {
      //删除键
      activeIndex.value--
      activeMessage.value = message.value[activeIndex.value]
      inputIndex.value = activeMessage.value.pys.length
      activeMessage.value.trueOrfalse = null
      activeMessage.value.isFocus = false
      statistics()
    } else if (v.keyCode == 8 && inputIndex.value != 0) {
      //删除键
      inputIndex.value--
      activeMessage.value.pys[inputIndex.value].trueOrfalse = null
    } else if (v.keyCode != 8) {
      activeMessage.value.isFocus = true
      if (inputIndex.value == activeMessage.value.pys.length) {
        const trueOrfalse = activeMessage.value.pys.some(p => p.trueOrfalse == false)
        if (trueOrfalse) {
          activeMessage.value.trueOrfalse = false
        } else {
          activeMessage.value.trueOrfalse = true
        }
        statistics()
        activeIndex.value++
        inputIndex.value = 0
        activeMessage.value = message.value[activeIndex.value]
        //专注模式控制滚动条
        const scrol = document.getElementsByClassName('scorebox')
        const activeBox = document.getElementsByClassName('activeBox')
        //专注模式控制滚动条
        //专注模式控制滚动条
        if (isfocus.value) {
          nextTick(() => {
            if (activeBox[0].offsetTop - scrol[0].scrollTop - 334 > 113) {
              scrol[0].scrollTop = scrol[0].scrollTop + 109
            }
          })
        } else {
          nextTick(() => {
            if (activeBox[0].offsetTop > 113) {
              scrol[0].scrollTop = activeBox[0].offsetTop - 109
              if (scrol[0].scrollTop > scrol[0].scrollHeight / 2 && big.value < message.value.length) {
                big.value += 50
                small.value += 50
              }
            }
          })
        }
      } else {
        let key = v.key
        if (v.key == 'Process') {
          key = v.code.substring(3, 4)
        }
        if (key.toLowerCase() == activeMessage.value.pys[inputIndex.value].py) {
          activeMessage.value.pys[inputIndex.value].trueOrfalse = true
        } else {
          activeMessage.value.pys[inputIndex.value].trueOrfalse = false
        }
        inputIndex.value++
      }
    }
  }
  const keyCodeDown2 = v => {}
  const changeSwitch = item => {
    isfocus.value = item
    if (item) {
      nextTick(() => {
        //专注模式控制滚动条
        const scrol = document.getElementsByClassName('scorebox')
        const activeBox = document.getElementsByClassName('activeBox')
        // scrol[0].scrollTop+=97
        scrol[0].scrollTop = activeBox[0].offsetTop - scrol[0].offsetTop
      })
    }
  }
  //改变专注模式值
  const selectMessage = (item, index) => {
    activeIndex.value = index
  }
  //改变输入框值
  const changeMessage = v => {
    v.value = v.value.toUpperCase()
  }
  //输入框失去焦点
  const changeFocus = input => {
    trainData.value.errorNumber = 0
    trainData.value.accuracy = 0
    if (input.text == input.value) {
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
      document.getElementsByTagName('input')[activeIndex.value].focus()
    }
  }
  const goback = () => {
    router.go(-1)
  }
  //滚动控制显示的组数
  const scoreRoll = e => {
    if (e.target.scrollTop > e.target.scrollHeight / 2 && big.value < message.value.length) {
      big.value += 50
      small.value += 50
    } else if (e.target.scrollTop < 50 && small.value > 0) {
      big.value -= 50
      small.value -= 50
    }
  }
  const inputFocus = () => {
    document.getElementsByTagName('input')[0].focus()
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
  onUnmounted(() => {
    // PubSub.unsubscribe("send_hanziPage");
  })

  onBeforeUnmount(() => {
    window.removeEventListener('keydown', keyCodeDown)
    clearInterval(autoTime.value)
    saveTest()
  })
  return {
    message,
    trainData,
    correct,
    activeMessage,
    isfocus,
    activeIndex,
    inputIndex,
    big,
    small,
    nowTime,
    computationTime2,
    scoreRoll,
    statistics,
    inputFocus,
    selectCard,
    keyCodeDown,
    keyCodeDown2,
    changeSwitch,
    selectMessage,
    goback,
    saveTest,
    changeMessage,
    changeFocus,
    getFocus
  }
}
