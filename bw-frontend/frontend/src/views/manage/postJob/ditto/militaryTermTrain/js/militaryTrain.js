import {ref, onMounted, onUnmounted, createVNode} from 'vue';
import {useRoute} from 'vue-router';
import {ExclamationCircleOutlined} from '@ant-design/icons-vue';
import {message, Modal} from 'ant-design-vue';
import {
  getPostMilitaryTrainDetails,
  beginPostMilitaryTrain,
  finishPostMilitaryTrain
} from '../../../../../../common/api/MilitaryTermApi.js';
import {partTimeFormatInfo} from '../../../../../../common/utils/Utils.js';
import {PubSub} from '../../../../../../common/utils/PubSub.js';
import {apiPostTrainGlobalRuleType} from '../../../../../../common/api/postWording.js';

export default function militaryTrain() {
  const trainTimeRef = ref(null);
  const trainTimer = ref(null);
  const route = useRoute();
  const militaryData = ref({});
  const currAnswer = ref(0);
  const resDuration = ref(0);
  const showResultModal = ref(false);
  const gradeTypeList = ref([]);

  PubSub.subscribe('send_examTrainPage', function () {
    if (militaryData.value.status === 1) {
      Modal.confirm({
        class: 'init_modal_style',
        content: '当前训练还未结束，是否结束训练？',
        icon: function () {
          return createVNode(ExclamationCircleOutlined);
        },
        okType: 'danger',
        okText: function () {
          return '结束';
        },
        cancelText: function () {
          return '取消';
        },
        maskClosable: true,
        onOk: function () {
          endTrainInfo('go');
        }
      });
    } else {
      PubSub.publish('callback_closeExamTrainPage', true);
    }
  });

  onMounted(function () {
    if (route.query.id && route.query.id !== '') {
      getMilitaryTrainInfo();
    }
  });

  onUnmounted(function () {
    stopTrainTimer();
    PubSub.unsubscribe('send_examTrainPage');
  });

  function getMilitaryTrainInfo() {
    getPostMilitaryTrainDetails({id: route.query.id}).then(function (res) {
      if (res.code !== 200) {
        message.error(res.message || '加载训练失败');
        return;
      }
      const data = res.data;
      if (!data || typeof data !== 'object' || !data.id || !Array.isArray(data.testPaperList)) {
        message.error('训练数据无效，请重新加载');
        return;
      }
      Object.assign(militaryData.value, data);
      const duration = data.duration || 0;
      resDuration.value = partTimeFormatInfo(duration * 1000, 'number').replace(/：/g, ':');
      getGradeTypeList({type: 1}, militaryData.value.accuracy);
      data.testPaperList.forEach(function (item, index) {
        if (currAnswer.value < 0 && item.userAnswer === null) {
          currAnswer.value = index;
          changeAnimate();
        }
      });
      if (data.status === 1) {
        trainTime();
      } else {
        stopTrainTimer();
      }
      if (data.status === 2) {
        showResultModal.value = true;
        if (trainTimeRef.value) trainTimeRef.value.autoSetTimeAdd(duration);
      }
    });
  }

  function getGradeTypeList(data, number) {
    const num = number ?? 0;
    apiPostTrainGlobalRuleType(data).then(function (res) {
      if (res.code !== 200) {
        message.error(res.message || '加载评分规则失败');
        return;
      }
      const list = res.data ?? [];
      gradeTypeList.value = list.map(function (item) {
        const bounds = item.accuracy.split('~');
        return {
          ...item,
          start: Number(bounds[0]),
          end: Number(bounds[1])
        };
      }).filter(function (item) {
        return item.start < num && num < item.end;
      });
    });
  }

  function stopTrainTimer() {
    if (trainTimer.value !== null) {
      clearInterval(trainTimer.value);
      trainTimer.value = null;
    }
  }

  function trainTime() {
    stopTrainTimer();
    trainTimer.value = setInterval(function () {
      militaryData.value.duration = (militaryData.value.duration || 0) + 1;
      if (trainTimeRef.value) {
        trainTimeRef.value.autoSetTimeAdd(militaryData.value.duration);
      }
    }, 1000);
  }

  function changeAnswer(num) {
    const papers = militaryData.value.testPaperList || [];
    if ((currAnswer.value === 0 && num < 0)
        || (currAnswer.value === papers.length - 1 && num > 0)) {
      return false;
    }
    currAnswer.value += num;
    changeAnimate();
  }

  function changeAnimate() {
    anime({
      targets: ['.option'],
      duration: 1000,
      keyframes: [
        {rotateX: 90, duration: 200},
        {rotateX: 360, duration: 200},
        {rotateX: 0, duration: 0}
      ]
    });
  }

  function startTrainInfo() {
    beginPostMilitaryTrain({id: militaryData.value.id}).then(function (res) {
      if (res.code !== 200) {
        message.error(res.message || '开始训练失败');
        return;
      }
      message.success('开始训练！');
      militaryData.value.status = 1;
      currAnswer.value = 0;
      trainTime();
      changeAnimate();
    });
  }

  function questAnswer(answer) {
    const papers = militaryData.value.testPaperList || [];
    if (militaryData.value.status !== 1 || !papers[currAnswer.value]) {
      return false;
    }
    papers[currAnswer.value].userAnswer = answer;
    if (currAnswer.value < papers.length - 1) {
      currAnswer.value++;
      changeAnimate();
    }
  }

  function endTrainInfo(go) {
    const testPaperList = (militaryData.value.testPaperList || []).map(function (item) {
      return {
        id: item.id,
        userAnswer: item.userAnswer
      };
    });
    finishPostMilitaryTrain({
      id: militaryData.value.id,
      testPaperList
    }).then(function (res) {
      if (res.code !== 200) {
        message.error(res.message || '训练结束失败');
        return;
      }
      stopTrainTimer();
      message.success('训练已结束！');
      if (go === 'go') {
        PubSub.publish('callback_closeExamTrainPage', true);
      } else {
        getMilitaryTrainInfo();
      }
    });
  }

  return {
    militaryData,
    trainTimeRef,
    currAnswer,
    resDuration,
    showResultModal,
    startTrainInfo,
    endTrainInfo,
    changeAnswer,
    questAnswer,
    changeAnimate,
    gradeTypeList
  };
}
