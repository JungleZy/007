<template>
  <div class="w-full h-full content-mask-bg">
    <div class="w-full h-full trainBoxs">
      <TrainLeft @StartTest="openTrainInfo" @endTest="closeTrainInfo" :trainData="trainData">
        <template v-slot:top>
          <div class="desc">
            {{ trainData.status == 0 ? '请点击下方[开始练习]按钮开启训练' : trainData.status == 1 ? '训练正在进行，当前总耗时' : trainData.status == 2 ? '本次练习已结束,总用时' : '' }}
          </div>
          <div>
            <count-down class="width-100-per layout-center" color="#70c9ff" ref="countDown" style="height: 40px" />
            <div style="font-size: 30px; height: 30px; display: flex; justify-content: center; margin-top: 20px" v-if="trainData.status == 1">
              <!--  暂停-->
              <PauseCircleOutlined v-if="!isStop" @click="stop(1)"></PauseCircleOutlined>
              <!--  继续-->
              <PlayCircleOutlined v-if="isStop" @click="stop(0)"></PlayCircleOutlined>
            </div>
          </div>
        </template>
        <template v-slot:bottom>
          <div class="userListBox">
            <div class="title">参训人员列表</div>
            <div class="userList overflow-auto">
              <div class="user" :class="[check == index ? 'check' : '']" @click="takeCheck(item, index)" v-for="(item, index) in trainData.receiveUser" :key="index">
                <div class="layout-left-center">
                  <img :src="fileUrl + item.userImg" class="avaImg" />
                  <span class="nobr ml-1">{{ item.userName }}</span>
                </div>
                <div class="layout-left-center">
                  <a-tooltip title="已上传抄收结果" v-if="item.userStatus == 1">
                    <FileTextOutlined style="color: #ff7f00; font-size: 16px; margin-right: 12px" />
                  </a-tooltip>
                  <div class="Ntag" v-if="trainData.status == 2">已结束</div>
                  <div class="Ntag" v-else-if="item.status == 0">离线</div>
                  <div class="tag" v-else-if="item.status == 1">在线</div>
                  <div class="ready" v-else-if="item.status == 2 && trainData.status == 0">已准备</div>
                  <div class="ready" v-else-if="item.status == 2 && trainData.status == 1">进行中</div>
                  <div class="Ntag" v-else></div>
                </div>
              </div>
            </div>
          </div>
        </template>
      </TrainLeft>
      <div class="trainCenter w-full overflow-hidden">
        <div class="layout-center" style="font-size: 24px; font-weight: bolder">{{ trainData.name }}</div>
        <div class="w-full" style="height: calc(100% - 40px)">
          <div class="patTelegraphBox" v-if="check == null">
            <div class="telegrapHead">
              <div>页码：【{{ trainData.currPag }}/{{ trainData.pag }}】</div>
              <div class="page">
                <div :class="{ pag: true, disabled: trainData.currPag == 1 }" @click="pageTurn('prev')">上一页</div>
                <div :class="{ pag: true, disabled: trainData.currPag == trainData.pag }" @click="pageTurn('next')">下一页</div>
              </div>
            </div>
            <div class="patTelegraph" style="height: calc(100% - 40px)">
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
                  <template v-for="(item, index) in trainData.content" :key="index">
                    <div :class="{ key: true, curr: trainData.status == 1 && trainData.currIndex == index }">
                      {{ item.key }}
                    </div>
                  </template>
                  <template v-if="trainData.content && trainData.content.length % 100 > 0">
                    <div class="key" v-for="(key, index) in 100 - (trainData.content.length % 100)" :key="index"></div>
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
          </div>
          <TrainResult class="patTelegraphBox" :result="result.res[result.curr + '']" :curr="result.curr" :all="result.existPage" @switchPage="modelPageTurn" v-if="check != null && result.user.userStatus == 1" />
          <div class="layout-center h-full w-full" v-if="check != null && result.user.userStatus != 1" style="font-size: 40px">该人员尚未上传答案!</div>
        </div>
      </div>
      <div class="playTipsBox" v-if="trainData.status == 1&&maskShow">
        <div class="tipCard" style="top: calc(50% - 200px)">
          <div class="title" style="padding: 60px 40px 20px">欢迎回来</div>
          <div class="roadItem" style="padding-top: 60px">
            <div class="roadBtn" @click="continuePlay(0)">重新播报</div>
            <div class="roadBtn ml-5" @click="continuePlay(1)">继续播报</div>
          </div>
          <div style="font-size: 14px;text-align: center;color: #f6bd70">本次弹框按钮只会改变本机播放报底，学员端只下发开始指令！</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'BroadcastTeachTrain'
}
</script>
<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { PauseCircleFilled, PlayCircleFilled, StopFilled, CheckCircleFilled, PauseCircleOutlined, PlayCircleOutlined, FileTextOutlined } from '@ant-design/icons-vue'
import CountDown from '../../components/common/CountDown.vue'
import TrainResult from '../../views/manage/unionJob/disturbCode/TrainResult.vue'

