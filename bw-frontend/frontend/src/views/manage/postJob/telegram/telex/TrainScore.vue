<template>
  <div class="w-full h-full content-mask-bg relative">
    <div class="loading" v-show="loading">
      <a-spin size="large" tip="正在努力加载..."/>
    </div>
    <div class="main w-full h-full">
      <div class="main_t">
        <div class="dataBox">
          <div class="top">
            <img :src="scoreSuperb" class="img">
            <div :class="{score:true,superb:parseFloat(scoreData.score)>80,suffice:parseFloat(scoreData.score)>60}">{{ scoreData.score }}</div>
          </div>
          <div class="bottom" style="justify-content: space-around">
            <div class="resGroup">
              <img :src="resaccuracy" class="ico">
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
              <img :src="restime" class="ico">
              <div class="cont">
                <div class="desc">时间</div>
                <div class="num">{{ computationTime(scoreData.validTime) }}</div>
              </div>
            </div>
          </div>
          <div class="chartBox">
            <div class="tabs">
              <div :class="{'tab colu': true, on: showChart=='total'}" @click="changeChart('total')">
                <img :src="chartIcoOn3" v-show="showChart == 'total'" class="ico">
                <img :src="chartIco3" v-show="showChart != 'total'" class="ico">
                统计
              </div>
              <div :class="{'tab': true, on: showChart=='column'}" @click="changeChart('column')">
                <img :src="chartIcoOn1" v-show="showChart == 'column'" class="ico">
                <img :src="chartIco1" v-show="showChart != 'column'" class="ico">
                用时(m)
              </div>
              <div :class="{'tab colu': true, on: showChart=='line'}" @click="changeChart('line')">
                <img :src="chartIcoOn2" v-show="showChart == 'line'" class="ico">
                <img :src="chartIco2" v-show="showChart != 'line'" class="ico">
                码率(组/分)
              </div>
            </div>
            <div class="box">
              <div class="totalTable" v-show="showChart=='total'">
                <div class="head">
                  <div class="item lab"></div>
                  <div class="item full">值</div>
                  <div class="item full">扣分情况</div>
                </div>
                <template v-if="scoreData.deductInfo">
                  <div class="row">
                    <div class="item lab full">码率</div>
                    <div class="item">{{scoreData.speed}} 组/分</div>
                    <div class="item" v-if="scoreData.deductInfo.speedLowNumber > 0">-{{scoreData.deductInfo.speedLowScore}} 分</div>
                    <div class="item" v-if="scoreData.deductInfo.speedOverTopNumber > 0">+{{scoreData.deductInfo.speedOverTopScore}} 分</div>
                  </div>
                  <div class="row" v-if="scoreData.deductInfo.moreLineNumber > 0">
                    <div class="item lab full">多行</div>
                    <div class="item">{{ scoreData.deductInfo.moreLineNumber }} 行</div>
                    <div class="item">-{{ scoreData.deductInfo.moreLineScore }} 分</div>
                  </div>
                  <div class="row" v-if="scoreData.deductInfo.lackLineNumber > 0">
                    <div class="item lab full">少行</div>
                    <div class="item">{{ scoreData.deductInfo.lackLineNumber }} 行</div>
                    <div class="item">-{{ scoreData.deductInfo.lackLineScore }} 分</div>
                  </div>
                  <div class="row" v-if="scoreData.deductInfo.moreGroupNumber > 0">
                    <div class="item lab full">多组</div>
                    <div class="item">{{ scoreData.deductInfo.moreGroupNumber }} 组</div>
                    <div class="item">-{{ scoreData.deductInfo.moreGroupScore }} 分</div>
                  </div>
                  <div class="row" v-if="scoreData.deductInfo.lackGroupNumber > 0">
                    <div class="item lab full">少组</div>
                    <div class="item">{{ scoreData.deductInfo.lackGroupNumber }} 组</div>
                    <div class="item">-{{ scoreData.deductInfo.lackGroupScore }} 分</div>
                  </div>
                  <div class="row" v-if="scoreData.deductInfo.errorGroupNumber > 0">
                    <div class="item lab full">错组</div>
                    <div class="item">{{ scoreData.deductInfo.errorGroupNumber }} 组</div>
                    <div class="item">-{{ scoreData.deductInfo.errorGroupScore }} 分</div>
                  </div>
                  <div class="row" v-if="scoreData.deductInfo.moreCodeNumber > 0">
                    <div class="item lab full">多码</div>
                    <div class="item">{{ scoreData.deductInfo.moreCodeNumber }} 组</div>
                    <div class="item">-{{ scoreData.deductInfo.moreCodeScore }} 分</div>
                  </div>
                  <div class="row" v-if="scoreData.deductInfo.lackCodeNumber > 0">
                    <div class="item lab full">少码</div>
                    <div class="item">{{ scoreData.deductInfo.lackCodeNumber }} 组</div>
                    <div class="item">-{{ scoreData.deductInfo.lackCodeScore }} 分</div>
                  </div>
                  <div class="row" v-if="scoreData.deductInfo.updateErrorNumber > 0">
                    <div class="item lab full">改错</div>
                    <div class="item">{{ scoreData.deductInfo.updateErrorNumber }} 组</div>
                    <div class="item">-{{ scoreData.deductInfo.updateErrorScore }} 分</div>
                  </div>
                  <div class="row" v-if="scoreData.deductInfo.lackReturnLineNumber > 0">
                    <div class="item lab full">少回行</div>
                    <div class="item">{{ scoreData.deductInfo.lackReturnLineNumber }} 行</div>
                    <div class="item">-{{ scoreData.deductInfo.lackReturnLineScore }} 分</div>
                  </div>
                  <div class="row" v-if="scoreData.deductInfo.lackPageMarkNumber > 0">
                    <div class="item lab full">少页标</div>
                    <div class="item">{{ scoreData.deductInfo.lackPageMarkNumber }} 个</div>
                    <div class="item">-{{ scoreData.deductInfo.lackPageMarkScore }} 分</div>
                  </div>
                  <div class="row" v-if="scoreData.deductInfo.pageMarkErrorNumber > 0">
                    <div class="item lab full">页标错</div>
                    <div class="item">{{ scoreData.deductInfo.pageMarkErrorNumber }} 个</div>
                    <div class="item">-{{ scoreData.deductInfo.pageMarkErrorScore }} 分</div>
                  </div>
                </template>

              </div>
              <div class="chart overflow-auto" v-show="showChart=='column'">
                <div id="columnChart" class="totalChart" ></div>
              </div>
              <div class="chart overflow-auto" v-show="showChart=='line'">
                <div id="lineChart" class="totalChart" ></div>
              </div>
            </div>
          </div>
        </div>
        <div class="telegraphBox">
          <div class="totalBox">
            <div class="layout-left-center w-full">
              <div class="total">电报纸数：<span class="num">{{ page.pageAll }}</span></div>
              <div class="total">错误总数：<span class="num">{{ scoreData.errorNumber }}</span></div>
              <div class="total">平均码率：<span class="num">{{ scoreData.speed }}</span></div>
              <div class="total">更正次数：<span class="num">{{ scoreData.change }}</span></div>
            </div>
            <div class="page">
              <div :class="{pag: true, disabled: page.current == 1}"
                   @click="pageTurn('prev')" >上一页</div>
              <div :class="{pag: true, disabled: page.current == page.pageAll}"
                   @click="pageTurn('next')">下一页</div>
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
                <template v-for="(key,index) in scoreData.content">
                  <div :class="[key.value==key.key||(index%100==99&&key.value==key.key+'-'+page.current)?'':key.value==''?'omission':'error']" class="key" >
                    <template v-if="key.value!=key.key||(index%100==99&&key.value==key.key+'-'+page.current)">
                      <div class="keyOmis" v-if="key.value==''">
                        <div class="text">漏拍</div>
                      </div>
                      <div class="keyErr" v-else>
                        <div class="text">{{ key.value }}</div>
                      </div>
                    </template>

                    {{ key.key}}
                  </div>
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
          <div class="totalDesc">
            <div class="text">页码：【{{page.current}}】</div>
          </div>
        </div>
      </div>
      <div class="main_b">
        <div class="title" style="height: 58px;">
          <div class="tabs">
            <!--<span class="tab active">拍发态势</span>-->
          </div>
          <div class="legend">
            <div class="leg dot">数码</div>
            <div class="leg gap">间隔</div>
