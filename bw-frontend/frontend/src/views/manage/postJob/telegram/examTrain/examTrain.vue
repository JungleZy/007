<template>
  <div class="w-full h-full overflow-hidden relative">
    <a-alert v-if="submissionError" type="error" :message="submissionError" style="position:absolute;z-index:1000;top:8px;left:20%">
      <template #description><a-button :loading="submissionBusy" @click="retrySubmit">重试原提交</a-button></template>
    </a-alert>
    <div class="loading" v-show="loading">
      <a-spin size="large" tip="正在努力加载..." />
    </div>
    <div class="w-full h-full trainBoxs content-mask-bg">
      <TrainLeft @startTest="startTrain" @endTest="endTrain" :trainData="trainData">
        <template v-slot:top>
          <div class="desc">
            {{ trainData.status == 0 ? '请点击下方[开始练习]按钮开启训练' : trainData.status == 1 ? '本次练习正在进行，当前总耗时' : trainData.status == 2 ? '本次练习正在进行，当前总耗时' : '本次练习已结束,总用时' }}
          </div>
          <count-down class="width-100-per layout-center" color="#70c9ff" ref="countDown" style="height: 55px" />
        </template>
        <template v-slot:bottom>
          <div class="stateBox">
            <div class="h-full flex"></div>
          </div>
          <div :class="{ flipContainer: true }" style="height: calc(100% - 80px)">
            <div :class="{ 'h-full overflow-auto totalBoxs': true }" style="padding: 0 8px">
              <div class="basicInit" style="height: 100%">
                <div class="tipSymbol">
                  <div class="symItem" v-if="trainData.ruleContent">
                    <div>设定码率：</div>
                    <div>{{ trainData.ruleContent.wpm.base }} 四码组/分</div>
                  </div>
                </div>
                <div class="item relative" v-if="trainData.messageType==2 || trainData.messageType==1">
                  <img :src="labDisturb" class="ico" />
                  <div>
                    <div class="title">
                      <a-tooltip class="layout-left-center" color="orange">
                        <template #title>字码拍发时，【首次码】与【后缀码】之间停顿的最大间隔时长</template>
                        停顿时长(ms)&nbsp;<QuestionCircleOutlined style="color: orange;position: relative;top: 2px"/>
                      </a-tooltip>
                    </div>
                    <div class="tags" style="width: 100%">
                      <div class="cont pr-1">
                        <div class="formSlider time" style="padding-top: 1px">
                          <a-slider v-model:value="pauseDuration" :min="200" :max="2000" :disabled="trainData.status!=1"
                                    :step="100"></a-slider>
                        </div>
                        <div class="w-full layout-side" style="color: #7b90af;font-size: 12px;line-height: 1.2">
                          <span>200</span>
                          <span>2000</span>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
                <div class=" relative" style="padding: 10px 0 10px 20px">
                  <span style="font-weight: bold">自动换行:</span>  <a-switch v-model:checked="autoLine" checked-children="是" un-checked-children="否"></a-switch>
                </div>
                <div class="item relative">
                  <img :src="labType" class="ico" />
                  <div>
                    <div class="title">报文类型</div>
                    <div class="tags nobr">
                      {{trainData.messageType==1?'字码报':trainData.messageType==2?'混合报':'数码报'}}
                    </div>
                  </div>
                </div>
                <div class="item relative">
                  <img :src="labNum" class="ico" />
                  <div>
                    <div class="title">报文组数</div>
                    <div class="tags nobr">{{ trainData.totalNumber }}个</div>
                  </div>
                </div>
                <div class="item relative">
                  <img :src="labSpeed" class="ico" />
                  <div>
                    <div class="title">本次采集码率（预估）</div>
                    <div class="tags nobr">{{ trainData.speed }}四码组/分</div>
                  </div>
                </div>
                <div class="item relative">
