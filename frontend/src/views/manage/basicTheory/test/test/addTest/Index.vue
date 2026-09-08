<template>
  <div class="addTestContainer">
    <div class="left grouping">
      <div class="top">添加人员</div>
      <div class="w-full grouping_halving_line"></div>
      <div class="w-full" style="height: calc(100% - 75px); padding: 0 8px; flex: 1; display: flex; flex-direction: column">
        <div class="item_group input search" style="margin-top: 8px">
          <a-input placeholder="请输入关键字" v-model:value="searchStudentStr" @change="searchStudent"></a-input>
          <div class="icon"></div>
        </div>
        <div class="studentSlist">
          <div style="width: 100%; display: flex; justify-content: flex-end">
            <a-checkbox @change="selectAllStu" > 全选 </a-checkbox>
          </div>
          <div class="leftlist" v-for="item of studentsList" :key="item" v-show="item.show" @click="selectStudent($event, item)" :class="[item.active ? 'listActive' : '']">
            {{ item.userName }}
            <IconFont v-if="item.active" type="icon-gou1" style="font-size: 20px; color: #06b60b; float: right"></IconFont>
          </div>
          <a-empty style="margin-top: 80%" v-if="studentsList.every(item => item.show == false)">
            <template #description>
              <span>暂无学员</span>
            </template>
          </a-empty>
        </div>
      </div>
      <div class="w-full layout-center" style="height: 35px">
        <div style="width: 65%; display: flex; justify-content: space-between">
          <div class="item_group btn" @click="goBack">返回 <IconFont type="icon-fanhui1" style="font-size: 20px; padding-left: 5px"></IconFont></div>
          <div class="item_group btn" @click="commitTest">提交 <IconFont type="icon-a-querentijiao1" style="font-size: 20px; padding-left: 5px"></IconFont></div>
        </div>
      </div>
    </div>
    <div class="right grouping">
      <div class="w-full grouping_halving_line"></div>
      <div class="fromTable">
        <div class="inputsContainer">
          考核名称：
          <a-input style="width: 200px" v-model:value="params.title" maxLength="30" placeholder="请输入考核名称" />
        </div>
        <div class="inputsContainer">
          考核时长(分钟)：
          <a-input-number :max="1000" :min="0" step="10" v-model:value="params.duration" style="width: 100px" />
        </div>
        <div class="inputsContainer">
          开始时间：
          <a-date-picker v-model:value="params.startTime" show-time format="YYYY-MM-DD HH:mm:ss"></a-date-picker>
        </div>
        <div class="inputsContainer">
          监考人员：
          <a-select style="width: 200px" v-model:value="params.teacher" :options="teachers"> </a-select>
        </div>
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
            <a-col v-for="(d, i) in listData" :key="i" :xs="{ span: 12, offset: 0 }" :md="{ span: 12, offset: 0 }" :lg="{ span: 12, offset: 0 }" :xl="{ span: 8, offset: 0 }" :xxl="{ span: 6, offset: 0 }">
              <div class="list relative" @click="selectPaper(d)" :class="[selectedPaper === d.id ? 'listActive' : '']">
                <div class="absolute listBg" style="z-index: 2; top: 1px; left: 1px; right: 1px; bottom: 1px">
                  <div class="theLabel" @click.stop="previewModel(d)">
                    <IconFont type="icon-yulan1" style="font-size: 20px;color: #dd994e"></IconFont>
                    <span style="margin-left: 5px;color: #dd994e" >预览</span>
                  </div>
                  <img class="imgs" :src="llcy" alt="" />
                  <div class="listLabel" :title="d.name"><span class="listLine"></span>{{ d.name }}</div>
                  <div class="listImg relative" :class="[selectedPaper === d.id ? 'activeList' : '']">
                    <div style="margin-left: 30px; font-size: 13px; color: #bfcde0">
                      <div>题目数量：{{ d['completion'].length + d.judge.length + d.multipleChoice.length + d.singleChoice.length + d.shortAnswer.length }}</div>
                      <div>总分：{{ d.total }}</div>
                    </div>
                    <div class="theTitle" :style="{ paddingTop: 40 - fs * 8 + 'px' }">
                      <div class="theTitleItem">
                        <div style="color: #63c1ff; font-weight: bold" :style="{ fontSize: fs * 2 + 18 + 'px' }">
                          {{ d.singleChoice.length }}
                        </div>
                        <div style="color: #a5b6d0" :style="{ fontSize: fs + 13 + 'px' }">
                          单选题
                        </div>
                        <IconFont
                          type="icon-xuanzeti1"
                          style="font-size: 30px; color: #6ebdff"
                        ></IconFont>
                      </div>
                      <div class="theTitleItem">
                        <div style="color: #63c1ff; font-weight: bold" :style="{ fontSize: fs * 2 + 18 + 'px' }">
                          {{ d.multipleChoice.length }}
                        </div>
                        <div style="color: #a5b6d0" :style="{ fontSize: fs + 13 + 'px' }">
                          多选题
                        </div>
                        <IconFont type="icon-duoxuan1" style="font-size: 30px; color: #6ebdff"
                        ></IconFont>
                      </div>
                      <div class="theTitleItem">
                        <div style="color: #63c1ff; font-weight: bold" :style="{ fontSize: fs * 2 + 18 + 'px' }">
                          {{ d.judge.length }}
                        </div>
                        <div style="color: #a5b6d0" :style="{ fontSize: fs + 13 + 'px' }">
                          判断题
                        </div>
                        <IconFont type="icon-panduanti1" style="font-size: 30px; color: #6ebdff"></IconFont>
                      </div>
                      <div class="theTitleItem">
                        <div style="color: #63c1ff; font-weight: bold" :style="{ fontSize: fs * 2 + 18 + 'px' }">
                          {{ d['completion'].length }}
                        </div>
                        <div style="color: #a5b6d0" :style="{ fontSize: fs + 13 + 'px' }">
                          填空题
                        </div>
                        <IconFont type="icon-tiankongti1" style="font-size: 30px; color: #6ebdff"></IconFont>
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
  provide('changeShortAnswerScore', '')
  const IconFont = createFromIconfontCN({
    scriptUrl: window.iconUrl
  })
  const fs = ref(JSON.parse(localStorage.getItem('fs')));
  provide('realTimeAnwser', {})
  const { listData, selectedPaper, questions, studentsList, KnowledgeData, searchStudentStr, teachers, params, selectPaper, searchPaperObj, preview, takeNoTestVisible, searchPaper, clearSearch, commitTest, searchStudent, selectStudent, previewModel, goBack, selectAllStu } = addTest()
