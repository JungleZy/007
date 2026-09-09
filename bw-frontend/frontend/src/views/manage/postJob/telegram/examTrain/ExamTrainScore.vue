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
              <img v-if="parseFloat(scoreData.score)>80" :src="scoreSuperb" class="img">
              <img v-else-if="parseFloat(scoreData.score)>60" :src="scoreSuffice" class="img">
              <img v-else :src="scoreFailed" class="img">
              <div class="name nobr" style="font-size: 12px">{{scoreData.title}}</div>
            </template>
            <img v-else :src="scoreSuperbHJJ" class="img" />
            <div :class="{score:true,superb:parseFloat(scoreData.score)>80,suffice:parseFloat(scoreData.score)>60}">{{ scoreData.score }}</div>
          </div>
          <div class="bottom" style="justify-content: space-around">
            <div class="resGroup">
              <img :src="resAccuracy" class="ico" />
              <div class="cont">
                <div class="desc">正确率</div>
                <div class="num">{{ parseInt(scoreData.accuracy) }}%</div>
              </div>
            </div>
            <div class="resGroup">
              <img :src="resTime" class="ico" />
              <div class="cont">
                <div class="desc">时间</div>
                <div class="num">{{ scoreData.duration }}</div>
              </div>
            </div>
          </div>
          <div class="chartBox">
            <div class="tabs">
              <div :class="{ 'tab colu': true, on: showChart == 'total' }" @click="showChart = 'total'">
                <img :src="chartIcoOn3" v-show="showChart == 'total'" class="ico" />
                <img :src="chartIco3" v-show="showChart != 'total'" class="ico" />
                统计
              </div>
              <div :class="{ 'tab colu': true, on: showChart == 'line' }" @click="showChart = 'line'">
                <img :src="chartIcoOn2" v-show="showChart == 'line'" class="ico" />
                <img :src="chartIco2" v-show="showChart != 'line'" class="ico" />
                码率(码/分)
              </div>
            </div>
            <div class="box">
              <div class="chart overflow-auto">
                <div id="lineChart" class="totalChart" v-show="showChart == 'line'"></div>
                <div class="totalTable" v-show="showChart == 'total'">
                  <div class="head">
                    <div class="item lab"></div>
                    <div class="item full">值</div>
                    <div class="item full">扣分情况</div>
                  </div>
                  <template v-if="scoreData.deductInfo">
                    <div class="row">
                      <div class="item lab full">码率</div>
                      <div class="item">{{ Number(scoreData.deductInfo.speedNumber)}} 码/分</div>
                      <div class="item">{{ scoreData.deductInfo.speedScore*1 }} 分</div>
                    </div>
                    <div class="row">
                      <div class="item lab full">改错</div>
                      <div class="item">{{ scoreData.deductInfo.alterErrorNumber }} 组</div>
                      <div class="item">{{ scoreData.deductInfo.alterErrorScore*1 }} 分</div>
                    </div>
                    <div class="row">
                      <div class="item lab full">错码</div>
                      <div class="item">{{ scoreData.deductInfo.errorNumber }} 组</div>
                      <div class="item">{{ scoreData.deductInfo.errorScore*1 }} 分</div>
                    </div>
                    <div class="row" >
                      <div class="item lab full">少码</div>
                      <div class="item">{{ scoreData.deductInfo.lackNumber }} 组</div>
                      <div class="item">{{ scoreData.deductInfo.lackScore*1 }} 分</div>
                    </div>
                    <div class="row" >
                      <div class="item lab full">多码</div>
                      <div class="item">{{ scoreData.deductInfo.moreNumber }} 组</div>
                      <div class="item">{{ scoreData.deductInfo.moreScore*1 }} 分</div>
                    </div>
                    <div class="row" >
                      <div class="item lab full">少组</div>
                      <div class="item">{{ scoreData.deductInfo.lackGroupNumber }} 组</div>
                      <div class="item">{{ scoreData.deductInfo.lackGroupScore*1 }} 分</div>
                    </div>
                    <div class="row" >
                      <div class="item lab full">多组</div>
                      <div class="item">{{ scoreData.deductInfo.moreGroupNumber }} 组</div>
                      <div class="item">{{ scoreData.deductInfo.moreGroupScore*1 }} 分</div>
                    </div>
                    <div class="row">
                      <div class="item lab full">串组</div>
                      <div class="item">{{ scoreData.deductInfo.bunchGroupNumber }} 组</div>
                      <div class="item">{{ scoreData.deductInfo.bunchGroupScore*1 }} 分</div>
                    </div>
                    <div class="row">
                      <div class="item lab full">少间隔</div>
                      <div class="item">{{ scoreData.deductInfo.lackGapNumber }} 组</div>
                      <div class="item">{{ scoreData.deductInfo.lackGapScore*1 }} 分</div>
                    </div>
                    <div class="row" >
                      <div class="item lab full">少行</div>
                      <div class="item">{{ scoreData.deductInfo.lackLineNumber }} 行</div>
                      <div class="item">{{ scoreData.deductInfo.lackLineScore*1 }} 分</div>
                    </div>
                    <div class="row" >
                      <div class="item lab full">
                        多行
                        <a-tooltip placement="right" color="#47421e"  v-if="moreLine.length>0">
                          <template #title>
                            <div class="layout-left-top">
                              <div v-for="(v,index) of moreLine" :key="index" style="margin-right: 5px">
                                <span v-for="(s,i) of v.value" :key="i">{{s}}</span>
                              </div>
                            </div>
                          </template>
                          &nbsp;<QuestionCircleOutlined style="color: orange;font-size: 12px" />
                        </a-tooltip>
                      </div>
                      <div class="item">{{ scoreData.deductInfo.moreLineNumber }} 行</div>
                      <div class="item">{{ scoreData.deductInfo.moreLineScore*1 }} 分</div>
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
              <div class="total">
                报底数：<span class="num">{{ scoreData.pag }}</span>
              </div>
              <div class="total">
                错误数：<span class="num">{{ scoreData.errorNumber }}</span>
              </div>
            </div>
            <div class="page">
              <div :class="{ pag: true, disabled: scoreData.currPage == 1 }"  v-debounce="{fn:switchTelegram,data:-1}">上一页</div>
              <div :class="{ pag: true, disabled: scoreData.currPage == scoreData.pag }"  v-debounce="{fn:switchTelegram,data:1}">下一页</div>
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
              <div class="keyBox" v-if="scoreData.content">
                <template v-for="(key, index) in 100" :key="index">
                  <div v-if="scoreData.content[index]"
                       :class="{
                          key: true,
                          error: scoreData.content[index].error,
                          omission: scoreData.content[index].omission,
                          more: scoreData.content[index].list,
                          warning:scoreData.content[index].warning,
                          lackgroup:scoreData.content[index].lackgroup,
                          bunchGroup:scoreData.content[index].bunchGroup,
                          lackCode:scoreData.content[index].lackCode,
                          moreCode:scoreData.content[index].moreCode,
                       }"
                       @click="seeCurrKeysHairTrend(scoreData.content[index], index)"
                  >
                    <div class="keyErr" :style="{left:scoreData.content[index].list&&index%10===9?-scoreData.content[index].list.length*50+'px':''}"
                         v-if="scoreData.content[index].error||
                         scoreData.content[index].warning||
                         scoreData.content[index].bunchGroup||
                         scoreData.content[index].lackCode||scoreData.content[index].moreCode">
                      <div class="box">
                        <div class="text" :style="{ fontSize:false ? '15px' : '16px' }">
                          <template v-if="scoreData.content[index].list">
                            <template v-for="(item,index) of scoreData.content[index].list" :key="index">
                              &nbsp;
                              <template v-for="(codes, i) of item" :key="i">
                                <span>{{ codes }}</span>
                              </template>
                            </template>
                          </template>
                          <template v-else>
                            <template v-for="(item, i) in scoreData.content[index].value" :key="i">
                              <span >{{ item }}</span>
                            </template>
                          </template>
                        </div>
                      </div>
                    </div>
                    <div class="keyOmis"
                         v-if="scoreData.content[index].omission||scoreData.content[index].lackgroup">
                      <div class="box">
                        <div class="text">{{scoreData.content[index].lackgroup?'少组':'漏拍'}}</div>
                      </div>
                    </div>
                    <div class="layout-center" v-if="scoreData.content[index]">
                      <template v-for="(item, i) in scoreData.content[index].key" :key="i">
                        <div :style="{fontWeight: 'bolder'}">{{ item }}</div>
                      </template>
                    </div>
                  </div>
                  <div class="key" v-else></div>
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
            <div class="text">页码：【{{ scoreData.currPage }}】</div>
            <!--            <div class="text" v-if="scoreData.total.cm > 0">错码：【{{ scoreData.total.cm }}】</div>-->
            <!--            <div class="text" v-if="scoreData.total.sm > 0">少码：【{{ scoreData.total.sm }}】</div>-->
            <!--            <div class="text" v-if="scoreData.total.dm > 0">多码：【{{ scoreData.total.dm }}】</div>-->
            <!--            <div class="text" v-if="scoreData.total.sz > 0">漏拍：【{{ scoreData.total.sz }}】</div>-->
            <!--            <div class="text" v-if="scoreData.total.dz > 0">多组：【{{ scoreData.total.dz }}】</div>-->
            <!--            <div class="text layout-left-center" v-if="scoreData.total.dh.length > 0">-->
            <!--              多行：【<div v-for="(dh, d) in scoreData.total.dh" :title="dh.message.join(',')">第{{dh.point}}行</div>】-->
            <!--            </div>-->
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
            <div class="layout-left-center">
              少组
              <div class="colorbox lackgroup"></div>
            </div>
            <div class="layout-left-center">
              串组
              <div class="colorbox bunchGroup"></div>
            </div>
            <div class="layout-left-center">
              少码
              <div class="colorbox lackCode"></div>
            </div>
            <div class="layout-left-center">
              多码
              <div class="colorbox moreCode"></div>
            </div>
          </div>
        </div>
      </div>
      <div class="main_b">
        <div class="title" style="height: 58px">
          <div class="tabs">
            <!--<span class="tab active">拍发态势</span>-->
          </div>
          <div class="legend">
            <div class="leg dot">数码</div>
            <div class="leg gap">间隔</div>
            <div class="leg omission">漏拍</div>
          </div>
        </div>
        <div class="patHairTrendBox">
          <div class="labs">
            <div class="_t" style="height: 20px; padding-right: 1px">ms</div>
            <div class="_t" style="height: 22px">用时</div>
            <div class="_t" style="height: 58px">按键值</div>
            <div class="_t" style="height: 32px">目标组</div>
          </div>
          <div class="patHairTrend" ref="patHairTrendBoxRef">
            <template v-for="(group, g) in trendLogKeyData" :key="g">
              <div class="sep" v-if="g > 0&&group.time!==null">
                <div class="times" v-if="group.time.length > 0">
                  <div class="time gap" :style="{ width: parseInt(group.time[0] / 10) + 'px' }">
                    <div class="num">{{ group.time[0] }}</div>
                  </div>
                </div>
              </div>
              <div class="groupLog" v-if="group.key" :id="'ht_' + scoreData.currPage + '_' + (g + 1) + '_' + group.key.join('')">
                <div class="codeContLog" v-if="group.value!==null">
                  <template v-for="(word, w) in group.value" :key="w">
                    <div class="sep" v-if="w > 0">
                      <div class="times">
                        <div class="time gap" v-if="group.time[w]" :style="{ width: parseInt(group.time[w] / 10) + 'px' }">
                          <div class="num">{{ group.time[w] }}</div>
                        </div>
                      </div>
                      <div class="keyGap"></div>
                    </div>
                    <div :class="{ key: true, omiss: group.value.length - group.key.length > 0 }">
                      <div class="times">
                        <div class="time key" style="width: 24px"></div>
                      </div>
                      <div :class="{ keyName: true, err: group.value[w] != group.key[w] }">
                        {{ group.value[w] || group.value[w] == 0 ? group.value[w] : '#' }}
                      </div>
                    </div>
                  </template>
                  <template v-if="group.key.length > group.value.length">
                    <template v-for="(omi, o) in group.key" :key="o">
                      <div class="key omission" v-if="o > group.value.length - 1">
                        <div class="omis"></div>
                        <div :class="{ keyName: true, err: !group.value[o] }"></div>
                      </div>
                    </template>
                  </template>
                </div>
                <div class="name" v-if="group.key!==null&&group.value!==null">{{ group.key.join('') }}</div>
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
    name: 'PatExamTrainScore'
  }
