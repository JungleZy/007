import {ref, onMounted, onUnmounted, watch, nextTick} from "vue";
import {useRoute} from "vue-router"
import useMorse from "../../../../../../../common/mixin/useMorse.js";
import {partTimeFormatInfo,sum} from "../../../../../../../common/utils/Utils.js";
import {getHandKeyZuXunPageNumber,getHandKeyZuXunDetails} from "../../../../../../../common/api/handkeyZuXun.js";
import * as echarts from "echarts"

export default function telegramList(showChart,selfId) {
  const loading = ref(true);
  const route = useRoute();
  let numberChart = null;
  let columnChart = null;
  let LineChart = null;
  const patHairTrendBoxRef = ref(null);
  const trendLogKeyData = ref([]);
  const {codeKey} = useMorse();
  const short = ref(null);
  const scoreData = ref({
    trainId: '',
    scale: {d: 1,l: 3,c: 1,w: 3,g: 5},
    currPage: 1,
    pag: 0,
    total: {
      sm: 0,
      dm: 0,
      sz: 0,
      dz: 0
    },
    telegraph: null,
  });
  const initSymbol = ref({
    alter: '001100',
    next: '0010,11',
  });
  const alter = ref(0);
  let timeChartData = ref([]);
  let numberChartData = ref([]);
  const userInfo = JSON.parse(localStorage.getItem('userInfo'))
  const successResolver = ref([])

  onMounted(() => {
    patHairTrendBoxRef.value.addEventListener('mousewheel', e => {
      if (e.deltaY > 0) {
        patHairTrendBoxRef.value.scrollLeft += 100;
      } else {
        patHairTrendBoxRef.value.scrollLeft -= 100;
      }
    });

    if (selfId == '') {
      selfId = userInfo.id
    }

    if (route.query.id && route.query.id !== '') {
      scoreData.value.trainId = route.query.id;
      getHandKeyZuXunDetails({
        id: scoreData.value.trainId,
        uid: selfId
      }).then(res => {
        loading.value = false;
        if (res.code === 200) {
          for (let key in res.data) {
            scoreData.value[key] = res.data[key];
          }
          scoreData.value.messageBody = [];
          scoreData.value.standards = [];
          scoreData.value.ruleContent = JSON.parse(scoreData.value.ruleContent);
          if(res.data.isCable===1){
            scoreData.value.pag = res.data.pageCount
          }else {
            scoreData.value.pag = Math.ceil(scoreData.value.messageNumber/100);
          }
          scoreData.value.userInfoList.forEach((item, i) => {
            if (item.userId == selfId) {
              if (scoreData.value.pag < item.existNumber.length) {
                scoreData.value.pag = item.existNumber.length;
              }
              scoreData.value.accuracy = item.accuracy;
              scoreData.value.score = item.score;
              scoreData.value.errorNumber = item.speed==null?0:item.errorNumber;
              scoreData.value.speed = item.speed==null?0:item.speed;
              scoreData.value.deductInfo = JSON.parse(item.deductInfo);
              scoreData.value.statisticInfo = JSON.parse(item.statisticInfo);
              scoreData.value.speedLog = item.speedLog;
              scoreData.value.validTime = partTimeFormatInfo(item.validTime * 1000, 'number');
              scoreData.value.validTime = scoreData.value.validTime.replace(/：/g, ':');
            }
          })
          short.value = (scoreData.value.type===1?'letter':scoreData.value.type===2?'mix':scoreData.value.codeSort?'long':'short');
          trendLogKeyData.value = [];
          getPostTrainKeyInfo(1)
          totalTelegraghMsg();
          if(scoreData.value.statisticInfo!==null){
            handleKeyTrendData(scoreData.value.statisticInfo);
          }

        }
      })
    }
  });

  onUnmounted(() => {
    numberChart = null;
    columnChart = null;
    LineChart = null;
  });

  watch(showChart,() => {
    if (showChart.value === 'line' && !LineChart) {
      renderLineChart()
    }
    if (showChart.value === 'column' && !columnChart) {
      renderColumnChart()
    }
    if (showChart.value === 'number' && !numberChart) {
      renderNumberChart()
    }
  });

  /**
   * 切换电报纸
   * @param type
   */
  const switchTelegram =async (type) => {
    if ((type === 'prev' && scoreData.value.currPage <= 1) ||
        (type === 'next' && scoreData.value.currPage >= scoreData.value.pag)) return false;
    if (type === 'prev') {
      scoreData.value.currPage --;
    }
    if (type === 'next') {
      scoreData.value.currPage ++;
    }
    if (scoreData.value.currPage == scoreData.value.messageBody.length && scoreData.value.currPage < scoreData.value.pag) {
      const res = await getPostTrainKeyInfo(scoreData.value.currPage+1)
    }
    totalTelegraghMsg();
    return false
  };

  const getPostTrainKeyInfo = async (page) => {
    const res = await getHandKeyZuXunPageNumber({
      id: route.query.id,
      userId: selfId,
      floorNumber: page
    })
    if (res.code === 200) {
      let obj = JSON.parse(res.data.resolver);
      if (obj.moreGroups) {
        obj['moreObj'] = {}
        obj.moreGroups.map(item => {
          if(item.message){
            obj['moreObj'][item.point+''] = item.message.join(',')
          }
        })
        successResolver.value.push(obj)
      }
      let result = resDataHandle(res.data.messageBody, JSON.parse(res.data.finishInfo))
      scoreData.value.messageBody.push(result)
      scoreData.value.standards.push(JSON.parse(res.data.standard))
      if (page == 1 && scoreData.value.messageBody.length == 1 && scoreData.value.pag > 1) {
        getPostTrainKeyInfo(2)
      }
    }
  };

  const resDataHandle = (leaf,finishInfo) => {
    let leafLogs = finishInfo&&finishInfo.patLogs?finishInfo.patLogs:[],
        pushNumber=0,cacheArr = [];

    if (leafLogs.length > 0) {
      leafLogs = leafLogs.filter((a,b) => b>0)
    }
    leaf = JSON.parse(leaf).map(item => {
      item['patLog'] = [];
      item.moresKey = item.moresKey=='#'?'#':JSON.parse(item.moresKey);
      item.patKeys = JSON.parse(item.patKeys);
      item.moresValue = JSON.parse(item.moresValue);
      item.moresTime = JSON.parse(item.moresTime);
      item.patLogs = JSON.parse(item.patLogs);
      if (leafLogs.length > 0) {
        item.moresTime.map(cod => {
          cacheArr = leafLogs.filter((log,lo) => lo>=pushNumber&&lo<(pushNumber+cod.length*2));
          item['patLog'].push(cacheArr);
          pushNumber += cod.length * 2
          if (pushNumber > leafLogs.length) {
            item['patLog'][item['patLog'].length-1].push({key: 2, name: "间隔"+pushNumber, value: 1000})
          }
        })
      }
      return item
    });
    trendLogKeyData.value.push(leaf);
    return leaf
  }

  /**
   * 统计当前电报纸拍发数据信息
   */
  const totalTelegraghMsg = () => {
    scoreData.value.total = {sm: 0, dm: 0, sz: 0, dz: 0};
    if (scoreData.value.messageBody[scoreData.value.currPage-1]) {
      scoreData.value.messageBody[scoreData.value.currPage-1].map(item => {
        if (item.moresKey === '#') {
          scoreData.value.total.dz ++;
        } else if (typeof item.moresKey === 'string') {
          item.moresKey = JSON.parse(item.moresKey);
        }
        if (typeof item.patKeys === 'string') {
          item.patKeys = JSON.parse(item.patKeys);
        }
        if (item.patKeys.length === 0) {
          scoreData.value.total.sz ++;
        } else if (item.moresKey.length > item.patKeys.length && item.moresKey !== '#') {
          scoreData.value.total.sm += 4 - item.patKeys.length;
        } else if (item.moresKey.length < item.patKeys.length && item.moresKey !== '#') {
          scoreData.value.total.dm += item.patKeys.length - 4;
        }
      });
    }
  };

  /**
   * 渲染拍发次数分组柱状图
   */
  const renderNumberChart = () => {
    let data_max = [],data_min = [],data_perfect = [], xTxt = [];
    numberChartData.value.map((item) => {
      if (item.type === 'max') {
        xTxt.push(item.name);
        data_max.push(Number(item.value))
      }
      if (item.type === 'min') {
        data_min.push(Number(item.value))
      }
      if (item.type === 'perfect') {
        data_perfect.push(Number(item.value))
      }
    });

    nextTick(()=>{
      let xian=document.getElementById("numberChart")
      xian.removeAttribute('_echarts_instance_')
      numberChart = echarts.init(xian,null, {
        renderer: 'canvas',
        useDirtyRect: false
      });
      let option = {
        color: ['#6ebdff'],
        grid: {
          containLabel:true,
          top: 20, left: 0, right: 6, bottom: 0,
        },
        tooltip: {
          show: true,
          backgroundColor: 'rgba(29, 65, 88, .9)',
          borderColor: 'rgb(29, 65, 88)',
          padding: [5,10],
          textStyle: {color: ['#6ebdff'], fontSize: 12},
          formatter: function(parm) {
            let _html = '',
                cachData = numberChartData.value.filter(item => item.name===parm.name);
            cachData.map(item => {
              _html += '<div class="tooltipItem"><div>'+item.name+item.text+'：</div><div>'+item.value+' 次</div></div>'
            });
            return _html
          }
        },
        xAxis: [{
          type: 'category',
          axisLine: {lineStyle: {color: '#354971'}},
          axisLabel: {color: '#7b90af'},
          axisTick: {show: true,alignWithLabel: true},
          data: xTxt
        }],
        yAxis: [{
          type: 'value',
          axisLine: {lineStyle: {color: '#7b90af'}},
          splitLine: {lineStyle: {color: ['#1f2b46'], type: 'dashed'}}
        }],
        series:[
          {
            type: 'bar',
            data: data_max,
            barMaxWidth: 20,
            showBackground: true,
            backgroundStyle:{color: 'rgba(76,117,149,0.1)'},
            barGap: 0,
            itemStyle: {
              color: new echarts.graphic.LinearGradient(0,0,0,1,[
                {offset: 0, color: '#0055ff'},
                {offset: 1, color: '#9fc7d7'}
              ])
            }
          },
          {
            type: 'bar',
            data: data_min,
            barMaxWidth: 20,
            showBackground: true,
            backgroundStyle:{color: 'rgba(76,117,149,0.1)'},
            barGap: 0,
            itemStyle: {
              color: new echarts.graphic.LinearGradient(0,0,0,1,[
                {offset: 0, color: '#0055ff'},
                {offset: 1, color: '#9fc7d7'}
              ])
            }
          },
          {
            type: 'bar',
            data: data_perfect,
            barMaxWidth: 20,
            showBackground: true,
            backgroundStyle:{color: 'rgba(76,117,149,0.1)'},
            barGap: 0,
            itemStyle: {
              color: new echarts.graphic.LinearGradient(0,0,0,1,[
                {offset: 0, color: '#0055ff'},
                {offset: 1, color: '#9fc7d7'}
              ])
            }
          }
        ]
      };
      numberChart.setOption(option)
    })
  };

  /**
   * 渲染拍发用时柱状图
   */
  const renderColumnChart = () => {
    let data = [],xTxt = [];
    console.log(timeChartData.value)
    timeChartData.value.map((item) => {
      xTxt.push(item.name);
      data.push(Number(item.value))
    });

    nextTick(()=>{
      let xian=document.getElementById("columnChart")
      xian.removeAttribute('_echarts_instance_')
      columnChart = echarts.init(xian)

      let option = {
        color: ['#6ebdff'],
        grid: {
          containLabel:true,
          top: 20, left: 0, right: 6, bottom: 0,
        },
        tooltip: {
          show: true,
          trigger: 'axis',
          axisPointer:{type: 'shadow',shadowStyle:{color: 'rgba(53,73,113,0.2)'}},
          backgroundColor: 'rgba(29, 65, 88, .9)',
          padding: [5,10],
          borderColor: 'rgb(29, 65, 88)',
          textStyle: {color: '#6ebdff', fontSize: 12},
          formatter: '<div class="tooltipItem"><div>用时：</div><div>{c0} ms</div></div>'
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
        series:[
          {
            type: 'bar', data: data,
            barMaxWidth: 20,
            itemStyle: {
              color: new echarts.graphic.LinearGradient(0,0,0,1,[
                {offset: 0, color: '#0055ff'},
                {offset: 1, color: '#9fc7d7'}
              ])
            }
          }
        ]
      };
      columnChart.setOption(option)
    })
  };

  /**
   * 渲染拍发码率折线图
   */
  const renderLineChart = () => {
    let data = [],xTxt = [],_data = JSON.parse(scoreData.value.speedLog);
    _data.map((item,i) => {
      if (i < scoreData.value.pag) {
        xTxt.push('第'+(i+1)+'页');
        data.push(Number(item))
      }
    });
    if (scoreData.value.pag > _data.length) {
      for(let p=0; p< scoreData.value.pag; p++) {
        if (p >= _data.length) {
          xTxt.push('第'+(p+1)+'页');
          data.push(0)
        }
      }
    }

    nextTick(()=>{
      let xian=document.getElementById("lineChart")
      xian.removeAttribute('_echarts_instance_')
      LineChart = echarts.init(xian)

      let option = {
        color: ['#6ebdff'],
        grid: {
          containLabel:true,
          top: 20, left: 0, right: 6, bottom: 0,
        },
        tooltip: {
          show: true,
          trigger: 'axis',
          axisPointer:{lineStyle:{color: '#354971'}},
          backgroundColor: 'rgba(29, 65, 88, .9)',
          borderColor: '#0d4c93',
          padding: [5,10],
          textStyle: {color: '#6ebdff', fontSize: 12},
          formatter: '<div class="tooltipItem"><div>{b0}码率：</div><div>{c0}'+(scoreData.value.ruleContent.wpm.type?' wpm':' 码/分')+'</div></div>'
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
        dataZoom:[
          {
            type: "inside",
            startVlaue: 0,
            endValue: 20
          },
        ],
        series:[{ type: 'line', data: data }]
      };
      LineChart.setOption(option)
    })
  };

  /**
   * 拍发态势数据处理
   */
  const handleKeyTrendData = (obj) => {
    let chart_data,number_data,d_l,c_w_g;
    scoreData.value.scale.l = Number(parseFloat(obj.lineAvg/obj.dotAvg).toFixed(1));
    scoreData.value.scale.c = Number(parseFloat(obj.codeAvg/obj.dotAvg).toFixed(1));
    scoreData.value.scale.w = Number(parseFloat(obj.wordAvg/obj.dotAvg).toFixed(1));
    scoreData.value.scale.g = Number(parseFloat(obj.groupAvg/obj.dotAvg).toFixed(1));

    scoreData.value.scale.d = obj.dotAvg==0?0:1;
    scoreData.value.scale.l = isNaN(scoreData.value.scale.l)?0:scoreData.value.scale.l;
    scoreData.value.scale.c = isNaN(scoreData.value.scale.c)?0:scoreData.value.scale.c;
    scoreData.value.scale.w = isNaN(scoreData.value.scale.w)?0:scoreData.value.scale.w;
    scoreData.value.scale.g = isNaN(scoreData.value.scale.g)?0:scoreData.value.scale.g;
    d_l = '1 : '+scoreData.value.scale.l;
    c_w_g = scoreData.value.scale.c+' : '+scoreData.value.scale.w+' : '+scoreData.value.scale.g;
    chart_data = [
      {name: '点',value: obj.dotAvg,d_l: d_l,c_w_g: c_w_g},
      {name: '划',value: obj.lineAvg,d_l: d_l,c_w_g: c_w_g},
      {name: '码间隔',value: obj.codeAvg,d_l: d_l,c_w_g: c_w_g},
      {name: '词间隔',value: obj.wordAvg,d_l: d_l,c_w_g: c_w_g},
      {name: '组间隔',value: obj.groupAvg,d_l: d_l,c_w_g: c_w_g},
    ];
    number_data = [
      {name: '点',value: obj.dotMaxNumber,text: '粗',type: 'max'},
      {name: '点',value: obj.dotMinNumber,text: '虚',type: 'min'},
      {name: '点',value: obj.dotPerfectNumber,text: '完美',type: 'perfect'},
      {name: '划',value: obj.lineMaxNumber,text: '长',type: 'max'},
      {name: '划',value: obj.lineMinNumber,text: '短',type: 'min'},
      {name: '划',value: obj.linePerfectNumber,text: '完美',type: 'perfect'},
      {name: '码间隔',value: obj.codeMaxNumber,text: '过大',type: 'max'},
      {name: '码间隔',value: obj.codeMinNumber,text: '过小',type: 'min'},
      {name: '码间隔',value: obj.codePerfectNumber,text: '完美',type: 'perfect'},
      {name: '词间隔',value: obj.wordMaxNumber,text: '过大',type: 'max'},
      {name: '词间隔',value: obj.wordMinNumber,text: '过小',type: 'min'},
      {name: '词间隔',value: obj.wordPerfectNumber,text: '完美',type: 'perfect'},
      {name: '组间隔',value: obj.groupMaxNumber,text: '过大',type: 'max'},
      {name: '组间隔',value: obj.groupMinNumber,text: '过小',type: 'min'},
      {name: '组间隔',value: obj.groupPerfectNumber,text: '完美',type: 'perfect'},
    ];
    timeChartData.value = chart_data;
    numberChartData.value = number_data;
  };

  /**
   * 点击查看当前报文拍发用时明细
   * @param key
   * @param i
   */
  const seeCurrKeysHairTrend = (key,i) => {
    let idTxt = 'ht_'+scoreData.value.currPage+'_'+(i+1)+'_'+key.moresKey.join('');
    nextTick(() => {
      let ht = ref(document.getElementById(idTxt));
      patHairTrendBoxRef.value.scrollTo({left: ht.value.offsetLeft-80, behavior: 'smooth'});
    });

  };


  return {
    scoreData,loading,patHairTrendBoxRef,trendLogKeyData,short,successResolver,switchTelegram,seeCurrKeysHairTrend
  }
}







