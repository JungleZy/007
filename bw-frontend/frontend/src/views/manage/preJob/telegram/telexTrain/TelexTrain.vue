<template>
  <div
    class="telexTrainBox h-full w-full overflow-hidden relative content-mask-bg"
    style="display: flex; justify-content: space-between"
  >
    <div class="trainLeft">
      <div class="statisticsBox">
        <div class="statistics">
          <img :src="countp" alt="" />
          <span>数量 : {{ trainData.totalNumber }}个</span>
        </div>
        <div class="statistics">
          <img :src="errorp" alt="" />
          <span>错误 : {{ trainData.errorNumber }}个</span>
        </div>
        <div class="statistics">
          <img :src="successp" alt="" />
          <span>正确率 : {{ correct }}%</span>
        </div>
        <div class="statistics">
          <img :src="speed" alt="" />
          <span>速度 : {{ trainData.speed * 4 }}码/分</span>
        </div>
        <!--        <div class="layout-left-top">-->
        <!--          <a-switch   v-model:checked="isfocus" v-if="trainData.status==1"-->
        <!--                      @change="changeSwitch" checked-children="专注" un-checked-children="经典"></a-switch>-->
        <!--        </div>-->
      </div>
      <div class="exerciseBtn" style="padding-left: 30px; margin-top: 20px">
        <div
          class="layout-center practiseBtn"
          @click="goback"
          style="margin-left: 20px"
        >
          <IconFont type="icon-rollback" style="margin-right: 5px"></IconFont>
          退出
        </div>
        <!--        <div class="btn oper end" v-if="trainData.status==1" @click="endExerciseInfo">结束练习</div>-->
        <!--        <div class="btn oper " v-if="trainData.status==3" @click="showResultModal=true">查看成绩</div>-->
      </div>
    </div>
    <div class="w-full" style="height: 100%; overflow: hidden">
      <div class="w-full layout-center" v-if="isfocus" style="padding: 30px 0">
        <div
          style="max-width: 100%; display: flex; width: 660px; min-width: 360px"
          class="focus"
        >
          <div class="messageBox big">
            <div class="imgBox" style="height: 160px; padding: 0 40px">
              <img
                v-for="img of activeMessage.text"
                :src="fileUrl + 'big/' + codeType + '/' + img + '.png'"
                alt=""
              />
            </div>
            <a-input
              v-model:value="activeMessage.value"
              style="height: 50px; font-size: 24px"
              class="value isfocusInput"
              @keydown="keyCodeDown2"
              @change="changeMessage(activeMessage)"
              @focus="getFocus(activeMessage)"
            ></a-input>
          </div>
        </div>
      </div>
      <div
        class="w-full cardBox absolute"
        style="left: 0px"
        :style="[isfocus ? 'height: calc(100% - 330px)' : 'height:100%']"
      >
        <div
          class="w-full layout-left-top"
          style="
            padding: 5px 0;
            position: sticky;
            top: 0px;
            padding: 0px 58px 0px 10px;
          "
        >
          <div
            v-for="i in 10"
            style="
              display: flex;
              justify-content: center;
              flex-direction: column;
              width: 10%;
              align-items: center;
            "
          >
            <div style="color: #7b90af">{{ i }}</div>
            <div
              style="
                width: 100%;
                height: 1px;
                background: #354971;
                margin-bottom: 5px;
              "
              class="relative"
            >
              <div
                style="
                  background: #6ebdff;
                  height: 2px;
                  width: 10px;
                  position: absolute;
                  top: -1px;
                "
                v-if="i == 1"
              ></div>
              <div
                style="
                  background: #6ebdff;
                  height: 2px;
                  width: 10px;
                  position: absolute;
                  top: -1px;
                  right: 0px;
                "
                v-if="i == 10"
              ></div>
            </div>
            <div style="width: 1px; height: 5px; background: #354971"></div>
          </div>
        </div>
        <div
          class="w-full layout-left-top scorebox"
          style="
            height: calc(100% - 43px);
            overflow: auto;
            max-height: max-content;
          "
        >
          <div
            style="
              max-height: max-content;
              width: calc(100% - 46px);
              display: flex;
              flex-wrap: wrap;
              padding: 0 4px;
            "
          >
            <div
              style="
                width: 10%;
                display: flex;
                height: max-content;
                max-width: 10%;
                overflow: hidden;
                box-sizing: border-box;
              "
              v-for="(v, index) of message"
              @click="selectMessage(v, index)"
            >
              <div
                class="messageBox"
                @click="selectCard(index)"
                :class="[
                  !v.type || (trainData.status == 3 && v.text != v.value)
                    ? 'erroyMessageBox'
                    : '',
                  activeIndex == index && trainData.status == 1
                    ? 'activeBox'
                    : ''
                ]"
              >
                <div class="imgBox">
                  <img
                    v-for="img of v.text"
                    :src="fileUrl + codeType + '/' + img + '.png'"
                    alt=""
                  />
                </div>
                <!--                <a-input v-model:value="v.value" style="font-size: 16px;-ms-ime-mode: disabled;ime-mode:active" :disabled="trainData.status!=1 || isfocus" @keydown="keyCodeDown($event,v,index)" class="value" @change="changeMessage(v)" @focus="getFocus(v)" ></a-input>-->
                <div
                  style="font-size: 16px; ime-mode: active"
                  :contenteditable="trainData.status == 1 && !isfocus"
                  @keydown="keyCodeDown($event, v, index)"
                  class="editDiv value"
                  @input="divChange($event, v)"
                  @focus="getFocus(v)"
                >
                  {{ v.value }}
                </div>
              </div>
            </div>
          </div>
          <div
            style="width: 36px; height: 100%; margin-right: 10px"
            v-if="trainData.totalNumber"
          >
            <div
              class="rightscale layout-center relative"
              :class="[
                index == 0 ? 'righttop' : '',
                i == Math.ceil(trainData.totalNumber / 10) && i % 10 != 0
                  ? 'rightbottom'
                  : ''
              ]"
              v-for="(i, index) in Math.ceil(trainData.totalNumber / 10)"
              :style="[i % 10 == 0 ? '' : ' border-right: 1px solid #354971;']"
            >
              <img
                v-if="i % 10 == 0"
                :src="hunderd"
                style="margin-right: -10px"
                alt=""
              />
              <img
                v-else-if="
                  index == Math.floor(activeIndex / 10) && trainData.status == 1
                "
                :src="twinke"
                alt=""
              />
              <img v-else :src="teinke" alt="" />
            </div>
          </div>
        </div>
      </div>
    </div>
    <div style="width: 278px">
      <div class="trainTime">
        <div class="timeNum" style="right: 196px">
          <number :value="nowTime.h1"></number>
        </div>
        <div class="timeNum" style="right: 166px">
          <number :value="nowTime.h2"></number>
        </div>
        <div class="timeNum" style="right: 116px">
          <number :value="nowTime.m1"></number>
        </div>
        <div class="timeNum" style="right: 86px">
          <number :value="nowTime.m2"></number>
        </div>
        <div class="timeNum" style="right: 36px">
          <number :value="nowTime.s1"></number>
        </div>
        <div class="timeNum" style="right: 6px">
          <number :value="nowTime.s2"></number>
        </div>
      </div>
      <div
        class="deployBoxs absolute"
        style="right: 10px; top: 50px; width: 240px"
      >
        <div class="deployGroup">
          <div class="title">
            <span class="leftLine"></span>
            <span class="dot"></span>
            <span class="text">字码风格</span>
            <span class="dot"></span>
            <span class="rightLine"></span>
          </div>
          <div class="cont codeCont">
            <div
              v-for="(item, i) in codeTypeArr"
              :class="{ codeStyle: true, on: item.type == codeType }"
              @click="codeType = item.type"
            >
              <img :src="fileUrl + item.type + '/A.png'" alt="" />
              <div class="name" :style="{ fontSize: fs * 1 + 12 + 'px' }">
                {{ item.name }}
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
    <div
      class="achievementMasking"
      v-if="showResultModal && trainData.status == 3"
    >
      <div class="achievement">
        <div class="resTitle">
          <img v-if="parseFloat(trainData.accuracy) > 40" :src="restext1" />
          <img
            v-else-if="parseFloat(trainData.accuracy) > 30"
            :src="restext2"
          />
          <img v-else :src="restext3" />
        </div>
        <div
          class="cont"
          style="display: flex; align-items: center; height: 400px"
        >
          <div class="dataBox">
            <div class="resLeft w-full">
              <div class="top">
                <img
                  v-if="parseFloat(trainData.accuracy) > 40"
                  :src="tagscrapsuccess"
                  alt=""
                />
                <img
                  v-else-if="parseFloat(trainData.accuracy) > 30"
                  :src="tagscrapwarning"
                  alt=""
                />
                <img v-else :src="tagscraperror" alt="" />
                <div class="desc" style="color: #7b90af">本次训练用时</div>
                <div class="time">
                  {{ computationTime(trainData.duration) }}
                </div>
              </div>
              <div class="bottom">
                <div class="resGroup">
                  <img :src="resaccuracy" alt="" />
                  <div class="cont">
                    <div class="desc">正确率</div>
                    <div class="num">{{ correct }}%</div>
                  </div>
                </div>
                <div class="resGroup">
                  <img :src="resspeed" alt="" />
                  <div class="cont">
                    <div class="desc">速度</div>
                    <div class="num">
                      {{ trainData.speed
                      }}<span style="font-size: 20px">组/分</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>
            <div class="splitLine"></div>
            <div class="resRight w-full">
              <div class="resTextItem">
                <div class="desc">报文总数</div>
                <div class="num">{{ trainData.totalNumber }}个</div>
              </div>
              <div class="resTextItem">
                <div class="desc">错误总数</div>
                <div class="num">{{ trainData.errorNumber }}个</div>
              </div>
            </div>
          </div>
        </div>
        <div class="resClose">
          <div class="closeInfo">
            <div class="close" @click="showResultModal = false"></div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { nextTick, onBeforeUnmount } from 'vue'

