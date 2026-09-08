import {ref, onMounted, onUnmounted, watch} from "vue";
import {useRoute,useRouter} from "vue-router"
import {message} from "ant-design-vue";
import {
  getTelegramTrain,startReceiveTrain,pauseReceiveTrain,goOnReceiveTrain,endReceiveTrain,getPreReceiveDotRate
} from "../../../../../../common/api/ReceiveApi.js";
import {PubSub} from "../../../../../../common/utils/PubSub";
import useMorse from "../../../../../../common/mixin/useMorse.js";
import operationMorseVoice from "../../../../../../common/utils/voice/operationMorseVoice";

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
  const {dots,scatter} = useMorse();
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
  const dotTime = ref(80)
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
  const {operation} = operationMorseVoice()
  PubSub.subscribe('send_receiveTrainPage', (e)=>{
    if (receiveData.value.status === 1) {
      pauseTrainInfo();
    }
    PubSub.publish('callback_receiveTrainPage', true);
  });
  PubSub.subscribe('receiveProcessData',res=>{
    playType.value = res.type
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
  onMounted(() => {
    if (route.query.id && route.query.id !== '') {
      getTelegramTrainInfo()
    }
    window.addEventListener('beforeunload', e => {
      if (receiveData.value.status === 1) {
        pauseTrainInfo();
      }
    });
    if (receiveBgRef.value) {
      receiveBgRef.value.currentTime = 4.7;
    }

    getPreReceiveDotRate().then(res => {
      if (res.code === 200) {
        dotTime.value = res.data;
      }
    })
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

  watch(rateWpm, () => {
    let type = (receiveData.value.type==0?'letter':receiveData.value.type==2?'mix':(receiveData.value.codeShort==1?'short':'long'));
    let ms = dotTime.value;
    let ml = 400 / rateWpm.value * 60 * 1000 / dots[type];
    let pr1 = ((ml - ms) * scatter[type].d / scatter[type].l + ml * 3) / ml;
    let pr2 = ((ml - ms) * scatter[type].d / scatter[type].w + ml * 3) / ml;
    let pr3 = ((ml - ms) * scatter[type].d / scatter[type].g + ml * 5) / ml;
    operation({type:'changeRatio',data:{
        dot: 1,
        dash: Number(pr1.toFixed(2)),
        gap: 1,
        word: Number(pr2.toFixed(2)),
        suite: Number(pr3.toFixed(2)),
        leaf: Number((pr3 / 5 * 7).toFixed(2))
      }})
  });

  onUnmounted(() => {
    PubSub.unsubscribe("send_receiveTrainPage");
    PubSub.unsubscribe("receiveProcessData");
    operation({type:'stop'})
    window.removeEventListener('beforeunload', e => {});
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
        if(wpmTOmm.value){
          let type = (receiveData.value.type==0?'letter':receiveData.value.type==2?'mix':(receiveData.value.codeShort==1?'short':'long'));
          let cri = (400 / (receiveData.value.isLowRate==1?35:receiveData.value.rate) * 60 * 1000 / dots[type]);
          PubSub.publish('receiveSendData',{type:'changeFrequency',data:parseFloat(frequency.value)})
          PubSub.publish('receiveSendData',{type:'changeCriterion',data:parseInt(cri)})
        }else {
          PubSub.publish('receiveSendData',{type:'changeFrequency',data:parseFloat(frequency.value)})
          PubSub.publish('receiveSendData',{type:'changeCriterion',data:calculateSpeed(res.data.rate)})
        }
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
   * 计算播报码率
   */
  const calculateSpeed = (val) => {
    return parseInt(1200/val)
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
  const auditionInfo = () => {
    PubSub.publish('receiveSendData',{type:'message',data:{
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
  const startTrainInfo = () => {
    startReceiveTrain({
      id: receiveData.value.id
    }).then(res => {
      if (res.code === 200) {
        if (receiveBgRef.value) {
          receiveBgRef.value.play();
        }
        receiveData.value.status = 1;
        operation({type:'message',data:{
            numType:receiveData.value.codeShort==1?'short':'long',
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
    PubSub.publish('receiveSendData',{type:'pause'})
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
  const continueTrainInfo = (i) => {
    goOnReceiveTrain({
      id: receiveData.value.id
    }).then(res => {
      if (res.code === 200) {
        if (receiveBgRef.value) {
          receiveBgRef.value.play();
        }
        console.log(receiveData.value.validTime)
        if(playType.value==='playing'){
          PubSub.publish('receiveSendData',{type:'continue'})
        }else {
          //刷新页面重头开始
          PubSub.publish('receiveSendData',{type:'message',data:{
              numType:receiveData.value.codeShort==1?'short':'long',
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
        PubSub.publish('receiveSendData',{type:'stop'})
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
  const repeatTrainInfo = (i) => {
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
    PubSub.publish('receiveSendData',{type:'message',data:{
        numType:receiveData.value.codeShort==1?'short':'long',
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







