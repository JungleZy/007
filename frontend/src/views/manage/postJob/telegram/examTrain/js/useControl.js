import {ref} from "vue";
import useTraffic from '../../../../../../common/mixin/useTraffic'

export default function (trainData) {
  const wsFlag = ref(true);
  const patKey = ref(null);
  // 电子键数据处理
  const {wsOnline, devOnline,changeCriterion,voiceCode} = useTraffic(false, (data) => {
    if (data.t === 0) {
      return false
    }
    if (data.d.length !== 0) {
      data.d.forEach(e => {
        patKey.value = e.toString()
      })
    }
  })
  return {
    wsOnline,
    devOnline,
    patKey,
    changeCriterion,
    voiceCode
  }
}