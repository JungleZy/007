import {ref, onMounted, onUnmounted, watch, createVNode} from "vue";
import {useRoute} from "vue-router"
import {ExclamationCircleOutlined} from "@ant-design/icons-vue";
import {message, Modal} from "ant-design-vue";
import {getPostMilitaryTrainDetails, beginPostMilitaryTrain, finishPostMilitaryTrain} from "../../../../../../common/api/MilitaryTermApi.js";
import {partTimeFormatInfo} from "../../../../../../common/utils/Utils";
import {PubSub} from "../../../../../../common/utils/PubSub";
import {
  apiPostTrainGlobalRuleType
} from '../../../../../../common/api/postWording'

export default function militaryTrain() {
  const trainTimeRef = ref(null);
  const trainTimer = ref(null);
  const route = useRoute();
  const militaryData = ref({});
  const currAnswer = ref(0);
  const resDuration = ref(0);
  const showResultModal = ref(false);
  const gradeTypeList = ref([])

  PubSub.subscribe('send_examTrainPage', (e)=>{
    if (militaryData.value.status === 1) {
      Modal.confirm({
        class: 'init_modal_style',
        content: '当前训练还未结束，是否结束训练？',
        icon: () => createVNode(ExclamationCircleOutlined),
        okType: 'danger',
        okText: () => '结束',
        cancelText: () => '取消',
        maskClosable: true,
        onOk: () => {
          endTrainInfo('go')
        }
      })
    } else {
      PubSub.publish('callback_closeExamTrainPage', true);
    }
  });

  onMounted(() => {
    if (route.query.id && route.query.id !== '') {
      getTelegramTrainInfo()
    }
  });

  onUnmounted(()=>{
    PubSub.unsubscribe("send_examTrainPage");
  });

  /**
   * 获取收报训练详情
   */
  const getTelegramTrainInfo = () => {
    getPostMilitaryTrainDetails({id: route.query.id}).then(res => {
      if (res.code === 200) {
        for (let key in res.data) {
          militaryData.value[key] = res.data[key];
        }
        resDuration.value = partTimeFormatInfo(res.data.duration*1000, 'number');
        resDuration.value = resDuration.value.replace(/：/g, ':');
        getGradeTypeList({type:1},militaryData.value.accuracy)
        militaryData.value.testPaperList.map((item,i) => {
          if (currAnswer.value < 0 && item.userAnswer === null) {
            currAnswer.value = i;
            changeAnimate();
          }
        });
        if(res.data.status === 1){
          trainTime();
        }
        if(res.data.status === 2){
          showResultModal.value = true;
          trainTimeRef.value.autoSetTimeAdd(militaryData.value.duration)
        }
      }
    })
  };

  //获取评论列表列表
  const getGradeTypeList = (data,number) => {
    const num = number ?? 0
    apiPostTrainGlobalRuleType(data).then(res => {
      const list = res.data ?? []
      gradeTypeList.value = list.map(item => {
        const obj = {
          ...item,
          start: Number(item.accuracy.split('~')[0]),
          end: Number(item.accuracy.split('~')[1])
        }
        return obj
      }).filter((item)=>{
        return item.start< num && num< item.end
      })
    })
  }

  /**
   * 训练用时
   */
  const trainTime = ()=>{
    trainTimer.value =  setInterval(()=>{
      militaryData.value.duration++;
      trainTimeRef.value.autoSetTimeAdd(militaryData.value.duration)
    },1000);
  };

  /**
   * 切换题目
   * @param num
   */
  const changeAnswer = (num) => {
    if ((currAnswer.value === 0 && num < 0) ||
        (currAnswer.value === (militaryData.value.testPaperList.length - 1) && num > 0)) return false;
    currAnswer.value += num;
    changeAnimate();
  };
  /**
   * 题目切换动画
   */
  const changeAnimate = () => {
     anime({
       targets:['.option'],
       duration:1000,
       keyframes:[
         {rotateX:90,duration:200},
         {rotateX:360,duration:200},
         {rotateX:0,duration:0},
       ]
     })
  };

  /**
   * 开始训练
   */
  const startTrainInfo = () => {
    beginPostMilitaryTrain({
      id: militaryData.value.id
    }).then(res => {
      if (res.code === 200) {
        message.success('开始训练！');
        militaryData.value.status = 1;
        currAnswer.value = 0;
        trainTime();
        changeAnimate();
      } else {
        message.error(res.message);
      }
    })

  };

  /**
   * 开始答题
   * @param answer
   */
  const questAnswer = (answer) => {
    if (militaryData.value.status !== 1) return false;
    militaryData.value.testPaperList[currAnswer.value].userAnswer = answer;
    if (currAnswer.value < militaryData.value.testPaperList.length - 1) {
      currAnswer.value ++;
      changeAnimate();
    }
  };

  /**
   * 结束训练
   * @param go
   */
  const endTrainInfo = (go) => {
    clearInterval(trainTimer.value);
    finishPostMilitaryTrain({
      id: militaryData.value.id,
      testPaperList: militaryData.value.testPaperList
    }).then(res => {
      if (res.code === 200) {
        message.success('训练已结束！');
        if (go === 'go') {
          PubSub.publish('callback_closeExamTrainPage', true);
        } else {
          getTelegramTrainInfo()
        }
      } else {
        message.error(res.message);
      }
    })
  };

  return {
    militaryData, trainTimeRef, currAnswer, resDuration, showResultModal,
    startTrainInfo, endTrainInfo, changeAnswer, questAnswer, changeAnimate,
    gradeTypeList
  }
}