<!--            <div class="leg omission">漏拍</div>-->
          </div>
        </div>
        <div class="patHairTrendBox">
          <div class="labs">
            <div class="_t" style="height: 20px;padding-right: 1px;">ms</div>
            <div class="_t" style="height: 22px;">用时</div>
            <div class="_t" style="height: 58px;">按键值</div>
            <div class="_t" style="height: 32px;">拍发报文</div>
          </div>
          <div class="patHairTrend" ref="patHairTrendBoxRef">
            <div v-if="!situaData[page.current-1] || situaData[page.current-1].length <= 0" class="layout-left-center">
              <img :src="dataEmpty" style="margin-left: 40px;">
            </div>
            <template v-for="(group, g) in situaData[page.current-1]">
              <div class="sep" v-if="g>0">
                <div class="times" v-if="group.time.length > 0">
                  <div class="time gap"
                       :style="{width:parseInt(group.time[0]/10)+'px'}">
                    <div class="num">{{group.time[0]}}</div>
                  </div>
                </div>
              </div>
              <div class="groupLog">
                <div class="codeContLog">
                  <template v-for="(word, w) in group.value">
                    <div class="sep" v-if="w>0">
                      <div class="times">
                        <div class="time gap" v-if="group.time[w]"
                             :style="{width:parseInt(group.time[w]/10)+'px'}">
                          <div class="num">{{group.time[w]}}</div>
                        </div>
                      </div>
                      <div class="keyGap"></div>
                    </div>
                    <div :class="{key: true, omiss: (group.value.length - group.key.length) > 0}">
                      <div class="times">
                        <div class="time key" style="width: 24px;" :style="[group.value[w]=='Alt'||group.value[w]=='Enter'?'width:60px':'']"></div>
                      </div>
                      <div :class="{keyName:true, err:group.value[w]!=group.key[w]}">
                        <!--<div class="text">{{group.value[w]}}</div>-->
                        {{group.value[w]||group.value[w]==0?group.value[w]:'#'}}
                      </div>
                    </div>
                  </template>
                  <template v-if="group.key.length > group.value.length">
                    <template v-for="(omi, o) in group.key">
                      <div class="key omission" v-if="o > (group.value.length - 1)">
                        <div class="omis"></div>
                        <div :class="{keyName:true, err:!group.value[o]}"></div>
                      </div>
                    </template>
                  </template>
                </div>
                <div class="name" v-if="group.key">{{group.key.join('')}}</div>
              </div>
            </template>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
  export default {
    name: "HandKeyTrainScore"
  }
