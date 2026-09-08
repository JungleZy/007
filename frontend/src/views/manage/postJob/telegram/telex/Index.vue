<template>
  <div class="w-full h-full overflow-hidden layout-side">
    <div class="w-full h-full">
      <div class="w-full h-full grouping_content content-mask-bg">
        <div class="w-full table_search_box">
          <!--          <div class="item_group btn" @click="goWordTrain">单字训练</div>-->
          <div class="item_group btn" @click="addDrillModalInfo"><PlusOutlined />新增训练</div>
<!--          <div class="item_group btn" @click="gradingRuleModalInfo"><SettingOutlined />&nbsp;评分规则</div>-->
          <div class="item_group btn" ><SettingOutlined />&nbsp;评分规则</div>
        </div>

        <div class="w-full" style="max-height: calc(100% - 70px)">
          <div class="table_list_box overflow-auto" style="height: calc(100% - 35px); padding: 0 10px">
            <a-table :columns="columns" :loading="tableLoading" :rowKey="record => record.id" :pagination="false" :data-source="tableList">
              <template #type="{ text }">
                {{ text == 0 ? '数字连贯' : text == 1 ? '字母连贯' : text == 2 ? '混合码' : '' }}
              </template>
              <template #duration="{ text }">
                {{ text ? partTimeFormatInfo(parseInt(text * 1000), 'chinese') : '--' }}
              </template>
              <template #speed="{ text }">
                {{ text == null ? '--' : text + '组/分' }}
              </template>
              <template #status="{ text }">
                <span class="tag finish" v-if="text == 3">已完成</span>
                <span class="tag pause" v-else-if="text == 2">已暂停</span>
                <span class="tag oper" v-else-if="text == 1">进行中</span>
                <span class="tag" v-else>未开始</span>
              </template>
              <template #action="{ record }">
                <div class="flex layout-center">
                  <div class="table_action_btn">
                    <div class="table_btn" :title="record.status == 3 ? '查看成绩' : record.status == 2 ? '继续训练' : '开始训练'" @click="startTrain(record)">
                      <FileTextOutlined v-if="record.status == 3" />
                      <PlayCircleOutlined v-else />
                    </div>
                    <!--<div class="table_btn" title="删除">
                      <DeleteOutlined/>
                    </div>-->
                  </div>
                </div>
              </template>
            </a-table>
          </div>
          <div class="table_pagination">
            <div class="total">共{{ tableData.length }}条数据</div>
            <div class="item prev" @click="selectTablePage('-')"></div>
            <template v-for="(item, i) in Math.ceil(tableData.length / 10)">
              <div :class="{ item: true, active: item == currTablePage }" v-if="item > currTablePage - 3 && item < currTablePage + 3" @click="selectTablePage(item)">{{ item }}</div>
            </template>
            <div class="item next" @click="selectTablePage('+')"></div>
          </div>
        </div>
      </div>
    </div>
    <!--新增训练-->
    <a-modal :destroyOnClose="true" :width="560" class="init_modal_style footer-border-none" destroyOnClose="true" v-model:visible="addDrillModal" @cancel="cancelTrainModal">
      <template #title>
        <strong>新增数据报拍发训练</strong>
      </template>
      <template #footer>
        <div class="w-full layout-center">
          <div @click="addTelexTrain" :class="{ createDrillBtn: true, 'btn-animate': !loading, loadingBtn: loading }">生成训练</div>
        </div>
      </template>
      <a-spin :spinning="loading">
        <div class="configurationBox">
          <div class="groupBoxs">
            <div class="groupTitle">训练配置</div>
            <div class="rowItem" style="margin: 20px 0 0">
              <div class="lab">类型：</div>
              <div class="item" style="padding-left: 2px">
                <a-radio-group v-model:value="trainData.type" @change="selectType">
                  <a-radio :value="0">数字连贯</a-radio>
                  <a-radio :value="1">字母连贯</a-radio>
                  <a-radio :value="2">混合码</a-radio>
                </a-radio-group>
              </div>
            </div>
            <div class="rowItem" style="padding-top: 0">
              <div class="lab"></div>
              <div class="msg" v-if="trainData.type == 0"><WarningOutlined style="margin-right: 8px; font-size: 16px" />数字连贯，纯数字报文连贯练习！</div>
              <div class="msg" v-if="trainData.type == 1"><WarningOutlined style="margin-right: 8px; font-size: 16px" />字母连贯，纯字母报文连贯练习！</div>
              <div class="msg" v-if="trainData.type == 2"><WarningOutlined style="margin-right: 8px; font-size: 16px" />组合连贯，数字、字母组合报文连贯练习！</div>
            </div>
          </div>

          <div class="groupBoxs">
            <div class="groupTitle">报文配置</div>
            <div class="rowItem" style="margin: 20px 0 0">
              <div class="lab">电报纸数：</div>
              <div class="item">
                <a-input-number :step="1" :min="1" :precision="0" v-model:value="trainData.count"></a-input-number>
              </div>
            </div>
            <div class="rowItem">
              <div class="lab">评分规则：</div>
              <div class="item">
                <a-select v-model:value="trainData.ruleId" style="width: 150px; text-align: left">
                  <a-select-option v-for="v of rule" :value="v.id">{{ v.title }}</a-select-option>
                </a-select>
              </div>
            </div>
          </div>
        </div>
      </a-spin>
    </a-modal>
    <!--新增训练-->
    <a-modal :destroyOnClose="true" :width="980" class="init_modal_style footer-border-none" destroyOnClose="true" centered v-model:visible="gradingRuleModal" @cancel="cancelGradingRuleModal">
      <template #title>
        <strong>数据报拍发评分规则管理</strong>
      </template>
      <template #footer>
        <div class="w-full layout-center"></div>
      </template>
      <TelexGardRule />
    </a-modal>
  </div>
