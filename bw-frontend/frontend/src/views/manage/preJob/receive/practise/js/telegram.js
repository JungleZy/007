import { message } from 'ant-design-vue'
import { useRouter } from 'vue-router'
import { ref, reactive, toRaw, onMounted, toRefs, watch, provide, inject } from 'vue'
import useMorse from '../../../../../../common/mixin/useMorse.js'
import { timeFormatInfo } from '../../../../../../common/utils/Utils.js'
import { getBasicSetting, saveBasicSetting, saveTelegramTrain, findReceiveTrainTotal, findPrevReceiveTrainInfo, getPreReceiveDotRate } from '../../../../../../common/api/ReceiveApi.js'
import operationMorseVoice from "../../../../../../common/utils/voice/operationMorseVoice";

export default function telegramList() {
  const { baseCode, morseCode } = useMorse()
  const router = useRouter()
  const drillPath = ref('')
  const drillBasicPath = ref('')
  const drillKochPath = ref('')
  router.getRoutes().forEach(r => {
    if (r.name === 'ReceiveTrain') {
      drillPath.value = r.path
    }
    if (r.name === 'ReceiveBasicTrain') {
      drillBasicPath.value = r.path
    }
    if (r.name === 'ReceiveKochTrain') {
      drillKochPath.value = r.path
    }
  })
  const {operation} = operationMorseVoice()

  const loading = ref(false)
  const addDrillModal = ref(false)
  const trainData = ref({
    type: 1,
    speed: null,
    baoDi: [
      { type: 0, code: 'A_Z', text: '字码报', num: 1, bw: 100, numberType: true },
      { type: 1, code: '0_9', text: '数码报', num: 1, bw: 100, numberType: false },
      { type: 2, code: 'mix', text: '混合报', num: 1, bw: 100, numberType: true },
      { type: 11, code: 'dot', text: '点报', num: 1, bw: 100, numberType: true },
      { type: 12, code: 'line', text: '划报', num: 1, bw: 100, numberType: true },
      { type: 14, code: 'd_l', text: '点划连接报', num: 1, bw: 100, numberType: true }
    ],
    isRandom: true
  })
  const formData = ref({
    name: '',
    rate: 0,
    type: 1,
    codeShort: 1,
    isLowRate: false,
    codeMessageBody: []
  })
  const totalList = ref([])
  const prevTrain = ref({
    status: 0,
    id: '',
    ring: '1',
    stage: 1
  })

  const playRate = ref(null)
  const basicTrainDeployModal = ref(false)
  const basicDeployData = ref([])
  const basicSpeed = ref({
    dotTime: 80
  })

  onMounted(() => {
    findPrevTrainMsg(22)
    findReceiveTrainTotal().then(res => {
      let hour, min, sec
      if (res.code === 200) {
        res.data.forEach(item => {
          hour = Math.floor((item.totalTime / 1000 / 60 / 60) % 24)
          min = Math.floor((item.totalTime / 1000 / 60) % 60)
          sec = Math.floor((item.totalTime / 1000) % 60)
          item.totalTime = (hour >= 10 ? hour : '0' + hour) + '：' + (min >= 10 ? min : '0' + min) + '：' + (sec >= 10 ? sec : '0' + sec)
          item['sort'] = item.type === 0 ? 0 : item.type === 2 ? 1 : 2
        })
        totalList.value = res.data.sort((x, y) => x.sort - y.sort)
      } else {
        message.error(res.message)
      }
    })
  })

  /**
   * 获取上一次训练信息
   */
  const findPrevTrainMsg = type => {
    findPrevReceiveTrainInfo({ type: type ? type : formData.value.type }).then(res => {
      if (res.code === 200) {
        if (res.data) {
          prevTrain.value.status = res.data.status
          prevTrain.value.id = res.data.id
          prevTrain.value.stage = res.data.schedule ? res.data.schedule : 1
          prevTrain.value.ring = res.data.mark ? res.data.mark : '1'
        } else {
          prevTrain.value.status = 0
          prevTrain.value.id = ''
          prevTrain.value.stage = 1
          prevTrain.value.ring = '1'
        }
      }
    })
  }

  /**
   * 新增训练
   */
  const addDrillModalInfo = type => {
    if (type === 0) {
      router.push({ path: drillBasicPath.value })
    } else if (type === 1) {
      router.push({ path: drillKochPath.value })
    } else {
      addDrillModal.value = true
      formData.value.type = 1
      getBasicSettingInfo()
      findPrevTrainMsg()
    }
  }

  /**
   * 关闭弹窗
   */
  const cancelTrainModal = () => {
    trainData.value = {
      type: 11,
      speed: null,
      baoDi: [
        { type: 0, code: 'A_Z', text: '字码报', num: 1, bw: 100, numberType: true },
        { type: 1, code: '0_9', text: '数码报', num: 1, bw: 100, numberType: true },
        { type: 2, code: 'mix', text: '混合报', num: 1, bw: 100, numberType: true },
        { type: 11, code: 'dot', text: '点报', num: 1, bw: 100, numberType: true },
        { type: 12, code: 'line', text: '划报', num: 1, bw: 100, numberType: true },
        { type: 14, code: 'd_l', text: '点划连接报', num: 1, bw: 100, numberType: true }
      ],
      isRandom: true
    }
  }

  /**
   * 选择播报码率
   */
  const selectRateInfo = () => {
    if (playRate.value > 0) {
      formData.value.rate = playRate.value
    } else {
      formData.value.rate = 60
    }
  }

  /**
   * 生成训练报底和报文数据
   * @param data
   */
  const generteTrainData = data => {
    let res = [],
      code = [],
      key = '',
      word = [],
      wordValue = [],
      len = 0
    for (let bd of data.baoDi) {
      if (bd.type === formData.value.type) {
        if (!bd.num || bd.num === 0) {
          message.error('报底数量不能为空！')
          loading.value = false
          return false
        }
        if (!bd.bw || bd.bw === 0) {
          message.error('报文数量不能为空！')
          loading.value = false
          return false
        }
        code = baseCode[bd.code] //数字
        len = code.length
        let bbw = bd.bw * 4
        for (let i = 0; i < bd.num; i++) {
          for (let j = 0; j < bbw; j++) {
            if (data.isRandom) {
              if (bd.type > 10) {
                word = []
                wordValue = []
                for (let k = 0; k < 4; k++) {
                  key = code[parseInt(Math.random() * len)]
                  word.push(key)
                  wordValue.push(morseCode[bd.numberType ? 'mix' : 'short'][key].value.split(''))
                }
                res.push({
                  key: JSON.stringify(word),
                  value: JSON.stringify(wordValue),
                  sort: res.length
                })
              } else {
                key = code[parseInt(Math.random() * len)]
                res.push({
                  key: key,
                  value: '[' + morseCode[bd.numberType ? 'mix' : 'short'][key].value.split('') + ']',
                  sort: res.length
                })
              }
            } else {
              key = code[j < len ? j : j % len]
              res.push({
                key: key,
                value: '[' + morseCode[bd.numberType ? 'mix' : 'short'][key].value.split('') + ']',
                sort: res.length
              })
            }
          }
        }
        formData.value.codeShort = bd.numberType ? 0 : 1
      }
    }
    if (!formData.value.rate || formData.value.rate === '') {
      message.error('播报码率不能为空！')
      loading.value = false
      return false
    }
    return res
  }

  /**
   * 生成训练
   */
  const createDrillInfo = () => {
    if (loading.value) return false
    loading.value = true
    formData.value.name = '收报训练-' + timeFormatInfo(new Date().getTime(), 'string')
    formData.value.codeMessageBody = generteTrainData(trainData.value)
    if (formData.value.isLowRate) {
      formData.value.rate = 35
    }
    formData.value.isLowRate = formData.value.isLowRate ? 1 : 0
    if (!formData.value.codeMessageBody) return false
    saveTelegramTrain(formData.value).then(res => {
      loading.value = false
      if (res.code === 200) {
        message.success('生成训练成功！')
        router.push({ path: drillPath.value, query: { id: res.data.id } })
      } else {
        message.error(res.message)
      }
    })
  }

  /**
   * 开始练习
   */
  const startTrain = () => {
    router.push({ path: drillPath.value, query: { id: prevTrain.value.id } })
  }

  /**
   * 开始基础练习配置
   */
  const basicDeploy = () => {
    basicTrainDeployModal.value = true
    getBasicSettingInfo()
  }

  /**
   * 获取基础练习配置
   */
  const getBasicSettingInfo = () => {
    getBasicSetting().then(res => {
      if (res.code === 200) {
        basicDeployData.value = []
        res.data.map((item, i) => {
          if (i === 1) {
            formData.value.rate = item.rate
            playRate.value = item.rate
          }
          basicDeployData.value.push({ id: item.id, name: item.type, speed: item.rate, text: item.text })
        })
      }
    })
    getPreReceiveDotRate().then(res => {
      if (res.code === 200) {
        basicSpeed.value.dotTime = res.data
      }
    })
  }

  /**
   * 新增码率
   */
  const createBasicNorm = () => {
    basicDeployData.value.push({ name: '', speed: '', text: '' })
  }

  /**
   * 删除码率
   * @param index
   */
  const closeBasicNorm = index => {
    basicDeployData.value = basicDeployData.value.filter((item, i) => i !== index)
  }

  /**
   * 基础练习数据处理
   */
  const handleBasicData = () => {
    let res = [],
      flag = false
    for (let item of basicDeployData.value) {
      if (item.name === '' || item.speed === '' || item.text === '') {
        flag = true
      }
      res.push({
        type: item.name,
        rate: item.speed,
        text: item.text
      })
    }
    if (flag) {
      return false
    } else {
      return res
    }
  }

  /**
   * 保存基础练习配置
   */
  const saveDeploy = () => {
    let data = handleBasicData()
    if (!data) {
      message.warning('配置输入框不能为空！')
      return false
    }
    saveBasicSetting({
      paramList: data,
      dotStandardTime: basicSpeed.value.dotTime
    }).then(res => {
      loading.value = false
      if (res.code === 200) {
        message.success('播报码率配置成功！')
        basicTrainDeployModal.value = false
      } else {
        message.error(res.message)
      }
    })
  }

  const auditionInfo = () => {
    operation({type:'changeCriterion',data:parseInt(basicSpeed.value.dotTime)})
    operation({type:'message',data:{
      numType:'long', data: ["0"]
    }})
  }

  return {
    loading,
    addDrillModal,
    trainData,
    formData,
    totalList,
    prevTrain,
    playRate,
    basicTrainDeployModal,
    basicDeployData,
    addDrillModalInfo,
    cancelTrainModal,
    selectRateInfo,
    createDrillInfo,
    startTrain,
    basicDeploy,
    createBasicNorm,
    closeBasicNorm,
    saveDeploy,
    findPrevTrainMsg,
    basicSpeed,
    auditionInfo
  }
}
