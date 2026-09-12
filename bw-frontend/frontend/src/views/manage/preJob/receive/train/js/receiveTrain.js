import {ref, onMounted, onUnmounted, watch} from "vue";
import {useRoute,useRouter} from "vue-router"
import {message} from "ant-design-vue";
import {
  getTelegramTrain,startReceiveTrain,pauseReceiveTrain,goOnReceiveTrain,endReceiveTrain
} from "../../../../../../common/api/ReceiveApi.js";
import {PubSub} from "../../../../../../common/utils/PubSub";
import operationMorseVoice from "../../../../../../common/utils/voice/operationMorseVoice";
import {receiveTiming} from '../../../../../../common/utils/voice/MorseVoiceHighPerformance'

export default function telegramList(wpmTOmm) {
  const receiveBgRef = ref(null);
  const downTimeRef = ref(null);
  const trainKeyCodeRef = ref(null);
  const route = useRoute();
  const router = useRouter();
  const receiveData = ref({});
  const plugData = ref({
    body: [],
    pag: 1,
    curr: 1,
  });
  const validTime = ref([0,0,0,0,0,0]);
  const voicePlayData = ref({
    curr: [],
    flow: [],
    index: 0,
    total: 0,
    currentCode:-1,
  });
  const audioVolume = ref(60);
  const rateWpm = ref(35);
  const trainTimer = ref(null);
  const symbol = ref({
    start: [1,0,0,0,1],
    end: [0,1,0,1,0]
  });
  /** 播报配置 */
  const frequency = ref(1000);
  const freqGather = ref({
    gather: [
      {name: '舒适', type: 'cozy', min: 500, max: 3000, step: 50},
      {name: '低频', type: 'low', min: 100, max: 500, step: 20},
      {name: '高频', type: 'high', min: 3000, max: 20000, step: 500},
    ],
    curr: {name: '舒适', type: 'cozy', min: 500, max: 3000, step: 100}
  });
  const disturbList = ref([
    {
      type: 1,
      name: '白噪音',
      url: '/006/noise/noise0.wav',
      ctx: null,
      xhr: null,
      gain: null,
      source: null,
    },
    {
      type: 2,
      name: '俄语',
      url: '/006/noise/noise1.wav',
      ctx: null,
      xhr: null,
      gain: null,
      source: null,
    },
    {
      type: 3,
      name: '日语',
      url: '/006/noise/noise2.wav',
      ctx: null,
      xhr: null,
      gain: null,
      source: null,
    },
    {
      type: 4,
      name: '英语',
      url: '/006/noise/noise3.wav',
      ctx: null,
      xhr: null,
      gain: null,
      source: null,
    },
    {
      type: 5,
      name: '战场音',
      url: '/006/noise/noise4.wav',
      ctx: null,
      xhr: null,
      gain: null,
      source: null,
    },
    {
      type: 6,
      name: '防空警报',
      url: '/006/noise/noise5.wav',
      ctx: null,
      xhr: null,
      gain: null,
      source: null,
    }
  ]);
  const checkedDisturb = ref([]);
  const disturbVol = ref(60);
  const playCode = ref([])
  const playType = ref('')
  const activePlayindex = ref(1)//当前播报字码总顺序
  const activeIndex = ref(0)//当前播报字码顺序
  const {operation, ensureReady} = operationMorseVoice()
  PubSub.subscribe('send_receiveTrainPage', (e)=>{
    if (receiveData.value.status === 1) {
      pauseTrainInfo();
    }
    PubSub.publish('callback_receiveTrainPage', true);
  });
  const audioSubscription = PubSub.subscribe('receiveProcessData',res=>{
    if (res.type === 'playing' || res.type === 'pause') playType.value = res.status === 'finish' ? 'ready' : res.type
    if (res.type === 'stopped') playType.value = 'ready'
    if(res.i>0){
      voicePlayData.value.index ++;
      receiveData.value.schedule = parseFloat((voicePlayData.value.index/voicePlayData.value.total)*100).toFixed(1);
      receiveData.value.schedule = receiveData.value.schedule>100?100:receiveData.value.schedule;
      if (res.key === " ") {
        activeIndex.value++
        voicePlayData.value.curr[0]++;
        voicePlayData.value.currentCode=0
      }
      if(res.key === "/"&&res.j===5){
        activeIndex.value = 0
        if (plugData.value.curr < plugData.value.pag) {
          plugData.value.curr ++;
          plugData.value.body = receiveData.value.codeMessageBody.filter((item, i) => {
            return (i >= (plugData.value.curr - 1) * 100 && i < plugData.value.curr * 100);
          });
          // voicePlayData.value.curr = [0,0]
        }
      }
      if (activePlayindex.value !==  res.i) {
        voicePlayData.value.currentCode ++;
      }
      activePlayindex.value =  res.i
    }
  })
  const beforeUnload = () => {
    if (receiveData.value.status === 1) pauseTrainInfo()
  }
  onMounted(() => {
    if (route.query.id && route.query.id !== '') {
      getTelegramTrainInfo()
    }
    window.addEventListener('beforeunload', beforeUnload)
    if (receiveBgRef.value) {
      receiveBgRef.value.currentTime = 4.7;
    }

  });

  watch(frequency, () => {
    operation({type:'changeFrequency',data:parseFloat(frequency.value)})
  });

  watch(audioVolume, () => {
    operation({type:'changeVolume',data:parseFloat(audioVolume.value)/100})
  });

  watch(disturbVol, () => {
    disturbList.value.map(item => {
      if (item.ctx && item.gain) {
        item.gain.gain.value = Number(disturbVol.value/100);
      }
    })
  });

  const applyTiming = () => {
    if (!receiveData.value.rate) return false
    try {
      operation({type: 'configure', data: {...receiveTiming(receiveData.value, receiveData.value.isLowRate == 1 ? rateWpm.value : receiveData.value.rate, wpmTOmm.value ? 'characters' : 'wpm'), frequency: Number(frequency.value), volume: audioVolume.value / 100, model: true}})
      return true
    } catch (error) {
      message.error(error.message)
      return false
    }
  }
  watch([rateWpm, wpmTOmm], applyTiming)

  onUnmounted(() => {
    PubSub.unsubscribe("send_receiveTrainPage");
    PubSub.unsubscribe(audioSubscription);
    operation({type:'stop'})
    clearInterval(trainTimer.value)
    window.removeEventListener('beforeunload', beforeUnload)
    disturbList.value.map(item => {
      if (item.ctx) {changeAudioPlay(item, false)}
    })
  });

  /**
   * 获取收报训练详情
   */
  const getTelegramTrainInfo = () => {
    getTelegramTrain({
      id: route.query.id
    }).then(res => {
      if (res.code === 200) {
        res.data.codeMessageBody.map(item => {
          item.value = JSON.parse(item.value);
        });
        receiveData.value = res.data;
        if (res.data.isLowRate == 1) {
          rateWpm.value = receiveData.value.rate;
        }
        handleVoicePlayCodeHigh(res.data.codeMessageBody)
        let list=[];
        receiveData.value.codeMessageBody.forEach((item,index)=>{
          if (!((index+1)%4)){
            list.push(
                [receiveData.value.codeMessageBody[index-3],
                  receiveData.value.codeMessageBody[index-2],
                  receiveData.value.codeMessageBody[index-1] ,
                  receiveData.value.codeMessageBody[index]]
            );
          }
        });
        receiveData.value.codeMessageBody=list;
        plugData.value.body = receiveData.value.codeMessageBody.filter((item, i) => i < 100);
        plugData.value.pag = Math.ceil(receiveData.value.codeMessageBody.length / 100);
        applyTiming()
        voicePlayData.value.curr = res.data.mark.split(',').map(n => Number(n));
        if (res.data.validTime && res.data.validTime > 0) {
          handleValidTime(res.data.validTime);
        }
      } else {
        message.error(res.message);
      }
    })
  };


  /**
   * 处理训练用时
   */
  const handleValidTime = (time) => {
    time = parseInt(time);
    let h = parseInt(time/(60*60*1000)),
        m = parseInt((time - h*60*60*1000)/(60*1000)),
        s = parseInt((time - h*60*60*1000 - m*60*1000)/1000),
        t = '000000';
    h = h<10?('0'+h):h+'';
    m = m<10?('0'+m):m+'';
    s = s<10?('0'+s):s+'';
    t = h+m+s;
    validTime.value = t.split('').map(num => parseInt(num))
  };
  /**
   * 听报字码转换成播放数据流
   */
  const handleVoicePlayCodeHigh = (data)=>{
    playCode.value.push("#")
    playCode.value.push(" ")
    data.forEach((item,index)=>{
      playCode.value.push(item.key)
      if(index%4==3&&index%400!==399&&index!==0){
        playCode.value.push(" ")
      }else if(index%400===399&&data.length-1!==index&&index!==0){
        playCode.value.push(" ")
        playCode.value.push("/")
        playCode.value.push(" ")
      }
    })
    playCode.value.push(" ")
    playCode.value.push("!")
  }
  /**
   * 试听
   */
  const auditionInfo = async () => {
    if (!await ensureReady()) return
    operation({type:'message',data:{
        numType:'short',
        data: ['5','0']
      }})
  };

  /**
   * 训练计时
   */
  const initTrainTiming = () => {
    trainTimer.value = setInterval(() => {
      if (!(receiveData.value.validTime && receiveData.value.validTime > 0)) {
        receiveData.value.validTime = 0;
      }
      receiveData.value.validTime = Number(receiveData.value.validTime);
      receiveData.value.validTime += 1000;
      handleValidTime(receiveData.value.validTime)
    },1000);
  };

  /**
   * 切换翻页
   * @param type
   */
  const changePage = (type) => {
    plugData.value.curr += type
    plugData.value.body = receiveData.value.codeMessageBody.filter((item, i) => {
      return (i >= (plugData.value.curr - 1) * 100 && i < plugData.value.curr * 100)
    });
  };

  /**
   * 开始训练
   */
  const startTrainInfo = async () => {
    if (!await ensureReady()) return
    if (!applyTiming()) return
    startReceiveTrain({
      id: receiveData.value.id
    }).then(res => {
      if (res.code === 200) {
        if (receiveBgRef.value) {
          receiveBgRef.value.play();
        }
        receiveData.value.status = 1;
        operation({type:'message',data:{
            numType:receiveData.value.type == 2 ? 'mix' : receiveData.value.codeShort == 1 ? 'short' : 'long',
            data:playCode.value
          }})
        initTrainTiming();
        disturbList.value.map(item => {
          if (checkedDisturb.value.indexOf(item.type) > -1) {
            changeAudioPlay(item, true)
          }
        });
      } else {
        message.error(res.message);
      }
    })
  };

  /**
   * 暂停训练
   */
  const pauseTrainInfo = () => {
    operation({type:'pause'})
    clearInterval(trainTimer.value);
    pauseReceiveTrain({
      id: receiveData.value.id,
      schedule: Number(receiveData.value.schedule),
      validTime: receiveData.value.validTime+'',
      mark:  voicePlayData.value.curr.join(','),
    }).then(res => {
      if (res.code === 200) {
        if (receiveBgRef.value) {
          receiveBgRef.value.pause();
          receiveBgRef.value.currentTime = 4.7;
        }
        disturbList.value.map(item => {
          if (item.ctx) {changeAudioPlay(item, false)}
        })
        receiveData.value.status = 2;
      } else {
        message.error(res.message);
      }
    })
  };

  /**
   * 继续训练
   */
  const continueTrainInfo = async (i) => {
    if (!await ensureReady()) return
    if (!applyTiming()) return
    goOnReceiveTrain({
      id: receiveData.value.id
    }).then(res => {
      if (res.code === 200) {
        if (receiveBgRef.value) {
          receiveBgRef.value.play();
        }
        console.log(receiveData.value.validTime)
        if(playType.value === 'playing' || playType.value === 'pause'){
          operation({type:'continue'})
        }else {
          //刷新页面重头开始
          operation({type:'message',data:{
              numType:receiveData.value.type == 2 ? 'mix' : receiveData.value.codeShort == 1 ? 'short' : 'long',
              data:playCode.value
            }})
        }
        receiveData.value.status = 1;
        disturbList.value.map(item => {
          if (checkedDisturb.value.indexOf(item.type) > -1) {
            changeAudioPlay(item, true)
          }
        });
        initTrainTiming();
      } else {
        message.error(res.message);
      }
    })
  };

  /**
   * 结束训练
   */
  const endTrainInfo = () => {
    clearInterval(trainTimer.value);
    endReceiveTrain({
      id: receiveData.value.id,
      schedule: Number(receiveData.value.schedule),
      validTime: receiveData.value.validTime+'',
      mark:  voicePlayData.value.curr.join(','),
    }).then(res => {
      if (res.code === 200) {
        if (receiveBgRef.value) {
          receiveBgRef.value.pause();
          receiveBgRef.value.currentTime = 4.7;
        }
        receiveData.value.status = 3;
        operation({type:'stop'})
        disturbList.value.map(item => {
          if (item.ctx) {changeAudioPlay(item, false)}
        })
        router.go(-1);
      } else {
        message.error(res.message);
      }
    })
  };

  /**
   * 重听
   */
  const repeatTrainInfo = async (i) => {
    if (!await ensureReady()) return
    if (i === 0) {
      receiveData.value.schedule = 0;
      voicePlayData.value.index = 0;
      // voicePlayData.value.curr = [0,0];
      voicePlayData.value.currentCode=0
      plugData.value.curr=1;
      plugData.value.body = receiveData.value.codeMessageBody.filter((item, i) => i < 100);
    }
    if (receiveBgRef.value) {
      receiveBgRef.value.play();
    }
    activeIndex.value = 0
    activePlayindex.value = 0
    receiveData.value.status = 1;
    operation({type:'message',data:{
        numType:receiveData.value.type == 2 ? 'mix' : receiveData.value.codeShort == 1 ? 'short' : 'long',
        data:playCode.value
      }})
  };

  /**
   * 停止所有干扰
   */
  const onOffDisturb = () => {
    disturbList.value.map(item => {
      if (item.ctx) {changeAudioPlay(item, false)}
    })
    checkedDisturb.value = [];
  }

  /**
   * 改变干扰选中
   * @param item
   */
  const changeDisturbInfo = (item) => {
    if (checkedDisturb.value.indexOf(item.type) == -1) {
      changeAudioPlay(item, true)
    } else {
      changeAudioPlay(item, false)
    }
  }

  /**
   * 开始干扰/停止干扰
   * @param item
   * @param status
   */
  const changeAudioPlay = (item, status) => {
    if (status) {
      item.ctx = new (AudioContext || window.webkitAudioContext)();
      item.xhr = new XMLHttpRequest();
      item.xhr.open("GET",window.fileUrl+item.url,true);
      item.xhr.responseType = "arraybuffer";
      item.xhr.onload = ()=>{
        item.ctx.decodeAudioData(item.xhr.response,buffer=>{
          item.source = item.ctx.createBufferSource();
          item.source.buffer = buffer;
          item.source.loop = true;
          item.gain = item.ctx.createGain();
          item.gain.gain.value = Number(disturbVol.value/100);
          item.gain.connect(item.ctx.destination);
          item.source.connect(item.gain);
          item.source.start(0);
        })
      };
      item.xhr.send();
    } else {
      item.source.stop(0);
      item.ctx = null;
      item.xhr = null;
      item.source = null;
      item.gain = null;
    }
  };


  return {
    receiveBgRef,downTimeRef,trainKeyCodeRef,receiveData,validTime,frequency,freqGather,audioVolume,voicePlayData,rateWpm,
    plugData,disturbList,checkedDisturb,disturbVol,auditionInfo,startTrainInfo,pauseTrainInfo,continueTrainInfo,
    endTrainInfo,repeatTrainInfo,changePage,onOffDisturb,changeDisturbInfo,activeIndex
  }
}







