import { onMounted, onUnmounted, ref, watch, nextTick, inject } from 'vue'
import {sum,deepClone} from "../../../../../../../common/utils/Utils.js";
import useMorse from "../../../../../../../common/mixin/useMorse.js";
import {message, Modal} from "ant-design-vue";
import {codeInKey,codeOnKey} from './keyCode.js';
import {useRouter} from "vue-router";
import PublicSocket from '../../../../../../../common/ws/PublicSocket.js'
import {
  finishElectronKeyZuXun,
  getElectronKeyZuXunPageNumber,uploadElectronKeyZuXunPatResult,resetElectronKeyZuXunTrain,startTrainUser
} from "../../../../../../../common/api/electronKeyZuXun.js";

export default function (trainData,wsOnline,devOnline,loading,emits,voiceCode,patKey) {
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
    closeWebSocket();
  });

  /**
   * 获取电报纸的报文内容
   * @param page
   */
  const getPostTrainKeyInfo = (page) => {
    getElectronKeyZuXunPageNumber({
      trainId: trainData.value.trainId,
      userId: userInfo.id,
      pageNumber: page
    }).then(res => {
      if (res.code === 200) {
        res.data.messageVO.forEach((item)=>{
          item.key=JSON.parse(item.key)
          item.time=JSON.parse(item.time)
          item.value=[]
        })
        trainData.value.telegraph[page-1] = res.data.messageVO;
      }
    })
  };

  /**
   * 训练时间转换显示
   */
  const initTrainTimeInfo = () => {
    if (!trainData.value.validTime) {
      trainData.value.validTime = 0;
    }
    trainTimer.value = setInterval(() => {
      trainData.value.validTime += 1;
      // trainData.value.speed = Number(patNumber.value / (trainData.value.validTime/60)).toFixed(1);
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
  const handleReceiveKeyCode = (val) => {
    let k_v = '#',time = 0,lastRow,lastKey,
        curr_t = new Date().getTime();
    if (loading.value || patUser.value.userStatus == 3) return false;
    if((Number(val) == 14||Number(val) == 41)&&cacheCode.length>0){
      time = curr_t - logsPatKeyTime.value;
      k_v = codeOnKey[cacheCode.join('')] ?? '#'
      console.log(k_v);
      cacheCode = []
      lastRow = trainData.value.patKeyVal[trainData.value.patKeyVal.length - 1];
      if (lastRow.length === 0) {
        lastRow.push([]);
      }
      lastKey = lastRow[lastRow.length - 1];
      patKeyAssignmentInfo(lastKey, k_v, time,true)
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
    if(isFirstKey){
      isFirstKey = false
      startTrainUser(trainData.value.trainId)
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
    time = curr_t - logsPatKeyTime.value;


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
        currPatKeyIndex.value = 0;
        trainData.value.process = 1;
        handlerSubmit();
        switchTelegram('next');
        patWsData.value.log.key = '句号'
        sendMessage(patWsData.value)
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
          patKey.value = null;
          timer = setTimeout(() => {
            k_v = codeOnKey[cacheCode.join('')]??'#'
            cacheCode = []
            patKeyAssignmentInfo(lastKey,k_v,time)
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
      patKeyAssignmentInfo(lastKey,k_v,time)
    }


//计算码率
    if(startTime!==0){
      trainData.value.speed = Number(parseFloat(patNumber.value / ((Date.now()-startTime)/1000/ 60)).toFixed(1))
    }
  };
  let codes = ''

  /**
   * 更新待提交的字码数据
   * @param lastKey
   * @param k_v
   * @param time
   */
  const patKeyAssignmentInfo = (lastKey,k_v,time) => {
    let currData = trainData.value.telegraph[trainData.value.floorNow - 1]
    patNumber.value++;
    lastKey.push(k_v);
    trainData.value.patCodeLog.push({key: k_v, time: (time>20000?20000:time)});
    currPatKeyIndex.value = currPatKeyIndex.value<0?0:currPatKeyIndex.value
    patWsData.value.log.key = k_v
    patWsData.value.log.time = (time>20000?20000:time)
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
  let count = 0
  const handlerSubmit = (type)=>{
    if(count!==0)return
    const data = deepClone(trainData.value.telegraph[trainData.value.floorNow - 1])
    const submitData = data.filter(item=>item.value.length>0)
    submitData.forEach((item)=>{
      item.value = JSON.stringify(item.value)
      item.key = JSON.stringify(item.key)
      item.time = JSON.stringify(item.time)
    })
    count++
    uploadElectronKeyZuXunPatResult({
      trainId: trainData.value.trainId,
      pageValue: submitData,
      pageNumber: trainData.value.floorNow,
    }).then(res => {
      count=0
      if (res.code === 200) {
        if (trainData.value.floorNow > trainData.value.pag || type == 'end') {
          patWsData.value.log.key = '完结'
          sendMessage(patWsData.value)
          finishTrainInfo(type)
        }
      }
    })
  };


  /**
   * 结束训练
   * @param type
   */
  const finishTrainInfo = (type) => {
    if (type == 'end') {
      patUser.value.isFinish = 1
      sendMessage({ topic: 'finish', id: userInfo.id })
    }
    loading.value = true;
    finishElectronKeyZuXun({
      trainId: trainData.value.trainId,
      userId: userInfo.id,
    }).then(res => {
      loading.value = false;
      if (res.code === 200) {} else {
        message.error(res.message);
      }
      router.push({path: scorePath.value, query: {id: trainData.value.trainId,status: 2}})
      emits('changeStatus')
    });
  };

  /**
   * 重置训练
   */
  const resetTrainInfo = () => {
    resetElectronKeyZuXunTrain({trainId: trainData.value.trainId}).then(res => {});
  };

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
      message.success("教员已开始训练，准备开始训练！")
      cutTimer.value = setInterval(()=>{
        cutTime.value--
        if(cutTime.value==0){
          clearInterval(cutTimer.value)
          cutTimer.value = "begin"
          if (trainTimer.value) {
            clearInterval(trainTimer.value);
          }
          initTrainTimeInfo()

          trainData.value.floorNow = 1;
          trainData.value.status = 1;
          trainData.value.process = 1;
          trainData.value.validTime = 0;
          cachePatCode.value = [];
          logsPatStandardCode.value = [];
          finishPatLogs.value = [];
        }
      },1000)
    }
    else if (data.topic == 'end') {
      trainData.value.status = 2;
      clearInterval(trainTimer.value)
      if (patUser.value.isFinish != 1) {
        handlerSubmit('end')
      } else {
        router.push({path: scorePath.value, query: {id: trainData.value.trainId,status: 2}})
        emits('changeStatus')
      }
    }
  }

  const readyTrainPat = (type) => {
    if(!wsOnline.value) {message.error('报训软件未连接!'); return false;}
    if(!devOnline.value) {message.error('电子键设备未连接！'); return false;}
    readyPat.value = true;
    if (type == 1) {
      let obj = JSON.parse(window.localStorage.getItem('handKeyZuXun'+trainData.value.trainId))
      trainData.value.floorNow = obj.patPage;
      trainData.value.validTime = obj.time;
      trainData.value.speed = obj.speed;
      currPatKeyIndex.value = obj.patKeyIndex;
    } else {
      cachePatCode.value = [];
      logsPatStandardCode.value = [];
      finishPatLogs.value = [];
      trainData.value.floorNow = 1;
      currPatKeyIndex.value = -1;
      trainData.value.validTime = 0;
      trainData.value.errorNumber = 0;
      trainData.value.accuracy = '0';
      trainData.value.speed = 0;
      if (trainData.value.status == 1) {
        trainData.value.process = 1;
        cutTimer.value = "begin"
        resetTrainInfo();
      }
    }
    sendMessage({topic: 'ready', id: userInfo.id})
  }

  return {
    patKeyBoxRef, patValBoxRef, trainTimeRef, initSymbol, errorText, currPatKeyIndex,readyPat,patUser,getPostTrainKeyInfo,
    switchTelegram, resetPatStart,handleReceiveKeyCode, timeAreaShow,readyTrainPat,connectWebsocket,
    initTrainTimeInfo,cutTime,cutTimer
  }
}