<template>
  <div class="w-full h-full content-mask-bg relative">
    <div class="loading" v-show="loading">
      <a-spin size="large" tip="正在努力加载..."/>
    </div>
    <div class="main w-full h-full">
      <div class="main_t">
        <div class="dataBox">
          <div class="top">
            <template v-if="interfaceStyle==='HJ'">
              <img v-if="parseFloat(scoreData.score)>80" :src="scoreSuperb" class="img">
              <img v-else-if="parseFloat(scoreData.score)>60" :src="scoreSuffice" class="img">
              <img v-else :src="scoreFailed" class="img">
              <div class="name nobr" style="font-size: 12px">{{scoreData.name}}</div>
            </template>
            <img v-else :src="scoreSuperbHJJ" class="img" />

            <div :class="{score:true,superb:parseFloat(scoreData.score)>80,suffice:parseFloat(scoreData.score)>60}">{{ scoreData.score }}</div>
          </div>
          <div class="bottom" style="justify-content: space-around">
            <div class="resGroup">
              <img :src="resAccuracy" class="ico">
              <div class="cont">
                <div class="desc">正确率</div>
                <div class="num">{{ parseInt(scoreData.accuracy) }}%</div>
              </div>
            </div>
            <div class="resGroup">
              <img :src="resTime" class="ico">
              <div class="cont">
                <div class="desc">时间</div>
                <div class="num">{{ scoreData.validTime }}</div>
              </div>
            </div>
          </div>
          <div class="chartBox">
            <div class="tabs">
              <div :class="{'tab colu': true, on: showChart=='total'}" @click="showChart = 'total'">
                <img :src="chartIcoOn3" v-show="showChart == 'total'" class="ico">
                <img :src="chartIco3" v-show="showChart != 'total'" class="ico">
                统计
              </div>
              <div :class="{'tab': true, on: showChart=='number'}" @click="showChart = 'number'">
                <img :src="chartIcoOn1" v-show="showChart == 'number'" class="ico">
                <img :src="chartIco1" v-show="showChart != 'number'" class="ico">
                数量
              </div>
              <div :class="{'tab': true, on: showChart=='column'}" @click="showChart = 'column'">
                <img :src="chartIcoOn1" v-show="showChart == 'column'" class="ico">
                <img :src="chartIco1" v-show="showChart != 'column'" class="ico">
                用时(ms)
              </div>
              <div :class="{'tab colu': true, on: showChart=='line'}" @click="showChart = 'line'">
                <img :src="chartIcoOn2" v-show="showChart == 'line'" class="ico">
                <img :src="chartIco2" v-show="showChart != 'line'" class="ico">
                码率({{ speedUnit }})
              </div>
            </div>
            <div class="box">
              <div class="chart overflow-auto">
                <div id="numberChart" class="totalChart" v-show="showChart=='number'"></div>
                <div id="columnChart" class="totalChart" v-show="showChart=='column'"></div>
                <div id="lineChart" class="totalChart" v-show="showChart=='line'"></div>
                <div class="totalTable" v-show="showChart=='total'">
                  <div class="head">
                    <div class="item lab"></div>
                    <div class="item full">标准值</div>
                    <div class="item full">实际值</div>
                  </div>
                  <template v-if="scoreData.ruleContent">
                    <div class="row">
                      <div class="item lab full">码率</div>
                      <div class="item">{{scoreData.ruleContent.wpm.base}} {{ speedUnit }}</div>
                      <div class="item">{{ scoreData.speed }} {{ speedUnit }}</div>
                    </div>
                    <div class="row">
                      <div class="item lab full">点划比</div>
                      <div class="item">{{scoreData.ruleContent.scale.dot}} : {{scoreData.ruleContent.scale.dash}}</div>
                      <div class="item">{{scoreData.scale.d}} : {{scoreData.scale.l}}</div>
                    </div>
                    <div class="row">
                      <div class="item lab full">码间隔</div>
                      <div class="item">{{scoreData.ruleContent.scale.little}} : {{scoreData.ruleContent.scale.middle}} : {{scoreData.ruleContent.scale.large}}</div>
                      <div class="item">{{scoreData.scale.c}} : {{scoreData.scale.w}} : {{scoreData.scale.g}}</div>
                    </div>
                  </template>
                  <div class="head">
                    <div class="item lab"></div>
                    <div class="item full">最大扣分</div>
                    <div class="item full">数量</div>
                    <div class="item full">扣分情况</div>
                  </div>
                  <template v-if="scoreData.ruleContent">
                    <div class="row" >
                      <div class="item lab full">码率</div>
                      <div class="item">--</div>
                      <div class="item">--</div>
                      <div class="item">{{ scoreData.deductInfo.wpmScore }} 分</div>
                    </div>
                    <div class="row">
                      <div class="item lab full">点</div>
                      <div class="item">{{ scoreData.ruleContent.code.dot.max }} 分</div>
                      <div class="item">{{ formatData(scoreData.deductInfo.dotMinNumber) }} 个</div>
                      <div class="item">-{{ scoreData.deductInfo.dotMinScore }} 分</div>
                    </div>
                    <div class="row" >
                      <div class="item lab full">划</div>
                      <div class="item">{{ scoreData.ruleContent.code.dash.max }} 分</div>
                      <div class="item">{{ formatData(scoreData.deductInfo.lineNumber) }} 个</div>
                      <div class="item">-{{ scoreData.deductInfo.lineScore }} 分</div>
                    </div>
                    <div class="row" >
                      <div class="item lab full">码间隔</div>
                      <div class="item">{{ scoreData.ruleContent.gap.little.max }} 分</div>
                      <div class="item">{{ formatData(scoreData.deductInfo.codeNumber) }} 个</div>
                      <div class="item">-{{ scoreData.deductInfo.codeGapScore }} 分</div>
                    </div>
                    <div class="row" >
                      <div class="item lab full">字间隔</div>
                      <div class="item">{{ scoreData.ruleContent.gap.middle.max }} 分</div>
                      <div class="item">{{ formatData(scoreData.deductInfo.wordNumber) }} 个</div>
                      <div class="item">-{{ scoreData.deductInfo.wordGapScore }} 分</div>
                    </div>
                    <div class="row" >
                      <div class="item lab full">组间隔</div>
                      <div class="item">{{ scoreData.ruleContent.gap.large.max }} 分</div>
                      <div class="item">{{ formatData(scoreData.deductInfo.groupNumber) }} 个</div>
                      <div class="item">-{{ scoreData.deductInfo.groupGapScore }} 分</div>
                    </div>
                    <div class="row" >
                      <div class="item lab full">改错</div>
                      <div class="item">{{ scoreData.ruleContent.other.alterError.max }} 分</div>
                      <div class="item">{{ formatData(scoreData.deductInfo.alterErrorNumber) }} 个</div>
                      <div class="item">-{{ scoreData.deductInfo.alterErrorScore }} 分</div>
                    </div>
                    <div class="row" >
                      <div class="item lab full">错误码</div>
                      <div class="item">{{ scoreData.ruleContent.other.errorCode.max }} 分</div>
                      <div class="item">{{ formatData(scoreData.deductInfo.errorWordNumber) }} 个</div>
                      <div class="item">-{{ scoreData.deductInfo.errorWord }} 分</div>
                    </div>
                    <div class="row" >
                      <div class="item lab full">多少码</div>
                      <div class="item">{{ scoreData.ruleContent.other.quantoCode.max }} 分</div>
                      <div class="item">{{ formatData(scoreData.deductInfo.quantoCodeNumber) }} 个</div>
                      <div class="item">-{{ scoreData.deductInfo.quantoCode }} 分</div>
                    </div>
                    <div class="row" >
                      <div class="item lab full">串组</div>
                      <div class="item">{{ scoreData.ruleContent.other.bunchGroup.max }} 分</div>
                      <div class="item">{{ formatData(scoreData.deductInfo.bunchGroupNumber) }} 个</div>
                      <div class="item">-{{ scoreData.deductInfo.bunchGroup }} 分</div>
                    </div>
                    <div class="row" >
                      <div class="item lab full">多少组</div>
                      <div class="item">{{ scoreData.ruleContent.other.quantoGroup.max }} 分</div>
                      <div class="item">{{ formatData(scoreData.deductInfo.quantoGroupNumber) }} 个</div>
                      <div class="item">-{{ scoreData.deductInfo.quantoGroup }} 分</div>
                    </div>
                    <div class="row">
                      <div class="item lab full">多少行</div>
                      <div class="item">{{ scoreData.ruleContent.other.quantoRow.max }} 分</div>
                      <div class="item">{{ formatData(scoreData.deductInfo.quantoRowNumber) }} 行</div>
                      <div class="item">-{{ scoreData.deductInfo.quantoRow }} 分</div>
                    </div>
                  </template>
                </div>
              </div>
            </div>
          </div>
        </div>
        <div class="telegraphBox">
          <div class="totalBox">
            <div class="layout-left-center w-full">
              <div class="total">报底数：<span class="num">{{ scoreData.pag }}</span></div>
              <div class="total">错误数：<span class="num">{{ scoreData.errorNumber }}</span></div>
            </div>
            <div class="page" v-if="scoreData.pag > 1">
              <div :class="{pag: true, disabled: scoreData.currPage == 1}"
                   v-debounce="{fn:switchTelegram,data:'prev'}"
              >上一页</div>
              <div :class="{pag: true, disabled: scoreData.currPage == scoreData.pag}"
                   v-debounce="{fn:switchTelegram,data:'next'}"
              >下一页</div>
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
              <div class="keyBox" v-if="scoreData.messageBody">
                <template v-for="(key, index) in scoreData.messageBody[scoreData.currPage-1]">
                  <div v-if="key.moresKey != '#'&&index<100"
                       :class="{key: true,
                                error: successResolver[scoreData.currPage-1]&&successResolver[scoreData.currPage-1].resolverMessage&&
                                       successResolver[scoreData.currPage-1].resolverMessage[index] &&
                                       key.moresKey.join('')!=successResolver[scoreData.currPage-1].resolverMessage[index] &&
                                       successResolver[scoreData.currPage-1].resolverMessage[index]!='',
                                omission: !successResolver[scoreData.currPage-1]||!successResolver[scoreData.currPage-1].resolverMessage[index] ||
                                           successResolver[scoreData.currPage-1].resolverMessage[index]=='',
                                more: successResolver[scoreData.currPage-1]&&successResolver[scoreData.currPage-1].moreObj[index+1],
                                warning: key.patKeys.indexOf('?') > -1 || key.patKeys.indexOf('/') > -1}"
                       @click="seeCurrKeysHairTrend(key,index)">
                    <div class="keyErr"
                         v-if="(successResolver[scoreData.currPage-1]&&key.moresKey.join('')!=successResolver[scoreData.currPage-1].resolverMessage[index]&&
                                successResolver[scoreData.currPage-1].resolverMessage[index]&&successResolver[scoreData.currPage-1].resolverMessage[index]!='') ||
                                (successResolver[scoreData.currPage-1]&&successResolver[scoreData.currPage-1].moreObj[index+1])">
                      <div class="box">
                        <div class="text" :style="{fontSize: key.patKeys.join('')==''?'15px':'16px'}">
                          {{ successResolver[scoreData.currPage-1].resolverMessage[index]?successResolver[scoreData.currPage-1].resolverMessage[index].replace(/I/g,'#'):'' }}
                          {{successResolver[scoreData.currPage-1].moreObj[index+1]?','+successResolver[scoreData.currPage-1].moreObj[index+1].replace(/I/g,'#'):''}}
                        </div>
                      </div>
                    </div>
                    <div class="keyOmis"
                         v-if="!successResolver[scoreData.currPage-1] || !successResolver[scoreData.currPage-1].resolverMessage[index] ||
                                successResolver[scoreData.currPage-1].resolverMessage[index]==''">
                      <div class="box">
                        <div class="text">漏拍</div>
                      </div>
                    </div>
                    <div class="layout-center">
                      <template v-for="(item, i) in key.moresKey">
                        <div :style="{color: successResolver[scoreData.currPage-1]&&item==successResolver[scoreData.currPage-1].resolverMessage[index]?'#fff':'inherit'}">{{item}}</div>
                      </template>
                    </div>
                  </div>
                  <div v-if="key.moresKey == '#'&&index<100" class="key error" @click="seeCurrKeysHairTrend(key,index)"
                       style="background-color: #4d2734">
                    <div class="keyErr" v-if="key.patKeys.join('')!=''">
                      <div class="box">
                        <div class="text" :style="{fontSize: key.patKeys.join('')==''?'15px':'16px'}">
                          {{ key.patKeys.join('') }}
                        </div>
                      </div>
                    </div>
                    <div class="layout-center">#</div>
                  </div>
                </template>
                <template v-if="scoreData.messageBody[scoreData.currPage-1] && scoreData.messageBody[scoreData.currPage-1].length < 100">
                  <div class="key" v-for="(key, index) in 100 - scoreData.messageBody[scoreData.currPage-1].length"></div>
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
            <div class="text">页码：【{{scoreData.currPage}}】</div>
            <div class="text" v-if="scoreData.total.sm > 0">少码：【{{scoreData.total.sm}}】</div>
            <div class="text" v-if="scoreData.total.dm > 0">多码：【{{scoreData.total.dm}}】</div>
            <div class="text" v-if="scoreData.total.sz > 0">漏拍：【{{scoreData.total.sz}}】</div>
            <div class="text" v-if="scoreData.total.dz > 0">多组：【{{scoreData.total.dz}}】</div>
            <div class="text layout-left-center" v-if="scoreData.total.dh.length > 0">
              多行：【<div v-for="(dh, d) in scoreData.total.dh" :title="dh.message.join(',')">第{{dh.point}}行</div>】
            </div>
            <div class="layout-left-center">
              正确:
              <div class="key colorbox"></div>
            </div>
            <div class="layout-left-center">
              错码:
              <div class="error colorbox"></div>
            </div>
            <div class="layout-left-center">
              改错:
              <div class="warning colorbox"></div>
            </div>
            <div class="layout-left-center">
              多组:
              <div class="more colorbox"></div>
            </div>
            <div class="layout-left-center">
              漏拍:
              <div class="omission colorbox"></div>
            </div>
          </div>
        </div>
      </div>
      <div class="main_b">
        <div class="title" style="height: 58px;">
          <div class="tabs">
            <!--<span class="tab active">拍发态势</span>-->
          </div>
          <div class="legend">
            <div class="leg dot">点</div>
            <div class="leg line">划</div>
            <div class="leg gap">间隔</div>
            <div class="leg alter">改错</div>
            <div class="leg omission">漏拍</div>
            <div class="leg nimiety">多拍</div>
            <div class="leg abnormal">异常</div>
          </div>
        </div>
        <div class="patHairTrendBox">
          <div class="labs">
            <div class="_t" style="height: 20px;padding-right: 1px;">ms</div>
            <div class="_t" style="height: 22px;">用时</div>
            <div class="_t" style="height: 58px;">拍发值</div>
            <div class="_t" style="height: 32px;">目标组</div>
          </div>
          <div class="patHairTrend" ref="patHairTrendBoxRef">
            <template v-for="(group, g) in trendLogKeyData[scoreData.currPage-1]">
              <div class="groupLog" v-if="group.moresKey" :id="'ht_'+scoreData.currPage+'_'+(g+1)+'_'+(group.moresKey=='#'?'#':group.moresKey.join(''))">
                <div class="codeContLog">
                  <template v-for="(word, w) in group.patLog">
                    <div :class="{key: true, omiss: (group.patKeys.length - group.patLog.length) > 0}">
                      <div class="times" v-if="group.patKeys[w] != '/'">
                        <template v-for="(code, c) in word">
                          <div :class="{time: true,
                                      dot: code.key!=2&&group.moresValue[w][c/2]==0,
                                      line: code.key!=2&&group.moresValue[w][c/2]==1,
                                      gap: code.key==2}"
                               :style="{width: parseInt(code.value/2)+'px',minWidth: '15px'}"
                               v-if="c < (word.length - 1)">
                            <div class="num">{{code.value}}</div>
                          </div>
                        </template>
                      </div>
                      <div class="times" v-else>
                        <div class="time" style="width: 20px;background-color: rgba(209,29,29,.6);height: 22px"></div>
                      </div>
                      <div :class="{keyName:true, err:group.patKeys[w]!=group.moresKey[w]}">
                        {{group.patKeys[w]}}
                      </div>
                    </div>
                    <div class="sep" v-if="w < (group.patLog.length - 1)">
                      <div class="times">
                        <div class="time gap"
                             v-if="word[word.length-1]"
                             :style="{width:parseInt(word[word.length-1].value/2)+'px'}">
                          <div class="num">{{word[word.length-1].value}}</div>
                          <div class="abno" v-if="word[word.length-1].value > scoreData.standards[scoreData.currPage-1][parseInt(g/10)].wordGap"
                               :title="'标准值：'+scoreData.standards[scoreData.currPage-1][parseInt(g/10)].wordGap"></div>
                        </div>
                      </div>
                      <div class="keyGap"></div>
                    </div>
                  </template>
                  <template v-if="group.moresKey != '#' && (group.moresKey.length - group.patLog.length) > 0">
                    <template v-for="(omi, o) in group.moresKey">
                      <div class="key omission" v-if="o > (group.patLog.length - 1)">
                        <div class="omis"></div>
                        <div :class="{keyName:true, err:!group.patKeys[o]}">
                          <!--{{omi}}-->
                        </div>
                      </div>
                    </template>
                  </template>
                </div>
                <div class="name" v-if="group.patLog.length>0 || group.moresKey != '#'">{{group.moresKey=='#'?'#':group.moresKey.join('')}}</div>
              </div>
              <div class="sep" v-if="g < (trendLogKeyData[scoreData.currPage-1].length - 1)">
                <div class="times" v-if="group.patLog.length > 0 && group.patLog[group.patLog.length-1] && group.patLog[group.patLog.length-1].length > 0">
                  <div class="time gap"
                       :style="{maxWidth: (scoreData.standards[scoreData.currPage-1][parseInt(g/10)].groupGap/2>800?800:
                                scoreData.standards[scoreData.currPage-1][parseInt(g/10)].groupGap/2)+'px',
                                width:parseInt(group.patLog[group.patLog.length-1][group.patLog[group.patLog.length-1].length-1].value/2)+'px'}">
                    <div class="num">{{group.patLog[group.patLog.length-1][group.patLog[group.patLog.length-1].length-1].value}}</div>
                    <div class="abno"
                         v-if="group.patLog[group.patLog.length-1][group.patLog[group.patLog.length-1].length-1].value >
                               scoreData.standards[scoreData.currPage-1][parseInt(g/10)].groupGap"
                         :title="'标准值：'+scoreData.standards[scoreData.currPage-1][parseInt(g/10)].groupGap"></div>
                  </div>
                </div>
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
    name: "PatTrainScore"
  }
