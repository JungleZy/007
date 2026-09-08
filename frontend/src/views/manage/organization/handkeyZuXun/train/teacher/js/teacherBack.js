import {onMounted, ref, onUnmounted,nextTick} from "vue";
import {getHandKeyZuXunDetails,resetHandKeyZuXunStatistics} from "../../../../../../common/http/api/OrganizationApi.js";
import PublicSocket from '../../../../../../common/websocket/PublicSocket.js'
import { useRoute } from 'vue-router'
import * as echarts from "echarts"

export default function () {
  const route = useRoute()
  const userInfo = JSON.parse(localStorage.getItem('userInfo'))
  const fileUrl = ref(window.fileUrl)
  const trainTimeRef = ref(null);
  const trainTimer = ref(null);
  const loading = ref(true)
  const trainId = ref(0)
  const trainData = ref({
    trainId: 0,
    status: 0
  })
  const userPatData = ref([])
  const activeUserId = ref(null)
  const chartTabIndex = ref(1)
  const {ws_connect,sendMessage,closeWebSocket} = PublicSocket();
  const chartData = ref({
    pie: [],
    column: {
      xAxis: ['点', '划', '点划脱节', '字间隔', '组间隔'],
      min_lab: ['虚','短','过小','过小','过小'],
      max_lab: ['粗','长','过大','过大','过大'],
      min: [12,13,21,18,14],
      max: [15,16,11,14,62]
    },
    line: {
      xAxis: [],
      curr: [],
      next: [],
      prev: []
    }
  });
  let pieChart = null;
  let columnChart = null
  let LineChart = null;
  let _data = [
    {name: '福建省军区翟华靖',thisScore: 69,lastScore: 73,nextScore: 70},
    {name: '空军兰州基地基指尹星照',thisScore: 86,lastScore: 88,nextScore: 80},
    {name: '武警江西省总队管浩彬',thisScore: 80,lastScore: 87,nextScore: 84},
    {name: '四川省军区吴荣发',thisScore: 88,lastScore: 89,nextScore: 80},
    {name: '东部战区海军航空兵潘浩鑫',thisScore: 70,lastScore: 71,nextScore: 75},
    {name: '空军乌鲁木齐基地基指刘金华',thisScore: 80,lastScore: 83,nextScore: 86},
    {name: '东部战区海军李澳博',thisScore: 76,lastScore: 72,nextScore: 70},
    {name: '陆军第76集团军张海波',thisScore: 82,lastScore: 85,nextScore: 78},
    {name: '南疆军区刘宇亭',thisScore: 85,lastScore:88,nextScore: 80},
    {name: '陆军第73集团军余长源',thisScore: 84,lastScore: 85,nextScore: 71},
    {name: '陆军勤务支援第73旅丁嘉洛',thisScore: 87,lastScore: 78,nextScore: 72},
    {name: '海军护卫舰第11师史向阳',thisScore: 78,lastScore: 80,nextScore: 71},
    {name: '武警上海总队张晓鹏',thisScore: 75,lastScore: 77,nextScore: 70},
    {name: '西部战区联指王嘉杰',thisScore: 96,lastScore: 92,nextScore: 82},
    {name: '战支信息通信第二旅刘雪莱',thisScore: 93,lastScore: 90,nextScore: 82},
    {name: '昌都军分区王宇航',thisScore: 95,lastScore: 87,nextScore: 80},
    {name: '阿克苏军分区符春林',thisScore: 96,lastScore: 94,nextScore: 85},
    {name: '陆军工程防化旅李欣泽',thisScore: 98,lastScore: 90,nextScore: 85},
    {name: '东部战区陆军',thisScore: 93,lastScore: 87,nextScore: 80},
    {name: '东部战区联指王泽远',thisScore: 95,lastScore: 90,nextScore: 83},
    {name: '武警福建省总队柳宇龙',thisScore: 91,lastScore: 85,nextScore: 79},
  ]

  onMounted(() => {
    if (route.query.id && route.query.id !== '') {
      trainId.value = route.query.id * 1
      getZuXunTrainDetails();
    }

    _data.forEach(item => {
      chartData.value.line.xAxis.push(item.name);
      chartData.value.line.prev.push(item.lastScore);
      chartData.value.line.curr.push(item.thisScore);
      chartData.value.line.next.push(item.nextScore);
    })
  })

  onUnmounted(() => {
    closeWebSocket()
    let dom = null;
    userPatData.value.forEach((item,i) => {
      dom = document.getElementById('patValBox'+i)
      if (dom) {
        dom.removeEventListener('mousewheel', e => {});
      }
    })
  })

  /** 获取训练详情 */
  const getZuXunTrainDetails = () => {
    getHandKeyZuXunDetails({
      id: trainId.value
    }).then(res => {
      loading.value = false;
      if (res.code === 200) {
        res.data.userInfoList = res.data.userInfoList.filter(user => {
          user.userStatus = (user.userStatus==null?0:user.userStatus)
          return user.userId != userInfo.id
        })
        userPatData.value = res.data.userInfoList.map((user,u) => {
          return {
            index: u,
            name: user.userName,
            id: user.userId,
            img: user.userImg,
            log: []
          }
        })
        trainData.value = res.data
        if (trainData.value.status < 2) {
          connectWebsocket();
          patLogsBoxAddEvent();
          if (trainData.value.status == 1) {
            initTrainTimeInfo();
          }
        } else {
          trainData.value.userInfoList = trainData.value.userInfoList.sort((x,y)=>y.isFinish-x.isFinish);
          // timeAreaShow(trainData.value.validTime * 1000);
          timeAreaShow(571 * 1000);
          trainStatistics()
        }
      }
    })
  }

  /** websocket连接 */
  const connectWebsocket = () => {
    const url =`/generalTickerPat/${userInfo.id}/${trainId.value}`
    ws_connect(url, receiveWebSocketMessage)
  }

  const receiveWebSocketMessage = (e) => {
    let data = JSON.parse(JSON.parse(e.data).data);
    // console.log(data)
    if(data.topic == 'online'){
      trainData.value.userInfoList.forEach(item=>{
        if(item.userId == data.id && item.isFinish != 1){
          item.userStatus = 1;
        }
      })
    }else if (data.topic == 'offline') {
      trainData.value.userInfoList.forEach(item=>{
        if(item.userId == data.id && item.isFinish != 1){
          item.userStatus = 0;
        }
      })
    }else if (data.topic == 'ready') {
      trainData.value.userInfoList.forEach(item=>{
        if(item.userId == data.id && item.isFinish != 1){
          item.userStatus = 2;
        }
      })
    } else if (data.topic == 'finish') {
      trainData.value.userInfoList.forEach(item=>{
        if(item.userId == data.id){
          item.isFinish = 1;
        }
      })
    } else if (data.topic == 'pat') {
      let dom = null;
      userPatData.value.forEach((item, i)=>{
        if(item.id == data.id){
          dom = document.getElementById('patValBox'+i)
          item.log.push(data.log)
        }
      })
      if (dom !== null) {
        nextTick(() => {
          dom.scrollLeft = dom.scrollWidth;
        })
      }
    }
  }

  /** 获取训练统计信息 */
  const trainStatistics = () => {
    resetHandKeyZuXunStatistics({id:trainId.value}).then(res => {
      if (res.code === 200) {
        chartData.value.pie = [
          {name: "70分以下", value:res.data.schoolReport.belowStandard.peopleNumber},
          {name: "70分-90分", value: res.data.schoolReport.good.peopleNumber},
          {name: "90分以上", value: res.data.schoolReport.nice.peopleNumber}
        ]
        /*chartData.value.column.min = [
          res.data.errorInfoVO.dotMin,
          res.data.errorInfoVO.lineMin,
          res.data.errorInfoVO.codeGapMin,
          res.data.errorInfoVO.wordGapMin,
          res.data.errorInfoVO.groupGapMin,
        ]
        chartData.value.column.max = [
          res.data.errorInfoVO.dotMax,
          res.data.errorInfoVO.lineMax,
          res.data.errorInfoVO.codeGapMax,
          res.data.errorInfoVO.wordGapMax,
          res.data.errorInfoVO.groupGapMax,
        ]*/
        /*res.data.userTendencyVO.forEach(item => {
          chartData.value.line.xAxis.push(item.userName);
          chartData.value.line.prev.push(item.lastScore);
          chartData.value.line.curr.push(item.thisScore);
        })*/
        getChartDataSource(1);
      }
    })
  }

  /**
   * 训练时间转换显示
   */
  const initTrainTimeInfo = () => {
    if (!trainData.value.validTime) {
      trainData.value.validTime = 0
    }
    trainTimer.value = setInterval(() => {
      trainData.value.validTime += 1;
      timeAreaShow(trainData.value.validTime * 1000);
    },1000);
  };

  /**
   * 时间区域显示
   */
  const timeAreaShow = (t) => {
    let h,m,s;
    h = Math.floor(t / 1000 / 60 / 60 % 24);
    m = Math.floor(t / 1000 / 60 % 60);
    s = Math.floor(t / 1000 % 60);
    h = h > 9 ? h + "" : '0' + h;
    m = m > 9 ? m + "" : '0' + m;
    s = s > 9 ? s + "" : '0' + s;
    trainTimeRef.value.h = h;
    trainTimeRef.value.m = m;
    trainTimeRef.value.s = s;
  };

  /** 学员拍发态势容器添加事件 */
  const patLogsBoxAddEvent = () => {
    nextTick(() => {
      let dom = null;
      userPatData.value.forEach((item,i) => {
        dom = document.getElementById('patValBox'+i)
        dom.addEventListener('mousewheel', e => {
          if (e.deltaY > 0) {
            dom.scrollLeft += 100;
          } else {
            dom.scrollLeft -= 100;
          }
        });
      })
    })
  }

  /** 开始训练 */
  const startTrain = () => {
    trainData.value.status = 1
    sendMessage({topic: 'begin',})
    initTrainTimeInfo()
  }

  /** 结束训练 */
  const endTrain = () => {
    trainData.value.status = 2
    sendMessage({topic: 'end'})
    clearInterval(trainTimer.value)
    trainStatistics();
  }

  /** 查看学员成绩 */
  const seeStudentScore = (user) => {
    if (trainData.value.status < 2) return false;
    activeUserId.value = (user.userId==activeUserId.value?null:user.userId)

    if (activeUserId.value == null) {
      getChartDataSource(1);
    }
  }

  /** 统计图表切换显示 */
  const getChartDataSource = (type) => {
    chartTabIndex.value = type
    if (type === 1) {
      applyPieChart()
    } else if (type == 2) {
      renderColumnChart();
    } else if (type == 3) {
      renderLineChart();
    }
  }

  /** 成绩分布饼图 */
  const applyPieChart = () => {
    nextTick(()=>{
      let pie=document.getElementById("scoreChart")
      pie.removeAttribute('_echarts_instance_')
      pieChart = echarts.init(pie);
      let option = {
        tooltip:{
          backgroundColor: 'rgba(24, 45, 86, .9)',
          borderColor: 'rgb(24,45,86)',
          formatter:(e)=>{return e.marker+e.data.name+"  "+e.data.value+"人"+"  "+Math.round(Number(e.percent))+"%"},
          textStyle:{color: '#6ebdff',fontSize: 16}
        },
        legend: {top: 'top', textStyle: {color: '#ffffff',fontSize: 15}},
        grid: {
          containLabel:true,
          top: 20, left: 0, right: 0, bottom: 0,
        },
        series:[{
          color:["#15a33e","#ff963b","#6ebdff"],
          type:"pie",
          radius: ['30%', '70%'],
          label: {
            formatter: (e)=>{return e.data.name+"  "+e.data.value+"人"+"  "+Math.round(Number(e.percent))+"%"},
            fontSize: 18,
            fontWeight: 'bolder',
            color: '#ffffff'
          },
          itemStyle: {
            borderRadius: 5
          },
          // data:chartData.value.pie
          data:[
            {name: "70分以下", value: 10},
            {name: "70分-90分", value: 29},
            {name: "90分以上", value: 44 }
          ]
        }]
      }
      pieChart.setOption(option)
    })
  }

  /** 错情统计柱状图 */
  const renderColumnChart = () => {
    nextTick(()=>{
      let column=document.getElementById("columnChart")
      column.removeAttribute('_echarts_instance_')
      columnChart = echarts.init(column);
      let option = {
        grid: {
          containLabel:true,
          top: 50, left: 10, right: 10, bottom: 10,
        },
        tooltip: {
          backgroundColor: 'rgba(24, 45, 86, .9)',
          padding: [5,10],
          textStyle: {color: ['#ffffff'], fontSize: 14},
          formatter: function(parm) {
            let index = chartData.value.column.xAxis.indexOf(parm.name);
            let _html = '<div class="tooltipItem" style="color: #4ed000"><div>'+parm.name+chartData.value.column.max_lab[index]+
              '：</div><div>'+chartData.value.column.max[index]+' %</div></div>'+
              '<div class="tooltipItem" style="color: #2ab0e5"><div>'+parm.name+chartData.value.column.min_lab[index]+
              '：</div><div>'+chartData.value.column.min[index]+' %</div></div>';
            return _html;
          }
        },
        xAxis: [{
          axisTick: { show: true },
          axisLine: { lineStyle: { color: 'rgba(255,255,255, .2)' } },
          axisLabel: { textStyle: { fontSize: 16, color: '#fff'  }, },
          data: chartData.value.column.xAxis
        }],
        yAxis: [
          {
            axisTick: { show: false },
            axisLine: { show: false, },
            splitLine: { lineStyle: { color: 'rgba(255,255,255, .05)' } },
            axisLabel: { textStyle: { fontSize: 16, color: '#fff' } }
          },
          {
            show: true,
            max: 100,
            splitLine: { show:false },
            axisLine: { show: false },
            axisTick: { show: false },
            axisLabel: {
              textStyle: { fontSize: 16, color: '#fff' },
              formatter: params => {
                return `${params}%`
              }
            }
          }
        ],
        series:[
          {
            z: 1,
            type: 'pictorialBar',
            symbolPosition: 'end',
            data: chartData.value.column.max,
            symbol : 'diamond',
            symbolOffset: ['-62%', '-50%'],
            symbolSize: [29, 19],
            itemStyle: {
              borderColor: '#4ed000',
              color: '#4ed000'
            },
          },{
            z: 1,
            type: 'bar',
            barWidth: 30,
            // barGap: '-50%',
            label: {
              show: true,
              position: 'top',
              formatter: '{c}%',
              fontSize: 16,
              fontWeight: 'bolder',
              color: '#ffffff',
              offset: [-35, 0]
            },
            data: chartData.value.column.max,
            itemStyle: {
              color: {
                type: 'linear',
                x: 0, x2: 1, y: 0, y2: 0,
                colorStops: [
                  { offset: 0, color: 'rgba(78, 208, 0, .7)' },
                  { offset: 0.5, color: 'rgba(78, 208, 0, .7)' },
                  { offset: 0.5, color: 'rgba(78, 208, 0, .3)' },
                  { offset: 1, color: 'rgba(78, 208, 0, .3)' }
                ]
              }
            },
          },{
            z: 2,
            type: 'pictorialBar',
            symbolPosition: 'end',
            data: chartData.value.column.min,
            symbol : 'diamond',
            symbolOffset: ["62%", '-50%'],
            symbolSize: [29, 19],
            itemStyle: {
              borderColor: '#2ab0e5',
              color: '#2ab0e5'
            },
          },{
            z: 2,
            type: 'bar',
            barWidth: 30,
            label: {
              show: true,
              position: 'top',
              formatter: '{c}%',
              fontSize: 16,
              color: '#ffffff',
              fontWeight: 'bolder',
              offset: [35, 0]
            },
            data: chartData.value.column.min,
            itemStyle: {
              color: {
                type: 'linear',
                x: 0, x2: 1, y: 0, y2: 0,
                colorStops: [
                  { offset: 0, color: 'rgba(42, 176, 229, .7)' },
                  { offset: 0.5, color: 'rgba(42, 176, 229, .7)' },
                  { offset: 0.5, color: 'rgba(42, 176, 229, .3)' },
                  { offset: 1, color: 'rgba(42, 176, 229, .3)' }
                ]
              }
            },
          },
        ]
      };
      columnChart.setOption(option)
    })
  };

  /** 成绩态势折线图 */
  const renderLineChart = () => {
    nextTick(()=>{
      let line=document.getElementById("lineChart")
      line.removeAttribute('_echarts_instance_')
      LineChart = echarts.init(line);
      let option = {
        legend: {top: 'top', textStyle: {color: '#ffffff',fontSize: 18}},
        grid: {
          containLabel:true,
          top: 50, left: 10, right: 10, bottom: 10,
        },
        tooltip: {
          trigger: 'axis',
          backgroundColor: 'rgba(24, 45, 86, .9)',
          borderColor: '#0d4c93',
          padding: [5,10],
          textStyle: {color: '#6ebdff', fontSize: 16},
          // formatter: '<div class="tooltipItem"><div>平均：</div><div>{c0} 分</div></div>'
        },
        color:["#6ebdff","#ff963b","#a1fc6a"],
        xAxis: {
          type: 'category',
          data: chartData.value.line.xAxis,
          boundaryGap: true,
          axisLine: {lineStyle: {color: '#354971'}},
          axisLabel: { textStyle: { fontSize: 16, color: '#fff'  }, },
          axisTick: {alignWithLabel: true}
        },
        yAxis: {
          type: 'value',
          axisLine: {lineStyle: {color: '#7b90af'}},
          splitLine: {
            show: true,
            lineStyle: {color: ['rgba(123,144,175, .5)'], type: 'dashed'}
          },
          axisLabel: { textStyle: { fontSize: 16, color: '#fff' } }
        },
        series:[
          {
            name:"5月第3周",
            type: 'line',
            smooth: true,
            label: {
              show: true,
              position: 'top',
              formatter: '{c}分',
              fontSize: 16,
              fontWeight: 'bolder',
              color: '#ffffff'
            },
            areaStyle:{
              color: {
                type:'linear',x: 0,y:0,x2:0,y2:1,
                colorStops:[
                  {offset: 0, color: 'rgba(110,189,255,.3)'},
                  {offset: 1, color: 'transparent'}
                ]
              }
            },
            lineStyle: {
              normal: {width: 4}
            },
            data: chartData.value.line.curr
          },
          {
            name:"5月第2周",
            type:'line',
            smooth: true,
            // label: {
            //   show: true,
            //   position: 'top',
            //   formatter: '{c}分',
            //   fontSize: 16,
            //   fontWeight: 'bolder',
            //   color: '#ffffff'
            // },
            data:chartData.value.line.prev
          },
          {
            name:"5月第1周",
            type:'line',
            smooth: true,
            // label: {
            //   show: true,
            //   position: 'top',
            //   formatter: '{c}分',
            //   fontSize: 16,
            //   fontWeight: 'bolder',
            //   color: '#ffffff'
            // },
            data:chartData.value.line.next
          }
        ]
      };
      LineChart.setOption(option)
    })
  };

  return {
    trainTimeRef,loading,trainData,fileUrl,userPatData,activeUserId,chartTabIndex,startTrain,endTrain,seeStudentScore,getChartDataSource
  }
}