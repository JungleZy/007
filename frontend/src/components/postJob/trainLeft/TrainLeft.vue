<template>
  <div class="trainLeft">
    <div class="trainTime grouping">
      <div class="w-full grouping_halving_line"></div>
      <div class="w-full grouping_content">
       <slot name="top"></slot>
      </div>
    </div>
    <div class="basicExercise grouping">
      <div class="w-full grouping_halving_line"></div>
      <div class="w-full grouping_content overflow-hidden">
       <slot name="bottom"></slot>
        <div class="exerciseBtn" :class="[trainData.status==1?'endTrain':'']" v-if="trainData.status==0">
          <template v-if="interfaceStyle!=='HJ'">
            <div class="start " @click="startTest" >开始训练</div>
            <slot name="btn"></slot>
          </template>
          <div v-else class="start btn-animate" @click="startTest">
            <img :src="startExercise" alt="">
          </div>
        </div>
        <div class="exerciseBtn" :class="[trainData.status==1?'endTrain':'']" v-if="trainData.status==1">
          <template v-if="interfaceStyle!=='HJ'">
            <div class="start" @click="endTest">结束训练</div>
            <slot name="btn"></slot>
          </template>
          <div v-else class="start btn-animate btn-animate-orange" @click="endTest" >
            <img :src="endExercise" alt="">
          </div>
        </div>
        <div class="exerciseBtn" :class="[trainData.status==1?'endTrain':'']" v-if="trainData.status==3 || (type == 'score' && trainData.status==2)">
          <div class="start" v-if="trainData.status==3">查看成绩</div>
          <slot name="btn"></slot>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
  export default {
    name: "TrainLeft",
  }
</script>
<script setup >
  import {ref,defineEmits} from 'vue'
  import startExercise from '../../../assets/HJ/postTrain/start-exercise.png';
  import endExercise from '../../../assets/HJ/postTrain/end-exercise.png';
  const interfaceStyle = window.interfaceStyle
  const {trainData} = defineProps({
    trainData:{type:Object},
    type:{
      type:String,
      default: ''
    }})
  const emit = defineEmits(['on-click'])
  const startTest = ()=>{
    emit('startTest')
  }
  const endTest = ()=>{
    emit('endTest')
  }
</script>

<style lang="less">
 .HJ{
 .trainLeft .trainTime .grouping_content .desc{
   color: #5e80b2!important;
   font-size: 15px!important;
   padding-bottom: 16px!important;
   text-align: center!important;
 }
 }
