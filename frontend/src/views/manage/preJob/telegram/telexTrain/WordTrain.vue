<template>
  <div style="height: 100%;width: 100%;background: rgba(24, 45, 86, 0.7) !important;z-index: -2;display: flex;flex-direction: column;justify-content: space-between;padding-bottom: 10px;">
    <div class="topBox layout-center relative">

      <div class="statisticsBox">
        <div class="statistics">
          <img :src="clockpng" alt="">
          <span>总时长 : {{computationTime(telexPat.duration)}}</span>
        </div>
        <div class="statistics" v-if="tabType!=3">
          <img :src="countpng" alt="">
          <span>总拍发数 : {{telexPat.count}}次</span>
        </div>
        <div class="statistics" v-if="tabType!=3">
          <img :src="errorpng" alt="">
          <span>总错误 : {{telexPat.mistake}}次</span>
        </div>
        <div class="statistics" v-if="tabType!=3">
          <img :src="successpng" alt="">
          <span>本次正确 : {{telexPat.theSuccessCount}}次</span>
        </div>
        <div class="statistics" v-if="tabType!=3">
          <img :src="errorpng" alt="">
          <span>本次错误 : {{telexPat.themistake}}次</span>
        </div>
        <div class="layout-left-top">
          <div @click="clearTrainHistory"
               class="layout-center practiseBtn"
               style="margin-left: 10px;">
            <IconFont type="icon-clear" style="margin-right: 5px"></IconFont> 清空
          </div>
          <div
            @click="goBack"
            class="layout-center practiseBtn">
            <IconFont type="icon-rollback" style="margin-right: 5px"></IconFont> 退出
          </div>
        </div>
      </div>
      <div class="trainTime">
        <div class="timeNum" style="right: 196px;"><number :value="nowTime.h1"></number></div>
        <div class="timeNum" style="right: 166px;"><number :value="nowTime.h2"></number></div>
        <div class="timeNum" style="right: 116px;"><number :value="nowTime.m1"></number></div>
        <div class="timeNum" style="right: 86px;"><number :value="nowTime.m2"></number></div>
        <div class="timeNum" style="right: 36px;"><number :value="nowTime.s1"></number></div>
        <div class="timeNum" style="right: 6px;"><number :value="nowTime.s2"></number></div>
      </div>
      <div class="tabs layout-left-center">
        <div class="tab " style="text-align: center;line-height:50px " @click="slelectTab(index)"
             :class="[tabType==index?'activeTab':'']" v-for="(v,index) of tab"
             :style="{fontSize: (fs * 2 + 18) + 'px'}">{{v.text}}练习</div>
      </div>
    </div>
    <div class="letterbox" v-if="tabType!=3" >
      <div class="letter" :class="[letterIndex==index?'letterActive':'']" v-for="(v,index) of letter">

        <img v-if="tabType!=2" style="height: 90px;opacity: 0.3" :src="fileUrl+(v.text2?v.text2:v.text)+'.png'" alt="">
        <div style="font-size:40px;font-weight: bold" :class="[letterIndex==index?'specialKeyActive':'specialKey']" v-else>
          {{v.text2?v.text2:v.text}}
        </div>
      </div>
    </div>
    <Keyboard v-if="tabType!=3" ref="Keyboard" :targetKey="targetKey" :keyBoardText = 'keyBoardText' :telexPat="telexPat" @keyboardClick="keyboardClick"></Keyboard>
    <MistakeTrain v-if="tabType==3"></MistakeTrain>
  </div>
</template>

