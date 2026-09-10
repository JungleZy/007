<template>
  <!--  ReceivePostTrain-->
  <div class="w-full h-full layout-center content-mask-bg">
    <div class="w-full h-full trainBoxs">
      <TrainLeft @startTest="startTrainInfo" @endTest="endTrainInfo" :trainData="receiveData">
        <template v-slot:top>
          <div class="desc">
            {{
              receiveData.status == 0
                  ? '请点击下方[开始练习]按钮开启训练'
                  : receiveData.status == 1
                      ? '本次练习正在进行，当前总耗时'
                      : receiveData.status == 2
                          ? '本次练习正在进行，当前总耗时'
                          : '本次练习已结束,总用时'
            }}
          </div>
          <count-down class="width-100-per layout-center" color="#70c9ff" ref="trainTimeRef" style="height: 55px"/>
        </template>
        <template v-slot:bottom>
          <div class="stateBox">
            <div class="h-full flex"></div>
            <div class="layout-center">
              <div class="deployBtn" @click="trainDeploy = trainDeploy == 1 ? 2 : 1">
                {{ trainDeploy == 1 ? '配置' : '统计' }}
              </div>
            </div>
          </div>
          <div :class="{ flipContainer: true, on: trainDeploy == 1, un: trainDeploy == 2 }"
               style="height: calc(100% - 100px)">
            <div class="flipLeftBoxs"></div>
            <div class="flipRightBoxs"></div>
            <div class="h-full overflow-auto deployBoxs" style="padding: 0 12px">
              <div class="deployGroup">
                <div class="title">
                  <span class="leftLine"></span>
                  <span class="dot"></span>
                  <span class="text">频率设置</span>
                  <span class="dot"></span>
                  <span class="rightLine"></span>
                </div>
                <div class="cont configureBox">
                  <div class="flex" style="justify-content: space-between; padding-top: 8px">
                    <div class="flex">
                      <div
                          v-for="(item, i) in freqGather.gather"
                          :key="i"
                          :class="{ typeBtn: true, active: item.type == freqGather.curr.type }"
                          @click="handleItem(item)"
                      >
                        {{ item.name }}
                      </div>
                    </div>
                    <div class="auditionBtn" @click="auditionInfo" v-if="receiveData.status != 1">试听</div>
                  </div>
                  <div class="volumeSlider voice">
                    <a-slider v-model:value="frequency" :min="freqGather.curr.min" :max="freqGather.curr.max"
                              :step="freqGather.curr.step"></a-slider>
                  </div>
                  <div class="flex text" style="justify-content: space-between">
                    <div>{{ freqGather.curr.min }}</div>
                    <div>{{ freqGather.curr.max }}</div>
                  </div>
                </div>
              </div>
              <div class="deployGroup">
                <div class="title">
                  <span class="leftLine"></span>
                  <span class="dot"></span>
                  <span class="text">音量设置</span>
                  <span class="dot"></span>
                  <span class="rightLine"></span>
                </div>
                <div class="cont">
                  <div class="volumeBox">
                    <div class="title">
                      <img v-if="audioVolume == 0" :src="volume1" alt=""/>
                      <img v-else-if="audioVolume < 50" :src="volume2" alt=""/>
                      <img v-else-if="audioVolume == 100" :src="volume3" alt=""/>
                      <img v-else :src="volume4" alt=""/>
                      <span>{{ audioVolume }}%</span>
                    </div>
                    <div class="volumeSlider">
                      <a-slider v-model:value="audioVolume" :min="0" :max="100" :step="5"
                                :tooltipVisible="false"></a-slider>
                    </div>
                  </div>
                </div>
              </div>
            </div>
            <div class="h-full overflow-auto totalBoxs" style="padding: 0 8px">
              <div class="basicInit">
                <div class="item relative">
                  <img :src="labType" class="ico"/>
                  <div>
                    <div class="title">类型</div>
                    <div class="tags nobr">
                      {{
                        receiveData.type == 1
                            ? '字码报'
                            : receiveData.type == 0
                                ? '数码报' + (receiveData.codeShort == 1 ? ' - 长码' : ' - 短码')
                                : '混合报'
                      }}
                    </div>
                  </div>
                </div>
                <div class="item relative">
                  <img :src="labNum" class="ico"/>
                  <div>
                    <div class="title">数量</div>
                    <div class="tags nobr">{{ receiveData.totalNumber ?? 0 }}组</div>
                  </div>
                </div>
                <div class="item relative">
                  <img :src="labSpeed" class="ico"/>
                  <div>
                    <div class="title">当前码率</div>
                    <div class="tags nobr">{{ receiveData.rate }}{{ wpmTOmm ? '码/分' : 'WPM' }}</div>
                  </div>
                </div>
                <div class="item relative" v-if="receiveData.disturbText && receiveData.disturbText.length > 0">
                  <img :src="labType" class="ico"/>
                  <div>
                    <div class="title">干扰项</div>
                    <div
                        class="tags nobr"
                        :title="receiveData.disturbText.join(',')"
                        style="min-width: 64%; max-width: 100%; width: auto; padding-right: 8px; display: inline-block"
                    >
                      {{ receiveData.disturbText.join(', ') }}
                    </div>
                  </div>
                </div>
                <div class="item relative" v-if="receiveData.disturbText && receiveData.disturbText.length > 0">
                  <div>
                    <div class="title">干扰强度</div>
                    <div class="volumeSlider voice" style="margin-top: 16px;">
                      <a-slider v-model:value="disturbVol" :min="0" :max="100" :step="5" :tooltipVisible="false"></a-slider>
                    </div>
                  </div>
                </div>
                <div class="layout-left-center relative" style="margin-top: 20px;margin-left: 30px">
                  <a-input-number :min="35" :max="500" v-model:value="audioSpeed"></a-input-number>
                  <div class="item_group btn" @click="changeRate">修改码率</div>
                </div>
                <div class="layout-left-center relative" style="margin-top: 20px;margin-left: 30px">
                  <a-input-number :min="0.5" :max="2" step="0.01" v-model:value="audioSpeedDeviation"></a-input-number>
                  <div class="item_group btn" @click="changeRate">修改偏差</div>
                  <a-tooltip placement="right" color="#47421e" >
                    <template #title>由于算法不同导致的播报速率偏差，播报速率实际偏差值。</template>&nbsp;<QuestionCircleOutlined style="color: orange;font-size: 24px" />
                  </a-tooltip>
                </div>
