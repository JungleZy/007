<template>
  <div class="w-full h-full overflow-hidden content-mask-bg p-2" style="position: relative;">
    <CutDown :nowTime="nowTime"></CutDown>
    <div class="w-full overflow-hidden layout-side" style="height: 100%">
      <div class="h-full" style="width: 211px;position: relative;padding-right: 10px;border-right: 1px solid #263757">
        <div :title="data.knowledge.title" class="listTitle fs_dispose" style="position: sticky; z-index: 11">
          {{ data.knowledge.title }}
          <img class="line" :src="linetp">
        </div>
        <div class="overflow-auto" style="height: calc(100% - 52px); width: 100%; display: flex; flex-direction: column; align-items: center; box-shadow: -7px 0px 7px -7px rgba(255, 255, 255, 0.09) inset; padding-top: 10px">
          <div class="list cursor-pointer-def"  v-for="(s,index) in data.knowledgeSwfs" :class="[Knowledge==index? 'listcheck ':'']"  @click="checkKnowledge(s,index,true)">
            <div class="timer layout-side">
              <!--            :style="{backgroundImage:'url('+fileUrl+(s.cover?s.cover:'/006/cover/base.jpg')+')'}"-->
              <div class="time">{{s.record?s.record>60?(s.record/60).toFixed(2)+' H':s.record.toFixed(0)+' M':'0 M'}}</div>
              <div class="triangle"></div>
            </div>
            <div class="listLabel pl-1 fs_dispose" >
              {{ s.title }}
            </div>
          </div>
        </div>
      </div>
      <div class="h-full overflow-auto layout-cente" style="width: calc(100% - 211px)">
        <div class="penInfo layout-center cursor-pointer-def w-full h-full" v-if="checkItem.haveTest" @click="showTest">
          <!--              <div v-waves style="width: 100%;height: 100%;color: #ffffff" class="layout-center cursor-pointer-def">-->
          <!--                <img :src="icoPen" class="mr-1"/>-->
          <!--                {{score==100?'已完成':'随堂测验'}}-->
          <!--              </div>-->
        </div>
        <div class="w-full layout-center" style="height: 80px; position: relative">
          <div class="w-full layout-center text-2xl font-extrabold tracking-wider" style="height: 45px">
            {{ activeTile }}
          </div>
          <div style="color: #7b90af">{{ getDayjs(data) }}</div>
        </div>
        <div class="w-full h-full overflow-auto layout-center" style="height: calc(100% - 80px)">
          <iframe id="iframeId" name="iframeId" width="100%" height="100%" frameborder="0" />
        </div>
        <div class="shadow fade-in" v-if="testVisible">
          <div class="shadowInfo">
            <div class="title">
              <!--              <div class="addClass btn item_group" @click="addClass">新增题目</div>-->
              <div class="text">随 堂 测 验</div>
            </div>
            <div class="newClosePopup" @click="takeNoTestVisible"><div class="closeIco"></div></div>
            <div class="close" @click="takeNoTestVisible"></div>
            <div class="center" :style="score !== 100 ? 'height: calc(100% - 56px - 62px);' : 'height: calc(100% - 56px);border-bottom: 1px solid #4f6675;'">
              <div class="right">
                <StudentQues v-for="(item, index) in rightValue" :key="index" :index="index" :params="item" :score="score"> </StudentQues>
                <div class="w-full h-full layout-center" v-if="rightValue.length === 0">
                  <nomore />
                </div>
              </div>
            </div>
            <div class="bottom layout-center" style="height: 62px" v-if="score !== 100">
              <div v-if="score !== 100" class="botBtn layout-center cursor-pointer-def" @click="bornTest">提交测验</div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
  export default {
    name: 'TheoryDetails'
  }
