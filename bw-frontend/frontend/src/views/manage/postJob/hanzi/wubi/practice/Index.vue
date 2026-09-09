<template>
  <div class="telexTrainBox content-mask-bg h-full w-full overflow-auto layout-left-top relative">
    <TrainLeft @startTest="beginTrain" @endTest="saveTest" :trainData="trainData" :type="'score'">
      <template v-slot:top>
        <div class="desc">当前训练时长</div>
        <count-down
          class="width-100-per layout-center"
          color="#70c9ff"
          ref="countDown"
          style="height: 55px"
        />
      </template>
      <template v-slot:bottom>
        <div class="stateBox">
          <div class="h-full flex"></div>
          <div class="layout-center">
            <a-switch
              v-model:checked="isfocus"
              v-if="trainData.status === 1 && trainData.type === 1"
              @change="changeSwitch"
              checked-children="专注"
              un-checked-children="经典"
            ></a-switch>
         </div>
          <div class="layout-center">
            <a-switch
              v-model:checked="inputeMethod"
              @change="changeInputeMethod"
              checked-children="系统五笔输入法"
              un-checked-children="训练系统五笔输入法"
            ></a-switch>
         </div>
        </div>
        <div
          :class="{ flipContainer: true, on: trainDeploy === 1, un: trainDeploy === 2 }"
          style="height: calc(100% - 100px);"
        >
          <div class="flipLeftBoxs"></div>
          <div :class="{ 'h-full overflow-auto totalBoxs': true }" style="padding: 0 8px">
            <div class="basicInit" style="position: relative">
              <div class="item relative">
                <img :src="labCheck" class="ico" />
                <div>
                  <div class="title">正确</div>
                  <div class="tags nobr">
                    {{ trainData.correctNum * 1 }}{{ trainData.type === 0 ? '个' : '组' }}
                  </div>
                </div>
              </div>
              <div class="item relative">
                <img :src="laberr" class="ico" />
                <div>
                  <div class="title">错误</div>
                  <div class="tags nobr">
                    {{ trainData.errorNum * 1 }}{{ trainData.type === 0 ? '个' : '组' }}
                  </div>
                </div>
              </div>
              <div class="item relative">
                <img :src="labCheck" class="ico" />
                <div>
                  <div class="title">正确率</div>
                  <div class="tags nobr">{{ trainData.accuracy * 1 }}%</div>
                </div>
              </div>
              <div class="item relative">
                <img v-if="interfaceStyle==='KJ'" :src="resspeedKJ" class="ico" />
                <img v-else-if="interfaceStyle!=='LJ'" :src="labSpeed" class="ico" />
                <img v-else :src="resspeed" class="ico" />
                <div>
                  <div class="title">速度</div>
                  <div class="tags nobr">{{ trainData.speed * 1 }}字/分</div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </template>
      <template v-slot:btn>
        <div class="start" @click="showResultModal = true" v-if="trainData.status === 2">
          <img v-if="interfaceStyle==='HJ'" :src="detailexercise" alt="">
          <span v-else>查看成绩</span>
        </div>
      </template>
    </TrainLeft>
    <div
      class="rightBox"
      style="width: calc(100% - 290px); height: 100%; overflow: hidden; margin-left: 10px;"
    >
      <div class="w-full h-full layout-center" v-if="message === null">
        <a-spin size="large" tip="正在努力加载..." />
      </div>
      <div class="w-full" style="height: 100%">
        <div
          class="w-full h-full layout-left-top scorebox"
          style="overflow: auto; max-height: max-content"
        >
          <WZTrain
            :message="message"
            :trainData="trainData"
            :isfocus="isfocus"
            :inputeMethod="inputeMethod"
          ></WZTrain>
        </div>
      </div>
    </div>
    <div class="achievementMasking" v-show="showResultModal && trainData.status === 2">
      <div class="achievement">
        <div class="resTitle">
          <img v-if="parseFloat(trainData.accuracy) > 80" :src="restext" />
          <img v-else-if="parseFloat(trainData.accuracy) > 60" :src="restext2" />
          <img v-else :src="restext3" />
        </div>
        <div class="cont" style="display: flex; align-items: center">
          <div class="dataBox">
            <div class="resLeft w-full">
              <div class="top">
                <img v-if="parseFloat(trainData.accuracy) > 40" :src="tagscrapsuccess" alt="" />
                <img
                  v-else-if="parseFloat(trainData.accuracy) > 30"
                  :src="tagscrapwarning"
                  alt=""
                />
                <img v-else :src="tagscraperror" alt="" />
                <div class="desc" style="color: #7b90af">本次训练用时</div>
                <div class="time">{{ computationTime(trainData.duration) }}</div>
              </div>
              <div class="bottom">
                <div class="resGroup">
                  <img :src="resaccuracy" alt="" />
                  <div class="cont">
                    <div class="desc">正确率</div>
                    <div class="num">{{ trainData.accuracy }}%</div>
                  </div>
                </div>
                <div class="resGroup">
                  <img :src="speed" alt="" />
                  <div class="cont">
                    <div class="desc">速度</div>
                    <div class="num">
                      {{ trainData.speed }}<span style="font-size: 20px">字/分</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>
            <div class="splitLine"></div>
            <div class="resRight w-full">

              <div class="resTextItem">
                <div class="desc">正确字数</div>
                &nbsp;{{ trainData.correctNum }}{{ trainData.type === 0 ? '字' : '组' }}
              </div>
              <div class="resTextItem">
                <div class="desc">错误字数</div>
                &nbsp;{{ trainData.errorNum }}{{ trainData.type === 0 ? '字' : '组' }}
              </div>
            </div>
            <div class="splitLine"></div>
            <div class="resRight w-full">
              <div class="bg" style="align-items: center; justify-content: center">评分等级</div>
              <div class="resText_box" v-for="item in gradeTypeList" :key="item.id">
                <div class="resText_item">{{ item.level }}</div>
                <div class="resText_item">{{ item.accuracy }}%</div>
                <div class="resText_item">{{ item.description }}</div>
              </div>
              <div class="resText_box" v-if="gradeTypeList.length === 0">
                <div style="width: 100%; text-align: center; line-height: 36px">暂无匹配的数据</div>
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
export default {
  name: 'Practive'
}
</script>

