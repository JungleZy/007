import { message, Modal } from 'ant-design-vue'
import moment from 'moment'
import { ref, reactive, toRaw, onMounted, toRefs, watch, provide, inject } from 'vue'
import { findPrePatTrainTotal, findPrevPatTrainInfo, saveTexPatTrain } from '../../../../../../common/api/TelegramApi.js'
import { numberKey, letterKey } from '../../../../../../components/preJob/telexTrain/js/enum.js'
import { useRouter, useRoute } from 'vue-router'

export default function telegramList(addDrillModal) {
  const router = useRouter()
  const route = useRoute()
  const trainData = ref({
    type: 0,
    count: 100,
    prev: {
      status: 0,
      id: ''
    },
    total: []
  })
  const content = ref([])
  onMounted(() => {
    init()
  })
  const init = () => {
    let hour, min, sec
    findPrePatTrainTotal().then(res => {
      if (res.code === 200) {
        res.data.forEach(item => {
          hour = Math.floor((item.totalTime / 60 / 60) % 24)
          min = Math.floor((item.totalTime / 60) % 60)
          sec = Math.floor(item.totalTime % 60)
          item.totalTime = (hour >= 10 ? hour : '0' + hour) + '：' + (min >= 10 ? min : '0' + min) + '：' + (sec >= 10 ? sec : '0' + sec)
        })
        trainData.value.total = res.data.sort((a, b) => a.type - b.type)
      }
    })
  }

  const selectType = () => {
    // addDrillModal.value = true;
    if (trainData.value.type === 1) {
      initContent(numberKey, 10)
    } else if (trainData.value.type === 2) {
      initContent(letterKey, 26)
    } else {
      let arr = [...numberKey, ...letterKey]
      initContent(arr, 36)
    }
    // findPrevTrainMsg();
  }

  const findPrevTrainMsg = () => {
    findPrevPatTrainInfo({ type: trainData.value.type - 1 }).then(res => {
      if (res.code === 200 && res.data) {
        trainData.value.prev.status = res.data.status
        trainData.value.prev.id = res.data.id
      } else {
        trainData.value.prev.status = 0
        trainData.value.prev.id = ''
      }
    })
  }

  // 生成随机报文
  const initContent = (keyboard, num) => {
    let ctAll = []
    for (let i = 0; i < trainData.value.count; i++) {
      let ct = ''
      for (let j = 0; j < 4; j++) {
        const mat = Math.floor(Math.random() * num)
        ct += keyboard[mat].text2 ? keyboard[mat].text2 : keyboard[mat].text
      }
      ctAll.push({
        value: '',
        text: ct.toString(),
        type: true,
        isFocus: false
      })
    }
    content.value = ctAll
    addTelexTrain()
  }

  // 新增连贯训练
  const addTelexTrain = () => {
    // addDrillModal.value = false
    // selectType()
    let time = moment().format('YYMMDDhhmmss')
    let title = ''
    switch (trainData.value.type) {
      case 1:
        title = '数字连贯-' + time
        break
      case 2:
        title = '字母连贯-' + time
        break
      case 3:
        title = '组合连贯-' + time
        break
    }
    const data = {
      content: JSON.stringify(content.value),
      title: title,
      type: trainData.value.type - 1,
      totalNumber: trainData.value.count
    }
    saveTexPatTrain(data).then(res => {
      if (res.code == 200) {
        // message.success("生成训练成功！")
        router.push({
          path: route.matched[4].path + '/telexTrain',
          query: { id: res.data.id }
        })
      } else {
        message.error('生成训练失败！')
      }
    })
  }

  return {
    trainData,
    selectType,
    addTelexTrain
  }
}