export default {
  name: 'TelexTrain'
}
</script>
<script setup>
import telexTrain from './js/telexTrain.js'
import CountDown from '../../../../../components/common/CountDown.vue'
import Number from '../../../../../components/number/Number.vue'
import labnum from '../../../../../assets/HJ/train/lab-num.png'
import laberr from '../../../../../assets/HJ/train/lab-err.png'
import labaccuracy from '../../../../../assets/HJ/train/lab-accuracy.png'
import labspeed from '../../../../../assets/HJ/train/lab-speed.png'
import startexercise from '../../../../../assets/HJ/train/start-exercise.png'
import endexercise from '../../../../../assets/HJ/train/end-exercise.png'
import detailexercise from '../../../../../assets/HJ/train/detail-exercise.png'
import hunderd from '../../../../../assets/HJ/telexTrain/telegram/hunderd.png'
import twinke from '../../../../../assets/HJ/telexTrain/telegram/twinke.gif'
import teinke from '../../../../../assets/HJ/telexTrain/telegram/teinke.png'
import restext1 from '../../../../../assets/HJ/train/res-text-1.png'
import restext2 from '../../../../../assets/HJ/train/res-text-2.png'
import restext3 from '../../../../../assets/HJ/train/res-text-3.png'
import tagscrapsuccess from '../../../../../assets/HJ/train/tag-scrap-success.png'
import tagscrapwarning from '../../../../../assets/HJ/train/tag-scrap-warning.png'
import tagscraperror from '../../../../../assets/HJ/train/tag-scrap-error.png'
import resaccuracy from '../../../../../assets/HJ/train/res-accuracy.png'
import resspeed from '../../../../../assets/HJ/train/res-speed.png'

