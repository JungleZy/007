<template>
  <!--  理论题库-->
  <div class="w-full h-full questionBank" style="display: flex; justify-content: space-between;padding-top: 0;">
    <nip-left-menu v-if="!params1&&interfaceStyle!=='HJ'"/>
    <div class="h-full grouping" style="width: 280px;" :class="[props.params1?'':'bgColor']">
      <div class="w-full grouping_halving_line"></div>
      <div>
        <div class="w-full h-full" :style="{ padding: props.params1 ? '' : ' 10px 8px' }">
          <div v-if="!props.params1&&userRole.id!=2" style="display: flex;justify-content: space-between">
            <div class="item_group btn layout-center" style="width: 80px;" @click="addKnowledge(1)">新增
              <PlusOutlined style="margin-left: 4px"/>
            </div>
            <div class="item_group btn layout-center" style="width: 80px" @click="addKnowledge(2)">编辑
              <FormOutlined style="margin-left: 4px"/>
            </div>
            <div class="item_group btn layout-center" style="width: 80px" @click="addKnowledge(3)">删除
              <DeleteOutlined style="margin-left: 4px"/>
            </div>
          </div>
          <div class="item_group input search" style="margin-top: 15px">
            <a-input @change="searchForKnowledge" placeholder="请输入关键字"></a-input>
            <div class="icon"></div>
          </div>
        </div>
      </div>
      <div class="  ove" style="height:calc(100% - 90px )">
        <div class="w-full  overflow-y-auto h-full">
          <div class="Cmenus">
            <div class="Cmenus_title Cmenus_titles title_item1" style="padding: 0px"
                 @click="clickKnowledge('知识总览',-1), findAllQuestion(-1),searchStr=''">
              <div class="layout-center-top w-full h-full bg">
                <!--                <div class="title_item2"></div>-->
                <div class="title_zl fs_dispose" :class="{activeKnowledge:activeKnowledge.index === -1}"
                     @click="openMenus">知识总览
                </div>
                <IconFont :type="menuIcon" style="color: #ffffff" @click.stop="openMenus"></IconFont>
              </div>
            </div>
            <div class="Cmenus"
                 v-for="(item,index) in knowledgeList"
                 :class="{'animate__animated':true, animate__fadeOutUp: !openMenu, animate__fadeInDown: openMenu}"
            >
              <div class="Cmenus_title" :class="{activeKnowledge2:activeKnowledge.index === index}"
                   @click="clickKnowledge(item,index), findAllQuestion(item),searchStr=''">
                <span class="actives">{{ item.title }}</span>
                <span @click.stop="clickMenu(item,index)" :class="{ico:true, open: item.isOpen}"></span>
              </div>
              <div style="transition: height 0.5s"
                   :style="{height:openMenu?item.isOpen?((item.children?item.children.length:0)*40)+'px':'0' :'0'}"
                   :class="{'Cmenus_content animate__animated': true, animate__fadeOutUp: !item.isOpen, animate__fadeInDown: item.isOpen}">
                <template v-for="(sub, s) in item.children">
                  <div class="menuQB" :class="{Cmenu_item: true, active: activeKnowledge.index === index+String(s)}"
                       @click="clickKnowledge(sub,index+String(s)), findAllQuestion(sub),searchStr=''">
                    {{ sub.title }}
                  </div>
                </template>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
    <div class="h-full" :class="[props.params1?'':'bgColor']"
         :style="{width: 'calc(100% - '+(leftMenuWidth+12+(!params1?290:0))+'px)'}">
      <div class="topSeach">
        <div class="grouping" style="height: 53px;">
          <div class="w-full grouping_halving_line"></div>
          <div class="w-full h-full" :class="{'layout-right-bottom':props.params1}"
               style="display: flex;padding-bottom: 5px">
            <div v-if="!props.params1" class=""
                 style="margin-top: 10px;margin-left: 8px;width: 270px;display: flex;height: 30px;">
              <a-select v-model:value="difficulty" style="width: 120px;height: 30px!important;"
                        placeholder="请选择类型">
                <a-select-option v-for="(item,index) in typeCheckList" :value="item.id">{{ item.name }}
                </a-select-option>
              </a-select>
              <a-input placeholder="标题" style="width: 78px;border: 1px transparent;height: 30px;"
                       @change="serchQuestion" v-model:value="searchStr"></a-input>
              <div class="serchInfo " style="">
                <div class="icon-search cursor-pointer-def" @click="serchQuestion"></div>
                <div class="borderI"></div>
                <div class="icon-delete cursor-pointer-def" @click="serchQuestion('delete')"></div>
              </div>
              <!--            <div class="icon"></div>-->
            </div>
            <div v-if="props.params1" class="item_group input search" style="margin: 8px 10px 0 0;">
              <a-input @change="serchQuestion" v-model:value="searchStr" placeholder="请输入关键字"></a-input>
              <div class="icon"></div>
            </div>
            <template v-if="!props.params1&&userRole.id!=2">
              <a-button class="layout-center btns" @click="takeNew">
                新建考题
                <IconFont type="icon-plus" style="font-size: 18px"></IconFont>
              </a-button>
              <!--              <div class="item_group btn layout-center" style="margin-top: 10px;margin-left: 8px;height: 30px" @click="takeNew">新建考题 <PlusOutlined style="margin-left: 4px" /></div>-->
              <a-upload
                  name="file"
                  :before-upload="beforeUpload"
                  :showUploadList="false"
                  :class="[
                  knowledgeId == -1 || knowledgeId == 1
                    ? 'cursor-not-allowed'
                    : ''
                ]"
                  accept=".docx,.xlsx"
                  :customRequest="uploadChange"
              >
                <a-button class="layout-center btns">
                  上传题库
                  <IconFont
                      type="icon-upload"
                      class="btnIco"
                      style="font-size: 18px"
                  ></IconFont>
                </a-button>
              </a-upload>
              <a-button class="layout-center btns" @click="exportTemplate(0)">
                导出模板
                <IconFont type="icon-download" style="font-size: 18px"></IconFont>
              </a-button>
              <a-button class="layout-center btns" @click="exportTemplate(1)">
                导出题库
                <IconFont type="icon-download" style="font-size: 18px"></IconFont>
              </a-button>
            </template>
          </div>
        </div>
      </div>
      <div class="botTable tableSelf" style="padding: 8px 10px 0px 10px">
        <a-table :columns="columns"
                 :loading="tableLoading"
                 :size="!props.params1?'small':'large'"
                 class="ant-table-striped table_list_box"
                 :rowKey="record=>record.id"
                 :data-source="listData">
          <template #createTime="{ text }">
            {{ getDayjs(text) }}
          </template>
          <template #action="{ record }">
            <div class="flex layout-center">
              <div class="table_action_btn">
                <div class="table_btn" title="查看" @click="toView(record)">
                  <FileSearchOutlined/>
                </div>
                <div v-if="!props.params1&&userRole.id!=2" class="table_btn" title="修改" @click="queryModal(record)">
                  <FormOutlined/>
                </div>
                <div v-if="!props.params1&&userRole.id!=2" class="table_btn" title="删除"
                     @click="deleteTheoryKnowledge(record.id)">
                  <CloseCircleOutlined/>
                </div>
                <div v-if="props.params1&&!record.isClick&&userRole.id!=2" class="table_btn" title="添加"
                     @click="addQuestion(record,true)">
                  <PlusOutlined/>
                </div>
                <div v-if="props.params1&&record.isClick&&userRole.id!=2" class="table_btn" title="取消"
                     @click="addQuestion(record,false)">
                  <CloseCircleOutlined/>
                </div>
              </div>
            </div>
          </template>
        </a-table>
      </div>
    </div>
    <a-modal
        :destroyOnClose="true"
        :width="300"
        class="init_modal_style footer-border-none"
        destroyOnClose="true"
        v-model:visible="addDrillModal"
        @cancel="addDrillModal = false"
    >
      <template #title>
        <strong>{{ addAndUpdate === 1 ? '在[' + activeKnowledge.name + ']下增加节点' : '修改[' + activeKnowledge.name + ']节点名称' }} </strong>
      </template>
      <template #footer>
        <div class="w-full layout-center">
          <div class="createDrillBtn"
               @click="addNode(addAndUpdate)">{{ addAndUpdate === 1 ? '生成' : '修改' }}节点
          </div>
        </div>
      </template>
      <div style="padding: 20px 10px">
        <span style="color: #fff"> 节点名：</span>
        <a-input v-model:value="newKnowledge.name" maxLength="6" placeholder="请填写节点名"
                 style="width: 190px"></a-input>
      </div>
    </a-modal>
    <div class="shadow fade-in" v-if="knowledgeShow">
      <div class="shadowInfo">
        <div class="title">
          <div class="text">
            {{ params.levelId ? "修 改" : "新 建" }} 考 题
          </div>
        </div>
        <div class="newClosePopup" @click="takeNoTestVisible">
          <div class="closeIco"></div>
        </div>
        <div class="bigbox relative">
          <div class="center w-full" style="height:100%">
            <div class="right">
              <room-test ref="roomtest" :selectTree="selecttreeA"
                         :selectValue="params.levelId?params.levelId:activeKnowledge.id" :indexShow="false"
                         :params="params" class="">
              </room-test>
            </div>
            <div class="bottom layout-center" style="height: 60px">
              <div class="botBtn layout-center cursor-pointer-def" @click="bornTest"> 保存考题</div>
            </div>
          </div>
        </div>
        <!--<div class="w-full bottominfo">
          <div class="close" @click="takeNoTestVisible"></div>
        </div>-->
      </div>
    </div>
    <div class="shadow fade-in" v-if="openModel.bool">
      <div class="shadowInfo shadowTwo">
        <div class="title">
          <div class="text">
            查 看 题 目
          </div>
        </div>
        <div class="newClosePopup" @click="openModel.bool = !openModel.bool ">
          <div class="closeIco"></div>
        </div>
        <div class="bigbox relative bigboxTwo" style="margin-top: 0px">
          <div class="center w-full h-full" style="height: calc(100% )">
            <div class="rights">
              <PreviewTheTopic ref="son" :params="openModel.value" :bool="true"/>
            </div>
          </div>

        </div>
        <div class="pre" @click="handlePrevious()">
          <div class="text1" style="user-select: none" :style="{fontSize: (15 + 1 * 2)+'px'}">
            上一题
          </div>
        </div>
        <div class="next" @click="handleNext()">
          <div class="text2" style="user-select: none" :style="{fontSize: (15 + 1 * 2)+'px'}">
            下一题
          </div>
        </div>
        <!--<div class="w-full  bottominfoTwo">
          <div class="close" @click="openModel.bool = !openModel.bool "></div>
        </div>-->
      </div>
    </div>
  </div>