</script>
<script setup>
  import {ref, onMounted, onUnmounted, watch} from "vue";
  import {useRoute} from "vue-router";
  import trainScore from "./js/trainScore.js";
  import chartLab from '../../../../../assets/HJ/postTrain/chartLab.png';
  import useMorse from "../../../../../common/mixin/useMorse.js";
  import scoreSuperb from "../../../../../assets/HJ/postTrain/score-superb.png";
  import scoreSuffice from "../../../../../assets/HJ/postTrain/score-suffice.png";
  import scoreFailed from "../../../../../assets/HJ/postTrain/score-failed.png";
  import resAccuracy from "../../../../../assets/HJ/postTrain/check.png";
  import resSpeed from "../../../../../assets/HJ/train/res-speed.png";
  import resTime from "../../../../../assets/HJ/postTrain/time.png";
  import chartIco1 from "../../../../../assets/HJ/train/chart-ico-1.png";
  import chartIcoOn1 from "../../../../../assets/HJ/train/chart-ico-1-on.png";
  import chartIco2 from "../../../../../assets/HJ/train/chart-ico-2.png";
  import chartIcoOn2 from "../../../../../assets/HJ/train/chart-ico-2-on.png";
  import chartIco3 from "../../../../../assets/HJ/train/chart-ico-3.png";
  import chartIcoOn3 from "../../../../../assets/HJ/train/chart-ico-3-on.png";
  import scoreSuperbHJJ from '../../../../../assets/HJ/postTrain/topBox-after.png'
  const interfaceStyle = window.interfaceStyle
  const {morseCode} = useMorse();
  const showChart = ref('total');
  const formatData = data=>{
    return data?data:'--'
  }

  const { speedUnit,scoreData,loading,patHairTrendBoxRef,trendLogKeyData,short,successResolver,switchTelegram,seeCurrKeysHairTrend } = trainScore(showChart);

</script>

<style scoped lang="less">
  .totalTable .head,.totalTable .row{
    height: calc(100% / 17) !important;
  }
  .totalTable .head .lab:after{
    border-bottom-width: 2.5vh!important;
  }
  .totalTable .head .lab:before{
    border-bottom-width: 2.5vh!important;
  }
  @import "./css/HandkeyTrainScore";
  @import "../../../../../common/styles/css/score";
</style>