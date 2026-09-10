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
            </template>
            <img v-else :src="scoreSuperbHJJ" class="img" />
<!--            <div class="name nobr" style="font-size: 12px">{{scoreData.title}}</div>-->
            <div :class="{score:true,superb:parseFloat(scoreData.score)>80,suffice:parseFloat(scoreData.score)>60}">{{ scoreData.score }}</div>
          </div>
          <div class="bottom" style="justify-content: space-around">
            <div class="resGroup">
              <img :src="resAccuracy" class="ico">
              <div class="cont">
                <div class="desc">正确率</div>
                <div class="num">{{ scoreData.accuracy }}%</div>
              </div>
            </div>
            <div class="resGroup">
              <img :src="resTime" class="ico">
              <div class="cont">
                <div class="desc">时间</div>
                <div class="num">{{ scoreData.duration }}</div>
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
              <div :class="{'tab colu': true, on: showChart=='line'}" @click="showChart = 'line'">
                <img :src="chartIcoOn2" v-show="showChart == 'line'" class="ico">
                <img :src="chartIco2" v-show="showChart != 'line'" class="ico">
                码率(码/分)
              </div>
            </div>
            <div class="box">
              <div class="chart overflow-auto">
                <div id="lineChart" class="totalChart" v-show="showChart=='line'"></div>
                <div class="totalTable" v-show="showChart=='total'">
                  <div class="head">
                    <div class="item lab"></div>
                    <div class="item full">值</div>
                    <div class="item full">扣分情况</div>
                  </div>
                  <template v-if="scoreData.deductInfo">
                    <div class="row">
                      <div class="item lab full">码率</div>
                      <div class="item">{{ Number(scoreData.deductInfo.speedNumber) }} 码/分</div>
                      <div class="item">{{ scoreData.deductInfo.speedScore }} 分</div>
                    </div>
                    <div class="row" v-if="scoreData.deductInfo.errorNumber > 0">
                      <div class="item lab full">错码</div>
                      <div class="item">{{ scoreData.deductInfo.errorNumber }} 组</div>
                      <div class="item">{{ scoreData.deductInfo.errorScore }} 分</div>
                    </div>
                    <div class="row" v-if="scoreData.deductInfo.lackNumber > 0">
                      <div class="item lab full">少码</div>
                      <div class="item">{{ scoreData.deductInfo.lackNumber }} 组</div>
                      <div class="item">{{ scoreData.deductInfo.lackScore }} 分</div>
                    </div>
                    <div class="row" v-if="scoreData.deductInfo.moreNumber > 0">
                      <div class="item lab full">多码</div>
                      <div class="item">{{ scoreData.deductInfo.moreNumber }} 组</div>
                      <div class="item">{{ scoreData.deductInfo.moreScore }} 分</div>
                    </div>
                    <div class="row" v-if="scoreData.deductInfo.lackGroupNumber > 0">
                      <div class="item lab full">少组</div>
                      <div class="item">{{ scoreData.deductInfo.lackGroupNumber }} 组</div>
                      <div class="item">{{ scoreData.deductInfo.lackGroupScore }} 分</div>
                    </div>
                    <div class="row" v-if="scoreData.deductInfo.moreGroupNumber > 0">
                      <div class="item lab full">多组</div>
                      <div class="item">{{ scoreData.deductInfo.moreGroupNumber }} 组</div>
                      <div class="item">{{ scoreData.deductInfo.moreGroupScore }} 分</div>
                    </div>
                    <div class="row" v-if="scoreData.deductInfo.lackLineNumber > 0">
                      <div class="item lab full">少行</div>
                      <div class="item">{{ scoreData.deductInfo.lackLineNumber }} 行</div>
                      <div class="item">{{ scoreData.deductInfo.lackLineScore }} 分</div>
                    </div>
                    <div class="row" v-if="scoreData.deductInfo.moreLineNumber > 0">
                      <div class="item lab full">多行</div>
                      <div class="item">{{ scoreData.deductInfo.moreLineNumber }} 行</div>
                      <div class="item">{{ scoreData.deductInfo.moreLineScore }} 分</div>
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
                   @click="switchTelegram(-1)">上一页</div>
              <div :class="{pag: true, disabled: scoreData.currPage == scoreData.pag}"
                   @click="switchTelegram(1)">下一页</div>
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
                <template v-for="(key, index) in scoreData.content" :key="index">
                  <div v-if="key.key != '#' && index < 100"
                       :class="{
                          key: true,
                          error: resolve[scoreData.currPage-1] && resolve[scoreData.currPage-1].message[index] &&
                                 key.key.join('') != resolve[scoreData.currPage-1].message[index] &&
                                 resolve[scoreData.currPage-1].message[index] != '',
                          omission: !resolve[scoreData.currPage-1] || !resolve[scoreData.currPage-1].message[index] ||
                                    resolve[scoreData.currPage-1].message[index] == '',
                          more: resolve[scoreData.currPage-1]&&resolve[scoreData.currPage-1].moreObj[index+''],
                          warning: key.value.indexOf('?') > -1,
                       }"
                       @click="seeCurrKeysHairTrend(key, index)"
                  >
                    <div class="keyErr"
                         v-if="(resolve[scoreData.currPage-1]&&resolve[scoreData.currPage-1].message[index]&&
                                key.key.join('')!=resolve[scoreData.currPage-1].message[index]&&
                                resolve[scoreData.currPage-1].message[index]!='')||
                               (resolve[scoreData.currPage-1]&&resolve[scoreData.currPage-1].moreObj[index+''])">
                      <div class="box">
                        <div class="text" :style="{ fontSize: resolve[scoreData.currPage-1].message[index] == '' ? '15px' : '16px' }">
                          {{ resolve[scoreData.currPage-1].message[index] }}
                          {{ resolve[scoreData.currPage-1].moreObj[index+'']?','+resolve[scoreData.currPage-1].moreObj[index+'']:'' }}
                        </div>
                      </div>
                    </div>
                    <div class="keyOmis"
                         v-if="!resolve[scoreData.currPage-1] || !resolve[scoreData.currPage-1].message[index] ||
                                resolve[scoreData.currPage-1].message[index] == ''">
                      <div class="box">
                        <div class="text">漏拍</div>
                      </div>
                    </div>
                    <div class="layout-center">
                      <template v-for="(item, i) in key.key" :key="i">
                        <div :style="{ color: resolve[scoreData.currPage-1] && resolve[scoreData.currPage-1].message[index] == item ? '#fff' : 'inherit' , fontWeight: 'bolder'}">{{ item }}</div>
                      </template>
                    </div>
                  </div>
                  <div v-if="key.key == '#' && index < 100" class="key error" @click="seeCurrKeysHairTrend(key, index)"
                       style="background-color: #4d2734">
                    <div class="keyErr" v-if="resolve[scoreData.currPage-1].message[index] != ''">
                      <div class="box">
                        <div class="text" :style="{ fontSize: resolve[scoreData.currPage-1].message[index] == '' ? '15px' : '16px' }">
                          {{ resolve[scoreData.currPage-1].message[index] }}
                        </div>
                      </div>
                    </div>
                    <div class="layout-center">#</div>
                  </div>
                </template>
                <template v-if="scoreData.content.length < 100">
                  <div class="key" v-for="(key, index) in 100 - scoreData.content.length" :key="index"></div>
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
            <div class="text" v-if="scoreData.total.cm > 0">错码：【{{ scoreData.total.cm }}】</div>
            <div class="text" v-if="scoreData.total.sm > 0">少码：【{{ scoreData.total.sm }}】</div>
            <div class="text" v-if="scoreData.total.dm > 0">多码：【{{ scoreData.total.dm }}】</div>
            <div class="text" v-if="scoreData.total.sz > 0">漏拍：【{{ scoreData.total.sz }}】</div>
            <div class="text" v-if="scoreData.total.dz > 0">多组：【{{ scoreData.total.dz }}】</div>
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
            <div class="leg omission">漏拍</div>
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
            <template v-for="(group, g) in trendLogKeyData" :key="g">
              <div class="sep" v-if="g > 0">
                <div class="times" v-if="group.time.length > 0">
                  <div class="time gap" :style="{ width: parseInt(group.time[0] / 10) + 'px' }">
                    <div class="num">{{ group.time[0] }}</div>
                  </div>
                </div>
              </div>
              <div class="groupLog" :id="'ht_' + scoreData.currPage + '_' + (g + 1) + '_' + group.key.join('')">
                <div class="codeContLog">
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
                <div class="name" v-if="group.key">{{ group.key.join('') }}</div>
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
  import { ref, onMounted, onUnmounted, watch, defineProps } from 'vue'
