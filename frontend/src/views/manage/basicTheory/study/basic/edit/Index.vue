<template>
  <div class="w-full h-full overflow-hidden layout-side editBox">
    <div class="h-full content-mask-bg leftBox" style="width: 300px">
      <div class="w-full p-2 h-full">
        <div class="w-full" style="display: flex; align-items: center; flex-direction: column">
          <div class="mb-1 layout-center" style="width: 98%">
            <button-style>
              <template v-slot:content>
                <div class="w-full editBookPic" style="height: 180px; min-width: 100px; max-width: 100%">
                  <a-upload v-model:file-list="fileList" name="avatar" list-type="picture-card" class="avatar-uploader" :data="{ currentPath: '006/cover/main/' + new Date().getTime() }" :show-upload-list="false" :action="uploadFileUrl" :before-upload="beforeUpload" @change="handleChange">
                    <!--                  alt="avatar"-->
                    <img v-if="tData.knowledge.cover" :src="fileUrl + tData.knowledge.cover" style="height: 170px" />
                    <div v-else>
                      <loading-outlined v-if="loading"></loading-outlined>
                      <plus-outlined v-else></plus-outlined>
                      <div class="ant-upload-text">上传</div>
                    </div>
                  </a-upload>
                </div>
              </template>
            </button-style>
            <div class="w-full mb-3 mt-1 layout-center" style="color: #506079">支持jpg,png,bmp格式</div>
          </div>
          <div class="w-full mb-3 layout-left-center">
            <div class="w-1/6">标题</div>
            <div class="w-5/6">
              <a-input v-model:value="tData.knowledge.title" placeholder="请输入标题"></a-input>
            </div>
          </div>
          <div class="w-full mb-3 layout-left-center">
            <div class="w-2/6">状态</div>
            <div class="w-4/6">
              <a-switch checked-children="开启" un-checked-children="关闭" v-model:checked="tData.knowledge.status" />
            </div>
          </div>
          <div class="w-full mb-3 layout-left-center">
            <div class="w-2/6">学分</div>
            <div class="w-4/6">
              <a-input-number step="0.5" :min="0" :max="100" v-model:value="tData.knowledge.credit"></a-input-number>
            </div>
          </div>
          <div class="w-full mb-3 layout-left-center">
            <div class="w-2/6">专业分类</div>
            <div class="w-4/6">
              <a-select style="width: 150px; height: 34px" v-model:value="tData.knowledge.specialtyId">
                <a-select-option v-for="v of searchList.specialtyList" :key="v.id" :value="v.id">{{ v.name }}</a-select-option>
              </a-select>
            </div>
          </div>
          <div class="w-full mb-3 layout-left-center">
            <div class="w-2/6">难易分类</div>
            <div class="w-4/6">
              <a-select style="width: 150px; height: 34px" v-model:value="tData.knowledge.difficultyId">
                <a-select-option v-for="v of searchList.difficultyList" :key="v.id" :value="v.id">{{ v.name }}</a-select-option>
              </a-select>
            </div>
          </div>
        </div>
        <div class="w-full mb-1">
          <div class="takenew">
            <div class="title">课件列表</div>
            <div class="layout-center mr-1 cursor-pointer-def addClass"@click="addSwf"><PlusOutlined style="margin-right: 5px"></PlusOutlined>新增课件</div>
          </div>
        </div>
        <div class="w-full overflow-auto" style="height: calc(100% - 443px - 100px)">
          <div class="w-full cursor-pointer-def" v-for="(ks, index) in tData.knowledgeSwfs" :key="index" @click="handleSelectedKnowledgeSwfs(index, ks)">
            <div class="w-full relative layout-left-center cursor-pointer-def" style="" :class="[selectedKnowledgeSwfsIndex === index ? 'left-flag' : 'no-left-flag']">
              <div style="width: 32px;height: 32px;background:rgb(37 69 109);border-bottom: 1px solid #17233b;font-style: oblique" :class="[selectedKnowledgeSwfsIndex === index ? 'flgbg' : 'noflgbg']" class="layout-center relative">
                <img :src="leftico" style="position: absolute; top: 9px; left: 0; width: 2px; height: 14px" class="infoTag" />
                {{ index + 1 < 10 ? '0' : '' }}{{ index + 1 }}
              </div>
              <div style="height: 32px;width: calc(100% - 32px);background:rgb(37 69 109);border-bottom: 1px solid #17233b;border-left:1px solid #17233b;padding-left: 8px;" :class="[selectedKnowledgeSwfsIndex === index ? 'flgbg' : 'noflgbg']" class="layout-left-center truncate">
                <div class="w-full truncate relative" style="padding-right: 20px">
                  {{ ks.title == '' ? '未命名的课件标题' : ks.title }}
                  <CloseOutlined style="position: absolute; top: 4px; right: 5px; color: #cae0f3" @click.stop="deleteCurseware(index)" class="closeOutLined"></CloseOutlined>
                </div>
              </div>
            </div>
          </div>
        </div>
        <div class="w-full layout-center" style="height: 35px">
          <div style="width: 65%; display: flex; justify-content: space-between">
            <div class="item_group btn" @click="handleOptions(0)">取消 <RollbackOutlined class="ml-1"></RollbackOutlined></div>
            <div class="item_group btn" @click="handleOptions(1)">提交 <CheckOutlined style="" class="ml-1"></CheckOutlined></div>
          </div>
          <!--          <a-button class="mr-2" @click="handleOptions(0)">取消</a-button>-->
          <!--          <a-button type="primary" @click="handleOptions(1)">提交</a-button>-->
        </div>
      </div>
    </div>
    <div class="h-full p-2 content-mask-bg relative rightBox" style="width: calc(100% - 310px)">
      <div class="penInfo layout-center cursor-pointer-def" @click="showTest"></div>
      <div class="w-full layout-left-bottom relative" style="height: 100px">
        <div class="layout-left-bottom">
          <!--          <div style="width: 80px">课件封面:</div>-->
          <div style="width: calc(100% - 0px)" class="tt-img">
            <a-upload v-model:file-list="fileList" name="avatar" list-type="picture-card" class="avatar-uploader" :data="{ currentPath: '006/cover/swf/' + new Date().getTime() }" :show-upload-list="false" :action="uploadFileUrl" :before-upload="beforeUpload" @change="handleChange">
              <img v-if="selectedKnowledgeSwfs.cover" :src="fileUrl + selectedKnowledgeSwfs.cover" alt="avatar" style="height: 80px" />
              <div v-else>
                <loading-outlined v-if="loading"></loading-outlined>
                <plus-outlined v-else></plus-outlined>
                <div class="ant-upload-text">上传</div>
              </div>
            </a-upload>
          </div>
        </div>
        <div class="layout-left-center pl-2" style="width: calc(100% - 78px); margin-bottom: 15px">
          <div style="width: 50px">标题:</div>
          <div style="width: calc(100% - 50px)">
            <a-input v-model:value="selectedKnowledgeSwfs.title" placeholder="默认课件标题" />
          </div>
        </div>
      </div>
      <div class="w-full overflow-hidden relative editor">
        <NipUEditor :top="0" />
        <div v-if="uploadType" class="layout-center UploadLoading">文档上传中...</div>
      </div>
    </div>
    <div class="shadow fade-in" v-if="testVisible">
      <div class="shadowInfo">
        <div class="title">
          <div class="text">随 堂 测 验</div>
        </div>
        <div class="newClosePopup" @click="takeNoTestVisible"><div class="closeIco"></div></div>
        <div class="bigbox relative">
          <div class="left" style="">
            <div class="botBox">
              <div class="item_group btn layout-center" style="width: 100px; margin-top: 9px" @click="addLeft">添加测验</div>
            </div>
            <div class="topBox">
              <div class="leftNocheck cursor-pointer-def" @click="editCheck(item, i)" v-for="(item, i) in leftArr" :key="i" :class="[testIndex == i ? 'leftCheck' : '']">
                <div class="leftBorder" v-if="testIndex == i"></div>
                <div class="leftText">{{ item.title }}</div>
                <a-switch :checked="item.versions == 1" checked-children="启用" un-checked-children="停用" @change="changeVersions(item, i, $event)"></a-switch>
                <div class="bg1" style="" :class="[testIndex == i ? 'bg2' : '']">{{ item.knowledgeTestContents.length }}</div>
              </div>
            </div>
          </div>
          <div class="center" style="height: calc(100% - 56px); margin-top: 15px">
            <div class="addClass btn item_group layout-center mb-1" @click="addClass" style="margin-left: 20px">添加题目</div>
            <div class="right" ref="right">
              <div v-if="rightValue.length === 0" class="w-full layout-center">
                <div class="nomore">
                  <p style="color: #7b90af">暂无数据！</p>
                </div>
              </div>
              <template v-if="rightShow">
                <room-test ref="roomtest" v-for="(item, index) in rightValue" :key="index" :selectedKnowledgeSwfs="selectedKnowledgeSwfs" :index="index" :params="item" :isShort="1" class="">
                  <template v-slot:delete>
                    <DeleteOutlined title="删除该题" @click="deleteClassItem(index)" class="" style="color: #d11d1d; font-size: 20px"></DeleteOutlined>
                  </template>
                </room-test>
              </template>
            </div>
            <div style="padding: 0 30px 0 30px">
              <a-divider style="background-color: #354971; margin: 10px 0px; width: 90%"></a-divider>
            </div>
            <div class="bottom layout-center" style="height: 59px">
              <div class="botBtn layout-center cursor-pointer-def" style="margin-left: -120px" @click="bornTest">生成测验</div>
            </div>
          </div>
        </div>
        <!--<div class="w-full bottominfo">
          <div class="close" @click="takeNoTestVisible"></div>
        </div>-->
      </div>
    </div>
    <a-upload name="file" style="display: none" :action="action" :showUploadList="false" accept=".txt,.md,.csv" :headers="headers" :before-upload="beforeUploadFile" @change="uploadChange">
      <a-button id="uploadBtn" style="display: none">上传</a-button>
    </a-upload>
  </div>
