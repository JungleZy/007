import {ref, watch} from 'vue'
import useTraffic, {handKeyEvents} from '../../../../../../../common/mixin/useTraffic'

export default function (trainData) {
  const patStandard = ref({dot: 80, line: 240, codeGap: 80, wordGap: 240, groupGap: 400})
  const initFloat = ref(50)
  const audioVolume = ref(60)
  const {handKeyDown, onKey, receive, reset} = handKeyEvents(
    () => trainData.value.process !== 0 && trainData.value.status === 1,
    () => patStandard.value.dot * (1 + initFloat.value / 100),
    () => patStandard.value.line * (1 + initFloat.value / 100)
  )
  const {wsOnline, devOnline} = useTraffic(true, receive)
  watch(() => trainData.value.attempt, reset, {flush: 'sync'})
  return {handKeyDown, patStandard, initFloat, onKey, audioVolume, wsOnline, devOnline}
}
