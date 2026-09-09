<template>
  <div class="wrapper">
    <div class="room_box" :style="{ width: roomValue }">
      <TrainLeft :trainData="trainData">
        <template v-slot:bottom>
          <div class="title">参训时间</div>
          <div class="time_cont_box">
            <count-down class="width-100-per layout-center" color="#70c9ff" ref="trainTimeRef" style="height: 55px" />
          </div>
          <div class="lssued_box" v-if="route.query.roomState == 'send'">
            <div class="title">接收文书</div>
            <div class="content">
              <p v-for="(item, index) in sendLessudTextList" :key="index">{{ item }}</p>
            </div>
          </div>
          <div class="user_list_box" :style="{ height: 'calc(100% - ' + (route.query.roomState == 'send' ? 296 : 156) + 'px)' }">
            <div class="title">接收人分组</div>
            <div class="user_list">
              <div class="send_list">
                <div class="pd_user_info" :class="{ active: item.activeState == true }" v-for="(item, index) in putAwayUserList" :key="item.id" @click="handleActivePutAway(item, index)">
                  <strong style="width: 30px; font-size: 16px">{{ item.channel + 1 }}</strong>
                  <div class="image">
                    <img :src="`${url}${item.userImg}`" alt="" />
                  </div>
                  <div class="name">{{ item.userName }}</div>
                  <div class="state" style="color: #de940b" v-if="trainData.stats == 2">已完成</div>
                  <div class="state" style="color: #af0505" v-else-if="item.socketStatus == 0">离线</div>
                  <div class="state" style="color: #40a9ff" v-else-if="item.socketStatus == 1 && trainData.stats == 0||item.id ===userInfo.deviceId">在线</div>
                  <div class="state" style="color: #52c41a" v-else-if="item.socketStatus > 0 && trainData.stats == 1">进行中</div>
                  <div class="state" style="color: #52c41a" v-else-if="item.socketStatus == 2 && trainData.stats == 0">准备</div>
                  <div class="room_state" style="color: #4ca7f5" v-if="item.status == 1"><FileTextOutlined /></div>
                </div>
              </div>
            </div>
          </div>
          <div class="item_group btn layout-center" style="width: 100%; margin-top: 5px" @click="handleStartTrain" v-if="buttonState && trainData.userStatus != 1 && trainData.stats == 0 && route.query.roomState != 'createUser'">准备训练</div>
        </template>
      </TrainLeft>
    </div>
    <div class="create_user_box" v-if="route.query.roomState == 'createUser' && trainData.stats < 2">
      <div class="startBtn" @click="handleBeginTrain" v-if="trainData.stats == 0">开始训练</div>
      <div class="underwayBox" v-if="trainData.stats == 1">
        <div class="trainStatusText">训练正在进行中...</div>
        <div class="disposeBox">
          <div class="lab">干扰类型：</div>
          <div class="disturbBox">
            <a-checkbox v-for="item of disturbList" :key="item.type" :checked="item.checked" @change="changeDisturbInfo(item)" style="margin: 10px 0 0 0; width: 120px">{{ item.name }}</a-checkbox>
          </div>
          <div class="lab mt-2">干扰强度：</div>
          <div class="volumeSlider voice" style="margin: 20px 40px 0; width: 360px">
            <a-slider v-model:value="disturbVolume" :min="0" :max="100" :step="1" @change="changeDisturbInfo()"></a-slider>
          </div>
          <div class="flex text" style="justify-content: space-between; width: 372px; margin-left: 38px">
            <div>0</div>
            <div>100</div>
          </div>
        </div>
        <div class="startBtn end" @click="handleEndTrain" style="top: 50%">结束训练</div>
      </div>
    </div>
    <template v-if="(trainData.stats == 2 && !activePutAway) || route.query.roomState == 'send'">
      <div class="trainCenter_box">
        <div class="_top">
          <img :src="topBg" class="bg" />
          <div class="cont">
            <div class="scale"></div>
            <div class="vals overflow-auto" id="patValBoxRef">
              <template v-for="(code, i) in trainData.patCodeLog" :key="i">
                <div class="gap" :style="{ width: code.gap / 10 + 'px' }">
                  {{ code.gap > 5000 ? code.gap + 'ms' : '' }}
                </div>
                <div class="val" :style="{ width: code.diff / 10 + 'px' }"></div>
              </template>
            </div>
          </div>
        </div>
        <div class="_main">
          <div class="cont">
            <div class="patTelegraphBox">
              <div class="switch">
                <img :src="prev" v-if="trainData.status != 1" @click="switchTelegram(-1)" class="img" />
              </div>
              <div class="patTelegraph">
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
                      <div
                        :class="{
                          key: true,
                        }"
                      >
                        {{ key.key }}
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
              <div class="switch">
                <img :src="next" v-if="trainData.status != 1" @click="switchTelegram(1)" class="img" />
              </div>
            </div>
          </div>
          <div class="_bottom">
            <div class="pag">
              <div class="curr">
                当前：<span class="num">{{ trainData.telegraph.prev }}</span>
              </div>
              <div class="line"></div>
              <div class="total">
                总数：<span class="num">{{ trainData.telegraph.allPage }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </template>

    <div class="trainCenter_box" v-if="activePutAway && trainData.stats == 2">
      <TrainResult style="margin-left: 20px" :result="a" :curr="curr" @switchPage="handlePageTurn" :all="all"></TrainResult>
    </div>
    <div class="room_box" :style="{ width: roomValue }">
      <TrainLeft :trainData="trainData">
        <template v-slot:bottom>
          <div class="title">文书下发</div>
          <div class="lssued_box">
            <div class="row">
              <a-input type="text" v-model:value="lssuedText" placeholder="请输入"></a-input>
              <a-button class="lessud_but" @click="sendLessudText">下发文书</a-button>
            </div>
          </div>
          <div class="user_list_box">
            <div class="title">发送人分组</div>
            <div class="user_list">
              <div class="send_list">
                <div class="pd_user_info" v-for="item in sendUserList" :key="item.id">
                  <strong style="width: 30px; font-size: 16px">{{ item.channel + 1 }}</strong>
                  <div class="image"><img :src="`${url}${item.userImg}`" alt="" /></div>
                  <div class="name">{{ item.userName }}</div>
                  <div class="state" style="color: #de940b" v-if="trainData.stats == 2">已完成</div>
                  <div class="state" style="color: #af0505" v-else-if="item.socketStatus == 0">离线</div>
                  <div class="state" style="color: #40a9ff" v-else-if="item.socketStatus == 1 && trainData.stats == 0">在线</div>
                  <div class="state" style="color: #52c41a" v-else-if="item.socketStatus > 0 && trainData.stats == 1">进行中</div>
                  <div class="state" style="color: #52c41a" v-else-if="item.socketStatus == 2 && trainData.stats == 0">准备</div>
                  <div class="room_state" style="color: #4ca7f5" v-if="item.status == 1"><FileTextOutlined /></div>
                </div>
              </div>
            </div>
          </div>
        </template>
      </TrainLeft>
    </div>
  </div>
</template>

<script>
export default {
  name: 'Issue'
}
</script>

<script setup>
import { FileTextOutlined } from '@ant-design/icons-vue'
import { ref, onMounted, onUnmounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import prev from '../../../../../assets/HJ/postTrain/prev.png'
import next from '../../../../../assets/HJ/postTrain/next.png'
import { getPostTelegramTrainById } from '../../../../../common/api/TelegramApi.js'
import { partTimeFormatInfo, sum } from '../../../../../common/utils/Utils.js'
import { PubSub } from '../../../../../common/utils/PubSub.js'

import textBg from '../../../../../assets/HJ/postTrain/machineTextBg.png'
import reset from '../../../../../assets/HJ/postTrain/reset.png'

import interfere from '../../../../../assets/HJ/train/interfere.png'
import issue from '../js/Issue'
import lineNotify from '../js'
import useMorse from '../../../../../common/mixin/useMorse.js'
import TrainResult from '../../disturbCode/TrainResult.vue'
import { message, Modal } from 'ant-design-vue'
import iconImage from "../../../postJob/js/iconImage";
const {topBg,labNum} = iconImage()

const route = useRoute()
const loading = ref(true)
const minSpeed = ref(60)
const showTipSymbol = ref(true)
const roomValue = ref('280px')
const roomState = ref(true)
const printShow = ref(false)
const url = window.fileUrl
const trainTimeRef = ref(0)
const userInfo = JSON.parse(window.localStorage.getItem('userInfo'))

const { morseCode } = useMorse()
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
  type: 1,
  telegraph: {
    prev: 1, //分页
    preCurr: [],
    curr: [],
    nextCurr: [],
    next: 0, //数据ID
    allPage: 0
  },
  allList: [],
  patCodeLog: [], // 实时的电码拍发记录集合
  currPatKeyIndex: -1,
  userStatus: 0,
  userSocketStatus: 0,
  duration: 0,
  speed: 0,
  currentUserChannel: null
})

