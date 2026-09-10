import {onMounted, onUnmounted, ref,onBeforeUnmount,nextTick,watch} from "vue";
import {useRouter, useRoute} from "vue-router"
import {getBasicSetting,saveHandKeyBasicTrain} from "../../../../../../common/api/TelegramApi.js";
import {sum} from "../../../../../../common/utils/Utils.js";
import * as echarts from "echarts";

export default function () {
  const barrageBoxRef = ref(null);
  const showBarrageBox = ref(true);
  const nowTime = ref({h1:0,h2:0,m1:0,m2:0,s1:0,s2:0});
  const trainTimer = ref(null);
  const barrageTimer = ref(null);
  const areaChart = ref(null);
  const currTrainTab = ref('dot');
  const trainLogData = ref({
    barrage: {dot: [], line: []},
    logData: {dot: [], line: []},
    chartData: {dot: [], line: []},
    dot: {min: 0, max: 0, average: 0},
    line: {min: 0, max: 0, average: 0}
  });
  const standard = ref({dot: [], line: []});
  const range = ref({dot: [], line: []});
  const annotations = ref({dot: [], line: []});
  const averArr = ref({dot:[],line:[]});
  const totalData = ref([]);
  const sustainTime = ref(0);
  const router = useRouter();

  onMounted(() => {
    let time = 0;
    trainTimer.value = setInterval(() => {
      time += 1000;
      sustainTime.value +=1000;
      timeAreaShow(time);
    },1000);
    annotations.value = {dot: [],line: []};
    getBasicSetting().then((res) => {
      if (res.code === 200) {
        res.data.map(item => {
          item.value = JSON.parse(item.value);
          if (item.value.type === 0) {
            range.value[item.key==='0'?'dot':'line'] = [item.value.min.slice(1), item.value.max.slice(1)];
            annotations.value[item.key==='0'?'dot':'line'].push([
              {yAxis: item.value.max.slice(1),itemStyle: {color: '#262239'}},
              {valueDim: 'y'},
            ]);
            annotations.value[item.key==='0'?'dot':'line'].push([
              {valueDim: 'y'},
              {yAxis: item.value.min.slice(1),itemStyle: {color: '#262239'}}
            ])
          } else {
            annotations.value[item.key==='0'?'dot':'line'].push([
              {yAxis: item.value.max},
              {yAxis: item.value.min,itemStyle: {color: item.value.type===2?'#263139':'#172939'}}
            ])
          }
        });
        standard.value.dot = res.data.filter(item => item.key === '0');
        standard.value.line = res.data.filter(item => item.key === '1');
        standard.value.line.map(item => {
          totalData.value.push({
            name: item.value.name,
            type: item.value.type,
            num: 0
          })
        });
        handleKeyLogChart(trainLogData.value.chartData[currTrainTab.value])
      }
    });
    if (barrageBoxRef.value) {
      showBarrageBox.value = (barrageBoxRef.value.clientHeight>=100);
    }
  });

  onBeforeUnmount(() => {
    saveHandKeyBasicTrain({
      totalKnockNumber: trainLogData.value.chartData.dot.length + trainLogData.value.chartData.line.length,
      sustainTime: sustainTime.value.toString()
    }).then()
  });

  onUnmounted(() => {
    cancelAnimationFrame(rafId);
    clearInterval(trainTimer.value);
  });
  /**
   * 组合式函数
   * 实时监测浏览器刷新率FPS
   *
   * FPS值可以帮助开发者识别性能瓶颈，以优化应用的性能
   *
   * @returns {Object} 返回一个包含 FPS 值的 ref 对象
   */
  let lastTime = performance.now(); // 上一次帧的时间戳
  let frameCount = 0;                // 统计帧数
  let fps =ref(0);
  let rafId
  const useFps = ()=> {
   const now = performance.now();   // 当前时间戳
   frameCount++
   // 每秒更新一次 FPS
   if (now >= lastTime + 1000) {
     lastTime = now
     fps.value = frameCount;              // 1 秒内渲染了多少帧
     frameCount = 0;                // 重置计数
     console.log('FPS:', fps.value);      // 或者更新到 UI
     lastTime = now;                // 重新计时
   }
    rafId = requestAnimationFrame(useFps);     // 继续下一帧
 }
  rafId = requestAnimationFrame(useFps);
  watch(fps,()=>{
    if(fps.value>50&&isFirst){
      isFirst = false
      showChart()
    }
  },{deep:true})

  const dotLineLog = ref([])
  let xData = [],yData = [];
  let arr = []
  let isFirst = false //判断监听只渲染一次
  const changeChartData = (val) => {
    isFirst = true
    arr = []
    xData = []
    yData = []
    let ch = barrageBoxRef.value?barrageBoxRef.value.clientHeight:0,
        top = parseInt(Math.random()*(ch - 85) + 5),
        curr = null;

    for (let item of standard.value[currTrainTab.value]) {
      if (item.value.type > 0 && val > item.value.min && val <= item.value.max) {
        curr = item.value;
        break;
      }
    }

    if (curr === null) {
      curr = standard.value[currTrainTab.value].filter(item => item.value.type===0)[0].value;
    }
    for (let row of totalData.value) {
      if (row.type === curr.type) {
        row.num++;
      }
    }
    trainLogData.value.logData[currTrainTab.value].unshift({
      value: val,
      type: curr.type,
      name: curr.name,
      msg: curr.msg,
      top: ch>100?top:10
    });
    dotLineLog.value = trainLogData.value.logData[currTrainTab.value].slice(0,5)
    trainLogData.value.barrage[currTrainTab.value].push({
      value: val,
      type: curr.type,
      name: curr.name,
      msg: curr.msg,
      top: ch>100?top:10
    });
    trainLogData.value.chartData[currTrainTab.value].push({
      name: (trainLogData.value.chartData[currTrainTab.value].length + 1).toString(),
      value: val,
      type: curr.type,
      text: curr.name
    });
    if (trainLogData.value.chartData[currTrainTab.value].length <= 60) {
      arr = trainLogData.value.chartData[currTrainTab.value];
    } else {
      arr = trainLogData.value.chartData[currTrainTab.value].filter((item, index) => (trainLogData.value.chartData[currTrainTab.value].length-index)<=60);
    }

    averArr.value[currTrainTab.value].push(val);
    if (trainLogData.value[currTrainTab.value].min === 0) {
      trainLogData.value[currTrainTab.value].min = val;
      trainLogData.value[currTrainTab.value].max = val;
      trainLogData.value[currTrainTab.value].average = val;
    } else {
      trainLogData.value[currTrainTab.value].min = val<trainLogData.value[currTrainTab.value].min?val:trainLogData.value[currTrainTab.value].min;
      trainLogData.value[currTrainTab.value].max = val>trainLogData.value[currTrainTab.value].max?val:trainLogData.value[currTrainTab.value].max;
      trainLogData.value[currTrainTab.value].average = parseInt(sum(averArr.value[currTrainTab.value])/averArr.value[currTrainTab.value].length);
    }
    if (barrageTimer.value) {
      clearTimeout(barrageTimer.value)
    }
    barrageTimer.value = setTimeout(() => {
      trainLogData.value.barrage[currTrainTab.value] = [];
    },15000);
    // showChart()
  };

  const showChart = ()=>{
    arr.map((item) => {
      xData.push(item.name);
      yData.push(Number(item.value))
    });
    areaChart.value.setOption({
      grid: {
        top: 20
      },
      xAxis: {
        data: xData
      },
      series: [{
        name: 'time',
        data: yData
      }]
    })
  }
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
    nowTime.value.h1 = h.substring(0, 1) * 1;
    nowTime.value.h2 = h.substring(1, 2) * 1;
    nowTime.value.m1 = m.substring(0, 1) * 1;
    nowTime.value.m2 = m.substring(1, 2) * 1;
    nowTime.value.s1 = s.substring(0, 1) * 1;
    nowTime.value.s2 = s.substring(1, 2) * 1;
  };

  /**
   * 手键拍发记录日志图表
   * @param chartData
   */
  const handleKeyLogChart = (chartData) => {
    if (areaChart.value) {
      areaChart.value = null;
    }
    let xTxt = [],data = [];
    chartData.map((item) => {
      xTxt.push(item.name);
      data.push(Number(item.value))
    });

    nextTick(()=>{
      let xian=document.getElementById("patChartLog")
      xian.removeAttribute('_echarts_instance_')
      areaChart.value = echarts.init(xian)

      let option = {
        color: ['#6ebdff'],
        grid: {
          containLabel:true,
          top: 20, left: 0, right: 1, bottom: 0,
        },
        tooltip: {
          show: true,
          backgroundColor: 'rgba(29, 65, 88, .9)',
          borderColor: '#0d4c93',
          padding: [5,10],
          textStyle: {color: '#6ebdff', fontSize: 12},
          formatter: (res) => {
            if (res.value && chartData[res.dataIndex] && chartData[res.dataIndex].text) {
              return '<div class="tooltipItem"><div>评定：</div><div>'+chartData[res.dataIndex].text+'</div></div>'+
                     '<div class="tooltipItem"><div>用时：</div><div>'+res.value+' ms</div></div>'
            } else {
              return ''
            }
          }
        },
        xAxis: {
          type: 'category',
          data: xTxt,
          boundaryGap: true,
          axisLine: {lineStyle: {color: '#354971'}},
          axisLabel: {color: '#7b90af'},
          axisTick: {alignWithLabel: true}
        },
        yAxis: {
          type: 'value',
          axisLine: {lineStyle: {color: '#7b90af'}},
          splitLine: {
            show: true,
            lineStyle: {color: ['#1f2b46'], type: 'dashed'}
          }
        },
        series:[{
          name: 'time',
          type: 'line',
          data: data,
          areaStyle: {
            color: new echarts.graphic.LinearGradient(0,0,0,1,[
              {offset: 0, color: 'rgba(99,174,237,.3)'},
              {offset: 1, color: 'rgba(33, 49, 79,.3)'}
            ])
          },
          markArea: {
            data: annotations.value[currTrainTab.value]
          }
        }]
      };
      areaChart.value.setOption(option)
    })
  };

  /**
   * 切换练习方式
   * @param way
   */
  const changeTrainWay = (way) => {
    if (way === currTrainTab.value) return false;
    currTrainTab.value = way;
    if (trainLogData.value.barrage[currTrainTab.value].length > 0) {
      trainLogData.value.barrage[currTrainTab.value] = [];
    }
    let arr = trainLogData.value.chartData[currTrainTab.value].filter((item, index) => (trainLogData.value.chartData[currTrainTab.value].length-index)<=60);
    handleKeyLogChart(arr);
    dotLineLog.value = []
  };

  /**
   * 返回
   */
  const goBack = ()=>{
    router.go(-1)
  };

  return {
    nowTime, barrageBoxRef, range, showBarrageBox, currTrainTab, trainLogData,totalData,standard,dotLineLog,
    changeChartData, changeTrainWay, goBack
  }
}