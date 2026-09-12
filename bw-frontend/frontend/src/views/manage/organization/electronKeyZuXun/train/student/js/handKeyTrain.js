import { onMounted, onUnmounted, ref, watch, nextTick, inject } from 'vue'
import {sum,deepClone} from "../../../../../../../common/utils/Utils.js";
import useMorse from "../../../../../../../common/mixin/useMorse.js";
import {message, Modal} from "ant-design-vue";
import {codeInKey,codeOnKey} from './keyCode.js';
import {useRouter} from "vue-router";
import PublicSocket from '../../../../../../../common/ws/PublicSocket.js'
import {
  finishElectronKeyZuXun,
  getElectronKeyZuXunPageNumber,uploadElectronKeyZuXunPatResult,startTrainUser,resetElectronKeyZuXunTrain
} from "../../../../../../../common/api/electronKeyZuXun.js";
import useConfirmedSubmission from '../../../../../../../common/mixin/useConfirmedSubmission'
import {audioOperation} from '../../../../../../../common/utils/MorseVoice'
import useTrainingCapture from '../../../../../../../common/mixin/useTrainingCapture'

export default function (trainData,wsOnline,devOnline,loading,emits,voiceCode,changeCriterion) {
  const submission = useConfirmedSubmission()
  const capture = useTrainingCapture()
  let closingAt = null
  let resetting = false
  const snapshotKey = () => `electron:pending:${userInfo.id}:${trainData.value.trainId}`
  const patKeyBoxRef = ref(null); // 字码和词组展示区域的容器
  const patValBoxRef = ref(null); // 拍发电码展示区域的容器
  const trainTimeRef = ref(null); // 训练时间展示区域的容器
  const trainTimer = ref(null); // 记录训练时长的计时器
  const cachePatCode = ref([]); // 缓存电码 - 还未转换成字码的电码集合
  const cachePatLogs = ref([]); // 缓存拍发记录 - 还未转换成字码的电码集合
  const cacheKey = ref([]); // 缓存字码 - 还未转换成词组的字码集合
  const cacheKeyCode = ref([]); // 缓存电码 - 还未转换成词组的电码码集合
  const logsPatStandardCode = ref([]); // 待转换拍发基准值的数据集合
  const cachePatKey = ref([]); // 缓存字码 - 还未翻页提交的字码集合
  const currPatKeyIndex = ref(-1); // 正在拍发的电报纸字码的下标
  const {morseCode,codeKey,dots} = useMorse();
  const {ws_connect,sendMessage,closeWebSocket} = PublicSocket();
  const router = useRouter();
  const scorePath = ref('');
  const errorText = ref('');
  const patNumber = ref(0);
  const pauseDuration = ref(800);
  const initSymbol = ref({ // 拍发特殊符号的电码值
    machine: '0001,0001,0001', // 试机操作符号
    start: '10001', // 开始符号
    end: '01010', // 结束符号
    turn: '00,00,00', // 翻页符号
    alter: '001100', // 改错符号-当前组
    next: '0010,11', // 改错符号-前一组
  });
  const oldPatStandard = ref({
    dot: 0,
    line: 0,
    c_gap: 0,
    w_gap: 0,
    g_gap: 0
  });
  const pagePatStandard = ref([]);
  const pageHandleIndex = ref(0);
  const finishPatLogs = ref([]);
  const wordTimer = ref(null);
  const groupTimer = ref(null);
  const textTimer = ref(null);
  const firstStandard = ref(false);
  const alter = ref(0);
  let isFirstKey = true
  const userInfo = JSON.parse(localStorage.getItem('userInfo'))
  const readyPat = ref(false);
  const patUser = ref({});
  watch(() => [trainData.value.status, patUser.value.userStatus], ([status, userStatus], previous = []) => {
    if (status !== 1 || userStatus === 3) capture.close()
    else if (previous[1] === 3 && capture.metadata.value) capture.open()
  }, {flush: 'sync'})
  const patWsData = ref({topic: 'pat', id: userInfo.id, log: {key: '', time: 0}})
  const startStatus = ref(false);

  const logsPatKeyTime = ref(0);
  const isModify = ref(false);
  const isPatF1 = ref(true);
  const isPatF2 = ref(false);
  const isPatF3 = ref(false);

  router.getRoutes().forEach(r => {
    if (r.name === 'ElectronKeyZuXunTrain') {
      scorePath.value = r.path;
    }
  });

  onMounted(() => {
    patKeyBoxRef.value.addEventListener('mousewheel', e => {
      if (e.deltaY > 0) {
        patKeyBoxRef.value.scrollLeft += 100;
      } else {
        patKeyBoxRef.value.scrollLeft -= 100;
      }
    });

    patValBoxRef.value.addEventListener('mousewheel', e => {
      if (e.deltaY > 0) {
        patValBoxRef.value.scrollLeft += 100;
      } else {
        patValBoxRef.value.scrollLeft -= 100;
      }
    });
  });

  onUnmounted(() => {
    clearInterval(trainTimer.value);
    clearTimeout(timer)
    clearInterval(cutTimer.value)
    cacheCode = []
    capture.close()
    closeWebSocket();
  });

  /**
   * 获取电报纸的报文内容
   * @param page
   */
  const decodePage = rows => rows.map(item => ({...item,
    key: typeof item.key === 'string' ? JSON.parse(item.key) : item.key,
    value: typeof item.value === 'string' ? JSON.parse(item.value) : item.value || [],
    time: typeof item.time === 'string' ? JSON.parse(item.time) : item.time || []}))
  const getPostTrainKeyInfo = page => submission.run(async request => {
    const response = await request(config => getElectronKeyZuXunPageNumber({trainId: trainData.value.trainId, userId: userInfo.id, pageNumber: page}, config))
    trainData.value.telegraph[page - 1] = decodePage(response.data.messageVO)
    if (page === trainData.value.floorNow) capture.bind(response.data)
  })

  /**
   * 训练时间转换显示
   */
  const initTrainTimeInfo = () => {
    clearInterval(trainTimer.value)
    if (!trainData.value.validTime) {
      trainData.value.validTime = 0;
    }
    trainTimer.value = setInterval(() => {
      const elapsed = capture.elapsed()
      trainData.value.validTime = elapsed / 1000;
      trainData.value.speed = elapsed > 0 ? Number((patNumber.value * 60000 / 4 / elapsed).toFixed(1)) : 0;
      timeAreaShow(trainData.value.validTime * 1000);
    },1000);
  };

  /**
   * 时间区域显示
   */
  const timeAreaShow = (t) => {
    let h,m,s;
    h = Math.floor(t / 1000 / 60 / 60 % 24);
    m = Math.floor(t / 1000 / 60 % 60);
    s = Math.floor(t / 1000 % 60);
    h = h > 9 ? h + "" : '0' + h;
    m = m > 9 ? m + "" : '0' + m;
    s = s > 9 ? s + "" : '0' + s;
    trainTimeRef.value.h = h;
    trainTimeRef.value.m = m;
    trainTimeRef.value.s = s;
  };

  /**
   * 切换电报纸
   * @param type
   */
  const switchTelegram = (type) => {
    if ([1, 3].includes(trainData.value.status)) return
    if ((type === 'prev' && trainData.value.floorNow <= 1) ||
        (type === 'next' && trainData.value.floorNow >= trainData.value.pag)) return false;
    if (type === 'prev') {
      trainData.value.floorNow --;
    }
    if (type === 'next') {
      trainData.value.floorNow ++;
    }
    if (!trainData.value.telegraph[trainData.value.floorNow + 1] && trainData.value.floorNow < trainData.value.pag) {
      getPostTrainKeyInfo(trainData.value.floorNow + 1);
    }
  };

  /**
   * 重拍开始符
   */
  const resetPatStart = () => {
    trainData.value.process = 1;
    cachePatCode.value = [];
    logsPatStandardCode.value = [];
    finishPatLogs.value = [];
  }
  let startTime = 0
  /**
   * 处理接收的电报code
   * @param val
   */
  let cacheCode = [],timer = null;
  let lastCharacterAt = null
  const handleReceiveKeyCode = (val, receivedAt, receipt = null) => {
    if (resetting) return
    const beforeClose = closingAt !== null && receivedAt <= closingAt
    if ((closingAt !== null && !beforeClose) || (trainData.value.status !== 1 && !beforeClose) || patUser.value.userStatus === 3) return
    if (!receipt) receipt = Object.freeze({...capture.stamp(receivedAt, receivedAt), queued: submission.pending()})
    if (submission.defer(() => handleReceiveKeyCode(val, receivedAt, receipt))) return
    if (receipt.queued) {
      capture.recordQueued(receipt)
      if (closingAt === null) capture.open()
    } else if (closingAt === null) capture.open(receivedAt)
    let k_v = '#', time = 0, lastRow, lastKey, curr_t = receipt.endedMs;
    if (loading.value || patUser.value.userStatus == 3) return false;
    if((Number(val) == 14||Number(val) == 41)&&cacheCode.length>0){
      time = capture.between(logsPatKeyTime.value, curr_t);
      k_v = codeOnKey[cacheCode.join('')] ?? '#'
      console.log(k_v);
      cacheCode = []
      lastRow = trainData.value.patKeyVal[trainData.value.patKeyVal.length - 1];
      if (lastRow.length === 0) {
        lastRow.push([]);
      }
      lastKey = lastRow[lastRow.length - 1];
      patKeyAssignmentInfo(lastKey, k_v, curr_t)
      clearTimeout(timer)
      timer = null
    }
    if (Number(val) == 12) {
      isPatF1.value = true;
      isPatF2.value = false;
      isPatF3.value = false;
      return false;
    }
    if (Number(val) == 13) {
      isPatF1.value = false;
      isPatF2.value = true;
      isPatF3.value = false;
      return false;
    }
    if (Number(val) == 14) {
      isPatF1.value = false;
      isPatF2.value = true;
      isPatF3.value = true;
      return false;
    }
    if (Number(val)===44&&trainData.value.process===1&&isPatF1.value) {
      startTime = Date.now()
      logsPatKeyTime.value = curr_t;
      lastCharacterAt = curr_t
      trainData.value.patKeyVal.push([['开始']]);
      trainData.value.patKeyVal.push([[]]);
      trainData.value.patCodeLog.push({key: '开始', time: 0});
      trainData.value.process = 2;
      currPatKeyIndex.value = 0;
      patWsData.value.log.key = '开始'
      sendMessage(patWsData.value)
      goScrollBottom()
      isPatF3.value = false
      return false;
    }
    if (trainData.value.process === 0) {message.error('训练还未开始!');return false;}
    if (trainData.value.process === 1) {message.error('请拍发开始键!');return false;}
    //开始训练初次拍发调用接口
    if (isFirstKey) {
      capture.close()
      return submission.run(async request => {
        await request(config => startTrainUser({trainId: trainData.value.trainId, attempt: capture.metadata.value.attempt}, config))
        isFirstKey = false
        const queuedReceipt = Object.freeze({...receipt, queued: true})
        submission.defer(() => handleReceiveKeyCode(val, receivedAt, queuedReceipt), true)
      })
    }

    if (trainData.value.patKeyVal.length === 0) {
      trainData.value.patKeyVal.push([]);
    }
    lastRow = trainData.value.patKeyVal[trainData.value.patKeyVal.length - 1];
    if (lastRow.length === 0) {
      lastRow.push([]);
    }
    if(!lastKey){
      lastKey = lastRow[lastRow.length - 1];
    }
    // lastKey = lastRow[lastRow.length - 1];
    time = capture.between(logsPatKeyTime.value, curr_t);


    if (Number(val) === 41) {
      if (isPatF3.value) {
        handlerSubmit('end');
        return false;
      }
      // currPatKeyIndex.value ++;
      // isModify.value = false;
      // lastRow.push([]);

      const lastcode = lastRow[lastRow.length-1]
      if(lastcode[lastcode.length-1]!=='?'&&lastcode[lastcode.length-1]!=='/'){
        if(isModify.value){
          currPatKeyIndex.value++
        }
        lastRow.push([]);
        currPatKeyIndex.value++
        isModify.value = false;
      }
      goScrollBottom()
      return false;
    }
    if (timer) {
      clearTimeout(timer)
      timer = null
    }
    if (Number(val) === 45&&(isPatF1.value||isPatF3.value)) {
      logsPatKeyTime.value = curr_t;
      trainData.value.patKeyVal.push([['句号']]);
      trainData.value.patCodeLog.push({key: '句号', time: time});
      if(trainData.value.floorNow == trainData.value.pag){
        currPatKeyIndex.value = 100;
        handlerSubmit('end');
      }else{
        handlerSubmit();
      }
      goScrollBottom()
      return false;
    }
    if (Number(val) > 20 && Number(val) <= 45 && Number(val) != 41) {
      logsPatKeyTime.value = curr_t;
      if (isPatF1.value || isPatF3.value) {
        k_v = codeInKey[val].text;
        cacheCode = []
      } else if (isPatF2.value) {
        if (Number(val) < 30) {
          if (cacheCode.length > 0) {
            cacheCode.push(...codeInKey[val]._code)
            k_v = codeOnKey[cacheCode.join('')]??'#'
            cacheCode = []
          } else {
            k_v = '#'
          }
        } else {
          if (cacheCode.length > 0) {
            k_v = codeOnKey[cacheCode.join('')]??'#'
            cacheCode = []
            cacheCode.push(...codeInKey[val]._code)
          } else {
            cacheCode = []
            cacheCode.push(...codeInKey[val]._code)
            k_v = ''
          }
          timer = setTimeout(() => {
            k_v = codeOnKey[cacheCode.join('')]??'#'
            cacheCode = []
            patKeyAssignmentInfo(lastKey,k_v,curr_t)
            clearTimeout(timer)
            timer = null
          },pauseDuration.value)
        }
      }
    }
    if (Number(val)===42 && isPatF1.value) {
      if (lastKey.length == 0) {
        currPatKeyIndex.value -- ;
      }
    }
    if (Number(val)===43 && isPatF1.value) {
      if (isModify.value || currPatKeyIndex.value == 0) {
        return false;
      } else {
        if (lastKey.length == 0) {
          currPatKeyIndex.value -= 2;
        } else {
          currPatKeyIndex.value -- ;
        }
        lastKey = lastRow[lastRow.length - 1];
        isModify.value = true;
        // trainData.value.telegraph[trainData.value.floorNow - 1][currPatKeyIndex.value].value.push('/')
      }
    }

    if (isPatF1.value || isPatF3.value || k_v != '') {
      patKeyAssignmentInfo(lastKey,k_v,curr_t)
    }


//计算码率
  };
  let codes = ''

  /**
   * 更新待提交的字码数据
   * @param lastKey
   * @param k_v
   * @param time
   */
  const patKeyAssignmentInfo = (lastKey,k_v,at) => {
    const time = capture.between(lastCharacterAt ?? at, at)
    lastCharacterAt = Math.max(lastCharacterAt ?? at, at)
    let currData = trainData.value.telegraph[trainData.value.floorNow - 1]
    if (k_v.trim() && k_v !== '?' && k_v !== '/') patNumber.value++;
    if (capture.elapsed() > 0) {
      trainData.value.speed = Number((patNumber.value * 60000 / 4 / capture.elapsed()).toFixed(1))
      changeCriterion(trainData.value.speed, trainData.value.messageType === 1 ? 'letter' : trainData.value.messageType === 2 ? 'mix' : 'short')
    }
    lastKey.push(k_v);
    trainData.value.patCodeLog.push({key: k_v, time});
    currPatKeyIndex.value = currPatKeyIndex.value<0?0:currPatKeyIndex.value
    patWsData.value.log.key = k_v
    patWsData.value.log.time = time
    sendMessage(patWsData.value)
    if (!currData) {
      currData = [{key: ['#'], value: [], time: []}]
    }
    if (!currData[currPatKeyIndex.value]) {
      currData[currPatKeyIndex.value] = {
        id: null,
        key: ['#'],
        pageNumber: currData[0].pageNumber,
        sort: currPatKeyIndex.value,
        trainId: currData[0].trainId,
        value: [],
        time: []
      }
    }

    currData[currPatKeyIndex.value].value.push(k_v=='/'?'?':k_v);
    currData[currPatKeyIndex.value].time.push(time);
    goScrollBottom()
    isPatF3.value = false;
    addcode()
  }

  const addcode = ()=>{
    const code = trainData.value.patCodeLog[trainData.value.patCodeLog.length-1]
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
  const handlerSubmit = (type, savedPayload = null) => {
    if (submission.pending()) return submission.retry()
    clearTimeout(timer)
    if (cacheCode.length && !savedPayload) {
      const row = trainData.value.patKeyVal.at(-1)
      const key = row?.at(-1)
      if (key) patKeyAssignmentInfo(key, codeOnKey[cacheCode.join('')] ?? '#', logsPatKeyTime.value)
      cacheCode = []
    }
    const pageNumber = savedPayload?.pageNumber ?? trainData.value.floorNow
    let payload = savedPayload
    if (!payload) {
      const pageValue = deepClone(trainData.value.telegraph[pageNumber - 1]).filter(item => item.value.length > 0)
      for (const item of pageValue) {
        item.value = JSON.stringify(item.value)
        item.key = JSON.stringify(item.key)
        item.time = JSON.stringify(item.time)
      }
      payload = {trainId: trainData.value.trainId, pageNumber, pageValue, ...capture.snapshot()}
    }
    submission.saveSnapshot(snapshotKey(), {type, payload})
    let uploaded = false
    return submission.run(async request => {
      if (payload.attempt !== capture.metadata.value?.attempt) throw new Error('训练轮次已变化，旧记录不会提交到新轮次')
      if (!uploaded) {
        await request(config => uploadElectronKeyZuXunPatResult(payload, config))
        uploaded = true
      }
      if (type === 'end' || pageNumber >= trainData.value.pag) {
        await finishTrainInfo(request)
      } else {
        const response = await request(config => getElectronKeyZuXunPageNumber({trainId: trainData.value.trainId, userId: userInfo.id, pageNumber: pageNumber + 1}, config))
        trainData.value.telegraph[pageNumber] = decodePage(response.data.messageVO)
        capture.bind(response.data)
        if (closingAt === null) capture.open()
        trainData.value.floorNow = pageNumber + 1
        trainData.value.process = 1
        currPatKeyIndex.value = 0
        cacheCode = []
        patWsData.value.log.key = '句号'
        sendMessage(patWsData.value)
      }
      submission.clearSnapshot(snapshotKey())
    })
  }


  /**
   * 结束训练
   * @param type
   */
  const finishTrainInfo = async request => {
    await request(config => finishElectronKeyZuXun({trainId: trainData.value.trainId, attempt: capture.metadata.value.attempt}, config))
    trainData.value.status = 2
    patUser.value.isFinish = 1
    clearInterval(trainTimer.value)
    sendMessage({topic: 'finish', id: userInfo.id})
    router.push({path: scorePath.value, query: {id: trainData.value.trainId, status: 2}})
    emits('changeStatus')
  }

  /**
   * 重置训练
   */

  const connectWebsocket = () => {
    const url =`/generalKeyPatTrain/${userInfo.id}/${trainData.value.trainId}`
    ws_connect(url, receiveWebSocketMessage)
  }

  const cutTime = ref(5)
  const cutTimer = ref(null)
  const receiveWebSocketMessage = (e) => {
    let data = JSON.parse(JSON.parse(e.data).data);
    // console.log(data)
    if(data.topic == 'online'){
      message.success('教员已回来！')
    }
    else if (data.topic == 'offline') {
      message.error('教员已离开！')
    }
    else if (data.topic == 'begin') {
      if (!audioOperation({type: 'ready'})) {
        audioOperation({type: 'message', data: {data: [], numType: 'short'}})
        return
      }
      trainData.value.floorNow = 1
      getPostTrainKeyInfo(1).then(confirmed => {
        if (!confirmed) return
        cutTime.value = 0
        cutTimer.value = 'begin'
        trainData.value.status = 1
        capture.open()
        trainData.value.process = 1
        trainData.value.validTime = 0
        cacheCode = []
        clearInterval(trainTimer.value)
        initTrainTimeInfo()
      })
    }
    else if (data.topic == 'end') {
      if (closingAt !== null) return
      closingAt = performance.now()
      capture.close()
      trainData.value.status = 3
      clearTimeout(timer)
      const finishAfterInput = () => {
        if (patUser.value.isFinish === 1) return
        handlerSubmit('end')
      }
      if (!submission.defer(finishAfterInput)) finishAfterInput()
    }
  }

  const resetAttempt = () => {
    if (trainData.value.status !== 1 || !capture.metadata.value) return
    const attempt = capture.metadata.value.attempt
    resetting = true
    audioOperation({type: 'stop'})
    clearTimeout(timer)
    timer = null
    submission.cancel()
    capture.close()
    let resetConfirmed = false
    return submission.run(async request => {
      if (!resetConfirmed) {
        const observed = await request(config => getElectronKeyZuXunPageNumber({trainId: trainData.value.trainId, userId: userInfo.id, pageNumber: 1}, config))
        if (observed.data.attempt === attempt) {
          await request(config => resetElectronKeyZuXunTrain({trainId: trainData.value.trainId, attempt}, config))
        } else if (observed.data.attempt !== attempt + 1) {
          throw new Error('训练轮次已由其他操作改变，请重新进入训练')
        }
        resetConfirmed = true
        submission.clearSnapshot(snapshotKey())
      }
      const response = await request(config => getElectronKeyZuXunPageNumber({trainId: trainData.value.trainId, userId: userInfo.id, pageNumber: 1}, config))
      if (response.data.attempt !== attempt + 1) throw new Error('重拍轮次已变化，请重新进入训练')
      trainData.value.telegraph = [decodePage(response.data.messageVO)]
      trainData.value.floorNow = 1
      trainData.value.attempt = response.data.attempt
      if (trainData.value.status === 1) closingAt = null
      capture.bind(response.data)
      if (closingAt === null) capture.open()
      trainData.value.process = 1
      trainData.value.validTime = 0
      trainData.value.patKeyVal = []
      trainData.value.patCodeLog = []
      currPatKeyIndex.value = -1
      cachePatCode.value = []
      cachePatLogs.value = []
      cacheKey.value = []
      cacheKeyCode.value = []
      logsPatStandardCode.value = []
      pagePatStandard.value = []
      finishPatLogs.value = []
      patNumber.value = 0
      isFirstKey = true
      patUser.value.isFinish = 0
      startStatus.value = false
      readyPat.value = true
      cutTimer.value = 'begin'
      cacheCode = []
      lastCharacterAt = null
      clearTimeout(timer)
      const rule = typeof trainData.value.ruleContent === 'string' ? JSON.parse(trainData.value.ruleContent) : trainData.value.ruleContent
      changeCriterion(rule.wpm.base, trainData.value.messageType === 1 ? 'letter' : trainData.value.messageType === 2 ? 'mix' : 'short')
      initTrainTimeInfo()
      resetting = false
    })
  }

  const readyTrainPat = async type => {
    if (resetting) return submission.retry()
    if (type !== 1 && trainData.value.status === 1) {
      Modal.confirm({title: '重新拍发', content: '将清空本轮记录并创建新轮次，是否继续？', onOk: resetAttempt})
      return
    }
    const saved = submission.loadSnapshot(snapshotKey())
    if (saved) {
      trainData.value.floorNow = saved.payload.pageNumber
      if (!await getPostTrainKeyInfo(trainData.value.floorNow)) return
      return handlerSubmit(trainData.value.status === 3 ? 'end' : saved.type, saved.payload)
    }
    if (!await getPostTrainKeyInfo(Math.min(trainData.value.floorNow, trainData.value.pag))) return
    if (trainData.value.status === 3) {
      if (capture.metadata.value?.submitted) return submission.run(finishTrainInfo)
      message.warning('训练收尾中，无本轮待补交快照，不能重新采集')
      return
    }
    if (!wsOnline.value || !devOnline.value) { message.error('报训设备未连接'); return }
    if (!await audioOperation({type: 'init'})) return
    readyPat.value = true
    if (trainData.value.status === 1) {
      trainData.value.process = 1
      cutTimer.value = 'begin'
      capture.open()
    }
    sendMessage({topic: 'ready', id: userInfo.id})
  }

  return {
    submissionError: submission.error, submissionBusy: submission.busy, retrySubmit: submission.retry,
    patKeyBoxRef, patValBoxRef, trainTimeRef, initSymbol, errorText, currPatKeyIndex,readyPat,patUser,getPostTrainKeyInfo,
    switchTelegram, resetPatStart,handleReceiveKeyCode, timeAreaShow,readyTrainPat,connectWebsocket,
    initTrainTimeInfo,cutTime,cutTimer
  }
}