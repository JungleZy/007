<template>
  <div class="w-full h-full overflow-hidden relative">
    <div class="w-full h-full trainBoxs">
      <TrainLeft
        @startTest="startTrainInfo"
        @endTest="endTrainInfo"
        :trainData="militaryData"
        :type="'score'"
      >
        <template v-slot:top>
          <div class="desc">
            {{
              militaryData.status == 0
                ? '请点击下方[开始练习]按钮开启训练'
                : militaryData.status == 1
                ? '本次练习正在进行，当前总耗时'
                : militaryData.status == 2
                ? '本次练习已结束,总用时'
                : ''
            }}
          </div>
          <count-down
            class="width-100-per layout-center"
            color="#70c9ff"
            ref="trainTimeRef"
            style="height: 55px"
          />
        </template>
        <template v-slot:bottom>
          <div style=" height: calc(100% - 60px)">
            <div class="flipContainer" style="height: 100%; padding-top: 10px">
              <div class="h-full overflow-auto totalBoxs">
                <div class="basicInit" style="padding: 8px">
                  <div class="item relative">
                    <img :src="labType" class="ico" />
                    <div>
                      <div class="title">军语类型</div>
                      <template v-if="militaryData.types">
                        <div class="tags nobr" :title="militaryData.types.join('、')">
                          <template v-if="militaryData.types.length == 0">全部类型</template>
                          <template v-else>
                            <template v-for="type in militaryData.types">【{{ type }}】</template>
                          </template>
                        </div>
                      </template>
                    </div>
                  </div>
                  <div class="questionTitle">
                    <div class="layout-left-center">
                      <div class="text">总览</div>
                      <div class="triangle"></div>
                    </div>
                    <div class="r">{{ militaryData.totalNumber }}题</div>
                  </div>
                  <div class="answerList overflow-auto" style="height: calc(100% - 130px)">
                    <div class="listBox" v-if="militaryData.testPaperList">
                      <div
                        v-for="(item, i) in militaryData.testPaperList"
                        :key="i"
                        :class="{
                          answer: true,
                          on: militaryData.testPaperList[i].userAnswer,
                          active: i == currAnswer,
                          correct:
                            militaryData.status == 2 &&
                            militaryData.testPaperList[i].userAnswer == militaryData.testPaperList[i].correctAnswer,
                          error:
                            militaryData.status == 2 &&
                            militaryData.testPaperList[i].userAnswer != militaryData.testPaperList[i].correctAnswer
                        }"
                        @click="currAnswer=i;changeAnimate()"
                      >
                        <div
                          v-if="militaryData.testPaperList[i].userAnswer!==null"
                          style="font-weight: bolder"
                        >
                          {{ militaryData.testPaperList[i].userAnswer }}
                        </div>
                        <div v-else>{{ i +1 }}</div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </template>
        <template v-slot:btn>
          <div class="start btn-animate btn-animate-orange" @click="showResultModal = true" v-if="militaryData.status == 2">
            <img v-if="interfaceStyle==='HJ'" :src="detailexercise" alt="">
            <span v-else>查看成绩</span>
          </div>
        </template>
      </TrainLeft>
      <div class="h-full trainCenter topicBox" style="width: calc(100% - 290px);">
        <div class="topic w-full h-full">
          <div class="title">
            <div class="text" v-if="militaryData.testPaperList" :style="{fontSize: (fs * 2 + 42) + 'px'}">
              {{militaryData.testPaperList[currAnswer].title}}
            </div>
            <img :src="keyBgBtm" class="img">
          </div>
          <div class="options">
            <template v-if="militaryData.testPaperList">
              <div v-for="(opt,o) in JSON.parse(militaryData.testPaperList[currAnswer].option)"
                   :class="{option: true, on: o == militaryData.testPaperList[currAnswer].userAnswer, play: militaryData.status<=1,
                            answer: militaryData.status==2&&o==militaryData.testPaperList[currAnswer].correctAnswer}"
                   @click="questAnswer(o)">
                <div class="lab">{{o}}</div>
                <div class="cont" :style="{fontSize: (fs * 1 + 15) + 'px'}">
                  <div class="overflow-auto" style="max-height: 100%">{{opt}}</div>
                </div>
              </div>
            </template>
          </div>
        </div>
        <div class="btns">
          <div class="bCont selectTopic">
            <div class="prev" @click="changeAnswer(-1)" :style="{fontSize: (fs * 1 + 16) + 'px'}">
              <div class="text" v-if="militaryData.testPaperList">
                {{militaryData.testPaperList[currAnswer-1]?militaryData.testPaperList[currAnswer-1].title:'无'}}
              </div>
            </div>
            <div class="next" @click="changeAnswer(1)" :style="{fontSize: (fs * 1 + 16) + 'px'}">
              <div class="text" v-if="militaryData.testPaperList">
                {{militaryData.testPaperList[currAnswer+1]?militaryData.testPaperList[currAnswer+1].title:'无'}}
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="achievementMasking" v-show="showResultModal">
      <div class="achievement" v-if="interfaceStyle==='HJ'">
        <div class="cont">
          <div class="dataBox">
            <div class="resLeft w-full">
              <div class="top">
                <img v-if="militaryData.score>=80" :src="scoreSuperb" class="img">
                <img v-else-if="militaryData.score>=60" :src="scoreSuffice" class="img">
                <img v-else :src="scoreFailed" class="img">
                <div :class="{score:true,superb: militaryData.score>=80,suffice: militaryData.score>=60&&militaryData.score<80}">
                  {{militaryData.score}}
                </div>
              </div>
              <div class="bottom">
                <div class="resGroup">
                  <img :src="resAccuracy" alt="">
                  <div class="cont">
                    <div class="desc" :style="{fontSize: (fs * 1 + 14) + 'px'}">正确率</div>
                    <div class="num" :style="{fontSize: (fs * 1 + 24) + 'px'}">{{militaryData.accuracy}}%</div>
                  </div>
                </div>
                <div class="resGroup">
                  <img :src="resTime" alt="">
                  <div class="cont">
                    <div class="desc" :style="{fontSize: (fs * 1 + 14) + 'px'}">训练用时</div>
                    <div class="num" :style="{fontSize: (fs * 1 + 24) + 'px'}">{{resDuration}}</div>
                  </div>
                </div>
              </div>
            </div>
            <div class="splitLine"></div>
            <div class="resRight w-full">
              <div class="resTextItem">
                <div class="desc" :style="{fontSize: (fs * 1 + 14) + 'px'}">军语总数</div>
                <div class="num" :style="{fontSize: (fs * 1 + 24) + 'px'}">{{militaryData.totalNumber}}个</div>
              </div>
              <div class="resTextItem">
                <div class="desc" :style="{fontSize: (fs * 1 + 14) + 'px'}">正确数量</div>
                <div class="num" :style="{fontSize: (fs * 1 + 24) + 'px'}">{{militaryData.correctNumber}}个</div>
              </div>
              <div class="resTextItem">
                <div class="desc" :style="{fontSize: (fs * 1 + 14) + 'px'}">错误数量</div>
                <div class="num" :style="{fontSize: (fs * 1 + 24) + 'px'}">{{militaryData.errorNumber}}个</div>
              </div>
            </div>
          </div>
        </div>
        <div class="resClose">
          <div class="closeInfo">
            <div class="close" @click="showResultModal=false"></div>
          </div>
        </div>
      </div >
      <div class="achievement" v-else>
        <div class="cont">
          <div class="dataBox">
            <div class="resLeft w-full">
              <div class="top_all_box">
                <div class="tab_top_box">
                  <div class="text">{{ militaryData.score }}</div>
                </div>
                <div class="center_box">
                  <div class="box">
                    <div class="text1">正确率</div>
                    <div>{{ militaryData.accuracy }}%</div>
                  </div>
                  <div class="box">
                    <div>训练用时</div>
                    <div>{{ resDuration }}</div>
                  </div>
                </div>
              </div>
              <div class="bottom_box">
                <div class="tab_top">
                  <div class="tab">军语总数</div>
                  <div class="tab">正确数量</div>
                  <div class="tab">错误总数</div>
                </div>
                <div class="bottom_box">
                  <div class="tab">{{ militaryData.totalNumber }}个</div>
                  <div class="tab">{{ militaryData.correctNumber }}个</div>
                  <div class="tab">{{ militaryData.errorNumber }}个</div>
                </div>
              </div>
              <div class="bottom_box">
                <div class="tab_top" style="align-items: center; justify-content: center">
                  评分等级
                </div>
                <div class="bottom_box" v-for="item in gradeTypeList" :key="item.id">
                  <div class="tab">{{ item.level }}</div>
                  <div class="tab">{{ item.accuracy }}%</div>
                  <div class="tab">{{ item.description }}</div>
                </div>
                <div class="bottom_box" v-if="gradeTypeList.length == 0">
                  <div style="width: 100%; text-align: center; line-height: 36px">
                    暂无匹配的数据
                  </div>
                </div>
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
  name: 'DittoPostWordingTrain'
}
</script>
<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import militaryTrain from './js/militaryTrain.js'
import Number from '../../../../../components/number/Number.vue'
import TrainLeft from '../../../../../components/postJob/trainLeft/TrainLeft.vue'

