<template>
  <div class="w-full h-full overflow-hidden layout-side">
    <div class="w-full h-full">
      <div class="w-full h-full grouping_content content-mask-bg">
        <div class="w-full table_search_box">
          <!--          <div class="item_group btn" @click="goWordTrain">单字训练</div>-->
          <div class="item_group btn" @click="addDrillModalInfo"><PlusOutlined />&nbsp;新增训练</div>
          <!--          <div class="item_group btn" v-per="'grad'" @click="gradingRuleModalInfo"><SettingOutlined />&nbsp;评分规则</div>-->
          <div class="item_group btn"  @click="gradingRuleModalInfo"><SettingOutlined />&nbsp;评分规则</div>
        </div>

        <div class="w-full" style="max-height: calc(100% - 70px)">
          <div class="table_list_box overflow-auto" style="height: calc(100% - 35px);">
            <a-table :columns="columns" :loading="tableLoading" :rowKey="record => record.id" :pagination="false" :data-source="tableList">
              <template #isCable="{text}">
                {{ text == 0 ? '随机报' : '固定报' }}
              </template>
              <template #type="{ text }">
                {{ text == 0 ? '数码报' : text == 1 ? '字码报' : text == 2 ? '混合报' : '' }}
              </template>
              <template #duration="{ text }">
                {{ text ? partTimeFormatInfo(parseInt(text * 1000), 'chinese') : '--' }}
              </template>
              <template #totalSpeed="{ text }">
                {{ text == null ? '--' : text + '码/分' }}
              </template>
              <template #score="{ record }">
                {{ record.status == 3?record.score:"100" }}
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
                    <div class="table_btn">
                      <DeleteOutlined style="color: red;" title="删除" @click="deleteModel(record)" />
                    </div>
                    <!--<div class="table_btn" title="删除">
                      <DeleteOutlined/>
                    </div>-->
                  </div>
                </div>
              </template>
            </a-table>
          </div>
          <Pagination
            :totalNumber="page.totalNumber"
            :pageAll="page.pageAll"
            :currentPage="page.currentPage"
            :selectTablePage="selectTablePage"
          ></Pagination>
        </div>
      </div>
    </div>
    <!--新增训练-->
    <a-modal :destroyOnClose="true" :width="560" class="init_modal_style footer-border-none" v-model:visible="addDrillModal" @cancel="cancelTrainModal">
      <template #title>
        <strong>新增电传拍发</strong>
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
            <div class="rowItem" style="margin-top: 16px">
              <div class="lab">训练名称：</div>
              <div class="item">
                <a-input v-model:value="trainData.name" style="width: 200px; text-align: left">
                </a-input>
              </div>
            </div>
            <div class="rowItem">
              <div class="lab">报文：</div>
              <div class="item" style="padding-left: 2px">
                <a-radio-group v-model:value="trainData.isCable" @change="selectIsCable">
                  <a-radio :value="0">随机报</a-radio>
                  <a-radio :value="1">固定报</a-radio>
                </a-radio-group>
              </div>
            </div>
            <div class="rowItem">
              <div class="lab">评分规则：</div>
              <div class="item">
                <a-select v-model:value="trainData.ruleId" style="width: 150px; text-align: left">
                  <a-select-option v-for="v of rule" :key="v" :value="v.id">{{ v.title }}</a-select-option>
                </a-select>
              </div>
            </div>

          </div>

          <div class="groupBoxs">
            <div class="groupTitle">报文配置</div>
            <template v-if="trainData.isCable ==0">
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
              <div class="rowItem" v-if="trainData.type==0">
                <div class="lab">报底类型：</div>
                <div class="item" style="padding-left: 2px">
                  <a-radio-group v-model:value="trainData. patType" @change="selectType">
                    <a-radio :value="0">挨指报底</a-radio>
                    <a-radio :value="1">对手报底</a-radio>
                    <a-radio :value="2">随机</a-radio>
                  </a-radio-group>
                </div>
              </div>
              <div class="rowItem" >
                <div class="lab">报文组数：</div>
                <div class="item">
                  <a-input-number :step="1" :min="1" :precision="0" v-model:value="trainData.count"></a-input-number>
                </div>
              </div>
            </template>
            <SelectCable class="mt-2" v-else/>
          </div>
        </div>
      </a-spin>
    </a-modal>
    <!--新增训练-->
    <a-modal :destroyOnClose="true" :width="980" class="init_modal_style footer-border-none" centered v-model:visible="gradingRuleModal" @cancel="cancelGradingRuleModal">
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
  import {ref, onMounted, provide, createVNode} from 'vue'
  import {
    PlayCircleOutlined,
    DeleteOutlined,
    FileTextOutlined,
    PlusOutlined,
    CloseCircleOutlined,
    WarningOutlined,
    SettingOutlined,
    ExclamationCircleOutlined
  } from '@ant-design/icons-vue'
  import {message, Modal} from 'ant-design-vue'
  import telegramList from './js/telex'
  import { timeFormatInfo, partTimeFormatInfo } from '../../../../../common/utils/Utils.js'
  import TelexGardRule from '../../../../../components/gradingRule/TelexGardRule.vue'
  import * as gr from '../../../../../common/api/GradingRuleApi'
  import SelectCable from "../../../../../components/cable/SelectCable.vue"
  import Pagination from '../../../../../components/common/Pagination.vue'
  import {getCableAll} from "../../../../../common/api/CableApi.js";
  import moment from "moment";

  const router = useRouter()
  const route = useRoute()
  const loading = ref(false)
  const addDrillModal = ref(false)
  const gradingRuleModal = ref(false)
  const basicTrainDeployModal = ref(false)
  const selectCable = ref(null)
  const cableList = ref([])
  const gradingRuleModalInfo = () => {
    gradingRuleModal.value = true
  }
  const cancelGradingRuleModal = () => {
    gradingRuleModal.value = false
  }
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
  const { columns, tableData, tableList, currTablePage, selectTablePage, tableLoading, selectType, initContent, trainData, addTelexTrain, rule, getRule,deleteHistory,page } = telegramList(addDrillModal)
  provide("selectCable",selectCable)
  provide("formData",trainData)
  provide("cableList",cableList)
  const selectIsCable = () => {
    if (trainData.value.isCable === 1) {
      getCableAll({scope: [1, 2]}).then(res => {
        cableList.value = res.data
        trainData.value.cableId = cableList.value[0].id
        trainData.value.type = cableList.value[0].codeType
        trainData.value.patType = 2
        selectCable.value = cableList.value[0]
      })
    } else {
      trainData.value.cableId = null
      selectCable.value = null
    }
  }
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
    trainData.value.name = '电传拍发-' + moment().format('YYMMDDhhmmss')
    getRule()
  }
  /**
   * 关闭弹窗
   */
  const cancelTrainModal = () => {
    trainData.value.count = 100
    addDrillModal.value = false
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
</style>
