<template>
  <div class="w-full h-full layout-side">
      <div class="w-full h-full relative layout-center" style="min-width: 768px;">
        <div class="floatPopRight" v-if="userRole.id != '2'">
          <div class="deploy fs_dispose_1" @click="basicDeploy"><img :src="deployIco" class="ico">基础配置</div>
        </div>
        <div v-for="(item,i) in totalList" class="trainItem" @click="addDrillModalInfo(item.type)">
          <div class="title fs_dispose" v-if="interfaceStyle!=='HJ'">{{item.type==0?'基础练习':item.type==1?'连贯练习':'单码练习'}}</div>
          <img :src="itemImg5" v-if="item.type==0" class="itemImg">
          <img :src="itemImg7" v-else-if="item.type==1" class="itemImg">
          <img :src="itemImg15" v-else class="itemImg">
          <div class="title fs_dispose" v-if="interfaceStyle==='HJ'">{{item.type==0?'基础练习':item.type==1?'连贯练习':'单码练习'}}</div>
          <div class="totalData">
            <div class="item">
              <img :src="itemLab1" class="itemLab">
              <img :src="itemLabOn1" class="itemLab hover">
              <div>
                <div class="lab fs_dispose_1">训练时长</div>
                <div class="val fs_dispose_1">{{item.totalTime}}</div>
              </div>
            </div>
            <div class="item">
              <img :src="itemLab2" class="itemLab">
              <img :src="itemLabOn2" class="itemLab hover">
              <div>
                <div class="lab fs_dispose_1">练习次数</div>
                <div class="val fs_dispose_1">{{item.totalCount}} <span class="text">/次</span></div>
              </div>
            </div>
            <div class="item" v-if="item.type==1">
              <img :src="itemLab4" class="itemLab">
              <img :src="itemLabOn4" class="itemLab hover">
              <div>
                <div class="lab fs_dispose_1">当前阶段</div>
                <div class="val fs_dispose_1">{{prevTrain.ring}} <span class="text">轮</span> | {{prevTrain.stage}}<span class="text"> 阶段</span></div>
              </div>
            </div>
            <div class="item" v-if="item.type==2">
              <img :src="itemLab3" class="itemLab">
              <img :src="itemLabOn3" class="itemLab hover">
              <div>
                <div class="lab fs_dispose_1">平均码率</div>
                <div class="val fs_dispose_1" v-if="item.avgSpeed==0">--</div>
                <div class="val fs_dispose_1" v-else>{{parseFloat(item.avgSpeed).toFixed(2)}} <span class="text">{{wpmTOmm?'码/分':'WPM'}}</span></div>
              </div>
            </div>
          </div>
        </div>
      </div>
      <!--新增训练-->
      <a-modal :destroyOnClose="true"
               :width="560"
               class="init_modal_style footer-border-none"
               destroyOnClose="true"
               v-model:visible="addDrillModal"
               @cancel="cancelTrainModal">
        <template #title>
          <strong :style="{fontSize: (fs * 2 + 16) + 'px'}">练习配置</strong>
        </template>
        <template #footer>
          <div class="w-full layout-center">
            <div v-if="prevTrain.status == 2" @click="startTrain"
                 :class="{'createDrillBtn prev': true,'btn-animate': !loading,loadingBtn: loading}" style="width: 120px"
                 :style="{fontSize: (fs * 2 + 15) + 'px'}">
              继续上次练习
            </div>
            <div :class="{createDrillBtn: true,'btn-animate': !loading,loadingBtn: loading}"
                 @click="createDrillInfo" style="margin-left: 15px"
                 :style="{fontSize: (fs * 2 + 15) + 'px'}">
              <a-spin v-if="loading" size="small"/>
              {{prevTrain.status==2?'重新生成':'开始练习'}}
            </div>
          </div>
        </template>
        <a-spin :spinning="loading">
          <div class="configurationBox" style="padding-bottom: 0">
            <div class="groupBoxs">
              <div class="groupTitle">训练配置</div>
              <div class="rowItem" style="margin: 10px 0 0;">
                <div class="lab" :style="{fontSize: (fs * 1 + 13) + 'px'}">类型：</div>
                <div class="item" style="padding-left: 2px;">
                  <a-radio-group v-model:value="formData.type" @change="findPrevTrainMsg()">
                    <a-radio :value="1">数码报</a-radio>
                    <a-radio :value="0">字码报</a-radio>
                    <a-radio :value="2">混合报</a-radio>
                  </a-radio-group>
                </div>
              </div>
              <template v-for="(bd,index) in trainData.baoDi">
                <div class="rowItem" v-if="bd.type == formData.type">
                  <div class="lab" :style="{fontSize: (fs * 1 + 13) + 'px'}">{{ bd.text }}：</div>
                  <!--<div class="item relative">
                    <span class="absolute">报底</span>
                    <a-input-number v-model:value="bd.num" :min="1" :max="1000" placeholder="数量"
                                    style="width: 120px;padding-left: 40px"></a-input-number>
                  </div>-->
                  <div class="item relative">
                    <span class="absolute" :style="{fontSize: (fs * 1 + 12) + 'px'}">报文</span>
                    <a-input-number v-model:value="bd.bw" :min="1" placeholder="数量"
                                    style="width: 120px;padding-left: 40px"></a-input-number>
                  </div>
                  <div class="item" style="margin: 0;" v-if="bd.type == 1">
                    <a-switch v-model:checked="bd.numberType" checked-children="长码" un-checked-children="短码"></a-switch>
                  </div>
                </div>
              </template>
              <div class="rowItem">
                <div class="lab" :style="{fontSize: (fs * 1 + 13) + 'px'}">低速练习：</div>
                <div class="item layout-left-center">
                  <a-switch v-model:checked="formData.isLowRate" checked-children="是" un-checked-children="否"></a-switch>
                </div>
              </div>
              <div class="rowItem" v-if="!formData.isLowRate">
                <div class="lab" :style="{fontSize: (fs * 1 + 13) + 'px'}">播报码率：</div>
                <div class="item" style="width: 200px;">
                  <a-select v-model:value="playRate" placeholder="请选择播报码率" @change="selectRateInfo"
                            style="width: 200px;text-align: left">
                    <a-select-option v-for="(item, index) in basicDeployData" :value="item.speed">{{ item.name }}
                      ({{ item.speed }}{{wpmTOmm?'码/分':'WPM'}})
                    </a-select-option>
                    <a-select-option :value="0">自定义</a-select-option>
                  </a-select>
                </div>
              </div>
              <div class="rowItem" v-if="playRate == 0 && !formData.isLowRate">
                <div class="lab" :style="{fontSize: (fs * 1 + 13) + 'px'}">自定义码率：</div>
                <div class="item flex" style="width: 100%;align-items: center">
                  <div>40</div>
                  <div class="formSlider" style="width: 266px;margin: 0 5px">
                    <a-slider v-model:value="formData.rate" :min="40" :max="180"></a-slider>
                  </div>
                  <div>180</div>
                </div>
              </div>
              <div class="rowItem">
                <div class="lab" :style="{fontSize: (fs * 1 + 13) + 'px'}">随机：</div>
                <div class="item layout-left-center">
                  <a-switch v-model:checked="trainData.isRandom" checked-children="是" un-checked-children="否"></a-switch>
                </div>
              </div>
            </div>
          </div>
        </a-spin>
      </a-modal>

      <!--基础配置-->
      <a-modal :destroyOnClose="true"
               :width="530"
               class="init_modal_style footer-border-none"
               destroyOnClose="true"
               v-model:visible="basicTrainDeployModal"
               @cancel="cancelTrainModal">
        <template #title>
          <strong :style="{fontSize: (fs * 2 + 16) + 'px'}">基础配置</strong>
        </template>
        <template #footer>
          <div class="w-full layout-center">
            <div :class="{createDrillBtn: true,'btn-animate': !loading,loadingBtn: loading}"
                 :style="{fontSize: (fs * 2 + 15) + 'px'}" @click="saveDeploy">
              <a-spin v-if="loading" size="small"/>
              保存配置
            </div>
          </div>
        </template>
        <a-spin :spinning="loading">
          <div class="configurationBox">
            <div class="groupBoxs">
              <div class="groupTitle">播报码率配置</div>
              <div class="rowItem title mini">
                <div class="item" style="width: 80px;">类型</div>
                <div class="item">码率({{wpmTOmm?'码/分':'WPM'}})</div>
                <div class="item" style="width: 220px;">文案</div>
              </div>
              <div class="rowItem mini" v-for="(item, index) in basicDeployData">
                <div class="item" style="width: 80px;">
                  <a-input v-model:value="item.name" style="width: 100%;text-align: center"></a-input>
                </div>
                <div class="item flex">
                  <a-input-number v-model:value="item.speed" :min="40" :max="180" placeholder="播报码率"
                                  style="width: 100px;text-align: center"></a-input-number>
                </div>
                <div class="item relative" style="width: 220px;">
                  <a-input v-model:value="item.text" placeholder="请输入文案说明" style="width: 100%;"></a-input>
                  <div class="close" @click="closeBasicNorm(index)">
                    <CloseCircleOutlined/>
                  </div>
                </div>
              </div>
              <div class="rowItem mini">
                <div class="item" style="width: auto;">
                  <div class="item_group btn layout-center" @click="createBasicNorm">
                    <PlusOutlined style="margin-right: 6px"/>
                    新增码率
                  </div>
                </div>
              </div>
            </div>
            <div class="groupBoxs">
              <div class="groupTitle">低速配置</div>
              <div class="rowItem mini mt-2">
                <div class="item" style="width: 80px;text-align: center">点时长</div>
                <div class="item flex" style="align-items: center">
                  <a-input-number v-model:value="basicSpeed.dotTime" :min="10" :max="120" placeholder="播报码率"
                                  style="width: 100px;text-align: center"></a-input-number>
                  <div class="ml-1">ms</div>
                </div>
                <div class="auditionBtn" @click="auditionInfo" :style="{fontSize: (fs * 1 + 12) + 'px'}">试听</div>
              </div>
            </div>
          </div>
        </a-spin>
      </a-modal>
    </div>