</script>
<script setup>
  import { ref, onMounted, onUnmounted, watch } from 'vue'
  import { useRoute } from 'vue-router'
  import trainScore from './js/trainScore.js'
  import chartLab from '../../../../../assets/HJ/postTrain/chartLab.png'
  import useMorse from '../../../../../common/mixin/useMorse.js'
  import scoreSuperb from '../../../../../assets/HJ/postTrain/score-superb.png'
  import scoreSuffice from '../../../../../assets/HJ/postTrain/score-suffice.png'
  import scoreFailed from '../../../../../assets/HJ/postTrain/score-failed.png'
  import resAccuracy from '../../../../../assets/HJ/postTrain/check.png'
  import resSpeed from '../../../../../assets/HJ/train/res-speed.png'
  import resTime from '../../../../../assets/HJ/postTrain/time.png'
  import chartIco1 from '../../../../../assets/HJ/train/chart-ico-1.png'
  import chartIcoOn1 from '../../../../../assets/HJ/train/chart-ico-1-on.png'
  import chartIco2 from '../../../../../assets/HJ/train/chart-ico-2.png'
  import chartIcoOn2 from '../../../../../assets/HJ/train/chart-ico-2-on.png'
  import chartIco3 from '../../../../../assets/HJ/train/chart-ico-3.png'
  import chartIcoOn3 from '../../../../../assets/HJ/train/chart-ico-3-on.png'
  import scoreSuperbHJJ from '../../../../../assets/HJ/postTrain/topBox-after.png'
  import {createFromIconfontCN,QuestionCircleOutlined} from "@ant-design/icons-vue";
  const IconFont = createFromIconfontCN({
    scriptUrl: window.iconUrl,
  });
  const interfaceStyle = window.interfaceStyle
  const { morseCode } = useMorse()
  const showChart = ref('total')

  const { scoreData, loading, patHairTrendBoxRef, trendLogKeyData, resolve,moreLine, switchTelegram, seeCurrKeysHairTrend } = trainScore(showChart)
</script>

<style scoped lang="less">

  @import "./css/ExamTrainScore";
  @import "../../../../../common/styles/css/score";
  .totalTable .head .lab:after{
    border-bottom-width: 3.6vh!important;
  }
  .totalTable .head .lab:before{
    border-bottom-width: 3.6vh!important;
  }
</style>
