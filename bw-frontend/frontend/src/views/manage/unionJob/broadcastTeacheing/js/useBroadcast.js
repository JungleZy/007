import { ref, onMounted } from 'vue'
import moment from 'moment'
import { letterKey, numberKey } from '../../../../../components/preJob/telexTrain/js/enum'
import { getAllStudent, addRoom, findRoom } from '../../../../../common/api/broaddcastTeacheingApi'
import { message } from 'ant-design-vue'
import useMorse from '../../../../../common/mixin/useMorse.js'
import { useRouter,useRoute } from 'vue-router'
export default function useBroadcast() {
  const router = useRouter()
  const route = useRoute()
  const { baseCode, morseCode } = useMorse()
  const drillPath = ref('')
  router.getRoutes().forEach(r => {
    if (r.name === 'BroadcastTeachTrain'&&route.name!=='ReceiveZuXunList') {
      drillPath.value = r.path
    }
    else if(r.name === 'ReceiveZuXunTrain'&&route.name==='ReceiveZuXunList'){
      drillPath.value = r.path
    }
  })
  const intoTrainRoom = item => {
    console.log(drillPath.value)
    router.push({ path: drillPath.value, query: { id: item.id, createUserId: item.createUserId } })
  }
  return {
    intoTrainRoom
  }
}