import countp from '../../../../../assets/HJ/telexTrain/count.png'
import errorp from '../../../../../assets/HJ/telexTrain/error.png'
import successp from '../../../../../assets/HJ/telexTrain/success.png'
import speed from '../../../../../assets/HJ/telexTrain/speed.png'
import CutDown from '../../../../../components/cutDown/CutDown.vue'
import { ref } from 'vue'
import { createFromIconfontCN } from '@ant-design/icons-vue'
const fileUrl = ref(window.fileUrl + '/006/code/')
const countDown = ref(null)
const showResultModal = ref(false)
const IconFont = createFromIconfontCN({
  scriptUrl: window.iconUrl
})
const fs = ref(JSON.parse(localStorage.getItem('fs')));
const trainDeploy = ref(0)
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
const codeTypeArr = ref([
  { type: 'gradient', name: '渐变' },
  { type: 'metal', name: '金属' },
  { type: 'chapped', name: '皲裂' },
  { type: 'white', name: '纯白' }
])
const codeType = ref('gradient')
const { message, trainData, correct, activeMessage, isfocus, activeIndex, nowTime, selectCard, keyCodeDown, keyCodeDown2, changeSwitch, changeMessage, changeFocus, divChange, beginTrain, selectMessage, getFocus, goback, endExerciseInfo } = telexTrain(countDown)
</script>

