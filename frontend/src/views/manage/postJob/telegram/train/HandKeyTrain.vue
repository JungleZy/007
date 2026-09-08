<template>
  <div class="w-full h-full overflow-hidden relative">
    <div class="loading" v-show="loading">
      <a-spin size="large" tip="正在努力加载..." />
    </div>
    <div class="w-full h-full trainBoxs content-mask-bg">
      <TrainLeft @startTest="beginExerciseInfo" @endTest="statisticsTelegraphData('end')" :trainData="trainData">
        <template v-slot:top>
          <div class="desc">
            {{ trainData.status == 0 ? '请点击下方[开始练习]按钮开启训练' : trainData.status == 1 ? '本次练习正在进行，当前总耗时' : trainData.status == 2 ? '本次练习正在进行，当前总耗时' : '本次练习已结束,总用时' }}
          </div>
          <count-down class="width-100-per layout-center" color="#70c9ff" ref="trainTimeRef" style="height: 55px" />
        </template>
        <template v-slot:bottom>
          <div class="stateBox">
            <div class="h-full layout-left-center"></div>
            <div :class="{ tipBtn: true, active: showTipSymbol }" @click="showTipSymbol = !showTipSymbol"></div>
          </div>
          <div :class="{ flipContainer: true }" style="height: calc(100% - 80px)">
            <div :class="{ 'h-full overflow-auto totalBoxs': true }" style="padding: 0 8px">
              <div class="basicInit">
                <div class="tipSymbol" v-if="showTipSymbol">
                  <div class="symItem" v-for="(sym, s) in initSymbol" :key="s">
                    <div>
                      {{ s == 'machine' ? '试机符：' : s == 'start' ? '开始符：' : s == 'turn' ? '翻页符：' : s == 'alter' || s == 'next' ? '改错符：' : s == 'end' ? '结束符：' : '' }}
                    </div>
                    <template v-for="(w, i) in sym.split(',')" :key="i">
                      <div v-if="i > 0" class="space"></div>
                      <div v-for="(v, j) in w.split('')" :key="j" class="val" :style="{ width: v == 1 ? '12px' : '4px', marginLeft: j > 0 ? '4px' : '0px' }"></div>
                    </template>
                    <div v-if="s == 'alter'" style="margin-left: 10px">（改错当前组）</div>
                    <div v-if="s == 'next'" style="margin-left: 10px">（改错前一组）</div>
                  </div>
                </div>
                <div class=" relative" style="padding: 10px 0 10px 20px">
                  <span style="font-weight: bold">自动换行:</span>  <a-switch v-model:checked="autoLine" checked-children="是" un-checked-children="否"></a-switch>
                </div>
                <div class="item relative">
                  <img :src="labNum" class="ico" />
                  <div>
                    <div class="title">报文组数</div>
                    <div class="tags nobr">{{ trainData.isCable===1?trainData.messageGroup:trainData.messageNumber }}组</div>
                  </div>
                </div>
                <div class="item relative">
                  <img :src="labSpeed" class="ico" />
                  <div>
                    <div class="title">拍发码率</div>
                    <div class="tags nobr">
                      <span v-if="speedUnit">{{ parseFloat(trainData.speed) }}WPM</span>
                      <span v-else>{{ parseFloat(trainData.speed) }}码/分</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </template>
      </TrainLeft>
      <div class="trainCenter" style="width: calc(100% - 290px)">
        <div class="_top">
          <img :src="topBg" class="bg" />
          <div class="cont">
            <div class="scale"></div>
            <div class="vals overflow-auto" ref="patValBoxRef">
              <template v-for="(code, i) in showPatCodeLog" :key="i">
                <div class="val" :style="{ width: code.diff / 10 + 'px' }"></div>
                <div class="gap" :style="{ width: code.gap / 10 + 'px' }">{{ code.gap > 5000 ? code.gap + 'ms' : '' }}</div>
              </template>
            </div>
          </div>
        </div>
        <div class="_main">
          <div class="cont" :style="{ padding: trainData.process == 0 || trainData.process == 3 ? '2% 0' : '0' }">
            <div class="patKey" v-show="trainData.process == 0 || trainData.process == 3">
              <img :src="keyBg" class="bg" />
              <div class="keys overflow-auto" ref="patKeyBoxRef">
                <div v-for="(leaf, i) in trainData.patKeyVal" :key="i" class="keysRow">
                  <template v-for="(key, j) in leaf" :key="j">
                    <div class="key" v-if="(trainData.type==0&&key.replace(/I/g,'') != '')||trainData.type>0">
                      {{ key.indexOf('I')>-1&&trainData.type==0?key.replace(/I/g,''):key }}
                    </div>
                    <template v-if="autoLine">
                      <div v-if="j % 10 == 9 && j != 0" style="width: 100%; height: 1px"></div>
                    </template>
                    <!--<div v-if="j % 10 == 9 && j != 0" style="width: 100%; height: 1px"></div>-->
                  </template>
                </div>
              </div>
            </div>
            <div class="machineBox" v-if="trainData.process == 1 || trainData.process == -1">
              <div class="main">
                <div class="title">试机操作</div>
                <div class="vals">
                  <template v-for="(word, w) in initSymbol.machine.split(',')">
                    <div v-for="(code, c) in word.split('')" :key="c" class="val" :style="{ width: code == 1 ? '30px' : '10px', marginLeft: w > 0 && c == 0 ? '30px' : w == 0 && c == 0 ? '0px' : '10px' }"></div>
                  </template>
                </div>
                <div class="msg">
                  <div class="error" v-if="trainData.process == -1">
                    <span class="text">{{ errorText }}</span>
                  </div>
                  <div class="box">
                    <img :src="textBg" class="bg" />
                    <div class="lab">操作说明</div>
                    <div class="text">
                      <div class="item">试机操作是为了确认您在进行手键拍发的时候统计您的拍发手法，以便为后续正文拍发确定拍发手法，请谨慎拍发！</div>
                    </div>
                    <div class="reset" @click="resetPatMachine"><img :src="reset" class="img" /></div>
                  </div>
                </div>
              </div>
            </div>
            <div class="machineBox" v-else-if="trainData.process == 2 || trainData.process == -2">
              <div class="main">
                <div class="title">拍发开始符号</div>
                <div class="vals">
                  <div v-for="(code, c) in initSymbol.start.split('')" :key="c" class="val" :style="{ width: code == 1 ? '30px' : '10px', marginLeft: c == 0 ? '0px' : '10px' }"></div>
                </div>
                <div class="msg">
                  <div class="error" v-if="trainData.process == -2">
                    <span class="text">开始符号电码拍发错误，请重新拍发！</span>
                  </div>
                  <div class="box">
                    <img :src="textBg" class="bg" />
                    <div class="lab">拍发说明</div>
                    <div class="text">
                      <div class="item">当前开始符号的拍发请您按照您在试机操作确定的拍发手法进行拍发</div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
            <template v-else>
              <div class="patTelegraphBox">
                <div class="switch"><img :src="prev" v-if="trainData.status != 1" @click="switchTelegram('prev')" class="img" /></div>
                <div class="patTelegraph">
                  <div class="printButton item_group btn layout-center" v-print="print" @click="printClick">打印报底</div>
                  <div class="tag">{{ trainData.codeSort ? '长码' : '短码' }}</div>
                  <div class="telegraph">
                    <div class="rowHead">
                      <div class="key">1</div>
                      <div class="key">2</div>
                      <div class="key">3</div>
                      <div class="key">4</div>
                      <div class="key">5</div>
                      <div class="key">6</div>
                      <div class="key">7</div>
                      <div class="key">8</div>
                      <div class="key">9</div>
                      <div class="key">10</div>
                    </div>
                    <div class="keyBox">
                      <template v-for="(key, index) in trainData.telegraph.curr" :key="index">
                        <div :class="{ key: true, curr: currPatKeyIndex == index }" v-if="key.moresKey != '#'">
                          {{ JSON.parse(key.moresKey).join('') }}
                        </div>
                      </template>
                      <template v-if="trainData.telegraph.curr && trainData.telegraph.curr.length < 100">
                        <div class="key" v-for="(key, index) in 100 - trainData.telegraph.curr.length" :key="index"></div>
                      </template>
                    </div>
                  </div>
                  <div class="serial">
                    <div class="ser head"></div>
                    <div class="ser">10</div>
                    <div class="ser">20</div>
                    <div class="ser">30</div>
                    <div class="ser">40</div>
                    <div class="ser">50</div>
                    <div class="ser">60</div>
                    <div class="ser">70</div>
                    <div class="ser">80</div>
                    <div class="ser">90</div>
                    <div class="ser">100</div>
                  </div>
                </div>
                <div class="switch"><img :src="next" v-if="trainData.status != 1" @click="switchTelegram('next')" class="img" /></div>
              </div>
            </template>
          </div>
          <div class="_bottom">
            <div class="pag" v-if="trainData.process == 0 || trainData.process == 3">
              <div class="curr">
                当前：<span class="num">{{ trainData.floorNow }}</span>
              </div>
              <div class="line"></div>
              <div class="total">
                总数：<span class="num">{{ trainData.pag }}</span>
              </div>
            </div>
          </div>
        </div>
        <div id="keyBox" v-if="printShow" style="position: absolute; height: 100%">
          <template v-if="messageBodyList">
            <div v-for="item of messageBodyList" :key="item" style="display: flex; flex-direction: column; height: 100%">
              <table>
                <tr style="display: flex; flex-wrap: wrap">
                  <td v-for="i in 10" class="td theader" :key="i">{{ i }}</td>
                </tr>
                <tr style="display: flex; flex-wrap: wrap" class="tr">
                  <template v-for="(k, index) in 100" :key="k">
                    <td v-if="item[index]" class="td" style="">{{ item[index].join('') }}</td>
                    <td v-else class="td" style=""></td>
                  </template>
                </tr>
              </table>
              <p style="page-break-after: always; flex: 1"></p>
            </div>
          </template>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
  export default {
    name: 'HandKeyPostJobTrain'
  }
