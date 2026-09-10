import { onBeforeUnmount, onMounted, onUnmounted, ref, watch, nextTick, createVNode } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getTelexTrainByID, postTelexPatTrain, endTelexPatTrain, apiPostTelexPatTrainGetPage, apiPostTelexPatTrainFinishPage } from '../../../../../../common/api/TelegramApi.js'
import { message, Modal } from 'ant-design-vue'
import { PubSub } from '../../../../../../common/utils/PubSub'
import { deepClone } from '../../../../../../common/utils/Utils.js'
import { ExclamationCircleOutlined } from '@ant-design/icons-vue'

export default function (countDown, loading) {
  const router = useRouter()
  const route = useRoute()
  let startNum = 0
  const keyDownStart = (key)=>{
    if (trainData.value.status === 0&&startNum===0) {
      startNum++
      startTest()
    }
    keyCodeup(key)
  }
  onMounted(() => {
    window.addEventListener('keydown', keyDownStart)
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
  const activeIndex = ref(0) //当前报文播报的下标
  const correctCode = ref([]) //所有编译过后的报文
  const compileCodePage = ref([]) //所有页的展示报文

  let isPage = ref(false) //是否已经输入分页
  const trainData = ref({
    id: '',
    errorNumber: 0,
    successNumber: 0,
    accuracy: '',
    speed: 0,
    validTime: 0,
    groupNumber: 0,
    preContent: [],
    content: '',
    nextContent: [],
    duration: 0,
    status: 0,
    pageTime: 0,
    speedList: []
  })
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
      trainData.value.pageTime++
      countDown.value.autoSetTimeAdd(trainData.value.duration)

      let num = 0
      code.value.forEach(item => {
        num += item.length
      })

      trainData.value.speed = (num / (trainData.value.duration / 60)).toFixed(1) * 1
    }, 1000)
  }
  const startTest = () => {
    postTelexPatTrain({ id: route.query.id }).then(res => {
      if (res.code == 200) {
        if (page.value.current !== 1) {
          page.value.current = 1
          init()
          // prev()
        }
        trainData.value.status = res.data.status
        trainTime()
        setTime()
      }
    })
  }
  const init = () => {
    getTelexTrainByID({ id: route.query.id }).then(res => {
      loading.value = false
      if (res.data.status == 1) {
        trainTime()
      }
      if (res.data.isCable === 1) {
        trainData.value.content = res.data.existPage.slice(0, 100)
        trainData.value.nextContent = res.data.existPage.slice(100, 200)
        page.value.pageAll = res.data.pageNumber
      } else {
        trainData.value.content = res.data.existPage.slice(0, 100)
        trainData.value.nextContent = res.data.existPage.slice(100, 200)
        page.value.pageAll = Math.ceil(res.data.groupNumber / 100)
      }
      trainData.value.id = res.data.id
      trainData.value.accuracy = parseInt(res.data.accuracy)
      trainData.value.speed = Number(res.data.speed)
      trainData.value.validTime = res.data.validTime
      trainData.value.groupNumber = res.data.groupNumber
      trainData.value.status = res.data.status
    })
  }
  const keyCodeup = v => {
    if (v.key == 'Control') return
    if (v.preventDefault) {
      v.preventDefault()
    } else {
      window.event.returnValue == false
    }
    // if (trainData.value.status != 1) {
    //   return false
    // }
    activeIndx(v)
    if (v.key.length == 1 ? v.key.toUpperCase() : v.key == 'Enter') {
      if (!code.value[page.value.current - 1]) {
        code.value.push([])
      }
      code.value[page.value.current - 1].push({
        text: v.key.length == 1 ? v.key.toUpperCase() : v.key,
        time: time
      })
    }
    if (v.key.length == 1 || v.key == 'Enter') {
      activeCode.value.push({
        text: v.key.length == 1 ? v.key.toUpperCase() : v.key,
        time: time
      })
    }
    if(v.code=="F4"){prev()}
    if(v.code=="F5"){
      if(v.preventDefault){
        v.preventDefault()
      }else {
        window.event.returnValue == false
      }
      next()
    }
    nextTick(() => {
      const DOM = document.getElementsByClassName('vals')[0]
      DOM.scrollLeft = DOM.scrollWidth - DOM.offsetWidth
      const DOM2 = document.getElementsByClassName('keys')
      const DOM3 = document.getElementsByClassName('activeLight')
      if(DOM3[0].offsetLeft>562){
        DOM2[0].scrollLeft = DOM3[0].offsetLeft-DOM2[0].clientWidth + 10
      }
      if(v.key == 'Enter'){
        DOM2[0].scrollLeft = 0
      }

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
  //控制下标位置
  let count = {
    str: '',
    slash: 0
  }
  const activeIndx = v => {
    count.str += v.key
    switch (v.keyCode) {
      case 191: // "/"
        count.slash++
        break
    }
    if (activeIndex.value % 10 != 9) {
      const arr = count.str.split('')
      if (v.keyCode == 32) {
        if (arr[arr.length - 2] !== '/') {
          activeIndex.value++
          count = {
            str: '',
            slash: 0
          }
        }
      }
      if (arr[0] === '/' && arr.length == 1) {
        activeIndex.value--
      }
    }
  }
  //编译报文
  const compiledCode = () => {
    const lastindex = compileCode.value.length - 1 ?? 0
    compileCode.value[lastindex] = []
    let str = ''
    // activeIndex.value=lastindex*10
    activeCode.value.forEach((v, index) => {
      const codeI = code.value[page.value.current - 1].length
      //翻页
      if (code.value[page.value.current - 1][codeI - 1].text == 'Enter' && code.value[page.value.current - 1][codeI - 2]?.text == 'Enter') {
        if (correctCode.value.length < page.value.current * 100) {
          //判断编译过后的报文数量是否小于总的报文数
          for (let i = correctCode.value.length; i < page.value.current * 100; i++) {
            correctCode.value[i] = undefined
          }
        }
        next2()
        return false
      }
      const lastindex = compileCode.value.length > 0 ? compileCode.value.length - 1 : 0
      const childrenLastIndex = compileCode.value[lastindex]?.length > 0 ? compileCode.value[lastindex].length - 1 : 0
      if (v.text == ' ') {
        str = ''
        compileCode.value[lastindex].push('')
      } else if (index > 0 && v.text == 'Enter') {
        //换行
        str = ''
        compileCode.value.push([])
        activeIndex.value = Number(lastindex + 1 + '0')
        activeCode.value = []
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
  let pageLoding = false
  const getPostTelegraphKeyPatTrainGetPage = num => {
    let number = page.value.current + num
    if (number > page.value.pageAll || number < 1) return
    pageLoding = true
    apiPostTelexPatTrainGetPage({
      pageNumber: number,
      trainId: trainData.value.id
    }).then(res => {
      pageLoding = false
      if (num == 1) {
        trainData.value.nextContent = res.data.pageVo
      } else {
        trainData.value.preContent = res.data.pageVo
      }
    })
  }
  //下一页

  const next = () => {
    if (page.value.current == page.value.pageAll) {
      message.error('已是最后一页！')
      return false
    }
    if(pageLoding){return }
    isPage.value = false
    page.value.current++
    activeCode.value = []
    trainData.value.preContent = deepClone(trainData.value.content)
    trainData.value.content = deepClone(trainData.value.nextContent)
    getPostTelegraphKeyPatTrainGetPage(1)
  }
  //下一页
  const next2 = () => {
    isPage.value = false
    activeIndex.value = 0
    activeCode.value = []
    compileCodePage.value.push(deepClone(compileCode.value))
    fisinshPage()
    if (next()) return

    compileCode.value = [[]]
  }
  //上一页
  const prev = () => {
    if (page.value.current == 1) {
      message.error('已是第一页！')
      return false
    }
    if(pageLoding){return }
    page.value.current--
    trainData.value.nextContent = deepClone(trainData.value.content)
    trainData.value.content = deepClone(trainData.value.preContent)
    getPostTelegraphKeyPatTrainGetPage(-1)
  }
  //结束
  const endTest = () => {
    loading.value = true
    const data = {
      id: route.query.id,
      validTime: trainData.value.duration,
      totalSpeed: parseInt(trainData.value.speed)
    }
    if (code.value[page.value.current - 1]) {
      if (page.value.current == page.value.pageAll) {
        clearInterval(cutDown)
        if (autoTime.value) {
          clearInterval(autoTime.value)
        }
      } else {
        trainData.value.pageTime = 0
      }
    }
    fisinshPage(()=>{
      endTelexPatTrain(data).then(res => {
        loading.value = false
        if (res.code == 200) {
          clearInterval(autoTime.value)
          trainData.value.status = 3
          PubSub.unsubscribe('callback_closeTelexTrainPage', true)
          router.push({
            path: route.matched[4].path + '/trainScore',
            query: {
              id: route.query.id
            }
          })
        } else {
          message.error('提交失败')
        }
      })
    })
  }
  // 提交每页数据
  const fisinshPage = (callback)=>{
    //计算码率和用时
    const time = JSON.parse(JSON.stringify(trainData.value.pageTime))
    let codelength = 0
    let str = ""
    if(code.value.length > 0){
      codelength = code.value[page.value.current - 1].length
      code.value[page.value.current - 1].forEach(item=>{
        str+=item.text
      })
    }
    const speed = (codelength / (time / 60) / 4).toFixed(1) * 1
    str = str.replaceAll('Enter','\n')
    if (page.value.current == page.value.pageAll) {
      clearInterval(cutDown)
      if (autoTime.value) {
        clearInterval(autoTime.value)
      }
    } else {
      trainData.value.pageTime = 0
    }
    apiPostTelexPatTrainFinishPage({
      pageNumber: page.value.current,
      // patValue: JSON.stringify(code.value[page.value.current - 1]),
      patValue: str,
      // patValue:JSON.stringify(arr),
      trainId: route.query.id,
      speed: speed,
      validTime: time
    }).then(res=>{
      if(res.code===200&&callback){
        callback()
      }
    })
  }
  onUnmounted(() => {
    PubSub.unsubscribe('send_telexTrainPage')
  })
  onBeforeUnmount(() => {
    window.removeEventListener('keydown', keyDownStart)
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
