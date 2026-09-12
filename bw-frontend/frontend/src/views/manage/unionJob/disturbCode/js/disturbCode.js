import {ref, onMounted} from 'vue'
import moment from 'moment'
import useMorse from '../../../../../common/mixin/useMorse.js'
import {message} from 'ant-design-vue'
import {addDisturbCodeTrain, reportIntoTrainRoom} from '../../../../../common/api/UnionApi.js'
import {useRouter} from 'vue-router'

export default function disturbCode(selectCable) {
  const addTrainModal = ref(false)
  const {baseCode, morseCode} = useMorse()
  const router = useRouter()
  const drillPath = ref('')
  const isRandom = ref(true)
  const isAverage = ref(false)
  const userInfo = ref(JSON.parse(window.localStorage.getItem('userInfo')))
  const formData = ref({
    roomName: '',
    bdType: 2,
    bwType: 1,
    bwCount: 100,
    content: [],
    isCable: 0,
    startPage: 1,
    mainSignal: {
      1: {type: '1', rate: 80, volume: 60, fre: 1000, status: 0, checked: true},
      2: {type: '2', rate: 90, volume: 60, fre: 1000, status: 0, checked: false},
      3: {type: '3', rate: 100, volume: 60, fre: 1000, status: 0, checked: false}
    },
    interferenceSignal: {
      1: {type: '1', rate: 100, volume: 60, fre: 0, cont: [], status: 0},
      2: {type: '2', rate: 100, volume: 60, fre: 0, cont: [], status: 0},
      3: {type: '3', rate: 100, volume: 60, fre: 0, cont: [], status: 0},
      4: {type: '4', rate: 100, volume: 60, fre: 0, cont: [], status: 0},
      5: {type: '5', rate: 100, volume: 60, fre: 0, cont: [], status: 0},
      6: {type: '6', rate: 100, volume: 60, fre: 0, cont: [], status: 0}
    }
  })

  router.getRoutes().forEach(r => {
    if (r.name === 'DisturbCodeTrain') {
      drillPath.value = r.path
    }
  })

  /**
   * 新增弹窗
   */
  const addTrainModalInfo = () => {
    addTrainModal.value = true
    formData.value.roomName = '抗干扰收报-' + moment().format('YYMMDDhhmmss')
  }

  /**
   * 生成训练
   */
  const addAntiDisturbTrain = () => {
    if (!formData.value.bwCount || formData.value.bwCount === 0) {
      message.error('报文组数不能为空！')
      return false
    }
    if (!formData.value.mainSignal['1'].checked && !formData.value.mainSignal['2'].checked && !formData.value.mainSignal['3'].checked) {
      message.error('线路配置至少选择一项！')
      return false
    }
    generateDisturbSignal()
    addDisturbCodeTrain({
      roomName: formData.value.roomName,
      bdType: formData.value.bdType,
      bwType: formData.value.bwType == 1 && formData.value.numberType ? 2 : formData.value.bwType,
      bwCount: formData.value.bwCount,
      isRandom: isRandom.value ? 1 : 0,
      content: JSON.stringify(formData.value.content),
      mainSignal: JSON.stringify(formData.value.mainSignal),
      interferenceSignal: JSON.stringify(formData.value.interferenceSignal),
      isCable: formData.value.isCable,
      cableId: formData.value.cableId,
      startPage: formData.value.startPage
    }).then(res => {
      addTrainModal.value = false
      if (res.code === 200) {
        message.success('生成训练成功！')
        router.push({path: drillPath.value, query: {id: res.data}})
      } else {
        message.error(res.message)
      }
    })
  }

  /**
   * 生成报文干扰信号
   */
  const generateDisturbSignal = () => {
    let item,
        code,
        len,
        type = 'short',
        key = '',
        word = [],
        val = []
    for (let i in formData.value.interferenceSignal) {
      item = formData.value.interferenceSignal[i]
      item.fre = parseInt(Math.random() * 17 + 4) * 100
      type = i == '6' ? 'mix' : i == '5' ? 'letter' : i == '4' ? 'long' : 'short'
      code = baseCode[i == '5' ? 'A_Z' : i == '6' ? 'mix' : '0_9']
      len = code.length
      for (let j = 0; j < 20; j++) {
        word = []
        val = []
        for (let k = 0; k < 4; k++) {
          key = code[parseInt(Math.random() * len)]
          word.push(key)
          val.push(morseCode[type][key].value.split(''))
        }
        item.cont.push({
          key: word,
          value: val
        })
      }
    }
  }

  /**
   * 关闭弹窗
   */
  const cancelTrainModal = () => {
    addTrainModal.value = false
    isRandom.value = true
    isAverage.value = false
    formData.value = {
      roomName: '',
      bdType: 2,
      bwType: 1,
      bwCount: 100,
      numberType: false,
      content: [],
      isCable: 0,
      mainSignal: {
        1: {type: 1, rate: 80, volume: 60, fre: 1000, status: 0, checked: true},
        2: {type: 2, rate: 90, volume: 60, fre: 1000, status: 0, checked: false},
        3: {type: 3, rate: 100, volume: 60, fre: 1000, status: 0, checked: false}
      },
      interferenceSignal: {
        1: {rate: 100, volume: 60, fre: 1000, cont: [], status: 0},
        2: {rate: 100, volume: 60, fre: 1000, cont: [], status: 0},
        3: {rate: 100, volume: 60, fre: 1000, cont: [], status: 0},
        4: {rate: 100, volume: 60, fre: 1000, cont: [], status: 0},
        5: {rate: 100, volume: 60, fre: 1000, cont: [], status: 0},
        6: {rate: 100, volume: 60, fre: 1000, cont: [], status: 0}
      }
    }
  }

  /**
   * 进入训练房间
   * @param item
   */
  const intoTrainRoom = async item => {
    try {
      if (item.createUserId != userInfo.value.id && item.stats < 2) {
        const response = await reportIntoTrainRoom({roomId: Number(item.id)})
        if (response?.code !== 200) throw new Error(response?.message || '加入训练失败')
      }
      router.push({path: drillPath.value, query: {id: item.id}})
    } catch (error) {
      message.error(error.message || '加入训练失败，请重试')
    }
  }

  return {
    addTrainModal,
    formData,
    isRandom,
    isAverage,
    cancelTrainModal,
    addAntiDisturbTrain,
    intoTrainRoom,
    addTrainModalInfo
  }
}