</template>

<script>
export default {
  name: 'TheoryEdit'
}
</script>

<script setup>
import icoPen from '../../../../../../assets/HJ/ico/ico-pen.png'
import RoomTest from '../../../../../../components/test/roomTest/RoomTest.vue'
import { useRouter, useRoute } from 'vue-router'
import { LoadingOutlined, PlusOutlined, SaveOutlined, EditOutlined, DeleteOutlined, CloseOutlined, RollbackOutlined, CheckOutlined, ExclamationCircleOutlined } from '@ant-design/icons-vue'
import NipUEditor from '../../../../../../components/common/NipUEditor.vue'
import leftico from '../../../../../../assets/HJ/train/left-ico.png'
import { createVNode, onUnmounted, provide, ref } from 'vue'
import useForm from './js/useForm.js'
import useEdit from './js/useEdit.js'

import useUpload from '../../../../../../common/mixin/useUpload.js'
import { apiUrl } from '../../../../../../common/http/endpoint.js'
import { PubSub } from '../../../../../../common/utils/PubSub.js'
import { message, Modal } from 'ant-design-vue'
import { isUploadSizeAllowed, uploadSizeMessage } from '../../../../../../common/utils/uploadLimits.js'
const roomtest = ref()
const fileUrl = ref(window.fileUrl)
const content = ref('')
const uploadType = ref(false)
const action = ref(apiUrl('/api/theoryKnowledge/uploadFileToNip'))
const token = window.localStorage.getItem('token')
const deviceId = window.localStorage.getItem('deviceId')
const headers = ref({ token, deviceId })
const uploadChange = ({ file }) => {
  if (file.status !== 'done' && file.status !== 'error') return
  uploadType.value = false
  const response = file.response
  if (file.status === 'error' || response?.code !== 200) {
    file.status = 'error'
    message.error(response?.message || '文档上传失败，请重试')
    return
  }
  const wordContent = response.data?.wordContent
  if (typeof wordContent !== 'string' || !wordContent.trim()) {
    file.status = 'error'
    message.error('文档内容为空或响应格式错误')
    return
  }
  const paragraph = document.createElement('p')
  paragraph.style.textIndent = '2em'
  content.value += wordContent.split(/\r\n|\r|\n/).map(line => {
    paragraph.textContent = line
    return paragraph.outerHTML
  }).join('')
}
const beforeUploadFile = file => {
  if (!/\.(txt|md|csv)$/i.test(file.name)) {
    message.error('仅支持 UTF-8 纯文本文档（txt/md/csv）')
    return false
  }
  if (!isUploadSizeAllowed(file)) {
    message.error(uploadSizeMessage())
    return false
  }
  uploadType.value = true
  return true
}
provide('content', content)
const uploadFile = () => {
  const q = document.getElementById('uploadBtn')
  q.click()
}
provide('uploadFile', uploadFile)
const backFlag = ref(true)
import { deleteThroyKnowledgeById } from '../../../../../../common/api/TestApi.js'
const route = useRoute()
const router = useRouter()
const { tData, selectedKnowledgeSwfs, selectedKnowledgeSwfsIndex, addSwf, handleSelectedKnowledgeSwfs, handleOptions, deleteCurseware } = useForm(content, backFlag)

