<template>
  <div class="w-full h-full content-mask-bg">
    <div class="w-full h-full trainBoxs layout-center">
      <div class="item_group btn addButton" @click="readyForTest" v-if="trainData.status == 0 && !isOnline">点击准备</div>
      <div class="tipHead" v-if="trainData.status == 1 || trainData.status == 0">
        <strong class="layout-center" style="font-size: 20px">{{ trainData.name }}</strong>
        <div class="desc">
          <div class="dis" v-if="trainData.bdType == 1">报底：<strong>平均报</strong></div>
          <div class="dis">
            类型：
            <strong style="font-size: 15px">{{ trainData.bwType == 1 ? '数字【短码】' : trainData.bwType == 2 ? '数字【长码】' : trainData.bwType == 3 ? '字码报' : trainData.bwType == 4 ? '混合报' : '' }}</strong>
          </div>
          <div class="dis">
            组数：
            <strong>{{ trainData.bwCount }}</strong>
          </div>
          <div class="dis">
            码率： <strong>{{ trainData.mainSignal }}</strong>
            <div class="unit">码/分</div>
          </div>
          <template v-if="trainData.status == 0">
            <div class="dis">
              状态：
              <strong style="color: red; font-size: 15px" v-if="isOnline">已准备</strong>
              <strong style="color: green; font-size: 15px" v-else>未准备</strong>
            </div>
          </template>
          <div class="dis" v-if="trainData.status == 1 && storage.status == 0">状态：<strong style="color: red; font-size: 15px">已暂停</strong></div>
        </div>
      </div>
      <div class="playTipsBox" v-if="trainData.status == 1 && storage.visible">
        <div class="tipCard" style="top: calc(50% - 200px)">
          <div class="title" style="padding: 60px 40px 20px">欢迎{{ newUser ? '进来' : '回来' }}【{{ userInfo.userName }}】</div>
          <div class="desc">
            <div class="dis" v-if="storage.totalTime > 0">
              您已抄收：<strong>{{ partTimeFormatInfo(parseInt(storage.totalTime * 1000), 'chinese') }}</strong>
            </div>
          </div>
          <div class="roadItem" style="padding-top: 60px">
            <div class="roadBtn" @click="againPlayCode(0)"> 重新抄收</div>
            <div class="roadBtn ml-5" @click="againPlayCode(1)">继续抄收</div>
          </div>
        </div>
      </div>
      <div class="tipText" v-if="trainData.status == 1 || trainData.status == 0">
        <div class="textRow">
          <img :src="text1" alt="" />
          <img :src="text2" alt="" />
          <img :src="text3" alt="" />
          <img :src="text4" alt="" />
        </div>
      </div>
      <div class="w-full h-full trainBoxs" v-else>
        <div class="TopBox">{{ trainData.name }} - {{ step == 1 ? '训练结束，填写您的答案：' : '训练完成查看您的答案' }}</div>
        <div class="w-full" style="height: calc(100% - 40px)" v-if="trainData.status == 2">
          <TrainResult class="trainCenter" :result="allBaoWen[trainData.currPag + '']" :curr="trainData.currPag" :all="trainData.pag" @switchPage="pageTurn" v-if="step == 2"></TrainResult>
          <FillInResult v-else @result="fillInTrainResult"></FillInResult>
        </div>
      </div>
    </div>
  </div>
</template>
<script></script>
<script setup>
import text1 from '../../assets/HJ/union/text-1.png'
import text2 from '../../assets/HJ/union/text-2.png'
import text3 from '../../assets/HJ/union/text-3.png'
import text4 from '../../assets/HJ/union/text-4.png'
import FillInResult from '../../views/manage/unionJob/disturbCode/FillInResult.vue'
import TrainResult from '../../views/manage/unionJob/disturbCode/TrainResult.vue'
import useBroadStudent from './js/useBroadStudent'
import { partTimeFormatInfo } from '../../common/utils/Utils'
import { ref } from 'vue'

const userInfo = ref(JSON.parse(window.localStorage.getItem('userInfo')))
const { trainData, isOnline, readyForTest, fillInTrainResult, step, result, storage, newUser, againPlayCode, allBaoWen, pageTurn } = useBroadStudent()
</script>
<style lang="less" scoped>
  .HJJ{
    .trainBoxs {
      background: url('../../assets/HJJ/receive/receiveBg.png') center !important;
      background-size: 100% 100%;
      position: relative;
    }
  }
