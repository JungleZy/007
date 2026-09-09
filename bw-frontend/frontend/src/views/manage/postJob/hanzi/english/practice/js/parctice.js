import { onUnmounted, onMounted, ref, onBeforeUnmount, createVNode, nextTick, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getById, begin, hanziFinish } from '../../../../../../../common/api/postHanZi'
import { PubSub } from '../../../../../../../common/utils/PubSub.js'
import { deepClone } from '../../../../../../../common/utils/Utils.js'
import { Modal } from 'ant-design-vue'
import { ExclamationCircleOutlined } from '@ant-design/icons-vue'
import Homophone from '../../../../../../../common/utils/Homophone'
import { apiPostTrainGlobalRuleType } from '../../../../../../../common/api/postWording'

export default function parctice(countDown) {
  onMounted(() => {
    init()
  })
  PubSub.subscribe('send_hanziPage', e => {
    if (trainData.value.status === 1) {
      Modal.confirm({
        class: 'init_modal_style',
        content: '当前练习还未结束，是否暂停练习？',
        icon: () => createVNode(ExclamationCircleOutlined),
        okType: 'danger',
        okText: () => '结束',
        cancelText: () => '取消',
        maskClosable: true,
        onOk: () => {
          saveTest()
        }
      })
    } else {
      PubSub.publish('callback_closehanziPage', true)
    }
  })
  const activeMessage = ref({
    text: ''
  })
  const activeIndex = ref(0) //选中的卡片下标
  const inputIndex = ref(0) //光标所在位置的下标
  const isfocus = ref(false)
  const message = ref({})
  const autoTime = ref(null)
  const route = useRoute()
  const router = useRouter()
  const correct = ref(0)
  const trainData = ref({})
  const gradeTypeList = ref([])
  const init = () => {
    getById({ id: route.query.id }).then(res => {
      trainData.value.duration = res.data.duration
      trainData.value.accuracy = parseInt(res.data.accuracy)
      trainData.value.speed = res.data.speed
      trainData.value.errorNum = res.data.errorNum
      trainData.value.correctNum = res.data.correctNum
      trainData.value.status = res.data.status
      trainData.value.duration = res.data.duration
      trainData.value.type = res.data.type

      getGradeTypeList(trainData.value.accuracy)
      const mark = ['!', '.', ',', '?', ';', ':', "'", '{', '}', '[', ']', '(', ')', '@', '#', '$', '%', '^', '&', '*', '.']
      if (res.data.status === 2) {
        countDown.value.autoSetTimeAdd(trainData.value.duration)
        message.value = JSON.parse(res.data.content)
        message.value.forEach(item => {
          const tfArr = []
          const arr = item.font.split(' ')
          const valueArr = item.value.split(' ')
          for (let i in arr) {
            if (arr[i] === valueArr[i]) {
              tfArr.push({
                text: valueArr[i],
                type: true
              })
            } else {
              tfArr.push({
                text: valueArr[i],
                type: false
              })
            }
          }
          item.tfArr = tfArr
        })
      } else {
        const fonts = []
        const width = document.querySelectorAll('.wz')[0].clientWidth - 20
        const lineNum = width / 7.3 - 5
        const arrs = JSON.parse(res.data.content)
        for (let j in arrs) {
          const arr = arrs[j].trim().split('')
          let line = []
          let s = []
          let count = 0
          for (let i = 0; i < arr.length; i++) {
            s.push(arr[i])
            count++
            if (i === arr.length - 1) {
              line.push(s)
              break
            }
            if (lineNum - count < 6 && arr[i] === ' ') {
              line.push(s)
              count = 0
              s = []
            }
          }
          for (let n in line) {
            const arr = deepClone(line[n])
            arr.forEach((item, index) => {
              if (mark.some(m => m === item)) {
                arr[index] = ' ' + item
              }
            })
            fonts.push({
              font: arr.join(''),
              value: '',
              isFirst: n === 0 ? true : false
            })
          }
        }

        message.value = fonts
        activeMessage.value = fonts[0]
      }
      if (res.data.status === 1) {
        trainTime()
      }
      if (res.data.type === 3) {
        message.value[0].isFocus = true
      }
    })
  }

  //获取评分列表
  const getGradeTypeList = number => {
    const num = number ?? 0
    apiPostTrainGlobalRuleType({ type: 3 }).then(res => {
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

  //组装保存数据
  const garde = () => {
    let correctNum = 0
    let errorNum = 0
    let valueLen = 0
    message.value.forEach(item => {
      valueLen = valueLen + item.value.length
      const arr = item.font.split(' ')
      const valueArr = item.value.split(' ')
      for (let i in arr) {
        if (arr[i] === valueArr[i]) {
          correctNum++
        } else {
          errorNum++
        }
      }
    })
    const data = {
      id: route.query.id,
      duration: trainData.value.duration,
      accuracy: Number(((correctNum / (correctNum + errorNum)) * 100).toFixed(2)),
      speed: Number((valueLen / (trainData.value.duration / 60)).toFixed(2)),
      correctNum: correctNum,
      errorNum: errorNum,
      content: JSON.stringify(message.value)
    }
    trainData.value.accuracy = Number(data.accuracy)
    trainData.value.speed = Number(data.speed)
    trainData.value.correctNum = data.correctNum
    trainData.value.errorNum = data.errorNum
    return data
  }
  //保存训练类容
  const saveTest = () => {
    hanziFinish(garde()).then(res => {
      clearInterval(autoTime.value)
      trainData.value.status = 2
      message.value.forEach(item => {
        const tfArr = []
        const arr = item.font.split(' ')
        const valueArr = item.value.split(' ')
        for (let i in arr) {
          if (arr[i] === valueArr[i]) {
            tfArr.push({
              text: valueArr[i],
              type: true
            })
          } else {
            tfArr.push({
              text: valueArr[i],
              type: false
            })
          }
        }
        item.tfArr = tfArr
      })
      PubSub.publish('callback_closehanziPage', true)
      init()
    })
  }
  // 训练用时
  const trainTime = () => {
    autoTime.value = setInterval(() => {
      trainData.value.duration++
      countDown.value.autoSetTimeAdd(trainData.value.duration)
    }, 1000)
  }
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
  const goback = () => {
    router.go(-1)
  }
  const beginTrain = () => {
    begin({ id: route.query.id }).then(r => {
      trainData.value.status = 1
      if (trainData.value.type === 3) {
        message.value[0].isFocus = true
      }
      nextTick(() => {
        document.querySelectorAll('.wz input')[0].focus()
      })
      trainTime()
    })
  }
  onUnmounted(() => {
    PubSub.unsubscribe('send_hanziPage')
  })
  return {
    message,
    trainData,
    correct,
    activeMessage,
    isfocus,
    activeIndex,
    inputIndex,
    beginTrain,
    changeSwitch,
    goback,
    saveTest,
    gradeTypeList
  }
}
