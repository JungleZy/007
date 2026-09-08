<template>
  <div
    class="telexTrainBox h-full w-full overflow-hidden relative content-mask-bg"
    style="display: flex; justify-content: space-between"
  >
    <div class="">
      <div class="statisticsBox statisticalBox">
        <div class="lineBox">
          <div class="box"><img :src="countp" alt="" /> <span>数量</span></div>
          <div class="box">{{ trainData.totalNumber }}个</div>
        </div>
        <div class="lineBox">
          <div class="box">
            <img :src="errorp" alt="" />
            <span>错误</span>
          </div>
          <div class="box">{{ trainData.errorNumber }}个</div>
        </div>
        <div class="lineBox">
          <div class="box"><img :src="successp" alt="" /> <span>正确率</span></div>
          <div class="box">{{ correct }}%</div>
        </div>
        <div class="lineBox">
          <div class="box"><img :src="speed" alt="" /> <span>速度</span></div>
          <div class="box">{{ trainData.speed }}码/分</div>
        </div>
        <div class="linebtns">
          <div @click="goback" class="layout-center btn"><IconFont type="icon-rollback" style="margin-right: 5px"></IconFont>退出</div>
        </div>
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
            <div class="color1">{{ i }}</div>
            <div

              style="
                width: 100%;
                height: 1px;
                margin-bottom: 5px;
              "
              class="relative bg1"
            >
              <div
                class="bg2"
                style="
                  height: 2px;
                  width: 10px;
                  position: absolute;
                  top: -1px;
                "
                v-if="i == 1"
              ></div>
              <div
                class="bg2"
                style="
                  height: 2px;
                  width: 10px;
                  position: absolute;
                  top: -1px;
                  right: 0px;
                "
                v-if="i == 10"
              ></div>
            </div>
            <div class="bg1" style="width: 1px; height: 5px;"></div>
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
                  : '',
                  i % 10 == 0 ? '' : 'border1'
              ]"
              v-for="(i, index) in Math.ceil(trainData.totalNumber / 10)"
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
      <CutDown :nowTime="nowTime"></CutDown>
      <div
        class="deployBoxs absolute"
        style="right: 10px; top: 60px; width: 240px"
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

  import countp from '../../../../../assets/HJJ/telexTrain/count.png'
  import errorp from '../../../../../assets/HJJ/telexTrain/error.png'
  import successp from '../../../../../assets/HJJ/telexTrain/success.png'
  import speed from '../../../../../assets/HJJ/telexTrain/speed.png'
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
 @import "./css/TelexTrain";
  @import "../../css/code";
  @import "../../css/messageBox";
</style>
