import {onMounted, onUnmounted, ref, watch} from "vue";
import {Ws, wsCode} from '../../../../../../../common/ws/Ws.js'
import useTraffic from '../../../../../../../common/mixin/useTraffic'

let wsBackend;
export default function (trainData) {
  const patStandard = ref({
    dot: 80,
    line: 240,
    codeGap: 80,
    wordGap: 240,
    groupGap: 400
  });
  const initFloat = ref(50); // 计算拍发基准值的浮动百分比
  const handKeyDown = ref(false);
  const handKeyValue = ref('');
  const upFlagValue = ref({d: 0});
  const downFlagValue = ref({d: 0});
  const diffTime = ref([]);
  const gapTime = ref([]);
  const flag = ref(1);
  const audioVolume = ref(60);
  const voiceFreq = ref(1000);
  const wsFlag = ref(true);
  // 手键数据处理
  const handleHandKeysData = (data) => {
    if (data.t == 1) {
      return false
    }
    if (trainData.value.process != 0) {
      handKeyDown.value = data.k === 0;
      if (data.k === 0) { // 按下
        // playVoice();
        if (diffTime.value.length > 0) {
          diffTime.value = [];
        }
        diffTime.value.push(data.d);
        gapTime.value.push(data.d);
        upFlagValue.value = data;
        if (downFlagValue.value.d !== 0) {
          const diff = data.d - downFlagValue.value.d;
          if (trainData.value.trainId && trainData.value.trainId !== '') {
            if (diff <= patStandard.value.codeGap * (1 + initFloat.value / 100)) {// 电码间隔
              assembleData(2, diff);
            } else if (diff <= patStandard.value.wordGap * (1 + initFloat.value / 100)) {// 词码间隔
              assembleData(2, diff);
            } else if (diff <= patStandard.value.groupGap * (1 + initFloat.value / 100)) {// 词组间隔
              assembleData(2, diff);
            } else {// 异常间隔
              assembleData(12, diff);
            }
          }
          handKeyValue.value = -1;
          downFlagValue.value = {d: 0};
        }
      } else { // 弹起
        diffTime.value.push(data.d);
        if (gapTime.value.length > 0) {
          gapTime.value = [];
        }
        gapTime.value.push(data.d);
        downFlagValue.value = data;
        if (upFlagValue.value.d !== 0) {
          const diff = data.d - upFlagValue.value.d;
          handKeyValue.value = '';
          if (diff <= 10) {
            // console.log("异常点")
            handKeyValue.value = 0;
            if (trainData.value.trainId && trainData.value.trainId !== '') {
              assembleData(10, diff);
            }
          } else if (diff <= 120) {/*patStandard.value.dot * (1 + dotInitFloat.value / 100)*/
            // console.log("点")
            handKeyValue.value = 0;
            if (trainData.value.trainId && trainData.value.trainId !== '') {
              assembleData(0, diff);
            }
          } else if (diff <= patStandard.value.line * (1 + initFloat.value / 100)) {
            // console.log("划")
            handKeyValue.value = 1;
            if (trainData.value.trainId && trainData.value.trainId !== '') {
              assembleData(1, diff);
            }
          } else {
            // console.log("异常划")
            handKeyValue.value = 1;
            if (trainData.value.trainId && trainData.value.trainId !== '') {
              assembleData(11, diff);
            }
          }
          // stopVoice();
          upFlagValue.value = {d: 0};
        }
      }
    }
  }
  const {wsOnline, devOnline} = useTraffic(true,handleHandKeysData)
  const init = () => {
    return new Promise(() => {
      if (trainData.value.status === 2) {
        return;
      }
      wsBackend = Ws.getInstance();
    })
  }
  onUnmounted(() => {
    wsBackend = null;
  })
  watch(audioVolume, () => {
    // diObj.volume = audioVolume.value / 100;
  })

  // 电子键数据处理
  const handleElectronicKeysData = (data) => {

  }
  const downTime = ref(0);
  const upTime = ref(0);
  const handleHandKeysDate = (data) => {
    return new Promise((resolve, reject) => {
      if (trainData.value.process !== 0) {
        if (data.k === 0) {  // 按下
          downTime.value = data.d;
          if (upTime.value > 0) {
            assembleData(2, downTime.value - upTime.value);
          }
        } else {  // 弹起
          upTime.value = data.d;
          if (downTime.value > 0) {
            const diff = upTime.value - downTime.value;
            if (diff <= patStandard.value.dot * (1 + initFloat.value / 100)) {// 点
              assembleData(0, diff);
              return;
            }
            if (diff <= patStandard.value.line * (1 + initFloat.value / 100)) {// 划
              assembleData(1, diff);
            }
          }
        }
      }
    })
  }
  const assembleData = (type, time) => {
    // wsBackend.sendData(wsCode.SEND_TELEGRAM_TRAIN_LOG, {
    //   telegramTrainId: trainData.value.trainId,
    //   type: type,
    //   value: time
    // });
  }
  return {
    handKeyDown,
    patStandard,
    initFloat,
    handKeyValue,
    diffTime,
    gapTime,
    audioVolume,
    wsOnline,
    devOnline,
    init
  }
}