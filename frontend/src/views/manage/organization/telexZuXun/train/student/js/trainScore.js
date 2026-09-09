import {ref, onMounted, onUnmounted, watch, nextTick} from "vue";
import {useRoute} from "vue-router"
import useMorse from "../../../../../../../common/mixin/useMorse.js";
import {partTimeFormatInfo,sum} from "../../../../../../../common/utils/Utils.js";
import {endPatDetail, getDatagramZuXunPageNumber} from '../../../../../../../common/api/datagramZuXun.js'
import * as echarts from "echarts"
import { deepClone } from '../../../../../../../common/utils/Utils'

export default function telegramList(showChart,selfId) {
  const loading = ref(true);
  const route = useRoute();
  let numberChart = null;
  let columnChart = null;
  let LineChart = null;
  const patHairTrendBoxRef = ref(null);
  const trendLogKeyData = ref([]);
  const patTotal = ref([]);
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
  const resolve = ref([])

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
      scoreData.value.trainId = Number(route.query.id);
      endPatDetail({
        trainId: scoreData.value.trainId,
        userId: selfId,
        pageNumber: 1
      }).then(res => {
        loading.value = false;
        if (res.code === 200) {
          for (let key in res.data) {
            scoreData.value[key] = res.data[key];
          }
          res.data.content.forEach(item => {
            item.key = JSON.parse(item.key)
            item.time = JSON.parse(item.time ?? '[]')
            item.value = JSON.parse(item.value ?? '[]')
          })
          scoreData.value.content = res.data.content.slice(0, 100)
          scoreData.value.nextContent = res.data.content.slice(100, 200)
          scoreData.value.deductInfo = JSON.parse(res.data.deductInfo || '{}')
          scoreData.value.ruleContent = JSON.parse(res.data.ruleContent || '{}')
          scoreData.value.pag = res.data.pageCount || Math.ceil(scoreData.value.totalNumber / 100)
          scoreData.value.duration = partTimeFormatInfo((scoreData.value.duration || 0) * 1000, 'number').replace(/：/g, ':')
          trendLogKeyData.value = scoreData.value.content
          patTotal.value = res.data.pageAnalyzeVOS || []
          resolve.value = [{message: [], moreGroups: [], moreObj: {}, moreLine: []}]
          if (scoreData.value.pag > 1) resolve.value.push({message: [], moreGroups: [], moreObj: {}, moreLine: []})
          totalTelegraghMsg()
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
  });

  /**
   * 切换电报纸
   * @param num
   */
  const switchTelegram = (num) => {
    if ((scoreData.value.currPage <= 1 && num < 0) || (num > 0 && scoreData.value.currPage >= scoreData.value.pag)) return false;
    scoreData.value.currPage+=num
    if (num ==1) {
      scoreData.value.preContent=deepClone(scoreData.value.content)
      scoreData.value.content=deepClone(scoreData.value.nextContent)
    }else{
      scoreData.value.nextContent=deepClone(scoreData.value.content)
      scoreData.value.content=deepClone(scoreData.value.preContent)
    }
    trendLogKeyData.value = scoreData.value.content;
    getPostTrainKeyInfo(num)
    totalTelegraghMsg();
  };

  /**
   * 获取指定页的报底
   * @param
   */
  const getPostTrainKeyInfo = num => {
    const page = scoreData.value.currPage + num
    if (page > scoreData.value.pag || page < 1) return
    getDatagramZuXunPageNumber({
      trainId: scoreData.value.trainId,
      userId: selfId,
      pageNumber: page
    }).then(res => {
      if (res.code !== 200) return
      const content = res.data.messageContent.map(item => ({
        ...item,
        key: JSON.parse(item.key),
        time: JSON.parse(item.time ?? '[]'),
        value: JSON.parse(item.value ?? '[]')
      }))
      if (num === 1) scoreData.value.nextContent = content
      else scoreData.value.preContent = content
    })
  }


  /**
   * 统计当前电报纸拍发数据信息
   */
  const totalTelegraghMsg = () => {
    scoreData.value.total = {cm: 0, sm: 0, dm: 0, sz: 0, dz: 0};
    scoreData.value.content.map(item => {
      if (item.value.length === 0) {
        scoreData.value.total.sz ++;
      } else if (item.key.length == 1 && item.key[0] == '#') {
        scoreData.value.total.dz ++
      } else if (item.key.length > item.value.length) {
        scoreData.value.total.sm += item.key.length - item.value.length;
      } else if (item.key.length < item.value.length) {
        scoreData.value.total.dm += item.value.length - item.key.length;
      } else if (item.key.length === item.value.length && item.key.join('') !== item.value.join('')) {
        scoreData.value.total.cm ++;
      }
    });
  };

  /**
   * 渲染拍发码率折线图
   */
  const renderLineChart = () => {
    let data = [],speed = 0,xTxt = [];
    patTotal.value.map((item,i) => {
      speed = Number(parseFloat(item.patNumber/(item.totalTime/60/1000)).toFixed(0));
      xTxt.push('第'+(i+1)+'页');
      data.push(isNaN(speed)?0:speed)
    });

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
          formatter: '<div class="tooltipItem"><div>{b0}码率：</div><div>{c0}'+' 码/分'+'</div></div>'
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
   * 点击查看当前报文拍发用时明细
   * @param key
   * @param i
   */
  const seeCurrKeysHairTrend = (key,i) => {
    let idTxt = 'ht_'+scoreData.value.currPage+'_'+(i+1)+'_'+key.key.join('');
    nextTick(() => {
      let ht = ref(document.getElementById(idTxt));
      patHairTrendBoxRef.value.scrollTo({left: ht.value.offsetLeft-80, behavior: 'smooth'});
    });
  };


  return {
    scoreData,loading,patHairTrendBoxRef,trendLogKeyData,resolve,switchTelegram,seeCurrKeysHairTrend
  }
}







