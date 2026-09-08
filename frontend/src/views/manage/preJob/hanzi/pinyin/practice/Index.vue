<template>
  <div class="telexTrainBox h-full w-full overflow-auto layout-side-n relative content-mask-bg ">
    <div class=" "  >
      <div class="statisticsBox statisticalBox">
        <div class="lineBox">
          <div class="box">
            <img :src="successp" alt="">
            <span>正确</span></div>
          <div class="box">{{ trainData.correctNum}}个</div>
        </div>
        <div class="lineBox">
          <div class="box">   <img :src="errorp" alt="">
            <span>错误</span></div>
          <div class="box">{{ trainData.errorNum}}个</div>
        </div>
        <div class="lineBox">
          <div class="box"> <img :src="successp" alt="">
            <span>正确率</span></div>
          <div class="box">{{ trainData.accuracy}}%</div>
        </div>
        <div class="lineBox">
          <div class="box">   <img :src="speed" alt="">
            <span>速度</span></div>
          <div class="box"> {{ trainData.speed}}组/分</div>
        </div>
        <div class="linebtns">
          <div class="exerciseBtn btn">
            <div class="layout-center" @click="goback">
              <IconFont type="icon-rollback" style="margin-right: 5px"></IconFont> 退出
            </div>
          </div>
        </div>
      </div>
    </div>
    <div class=""  style="width: calc(100% - 290px - 278px);height: 100%;overflow: hidden;margin-left: 10px;">
      <div class="w-full h-full layout-center" v-if="message==null">
        <a-spin size="large" tip="正在努力加载..."/>
      </div>
      <div class="w-full layout-center" v-if="isfocus" style="padding: 40px 0;height: 330px">
        <div style="width: 50%;display: flex;height: 100%" class="focus" >
          <div class="focusBox" style="height: 100%;display: flex;flex-direction: column;justify-content: center;align-items: center" >
            <div v-if=" trainData.status!=2" class="imgBox" style="font-size: 40px;font-weight: bold;padding-bottom: 30px">{{activeMessage.font}}</div>
            <div v-if=" trainData.status!=2" style="text-align: center;height: 60px;line-height: 60px;" class="layout-center" >
                  <div v-for="(zm,i) of activeMessage.pys" style="color: rgba(255,255,255,0.5)" :style="[zm.trueOrfalse==false?'color:red':'',zm.trueOrfalse==true?'color:#fff':'']" >
                    <div style="font-size: 40px;font-weight: bold;border-left: 1px solid transparent;border-right: 1px solid transparent"
                         :class="[inputIndex==i&&isfocus?'activeL':'',
                  inputIndex==(activeMessage.pys.length)&&inputIndex==i+1?'activeR':'']"
                    > {{zm.py}}</div>
                  </div>
            </div>
            <div v-if=" trainData.status==2" style="font-size: 32px">练习已结束</div>
            </div>
        </div>
      </div>
      <div class="w-full absolute "  style="left: 0px; "  :style="[isfocus?'height: calc(100% - 330px)':'height:100%']">
        <div class="w-full h-full layout-left-top scorebox"  @scroll="scoreRoll" style=" overflow: auto;;max-height: max-content">
          <div  style="max-height: max-content;width: 100% ;display: flex;flex-wrap: wrap;padding: 0 4px;" :style="[trainData.type>1?'justify-content: space-between':'']">
            <div v-if="trainData.type!=4" class="cardBox" style="display: flex;height: max-content;" v-for=" (v,index) of message" :style="[trainData.type>1?'min-width:16%':'width: 10%']" >
              <div class="messageBox"
                   v-if="index>=small&&index<big"
                   :class="[!v.trueOrfalse&&v.trueOrfalse!=null&&(v.isFocus)?'erroyMessageBox':'',v.trueOrfalse&&(v.isFocus)?'successMessageBox':'',activeIndex==index&&trainData.status==1?'activeBox':'']">
                <div class="imgBox" style="">{{v.font}}</div>
                <div class="layout-center" style="text-align: center;" >
                  <div class="layout-left-top" v-for="(zm,i) of v.pys" style="color: rgba(255,255,255,0.5)" :style="[zm.trueOrfalse==false?'color:red':'',zm.trueOrfalse==true?'color:#fff':'']" >
                  <div style="font-size: 24px;border-left: 1px solid transparent;border-right: 1px solid transparent;"
                  :class="[activeIndex==index&&inputIndex==i&&!isfocus&&trainData.status==1?'activeL':'',
                  activeIndex==index&&inputIndex==(v.pys.length)&&!isfocus&&trainData.status==1&&inputIndex==i+1?'activeR':'']"
                  > {{zm.py}}</div>
                  </div>
                </div>
              </div>
            </div>
            <div v-else class="h-full w-full ">
              <WZTrain :message="message" :trainData="trainData" @statistics="statistics"></WZTrain>
            </div>
          </div>
        </div>
      </div>

    </div>
    <div style="width: 278px">
      <CutDown :nowTime="nowTime"></CutDown>
    </div>
    <div class="achievementMasking" v-show="showResultModal && trainData.status == 2">
      <div class="achievement">
        <div class="resTitle">
          <img v-if="parseFloat(trainData.accuracy)>80" :src="restext1">
          <img v-else-if="parseFloat(trainData.accuracy)>60" :src="restext2">
          <img v-else :src="restext3">
        </div>
        <div class="cont" style="display: flex;align-items: center;height: 400px">
          <div class="dataBox">
            <div class="resLeft w-full">
              <div class="top">
                <img v-if="parseFloat(trainData.accuracy)>40" :src="tagscrapsuccess"
                     alt="">
                <img v-else-if="parseFloat(trainData.accuracy)>30" :src="tagscrapwarning"
                     alt="">
                <img v-else :src="tagscraperror" alt="">
                <div class="desc" style="color: #7b90af;">本次训练用时</div>
                <div class="time">{{ computationTime(trainData.duration) }}</div>
              </div>
              <div class="bottom">
                <div class="resGroup">
                  <img :src="resaccuracy" alt="">
                  <div class="cont">
                    <div class="desc">正确率</div>
                    <div class="num">{{ trainData.accuracy }}%</div>
                  </div>
                </div>
                <div class="resGroup">
                  <img :src="resspeed" alt="">
                  <div class="cont">
                    <div class="desc">速度</div>
                    <div class="num">{{ trainData.speed }}<span style="font-size: 20px;">组/分</span></div>
                  </div>
                </div>
              </div>
            </div>
            <div class="splitLine"></div>
            <div class="resRight w-full">
              <div class="resTextItem">
                <div class="desc">正确组数</div>
                <div class="num">{{ trainData.correctNum }}组</div>
              </div>
              <div class="resTextItem">
                <div class="desc">错误组数</div>
                <div class="num">{{ trainData.errorNum }}组</div>
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
    </div>
  </div>