</script>
<script setup>
  import {ref, onMounted, onUnmounted, watch} from "vue";
  import {useRoute} from "vue-router";
  import botBg from '../../../../../assets/HJ/postTrain/bottom.png';
  import topBg from '../../../../../assets/HJ/postTrain/top.png';
  import trainScore from "./js/trainScore.js";
  import keyBg from '../../../../../assets/HJ/postTrain/keys.png';
  import chartLab from '../../../../../assets/HJ/postTrain/chartLab.png';
  import scoresuffice from '../../../../../assets/HJ/postTrain/score-suffice.png';
  import scorefailed from '../../../../../assets/HJ/postTrain/score-failed.png';
  import resaccuracy from '../../../../../assets/HJ/postTrain/check.png';
  import resspeed from '../../../../../assets/HJ/train/res-speed.png';
  import restime from '../../../../../assets/HJ/postTrain/time.png';
  import chartIco1 from "../../../../../assets/HJ/train/chart-ico-1.png";
  import chartIcoOn1 from "../../../../../assets/HJ/train/chart-ico-1-on.png";
  import chartIco2 from "../../../../../assets/HJ/train/chart-ico-2.png";
  import chartIcoOn2 from "../../../../../assets/HJ/train/chart-ico-2-on.png";
  import chartIco3 from "../../../../../assets/HJ/train/chart-ico-3.png";
  import chartIcoOn3 from "../../../../../assets/HJ/train/chart-ico-3-on.png";
  import dataEmpty from '../../../../../assets/HJ/train/dataEmpty.png';
  import scoreSuperb from "../../../../../assets/HJ/postTrain/topBox-after.png";

  //格式化时间
  const computationTime = (total)=> {
    let hour;
    let min;
    let sec;
    let day;
    let h;
    let m;
    let s;
    hour = Math.floor(total / 60 / 60 % 24);
    min = Math.floor(total / 60 % 60);
    sec = Math.floor(total  % 60);
    day = Math.floor(total / 60 / 60 / 24);
    // 计算总小时数
    hour = hour + day * 24;
    if (hour < 10 && hour >= 0) {
      h = "0" + hour
    } else {
      h = hour.toString()
    }
    if (min < 10 && min >= 0) {
      m = "0" + min
    } else {
      m = min
    }
    if (sec < 10 && sec >= 0) {
      s = "0" + sec
    } else {
      s = sec
    }
    return h+" : "+m+" : "+s
  }
  const patHairTrendBoxRef = ref(null)
  const activeMenu = ref(0)
  const selectMenu = (index)=>{
    activeMenu.value = index
  }
  const { scoreData,loading,page,testData,pageTurn,changeChart,showChart,situaData} = trainScore(patHairTrendBoxRef);