</script>

<style lang="less" scoped>
  @import "../../css/paperCard";
  @import "./css/KJ";
  .HJ{
    :deep(.item_group) {
      height: 32px !important;
    }
    :deep(.ant-input) {
      background: #071633 !important;
      height: 32px !important;
      border-color: #354971 !important;
    }
    .addTestContainer {
      width: 100%;
      height: 100%;
      box-sizing: border-box;
      display: flex;
    }
    .left {
      padding-bottom: 20px;
      width: 280px;
      height: 100%;
      background: rgba(24, 45, 86, 0.7);
      margin-right: 10px;
      display: flex;
      flex-direction: column;
      .top {
        background-image: url('../../../../../../assets/HJ/test/addTop.png');
        width: 100%;
        background-repeat: no-repeat;
        height: 40px;
        color: #70b9ec;
        text-align: center;
        font-weight: bold;
        margin-top: 8px;
      }
      .studentSlist {
        flex: 1;
        padding-top: 10px;
        overflow: auto;
        .leftlist {
          padding: 5px;
          color: #a5b6d0;
          cursor: pointer;
          margin-top: 2px;
        }
        .listActive {
          color: #ffffff;
          background: rgba(110, 189, 255, 0.2);
        }
      }
    }
    .right {
      width: calc(100% - 280px - 10px);
      height: 100%;
      padding: 0px 17px;
      background: rgba(24, 45, 86, 0.7);
      .fromTable {
        display: flex;
        width: 100%;
        height: 88px;
        padding: 15px 0 6px 0;
        border-bottom: 1px solid rgba(123, 152, 180, 0.2);
        align-items: center;
      }
      .inputsContainer {
        color: #a5b6d0;
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
            background-image: url('../../../../../../assets/HJ/train/button.png');
          }
        }
        .bottominfo {
          /*position: absolute;*/
          /*bottom:-100px;*/
          /*left: 0;*/
          /*height: 100px;*/
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
        /*.bottominfo::before{*/
        /*  content: '';*/
        /*  width: 1px;*/
        /*  height: 33px;*/
        /*  background: url("../../../../../../assets/HJ/ico/ico-lone.png");*/
        /*  position: absolute;*/
        /*  top: 620px;*/
        /*  left: 50%;*/
        /*}*/
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
            /*border-right: ;*/
          }
        }
        .bottominfo {
          /*position: absolute;*/
          /*bottom:-100px;*/
          /*left: 0;*/
          /*height: 300px;*/
          position: absolute;
          top: 20px;
          right: 20px;
          width: max-content;
          /*display: flex;*/
          /*justify-content: center;*/
          /*align-items: center;*/
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
        /*.bottominfo::before{*/
        /*  content: '';*/
        /*  width: 1px;*/
        /*  height: 33px;*/
        /*  background: url("../../../../../../assets/HJ/ico/ico-lone.png");*/
        /*  position: absolute;*/
        /*  top: 760px;*/
        /*  left: 50%;*/
        /*}*/
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
    .addTestContainer {
      width: 100%;
      height: 100%;
      box-sizing: border-box;
      display: flex;
    }
    .left {
      padding-bottom: 20px;
      width: 280px;
      height: 100%;
      background: rgba(23, 31, 41, 0.7);
      margin-right: 10px;
      display: flex;
      flex-direction: column;
      .top {
        background-image: url('../../../../../../assets/HJJ/test/addTop.png');
        width: 100%;
        background-repeat: no-repeat;
        height: 40px;
        color: #768aa9;
        font-size: 20px;
        text-align: center;
        font-weight: bold;
        line-height: 35px;
      }
      .studentSlist {
        flex: 1;
        padding-top: 10px;
        overflow: auto;
        .leftlist {
          padding: 5px;
          font-size: 13px;
          color: #7b90af;
          cursor: pointer;
          margin-top: 2px;
        }
        .listActive {
          color: #ffffff;
          background: rgba(110, 189, 255, 0.2);
        }
      }
    }
    .right {
      width: calc(100% - 280px - 10px);
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
          /*height: 100px;*/
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
    .listBg{
      /*background-image: linear-gradient(to bottom,#394d66,#192533);*/
      /*border:1px solid #3c4b5e;*/
      transition: all 0.5s;
      background: url('../../../../../../assets/LJ/test/testBg.png') no-repeat;
      background-size: 100% 100%;
      /*margin-bottom: 5px;*/
    }
    :deep{
      .item_group{
        height: 32px!important;
      }
      .ant-input{
        height: 32px!important;
      }
    }
    .addTestContainer{
      width: 100%;
      height: 100%;
      box-sizing: border-box;
      display: flex;
    }
    .left{
      padding-bottom:20px ;
      width: 280px;
      height: 100%;
      background: rgba(38,41,36,0.3) ;
      margin-right: 10px;
      display: flex;
      flex-direction: column;
      .top{
        background-image: url("../../../../../../assets/LJ/test/addTop.png");
        width: 100%;
        background-repeat: no-repeat;
        height: 40px;
        color: #a9abaa;
        font-size: 20px;
        text-align: center;
        font-weight: bold;
        line-height: 35px;
      }
      .studentSlist{
        height: calc(100vh - 235px);
        margin-bottom: 10px;
        padding-top: 10px;
        overflow: auto;
        .leftlist{
          padding: 5px;
          font-size: 13px;
          color: #a9abaa;
          cursor: pointer;
          margin-top: 2px;
        }
        .listActive{
          color: #ffffff;
          background: rgb(43, 51, 46);
        }
      }
    }
    .right{
      width: calc(100% - 280px - 10px);
      height: 100%;
      padding:0px 17px;
      background: rgba(38,41,36,0.3) ;
      .fromTable{
        display: flex;
        width: 100%;
        padding: 25px 0 17px 0;
        border-bottom: 1px solid #27352f;
      }
      .inputsContainer{
        color: #a9abaa;
        padding-right: 20px;
      }
      .rightBottom{
        padding: 15px 0;
      }
      .search{
        width: 100%;
        display: flex;
        justify-content: space-between;
      }

    }
    .shadow{
      position: fixed;
      z-index: 1000;
      top: 0;
      left:0 ;
      width: 100%;
      height: 100%;
      background: rgba(0,0,0,.5);
      .shadowInfo{

        position: absolute;
        margin: auto;
        top: 0;
        left: 0;
        right: 0;
        bottom: 0;
        background-color: #172943;
        width: 1000px;
        height: 620px;
        background-image:url("../../../../../../assets/LJ/train/modal-bg.jpg");
        background-size: 1000px 160px;
        background-repeat:no-repeat ;
        .title{
          margin: 0 auto;
          width: 338px;
          height: 56px;
          background-image:url("../../../../../../assets/LJ/train/title-bg.png");
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
            background-image: -webkit-linear-gradient(bottom,#44aaff,#ffffff);
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
                .bg1{
                  display: flex;
                  align-items: center;
                  justify-content: center;
                  border-radius:16px;
                  padding: 0 4px;
                  min-width:16px ;
                  height: 16px;
                  background: #26332e;
                  font-size: 14px;
                }
                .bg2{
                  background: #34b34c;
                  color: #274666;
                }
              }
              .leftCheck{
                background-image:url("../../../../../../assets/LJ/train/tabs-check.jpg");
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
            background-image:url("../../../../../../assets/LJ/train/button.png");
          }
        }
        .bottominfo{
          /*position: absolute;*/
          /*bottom:-100px;*/
          /*left: 0;*/
          /*height: 100px;*/
          display: flex;
          justify-content: center;
          align-items: center;
          .close{
            width: 31px;
            height: 31px;
            border: 2px solid #a9abaa;
            border-radius: 50%;
            background: url("../../../../../../assets/LJ/ico/ico-clear.png") no-repeat center;
            cursor: pointer;
          }
          .close:hover{
            background: url("../../../../../../assets/LJ/ico/ico-clear-1.png") no-repeat center;
            animation: rotate 0.4s linear;
            border-color: #34b34c;
          }
        }
        /*.bottominfo::before{*/
        /*  content: '';*/
        /*  width: 1px;*/
        /*  height: 33px;*/
        /*  background: url("../../../../../../assets/LJ/ico/ico-lone.png");*/
        /*  position: absolute;*/
        /*  top: 620px;*/
        /*  left: 50%;*/
        /*}*/
      }
    }
    .shadowFind{
      position: fixed;
      z-index: 1000;
      top: 0;
      left:0 ;
      width: 100%;
      height: 100%;
      background: rgba(0,0,0,.5);
      .shadowTwo{
        height: 350px !important;
        .bigboxTwo{
          height: calc(350px - 40px) !important;
        }
      }
      .shadowInfo{
        box-shadow:inset 0 -120px 200px -120px rgba(38,59,48, 0.8) ;
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
        background-image:url("../../../../../../assets/LJ/train/up.png"),url("../../../../../../assets/LJ/train/down.png");
        background-position: top 0 left 0 ,bottom 0 left 0;
        background-size: 1300px 20px;
        background-repeat:no-repeat ;
        .title{
          margin: 0 auto;
          width: 338px;
          height: 56px;
          background-image:url("../../../../../../assets/LJ/train/title-bg.png");
          background-size: 338px 56px;
          background-repeat:no-repeat ;
          position: relative;

          .text{
            width: 100%;
            height: 100%;
            background-image: -webkit-linear-gradient(bottom,#daeeff,#9cd2ff,#70beff);
            -webkit-background-clip: text;
            -webkit-text-fill-color: transparent;
            text-align: center;
            line-height: 56px;
            font-size: 30px;
            font-family:"Microsoft Yahei";
            font-weight: bold;
          }
        }
        .bigbox{
          /*background: linear-gradient(to bottom right ,#172842,#163a63);*/

          margin-top: 20px;

          display: flex;
          .left{
            position: relative;
            width: 120px;
            height:calc(750px - 47px) ;
            .border-right{
              position: absolute;
              height: 100%;
              top: 0;
              right: -5px;
              width: 10px;
            }
            .topBox{
              overflow-y: auto;
              margin-top: 20px;
              height:calc(100% - 70px) ;
              .leftNocheck{
                .leftBorder{
                  height: 100%;
                  position: absolute;
                  left: 0;
                  top: 0;
                  width:2px;
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
                .bg1{
                  display: flex;
                  align-items: center;
                  justify-content: center;
                  border-radius:16px;
                  padding: 0 4px;
                  min-width:16px ;
                  height: 16px;
                  background: #26332e;
                  font-size: 14px;
                }
                .bg2{
                  background: #34b34c;
                  color: #274666;
                }
              }
              .leftCheck{
                background-image:url("../../../../../../assets/LJ/train/tabs-check.jpg");
                background-size: 100%;
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
        }
        .bottominfo{
          /*position: absolute;*/
          /*bottom:-100px;*/
          /*left: 0;*/
          /*height: 300px;*/
          position: absolute;
          top: 20px;
          right: 20px;
          width: max-content;
          /*display: flex;*/
          /*justify-content: center;*/
          /*align-items: center;*/
          .close{
            width: 31px;
            height: 31px;
            border: 2px solid #a9abaa;
            border-radius: 50%;
            background: url("../../../../../../assets/LJ/ico/ico-clear.png") no-repeat center;
            cursor: pointer;
          }
          .close:hover{
            background: url("../../../../../../assets/LJ/ico/ico-clear-1.png") no-repeat center;
            animation: rotate 0.4s linear;
            border-color: #34b34c;
          }
        }
        /*.bottominfo::before{*/
        /*  content: '';*/
        /*  width: 1px;*/
        /*  height: 33px;*/
        /*  background: url("../../../../../../assets/LJ/ico/ico-lone.png");*/
        /*  position: absolute;*/
        /*  top: 760px;*/
        /*  left: 50%;*/
        /*}*/
        .bottominfoTwo{
          display: flex;
          justify-content: center;
          align-items: center;
          .close{
            width: 31px;
            height: 31px;
            border: 2px solid #a9abaa;
            border-radius: 50%;
            background: url("../../../../../../assets/LJ/ico/ico-clear.png") no-repeat center;
            cursor: pointer;
          }
          .close:hover{
            background: url("../../../../../../assets/LJ/ico/ico-clear-1.png") no-repeat center;
            animation: rotate 0.4s linear;
            border-color: #34b34c;
          }
        }
        .bottominfoTwo::before{
          content: '';
          width: 1px;
          height: 33px;
          background: url("../../../../../../assets/LJ/ico/ico-lone.png");
          position: absolute;
          top: 350px;
          left: 50%;
        }
        .center{
          .addClass{
            width: 100px;
            /*position: absolute;*/
            /*bottom: 0px;*/
            /*right: -230px;*/
          }
          /*width: calc(100% - 120px);*/
          /*padding-top: 11px;*/
          display: flex;
          flex-direction: column;
          .right{
            overflow-y: auto;
            width:calc(100%);
            /*margin-top: 20px;*/
            height: calc(565px - 40px);
            padding: 0 40px 0 30px;
            position: relative;
          }
        }
        .bottom{
          .botBtn{
            width: 110px;
            height: 32px;
            background-image:url("../../../../../../assets/LJ/train/button.png");
          }
        }
        .rights{
          overflow-y: auto;
          width:calc(100%);
          /*margin-top: 20px;*/
          height: calc(575px );
          padding: 0 40px 0 30px;
          position: relative;
        }
      }
    }
  }

</style>