<!--                查看报底-->
                <div class="layout-left-center relative" style="margin-top: 20px;margin-left: 30px">
                  <div class="item_group btn" @click="openMessageModel">查看报底</div>
                </div>
                <!--                生成报头-->
                <!--              <div class="messageHeaderContainer">-->
                <!--                <div class="messageHeader">号数：<a-input-number v-model:value="messageHeader.nr"  :min="1" :max="1000" :step="1"  /></div>-->
                <!--                <div class="messageHeader">等级：<a-input-number v-model:value="messageHeader.plb" :min="1" :max="1000" :step="1"  /></div>-->
                <!--                <div class="messageHeader">附注：<a-textarea v-model:value="messageHeader.remaks" style="width: 80%"/></div>-->
                <!--                <div class="messageHeader layout-center">-->
                <!--                  <div class="item_group btn" @click="generateMessageHeader">生成</div>-->
                <!--                </div>-->
                <!--              </div>-->
              </div>
            </div>
          </div>
        </template>
      </TrainLeft>
      <div class="trainCenter">
        <div class="w-full h-full relative">
          <video :src="receiveBgMP4" ref="receiveBgRef" loop muted
                 style="object-fit: fill; width: 100%; height: 100%"></video>
          <!--          <img :src="receiveBgPng" v-else style="width: 100%; height: 100%" />-->
          <div class="w-full h-full absolute" style="top: 0"></div>
          <div class="tipsBoxs" v-if="broadcastFinished || receiveData.status == 2">
            <div class="tipsCont">
              <img :src="tipsBg" class="bg"/>
              <div class="cont w-full h-full">
                <div class="title">
                  <div class="text" v-if="receiveData.status == 2"></div>
                  <div class="text" v-else><!--播报提示--></div>
                </div>
                <template v-if="receiveData.status == 2">
                  <div class="textBox">本次训练已结束，请上传您填报的电报纸进行评分！</div>
                  <div class="btns" style="padding-top: 4%">
                    <!--<div class="btn oper max" @click="updateTelegraph(1)">自动识别</div>-->
                    <div class="btn oper max" @click="updateTelegraph(2)">手动填报</div>
                  </div>
                </template>
                <template v-else>
                  <div class="textBox">本次训练的电报播报内容已经播放完毕！<br/><br/>请点击下列操作：</div>
                  <div class="btns">
                    <div class="btn oper" @click="endTrainInfo">评分</div>
                    <div class="btn oper" @click="hardHearingInfo">重听</div>
                  </div>
                </template>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
    <a-modal :destroyOnClose="true" :width="800" class="init_modal_style footer-border-none"
             v-model:visible="previewModal" @cancel="cancelTrainModal">
      <template #title>
        <strong>报文信息</strong>
      </template>
      <template #footer>
        <div></div>
      </template>
      <PreviewMessage :pageData="pageData" :allPage="allPage"></PreviewMessage>
    </a-modal>
  </div>
</template>

