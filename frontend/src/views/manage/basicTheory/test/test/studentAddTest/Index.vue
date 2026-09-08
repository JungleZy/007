<template>
  <div class="addTestContainer">
    <div class="right grouping">
      <div class="w-full grouping_halving_line"></div>
      <div class="fromTable">
        <div class="layout-center">请选择试卷</div>
      </div>
      <div class="w-full h-full rightBottom">
        <div class="search">
          <div class="w-full h-full" style="padding: 0 8px; width: 200px; display: flex; align-items: center">
            <div class="item_group input search" style="margin-top: 8px">
              <a-input placeholder="请输入关键字" @change="searchPaper('name')" v-model:value="searchPaperObj.name"></a-input>
              <div class="icon"></div>
            </div>
            <div class="item_group btn" style="margin-top: 7px; margin-left: 10px" @click="clearSearch">清空 <IconFont type="icon-clear" style="font-size: 16px; padding-left: 5px"></IconFont></div>
          </div>
          <div class="inputsContainer layout-right-center">
            选择知识节点：
            <a-tree-select v-model:value="searchPaperObj.levelId" style="max-width: 200px; min-width: 150px; width: calc(100% - 600px); align-self: flex-end" :treeData="KnowledgeData" treeDefaultExpandAll @select="searchPaper" />
          </div>
        </div>
        <div class="w-full overflow-y-auto" style="height: calc(100% - 90px - 8px - 8px)">
          <a-row>
            <a-col v-for="(d, i) in listData" :xs="{ span: 12, offset: 0 }" :md="{ span: 12, offset: 0 }" :lg="{ span: 12, offset: 0 }" :xl="{ span: 8, offset: 0 }" :xxl="{ span: 6, offset: 0 }">
              <div class="list relative" @click="selectPaper(d)" :class="[selectedPaper === d.id ? 'listActive' : '']">
                <div class="absolute listBg" style="z-index: 2; top: 1px; left: 1px; right: 1px; bottom: 1px">
<!--                  <div class="theLabel" @click.stop="previewModel(d)">-->
<!--                    <IconFont type="icon-yulan1" style="font-size: 20px;color: #dd994e"></IconFont>-->
<!--                    <span style="margin-left: 5px;color: #dd994e" >预览</span>-->
<!--                  </div>-->
                  <img class="imgs" :src="llcy" alt="" />
                  <div class="listLabel" :title="d.name"><span class="listLine"></span>{{ d.name }}</div>
                  <div class="listImg relative" :class="[selectedPaper === d.id ? 'activeList' : '']">
                    <div style="margin-left: 30px; font-size: 13px; color: #bfcde0">
                      <div>题目数量：{{ d['completion'].length + d.judge.length + d.multipleChoice.length + d.singleChoice.length + d.shortAnswer.length }}</div>
                      <div>总分：{{ d.total }}</div>
                    </div>
                    <div
                      class="theTitle"
                      :style="{ paddingTop: 40 - fs * 8 + 'px' }"
                    >
                      <div class="theTitleItem">
                        <div
                          style="color: #63c1ff; font-weight: bold"
                          :style="{ fontSize: fs * 2 + 18 + 'px' }"
                        >
                          {{ d.singleChoice.length }}
                        </div>
                        <div
                          style="color: #a5b6d0"
                          :style="{ fontSize: fs + 13 + 'px' }"
                        >
                          单选题
                        </div>
                        <IconFont
                          type="icon-xuanzeti1"
                          style="font-size: 30px; color: #6ebdff"
                        ></IconFont>
                      </div>
                      <div class="theTitleItem">
                        <div
                          style="color: #63c1ff; font-weight: bold"
                          :style="{ fontSize: fs * 2 + 18 + 'px' }"
                        >
                          {{ d.multipleChoice.length }}
                        </div>
                        <div
                          style="color: #a5b6d0"
                          :style="{ fontSize: fs + 13 + 'px' }"
                        >
                          多选题
                        </div>
                        <IconFont
                          type="icon-duoxuan1"
                          style="font-size: 30px; color: #6ebdff"
                        ></IconFont>
                      </div>
                      <div class="theTitleItem">
                        <div
                          style="color: #63c1ff; font-weight: bold"
                          :style="{ fontSize: fs * 2 + 18 + 'px' }"
                        >
                          {{ d.judge.length }}
                        </div>
                        <div
                          style="color: #a5b6d0"
                          :style="{ fontSize: fs + 13 + 'px' }"
                        >
                          判断题
                        </div>
                        <IconFont
                          type="icon-panduanti1"
                          style="font-size: 30px; color: #6ebdff"
                        ></IconFont>
                      </div>
                      <div class="theTitleItem">
                        <div
                          style="color: #63c1ff; font-weight: bold"
                          :style="{ fontSize: fs * 2 + 18 + 'px' }"
                        >
                          {{ d['completion'].length }}
                        </div>
                        <div
                          style="color: #a5b6d0"
                          :style="{ fontSize: fs + 13 + 'px' }"
                        >
                          填空题
                        </div>
                        <IconFont
                          type="icon-tiankongti1"
                          style="font-size: 30px; color: #6ebdff"
                        ></IconFont>
                      </div>
                      <div class="theTitleItem">
                        <div style="color: #63c1ff;font-weight: bold" :style="{fontSize: (fs * 2 + 18) + 'px'}">
                          {{d.shortAnswer.length}}
                        </div>
                        <div style="color: #a5b6d0" :style="{fontSize: (fs * 1 + 13) + 'px'}">
                          简答题
                        </div>
                        <IconFont type="icon-jiandati1" style="font-size: 30px;color: #6ebdff"></IconFont>
                      </div>
                    </div>
                  </div>
                  <div class="trangle" v-if="selectedPaper === d.id"></div>
                  <div class="icon" v-if="selectedPaper === d.id">
                    <IconFont type="icon-gou1" style="font-size: 20px; color: #06b60b"></IconFont>
                  </div>
                </div>
              </div>
            </a-col>
          </a-row>
        </div>
      </div>
    </div>
    <div class="shadowFind fade-in" v-if="preview">
      <div class="shadowInfo relative">
        <perviewTest :paperData="questions"></perviewTest>
        <div class="w-full bottominfo">
          <div class="close" @click="preview = !preview"></div>
        </div>
      </div>
    </div>
    <!--    model-->
    <!--    <model-self ref="modelSelf" :title="'考卷预览'">-->
    <!--      <template v-slot:center >-->
    <!--        <div style="width: 100%;height: 100%;overflow: hidden">-->
    <!--&lt;!&ndash;          <StudentQues v-for="(item,index) in questions"  :index="index" :params="item" :score="100">&ndash;&gt;-->
    <!--&lt;!&ndash;          </StudentQues>&ndash;&gt;-->
    <!--          <perviewTest :paperData="questions"></perviewTest>-->
    <!--        </div>-->
    <!--      </template>-->
    <!--    </model-self>-->
  </div>