</template>

<script>
  export default {
    name: "Practive",
  }
</script>

<script setup>
  import parctice from "./js/parctice.js";
  import WZTrain from "./compoents/WZTrain.vue";
  import CountDown from '../../../../../../components/common/CountDown.vue';
  import Number from '../../../../../../components/number/Number.vue';
  import labnum from '../../../../../../assets/HJ/train/lab-num.png'
  import laberr from '../../../../../../assets/HJ/train/lab-err.png'
  import labaccuracy from '../../../../../../assets/HJ/train/lab-accuracy.png'
  import labspeed from '../../../../../../assets/HJ/train/lab-speed.png'
  import endexercise from '../../../../../../assets/HJ/train/end-exercise.png'
  import detailexercise from '../../../../../../assets/HJ/train/detail-exercise.png'
  import restext1 from '../../../../../../assets/HJ/train/res-text-1.png'
  import restext2 from '../../../../../../assets/HJ/train/res-text-2.png'
  import restext3 from '../../../../../../assets/HJ/train/res-text-3.png'
  import tagscrapsuccess from '../../../../../../assets/HJ/train/tag-scrap-success.png'
  import tagscrapwarning from '../../../../../../assets/HJ/train/tag-scrap-warning.png'
  import tagscraperror from '../../../../../../assets/HJ/train/tag-scrap-error.png'
  import resaccuracy from '../../../../../../assets/HJ/train/res-accuracy.png'
  import resspeed from '../../../../../../assets/HJ/train/res-speed.png'

  import countp from '../../../../../../assets/HJJ/telexTrain/count.png'
  import errorp from '../../../../../../assets/HJJ/telexTrain/error.png'
  import successp from '../../../../../../assets/HJJ/telexTrain/success.png'
  import speed from '../../../../../../assets/HJJ/telexTrain/speed.png'
  import CutDown from "../../../../../../components/cutDown/CutDown.vue";
  import {ref} from "vue";
  import {createFromIconfontCN} from "@ant-design/icons-vue";
  const fileUrl = ref(window.fileUrl+'/006/code/');
  const countDown = ref(null)
  const showResultModal = ref(true)
  const IconFont = createFromIconfontCN({
    scriptUrl: window.iconUrl,
  });
  const trainDeploy = ref(0);
  //格式化时间
  const computationTime = (total)=> {
    let hour;
    let min;
    let sec;
    let day;
    let h;
    let m;
    let s;
    hour = Math.floor(total / 60 / 60 % 24);
    min = Math.floor(total / 60 % 60);
    sec = Math.floor(total  % 60);
    day = Math.floor(total / 60 / 60 / 24);
    // 计算总小时数
    hour = hour + day * 24;
    if (hour < 10 && hour >= 0) {
      h = "0" + hour
    } else {
      h = hour.toString()
    }
    if (min < 10 && min >= 0) {
      m = "0" + min
    } else {
      m = min
    }
    if (sec < 10 && sec >= 0) {
      s = "0" + sec
    } else {
      s = sec
    }
    return h+" : "+m+" : "+s
  }
  const codeTypeArr = ref([
    {type: 'gradient', name: '渐变'},
    {type: 'metal', name: '金属'},
    {type: 'chapped', name: '皲裂'},
    {type: 'white', name: '纯白'}
  ]);

  const codeType = ref('gradient');
  const {
    message,
    trainData,
    correct,
    activeMessage,
    isfocus,
    activeIndex,
    inputIndex,
      big,
      small,
    nowTime,
    computationTime2,
    inputFocus,
    scoreRoll,
    selectCard,
    keyCodeDown,
    keyCodeDown2,
    changeSwitch,
    changeMessage,
    changeFocus,
    statistics,
    saveTest,
    selectMessage,
    getFocus,
    goback,
  } = parctice()
</script>

<style scoped lang="less">

  @import "./css/index";
  /*@import '../../../telegram/train/js/handkey.css';*/
</style>