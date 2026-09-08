import { ref, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import useMorse from '../../../../../../common/mixin/useMorse.js'
import { partTimeFormatInfo } from '../../../../../../common/utils/Utils.js'
import { getTelexTrainByID } from '../../../../../../common/api/TelegramApi.js'
import { deepClone } from '../../../../../../common/utils/Utils.js'
import * as echarts from 'echarts'

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
    change: 0
  })
  const page = ref({
    pageAll: 0,
    current: 1
  })
  let lineData = []
  let barData = []
  const testData = ref({})
  let columnChart = null
  let speedChart = null
  const situaData = ref([])
  onMounted(() => {
    patHairTrendBoxRef.value.addEventListener('mousewheel', e => {
      if (e.deltaY > 0) {
        patHairTrendBoxRef.value.scrollLeft += 100
      } else {
        patHairTrendBoxRef.value.scrollLeft -= 100
      }
    })
    if (route.query.id && route.query.id !== '') {
      scoreData.value.trainId = route.query.id
      getTelexTrainByID({
        id: scoreData.value.trainId
      }).then(res => {
        loading.value = false
        if (res.code === 200) {
          // scoreData.value[key]
          testData.value = JSON.parse(res.data.content)
          testData.value.content = JSON.parse(testData.value.content)
          scoreData.value.errorNumber = res.data.errorNumber
          scoreData.value.speed = res.data.speed
          scoreData.value.accuracy = parseInt(res.data.accuracy)
          scoreData.value.validTime = res.data.validTime
          scoreData.value.score = res.data.score
          scoreData.value.deductInfo = JSON.parse(res.data.deductInfo)
          scoreData.value.change = res.data.change
          page.value.pageAll = Math.ceil(testData.value.content.length / 100)
          let arr = []
          testData.value.content.filter((item, index) => {
            if (index < 100) {
              arr.push(item)
            }
          })
          scoreData.value.content = arr
          //
          let obj = {
            key: [],
            value: [],
            time: []
          }
          let _arr = []
          testData.value.codeAll.map((v, i) => {
            // if(v.text!==' '&& v.text!=='Alt' && v.text !=="Enter"){
            if (v.text !== ' ' && v.text !== 'Enter') {
              obj.key.push(v.text)
              obj.time.push(v.time * 100)
              obj.value.push(v.text)
              // }else if(v.text==='Alt' || v.text ==="Enter"){
            } else if (v.text === 'Enter') {
              obj.time.push(v.time * 100)
              obj.value.push(v.text)
              _arr.push(obj)
              obj = {
                key: [],
                value: [],
                time: []
              }
            } else {
              _arr.push(obj)
              obj = {
                key: [],
                value: [],
                time: []
              }
            }
            // if (v.text ==="Enter" && testData.value.codeAll[i-1].text === 'Enter' && testData.value.codeAll[i-2].text === 'Alt') {
            if (v.text === 'Enter' && testData.value.codeAll[i - 1].text === 'Enter') {
              situaData.value.push(_arr)
              _arr = []
            }
          })
          situaData.value.push(_arr)
          //处理统计图数据
          let time = 0
          let n = 0
          let num = 0
          const code = deepClone(testData.value.codeAll)
          testData.value.codeAll.forEach((item, index) => {
            if (item.text != '') {
              num++
              time = item.time + time
              lineData.push({
                date: time / 10 + '',
                value: time != 0 ? (num / (time / 10 / 60)).toFixed(0) : 0
              })
              if (testData.value.codeAll[index].text == 'Enter' && testData.value.codeAll[index - 1].text == 'Enter') {
                const arr = []
                for (let i = n; i < index + 1; i++) {
                  arr.push(code[i])
                }
                n = index + 1
                barData.push(arr)
              }
              if (index == testData.value.codeAll.length - 1) {
                const arr = []
                for (let i = n; i < code.length; i++) {
                  arr.push(code[i])
                }
                barData.push(arr)
              }
            }
          })
        }
      })
    }
  })
  const pageTurn = type => {
    if (type == 'next') {
      if (page.value.current == page.value.pageAll) {
        return false
      }
      page.value.current++
      scoreData.value.content = []
      for (let i in testData.value.content) {
        if (i < page.value.current * 100 && i > page.value.current * 100 - 101) {
          scoreData.value.content.push(testData.value.content[i])
        }
      }
    } else {
      if (page.value.current == 1) {
        return false
      }
      page.value.current--
      scoreData.value.content = []
      for (let i in testData.value.content) {
        if (i < page.value.current * 100 && i > page.value.current * 100 - 101) {
          scoreData.value.content.push(testData.value.content[i])
        }
      }
    }
  }
  const lineChart = () => {
    let data = [],
      xTxt = []
    lineData.map(item => {
      xTxt.push(item.date)
      data.push(Number(item.value / 4))
    })

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
          formatter: '<div class="tooltipItem"><div>码率：</div><div>{c0}' + ' 组/分</div></div>'
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
    for (let i in barData) {
      let time = 0
      for (let n of barData[i]) {
        time += n.time
      }
      xTxt.push('第' + (Number(i) + 1) + '页')
      data.push(Number((time / 10 / 60).toFixed(1)))
    }

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
          formatter: '<div class="tooltipItem"><div>用时：</div><div>{c0} m</div></div>'
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
  return {
    scoreData,
    loading,
    page,
    testData,
    pageTurn,
    changeChart,
    showChart,
    situaData
  }
}
