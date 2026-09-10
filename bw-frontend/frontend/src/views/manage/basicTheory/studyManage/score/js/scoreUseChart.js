import { reactive, ref, toRefs, onMounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import moment from 'moment'
import { v1 } from 'uuid-umd'
import { getScoreChartsDataByTypeAndTime } from '../../../../../../common/api/StudyManage_classHours'

// const G = G2.getEngine('canvas')

// 处理小数点
function formatDecimal(num, decimal) {
  decimal = 2
  // num = num.toString()
  // let index = num.indexOf('.')
  // if (index !== -1) {
  //   num = num.substring(0, decimal + index + 1)
  // } else {
  //   num = num.substring(0)
  // }
  return parseFloat(num).toFixed(decimal)
}

// 三个参数都是外面传进来 会随外面改变而改变
// theDate具体哪年哪月 走接口数据的时候会用上
export default function scoreUseChart(currentTimeTag, currentTypeTag, theDate, isShowMuBan) {
  let data = reactive({
    // 图表对象
    theChart: '',
    cishu: {
      all: 0,
      good: 0
    }
  })
  let chart
  onMounted(() => {
    let xian = document.getElementById('containerScore')
    xian.removeAttribute('_echarts_instance_')
    chart = echarts.init(xian)
  })
  /**
   *
   * @param type 查询类型
   * @param timetag 时间类型
   * @returns {Promise<unknown>}
   */
  let getDataApi = (type, timetag) => {
    return new Promise((resolve, reject) => {
      // theDate当前选择的具体时间
      // theDate为传进来的当前选择时间
      let nowTime = moment(theDate.value).format('YYYY-MM-DD')
      nowTime = nowTime.split('-')
      let apiObj = { year: nowTime[0], month: nowTime[1], type: type }
      if (timetag === '年') {
        apiObj.month = ''
      }
      getScoreChartsDataByTypeAndTime(apiObj).then(res => {
        resolve(res.data)
        // 次数在这里一起处理了
        data.cishu.all = res.data.up.all
        data.cishu.good = res.data.up.good
      })
    })
  }
  const interfaceStyle = window.interfaceStyle
  let getChartDataSource = async () => {
    // 三种图的清空图表
    if (data.theChart !== '') {
      data.theChart.destroy()
    }
    // 饼图
    if (currentTypeTag.value === '成绩分布') {
      isShowMuBan.value = false
      let dateFlag
      if (currentTimeTag.value === '月') {
        dateFlag = '月'
      } else {
        dateFlag = '年'
      }
      // 默认0次
      let source = [
        {
          name: '60分以下',
          value: 0
        },
        {
          name: '60分 ~ 80分',
          value: 0
        },
        {
          name: '80分以上',
          value: 0
        }
      ]
      let sourceData = await getDataApi('0', dateFlag)
      let down = sourceData.down
      for (let key in down) {
        for (let item of source) {
          if (key == '59') {
            if (item.name === '60分以下') {
              item.value = down[key]
            }
          }
          if (key == '60') {
            if (item.name === '60分 ~ 80分') {
              item.value = down[key]
            }
          }
          if (key == '81') {
            if (item.name === '80分以上') {
              item.value = down[key]
            }
          }
        }
      }
      chart.clear()
      const option = {
        tooltip: {
          show: true,
          formatter: e => {
            return e.marker + e.data.name + '  ' + e.data.value + '次  ' + e.percent + '%'
          }
        },
        series: [
          {
            color: ['#6ebdff', '#ff963b', '#1bc74d'],
            type: 'pie',
            radius: '80%',
            label: {
              color: '#6ebdff',
              fontSize: 14,
              lineHeight: 20,
              formatter: e => {
                const name = e.data.name
                const value = e.data.value
                const percent = e.percent
                return '{a|' + name + '}' + '{b|' + value + '次' + '}' + '{c|' + percent + '%' + '}'
              },
              // formatter:'{a|b}  {c}次   {d}',
              rich: {
                a: {
                  color: '#fff',
                  padding: 10
                },
                b: {
                  color: '#ff963b',
                  padding: 10
                },
                c: {
                  color: '#1bc74d',
                  padding: 10
                }
              }
            },
            data: source
          }
        ]
      }
      chart.setOption(option)
    }
    // 柱状图
    else if (currentTypeTag.value === '训练次数') {
      isShowMuBan.value = true
      let cfg = {
        data: ''
      }
      const x = []
      const source = []
      const label = []
      if (currentTimeTag.value === '月') {
        let nowTime = moment(theDate.value).format('YYYY-MM-DD')
        nowTime = nowTime.split('-')
        // 最后一位传0意为获取这个月有多少天
        let monthDays = new Date(Number(nowTime[0]), Number(nowTime[1]), 0)
        let dataSource = []
        for (let i = 1; i <= monthDays.getDate(); i++) {
          let obj = {
            studyTime: i + '号',
            次数: 0
          }
          dataSource.push(obj)
        }
        cfg.data = dataSource
        let sourceData = await getDataApi('1', '月')
        let down = sourceData.down
        for (let key in down) {
          for (let item of dataSource) {
            if (item.studyTime.substring(0, item.studyTime.length - 1) == key) {
              item['次数'] = down[key]
            }
          }
        }
      }
      // 年
      else {
        let dataSource = []
        for (let i = 1; i <= 12; i++) {
          let obj = {
            studyTime: i + '月',
            次数: 0
          }
          dataSource.push(obj)
        }
        cfg.data = dataSource
        let sourceData = await getDataApi('1', '年')
        let down = sourceData.down
        for (let key in down) {
          for (let item of dataSource) {
            if (item.studyTime.substring(0, item.studyTime.length - 1) == key) {
              item['次数'] = down[key]
            }
          }
        }
      }
      cfg.data.forEach(item => {
        x.push(item.studyTime)
        source.push(item['次数'])
        label.push(0)
      })

      const color = {
        color1:'RGBA(69,95,120,1)',
        color2:'RGBA(233,222,178,1)',
        color3:'RGBA(69,95,120,0.3)',
      }
      if(interfaceStyle==='HJJ'){
        color.color1='RGBA(69,95,120,1)'
        color.color2='RGBA(233,222,178,1)'
        color.color3='RGBA(69,95,120,0.3)'
      }
      else if(interfaceStyle==='LJ'){
        color.color1='RGBA(233,222,178,0.6)'
        color.color2='RGBA(233,222,178,0.6)'
        color.color3='RGBA(233,222,178,0.3)'
      } else if(interfaceStyle==='GD'){
        color.color1='RGBA(255,255,255,0.6)'
        color.color2='RGBA(233,222,178,0.6)'
        color.color3='RGBA(233,222,178,0.3)'
      }
      else if(interfaceStyle==='KJ'){
        color.color1='RGBA(255,255,255,0.8)'
        color.color2='RGBA(233,222,178,0.6)'
        color.color3='RGBA(233,222,178,0.5)'
      }
      const option = {
        tooltip: {
          formatter: e => {
            return `${e.name}&nbsp;&nbsp;&nbsp;&nbsp;${e.data}次`
          }
        },
        grid: {
          containLabel: true,
          left: '5%',
          bottom: '15%',
          right: '5%',
          top: '5%'
        },
        data: source,
        yAxis: {
          type: 'value',
          splitLine: { show: false },
          axisLabel: {
            color: color.color1
          }
        },
        xAxis: {
          type: 'category',
          data: x,
          axisLabel: {
            color: color.color1
          },
          axisLine: { show: false },
          axisTick: { show: false }
        },
        series: [
          {
            type: 'pictorialBar',
            symbolRepeat: 'true',
            symbolMargin: '20%',
            symbol: 'rect',
            symbolSize: [15, 5],
            itemStyle: {
              color:color.color2
            },
            data: source,
            z: 1,
            animationEasing: 'elasticOut',
            label: {
              show: true,
              position: 'top',
              color: color.color3,
              formatter: e => {
                return e.value > 0 ? e.value : ''
              }
            }
          },
          {
            name: '背景',
            type: 'pictorialBar',
            animationDuration: 0,
            symbolRepeat: 'fixed',
            symbolMargin: '20%',
            symbol: 'rect',
            symbolSize: [15, 5],
            itemStyle: {
              color: color.color3
            },
            label: { show: false },
            data: source,
            z: 0,
            animationEasing: 'elasticOut'
          }
        ]
      }
      chart.clear()
      nextTick(() => {
        chart.setOption(option)
      })
    }
    // 折线图
    else {
      isShowMuBan.value = false
      let data1 = [],
        data2 = [],
        data3 = [],
        x = []
      if (currentTimeTag.value === '月') {
        // 选中月
        let nowTime = moment(theDate.value).format('YYYY-MM-DD')
        nowTime = nowTime.split('-')
        // 最后一位传0意为获取这个月有多少天
        let monthDays = new Date(Number(nowTime[0]), Number(nowTime[1]), 0)
        for (let i = 1; i <= monthDays.getDate(); i++) {
          x.push(i + '号')
          data1.push(0)
          data2.push(0)
          data3.push(0)
        }
        let sourceData = await getDataApi('2', '月')
        let down = sourceData.down
        for (let key in down) {
          data1[key - 1] = down[key].high
          data2[key - 1] = down[key].avg
          data3[key - 1] = down[key].low
        }
      }
      // 年
      else {
        for (let i = 1; i <= 12; i++) {
          x.push(i + '月')
          data1.push(0)
          data2.push(0)
          data3.push(0)
        }
        let sourceData = await getDataApi('2', '年')
        let down = sourceData.down
        for (let key in down) {
          data1[key - 1] = down[key].high
          data2[key - 1] = down[key].avg
          data3[key - 1] = down[key].low
        }
      }
      chart.clear()

      const color = {
        color1:'RGBA(105,181,246,0.5)',
        color2:'rgba(105,181,246,0.2)',
        color3:'rgba(105,181,246,0.5)',
      }
      if(interfaceStyle==='HJJ'){
        color.color1='RGBA(105,181,246,0.5)'
        color.color2='rgba(105,181,246,0.2)'
        color.color3='rgba(105,181,246,0.5)'
      }
      else if(interfaceStyle==='LJ'){
        color.color1='rgba(233,222,178,0.6)'
        color.color2='rgba(233,222,178,0.2)'
        color.color3='rgba(233,222,178,0.6)'
      } else if(interfaceStyle==='GD'){
        color.color1='rgba(255,255,255,0.6)'
        color.color2='rgba(233,222,178,0.2)'
        color.color3='rgba(255,255,255,0.6)'
      }
      else if(interfaceStyle==='KJ'){
        color.color1='rgba(255,255,255,0.8)'
        color.color2='rgba(233,222,178,0.5)'
        color.color3='rgba(255,255,255,0.6)'
      }
      const option = {
        legend: {
          left: '5%',
          textStyle: {
            color: '#fff'
          }
        },
        grid: {
          containLabel: true,
          left: '5%',
          bottom: '5%',
          right: '5%',
          top: '5%'
        },
        tooltip: {
          trigger: 'axis'
        },
        color: ['#6ebdff', '#ff963b', '#1bc74d'],
        xAxis: {
          type: 'category',
          data: x,
          axisLabel: {
            color: color.color1
          },
          position: {
            top: 20,
            bottom: 20
          },
          axisLine: {
            lineStyle: {
              color: color.color2
            }
          },
          axisTick: {}
        },
        yAxis: {
          type: 'value',
          splitLine: {
            lineStyle: {
              color: color.color2
            }
          },
          axisLabel: {
            color: color.color3
          }
        },
        series: [
          {
            name: '最高分',
            type: 'line',
            areaStyle: {},
            data: data1
          },
          {
            name: '平均分',
            type: 'line',
            areaStyle: {},
            data: data2
          },
          {
            name: '最低分',
            type: 'line',
            areaStyle: {},
            data: data3
          }
        ]
      }
      chart.setOption(option)
      // data.theChart = new Line('containerScore', cfg);
    }

    // 三种图的渲染
    // data.theChart.render()
  }

  return {
    ...toRefs(data),
    getChartDataSource
  }
}
