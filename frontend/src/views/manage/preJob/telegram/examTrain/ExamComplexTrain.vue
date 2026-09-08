<template>
  <div class="telexTrainBox h-full w-full content-mask-bg relative">
    <div class="statisticsBox statisticalBox">
      <div class="lineBox">
        <div class="box"><img :src="countLab" alt="" /> <span>总数量</span></div>
        <div class="box">{{ trainData.totalNumber }}个</div>
      </div>
      <div class="lineBox">
        <div class="box">
          <img :src="errorLab" alt="" />
          <span>错误数</span>
        </div>
        <div class="box">{{ trainData.errorNumber }}个</div>
      </div>
      <div class="lineBox">
        <div class="box"><img :src="successLab" alt="" /> <span>正确率</span></div>
        <div class="box">{{ parseFloat(trainData.accuracy) }}%</div>
      </div>
      <div class="lineBox">
        <div class="box"><img :src="speedLab" alt="" /> <span>速度</span></div>
        <div class="box">{{ trainData.speed }}码/分</div>
      </div>
      <div class="linebtns">
        <div @click="goBack" class="layout-center btn"><IconFont type="icon-rollback" style="margin-right: 5px"></IconFont> 退出</div>
      </div>
    </div>
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
        <template v-if="trainData.messageType==1||trainData.messageType==2">
          <div class="title mt-1">
            <span class="leftLine"></span>
            <span class="dot"></span>
            <span class="text">
            <a-tooltip class="layout-left-center" color="orange">
              <template #title>字码拍发时，【首次码】与【后缀码】之间停顿的最大间隔时长</template>
              停顿时长(ms)&nbsp;<QuestionCircleOutlined style="color: orange;position: relative;top: 1px"/>
            </a-tooltip>
          </span>
            <span class="dot"></span>
            <span class="rightLine"></span>
          </div>
          <div class="cont" style="padding: 0">
            <div class="formSlider">
              <a-slider v-model:value="pauseDuration" :min="200" :max="2000" :step="100" style="margin: 8px 0 5px"></a-slider>
            </div>
            <div class="w-full layout-side" style="color: #7b90af;font-size: 12px">
              <span>200</span>
              <span>2000</span>
            </div>
          </div>
        </template>
      </div>
    </div>
    <div class="w-full layout-center" v-if="isfocus" style="padding: 40px 0">
      <div style="max-width: calc(100% - 480px); display: flex; width: 660px">
        <div class="messageBox big">
          <div
            class="imgBox"
            style="height: 160px; padding: 0 40px"
            v-if="activeMessage.text"
          >
            <img
              v-for="(img, i) in activeMessage.text.split('')"
              :src="fileUrl + 'big/' + codeType + '/' + img + '.png'"
              alt=""
            />
          </div>
          <a-input
            v-model:value="activeMessage.value"
            :disabled="trainData.status != 1"
            class="value isfocusInput"
            @change="changeMessage(activeMessage)"
            readonly
          ></a-input>
        </div>
      </div>
    </div>
    <div
      class="w-full cardBox"
      :style="[isfocus ? 'height: calc(100% - 330px)' : 'height:100%']"
    >
      <div class="w-full layout-left-top" style="padding: 5px 0; position: sticky;top: 0px;padding: 0px 58px 0px 10px;">
        <div v-for="i in 10" style=" display: flex; justify-content: center; flex-direction: column;width: 10%;align-items: center; ">
          <div class="color1" >{{ i }}</div>
          <div  style="width: 100%;height: 1px;margin-bottom: 5px;" class="relative bg1">
            <div class="bg2" style="height: 2px; width: 10px;position: absolute;top: -1px;" v-if="i == 1"></div>
            <div class="bg2" style="height: 2px; width: 10px;position: absolute;top: -1px;right: 0px;" v-if="i == 10"></div>
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
            style="width: 10%; display: flex; height: max-content"
            v-for="(v, index) of messageData"
            @click="selectMessage(v, index)"
          >
            <div
              class="messageBox"
              @click="selectCard(index)"
              :class="[
                !v.type || (trainData.status == 3 && v.text != v.value)
                  ? 'erroyMessageBox'
                  : '',
                activeIndex == index && trainData.status == 1 ? 'activeBox' : ''
              ]"
            >
              <div class="imgBox">
                <img
                  v-for="img of v.text.split('')"
                  :src="fileUrl + codeType + '/' + img + '.png'"
                  alt=""
                />
              </div>
              <a-input
                v-model:value="v.value"
                :disabled="trainData.status != 1 || isfocus"
                class="value"
                @change="changeMessage(v)"
              ></a-input>
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
            v-for="(i, index) in parseInt(trainData.totalNumber / 10)"
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
              :src="twinkeGif"
              alt=""
            />
            <img v-else :src="teinke" alt="" />
          </div>
        </div>
      </div>
    </div>

