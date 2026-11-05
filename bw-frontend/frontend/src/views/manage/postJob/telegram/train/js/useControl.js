import {ref} from 'vue'
import useTraffic, {handKeyEvents} from '../../../../../../common/mixin/useTraffic'
import {DEFAULT_HAND_KEY_STANDARD, DEFAULT_HAND_KEY_SKEW, handKeyThresholds} from '../../../../../../common/utils/handKeyTiming.js'

export default function (trainData) {
  const patStandard = ref({...DEFAULT_HAND_KEY_STANDARD})
  const initFloat = ref(DEFAULT_HAND_KEY_SKEW)
  const audioVolume = ref(60)
  const {handKeyDown, onKey, receive} = handKeyEvents(
    () => trainData.value.process !== 0 && trainData.value.status === 1,
    () => handKeyThresholds(patStandard.value, initFloat.value).dotDash,
    () => patStandard.value.line * (1 + initFloat.value / 100)
  )
  const {wsOnline, devOnline} = useTraffic(true, receive)
  return {handKeyDown, patStandard, initFloat, onKey, audioVolume, wsOnline, devOnline}
}
