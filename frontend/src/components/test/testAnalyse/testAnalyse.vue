<template>
  <div class="cont">
    <div class="head">
      <div class="item one" v-if="ranking[0]">
        <div class="num">{{ ranking[0].currentScore }} <span class="unit fs_dispose_1">分</span></div>
        <div class="name nobr">{{ ranking[0].userName }}</div>
      </div>
      <div class="item two" v-if="ranking[1]">
        <div class="num">{{ ranking[1].currentScore }} <span class="unit fs_dispose_1">分</span></div>
        <div class="name nobr">{{ ranking[1].userName }}</div>
      </div>
      <div class="item three" v-if="ranking[2]">
        <div class="num">{{ ranking[2].currentScore }} <span class="unit fs_dispose_1">分</span></div>
        <div class="name nobr">{{ ranking[2].userName }}</div>
      </div>
    </div>
    <div class="boxs">
      <div class="c">
        <div class="w-full h-full" id="contrastChart"></div>
      </div>
      <div class="lrBox">
        <div class="lr l">
          <div class="w-full h-full" id="scoreChart"></div>
        </div>
        <div class="line l"></div>
        <div class="line r"></div>
        <div class="lr overflow-auto r">
          <template v-if="props.analyseData && props.analyseData.errorTop3">
            <template v-for="(item, i) in props.analyseData.errorTop3">
              <div class="item">
                <!-- v-if="item.topic && item.topic != ''"-->
                <div class="text nobr-2">[{{ typeList[item.type - 1] }}]-{{ item.topic }}</div>
                <div class="num">{{ item.number }}</div>
              </div>
            </template>
          </template>
        </div>
      </div>
    </div>
  </div>
</template>
<script>
export default {
  name: 'TestAnalyse'
}
</script>
<script setup>
import { ref, toRefs, onMounted, defineProps, nextTick } from 'vue'
import { createFromIconfontCN } from '@ant-design/icons-vue'
import { deepClone } from '../../../common/utils/Utils'
import * as echarts from 'echarts'

let pieChart = null
let lineChart = null
const ranking = ref([])
const typeList = ref(['单选', '多选', '判断', '填空', '简答'])
const props = defineProps({
  analyseData: Object
})

onMounted(() => {
  let yuan = document.getElementById('scoreChart')
  yuan.removeAttribute('_echarts_instance_')
  let xian = document.getElementById('contrastChart')
  xian.removeAttribute('_echarts_instance_')
  pieChart = echarts.init(yuan)
  lineChart = echarts.init(xian)
  ranking.value = deepClone(props.analyseData.scoreList)
  ranking.value = ranking.value.sort((x, y) => y.currentScore - x.currentScore)
  nextTick(() => {
    applyPieChart()
    applyLineChart()
  })
})

const applyPieChart = () => {
  let source = [
    { name: '60分以下', value: props.analyseData.failing },
    { name: '60分-80分', value: props.analyseData.ordinary },
    { name: '80分以上', value: props.analyseData.good }
  ]
  let option = {
    tooltip: {
      formatter: e => {
        return e.marker + e.data.name + '  ' + e.data.value + '人'
      }
    },
    legend: { top: 'top', textStyle: { color: '#b4d5f0' } },
    series: [
      {
        color: ['#6ebdff', '#ff963b', '#1bc74d'],
        label: { show: false },
        type: 'pie',
        radius: '70%',
        data: source
      }
    ]
  }
  nextTick(() => {
    pieChart.setOption(option)
  })
}

const applyLineChart = () => {
  let x_text = [],
    curr_score = [],
    prev_score = []
  props.analyseData.scoreList.map(item => {
    x_text.push(item.userName)
    curr_score.push(item.currentScore)
    prev_score.push(item.previousScore)
  })
  let option = {
    legend: { top: 'top', textStyle: { color: '#b4d5f0' } },
    grid: { containLabel: true, left: 0, bottom: 10, right: 0, top: 30 },
    tooltip: {
      trigger: 'axis'
    },
    color: ['#6ebdff', '#ff963b'],
    xAxis: {
      type: 'category',
      data: x_text,
      axisLabel: { color: '#a5b6d0' },
      position: { top: 20, bottom: 20 },
      axisLine: { lineStyle: { color: 'rgba(105,181,246,0.1)' } },
      axisTick: {}
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: 'rgba(105,181,246,0.1)' } },
      axisLabel: { color: '#a5b6d0' }
    },
    series: [
      {
        name: '本次成绩',
        type: 'line',
        areaStyle: {
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: 'rgba(110,189,255,.8)' },
              { offset: 1, color: 'transparent' }
            ]
          }
        },
        data: curr_score
      },
      {
        name: '上次成绩',
        type: 'line',
        data: prev_score
      }
    ]
  }
  nextTick(() => {
    lineChart.setOption(option)
  })
}
</script>

