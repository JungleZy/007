import { reactive, ref, toRefs, onMounted } from 'vue'
import { Column } from '@antv/g2plot'
import * as echarts from 'echarts'
import { analyzeUp } from '../../../../../../common/api/StudyManage_classHours.js'

export default function useChart2(currentYear1) {
  let data = reactive({
    theChart2: '',
    sourceData: [
      {
        mounth: '1月',
        value: 0,
        type: '课件数量',
        type2: '学习时长',
        value2: 0
      },
      {
        mounth: '2月',
        value: 0,
        type: '课件数量',
        type2: '学习时长',
        value2: 0
      },
      {
        mounth: '3月',
        value: 0,
        type: '课件数量',
        type2: '学习时长',
        value2: 0
      },
      {
        mounth: '4月',
        value: 0,
        type: '课件数量',
        type2: '学习时长',
        value2: 0
      },
      {
        mounth: '5月',
        value: 0,
        type: '课件数量',
        type2: '学习时长',
        value2: 0
      },
      {
        mounth: '6月',
        value: 0,
        type: '课件数量',
        type2: '学习时长',
        value2: 0
      },
      {
        mounth: '7月',
        value: 0,
        type: '课件数量',
        type2: '学习时长',
        value2: 0
      },
      {
        mounth: '8月',
        value: 0,
        type: '课件数量',
        type2: '学习时长',
        value2: 0
      },
      {
        mounth: '9月',
        value: 0,
        type: '课件数量',
        type2: '学习时长',
        value2: 0
      },
      {
        mounth: '10月',
        value: 0,
        type: '课件数量',
        type2: '学习时长',
        value2: 0
      },
      {
        mounth: '11月',
        value: 0,
        type: '课件数量',
        type2: '学习时长',
        value2: 0
      },
      {
        mounth: '12月',
        value: 0,
        type: '课件数量',
        type2: '学习时长',
        value2: 0
      }
    ]
  })

  let upData = ref({
    one: '',
    two: '',
    three: ''
  })
  let chart
  onMounted(() => {
    // let xian = document.getElementById('containerTt')
    // xian.removeAttribute('_echarts_instance_')
    // chart = echarts.init(xian)
  })
  async function getChartDataSource2() {
    let xian = document.getElementById('containerTt')
    // xian.removeAttribute('_echarts_instance_')
    chart = echarts.init(xian)
    if (data.theChart2) {
      data.theChart2.destroy()
    }
    const data1 = ref([])
    const data2 = ref([])
    const x = ['1月', '2月', '3月', '4月', '5月', '6月', '7月', '8月', '9月', '10月', '11月', '12月']
    const interfaceStyle = window.interfaceStyle
    const color = {
      color1:'#7b90af',
      color2:'rgba(76,117,149,0.2)',
      color3:['#3d5d78', '#e9deb2'],
    }
    if(interfaceStyle==='LJ'){
      color.color1=  '#a9abaa'
      color.color2= 'rgba(233,222,178,0.3)'
      color.color3= ['#095410', '#e9deb2']
    }
    let option = {
      grid: {
        containLabel: true,
        left: '5%',
        bottom: '5%',
        right: '5%',
        top: '15%'
      },
      legend: {
        textStyle: {
          color: color.color1
        },
        top: 0
      },
      tooltip: {},
      xAxis: {
        data: x,
        axisLine: {
          lineStyle: {
            color: color.color2
          }
        },
        axisLabel: {
          color: color.color1
        }
      },
      yAxis: {
        type: 'value',
        splitLine: {
          lineStyle: {
            color:color.color2,
            type: 'dashed'
          }
        },
        axisLabel: {
          color: color.color1
        }
      },
      color: color.color3,
      barWidth: 20,
      series: [
        {
          name: '课件数量',
          type: 'bar',
          stack: 'one',
          data: []
        },
        {
          name: '学习时长',
          type: 'bar',
          stack: 'one',
          data: []
        }
      ]
    }

    await new Promise(resolve => {
      analyzeUp({ year: String(currentYear1.value) }).then(res => {
        upData.value.one = res.data.baseTheoryCredit
        upData.value.two = res.data.equipCredit
        upData.value.three = res.data.workCredit

        data.sourceData.forEach(item => {
          res.data.yearInfoVO.forEach(ele => {
            if (item.mounth.substring(0, item.mounth.length - 1) == ele.moth) {
              item.value = ele.swfNum ?? 0
              item.value1 = ele.studyTime ?? 0
            }
          })
        })
        option.series[0].data = data.sourceData.map(item => {
          return item.value
        })
        option.series[1].data = data.sourceData.map(item => {
          return item.value1
        })
        chart.setOption(option)
      })
    })
    // data.theChart2.render()
  }

  function theChart2Destroy() {
    data.theChart2 && data.theChart2.destroy()
  }

  return {
    getChartDataSource2,
    theChart2Destroy,
    upData
  }
}