<style scoped lang="less">
  :deep(.ant-input) {
    padding: 0px !important;
  }
  :deep(.ant-input-disabled) {
    border-bottom: 1px solid #354971 !important;
    color: #b8a5a5 !important;
  }
  @import '../telex/js/handkey.css';
  ::-webkit-scrollbar {
    display: none !important;
  }
  .editDiv {
    overflow: hidden;
    /*max-width: 137px;*/
  }
  .focus {
    /*background: url("../../../../../assets/HJ/telexTrain/focus.png") no-repeat;*/
    /*background-size: 100% 100%;*/
    padding: 20px;
  }
  .focusBox {
    width: 100%;
    height: max-content;
  }
  .trainTime {
    height: 54px;
    width: 278px;
    background: url('../../../../../assets/HJ/receive/trainTime.gif') no-repeat
    center;
  }
  .cool .trainTime {
    background: url('../../../../../assets/HJ/receive/trainTime.png') no-repeat
    center;
  }
  .trainTime .timeNum {
    width: 29px;
    height: 36px;
    display: flex;
    align-items: center;
    justify-content: center;
    position: absolute;
    top: 6px;
  }
  .trainTime .timeNum .num {
    height: 40px;
    font-size: 28px;
    line-height: 40px;
    overflow: hidden;
    display: flex;
    text-align: center;
    color: #dbe5fa;
  }
  .statisticsBox {
    padding-left: 30px;
    /*display: flex;*/
    /*align-items: flex-end;*/
  }
  .statistics {
    background: url('../../../../../assets/HJ/telexTrain/statistics.png') no-repeat;
    display: flex;
    align-items: flex-end;
    padding-left: 15px;
    padding-bottom: 10px;
    background-size: 100% 100%;
    height: 38px;
    margin-bottom: 10px;
  }
  .statistics img {
    width: 15px;
    height: 15px;
  }
  .statistics span {
    line-height: 12px;
    padding-left: 10px;
    color: #adcde8;
  }

  @keyframes glint {
    0% {
      -webkit-box-shadow: inset 0 0 4px rgba(110, 189, 255, 0.2);
      box-shadow: inset 0 0 4px rgba(110, 189, 255, 0.2);
    }
    25% {
      -webkit-box-shadow: inset 0 0 8px rgba(110, 189, 255, 0.4);
      box-shadow: inset 0 0 10px rgba(110, 189, 255, 0.4);
    }
    50% {
      -webkit-box-shadow: inset 0 0 16px rgba(110, 189, 255, 0.8);
      box-shadow: inset 0 0 20px rgba(110, 189, 255, 0.8);
    }
    75% {
      -webkit-box-shadow: inset 0 0 8px rgba(110, 189, 255, 0.4);
      box-shadow: inset 0 0 10px rgba(110, 189, 255, 0.4);
    }
    100% {
      -webkit-box-shadow: inset 0 0 4px rgba(110, 189, 255, 0.2);
      box-shadow: inset 0 0 4px rgba(110, 189, 255, 0.2);
    }
  }
  .rightscale {
    width: 100%;
    height: 98px;
  }
  .righttop {
    background: url('../../../../../assets/HJ/telexTrain/telegram/top.png') no-repeat;
    border: 0px !important;
    background-position: right;
  }
  .rightbottom {
    background: url('../../../../../assets/HJ/telexTrain/telegram/bottom.png')
    no-repeat;
    background-position: right;
    border: 0px !important;
  }
  .telexTrainBox {
    /*background: #132649;*/
  }

  .messageBox {
    width: 100%;
    border: 1px solid #354971;
    padding: 0 5px 10px;
    margin: 4px 6px 8px 6px;
    overflow: hidden;
    height: max-content;
    background-color: #122548;
    box-shadow: inset 0 60px 30px -60px rgb(26 53 107);
  }
  .messageBox.big {
    border: none;
    background-color: transparent;
    box-shadow: inset 0 0 10px 10px #192e52;
    position: relative;
    max-height: 248px;
    padding: 10px 30px 16px;
  }
  .messageBox.big:before,
  .messageBox.big:after {
    content: '';
    width: 130px;
    position: absolute;
    top: 0;
    bottom: 0;
    z-index: 6;
  }
  .messageBox.big:before {
    background: url('../../../../../assets/HJ/preTrain/val-left-bg.png') no-repeat
    left center;
    background-size: auto 100%;
    left: 0;
  }
  .messageBox.big:after {
    background: url('../../../../../assets/HJ/preTrain/val-right-bg.png') no-repeat
    right center;
    background-size: auto 100%;
    right: 0;
  }
  .erroyMessageBox {
    background: url('../../../../../assets/HJ/train/key-err-bg.jpg') no-repeat top
    center;
  }
  .activeBox {
    background: url('../../../../../assets/HJ/train/key-bg.jpg') no-repeat top center;
    animation: glint 2s linear infinite;
    -webkit-animation: glint 2s linear infinite;
  }
  .activeBox input {
    background: #ffffff;
    color: #0a1a35;
  }

  .practiseBtn {
    padding: 2px 16px;
    border: 1px solid rgb(4 90 198);
    margin-top: 10px;
    width: max-content;
    height: max-content;
    border-radius: 3px;
    cursor: pointer;
    color: #dbe5fa;
    box-shadow: inset 0 -10px 10px -10px rgba(12, 114, 226, 0.8);
  }
  .practiseBtn:hover {
    box-shadow: inset 0 10px 10px -10px rgba(12, 114, 226, 0.8);
  }
  .imgBox {
    /*height: 60px;*/
    height: 48px;
    width: 100%;
    padding: 0 6px;
    display: flex;
    align-items: center;
    justify-content: center;
    margin-left: 2px;
    position: relative;
    z-index: 8;
  }
  .imgBox img {
    width: 30%;
    margin-left: -4px;
  }
  .value {
    width: 100%;
    height: 26px;
    border: 1px solid #354971;
    background-color: #0d1c38;
    display: flex;
    border-radius: 2px;
    justify-content: center;
    align-items: center;
    cursor: pointer;
    text-align: center;
    position: relative;
    z-index: 8;
  }

  @media (max-width: 1260px) {
    .trainLeft {
      width: 190px;
    }
    .imgBox {
      padding: 0;
      height: 32px;
    }
    .imgBox img {
      width: 36%;
    }
  }
</style>