<script setup>

import parctice from './js/parctice.js'
import WZTrain from './compoents/WZTrain.vue'
import TrainLeft from '../../../../../../components/postJob/trainLeft/TrainLeft.vue'
import detailexercise from '../../../../../../assets/HJ/train/detail-exercise.png'

import CountDown from '../../../../../../components/common/CountDown.vue'
import Number from '../../../../../../components/number/Number.vue'
import resspeed from '../../../../../../assets/LJ/train/new-lab-speed.png'
import resspeedKJ from '../../../../../../assets/KJ/train/new-lab-speed.png'

import restext from '../../../../../../assets/HJ/train/res-text-1.png'
import restext2 from '../../../../../../assets/HJ/train/res-text-2.png'
import restext3 from '../../../../../../assets/HJ/train/res-text-3.png'
import tagscrapsuccess from '../../../../../../assets/HJ/train/tag-scrap-success.png'
import tagscrapwarning from '../../../../../../assets/HJ/train/tag-scrap-warning.png'
import tagscraperror from '../../../../../../assets/HJ/train/tag-scrap-error.png'

import resaccuracyHJ from '../../../../../../assets/HJ/postTrain/check.png'
import resaccuracyKJ from '../../../../../../assets/KJ/postTrain/check.png'
import speedHJ from '../../../../../../assets/HJ/postTrain/speed.png'
import speedKJ from '../../../../../../assets/KJ/postTrain/speed.png'
import iconImage from "../../../js/iconImage";

import { ref } from 'vue'
import { createFromIconfontCN } from '@ant-design/icons-vue'
let resaccuracy,speed;

const interfaceStyle = window.interfaceStyle
if(interfaceStyle==='KJ'){
  resaccuracy = resaccuracyKJ
  speed = speedKJ
}else {
  resaccuracy = resaccuracyHJ
  speed = speedHJ
}


const {labSpeed,laberr,labNum,labCheck} = iconImage()

const fileUrl = ref(window.fileUrl + '/006/code/')
const countDown = ref(null)
const showResultModal = ref(true)
const IconFont = createFromIconfontCN({
  scriptUrl: window.iconUrl
})
const inputeMethod = ref(false)
const changeInputeMethod = () => {}
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
const {
  message,
  trainData,
  correct,
  activeMessage,
  isfocus,
  activeIndex,
  inputIndex,
  beginTrain,
  changeSwitch,
  saveTest,
  goback,
  gradeTypeList
} = parctice(countDown)
</script>

<style scoped lang="less">
  @import '../../pinyin/practice/css/index';

  /*@import '../../../../preJob/telegram/train/js/handkey.css';*/
:deep {
  .ant-input {
    padding: 0px !important;
  }
  .ant-input-disabled {
    border-bottom: 1px solid rgba(198, 187, 187, 0.5) !important;
    color: #b8a5a5 !important;
  }

  [type='text']:focus {
    --tw-ring-color: rgba(0, 0, 0, 0);
  }
  .ant-input:focus,
  .ant-input:hover {
    border-color: rgba(0, 0, 0, 0);
    box-shadow: 0 0 0 0px rgba(0, 0, 0, 0);
  }
}

</style>
