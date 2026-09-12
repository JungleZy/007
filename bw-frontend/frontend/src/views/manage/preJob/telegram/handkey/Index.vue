<template>
  <div class="w-full h-full layout-side">
    <div class="w-full h-full relative layout-center" style="min-width: 768px;">
      <div class="floatPopRight" v-if="userRole.id != '2'">
        <div class="deploy fs_dispose_1" @click="basicDeploy"><img :src="deployIco" class="ico">基础配置</div>
      </div>
      <div v-for="(item,i) in totalList" class="trainItem" @click="addDrillModalInfo(item.type)">
        <div class="title fs_dispose" v-if="interfaceStyle!=='HJ'">{{item.type==0?'单字练习':item.type==1?'词组练习':'基础练习'}}</div>
        <img :src="itemImg1" v-if="item.type==0" class="itemImg">
        <img :src="itemImg6" v-else-if="item.type==1" class="itemImg">
        <img :src="itemImg5" v-else class="itemImg">
        <div class="title fs_dispose" v-if="interfaceStyle==='HJ'">{{item.type==0?'单字练习':item.type==1?'词组练习':'基础练习'}}</div>
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
          <div class="item" v-if="item.type!=2">
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
        <strong :style="{fontSize: (fs * 2 + 16) + 'px'}">手键拍发生成设置</strong>
      </template>
      <template #footer>
        <div class="w-full layout-center">
          <div v-if="prevTrain.status == 2" @click="startTrain"
               :class="{'createDrillBtn prev': true,'btn-animate': !loading,loadingBtn: loading}" style="width: 120px"
               :style="{fontSize: (fs * 2 + 15) + 'px'}">
            继续上次练习
          </div>
          <div :class="{createDrillBtn: true,'btn-animate': !loading,loadingBtn: loading}"
               @click="createDrillInfo" style="margin-left: 15px" :style="{fontSize: (fs * 2 + 15) + 'px'}">
            <a-spin v-if="loading" size="small"/>
            {{prevTrain.status==2?'重新生成':'开始练习'}}
          </div>
        </div>
      </template>
      <a-spin :spinning="loading">
        <div class="configurationBox">
          <div class="groupBoxs" v-if="trainData.way >= 0">
            <div class="groupTitle">训练配置</div>
            <div class="rowItem" style="margin: 20px 0 0;">
              <div class="lab">类型：</div>
              <div class="item" style="padding-left: 2px;">
                <a-radio-group v-model:value="formData.train.type" @change="findPrevTrainMsg">
                  <template v-if="trainData.way == 1">
                    <a-radio :value="11">点报</a-radio>
                    <a-radio :value="12">划报</a-radio>
                    <!--<a-radio :value="13">点划报</a-radio>-->
                    <a-radio :value="14">点划连接报</a-radio>
                  </template>
                  <template v-else>
                    <a-radio :value="1">数码报</a-radio>
                    <a-radio :value="0">字码报</a-radio>
                    <a-radio :value="2">混合报</a-radio>
                  </template>
                </a-radio-group>
              </div>
            </div>
            <template v-for="(bd,index) in trainData.baoDi">
              <div class="rowItem" v-if="bd.type == formData.train.type">
                <div class="lab">{{ bd.text }}：</div>
                <!--<div class="item relative">
                  <span class="absolute">报底</span>
                  <a-input-number v-model:value="bd.num" :min="1" :max="1000" placeholder="数量" style="width: 120px;padding-left: 40px"></a-input-number>
                </div>-->
                <div class="item relative">
                  <a-input-number v-model:value="bd.bw" :min="1" :max="100000" :formatter="(value) => (value>100000?100000:value)" placeholder="数量" style="width: 120px;"></a-input-number>
                </div>
                <div class="item" style="margin: 0;" v-if="bd.type == 1">
                  <a-switch v-model:checked="bd.numberType" checked-children="长码" un-checked-children="短码"></a-switch>
                </div>
              </div>
            </template>
            <div class="rowItem">
              <div class="lab">随机：</div>
              <div class="item layout-left-center">
                <a-switch v-model:checked="trainData.isRandom" checked-children="是" un-checked-children="否"></a-switch>
              </div>
            </div>
          </div>

          <div class="groupBoxs">
            <div class="groupTitle">拍发配置</div>
            <div class="rowItem title">
              <div class="lab"></div>
              <div class="item">最小值 (ms)</div>
              <div class="item">最大值 (ms)</div>
              <div class="item" style="margin: 0;" v-if="trainData.way >= 0">比例</div>
            </div>
            <template v-for="(deploy, index) in trainData.interval">
              <div class="rowItem" v-if="deploy.type < 2 || (deploy.type == 3 && formData.train.type > 10) || (deploy.type == 2 && trainData.way > -1)">
                <div class="lab">{{ deploy.text }}：</div>
                <div class="item">
                  <a-input-number v-if="deploy.type==0&&trainData.way == -1" v-model:value="basicLabTitle.dot[0]" disabled style="width: 120px;"></a-input-number>
                  <a-input-number v-else-if="deploy.type==1&&trainData.way == -1" v-model:value="basicLabTitle.line[0]" disabled style="width: 120px;"></a-input-number>
                  <a-input-number v-else-if="deploy.type<2" v-model:value="deploy.min" disabled style="width: 120px;"></a-input-number>
                  <a-input-number v-else v-model:value="deploy.min" disabled style="width: 120px;"></a-input-number>
                </div>
                <div class="item">
                  <a-input-number v-if="trainData.way >= 0" v-model:value="deploy.max" :min="2" :max="deploy.type==0?200:2000" :precision="0"
                                  :formatter="(value) => (value>2000?2000:value)" @change="handlePatDeployData(deploy)" style="width: 120px;"></a-input-number>
                  <a-input-number v-else-if="deploy.type==0&&trainData.way == -1" v-model:value="basicLabTitle.dot[1]" disabled style="width: 120px;"></a-input-number>
                  <a-input-number v-else-if="deploy.type==1&&trainData.way == -1" v-model:value="basicLabTitle.line[1]" disabled style="width: 120px;"></a-input-number>
                </div>
                <div class="item" style="margin: 0;" v-if="trainData.way >= 0">
                  <a-input-number v-if="deploy.type==0" v-model:value="deploy.scale" disabled style="width: 60px;"></a-input-number>
                  <a-input-number v-else v-model:value="deploy.scale" @change="handlePatDeployData()" :min="3" :max="10" :formatter="(value) => (value>10?10:value)" style="width: 60px;"></a-input-number>
                </div>
              </div>
            </template>
            <div class="rowItem" v-if="trainData.way ==  -1" style="padding-top: 0">
              <div class="lab"></div>
              <div class="msg"><WarningOutlined style="margin-right: 8px;font-size: 16px;"/>基础练习专用于个人练习点和划拍发的准确性！</div>
            </div>
          </div>
        </div>
      </a-spin>
    </a-modal>

    <!--基础配置-->
    <a-modal :destroyOnClose="true"
             :width="660"
             class="init_modal_style footer-border-none"
             destroyOnClose="true"
             v-model:visible="basicTrainDeployModal"
             @cancel="cancelTrainModal">
      <template #title>
        <strong  :style="{fontSize: (fs * 2 + 16) + 'px'}">基础配置</strong>
      </template>
      <template #footer>
        <div class="w-full layout-center">
          <div :class="{createDrillBtn: true,'btn-animate': !loading,loadingBtn: loading}"
               :style="{fontSize: (fs * 2 + 15) + 'px'}"
               @click="saveDeploy"><a-spin v-if="loading" size="small"/> 保存配置</div>
        </div>
      </template>
      <a-spin :spinning="loading">
        <div class="configurationBox">
          <div class="groupBoxs">
            <div class="groupTitle">基础练习拍发标准配置</div>
            <div class="rowItem title mini">
              <div class="item" style="width: 80px;">类型</div>
              <div class="item">点 ({{basicLabTitle.dot[0]}}-{{basicLabTitle.dot[1]}})</div>
              <div class="item">划 ({{basicLabTitle.line[0]}}-{{basicLabTitle.line[1]}})</div>
              <div class="item" style="width: 220px;">文案</div>
            </div>
            <div class="rowItem mini" v-for="(item, index) in basicDeployData">
              <div class="item" style="width: 80px;">
                <a-input v-model:value="item.name" :disabled="!item.prune"
                         :style="{width: '100%',opacity: item.prune?'1':'.8'}"></a-input>
              </div>
              <div class="item flex">
                <a-input-group compact :class="{disabled:item.type==0}">
                  <a-input v-model:value="item.dot.min" :disabled="item.type==0"
                           @change="changeBasicValue"
                           style="width: 50px;text-align: center;padding: 4px 0;"></a-input>
                  <a-input :placeholder="item.type==0?'&':'~'" disabled
                           style="width: 20px;border-left: none;border-right: none;pointer-events: none;padding: 4px 0;"></a-input>
                  <a-input v-model:value="item.dot.max" :disabled="item.type==0"
                           @change="changeBasicValue"
                           style="width: 50px;border-left: none;text-align: center;padding: 4px 0;"></a-input>
                </a-input-group>

              </div>
              <div class="item flex">
                <a-input-group compact :class="{disabled:item.type==0}">
                  <a-input v-model:value="item.line.min" :disabled="item.type==0"
                           @change="changeBasicValue"
                           style="width: 50px;text-align: center;padding: 4px 0;"></a-input>
                  <a-input :placeholder="item.type==0?'&':'~'" disabled
                           style="width: 20px;border-left: none;border-right: none;pointer-events: none;padding: 4px 0;"></a-input>
                  <a-input v-model:value="item.line.max" :disabled="item.type==0"
                           @change="changeBasicValue"
                           style="width: 50px;border-left: none;text-align: center;padding: 4px 0;"></a-input>
                </a-input-group>
              </div>
              <div class="item relative" style="width: 220px;">
                <a-input v-model:value="item.text" placeholder="请输入文案说明" style="width: 100%;"></a-input>
                <div class="close" v-if="item.prune" @click="closeBasicNorm(index)"><CloseCircleOutlined/></div>
              </div>
            </div>
            <div class="rowItem mini">
              <div class="item" style="width: auto;">
                <div class="item_group btn layout-center" @click="createBasicNorm">
                  <PlusOutlined style="margin-right: 6px"/> 新增标准
                </div>
              </div>
            </div>
          </div>
        </div>
      </a-spin>
    </a-modal>
  </div>