<script>
import Keyboard from '../../../../../components/preJob/telexTrain/Keyboard.vue'
import MistakeTrain from '../../../../../components/preJob/mistake/MistakeTrain.vue'
import { numberKey, letterKey, specialKey } from '../../../../../components/preJob/telexTrain/js/enum'
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { saveTelexPat, findTelexPatById, deleteTexPatByToken } from '../../../../../common/api/TelegramApi'
import { createFromIconfontCN } from '@ant-design/icons-vue'
import { useRouter } from 'vue-router'
import clockp from '../../../../../assets/HJ/telexTrain/clock.png'
import countp from '../../../../../assets/HJ/telexTrain/count.png'
import errorp from '../../../../../assets/HJ/telexTrain/error.png'
import successp from '../../../../../assets/HJ/telexTrain/success.png'
import CutDown from '../../../../../components/cutDown/CutDown.vue'
const IconFont = createFromIconfontCN({
  scriptUrl: window.iconUrl
})
export default {
  name: 'WordTrain',
  components: {
    Keyboard,
    MistakeTrain,
    IconFont
  },

  setup() {
    let clockpng = clockp
    let errorpng = errorp
    let countpng = countp
    let successpng = successp
    const fileUrl = ref(window.fileUrl + '/006/code/big/gradient/')
    const tab = ref([{ text: '数码' }, { text: '字码' }, { text: '特殊键' }, { text: '改错' }])
    const letter = ref([])
    const tabType = ref(0)
    const letterIndex = ref(0)
    const targetKey = ref(0)
    const keyBoardText = ref('')
    const Keyboard = ref(null)
    const router = useRouter()
    const telexPat = ref({
      count: 0,
      mistake: 0,
      type: tabType.value,
      time: 0,
      duration: 0,
      themistake: 0,
      theSuccessCount: 0
    })
    const timer = ref(null)
    const nowTime = ref({})
    onMounted(() => {
      assembliesCode()
      initTelexPat()
    })
    const slelectTab = index => {
      commitTelexPat()
      tabType.value = index
      computationTime2(0)
      letterIndex.value = 0
      telexPat.value.themistake = 0
      telexPat.value.theSuccessCount = 0
      if (index != 3) {
        assembliesCode()
      }
      initTelexPat()
    }
    const rote = ref(0)
    //组装目标数码
    const assembliesCode = () => {
      letter.value = []
      rote.value = 180 + rote.value
      for (let i=0;i<5;i++) {
        if (tabType.value === 0) {
          const mat = Math.floor(Math.random() * 10)
          letter.value.push(numberKey[mat])
        } else if (tabType.value === 1) {
          const mat = Math.floor(Math.random() * 26)
          letter.value.push(letterKey[mat])
        } else {
          const mat = Math.floor(Math.random() * 5)
          letter.value.push(specialKey[mat])
        }
      }
      initkeyBoardText()
      anime({
        targets: ['.letter'],
        duration: 1000,
        keyframes: [
          { rotateY: 90, duration: 250 },
          { rotateY: 360, duration: 250 },
          { rotateY: 0, duration: 0 }
        ],
        delay: anime.stagger(100)
      })
    }
    const keyboardClick = v => {
      if (v.keyCode == letter.value[letterIndex.value].keyCode) {
        letterIndex.value = letterIndex.value + 1
        if (letterIndex.value > 4) {
          letterIndex.value = 0
          assembliesCode()
        }
        initkeyBoardText()
      }
    }
    //改变keyBoardText的值
    const initkeyBoardText = () => {
      targetKey.value = letter.value[letterIndex.value].keyCode
      const target = letter.value[letterIndex.value]
      let finger = ''
      if (tabType.value != 2) {
        switch (target.finger.substring(0, 2)) {
          case 'l1':
            finger = '左手小指'
            break
          case 'l2':
            finger = '左手无名指'
            break
          case 'l3':
            finger = '左手中指'
            break
          case 'l4':
            finger = '左手食指'
            break
          case 'r1':
            finger = '右手小指'
            break
          case 'r2':
            finger = '右手无名指'
            break
          case 'r3':
            finger = '右手中指'
            break
          case 'r4':
            finger = '右手食指'
            break
        }
      }
      keyBoardText.value = `${tabType.value == 0 ? '数字' : '字母'}键${target.text2 ? target.text2 : target.text}，请用${finger}控制！`
      if (tabType.value === 2) {
        keyBoardText.value = target.explain
      }
    }
    //提交拍发数
    const commitTelexPat = () => {
      telexPat.value.type = tabType.value
      saveTelexPat(telexPat.value)
    }
    //清空拍发记录
    const clearTrainHistory = () => {
      deleteTexPatByToken({ type: tabType.value }).then(res => {
        telexPat.value.duration = 0
        initTelexPat()
      })
    }
    //初始化拍发数
    const initTelexPat = () => {
      clearInterval(timer.value)
      telexPat.value.time = 0
      findTelexPatById({ type: tabType.value }).then(res => {
        if (res.data) {
          telexPat.value.count = res.data.count
          telexPat.value.mistake = res.data.mistake
          telexPat.value.duration = res.data.duration != 'null' ? res.data.duration : 0
        } else {
          telexPat.value.count = 0
          telexPat.value.mistake = 0
          telexPat.value.theSuccessCount = 0
          telexPat.value.themistake = 0
        }
      })
      timer.value = setInterval(() => {
        telexPat.value.time++
        computationTime2(telexPat.value.time)
        telexPat.value.duration = Number(telexPat.value.duration) + 1
      }, 1000)
    }
    const goBack = () => {
      router.go(-1)
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
    //格式化时间
    const computationTime2 = total => {
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
        m = min.toString()
      }
      if (sec < 10 && sec >= 0) {
        s = '0' + sec
      } else {
        s = sec.toString()
      }
      nowTime.value.h1 = h.substring(0, 1) * 1
      nowTime.value.h2 = h.substring(1, 2) * 1
      nowTime.value.m1 = m.substring(0, 1) * 1
      nowTime.value.m2 = m.substring(1, 2) * 1
      nowTime.value.s1 = s.substring(0, 1) * 1
      nowTime.value.s2 = s.substring(1, 2) * 1
    }
    onBeforeUnmount(() => {
      commitTelexPat()
      clearInterval(timer.value)
    })
    const fs = ref(JSON.parse(localStorage.getItem('fs')));
    return {
      tab,
      letter,
      tabType,
      letterIndex,
      slelectTab,
      keyboardClick,
      nowTime,
      clockpng,
      errorpng,
      countpng,
      successpng,
      targetKey,
      fileUrl,
      keyBoardText,
      Keyboard,
      telexPat,
      fs,
      clearTrainHistory,
      computationTime,
      goBack
    }
  }
}
</script>

