import { reactive, ref, toRefs, nextTick, onMounted } from 'vue'
import { analyzeDown } from '../../../../../../common/api/StudyManage_classHours'
import * as echarts from 'echarts'
import { sources } from '@fingerprintjs/fingerprintjs'
export default function pointUseChart(currentYear2) {
  let data = reactive({
    theChartPoint: '',
    sourceData: [
      // { x: 1, y: 4.181 },
      // { x: 5, y: 5.448 },
      // { x: 7, y: 5.653 },
      // { x: 11, y: 6.311 },
      // { x: 13, y: 6.479 },
      // { x: 17, y: 6.852 },
      // { x: 19, y: 6.682 },
      // { x: 23, y: 6.951 },
      // { x: 25, y: 7.143 },
      // { x: 27, y: 6.941 },
      // { x: 29, y: 6.898 },
      // { x: 31, y: 6.938 },
    ]
  })
  const avgScores = [
    {month:'1',avgScore:0},
    {month:'2',avgScore:0},
    {month:'3',avgScore:0},
    {month:'4',avgScore:0},
    {month:'5',avgScore:0},
    {month:'6',avgScore:0},
    {month:'7',avgScore:0},
    {month:'8',avgScore:0},
    {month:'9',avgScore:0},
    {month:'10',avgScore:0},
    {month:'11',avgScore:0},
    {month:'12',avgScore:0},
  ]
  let chart
  onMounted(() => {
    let xian = document.getElementById('pointContainer')
    xian.removeAttribute('_echarts_instance_')
    chart = echarts.init(xian)
  })
  let pointGetChartDataSource = async () => {
    data.sourceData = []
    let x = []
    let source = []
    await new Promise(resolve => {
      analyzeDown({ year: String(currentYear2.value) }).then(res => {
        // 拿到所有的月 因为有几个月就有几个点
        let obj = []

        if (res.data.avgScores && res.data.avgScores instanceof Array) {
          res.data.avgScores.forEach(item=>{
            avgScores.forEach(v=>{
              if(v.month===item.month){
                v.avgScore = item.avgScore
              }
            })
          })
          avgScores.forEach(qq => {
            if (res.data.theoryYearInfoVOS && res.data.theoryYearInfoVOS instanceof Array) {
              obj = res.data.theoryYearInfoVOS.filter(st => st.moth === qq.month && st.studyTime > 0)
              if (obj.length > 0) {
                source.push(qq.avgScore)
                x.push(obj[0].studyTime.toString())
                data.sourceData.push({
                  月份: qq.month + '月',
                  平均分: qq.avgScore,
                  学习时长: obj[0].studyTime.toString() + '分钟',
                  m: qq.month
                })
              }
            }
          })
        }
        resolve()
      })
    })
    if (data.theChartPoint !== '') {
      data.theChartPoint.destroy()
    }
    const interfaceStyle = window.interfaceStyle
    const color = {
      color1:'rgba(255,255,255,0.5)',
      color2:'rgba(76,117,149,0.2)',
      color3:'rgba(76,117,149,0.5)',
    }
   if(interfaceStyle==='LJ'){
      color.color1= 'rgba(233,222,178,0.5)'
      color.color2= 'rgba(233,222,178,0.3)'
      color.color3= 'rgba(233,222,178,0.3)'
    } else if(interfaceStyle==='GD'){
      color.color1= 'rgba(255,255,255,0.5)'
      color.color2= 'rgba(255,255,255,0.3)'
      color.color3= 'rgba(255,255,255,0.5)'
    }else if(interfaceStyle==='KJ'){
     color.color1= 'rgba(255,255,255,0.5)'
     color.color2= 'rgba(255,255,255,0.3)'
     color.color3= 'rgba(255,255,255,0.5)'
   }
    const option = {
      grid: {
        containLabel: true,
        left: '5%',
        bottom: '15%',
        right: '5%',
        top: '10%'
      },
      tooltip: {
        trigger: 'axis',
        formatter: e => {
          const index = e[0].dataIndex
          return e[0].marker + '月份：' + data.sourceData[index]['月份'] + '<br/>' + e[0].marker + '平均分：' + data.sourceData[index]['平均分'] + '<br/>' + e[0].marker + '学习时长：' + data.sourceData[index]['学习时长']
        }
      },
      color: ['#e9deb2'],
      xAxis: {
        type: 'category',
        data: x,
        name: '学习时长/月',
        nameTextStyle: {
          color: color.color1,
          lineHeight: 50
        },
        nameLocation: 'middle',
        axisLabel: {
          color: color.color2
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
        name: '平均分/月',
        y: 40,
        nameTextStyle: {
          color: color.color3
        },
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
          name: '平均分',
          type: 'line',
          areaStyle: {},
          data: source
        }
      ]
    }
    chart.setOption(option)
    // data.theChartPoint.render();
  }

  return {
    pointGetChartDataSource
  }
}