</template>

<script>
import {defineComponent, toRefs} from "vue"

export default defineComponent({
  name: "questionBank",

})
</script>

<script setup>
import {defineProps, provide} from 'vue'

const props = defineProps({
  params1: Boolean,
  topicType: String,
  activeList: Array
})
import useQuestionBank from './js/useQuestionBank.js'
import knowledgeTabel from './js/knowledgeTabel.js'
import {
  PlusOutlined,
  FileSearchOutlined,
  FormOutlined,
  DeleteOutlined,
  CloseCircleOutlined,
  CloseOutlined, createFromIconfontCN,
} from '@ant-design/icons-vue';
import NipLeftMenu from "../../../../../components/common/NipLeftMenu.vue";
import RoomTest from '../../../../../components/test/roomTest/RoomTest.vue'
import PreviewTheTopic from '../../../../../components/test/previewTheTopic/PreviewTheTopic.vue'
import {useRouter, useRoute} from 'vue-router'

provide("realTimeAnwser", "")
const IconFont = createFromIconfontCN({
  scriptUrl: window.iconUrl,
});
import {ref, onMounted, defineEmits} from "vue";
import {message} from "ant-design-vue";
import { isUploadSizeAllowed, uploadSizeMessage } from '../../../../../common/utils/uploadLimits.js'

