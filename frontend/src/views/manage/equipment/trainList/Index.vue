<template>
  <div class="w-full h-full overflow-hidden layout-side" style="padding: 0px 12px 12px 0px">
    <nip-left-menu />
    <div class="h-full transition-all duration-300 overflow-auto listBox content-mask-bg" style="margin-left: 12px; padding-bottom: 20px" :style="{ width: 'calc(100% - ' + (leftMenuWidth + 12) + 'px)' }">
      <div class="w-full" style="max-height: calc(100% - 70px)">
        <div class="w-full table_search_box" style="padding: 10px 10px 0px 0px">
          <div class="item_group btn layout-center" v-per="'add'" style="width: 100px; margin-bottom: 10px; margin-left: 10px" @click="addTrain"><PlusOutlined />新增训练</div>
          <div class="item_group btn" v-per="'grad'" @click="gradingRuleModalInfo"><SettingOutlined />&nbsp;评分规则</div>
        </div>
        <div class="table_list_box overflow-auto" style="height: calc(100% - 35px); padding: 0 10px">
          <a-table :columns="columns" :loading="tableLoading" :rowKey="record => record.id" :pagination="false" :data-source="tableList">
            <template #action="{ record }">
              <div class="flex layout-center">
                <div class="table_action_btn">
                  <div class="table_btn" @click="startTrain(record)">
                    <FileTextOutlined v-if="record.trainStatus == 1" />
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
        <Pagination :tableAllData="tableData" @getTableList="getTableList"></Pagination>
      </div>
    </div>
    <a-modal :destroyOnClose="true" :width="1580" class="init_modal_style footer-border-none" v-model:visible="gradingRuleModal" @cancel="gradingRuleModal = false">
      <template #title>
        <strong>评分规则</strong>
      </template>
      <a-spin :spinning="loading">
        <div class="configurationBox">
          <div class="layout-center" style="margin-bottom: 10px">
            <span>设备：</span>
            <a-select style="width: 150px" v-model:value="activeEquipment" @change="selectDev">
              <a-select-option v-for="(item, index) in equipmentLists" :key="index" :value="index">{{ item.name }}</a-select-option>
            </a-select>
            <span>联络文件：</span>
            <a-select style="width: 150px" v-model:value="activeDoc" v-if="liaisonDoc" @change="selectDoc">
              <a-select-option v-for="(item, index) in liaisonDoc.children" :key="index" :value="index">{{ item.name }}</a-select-option>
            </a-select>
<!--            <div style="margin-left: 100px">总分：{{ allScore }}</div>-->
          </div>
          <div class="layout-center" style="height: calc(100% - 42px); overflow: auto; padding: 5px;width: 100%">
            <table style="display: flow-root;margin: 0 auto;max-width: 100%">
              <tr v-for="(item, index) of tableDocData" :key="index">
                <td class="tableTd" @click="cliceTd(index, num, v.value)" v-for="(v, num) of item" :key="num" :colspan="v.colspan" :rowspan="v.rowspan" :class="[isTitle(v.value) ? 'tableTitle' : '']" style="min-width: 60px" :style="{ height: v.height + 'px', width: v.width + 'px' }">
                  {{ v.value == 'table' ? '' : v.value }}
                  <table v-if="v.value == 'table'" style="width: 100%">
                    <tr v-for="(item2, index2) of tableDocData2" :key="index2">
                      <td class="tableTd" @click.stop="cliceTd(index2, num2, v2.value)" v-for="(v2, num2) of item2" :key="num2" :colspan="v2.colspan" :rowspan="v2.rowspan" :class="[isTitle(v2.value) ? 'tableTitle' : '']" :style="{ height: v2.height + 'px', width: 75 + 'px' }">
                        {{ v2.value }}
                      </td>
                    </tr>
                  </table>
                </td>
              </tr>
            </table>
          </div>
        </div>
      </a-spin>
      <template #footer>
        <div class="w-full layout-center">
          <div :class="{ createDrillBtn: true, 'btn-animate': !loading, loadingBtn: loading }" @click="deleteById"><a-spin v-if="loading" size="small" /> 删除配置</div>
          <div style="margin-left: 20px" :class="{ createDrillBtn: true, 'btn-animate': !loading, loadingBtn: loading }" @click="saveDeploy"><a-spin v-if="loading" size="small" /> 保存配置</div>
        </div>
      </template>
    </a-modal>
    <a-modal :destroyOnClose="true" :width="300" class="init_modal_style footer-border-none" v-model:visible="addDrillModal" @cancel="addDrillModal = false">
      <template #title>
        <strong>添加分数</strong>
      </template>
      <template #footer>
        <div class="w-full layout-center">
          <div class="createDrillBtn" @click="addScore(addAndUpdate)">添加分数</div>
        </div>
      </template>
      <div style="padding: 20px 10px"><span style="color: #fff"> 分数：</span><a-input-number v-model:value="addAndUpdate.score" :min="1"  precision="0"  maxLength="1" placeholder="请填写正整数分数" style="width: 190px"></a-input-number></div>
    </a-modal>
  </div>
