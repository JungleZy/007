<template>
  <div class="w-full h-full overflow-hidden relative">
    <div class="loading" v-show="loading">
      <a-spin size="large" tip="正在努力加载..." />
    </div>
    <div class="w-full h-full trainBoxs content-mask-bg">
      <TrainLeft :trainData="trainData">
        <template v-slot:top>
          <div class="desc">
            {{ trainData.status == 0 ? '请点击下方[开始练习]按钮开启训练' : trainData.status == 1 ? '本次练习正在进行，当前总耗时' : trainData.status == 2 ? '本次练习正在进行，当前总耗时' : '本次练习已结束,总用时' }}
          </div>
          <div class="desc" style="top: 46px;font-size: 14px">{{trainData.title}}</div>
          <count-down class="width-100-per layout-center" color="#70c9ff" ref="trainTimeRef" style="height: 55px;margin-top: 24px" />
        </template>
        <template v-slot:bottom>
          <div class="basicExercise">
            <div class="w-full grouping_content overflow-hidden">
              <div :class="{ flipContainer: true }" style="height: 100%">
                <div :class="{ 'h-full overflow-auto totalBoxs': true }" style="padding: 0 8px">
                  <div class="basicInit">
                    <div class="item relative">
                      <img :src="labType" class="ico" />
                      <div>
                        <div class="title">报文类型</div>
                        <div class="tags nobr">
                          {{ trainData.type==0?'数据报':trainData.type==1?'字码报':'混合报' }}
                        </div>
                      </div>
                    </div>
                    <div class="item relative">
                      <img :src="labNum" class="ico" />
                      <div>
                        <div class="title">报文组数</div>
                        <div class="tags nobr">{{ trainData.totalNumber }}组</div>
                      </div>
                    </div>
                    <div class="item relative">
                      <img :src="labSpeed" class="ico" />
                      <div>
                        <div class="title">拍发码率</div>
                        <div class="tags nobr">{{ parseFloat(trainData.speed) }}码/分</div>
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
        <div class="_main">
          <div class="cont" style="padding: 2% 0 24px">
            <NipMagicSpace>
              <template v-slot:one>
                <div class="relative">
                  <img :src="keyBg" class="bg" style="height: 370px"/>
                  <div class="w-full h-full overflow-auto absolute" ref="patKeyBoxRef" style="display: block;top: 0">
                    <a-textarea v-model:value="pageCodes[currPatKeyIndex]" @change="textareaChange" spellcheck="false"
                                style="height: 100%; resize: none; font-size: 22px;font-family: Consolas, Monaco, 'Andale Mono', 'Ubuntu Mono', monospace;line-height: 30px;color: white"></a-textarea>
                  </div>
                </div>
              </template>
              <template v-slot:two>
                <div  style="display: flex;align-items: center;">
                  <div class="switch"><img :src="prev" v-if="trainData.status != 1" @click="switchTelegram('prev')" class="img" /></div>
                  <div class="patTelegraph">
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
                      <div class="keyBox" v-if="trainData.telegraph">
                        <template v-for="(key, index) in trainData.telegraph[trainData.floorNow - 1]" :key="index">
                          <div :class="{ key: true, curr: currPatKeyIndex == index }" v-if="key.key != '#'">
                            {{ key.key.join('') }}
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
            </NipMagicSpace>
          </div>
          <div class="_bottom">
            <div class="pag">
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
      </div>
    </div>
    <div class="playTipsBox" v-if="cutTimer!=='begin'&&patUser.isFinish != 1">
