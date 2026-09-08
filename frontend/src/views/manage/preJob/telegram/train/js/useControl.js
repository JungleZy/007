import { onMounted, onUnmounted, ref, watch } from 'vue'
import { Ws, wsCode } from '../../../../../../common/ws/Ws.js'
import useTraffic from '../../../../../../common/mixin/useTraffic'
import operationMorseVoice from "../../../../../../common/utils/voice/operationMorseVoice";

let wsBackend
export default function (trainData) {
  const patStandard = ref({
    dot: { min: 1, max: 80 },
    line: { min: 81, max: 240 },
    interval: { min: 81, max: 240 },
    gap: { min: 241, max: 400 }
  })
  const handKeyDown = ref(false)
  const handKeyValue = ref(null)
  const upFlagValue = ref({ d: 0 })
  const downFlagValue = ref({ d: 0 })
  const diffTime = ref([])
  const gapTime = ref([])
  const audioVolume = ref(60)
  const {operation} = operationMorseVoice()

  // 手键数据处理
  const handleHandKeysData = data => {
    if (data.t == 1) {
      return false
    }
    if (trainData.value.status <= 1) {
      handKeyDown.value = data.k === 0
      // handleHandKeysDate(data).then();
      if (data.k === 0) {
        // 按下
        // playVoice();
        if (diffTime.value.length > 0) {
          diffTime.value = []
        }
        diffTime.value.push(data.d)
        gapTime.value.push(data.d)
        upFlagValue.value = data
        if (downFlagValue.value.d !== 0) {
          const diff = data.d - downFlagValue.value.d
          if (trainData.value.trainId && trainData.value.trainId !== '') {
            if (diff <= patStandard.value.dot.max) {
              // 电间隔
              assembleData(2, diff)
            } else if (diff <= patStandard.value.interval.max) {
              // 小间隔
              assembleData(2, diff)
            } else if (trainData.value.type > 10 && diff <= patStandard.value.gap.max) {
              // 大间隔
              assembleData(2, diff)
            } else {
              // 异常间隔
              assembleData(12, diff)
            }
          }
          handKeyValue.value = -1
          downFlagValue.value = { d: 0 }
        }
      } else {
        // 弹起
        diffTime.value.push(data.d)
        if (gapTime.value.length > 0) {
          gapTime.value = []
        }
        gapTime.value.push(data.d)
        downFlagValue.value = data
        if (upFlagValue.value.d !== 0) {
          const diff = data.d - upFlagValue.value.d
          handKeyValue.value = null
          if (diff <= patStandard.value.dot.min) {
            // console.log("异常点")
            handKeyValue.value = 0
            if (trainData.value.trainId && trainData.value.trainId !== '') {
              assembleData(10, diff)
            }
          } else if (diff >= patStandard.value.dot.min && diff <= patStandard.value.dot.max) {
            // console.log("点")
            handKeyValue.value = 0
            if (trainData.value.trainId && trainData.value.trainId !== '') {
              assembleData(0, diff)
            }
          } else if (diff >= patStandard.value.line.min && diff <= patStandard.value.line.max) {
            // console.log("划")
            handKeyValue.value = 1
            if (trainData.value.trainId && trainData.value.trainId !== '') {
              assembleData(1, diff)
            }
          } else {
            // console.log("异常划")
            handKeyValue.value = 1
            if (trainData.value.trainId && trainData.value.trainId !== '') {
              assembleData(11, diff)
            }
          }
          // stopVoice();
          upFlagValue.value = { d: 0 }
        }
      }
    }
  }
  const { wsOnline, devOnline } = useTraffic(true, handleHandKeysData)
  const init = () => {
    return new Promise(() => {
      if (trainData.value.status === 3) {
        return
      }
      wsBackend = Ws.getInstance()
    })
  }
  onUnmounted(() => {
    wsBackend = null
  })
  watch(audioVolume, () => {
    operation({type:'changeVolume',data:parseFloat(audioVolume.value)/100})
    // diObj.volume = audioVolume.value / 100;
  },{immediate:true})
  // 电子键数据处理
  const handleElectronicKeysData = data => {}
  const downTime = ref(0)
  const upTime = ref(0)
  const handleHandKeysDate = data => {
    return new Promise((resolve, reject) => {
      if (trainData.value.status <= 1) {
        if (data.k === 0) {
          // 按下
          downTime.value = data.d
          if (upTime.value > 0) {
            assembleData(2, downTime.value - upTime.value)
          }
        } else {
          // 弹起
          upTime.value = data.d
          if (downTime.value > 0) {
            const diff = upTime.value - downTime.value
            if (diff >= patStandard.value.dot.min && diff <= patStandard.value.dot.max) {
              // 点
              assembleData(0, diff)
              return
            }
            if (diff >= patStandard.value.line.min && diff <= patStandard.value.line.max) {
              // 线
              assembleData(1, diff)
            }
          }
        }
      }
    })
  }
  const assembleData = (type, time) => {
    wsBackend.sendData(wsCode.SEND_TELEGRAM_TRAIN_LOG, {
      telegramTrainId: trainData.value.trainId,
      type: type,
      value: time
    })
  }
  return {
    handKeyDown,
    patStandard,
    handKeyValue,
    diffTime,
    gapTime,
    audioVolume,
    wsOnline,
    devOnline,
    init
  }
}
