import { message, Modal } from 'ant-design-vue'
import { ref, onMounted } from 'vue'
import { getRoomDetail } from '../../../../../common/api/broaddcastTeacheingApi.js'
import { useRoute } from 'vue-router'

export default function useBroadcastTrain() {
  const trainTimeRef = ref(null)
  const route = useRoute()
  const trainData = ref({
    status: 0
  })

  onMounted(() => {
    if (route.query.id && route.query.id !== '') {
      findTrainDataInfo()
    }
  })

  const findTrainDataInfo = () => {
    getRoomDetail({
      roomgId: Number(route.query.id)
    }).then(res => {
      if (res.code === 200) {
        res.data.content = JSON.parse(res.data.content)
        // res.data.mainSignal = JSON.parse(res.data.mainSignal);
        // res.data.interferenceSignal = JSON.parse(res.data.interferenceSignal);
        trainData.value = res.data
        trainData.value['status'] = trainData.value.stats
        trainData.value['pag'] = Math.ceil(trainData.value.content.length / 100)
        trainData.value['currPag'] = 1
        trainData.value['currIndex'] = 0
      }
    })
  }

  /**
   * 切换分页
   * @param type
   */
  const pageTurn = type => {
    if (type == 'next') {
      if (trainData.value.currPag == trainData.value.pag) {
        return false
      }
      trainData.value.currPag++
    } else {
      if (trainData.value.currPag == 1) {
        return false
      }
      trainData.value.currPag--
    }
  }

  /**
   * 开启训练
   */
  const openTrainInfo = () => {}

  /**
   * 关闭训练
   */
  const closeTrainInfo = () => {}

  return {
    trainTimeRef,
    trainData,
    openTrainInfo,
    closeTrainInfo,
    pageTurn
  }
}