</template>

<script>
export default {
  name: 'TelexPost'
}
</script>
<script setup>
import NipUEditor from '../../../../../components/common/NipUEditor.vue'
import { useRouter, useRoute } from 'vue-router'
import { ref, onMounted, provide } from 'vue'
import { PlayCircleOutlined, DeleteOutlined, FileTextOutlined, PlusOutlined, CloseCircleOutlined, WarningOutlined, SettingOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import telegramList from './js/telex'
import { timeFormatInfo, partTimeFormatInfo } from '../../../../../common/utils/Utils.js'
import TelexGardRule from '../../../../../components/gradingRule/TelexGardRule.vue'
import * as gr from '../../../../../common/api/GradingRuleApi'
const router = useRouter()
const route = useRoute()
const loading = ref(false)
const addDrillModal = ref(false)
const gradingRuleModal = ref(false)
const basicTrainDeployModal = ref(false)
const gradingRuleModalInfo = () => {
  gradingRuleModal.value = true
}
const cancelGradingRuleModal = () => {
  gradingRuleModal.value = false
}
//格式化时间
const computationTime = total => {
  let hour
  let min
  let sec
  let day
  let h
  let m
  let s
  hour = Math.floor((total / 60 / 60) % 24)
  min = Math.floor((total / 60) % 60)
  sec = Math.floor(total % 60)
  day = Math.floor(total / 60 / 60 / 24)
  // 计算总小时数
  hour = hour + day * 24
  if (hour < 10 && hour >= 0) {
    h = '0' + hour
  } else {
    h = hour.toString()
  }
  if (min < 10 && min >= 0) {
    m = '0' + min
  } else {
    m = min
  }
  if (sec < 10 && sec >= 0) {
    s = '0' + sec
  } else {
    s = sec
  }
  return h + ' : ' + m + ' : ' + s
}
const { columns, tableData, tableList, currTablePage, selectTablePage, tableLoading, selectType, initContent, trainData, addTelexTrain, rule, getRule } = telegramList(addDrillModal)
const startTrain = record => {
  let str
  if (record.status == 3) {
    str = '/trainScore'
  } else {
    str = '/postJobTelexTrain'
  }
  router.push({
    path: route.matched[4].path + str,
    query: {
      id: record.id
    }
  })
}
/**
 * 新增训练
 */
const addDrillModalInfo = () => {
  addDrillModal.value = true
  getRule()
}
/**
 * 关闭弹窗
 */
const cancelTrainModal = () => {
  trainData.value.count = 1
  addDrillModal.value = false
}
</script>

<style scoped>
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
  /*width: 120px;*/
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
.init_modal_style >>> .ant-modal-footer {
  border-top: none !important;
}
</style>