const userRole = ref(JSON.parse(localStorage.getItem('userRole')));
const leftMenuWidth = ref(215)
const interfaceStyle = window.interfaceStyle
if (interfaceStyle === "HJJ") {
  leftMenuWidth.value = 210
} else if (interfaceStyle === "HJ") {
  leftMenuWidth.value = -12
} else {
  leftMenuWidth.value = 170
}
const atRoute = ref({
  name: ''
})
const page = ref({
  showQuickJumper: true,
  showSizeChanger: true,
});
onMounted(() => {
  queryKnowledgeTree()
  clickKnowledge('知识总览', -1);
  findAllQuestion(-1);
})
const beforeUpload = file => {
  if (knowledgeId.value == -1 || knowledgeId.value == 1) {
    message.error('请先选择需要上传题库的二级知识节点！')
    return false
  }
  if (!/\.(docx|xlsx)$/i.test(file.name || '')) {
    message.error('仅支持 DOCX 或 XLSX 题库文件')
    return false
  }
  if (!isUploadSizeAllowed(file)) {
    message.error(uploadSizeMessage())
    return false
  }
  return true
}
const roomtest = ref();
const emit = defineEmits(['clickActive']);
const {
  knowledgeList,
  addDrillModal,
  activeKnowledge,
  newKnowledge,
  addAndUpdate,
  openMenu,
  queryKnowledgeTree,
  clickMenu,
  clickKnowledge,
  addKnowledge,
  addNode,
  searchForKnowledge,
  selecttreeA,
  openMenus,
  menuIcon
} = useQuestionBank();
const {
  typeCheckList,
  tableLoading,
  difficulty,
  listData,
  columns,
  knowledgeShow,
  params,
  openModel,
  searchStr,
  knowledgeId,
  son,
  exportTemplate,
  takeNew,
  takeNoTestVisible,
  bornTest,
  findAllQuestion,
  uploadChange: uploadQuestionFile,
  deleteTheoryKnowledge,
  queryModal,
  toView,
  addQuestion,
  serchQuestion,
  handlePrevious,
  handleNext
} = knowledgeTabel(selecttreeA, roomtest, props.topicType, emit, props.activeList, activeKnowledge)
const uploadChange = async (options) => {
  if (!isUploadSizeAllowed(options?.file)) {
    message.error(uploadSizeMessage())
    if (options?.onError) options.onError(new Error(uploadSizeMessage()))
    return false
  }
  return uploadQuestionFile(options)
}
const atMenus = ref({
  children: [{
    meta: {
      isMenu: true
    }
  }]
})
const getDayjs = (text) => {
  return dayjs(Number(text)).format('YYYY-MM-DD HH:mm:ss')
}
</script>

<style lang="less" scoped>
  @import './css/KJ';
@media (max-width: 1060px) {
  .btnIco {
    display: none;
  }
}


:deep(.topSeach .ant-select-single:not(.ant-select-customize-input) .ant-select-selector) {
  height: 30px !important;
}