<!--    <div-->
<!--      class="achievementMasking"-->
<!--      v-show="showResultModal && trainData.status == 3"-->
<!--    >-->
<!--      <div class="achievement">-->
<!--        <div class="resTitle">-->
<!--          <img v-if="parseFloat(trainData.accuracy) > 40" :src="restext1" />-->
<!--          <img-->
<!--            v-else-if="parseFloat(trainData.accuracy) > 30"-->
<!--            :src="restext2"-->
<!--          />-->
<!--          <img v-else :src="restext3" />-->
<!--        </div>-->
<!--        <div-->
<!--          class="cont"-->
<!--          style="display: flex; align-items: center; height: 400px"-->
<!--        >-->
<!--          <div class="dataBox">-->
<!--            <div class="resLeft w-full">-->
<!--              <div class="top">-->
<!--                <img-->
<!--                  v-if="parseFloat(trainData.accuracy) > 40"-->
<!--                  :src="tagscrapsuccess"-->
<!--                  alt=""-->
<!--                />-->
<!--                <img-->
<!--                  v-else-if="parseFloat(trainData.accuracy) > 30"-->
<!--                  :src="tagscrapwarning"-->
<!--                  alt=""-->
<!--                />-->
<!--                <img v-else :src="tagscraperror" alt="" />-->
<!--                <div class="desc" style="color: #7b90af">本次训练用时</div>-->
<!--                <div class="time">-->
<!--                  {{ computationTime(trainData.duration) }}-->
<!--                </div>-->
<!--              </div>-->
<!--              <div class="bottom">-->
<!--                <div class="resGroup">-->
<!--                  <img :src="resaccuracy" alt="" />-->
<!--                  <div class="cont">-->
<!--                    <div class="desc">正确率</div>-->
<!--                    <div class="num">{{ parseFloat(trainData.accuracy) }}%</div>-->
<!--                  </div>-->
<!--                </div>-->
<!--                <div class="resGroup">-->
<!--                  <img :src="resspeed" alt="" />-->
<!--                  <div class="cont">-->
<!--                    <div class="desc">速度</div>-->
<!--                    <div class="num">-->
<!--                      {{ trainData.speed-->
<!--                      }}<span style="font-size: 20px">WPM</span>-->
<!--                    </div>-->
<!--                  </div>-->
<!--                </div>-->
<!--              </div>-->
<!--            </div>-->
<!--            <div class="splitLine"></div>-->
<!--            <div class="resRight w-full">-->
<!--              <div class="resTextItem">-->
<!--                <div class="desc">报文总数</div>-->
<!--                <div class="num">{{ trainData.totalNumber }}个</div>-->
<!--              </div>-->
<!--              <div class="resTextItem">-->
<!--                <div class="desc">错误总数</div>-->
<!--                <div class="num">{{ trainData.errorNumber }}个</div>-->
<!--              </div>-->
<!--            </div>-->
<!--          </div>-->
<!--        </div>-->
<!--        <div class="resClose">-->
<!--          <div class="closeInfo">-->
<!--            <div class="close" @click="showResultModal = false"></div>-->
<!--          </div>-->
<!--        </div>-->
<!--      </div>-->
<!--    </div>-->
  </div>
</template>
<script>
import { nextTick, onBeforeUnmount } from 'vue'

export default {
  name: 'ExamComplexTrain'
}
</script>
<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { createFromIconfontCN,QuestionCircleOutlined } from '@ant-design/icons-vue'
import examTrain from './js/examTrain.js'
import Number from '../../../../../components/number/Number.vue'
import hunderd from '../../../../../assets/HJ/telexTrain/telegram/hunderd.png'
import twinkeGif from '../../../../../assets/HJ/telexTrain/telegram/twinke.gif'
import teinke from '../../../../../assets/HJ/telexTrain/telegram/teinke.png'
import countLab from '../../../../../assets/HJJ/telexTrain/count.png'
import errorLab from '../../../../../assets/HJJ/telexTrain/error.png'
import successLab from '../../../../../assets/HJJ/telexTrain/success.png'
import speedLab from '../../../../../assets/HJJ/telexTrain/speed.png'
import restext1 from '../../../../../assets/HJ/train/res-text-1.png'
import restext2 from '../../../../../assets/HJ/train/res-text-2.png'
import restext3 from '../../../../../assets/HJ/train/res-text-3.png'
import tagscrapsuccess from '../../../../../assets/HJ/train/tag-scrap-success.png'
import tagscrapwarning from '../../../../../assets/HJ/train/tag-scrap-warning.png'
import tagscraperror from '../../../../../assets/HJ/train/tag-scrap-error.png'
import resaccuracy from '../../../../../assets/HJ/train/res-accuracy.png'
import resspeed from '../../../../../assets/HJ/train/res-speed.png'
import CutDown from '../../../../../components/cutDown/CutDown.vue'
const IconFont = createFromIconfontCN({
  scriptUrl: window.iconUrl
})
const fileUrl = ref(window.fileUrl + '/006/code/')
const fs = ref(JSON.parse(localStorage.getItem('fs')))
const router = useRouter()
const trainDeploy = ref(0)
const showResultModal = ref(true)
const codeTypeArr = ref([
  { type: 'gradient', name: '渐变' },
  { type: 'metal', name: '金属' },
  { type: 'chapped', name: '皲裂' },
  { type: 'white', name: '纯白' }
])
const codeType = ref('gradient')

const { wsOnline, devOnline, messageData, nowTime, trainData, activeMessage, isfocus, activeIndex, pauseDuration,
  selectCard, changeSwitch, selectMessage, endExamTrain, changeMessage, changeFocus, beginExamTrain } = examTrain()

/**
 * 格式化时间
 * @param total
 * @returns {string}
 */
const computationTime = total => {
  let hour, min, sec, day, h, m, s
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
const goBack = () => {
  router.go(-1)
}
</script>

<style scoped lang="less">
  /*@import '../train/js/handkey.css';*/
@import "./css/ExamComplexTrain";
@import "../../css/code";
@import "../../css/messageBox";

</style>