import useBroadTeacher from './js/useBroadTeacher'
const fileUrl = ref(window.fileUrl)
const countDown = ref(null)
const { trainTimeRef, trainData,maskShow, openTrainInfo,continuePlay, closeTrainInfo, pageTurn, stop, isStop, check, takeCheck, result, allBaoWen, modelPageTurn } = useBroadTeacher(countDown)
</script>

<style lang="less" scoped>
.check {
  background-color: #243443;
}
.trainBoxs {
  display: flex;
  .userListBox {
    height: calc(100% - 60px);
    margin-bottom: 10px;
    .title {
      display: flex;
      align-items: center;
      height: 40px;
      padding: 0 16px;
      font-weight: bolder;
      font-size: 16px;
      background: url('../../assets/HJ/train/militaryBg.png') no-repeat center;
      background-size: 100% 100%;
    }
    .userList {
      height: calc(100% - 40px);
      padding: 8px;
      .user {
        height: 36px;
        display: flex;
        align-items: center;
        justify-content: space-between;
        padding: 0 8px;
        margin-bottom: 6px;
        &:hover {
          background-color: #243443;
        }

        .avaImg {
          width: 26px;
          height: 26px;
          border-radius: 50%;
          margin-right: 10px;
        }
        .tag {
          font-weight: bolder;
          color: #00a1ff;
        }
        .ready {
          font-weight: bolder;
          color: #2fff00;
        }
        .Ntag {
          font-weight: bolder;
          color: #c70000;
        }
      }
    }
  }
  .trainCenter {
    padding: 10px;
    .patTelegraphBox {
      width: 100%;
      height: 100%;
      min-height: 420px;
      margin: 0 auto;
      .telegrapHead {
        height: 40px;
        display: flex;
        align-items: center;
        justify-content: space-between;
        padding: 0 36px 10px 16px;
        .page {
          height: 30px;
          display: flex;
          align-items: center;
          flex-shrink: 0;
          .pag {
            height: 26px;
            display: flex;
            align-items: center;
            justify-content: center;
            color: #4c7595;
            font-size: 13px;
            position: relative;
            padding: 0 20px;
            border: 1px solid #4c7595;
            cursor: pointer;
            &:hover {
              background-color: #4c7595;
              color: #fff;
            }
            &.disabled {
              opacity: 0.4;
              cursor: no-drop;
              &:hover {
                background-color: transparent;
                color: #4c7595;
              }
            }
            & + .pag {
              margin-left: 10px;
            }
          }
        }
      }
      .patTelegraph {
        width: 100%;
        display: flex;
        .serial {
          width: 30px;
          margin-left: 6px;
          display: flex;
          flex-direction: column;
          color: #8eafca;
          align-content: stretch;
          .ser {
            height: calc((100% - 36px) / 10);
            flex-grow: 1;
            flex-shrink: 0;
            display: flex;
            align-items: center;
            &.head {
              height: 36px;
              flex-grow: 1;
              flex-shrink: 0;
              display: flex;
              align-items: center;
            }
          }
        }
      }
    }
    .disposeBox {
      height: 360px;
      padding-top: 10px;
      display: flex;
      .groupBoxs {
        position: relative;
        border: 1px solid #3d586f;
        margin-top: 16px;
        padding-top: 10px;
        min-height: 120px;
        height: calc(50% - 16px);
        .groupTitle {
          font-size: 13px;
          color: #bbcdef;
          line-height: 20px;
          padding: 0 10px;
          background-color: #2e4559;
          position: absolute;
          left: 10px;
          top: -10px;
        }
        .rowItem {
          padding: 4px 0;
          display: flex;
          align-items: center;
          .lab {
            width: 78px;
            flex-shrink: 0;
            color: #7b90af;
            text-align: center;
          }
          .oper {
            width: 60px;
            flex-shrink: 0;
            color: #7b90af;
            display: flex;
            justify-content: center;
            .ico {
              font-size: 16px;
              cursor: pointer;
              & + .ico {
                margin-left: 6px;
              }
            }
          }
          .item {
            font-size: 12px;
            color: #7b90af;
            text-align: center;
            padding: 0 8px;
          }
          &.zhu {
            .item:nth-of-type(2),
            .item:nth-of-type(4) {
              width: calc((100% - 138px) * 0.3);
            }
            .item:nth-of-type(3) {
              width: calc((100% - 138px) * 0.4);
            }
          }
          &.code {
            .lab {
              width: 90px;
            }
            .item:nth-of-type(2),
            .item:nth-of-type(4) {
              width: calc((100% - 150px) * 0.3);
            }
            .item:nth-of-type(3) {
              width: calc((100% - 150px) * 0.4);
            }
          }
        }
      }
      .disposeItem {
        width: 50%;
        flex-shrink: 0;
        padding-right: 10px;
        height: 100%;
        & + .disposeItem {
          padding-left: 10px;
          padding-right: 0;
        }
      }
    }
  }
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

.HJ,.HJJ{
  .playTipsBox {
    width: 100%;
    height: 100%;
    background: rgba(0, 0, 0, 0.3);
    position: absolute;
    top: 0;
    left: 0;
  }
  .tipCard {
    width: 700px;
    height: 400px;
    background: url('../../assets/HJ/postTrain/tipsBg.png') no-repeat center;
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
      padding: 0 40px 30px;
      display: flex;
      justify-content: center;
      align-items: center;
      .roadBtn {
        width: 154px;
        height: 50px;
        background: url('../../assets/HJ/postTrain/receiveBtn.png') no-repeat center;
        display: flex;
        align-items: center;
        justify-content: center;
        font-size: 17px;
        padding-bottom: 6px;
        cursor: pointer;
        &.on,
        &:hover {
          background: url('../../assets/HJ/postTrain/receiveBtnOn.png') no-repeat center;
          color: #fff;
        }
        & + .desc {
          height: auto;
          padding-top: 0;
        }
      }
      .desc {
        line-height: 40px;
        font-size: 18px;
        padding-left: 50px;
        display: flex;
        width: 150px;
        &.on {
          color: #ff7f00;
        }
        .num {
          font-weight: bolder;
          font-size: 24px;
          margin-right: 10px;
        }
      }
    }
  }
}
.LJ{
  .playTipsBox {
    width: 100%;
    height: 100%;
    background: rgba(0, 0, 0, 0.3);
    position: absolute;
    top: 0;
    left: 0;
  }
  .tipCard {
    width: 700px;
    height: 400px;
    background: url('../../assets/LJ/postTrain/tipsBg.png') no-repeat center;
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
      padding: 0 40px 30px;
      display: flex;
      justify-content: center;
      align-items: center;
      .roadBtn {
        width: 154px;
        height: 50px;
        background: url('../../assets/LJ/postTrain/receiveBtn.png') no-repeat center;
        display: flex;
        align-items: center;
        justify-content: center;
        font-size: 17px;
        padding-bottom: 6px;
        cursor: pointer;
        &.on,
        &:hover {
          background: url('../../assets/LJ/postTrain/receiveBtnOn.png') no-repeat center;
          color: #fff;
        }
        & + .desc {
          height: auto;
          padding-top: 0;
        }
      }
      .desc {
        line-height: 40px;
        font-size: 18px;
        padding-left: 50px;
        display: flex;
        width: 150px;
        &.on {
          color: #ff7f00;
        }
        .num {
          font-weight: bolder;
          font-size: 24px;
          margin-right: 10px;
        }
      }
    }
  }
}
.KJ{
  .playTipsBox {
    width: 100%;
    height: 100%;
    background: rgba(0, 0, 0, 0.3);
    position: absolute;
    top: 0;
    left: 0;
  }
  .tipCard {
    width: 700px;
    height: 400px;
    background: url('../../assets/KJ/postTrain/tipsBg.png') no-repeat center;
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
      padding: 0 40px 30px;
      display: flex;
      justify-content: center;
      align-items: center;
      .roadBtn {
        width: 154px;
        height: 50px;
        background: url('../../assets/KJ/postTrain/receiveBtn.png') no-repeat center;
        display: flex;
        align-items: center;
        justify-content: center;
        font-size: 17px;
        padding-bottom: 6px;
        cursor: pointer;
        &.on,
        &:hover {
          background: url('../../assets/KJ/postTrain/receiveBtnOn.png') no-repeat center;
          color: #fff;
        }
        & + .desc {
          height: auto;
          padding-top: 0;
        }
      }
      .desc {
        line-height: 40px;
        font-size: 18px;
        padding-left: 50px;
        display: flex;
        width: 150px;
        &.on {
          color: #ff7f00;
        }
        .num {
          font-weight: bolder;
          font-size: 24px;
          margin-right: 10px;
        }
      }
    }
  }
}
</style>

