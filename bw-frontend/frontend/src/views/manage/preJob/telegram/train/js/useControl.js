import {ref, watch} from 'vue'
import useTraffic, {handKeyEvents} from '../../../../../../common/mixin/useTraffic'
import {Ws, wsCode} from '../../../../../../common/ws/Ws.js'
import operationMorseVoice from '../../../../../../common/utils/voice/operationMorseVoice'

export default function (trainData) {
  const patStandard = ref({dot: {min: 1, max: 80}, line: {min: 81, max: 240}, interval: {min: 81, max: 240}, gap: {min: 241, max: 400}})
  const initFloat = ref(50)
  const audioVolume = ref(60)
  let backend
  const {operation} = operationMorseVoice()
  watch(audioVolume, value => operation({type: 'changeVolume', data: Number(value) / 100}), {immediate: true})
  const {handKeyDown, onKey, receive} = handKeyEvents(
    () => trainData.value.status <= 1,
    () => patStandard.value.dot.max,
    () => patStandard.value.line.max,
    (type, value) => { if (backend && trainData.value.trainId) backend.sendData(wsCode.SEND_TELEGRAM_TRAIN_LOG, {telegramTrainId: trainData.value.trainId, type, value}) }
  )
  const {wsOnline, devOnline} = useTraffic(true, receive)
  const init = () => { backend = Ws.getInstance() }
  return {handKeyDown, patStandard, initFloat, onKey, audioVolume, wsOnline, devOnline, init}
}