<!--                  <img :src="labSpeed" class="ico" />-->
                  <div>
                    <div class="title">播报速率（四码组/分）</div>
                    <a-input style="width: 100px" v-model:value="playSpeed"/>
                    <a-button @click="changePlaySpeed">修改</a-button>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </template>
      </TrainLeft>
      <div class="trainCenter" style="width: calc(100% - 290px)">
        <div class="_top">
          <img :src="topBg" class="bg" style="width: 100%" />
          <div class="cont">
            <div class="scale"></div>
            <div class="vals overflow-auto" ref="patValBoxRef">
              <div v-for="v of patCodeLogs" :key="v" :style="['margin-left:' + v.time / 50 + 'px']">{{ v.key }}</div>
            </div>
          </div>
        </div>
        <div class="_main">
          <div class="cont">
            <div class="patKey">
              <img :src="keyBg" class="bg" />
              <div class="keys overflow-auto" ref="patKeyBoxRef" style="display: block">
                <div class="layout-left-top" v-for="(row,r) of patKeysLogs" :key="r" >
                  <template v-if="r===patKeysLogs.length - 1||r===patKeysLogs.length - 2" v-for="(key, k) of row" :key="k">
                    <div class="key layout-left-top" style="font-family: Consolas, Monaco, 'Andale Mono', 'Ubuntu Mono', monospace;">
                      {{ key.join('') }}
                      <div class="cursor" v-if="r == patKeysLogs.length-1 && k == row.length-1"></div>
                    </div>
                    <template v-if="autoLine">
                      <div v-if="k % 10 == 9 && k != 0" style="width: 100%; height: 1px"></div>
                    </template>
                  </template>
                </div>
              </div>
            </div>
            <div class="patTelegraphBox">
              <div class="switch" @click="switchPage(-1)">
                <img :src="prev" class="img" v-if="trainData.status != 1" />
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
                    <template v-for="(item, index) in trainData.content" :key="index">
                      <div :class="{ key: true, curr: currPageIndex == index }" v-if="item.pageNumber === currPage">
                        {{ item.key.join('') }}
                      </div>
                    </template>
                    <template v-if="trainData.content && trainData.content.length < 100">
                      <div class="key" v-for="(key, index) in 100 - trainData.content.length" :key="index"></div>
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
              <div class="switch" @click="switchPage(1)">
                <img :src="next" class="img" v-if="trainData.status != 1" />
              </div>
            </div>
          </div>
          <div class="_bottom">
            <div class="pag">
              <div class="curr">
                当前：<span class="num">{{ currPage }}</span>
              </div>
              <div class="line"></div>
              <div class="total">
                总数：<span class="num">{{ allPage }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'ExamPostJobTrain'
}
</script>
<script setup>
import { QuestionCircleOutlined } from '@ant-design/icons-vue'
import CountDown from '../../../../../components/common/CountDown.vue'
import TrainLeft from '../../../../../components/postJob/trainLeft/TrainLeft.vue'
import Number from '../../../../../components/number/Number.vue'
import telexTrain from './js/examTrain.js'




import startexercise from '../../../../../assets/HJ/train/start-exercise.png'
import endexercise from '../../../../../assets/HJ/train/end-exercise.png'
import detailexercise from '../../../../../assets/HJ/train/detail-exercise.png'
import labDisturb from '../../../../../assets/HJ/train/new-lab-disturb.png'
import iconImage from "../../js/iconImage";
const {labSpeed,labNum,labType,topBg,keyBg,prev,next} = iconImage()
import {ref} from "vue";
const autoLine = ref(true)
const {
  submissionError, submissionBusy, retrySubmit,
  loading, patValBoxRef, patKeyBoxRef, wsOnline, devOnline, trainData, countDown, currPage, allPage, patCodeLogs,
  patKeysLogs, pauseDuration,currPageIndex, switchPage, startTrain, endTrain,changePlaySpeed,
  playSpeed,lastPatKey
} = telexTrain()
</script>

<style scoped>
@import './js/handkey.css';
.tipSymbol {
  border: 1px solid rgba(255, 255, 213, 0.4);
  padding: 6px 8px 10px;
  background: url('../../../../../assets/HJ/train/hint-bg.png') no-repeat center bottom;
  margin-bottom: 8px;
  font-size: 12px;
}
.tipSymbol .symItem {
  height: 25px;
  display: flex;
  align-items: center;
  color: #ffffd5;
}
._main .patKey .cursor {
  width: 1px;
  height: 20px;
  border-left: 1px solid rgba(255,255,255,.5);
  animation: flicker 1s linear infinite;
  position: relative;
  top: 6px;
}
@keyframes flicker {
  0% {opacity: 0}
  50% {opacity: 1}
  100% {opacity: 0}
}
</style>