import {useRoute} from "vue-router";
import trainScore from "./js/trainScore.js";
import chartLab from '../../../../../../assets/HJ/postTrain/chartLab.png';
import useMorse from "../../../../../../common/mixin/useMorse.js";
import scoreSuperb from "../../../../../../assets/HJ/postTrain/score-superb.png";
import scoreSuffice from "../../../../../../assets/HJ/postTrain/score-suffice.png";
import scoreFailed from "../../../../../../assets/HJ/postTrain/score-failed.png";
import resAccuracy from "../../../../../../assets/HJ/postTrain/check.png";
import resSpeed from "../../../../../../assets/HJ/train/res-speed.png";
import resTime from "../../../../../../assets/HJ/postTrain/time.png";
import chartIco1 from "../../../../../../assets/HJ/train/chart-ico-1.png";
import chartIcoOn1 from "../../../../../../assets/HJ/train/chart-ico-1-on.png";
import chartIco2 from "../../../../../../assets/HJ/train/chart-ico-2.png";
import chartIcoOn2 from "../../../../../../assets/HJ/train/chart-ico-2-on.png";
import chartIco3 from "../../../../../../assets/HJ/train/chart-ico-3.png";
import chartIcoOn3 from "../../../../../../assets/HJ/train/chart-ico-3-on.png";
  import scoreSuperbHJJ from '../../../../../../assets/HJ/postTrain/topBox-after.png'
  const interfaceStyle = window.interfaceStyle
const {morseCode} = useMorse();
const showChart = ref('total');
const { selfId } = defineProps({
  selfId: {
    type: String,
    default: ''
  }
})
console.log(selfId)
const {
  scoreData,loading,patHairTrendBoxRef,trendLogKeyData,resolve,switchTelegram,seeCurrKeysHairTrend
} = trainScore(showChart,selfId);

</script>

<style scoped lang="less">
  @import './css/score';
  @import '../../../../../../common/styles/css/score';

</style>