<style lang="less" scoped>
  .cont {
    width: 96%;
    margin: 88px auto 66px;
    height: 400px;
    position: relative;
    padding-top: 60px;
    background-image: linear-gradient(to left,rgba(0,0,0,0),#0a2149 15% 85%,rgba(0,0,0,0));
    &:before{
      content: '';
      position: absolute;
      top: 0px;
      width: 100%;
      height: 1px;
      background-image: linear-gradient(to left,rgba(0,0,0,0),#3f5a90,rgba(0,0,0,0));
    }
    &:after{
      content: '';
      position: absolute;
      bottom: 0px;
      width: 100%;
      height: 1px;
      background-image: linear-gradient(to left,rgba(0,0,0,0),#3f5a90,rgba(0,0,0,0));
    }
    .head {
      height: 92px;
      width: 100%;
      background: url("../../../assets/HJ/test/podium.png") no-repeat center;
      position: absolute;
      top: -37px;
      .item {
        width: 94px;
        height: 54px;
        background: url("../../../assets/HJ/test/podiumRank.png") no-repeat center;
        position: absolute;
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        &.one {
          left: calc(50% - 47px);
          top: -45px;
        }
        &.two {
          left: calc(50% - 194px);
          top: -27px;
        }
        &.three {
          left: calc(50% + 104px);
          top: -27px;
        }
        .name {
          color: #b4d5f0;
          font-size: 13px;
          text-align: center;
          line-height: 1;
          margin-top: 4px;
          width: 100%;
        }
        .num {
          font-size: 18px;
          text-align: center;
          background-image: linear-gradient(to bottom, #cdc370, #d38c1e);
          -webkit-background-clip: text;
          color: transparent;
          font-weight: bolder;
          line-height: 1;
          .unit {
            font-size: 13px;
          }
        }
      }
    }
    .boxs {
      max-width: 1700px;
      height: 100%;
      padding-bottom: 10px;
      margin: 0 auto;
      position: relative;
      .lrBox {
        .lr {
          width: 24%;
          flex-shrink: 0;
          padding: 0 12px;
          height: calc(100% - 10px);
          position: absolute;
          top: 0;
          &.l {
            left: 0;
          }
          &.r {
            right: 0;
          }
          .item {
            padding: 10px 0 6px;
            color: #a5b6d0;
            border-bottom: 1px solid #18345b;
            display: flex;
            align-items: center;
            justify-content: space-between;
            .text {
              padding-right: 20px;
            }
            .num {
              width: 40px;
              height: 16px;
              font-size: 12px;
              background: url("../../../assets/HJ/test/num-bg.png") no-repeat center;
              flex-shrink: 0;
              display: flex;
              align-items: center;
              justify-content: center;
              font-weight: bolder;
              color: #ff963b;
            }
          }
        }
        .line {
          position: absolute;
          width: 1px;
          flex-shrink: 0;
          height: 100%;
          background-image: linear-gradient(to top,transparent,#1c3a5a,transparent);
          margin: 0 8px;
          top: 0;
          &.l {
            left: 24%;
          }
          &.r {
            right: 24%;
          }
        }
      }
      .c {
        width: calc(52% - 18px);
        height: 100%;
        padding: 0 20px 0 12px;
        margin: 0 auto;
      }
    }
  }

  @media (max-width: 1260px) {
    .cont {
      height: 490px;
      .boxs {
        .c {
          height: 180px;
          width: 860px;
          margin: 0 auto;
        }
        .lrBox {
          display: flex;
          align-items: center;
          justify-content: space-between;
          width: 860px;
          margin: 0 auto;
          height: 240px;
          padding-top: 20px;
          .lr {
            width: 48%;
            height: 220px;
            float: right;
            position: static;
          }
          .line {
            position: static;
            height: 220px;
            margin: 0;
            & + .line {
              display: none;
            }
          }
        }
      }
    }
  }
</style>
