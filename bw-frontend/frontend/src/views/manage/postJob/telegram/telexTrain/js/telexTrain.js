import { onBeforeUnmount, onMounted, onUnmounted, ref, watch, nextTick, createVNode } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getTelexTrainByID, postTelexPatTrain, endTelexPatTrain } from '../../../../../../common/api/TelegramApi.js'
import { message, Modal } from 'ant-design-vue'
import { PubSub } from '../../../../../../common/utils/PubSub'
import { deepClone } from '../../../../../../common/utils/Utils.js'
import { ExclamationCircleOutlined } from '@ant-design/icons-vue'

export default function (countDown) {
  const router = useRouter()
  const route = useRoute()
  onMounted(() => {
    window.addEventListener('keydown', keyCodeup)
    init()
  })
  PubSub.subscribe('send_telexTrainPage', e => {
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
          endTest()
        }
      })
    } else {
      PubSub.publish('callback_closeTelexTrainPage', true)
    }
  })
  let cutDown = null
  let time = 0
  const code = ref([])
  const activeCode = ref([])
  const compileCode = ref([[]]) //当前页展示报文
  const activeIndex = ref(0)
  const correctCode = ref([]) //所有编译过后的报文
  const compileCodePage = ref([]) //所有页的展示报文
  let serial = {
    //串行串少时使用
    type: false,
    code: [],
    first: 0,
    number: 0 //添加的组数
  }
  let isPage = false //是否已经输入分页
  const trainData = ref({
    id: '',
    errorNumber: 0,
    successNumber: 0,
    accuracy: '',
    speed: 0,
    validTime: 0,
    groupNumber: 0,
    content: '',
    duration: 0,
    status: 0
  })
  let contentAll = null
  let lineArr = []
  const page = ref({
    pageAll: 0,
    current: 1
  })
  // 训练用时
  const autoTime = ref(null)
  const trainTime = () => {
    autoTime.value = setInterval(() => {
      trainData.value.duration++
      countDown.value.autoSetTimeAdd(trainData.value.duration)
    }, 1000)
  }
  const startTest = () => {
    postTelexPatTrain({ id: route.query.id }).then(res => {
      if (res.code == 200) {
        if (page.value.current !== 1) {
          page.value.current = 2
          prev()
        }
        trainData.value.status = res.data.status
        trainTime()
        setTime()
      }
    })
  }
  const init = () => {
    getTelexTrainByID({ id: route.query.id }).then(res => {
      if (res.data.status == 1) {
        trainTime()
      }
      contentAll = JSON.parse(res.data.content)
      let arr = []
      contentAll.filter((item, index) => {
        if (index < 100) {
          arr.push(item)
        }
      })
      trainData.value.content = arr
      page.value.pageAll = Math.ceil(contentAll.length / 100)
      trainData.value.id = res.data.id
      trainData.value.errorNumber = res.data.errorNumber
      trainData.value.accuracy = parseInt(res.data.accuracy)
      trainData.value.speed = Number(res.data.speed)
      trainData.value.validTime = res.data.validTime
      trainData.value.groupNumber = res.data.groupNumber
      trainData.value.status = res.data.status
    })
  }
  const keyCodeup = v => {
    if (v.preventDefault) {
      v.preventDefault()
    } else {
      window.event.returnValue == false
    }
    if (trainData.value.status != 1) {
      return false
    }
    // if(v.key.length==1?v.key.toUpperCase():(v.key=="Alt"||v.key=="Enter")){
    if (v.key.length == 1 ? v.key.toUpperCase() : v.key == 'Enter') {
      code.value.push({
        text: v.key.length == 1 ? v.key.toUpperCase() : v.key,
        time: time
      })
    }
    // if(v.key.length==1||v.key=="Alt"||v.key=="Enter"){
    if (v.key.length == 1 || v.key == 'Enter') {
      activeCode.value.push({
        text: v.key.length == 1 ? v.key.toUpperCase() : v.key,
        time: time
      })
    }
    nextTick(() => {
      const DOM = document.getElementsByClassName('vals')[0]
      DOM.scrollLeft = DOM.scrollWidth - DOM.offsetWidth
    })
    compiledCode()
    setTime()
  }
  const setTime = () => {
    time = 0
    clearInterval(cutDown)
    cutDown = setInterval(() => {
      time++
    }, 100)
  }
  //编译报文
  const compiledCode = () => {
    const lastindex = compileCode.value.length > 0 ? compileCode.value.length - 1 : 0
    compileCode.value[lastindex] = []
    let str = ''
    activeIndex.value = lastindex * 10
    activeCode.value.forEach((v, index) => {
      const codeI = code.value.length
      // if(code.value[codeI-1].text=="Enter" &&code.value[codeI-2].text =="Enter"&&code.value[codeI-3].text =="Alt"){
      if (code.value[codeI - 1].text == 'Enter' && code.value[codeI - 2].text == 'Enter') {
        if (correctCode.value.length < page.value.current * 100) {
          for (let i = correctCode.value.length; i < page.value.current * 100; i++) {
            correctCode.value[i] = undefined
          }
        }
        next2()
        return false
      }
      const lastindex = compileCode.value.length > 0 ? compileCode.value.length - 1 : 0
      const childrenLastIndex = compileCode.value[lastindex].length > 0 ? compileCode.value[lastindex].length - 1 : 0
      if (v.text == ' ') {
        str = ''
        compileCode.value[lastindex].push('')
        if (index > 3 && activeCode.value[index - 1].text != '/' && activeCode.value[index - 2].text != '/' && activeCode.value[index - 3].text != '/' && activeCode.value[index - 4].text != '/') {
          if (activeIndex.value.toString().indexOf('9') == -1) {
            activeIndex.value++
          }
        }
        if (index > 3 && activeCode.value[index - 1].text == '/' && activeCode.value[index - 2].text == '/' && activeCode.value[index - 3].text == '/' && activeCode.value[index - 4].text == '/') {
          activeIndex.value--
        }
      } else if (v.text == 'Alt') {
        str = ' '
      }
      // else if (index>0&&v.text == "Enter"&&activeCode.value[index-1].text=="Alt"){
      else if (index > 0 && v.text == 'Enter') {
        //换行
        statisticalNumber()
        if (isPage) {
          //分页后处理错误的报文
          afterPageEditCode()
        } else {
          //换行统计本行正确错误报文数....
          const n = page.value.current * 100 + lastindex * 10 - 100
          for (let i = 0; i < 10; i++) {
            if (lineArr[i] == contentAll[n + Number(i)].text.join('')) {
              contentAll[n + i].value = true
            } else {
              contentAll[n + i].value = false
            }
            correctCode.value.push(lineArr[i])
          }
        }
        str = ''
        compileCode.value.push([])
        activeIndex.value = Number(lastindex + 1 + '0')
        activeCode.value = []
        if (lineArr[lineArr.length - 1].indexOf('-') > -1) {
          isPage = true
        }
      } else if (v.text !== 'Enter') {
        str += v.text
        compileCode.value[lastindex][childrenLastIndex] = str
      }
    })

    nextTick(() => {
      const DOM = document.getElementsByClassName('keys')[0]
      DOM.scrollTop = DOM.scrollHeight
    })
  }
  //统计报文正确、错误数量
  const statisticalNumber = () => {
    reviseCode()
    trainData.value.successNumber = 0
    trainData.value.errorNumber = 0
    contentAll.forEach(item => {
      if (item.value === true) {
        trainData.value.successNumber++
      } else if (item.value === false) {
        trainData.value.errorNumber++
      }
    })
  }
  //报文分页前及分页改错
  const reviseCode = () => {
    const arr = deepClone(compileCode.value)
    //判断是否有改错型报文
    arr.forEach((item, index) => {
      item.forEach((item2, index2) => {
        let num = index * 10 + index2 - 1 > 0 ? index * 10 + index2 - 1 : 0
        if (item2 === '////') {
          //第四码发错拍发间隔
          // num = index*10+index2-2>0?index*10+index2-2:0
          item[index2 - 1] = item[index2 + 1]
          if (contentAll[num].text.join('') == item[index2 + 1]) {
            contentAll[num].value = true
          } else {
            contentAll[num].value = false
          }
          item.splice(index2, 2)
        } else if (item2.indexOf('/') > -1 && item2.length > 4 && item2.indexOf('-') == -1) {
          //第四码发错未拍间隔
          item2 = item2.substring(item2.indexOf('/') + 1, item2.length)
          item[index2] = item2
          num = index * 10 + index2 > 0 ? index * 10 + index2 : 0
          if (contentAll[num].text.join('') == item2) {
            contentAll[num].value = true
          } else {
            contentAll[num].value = false
          }
        } else if (item[index2 - 1] && item[index2 - 1].indexOf('/') > -1 && item[index2 - 1].length == 4) {
          //前三码发错型A///
          item[index2 - 1] = item2
          if (contentAll[num].text.join('') == item2) {
            contentAll[num].value = true
          } else {
            contentAll[num].value = false
          }
          item.splice(index2, 1)
        }
        //行尾修改报文
        if (index2 > 9 && item2.length != 1 && item[index2 - 1].length == 1 && item[index2 - 2] !== 'ADD' && item[index2 - 2] !== 'QAT') {
          const n = Number(item[index2 - 1]) - 1
          item[n] = item2
          item.splice(index2 - 1, 1)
          if (contentAll[index * 10 + n].text.join('') == item[n]) {
            contentAll[index * 10 + n].value = true
          } else {
            contentAll[index * 10 + n].value = false
          }
        }
        // 行尾增报文组数（漏拍）
        if (index2 > 1 && item[index2 - 2] == 'ADD' && item[index2 - 1].length == 1 && item2.length == 4) {
          const n = Number(item[index2 - 1])
          item.splice(index2 - 2, 2)
          item.splice(n - 1, 0, item2)
        }
        // 行尾减报文组数（多拍）
        if (index2 > 9 && item[index2 - 1] == 'QAT') {
          const n = Number(item2)
          item.splice(n - 1, 1)
        }
        //分页错误
        if (item2.indexOf('-') > -1 && item2.indexOf('/') > -1) {
          const strs = item2.split('-')
          item[index2] = strs[0] + '-' + strs[1].split('/')[1]
        }
      })
    })
    const lastindex = compileCode.value.length > 0 ? compileCode.value.length - 1 : 0
    lineArr = deepClone(arr)[lastindex]
  }
  //分页后修改报文
  const afterPageEditCode = () => {
    if (serial.type === true && serial.code.length < serial.number) {
      for (let i = 0; i < 10; i++) {
        if (lineArr[i]) {
          serial.code.push(lineArr[i])
        } else {
          serial.code.push(undefined)
        }
      }
      if (serial.number == serial.code.length) {
        const n = page.value.current * 100 - 100 + serial.first
        correctCode.value.splice(n, 0, ...serial.code)
        serial = {
          //串行串少时使用
          type: false,
          code: [],
          first: 0,
          number: 0 //添加的组数
        }
      }
      return false
    }
    lineArr.forEach((item, index) => {
      if (lineArr[index - 2] && isNaN(lineArr[index - 2])) {
        //修改其他页错误报文
        const p = lineArr[index - 2].substring(0, lineArr[index - 2].indexOf('P'))
        const n = Number(p) * 100 - 100 + Number(lineArr[index - 1]) - 1
        correctCode.value[n] = item
      } else if (Number(lineArr[index - 1]) < 101) {
        //修改本页的报文
        const n = page.value.current * 100 - 100 + Number(lineArr[index - 1]) - 1
        correctCode.value[n] = item
      }
      //串行
      if (isNaN(item) && item.indexOf('---') != -1) {
        // 串多
        if (lineArr[index - 1] == 'QTA') {
          const arr = item.split('---')
          const n = Number(arr[1]) - Number(arr[0]) + 1
          const n2 = page.value.current * 100 - 100 + Number(arr[0]) - 1
          correctCode.value.splice(n2, n)
        }
        //串少
        if ((item.indexOf('---') > -1 && index == 0 && serial.type === false) || (lineArr[index - 1] == 'ADD' && serial.type === false)) {
          const arr = item.split('---')
          serial.number = Number(arr[1]) - Number(arr[0]) + 1
          serial.first = Number(arr[0]) - 1
          serial.type = true
        }
      }
    })
  }
  //下一页
  const next = () => {
    if (trainData.value.status != 0) {
      return false
    }
    if (page.value.current == page.value.pageAll) {
      message.error('已是最后一页！')
      return false
    }
    isPage = false
    activeIndex.value = 0
    activeCode.value = []
    page.value.current++
    compileCodePage.value.push(deepClone(compileCode.value))
    compileCode.value = []

    trainData.value.content = []
    for (let i in contentAll) {
      if (i < page.value.current * 100 && i > page.value.current * 100 - 101) {
        trainData.value.content.push(contentAll[i])
      }
    }
  }
  //下一页
  const next2 = () => {
    if (page.value.current == page.value.pageAll) {
      message.error('已是最后一页！')
      return false
    }
    isPage = false
    activeIndex.value = 0
    activeCode.value = []
    page.value.current++
    compileCodePage.value.push(deepClone(compileCode.value))
    compileCode.value = []
    //统计正确错误个数
    correctCode.value.forEach((item, index) => {
      if (item == contentAll[index].text.join('')) {
        trainData.value.successNumber++
      } else {
        trainData.value.errorNumber++
      }
    })
    //统计速度
    let codeAllNumber = 0
    compileCodePage.value.forEach(item => {
      codeAllNumber = item.length + codeAllNumber
    })
    trainData.value.speed = (codeAllNumber / (trainData.value.duration / 60)).toFixed(2) * 1
    //
    trainData.value.accuracy = ((trainData.value.successNumber / (trainData.value.successNumber + trainData.value.errorNumber)) * 100).toFixed(2)
    trainData.value.content = []
    for (let i in contentAll) {
      if (i < page.value.current * 100 && i > page.value.current * 100 - 101) {
        trainData.value.content.push(contentAll[i])
      }
    }
  }
  //上一页
  const prev = () => {
    if (trainData.value.status != 0) {
      return false
    }
    if (page.value.current == 1) {
      message.error('已是第一页！')
      return false
    }
    page.value.current--
    trainData.value.content = []
    for (let i in contentAll) {
      if (i < page.value.current * 100 && i > page.value.current * 100 - 101) {
        trainData.value.content.push(contentAll[i])
      }
    }
    activeIndex.value = trainData.value.content.findIndex((item, index) => {
      if (item.value == null) {
        return index
      }
    })
  }
  const endTest = () => {
    compileCodePage.value[page.value.current - 1] = deepClone(compileCode.value)
    statisticalNumber()
    correctCode.value.push(...lineArr)
    if (correctCode.value.length < page.value.pageAll * 100) {
      for (let i = correctCode.value.length; i < page.value.current * 100; i++) {
        correctCode.value[i] = undefined
      }
    }
    //统计速度
    let codeAllNumber = 0
    compileCodePage.value.forEach(item => {
      codeAllNumber = item.length + codeAllNumber
    })
    trainData.value.speed = (codeAllNumber / (trainData.value.duration / 60)).toFixed(2) * 1
    let successN = 0
    contentAll.forEach((item, index) => {
      if (correctCode.value[index]) {
        if (item.text.join('') == correctCode.value[index]) {
          item.value = true
          successN++
        } else {
          item.value = false
        }
        item.code = correctCode.value[index]
      } else {
        item.value = false
        item.code = undefined
      }
    })
    const codes = {
      pageCode: compileCodePage.value,
      codeAll: code.value,
      content: contentAll
    }
    const data = {
      id: route.query.id,
      errorNumber: contentAll.length - successN,
      accuracy: ((successN / contentAll.length) * 100).toFixed(2),
      speed: trainData.value.speed,
      validTime: trainData.value.duration,
      content: JSON.stringify(codes)
    }

    endTelexPatTrain(data).then(res => {
      clearInterval(autoTime.value)
      trainData.value.status = 3

      PubSub.unsubscribe('callback_closeTelexTrainPage', true)
      router.push({
        path: route.matched[4].path + '/trainScore',
        query: {
          id: route.query.id
        }
      })
    })
  }
  onUnmounted(() => {
    PubSub.unsubscribe('send_telexTrainPage')
  })
  onBeforeUnmount(() => {
    window.removeEventListener('keydown', keyCodeup)
    clearInterval(cutDown)
    if (autoTime.value) {
      clearInterval(autoTime.value)
    }
  })
  return {
    trainData,
    code,
    compileCode,
    activeIndex,
    compileCodePage,
    page,
    next,
    prev,
    startTest,
    endTest
  }
}
