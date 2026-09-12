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
import useConfirmedSubmission from '../../../../../../common/mixin/useConfirmedSubmission'
import {audioOperation} from '../../../../../../common/utils/MorseVoice'
import useTrainingCapture from '../../../../../../common/mixin/useTrainingCapture'

export default function () {
  const submission = useConfirmedSubmission()
  const capture = useTrainingCapture()
  const snapshotKey = () => `personal-electron:pending:${trainData.value.id}`
  const patKeyBoxRef = ref(null)
  const patValBoxRef = ref(null)
  const loading = ref(true)
  const countDown = ref(null)
  const router = useRouter()
  const route = useRoute()
  const autoTime = ref(null)
  const {wsOnline, devOnline, onKey, changeCriterion,voiceCode} = useControl()
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
  const patNumber = ref(0) // 本次采集的正文字符数，不含控制符
  const scorePath = ref('');
  const isModify = ref(false);
  const isPatF1 = ref(true);
  const isPatF2 = ref(false);
  const isPatF3 = ref(false);
  const playSpeed = ref(null)
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
          playSpeed.value = Number(trainData.value.ruleContent?.wpm?.base)
          changePlaySpeed()
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
            capture.bind(res.data)
            getPostTelegraphKeyPatTrainGetPage(0)
            trainTime()
          }
        }
      })
    }
  })

  onUnmounted(() => {
    PubSub.unsubscribe('send_examTrainPage')
    window.removeEventListener("keydown", keyDownStart)
    clearInterval(autoTime.value)
    clearTimeout(timer)
    cacheCode = []
    capture.close()
    window.removeEventListener('keydown', keyDownStart);
    patKeyBoxRef.value?.removeEventListener('mousewheel');
    patValBoxRef.value?.removeEventListener('mousewheel');
  })

  onKey(({code, receivedAt}) => {
    if (code && trainData.value.status === 1) {
      handlePatKeyCodeLogs(code, receivedAt)
      if (Number(trainData.value.speed) > 0) {
        playSpeed.value = Number(trainData.value.speed)
        changePlaySpeed()
      }
    }
  })
  //改变电子键播报码率
  const changePlaySpeed = ()=>{
    changeCriterion(playSpeed.value, trainData.value.messageType == 1 ? 'letter' : trainData.value.messageType == 2 ? 'mix' : 'short')
  }
  /**
   * 训练用时
   */
  const trainTime = () => {
    clearInterval(autoTime.value)
    autoTime.value = setInterval(() => {
      trainData.value.duration = capture.elapsed() / 1000
      countDown.value.autoSetTimeAdd(trainData.value.duration)
      if(startTime!==0){
        if (capture.elapsed() > 0) trainData.value.speed = Number((patNumber.value * 60000 / 4 / capture.elapsed()).toFixed(1))
      }
    }, 1000)
  }

  /**
   * 切换分页
   * @param num
   */
  let pageloding = false
  const switchPage = num => {
    if ([1, 3].includes(trainData.value.status)) return
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
  const decodePage = rows => rows.map(item => ({...item,
    key: typeof item.key === 'string' ? JSON.parse(item.key) : item.key,
    time: typeof item.time === 'string' ? JSON.parse(item.time) : item.time || [],
    value: typeof item.value === 'string' ? JSON.parse(item.value) : item.value || []}))
  const getPostTelegraphKeyPatTrainGetPage = num => {
    const saved = num === 0 ? submission.loadSnapshot(snapshotKey()) : null
    const page = saved?.payload.pageNumber ?? currPage.value + num
    if (page > allPage.value || page < 1) return
    capture.close()
    return submission.run(async request => {
      const response = await request(config => apiPostTelegraphKeyPatTrainGetPage({pageNumber: page, trainId: trainData.value.id}, config))
      const content = decodePage(saved?.payload.attempt === response.data.attempt ? saved.payload.value : response.data.messageVO)
      if (num === 0) {
        currPage.value = page
        trainData.value.content = content
        capture.bind(response.data)
        if (trainData.value.status === 1 && !saved) capture.open()
      } else if (num === 1) trainData.value.nextContent = content
      else trainData.value.preContent = content
      if (saved) submission.defer(() => handlerSubmit(saved.end), true)
    })
  }

  /**
   * 处理电子键拍发记录
   */
  let cacheCode = [], timer = null;
  let lastCharacterAt = null
  let codes = ""
  const handlePatKeyCodeLogs = (code, receivedAt, receipt = null) => {
    if (trainData.value.status !== 1) return
    if (!receipt) receipt = Object.freeze({...capture.stamp(receivedAt, receivedAt), queued: submission.pending()})
    if (submission.defer(() => handlePatKeyCodeLogs(code, receivedAt, receipt))) return
    if (receipt.queued) { capture.recordQueued(receipt); capture.open() }
    else capture.open(receivedAt)
    let k_v = '#', time = 0, lastRow, lastKey, curr_t = receipt.endedMs;
    if((Number(code) == 14||Number(code) == 41)&&cacheCode.length>0){
      time = capture.between(logsPatKeyTime.value, curr_t);
      k_v = codeOnKey[cacheCode.join('')] ?? '#'
      console.log(k_v);
      cacheCode = []
      lastRow = patKeysLogs.value[patKeysLogs.value.length - 1];
      lastPatKey.value = patKeysLogs.value[patKeysLogs.value.length - 1];
      if (lastRow.length === 0) {
        lastRow.push([]);
      }
      lastKey = lastRow[lastRow.length - 1];
      patKeyAssignmentInfo(lastKey, k_v, curr_t,true)
      clearTimeout(timer)
      timer = null
    }
    if (Number(code) == 12) {
      isPatF1.value = true;
      isPatF2.value = false;
      isPatF3.value = false;
      return false;
    }
    if (Number(code) == 13) {
      isPatF1.value = false;
      isPatF2.value = true;
      isPatF3.value = false;
      return false;
    }
    if (Number(code) == 14) {
      isPatF1.value = false;
      isPatF2.value = true;
      isPatF3.value = true;
      return false;
    }

    if (Number(code) === 44 && progress.value === 0 && (isPatF1.value||isPatF3.value)) {
      if(startTime===0){
        startTime = Date.now()
      }
      logsPatKeyTime.value = curr_t;
      lastCharacterAt = curr_t
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
    time = capture.between(logsPatKeyTime.value, curr_t);


    if (Number(code) === 41) {
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
    if (Number(code) === 45 && (isPatF1.value||isPatF3.value)) {
      logsPatKeyTime.value = curr_t;
      patKeysLogs.value.push([['句号']])
      patKeysLogs.value.push([])
      patCodeLogs.value.push({key: '句号', time: time})
      if (currPage.value == allPage.value) {
        currPageIndex.value = 100;
        endTrain();
      } else {
        handlerSubmit()
      }
      goScrollBottom()
      return false
    }
    if (Number(code) == 14) {
      isPatF3.value = true;
      return false;
    }

    if (Number(code) > 20 && Number(code) <= 45 && Number(code) != 41) {
      logsPatKeyTime.value = curr_t;
      if (isPatF1.value || isPatF3.value) {
        k_v = codeInKey[code].text;
        cacheCode = []
      } else if (isPatF2.value) {
        if (Number(code) < 30) {
          if (cacheCode.length > 0) {
            cacheCode.push(...codeInKey[code]._code)
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
            cacheCode.push(...codeInKey[code]._code)
          } else {
            cacheCode = []
            cacheCode.push(...codeInKey[code]._code)
            k_v = ''
          }
          timer = setTimeout(() => {
            k_v = codeOnKey[cacheCode.join('')] ?? '#'
            cacheCode = []
            patKeyAssignmentInfo(lastKey, k_v, curr_t)
            clearTimeout(timer)
            timer = null
            console.log(3333333333)
          }, pauseDuration.value)
        }
      }
    }
    if (Number(code) === 42 && isPatF1.value) {
      if (lastKey.length == 0) {
        currPageIndex.value--;
      }
    }
    if (Number(code) === 43 && isPatF1.value) {
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
      patKeyAssignmentInfo(lastKey, k_v, curr_t)
    }

//计算码率
  }

  /**
   * 拍发字码赋值到数据中
   * @param lastKey
   * @param k_v
   * @param time
   */
  const patKeyAssignmentInfo = (lastKey, k_v, at,type=false) => {
    const time = capture.between(lastCharacterAt ?? at, at)
    lastCharacterAt = Math.max(lastCharacterAt ?? at, at)
    if (k_v !== '开始' && k_v !== '句号' && k_v !== '结束' && k_v !== '?' && k_v !== '/') {
      for (const character of k_v) {
        if (!/[\s\p{Cc}]/u.test(character)) patNumber.value++
      }
    }
    if (capture.elapsed() > 0) {
      trainData.value.speed = Number((patNumber.value * 60000 / 4 / capture.elapsed()).toFixed(1))
      playSpeed.value = trainData.value.speed
      changePlaySpeed()
    }
    lastKey.push(k_v);
    patCodeLogs.value.push({key: k_v, time});
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
  const handlerSubmit = (end = false) => {
    clearTimeout(timer)
    if (cacheCode.length) {
      const row = patKeysLogs.value.at(-1)
      const key = row?.at(-1)
      if (key) patKeyAssignmentInfo(key, codeOnKey[cacheCode.join('')] ?? '#', logsPatKeyTime.value)
      cacheCode = []
    }
    const saved = submission.loadSnapshot(snapshotKey())
    const pageNumber = saved?.payload.pageNumber ?? currPage.value
    let payload = saved?.payload
    if (saved) end = saved.end
    if (!payload) {
      const value = deepClone(trainData.value.content).filter(item => item.value.length > 0)
      for (const item of value) {
        item.value = JSON.stringify(item.value)
        item.key = JSON.stringify(item.key)
        item.time = JSON.stringify(item.time)
      }
      payload = {id: trainData.value.id, pageNumber, protocolVersion: 1, value, ...capture.snapshot()}
    }
    submission.saveSnapshot(snapshotKey(), {end, payload})
    let uploaded = false
    return submission.run(async request => {
      if (payload.attempt !== capture.metadata.value?.attempt) throw new Error('训练轮次已变化，旧记录不会提交到新轮次')
      if (!uploaded) {
        await request(config => apiPostTelegraphKeyPatTrainFinishPage(payload, config))
        uploaded = true
      }
      if (end || pageNumber >= allPage.value) {
        await request(config => endPostExamTrainInfo({id: trainData.value.id, attempt: payload.attempt, protocolVersion: 1}, config))
        trainData.value.status = 2
        clearInterval(autoTime.value)
        router.push({path: scorePath.value, query: {id: trainData.value.id}})
      } else {
        const response = await request(config => apiPostTelegraphKeyPatTrainGetPage({pageNumber: pageNumber + 1, trainId: trainData.value.id}, config))
        capture.bind(response.data)
        capture.open()
        const next = response.data.messageVO.map(item => ({...item, key: JSON.parse(item.key), time: JSON.parse(item.time), value: []}))
        trainData.value.preContent = trainData.value.content
        trainData.value.content = next
        currPage.value = pageNumber + 1
        currPageIndex.value = 0
        progress.value = 0
        cacheCode = []
      }
      submission.clearSnapshot(snapshotKey())
    })
  }

  /**
   * 开始训练
   */
  const startTrain = async () => {
    if (!wsOnline.value) { message.error('报训软件未连接!'); return false }
    if (!devOnline.value) { message.error('电子键设备未连接！'); return false }
    if (!await audioOperation({type: 'init'})) return false
    return submission.run(async request => {
      const response = await request(config => apiPostTelegraphKeyPatTrainGetPage({pageNumber: 1, trainId: trainData.value.id}, config))
      const begun = await request(config => startPostExamTrainInfo({id: trainData.value.id, attempt: response.data.attempt, protocolVersion: 1}, config))
      capture.bind({...response.data, serverElapsedMs: begun.data.serverElapsedMs, attempt: begun.data.attempt, protocolVersion: begun.data.protocolVersion})
      capture.open()
      trainData.value.content = response.data.messageVO.map(item => ({...item, key: JSON.parse(item.key), time: JSON.parse(item.time), value: []}))
      currPage.value = 1
      trainData.value.status = 1
      trainTime()
    })
  }

  /**
   * 结束训练
   */
  const endTrain = () => handlerSubmit(true)

  return {
    submissionError: submission.error, submissionBusy: submission.busy, retrySubmit: submission.retry,
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
