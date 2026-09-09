<template>
  <!--  要点讲解-->
  <div class="w-full h-full bg" >
    <div class="titleBg" style="display: flex;justify-content: center;overflow: hidden">
      <div class="h-full layout-left-top title1"  v-if="interfaceStyle==='HJ'">
        <img :src="ydjj03" alt="">
        <div class="titles"></div>
      </div>
      <div class="h-full title2 layout-center">
        <div class="titleL"  v-if="interfaceStyle==='HJ'"></div>
        <div class="titleR"  v-if="interfaceStyle==='HJ'"></div>
        <div class="label label3 fs_dispose" style="padding-left: 30px" :class="{labelS:activeS==0}" @click="labelClick(0)">手键拍发</div>
        <!--<div class="label label2" :class="{labelS:activeS==1}" @click="labelClick(1)">电子键拍发</div>-->
        <div class="label label3 fs_dispose" style="padding-right: 30px" :class="{labelS:activeS==1}" @click="labelClick(1)">电子键拍发</div>
      </div>
      <div class="h-full layout-right-top title1"  v-if="interfaceStyle==='HJ'">
        <div class="titles"></div>
        <img :src="ydjj09" alt="">
      </div>
    </div>
    <div class="w-full layout-center" style="height: calc(100% - 120px); padding: 20px; overflow: auto; display: flex">
      <div class="layout-side" v-for="(item, index) in listData" style="flex-wrap: nowrap; max-width: 800px; transition: all 0.5s; margin-right: 10px" :style="{ width: active === index ? 'calc(100% - ' + (listData.length - 1) * 100 + 'px)' : '80px' }">
        <div class="list" :class="{ listTwo: active === index }">
          <div class="w-full h-full timu" :class="{ timu1: active === index }" @click="clickTitle(index)">
            <div class="labNum">
              <div class="num">{{ index + 1 }}</div>
            </div>
            <div class="data" :class="{ data1: active === index }">{{ item.name }}</div>
          </div>
        </div>
        <div class="listBodyTwo" :class="{ listBody: active === index }">
          <div class="h-full" style="width: 100%; padding: 10px" v-if="item.type == 0">
            <a-textarea v-model:value="item.content" disabled v-if="active === index" style="width: 100%; height: 100%; padding: 10px; color: #e6ebf1; resize: none"></a-textarea>
          </div>
          <div class="h-full" style="width: 100%; padding: 10px; display: flex" v-if="item.type == 1">
            {{}}
            <video controls loop :src="fileUrl + '/' + item.content[0].url"></video>
          </div>
          <div class="h-full" style="width: 100%; padding: 10px; overflow: auto" v-if="item.type == 2">
            <img v-for="j in item.content" :src="fileUrl + '/' + j.url" alt="" />
          </div>
          <div class="h-full layout-center" style="width: 100%; padding: 10px" v-if="item.type == 3">
            <playAudio :url="fileUrl + '/' + item.content[0].url" />
          </div>
        </div>
      </div>
    </div>
    <div class="layout-center">
      <div class="item_group btn layout-center" style="margin-top: 2px; margin-left: 8px" @click="openAddMainPoints">
        <IconFont type="icon-tianjia1" color="#f60" style="font-size: 16px; margin-right: 5px"></IconFont>
        添加
      </div>
      <div class="item_group btn layout-center" style="margin-top: 2px; margin-left: 8px" @click="modifyMainPoints">
        <IconFont type="icon-jiandati1" color="#f60" style="font-size: 16px; margin-right: 5px"></IconFont>
        修改
      </div>
      <div class="item_group btn layout-center" style="margin-top: 2px; margin-left: 8px" @click="deleteMainPoints">
        <IconFont type="icon-shanchu1" color="#f60" style="font-size: 16px; margin-right: 5px"></IconFont>
        删除
      </div>
    </div>
    <a-modal :destroyOnClose="true" :width="900" class="init_modal_style footer-border-none" destroyOnClose="true" v-model:visible="addDrillModal" @cancel="addDrillModal = false">
      <template #title>
        <strong>{{ newKnowledge.update ? '修改要点' : '新增要点' }} </strong>
      </template>
      <template #footer>
        <div class="w-full layout-center">
          <div class="createDrillBtn" @click="addMainPoints">{{ newKnowledge.update ? '修改要点' : '新增要点' }}</div>
        </div>
      </template>
      <div style="padding: 20px 30px">
        <p>
          <span style="color: #fff"> 节点名：</span>
          <a-input v-model:value="newKnowledge.name" maxLength="6" placeholder="请填写要点名" style=""></a-input>
        </p>
        <p>
          <span style="color: #fff"> 类型：</span>
          <a-radio-group v-model:value="newKnowledge.type" @change="clickMainPoints(newKnowledge.type)">
            <a-radio-button :disabled="newKnowledge.update" value="0">文字</a-radio-button>
            <a-radio-button :disabled="newKnowledge.update" value="1">视频</a-radio-button>
            <a-radio-button :disabled="newKnowledge.update" value="2">图片</a-radio-button>
            <a-radio-button :disabled="newKnowledge.update" value="3">音频</a-radio-button>
          </a-radio-group>
        </p>
        <p v-if="newKnowledge.type == 0">
          <span style="color: #fff"> 内容：</span>
          <a-textarea :rows="12" v-model:value="newKnowledge.content" placeholder="请填写内容" style=""></a-textarea>
        </p>
        <div v-else>
          <span style="color: #fff"> 内容：</span>
          <div style="margin-left: 50px">
            <a-upload v-model:file-list="fileList" list-type="picture-card" class="avatar-uploader" :data="{ currentPath: 'mainPoints' }" :show-upload-list="true" :max-count="newKnowledge.type == 2 ? 10 : 1" :action="uploadFileUrl" :before-upload="beforeUpload" @change="handleChange">
              <div v-if="fileList.length < 1 && newKnowledge.type == 1" class="ant-upload-text">上传视频</div>
              <div v-if="fileList.length < 10 && newKnowledge.type == 2" class="ant-upload-text">上传图片</div>
              <div v-if="fileList.length < 1 && newKnowledge.type == 3" class="ant-upload-text">上传音频</div>
            </a-upload>
          </div>
        </div>
      </div>
    </a-modal>
  </div>
</template>

<script>
export default {
  name: 'FocusExplain'
}
</script>
<script setup>
import { createFromIconfontCN } from '@ant-design/icons-vue'
import explain from './js/explain'
import ydjj03 from '../../../../../assets/HJ/explain/ydjj03.png'
import ydjj09 from '../../../../../assets/HJ/explain/ydjj09.png'
const interfaceStyle = window.interfaceStyle
const IconFont = createFromIconfontCN({
  scriptUrl: window.iconUrl
})
const { uploadFileUrl, fileUrl, fileList, listData, active, activeS, addDrillModal, newKnowledge, mainPointsType, clickTitle, labelClick, openAddMainPoints, modifyMainPoints, deleteMainPoints, addMainPoints, clickMainPoints, handleChange, beforeUpload } = explain()
</script>
<style scoped lang="less">
  @import "../../../../../common/styles/css/explain.less";
  .HJ{
    .title2 {
      max-width: 855px!important;
      min-width: 855px!important;
      box-sizing: border-box;
      position: relative;
      padding-top: 6px;
    }
    .title1{
      width: calc((100% - 855px) / 2);
    }
  }


</style>
