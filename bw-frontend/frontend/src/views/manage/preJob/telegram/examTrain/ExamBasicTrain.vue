<template>
  <!--  电子键拍发-->
  <div class="w-full h-full overflow-hidden content-mask-bg layout-side">
    <div class="w-full h-full relative boxs">
      <div class="statisticsBox">
        <div class="statisticalBox">
          <div class="lineBox">
            <div class="box"><img :src="clockLab" alt="" /> <span>总时长</span></div>
            <div class="box">{{ partTimeFormatInfo(patTotal.totalTime, 'number') }}</div>
          </div>
          <div class="lineBox">
            <div class="box">
              <img :src="countLab" alt="" />
              <span>总拍发数</span>
            </div>
            <div class="box">{{ patTotal.totalCount }}次</div>
          </div>
          <div class="lineBox">
            <div class="box"><img :src="errorLab" alt="" /> <span>总错误</span></div>
            <div class="box">{{ patTotal.totalError }}次</div>
          </div>
          <div class="lineBox">
            <div class="box"><img :src="successLab" alt="" /> <span>本次正确</span></div>
            <div class="box">{{ patTotal.currSuccess }}次</div>
          </div>
          <div class="lineBox">
            <div class="box"><img :src="errorLab" alt="" /> <span>本次错误</span></div>
            <div class="box">{{ patTotal.currError }}次</div>
          </div>
          <div class="lineBox">
            <div class="box"><img :src="speed" alt="" /> <span>码率</span></div>
            <div class="box">{{ ((patTotal.currSuccess + patTotal.currError) / (dataTime / 60)).toFixed(2) }}次</div>
            <!-- totalTime: patTotal.value.totalTime,
    totalNum: patTotal.value.totalCount, -->
          </div>
          <div class="linebtns">
            <div class="layout-center btn" @click="clearTrainHistory">
              <IconFont type="icon-clear" style="margin-right: 5px"></IconFont>
              清空
            </div>
            <div class="layout-center btn" @click="goBack">
              <IconFont type="icon-rollback" style="margin-right: 5px"></IconFont>
              返回
            </div>
          </div>
        </div>
      </div>
      <CutDown :nowTime="nowTime"></CutDown>
      <div class="tabs layout-center">
        <template v-if="queryType==0">
          <div v-for="(v,index) of tab" :class="{ZGbtns:true,on:tabIndex==index}"  @click="selectTab(index)" >
            <div class="left">
              <div class="left-top"></div>
              <div class="left-bottom"></div>
            </div>
            <div class="center layout-center">{{v.text}}</div>
            <div class="right">
              <div class="right-top"></div>
              <div class="right-bottom"></div>
            </div>
          </div>
        </template>
        <template v-if="queryType == 1">
          <div :class="{ZGbtns:true,on:tabIndex==0}"  @click="selectTab(0)" >
            <div class="left">
              <div class="left-top"></div>
              <div class="left-bottom"></div>
            </div>
            <div class="center layout-center">数码</div>
            <div class="right">
              <div class="right-top"></div>
              <div class="right-bottom"></div>
            </div>
          </div>
          <div :class="{ZGbtns:true,on:tabIndex==1}"  @click="selectTab(1)" >
            <div class="left">
              <div class="left-top"></div>
              <div class="left-bottom"></div>
            </div>
            <div class="center layout-center">字码</div>
            <div class="right">
              <div class="right-top"></div>
              <div class="right-bottom"></div>
            </div>
          </div>
        </template>
      </div>
      <div class="letterbox" :style="{padding: queryType==1?'40px 0':'40px 0',height: 'auto'}">
        <div v-for="(v,index) of letter" :class="{letter:true, on: letterIndex==index}">
          <img v-if="numberArr.indexOf(v.key)>-1&&interfaceStyle==='HJ'" :src="fileUrl+v.key+'.png'" class="img">
          <div class="key" style="font-size: 80px" v-else>{{v.key}}</div>
        </div>
      </div>
      <div class="examKeyboardBox">
        <div class="examKeyboard" v-if="letter.length > 0">
          <img :src="keyboardBg" class="bg">
          <div class="pilotLamp"></div>
          <div class="keysBox">
            <div v-if="tabIndex == 1" class="letterText">
              {{letterText}}
            </div>
            <div class="lineKeys" v-for="(row, r) in electronKey">
              <div class="patGist" v-if="r==0">
                <div class="tit">拍发要领</div>
                <div class="gist" v-if="letter[letterIndex]">{{letter[letterIndex].tip}}用力，垂直击下</div>
              </div>
              <div class="keyBox" v-if="r==1">
                <div class="keys">
                  <div class="key"><div>应急</div></div>
                </div>
              </div>
              <div class="keyBox" v-for="(key, k) in row">
                <div :class="{keys: true, activeKey: letter[letterIndex]&&letter[letterIndex].code==key.key,
                              successKey: patKey==key.code&&letter[letterIndex]&&letter[letterIndex].code==key.key,
                              errorKey: patKey==key.code&&letter[letterIndex]&&letter[letterIndex].code!=key.key}">
                  <div class="key" v-if="key.key=='enter'" style="width: 140px">
                    <img :src="keyEnter" style="width: 50%;">
                  </div>
                  <div class="key" v-else>
                    <div>{{key.text}}</div>
                    <div class="cod" v-if="key._code&&key._code.length > 0">
                      <div class="co" v-for="(co, c) in key._code" :data="co"></div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
  export default {
    name: 'ExamBasicExam'
  }
