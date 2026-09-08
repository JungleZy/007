<template>
  <div class="trainTime">
    <img :src="trainTime" class="ico" />
    <div class="timeNum"><number :value="nowTime.h1 ? nowTime.h1 : nowTime['0']"></number></div>
    <div class="timeNum"><number :value="nowTime.h2 ? nowTime.h2 : nowTime['1']"></number></div>
    <div class="timeNum"><number :value="nowTime.m1 ? nowTime.m1 : nowTime['2']"></number></div>
    <div class="timeNum"><number :value="nowTime.m2 ? nowTime.m2 : nowTime['3']"></number></div>
    <div class="timeNum"><number :value="nowTime.s1 ? nowTime.s1 : nowTime['4']"></number></div>
    <div class="timeNum"><number :value="nowTime.s2 ? nowTime.s2 : nowTime['5']"></number></div>
  </div>
</template>

<script>
import { ref, toRefs, watch } from 'vue'
import trainTimeHJ from '../../assets/HJ/receive/trainTime.png'
import trainTimeHJJ from '../../assets/HJJ/receive/trainTime.png'
import trainTimeLJ from '../../assets/LJ/receive/trainTime.png'
import trainTimeKJ from '../../assets/KJ/receive/trainTime.png'
export default {
  name: 'CutDown',
  props: {
    nowTime: Object
  },
  setup(props) {
    const { nowTime } = toRefs(props)
    const trainTime = ref('')
    const interfaceStyle = window.interfaceStyle
    if(interfaceStyle==="HJJ"){
      trainTime.value=trainTimeHJJ
    }else if (interfaceStyle==="HJ"){
      trainTime.value=trainTimeHJ
    }else if(interfaceStyle==="KJ"){
      trainTime.value = trainTimeKJ
    }
    else{
      trainTime.value=trainTimeLJ
    }
    return { trainTime, nowTime }
  }
}
</script>
<!--<script setup>-->
<!--  import trainTime from '../../assets/HJ/receive/trainTime.png'-->
<!--  import {ref,toRefs} from 'vue'-->
<!--  const props = defineProps({nowTime:{type:Object}})-->
<!--  const nowTime = ref(props.nowTime)-->
<!--</script>-->

<style scoped lang="less">

  .trainTime {
    position: absolute;
    right: 0px;
    top: 0px;
  }

  .HJ{
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
  .timeNum:nth-child(2){right: 196px;}
  .timeNum:nth-child(3){right: 166px}
  .timeNum:nth-child(4){right: 116px}
  .timeNum:nth-child(5){right: 86px}
  .timeNum:nth-child(6){right: 36px}
  .timeNum:nth-child(7){right: 6px}
  }
  .HJJ{
    .trainTime .timeNum {
      width: 29px;
      height: 36px;
      display: flex;
      align-items: center;
      justify-content: center;
      position: absolute;
      top: 22px;
    }
    .trainTime .timeNum .num {
      height: 40px;
      font-size: 32px;
      line-height: 40px;
      overflow: hidden;
      display: flex;
      text-align: center;
      color: #151d26;
      font-weight: bold;
      text-shadow: 0px 1px 1px rgba(255, 255, 255, 0.5);
    }
    .timeNum:nth-child(2){right: 249px;}
    .timeNum:nth-child(3){right: 212px}
    .timeNum:nth-child(4){right: 158px}
    .timeNum:nth-child(5){right: 122px}
    .timeNum:nth-child(6){right: 69px}
    .timeNum:nth-child(7){right: 30px}
  }
  .LJ,.KJ{
    .trainTime .timeNum {
      width: 29px;
      height: 36px;
      display: flex;
      align-items: center;
      justify-content: center;
      position: absolute;
      top: 25px;
      font-family: "Helvetica Neue", Helvetica, Arial, "PingFang SC", "Hiragino Sans GB", "Heiti SC", "Microsoft YaHei", "WenQuanYi Micro Hei", sans-serif;
    }
    .trainTime .timeNum .num {
      height: 40px;
      font-size: 32px;
      line-height: 40px;
      overflow: hidden;
      display: flex;
      text-align: center;
      color: white;
      font-weight: bold;
      /*text-shadow: 0px 1px 1px rgba(255, 255, 255, 0.5);*/
    }
    .timeNum:nth-child(2){right: 254px;}
    .timeNum:nth-child(3){right: 217px}
    .timeNum:nth-child(4){right: 158px}
    .timeNum:nth-child(5){right: 122px}
    .timeNum:nth-child(6){right: 64px}
    .timeNum:nth-child(7){right: 25px}
  }


</style>
