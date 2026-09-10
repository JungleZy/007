import {onBeforeUnmount, onMounted, onUnmounted, ref, watch, nextTick, createVNode} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import {message, Modal} from 'ant-design-vue'
import {ExclamationCircleOutlined} from '@ant-design/icons-vue'
import useControl from './useControl.js'
import {codeInKey, codeOnKey} from './keyCode.js'
import {PubSub} from '../../../../../../common/utils/PubSub'
import {sum, deepClone} from '../../../../../../common/utils/Utils.js'
import {
  getPostExamTrainDetails, startPostExamTrainInfo, endPostExamTrainInfo, apiPostTelegraphKeyPatTrainGetPage,
  apiPostTelegraphKeyPatTrainFinishPage
} from '../../../../../../common/api/TelegramApi.js'

export default function () {
  const patKeyBoxRef = ref(null)
  const patValBoxRef = ref(null)
  const loading = ref(true)
  const countDown = ref(null)
  const router = useRouter()
  const route = useRoute()
  const autoTime = ref(null)
  const {wsOnline, devOnline, patKey, changeCriterion,voiceCode} = useControl()
  const trainData = ref({
    id: '',
    errorNumber: 0,
    successNumber: 0,
    accuracy: '',
    speed: 0,
    totalNumber: 0,
    nextContent: [],
    content: [],
    preContent: [],
    duration: 0,
    status: 0
  })
  const allPage = ref(1)
  const currPage = ref(1) //当前分页
  const currPageIndex = ref(-1) //拍发选中位置
  const patCodeLogs = ref([]) //刻度数组
  const patKeysLogs = ref([]) //拍发总数组
  const logsPatKeyTime = ref(0)
  const progress = ref(0) //控制开始键位
  const patNumber = ref(0) //按下键总数
  const scorePath = ref('');
  const isModify = ref(false);
  const isPatF1 = ref(true);
  const isPatF2 = ref(false);
  const isPatF3 = ref(false);
  const playSpeed = ref(80)
  const pauseDuration = ref(800)
  const lastPatKey = ref([])
  let startTime = 0
  router.getRoutes().forEach(r => {
    if (r.name === 'PatExamTrainScore') {
      scorePath.value = r.path;
    }
  });

  //空格开始
  const keyDownStart = (key) => {
    if (trainData.value.status === 0 && key.keyCode == 32) {
      startTrain();
    }
  }
  PubSub.subscribe('send_examTrainPage', e => {
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
          endTrain()
        }
      })
    } else {
      PubSub.publish('callback_closeExamTrainPage', true)
    }
  })

  onMounted(() => {
    patKeyBoxRef.value.addEventListener('mousewheel', e => {
      if (e.deltaY > 0) {
        patKeyBoxRef.value.scrollLeft += 100
      } else {
        patKeyBoxRef.value.scrollLeft -= 100
      }
    })

    patValBoxRef.value.addEventListener('mousewheel', e => {
      if (e.deltaY > 0) {
        patValBoxRef.value.scrollLeft += 100
      } else {
        patValBoxRef.value.scrollLeft -= 100
      }
    })

    window.addEventListener('keydown', keyDownStart)
    if (route.query.id && route.query.id !== '') {
      getPostExamTrainDetails({id: route.query.id}).then(res => {
        loading.value = false
        if (res.code === 200) {
          for (let key in res.data) {
            trainData.value[key] = res.data[key]
          }

          trainData.value.speed = Number(trainData.value.speed)
          res.data.content.forEach(item => {
            item.key = JSON.parse(item.key)
            item.time = JSON.parse(item.time)
            item.value = []
          })
          trainData.value.ruleContent = JSON.parse(res.data.ruleContent)
          if (res.data.isCable === 0) {
            trainData.value.content = res.data.content.slice(0, 100)
            trainData.value.nextContent = res.data.content.slice(100, 200)
            allPage.value = Math.ceil(trainData.value.totalNumber / 100)
          } else {
            trainData.value.content = res.data.content
            trainData.value.nextContent = res.data.content
            allPage.value = Math.ceil(trainData.value.totalNumber / 100)
          }
          if (res.data.status === 1) {
            trainTime()
          }
        }
      })
    }
    changePlaySpeed()
  })

  onUnmounted(() => {
    PubSub.unsubscribe('send_examTrainPage')
    window.removeEventListener("keydown", keyDownStart)
    clearInterval(autoTime.value)
    window.removeEventListener('keydown', keyDownStart);
    patKeyBoxRef.value?.removeEventListener('mousewheel');
    patValBoxRef.value?.removeEventListener('mousewheel');
  })

  watch(patKey, () => {
    if (patKey.value && trainData.value.status === 1) {
      handlePatKeyCodeLogs()
      //改变电子键播报码率
      // changeCriterion(trainData.value.speed)
    }
  })
  //改变电子键播报码率
  const changePlaySpeed = ()=>{
    changeCriterion(playSpeed.value)
  }
  /**
   * 训练用时
   */
  const trainTime = () => {
    autoTime.value = setInterval(() => {
      trainData.value.duration++
      countDown.value.autoSetTimeAdd(trainData.value.duration)
      if(startTime!==0){
        trainData.value.speed = Number(parseFloat(patNumber.value / ((Date.now()-startTime)/1000/ 60)).toFixed(1))
      }
    }, 1000)
  }

  /**
   * 切换分页
   * @param num
   */
  let pageloding = false
  const switchPage = num => {
    if(pageloding){return }
    console.log(num)
    if ((currPage.value <= 1 && num < 0) || (num > 0 && currPage.value >= allPage.value)) return false
    currPage.value += num
    if (num === 1) {
      trainData.value.preContent = deepClone(trainData.value.content)
      trainData.value.content = deepClone(trainData.value.nextContent)
    } else {
      trainData.value.nextContent = deepClone(trainData.value.content)
      trainData.value.content = deepClone(trainData.value.preContent)
    }
    getPostTelegraphKeyPatTrainGetPage(num)
  }

  /**
   * 获取指定页的报底
   * @param num
   */
  const getPostTelegraphKeyPatTrainGetPage = (num) => {
    let page = currPage.value + num
    if (page > allPage.value || page < 1) return
    pageloding = true
    apiPostTelegraphKeyPatTrainGetPage({
      pageNumber: page,
      trainId: trainData.value.id
    }).then(res => {
      pageloding = false
      res.data.messageVO.forEach(item => {
        item.key = JSON.parse(item.key)
        item.time = JSON.parse(item.time)
        item.value = []
      })
      if (num == 1) {
        trainData.value.nextContent = res.data.messageVO
      } else if (num == 0) {
        trainData.value.content = res.data.messageVO
      } else {
        trainData.value.preContent = res.data.messageVO
      }
    })
  }

  /**
   * 处理电子键拍发记录
   */
  let cacheCode = [], timer = null;
  let codes = ""
  const handlePatKeyCodeLogs = () => {
    let k_v = '#', time = 0, lastRow,   lastKey,
        curr_t = new Date().getTime();
    if((Number(patKey.value) == 14||Number(patKey.value) == 41)&&cacheCode.length>0){
      time = curr_t - logsPatKeyTime.value;
      k_v = codeOnKey[cacheCode.join('')] ?? '#'
      console.log(k_v);
      cacheCode = []
      lastRow = patKeysLogs.value[patKeysLogs.value.length - 1];
      lastPatKey.value = patKeysLogs.value[patKeysLogs.value.length - 1];
      if (lastRow.length === 0) {
        lastRow.push([]);
      }
      lastKey = lastRow[lastRow.length - 1];
      patKeyAssignmentInfo(lastKey, k_v, time,true)
      clearTimeout(timer)
      timer = null
    }
    if (Number(patKey.value) == 12) {
      isPatF1.value = true;
      isPatF2.value = false;
      isPatF3.value = false;
      return false;
    }
    if (Number(patKey.value) == 13) {
      isPatF1.value = false;
      isPatF2.value = true;
      isPatF3.value = false;
      return false;
    }
    if (Number(patKey.value) == 14) {
      isPatF1.value = false;
      isPatF2.value = true;
      isPatF3.value = true;
      return false;
    }

    if (Number(patKey.value) === 44 && progress.value === 0 && (isPatF1.value||isPatF3.value)) {
      if(startTime===0){
        startTime = Date.now()
      }
      logsPatKeyTime.value = curr_t;
      patKeysLogs.value.push([['开始']]);
      patKeysLogs.value.push([]);
      patCodeLogs.value.push({key: '开始', time: 0});
      progress.value = 1;
      currPageIndex.value = 0;
      goScrollBottom()
      isPatF3.value = false
      return false;
    }
    if (progress.value === 0) {
      message.error('请拍发开始键!');
      return false;
    }

    if (patKeysLogs.value.length === 0) {
      patKeysLogs.value.push([]);
    }
    lastRow = patKeysLogs.value[patKeysLogs.value.length - 1];
    if (lastRow.length === 0) {
      lastRow.push([]);
    }
    if(!lastKey){
      lastKey = lastRow[lastRow.length - 1];
    }
    time = curr_t - logsPatKeyTime.value;


    if (Number(patKey.value) === 41) {
      if (isPatF3.value) {
        endTrain();
        return false;
      }
      // currPageIndex.value++
      // isModify.value = false;
      // lastRow.push([]);
      const lastcode = lastRow[lastRow.length-1]
      if(lastcode[lastcode.length-1]!=='?'&&lastcode[lastcode.length-1]!=='/'){
        if(isModify.value){
          currPageIndex.value++
        }
        lastRow.push([]);
        currPageIndex.value++
        isModify.value = false;
      }
      goScrollBottom()
      codes+=" "
      return false;
    }
    if (timer!==null) {
      clearTimeout(timer)
      timer = null
    }
    if (Number(patKey.value) === 45 && (isPatF1.value||isPatF3.value)) {
      logsPatKeyTime.value = curr_t;
      patKeysLogs.value.push([['句号']])
      patKeysLogs.value.push([])
      patCodeLogs.value.push({key: '句号', time: time})
      if (currPage.value == allPage.value) {
        currPageIndex.value = 100;
        endTrain();
      } else {
        currPageIndex.value = 0;
        progress.value = 0;
        handlerSubmit()
        switchPage(1)
      }
      goScrollBottom()
      return false
    }
    if (Number(patKey.value) == 14) {
      isPatF3.value = true;
      return false;
    }

    if (Number(patKey.value) > 20 && Number(patKey.value) <= 45 && Number(patKey.value) != 41) {
      logsPatKeyTime.value = curr_t;
      if (isPatF1.value || isPatF3.value) {
        k_v = codeInKey[patKey.value].text;
        cacheCode = []
      } else if (isPatF2.value) {
        if (Number(patKey.value) < 30) {
          if (cacheCode.length > 0) {
            cacheCode.push(...codeInKey[patKey.value]._code)
            k_v = codeOnKey[cacheCode.join('')] ?? '#'
            cacheCode = []
          } else {
            cacheCode = []
            k_v = '#'
          }
        } else {
          if (cacheCode.length > 0) {
            k_v = codeOnKey[cacheCode.join('')] ?? '#'
            cacheCode = []
            cacheCode.push(...codeInKey[patKey.value]._code)
          } else {
            cacheCode = []
            cacheCode.push(...codeInKey[patKey.value]._code)
            k_v = ''
          }
          patKey.value = null;
          timer = setTimeout(() => {
            k_v = codeOnKey[cacheCode.join('')] ?? '#'
            cacheCode = []
            patKeyAssignmentInfo(lastKey, k_v, time)
            clearTimeout(timer)
            timer = null
            console.log(3333333333)
          }, pauseDuration.value)
        }
      }
    }
    if (Number(patKey.value) === 42 && isPatF1.value) {
      if (lastKey.length == 0) {
        currPageIndex.value--;
      }
    }
    if (Number(patKey.value) === 43 && isPatF1.value) {
      console.log(lastKey.length)
      if (isModify.value || currPageIndex.value == 0) {
        return false;
      } else {
        if (lastKey.length == 0) {
          currPageIndex.value -= 2;
        } else {
          currPageIndex.value--;
        }
        lastKey = lastRow[lastRow.length - 1];
        isModify.value = true;
        // trainData.value.content[currPageIndex.value + 1].value.push('/')
      }
    }

    if (isPatF1.value || isPatF3.value || k_v != ''||k_v=='#') {
      patKeyAssignmentInfo(lastKey, k_v, time)
    }

//计算码率
    if(startTime!==0){
      trainData.value.speed = Number(parseFloat(patNumber.value / ((Date.now()-startTime)/1000/ 60)).toFixed(1))
    }
  }

  /**
   * 拍发字码赋值到数据中
   * @param lastKey
   * @param k_v
   * @param time
   */
  const patKeyAssignmentInfo = (lastKey, k_v, time,type=false) => {
    patNumber.value++;
    lastKey.push(k_v);
    patCodeLogs.value.push({key: k_v, time: (time > 20000 ? 20000 : time)});
    if(patCodeLogs.value.length>100){
      patCodeLogs.value = patCodeLogs.value.slice(patCodeLogs.value.length-100,patCodeLogs.value.length)
    }
    currPageIndex.value = currPageIndex.value < 0 ? 0 : currPageIndex.value

    if (!trainData.value.content) {
      trainData.value.content = [{key: ['#'], value: [], time: []}]
    }
    if (!trainData.value.content[currPageIndex.value]) {
      trainData.value.content[currPageIndex.value] = {
        id: null,
        key: ['#'],
        pageNumber: trainData.value.content[0].pageNumber,
        sort: currPageIndex.value,
        trainId: trainData.value.content[0].trainId,
        value: [],
        time: []
      }
    }

    trainData.value.content[currPageIndex.value].value.push(k_v == '/' ? '?' : k_v);
    trainData.value.content[currPageIndex.value].time.push(time);

    goScrollBottom()
    if(!type){
      patKey.value = null;
      isPatF3.value = false;
    }
    addCode()
  }
  const addCode = ()=>{
    const code = patCodeLogs.value[patCodeLogs.value.length-1]
    let sendcode = code.key
    switch (code.key){
      case '开始':
        sendcode = "# "
        break;
      case '结束':
        sendcode = " !"
        break
      case '句号':
        sendcode = " 。"
        break
      case '/':
        sendcode = "-"
        break
    }
    codes+=sendcode
    // console.log(22222222)
    if(isPatF1.value){
      voiceCode({numType:'short',code:codes})
    }else {
      voiceCode({numType:'long',code:codes})
    }
    codes = ''
  }
  const goScrollBottom = ()=>{
    nextTick(() => {
      patValBoxRef.value.scrollLeft = patValBoxRef.value.scrollWidth;
      patKeyBoxRef.value.scrollTop = patKeyBoxRef.value.scrollHeight;
    });
  }

  /**
   * 提交当前页面拍发的数据
   */
  const handlerSubmit = () => {
    const data = deepClone(trainData.value.content)
    const submitData = data.filter(item=>item.value.length>0)
    submitData.forEach(item => {
      item.value = JSON.stringify(item.value)
      item.key = JSON.stringify(item.key)
      item.time = JSON.stringify(item.time)
    })
    apiPostTelegraphKeyPatTrainFinishPage(submitData, trainData.value.content[0].pageNumber, trainData.value.content[0].trainId).then(res => {
      if (res.code === 200 && currPage.value > allPage.value) {
        setTimeout(() => {
          endTrain('end')
        }, 1000)
      }
    })
  }

  /**
   * 开始训练
   */
  const startTrain = () => {
    if (!wsOnline.value) {
      message.error('报训软件未连接!')
      return false
    }
    if (!devOnline.value) {
      message.error('电子键设备未连接！')
      return false
    }
    currPage.value = 1
    getPostTelegraphKeyPatTrainGetPage(0)
    setTimeout(() => {
      getPostTelegraphKeyPatTrainGetPage(1)
    }, 300)
    startPostExamTrainInfo({
      id: trainData.value.id
    }).then(res => {
      if (res.code === 200) {
        trainData.value.status = 1
        trainTime()
      }
    })
  }

  /**
   * 结束训练
   */
  const endTrain = type => {
    if (type != 'end') {
      handlerSubmit()
    }
    setTimeout(() => {
      loading.value = true
      endPostExamTrainInfo({
        id: trainData.value.id,
        content: ''
      }).then(res => {
        loading.value = false
        if (res.code === 200) {
          trainData.value.status = 2
          clearInterval(autoTime.value)
          PubSub.unsubscribe('callback_closeExamTrainPage', true)
          router.push({
            path: scorePath.value,
            query: {id: trainData.value.id}
          })
        }
      })
    }, 1000)
  }

  return {
    loading,
    patValBoxRef,
    patKeyBoxRef,
    wsOnline,
    devOnline,
    trainData,
    countDown,
    currPage,
    allPage,
    patCodeLogs,
    patKeysLogs,
    pauseDuration,
    currPageIndex,
    switchPage,
    startTrain,
    endTrain,
    changePlaySpeed,
    playSpeed,
    lastPatKey
  }
}
