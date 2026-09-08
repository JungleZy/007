import {onBeforeUnmount, onMounted, onUnmounted, ref, watch, nextTick, createVNode} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import {
  getTelexTrainByID,
  postTelexPatTrain,
  endTelexPatTrain,
  apiPostTelexPatTrainGetPage,
  apiPostTelexPatTrainFinishPage
} from '../../../../../../common/api/TelegramApi.js'
import {message, Modal} from 'ant-design-vue'
import {PubSub} from '../../../../../../common/utils/PubSub'
import {deepClone} from '../../../../../../common/utils/Utils.js'
import {ExclamationCircleOutlined} from '@ant-design/icons-vue'

export default function (countDown, loading) {
  const router = useRouter()
  const route = useRoute()
  let startNum = 0
  const isCountdown = ref(false)
  const duration = ref(120)
  let pageTime = 0
  const isFocus = ref(false)//倒计时输入框是否获取焦点
  const keyDownStart = (key) => {
    if(isFocus.value)return
    if (trainData.value.status === 0&&startNum===0) {
      startNum++
      startTest()
    }
    keyCodeup(key)
  }
  onMounted(() => {
    nextTick(() => {
      document.getElementsByTagName('textarea')[0].focus()
    })
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
  const code = ref([])
  const activeIndex = ref(0)
  const pageCodes = ref([])
  let isPage = false //是否已经输入分页
  const trainData = ref({
    id: '',
    errorNumber: 0,
    successNumber: 0,
    accuracy: '',
    speed: 0,
    validTime: 0,
    groupNumber: 0,
    preContent: [],
    content: [],
    nextContent: [],
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
      if(isCountdown.value){
        let coun = duration.value*60 - trainData.value.duration
        if(coun===0){
          endTest()
        }
        countDown.value.autoSetTimeAdd(coun)
      }else {
        countDown.value.autoSetTimeAdd(trainData.value.duration)
      }

      let codelength = 0
      pageCodes.value.forEach(item => {
        const str = item.replaceAll(" ",'')
        codelength += str.length
      })
      trainData.value.speed = (codelength / (trainData.value.duration / 60)/4).toFixed(1) * 1
    }, 1000)
  }
  const startTest = () => {
    postTelexPatTrain({id: route.query.id}).then(res => {
      if (res.code == 200) {
        if (page.value.current !== 1) {
          page.value.current = 1
          init()
          // page.value.current = 2
          // prev()
        }
        trainData.value.status = res.data.status
        trainTime()
        nextTick(() => {
          document.getElementsByTagName('textarea')[0].focus()
        })
      }
    })
  }
  const init = () => {
    getTelexTrainByID({id: route.query.id}).then(res => {
      loading.value = false
      if (res.data.status == 1) trainTime()
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
      trainData.value.errorNumber = res.data.errorNumber
      trainData.value.accuracy = parseInt(res.data.accuracy)
      trainData.value.speed = Number(res.data.speed)
      trainData.value.validTime = res.data.validTime
      trainData.value.groupNumber = res.data.groupNumber
      trainData.value.status = res.data.status

    })
  }
  let enterTimer = 0
  const keyCodeup = v => {
    const str = pageCodes.value[page.value.current - 1]
    const arr = str ? str.split('\n') : false
    const lastCode = arr ? arr[arr.length - 1].split(' ') : false
    if (v.keyCode == 8 && (str[str.length - 1] == ' ' || str[str.length - 1] == '\n') && lastCode && lastCode.length < 11) {
      activeIndex.value--
    }
    if (v.keyCode == 13) {
      enterTimer++
      if (activeIndex.value % 10 == 0) {
        activeIndex.value += 10
      } else {
        activeIndex.value = Math.ceil(activeIndex.value / 10) * 10
        // apiPostTelexPatTrainFinishPage()
      }
    } else {
      enterTimer = 0
    }
    if (v.code == "F4") {
      prev()
    }
    if (v.code == "F5") {
      if (v.preventDefault) {
        v.preventDefault()
      } else {
        window.event.returnValue == false
      }
      next()
    }
    if (v.keyCode == 32 && activeIndex.value % 10 != 9) {
      activeIndex.value++
    }
  }
  const textareaChange = () => {
    if (enterTimer == 2) {
      next()
      activeIndex.value = 0
      enterTimer = 0
    }

    if (pageCodes.value[page.value.current - 1]) {
      pageCodes.value[page.value.current - 1] = pageCodes.value[page.value.current - 1].toUpperCase()
    }
  }

  //下一页
  let pageLoding = false
  const next = () => {
    if(pageLoding){return }
    finishPage()
    if (page.value.current == page.value.pageAll) {
      message.error('已是最后一页！')
      return false
    }
    isPage = false
    page.value.current++
    trainData.value.preContent = deepClone(trainData.value.content)
    trainData.value.content = deepClone(trainData.value.nextContent)
    getPostTelegraphKeyPatTrainGetPage(1)
  }
  //上一页
  const prev = () => {
    if(pageLoding){return }
    finishPage()
    if (page.value.current == 1) {
      message.error('已是第一页！')
      return false
    }
    page.value.current--
    trainData.value.nextContent = deepClone(trainData.value.content)
    trainData.value.content = deepClone(trainData.value.preContent)
    getPostTelegraphKeyPatTrainGetPage(-1)
  }
  // //提交当前页面拍发的数据
  // const PostTelexPatTrainFinishPage = ()=>{
  //   apiPostTelexPatTrainFinishPage({
  //     pageNumber:page.value.current-1,
  //     patValue:JSON.stringify(pageCodes.value[page.value.current-2]),
  //     trainId:route.query.id
  //   })
  // }

  const getPostTelegraphKeyPatTrainGetPage = num => {
    // PostTelexPatTrainFinishPage()//提交当前页面拍发的数据
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
  const endTest = () => {
    loading.value = true
    finishPage(endTrain)
  }
  const endTrain = ()=>{
    const data = {
      id: route.query.id,
      totalSpeed: parseInt(trainData.value.speed),
      validTime: trainData.value.duration
    }
    endTelexPatTrain(data).then(res => {
      loading.value = false
      clearInterval(autoTime.value)
      trainData.value.status = 3
      PubSub.unsubscribe('callback_closeTelexTrainPage', true)
      router.push({
        path: route.matched[4].path + '/datagramTrainScore',
        query: {
          id: route.query.id
        }
      })
    })
  }
  const finishPage = (callback)=>{
    pageTime = pageTime===0?trainData.value.duration:trainData.value.duration-pageTime
    const groups = pageCodes.value[page.value.current - 1]?pageCodes.value[page.value.current - 1].split(' '):''
    const speed = (groups.length / (pageTime / 60)).toFixed(1) * 1
    apiPostTelexPatTrainFinishPage({
      pageNumber: page.value.current,
      patValue: pageCodes.value[page.value.current - 1],
      trainId: route.query.id,
      speed:speed,
      validTime:pageTime
    }).then(res=>{
      if(callback){
        callback()
      }
    })
  }
  //倒计时获取失去焦点
  const getFocus = ()=>{
    isFocus.value = true
  }
  const lackFocus = ()=>{
    isFocus.value = false
    nextTick(() => {
      document.getElementsByTagName('textarea')[0].focus()
    })
  }
  const changeCountdown = ()=>{
    if(isCountdown.value){
      countDown.value.autoSetTimeAdd(duration.value*60)
    }else {
      countDown.value.autoSetTimeAdd(0)
    }
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
    pageCodes,
    activeIndex,
    page,
    isCountdown,
    duration,
    getFocus,
    lackFocus,
    changeCountdown,
    next,
    prev,
    startTest,
    endTest,
    textareaChange
  }
}