// content.value = '<img src="http://10.10.0.99:8003/ueditor/image/20220215/(无水印)欧参-短波通信_00.png"/>'
const right = ref(null)
const { uploadFileUrl, fileList, loading, handleChange, beforeUpload } = useUpload(res => {
  if (res.indexOf('/main/') > -1) {
    tData.value.knowledge.cover = res
  } else {
    selectedKnowledgeSwfs.value.cover = res
  }
})
const {
  showTest,
  testVisible,
  leftCheck,
  editCheck,
  changeVersions,
  // typeCheckList,
  // Type
  bornTest,
  leftArr,
  addLeft,
  rightValue,
  testIndex,
  rightShow,
  addClass,
  deleteClassItem,
  takeNoTestVisible,
  searchList
} = useEdit(roomtest, selectedKnowledgeSwfs, tData, content, selectedKnowledgeSwfsIndex, right, handleSelectedKnowledgeSwfs)
PubSub.subscribe('send_theoryEdit_close', () => {
  if (backFlag.value) {
    Modal.confirm({
      title: () => '当前信息未保存，确定要退出吗?',
      content: () => '退出后未保存信息会全部清空',
      icon: () => createVNode(ExclamationCircleOutlined),
      okType: 'danger',
      okText: () => '确定退出',
      cancelText: () => '取消',
      onOk() {
        if (tData.value.knowledge.id != undefined && route.query.type == 0) {
          deleteThroyKnowledgeById({
            id: tData.value.knowledge.id
          }).then(res => {
            PubSub.publishSync('callback_theoryEdit_close', true)
          })
        } else {
          PubSub.publishSync('callback_theoryEdit_close', true)
        }
      }
    })
  } else {
    PubSub.publishSync('callback_theoryEdit_close', true)
  }
})

