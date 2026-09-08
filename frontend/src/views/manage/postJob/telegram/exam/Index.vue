<template>
  <div class="w-full h-full overflow-hidden layout-side">
    <div class="w-full h-full">
      <div class="w-full h-full grouping_content content-mask-bg">
        <div class="w-full table_search_box">
          <div class="item_group btn" @click="addDrillModalInfo">
            <PlusOutlined/>&nbsp;新增训练
          </div>
          <!--          <div class="item_group btn" v-if="userRole.id != '2'" @click="gradingRuleModalInfo"><SettingOutlined />&nbsp;评分规则</div>-->
          <div class="item_group btn"  @click="gradingRuleModalInfo">
            <SettingOutlined/>&nbsp;评分规则
          </div>
        </div>

        <div class="w-full" style="max-height: calc(100% - 70px)">
          <div class="table_list_box overflow-auto" style="height: calc(100% - 35px);">
            <a-table :columns="columns" :loading="tableLoading" :rowKey="record => record.id" :pagination="false"
                     :data-source="tableList">
              <template #isCable="{text}">
                {{ text == 0 ? '随机报' : '固定报' }}
              </template>
              <template #duration="{ text }">
                {{ text && text > 0 ? partTimeFormatInfo(parseInt(text * 1000), 'chinese') : '--' }}
              </template>
              <template #speed="{ text }">
                {{ text }}
              </template>
              <template #messageType="{ text }">
                {{ text == 1 ? '字码报' : text == 2 ? '混合报' : '数码报' }}
              </template>
              <template #score="{ record }">
                {{ record.status == 2?record.score:"100" }}
              </template>
              <template #status="{ text }">
                <span class="tag finish" v-if="text == 2">已完成</span>
                <span class="tag oper" v-else-if="text == 1">进行中</span>
                <span class="tag" v-else>未开始</span>
              </template>

              <template #action="{ record }">
                <div class="flex layout-center">
                  <div class="table_action_btn">
                    <div class="table_btn" :title="record.status == 2 ? '查看成绩' : '开始训练'" @click="startTrain(record)">
                      <FileTextOutlined v-if="record.status == 2"/>
                      <PlayCircleOutlined v-else/>
                    </div>
                    <div class="table_btn">
                      <DeleteOutlined style="color: red;" title="删除" @click="deleteModel(record)" />
                    </div>
                  </div>
                </div>
              </template>
            </a-table>
          </div>
          <div class="table_pagination">
            <div class="total">共{{ tableData.length }}条数据</div>
            <div class="item prev" @click="selectTablePage('-')"></div>
            <template v-for="(item, i) in Math.ceil(tableData.length / 10)" :key="i">
              <div
                  :class="{ item: true, active: item == currTablePage }"
                  v-if="item > currTablePage - 3 && item < currTablePage + 3"
                  @click="selectTablePage(item)"
              >
                {{ item }}
              </div>
            </template>
            <div class="item next" @click="selectTablePage('+')"></div>
          </div>
        </div>
      </div>
    </div>
    <!--新增训练-->
    <a-modal
        :destroyOnClose="true"
        :width="560"
        class="init_modal_style footer-border-none"
        v-model:visible="addDrillModal"
        @cancel="cancelTrainModal"
    >
      <template #title>
        <strong>新增电子键拍发训练</strong>
      </template>
      <template #footer>
        <div class="w-full layout-center">
          <div @click="addTelexTrain" :class="{ createDrillBtn: true, 'btn-animate': !loading, loadingBtn: loading }">
            生成训练
          </div>
        </div>
      </template>
      <a-spin :spinning="loading">
        <div class="configurationBox">
          <div class="groupBoxs">
            <div class="groupTitle">报文配置</div>
            <div class="rowItem" style="margin-top: 16px">
              <div class="lab">训练名称：</div>
              <div class="item">
                <a-input v-model:value="trainData.title" style="width: 200px; text-align: left">
                </a-input>
              </div>
            </div>
            <div class="rowItem">
              <div class="lab">报文：</div>
              <div class="item">
                <a-radio-group v-model:value="trainData.isCable" @change="selectIsCable">
                  <a-radio :value="0">随机报</a-radio>
                  <a-radio :value="1">固定报</a-radio>
                </a-radio-group>
              </div>
            </div>
            <div class="rowItem">
              <div class="lab">评分规则：</div>
              <div class="item">
                <a-select v-model:value="ruleId" style="width: 200px; text-align: left">
                  <a-select-option v-for="(item, index) in ruleList" :key="index" :value="item.id">
                    {{ item.title }}
                  </a-select-option>
                </a-select>
              </div>
            </div>
            <template v-if="trainData.isCable ==0">
              <div class="rowItem">
                <div class="lab">报文类型：</div>
                <div class="item">
                  <a-radio-group v-model:value="trainData.type">
                    <a-radio :value="0">数码报</a-radio>
                    <a-radio :value="1">字码报</a-radio>
                    <a-radio :value="2">混合报</a-radio>
                  </a-radio-group>
                </div>
              </div>
              <div class="rowItem">
                <div class="lab">报文组数：</div>
                <div class="item">
                  <a-input-number :step="1" :precision="0" :min="1" v-model:value="trainData.count"
                                  style="width: 160px"></a-input-number>
                </div>
              </div>
              <div class="rowItem" style="padding-top: 0">
                <div class="lab"></div>
                <div class="msg">
                  <WarningOutlined style="margin-right: 8px; font-size: 16px"/>
                  纯数字组成的数码报训练！
                </div>
              </div>
            </template>
            <SelectCable v-else/>
          </div>
        </div>
      </a-spin>
    </a-modal>
    <!--新增训练-->
    <a-modal
        :destroyOnClose="true"
        :width="980"
        class="init_modal_style footer-border-none"
        centered
        v-model:visible="gradingRuleModal"
        @cancel="cancelGradingRuleModal"
    >
      <template #title>
        <strong>电子键拍发评分规则管理</strong>
      </template>
      <template #footer>
        <div class="w-full layout-center"></div>
      </template>
      <ExamGardRule/>
    </a-modal>
  </div>
