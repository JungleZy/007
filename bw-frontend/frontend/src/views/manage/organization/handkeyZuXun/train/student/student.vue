<template>
  <div class="w-full h-full overflow-hidden relative">
    <a-alert v-if="submissionError" type="error" :message="submissionError" style="position:absolute;z-index:1000;top:8px;left:20%">
      <template #description><a-button :loading="submissionBusy" @click="retrySubmit">重试原提交</a-button></template>
    </a-alert>
    <div class="loading" v-show="loading">
      <a-spin size="large" tip="正在努力加载..." />
    </div>
    <div class="w-full h-full trainBoxs content-mask-bg">
      <TrainLeft :trainData="trainData">
        <template v-slot:top>
          <div class="desc">
            {{ trainData.status === 3 ? '收尾补交 / 待结算，已停止新采集' : trainData.status === 2 ? '本轮已完成' : trainData.status === 1 ? '本轮正在进行' : '等待教员开始训练' }}
          </div>
          <div class="desc" style="top: 46px;font-size: 14px">{{trainData.name}}</div>
          <count-down class="width-100-per layout-center" color="#70c9ff" ref="trainTimeRef" style="height: 55px;margin-top: 24px" />
        </template>
        <template v-slot:bottom>
          <div class="basicExercise">
            <div class="w-full grouping_content overflow-hidden">
              <div class="stateBox">
                <div class="h-full layout-left-center"></div>
                <div :class="{ tipBtn: true, active: showTipSymbol }" @click="showTipSymbol = !showTipSymbol"></div>
              </div>
              <div :class="{ flipContainer: true }" style="height: 100%">
                <div :class="{ 'h-full overflow-auto totalBoxs': true }" style="padding: 0 8px">
                  <div class="basicInit">
                    <div class="tipSymbol" v-if="showTipSymbol">
                      <div class="symItem" v-for="(sym, s) in initSymbol" :key="s">
                        <div>
                          {{
                          s == 'machine'
                          ? '试机符：'
                          : s == 'start'
                          ? '开始符：'
                          : s == 'turn'
                          ? '翻页符：'
                          : s == 'alter' || s == 'next'
                          ? '改错符：'
                          : s == 'end'
                          ? '结束符：'
                          : ''
                          }}
                        </div>
                        <template v-for="(w, i) in sym.split(',')" :key="i">
                          <div v-if="i > 0" class="space"></div>
                          <div
                            v-for="(v, j) in w.split('')"
                            :key="j"
                            class="val"
                            :style="{ width: v == 1 ? '12px' : '4px', marginLeft: j > 0 ? '4px' : '0px' }"
                          ></div>
                        </template>
                        <div v-if="s == 'alter'" style="margin-left: 10px">（改错当前组）</div>
                        <div v-if="s == 'next'" style="margin-left: 10px">（改错前一组）</div>
                      </div>
                    </div>
                    <div class=" relative" style="padding: 10px 0 0 20px">
                      <span style="font-weight: bold">自动换行:</span>  <a-switch v-model:checked="autoLine" checked-children="是" un-checked-children="否"></a-switch>
                    </div>
                    <div class="item relative">
                      <img :src="labNum" class="ico" />
                      <div>
                        <div class="title">报文组数</div>
                        <div class="tags nobr">{{ trainData.messageNumber }}组</div>
                      </div>
                    </div>
                    <div class="item relative">
                      <img :src="labSpeed" class="ico" />
                      <div>
                        <div class="title">本次采集码率（预估）</div>
                        <div class="tags nobr">{{ parseFloat(trainData.speed) }}字符/分</div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </template>
      </TrainLeft>
      <div class="trainCenter" style="width: calc(100% - 300px)">
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
          <div class="cont" :style="{ padding: trainData.process == 0 || trainData.process == 2 ? '2% 0 24px' : '0' }">
            <div class="patKey" v-show="trainData.process == 0 || trainData.process == 2">
              <img :src="keyBg" class="bg" />
              <div class="keys overflow-auto" ref="patKeyBoxRef">
                <div v-for="(leaf, i) in trainData.patKeyVal" :key="i" class="keysRow">
                  <template v-for="(key, j) in leaf" :key="j">
                    <div class="key">{{ key.indexOf('I')>-1&&trainData.type==0?key.replace(/I/g,''):key }}</div>