</script>
<script setup>
  import { LoadingOutlined, PlusOutlined, SaveOutlined, EditOutlined, DeleteOutlined, CloseOutlined, RollbackOutlined, CheckOutlined, ExclamationCircleOutlined } from '@ant-design/icons-vue'
  import StudentQues from '../../../../../../components/test/studentQues/StudentQues.vue'
  import nomore from '../../../../../../components/nomore/nomore.vue'
  import icoPen from '../../../../../../assets/HJ/ico/ico-pen.png'
  import { getById, getByIdAndToken } from '../../../../../../common/api/TheoryKnowledgeApi.js'
  import trainTime from '../../../../../../assets/HJ/receive/trainTime.png'
  import linetp from '../../../../../../assets/HJ/basicTheory/line.png'
  import CutDown from '../../../../../../components/cutDown/CutDown.vue'
  import dayjs from 'dayjs'

  import { useRoute } from 'vue-router'
  import { onMounted, onBeforeUnmount, ref } from 'vue'
  import useDetails from './js/useDetails.js'
  const route = useRoute()
  const data = ref({ knowledge: { title: '' }, knowledgeSwfs: [{ content: '' }] })
  const fileUrl = ref(window.fileUrl)
  let boo = true
  onMounted(() => {
    getToken()
  })
  onBeforeUnmount(() => {
    loginAndLogoutTwo(knowledgeSwfsId)
    boo = false
  })
  window.addEventListener('beforeunload', e => {
    if (boo) {
      loginAndLogoutTwo(knowledgeSwfsId)
      boo = false
    }
  })
  const { hour, min, sec, score, knowledgeSwfsId, Knowledge, checkKnowledge, checkItem, nowTime, getToken, showTest, testVisible, rightShow, rightValue, bornTest, activeTile, takeNoTestVisible, loginAndLogoutTwo } = useDetails(data)
  const getDayjs = data => {
    return dayjs(Number(data.knowledge.createTime)).format('YYYY-MM-DD HH:mm:ss')
  }
</script>

