import {ref, computed, onMounted, onUnmounted, watch, nextTick} from "vue";
import {useRoute} from "vue-router"
import useMorse from "../../../../../../../common/mixin/useMorse.js";
import {partTimeFormatInfo,sum} from "../../../../../../../common/utils/Utils.js";
import {getElectronKeyZuXunPageNumber,updateElectronKeyPatTrainDetails} from "../../../../../../../common/api/electronKeyZuXun.js";
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
  const speedUnit = computed(() => scoreData.value.protocolVersion === 1 ? '四码组/分' : '码/分（历史）');
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
      updateElectronKeyPatTrainDetails({
        trainId: scoreData.value.trainId,
        userId: selfId
      }).then(res => {
        loading.value = false;
        if (res.code === 200) {
          for (let key in res.data) {
            scoreData.value[key] = res.data[key];
          }
          let arr = formatData(res.data.content)

          // res.data.content.forEach((item)=>{
          //   item.key=JSON.parse(item.key)
          //   item.time=JSON.parse(item.time??'[]')
          //   item.value=JSON.parse(item.value??'[]')
          // })
          // scoreData.value.content = res.data.content.slice(0,100)
          // scoreData.value.nextContent = res.data.content.slice(100,200)
          scoreData.value.content = arr.filter(item=>item.pageNumber===1)
          scoreData.value.nextContent = arr.filter(item=>item.pageNumber===2)
          scoreData.value.deductInfo = JSON.parse(scoreData.value.deductInfo);
          scoreData.value.ruleContent = JSON.parse(scoreData.value.ruleContent);
          if(res.data.isCable===1){
            scoreData.value.pag = res.data.pageCount
          }else {
            scoreData.value.pag = Math.ceil(scoreData.value.totalNumber/100);
          }
          scoreData.value.duration = partTimeFormatInfo(scoreData.value.activeMillis ?? scoreData.value.duration*1000, 'number');
          scoreData.value.duration = scoreData.value.duration.replace(/：/g, ':');
          trendLogKeyData.value = scoreData.value.content;
          patTotal.value = scoreData.value.pageAnalyzeVOS.map((item, index) => ({...item, pageNumber: item.pageNumber || index + 1})).sort((a, b) => a.pageNumber - b.pageNumber);
          resolve.value = [];
          totalTelegraghMsg();
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
  const moreLine = ref([])

  /**
   * 切换电报纸
   * @param num
   */
  const switchTelegram =async (num) => {
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

    let page =scoreData.value.currPage+num
    if(page>scoreData.value.pag || page < 1)return false
    const res = await getElectronKeyZuXunPageNumber({
      trainId: route.query.id,
      userId: selfId,
      pageNumber: page
    })
    if (res.code === 200) {
      let arr = formatData(res.data.messageVO)
      if(num==1){
        scoreData.value.nextContent = arr
      }else{
        scoreData.value.preContent = arr
      }
    }
    // getPostTrainKeyInfo(num)
    totalTelegraghMsg();
    return false
  };

  /**
   * 获取指定页的报底
   * @param
   */
  const getPostTrainKeyInfo = (num) => {
    let page =scoreData.value.currPage+num
    if(page>scoreData.value.pag || page < 1)return false
    getElectronKeyZuXunPageNumber({
      trainId: route.query.id,
      userId: selfId,
      pageNumber: page
    }).then(res => {
      if (res.code === 200) {
        let arr = formatData(res.data.messageVO)
        if(num==1){
          scoreData.value.nextContent = arr
        }else{
          scoreData.value.preContent = arr
        }
      }
    })
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
    scoreData.value.total = {cm: 0, sm: 0, dm: 0, sz: 0, dz: 0};
    scoreData.value.content.map(item => {
      if (item.value===null||item.value.length === 0) {
        // scoreData.value.total.sz++
      }  else if(item.key===null){
        scoreData.value.total.dz++
      }else if (item.key.length == 1 && item.key[0] == '#') {
        scoreData.value.total.dz++
      } else if (item.key.length > item.value.length) {
        scoreData.value.total.sm += item.key.length - item.value.length
      } else if (item.key.length < item.value.length) {
        scoreData.value.total.dm += item.value.length - item.key.length
      } else if (item.key.length === item.value.length && item.key.join('') !== item.value.join('')) {
        scoreData.value.total.cm++
      }
    })
  };

  /**
   * 渲染拍发码率折线图
   */
  const renderLineChart = () => {
    let data = [],speed = 0,xTxt = [];
    patTotal.value.map((item,i) => {
      speed = item.totalTime > 0 ? Number((item.patNumber * 60000 / item.totalTime / 4).toFixed(0)) : 0;
      xTxt.push('第'+item.pageNumber+'页');
      data.push(speed)
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
          formatter: '<div class="tooltipItem"><div>{b0}码率：</div><div>{c0} '+(scoreData.value.protocolVersion === 1 ? speedUnit.value : '组/分（历史）')+'</div></div>'
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
  //格式化数据
  const formatData = (data)=>{
    let arr = []
    data.forEach(item => {
      item.key = JSON.parse(item.key)
      item.time = JSON.parse(item.time)
      item.value = item.value==''?[]:JSON.parse(item.value)
      if(item.value!==null){
        if(item.value.join().indexOf("?")>-1){
          let str = ""
          item.value.forEach(item=>{
            str+=item
          })
          const codes = str.split("?")
          let s = ""
          codes.forEach(sn=>{
            if(sn.length>0){
              s = sn
            }
          })
          const scode = s.split('')
          if(scode.join()===item.key.join()){
            item.warning = true
          }else {
            item.error = true
          }
        }
        else if(item.key===null){
          moreLine.value.push(item)
        }
        else if(item.value.join().indexOf("bunchGroup")>-1){
          //串组
          item.bunchGroup = true
          item.value.shift()
        }
        else if(item.value.join().indexOf("more")>-1){
          //多码
          item.moreCode = true
          item.value.shift()
        }
        else if(item.value.join().indexOf("lack")>-1){
          //少码
          item.lackCode = true
          item.value.shift()
        }
        else if(item.value.join()!==item.key.join()&&item.time.length>0){
          item.error = true
        }
        else if(item.time.length===0){
          //少组
          item.lackgroup = true
        }
      }else {
        item.omission = true
      }
      let a = arr[arr.length-1]
      if(arr.length==0||arr[arr.length-1].sort!==item.sort){
        if(a?.list){
          a.list.sort((a,b)=>a[0]-b[0])
          let str = []
          a.list.forEach(item=>{
            item.shift()
            str.push(...item)
          })
          a.value = str
        }
        arr.push(item)
      }else {
        a.time.push(...item.time)
        if(a.list===undefined){
          a.list = [a.value]
          a.list.push(item.value)
        }else{
          a.list.push(item.value)
        }
      }
    })
    return arr
  }


  return {
    speedUnit,scoreData,loading,patHairTrendBoxRef,trendLogKeyData,resolve,moreLine,switchTelegram,seeCurrKeysHairTrend
  }
}