.HJ,.HJJ{
  .addButton {
    position: absolute;
    top: 180px;
  }
  .playTipsBox {
    width: 100%;
    height: 100%;
    background: rgba(0, 0, 0, 0.3);
    position: absolute;
    top: 0;
    left: 0;
  }
  .trainBoxs {
    background: url('../../assets/HJ/receive/receiveBg.png');
    background-size: 100% 100%;
    position: relative;
  }
  .tipHead {
    width: 760px;
    display: flex;
    align-items: center;
    justify-content: center;
    flex-direction: column;
    background: rgba(30, 49, 64, 0.5);
    padding: 10px;
    position: absolute;
    top: 40px;
    left: calc(50% - 380px);
    .tit {
      font-size: 30px;
      padding-bottom: 20px;
      strong {
        color: #ff7f00;
      }
    }
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
      & + .dis {
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
  .tipText {
    width: 90%;
    position: absolute;
    left: 5%;
    bottom: 2%;
    .textRow {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 16px;
      img {
        max-width: 20%;
      }
    }
  }
  .trainBoxs {
    display: flex;
    flex-direction: column;
    .TopBox {
      height: 40px;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 30px;
    }
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
        /*height: calc(100% - 360px);*/
        height: 100%;
        /*max-width: 960px;*/
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
          .telegraph {
            width: 100%;
            height: 100%;
            border: 1px solid #171e27;
            background-color: #213141;
            display: flex;
            flex-direction: column;
            .key {
              font-size: 18px;
              width: 10%;
              height: 10%;
              display: flex;
              align-items: center;
              justify-content: center;
              border-right: 1px solid #171e27;
              &:nth-of-type(10n) {
                border-right: none;
              }
              &:nth-of-type(n + 10) {
                border-top: 1px solid #171e27;
              }
              &.curr {
                color: #e9deb2;
                animation: glint 1s linear infinite;
              }
            }
            .rowHead {
              width: 100%;
              height: 36px;
              display: flex;
              flex-shrink: 0;
              .key {
                color: #161e29;
                height: 100%;
                font-weight: bold;
                font-size: 18px;
                background-color: #4c7595;
                border: none;
              }
            }
            .keyBox {
              display: flex;
              flex-wrap: wrap;
              height: 100%;
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
          border: 1px solid #38556d;
          margin-top: 16px;
          padding-top: 10px;
          min-height: 120px;
          height: calc(50% - 16px);
          .groupTitle {
            font-size: 13px;
            color: #bbcdef;
            line-height: 20px;
            padding: 0 10px;
            background-color: #38556d;
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
}
  .LJ{
    .addButton {
      position: absolute;
      top: 180px;
    }
    .playTipsBox {
      width: 100%;
      height: 100%;
      background: rgba(0, 0, 0, 0.3);
      position: absolute;
      top: 0;
      left: 0;
    }
    .trainBoxs {
      background: url('../../assets/LJ/receive/receiveBg.png');
      background-size: 100% 100%;
      position: relative;
    }
    .tipHead {
      width: 760px;
      display: flex;
      align-items: center;
      justify-content: center;
      flex-direction: column;
      background: rgba(30, 49, 64, 0.5);
      padding: 10px;
      position: absolute;
      top: 40px;
      left: calc(50% - 380px);
      .tit {
        font-size: 30px;
        padding-bottom: 20px;
        strong {
          color: #ff7f00;
        }
      }
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
        & + .dis {
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
    .tipText {
      width: 90%;
      position: absolute;
      left: 5%;
      bottom: 2%;
      .textRow {
        display: flex;
        align-items: center;
        justify-content: space-between;
        padding: 16px;
        img {
          max-width: 20%;
        }
      }
    }
    .trainBoxs {
      display: flex;
      flex-direction: column;
      .TopBox {
        height: 40px;
        display: flex;
        align-items: center;
        justify-content: center;
        font-size: 30px;
      }
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
          background: url('../../assets/LJ/train/militaryBg.png') no-repeat center;
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
          /*height: calc(100% - 360px);*/
          height: 100%;
          /*max-width: 960px;*/
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
            .telegraph {
              width: 100%;
              height: 100%;
              border: 1px solid #171e27;
              background-color: #213141;
              display: flex;
              flex-direction: column;
              .key {
                font-size: 18px;
                width: 10%;
                height: 10%;
                display: flex;
                align-items: center;
                justify-content: center;
                border-right: 1px solid #171e27;
                &:nth-of-type(10n) {
                  border-right: none;
                }
                &:nth-of-type(n + 10) {
                  border-top: 1px solid #171e27;
                }
                &.curr {
                  color: #e9deb2;
                  animation: glint 1s linear infinite;
                }
              }
              .rowHead {
                width: 100%;
                height: 36px;
                display: flex;
                flex-shrink: 0;
                .key {
                  color: #161e29;
                  height: 100%;
                  font-weight: bold;
                  font-size: 18px;
                  background-color: #4c7595;
                  border: none;
                }
              }
              .keyBox {
                display: flex;
                flex-wrap: wrap;
                height: 100%;
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
            border: 1px solid #38556d;
            margin-top: 16px;
            padding-top: 10px;
            min-height: 120px;
            height: calc(50% - 16px);
            .groupTitle {
              font-size: 13px;
              color: #bbcdef;
              line-height: 20px;
              padding: 0 10px;
              background-color: #38556d;
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
                color: #a9abaa;
                text-align: center;
              }
              .oper {
                width: 60px;
                flex-shrink: 0;
                color: #a9abaa;
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
                color: #a9abaa;
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
  }
  .KJ{
    .addButton {
      position: absolute;
      top: 180px;
    }
    .playTipsBox {
      width: 100%;
      height: 100%;
      background: rgba(0, 0, 0, 0.3);
      position: absolute;
      top: 0;
      left: 0;
    }
    .trainBoxs {
      background: url('../../assets/KJ/receive/receiveBg.png');
      background-size: 100% 100%;
      position: relative;
    }
    .tipHead {
      width: 760px;
      display: flex;
      align-items: center;
      justify-content: center;
      flex-direction: column;
      background: rgba(30, 49, 64, 0.5);
      padding: 10px;
      position: absolute;
      top: 40px;
      left: calc(50% - 380px);
      .tit {
        font-size: 30px;
        padding-bottom: 20px;
        strong {
          color: #ff7f00;
        }
      }
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
        & + .dis {
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
    .tipText {
      width: 90%;
      position: absolute;
      left: 5%;
      bottom: 2%;
      .textRow {
        display: flex;
        align-items: center;
        justify-content: space-between;
        padding: 16px;
        img {
          max-width: 20%;
        }
      }
    }
    .trainBoxs {
      display: flex;
      flex-direction: column;
      .TopBox {
        height: 40px;
        display: flex;
        align-items: center;
        justify-content: center;
        font-size: 30px;
      }
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
          background: url('../../assets/KJ/train/militaryBg.png') no-repeat center;
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
          /*height: calc(100% - 360px);*/
          height: 100%;
          /*max-width: 960px;*/
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
            .telegraph {
              width: 100%;
              height: 100%;
              border: 1px solid #171e27;
              background-color: #213141;
              display: flex;
              flex-direction: column;
              .key {
                font-size: 18px;
                width: 10%;
                height: 10%;
                display: flex;
                align-items: center;
                justify-content: center;
                border-right: 1px solid #171e27;
                &:nth-of-type(10n) {
                  border-right: none;
                }
                &:nth-of-type(n + 10) {
                  border-top: 1px solid #171e27;
                }
                &.curr {
                  color: #e9deb2;
                  animation: glint 1s linear infinite;
                }
              }
              .rowHead {
                width: 100%;
                height: 36px;
                display: flex;
                flex-shrink: 0;
                .key {
                  color: #161e29;
                  height: 100%;
                  font-weight: bold;
                  font-size: 18px;
                  background-color: #4c7595;
                  border: none;
                }
              }
              .keyBox {
                display: flex;
                flex-wrap: wrap;
                height: 100%;
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
            border: 1px solid #38556d;
            margin-top: 16px;
            padding-top: 10px;
            min-height: 120px;
            height: calc(50% - 16px);
            .groupTitle {
              font-size: 13px;
              color: #bbcdef;
              line-height: 20px;
              padding: 0 10px;
              background-color: #38556d;
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
                color: #a9abaa;
                text-align: center;
              }
              .oper {
                width: 60px;
                flex-shrink: 0;
                color: #a9abaa;
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
                color: #a9abaa;
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
  }
</style>
