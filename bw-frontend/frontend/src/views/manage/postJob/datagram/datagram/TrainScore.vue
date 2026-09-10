<template>
  <div class="w-full h-full content-mask-bg relative">
    <div class="loading" v-show="loading">
      <a-spin size="large" tip="正在努力加载..." />
    </div>
    <div class="main w-full h-full">
      <div class="main_t">
        <div class="dataBox">
          <div class="top">
            <template v-if="interfaceStyle==='HJ'">
              <img v-if="parseFloat(scoreData.score)>80" :src="scoresuperb" class="img">
              <img v-else-if="parseFloat(scoreData.score)>60" :src="scoresuffice" class="img">
              <img v-else :src="scorefailed" class="img">
              <div class="name nobr" style="font-size: 12px">{{scoreData.name}}</div>
            </template>
            <img v-else :src="scoreSuperbHJJ" class="img" />
            <div :class="{score:true,superb:parseFloat(scoreData.score)>80,suffice:parseFloat(scoreData.score)>60}">{{ scoreData.score }}</div>
          </div>
          <div class="bottom" style="justify-content: space-around">
            <div class="resGroup">
              <img :src="resaccuracy" class="ico" />
              <div class="cont">
                <div class="desc">正确率</div>
                <div class="num">{{ scoreData.accuracy }}%</div>
              </div>
            </div>
            <!--<div class="resGroup">
              <img :src="resspeed" class="ico">
              <div class="cont">
                <div class="desc">速度</div>
                <div class="num">{{ scoreData.speed }}<strong style="font-size: 16px;">组/分</strong></div>
              </div>
            </div>-->
            <div class="resGroup">
              <img :src="restime" class="ico" />
              <div class="cont">
                <div class="desc">时间</div>
                <div class="num">{{ computationTime(scoreData.validTime) }}</div>
              </div>
            </div>
          </div>
          <div class="chartBox">
            <div class="tabs">
              <div :class="{ 'tab colu': true, on: showChart == 'total' }" @click="changeChart('total')">
                <img :src="chartIcoOn3" v-show="showChart == 'total'" class="ico" />
                <img :src="chartIco3" v-show="showChart != 'total'" class="ico" />
                统计
              </div>
            </div>
            <div class="box">
              <div class="totalTable" v-show="showChart == 'total'">
                <div class="head">
                  <div class="item lab"></div>
                  <div class="item full">值</div>
                  <div class="item full">扣分情况</div>
                </div>
                <template v-if="scoreData.deductInfo">
                  <div class="row">
                    <div class="item lab full">码率</div>
                    <div class="item">{{ scoreData.speed?scoreData.speed:0 }} 组/分</div>
                    <div class="item">{{ scoreData.deductInfo.speedScore?scoreData.deductInfo.speedScore*1:'--' }} 分</div>
                  </div>
                  <div class="row">
                    <div class="item lab full">错码</div>
                    <div class="item">{{ scoreData.deductInfo.errorCodeNumber? scoreData.deductInfo.errorCodeNumber:0 }} 个</div>
                    <div class="item">{{ scoreData.deductInfo.errorCodeScore?scoreData.deductInfo.errorCodeScore*1:'--' }} 分</div>
                  </div>
                  <div class="row">
                    <div class="item lab full">多少行</div>
                    <div class="item">{{ scoreData.deductInfo.muchLessLineNumber?scoreData.deductInfo.muchLessLineNumber:0 }} 行</div>
                    <div class="item">{{ scoreData.deductInfo.muchLessLineScore?scoreData.deductInfo.muchLessLineScore*1:'--' }} 分</div>
                  </div>
                  <div class="row">
                    <div class="item lab full">多少组</div>
                    <div class="item">{{ scoreData.deductInfo.muchLessGroupsNumber?scoreData.deductInfo.muchLessGroupsNumber:0 }} 组</div>
                    <div class="item">{{ scoreData.deductInfo.muchLessGroupsScore?scoreData.deductInfo.muchLessGroupsScore*1:'--' }} 分</div>
                  </div>
                  <div class="row">
                    <div class="item lab full">多少码</div>
                    <div class="item">{{ scoreData.deductInfo.muchLessCodeNumber?scoreData.deductInfo.muchLessCodeNumber:0 }} 组</div>
                    <div class="item">{{ scoreData.deductInfo.muchLessCodeScore?scoreData.deductInfo.muchLessCodeScore*1:'--' }} 分</div>
                  </div>
                  <div class="row">
                    <div class="item lab full">少回行</div>
                    <div class="item">{{ scoreData.deductInfo.lessReturnLineNumber?scoreData.deductInfo.lessReturnLineNumber:0 }} 组</div>
                    <div class="item">{{ scoreData.deductInfo.lessReturnLineScore?scoreData.deductInfo.lessReturnLineScore*1:'--' }} 分</div>
                  </div>
                  <div class="row">
                    <div class="item lab full">少页标</div>
                    <div class="item">{{ scoreData.deductInfo.lessPageNumber?scoreData.deductInfo.lessPageNumber:0 }} 个</div>
                    <div class="item">{{ scoreData.deductInfo.lessPageScore?scoreData.deductInfo.lessPageScore*1:'--' }} 分</div>
                  </div>
                  <div class="row">
                    <div class="item lab full">页标错</div>
                    <div class="item">{{ scoreData.deductInfo.errorPageNumber?scoreData.deductInfo.errorPageNumber:0 }} 个</div>
                    <div class="item">{{ scoreData.deductInfo.errorPageScore?scoreData.deductInfo.errorPageScore*1:'--' }} 分</div>
                  </div>
                  <div class="row">
                    <div class="item lab full">不归</div>
                    <div class="item">{{ scoreData.deductInfo.nonStandartNumber?scoreData.deductInfo.nonStandartNumber:0}} 个</div>
                    <div class="item">{{ scoreData.deductInfo.nonStandartScore?scoreData.deductInfo.nonStandartScore*1:'--' }} 分</div>
                  </div>
                  <div class="row">
                    <div class="item lab full">改错</div>
                    <div class="item">{{ scoreData.deductInfo.correctMistakesNumber?scoreData.deductInfo.correctMistakesNumber:0 }} 个</div>
                    <div class="item">{{ scoreData.deductInfo.correctMistakesScore?scoreData.deductInfo.correctMistakesScore*1:'--' }} 分</div>
                  </div>
                </template>
              </div>
              <div class="chart overflow-auto" v-show="showChart == 'column'">
                <div id="columnChart" class="totalChart"></div>
              </div>
              <div class="chart overflow-auto" v-show="showChart == 'line'">
                <div id="lineChart" class="totalChart"></div>
              </div>
            </div>
          </div>
        </div>
        <div class="telegraphBox">
          <div style="height: 60%">
            <div class="totalBox">
              <div class="layout-left-center w-full">
                <div class="total">
                  电报纸数：<span class="num">{{ page.pageAll }}</span>
                </div>
                <div class="total">
                  错误总数：<span class="num">{{ scoreData.errorNumber }}</span>
                </div>
                <!--<div class="total">
                  平均码率：<span class="num">{{ scoreData.speed }}</span>
                </div>-->
              </div>
              <div class="page">
                <div :class="{ pag: true, disabled: page.current == 1 }" v-debounce="{fn:pageTurn,data:-1}">上一页</div>
                <div :class="{ pag: true, disabled: page.current == page.pageAll }"  v-debounce="{fn:pageTurn,data:1}">下一页</div>
              </div>
            </div>
            <div class="patTelegraph">
              <div class="telegraph">
                <div class="rowHead">
                  <div class="key">1</div>
                  <div class="key">2</div>
                  <div class="key">3</div>
                  <div class="key">4</div>
                  <div class="key">5</div>
                  <div class="key">6</div>
                  <div class="key">7</div>
                  <div class="key">8</div>
                  <div class="key">9</div>
                  <div class="key">10</div>
                </div>
                <div class="keyBox">
                  <template v-for="(key, index) in 100" :key="key">
                    <template v-if="scoreData.content[index]">
                      <div
                        :class="[
                          scoreData.content[index].value == scoreData.content[index].key || (index % 100 == 99 && scoreData.content[index].value == scoreData.content[index].key + '-' + page.current)
                            ? ''
                            : scoreData.content[index].value == '' || scoreData.content[index].value == null
                            ? 'omission'
                            : 'error',
                             scoreData.content[index].list?'more':''
                        ]"
                        class="key"
                      >
                        <template v-if="scoreData.content[index].value != scoreData.content[index].key || (index % 100 == 99 && scoreData.content[index].value == scoreData.content[index].key + '-' + page.current)">
                          <div class="keyOmis" v-if="scoreData.content[index].value == ''">
                            <div class="box">
                              <div class="text">漏拍</div>
                            </div>
                          </div>
                          <div class="keyErr" :style="{left:scoreData.content[index].list&&index%10===9?-scoreData.content[index].list.length*50+'px':''}" v-else>
                            <template v-if="scoreData.content[index].list">
                              <div class="box">
                                <div class="text">
                                  <template v-for="(item,index) of scoreData.content[index].list" :key="index">
                                    &nbsp;
                                    <template v-for="(codes, i) of item[1]" :key="i">
                                      <span>{{ codes }}</span>
                                    </template>
                                  </template>
                                </div>
                              </div>
                            </template>
                            <div class="box" v-else>
                              <div class="text">{{ scoreData.content[index].value }}</div>
                            </div>
                          </div>
                        </template>
                        {{ scoreData.content[index].key }}
                      </div>
                    </template>
                    <div v-else class="key"></div>
                  </template>
                </div>
              </div>
              <div class="serial">
                <div class="ser head"></div>
                <div class="ser">10</div>
                <div class="ser">20</div>
                <div class="ser">30</div>
                <div class="ser">40</div>
                <div class="ser">50</div>
                <div class="ser">60</div>
                <div class="ser">70</div>
                <div class="ser">80</div>
                <div class="ser">90</div>
                <div class="ser">100</div>
              </div>
            </div>
            <div class="totalDesc layout-side-n">
              <div class="text">实际拍发报文:</div>
              <div class="text">页码：【{{ page.current }}】</div>
            </div>
          </div>
          <div style="height: 40%" v-if="testData.codeAll.length>0">
            <a-textarea v-model:value="testData.codeAll" disabled style="height: 100%; resize: none; word-spacing: 10px; border: 1px solid #384c77; background: #0a1936"></a-textarea>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
  export default {
    name: 'HandKeyTrainScore'
  }
