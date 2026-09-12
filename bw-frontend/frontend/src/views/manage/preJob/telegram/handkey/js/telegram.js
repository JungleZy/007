import { message } from 'ant-design-vue'
import { useRouter } from 'vue-router'
import useMorse from '../../../../../../common/mixin/useMorse.js'
import { ref, reactive, toRaw, onMounted, toRefs, watch, provide, inject } from 'vue'
import { timeFormatInfo } from '../../../../../../common/utils/Utils.js'
import { saveTelegramTrain, saveBasicSetting, getBasicSetting, findHandKeyTrainTotal, findPrevHandKeyTrainInfo } from '../../../../../../common/api/TelegramApi.js'
import { log } from '@antv/g2plot/lib/utils/invariant.js'
import { parseTelegramBasicSettings, settingMilliseconds } from '../../../../../../common/utils/telegramSettings.js'

export default function telegramList() {
  const router = useRouter()
  const drillPath = ref('')
  router.getRoutes().forEach(r => {
    if (r.name === 'HandKeyTrain') {
      drillPath.value = r.path
    }
  })

  const { baseCode } = useMorse()
  const loading = ref(false)
  const addDrillModal = ref(false)
  const trainData = ref({
    way: -1,
    type: 1,
    baoDi: [
      { type: 0, code: 'A_Z', text: '字码报', num: 1, bw: 100, numberType: true },
      { type: 1, code: '0_9', text: '数码报', num: 1, bw: 100, numberType: false },
      { type: 2, code: 'mix', text: '混合报', num: 1, bw: 100, numberType: true },
      { type: 11, code: 'dot', text: '点报', num: 1, bw: 100, numberType: true },
      { type: 12, code: 'line', text: '划报', num: 1, bw: 100, numberType: true },
      { type: 14, code: 'd_l', text: '点划连接报', num: 1, bw: 100, numberType: true }
    ],
    isRandom: true,
    interval: [
      { type: 0, text: '点区间', min: 10, max: 80, scale: 1 },
      { type: 1, text: '划区间', min: 81, max: 240, scale: 3 },
      { type: 2, text: '码间隔', min: 80, max: 240, scale: 3 },
      { type: 3, text: '组间隔', min: 240, max: 400, scale: 5 }
    ]
  })
  const formData = ref({
    train: {
      title: '',
      type: 1,
      totalNumber: 0,
      rateDotMinMs: 10,
      rateDotMaxMs: 80,
      rateLineMinMs: 81,
      rateLineMaxMs: 240,
      rateIntervalMinMs: 80,
      rateIntervalMaxMs: 240,
      bigIntervalMinMs: 240,
      bigIntervalMaxMs: 400
    },
    trainFloors: []
  })
  const totalList = ref([])
  const prevTrain = ref({
    status: 0,
    id: ''
  })

  const basicTrainDeployModal = ref(false)
  const basicLabTitle = ref({
    dot: [10, 80],
    line: [80, 560]
  })
  const basicDeployData = ref([
    {
      name: '异常',
      dot: { min: '<10', max: '>80' },
      line: { min: '<80', max: '>560' },
      text: '您还需要多加练习！',
      type: 0,
      prune: false
    },
    {
      name: '过粗',
      dot: { min: '45', max: '80' },
      line: { min: '200', max: '560' },
      text: '合格，请继续努力！',
      type: 1,
      prune: false
    },
    {
      name: '完美',
      dot: { min: '25', max: '45' },
      line: { min: '160', max: '200' },
      text: '完美，请继续保持！',
      type: 2,
      prune: false
    },
    {
      name: '过虚',
      dot: { min: '10', max: '25' },
      line: { min: '80', max: '160' },
      text: '合格，请继续努力！',
      type: 3,
      prune: false
    }
  ])

  onMounted(() => {
    getBasicSettingInfo()
    findHandKeyTrainTotal().then(res => {
      let hour, min, sec
      if (res.code === 200) {
        res.data.forEach(item => {
          hour = Math.floor((item.totalTime / 1000 / 60 / 60) % 24)
          min = Math.floor((item.totalTime / 1000 / 60) % 60)
          sec = Math.floor((item.totalTime / 1000) % 60)
          item.totalTime = (hour >= 10 ? hour : '0' + hour) + '：' + (min >= 10 ? min : '0' + min) + '：' + (sec >= 10 ? sec : '0' + sec)
        })
        totalList.value = res.data
      } else {
        message.error(res.message)
      }
    })
  })

  /**
   * 新增训练
   */
  const addDrillModalInfo = type => {
    formData.value.train.type = type === 1 ? 11 : 1
    trainData.value.way = type === 0 ? 0 : type === 1 ? 1 : -1
    if (type === 2) {
      let path = ''
      router.getRoutes().forEach(r => {
        if (r.name === 'HandKeyBasicTrain') {
          path = r.path
        }
      })
      router.push({ path: path })
    } else {
      addDrillModal.value = true
      handlePatDeployData()
      if (trainData.value.way > -1) {
        findPrevTrainMsg()
      }
    }
  }

  /**
   * 获取上一次训练信息
   */
  const findPrevTrainMsg = () => {
    findPrevHandKeyTrainInfo({ type: formData.value.train.type }).then(res => {
      if (res.code === 200) {
        if (res.data) {
          prevTrain.value.status = res.data.status
          prevTrain.value.id = res.data.id
        } else {
          prevTrain.value.status = 0
          prevTrain.value.id = ''
        }
      }
    })
  }

  /**
   * 关闭弹窗
   */
  const cancelTrainModal = () => {
    trainData.value = {
      way: -1,
      type: 11,
      baoDi: [
        { type: 0, code: 'A_Z', text: '字码报', num: 1, bw: 100, numberType: true },
        { type: 1, code: '0_9', text: '数码报', num: 1, bw: 100, numberType: true },
        { type: 2, code: 'mix', text: '混合报', num: 1, bw: 100, numberType: true },
        { type: 11, code: 'dot', text: '点报', num: 1, bw: 100, numberType: true },
        { type: 12, code: 'line', text: '划报', num: 1, bw: 100, numberType: true },
        { type: 14, code: 'd_l', text: '点划连接报', num: 1, bw: 100, numberType: true }
      ],
      isRandom: true,
      interval: [
        { type: 0, text: '点区间', min: 10, max: 80, scale: 1 },
        { type: 1, text: '划区间', min: 81, max: 240, scale: 7 },
        { type: 2, text: '码间隔', min: 80, max: 240, scale: 4 },
        { type: 3, text: '组间隔', min: 240, max: 400, scale: 10 }
      ]
    }
    basicDeployData.value = [
      {
        name: '异常',
        dot: { min: '<10', max: '>80' },
        line: { min: '<80', max: '>560' },
        text: '您还需要多加练习！',
        type: 0,
        prune: false
      },
      {
        name: '过粗',
        dot: { min: '45', max: '80' },
        line: { min: '200', max: '560' },
        text: '合格，请继续努力！',
        type: 1,
        prune: false
      },
      {
        name: '完美',
        dot: { min: '25', max: '45' },
        line: { min: '160', max: '200' },
        text: '完美，请继续保持！',
        type: 2,
        prune: false
      },
      {
        name: '过虚',
        dot: { min: '10', max: '25' },
        line: { min: '80', max: '160' },
        text: '合格，请继续努力！',
        type: 3,
        prune: false
      }
    ]
  }

  /**
   * 处理拍发配置数据
   */
  const handlePatDeployData = changed => {
    if (changed && changed.type > 0 && formData.value.train.rateDotMaxMs > 0) {
      changed.scale = Number(changed.max) / formData.value.train.rateDotMaxMs
    }
    for (let deploy of trainData.value.interval) {
      if (deploy.type === 0) {
        deploy.min = Math.floor(Number(deploy.max) / 2)
        formData.value.train.rateDotMinMs = deploy.min
        formData.value.train.rateDotMaxMs = Number(deploy.max)
      }
      if (deploy.type === 1) {
        deploy.min = formData.value.train.rateDotMaxMs + 1
        deploy.max = Math.round(formData.value.train.rateDotMaxMs * deploy.scale)
        formData.value.train.rateLineMinMs = deploy.min
        formData.value.train.rateLineMaxMs = deploy.max
      }
      if (deploy.type === 2) {
        deploy.min = formData.value.train.rateDotMaxMs
        deploy.max = Math.round(formData.value.train.rateDotMaxMs * deploy.scale)
        formData.value.train.rateIntervalMinMs = deploy.min
        formData.value.train.rateIntervalMaxMs = deploy.max
      }
      if (deploy.type === 3) {
        deploy.min = formData.value.train.rateIntervalMaxMs
        deploy.max = Math.round(formData.value.train.rateDotMaxMs * deploy.scale)
        formData.value.train.bigIntervalMinMs = deploy.min
        formData.value.train.bigIntervalMaxMs = deploy.max
      }
    }
  }

  /**
   * 生成训练报底和报文数据
   * @param data
   */
  const generteTrainData = data => {
    let res = [],
      bwArr = [],
      code = [],
      word = [],
      len = 0
    formData.value.train.totalNumber = 0
    formData.value.train.title = '手键拍发训练-' + timeFormatInfo(new Date().getTime(), 'string')
    for (let bd of data.baoDi) {
      if (bd.type === formData.value.train.type) {
        if (!bd.bw || bd.bw === 0) {
          message.error('报文数量不能为空！')
          loading.value = false
          return false
        }
        bd.num = Math.ceil(bd.bw / 100)
        formData.value.train.totalNumber += parseInt(bd.bw)
        code = baseCode[bd.code]
        len = code.length
        bwArr = []
        for (let j = 0; j < bd.bw; j++) {
          if (data.isRandom) {
            if (bd.type > 10) {
              word = []
              for (let k = 0; k < 4; k++) {
                word.push(code[parseInt(Math.random() * len)])
              }
              bwArr.push({
                moresKey: JSON.stringify(word),
                moresValue: '[[],[],[],[]]'
              })
            } else {
              bwArr.push({
                moresKey: code[parseInt(Math.random() * len)],
                moresValue: '[]'
              })
            }
          } else {
            if (bd.type > 10) {
              word = []
              for (let k = 0; k < 4; k++) {
                word.push(code[(((j * 4) % code.length) + k) % code.length])
              }
              bwArr.push({
                moresKey: JSON.stringify(word),
                moresValue: '[[],[],[],[]]'
              })
            } else {
              bwArr.push({
                moresKey: code[j < len ? j : j % len],
                moresValue: '[]'
              })
            }
          }

          if (bwArr.length >= 100 || j == bd.bw - 1) {
            res.push({
              floor: {
                type: bd.type,
                numberType: bd.numberType ? 0 : 1
              },
              floorContents: bwArr
            })
            bwArr = []
          }
        }
      }
    }
    return res
  }

  /**
   * 生成训练
   */
  const createDrillInfo = () => {
    if (loading.value) return false
    loading.value = true
    if (trainData.value.way < 0) {
      let path = ''
      router.getRoutes().forEach(r => {
        if (r.name === 'HandKeyBasicTrain') {
          path = r.path
        }
      })
      loading.value = false
      router.push({ path: path })
    } else {
      formData.value.trainFloors = generteTrainData(trainData.value)
      // return
      if (!formData.value.trainFloors) return false
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
  }

  /**
   * 开始训练
   */
  const startTrain = () => {
    router.push({ path: drillPath.value, query: { id: prevTrain.value.id } })
  }

  /**
   * 获取基础练习配置
   */
  const getBasicSettingInfo = async () => {
    if (loading.value) return
    loading.value = true
    try {
      const res = await getBasicSetting()
      if (res.code !== 200) throw new Error(res.message || '基础配置加载失败')
      const rows = parseTelegramBasicSettings(res.data)
      basicDeployData.value = rows.filter(item => item.key === '0').map(item => {
        const line = rows.find(row => row.key === '1' && row.value.type === item.value.type).value
        return {
          name: item.value.name,
          dot: { min: item.value.min, max: item.value.max },
          line: { min: line.min, max: line.max },
          text: item.value.msg,
          type: item.value.type,
          prune: item.value.type > 3
        }
      })
      changeBasicValue()
    } catch (error) {
      message.error(`${error.message || '基础配置加载失败'}；可重新打开重试或修正后保存`)
    } finally {
      loading.value = false
    }
  }

  /**
   * 基础练习配置
   */
  const basicDeploy = () => {
    basicTrainDeployModal.value = true
    getBasicSettingInfo()
  }

  /**
   * 基础练习配置值的改变
   */
  const changeBasicValue = () => {
    const positive = basicDeployData.value.filter(item => item.type > 0)
    const abnormal = basicDeployData.value.find(item => item.type === 0)
    if (!positive.length || !abnormal) return false
    try {
      const bounds = {}
      for (const key of ['dot', 'line']) {
        const values = positive.flatMap(item => [settingMilliseconds(item[key].min), settingMilliseconds(item[key].max)])
        bounds[key] = [Math.min(...values), Math.max(...values)]
      }
      for (const key of ['dot', 'line']) {
        basicLabTitle.value[key] = bounds[key]
        abnormal[key].min = '<' + bounds[key][0]
        abnormal[key].max = '>' + bounds[key][1]
      }
      return true
    } catch {
      return false
    }
  }

  /**
   * 新增标准
   */
  const createBasicNorm = () => {
    basicDeployData.value.push({
      name: '',
      dot: { min: '', max: '' },
      line: { min: '', max: '' },
      text: '',
      type: basicDeployData.value.length,
      prune: true
    })
  }

  /**
   * 删除标准
   * @param index
   */
  const closeBasicNorm = index => {
    basicDeployData.value = basicDeployData.value.filter((item, i) => i !== index)
    basicDeployData.value.map((item, i) => {
      item.type = i
    })
  }

  /**
   * 基础练习数据处理
   */
  const handleBasicData = () => {
    let res = [],
      flag = false
    for (let item of basicDeployData.value) {
      if (!item.name?.trim() || !item.text?.trim()) {
        flag = true
      }
      res.push({
        type: 0,
        key: '0',
        value: JSON.stringify({ min: item.dot.min, max: item.dot.max, type: item.type, name: item.name, msg: item.text })
      })
      res.push({
        type: 0,
        key: '1',
        value: JSON.stringify({ min: item.line.min, max: item.line.max, type: item.type, name: item.name, msg: item.text })
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
  const saveDeploy = async () => {
    if (loading.value) return
    try {
      if (!changeBasicValue()) throw new Error('点和划均须填写有效正区间及异常区间')
      const data = handleBasicData()
      parseTelegramBasicSettings(data)
      loading.value = true
      const res = await saveBasicSetting(data)
      if (res.code !== 200) throw new Error(res.message || '基础配置保存失败')
      message.success('基础练习配置保存成功！')
      basicTrainDeployModal.value = false
    } catch (error) {
      message.error(error.message || '基础配置保存失败，请重试')
    } finally {
      loading.value = false
    }
  }

  return {
    loading,
    addDrillModal,
    trainData,
    formData,
    totalList,
    prevTrain,
    basicTrainDeployModal,
    basicLabTitle,
    basicDeployData,
    addDrillModalInfo,
    cancelTrainModal,
    handlePatDeployData,
    createDrillInfo,
    startTrain,
    basicDeploy,
    changeBasicValue,
    createBasicNorm,
    closeBasicNorm,
    saveDeploy,
    findPrevTrainMsg
  }
}
