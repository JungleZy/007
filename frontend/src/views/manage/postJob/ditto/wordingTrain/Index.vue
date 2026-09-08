<template>
  <div class="w-full h-full overflow-hidden relative">
    <div class="w-full h-full trainBoxs layout-side">
      <TrainLeft @startTest="beginExerciseInfo" @endTest="finish" :trainData="trainData" :type="'score'">
        <template v-slot:top>
          <div class="desc">{{ trainData.title }}</div>
          <count-down
            class="width-100-per layout-center"
            color="#70c9ff"
            ref="countDown"
            style="height: 55px"
          />
        </template>
        <template v-slot:bottom>
          <div class="stateBox" v-if="trainData.trainType == 0">
            干扰项
            <div :class="{ tipBtn: true, active: isfocus }" @click="isfocus = !isfocus"></div>
          </div>
          <div
            class="flipContainer"
            :style="{ height: 'calc(100% - ' + (trainData.trainType == 0 ? 102 : 45) + 'px)' }"
          >
            <div
              class="h-full totalBoxs"
              v-if="trainData.trainType == 0"
              style="padding: 10px 8px 0px 8px"
            >
              <div class="tipSymbol" v-if="isfocus">
                <div class="symItem">
                  <div>换组：</div>
                  <div>Tab</div>
                </div>
                <div class="symItem">
                  <div>换行：</div>
                  <div>↑ ↓</div>
                </div>
              </div>
              <div
                class="disturbDeploy overflow-auto"
                :style="[isfocus ? 'height: calc(100% - 68px)' : 'height:100%']"
                v-if="disturbList.length > 0"
              >
                <div class="disturbItem" v-for="(item, i) in disturbList" :key="i">
                  <div class="text">{{ item.name }}</div>
                  <div class="cont">
                    <img
                      v-if="trainData.status == 1 && trainData.audioState"
                      :src="videoV"
                      alt=""
                    />
                    <img v-else :src="videoP" alt="" />
                  </div>
                </div>
              </div>
              <div v-else class="layout-center" style="height: 100%">
                <div class="interfere"></div>
              </div>
            </div>
          </div>
        </template>
        <template v-slot:btn>
          <div class="start btn-animate btn-animate-orange" @click="showResultModal = true" v-if="trainData.status == 2">
            <img v-if="interfaceStyle==='HJ'" :src="detailexercise" alt="">
            <span v-else>查看成绩</span>
          </div>
        </template>
      </TrainLeft>
      <div class="h-full pl-2" style="width: calc(100% - 280px)">
        <div class="w-full h-full overflow-hidden relative">
          <div class="w-full layout-center top topBG" style="height: 92px; padding: 0 20px 2px">
            <img
              v-if="(trainData.status == 0 && trainData.audioState) || trainData.trainType == 1"
              :src="videoP"
              style="height: 65px; width: 100%"
              alt=""
            />
            <img
              v-if="trainData.status == 1 && trainData.audioState && trainData.trainType == 0"
              :src="videoV"
              style="height: 65px; width: 100%"
              alt=""
            />
            <div v-if="!trainData.audioState || trainData.status == 2" style="font-size: 16px">
              {{ trainData.status == 1 ? '训练已完毕请填写答案' : '训练已完毕' }}
            </div>
          </div>
          <div class="w-full p-2 center" style="height: calc(100% - 92px - 10px)">
            <div style="width: 100%; height: 100%; overflow: auto" v-if="interfaceStyle==='HJ'">
              <template v-for="(d,index) in trainData.data">
                <div class="p-1 card relative"
                     :style="[trainData.type==1?'width:33%':'width:20%']"
                     style="height: 120px;min-width:260px;float: left;position: relative;transition: all 2s;display: flex">
                  <div class="w-full h-full layout-side" :class="[trainData.index==index&&trainData.status==1?'active':'']">
                    <div class="cardLeft"></div>
                    <div class="cardCenter w-full h-full" ></div>
                    <div class="cardRight">
                      <div class="text">{{ index + 1 }}</div>
                    </div>
                  </div>
                  <div class="p-1 absolute layout-center-v " v-if="trainData.trainType==0"
                       style="top: 28px;left: 15px;width: calc(100% - 40px);border-radius: 5px;"
                       :class="[inputIndex==index&&trainData.status==1?'activeCard':'']">
                    <div class="w-full mb-1 layout-left-center relative term ">
                      <div :contenteditable="trainData.status==1" class="inputs "
                           @input="term($event,d)"
                           @focus="getFocus(index)"
                           :style="[trainData.status==2&&d.key!==d.answer.key?'color:red;cursor: pointer':'']"
                           placeholder="用语" style="width: 100%">
                        {{trainData.status==2?d.key:''}}
                      </div>
                      <div class="keyErr" v-if="trainData.status==2&&d.key!==d.answer.key">
                        {{d.answer.key!==''?d.answer.key:'未输入答案'}}
                      </div>
                      <!--                     <a-input style="width: 100%" placeholder="用语" v-model:value="d.answer.key" />-->
                    </div>
                    <div class="w-full layout-left-center relative meaning">
                      <div :contenteditable="trainData.status==1" class="inputs"
                           @input="meaning($event,d)"
                           @focus="getFocus(index)"
                           :style="[trainData.status==2&&(d.value).trim()!==d.answer.value?'color:red;cursor: pointer':'']"
                           placeholder="含义" style="width: 100%">
                        {{trainData.status==2?d.value:''}}
                      </div>
                      <div class="keyErr" v-if="trainData.status==2&&(d.value).trim()!==d.answer.value">
                        {{d.answer.value!==''?d.answer.value:'未输入答案'}}
                      </div>
                      <!--                     <a-input style="width: 100%" placeholder="含义"  v-model:value="d.answer.value" />-->
                    </div>
                  </div>
                  <div class="p-1 absolute layout-center-v " v-else
                       style="top: 28px;left: 15px;width: calc(100% - 40px);border-radius: 5px;"
                       :class="[inputIndex==index&&trainData.status==1?'activeCard':'']">
                    <div class="w-full mb-1 layout-left-center relative term ">
                      <div :contenteditable="trainData.status==1&&trainData.type==0" class="inputs "
                           @input="term($event,d)"
                           @focus="getFocus(index)"
                           :style="[trainData.status==2&&d.key!==d.answer.key&&trainData.type==0?'color:red;cursor: pointer':'']"
                           placeholder="用语" style="width: 100%">
                        {{trainData.type==1?d.key:trainData.status==2?d.key:''}}
                      </div>
                      <div class="keyErr" v-if="trainData.status==2&&d.key!==d.answer.key&&trainData.type==0">
                        {{d.answer.key!==''?d.answer.key:'未输入答案'}}
                      </div>
                      <!--                     <a-input style="width: 100%" placeholder="用语" v-model:value="d.answer.key" />-->
                    </div>
                    <div class="w-full layout-left-center relative meaning">
                      <div :contenteditable="trainData.status==1&&trainData.type==1" class="inputs"
                           @input="meaning($event,d)"
                           @focus="getFocus(index)"
                           :style="[trainData.status==2&&(d.value).trim()!==d.answer.value&&trainData.type==1?'color:red;cursor: pointer':'']"
                           placeholder="含义" style="width: 100%">
                        {{trainData.type==0?d.value:trainData.status==2?d.value:''}}
                      </div>
                      <div class="keyErr" v-if="trainData.status==2&&(d.value).trim()!==d.answer.value&&trainData.type==1">
                        {{d.answer.value!==''?d.answer.value:'未输入答案'}}
                      </div>
                      <!--                     <a-input style="width: 100%" placeholder="含义"  v-model:value="d.answer.value" />-->
                    </div>
                  </div>
                </div>
              </template>
            </div>
            <div style="width: 100%; height: 100%; overflow: auto" v-else>
              <template v-for="(d, index) in trainData.data" :key="index">
                <div class="p-1 card relative" :style="[trainData.type == 1 ? 'width:33%' : 'width:20%']" style="height: 120px;min-width: 260px;float: left;position: relative;transition: all 2s;display: flex; ">
                  <div class="w-full h-full layout-side cardbox" :class="[trainData.index == index && trainData.status == 1 ? 'active' : '']">
                    <div class="cardLeft">
                      <div class="text">{{ index + 1 }}</div>
                    </div>
                  </div>
                  <div class="p-1 absolute layout-center-v" v-if="trainData.trainType == 0" style="top: 28px; left: 15px; width: calc(100% - 40px); border-radius: 5px" :class="[inputIndex == index && trainData.status == 1 ? 'activeCard' : '',trainData.index == index && trainData.status == 1 ? 'acItem' : '']">
                    <div class="w-full mb-1 layout-left-center relative term">
                      <div :contenteditable="trainData.status == 1" class="inputs border-btb" @input="term($event, d)" @focus="getFocus(index)" :style="[trainData.status == 2 && d.key !== d.answer.key? 'color:red;cursor: pointer': '',trainData.index == index && trainData.status == 1? 'border-color:#5b5a57': 'border-color:#42596e']" placeholder="用语" style="width: 100%">
                        {{ trainData.status == 2 ? d.key : '' }}
                      </div>
                      <div class="keyErr" v-if="trainData.status == 2 && d.key !== d.answer.key">
                        {{ d.answer.key !== '' ? d.answer.key : '未输入答案' }}
                      </div>
                    </div>
                    <div class="w-full layout-left-center relative meaning">
                      <div :contenteditable="trainData.status == 1" class="inputs" @input="meaning($event, d)" @focus="getFocus(index)" :style="[ trainData.status == 2 && d.value.trim() !== d.answer.value? 'color:red;cursor: pointer': '']" placeholder="含义" style="width: 100%">
                        {{ trainData.status == 2 ? d.value : '' }}
                      </div>
                      <div class="keyErr" v-if="trainData.status == 2 && d.value.trim() !== d.answer.value">
                        {{ d.answer.value !== '' ? d.answer.value : '未输入答案' }}
                      </div>
                    </div>
                  </div>
                  <div class="p-1 absolute layout-center-v" v-else style="top: 28px; left: 15px; width: calc(100% - 40px); border-radius: 5px" :class="[inputIndex == index && trainData.status == 1 ? 'activeCard' : '']">
                    <div class="w-full mb-1 layout-left-center relative term">
                      <div :contenteditable="trainData.status == 1 && trainData.type == 0" class="inputs" @input="term($event, d)" @focus="getFocus(index)" :style="[ trainData.status == 2 && d.key !== d.answer.key && trainData.type == 0? 'color:red;cursor: pointer' : '']" placeholder="用语" style="width: 100%">
                        {{ trainData.type == 1 ? d.key : trainData.status == 2 ? d.key : '' }}
                      </div>
                      <div class="keyErr" v-if="trainData.status == 2 && d.key !== d.answer.key && trainData.type == 0 ">
                        {{ d.answer.key !== '' ? d.answer.key : '未输入答案' }}
                      </div>
                    </div>
                    <div class="w-full layout-left-center relative meaning">
                      <div :contenteditable="trainData.status == 1 && trainData.type == 1" class="inputs" @input="meaning($event, d)" @focus="getFocus(index)" :style="[trainData.status == 2 &&d.value.trim() !== d.answer.value &&trainData.type == 1? 'color:red;cursor: pointer': '']" placeholder="含义" style="width: 100%">
                        {{ trainData.type == 0 ? d.value : trainData.status == 2 ? d.value : '' }}
                      </div>
                      <div class="keyErr" v-if="trainData.status == 2 &&d.value.trim() !== d.answer.value &&trainData.type == 1 ">
                        {{ d.answer.value !== '' ? d.answer.value : '未输入答案' }}
                      </div>
                    </div>
                  </div>
                </div>
              </template>
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
                <img v-if="trainData.score>=80" :src="scoreSuperb" class="img">
                <img v-else-if="trainData.score>=60" :src="scoreSuffice" class="img">
                <img v-else :src="scoreFailed" class="img">
                <div :class="{score:true,superb: trainData.score>=80,suffice: trainData.score>=60&&trainData.score<80}">
                  {{trainData.score}}
                </div>
                <!--<img v-if="parseFloat(trainData.accuracy)>40" :src="tagscrapsuccess"
                     alt="">
                <img v-else-if="parseFloat(trainData.accuracy)>30" :src="tagscrapwarning"
                     alt="">
                <img v-else :src="tagscraperror" alt="">
                <div class="desc" style="color: #7b90af;">本次训练用时</div>
                <div class="time">{{ computationTime(trainData.duration) }}</div>-->
              </div>
              <div class="bottom">
                <div class="resGroup">
                  <img :src="resaccuracy" alt="">
                  <div class="cont">
                    <div class="desc" :style="{fontSize: (fs * 1 + 14) + 'px'}">正确率</div>
                    <div class="num" :style="{fontSize: (fs * 1 + 24) + 'px'}">{{ trainData.accuracy?trainData.accuracy:0 }}%</div>
                  </div>
                </div>
                <div class="resGroup">
                  <img :src="resTime" alt="">
                  <div class="cont">
                    <div class="desc" :style="{fontSize: (fs * 1 + 14) + 'px'}">训练用时</div>
                    <div class="num" :style="{fontSize: (fs * 1 + 24) + 'px'}">{{computationTime(trainData.duration)}}</div>
                  </div>
                </div>
              </div>
            </div>
            <div class="splitLine"></div>
            <div class="resRight w-full">
              <div class="resTextItem">
                <div class="desc" :style="{fontSize: (fs * 1 + 14) + 'px'}">报文总数</div>
                <div class="num" :style="{fontSize: (fs * 1 + 24) + 'px'}">{{ trainData.number }}个</div>
              </div>
              <div class="resTextItem">
                <div class="desc" :style="{fontSize: (fs * 1 + 14) + 'px'}">正确数量</div>
                <div class="num" :style="{fontSize: (fs * 1 + 24) + 'px'}">{{ trainData.passNumber }}个</div>
              </div>
              <div class="resTextItem">
                <div class="desc" :style="{fontSize: (fs * 1 + 14) + 'px'}">错误总数</div>
                <div class="num" :style="{fontSize: (fs * 1 + 24) + 'px'}">{{ trainData.errorNumber }}个</div>
              </div>
            </div>
          </div>
        </div>
        <div class="resClose">
          <div class="closeInfo">
            <div class="close" @click="showResultModal=false"></div>
          </div>
        </div>
      </div>
      <div class="achievement" v-else>
        <div class="cont">
          <div class="dataBox">
            <div class="resLeft w-full">
              <div class="top_all_box">
                <div class="tab_top_box">
                  <div class="text">{{ trainData.score }}</div>
                </div>
                <div class="center_box">
                  <div class="box">
                    <div class="text1">正确率</div>
                    <div>{{ trainData.accuracy ? trainData.accuracy : 0 }}%</div>
                  </div>
                  <div class="box">
                    <div>训练用时</div>
                    <div>{{ computationTime(trainData.duration) }}</div>
                  </div>
                </div>
              </div>
              <div class="bottom_box">
                <div class="tab_top">
                  <div class="tab">报文总数</div>
                  <div class="tab">正确数量</div>
                  <div class="tab">错误总数</div>
                </div>
                <div class="bottom_box">
                  <div class="tab">{{ trainData.number }}个</div>
                  <div class="tab">{{ trainData.passNumber }}个</div>
                  <div class="tab">{{ trainData.errorNumber }}个</div>
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
    name: 'DittoPostMilitaryTermTrain'
  }
