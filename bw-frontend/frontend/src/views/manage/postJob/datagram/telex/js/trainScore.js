import { ref, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import useMorse from '../../../../../../common/mixin/useMorse.js'
import { partTimeFormatInfo } from '../../../../../../common/utils/Utils.js'
import { getTelexTrainByID, apiPostTelexPatTrainGetPage } from '../../../../../../common/api/TelegramApi.js'
import { deepClone } from '../../../../../../common/utils/Utils.js'
import * as echarts from 'echarts'
import { log } from '@antv/g2plot/lib/utils/invariant.js'

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
    content: [],
    change: 0,
    validTimeLog: [],
    speedLog: [],
    name:''
  })
  const page = ref({
    pageAll: 0,
    current: 1
  })
  let lineData = []
  let barData = []
  const testData = ref({
    codeAll: []
  })
  let columnChart = null
  let speedChart = null
  const situaData = ref([])
  const strCode = ref('')
  const displayText = text => {
    if (scoreData.value.protocolVersion === 1 || !text?.trimStart().startsWith('[')) return text
    // Protocol-0 results may contain either raw text or the historical key-event array.
    let events
    try { events = JSON.parse(text) } catch { return text }
    if (!Array.isArray(events) || !events.every(event => typeof event?.text === 'string')) return text
    return events.map(event => event.text === 'Enter' ? '\n' : event.text).join('')
  }
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
        change: res.data.change, name: res.data.name,
        validTimeLog: JSON.parse(res.data.validTimeLog || '[]'), speedLog: JSON.parse(res.data.speedLog || '[]')
      })
      page.value.pageAll = res.data.isCable === 1 ? res.data.pageNumber : Math.ceil(res.data.groupNumber / 100)
      const first = await apiPostTelexPatTrainGetPage({ pageNumber: 1, trainId: route.query.id })
      if (first.code !== 200) throw new Error(first.message || '成绩页面读取失败')
      scoreData.value.content = formatData(first.data.pageVo)
      testData.value.codeAll = first.data.codeAll
      strCode.value = displayText(first.data.codeAll)
      page.value.current = 1
      scoreReady.value = true
    } catch (error) {
      message.error(`${error.message || '成绩读取失败'}，请点击重新加载`)
    } finally {
      loading.value = false
    }
  }
  onMounted(loadScore)


  //获取指定分页低报
  const pageTurn = async num => {
    const target = page.value.current + num
    if (loading.value || target < 1 || target > page.value.pageAll) return
    loading.value = true
    try {
      const res = await apiPostTelexPatTrainGetPage({ pageNumber: target, trainId: scoreData.value.trainId })
      if (res.code !== 200) throw new Error(res.message || '成绩页面读取失败')
      scoreData.value.content = formatData(res.data.pageVo)
      testData.value.codeAll = res.data.codeAll
      strCode.value = displayText(res.data.codeAll)
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
  const lineChart = () => {
    let data = [],
        xTxt = []
    scoreData.value.speedLog.forEach((item, idx) => {
      xTxt.push('第' + (Number(idx) + 1) + '页')
      data.push(item)
    })

    // lineData.map(item => {
    //   xTxt.push(item.date)
    //   data.push(Number(item.value / 4))
    // })

    nextTick(() => {
      let xian = document.getElementById('lineChart')
      xian.removeAttribute('_echarts_instance_')
      speedChart = echarts.init(xian)
      let option = {
        color: ['#6ebdff'],
        grid: {
          containLabel: true,
          top: 20,
          left: 0,
          right: 6,
          bottom: 0
        },
        tooltip: {
          show: true,
          backgroundColor: 'rgba(29, 65, 88, .9)',
          borderColor: '#0d4c93',
          padding: [5, 10],
          textStyle: { color: '#6ebdff', fontSize: 12 },
          formatter: '<div class="tooltipItem"><div>码率：</div><div>{c0} ' + (scoreData.value.protocolVersion === 1 ? '字符/分' : '（历史原口径）') + '</div></div>'
        },
        xAxis: {
          type: 'category',
          data: xTxt,
          boundaryGap: true,
          axisLine: { lineStyle: { color: '#354971' } },
          axisLabel: { color: '#7b90af' },
          axisTick: { alignWithLabel: true }
        },
        yAxis: {
          type: 'value',
          axisLine: { lineStyle: { color: '#7b90af' } },
          splitLine: {
            show: true,
            lineStyle: { color: ['#1f2b46'], type: 'dashed' }
          }
        },
        dataZoom: [
          {
            type: 'inside',
            startVlaue: 0,
            endValue: 20
          }
        ],
        series: [
          {
            type: 'line',
            data: data,
            areaStyle: {
              color: 'rgba(112,201,255,.4)'
            }
          }
        ]
      }
      speedChart.setOption(option)
    })
  }
  const barChart = () => {
    let data = [],
        xTxt = []

    scoreData.value.validTimeLog.forEach((item, idx) => {
      xTxt.push('第' + (Number(idx) + 1) + '页')
      data.push(item)
    })

    nextTick(() => {
      let xian = document.getElementById('columnChart')
      xian.removeAttribute('_echarts_instance_')
      columnChart = echarts.init(xian)

      let option = {
        color: ['#6ebdff'],
        grid: {
          containLabel: true,
          top: 20,
          left: 0,
          right: 6,
          bottom: 0
        },
        tooltip: {
          show: true,
          backgroundColor: 'rgba(29, 65, 88, .9)',
          padding: [5, 10],
          textStyle: { color: '#6ebdff', fontSize: 12 },
          formatter: '<div class="tooltipItem"><div>用时：</div><div>{c0} s</div></div>'
        },
        xAxis: {
          type: 'category',
          data: xTxt,
          boundaryGap: true,
          axisLine: { lineStyle: { color: '#354971' } },
          axisLabel: { color: '#7b90af' },
          axisTick: { alignWithLabel: true }
        },
        yAxis: {
          type: 'value',
          axisLine: { lineStyle: { color: '#7b90af' } },
          splitLine: {
            show: true,
            lineStyle: { color: ['#1f2b46'], type: 'dashed' }
          }
        },
        dataZoom: [
          {
            type: 'inside',
            startVlaue: 0,
            endValue: 20
          }
        ],
        series: [
          {
            type: 'bar',
            data: data,
            barMaxWidth: 20,
            itemStyle: {
              color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                { offset: 0, color: '#4c7595' },
                { offset: 1, color: '#9fc7d7' }
              ])
            }
          }
        ]
      }
      columnChart.setOption(option)
    })
  }
  const showChart = ref('total')
  const changeChart = type => {
    showChart.value = type
    if (type === 'line' && !speedChart) {
      lineChart()
    }
    if (type === 'column' && !columnChart) {
      barChart()
    }
  }

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
    changeChart,
    showChart,
    situaData,
    seeCurrKeysHairTrend,
    strCode
  }
}