</template>

<script>
export default {
  name: 'ReceivePractise'
}
</script>
<script setup>
import { ref} from 'vue'
import { PlusOutlined, CloseCircleOutlined } from '@ant-design/icons-vue'
import telegramList from './js/telegram'
import iconImage from "../../js/iconImage";

const {deployIco,itemImg5,itemImg7,itemImg15,itemLab1,itemLab2,itemLab3,itemLab4,itemLabOn1,itemLabOn3,itemLabOn4,itemLabOn2} = iconImage()
const interfaceStyle = window.interfaceStyle

const fs = ref(JSON.parse(localStorage.getItem('fs')));
import { inject } from 'vue'
const wpmTOmm = inject('wpmTOmm')

const userRole = ref(JSON.parse(localStorage.getItem('userRole')))
const {
  loading,
  addDrillModal,
  trainData,
  formData,
  totalList,
  prevTrain,
  playRate,
  basicTrainDeployModal,
  basicDeployData,
  addDrillModalInfo,
  cancelTrainModal,
  selectRateInfo,
  createDrillInfo,
  startTrain,
  basicDeploy,
  createBasicNorm,
  closeBasicNorm,
  saveDeploy,
  findPrevTrainMsg,
  basicSpeed,
  auditionInfo
} = telegramList()
</script>