.HJ {
  .bgColor {
    background: rgba(24, 45, 86, 0.7)
  }

  .pre {
    position: absolute;
    width: 288px;
    height: 40px;
    font-size: 16px;
    cursor: pointer;
    color: #a6c9f2;
    line-height: 40px;
    left: 0;
    bottom: 0;
    background-image: url("../../../../../assets/HJ/basicTheory/studyManage/analyze/pre.png");
    background-repeat: no-repeat;
    text-align: right;
    padding-right: 90px;
  }

  .pre:hover {
    color: #ffffff;
    background-image: url("../../../../../assets/HJ/basicTheory/studyManage/analyze/pre-hover.png");
  }

  .next {
    width: 288px;
    height: 40px;
    color: #a6c9f2;
    line-height: 40px;
    font-size: 16px;
    position: absolute;
    right: 0;
    bottom: 0;
    cursor: pointer;
    padding-left: 90px;
    background-image: url("../../../../../assets/HJ/basicTheory/studyManage/analyze/next.png");
  }

  .next:hover {
    color: #ffffff;
    background-image: url("../../../../../assets/HJ/basicTheory/studyManage/analyze/next-hover.png");
  }

  :deep(th) {
    text-align: center !important;
  }

  .grouping:deep(.ant-select-selector) {
    border: 0px !important
  }

  .grouping:deep(.ant-select) {
    border: 0px !important
  }

  .grouping:deep(.ant-btn[disabled], .ant-btn[disabled]:hover, .ant-btn[disabled]:focus, .ant-btn[disabled]:active) {
    color: rgba(226, 242, 255, 0.85) !important;
  }

  .btns {
    background: #20426c;
    box-shadow: inset 0px 0px 5px #007ac1;
    margin: 10px 0px 0px 8px;
  }

  .bg {
    background-image: url('../../../../../assets/HJ/basicTheory/test/question_bank_bg.png');
    background-repeat: no-repeat;
    background-size: 100% 100%;
    color: #ffffff !important;
  }

  .menuQB {
    margin-top: 4px !important;
  }

  .menuQB:last-child {
    margin-bottom: 4px;
  }

  .questionBank {
    .shadow {
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
          height: calc(350px - 56px) !important;
        }
      }

      .shadowInfo {
        box-shadow: inset 0 -220px 336px -220px rgb(37 101 171 / 80%);
        position: absolute;
        margin: auto;
        top: 0;
        left: 0;
        right: 0;
        bottom: 0;
        background-color: #0e2446;
        width: 1000px;
        height: 660px;
        background-image: url('../../../../../assets/HJ/train/modal-bg.jpg');
        background-size: 1000px 160px;
        background-repeat: no-repeat;

        .title {
          margin: 0 auto;
          width: 338px;
          height: 56px;
          background-image: url('../../../../../assets/HJ/train/title-bg.png');
          background-size: 338px 56px;
          background-repeat: no-repeat;
          position: relative;

          .text {
            width: 100%;
            height: 100%;
            background-image: -webkit-linear-gradient(bottom, #44aaff, #e2f2ff);
            -webkit-background-clip: text;
            -webkit-text-fill-color: transparent;
            text-align: center;
            line-height: 56px;
            font-size: 24px;
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
            height: calc(650px - 47px);

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
                background-image: url('../../../../../assets/HJ/train/tabs-check.jpg');
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
          height: 100px;
          display: flex;
          justify-content: center;
          align-items: center;

          .close {
            width: 31px;
            height: 31px;
            border: 2px solid #7b90af;
            border-radius: 50%;
            background: url('../../../../../assets/HJ/ico/ico-clear.png') no-repeat center;
            cursor: pointer;
          }

          .close:hover {
            background: url('../../../../../assets/HJ/ico/ico-clear-1.png') no-repeat center;
            animation: rotate 0.4s linear;
            border-color: #6ebdff;
          }
        }

        .bottominfo::before {
          content: '';
          width: 1px;
          height: 33px;
          background: url('../../../../../assets/HJ/ico/ico-lone.png');
          position: absolute;
          top: 660px;
          left: 50%;
        }

        .bottominfoTwo {
          display: flex;
          justify-content: center;
          align-items: center;
          height: 100px;

          .close {
            width: 31px;
            height: 31px;
            border: 2px solid #7b90af;
            border-radius: 50%;
            background: url('../../../../../assets/HJ/ico/ico-clear.png') no-repeat center;
            cursor: pointer;
          }

          .close:hover {
            background: url('../../../../../assets/HJ/ico/ico-clear-1.png') no-repeat center;
            animation: rotate 0.4s linear;
            border-color: #6ebdff;
          }
        }

        .bottominfoTwo::before {
          content: '';
          width: 1px;
          height: 33px;
          background: url('../../../../../assets/HJ/ico/ico-lone.png');
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
            border-right: 1px solid #2d4f72;
            border-left: 1px solid #2d4f72;
          }
        }

        .bottom {
          border-right: 1px solid #2d4f72;
          border-left: 1px solid #2d4f72;
          border-bottom: 1px solid #2d4f72;

          .botBtn {
            width: 110px;
            height: 32px;
            background-image: url('../../../../../assets/HJ/train/button.png');
          }
        }

        .rights {
          border-right: 1px solid #2d4f72;
          border-left: 1px solid #2d4f72;
          border-bottom: 1px solid #2d4f72;
          overflow-y: auto;
          width: calc(100%);
          /*margin-top: 20px;*/
          height: calc(350px - 56px);
          padding: 20px 40px 0 30px;
          position: relative;
        }
      }
    }
  }

  .info {
    height: 32px;
    border-left: 1px dashed #36465f;
    /*padding: 0 12px;*/
    margin-left: 30px;

    .leftMenus_item {
      padding: 0 12px;
      width: 100%;
      height: 100%;
    }
  }

  .serchInfo {
    display: flex;
    align-items: center;

    .icon-search {
      width: 15px;
      height: 15px;
      background: url("../../../../../assets/HJ/ico/ico-search.png") no-repeat center;
      margin-left: 10px;
      /*padding-right: 12px;*/
      /*border-right: 1px solid #293c5d;*/
    }

    .borderI {
      margin-left: 10px;
      margin-right: 8px;
      width: 2px;
      height: 15px;
      background: #293c5d;
    }

    .icon-delete {
      width: 15px;
      height: 15px;
      background: url("../../../../../assets/HJ/ico/ico-clear.png") no-repeat center;
    }
  }

  .infoDrop {
    height: 28px;
  }

  .activeKnowledge {
    color: #814200;
    font-weight: bolder;
  }

  .activeKnowledge2 {
    font-weight: bolder;
    font-size: 16px;
    background-image: linear-gradient(0deg, #115197, #3169b0);
  }

  .Cmenus_title:hover {
    font-weight: bolder;
    background-image: linear-gradient(0deg, #115197, #3169b0);
  }

  .createDrillBtn {

    width: 96px;
    height: 30px;
    color: #e2f2ff;
    cursor: pointer;
    font-size: 15px;
    text-align: center;
    line-height: 28px;
    background-image: linear-gradient(#70a3b8, #4c7595);
    box-shadow: 2px 2px 3px rgba(0, 0, 0, .2);
    border-radius: 2px;
  }

  .title_item1 {
    margin-bottom: 1px;
    height: 64px;
  }

  .title_item2 {
    width: 125px;
    height: 0;
    border-top: 5px solid #2a4163;
    border-left: 5px solid transparent;
    border-right: 5px solid transparent;
  }

  .title_zl {
    line-height: 18px;
    font-size: 18px;
    margin-top: 18px;
    margin-bottom: 8px;
  }
}

.HJJ {
  .bgColor {
    background: rgba(23, 31, 41, 0.7)
  }

  .pre {
    position: absolute;
    width: 288px;
    height: 40px;
    font-size: 16px;
    cursor: pointer;
    color: #a6c9f2;
    line-height: 40px;
    left: 0;
    bottom: 0;
    background-image: url("../../../../../assets/HJJ/basicTheory/studyManage/analyze/pre.png");
    background-repeat: no-repeat;
    text-align: right;
    padding-right: 90px;
  }

  .pre:hover {
    color: #ffffff;
    background-image: url("../../../../../assets/HJJ/basicTheory/studyManage/analyze/pre-hover.png");
  }

  .next {
    width: 288px;
    height: 40px;
    color: #a6c9f2;
    line-height: 40px;
    font-size: 16px;
    position: absolute;
    right: 0;
    bottom: 0;
    cursor: pointer;
    padding-left: 90px;
    background-image: url("../../../../../assets/HJJ/basicTheory/studyManage/analyze/next.png");
  }

  .next:hover {
    color: #ffffff;
    background-image: url("../../../../../assets/HJJ/basicTheory/studyManage/analyze/next-hover.png");
  }

  :deep(th) {
    text-align: center !important;
  }

    .grouping:deep(.ant-select-selector){
      border: 0px !important
    }

  .grouping:deep(.ant-select) {
      border: 0px !important
    }

  .grouping:deep(.ant-btn[disabled], .ant-btn[disabled]:hover, .ant-btn[disabled]:focus, .ant-btn[disabled]:active) {
      color: rgba(226, 242, 255, 0.85) !important;
    }

  .btns {
    background-image: linear-gradient(to right, #2e4051 0%, #446782 50%, #7d765c 100%) !important;
    text-transform: uppercase;
    background-size: 300% auto;
    /* box-shadow: inset 0px 0px 5px #007ac1; */
    border: 1px solid #4c7595;
    height: 32px;
    color: #c4dafb;
    transition: all 1s;
    margin: 10px 0px 0px 8px;
  }

  .ant-btn:hover, .btns:hover {
    /*background-image: linear-gradient(to right, #2e4051 0%, #446782 50% ,#7d765c 100%)!important;*/
    background-position: right center;
    color: #ffffff;
    box-shadow: inset 0px 0px 5px #cac1a6;
    border: 1px solid #bbb193;
    transition: all 1s;
  }

  .bg {
    background-image: url("../../../../../assets/HJJ/basicTheory/test/question_bank_bg.png");
    background-repeat: no-repeat;
    background-size: 100% 100%;
    color: #bfcde0 !important;
    font-size: 20px;
    font-weight: bold;
    text-align: center;
  }

  .menuQB {
    margin-top: 4px !important;
  }

  .menuQB:last-child {
    margin-bottom: 4px;
  }

  .questionBank {
    .shadow {
      position: fixed;
      z-index: 1000;
      top: 0;
      left: 0;
      width: 100%;
      height: 100%;
      background: rgba(0, 0, 0, .5);

      .shadowTwo {
        height: 350px !important;

        .bigboxTwo {
          height: calc(350px - 56px) !important;
        }
      }

      .shadowInfo {
        position: absolute;
        margin: auto;
        top: 0;
        left: 0;
        right: 0;
        bottom: 0;
        background: url("../../../../../assets/HJJ/login/registerCenter-1.png") no-repeat center;
        background-size: 100% 100%;
        box-shadow: 0 0 20px rgba(0, 0, 0, .5);
        width: 1000px;
        height: 660px;
        padding-bottom: 20px;

        .title {
          margin: 0 auto;
          width: 100%;
          height: 40px;
          background: #2e4559 url("../../../../../assets/HJJ/login/popTitle.png") no-repeat left center;
          box-shadow: 0 0 20px #1c2733;
          border-bottom: 1px solid #324b60;
          position: relative;

          .text {
            width: 100%;
            height: 100%;
            text-align: left;
            padding-left: 30px;
            line-height: 40px;
            font-weight: bold;
            color: #ffffff;
            font-size: 18px;
          }
        }

        .newClosePopup .closeIco {
          transform: scale(.8);
        }

        .bigbox {
          /*background: linear-gradient(to bottom right ,#172842,#163a63);*/
          margin-top: 26px;
          display: flex;
          padding: 0 30px 20px 30px;

          .left {
            position: relative;
            width: 120px;
            height: calc(650px - 47px);

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
                background-image: url("../../../../../assets/HJJ/train/tabs-check.jpg");
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
          height: 100px;
          display: flex;
          justify-content: center;
          align-items: center;

          .close {
            width: 31px;
            height: 31px;
            border: 2px solid #7b90af;
            border-radius: 50%;
            background: url("../../../../../assets/HJJ/ico/ico-clear.png") no-repeat center;
            cursor: pointer;
          }

          .close:hover {
            background: url("../../../../../assets/HJJ/ico/ico-clear-1.png") no-repeat center;
            animation: rotate 0.4s linear;
            border-color: #6ebdff;
          }
        }

        .bottominfo::before {
          content: '';
          width: 1px;
          height: 33px;
          background: url("../../../../../assets/HJJ/ico/ico-lone.png");
          position: absolute;
          top: 660px;
          left: 50%;
        }

        .bottominfoTwo {
          display: flex;
          justify-content: center;
          align-items: center;
          height: 100px;

          .close {
            width: 31px;
            height: 31px;
            border: 2px solid #7b90af;
            border-radius: 50%;
            background: url("../../../../../assets/HJJ/ico/ico-clear.png") no-repeat center;
            cursor: pointer;
          }

          .close:hover {
            background: url("../../../../../assets/HJJ/ico/ico-clear-1.png") no-repeat center;
            animation: rotate 0.4s linear;
            border-color: #6ebdff;
          }
        }

        .bottominfoTwo::before {
          content: '';
          width: 1px;
          height: 33px;
          background: url("../../../../../assets/HJJ/ico/ico-lone.png");
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
            height: calc(565px - 60px);
            padding: 0 40px 0 30px;
            position: relative;
          }
        }

        .bottom {
          .botBtn {
            width: 110px;
            height: 32px;
            background-image: linear-gradient(#70a3b8, #4c7595);
          }
        }

        .rights {
          /*border-right: 1px solid #2d4f72;*/
          /*border-left: 1px solid #2d4f72;*/
          /*border-bottom: 1px solid #2d4f72;*/
          overflow-y: auto;
          width: calc(100%);
          /*margin-top: 20px;*/
          height: calc(350px - 56px);
          padding: 20px 40px 0 30px;
          position: relative;
        }
      }
    }
  }

  .info {
    height: 32px;
    border-left: 1px dashed #36465f;
    /*padding: 0 12px;*/
    margin-left: 30px;

    .leftMenus_item {
      padding: 0 12px;
      width: 100%;
      height: 100%;
    }
  }

  .serchInfo {
    display: flex;
    align-items: center;

    .icon-search {
      width: 15px;
      height: 15px;
      background: url("../../../../../assets/HJJ/ico/ico-search.png") no-repeat center;
      margin-left: 10px;
      /*padding-right: 12px;*/
      /*border-right: 1px solid #293c5d;*/
    }

    .borderI {
      margin-left: 10px;
      margin-right: 8px;
      width: 2px;
      height: 15px;
      background: #293c5d;
    }

    .icon-delete {
      width: 15px;
      height: 15px;
      background: url("../../../../../assets/HJJ/ico/ico-clear.png") no-repeat center;
    }
  }

  .infoDrop {
    height: 28px;
  }

  .activeKnowledge {
    color: #FFFFFF;
    font-weight: bolder;
  }

  .activeKnowledge2 {
    font-weight: bolder;
    font-size: 16px;
    background-image: linear-gradient(0deg, #344a5d, rgba(100, 141, 177, 0.99));
  }

  .Cmenus_title:hover {
    font-weight: bolder;
    font-size: 16px;
    background-image: linear-gradient(0deg, #344a5d, rgba(100, 141, 177, 0.99));
  }

  .createDrillBtn {

    width: 96px;
    height: 30px;
    color: #e2f2ff;
    cursor: pointer;
    font-size: 15px;
    text-align: center;
    line-height: 28px;
    background-image: linear-gradient(#70a3b8, #4c7595);
    box-shadow: 2px 2px 3px rgba(0, 0, 0, .2);
    border-radius: 2px;
  }

  .title_item1 {
    margin-bottom: 1px;
    height: 64px;
  }

  .title_item2 {
    width: 125px;
    height: 0;
    border-top: 5px solid #2a4163;
    border-left: 5px solid transparent;
    border-right: 5px solid transparent;
  }
}

.LJ {
  .bgColor {
    background: rgba(38, 41, 36, 0.3)
  }

  .disSelect {
    user-select: none;
  }

  .pre {
    position: absolute;
    width: 162px;
    height: 40px;
    font-size: 16px;
    cursor: pointer;
    color: #e9e9ea;
    line-height: 40px;
    left: 32%;
    bottom: 5%;
    background-image: url("../../../../../assets/LJ/basicTheory/studyManage/pre.png");
    background-repeat: no-repeat;
    text-align: right;
    padding-right: 50px;
  }

  .pre:hover {
    color: #ffffff;
    background-image: url("../../../../../assets/LJ/basicTheory/studyManage/pre-hover.png");
  }

  .next {
    width: 162px;
    height: 40px;
    color: #e9e9ea;
    line-height: 40px;
    font-size: 16px;
    position: absolute;
    right: 32%;
    bottom: 5%;
    cursor: pointer;
    padding-left: 50px;
    background-image: url("../../../../../assets/LJ/basicTheory/studyManage/next.png");
  }

  .next:hover {
    color: #ffffff;
    background-image: url("../../../../../assets/LJ/basicTheory/studyManage/next-hover.png");
  }

  :deep {
    th {
      text-align: center !important;
    }

    .ant-spin-nested-loading {
      height: 100%;
    }

    .ant-spin-container {
      height: 100%;
    }

    .ant-table-pagination.ant-pagination {
      position: absolute;
      bottom: -30px;
      left: 0;
      right: 0;
    }
  }

  .grouping:deep {
    .ant-select-selector {
      border: 0px !important
    }

    .ant-select {
      border: 0px !important
    }

    .ant-btn[disabled], .ant-btn[disabled]:hover, .ant-btn[disabled]:focus, .ant-btn[disabled]:active {
      color: rgba(226, 242, 255, 0.85) !important;
    }
  }

  .btns {
    background-image: linear-gradient(to right, #353c38 0%, #424f47 50%, #8f9593 100%) !important;
    text-transform: uppercase;
    background-size: 300% auto;
    /* box-shadow: inset 0px 0px 5px #007ac1; */
    border: 1px solid #5a615f;
    height: 32px;
    color: white;
    transition: all 1s;
    margin: 10px 0px 0px 8px;
  }

  .ant-btn:hover, .btns:hover {
    /*background-image: linear-gradient(to right, #2e4051 0%, #446782 50% ,#7d765c 100%)!important;*/
    background-position: right center;
    color: #ffffff;
    box-shadow: inset 0px 0px 5px #cac1a6;
    border: 1px solid #bbb193;
    transition: all 1s;
  }

  .bg {
    background-image: url("../../../../../assets/LJ/basicTheory/test/question_bank_bg.png");
    background-repeat: no-repeat;
    background-size: 100% 100%;
    color: #fff !important;
    font-size: 20px;
    font-weight: bold;
    text-align: center;
  }

  .menuQB {
    margin-top: 4px !important;
  }

  .menuQB:last-child {
    margin-bottom: 4px;
  }

  .questionBank {
    .shadow {
      position: fixed;
      z-index: 1000;
      top: 0;
      left: 0;
      width: 100%;
      height: 100%;
      background: rgba(0, 0, 0, .5);

      .shadowInfo {
        position: absolute;
        margin: auto;
        top: 0;
        left: 0;
        right: 0;
        bottom: 0;
        background: url("../../../../../assets/LJ/basicTheory/registerCenter.png") no-repeat center;
        background-size: 100% 100%;
        box-shadow: 0 0 20px rgba(0, 0, 0, .5);
        width: 1000px;
        height: 660px;
        /*padding-bottom: 20px;*/

        .title {
          margin: 0 auto;
          width: 100%;
          height: 40px;
          /*background:#2e4559 url("../../../../../assets/LJ/login/popTitle.png") no-repeat left center;*/
          /*box-shadow: 0 0 20px #1c2733;*/
          /*border-bottom: 1px solid #324b60;*/
          position: relative;

          .text {
            width: 100%;
            height: 100%;
            text-align: center;
            padding-left: 30px;
            line-height: 40px;
            font-weight: bold;
            color: #ffffff;
            font-size: 24px;
          }
        }

        .newClosePopup {
          right: 15px;
          top: 30px;
          z-index: 99;
        }

        .bigbox {
          /*background: linear-gradient(to bottom right ,#172842,#163a63);*/
          margin-top: 26px;
          display: flex;
          padding: 0 30px;

          .left {
            position: relative;
            width: 120px;
            height: calc(650px - 47px);

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
                background-image: url("../../../../../assets/LJ/train/tabs-check.jpg");
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
          height: 100px;
          display: flex;
          justify-content: center;
          align-items: center;

          .close {
            width: 31px;
            height: 31px;
            border: 2px solid #a9abaa;
            border-radius: 50%;
            background: url("../../../../../assets/LJ/ico/ico-clear.png") no-repeat center;
            cursor: pointer;
          }

          .close:hover {
            background: url("../../../../../assets/LJ/ico/ico-clear-1.png") no-repeat center;
            animation: rotate 0.4s linear;
            border-color: #34b34c;
          }
        }

        .bottominfo::before {
          content: '';
          width: 1px;
          height: 33px;
          background: url("../../../../../assets/LJ/ico/ico-lone.png");
          position: absolute;
          top: 660px;
          left: 50%;
        }

        .bottominfoTwo {
          display: flex;
          justify-content: center;
          align-items: center;
          height: 100px;

          .close {
            width: 31px;
            height: 31px;
            border: 2px solid #a9abaa;
            border-radius: 50%;
            background: url("../../../../../assets/LJ/ico/ico-clear.png") no-repeat center;
            cursor: pointer;
          }

          .close:hover {
            background: url("../../../../../assets/LJ/ico/ico-clear-1.png") no-repeat center;
            animation: rotate 0.4s linear;
            border-color: #34b34c;
          }
        }

        .bottominfoTwo::before {
          content: '';
          width: 1px;
          height: 33px;
          background: url("../../../../../assets/LJ/ico/ico-lone.png");
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
            height: calc(565px - 60px);
            padding: 0 40px 0 30px;
            position: relative;
          }
        }

        .bottom {
          .botBtn {
            width: 102px;
            height: 42px;
            background: url('../../../../../assets/LJ/train/button.png') no-repeat center/100% 100%;
            font-size: 16px;
            color: white;
          }
        }

        .rights {
          /*border-right: 1px solid #2d4f72;*/
          /*border-left: 1px solid #2d4f72;*/
          /*border-bottom: 1px solid #2d4f72;*/
          overflow-y: auto;
          width: calc(100%);
          height: calc(100% - 42px);
          /*margin-top: 20px;*/
          /*height: calc( 350px - 56px );*/
          padding: 20px 40px 0 30px;
          position: relative;
        }
      }
    }
  }

  .info {
    height: 32px;
    border-left: 1px dashed #36465f;
    /*padding: 0 12px;*/
    margin-left: 30px;

    .leftMenus_item {
      padding: 0 12px;
      width: 100%;
      height: 100%;
    }
  }

  .serchInfo {
    display: flex;
    align-items: center;

    .icon-search {
      width: 15px;
      height: 15px;
      background: url("../../../../../assets/LJ/ico/ico-search.png") no-repeat center;
      margin-left: 10px;
      /*padding-right: 12px;*/
      /*border-right: 1px solid #293c5d;*/
    }

    .borderI {
      margin-left: 10px;
      margin-right: 8px;
      width: 2px;
      height: 15px;
      background: #4f5653;
    }

    .icon-delete {
      width: 15px;
      height: 15px;
      background: url("../../../../../assets/LJ/ico/ico-clear.png") no-repeat center;
    }
  }

  .infoDrop {
    height: 28px;
  }

  .activeKnowledge {
    color: #FFFFFF;
    font-weight: bolder;
  }

  .activeKnowledge2 {
    font-weight: bolder;
    font-size: 16px;
    background: #533e1a;
  }

  .Cmenus_title:hover {
    font-weight: bolder;
    font-size: 16px;
    background: #533e1a;
  }

  .title_item1 {
    margin-bottom: 1px;
    height: 64px;
  }

  .title_item2 {
    width: 125px;
    height: 0;
    border-top: 5px solid #2a4163;
    border-left: 5px solid transparent;
    border-right: 5px solid transparent;
  }
}

.GD {
  .btns {
    background-image: linear-gradient(to right, #1b6a9a 0%, #2798d8 50%, #0f3b54 100%) !important;
    text-transform: uppercase;
    background-size: 300% auto;
    /* box-shadow: inset 0px 0px 5px #007ac1; */
    border: 1px solid #4ca6f6;
    height: 32px;
    color: white;
    transition: all 1s;
    margin: 10px 0px 0px 8px;
  }

  .ant-btn:hover, .btns:hover {
    /*background-image: linear-gradient(to right, #2e4051 0%, #446782 50% ,#7d765c 100%)!important;*/
    background-position: right center;
    color: #ffffff;
    box-shadow: inset 0px 0px 5px #cac1a6;
    border: 1px solid #4ca6f6;
    transition: all 1s;
  }
}


</style>