</script>
<script setup>
  import { onMounted, ref, watch, nextTick, onBeforeUnmount } from 'vue'
  import { createFromIconfontCN } from '@ant-design/icons-vue'
  import { message } from 'ant-design-vue'
  import { useRouter, useRoute } from 'vue-router'
  import useControl from './js/useControl.js'
  import { numberKey, letterKey, specialKey, electronKey, fingerKey } from './js/keyCode.js'
  import { getExamBasicTrain, clearExamBasicTrain, saveExamBasicTrain } from '../../../../../common/api/examApi.js'
  import { partTimeFormatInfo } from '../../../../../common/utils/Utils.js'
  import keyboardBgHJJ from '../../../../../assets/HJJ/exam/keyborad-min.png'
  import keyboardBgHJ from '../../../../../assets/HJ/exam/keyborad-min.png'
  import keyboardBgLJ from '../../../../../assets/LJ/exam/keyborad-min.png'
  import keyboardBgKJ from '../../../../../assets/KJ/exam/keyborad-min.png'

  import clockLab from '../../../../../assets/HJJ/telexTrain/clock.png'
  import countLab from '../../../../../assets/HJJ/telexTrain/count.png'
  import errorLab from '../../../../../assets/HJJ/telexTrain/error.png'
  import successLab from '../../../../../assets/HJJ/telexTrain/success.png'
  import speed from '../../../../../assets/HJJ/telexTrain/speed.png'

  import stateDev from '../../../../../assets/HJ/ico/ico-state-dev.png'
  import stateDevOn from '../../../../../assets/HJ/ico/ico-state-dev-on.png'
  import stateWs from '../../../../../assets/HJ/ico/ico-state-ws.png'
  import stateWsOn from '../../../../../assets/HJ/ico/ico-state-ws-on.png'
  import keyEnter from '../../../../../assets/HJ/exam/keyEnter.png'
  import CutDown from '../../../../../components/cutDown/CutDown.vue'
  import { log } from '@antv/g2plot/lib/utils/invariant.js'
  const interfaceStyle = window.interfaceStyle
  let keyboardBg
  if(interfaceStyle==='HJ' || interfaceStyle==='GD'){
    keyboardBg = keyboardBgHJ
  }else if (interfaceStyle==='HJJ'){
    keyboardBg = keyboardBgHJJ
  }else if (interfaceStyle==='KJ'){
    keyboardBg = keyboardBgKJ
  }else {
    keyboardBg = keyboardBgLJ
  }
  const IconFont = createFromIconfontCN({
    scriptUrl: window.iconUrl
  })
  const nowTime = ref({ h1: 0, h2: 0, m1: 0, m2: 0, s1: 0, s2: 0 })
  const route = useRoute()
  const router = useRouter()
  const { wsOnline, devOnline, patKey,changeCriterion } = useControl()
  const timer = ref(null)
  const dataTime = ref(0)
  const fileUrl = ref(window.fileUrl + '/006/code/big/gradient/')
  const tab = ref([{ text: '1和6' }, { text: '2和7' }, { text: '3和8' }, { text: '4和9' }, { text: '5和0' }, { text: '特殊键' }])
  const numberArr = ref(['1', '1', '2', '3', '4', '5', '6', '7', '8', '9', '0'])
  const letterArr = ref(['A','B','C','D','E','F','G','H','I','J','K','L','M','N','O','P','Q','R','S','T','U','V','W','X','Y','Z']);
  const queryType = ref(null)
  const tabIndex = ref(0)
  const letterText = ref('');
  const letter = ref([])
  const letterIndex = ref(0)
  const rote = ref(0)
  const back = ref(false)
  const patTotal = ref({
    id: null,
    totalTime: 0,
    totalCount: 0,
    totalError: 0,
    currSuccess: 0,
    currError: 0
  })

  onMounted(() => {
    queryType.value = Number(route.query.type)
    initTiming()
    assembliesCode()
    getExamBasicTrainInfo()
  })

  watch(patKey, () => {
    if (patKey.value) {
      nextTick(() => {
        patTotal.value.totalCount++
        if (patKey.value === letter.value[letterIndex.value].coding) {
          letterIndex.value++
          patTotal.value.currSuccess++
          patKey.value = null
          if (letterIndex.value > letter.value.length-1) {
            letterIndex.value = 0
            assembliesCode()
          }
        } else {
          patTotal.value.currError++
          patTotal.value.totalError++
        }
        let speed =  ((patTotal.value.currSuccess + patTotal.value.currError) / (dataTime.value / 60)).toFixed(2)
        changeCriterion(speed)//改变码率
      })
    }
  })

  onBeforeUnmount(() => {
    if (!back.value) {
      saveExamBasicTrainInfo()
    }
  })
  /**
   * 返回
   */
  const goBack = () => {
    back.value = true
    saveExamBasicTrainInfo('back')
  }

  /**
   * 获取练习数据
   */
  const getExamBasicTrainInfo = () => {
    getExamBasicTrain({ type: queryType.value }).then(res => {
      if (res.code === 200) {
        patTotal.value.id = res.data.id
        patTotal.value.totalTime = res.data.totalTime
        patTotal.value.totalCount = res.data.totalNum
        patTotal.value.totalError = res.data.totalError
      }
    })
  }

  /**
   * 保存练习数据
   * @param type
   */
  const saveExamBasicTrainInfo = type => {
    // return
    saveExamBasicTrain({
      id: patTotal.value.id,
      type: queryType.value,
      totalTime: patTotal.value.totalTime,
      totalNum: patTotal.value.totalCount,
      totalError: patTotal.value.totalError
    }).then(res => {
      if (type === 'back') {
        router.go(-1)
      }
    })
  }

  /**
   * 初始化计时
   */
  const initTiming = () => {
    if (timer.value) {
      clearInterval(timer.value)
      timer.value = null
    }
    let time = 0
    timer.value = setInterval(() => {
      patTotal.value.totalTime += 1000
      time++
      dataTime.value = time
      computationTime(time)
    }, 1000)
  }

  /**
   * 计时数据处理
   * @param total
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

  /**
   * tab选项切换
   * @param index
   */
  const selectTab = index => {
    tabIndex.value = index
    letterIndex.value = 0
    assembliesCode()
  }

  /**
   * 生成一组数据
   */
  const assembliesCode = () => {
    let mat,
        arr = []
    letter.value = []
    rote.value = 180 + rote.value
    if (queryType.value == 1 && tabIndex.value == 1) {
      mat = Math.floor(Math.random()*26);
      console.log(letterArr.value[mat])
      letterText.value = letterArr.value[mat]
      letter.value.push(...letterKey[letterText.value])
    } else {
      for (let i=0;i<4;i++) {
        if (queryType.value === 0) {
          mat = Math.floor(Math.random() * fingerKey[tabIndex.value].length)
          letter.value.push(fingerKey[tabIndex.value][mat])
        } else if (queryType.value === 1) {
          mat = Math.floor(Math.random() * 10)
          arr = [...numberKey, ...specialKey]
          letter.value.push(numberKey[mat])
        }
      }
    }

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

  /**
   * 清空
   */
  const clearTrainHistory = () => {
    clearExamBasicTrain({ type: queryType.value }).then(res => {
      if (res.code === 200) {
        message.success('练习数据已清空！')
        patTotal.value.totalTime = 0
        patTotal.value.totalCount = 0
        patTotal.value.totalError = 0
        patTotal.value.currSuccess = 0
        patTotal.value.currError = 0
        initTiming()
      }
    })
  }
</script>
<style scoped lang="less">
  @import "./css/ExamBasicTrain";
</style>