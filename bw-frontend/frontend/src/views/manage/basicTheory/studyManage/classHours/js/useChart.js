import {reactive, toRefs, nextTick, onMounted} from 'vue'
import * as echarts from 'echarts'
import {getClassChartsDataByTypeAndTime} from '../../../../../../common/api/StudyManage_classHours.js'
import moment from 'moment'
import {v1} from 'uuid-umd'
import {formatDuration} from '../../duration.js'

// 三个参数都是外面传进来 会随外面改变而改变
export default function useChart(currentTimeTag, currentTypeTag, theDate, tableData) {
  let chart
  onMounted(() => {
    let xian = document.getElementById('container')
    xian.removeAttribute('_echarts_instance_')
    chart = echarts.init(xian)
  })
  let data = reactive({
    theChart: '',
    sourceData: [],
    // 横坐标为月的数据
    sourceData1: [
      { studyTime: '1月', seconds: 0 },
      { studyTime: '2月', seconds: 0 },
      { studyTime: '3月', seconds: 0 },
      { studyTime: '4月', seconds: 0 },
      { studyTime: '5月', seconds: 0 },
      { studyTime: '6月', seconds: 0 },
      { studyTime: '7月', seconds: 0 },
      { studyTime: '8月', seconds: 0 },
      { studyTime: '9月', seconds: 0 },
      { studyTime: '10月', seconds: 0 },
      { studyTime: '11月', seconds: 0 },
      { studyTime: '12月', seconds: 0 }
    ],
    // 柱子粗细
    columnWidth: 20,
    sourceData2: [{ studyTime: '1号', seconds: 0 }],
    sourceData3: []
  })

  let handleTableData = sourceData => {
    let needTableData = []
    for (let key in sourceData) {
      let keyArr = key.split(',')
      let tarObj = {
        key: keyArr[6],
        knowledgeName: keyArr[4].split('=')[1],
        classHour: keyArr[7].split('=')[1].substring(0, keyArr[7].split('=')[1].length),
        duration: '00:00:00',
        children: []
      }
      let totalSeconds = 0
      for (let childKey in sourceData[key]) {
        let childTarObj = {
          key: v1(),
          knowledgeName: childKey,
          classHour: '—',
          duration: formatDuration(sourceData[key][childKey])
        }
        tarObj.children.push(childTarObj)
        totalSeconds += Number(sourceData[key][childKey]) || 0
      }
      tarObj.duration = formatDuration(totalSeconds)
      needTableData.push(tarObj)
    }
    tableData.value = needTableData
  }

  /**
   *
   * @param type 学习大类
   * @param timetag  选择的月或者日
   */
  let classApi = (type, timetag) => {
    return new Promise((resolve, reject) => {
      // theDate当前选择的具体时间
      let nowTime = moment(theDate.value).format('YYYY-MM-DD')
      nowTime = nowTime.split('-')
      let apiObj = { year: nowTime[0], month: nowTime[1], type: type }
      if (timetag === '年') {
        apiObj.month = ''
      }
      getClassChartsDataByTypeAndTime(apiObj).then(res => {
        resolve(res.data.up)
        handleTableData(res.data.down)
      })
    })
  }

  let getChartDataSource = async () => {
    let studyType = '0'
    // 基础理论
    if (currentTypeTag.value === '基础理论') {
      studyType = '0'
    }
    // 值掌装备
    else if (currentTypeTag.value === '值掌装备') {
      studyType = '1'
    }
    // 值勤业务
    else {
      studyType = '2'
    }
    data.sourceData3 = []
    for (let i = 2011; i <= 2022; i++) {
      let obj = {
        studyTime: i + '年',
        seconds: 0
      }
      data.sourceData3.push(obj)
    }

    if (currentTimeTag.value === '日') {
      // 暂时没有日
    } else if (currentTimeTag.value === '年') {
      // 进来默认选中年
      data.columnWidth = 20
      let requestData = await classApi(studyType, '年')
      for (let item of data.sourceData1) {
        let flag = false
        for (let ele in requestData) {
          if (ele == item.studyTime.substring(0, item.studyTime.length - 1)) {
            item.seconds = Number(requestData[ele])
            flag = true
          }
        }
        if (flag === false) {
          item.seconds = 0
        }
      }
      data.sourceData = data.sourceData1
    } else {
      data.columnWidth = 10
      data.sourceData = data.sourceData2
      let nowTime = moment(theDate.value).format('YYYY-MM-DD')
      nowTime = nowTime.split('-')
      // 最后一位传0意为获取这个月有多少天
      let monthDays = new Date(Number(nowTime[0]), Number(nowTime[1]), 0)
      data.sourceData2 = []
      for (let i = 1; i <= monthDays.getDate(); i++) {
        let obj = {
          studyTime: i + '号',
          seconds: 0
        }
        data.sourceData2.push(obj)
      }
      let requestData = await classApi(studyType, '日')
      for (let item of data.sourceData2) {
        let flag = false
        for (let ele in requestData) {
          if (ele == item.studyTime.substring(0, item.studyTime.length - 1)) {
            item.seconds = Number(requestData[ele])
            flag = true
          }
        }
        if (flag === false) {
          item.seconds = 0
        }
      }
      data.sourceData = data.sourceData2
    }

    if (data.theChart !== '') {
      data.theChart.destroy()
    }

    chart.clear()
    const x = []
    const source = []
    const label = []
    data.sourceData.forEach(item => {
      x.push(item.studyTime)
      source.push(item.seconds)
      label.push(0)
    })
    const interfaceStyle = window.interfaceStyle
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
    }else if(interfaceStyle==='GD'){
      color.color1='RGBA(255,255,255,0.8)'
      color.color2='RGBA(233,222,178,1)'
      color.color3='RGBA(255,255,255,0.3)'
    }else if(interfaceStyle==='KJ'){
      color.color1='RGBA(255,255,255,0.8)'
      color.color2='RGBA(233,222,178,1)'
      color.color3='RGBA(255,255,255,0.5)'
    }
    const option = {
      tooltip: {
        formatter: params => `${params.name}&nbsp;&nbsp;&nbsp;&nbsp;${formatDuration(params.value)}`
      },
      grid: {
        containLabel: true,
        left: '5%',
        bottom: '25%',
        right: '5%',
        top: '5%'
      },
      data: source,
      yAxis: {
        type: 'value',
        splitLine: { show: false },
        axisLabel: {
          formatter: formatDuration,
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
            color: color.color2
          },
          data: source,
          z: 1,
          animationEasing: 'elasticOut'
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
            color:color.color3
          },
          label: { show: false },
          data: source,
          z: 0,
          animationEasing: 'elasticOut'
        }
      ]
    }
    nextTick(() => {
      chart.setOption(option)
    })
  }

  return {
    ...toRefs(data),
    getChartDataSource
  }
}
