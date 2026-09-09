import { onMounted, onUnmounted, ref, watch, nextTick, inject } from 'vue'
import {sum,deepClone} from "../../../../../../../common/utils/Utils.js";
import useMorse from "../../../../../../../common/mixin/useMorse.js";
import {message, Modal} from "ant-design-vue";
import {codeInKey,codeOnKey} from './keyCode.js';
import {useRouter} from "vue-router";
import PublicSocket from '../../../../../../../common/ws/PublicSocket.js'
import {
  getDatagramZuXunPageNumber,uploadDatagramResult,finishDatagramZuXun,startTrainUser
} from "../../../../../../../common/api/datagramZuXun";
export default function (trainData,loading,emits) {

  const {ws_connect,sendMessage,closeWebSocket} = PublicSocket();
  const router = useRouter();
  const userInfo = JSON.parse(localStorage.getItem('userInfo'))
  const currPatKeyIndex = ref(0); // 正在拍发的电报纸字码的下标
  const trainTimeRef = ref(null); // 训练时间展示区域的容器
  const patUser = ref({});
  let submitPromise = null
  let finishPromise = null
  const readyPat = ref(false);
  let isFirstKey = true
  let enterTimer = 0
  let pageTime = 0
  const scorePath = ref('');
  const trainTimer = ref(null); // 记录训练时长的计时器
  const pageCodes = ref([])
  const patWsData = ref({topic: 'pat', id: userInfo.id, log: {key: '', time: 0}})
  router.getRoutes().forEach(r => {
    if (r.name === 'datagramZuXunTrain') {
      scorePath.value = r.path;
    }
  });
  const connectWebsocket = () => {
    const url =`/generalTelexPatTrain/${userInfo.id}/${trainData.value.trainId}`
    ws_connect(url, receiveWebSocketMessage)
  }
  const cutTime = ref(5)
  const cutTimer = ref(null)
  const receiveWebSocketMessage = (e) => {
    let data = JSON.parse(JSON.parse(e.data).data);
    // console.log(data)
    if(data.topic == 'online'){
      message.success('教员已回来！')
    }else if (data.topic == 'offline') {
      message.error('教员已离开！')
    }else if (data.topic == 'begin') {
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
          startTrain()
          trainData.value.floorNow = 1;
          trainData.value.status = 1;
          trainData.value.process = 1;
          trainData.value.validTime = 0;
        }
      },1000)
    }else if (data.topic == 'end') {
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
  //开始训练
  const startTrain = ()=>{
    nextTick(() => {
      document.getElementsByTagName('textarea')[0].focus()
    })
    window.addEventListener('keydown', keyCodeup)
  }
  let lastTime = null
  const keyCodeup = v => {
    const time = Date.now()
    patWsData.value.log.key = v.key
    patWsData.value.log.time = lastTime===null?0:time-lastTime
    lastTime = time
    sendMessage(patWsData.value)
    const str = pageCodes.value[ trainData.value.floorNow - 1]
    if (v.keyCode == 13) {
      enterTimer++
    } else {
      enterTimer = 0
    }
    if (v.code == "F4") {
      switchTelegram('prev')
    }
    if (v.code == "F5") {
      if (v.preventDefault) {
        v.preventDefault()
      } else {
        window.event.returnValue == false
      }
      switchTelegram('next')
    }
    if(isFirstKey){
      isFirstKey = false
      startTrainUser(trainData.value.trainId)
    }
  }
  const textareaChange = () => {
    if (pageCodes.value[currPatKeyIndex.value]) {
      pageCodes.value[currPatKeyIndex.value] = pageCodes.value[currPatKeyIndex.value].toUpperCase()
    }
    if (enterTimer == 2) {
      switchTelegram('next')
      enterTimer = 0
    }
  }
  /**
   * 切换电报纸
   * @param type
   */
  const switchTelegram = (type) => {
    handlerSubmit()

    if((type === 'prev' && trainData.value.floorNow <= 1)){
      message.error('已经是第一页！')
      return false
    }
    if(type === 'next' &&trainData.value.floorNow >= trainData.value.pag){
      message.error('已经是最后一页！')
      return false
    }
    if (type === 'prev') {
      trainData.value.floorNow --;
    }
    if (type === 'next') {
      trainData.value.floorNow ++;
      currPatKeyIndex.value++
      pageCodes.value[currPatKeyIndex.value] = ""
    }
    if (!trainData.value.telegraph[trainData.value.floorNow + 1] && trainData.value.floorNow < trainData.value.pag) {
      getPostTrainKeyInfo(trainData.value.floorNow + 1);
    }

  };
  /**
   * 获取电报纸的报文内容
   * @param page
   */
  const getPostTrainKeyInfo = (page) => {
    getDatagramZuXunPageNumber({
      trainId: trainData.value.trainId,
      userId: userInfo.id,
      pageNumber: page
    }).then(res => {
      if (res.code === 200) {
        res.data.messageVO.forEach((item)=>{
          item.value=[]
        })
        console.log(page)
        trainData.value.telegraph[page-1] = res.data.messageVO.filter(item=>item.sort>-1);
      }
    })
  };

  /**
   * 提交当前页面拍发的数据
   */
  const handlerSubmit = type => {
    if (submitPromise) return submitPromise
    const value = pageCodes.value[currPatKeyIndex.value]
    if (!value) return type === 'end' ? finishTrainInfo(type) : Promise.resolve(true)
    const currentTime = Number(trainData.value.validTime) || 0
    const elapsed = Math.max(0, currentTime - pageTime)
    const groups = value.split(' ')
    const speed = elapsed > 0 ? (groups.length / (elapsed / 60)).toFixed(1) : '0'
    submitPromise = uploadDatagramResult({trainId: trainData.value.trainId, patValue: value,
      pageNumber: trainData.value.floorNow, validTime: elapsed, speed}).then(res => {
      if (res.code !== 200) { message.error(res.message || '提交训练内容失败'); return false }
      pageTime = currentTime
      return type === 'end' ? finishTrainInfo(type) : true
    }).finally(() => { submitPromise = null })
    return submitPromise
  }

  const endTrain = () => handlerSubmit('end')
  /**
   * 结束训练
   * @param type
   */
  const finishTrainInfo = type => {
    if (finishPromise) return finishPromise
    if (patUser.value.isFinish === 1) return Promise.resolve(false)
    loading.value = true
    finishPromise = finishDatagramZuXun({trainId: trainData.value.trainId, userId: userInfo.id}).then(res => {
      if (res.code !== 200) { message.error(res.message || '完成训练失败'); return false }
      patUser.value.isFinish = 1
      sendMessage({topic: 'finish', id: userInfo.id})
      router.push({path: scorePath.value, query: {id: trainData.value.trainId, status: 2}})
      emits('changeStatus')
      return true
    }).finally(() => { loading.value = false; finishPromise = null })
    return finishPromise
  }

  const readyTrainPat = (type) => {
    readyPat.value = true;
    if (type == 1) {
      let obj = JSON.parse(window.localStorage.getItem('datagramZuXun'+trainData.value.trainId))
      trainData.value.floorNow = obj.patPage;
      trainData.value.validTime = obj.time;
      trainData.value.speed = obj.speed;
      // pageCodes.value = obj.pageCodes;
      currPatKeyIndex.value = obj.patKeyIndex;
    } else {
      trainData.value.floorNow = 1;
      currPatKeyIndex.value = 0;
      trainData.value.validTime = 0;
      trainData.value.errorNumber = 0;
      trainData.value.accuracy = '0';
      trainData.value.speed = 0;
      if (trainData.value.status == 1) {
        trainData.value.process = 1;
        cutTimer.value = "begin"
        // resetTrainInfo();
      }
    }
    startTrain()
    nextTick(() => {
      document.getElementsByTagName('textarea')[0].focus()
    })
    sendMessage({topic: 'ready', id: userInfo.id})
  }
  /**
   * 重置训练
   */
  const resetTrainInfo = () => {
    resetHandKeyZuXunTrain({
      id: trainData.value.trainId
    }).then(res => {});
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
   * 训练时间转换显示
   */
  const initTrainTimeInfo = () => {
    if (!trainData.value.validTime) {
      trainData.value.validTime = 0;
    }
    trainTimer.value = setInterval(() => {
      trainData.value.validTime += 1;

      // trainData.value.speed = Number(patNumber.value / (trainData.value.validTime/60)).toFixed(1);

      let codelength = 0
      pageCodes.value.forEach(item => {
        const str = item.replaceAll(" ",'')
        codelength += str.length
      })
      trainData.value.speed = (codelength / (trainData.value.validTime / 60) / 4).toFixed(1) * 1
      timeAreaShow(trainData.value.validTime * 1000);
    },1000);
  };
  onUnmounted(() => {
    window.removeEventListener('keydown', keyCodeup)
    clearInterval(trainTimer.value);
    closeWebSocket();
  });
  return {
    timeAreaShow,
    connectWebsocket,
    initTrainTimeInfo,
    patUser,
    readyPat,
    getPostTrainKeyInfo,
    readyTrainPat,
    currPatKeyIndex,
    pageCodes,
    textareaChange,
    switchTelegram,
    cutTime,cutTimer,trainTimeRef,
    endTrain
  }
}