<script>
export default {
  name: 'ReceivePostTrain'
}
</script>
<script setup>
import {ref, onMounted, onUnmounted, watch, inject} from 'vue'
import {useRoute} from 'vue-router'
import receiveTrain from './js/receiveTrain.js'
import Number from '../../../../../components/number/Number.vue'
import TrainLeft from '../../../../../components/postJob/trainLeft/TrainLeft.vue'
import PreviewMessage from "../../../../../components/previewMessage/PreviewMessage.vue";

import end from '../../../../../assets/HJ/receive/end.png'
import reset from '../../../../../assets/HJ/receive/reset.png'
import volume1 from '../../../../../assets/HJ/train/volume-none.png'
import volume2 from '../../../../../assets/HJ/train/volume-mini.png'
import volume3 from '../../../../../assets/HJ/train/volume-big.png'
import volume4 from '../../../../../assets/HJ/train/volume-small.png'

import receiveBgMP4HJ from '../../../../../assets/HJ/receive/receiveBg.mp4'
import receiveBgPngHJ from '../../../../../assets/HJ/receive/receiveBg.png'
import tipsBgHJ from '../../../../../assets/HJ/postTrain/tipsBg.png'

import receiveBgMP4HJJ from '../../../../../assets/HJJ/receive/receiveBg.mp4'
import receiveBgPngHJJ from '../../../../../assets/HJJ/receive/receiveBg.png'
import tipsBgHJJ from '../../../../../assets/HJJ/postTrain/tipsBg.png'
import receiveBgMP4LJ from '../../../../../assets/LJ/receive/receiveBg.mp4'
import receiveBgPngLJ from '../../../../../assets/LJ/receive/receiveBg.png'
import tipsBgLJ from '../../../../../assets/LJ/postTrain/tipsBg.png'

import receiveBgMP4KJ from '../../../../../assets/HJ/receive/receiveBg.mp4'
import receiveBgPngKJ from '../../../../../assets/KJ/receive/receiveBg.png'
import tipsBgKJ from '../../../../../assets/KJ/postTrain/tipsBg.png'

import iconImage from "../../js/iconImage";
import labTypeHJ from "../../../../../assets/HJ/train/lab-type.png";
import labNumHJ from "../../../../../assets/HJ/train/lab-num.png";
import labSpeedHJ from "../../../../../assets/HJ/train/lab-speed.png";
import labTypeHJJ from "../../../../../assets/HJJ/train/new-lab-type.png";
import labNumHJJ from "../../../../../assets/HJJ/train/lab-number.png";
import labSpeedHJJ from "../../../../../assets/HJJ/train/new-lab-speed.png";
import labTypeLJ from "../../../../../assets/LJ/train/lab-type.png";
import labNumLJ from "../../../../../assets/LJ/train/lab-num.png";
import labSpeedLJ from "../../../../../assets/LJ/train/lab-speed.png";
import {QuestionCircleOutlined} from "@ant-design/icons-vue";

const interfaceStyle = window.interfaceStyle
let receiveBgMP4, receiveBgPng, tipsBg
if (interfaceStyle === 'HJ') {
  receiveBgMP4 = receiveBgMP4HJ
  receiveBgPng = receiveBgPngHJ
  tipsBg = tipsBgHJ
} else if (interfaceStyle === 'HJJ') {
  receiveBgMP4 = receiveBgMP4HJJ
  receiveBgPng = receiveBgPngHJJ
  tipsBg = tipsBgHJJ
} else if (interfaceStyle === 'KJ') {
  receiveBgMP4 = receiveBgMP4KJ
  receiveBgPng = receiveBgPngKJ
  tipsBg = tipsBgKJ
}else {
  receiveBgMP4 = receiveBgMP4LJ
  receiveBgPng = receiveBgPngLJ
  tipsBg = tipsBgLJ
}
const previewModal = ref(false)
const cancelTrainModal = () => {
  previewModal.value = false
}
const openMessageModel = ()=>{
  previewModal.value = true
}
const {labType, labNum, labSpeed} = iconImage()
const trainDeploy = ref(0)
const cool = inject('cool')
const wpmTOmm = inject('wpmTOmm')
const {
  receiveBgRef,
  receiveData,
  frequency,
  freqGather,
  audioVolume,
  trainTimeRef,
  broadcastFinished,
  audioSpeed,
  audioSpeedDeviation,
  disturbVol,
  pageData,
  messageHeader,
  changeRate,
  auditionInfo,
  startTrainInfo,
  endTrainInfo,
  hardHearingInfo,
  updateTelegraph,
  generateMessageHeader,
    showCode,
  allPage
} = receiveTrain(wpmTOmm)
const handleItem = item => {
  freqGather.value.curr = item
  frequency.value = item.default
}
</script>

<style scoped lang="less">
.messageHeaderContainer{
  padding: 20px;
  .messageHeader{
    margin-top: 20px;
    display: flex;
    align-items: flex-start;
  }
}

@import './css/receiveTrain.less';
</style>