</template>

<script>
export default {
  name: 'HandKeyPat'
}
</script>
<script setup>
import { ref } from 'vue'
import { PlusOutlined, CloseCircleOutlined, WarningOutlined } from '@ant-design/icons-vue'
import telegramList from './js/telegram'
import iconImage from "../../js/iconImage";
const interfaceStyle = window.interfaceStyle
const {deployIco,itemImg5,itemLab1,itemLab2,itemLab3,itemLabOn1,itemLabOn3,itemLabOn2,itemImg1,itemImg6} = iconImage()
import { inject } from 'vue'
const fs = ref(JSON.parse(localStorage.getItem('fs')));
const wpmTOmm = inject('wpmTOmm')
const userRole = ref(JSON.parse(localStorage.getItem('userRole')))
const {
  loading,
  addDrillModal,
  trainData,
  formData,
  totalList,
  prevTrain,
  basicTrainDeployModal,
  basicLabTitle,
  basicDeployData,
  addDrillModalInfo,
  cancelTrainModal,
  handlePatDeployData,
  createDrillInfo,
  startTrain,
  basicDeploy,
  changeBasicValue,
  createBasicNorm,
  closeBasicNorm,
  saveDeploy,
  findPrevTrainMsg
} = telegramList()
</script>

<style scoped lang="less">
  :deep{
    .ant-modal-footer {
      border-top: none !important;
    }
  }
  .HJ{
    .createDrillBtn {
      width: 96px;
      height: 30px;
      color: #e2f2ff;
      font-size: 15px;
      text-align: center;
      line-height: 28px;
      box-shadow: 2px 2px 3px rgba(0,0,0,.2);
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
      color: #7b90af;
      text-align: right;
    }
    .configurationBox .rowItem .item {
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

  }
  .HJJ{
    .createDrillBtn {
      width: 96px;
      height: 30px;
      color: #e2f2ff;
      font-size: 15px;
      text-align: center;
      line-height: 28px;
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
  }
  .LJ{

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
    .configurationBox .rowItem.mini {
      padding-left: 6px;
    }
    .configurationBox .rowItem.mini .lab {
      width: 86px;
    }
    .configurationBox .rowItem.mini .item {
      width: 130px;
      margin-left: 12px;
    }
    .configurationBox .rowItem.mini .item .close {
      display: flex;
      margin-top: -7px;
      color: #d11d1d;
      position: absolute;
      right: -24px;
      top: 50%;
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
      background-color: #2e4559;
      position: absolute;
      left: 10px;
      top: -10px;
    }
  }
  .KJ{

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
    .configurationBox .rowItem.mini {
      padding-left: 6px;
    }
    .configurationBox .rowItem.mini .lab {
      width: 86px;
    }
    .configurationBox .rowItem.mini .item {
      width: 130px;
      margin-left: 12px;
    }
    .configurationBox .rowItem.mini .item .close {
      display: flex;
      margin-top: -7px;
      color: #d11d1d;
      position: absolute;
      right: -24px;
      top: 50%;
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
      background-color: #2e4559;
      position: absolute;
      left: 10px;
      top: -10px;
    }
  }
</style>