</script>
<script setup>
  import { ref, onMounted, onUnmounted, watch } from 'vue'
  import { useRoute } from 'vue-router'
  import { message } from 'ant-design-vue'
  import CountDown from '../../../../../components/common/CountDown.vue'
  import TrainLeft from '../../../../../components/postJob/trainLeft/TrainLeft.vue'

  import useControl from './js/useControl.js'
  import details from './js/details.js'
  import { getPostTelegramTrainById, apiPostTelegramTrainPrintBottomReport } from '../../../../../common/api/TelegramApi.js'
  import { partTimeFormatInfo, sum } from '../../../../../common/utils/Utils.js'
  import { PubSub } from '../../../../../common/utils/PubSub.js'
  import { wsCode } from '../../../../../common/ws/Ws.js'
  import Number from '../../../../../components/number/Number.vue'

  import textBg from '../../../../../assets/HJ/postTrain/machineTextBg.png'
  import reset from '../../../../../assets/HJ/postTrain/reset.png'

  import iconImage from "../../js/iconImage";
  const {labSpeed,labNum,labType,topBg,keyBg,prev,next} = iconImage()
  const autoLine = ref(true)
  const route = useRoute()
  const loading = ref(true)
  const minSpeed = ref(60)
  const showTipSymbol = ref(true)
  const messageBodyList = ref([])
  const printShow = ref(false)
  const print = {
    id: 'keyBox',
    beforeOpenCallback() {
      printShow.value = false
    }
  }
  const printClick = () => {
    printShow.value = true
  }
  const trainData = ref({
    trainId: '',
    tapNumber: 0,
    process: 0, // 0：未开始，1：试机，2：开始符号，3：正式开始,-1:试机失败，-2：开始失败
    telegraph: {
      prev: [],
      curr: [],
      next: []
    },
    patCodeLog: [], // 实时的电码拍发记录集合
    patKeyVal: [], // 拍发电码转换成的字码集合
    countPatSpeedCode: {
      // 待提交计算码率的电码/间隔的时间集合
      dot: [],
      line: [],
      code: [],
      word: [],
      group: [],
      WPM: 0
    }
  })

  const { handKeyDown, patStandard, initFloat, handKeyValue, diffTime, gapTime, wsOnline, devOnline, audioVolume, init } = useControl(trainData)

  const {
    patKeyBoxRef, patValBoxRef, trainTimeRef, initSymbol, errorText, speedUnit, currPatKeyIndex, getPostTrainKeyInfo,
    switchTelegram, handleReceiveKeyCode, beginExerciseInfo, resetPatMachine, timeAreaShow, statisticsTelegraphData,
    getScoreOffsetInfo,resetTrainInfo,showPatCodeLog
  } = details(
      trainData,
      patStandard,
      initFloat,
      wsOnline,
      devOnline,
      loading,
      messageBodyList
  )

  onMounted(() => {
    if (route.query.id && route.query.id !== '') {
      // apiPostTelegramTrainPrintBottomReport({ id: route.query.id }).then(res => {
      //   let arr = res.data.map(item => {
      //     item = JSON.parse(item)
      //     return item
      //   })
      //   for (let i = 0; i < arr.length / 100; i++) {
      //     let num = arr.slice(i * 100, (i + 1) * 100)
      //     messageBodyList.value.push(num)
      //   }
      //   console.log(messageBodyList.value);
      // })

      trainData.value.trainId = route.query.id
      getTrainDetails()
    }
  })
  const getTrainDetails = ()=>{
    getPostTelegramTrainById({
      id: trainData.value.trainId
    }).then(res => {
      console.log(res)
      loading.value = false
      if (res.code === 200) {
        if(res.data.status===1){
          trainData.value.status = res.data.status
          resetTrainInfo()
          setTimeout(()=>{
            getTrainDetails()
          },1000)
          return
        }
        for (let key in res.data) {
          trainData.value[key] = res.data[key]
        }
        trainData.value.pag = res.data.isCable===1?res.data.messageNumber:Math.ceil(res.data.messageNumber / 100)
        trainData.value.messageBody = trainData.value.messageBody.map(item => {
          item = JSON.parse(item)
          return item
        })
        getScoreOffsetInfo(res.data.ruleId)
        getPostTrainKeyInfo(res.data.floorNow, 'curr')
        if (res.data.floorNow > 1) {
          getPostTrainKeyInfo(res.data.floorNow - 1, 'prev')
        }
        if (res.data.floorNow < trainData.value.pag) {
          getPostTrainKeyInfo(res.data.floorNow + 1, 'next')
        }
        if (res.data.status === 2) {
          timeAreaShow(trainData.value.time.validTime)
        }
        init().then()
      }
    })
  }
  onUnmounted(() => {
    PubSub.unsubscribe('message')
  })
  watch(handKeyValue, () => {
    if (handKeyValue.value !== '') {
      handleReceiveKeyCode(handKeyValue.value, diffTime.value, gapTime.value)
    }
  })
</script>

<style scoped>
  @import './css/HandKeyTrain.less';
  p {
    page-break-after: always;
    break-after: page;
  }
  .theader {
    color: red;
    border-left: 1px solid rgba(243, 100, 100, 0.3);
    border-top: 1px solid rgba(243, 100, 100, 1);
    font-weight: bold;
  }
  .td {
    width: 10%;
    height: 60px;
    display: flex;
    justify-content: center;
    align-items: center;
    border-left: 1px solid rgba(243, 100, 100, 1);
    border-top: 1px solid rgba(243, 100, 100, 1);
    font-size: 16px;
  }
  .tr {
    color: #000;
  }
  tr {
    border-right: 1px solid rgba(243, 100, 100, 1);
  }
  tr:last-of-type {
    border-bottom: 1px solid rgba(243, 100, 100, 1);
  }
  @media print {
    @page {
      size: landscape;
      margin: 3mm;
    }
  }
  .printButton {
    position: absolute;
    top: 3px;
    right: -70px;
    cursor: pointer;
  }
</style>