</template>

<script>
export default {
  name: 'Index'
}
</script>
<script setup>
import NipLeftMenu from '../../../../components/common/NipLeftMenu.vue'
import Pagination from '../../../../components/pagination/Pagination.vue'

import { useRoute, useRouter } from 'vue-router'
import { PlayCircleOutlined, DeleteOutlined, FileTextOutlined, PlusOutlined, CloseCircleOutlined, WarningOutlined, SettingOutlined } from '@ant-design/icons-vue'
import { ref, onUnmounted, provide, onMounted } from 'vue'
import equipmentList from './js/equipmentList'
import {global} from "../../../../config/pinia/index.js"

const useGlobalStore = global.useGlobalStore()
const leftMenuWidth = ref(useGlobalStore.leftWidth);
const route = useRoute()
const router = useRouter()

const basicDeployData = ref([])
const loading = ref(false)
provide('atRoute', route.matched[4])
provide('leftMenuWidth', leftMenuWidth)
const {
  columns,
  tableData,
  tableList,
  currTablePage,
  selectTablePage,
  tableLoading,
  equipmentLists,
  liaisonDoc,
  tableDocData,
  tableDocData2,
  activeEquipment,
  activeDoc,
  addDrillModal,
  addAndUpdate,
  gradingRuleModal,
  allScore,
  saveDeploy,
  addScore,
  selectDev,
  selectDoc,
  cliceTd,
  isTitle,
  deleteById
} = equipmentList()
const startTrain = record => {
  router.push({
    path: route.matched[3].path + '/equipmentScore',
    query: {
      id: record.id
    }
  })
}
const addTrain = () => {
  router.push({
    path: route.matched[3].path + '/equipmentList'
  })
}
const getTableList = data => {
  tableList.value = data
}

const gradingRuleModalInfo = () => {
  gradingRuleModal.value = true
}

/**
 * 新增码率
 */
const createBasicNorm = () => {
  basicDeployData.value.push({ name: '', speed: '', text: '' })
}
/**
 * 删除码率
 * @param index
 */
const closeBasicNorm = index => {
  basicDeployData.value = basicDeployData.value.filter((item, i) => i !== index)
}
/**
 * 基础练习数据处理
 */
const handleBasicData = () => {
  let res = []
  for (let item of basicDeployData.value) {
    if (!item.name || !item.speed || !item.text) {
      return false
    } else {
      res.push({
        type: item.name,
        rate: item.speed,
        text: item.text
      })
    }
  }
  return res
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
  height: 700px;
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
.init_modal_style >>> .ant-modal-footer {
  border-top: none !important;
}
.tableTd {
  border: 1px solid rgba(255, 255, 255, 0.5);
  text-align: center;
  background: #18222d;
  color: #bfcde0;
  font-size: 14px;
}
.tableTitle {
  background: #31566e !important;
  color: #70a3b8 !important;
  cursor: pointer;
}
</style>