<style scoped lang="less">
  :deep{
     .ant-modal-footer {
      border-top: none !important;
    }
  }

  .HJ{
    .grouping_content {
      display: flex;
      flex-direction: column;
      padding: 0;
    }
    .createDrillBtn {
      width: 96px;
      height: 30px;
      color: #e2f2ff;
      font-size: 15px;
      text-align: center;
      line-height: 28px;
      /*background-image: linear-gradient(#6cebfc, #006ea4);*/
      box-shadow: 2px 2px 3px rgba(0, 0, 0, .2);
      border-radius: 2px;
      background-image: linear-gradient(#22acff, #0068de);

    }
    .createDrillBtn.prev {
      background-image: linear-gradient(#e6c165, #bf6c2a);
    }
    .createDrillBtn.loadingBtn {
      cursor: no-drop;
      opacity: .8;
    }

    .configurationBox {
      padding-bottom: 20px;
      color: #fff;
    }

    .configurationBox .rowItem {
      padding: 8px 0;
      display: flex;
      align-items: center;
    }

    .configurationBox .rowItem.title {
      padding: 16px 0 0;
    }

    .configurationBox .rowItem .lab {
      width: 100px;
      flex-shrink: 0;
      color: #a5b6d0;
      text-align: right;
    }

    .configurationBox .rowItem .item {
      color: #a5b6d0;
      width: 120px;
      text-align: center;
    }

    .configurationBox .rowItem .item + .item {
      margin-left: 30px;
    }

    .configurationBox .rowItem .item .absolute {
      left: 0;
      top: 0;
      line-height: 32px;
      z-index: 9;
      padding: 0 8px;
      font-size: 12px;
      color: #a5b6d0;
      border-right: 1px solid #354971;
    }

    .configurationBox .rowItem.mini {
      padding-left: 6px;
    }

    .configurationBox .rowItem.mini .lab {
      width: 86px;
    }

    .configurationBox .rowItem.mini .item {
      width: 120px;
      margin-left: 12px;
    }

    .configurationBox .rowItem.mini .item .close {
      display: flex;
      margin-top: -7px;
      color: #d11d1d;
      position: absolute;
      right: -24px;
      top: 50%;
      cursor: pointer;
    }

    .configurationBox .rowItem .msg {
      height: 24px;
      font-size: 12px;
      color: #f8cf6f;
      display: flex;
      align-items: center;
      padding: 0 8px;
      background-color: #374a63;
    }

    .groupBoxs {
      position: relative;
      border: 1px solid #354971;
      margin-top: 20px;
      padding-bottom: 8px;
    }

    .groupBoxs .groupTitle {
      color: #bbcdef;
      line-height: 20px;
      padding: 0 10px;
      background-color: #1f3a61;
      position: absolute;
      left: 10px;
      top: -10px;
    }

    .auditionBtn {
      background-color: #009afd;
      line-height: 20px;
      flex-shrink: 0;
      font-size: 12px;
      padding: 0 10px;
      border-radius: 2px;
      cursor: pointer;
      margin-left: 20px;
    }
  }
  .HJJ{
    .grouping_content {
      display: flex;
      flex-direction: column;
      padding: 0;
    }

    .createDrillBtn {
      width: 96px;
      height: 30px;
      color: #e2f2ff;
      font-size: 15px;
      text-align: center;
      line-height: 28px;
      /*background-image: linear-gradient(#6cebfc, #006ea4);*/
      box-shadow: 2px 2px 3px rgba(0, 0, 0, 0.2);
      border-radius: 2px;
      background-image: linear-gradient(#70a3b8, #4c7595);
    }
    .createDrillBtn.prev {
      background-image: linear-gradient(#e6c165, #bf6c2a);
    }
    .createDrillBtn.loadingBtn {
      cursor: no-drop;
      opacity: 0.8;
    }

    .configurationBox {
      padding: 10px 20px 20px;
      color: #fff;
    }

    .configurationBox .rowItem {
      padding: 8px 0;
      display: flex;
      align-items: center;
    }

    .configurationBox .rowItem.title {
      padding: 16px 0 0;
    }

    .configurationBox .rowItem .lab {
      width: 100px;
      flex-shrink: 0;
      font-size: 13px;
      color: #7b90af;
      text-align: right;
    }

    .configurationBox .rowItem .item {
      font-size: 13px;
      color: #7b90af;
      width: 120px;
      text-align: center;
    }

    .configurationBox .rowItem .item + .item {
      margin-left: 30px;
    }

    .configurationBox .rowItem .item .absolute {
      left: 0;
      top: 0;
      line-height: 32px;
      z-index: 9;
      padding: 0 8px;
      font-size: 12px;
      color: #7b90af;
      border-right: 1px solid #354971;
    }

    .configurationBox .rowItem.mini {
      padding-left: 6px;
    }

    .configurationBox .rowItem.mini .lab {
      width: 86px;
    }

    .configurationBox .rowItem.mini .item {
      width: 120px;
      margin-left: 12px;
    }

    .configurationBox .rowItem.mini .item .close {
      display: flex;
      margin-top: -7px;
      color: #d11d1d;
      position: absolute;
      right: -24px;
      top: 50%;
      cursor: pointer;
    }

    .configurationBox .rowItem .msg {
      height: 24px;
      font-size: 12px;
      color: #f8cf6f;
      display: flex;
      align-items: center;
      padding: 0 8px;
      background-color: #374a63;
    }

    .groupBoxs {
      position: relative;
      border: 1px solid #3d586f;
      margin-top: 20px;
      padding-bottom: 8px;
    }

    .groupBoxs .groupTitle {
      font-size: 13px;
      color: #bbcdef;
      line-height: 20px;
      padding: 0 10px;
      background-color: #2e4559;
      position: absolute;
      left: 10px;
      top: -10px;
    }

    .auditionBtn {
      background-color: #38556d;
      line-height: 20px;
      flex-shrink: 0;
      font-size: 12px;
      padding: 0 10px;
      border-radius: 2px;
      cursor: pointer;
      margin-left: 20px;
    }

  }
  .LJ{
    .grouping_content {
      display: flex;
      flex-direction: column;
      padding: 0;
    }
    .configurationBox {
      padding: 10px 20px 20px;
      color: #fff;
    }
    .configurationBox .rowItem {
      padding: 8px 0;
      display: flex;
      align-items: center;
    }

    .configurationBox .rowItem.title {
      padding: 16px 0 0;
    }

    .configurationBox .rowItem .lab {
      width: 100px;
      flex-shrink: 0;
      font-size: 13px;
      color: #a9abaa;
      text-align: right;
    }

    .configurationBox .rowItem .item {
      font-size: 13px;
      color: #a9abaa;
      width: 120px;
      text-align: center;
    }

    .configurationBox .rowItem .item + .item {
      margin-left: 30px;
    }

    .configurationBox .rowItem .item .absolute {
      left: 0;
      top: 0;
      line-height: 32px;
      z-index: 9;
      padding: 0 8px;
      font-size: 12px;
      color: #a9abaa;
      border-right: 1px solid #26332e;
    }

    .configurationBox .rowItem.mini .lab {
      width: 86px;
    }

    .configurationBox .rowItem.mini .item {
      width: 120px;
      margin-left: 12px;
    }

    .configurationBox .rowItem.mini .item:last-child {
      margin-right: 12px;
    }

    .configurationBox .rowItem.mini .item .close {
      display: flex;
      margin-top: -7px;
      color: #d11d1d;
      position: absolute;
      right: -35px;
      top: 50%;
      cursor: pointer;
    }

    .configurationBox .rowItem .msg {
      height: 24px;
      font-size: 12px;
      color: #f8cf6f;
      display: flex;
      align-items: center;
      padding: 0 8px;
      background-color: #162520;
    }

    .groupBoxs {
      position: relative;
      border: 1px solid #3d586f;
      margin-top: 20px;
      padding-bottom: 8px;
    }

    .groupBoxs .groupTitle {
      font-size: 13px;
      color: #bbcdef;
      line-height: 20px;
      padding: 0 10px;
      background-color: #323736;
      position: absolute;
      left: 10px;
      top: -10px;
    }

    .auditionBtn {
      background-color: #0f7151;
      color: #ffffff;
      line-height: 20px;
      flex-shrink: 0;
      font-size: 12px;
      padding: 0 10px;
      border-radius: 2px;
      cursor: pointer;
      margin-left: 20px;
    }
  }
  .KJ{
    .grouping_content {
      display: flex;
      flex-direction: column;
      padding: 0;
    }
    .configurationBox {
      padding: 10px 20px 20px;
      color: #fff;
    }
    .configurationBox .rowItem {
      padding: 8px 0;
      display: flex;
      align-items: center;
    }

    .configurationBox .rowItem.title {
      padding: 16px 0 0;
    }

    .configurationBox .rowItem .lab {
      width: 100px;
      flex-shrink: 0;
      font-size: 13px;
      color: #a9abaa;
      text-align: right;
    }

    .configurationBox .rowItem .item {
      font-size: 13px;
      color: #a9abaa;
      width: 120px;
      text-align: center;
    }

    .configurationBox .rowItem .item + .item {
      margin-left: 30px;
    }

    .configurationBox .rowItem .item .absolute {
      left: 0;
      top: 0;
      line-height: 32px;
      z-index: 9;
      padding: 0 8px;
      font-size: 12px;
      color: black;
      border-right: 1px solid #26332e;
    }

    .configurationBox .rowItem.mini .lab {
      width: 86px;
    }

    .configurationBox .rowItem.mini .item {
      width: 120px;
      margin-left: 12px;
    }

    .configurationBox .rowItem.mini .item:last-child {
      margin-right: 12px;
    }

    .configurationBox .rowItem.mini .item .close {
      display: flex;
      margin-top: -7px;
      color: #d11d1d;
      position: absolute;
      right: -35px;
      top: 50%;
      cursor: pointer;
    }

    .configurationBox .rowItem .msg {
      height: 24px;
      font-size: 12px;
      color: #f8cf6f;
      display: flex;
      align-items: center;
      padding: 0 8px;
      background-color: #162520;
    }

    .groupBoxs {
      position: relative;
      border: 1px solid #3d586f;
      margin-top: 20px;
      padding-bottom: 8px;
    }

    .groupBoxs .groupTitle {
      font-size: 13px;
      color: #bbcdef;
      line-height: 20px;
      padding: 0 10px;
      background-color: #323736;
      position: absolute;
      left: 10px;
      top: -10px;
    }

    .auditionBtn {
      background-color: #0f7151;
      color: #ffffff;
      line-height: 20px;
      flex-shrink: 0;
      font-size: 12px;
      padding: 0 10px;
      border-radius: 2px;
      cursor: pointer;
      margin-left: 20px;
    }
  }
</style>
