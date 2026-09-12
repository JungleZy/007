import { ref, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import useMorse from '../../../../../../common/mixin/useMorse.js'
import { partTimeFormatInfo } from '../../../../../../common/utils/Utils.js'
import { getTelexTrainByID, apiPostTelexPatTrainGetPage } from '../../../../../../common/api/TelegramApi.js'
import { deepClone } from '../../../../../../common/utils/Utils.js'
import * as echarts from 'echarts'

export default function telegramList(patHairTrendBoxRef) {
  const loading = ref(true)
  const scoreReady = ref(false)
  const route = useRoute()
  const scoreData = ref({
    trainId: '',
    currPage: 1,
    errorNumber: 0,
    speed: 0,
    accuracy: 0,
    validTime: 0,
    preContent: [],
    content: [],
    nextContent: [],
    change: 0,
    name:""
  })
  const page = ref({
    pageAll: 0,
    current: 1
  })
  const situaData = ref([])
  const testData = ref({
    preCode: [],
    codeAll: [],
    nextCode: []
  })
  const loadScore = async () => {
    if (!route.query.id) { loading.value = false; return }
    loading.value = true
    scoreReady.value = false
    try {
      scoreData.value.trainId = route.query.id
      const res = await getTelexTrainByID({ id: route.query.id })
      if (res.code !== 200) throw new Error(res.message || '成绩读取失败')
      if (res.data.status !== 3) throw new Error('训练尚未完成确认，请返回训练页重试提交')
      Object.assign(scoreData.value, {
        protocolVersion: res.data.protocolVersion,
        errorNumber: res.data.errorNumber, speed: res.data.totalSpeed,
        accuracy: Number(res.data.accuracy), validTime: res.data.validTime,
        score: res.data.score, deductInfo: JSON.parse(res.data.deductInfo || '{}'),
        change: res.data.change, name: res.data.name
      })
      page.value.pageAll = res.data.isCable === 1 ? res.data.pageNumber : Math.ceil(res.data.groupNumber / 100)
      const first = await apiPostTelexPatTrainGetPage({ pageNumber: 1, trainId: route.query.id })
      if (first.code !== 200) throw new Error(first.message || '成绩页面读取失败')
      scoreData.value.content = formatData(first.data.pageVo)
      testData.value.codeAll = first.data.codeAll
      page.value.current = 1
      scoreReady.value = true
    } catch (error) {
      message.error(`${error.message || '成绩读取失败'}，请点击重新加载`)
    } finally {
      loading.value = false
    }
  }
  onMounted(loadScore)
  const pageTurn = async num => {
    const target = page.value.current + num
    if (loading.value || target < 1 || target > page.value.pageAll) return
    loading.value = true
    try {
      const res = await apiPostTelexPatTrainGetPage({ pageNumber: target, trainId: scoreData.value.trainId })
      if (res.code !== 200) throw new Error(res.message || '成绩页面读取失败')
      scoreData.value.content = formatData(res.data.pageVo)
      testData.value.codeAll = res.data.codeAll
      page.value.current = target
    } catch (error) {
      message.error(`${error.message || '成绩页面读取失败'}，页码未变，请重新翻页`)
    } finally {
      loading.value = false
    }
  }
  //格式化数据
  const formatData = (data)=>{
    let arr = []
    data.forEach(item => {
      let a = arr[arr.length-1]
      if(arr.length==0||arr[arr.length-1].sort!==item.sort){
        if(a?.list){
          a.list.sort((a,b)=>a[0]-b[0])
          let str = []
          a.list.forEach(item=>{
            str.push(item)
          })
          a.value = str
        }
        arr.push(item)
      }else {
        if(a.list===undefined){
          a.list = [a.value.split(',')]
          a.list.push(item.value.split(','))
        }else{
          a.list.push(item.value.split(','))
        }
      }
    })
    return arr
  }

  const showChart = ref('total')

  /**
   * 点击查看当前报文拍发用时明细
   * @param key
   * @param i
   */
  const seeCurrKeysHairTrend = (key, i) => {
    let idTxt = 'ht_' + page.value.current + '_' + (i + 1) + '_' + key.key
    nextTick(() => {
      let ht = ref(document.getElementById(idTxt))
      patHairTrendBoxRef.value.scrollTo({ left: ht.value.offsetLeft - 80, behavior: 'smooth' })
    })
  }
  return {
    loadScore,
    scoreReady,
    scoreData,
    loading,
    page,
    testData,
    pageTurn,
    showChart,
    situaData,
    seeCurrKeysHairTrend
  }
}