<style scoped lang="less">
  .penInfo {
    width: 77px;
    height: 77px;
    position: absolute;
    right: 12px;
    top: calc(50% - 38px);
    z-index: 9999;
    background: url('../../../../../../assets/HJ/basicTheory/stcy.png');
  }
  .trainTime {
    position: absolute;
    right: 0px;
    top: 0px;
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
  .countDown {
    height: 22px;
    z-index: 2;
    position: absolute;
    right: 0px;
    top: 0px;
    /*float: right;*/
    display: flex;
    .triangle {
      border-left: 15px solid transparent;
      border-right: 0px solid transparent;
      border-top: 22px solid #c7853e;
      border-bottom: 0px solid transparent;
    }
    .time {
      font-weight: 600;
      letter-spacing: 1px;
      background-color: #c7853e;
      height: 100%;
      color: #ffffff;
      padding: 0 5px;
      display: flex;
      align-items: center;
    }
  }
  .listTitle {
    font-size: 17px;
    font-weight: bold;
    text-align: center;
    display: flex;
    flex-direction: column;
    align-items: center;
  }
  .line{
    margin-bottom: 10px;
    width: 90%;
  }
  .timer{
    height: 20px;
    float: left;
    margin-left: -2px;
    margin-top: -5px;
    .time{
      background-color: #24508a;
      height: 100%;
      color: #ffffff;
      padding: 0 5px;
      display: flex;
      align-items: center;
    }
    .triangle{
      border-left: 10px solid #24508a;
      border-right: 0px solid transparent;
      border-top: 0px solid transparent;
      border-bottom: 20px solid transparent;
    }
  }
  .init_modal_style.footer-border-none .ant-modal-footer {
    border-top-color: transparent;
  }
  @keyframes rotate {
    0% {
      transform: rotate(0deg);
    }
    25% {
      transform: rotate(90deg);
    }
    50% {
      transform: rotate(180deg);
    }
    75% {
      transform: rotate(270deg);
    }
    100% {
      transform: rotate(360deg);
    }
  }
  .list{
    /*height: 250px;*/
    background-size: 100% 100%;
    transition: all .5s;
    margin: 0 0px 20px 0px;
    background:url("../../../../../../assets/HJ/basicTheory/details/bg.png");
    height: 70px;
    width: 200px;
    background-size: 100% 100%;
    background-repeat: no-repeat;
  }
  .listcheck{
    background:url("../../../../../../assets/HJ/basicTheory/details/bg-hover.png");
    .listLabel{
      color: white;
    }
    .timer .time{
      background-color: #a27016;
    }
    .triangle{
      border-left: 10px solid #a27016;
    }
  }
  :hover.list{
    background:url("../../../../../../assets/HJ/basicTheory/details/bg-hover.png");
  }
  :hover.list .listLabel{
    color: white;
  }
  .listLabel{
    width: 100%;
    color: #8fa3c0;
    background-size: 100% 100%;
    background-repeat: no-repeat;
    font-size: 16px;
    transition: all .5s;
    white-space: nowrap;
    text-overflow: ellipsis;
    overflow: hidden;
    height: 70px;
    line-height: 40px;
  }
  .test{
    width: 112px;
    height: 34px;
    position: absolute;
    top: 20px;
    right: 0px;
  }
  .HJ{
    .shadow{
      position: fixed;
      z-index: 1000;
      top: 0;
      left:0 ;
      width: 100%;
      height: 100%;
      background: rgba(0,0,0,.5);
      .shadowInfo{
        box-shadow: inset 0 -220px 336px -220px rgb(37 101 171 / 80%);
        position: absolute;
        margin: auto;
        top: 0;
        left: 0;
        right: 0;
        bottom: 0;
        background-color: #0e2446;
        width: 1000px;
        height: 620px;
        background-image:url("../../../../../../assets/HJ/train/modal-bg.jpg");
        background-size: 1000px 160px;
        background-repeat:no-repeat ;
        .title{
          margin: 0 auto;
          width: 338px;
          height: 56px;
          background-image:url("../../../../../../assets/HJ/train/title-bg.png");
          background-size: 338px 56px;
          background-repeat:no-repeat ;
          position: relative;
          .addClass{
            position: absolute;
            bottom: 0px;
            right: -230px;
          }
          .text{
            width: 100%;
            height: 100%;
            background-image: -webkit-linear-gradient(bottom,#44aaff,#e2f2ff);
            -webkit-background-clip: text;
            -webkit-text-fill-color: transparent;
            text-align: center;
            line-height: 56px;
            font-size: 30px;
            font-family:"Microsoft Yahei";
            font-weight: bold;
          }
        }
        .center{
          border-left: 1px solid #2d4f72;
          border-right: 1px solid #2d4f72;
          padding-top: 20px;
          display: flex;
          .left{
            position: relative;
            width: 110px;
            height: 400px;
            .border-right{
              position: absolute;
              height: 100%;
              top: 0;
              right: -5px;
              width: 10px;
            }
            .topBox{
              overflow-y: auto;
              height:calc(100% - 50px) ;
              .leftNocheck{

                .leftBorder{
                  height: 100%;
                  position: absolute;
                  left: 0;
                  top: 0;
                  width:2px;
                  background: #6ebdff;
                }
                position: relative;
                width: 100%;
                display: flex;
                align-items: center;
                justify-content: space-between;
                padding: 0 10px 0 20px;
                font-size: 15px;
                height: 37px;
                .leftText{
                  /*margin-left: 10px;*/
                }
                .bg1{
                  display: flex;
                  align-items: center;
                  justify-content: center;
                  border-radius:16px;
                  padding: 0 4px;
                  min-width:16px ;
                  height: 16px;
                  background: #354971;
                  font-size: 14px;
                }
                .bg2{
                  background: #6ebdff;
                  color: #274666;
                }
              }
              .leftCheck{
                background-image:url("../../../../../../assets/HJ/train/tabs-check.jpg");
              }
            }
            .botBox{
              display: flex;
              justify-content: center;
              align-items: center;
              height: 50px;

            }
            /*border-right: ;*/
          }
          .right{
            overflow-y: auto;
            width:calc(100%);
            padding: 0 40px;
            position: relative;

          }
        }
        .bottom{
          border-left: 1px solid #2d4f72;
          border-right: 1px solid #2d4f72;
          border-bottom: 1px solid #2d4f72;
          .botBtn{
            width: 110px;
            height: 32px;
            background-image:url("../../../../../../assets/HJ/train/button.png");
          }
        }
        .bottominfo{
          /*position: absolute;*/
          /*bottom:-100px;*/
          /*left: 0;*/
          height: 100px;
          display: flex;
          justify-content: center;
          align-items: center;
          .close{
            width: 31px;
            height: 31px;
            border: 2px solid #7b90af;
            border-radius: 50%;
            background: url("../../../../../../assets/HJ/ico/ico-clear.png") no-repeat center;
            cursor: pointer;
          }
          .close:hover{
            background: url("../../../../../../assets/HJ/ico/ico-clear-1.png") no-repeat center;
            animation: rotate 0.4s linear;
            border-color: #6ebdff;
          }
        }
        .bottominfo::before{
          content: '';
          width: 1px;
          height: 33px;
          background: url("../../../../../../assets/HJ/ico/ico-lone.png");
          position: absolute;
          top: 620px;
          left: 50%;
        }
      }
    }
  }
  .HJJ{
    .shadow {
      position: fixed;
      z-index: 1000;
      top: 0;
      left: 0;
      width: 100%;
      height: 100%;
      background: rgba(0, 0, 0, 0.5);
      .shadowInfo {
        position: absolute;
        margin: auto;
        top: 0;
        left: 0;
        right: 0;
        bottom: 0;
        width: 1000px;
        height: 620px;
        background-image: url('../../../../../../assets/HJJ/login/registerCenter.png');
        background-size: 120% 120%;
        background-position: -20px;
        background-repeat: no-repeat;
        .title {
          margin: 0 auto;
          width: 100%;
          height: 56px;
          background-image: url('../../../../../../assets/HJJ/login/popTitle.png');
          background-size: 100% 56px;
          background-repeat: no-repeat;
          position: relative;
          border-bottom: 1px solid #324b60;
          box-shadow: 0px 7px 17px -5px #1c2733;
          .addClass {
            position: absolute;
            bottom: 0px;
            right: -230px;
          }
          .text {
            width: 100%;
            height: 100%;
            color: #ffffff;
            text-align: center;
            line-height: 56px;
            font-size: 30px;
            font-family: 'Microsoft Yahei';
            font-weight: bold;
          }
        }
        .center {
          border-left: 1px solid #4f6675;
          border-right: 1px solid #4f6675;
          padding-top: 20px;
          display: flex;
          .left {
            position: relative;
            width: 110px;
            height: 400px;
            .border-right {
              position: absolute;
              height: 100%;
              top: 0;
              right: -5px;
              width: 10px;
            }
            .topBox {
              overflow-y: auto;
              height: calc(100% - 50px);
              .leftNocheck {
                .leftBorder {
                  height: 100%;
                  position: absolute;
                  left: 0;
                  top: 0;
                  width: 2px;
                  background: #6ebdff;
                }
                position: relative;
                width: 100%;
                display: flex;
                align-items: center;
                justify-content: space-between;
                padding: 0 10px 0 20px;
                font-size: 15px;
                height: 37px;
                .leftText {
                  /*margin-left: 10px;*/
                }
                .bg1 {
                  display: flex;
                  align-items: center;
                  justify-content: center;
                  border-radius: 16px;
                  padding: 0 4px;
                  min-width: 16px;
                  height: 16px;
                  background: #354971;
                  font-size: 14px;
                }
                .bg2 {
                  background: #6ebdff;
                  color: #274666;
                }
              }
              .leftCheck {
                background-image: url('../../../../../../assets/HJJ/train/tabs-check.jpg');
              }
            }
            .botBox {
              display: flex;
              justify-content: center;
              align-items: center;
              height: 50px;
            }
            /*border-right: ;*/
          }
          .right {
            overflow-y: auto;
            width: calc(100%);
            padding: 0 40px;
            position: relative;
          }
        }
        .bottom {
          border-left: 1px solid #4f6675;
          border-right: 1px solid #4f6675;
          border-bottom: 1px solid #4f6675;
          .botBtn {
            width: 110px;
            height: 32px;
            background-image: url('../../../../../../assets/HJJ/train/button.png');
          }
        }
        .bottominfo {
          /*position: absolute;*/
          /*bottom:-100px;*/
          /*left: 0;*/
          height: 100px;
          display: flex;
          justify-content: center;
          align-items: center;
          .close {
            width: 31px;
            height: 31px;
            border: 2px solid #7b90af;
            border-radius: 50%;
            background: url('../../../../../../assets/HJJ/ico/ico-clear.png') no-repeat center;
            cursor: pointer;
          }
          .close:hover {
            background: url('../../../../../../assets/HJJ/ico/ico-clear-1.png') no-repeat center;
            animation: rotate 0.4s linear;
            border-color: #6ebdff;
          }
        }
        .bottominfo::before {
          content: '';
          width: 1px;
          height: 33px;
          background: url('../../../../../../assets/HJJ/ico/ico-lone.png');
          position: absolute;
          top: 620px;
          left: 50%;
        }
      }
    }
  }
  .LJ{
    .shadow {
      position: fixed;
      z-index: 1000;
      top: 0;
      left: 0;
      width: 100%;
      height: 100%;
      background: rgba(0, 0, 0, 0.5);
      .shadowInfo {
        position: absolute;
        margin: auto;
        top: 0;
        left: 0;
        right: 0;
        bottom: 0;
        width: 1000px;
        height: 620px;
        background-image: url('../../../../../../assets/LJ/basicTheory/registerCenter.png');
        background-size: 100% 100%;
        /*background-position: -20px;*/
        background-repeat: no-repeat;
        .title {
          margin: 0 auto;
          width: 100%;
          height: 56px;
          /*background-image: url('../../../../../../assets/login/popTitle.png');*/
          /*background-size: 100% 56px;*/
          /*background-repeat: no-repeat;*/
          /*position: relative;*/

          .addClass {
            position: absolute;
            bottom: 0px;
            right: -230px;
          }
          .text {
            width: 100%;
            height: 100%;
            color: #ffffff;
            text-align: center;
            line-height: 45px;
            font-size: 24px;
            font-family: 'Microsoft Yahei';
            font-weight: bold;
          }
        }
        .center {
          border-left: 1px solid #4f6675;
          border-right: 1px solid #4f6675;
          padding-top: 20px;
          display: flex;
          .left {
            position: relative;
            width: 110px;
            height: 400px;
            .border-right {
              position: absolute;
              height: 100%;
              top: 0;
              right: -5px;
              width: 10px;
            }
            .topBox {
              overflow-y: auto;
              height: calc(100% - 50px);
              .leftNocheck {
                .leftBorder {
                  height: 100%;
                  position: absolute;
                  left: 0;
                  top: 0;
                  width: 2px;
                  background: #34b34c;
                }
                position: relative;
                width: 100%;
                display: flex;
                align-items: center;
                justify-content: space-between;
                padding: 0 10px 0 20px;
                font-size: 15px;
                height: 37px;
                .leftText {
                  /*margin-left: 10px;*/
                }
                .bg1 {
                  display: flex;
                  align-items: center;
                  justify-content: center;
                  border-radius: 16px;
                  padding: 0 4px;
                  min-width: 16px;
                  height: 16px;
                  background: #26332e;
                  font-size: 14px;
                }
                .bg2 {
                  background: #34b34c;
                  color: #274666;
                }
              }
              .leftCheck {
                background-image: url('../../../../../../assets/LJ/train/tabs-check.jpg');
              }
            }
            .botBox {
              display: flex;
              justify-content: center;
              align-items: center;
              height: 50px;
            }
            /*border-right: ;*/
          }
          .right {
            overflow-y: auto;
            width: calc(100%);
            padding: 0 40px;
            position: relative;
          }
        }
        .bottom {
          border-left: 1px solid #4f6675;
          border-right: 1px solid #4f6675;
          border-bottom: 1px solid #4f6675;
          .botBtn {
            width: 102px;
            height: 42px;
            color: white;
            font-size: 16px;
            margin-bottom: 20px;
            background: url('../../../../../../assets/LJ/train/button.png') no-repeat;
          }
        }
        .bottominfo {
          /*position: absolute;*/
          /*bottom:-100px;*/
          /*left: 0;*/
          height: 100px;
          display: flex;
          justify-content: center;
          align-items: center;
          .close {
            width: 31px;
            height: 31px;
            border: 2px solid #a9abaa;
            border-radius: 50%;
            background: url('../../../../../../assets/LJ/ico/ico-clear.png') no-repeat center;
            cursor: pointer;
          }
          .close:hover {
            background: url('../../../../../../assets/LJ/ico/ico-clear-1.png') no-repeat center;
            animation: rotate 0.4s linear;
            border-color: #34b34c;
          }
        }
        .bottominfo::before {
          content: '';
          width: 1px;
          height: 33px;
          background: url('../../../../../../assets/LJ/ico/ico-lone.png');
          position: absolute;
          top: 620px;
          left: 50%;
        }
      }
    }
  }
  .KJ{
    .shadow {
      position: fixed;
      z-index: 1000;
      top: 0;
      left: 0;
      width: 100%;
      height: 100%;
      background: rgba(0, 0, 0, 0.5);
      .shadowInfo {
        position: absolute;
        margin: auto;
        top: 0;
        left: 0;
        right: 0;
        bottom: 0;
        width: 1000px;
        height: 620px;
        background-image: url('../../../../../../assets/KJ/basicTheory/registerCenter.png');
        background-size: 100% 100%;
        /*background-position: -20px;*/
        background-repeat: no-repeat;
        .title {
          margin: 0 auto;
          width: 100%;
          height: 56px;
          /*background-image: url('../../../../../../assets/login/popTitle.png');*/
          /*background-size: 100% 56px;*/
          /*background-repeat: no-repeat;*/
          /*position: relative;*/

          .addClass {
            position: absolute;
            bottom: 0px;
            right: -230px;
          }
          .text {
            width: 100%;
            height: 100%;
            color: #ffffff;
            text-align: center;
            line-height: 45px;
            font-size: 24px;
            font-family: 'Microsoft Yahei';
            font-weight: bold;
          }
        }
        .center {
          border-left: 1px solid #4f6675;
          border-right: 1px solid #4f6675;
          padding-top: 20px;
          display: flex;
          .left {
            position: relative;
            width: 110px;
            height: 400px;
            .border-right {
              position: absolute;
              height: 100%;
              top: 0;
              right: -5px;
              width: 10px;
            }
            .topBox {
              overflow-y: auto;
              height: calc(100% - 50px);
              .leftNocheck {
                .leftBorder {
                  height: 100%;
                  position: absolute;
                  left: 0;
                  top: 0;
                  width: 2px;
                  background: #34b34c;
                }
                position: relative;
                width: 100%;
                display: flex;
                align-items: center;
                justify-content: space-between;
                padding: 0 10px 0 20px;
                font-size: 15px;
                height: 37px;
                .leftText {
                  /*margin-left: 10px;*/
                }
                .bg1 {
                  display: flex;
                  align-items: center;
                  justify-content: center;
                  border-radius: 16px;
                  padding: 0 4px;
                  min-width: 16px;
                  height: 16px;
                  background: #26332e;
                  font-size: 14px;
                }
                .bg2 {
                  background: #34b34c;
                  color: #274666;
                }
              }
              .leftCheck {
                background-image: url('../../../../../../assets/KJ/train/tabs-check.jpg');
              }
            }
            .botBox {
              display: flex;
              justify-content: center;
              align-items: center;
              height: 50px;
            }
            /*border-right: ;*/
          }
          .right {
            overflow-y: auto;
            width: calc(100%);
            padding: 0 40px;
            position: relative;
          }
        }
        .bottom {
          border-left: 1px solid #4f6675;
          border-right: 1px solid #4f6675;
          border-bottom: 1px solid #4f6675;
          .botBtn {
            width: 102px;
            height: 42px;
            color: white;
            font-size: 16px;
            margin-bottom: 20px;
            background: url('../../../../../../assets/KJ/train/button.png') no-repeat;
          }
        }
        .bottominfo {
          /*position: absolute;*/
          /*bottom:-100px;*/
          /*left: 0;*/
          height: 100px;
          display: flex;
          justify-content: center;
          align-items: center;
          .close {
            width: 31px;
            height: 31px;
            border: 2px solid #a9abaa;
            border-radius: 50%;
            background: url('../../../../../../assets/KJ/ico/ico-clear.png') no-repeat center;
            cursor: pointer;
          }
          .close:hover {
            background: url('../../../../../../assets/KJ/ico/ico-clear-1.png') no-repeat center;
            animation: rotate 0.4s linear;
            border-color: #34b34c;
          }
        }
        .bottominfo::before {
          content: '';
          width: 1px;
          height: 33px;
          background: url('../../../../../../assets/KJ/ico/ico-lone.png');
          position: absolute;
          top: 620px;
          left: 50%;
        }
      }
    }
  }
</style>