//初始化函数
const {
  lssuedText,
  sendLessudText,
  getRoomDetails,
  sendUserList,
  putAwayUserList,
  sendLessudTextList,
  buttonState,
  disturbVolume,
  checkedDisturb,
  disturbList,
  handleStartTrain,
  handleBeginTrain,
  handleEndTrain,
  overBulletin,
  changeDisturbInfo,
  switchTelegram,
  activePutAway,
  activeSend,
  handleActivePutAway,
  handlePageTurn,
  a,
  all,
  curr
} = issue(trainData, trainTimeRef)
</script>

<style lang="less" scoped>
@import '../js/handkey.css';
/deep/.basicExercise {
  height: 100%!important;
}
/deep/.trainTime {
  display: none!important;
}
.LJ{
  .startBtn {
    width: 200px;
    height: 60px;
    display: flex;
    align-items: center;
    justify-content: center;
    position: absolute;
    top: calc(50% - 60px);
    left: calc(50% - 100px);
    font-size: 24px;
    font-weight: bolder;
    background-image: linear-gradient(to right, #405c53 0%, #c2dad8 50%, #2a504e 100%) !important;
    text-transform: uppercase;
    background-size: 300% auto;
    border: 1px solid #cac1a6!important;
    color: #03fc5e!important;
    transition: all 1s;
    cursor: pointer;
    &:hover {
      background-position: right center;
      color: #ffffff!important;
      box-shadow: inset 0px 0px 5px #cac1a6!important;
      border: 1px solid #bbb193!important;
    }
  }
}
.trainLeft,.trainRight{
  width: 280px;
  flex-shrink: 0;
  height: 100%;
  box-shadow: 0px 0px 6px 2px rgb(255 255 255 / 20%) inset !important;
  border-radius: 13px 13px 0 0px!important;
}
/*glint-33b5702e 1s linear infinite*/
.curr_change {
  color: #70c9ff;
  animation: glint 3s infinite;
}
@keyframes glint {
  0% {
    -webkit-box-shadow: inset 0 0 4px rgba(233, 222, 178, 0.8);
    box-shadow: inset 0 0 4px rgba(233, 222, 178, 0.8);
  }
  25% {
    -webkit-box-shadow: inset 0 0 8px rgba(233, 222, 178, 0.8);
    box-shadow: inset 0 0 8px rgba(233, 222, 178, 0.8);
  }
  50% {
    -webkit-box-shadow: inset 0 0 16px rgba(233, 222, 178, 0.8);
    box-shadow: inset 0 0 16px rgba(233, 222, 178, 0.8);
  }
  75% {
    -webkit-box-shadow: inset 0 0 8px rgba(233, 222, 178, 0.8);
    box-shadow: inset 0 0 8px rgba(233, 222, 178, 0.8);
  }
  100% {
    -webkit-box-shadow: inset 0 0 4px rgba(233, 222, 178, 0.8);
    box-shadow: inset 0 0 4px rgba(233, 222, 178, 0.8);
  }
}
.wrapper {
  display: flex;
  flex-direction: row;
  width: 100%;
  height: 100%;
  position: relative;
}
.room_box {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
  transition: all 0.2s linear;
  .user_list_box {
    height: calc(100% - 156px);
    overflow: auto;
    .user_list {
      display: flex;
      flex-direction: column;
      .send_list {
        display: flex;
        flex-direction: column;
        .send {
          font-size: 14px;
          line-height: 30px;
          padding-left: 10px;
          background: #272f39;
        }
        .pd_user_info {
          display: flex;
          flex-direction: row;
          align-items: center;
          position: relative;
          padding: 5px 10px;
          .image {
            width: 35px;
            height: 35px;
            border-radius: 35px;
            overflow: hidden;
            margin-right: 10px;
            img {
              width: 100%;
              height: 100%;
              margin-right: 10px;
            }
          }
          .state {
            position: absolute;
            right: 13px;
          }
          .room_state {
            position: absolute;
            right: 55px;
            top: 9px;
          }
        }
        .active {
          background: #213141;
        }
      }
    }
  }
  .lssued_box {
    display: flex;
    flex-direction: column;
    .row {
      display: flex;
      flex-direction: row;
      height: 75px;
      padding: 5px;
      .lessud_but {
        height: 100%;
        margin-left: 2px;
      }
    }
    .content {
      height: 99px;
      overflow: auto;
      p {
        margin: 5px;
      }
    }
  }
  .button {
    width: 100%;
    bottom: 0;
    width: 100%;
    height: 40px;
    line-height: 40px;
    text-align: center;
    background: red;
  }
  .title {
    display: flex;
    align-items: center;
    height: 40px;
    padding: 0 16px;
    font-weight: bolder;
    font-size: 16px;
    background: url('../../../../../assets/HJ/train/militaryBg.png') no-repeat center;
    background-size: 100% 100%;
  }
  .chart_content {
    flex: 1;
    height: 0;
    overflow: auto;
    padding: 0 10px;
  }
}
.trainCenter_box {
  flex: 1;
  width: 0px;
}
.create_user_box {
  flex: 1;
  position: relative;
  .startBtn {
    width: 200px;
    height: 60px;
    display: flex;
    align-items: center;
    justify-content: center;
    position: absolute;
    top: calc(50% - 60px);
    left: calc(50% - 100px);
    font-size: 24px;
    font-weight: bolder;
    background-image: linear-gradient(to right, #1f76bd 0%, #092862 50%, #8d2125 100%);
    text-transform: uppercase;
    background-size: 300% auto;
    border: 1px solid #4c7595;
    color: #c4dafb;
    transition: all 1s;
    cursor: pointer;
    &:hover {
      background-position: right center;
      color: #ffffff;
      box-shadow: inset 0px 0px 5px #cac1a6;
      border: 1px solid #bbb193;
    }
  }
  .underwayBox {
    width: 100%;
    height: 90%;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    .trainStatusText {
      font-size: 24px;
      font-weight: bolder;
      color: #fff;
    }
    .startBtn.end {
      position: static;
      background-image: linear-gradient(to right, #400e0e 0%, #af4549 50%, #e00e14 100%);
      border: 1px solid #b95a38;
      color: #fff;
      margin-top: 30px;
    }
  }
}
.disposeBox {
  width: 480px;
  height: 200px;
  background-color: #161e29;
  border: 1px solid #2a3a4b;
  margin-top: 20px;
  padding: 10px;
  color: #b4d5f0;
}
.disturbBox {
  width: 100%;
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  padding-left: 40px;
}
.volumeSlider.voice {
  width: 100%;
  padding-bottom: 12px;
  border-left: 1px solid #354971;
  border-right: 1px solid #354971;
}
.zoom_button {
  position: absolute;
  top: calc(49% - 40px);
  left: 300px;
  width: 40px;
  height: 80px;
  padding: 10px;
  background: #0a1a35;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 10;
  transition: all 0.2s linear;
}
.time_cont_box {
  padding: 10px 0;
}

</style>