</template>

<script>
export default {
  name: 'ExamPostList'
}
</script>
<script setup>
import {useRouter, useRoute} from 'vue-router'
import {ref, onMounted, provide, createVNode} from 'vue'
import {
  CheckOutlined,
  PlayCircleOutlined,
  DeleteOutlined,
  FileTextOutlined,
  PlusOutlined,
  CloseCircleOutlined,
  WarningOutlined,
  SettingOutlined, ExclamationCircleOutlined
} from '@ant-design/icons-vue'
import {message, Modal} from 'ant-design-vue'
import telegramList from './js/telex'
import {timeFormatInfo, partTimeFormatInfo} from '../../../../../common/utils/Utils.js'
import ExamGardRule from '../../../../../components/gradingRule/ExamGardRule.vue'
import {getCableAll} from "../../../../../common/api/CableApi.js";
import SelectCable from "../../../../../components/cable/SelectCable.vue"

const router = useRouter()
const route = useRoute()
const loading = ref(false)
const addDrillModal = ref(false)
const gradingRuleModal = ref(false)
const basicTrainDeployModal = ref(false)
const userRole = ref(JSON.parse(localStorage.getItem('userRole')))
const deleteModel = (v)=>{
  Modal.confirm({
    class: 'init_modal_style',
    content: '是否删除该记录？',
    icon: () => createVNode(ExclamationCircleOutlined),
    okType: 'danger',
    okText: () => '确定',
    cancelText: () => '取消',
    maskClosable: true,
    onOk: () => {
      deleteHistory(v)
    }
  })
}
const {
  columns,
  tableData,
  tableList,
  currTablePage,
  selectTablePage,
  tableLoading,
  trainData,
  addTelexTrain,
  getGradingRuleList, selectIsCable,deleteHistory,
  ruleId,
  ruleList, selectCable, cableList
} = telegramList(addDrillModal)

/**
 * 格式化时间
 * @param total
 */
const computationTime = total => {
  let hour, min, sec, day, h, m, s
  hour = Math.floor((total / 60 / 60) % 24)
  min = Math.floor((total / 60) % 60)
  sec = Math.floor(total % 60)
  day = Math.floor(total / 60 / 60 / 24)

  hour = hour + day * 24
  h = hour < 10 ? '0' + hour : hour
  m = min < 10 ? '0' + min : min
  s = sec < 10 ? '0' + sec : sec
  return h + ' : ' + m + ' : ' + s
}

/**
 * 开始训练
 * @param record
 */
const startTrain = record => {
  let str
  if (record.status === 2) {
    str = '/patExamTrainScore'
  } else {
    str = '/examPostJobTrain'
  }
  router.push({
    path: route.matched[4].path + str,
    query: {
      id: record.id
    }
  })
}

/**
 * 打开训练弹窗
 */
const addDrillModalInfo = () => {
  addDrillModal.value = true
  getGradingRuleList()
}

/**
 * 关闭训练弹窗弹窗
 */
const cancelTrainModal = () => {
  trainData.value.count = 100
  trainData.value.type = 0
  addDrillModal.value = false
}

/**
 * 打开评分弹窗
 */
const gradingRuleModalInfo = () => {
  gradingRuleModal.value = true
}

/**
 * 关闭评分弹窗
 */
const cancelGradingRuleModal = () => {
  gradingRuleModal.value = false
}
</script>

<style scoped>

@import "../../css/addTrainStyle.less";

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

.HJ .configurationBox .rowItem .item .absolute, .text {
  left: 0;
  top: 0;
  line-height: 32px;
  z-index: 9;
  padding: 0 8px;
  font-size: 12px;
  color: #7b90af;
  border-right: 1px solid #354971;
}

.HJJ .configurationBox .rowItem .item .absolute, .text {
  left: 0;
  top: 0;
  line-height: 32px;
  z-index: 9;
  padding: 0 8px;
  font-size: 12px;
  color: #7b90af;
  border-right: 1px solid #354971;
}

.LJ .configurationBox .rowItem .item .absolute, .text {
  left: 0;
  top: 0;
  line-height: 32px;
  z-index: 9;
  padding: 0 8px;
  font-size: 12px;
  color: #a9abaa;
  border-right: 1px solid #26332e;
}
</style>