import detailexercise from '../../../../../assets/HJ/train/detail-exercise.png'
import dataEmpty from '../../../../../assets/HJ/train/dataEmpty.png'
import scoreSuperb from '../../../../../assets/HJ/postTrain/score-superb.png'
import scoreSuffice from '../../../../../assets/HJ/postTrain/score-suffice.png'
import scoreFailed from '../../../../../assets/HJ/postTrain/score-failed.png'
import resAccuracy from '../../../../../assets/HJ/train/res-accuracy.png'
import resTime from '../../../../../assets/HJ/train/res-time.png'
import keyBgBtmHJJ from '../../../../../assets/HJJ/term/keyBgBtm.png'
import keyBgBtmLJ from '../../../../../assets/LJ/term/keyBgBtm.png'
const interfaceStyle = window.interfaceStyle
let keyBgBtm = keyBgBtmHJJ
if (interfaceStyle==='LJ'){
  keyBgBtm = keyBgBtmLJ
}
import iconImage from "../../js/iconImage";
const {labType} = iconImage()
const fs = ref(JSON.parse(localStorage.getItem('fs')));
const {
  militaryData,
  trainTimeRef,
  currAnswer,
  resDuration,
  showResultModal,
  startTrainInfo,
  endTrainInfo,
  changeAnswer,
  questAnswer,
  changeAnimate,
  gradeTypeList
} = militaryTrain()
</script>
<style scoped lang="less">
 @import "./css/index.less";
  @import "../../css/addTrainStyle.less";
</style>