<!--                    <div v-if="j % 10 == 9 && j != 0" style="width: 100%; height: 1px"></div>-->
                    <template v-if="autoLine">
                      <div v-if="j % 10 == 9 && j != 0" style="width: 100%; height: 1px"></div>
                    </template>
                  </template>
                </div>
              </div>
            </div>
            <div class="machineBox" v-if="trainData.process == 1 || trainData.process == -1">
              <div class="main">
                <div class="title">拍发开始符号</div>
                <div class="vals">
                  <div v-for="(code, c) in initSymbol.start.split('')" :key="c" class="val"
                       :style="{ width: code == 1 ? '30px' : '10px', marginLeft: c == 0 ? '0px' : '10px' }"></div>
                </div>
                <div class="msg">
                  <div class="error" v-if="trainData.process == -1">
                    <span class="text">开始符号电码拍发错误，请重新拍发！</span>
                  </div>
                  <div class="box">
                    <img :src="textBg" class="bg" />
                    <div class="lab">拍发说明</div>
                    <div class="text">
                      <div class="item">当前开始符号的拍发请您按照您在试机操作确定的拍发手法进行拍发</div>
                    </div>
                    <div class="reset" @click="resetPatStart"><img :src="reset" class="img" /></div>
                  </div>
                </div>
              </div>
            </div>
            <template v-else>
              <div class="patTelegraphBox">
                <div class="switch"><img :src="prev" v-if="trainData.status != 1" @click="switchTelegram('prev')" class="img" /></div>
                <div class="patTelegraph">
<!--                  <div class="printButton item_group btn layout-center" v-print="print" @click="printClick">打印报底</div>-->
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
                      <template v-for="(key, index) in trainData.telegraph[trainData.floorNow - 1]" :key="index">
                        <div :class="{ key: true, curr: currPatKeyIndex == index }">
                          {{ key.moresKey === '#' ? '#' : JSON.parse(key.moresKey).join('') }}
                        </div>
                      </template>
                      <template v-if="trainData.telegraph[trainData.floorNow - 1] && trainData.telegraph[trainData.floorNow - 1].length < 100">
                        <div class="key" v-for="(key, index) in 100 - trainData.telegraph[trainData.floorNow - 1].length" :key="index"></div>
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
            <div class="pag" v-if="trainData.process == 0 || trainData.process == 2">
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
          <div v-for="item of trainData.messageBody" :key="item" style="display: flex; flex-direction: column; height: 100%">
            <table>
              <tr style="display: flex; flex-wrap: wrap">
                <td v-for="i in 10" class="td theader" :key="i">{{ i }}</td>
              </tr>
              <tr style="display: flex; flex-wrap: wrap" class="tr">
                <template v-for="(k, index) in 100" :key="k">
                  <td v-if="item[index]" class="td" style="">{{ JSON.parse(item[index].moresKey).join('') }}</td>
                  <td v-else class="td" style=""></td>
                </template>
              </tr>
            </table>
            <p style="page-break-after: always; flex: 1"></p>
          </div>
        </div>
      </div>
    </div>
    <div class="playTipsBox" v-if="cutTimer!=='begin' && patUser.isFinish != 1">
      <div class="tipCard" style="top: calc(50% - 200px)">
        <div class="title" style="padding: 60px 40px 20px">欢迎{{trainData.status==0?'进来':'回来'}}【{{userInfo.userName}}】</div>
        <div class="desc">
          <div class="dis" v-if="readyPat&&cutTimer==null">已经准备等待教员开始训练</div>
          <div class="dis" v-if="readyPat&&cutTimer!=null">训练已开始，请准备开始训练</div>
          <div class="dis" v-if="trainData.status==1 && (currPatKeyIndex > 0 || trainData.floorNow > 1)">
            您已拍发到：第<strong>{{trainData.floorNow}}</strong>页，第<strong>{{currPatKeyIndex}}</strong>组
          </div>
        </div>
        <div class="roadItem" style="padding-top: 60px">
          <div style="font-size: 46px" v-if="readyPat&&cutTimer!=null">{{ cutTime }}</div>
          <div class="roadBtn" v-if="!readyPat && trainData.status !== 3" @click="readyTrainPat">{{trainData.status==0?'准备拍发':'重新拍发'}}</div>
          <div class="roadBtn ml-5" v-if="trainData.status==1"
               @click="readyTrainPat(1)">继续拍发</div>
          <a-button v-if="trainData.status === 3" @click="readyTrainPat(1)">补交 / 完成本轮</a-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
  export default {
    name: 'HandKeyZuXunStudent'
  }