</template>

<script>
import ModelSelf from '../../../../../../components/modelSelf/ModelSelf.vue'
import StudentQues from '../../../../../../components/test/studentQues/StudentQues.vue'
export default {
  name: 'AddTest',
  components: { ModelSelf, StudentQues }
}
</script>
<script setup>
import { ref, onMounted, watch, provide } from 'vue'
import addTest from './js/addTest'
import { createFromIconfontCN } from '@ant-design/icons-vue'
import perviewTest from '../../../../../../components/test/perviewTest/perviewTest.vue'
import llcy from '../../../../../../assets/HJ/test/llcy.png'

const IconFont = createFromIconfontCN({
  scriptUrl: window.iconUrl
})
const fs = ref(JSON.parse(localStorage.getItem('fs')));
provide('realTimeAnwser', {})
const { listData, selectedPaper, questions, studentsList, KnowledgeData, searchStudentStr, teachers, params, selectPaper, searchPaperObj, preview, takeNoTestVisible, searchPaper, clearSearch, commitTest, previewModel, goBack, selectAllStu } = addTest()
</script>

<style lang="less" scoped>
  .HJ{
    :deep(.item_group) {
      height: 32px !important;
    }
    :deep(.ant-input) {
      background: #071633 !important;
      height: 32px !important;
      border-color: #354971 !important;
    }
    .topicList {
      font-size: 15px;
      color: #a5b6d0;
      line-height: 33px;
      transition: all 0.5s;
      border-radius: 3px;
    }
    .topicList:hover {
      background: #284c77;
      color: #ffffff !important;
    }
    .list::before,
    .list::after {
      content: '';
      position: absolute;
      z-index: -1;
    }
    .list::before {
      background-image: linear-gradient(0deg, #fbab44 10%, #47526d 50%);
      top: 0;
      left: 0;
      width: 100%;
      height: 100%;
      transform: translate3d(0, 100%, 0);
      transition: transform 0.5s;
    }
    .list:hover::before {
      transform: translate3d(0, 0, 0);
    }
    .list:hover {
      border: 1px solid transparent;
    }
    .addTestContainer {
      width: 100%;
      height: 100%;
      box-sizing: border-box;
      display: flex;
    }
    .right {
      width: calc(100% - 10px);
      height: 100%;
      padding: 0px 17px;
      margin-left: 10px;
      background: rgba(24, 45, 86, 0.7);
      .fromTable {
        display: flex;
        width: 100%;
        justify-content: center;
        padding: 25px 0 17px 0;
        border-bottom: 1px solid rgba(123, 152, 180, 0.2);
        font-size: 20px;
      }
      .inputsContainer {
        color: #7b90af;
        padding-right: 20px;
      }
      .rightBottom {
        padding: 15px 0;
      }
      .search {
        width: 100%;
        display: flex;
        justify-content: space-between;
      }
      .theTitle {
        width: 100%;
        height: 160px;
        display: flex;
        padding-top: 40px;
        align-items: center;
        justify-content: space-evenly;
      }
      .theTitleItem {
        background: url('../../../../../../assets/HJ/test/gif3.gif.png');
        background-size: 100%;
        background-repeat: no-repeat;
        background-position: bottom;
        text-align: center;
        padding: 14px;
      }
      .imgs {
        position: absolute;
        top: 0;
        right: 0;
        z-index: 1;
        opacity: 0.7;
      }
      .list {
        height: 245px;
        min-width: 222px;
        margin: 10px;
        cursor: pointer;
        transition: all 0.5s;
        /*border: 1px solid transparent;*/
        /*background-image: linear-gradient(50deg, #0e1c38 75%, rgba(37, 83, 128, 0.2));*/
        /*background-size: 300% auto;*/
        overflow: hidden;
        z-index: 99;
        border: 1px solid #2c3b5a;
        padding: 1px;
      }
      .listActive,
      .list:hover {
        border: 1px solid transparent;
        /*border:1px solid #354971!important;*/
        /*box-shadow:  0 0 15px #364772;*/
        /*background-position: right center;color: #ffffff;*/
      }
      .list:hover .theTitle {
        background-image: linear-gradient(
          0deg,
          rgba(163, 114, 29, 0.1),
          transparent
        );
      }
      .list:hover .theTitleItem {
        background: url('../../../../../../assets/HJ/test/gif4.gif.png') no-repeat
        bottom;
        background-size: 100%;
      }
      .listLabel {
        height: 40px;
        margin: auto 0;
        position: relative;
        background: #0e1c38;
        font-size: 16px;
        font-weight: bold;
        line-height: 19px;
        padding-right: 110px;
        overflow: hidden;
        white-space: nowrap;
        text-overflow: ellipsis;
      }
      .theLabel:hover {
        background: #1f212d;
      }
      .theLabel {
        height: 22px;
        width: 70px;
        position: absolute;
        border: 1px solid rgba(221, 153, 78, 0.3);
        top: 12px;
        right: 12px;
        z-index: 2;
        display: flex;
        align-items: center;
        justify-content: center;
        white-space: nowrap;
        text-overflow: ellipsis;
        overflow: hidden;
        .theLabelInfo {
          text-align: center;
          word-break: break-all;
          word-break: break-word;
          position: absolute;
          right: 6px;
          font-size: 12px;
          top: 3px;
          width: 45px;
          height: 40px;
        }
      }
      .listImg {
        height: calc(100% - 40px);
        width: 100%;
        /*background-repeat: no-repeat;*/
        background: #0e1c38;
        .infoimg {
          width: 100%;
          height: 100%;
        }
      }
      .list:hover .listImg {
        box-shadow: inset 0px -15px 20px #182b46;
      }
      .activeList,
      .listImg:hover {
        box-shadow: inset 0px -15px 20px #182b46;
      }
      .listLine {
        display: inline-block;
        height: 14px;
        width: 3px;
        background: #6ebdff;
        vertical-align: middle;
        margin: 13px 12px 13px 12px;
      }
      .trangle {
        position: absolute;
        right: 0px;
        bottom: 0px;
        border-bottom: 40px solid #354971;
        border-top: 0px solid transparent;
        border-left: 40px solid transparent;
        border-right: 0px solid transparent;
      }
      .icon {
        position: absolute;
        right: 2px;
        bottom: 5px;
      }
    }
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
        background-color: #172943;
        width: 1000px;
        height: 620px;
        background-image: url('../../../../../../assets/HJ/train/modal-bg.jpg');
        background-size: 1000px 160px;
        background-repeat: no-repeat;
        .title {
          margin: 0 auto;
          width: 338px;
          height: 56px;
          background-image: url('../../../../../../assets/HJ/train/title-bg.png');
          background-size: 338px 56px;
          background-repeat: no-repeat;
          position: relative;
          .addClass {
            position: absolute;
            bottom: 0px;
            right: -230px;
          }
          .text {
            width: 100%;
            height: 100%;
            background-image: -webkit-linear-gradient(bottom, #44aaff, #e2f2ff);
            -webkit-background-clip: text;
            -webkit-text-fill-color: transparent;
            text-align: center;
            line-height: 56px;
            font-size: 30px;
            font-family: 'Microsoft Yahei';
            font-weight: bold;
          }
        }
        .center {
          border-left: 1px solid #2d4f72;
          border-right: 1px solid #2d4f72;
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
                background-image: url('../../../../../../assets/HJ/train/tabs-check.jpg');
              }
            }
            .botBox {
              display: flex;
              justify-content: center;
              align-items: center;
              height: 50px;
            }
          }
          .right {
            overflow-y: auto;
            width: calc(100%);
            padding: 0 40px;
            position: relative;
          }
        }
        .bottom {
          border-left: 1px solid #2d4f72;
          border-right: 1px solid #2d4f72;
          border-bottom: 1px solid #2d4f72;
          .botBtn {
            width: 110px;
            height: 32px;
            background-image: url('../../../../../../assets/HJ/train/button.png');
          }
        }
        .bottominfo {
          height: 100px;
          display: flex;
          justify-content: center;
          align-items: center;
          .close {
            width: 31px;
            height: 31px;
            border: 2px solid #7b90af;
            border-radius: 50%;
            background: url('../../../../../../assets/HJ/ico/ico-clear.png') no-repeat
            center;
            cursor: pointer;
          }
          .close:hover {
            background: url('../../../../../../assets/HJ/ico/ico-clear-1.png')
            no-repeat center;
            animation: rotate 0.4s linear;
            border-color: #6ebdff;
          }
        }
        .bottominfo::before {
          content: '';
          width: 1px;
          height: 33px;
          background: url('../../../../../../assets/HJ/ico/ico-lone.png');
          position: absolute;
          top: 620px;
          left: 50%;
        }
      }
    }
    .shadowFind {
      position: fixed;
      z-index: 1000;
      top: 0;
      left: 0;
      width: 100%;
      height: 100%;
      background: rgba(0, 0, 0, 0.5);
      .shadowTwo {
        height: 350px !important;
        .bigboxTwo {
          height: calc(350px - 40px) !important;
        }
      }
      .shadowInfo {
        box-shadow: inset 0 -120px 200px -120px rgba(22, 59, 100, 0.8);
        position: absolute;
        margin: auto;
        top: 0;
        left: 0;
        right: 0;
        bottom: 0;
        background-color: #203a61;
        width: 1300px;
        height: 760px;
        border-radius: 10px;
        background-image: url('../../../../../../assets/HJ/train/up.png'),
        url('../../../../../../assets/HJ/train/down.png');
        background-position: top 0 left 0, bottom 0 left 0;
        background-size: 1300px 20px;
        background-repeat: no-repeat;
        .title {
          margin: 0 auto;
          width: 338px;
          height: 56px;
          background-image: url('../../../../../../assets/HJ/train/title-bg.png');
          background-size: 338px 56px;
          background-repeat: no-repeat;
          position: relative;
          .text {
            width: 100%;
            height: 100%;
            background-image: -webkit-linear-gradient(
              bottom,
              #daeeff,
              #9cd2ff,
              #70beff
            );
            -webkit-background-clip: text;
            -webkit-text-fill-color: transparent;
            text-align: center;
            line-height: 56px;
            font-size: 30px;
            font-family: 'Microsoft Yahei';
            font-weight: bold;
          }
        }
        .bigbox {
          margin-top: 20px;
          display: flex;
          .left {
            position: relative;
            width: 120px;
            height: calc(750px - 47px);
            .border-right {
              position: absolute;
              height: 100%;
              top: 0;
              right: -5px;
              width: 10px;
            }
            .topBox {
              overflow-y: auto;
              margin-top: 20px;
              height: calc(100% - 70px);
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
                background-image: url('../../../../../../assets/HJ/train/tabs-check.jpg');
                background-size: 100%;
              }
            }
            .botBox {
              display: flex;
              justify-content: center;
              align-items: center;
              height: 50px;
            }
          }
        }
        .bottominfo {
          position: absolute;
          top: 20px;
          right: 20px;
          width: max-content;
          .close {
            width: 31px;
            height: 31px;
            border: 2px solid #7b90af;
            border-radius: 50%;
            background: url('../../../../../../assets/HJ/ico/ico-clear.png') no-repeat
            center;
            cursor: pointer;
          }
          .close:hover {
            background: url('../../../../../../assets/HJ/ico/ico-clear-1.png')
            no-repeat center;
            animation: rotate 0.4s linear;
            border-color: #6ebdff;
          }
        }
        .bottominfo::before {
        }
        .bottominfoTwo {
          display: flex;
          justify-content: center;
          align-items: center;
          .close {
            width: 31px;
            height: 31px;
            border: 2px solid #7b90af;
            border-radius: 50%;
            background: url('../../../../../../assets/HJ/ico/ico-clear.png') no-repeat
            center;
            cursor: pointer;
          }
          .close:hover {
            background: url('../../../../../../assets/HJ/ico/ico-clear-1.png')
            no-repeat center;
            animation: rotate 0.4s linear;
            border-color: #6ebdff;
          }
        }
        .bottominfoTwo::before {
          content: '';
          width: 1px;
          height: 33px;
          background: url('../../../../../../assets/HJ/ico/ico-lone.png');
          position: absolute;
          top: 350px;
          left: 50%;
        }
        .center {
          .addClass {
            width: 100px;
          }
          display: flex;
          flex-direction: column;
          .right {
            overflow-y: auto;
            width: calc(100%);
            /*margin-top: 20px;*/
            height: calc(565px - 40px);
            padding: 0 40px 0 30px;
            position: relative;
          }
        }
        .bottom {
          .botBtn {
            width: 110px;
            height: 32px;
            background-image: url('../../../../../../assets/HJ/train/button.png');
          }
        }
        .rights {
          overflow-y: auto;
          width: calc(100%);
          /*margin-top: 20px;*/
          height: calc(575px);
          padding: 0 40px 0 30px;
          position: relative;
        }
      }
    }
  }
  .HJJ{
    .listBg {
      background-image: linear-gradient(to bottom, #394d66, #192533);
      border: 1px solid #3c4b5e;
    }
    :deep {
      .item_group {
        height: 32px !important;
      }
      .ant-input {
        height: 32px !important;
      }
    }
    .list::before,
    .list::after {
      content: '';
      position: absolute;
      z-index: -1;
    }
    .list::before {
      background-image: linear-gradient(0deg, #b0a58a 10%, #47526d 50%);
      top: 0;
      left: 0;
      width: 100%;
      height: 100%;
      transform: translate3d(0, 100%, 0);
      transition: transform 0.5s;
    }
    .list:hover::before {
      transform: translate3d(0, 0, 0);
    }
    .list:hover {
      border: 1px solid transparent;
    }
    .addTestContainer {
      width: 100%;
      height: 100%;
      box-sizing: border-box;
      display: flex;
    }
    .right {
      width: calc(100% - 10px);
      height: 100%;
      padding: 0px 17px;
      background: rgba(23, 31, 41, 0.7);
      .fromTable {
        display: flex;
        width: 100%;
        padding: 25px 0 17px 0;
        border-bottom: 1px solid rgba(123, 152, 180, 0.2);
      }
      .inputsContainer {
        color: #7b90af;
        padding-right: 20px;
      }
      .rightBottom {
        padding: 15px 0;
      }
      .search {
        width: 100%;
        display: flex;
        justify-content: space-between;
      }
      .theTitle {
        width: 100%;
        height: 160px;
        display: flex;
        align-items: center;
        justify-content: space-evenly;
        padding-bottom: 40px;
      }
      .theTitleItem {
        background: url('../../../../../../assets/HJJ/basicTheory/test/theTitleItemBg.png') no-repeat bottom;
        background-size: 100%;
        text-align: center;
        padding: 14px;
      }
      .imgs {
        position: absolute;
        top: 60px;
        right: 0;
        z-index: 1;
        opacity: 0.7;
      }
      .list {
        height: 220px;
        min-width: 222px;
        margin: 10px;
        cursor: pointer;
        transition: all 0.5s;
        border: 1px solid transparent;
        /*background-image: linear-gradient(50deg, #0e1c38 75%, rgba(37, 83, 128, 0.2));*/
        /*background-size: 300% auto;*/
        overflow: hidden;
        z-index: 1;
        padding: 1px;
      }
      .list:hover {
        border: 1px solid transparent !important;
        /*border:1px solid #354971!important;*/
        /*box-shadow:  0 0 15px #364772;*/
        /*background-position: right center;color: #ffffff;*/
      }
      .listActive {
        border: 1px solid #354971 !important;
        box-shadow: 0 0 15px #364772;
        background-position: right center;
        color: #ffffff;
      }
      .list::before,
      .list::after {
        content: '';
        position: absolute;
        z-index: -1;
      }
      .list::before {
        background-image: linear-gradient(0deg, #b0a58a 10%, #47526d 50%);
        top: 0;
        left: 0;
        width: 100%;
        height: 100%;
        transform: translate3d(0, 100%, 0);
        transition: transform 0.5s;
      }
      .list:hover::before {
        transform: translate3d(0, 0, 0);
      }
      .list:hover .theTitle {
        background-image: linear-gradient(0deg, rgba(163, 114, 29, 0.1), transparent);
      }
      .list:hover .theTitleItem {
      }
      .listLabel {
        height: 40px;
        background: url('../../../../../../assets/HJJ/basicTheory/test/testTitleBg.png') no-repeat;
        margin: auto 0;
        position: relative;
        /*background: #0e1c38;*/
        font-size: 20px;
        font-weight: bold;
        padding-right: 110px;
        overflow: hidden;
        white-space: nowrap;
        text-overflow: ellipsis;
        background-size: 85% 59%;
        line-height: 10px;
        background-position: 15px center;
        width: 80%;
      }
      .theLabel:hover {
        background: #1f212d;
      }
      .theLabel {
        height: 22px;
        width: 70px;
        position: absolute;
        border: 1px solid rgba(221, 153, 78, 0.3);
        top: 12px;
        right: 12px;
        z-index: 2;
        display: flex;
        align-items: center;
        justify-content: center;
        white-space: nowrap;
        text-overflow: ellipsis;
        overflow: hidden;
        .theLabelInfo {
          text-align: center;
          word-break: break-all;
          word-break: break-word;
          position: absolute;
          right: 6px;
          font-size: 12px;
          top: 3px;
          width: 45px;
          height: 40px;
        }
      }
      .listImg {
        height: calc(100% - 40px);
        width: 100%;
        /*background-repeat: no-repeat;*/
        .infoimg {
          width: 100%;
          height: 100%;
        }
      }
      .list:hover .listImg {
        box-shadow: inset 0px -15px 20px #182b46;
      }
      .activeList,
      .listImg:hover {
        box-shadow: inset 0px -15px 20px #182b46;
      }
      .listLine {
        display: inline-block;
        height: 21px;
        width: 2px;
        background: #6f7986;
        vertical-align: middle;
        margin: 12px 12px 13px 12px;
      }
      .trangle {
        position: absolute;
        right: 0px;
        bottom: 0px;
        border-bottom: 40px solid #354971;
        border-top: 0px solid transparent;
        border-left: 40px solid transparent;
        border-right: 0px solid transparent;
      }
      .icon {
        position: absolute;
        right: 2px;
        bottom: 5px;
      }
    }
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
        background-color: #172943;
        width: 1000px;
        height: 620px;
        background-image: url('../../../../../../assets/HJJ/train/modal-bg.jpg');
        background-size: 1000px 160px;
        background-repeat: no-repeat;
        .title {
          margin: 0 auto;
          width: 338px;
          height: 56px;
          background-image: url('../../../../../../assets/HJJ/train/title-bg.png');
          background-size: 338px 56px;
          background-repeat: no-repeat;
          position: relative;
          .addClass {
            position: absolute;
            bottom: 0px;
            right: -230px;
          }
          .text {
            width: 100%;
            height: 100%;
            background-image: -webkit-linear-gradient(bottom, #44aaff, #e2f2ff);
            -webkit-background-clip: text;
            -webkit-text-fill-color: transparent;
            text-align: center;
            line-height: 56px;
            font-size: 30px;
            font-family: 'Microsoft Yahei';
            font-weight: bold;
          }
        }
        .center {
          border-left: 1px solid #2d4f72;
          border-right: 1px solid #2d4f72;
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
          border-left: 1px solid #2d4f72;
          border-right: 1px solid #2d4f72;
          border-bottom: 1px solid #2d4f72;
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
    .shadowFind {
      position: fixed;
      z-index: 1000;
      top: 0;
      left: 0;
      width: 100%;
      height: 100%;
      background: rgba(0, 0, 0, 0.5);
      .shadowTwo {
        height: 350px !important;
        .bigboxTwo {
          height: calc(350px - 40px) !important;
        }
      }
      .shadowInfo {
        box-shadow: inset 0 -120px 200px -120px rgba(22, 59, 100, 0.8);
        position: absolute;
        margin: auto;
        top: 0;
        left: 0;
        right: 0;
        bottom: 0;
        background-color: #1f2d3b;
        width: 1300px;
        height: 760px;
        border-radius: 10px;
        background-image: url('../../../../../../assets/HJJ/train/up.png'), url('../../../../../../assets/HJJ/train/down.png');
        background-position: top 0 left 0, bottom 0 left 0;
        background-size: 1300px 20px;
        background-repeat: no-repeat;
        .title {
          margin: 0 auto;
          width: 338px;
          height: 56px;
          background-image: url('../../../../../../assets/HJJ/train/title-bg.png');
          background-size: 338px 56px;
          background-repeat: no-repeat;
          position: relative;

          .text {
            width: 100%;
            height: 100%;
            background-image: -webkit-linear-gradient(bottom, #daeeff, #9cd2ff, #70beff);
            -webkit-background-clip: text;
            -webkit-text-fill-color: transparent;
            text-align: center;
            line-height: 56px;
            font-size: 30px;
            font-family: 'Microsoft Yahei';
            font-weight: bold;
          }
        }
        .bigbox {
          /*background: linear-gradient(to bottom right ,#172842,#163a63);*/

          margin-top: 20px;

          display: flex;
          .left {
            position: relative;
            width: 120px;
            height: calc(750px - 47px);
            .border-right {
              position: absolute;
              height: 100%;
              top: 0;
              right: -5px;
              width: 10px;
            }
            .topBox {
              overflow-y: auto;
              margin-top: 20px;
              height: calc(100% - 70px);
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
                background-size: 100%;
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
        }
        .bottominfo {
          position: absolute;
          top: 20px;
          right: 20px;
          width: max-content;
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
          /*content: '';*/
          /*width: 1px;*/
          /*height: 33px;*/
          /*background: url("../../../../../../assets/ico/ico-lone.png");*/
          /*position: absolute;*/
          /*top: 760px;*/
          /*left: 50%;*/
        }
        .bottominfoTwo {
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
        .bottominfoTwo::before {
          content: '';
          width: 1px;
          height: 33px;
          background: url('../../../../../../assets/HJJ/ico/ico-lone.png');
          position: absolute;
          top: 350px;
          left: 50%;
        }
        .center {
          .addClass {
            width: 100px;
            /*position: absolute;*/
            /*bottom: 0px;*/
            /*right: -230px;*/
          }
          /*width: calc(100% - 120px);*/
          /*padding-top: 11px;*/
          display: flex;
          flex-direction: column;
          .right {
            overflow-y: auto;
            width: calc(100%);
            /*margin-top: 20px;*/
            height: calc(565px - 40px);
            padding: 0 40px 0 30px;
            position: relative;
          }
        }
        .bottom {
          .botBtn {
            width: 110px;
            height: 32px;
            background-image: url('../../../../../../assets/HJJ/train/button.png');
          }
        }
        .rights {
          overflow-y: auto;
          width: calc(100%);
          /*margin-top: 20px;*/
          height: calc(575px);
          padding: 0 40px 0 30px;
          position: relative;
        }
      }
    }
  }
  .LJ{
    .listBg {
      background: url('../../../../../../assets/LJ/test/testBg.png') no-repeat;
      background-size: 100% 100%;
    }
    :deep {
      .item_group {
        height: 32px !important;
      }
      .ant-input {
        height: 32px !important;
      }
    }
    .list::before,
    .list::after {
      content: '';
      position: absolute;
      z-index: -1;
    }
    .list::before {
      background-image: linear-gradient(0deg, #b0a58a 10%, #47526d 50%);
      top: 0;
      left: 0;
      width: 100%;
      height: 100%;
      transform: translate3d(0, 100%, 0);
      transition: transform 0.5s;
    }
    .addTestContainer {
      width: 100%;
      height: 100%;
      box-sizing: border-box;
      display: flex;
    }
    .right {
      width: calc(100% - 10px);
      height: 100%;
      padding: 0px 17px;
      background: rgba(38,41,36,0.3);
      .fromTable {
        display: flex;
        width: 100%;
        padding: 25px 0 17px 0;
        border-bottom: 1px solid #27352f;
      }
      .inputsContainer {
        color: #a9abaa;
        padding-right: 20px;
      }
      .rightBottom {
        padding: 15px 0;
      }
      .search {
        width: 100%;
        display: flex;
        justify-content: space-between;
      }
      .theTitle {
        width: 100%;
        height: 160px;
        display: flex;
        align-items: center;
        justify-content: space-evenly;
        padding-bottom: 90px;
      }
      .theTitleItem {
        background: url('../../../../../../assets/LJ/basicTheory/test/theTitleItemBg.png') no-repeat bottom;
        background-size: 100%;
        text-align: center;
        padding: 14px;
      }
      .imgs {
        position: absolute;
        top: 60px;
        right: 0;
        z-index: 1;
        opacity: 0.7;
      }
      .list {
        height: 220px;
        min-width: 222px;
        margin: 10px;
        cursor: pointer;
        transition: all 0.5s;
        border: 1px solid transparent;
        /*background-image: linear-gradient(50deg, #0e1c38 75%, rgba(37, 83, 128, 0.2));*/
        /*background-size: 300% auto;*/
        overflow: hidden;
        z-index: 1;
        padding: 1px;
      }
      .listActive {
        border: 1px solid #26332e !important;
        border-radius: 15px;
        box-shadow: 0 0 10px rgba(6, 182, 11, 0.5);
        background-position: right center;
        color: #ffffff;
      }
      .list::before,
      .list::after {
        content: '';
        position: absolute;
        z-index: -1;
      }
      .list::before {
        background-image: linear-gradient(0deg, #b0a58a 10%, #47526d 50%);
        top: 0;
        left: 0;
        width: 100%;
        height: 100%;
        transform: translate3d(0, 100%, 0);
        transition: transform 0.5s;
      }
      .listLabel {
        background: url("../../../../../../assets/LJ/basicTheory/test/testTitleBg.png") no-repeat;
        height: 34px;
        margin: auto 0;
        position: relative;
        text-align: center;
        font-size: 18px;
        font-weight: bold;
        overflow: hidden;
        white-space: nowrap;
        text-overflow: ellipsis;
        line-height: 34px;
        margin-bottom: 15px;
        background-position: 15px center;
        align-items: center;
        color: white;
        text-shadow: 0px 1px 0px #000000;
      }
      .theLabel:hover {
        background: #1f212d;
      }
      .theLabel {
        height: 22px;
        width: 70px;
        position: absolute;
        border: 1px solid rgba(221, 153, 78, 0.3);
        top: 12px;
        right: 12px;
        z-index: 2;
        display: flex;
        align-items: center;
        justify-content: center;
        white-space: nowrap;
        text-overflow: ellipsis;
        overflow: hidden;
        .theLabelInfo {
          text-align: center;
          word-break: break-all;
          word-break: break-word;
          position: absolute;
          right: 6px;
          font-size: 12px;
          top: 3px;
          width: 45px;
          height: 40px;
        }
      }
      .listImg {
        height: calc(100% - 40px);
        width: 100%;
        /*background-repeat: no-repeat;*/
        .infoimg {
          width: 100%;
          height: 100%;
        }
      }
      .listLine {
        display: inline-block;
        height: 21px;
        width: 2px;
        /*background: #6f7986;*/
        vertical-align: middle;
        margin: 12px 12px 13px 12px;
      }
      .trangle {
        position: absolute;
        right: 0px;
        bottom: 0px;
        border-bottom: 40px solid #26332e;
        border-top: 0px solid transparent;
        border-left: 40px solid transparent;
        border-right: 0px solid transparent;
      }
      .icon {
        position: absolute;
        right: 2px;
        bottom: 5px;
      }
    }
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
        background-color: #172943;
        width: 1000px;
        height: 620px;
        background-image: url('../../../../../../assets/LJ/train/modal-bg.jpg');
        background-size: 1000px 160px;
        background-repeat: no-repeat;
        .title {
          margin: 0 auto;
          width: 338px;
          height: 56px;
          background-image: url('../../../../../../assets/LJ/train/title-bg.png');
          background-size: 338px 56px;
          background-repeat: no-repeat;
          position: relative;
          .addClass {
            position: absolute;
            bottom: 0px;
            right: -230px;
          }
          .text {
            width: 100%;
            height: 100%;
            background-image: -webkit-linear-gradient(bottom, #44aaff, #ffffff);
            -webkit-background-clip: text;
            -webkit-text-fill-color: transparent;
            text-align: center;
            line-height: 56px;
            font-size: 30px;
            font-family: 'Microsoft Yahei';
            font-weight: bold;
          }
        }
        .center {
          border-left: 1px solid #2d4f72;
          border-right: 1px solid #2d4f72;
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
          border-left: 1px solid #2d4f72;
          border-right: 1px solid #2d4f72;
          border-bottom: 1px solid #2d4f72;
          .botBtn {
            width: 110px;
            height: 32px;
            background-image: url('../../../../../../assets/LJ/train/button.png');
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
    .shadowFind {
      position: fixed;
      z-index: 1000;
      top: 0;
      left: 0;
      width: 100%;
      height: 100%;
      background: rgba(0, 0, 0, 0.5);
      .shadowTwo {
        height: 350px !important;
        .bigboxTwo {
          height: calc(350px - 40px) !important;
        }
      }
      .shadowInfo {
        box-shadow: inset 0 -120px 200px -120px rgba(38,59,48, 0.8);
        position: absolute;
        margin: auto;
        top: 0;
        left: 0;
        right: 0;
        bottom: 0;
        background-color: #28302c;
        width: 1300px;
        height: 760px;
        border-radius: 10px;
        background-image: url('../../../../../../assets/LJ/train/up.png'), url('../../../../../../assets/LJ/train/down.png');
        background-position: top 0 left 0, bottom 0 left 0;
        background-size: 1300px 20px;
        background-repeat: no-repeat;
        .title {
          margin: 0 auto;
          width: 338px;
          height: 56px;
          background-image: url('../../../../../../assets/LJ/train/title-bg.png');
          background-size: 338px 56px;
          background-repeat: no-repeat;
          position: relative;

          .text {
            width: 100%;
            height: 100%;
            background-image: -webkit-linear-gradient(bottom, #daeeff, #9cd2ff, #70beff);
            -webkit-background-clip: text;
            -webkit-text-fill-color: transparent;
            text-align: center;
            line-height: 56px;
            font-size: 30px;
            font-family: 'Microsoft Yahei';
            font-weight: bold;
          }
        }
        .bigbox {
          /*background: linear-gradient(to bottom right ,#172842,#163a63);*/

          margin-top: 20px;

          display: flex;
          .left {
            position: relative;
            width: 120px;
            height: calc(750px - 47px);
            .border-right {
              position: absolute;
              height: 100%;
              top: 0;
              right: -5px;
              width: 10px;
            }
            .topBox {
              overflow-y: auto;
              margin-top: 20px;
              height: calc(100% - 70px);
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
                background-size: 100%;
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
        }
        .bottominfo {
          position: absolute;
          top: 20px;
          right: 20px;
          width: max-content;
          /*bottom:-100px;*/
          /*left: 0;*/
          /*height: 300px;*/
          /*display: flex;*/
          /*justify-content: center;*/
          /*align-items: center;*/
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
          /*content: '';*/
          /*width: 1px;*/
          /*height: 33px;*/
          /*background: url("../../../../../../assets/LJ/ico/ico-lone.png");*/
          /*position: absolute;*/
          /*top: 760px;*/
          /*left: 50%;*/
        }
        .bottominfoTwo {
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
        .bottominfoTwo::before {
          content: '';
          width: 1px;
          height: 33px;
          background: url('../../../../../../assets/LJ/ico/ico-lone.png');
          position: absolute;
          top: 350px;
          left: 50%;
        }
        .center {
          .addClass {
            width: 100px;
            /*position: absolute;*/
            /*bottom: 0px;*/
            /*right: -230px;*/
          }
          /*width: calc(100% - 120px);*/
          /*padding-top: 11px;*/
          display: flex;
          flex-direction: column;
          .right {
            overflow-y: auto;
            width: calc(100%);
            /*margin-top: 20px;*/
            height: calc(565px - 40px);
            padding: 0 40px 0 30px;
            position: relative;
          }
        }
        .bottom {
          .botBtn {
            width: 110px;
            height: 32px;
            background-image: url('../../../../../../assets/LJ/train/button.png');
          }
        }
        .rights {
          overflow-y: auto;
          width: calc(100%);
          /*margin-top: 20px;*/
          height: calc(575px);
          padding: 0 40px 0 30px;
          position: relative;
        }
      }
    }
  }
  .KJ{
    .listBg {
      background: url('../../../../../../assets/KJ/test/testBg.png') no-repeat;
      background-size: 100% 100%;
    }
    :deep {
      .item_group {
        height: 32px !important;
      }
      .ant-input {
        height: 32px !important;
      }
    }
    .list::before,
    .list::after {
      content: '';
      position: absolute;
      z-index: -1;
    }
    .list::before {
      background-image: linear-gradient(0deg, #b0a58a 10%, #47526d 50%);
      top: 0;
      left: 0;
      width: 100%;
      height: 100%;
      transform: translate3d(0, 100%, 0);
      transition: transform 0.5s;
    }
    .addTestContainer {
      width: 100%;
      height: 100%;
      box-sizing: border-box;
      display: flex;
    }
    .right {
      width: calc(100% - 10px);
      height: 100%;
      padding: 0px 17px;
      background: rgba(38,41,36,0.3);
      .fromTable {
        display: flex;
        width: 100%;
        padding: 25px 0 17px 0;
        border-bottom: 1px solid #27352f;
      }
      .inputsContainer {
        color: #a9abaa;
        padding-right: 20px;
      }
      .rightBottom {
        padding: 15px 0;
      }
      .search {
        width: 100%;
        display: flex;
        justify-content: space-between;
      }
      .theTitle {
        width: 100%;
        height: 160px;
        display: flex;
        align-items: center;
        justify-content: space-evenly;
        padding-bottom: 90px;
      }
      .theTitleItem {
        background: url('../../../../../../assets/KJ/basicTheory/test/theTitleItemBg.png') no-repeat bottom;
        background-size: 100%;
        text-align: center;
        padding: 14px;
      }
      .imgs {
        position: absolute;
        top: 60px;
        right: 0;
        z-index: 1;
        opacity: 0.7;
      }
      .list {
        height: 220px;
        min-width: 222px;
        margin: 10px;
        cursor: pointer;
        transition: all 0.5s;
        border: 1px solid transparent;
        /*background-image: linear-gradient(50deg, #0e1c38 75%, rgba(37, 83, 128, 0.2));*/
        /*background-size: 300% auto;*/
        overflow: hidden;
        z-index: 1;
        padding: 1px;
      }
      .listActive {
        border: 1px solid #26332e !important;
        border-radius: 15px;
        box-shadow: 0 0 10px rgba(6, 182, 11, 0.5);
        background-position: right center;
        color: #ffffff;
      }
      .list::before,
      .list::after {
        content: '';
        position: absolute;
        z-index: -1;
      }
      .list::before {
        background-image: linear-gradient(0deg, #b0a58a 10%, #47526d 50%);
        top: 0;
        left: 0;
        width: 100%;
        height: 100%;
        transform: translate3d(0, 100%, 0);
        transition: transform 0.5s;
      }
      .listLabel {
        background: url("../../../../../../assets/KJ/basicTheory/test/testTitleBg.png") no-repeat;
        height: 34px;
        margin: auto 0;
        position: relative;
        text-align: center;
        font-size: 18px;
        font-weight: bold;
        overflow: hidden;
        white-space: nowrap;
        text-overflow: ellipsis;
        line-height: 34px;
        margin-bottom: 15px;
        background-position: 15px center;
        align-items: center;
        color: white;
        text-shadow: 0px 1px 0px #000000;
      }
      .theLabel:hover {
        background: #1f212d;
      }
      .theLabel {
        height: 22px;
        width: 70px;
        position: absolute;
        border: 1px solid rgba(221, 153, 78, 0.3);
        top: 12px;
        right: 12px;
        z-index: 2;
        display: flex;
        align-items: center;
        justify-content: center;
        white-space: nowrap;
        text-overflow: ellipsis;
        overflow: hidden;
        .theLabelInfo {
          text-align: center;
          word-break: break-all;
          word-break: break-word;
          position: absolute;
          right: 6px;
          font-size: 12px;
          top: 3px;
          width: 45px;
          height: 40px;
        }
      }
      .listImg {
        height: calc(100% - 40px);
        width: 100%;
        /*background-repeat: no-repeat;*/
        .infoimg {
          width: 100%;
          height: 100%;
        }
      }
      .listLine {
        display: inline-block;
        height: 21px;
        width: 2px;
        /*background: #6f7986;*/
        vertical-align: middle;
        margin: 12px 12px 13px 12px;
      }
      .trangle {
        position: absolute;
        right: 0px;
        bottom: 0px;
        border-bottom: 40px solid #26332e;
        border-top: 0px solid transparent;
        border-left: 40px solid transparent;
        border-right: 0px solid transparent;
      }
      .icon {
        position: absolute;
        right: 2px;
        bottom: 5px;
      }
    }
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
        background-color: #172943;
        width: 1000px;
        height: 620px;
        background-image: url('../../../../../../assets/KJ/train/modal-bg.jpg');
        background-size: 1000px 160px;
        background-repeat: no-repeat;
        .title {
          margin: 0 auto;
          width: 338px;
          height: 56px;
          background-image: url('../../../../../../assets/KJ/train/title-bg.png');
          background-size: 338px 56px;
          background-repeat: no-repeat;
          position: relative;
          .addClass {
            position: absolute;
            bottom: 0px;
            right: -230px;
          }
          .text {
            width: 100%;
            height: 100%;
            background-image: -webkit-linear-gradient(bottom, #44aaff, #ffffff);
            -webkit-background-clip: text;
            -webkit-text-fill-color: transparent;
            text-align: center;
            line-height: 56px;
            font-size: 30px;
            font-family: 'Microsoft Yahei';
            font-weight: bold;
          }
        }
        .center {
          border-left: 1px solid #2d4f72;
          border-right: 1px solid #2d4f72;
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
          border-left: 1px solid #2d4f72;
          border-right: 1px solid #2d4f72;
          border-bottom: 1px solid #2d4f72;
          .botBtn {
            width: 110px;
            height: 32px;
            background-image: url('../../../../../../assets/KJ/train/button.png');
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
    .shadowFind {
      position: fixed;
      z-index: 1000;
      top: 0;
      left: 0;
      width: 100%;
      height: 100%;
      background: rgba(0, 0, 0, 0.5);
      .shadowTwo {
        height: 350px !important;
        .bigboxTwo {
          height: calc(350px - 40px) !important;
        }
      }
      .shadowInfo {
        box-shadow: inset 0 -120px 200px -120px rgba(38,59,48, 0.8);
        position: absolute;
        margin: auto;
        top: 0;
        left: 0;
        right: 0;
        bottom: 0;
        background-color: #28302c;
        width: 1300px;
        height: 760px;
        border-radius: 10px;
        background-image: url('../../../../../../assets/KJ/train/up.png'), url('../../../../../../assets/KJ/train/down.png');
        background-position: top 0 left 0, bottom 0 left 0;
        background-size: 1300px 20px;
        background-repeat: no-repeat;
        .title {
          margin: 0 auto;
          width: 338px;
          height: 56px;
          background-image: url('../../../../../../assets/KJ/train/title-bg.png');
          background-size: 338px 56px;
          background-repeat: no-repeat;
          position: relative;

          .text {
            width: 100%;
            height: 100%;
            background-image: -webkit-linear-gradient(bottom, #daeeff, #9cd2ff, #70beff);
            -webkit-background-clip: text;
            -webkit-text-fill-color: transparent;
            text-align: center;
            line-height: 56px;
            font-size: 30px;
            font-family: 'Microsoft Yahei';
            font-weight: bold;
          }
        }
        .bigbox {
          /*background: linear-gradient(to bottom right ,#172842,#163a63);*/

          margin-top: 20px;

          display: flex;
          .left {
            position: relative;
            width: 120px;
            height: calc(750px - 47px);
            .border-right {
              position: absolute;
              height: 100%;
              top: 0;
              right: -5px;
              width: 10px;
            }
            .topBox {
              overflow-y: auto;
              margin-top: 20px;
              height: calc(100% - 70px);
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
                background-size: 100%;
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
        }
        .bottominfo {
          position: absolute;
          top: 20px;
          right: 20px;
          width: max-content;
          /*bottom:-100px;*/
          /*left: 0;*/
          /*height: 300px;*/
          /*display: flex;*/
          /*justify-content: center;*/
          /*align-items: center;*/
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
          /*content: '';*/
          /*width: 1px;*/
          /*height: 33px;*/
          /*background: url("../../../../../../assets/KJ/ico/ico-lone.png");*/
          /*position: absolute;*/
          /*top: 760px;*/
          /*left: 50%;*/
        }
        .bottominfoTwo {
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
        .bottominfoTwo::before {
          content: '';
          width: 1px;
          height: 33px;
          background: url('../../../../../../assets/KJ/ico/ico-lone.png');
          position: absolute;
          top: 350px;
          left: 50%;
        }
        .center {
          .addClass {
            width: 100px;
            /*position: absolute;*/
            /*bottom: 0px;*/
            /*right: -230px;*/
          }
          /*width: calc(100% - 120px);*/
          /*padding-top: 11px;*/
          display: flex;
          flex-direction: column;
          .right {
            overflow-y: auto;
            width: calc(100%);
            /*margin-top: 20px;*/
            height: calc(565px - 40px);
            padding: 0 40px 0 30px;
            position: relative;
          }
        }
        .bottom {
          .botBtn {
            width: 110px;
            height: 32px;
            background-image: url('../../../../../../assets/KJ/train/button.png');
          }
        }
        .rights {
          overflow-y: auto;
          width: calc(100%);
          /*margin-top: 20px;*/
          height: calc(575px);
          padding: 0 40px 0 30px;
          position: relative;
        }
      }
    }
  }

</style>