<style scoped>
  @keyframes letterA {
    0%{
      transform: rotateY(0deg);
    }
    50%{
      transform: rotateY(180deg);
    }
    100%{
      transform: rotateY(360deg);
    }
  }

  .trainTime {
    height: 54px;
    width: 278px;
    background: url("../../../../../assets/HJ/receive/trainTime.gif") no-repeat center;
    position: absolute;
    right: 0;
    top: 0;
    z-index: 9;
  }
  .cool .trainTime {
    background: url("../../../../../assets/HJ/receive/trainTime.png") no-repeat center;
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
  .topBox{
    padding-top:20px;
  }
  .statisticsBox{
    /*display: flex;*/
    /*align-items: flex-end;*/
    position: absolute;
    top: 10px;
    left: 30px;
    padding-right: 10px;
  }
  .statistics{
    background:url("../../../../../assets/HJ/telexTrain/statistics.png") no-repeat ;
    display: flex;
    align-items: flex-end;
    padding-left: 15px;
    padding-bottom: 10px;
    background-size: 100% 100%;
    height: 38px;
    margin-bottom: 10px;
  }
  .statistics img {
    position: relative;
    top: 3px;
  }
  .statistics span{
    line-height: 12px;
    padding-left: 10px;
    color: #adcde8;
  }
  .letterbox{
    width: 700px;
    height: 220px;
    padding: 40px 0;
    margin: 0px auto;
    display: flex;
    /*transition: all .5s;*/
  }
  .letter{
    min-width: 130px;
    height: 140px;
    background-image: url("../../../../../assets/HJ/telexTrain/letter-bg.png");
    font-size: 100px;
    font-weight: bold;
    display: flex;
    align-items: center;
    justify-content: center;
    margin: 5px;
    transition: all ;
    animation: letterA 1s;
  }
  .letterActive{
    background-image: url("../../../../../assets/HJ/telexTrain/letter-bg-active.png");
  }
  .specialKeyActive{
    background-image: linear-gradient(0deg,#6690e8 30%,#ffffff 50%);
    background-clip: text;
    -webkit-text-fill-color: transparent;
  }
  .specialKey{
    background-image: linear-gradient(0deg,#334d83 30%,#5d6477 50%);
    background-clip: text;
    -webkit-text-fill-color: transparent;
  }
  .letterActive img{
    opacity: 1!important;
  }
  .tab{
    height: 54px;
    width: 186px;
    cursor: pointer;
    background-image:  url("../../../../../assets/HJ/telexTrain/tab.png");
    margin: 0 5px;
    font-weight: bold;
    font-size: 18px;
    transition: all 1s ;
  }
  .activeTab{
    background-image:  url("../../../../../assets/HJ/telexTrain/tabActive.png");
    color: #814200;
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
    box-shadow: inset 0 -10px 10px -10px rgba(12, 114, 226, .8);
  }

  .practiseBtn + .practiseBtn {
    margin-left: 10px;
  }

  .practiseBtn:hover {
    box-shadow: inset 0 10px 10px -10px rgba(12, 114, 226, .8);
  }
  @media (max-width: 1740px) {
    .letterbox {
      transform: scale(.92);
    }
    .container {
      height: 452px;
      background-size: auto 100%, auto 100%;
    }
  }
  @media (max-width: 1600px) {
    .topBox .tabs {
      max-width: 760px;
      justify-content: center;
    }
    .letterbox {
      transform: scale(.84);
    }
    .container {
      height: 428px;
      background-size: auto 100%, auto 100%;
    }
  }
  @media (max-width: 1450px) {
    .letterbox {
      transform: scale(.76);
    }
    .container {
      height: 404px;
      background-size: auto 100%, auto 100%;
    }
  }
  @media (max-width: 1300px) {
    .topBox .tabs {
      max-width: 400px;
      justify-content: center;
    }
    .letterbox {
      transform: scale(.68);
    }
    .container {
      height: 380px;
      background-size: auto 100%, auto 100%;
    }
  }
  @media (max-width: 1120px) {
    .letterbox {
      transform: scale(.6);
    }
    .container {
      height: 356px;
      background-size: auto 100%, auto 100%;
    }
  }
</style>