</script>
<script setup>
  import { ref, onMounted, onUnmounted, watch, defineProps, defineEmits } from 'vue'
  import { useRoute } from 'vue-router'
  import { message } from 'ant-design-vue'
  import TrainLeft from '../../../../../../components/postJob/trainLeft/TrainLeft.vue'
  import { PubSub } from '../../../../../../common/utils/PubSub.js'
  import { wsCode } from '../../../../../../common/ws/Ws.js'
  import { partTimeFormatInfo, sum } from '../../../../../../common/utils/Utils.js'
  import { getPostTelegramTrainById } from '../../../../../../common/api/TelegramApi.js'
  import {getHandKeyZuXunDetails} from "../../../../../../common/api/handkeyZuXun.js";
  import useControl from './js/useControl.js'
  import details from './js/handKeyTrain.js'
  import Number from '../../../../../../components/number/Number.vue'


  import reset from '../../../../../../assets/HJ/postTrain/reset.png'

  import iconImage from "../../../../postJob/js/iconImage";
  const {labSpeed,labNum,labType,topBg,keyBg,prev,next,textBg} = iconImage()
  const autoLine = ref(true)
  const route = useRoute()
  const loading = ref(true)
  const showTipSymbol = ref(true)
  const userInfo = JSON.parse(localStorage.getItem('userInfo'))
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
    process: 0, // 0：未开始，1：试机，2：开始符号，3：正式开始,-1:试机失败，-2：开始失败
    telegraph: [],
    patCodeLog: [], // 实时的电码拍发记录集合
    patKeyVal: [], // 拍发电码转换成的字码集合
  })
  const emits = defineEmits(['changeStatus'])

  const { handKeyDown, patStandard, initFloat, onKey, wsOnline, devOnline, audioVolume } = useControl(trainData)

  const {
    submissionError, submissionBusy, retrySubmit,
    patKeyBoxRef, patValBoxRef, trainTimeRef, initSymbol, errorText, currPatKeyIndex,readyPat,patUser,getPostTrainKeyInfo,
    switchTelegram, resetPatStart,handleReceiveKeyCode, timeAreaShow, getScoreOffsetInfo,readyTrainPat,connectWebsocket,
    initTrainTimeInfo,cutTime,cutTimer,showPatCodeLog
  } = details(trainData, patStandard, initFloat, wsOnline, devOnline, loading,emits)

  onMounted(() => {
    if (route.query.id && route.query.id !== '') {
      trainData.value.trainId = route.query.id * 1
      getHandKeyZuXunDetails({
        id: trainData.value.trainId,
        uid: userInfo.id
      }).then(res => {
        loading.value = false
        if (res.code === 200) {
          for (let key in res.data) {
            trainData.value[key] = res.data[key]
          }
          trainData.value.status = res.data.status
          trainData.value.pag = Math.ceil(res.data.messageNumber / 100)
          res.data.userInfoList.map(item => {
            if (userInfo.id == item.userId) {
              trainData.value['floorNow'] = item.existPageNumber + 1
              trainData.value['speed'] = item.speed==null?'0':item.speed
              trainData.value['speedLog'] = item.speedLog
              trainData.value['score'] = item.score
              trainData.value['accuracy'] = item.accuracy
              trainData.value['validTime'] = item.validTime
              trainData.value['patValue'] = item.patValue
              patUser.value = item
              if (item.isFinish == 1) {
                timeAreaShow(item.validTime * 1000)
                trainData.value['floorNow'] = 1;
              } else if (res.data.status == 1) {
                initTrainTimeInfo()
              }
            }
          })
          // console.log(trainData.value)
          trainData.value.floorNow = Math.min(trainData.value.floorNow, trainData.value.pag)
          getPostTrainKeyInfo(trainData.value.floorNow)
          if (res.data.status === 2) {
            timeAreaShow(trainData.value.validTime)
          } else {
            connectWebsocket();
          }
          getScoreOffsetInfo(res.data.ruleId)
        }
      })
    }
  })
  onUnmounted(() => {
    if (trainData.value.status == 1) {
      let obj = {
        patPage: trainData.floorNow,
        patKeyIndex: currPatKeyIndex.value,
        time: trainData.value.validTime,
        speed: trainData.speed
      }
      window.localStorage.setItem('handKeyZuXun'+trainData.value.trainId, JSON.stringify(obj))
    }
    PubSub.unsubscribe('message')
  })
  window.onbeforeunload = () => {
    if (trainData.value.status == 1) {
      let obj = {
        patPage: trainData.floorNow,
        patKeyIndex: currPatKeyIndex.value,
        time: trainData.value.validTime,
        speed: trainData.speed
      }
      window.localStorage.setItem('handKeyZuXun'+trainData.value.trainId, JSON.stringify(obj))
    }
  }
  onKey(event => handleReceiveKeyCode(event.code, event.diffTime, event.gapTime, event))