</style>
<style scoped lang="less">
  .HJ{
    .trainLeft, .trainRight {
      width: 280px;
      flex-shrink: 0;
      height: 100%;
      /*box-shadow: 0px 0px 6px 2px rgb(255 255 255 / 20%) inset !important;*/
      /*background: url("../../../assets/HJ/telexTrain/statisticalBoxBG.png") repeat !important;*/
      border-radius: 13px 13px 0 0px;
    }
    .trainLeft .trainTime .grouping_content{
      height: 156px;
      background: url("../../../assets/HJ/train/train-time-bg.jpg") no-repeat center bottom;
      padding: 24px 8px;
    }


    .basicExercise {
      height: calc(100% - 164px);
    }

    .basicExercise .grouping_content {
      height: 100%;
    }

    .basicExercise .msg {
      padding: 8px 8px 8px 45px;
      border: 1px solid transparent;
      flex-shrink: 0;
      position: relative;
      font-size: 15px;
      line-height: 20px;
    }

    .basicExercise .msg:before {
      content: '';
      width: 33px;
      height: 33px;
      position: absolute;
      left: 6px;
      top: 1px;
    }

    .basicExercise .deployBtn {
      width: 70px;
      height: 24px;
      background: url("../../../assets/HJ/train/deploy-btn-bg.png") no-repeat center;
      color: #e2f2ff;
      font-size: 14px;
      text-align: center;
      line-height: 22px;
      margin-left: 6px;
      cursor: pointer;
    }

    .basicExercise .deployBtn:hover {
      color: #70c9ff;
      position: relative;
    }

    .basicExercise .deployBtn:hover:before {
      content: '';
      height: 24px;
      width: 70px;
      background: url("../../../assets/HJ/ico/ico-state-on-bg.png") no-repeat bottom center;
      position: absolute;
      left: 0;
      bottom: 0;
    }

    .basicExercise .hint {
      border-color: rgba(255, 255, 213, .4);
      color: #ffffd5;
      background: url("../../../assets/HJ/train/hint-bg.png") no-repeat center bottom;
      margin-top: 8px;
    }

    .basicExercise .hint:before {
      background: url("../../../assets/HJ/train/hint-ico.png") no-repeat center bottom;
    }

    .basicExercise .exerciseBtn {
      height: 62px;
      padding: 10px;
      background: rgba(24, 45, 86, .7);
    }
    .basicExercise .exerciseBtn .start{
      height: 100%;
      width: 100%;
      cursor: pointer;
      z-index: 9;
      position: relative;
    }
    .endTrain{
      /*background: url("../../../assets/HJ/postTrain/btnBg_end.png")!important;*/
      /*color: #e9deb2!important;*/
    }
  }
  .HJJ{
    .trainLeft, .trainRight {
      width: 280px;
      flex-shrink: 0;
      height: 100%;
      box-shadow: 0px 0px 6px 2px rgb(255 255 255 / 20%) inset !important;
      background: url("../../../assets/HJJ/telexTrain/statisticalBoxBG.png") repeat !important;
      border-radius: 13px 13px 0 0px;
    }
    .basicExercise {
      height: calc(100% - 194px);
    }

    .basicExercise .grouping_content {
      height: 100%;
    }

    .basicExercise .msg {
      padding: 8px 8px 8px 45px;
      border: 1px solid transparent;
      flex-shrink: 0;
      position: relative;
      font-size: 15px;
      line-height: 20px;
    }

    .basicExercise .msg:before {
      content: '';
      width: 33px;
      height: 33px;
      position: absolute;
      left: 6px;
      top: 1px;
    }

    .basicExercise .deployBtn {
      width: 70px;
      height: 24px;
      background: url("../../../assets/HJJ/train/deploy-btn-bg.png") no-repeat center;
      color: #e2f2ff;
      font-size: 14px;
      text-align: center;
      line-height: 22px;
      margin-left: 6px;
      cursor: pointer;
    }

    .basicExercise .deployBtn:hover {
      color: #70c9ff;
      position: relative;
    }

    .basicExercise .deployBtn:hover:before {
      content: '';
      height: 24px;
      width: 70px;
      background: url("../../../assets/HJJ/ico/ico-state-on-bg.png") no-repeat bottom center;
      position: absolute;
      left: 0;
      bottom: 0;
    }

    .basicExercise .hint {
      border-color: rgba(255, 255, 213, .4);
      color: #ffffd5;
      background: url("../../../assets/HJJ/train/hint-bg.png") no-repeat center bottom;
      margin-top: 8px;
    }

    .basicExercise .hint:before {
      background: url("../../../assets/HJJ/train/hint-ico.png") no-repeat center bottom;
    }

    .basicExercise .exerciseBtn {
      background: url("../../../assets/HJJ/postTrain/btnBg.png");
      padding: 10px;
      width:246px ;
      height: 40px;
      margin: 0 auto;
      text-align: center;
      color: #bfcde0;
      font-size: 18px;
      font-weight: bold;
      line-height: 19px;
    }
    .basicExercise .exerciseBtn .start{
      height: 100%;
      width: 100%;
      cursor: pointer;
      z-index: 9;
      position: relative;
    }
    .endTrain{
      background: url("../../../assets/HJJ/postTrain/btnBg_end.png")!important;
      color: #e9deb2!important;
    }
  }
  .LJ{
    .trainLeft, .trainRight {
      width: 280px;
      flex-shrink: 0;
      height: 100%;
      box-shadow: 0px 0px 6px 2px rgb(255 255 255 / 20%) inset !important;
      background: url("../../../assets/LJ/telexTrain/statisticalBoxBG.png") repeat !important;
      border-radius: 13px 13px 0 0px;
    }

    .basicExercise {
      height: calc(100% - 187px);
      position: relative;
    }

    .basicExercise .grouping_content {
      height: 100%;
    }

    .basicExercise .msg {
      padding: 8px 8px 8px 45px;
      border: 1px solid transparent;
      flex-shrink: 0;
      position: relative;
      font-size: 15px;
      line-height: 20px;
    }

    .basicExercise .msg:before {
      content: '';
      width: 33px;
      height: 33px;
      position: absolute;
      left: 6px;
      top: 1px;
    }

    .basicExercise .deployBtn {
      width: 70px;
      height: 24px;
      background: url("../../../assets/LJ/train/deploy-btn-bg.png") no-repeat center;
      color: #ffffff;
      font-size: 14px;
      text-align: center;
      line-height: 22px;
      margin-left: 6px;
      cursor: pointer;
    }

    .basicExercise .deployBtn:hover {
      color: #70c9ff;
      position: relative;
    }

    .basicExercise .deployBtn:hover:before {
      content: '';
      height: 24px;
      width: 70px;
      background: url("../../../assets/LJ/ico/ico-state-on-bg.png") no-repeat bottom center;
      position: absolute;
      left: 0;
      bottom: 0;
    }

    .basicExercise .hint {
      border-color: rgba(255, 255, 213, .4);
      color: #ffffd5;
      background: url("../../../assets/LJ/train/hint-bg.png") no-repeat center bottom;
      margin-top: 8px;
    }

    .basicExercise .hint:before {
      background: url("../../../assets/LJ/train/hint-ico.png") no-repeat center bottom;
    }

    .basicExercise .exerciseBtn {
      position: absolute;
      bottom: 12px;
      left: 50%;
      transform: translateX(-50%);
      background: url("../../../assets/LJ/postTrain/btnBg.png");
      padding: 10px;
      width: 246px;
      height: 40px;
      margin: 0 auto;
      text-align: center;
      color: #fff;
      font-size: 18px;
      font-weight: bold;
      line-height: 19px;
      text-shadow: 1px 1px 1px #000 !important;

    }

    .basicExercise .exerciseBtn .start {
      height: 100%;
      width: 100%;
      cursor: pointer;
      z-index: 9;
      position: relative;
    }

    .endTrain {
      background: url("../../../assets/LJ/postTrain/btnBg_end.png") !important;
      color: #fff !important;
    }
  }
  .KJ{
    .trainLeft, .trainRight {
      width: 280px;
      flex-shrink: 0;
      height: 100%;
      box-shadow: 0px 0px 6px 2px rgb(255 255 255 / 20%) inset !important;
      background: url("../../../assets/KJ/telexTrain/statisticalBoxBG.png") repeat !important;
      border-radius: 13px 13px 0 0px;
    }

    .basicExercise {
      height: calc(100% - 187px);
      position: relative;
    }

    .basicExercise .grouping_content {
      height: 100%;
    }

    .basicExercise .msg {
      padding: 8px 8px 8px 45px;
      border: 1px solid transparent;
      flex-shrink: 0;
      position: relative;
      font-size: 15px;
      line-height: 20px;
    }

    .basicExercise .msg:before {
      content: '';
      width: 33px;
      height: 33px;
      position: absolute;
      left: 6px;
      top: 1px;
    }

    .basicExercise .deployBtn {
      width: 70px;
      height: 24px;
      background: url("../../../assets/KJ/train/deploy-btn-bg.png") no-repeat center;
      color: #ffffff;
      font-size: 14px;
      text-align: center;
      line-height: 22px;
      margin-left: 6px;
      cursor: pointer;
    }

    .basicExercise .deployBtn:hover {
      color: #70c9ff;
      position: relative;
    }

    .basicExercise .deployBtn:hover:before {
      content: '';
      height: 24px;
      width: 70px;
      background: url("../../../assets/KJ/ico/ico-state-on-bg.png") no-repeat bottom center;
      position: absolute;
      left: 0;
      bottom: 0;
    }

    .basicExercise .hint {
      border-color: rgba(255, 255, 213, .4);
      color: #ffffd5;
      background: url("../../../assets/KJ/train/hint-bg.png") no-repeat center bottom;
      margin-top: 8px;
    }

    .basicExercise .hint:before {
      background: url("../../../assets/KJ/train/hint-ico.png") no-repeat center bottom;
    }

    .basicExercise .exerciseBtn {
      position: absolute;
      bottom: 12px;
      left: 50%;
      transform: translateX(-50%);
      background: url("../../../assets/KJ/postTrain/btnBg.png");
      padding: 10px;
      width: 246px;
      height: 40px;
      margin: 0 auto;
      text-align: center;
      color: #fff;
      font-size: 18px;
      font-weight: bold;
      line-height: 19px;
      text-shadow: 1px 1px 1px #000 !important;

    }

    .basicExercise .exerciseBtn .start {
      height: 100%;
      width: 100%;
      cursor: pointer;
      z-index: 9;
      position: relative;
    }

    .endTrain {
      background: url("../../../assets/KJ/postTrain/btnBg_end.png") !important;
      color: #fff !important;
    }
  }
</style>