</script>
<script setup>
  import { ref, onMounted, onUnmounted, watch } from 'vue'
  import { useRoute } from 'vue-router'
  import trainScore from './js/trainScore.js'
  import chartLab from '../../../../../assets/HJ/postTrain/chartLab.png'
  import scoresuffice from '../../../../../assets/HJ/postTrain/score-suffice.png'
  import scorefailed from '../../../../../assets/HJ/postTrain/score-failed.png'
  import resaccuracy from '../../../../../assets/HJ/postTrain/check.png'
  import resspeed from '../../../../../assets/HJ/train/res-speed.png'
  import restime from '../../../../../assets/HJ/postTrain/time.png'

  import chartIco3 from '../../../../../assets/HJ/train/chart-ico-3.png'
  import chartIcoOn3 from '../../../../../assets/HJ/train/chart-ico-3-on.png'
  import dataEmpty from '../../../../../assets/HJ/train/dataEmpty.png'
  import scoreSuperbHJJ from '../../../../../assets/HJ/postTrain/topBox-after.png'
  const interfaceStyle = window.interfaceStyle
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
  const patHairTrendBoxRef = ref(null)
  const activeMenu = ref(0)
  const selectMenu = index => {
    activeMenu.value = index
  }
  const { scoreData, loading, page, testData, pageTurn, changeChart, showChart, situaData } = trainScore(patHairTrendBoxRef)
</script>
<style scoped lang="less">
  @import "../../../../../common/styles/css/score";
  @import "./css/TrainScore.less";
  .totalTable .head,.totalTable .row{
    height: calc(100% / 11)!important;
  }
  .totalTable .head .lab:after{
    border-bottom-width: 6.1vh!important;
  }
  .totalTable .head .lab:before{
    border-bottom-width: 6.1vh!important;
  }
</style>