</script>

<style scoped lang="less">
  :deep(.basicExercise .exerciseBtn){
    display: none!important;
  }
  @import './css/handKeyTrain.css';
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
  .playTipsBox {
    width: 100%;
    height: 100%;
    background: rgba(0,0,0,.6);
    position: absolute;
    top: 0;
    left: 0;
    z-index: 9;
  }
  .tipCard {
    width: 700px;
    height: 400px;
    background: url("../../../../../../assets/HJ/postTrain/tipsBg.png") no-repeat center;
    position: absolute;
    left: calc(50% - 350px);
    top: calc(50% - 240px);
    .title {
      font-size: 24px;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 40px;
      strong {
        color: #ff7f00;
      }
    }
    .riadBox {
      min-height: 200px;
      display: flex;
      justify-content: center;
      align-items: center;
      flex-direction: column;
    }
    .roadItem {
      padding:  0 40px 30px;
      display: flex;
      justify-content: center;
      align-items: center;
      .roadBtn {
        width: 154px;
        height: 50px;
        background: url("../../../../../../assets/HJ/postTrain/receiveBtn.png") no-repeat center;
        display: flex;
        align-items: center;
        justify-content: center;
        font-size: 17px;
        padding-bottom: 6px;
        cursor: pointer;
        &.on,&:hover {
          background: url("../../../../../../assets/HJ/postTrain/receiveBtnOn.png") no-repeat center;
          color: #fff;
        }
      }
    }
    .desc {
      height: 60px;
      font-size: 15px;
      padding: 10px 0;
      display: flex;
      align-items: center;
      justify-content: center;
      .dis {
        display: flex;
        align-items: center;
        &+.dis {
          margin-left: 30px;
        }
      }
      strong {
        font-size: 18px;
        color: #ff7f00;
        margin-right: 5px;
      }
      .unit {
        color: #ff7f00;
      }
    }
  }
  .LJ{
    .tipCard {
      width: 700px;
      height: 400px;
      background: url("../../../../../../assets/LJ/postTrain/tipsBg.png") no-repeat center;
      position: absolute;
      left: calc(50% - 350px);
      top: calc(50% - 240px);
      .title {
        font-size: 24px;
        display: flex;
        align-items: center;
        justify-content: center;
        padding: 40px;
        strong {
          color: #ff7f00;
        }
      }
      .riadBox {
        min-height: 200px;
        display: flex;
        justify-content: center;
        align-items: center;
        flex-direction: column;
      }
      .roadItem {
        padding:  0 40px 30px;
        display: flex;
        justify-content: center;
        align-items: center;
        .roadBtn {
          width: 154px;
          height: 50px;
          background: url("../../../../../../assets/LJ/postTrain/receiveBtn.png") no-repeat center;
          display: flex;
          align-items: center;
          justify-content: center;
          font-size: 17px;
          padding-bottom: 6px;
          cursor: pointer;
          &.on,&:hover {
            background: url("../../../../../../assets/LJ/postTrain/receiveBtnOn.png") no-repeat center;
            color: #fff;
          }
        }
      }
      .desc {
        height: 60px;
        font-size: 15px;
        padding: 10px 0;
        display: flex;
        align-items: center;
        justify-content: center;
        .dis {
          display: flex;
          align-items: center;
          &+.dis {
            margin-left: 30px;
          }
        }
        strong {
          font-size: 18px;
          color: #ff7f00;
          margin-right: 5px;
        }
        .unit {
          color: #ff7f00;
        }
      }
    }
  }
</style>
