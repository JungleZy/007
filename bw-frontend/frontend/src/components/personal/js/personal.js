import {ref,onMounted} from "vue";
import {getUserTrainDurationStat,getRecentHandKeyTrains,getRecentElectronicKeyTrains} from "../../../common/api/trainingStatistics";
import {changePassword} from "../../../common/api/UserApi";
import {message} from "ant-design-vue";
import {useRoute, useRouter} from "vue-router";
import dayjs from "dayjs";
import * as echarts from 'echarts'
export default function Personal(props) {
  const userInfo = ref(JSON.parse(window.localStorage.getItem('userInfo')))
  const activeKey = ref('3')
  const router = useRouter()
  const route = useRoute()
  const time = ref(null)
  const editPasswordData = ref({
    oldPassword: '',
    newPassword: '',
    newPasswordV: '',
  })
  const trainType = ref([
    {type:'手键',key:'handKeyDuration'},
    {type:'电子键',key:'electronicKeyDuration'},
    {type:'收报',key:'receiveDuration'},
    {type:'数据报',key:'datagramDuration'},
    {type:'电传',key:'telexDuration'},
    {type:'拼音',key:'pinyinDuration'},
    {type:'五笔',key:'wubiDuration'},
    {type:'英语',key:'englishDuration'},
  ])
  const trainType10 = ref('handKeyDuration')//近十次训练统计类型
  const isHaveTrainData = ref(false) //是否有近十次训练数据
  const interfaceStyle = window.interfaceStyle
  const color = {
    color1:'rgba(255,255,255,0.5)',
    color2:'rgba(76,117,149,0.2)',
    color3:'rgba(76,117,149,0.5)',
  }
  if(interfaceStyle==='LJ'){
    color.color1= 'rgba(233,222,178,0.7)'
    color.color2= 'rgba(233,222,178,0.5)'
    color.color3= 'rgba(233,222,178,0.3)'
  }
  else if(interfaceStyle==='GD'){
    color.color1= 'rgba(255,255,255,0.5)'
    color.color2= 'rgba(255,255,255,0.3)'
    color.color3= 'rgba(255,255,255,0.5)'
  }
  else if(interfaceStyle==='KJ'){
    color.color1= 'rgba(255,255,255,0.5)'
    color.color2= 'rgba(255,255,255,0.3)'
    color.color3= 'rgba(255,255,255,0.5)'
  }
  const trainData = ref({
    handKeyDuration:0,
    electronicKeyDuration:0,
    receiveDuration:0,
    datagramDuration:0,
    telexDuration:0,
    pinyinDuration:0,
    wubiDuration:0,
    englishDuration:0,
  })
  onMounted(() => {
    // getTrainData()

  })
  // 切换tab
  const changeTab = ()=>{
    console.log(activeKey.value);
    if(activeKey.value === '2'){
      handleChangeTrainType()
    }
  }
// 训练统计修改时间区间
  const changeTime = ()=>{
    console.log(time.value);
    getTrainData()
  }
// 获取训练统计数据
  const getTrainData = () => {
    // {userID,startTime:'',endTime:''}
    const submitdata = {userId:userInfo.value.id}
    if(time.value!==null){
      submitdata.startTime = dayjs(time.value[0]).format('YYYY-MM-DD HH:mm:ss')
      submitdata.endTime = dayjs(time.value[1]).format('YYYY-MM-DD HH:mm:ss')
    }

    getUserTrainDurationStat(submitdata).then(res => {
      trainData.value = res.data
      const x = []
      const data = []
      trainType.value.forEach(item=>{
        x.push(item.type)
        const time = Math.ceil(res.data[item.key] / 60)
        data.push(time)
      })
      initChart(x,data)
    })
  }
// 训练统计 统计图
  let myChart = null
  const initChart = (x,data)=>{
    if(myChart!==null){
      myChart.clear()
    }
    myChart = echarts.init(document.getElementById('trainTimeCharts'));

    let option = {
      grid: {
        containLabel: true,
        left: '0',
        bottom: '30px',
        right: '0',
        top: '50px'
      },
      title: {
        text: '训练时长',
        left: 'center',
        textStyle: {
          fontSize: 18,
          fontWeight: 'bold',
          color:color.color1,
        }
      },
      tooltip: {
        trigger: 'axis',
        axisPointer: {
          type: 'shadow'
        }
      },
      xAxis: {
        type: 'category',
        data: x,
        axisLabel: {
          rotate: 0
        },
      },
      yAxis: {
        type: 'value',
        name: '时长（分钟）',
        axisLabel: {
          formatter: '{value} 分钟'
        },
        splitLine: {
          show: true,
          lineStyle: {
            type: 'dashed',
            color: color.color3,
            width: 1
          }
        },
      },
      series: [{
        data: data,
        type: 'bar',
        barWidth: '40%',
        itemStyle: {
          color: color.color1 // 柱子颜色
        },
        label: {
          show: true,
          position: 'top',
          formatter: '{c} 分钟',
          color: color.color1,
        }
      }]
    };
    myChart.setOption(option);
  }
// 修改密码
  const editPassword = () => {
    editPasswordData.value.userId = userInfo.value.id
    changePassword(editPasswordData.value).then(res => {
      if (res.data) {
        message.success('修改密码成功')
        router.replace('/login').then()
      } else {
        message.error(res.message)
      }
    })
  }
//格式化时间
  const computationTime = total => {
    let hour
    let min
    let sec
    let day
    let h
    let m
    let s
    hour = Math.floor((total / 60 / 60) % 24)
    min = Math.floor((total / 60) % 60)
    sec = Math.floor(total % 60)
    day = Math.floor(total / 60 / 60 / 24)
    // 计算总小时数
    hour = hour + day * 24
    if (hour < 10 && hour >= 0) {
      h = '0' + hour
    } else {
      h = hour.toString()
    }
    if (min < 10 && min >= 0) {
      m = '0' + min
    } else {
      m = min
    }
    if (sec < 10 && sec >= 0) {
      s = '0' + sec
    } else {
      s = sec
    }
    return h + ' : ' + m + ' : ' + s
  }

  let myChart10 = null
  //近十次训练统计类型 切换
  const handleChangeTrainType =async (value) => {
    isHaveTrainData.value = false
    const submitdata = {userId:userInfo.value.id}
    let res
    switch (trainType10.value) {
      case 'handKeyDuration':
        res = await getRecentHandKeyTrains(submitdata)
        break;
      case 'electronicKeyDuration':
        res = await getRecentElectronicKeyTrains(submitdata)
        break;
    }
    if(res.data.length === 0){
      isHaveTrainData.value = true
      if (myChart10!==null){
        myChart10.clear()
      }
      return
    }

    ininCharts10(res)
  }
  // 近10次统计图绘画
  const ininCharts10 = (res)=>{
    const series = [
      {
      name: '分数',
      type: 'line',
      data: [],
      smooth: true,
      symbolSize: 6,
      lineStyle: { width: 2 },
      itemStyle: { color: '#FAC858' }
    },
      {
        name: '用时',
        type: 'line',
        data: [],
        smooth: true,
        symbolSize: 6,
        lineStyle: { width: 2 },
        itemStyle: { color: '#EE6666' }
      },
      {
        name: '速率',
        type: 'line',
        data: [],
        smooth: true,
        symbolSize: 6,
        lineStyle: { width: 2 },
        itemStyle: { color: '#9ef40a' }
      }]
    res.data.forEach(item=>{
      series[0].data.push(item.score)
      series[1].data.push(Math.ceil(item.trainTime / 60))
      series[2].data.push(item.speed)
    })
    myChart10 = echarts.init(document.getElementById('trainCharts10'));
    myChart10.clear()
    let option = {
      grid: {
        containLabel: true,
        left: '0',
        bottom: '30px',
        right: '0',
        top: '50px'
      },
      title: {
        text: '近10次数据分析',
        left: 'center',
        textStyle: {
          fontSize: 18,
          fontWeight: 'bold',
          color:color.color1,
        }
      },
      tooltip: {
        trigger: 'axis',
        axisPointer: {
          type: 'shadow'
        },
        formatter:(e)=>{
          console.log(e);
          let a = '第 '+`<span style="color: #0a9a89;font-weight: bold">${e[0].name}</span>` + ' 次<br/>'
          a+=e[0].marker + ' 分数：' +e[0].data + '分<br/>'
          a+=e[1].marker + ' 用时：' +e[1].data + '分钟<br/>'
          a+=e[2].marker + ' 速率：' +e[2].data + '码/分<br/>'
          return a
        }
      },
      xAxis: {
        type: 'category',
        data: ['1', '2', '3', '4', '5', '6', '7', '8', '9', '10'],
        axisLabel: {
          rotate: 0
        },
      },
      yAxis: {
        type: 'value',
        name: '',
        axisLabel: {
          formatter: '{value} '
        },
        splitLine: {
          show: true,
          lineStyle: {
            type: 'dashed',
            color: color.color3,
            width: 1
          }
        },
      },
      series: series
    };
    myChart10.setOption(option);
  }
  return {
    trainType,trainData,changeTime,editPassword,userInfo,editPasswordData,time,activeKey,
    handleChangeTrainType,
    trainType10,changeTab,isHaveTrainData
  }
}