onUnmounted(() => {
  PubSub.unsubscribe('send_theoryEdit_close')
})
</script>

<style lang="less">
  @import "./css/KJ";
  .mt-4px {
    margin-top: 4px;
  }
  .mt-20px {
    margin-top: 20px;
  }
  .editBookPic {
    display: flex;
    justify-content: center;
    .ant-upload {
      display: flex;
      justify-content: center;
    }
  }
  .editModal {
    padding: 0;
  }
  .editBookPic .ant-upload-select-picture-card {
    width: 100% !important;
    height: 100% !important;
  }
  .tt-img .ant-upload-select-picture-card {
    width: 70px !important;
    height: 70px !important;
  }
  .HJ{
    .editor{height: calc(100% - 100px);background: #183a66;border: 1px solid #28476a;}
    .takenew{
      display: flex;align-items: center;justify-content: space-between;border-top: 2px solid #28476a;
      .title{
        border-top: 2px solid #6ebdff;padding-top: 5px;color:#6ebdff;font-weight:600;padding-left: 2px ;margin-top: -2px;
      }
      .addClass{
        padding-top: 5px; color: #7b90af; margin-top: -2px
      }
    }
    .UploadLoading {
      height: 100%;
      width: 100%;
      position: absolute;
      top: 0;
      left: 0;
      background: rgba(0, 0, 0, 0.5);
      z-index: 1000;
      color: white;
    }
    .editBox {
      .shadow {
        position: fixed;
        z-index: 1000;
        top: 0;
        left: 0;
        width: 100%;
        height: 100%;
        background: rgba(0, 0, 0, .5);

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
          height: 680px;
          background-image: url("../../../../../../assets/HJ/train/modal-bg.jpg");
          background-size: 1000px 160px;
          background-repeat: no-repeat;

          .title {
            margin: 0 auto;
            width: 338px;
            height: 56px;
            background-image: url("../../../../../../assets/HJ/train/title-bg.png");
            background-size: 338px 56px;
            background-repeat: no-repeat;
            position: relative;

            .text {
              width: 100%;
              height: 100%;
              background-image: -webkit-linear-gradient(bottom, #44aaff, #ffffff);
              -webkit-background-clip: text;
              -webkit-text-fill-color: transparent;
              text-align: center;
              line-height: 56px;
              font-size: 30px;
              font-family: "Microsoft Yahei";
              font-weight: bold;
            }
          }

          .bigbox {
            /*background: linear-gradient(to bottom right ,#172842,#163a63);*/

            margin-top: 20px;
            border-left: 1px solid #2d4f72;
            border-right: 1px solid #2d4f72;
            border-bottom: 1px solid #2d4f72;
            display: flex;

            .left {
              position: relative;
              width: 160px;
              height: calc(650px - 47px);
              box-shadow: rgb(79 102 117) -14px -2px 6px -14px inset;
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
                    display: none;
                    height: 100%;
                    position: absolute;
                    left: 0;
                    top: 0;
                    width: 2px;
                    background: #6ebdff;
                  }

                  .delete {
                    display: none;
                  }

                  position: relative;
                  width: 100%;
                  display: flex;
                  align-items: center;
                  justify-content: space-between;
                  padding: 0 10px;
                  font-size: 15px;
                  height: 37px;
                  margin-bottom: 1px;

                  &:hover, &.leftCheck {
                    background-image: url("../../../../../../assets/HJ/train/tabs-check.jpg");
                    background-size: 100%;

                    .leftBorder, .delete {
                      display: block;
                    }
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
                  }

                  .bg2 {
                    background: #6ebdff;
                    color: #274666;
                  }
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
              background: url("../../../../../../assets/HJ/ico/ico-clear.png") no-repeat center;
              cursor: pointer;
            }

            .close:hover {
              background: url("../../../../../../assets/HJ/ico/ico-clear-1.png") no-repeat center;
              animation: rotate 0.4s linear;
              border-color: #6ebdff;
            }
          }

          .bottominfo::before {
            content: '';
            width: 1px;
            height: 33px;
            background: url("../../../../../../assets/HJ/ico/ico-lone.png");
            position: absolute;
            top: 680px;
            left: 50%;
          }

          .center {
            .addClass {
              width: 100px;
              /*position: absolute;*/
              /*bottom: 0px;*/
              /*right: -230px;*/
            }

            width: calc(100% - 160px);
            /*padding-top: 11px;*/
            display: flex;
            flex-direction: column;

            .right {
              overflow-y: auto;
              width: calc(100%);
              margin-top: 20px;
              height: calc(510px - 40px - 21px);
              padding: 0 20px;
              position: relative;
            }
          }

          .bottom {
            .botBtn {
              width: 110px;
              height: 32px;
              background-image: url("../../../../../../assets/HJ/train/button.png");
            }
          }
        }
      }
    }
    .penInfo {
      width: 77px;
      height: 77px;
      position: absolute;
      right: 12px;
      top: calc(50% - 38px);
      z-index: 9999;
      background: url('../../../../../../assets/HJ/basicTheory/stcy.png');
    }
    .left-flag {
      color: #6ebdff;
      .closeOutLined {
        display: block;
      }
      .infoTag {
        display: block;
      }
    }
    .no-left-flag {
      color: #c4dafb;
      .closeOutLined {
        display: none;
      }
      .infoTag {
        display: none;
      }
    }
    .no-left-flag:hover {
      color: #6ebdff;
      .closeOutLined {
        display: block;
      }
      .infoTag {
        display: block;
      }
    }
    .editBookPic .ant-upload.ant-upload-select-picture-card {
      border: 1px solid #374954;
      margin-right: 0px;
      margin-bottom: 0px;
    }
    .tt-img .ant-upload.ant-upload-select-picture-card {
      border: 1px solid #374954;
    }
    .nomore {
      background: url('../../../../../../assets/HJ/train/nomore.png') no-repeat 100%;
      height: 293px;
      width: 290px;
      display: flex;
      align-items: flex-end;
      justify-content: center;
      font-size: 20px;
      color: #00a0e9;
    }
  }
  .HJJ{
    .editor{height: calc(100% - 100px); background: #141e28; border: 1px solid #374954}
    .takenew{
      display: flex; align-items: center; justify-content: space-between; border-top: 2px solid #2e4051;
      .title{
      border-top: 2px solid #70a3b8; padding-top: 5px; color: #70a3b8; font-weight: 600; padding-left: 2px; margin-top: -2px;
      }
      .addClass{
        padding-top: 5px; color: #7b90af; margin-top: -2px;
      }
    }
    .UploadLoading {
      height: 100%;
      width: 100%;
      position: absolute;
      top: 0;
      left: 0;
      background: rgba(0, 0, 0, 0.5);
      z-index: 1000;
      color: white;
    }
    .editBox {
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
          /*background-color: #0e2446;*/
          width: 1000px;
          height: 680px;
          background: #2e4559;
          .title {
            margin: 0 auto;
            width: 100%;
            height: 40px;
            background-image: url('../../../../../../assets/HJJ/login/popTitle.png');
            background-repeat: no-repeat;
            position: relative;
            border-bottom: 1px solid #324b60;
            box-shadow: 0px 7px 17px -5px #1c2733;
            .text {
              width: 100%;
              height: 100%;
              color: #ffffff;
              text-align: center;
              line-height: 40px;
              font-size: 30px;
              font-family: 'Microsoft Yahei';
              font-weight: bold;
            }
          }
          .bigbox {
            /*background: linear-gradient(to bottom right ,#172842,#163a63);*/

            margin-top: 20px;
            display: flex;
            height: calc(100% - 40px);
            .left {
              position: relative;
              width: 160px;
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
                    background: #e2d6b0;
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
                    background: #e2d6b0;
                    color: #274666;
                  }
                }
                .leftCheck {
                  /*background-image:url("../../../../../../assets/train/tabs-check.jpg");*/
                  /*background-size: 100%;*/
                  background: #575e5d;
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
          .bigbox::before {
            position: absolute;
            content: '';
            width: 100%;
            height: 66px;
            bottom: 0;
            background: url('../../../../../../assets/HJJ/home/model_before.jpg') no-repeat;
            background-size: 100% 100%;
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
            top: 680px;
            left: 50%;
          }
          .center {
            .addClass {
              width: 100px;
              /*position: absolute;*/
              /*bottom: 0px;*/
              /*right: -230px;*/
            }
            width: calc(100% - 160px);
            /*padding-top: 11px;*/
            display: flex;
            flex-direction: column;
            .right {
              overflow-y: auto;
              width: calc(100%);
              margin-top: 20px;
              height: calc(510px - 40px - 21px);
              padding: 0 40px 0 30px;
              position: relative;
            }
          }
          .bottom {
            .botBtn {
              width: 102px;
              height: 38px;
              color: #000000;
              font-weight: bold;
              font-size: 15px;
              z-index: 9;
              background-image: url('../../../../../../assets/HJJ/login/registerBtn.png');
            }
          }
        }
      }
    }
    .penInfo {
      width: 77px;
      height: 77px;
      position: absolute;
      right: 12px;
      top: calc(50% - 38px);
      z-index: 9999;
      background: url('../../../../../../assets/HJJ/basicTheory/stcy.png');
    }
    .flgbg {
      background: #8e8578 !important;
      color: #ffffff;
    }
    .left-flag {
      color: #70a3b8;
      .closeOutLined {
        display: block;
      }
      .infoTag {
        display: block;
      }
    }
    .no-left-flag {
      color: #bfcde0;
      .closeOutLined {
        display: none;
      }
      .infoTag {
        display: none;
      }
    }
    .no-left-flag:hover {
      color: #ffffff;
      .noflgbg {
        background: #8e8578 !important;
      }
      .closeOutLined {
        display: block;
      }
      .infoTag {
        display: block;
      }
    }
    .left-flag:before {
      /*content: '';*/
      /*position: absolute;*/
      /*width: 2px;*/
      /*background: #1890ff;*/
      /*height: 100%;*/
      /*left: -2px;*/
    }
    .editBookPic .ant-upload.ant-upload-select-picture-card {
      border: 1px solid #374954;
      margin-right: 0px;
      margin-bottom: 0px;
    }
    .tt-img .ant-upload.ant-upload-select-picture-card {
      border: 1px solid #374954;
    }
    .nomore {
      background: url('../../../../../../assets/HJJ/train/nomore.png') no-repeat 100%;
      height: 293px;
      width: 290px;
      display: flex;
      align-items: flex-end;
      justify-content: center;
      font-size: 20px;
      color: #00a0e9;
    }
  }
  .LJ{
    .editor{
      height: calc(100% - 100px);
      background: #181a19;
      border: 1px solid #1d2d27;
    }
    .takenew{
      display: flex; align-items: center; justify-content: space-between; border-top: 2px solid rgb(25, 39, 34);;
      .title{
        border-top: 2px solid #a9abaa;padding-top: 5px;color:#00910e;font-weight:600;padding-left: 2px ;margin-top: -2px;
      }
      .addClass{
        padding-top: 5px; color: rgb(169, 171, 170); margin-top: -2px
      }
    }
    .modalBtn {
      background: rgba(0, 0, 0, 0) !important;
      border: #4f545a 2px solid !important;
      color: #35383a !important;
      span {
        color: #35383a !important;
      }
    }

    .UploadLoading {
      height: 100%;
      width: 100%;
      position: absolute;
      top: 0;
      left: 0;
      background: rgba(0, 0, 0, 0.5);
      z-index: 1000;
      color: white;
    }
    .editBox {
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
          background-image: url("../../../../../../assets/LJ/basicTheory/registerCenter.png");
          background-size: 100% 100%;
          background-repeat: no-repeat;
          width: 1000px;
          height: 680px;
          .title {
            margin: 0 auto;
            width: 100%;
            height: 40px;
            background-repeat: no-repeat;
            position: relative;
            .text {
              width: 100%;
              height: 100%;
              color: #ffffff;
              text-align: center;
              line-height: 40px;
              font-size: 24px;
              font-family: "Microsoft Yahei";
              font-weight: bold;
            }
          }
          .bigbox {
            margin-top: 20px;
            display: flex;
            height: calc(100% - 40px);
            .left {
              position: relative;
              width: 200px;
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
                  width: 130px;
                  height: 40px;
                  margin-left: 25px;
                  margin-bottom: 10px;
                  background: url("../../../../../../assets/LJ/train/test-title.png")
                  no-repeat center;
                  background-size: 103%;
                  .leftBorder {
                    height: 100%;
                    position: absolute;
                    left: 0;
                    top: 0;
                    width: 2px;
                    background: #e2d6b0;
                  }
                  .ant-switch::after {
                    background: #fff;
                  }

                  .ant-switch-checked {
                    background-color: #33b34b !important;
                  }
                  position: relative;
                  width: 170px;
                  display: flex;
                  align-items: center;
                  justify-content: space-between;
                  padding: 0 10px;
                  font-size: 15px;
                  &:hover .delMenuBtn {
                    display: flex;
                    align-items: center;
                    height: 20px;
                    font-size: 16px;
                  }
                  .leftText {
                  }
                  .bg1 {
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    border-radius: 16px;
                    padding: 0 4px;
                    min-width: 16px;
                    height: 16px;
                    background: #37383b;
                    color: #ffffff;
                    font-size: 14px;
                  }
                  .bg2 {
                    background: #895515;
                  }
                  .delMenuBtn {
                    display: none;
                  }
                }
                .leftCheck {
                  background: url("../../../../../../assets/LJ/train/checked-test-title.png")
                  no-repeat center;
                  background-size: 103%;
                  .delMenuBtn {
                    display: flex;
                    align-items: center;
                    height: 20px;
                    font-size: 16px;
                  }
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
          .bigbox::before {
            position: absolute;
            content: "";
            width: 100%;
            height: 66px;
            bottom: 0;
            background-size: 100% 100%;
          }
          .bottominfo {
            height: 100px;
            display: flex;
            justify-content: center;
            align-items: center;
            .close {
              width: 31px;
              height: 31px;
              border: 2px solid #a9abaa;
              border-radius: 50%;
              background: url("../../../../../../assets/LJ/ico/ico-clear.png")
              no-repeat center;
              cursor: pointer;
            }
            .close:hover {
              background: url("../../../../../../assets/LJ/ico/ico-clear-1.png")
              no-repeat center;
              animation: rotate 0.4s linear;
              border-color: #34b34c;
            }
          }
          .bottominfo::before {
            content: "";
            width: 1px;
            height: 33px;
            background: url("../../../../../../assets/LJ/ico/ico-lone.png");
            position: absolute;
            top: 680px;
            left: 50%;
          }
          .center {
            .addClass {
              width: 100px;
              background: url("../../../../../../assets/LJ/basicTheory/addQuesBtn.png")
              no-repeat;
              background-size: 100% 100%;
              .addIco {
                width: 13px;
                height: 13px;
                margin-right: 2px;
                background: url("../../../../../../assets/LJ/ico/ico-active-plus.png")
                no-repeat;
              }
            }
            width: calc(100% - 160px);
            display: flex;
            flex-direction: column;
            .right {
              background: #6e6a5e;
              overflow-y: auto;
              width: calc(94%);
              margin: 20px;
              margin-bottom: 0px;
              height: calc(510px - 40px - 21px);
              padding: 20px 40px 0 30px;
              position: relative;
            }
          }
          .bottom {
            .botBtn {
              width: 102px;
              height: 42px;
              /*color: #000000;*/
              font-weight: bold;
              font-size: 15px;
              z-index: 9;
              background-image: url("../../../../../../assets/LJ/train/button.png");
            }
          }
        }
      }
    }
    .penInfo {
      width: 77px;
      height: 77px;
      position: absolute;
      right: 12px;
      top: calc(50% - 38px);
      z-index: 9999;
      background: url("../../../../../../assets/LJ/basicTheory/stcy.png");
    }
    .flgbg {
      background: #343c39 !important;
      color: #ffffff;
    }

    .left-flag {
      color: #70a3b8;
      .closeOutLined {
        display: block;
      }
      .infoTag {
        display: block;
      }
    }
    .no-left-flag {
      color: #fff;
      .closeOutLined {
        display: none;
      }
      .infoTag {
        display: none;
      }
    }
    .no-left-flag:hover {
      color: #ffffff;
      .noflgbg {
        background: #343c39 !important;
      }
      .closeOutLined {
        display: block;
      }
      .infoTag {
        display: block;
      }
    }
    .editBookPic .ant-upload.ant-upload-select-picture-card {
      border: 1px solid #374954;
      margin-right: 0px;
      margin-bottom: 0px;
    }
    .tt-img .ant-upload.ant-upload-select-picture-card {
      border: 1px solid #374954;
    }
    .nomore {
      background: url("../../../../../../assets/LJ/train/nomore.png") no-repeat center 100%;
      height: 293px;
      width: 290px;
      display: flex;
      align-items: flex-end;
      justify-content: center;
      font-size: 20px;
      color: #00a0e9;
    }
  }


</style>