<!--    <div class="playTipsBox" v-if="false">-->
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
          <div class="roadBtn" v-if="!readyPat" @click="readyTrainPat">{{trainData.status==0?'准备拍发':'重新拍发'}}</div>
          <div class="roadBtn ml-5" v-if="trainData.status==1 && (currPatKeyIndex > 0 || trainData.floorNow > 1)"
               @click="readyTrainPat(1)">继续拍发</div>
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
  import NipMagicSpace from "../../../../../../components/common/NipMagicSpace.vue";
  import { useRoute } from 'vue-router'
  import { message } from 'ant-design-vue'
  import { PubSub } from '../../../../../../common/utils/PubSub.js'
  import { wsCode } from '../../../../../../common/ws/Ws.js'
  import { partTimeFormatInfo, sum } from '../../../../../../common/utils/Utils.js'
  import {getDatagramDetail} from '../../../../../../common/api/datagramZuXun.js'
  import details from './js/datagramTrain.js'
  import Number from '../../../../../../components/number/Number.vue'

  import textBg from '../../../../../../assets/HJ/postTrain/machineTextBg.png'
  import reset from '../../../../../../assets/HJ/postTrain/reset.png'

  import iconImage from "../../../../postJob/js/iconImage";
  const {labSpeed,labNum,labType,topBg,keyBg,prev,next} = iconImage()
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
    telegraph: [],
    patCodeLog: [], // 实时的电码拍发记录集合
    patKeyVal: [], // 拍发电码转换成的字码集合
  })
  const emits = defineEmits(['changeStatus'])

  const {
    switchTelegram,timeAreaShow,initTrainTimeInfo,connectWebsocket,cutTime,cutTimer,trainTimeRef,
    patUser,readyPat,getPostTrainKeyInfo,currPatKeyIndex,pageCodes,textareaChange,readyTrainPat
  } = details(trainData, loading, emits)

  onMounted(() => {
    if (route.query.id && route.query.id !== '') {
      trainData.value.trainId = String(route.query.id)
      getDatagramDetail({
        trainId: trainData.value.trainId,
        userId: userInfo.id
      }).then(res => {
        loading.value = false
        if (res.code === 200) {
          for (let key in res.data) {
            trainData.value[key] = res.data[key]
          }
          trainData.value.status = res.data.status
          if(res.data.isCable===1){
            trainData.value.pag = res.data.pageCount
          }else {
            trainData.value.pag = Math.ceil(res.data.totalNumber / 100)
          }
          res.data.userInfoList.map(item => {
            if (userInfo.id == item.userId) {
              trainData.value['floorNow'] = item.existPageNumber + 1
              trainData.value['speed'] = item.speed==null?'0':item.speed
              trainData.value['speedLog'] = item.speedLog
              trainData.value['score'] = item.score
              trainData.value['accuracy'] = item.accuracy
              trainData.value['validTime'] = item.validTime
              patUser.value = item
              if (item.isFinish == 1) {
                timeAreaShow(item.validTime * 1000)
                trainData.value['floorNow'] = 1;
              } else if (res.data.status == 1) {
                initTrainTimeInfo()
              }
            }
          })
          getPostTrainKeyInfo(1)
          if (trainData.value.pag > 1) {
            getPostTrainKeyInfo(2)
          }
          if (res.data.status === 2) {
            timeAreaShow(trainData.value.validTime)
          } else {
            connectWebsocket();
          }
        }
      })
    }
  })
  onUnmounted(() => {
    if (trainData.value.status === 1) {
      let obj = {
        patPage: trainData.value.floorNow,
        patKeyIndex: currPatKeyIndex.value,
        time: trainData.value.validTime,
        speed: trainData.value.speed,
        pageCodes: pageCodes.value
      }
      window.localStorage.setItem('telexZuXun' + trainData.value.trainId, JSON.stringify(obj))
    }
    PubSub.unsubscribe('message')
  })
  window.onbeforeunload = () => {
    if (trainData.value.status == 1) {
      let obj = {
        patPage: trainData.value.floorNow,
        patKeyIndex: currPatKeyIndex.value,
        time: trainData.value.validTime,
        speed: trainData.value.speed,
        pageCodes: pageCodes.value
      }
      window.localStorage.setItem('telexZuXun' + trainData.value.trainId, JSON.stringify(obj))
    }
  }
</script>

<style scoped lang="less">
  :deep{
    .basicExercise .exerciseBtn{
      display: none!important;
    }
  }
  @import 'css/handKeyTrain';
  .trainCenter ._main {
    width: 100%;
    height: 100%;
    padding: 0 .96% .9%;
    position: relative;
  }
  p {
    page-break-after: always;
    break-after: page;
  }

  tr {
    border-right: 1px solid rgba(243, 100, 100, 1);
  }
  tr:last-of-type {
    border-bottom: 1px solid rgba(243, 100, 100, 1);
  }
  @keyframes flicker {
    0% {opacity: 0}
    50% {opacity: 1}
    100% {opacity: 0}
  }
  @media print {
    @page {
      size: landscape;
      margin: 3mm;
    }
  }
  .HJJ,.HJ{
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
    .cursor {
      width: 1px;
      height: 20px;
      border-left: 1px solid rgba(255,255,255,.5);
      animation: flicker 1s linear infinite;
      position: relative;
      top: 6px;
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
    p {
      page-break-after: always;
      break-after: page;
    }
    .cursor {
      width: 1px;
      height: 20px;
      border-left: 1px solid rgba(255,255,255,.5);
      animation: flicker 1s linear infinite;
      position: relative;
      top: 6px;
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
  }
</style>