</script>
<style scoped lang="less">

  .patHairTrend .times .time.key {
    border-top-color: #4c7595;
    background-color: #223241;
  }
  .patHairTrend .times .time.gap {
    border-top-color: #e2f2ff;
    background-color: rgba(226,242,255,.3);
    min-width: 12px;
    max-width: 500px;
  }

  .scoreBody {
    background-color: #122345;
  }
  .scoreBody:before, .scoreBody:after {
    content: '';
    width: 288px;
    position: absolute;
    top: 0;
    bottom: 0;
  }
  .scoreBody:before {
    background: url("../../../../../assets/HJ/basicTheory/studyManage/analyze/zuo.png") no-repeat left;
    background-size: auto 100%;
    left: 0;
  }
  .scoreBody:after {
    background: url("../../../../../assets/HJ/basicTheory/studyManage/analyze/you.png") no-repeat right;
    background-size: auto 100%;
    right: 0;
  }
  .main {
    padding: 0px 1.5% 12px 1.5%;
    position: relative;
    z-index: 9;
  }
  .main_t {
    width: 100%;
    height: calc(100% - 220px);
    display: flex;
  }
  .main_b {
    width: 100%;
    height: 220px;
  }
  .dataBox {
    width: 35%;
    min-width: 420px;
    max-width: 520px;
    flex-shrink: 0;
    height: 100%;
    display: flex;
    padding: 0 5px;
    flex-direction: column;
    background:url("../../../../../assets/HJ/postTrain/gardBg.png") no-repeat;
    background-size: 100% 100% ;
  }
  .telegraphBox {
    width: 100%;
    height: 100%;
    padding: 0 5px;
  }
  .dataBox .top {
    width: 100%;
    height: 162px;
    text-align: center;
    padding: 0 20px;
    flex-shrink: 0;
    position: relative;
  }
  .dataBox .top img {
    width: 400px;
    margin: 32px auto;
  }
  .dataBox .top .score {
    width: 100%;
    font-weight: bolder;
    font-size: 72px;
    line-height: 1;
    position: absolute;
    top: 0;
    left: 0;
    color: #ffae00;
    text-align: center;
    text-shadow: 0px 0px 2px #e9deb2, 0px 0px 2px #e9deb2, 0px 0px 2px #e9deb2, 0px 0px 2px #e9deb2;
  }
  .dataBox .top .score.superb {
    background-image: linear-gradient(to bottom, #e0bd19, #d36b1e);
    -webkit-background-clip: text;
    color: transparent;
  }
  .dataBox .top .score.suffice {
    background-image: linear-gradient(to bottom, #cdc370, #d38c1e);
    -webkit-background-clip: text;
    color: transparent;
  }
  .dataBox .bottom {
    width: 100%;
    height: 72px;
    padding: 3% 0;
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
  }
  .dataBox .resGroup {
    display: flex;
    align-items: center;
    font-size: 14px;
    position: relative;
  }
  .dataBox .resGroup+.resGroup {
    margin-left: 6%;
  }
  .dataBox .resGroup .cont {
    padding-left: 12px;
    font-size: 13px;
  }
  .dataBox .resGroup .ico {
    width: 32px;
  }
  .dataBox .resGroup .desc{
    font-size: 12px;
    color: #84929f;
  }
  .dataBox .resGroup .num {
    font-size: 20px;
    font-weight: bolder;
    line-height: 26px;
    color: #e9deb2;
    display: flex;
    align-items: center;
  }
  .chartBox {
    width: 100%;
    height: calc(100% - 234px);
    padding-right: 10px;
  }
  .chartBox .tabs {
    height: 30px;
    display: flex;
    align-items: center;
  }
  .chartBox .tabs .tab {
    height: 30px;
    padding: 0 12px;
    display: flex;
    align-items: center;
    background-color: #314150;
    border: 1px solid #3f505f;
    color: #bfcde0;
    font-size: 13px;
    margin-right: 2px;
    margin-bottom: 10px;
    cursor: pointer;
    position: relative;
    top: 1px;
    z-index: 8;
  }
  .chartBox .tabs .tab + .tab {
    border-left: transparent;
  }
  .chartBox .tabs .tab.on {
    color:#bfcde0!important; ;
    background: linear-gradient(to bottom,#4a5151,#314150);
    border-bottom: transparent;
    border-top:2px solid #e9deb2;
  }
  .chartBox .tabs .tab .ico {
    margin-right: 6px;
  }
  .chartBox .box {
    height: calc(100% - 40px);
    border: 1px solid #4c7595;
    background: rgba(76,117,149,0.2);
    padding: 4px 10px;
    position: relative;
  }
  .chartBox .box:before{
    position: absolute;
    content: "";
    background-image: url("../../../../../assets/HJ/postTrain/leftTop.png"),url("../../../../../assets/HJ/postTrain/leftBottom.png");
    background-repeat: no-repeat;
    background-position: left top,left bottom;
    height: 361px;
    width: 12px;
    left: -2px;
    top: -2px;
  }
  .chartBox .box:after{
    position: absolute;
    content: "";
    background-image: url("../../../../../assets/HJ/postTrain/rightTop.png"),url("../../../../../assets/HJ/postTrain/rightBottom.png");
    background-repeat: no-repeat;
    background-position: right top,right bottom;
    height: 361px;
    width: 12px;
    right: -2px;
    top: -2px;
  }
  .chartBox .chart {
    height: calc(100% - 8px);
  }
  .chartBox .totalChart {
    width: calc(100% - 5px);
    height: calc(100% - 5px);
  }
  .chartBox .box .title {
    display: flex;
    height: 32px;
    align-items: center;
    color: #70c9ff;
    font-size: 15px;
    font-weight: bold;
    padding-left: 6px;
  }
  .chartBox .box .title .lab {
    margin-right: 10px;
    font-weight: bold;
  }
  .totalTable {
    margin-top: 10px;
    color: #bfcde0;
  }
  .totalTable .head,
  .totalTable .row {
    display: flex;
  }
  .totalTable .item {
    width: 100%;
    height: 30px;
    display: flex;
    align-items: center;
    justify-content: center;
    border-bottom: 1px solid #3f5669;
    border-right: 1px solid #3f5669;
  }
  .totalTable .lab {
    width: 100px;
    flex-shrink: 0;
  }
  .totalTable .full {
    background-color: #151c25;
  }
  .totalTable .head .lab {
    border: 1px solid #3f5669;
    position: relative;
    overflow: hidden;
  }
  .totalTable .head .lab:after,
  .totalTable .head .lab:before {
    content: '';
    width: 0;
    height: 0;
    position: absolute;
    left: 0;
    bottom: 0;
  }
  .totalTable .head .lab:after {
    border-right: 100px solid transparent;
    border-bottom: 28px solid #3f5669;
    z-index: 2;
  }
  .totalTable .head .lab:before {
    border-right: 98px solid transparent;
    border-bottom: 27px solid #151c25;
    z-index: 3;
  }
  .totalTable .head .full {
    border-top: 1px solid #3f5669;
  }
  .totalTable .row .full {
    border-left: 1px solid #3f5669;
  }
  .telegraphBox .totalBox {
    display: flex;
    height: 46px;
    align-items: flex-start;
    justify-content: space-between;
    padding-right: 35px;
  }
  .telegraphBox .totalBox .total {
    width: 154px;
    height: 34px;
    display: flex;
    align-items: center;
    justify-content: space-around;
    color: #bfcde0;
    font-size: 14px;
    background: url("../../../../../assets/HJ/postTrain/totalBg.png") no-repeat center;
    background-size: 100% 100%;
  }
  .telegraphBox .totalBox .total + .total {
    margin-left: 3%;
  }
  .telegraphBox .totalBox .total .num {
    font-weight: bold;
    color: #e9deb2;
  }
  .telegraphBox .totalBox .page {
    height: 34px;
    display: flex;
    align-items: center;
    flex-shrink: 0;
  }
  .telegraphBox .totalBox .page .pag {
    width: 100px;
    height: 26px;
    display: flex;
    align-items: center;
    justify-content: center;
    color: rgba(255,255,255,0.6);
    font-size: 13px;
    position: relative;
    padding: 0 20px;
    border: 1px solid #5d6872;
  }
  .telegraphBox .totalBox .page .pag:first-child:before{
    position: absolute;
    content: "";
    background: url("../../../../../assets/HJ/postTrain/left.png");
    height: 7px;
    width: 8px;
    left: 10px;
  }
  .telegraphBox .totalBox .page .pag + .pag:before{
    position: absolute;
    content: "";
    background: url("../../../../../assets/HJ/postTrain/right.png");
    height: 7px;
    width: 8px;
    right: 10px;
  }
  .telegraphBox .totalBox .page .pag + .pag {
    margin-left: 10px;
  }
  .telegraphBox .totalBox .page .pag.disabled {
    opacity: .4;
  }
  .patTelegraph {
    width: 100%;
    height: calc(100% - 78px);
    display: flex;
    position: relative;
  }
  .patTelegraph .serial {
    width: 30px;
    margin-left: 5px;
    display: flex;
    flex-direction: column;
    color: #8eafca;
    align-content: stretch;
  }
  .patTelegraph .serial .ser {
    height: calc((100% - 36px) / 10);
    flex-grow: 1;
    flex-shrink: 0;
    display: flex;
    align-items: center;
  }
  .patTelegraph .serial .ser.head {
    height: 36px;
    flex-grow: 1;
    flex-shrink: 0;
    display: flex;
    align-items: center;
  }
  .patTelegraph .telegraph {
    width: 100%;
    height: 100%;
    border: 1px solid #171e27;
    background-color: #213141;
    display: flex;
    flex-direction: column;
  }
  .patTelegraph .telegraph .rowHead {
    width: 100%;
    height: 36px;
    display: flex;
    flex-shrink: 0;
  }
  .patTelegraph .telegraph .keyBox {
    display: flex;
    flex-wrap: wrap;
    height: 100%;
  }
  .patTelegraph .telegraph .key {
    color: #fff;
    font-size: 16px;
    width: 10%;
    height: 10%;
    display: flex;
    align-items: center;
    justify-content: center;
    border-right: 1px solid #171e27;
  }
  .patTelegraph .telegraph .key.error {
    color: #d11d1d;
    font-weight: bold;
    position: relative;
  }
  .patTelegraph .telegraph .key.omission {
    color: #4c7595;
    font-weight: bold;
    position: relative;
  }
  .patTelegraph .telegraph .key:nth-of-type(10n) {
    border-right: none;
  }
  .patTelegraph .telegraph .key:nth-of-type(n+10) {
    border-top: 1px solid #171e27;
  }
  .patTelegraph .telegraph .key .keyErr,
  .patTelegraph .telegraph .key .keyOmis {
    display: none;
    min-width: 80px;
    height: 56px;
    font-size: 16px;
    position: absolute;
    top: -36px;
    left: 50%;
  }
  .patTelegraph .telegraph .key .keyOmis {
    width: 105px;
    height: 57px;
    font-size: 15px;
    text-align: center;
    line-height: 33px;
    background: url("../../../../../assets/HJ/postTrain/keyPopBg.png") no-repeat center;
  }
  .patTelegraph .telegraph .key .keyErr:before,
  .patTelegraph .telegraph .key .keyErr:after {
    content: '';
    height: 56px;
    position: absolute;
    top: 0;
  }
  .patTelegraph .telegraph .key .keyErr:before {
    width: 36px;
    background: url("../../../../../assets/HJ/postTrain/keyErrorBg-l.png") no-repeat center;
    left: -36px;
  }
  .patTelegraph .telegraph .key .keyErr:after {
    width: 16px;
    background: url("../../../../../assets/HJ/postTrain/keyErrorBg-r.png") no-repeat center;
    right: -16px;
  }
  .patTelegraph .telegraph .key .keyErr .box,
  .patTelegraph .telegraph .key .keyOmis .box {
    height: 31px;
    text-align: center;
    line-height: 31px;
    padding: 0 12px 0 32px;
    position: relative;
    z-index: 6;
  }
  .patTelegraph .telegraph .key .keyErr .text {
    height: 31px;
    background: url("../../../../../assets/HJ/postTrain/keyErrorBg.png");
    line-height: 31px;
  }
  .patTelegraph .telegraph .key.error:hover .keyErr {
    display: block;
  }
  .patTelegraph .telegraph .key.omission:hover .keyOmis {
    display: block;
  }
  .patTelegraph .telegraph .rowHead .key {
    color: #161e29;
    height: 100%;
    font-weight: bold;
    font-size: 18px;
    background-color: #4c7595;
    border: none;
  }
  .totalDesc {
    height: 32px;
    display: flex;
    align-items: center;
    font-size: 13px;
    color: #7b90af;
  }
  .totalDesc .text {
    margin-right: 12px;
  }
  .main_b .title {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 8px 0 16px;
  }
  .main_b .title .tabs {
    display: flex;
  }
  .main_b .title .tabs .tab {
    color: #354971;
    font-size: 13px;
    padding: 0 8px;
    border: 1px solid #354971;
    height: 24px;
    line-height: 22px;
    cursor: pointer;
    position: relative;
    z-index: 1;
  }
  .main_b .title .tabs .tab + .tab {
    margin-left: -1px;
  }
  .main_b .title .tabs .tab:hover,
  .main_b .title .tabs .tab.active {
    color: #6ebdff;
    border-color: #6ebdff;
    z-index: 2;
  }
  .main_b .title .legend {
    display: flex;
    justify-content: flex-end;
    align-items: center;
  }
  .main_b .title .legend .leg {
    color: #e2f2ff;
    font-size: 12px;
    padding-left: 18px;
    margin-right: 20px;
    position: relative;
  }
  .main_b .title .legend .leg:after {
    content: '';
    width: 12px;
    height: 12px;
    border: 1px solid transparent;
    position: absolute;
    left: 0;
    top: 50%;
    margin-top: -6px;
  }
  .main_b .title .legend .leg.dot:after {
    background-color: #4c7595;
    border-color: #4c7595;
  }
  .main_b .title .legend .leg.line:after {
    background-color: #d1a03c;
    border-color: #d1a03c;
  }
  .main_b .title .legend .leg.gap:after {
    background-color: #e2f2ff;
    border-color: #e2f2ff;
  }
  .main_b .title .legend .leg.alter:after {
    background-color: #d11d1d;
    border-color: #d11d1d;
  }
  .main_b .title .legend .leg.omission:after {
    background-color: #101c33;
    border-color: #354971;
  }
  .main_b .title .legend .leg.nimiety:after {
    background: url("../../../../../assets/HJ/train/leg-nimiety.png") no-repeat center;
  }
  .main_b .title .legend .leg.abnormal:after {
    background: url("../../../../../assets/HJ/train/leg-abnormal.png") no-repeat center;
  }
  .patHairTrendBox {
    display: flex;
  }
  .patHairTrendBox .labs {
    font-size: 12px;
    color: #7b90af;
    padding: 0 0 12px 0;
    flex-shrink: 0;
  }
  .patHairTrendBox .labs ._t {
    display: flex;
    align-items: center;
    justify-content: flex-end;
  }
  .patHairTrendBox .labs ._t+._t {
    transform: scale(.88);
  }
  .patHairTrend {
    width: 100%;
    padding: 20px 10px 12px;
    display: flex;
    overflow-x: auto;
  }
  .patHairTrend .name {
    color: #4c7595;
    font-size: 18px;
    text-align: center;
    line-height: 30px;
    background-color: #223241;
    padding: 0 4px;
    border-top: none;
    letter-spacing: 5px;
  }
  .patHairTrend .codeContLog {
    display: flex;
    box-shadow: inset 0 -60px 80px -60px rgb(18, 33, 56);
  }
  .patHairTrend .key {
    position: relative;
  }
  .patHairTrend .key:after,
  .patHairTrend .key:before {
    content: '';
    width: 2px;
    background-image: linear-gradient(to top, #181f24, #2f394a);
    position: absolute;
    top: 22px;
    bottom: 0;
  }
  .patHairTrend .key:after {
    right: 0;
  }
  .patHairTrend .key:before {
    left: 0;
  }
  .patHairTrend .key+.omission:before {
    display: none;
  }
  .patHairTrend .keyGap {
    height: 58px;
    position: relative;
  }
  .patHairTrend .keyName {
    height: 58px;
    color: #4c7595;
    font-size: 18px;
    display: flex;
    align-items: center;
    justify-content: center;
    position: relative;
    cursor: pointer;
  }
  .patHairTrend .keyName:after,
  .patHairTrend .keyName:before {
    content: '';
    height: 2px;
    border-top: 2px dashed #354971;
    position: absolute;
    top: 50%;
  }
  .patHairTrend .keyName:after {
    left: 2px;
    right: calc(50% + 10px);
  }
  .patHairTrend .keyName:before {
    left: calc(50% + 10px);
    right: 2px;
  }
  .patHairTrend .keyName.err {
    font-weight: bold;
    color: #d11d1d;
    position: relative;
  }
  .patHairTrend .keyName .text {
    display: none;
    width: 48px;
    height: 26px;
    background-color: #0a1936;
    box-shadow: inset 0 0 10px #d11d1d;
    align-items: center;
    justify-content: center;
    position: absolute;
    left: calc(50% - 24px);
    top: -16px;
  }
  .patHairTrend .keyName.err .text:after {
    content: '';
    width: 0;
    height: 0;
    border-left: 5px solid transparent;
    border-right: 5px solid transparent;
    border-top: 6px solid rgba(209,29,29,.6);
    position: absolute;
    bottom: -6px;
    left: 50%;
    margin-left: -5px;
  }
  .patHairTrend .keyName.err:hover .text {
    display: flex;
  }
  .patHairTrend .times {
    display: flex;
  }
  .patHairTrend .times .time {
    height: 22px;
    border-top: 2px solid transparent;
    display: flex;
    justify-content: center;
    align-items: center;
    position: relative;
  }
  .patHairTrend .times .time:after {
    content: '';
    width: 0;
    height: 0;
    border-left: 4px solid transparent;
    border-right: 4px solid transparent;
    border-bottom: 5px solid transparent;
    position: absolute;
    top: -7px;
    left: 50%;
    margin-left: -4px;
  }
  .patHairTrend .times .time.dot {
    border-top-color: #4c7595;
    background-color: rgba(110,189,255,.3);
  }
  .patHairTrend .times .time.line {
    border-top-color: #c99a3b;
    background-color: rgba(201,154,59,.3);
  }
  .patHairTrend .times .time.gap {
    border-top-color: #e2f2ff;
    background-color: rgba(226,242,255,.3);
    max-width: 500px;
  }
  .patHairTrend .times .time.dot:after {
    border-bottom-color: #6ebdff;
  }
  .patHairTrend .times .time.line:after {
    border-bottom-color: #c99a3b;
  }
  .patHairTrend .times .time.gap:after {
    border-bottom-color: #e2f2ff;
  }
  .patHairTrend .times .time .num {
    font-size: 12px;
    width: 100%;
    text-align: center;
    line-height: 1;
    transform: scale(.8);
    position: absolute;
    top: -20px;
    left: 0;
  }
  .patHairTrend .times .time.dot .num {
    color: #6ebdff;
  }
  .patHairTrend .times .time.line .num {
    color: #c99a3b;
  }
  .patHairTrend .times .time.gap .num {
    color: #e2f2ff;
  }
  .patHairTrend .times .time .abno {
    width: 12px;
    height: 12px;
    background: url("../../../../../assets/HJ/train/leg-abnormal.png") no-repeat center;
    position: relative;
  }
  .patHairTrend .times .time .nimi {
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
  }
  .patHairTrend .times .time.dot .nimi {
    background: url("../../../../../assets/HJ/train/nimiety-0.png") repeat left;
  }
  .patHairTrend .times .time.line .nimi {
    background: url("../../../../../assets/HJ/train/nimiety-1.png") repeat left;
  }
  .patHairTrend .omission {
    position: relative;
  }
  .patHairTrend .omission:before {
    content: '';
    width: 2px;
    background-image: linear-gradient(to bottom, #2e4065, #182741);
    position: absolute;
    top: 22px;
    bottom: 0;
    left: 0;
  }
  .patHairTrend .omis {
    height: 22px;
    background-color: #101c33;
    border: 2px solid #354971;
    border-left: none;
    width: 36px;
  }

</style>