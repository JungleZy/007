<template>
  <div class="w-full h-full layout-side">
    <div class="w-full h-full relative layout-center" style="min-width: 768px;">
      <div v-for="(item,i) in trainData.total" class="trainItem" @click="addDrillModalInfo(item.type)">
        <div class="title fs_dispose" v-if="interfaceStyle!=='HJ'">{{item.type==0?'单字练习':item.type==1?'数字连贯':item.type==2?'字母连贯':'混合码'}}</div>
        <img :src="itemImg1" v-if="item.type==0" class="itemImg">
        <img :src="itemImg2" v-else-if="item.type==1" class="itemImg">
        <img :src="itemImg3" v-else-if="item.type==2" class="itemImg">
        <img :src="itemImg4" v-else class="itemImg">
        <div class="title fs_dispose" v-if="interfaceStyle==='HJ'">{{item.type==0?'单字练习':item.type==1?'数字连贯':item.type==2?'字母连贯':'混合码'}}</div>
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
          <div class="item" v-if="item.type > 0">
            <img :src="itemLab3" class="itemLab">
            <img :src="itemLabOn3" class="itemLab hover">
            <div>
              <div class="lab fs_dispose_1">平均码率</div>
              <div class="val fs_dispose_1" v-if="item.avgSpeed==0">--</div>
              <div class="val fs_dispose_1" v-else>{{parseFloat(item.avgSpeed).toFixed(2)}} <span class="text">码/分</span></div>
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
        <strong :style="{fontSize: (fs * 2 + 16) + 'px'}">{{trainData.type==0?'数字连贯':trainData.type==1?'字母连贯':'组合连贯'}}拍发练习</strong>
      </template>
      <template #footer>
        <div class="w-full layout-center">
          <div v-if="trainData.prev.status == 2" @click="startTrain"
               :class="{'createDrillBtn prev': true,'btn-animate': !loading,loadingBtn: loading}" style="width: 120px"
               :style="{fontSize: (fs * 2 + 15) + 'px'}">
            继续上次练习
          </div>
          <div @click="addTelexTrain" :class="{createDrillBtn: true,'btn-animate': !loading,loadingBtn: loading}" style="margin-left: 15px"
               :style="{fontSize: (fs * 2 + 15) + 'px'}">
            {{trainData.prev.status==2?'重新生成':'开始练习'}}
          </div>
        </div>
      </template>
      <a-spin :spinning="loading">
        <div class="configurationBox" style="padding-bottom: 10px">
          <div class="groupBoxs">
            <div class="groupTitle">报文配置</div>
            <div class="rowItem" style="margin: 20px 0 0;">
              <div class="lab">报文组数：</div>
              <div class="item layout-left-center" style="width: auto">
                <a-input-number v-model:value="trainData.count" style="width: 120px;margin-right: 10px"></a-input-number>
              </div>
            </div>
            <div class="rowItem" style="padding-top: 0">
              <div class="lab"></div>
              <div class="msg" v-if="trainData.type ==  0"><WarningOutlined style="margin-right: 8px;font-size: 16px;"/>纯数字报文连贯练习！</div>
              <div class="msg" v-if="trainData.type ==  1"><WarningOutlined style="margin-right: 8px;font-size: 16px;"/>纯字母报文连贯练习！</div>
              <div class="msg" v-if="trainData.type ==  2"><WarningOutlined style="margin-right: 8px;font-size: 16px;"/>数字、字母组合报文连贯练习！</div>
            </div>
          </div>
        </div>
      </a-spin>
    </a-modal>

  </div>
</template>

<script>
export default {
  name: 'TelexPat'
}
</script>
<script setup>
import { useRouter, useRoute } from 'vue-router'
import { ref, onMounted, provide } from 'vue'
import { WarningOutlined } from '@ant-design/icons-vue'
import telegramList from './js/telex'

import iconImage from "../../js/iconImage";
const {itemImg1,itemImg2,itemImg3,itemImg4,itemLab1,itemLab2,itemLab3,itemLabOn1,itemLabOn2,itemLabOn3} = iconImage()
const interfaceStyle = window.interfaceStyle
const router = useRouter()
const route = useRoute()
const loading = ref(false)
const addDrillModal = ref(false)
const fs = ref(JSON.parse(localStorage.getItem('fs')));
const { selectType, trainData, addTelexTrain } = telegramList(addDrillModal)

const startTrain = () => {
  router.push({
    path: route.matched[4].path + '/telexTrain',
    query: { id: trainData.value.prev.id }
  })
}

/**
 * 新增训练
 */
const addDrillModalInfo = type => {
  if (type === 0) {
    router.push({
      path: route.matched[4].path + '/wordTrain'
    })
  } else {
    trainData.value.type = type
    selectType()
  }
}

/**
 * 关闭弹窗
 */
const cancelTrainModal = () => {
  addDrillModal.value = false
}
</script>

<style scoped>
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
    font-size: 13px;
    color: #bbcdef;
    line-height: 20px;
    padding: 0 10px;
    background-color: #1f3a61;
    position: absolute;
    left: 10px;
    top: -10px;
  }
  .init_modal_style >>> .ant-modal-footer {
    border-top: none !important;
  }
</style>