</script>
<script setup>
  import CountDown from '../../../../../components/common/CountDown.vue'
  import TrainLeft from '../../../../../components/postJob/trainLeft/TrainLeft.vue'

  import play from '../../../../../assets/HJ/term/play-ico.png'
  import pause from '../../../../../assets/HJ/term/pause-ico.png'

  import scoreSuperb from '../../../../../assets/HJ/postTrain/score-superb.png'
  import scoreSuffice from '../../../../../assets/HJ/postTrain/score-suffice.png'
  import scoreFailed from '../../../../../assets/HJ/postTrain/score-failed.png'
  import detailexercise from '../../../../../assets/HJ/train/detail-exercise.png'
  import tagscrapsuccess from '../../../../../assets/HJ/train/tag-scrap-success.png'
  import tagscrapwarning from '../../../../../assets/HJ/train/tag-scrap-warning.png'
  import tagscraperror from '../../../../../assets/HJ/train/tag-scrap-error.png'
  import resaccuracy from '../../../../../assets/HJ/train/res-accuracy.png'
  import resTime from '../../../../../assets/HJ/train/res-time.png'

  import videoVHJ from '../../../../../assets/HJ/term/val-hide.gif'
  import videoPHJ from '../../../../../assets/HJ/term/val-hide.png'
  import videoVHJJ from '../../../../../assets/HJJ/term/val-hide.gif'
  import videoPHJJ from '../../../../../assets/HJJ/term/val-hide.png'
  import videoVLJ from '../../../../../assets/LJ/term/val-hide.gif'
  import videoPLJ from '../../../../../assets/LJ/term/val-hide.png'
  import videoVKJ from '../../../../../assets/KJ/term/val-hide.gif'
  import videoPKJ from '../../../../../assets/KJ/term/val-hide.png'

  import wordingTrain from './js/wordTrain'
  import { ref } from 'vue'
  const countDown = ref(null)
  let videoV,videoP
  const interfaceStyle = window.interfaceStyle
  if(interfaceStyle==='HJ'){
    videoV = videoVHJ
    videoP = videoPHJ
  }else if (interfaceStyle==='HJJ'){
    videoV = videoVHJJ
    videoP = videoPHJJ
  }else if (interfaceStyle==='KJ'){
    videoV = videoVKJ
    videoP = videoPKJ
  }else {
    videoV = videoVLJ
    videoP = videoPLJ
  }

  const fs = ref(JSON.parse(localStorage.getItem('fs')));
  const fileUrl = ref(window.fileUrl)
  const term = (e, d) => {
    d.answer.key = e.target.innerHTML
  }
  const isfocus = ref(true)
  const meaning = (e, d) => {
    d.answer.value = e.target.innerHTML
  }
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
  const {
    start,
    trainData,
    inputIndex,
    finish,
    disturbList,
    changeVolume,
    changeAudioPlay,
    showResultModal,
    gradeTypeList
  } = wordingTrain(countDown)
  const getFocus = index => {
    if (trainData.value.trainType == 1) {
      trainData.value.index = index
    }
    inputIndex.value = index
  }
  const beginExerciseInfo = () => {
    start()
  }
</script>

<style scoped>
  @import "../../css/addTrainStyle.less";
  @import "../../telegram/telexTrain/js/handkey.css";
  @import "./css/index.less";

</style>
