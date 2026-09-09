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
  onMounted(() => {
    if (route.query.id && route.query.id !== '') {
      scoreData.value.trainId = route.query.id
      getTelexTrainByID({
        id: scoreData.value.trainId
      }).then(res => {
        loading.value = false
        if (res.code === 200) {
          let arr = formatData(res.data.existPage)
          console.log(arr);
          scoreData.value.content = arr.slice(0, 100)
          // scoreData.value.nextContent = res.data.existPage.slice(100, 200)
          scoreData.value.errorNumber = res.data.errorNumber
          scoreData.value.speed = res.data.speed
          scoreData.value.accuracy = parseInt(res.data.accuracy)
          scoreData.value.validTime = res.data.validTime
          scoreData.value.score = res.data.score
          scoreData.value.deductInfo = JSON.parse(res.data.deductInfo)
          scoreData.value.change = res.data.change
          scoreData.value.name = res.data.name
          if(res.data.isCable===1){
            page.value.pageAll = res.data.pageNumber
          }else {
            page.value.pageAll = Math.ceil(res.data.groupNumber / 100)
          }
          scoreData.value.validTimeLog = JSON.parse(res.data.validTimeLog)
          scoreData.value.speedLog = JSON.parse(res.data.speedLog)

          apiPostTelexPatTrainGetPage({
            pageNumber: page.value.current,
            trainId: scoreData.value.trainId
          }).then(res => {
            scoreData.value.content = formatData(res.data.pageVo)
            testData.value.codeAll = res.data.codeAll
            strCode.value = res.data.codeAll
            try {
              //老数据兼容，没有老数据可以删掉
              testData.value.codeAll = JSON.parse(res.data.codeAll)
              strCode.value = ""
              testData.value.codeAll.map(v=>{
                if(v.text!=="Enter"){
                  strCode.value+=v.text
                }else {
                  strCode.value+="\n"
                }
              })
            }catch (e) {

            }
          })
        }
      })
    }
  })


  //获取指定分页低报
  const pageTurn =async num => {
    page.value.current += num
    if (page.value.current < 1) {
      page.value.current = 1
      // message.error('已经是第一页')
      return false
    }
    if (page.value.current > page.value.pageAll) {
      page.value.current = page.value.pageAll
      // message.error('已经是最后一页')
      return false
    }
    const res = await apiPostTelexPatTrainGetPage({
      pageNumber: page.value.current,
      trainId: scoreData.value.trainId
    })
    scoreData.value.content =formatData(res.data.pageVo)
    testData.value.codeAll = res.data.codeAll
    strCode.value = res.data.codeAll
    return false
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
          formatter: '<div class="tooltipItem"><div>码率：</div><div>{c0}' + ' 码/分</div></div>'
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
