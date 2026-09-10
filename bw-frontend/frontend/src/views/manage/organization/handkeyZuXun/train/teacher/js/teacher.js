import {onMounted, ref, onUnmounted, onBeforeUnmount, nextTick} from "vue";
import {
  getHandKeyZuXunDetails,resetHandKeyZuXunStatistics,updateHandKeyTrainStatus
} from "../../../../../../../common/api/handkeyZuXun.js";
import PublicSocket from '../../../../../../../common/ws/PublicSocket.js'
import {PubSub} from "../../../../../../../common/utils/PubSub.js";
import { wsCode } from '../../../../../../../common/ws/Ws.js'
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
  const isFinish = ref(null)
  const chartTabIndex = ref(1)
  const scoreloading = ref(false)//结算加载
  let updateUserStatus = []
  const {ws_connect,sendMessage,closeWebSocket} = PublicSocket();
  const chartData = ref({
    pie: [],
    column: {
      xAxis: ['点', '划', '点划脱节', '字间隔', '组间隔'],
      min_lab: ['虚','短','过小','过小','过小'],
      max_lab: ['粗','长','过大','过大','过大'],
      min: [],
      max: []
    },
    line: {
      xAxis: [],
      curr: [],
      prev: [],
      prevPrev: []
    }
  });
  let pieChart = null;
  let columnChart = null
  let LineChart = null;

  onMounted(() => {
    if (route.query.id && route.query.id !== '') {
      trainId.value = route.query.id * 1
      getZuXunTrainDetails();
    }
  })
  onBeforeUnmount(() => {
    PubSub.publish('listenMainView', false)
  })
  onUnmounted(() => {
    PubSub.unsubscribe(wsCode.NOTIFICATION_TRAIN_RESULT)
    closeWebSocket()
    clearInterval(trainTimer.value)
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
          PubSub.subscribe(wsCode.NOTIFICATION_TRAIN_RESULT,res=>{
            userSubmitStatus(res)
          })
          connectWebsocket();
          patLogsBoxAddEvent();
          if (trainData.value.status == 1) {
            initTrainTimeInfo();
          }
          PubSub.publish('listenMainView', true)
        } else {
          trainData.value.userInfoList = trainData.value.userInfoList.sort((x,y)=>y.isFinish-x.isFinish);
          timeAreaShow(trainData.value.validTime * 1000);
          trainStatistics()
        }
      }
    })
  }

  /** websocket连接 */
  const connectWebsocket = () => {
    const url =`/generalTickerPat/${userInfo.id}/${trainId.value}/1`
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
    let obj = {};
    resetHandKeyZuXunStatistics({id:trainId.value}).then(res => {
      if (res.code === 200) {
        chartData.value.pie = [
          {name: "70分以下", value:res.data.schoolReport.belowStandard.peopleNumber},
          {name: "70分-90分", value: res.data.schoolReport.nice.peopleNumber},
          {name: "90分以上", value: res.data.schoolReport.good.peopleNumber}
        ]
        chartData.value.column.min = [
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
        ]
        res.data.userTendencyVO.forEach(item => {
          chartData.value.line.xAxis.push(item.userName);
          chartData.value.line.prevPrev.push(item.lastLastScore);
          chartData.value.line.prev.push(item.lastScore);
          chartData.value.line.curr.push(item.thisScore);
          obj[item.userId] = item.thisScore
        })
        trainData.value.userInfoList.map(user => {
          user.score = obj[user.userId]
        })
        getChartDataSource(1);
      }
    })
  }

  const userSubmitStatus = (data)=>{
    if(data.trainId!==trainId.value)return;
    if(updateUserStatus.length===0){
      trainData.value.userInfoList.forEach(item=>{
        if(item.userId == data.userId){
          item.isFinish = 1;
        }
      })
      scoreloading.value = false
      return
    }
    if(data.type==='ticker'){
      for (let i=0;i<updateUserStatus.length;i++){
        if(updateUserStatus[i]===data.userId){
          updateUserStatus.splice(i,1)
        }
      }
    }
    if(updateUserStatus.length===0){
      scoreloading.value = false
      trainData.value.status = 2
      trainStatistics();
      getZuXunTrainDetails()
    }
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
    updateHandKeyTrainStatusInfo(1)
  }

  /** 结束训练 */
  const endTrain = () => {
    scoreloading.value = true
    trainData.value.userInfoList.forEach(item=>{
      if(item.userStatus!==0&&item.isFinish!==1){
        updateUserStatus.push(item.userId)
      }
    })
    updateHandKeyTrainStatusInfo(2)
  }

  /**
   * 更新组训状态
   * @param status 1-开始；2-结束；
   */
  const updateHandKeyTrainStatusInfo = (status) => {
    updateHandKeyTrainStatus({
      trainId: trainId.value,
      status: status
    }).then(res => {
      if (res.code === 200) {
        if (status == 1) {
          trainData.value.status = 1
          sendMessage({topic: 'begin',})
          initTrainTimeInfo()
        } else if (status == 2) {
          sendMessage({topic: 'end'})
          clearInterval(trainTimer.value)
          if(updateUserStatus.length===0){
            scoreloading.value = false
            trainData.value.status = 2
            trainStatistics();
            getZuXunTrainDetails()
          }
        }
      }
    })
  }

  /** 查看学员成绩 */
  const seeStudentScore = (user) => {
    if (trainData.value.status < 2) return false;
    activeUserId.value = (user.userId==activeUserId.value?null:user.userId)
    isFinish.value = user.isFinish
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
          formatter:(e)=>{return e.marker+e.data.name+"  "+e.data.value+"人"+"  "+e.percent+"%"},
          textStyle:{color: '#6ebdff',fontSize: 16}
        },
        legend: {left: '5%', orient: "vertical", textStyle: {color: '#ffffff',fontSize: 17,lineHeight: 30}},
        grid: {
          containLabel:true,
          top: 20, left: 0, right: 0, bottom: 0,
        },
        series:[{
          color:["#15a33e","#ff963b","#6ebdff"],
          type:"pie",
          radius: ['30%', '70%'],
          label: {
            formatter: (e)=>{return e.data.name+"  "+e.data.value+"人"+"  "+e.percent+"%"},
            fontSize: 18,
            fontWeight: 'bolder',
            color: '#ffffff'
          },
          itemStyle: {
            borderRadius: 5
          },
          data:chartData.value.pie
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
        legend: {top: 'top', textStyle: {color: '#ffffff',fontSize: 15}},
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
                  {offset: 0, color: 'rgba(110,189,255,.8)'},
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
            label: {
              show: true,
              position: 'top',
              formatter: '{c}分',
              fontSize: 16,
              fontWeight: 'bolder',
              color: '#ffffff'
            },
            data:chartData.value.line.prev
          },
          {
            name:"5月第1周",
            type:'line',
            smooth: true,
            label: {
              show: true,
              position: 'top',
              formatter: '{c}分',
              fontSize: 16,
              fontWeight: 'bolder',
              color: '#ffffff'
            },
            data:chartData.value.line.prevPrev
          }
        ]
      };
      LineChart.setOption(option)
    })
  };

  return {
    trainTimeRef,loading,trainData,fileUrl,userPatData,activeUserId,chartTabIndex,isFinish,scoreloading,
    startTrain,endTrain,seeStudentScore,getChartDataSource
  }
}