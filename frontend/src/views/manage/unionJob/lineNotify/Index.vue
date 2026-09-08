<template>
  <div class="content-mask-bg contentBox layout-left-center">
    <nip-left-menu/>
    <div class="content-mask-bg contentBox w-full" :style="{width: 'calc(100% - '+(leftMenuWidth+10)+'px)'}">
      <div class="w-full" style="max-height: calc(100% - 70px)">
        <div class="header">
          <div class="item_group btn layout-center" @click="addLIneBulletin">
            <PlusOutlined/>
            新增训练
          </div>
        </div>
        <div class="table_list_box overflow-auto" style="height: calc(100% - 35px); padding: 0 10px">
          <a-table :columns="columns" :loading="tableLoading" :pagination="false" :data-source="tableList"
                   :rowKey="record => record.id">
            <template #bwType="{ record }">
              {{ record.bwType == 1 ? '数组短码' : record.bwType == 2 ? '数组长码' : record.bwType == 3 ? '字码' : '混合码' }}
            </template>
            <template #bdType="{ record }">
              {{ record.bwType == 1 ? '平均保底' : '乱码报底' }}
            </template>
            <template #stats="{ record }">
              <span v-if="record.stats == 0" class="tag">未开始</span>
              <span v-else-if="record.stats == 1" class="tag oper">进行中</span>
              <span v-else class="tag end">已结束</span>
            </template>
            <template #ident="{ record }">
              <span class="tag finish" v-if="record.createUserId == userInfo.id">组训人</span>
              <span class="tag oper" v-else>参训人</span>
            </template>
            <template #action="{ record }">
              <div class="flex layout-center">
                <div class="table_action_btn" >
                  <div class="table_btn" title="开始训练" v-if="record.stats == 0" @click="seeData(record)">
                    <PlayCircleOutlined/>
                  </div>
                  <div class="table_btn" title="继续训练" v-if="record.stats == 1" @click="seeData(record)">
                    <PlayCircleOutlined/>
                  </div>
                  <div class="table_btn" title="查看训练" v-if="record.stats == 2" @click="seeData(record)">
                    <FileTextOutlined/>
                  </div>
                  <div class="table_btn" v-if="record.createUserId===userInfo.id&&record.stats != 1">
                    <DeleteOutlined style="color: red;" title="删除" @click="deleteModel(record)" />
                  </div>
                </div>
              </div>
            </template>
          </a-table>
        </div>
        <Pagination :tableAllData="tableData" @getTableList="getTableList"></Pagination>
        <!--基础练习配置-->
        <a-modal
            :destroyOnClose="true"
            :width="900"
            class="init_modal_style footer-border-none"
            v-model:visible="visibleModal"
            @cancel="cancelTrianModal"
        >
          <template #title>
            <strong>新增线路通报</strong>
          </template>
          <a-spin :spinning="loading">
            <div class="configurationBox" style="padding-bottom: 0">
              <div class="groupBoxs">
                <div class="groupTitle">训练配置</div>
                <div class="rowItem" style="margin: 10px 0 0">
                  <div class="lab">训练名称：</div>
                  <div class="item" style="padding-left: 2px">
                    <a-input v-model:value="roomName" placeholder="请输入" style="width: 200px"></a-input>
                  </div>
                </div>
                <div class="rowItem">
                  <div class="lab">报文：</div>
                  <a-radio-group v-model:value="trainData.isCable" @change="selectIsCable">
                    <a-radio :value="0">随机报</a-radio>
                    <a-radio :value="1">固定报</a-radio>
                  </a-radio-group>
                </div>
                <template v-if="trainData.isCable===0">
                  <div class="rowItem" style="margin: 10px 0 0">
                    <div class="lab">类型：</div>
                    <div class="item" style="padding-left: 2px">
                      <a-radio-group v-model:value="trainData.bwType">
                        <a-radio :value="1">数码报</a-radio>
                        <a-radio :value="3">字码报</a-radio>
                        <a-radio :value="4">混合报</a-radio>
                      </a-radio-group>
                    </div>
                  </div>
                  <div class="rowItem">
                    <div class="lab">报文组数：</div>
                    <div class="item">
                      <a-input-number :step="1" :min="1" :precision="0"
                                      v-model:value="trainData.bwCount"></a-input-number>
                    </div>
                    <div class="textColor" style="color: #7b90af">组</div>
                    <!--                    <div style="margin: 0 20px" v-if="trainData.bwType == 1">-->
                    <!--                      <a-switch v-model:checked="trainData.numberType" checked-children="长码" un-checked-children="短码"></a-switch>-->
                    <!--                    </div>-->
                  </div>
                  <div class="rowItem">
                    <div class="lab">随机：</div>
                    <div class="item layout-left-center">
                      <a-switch v-model:checked="trainData.isRandom" checked-children="是"
                                un-checked-children="否"></a-switch>
                    </div>
                  </div>
                  <div class="rowItem" v-if="trainData.isRandom">
                    <div class="lab">平均报：</div>
                    <div class="item layout-left-center">
                      <a-switch v-model:checked="trainData.isAverage" checked-children="是"
                                un-checked-children="否"></a-switch>
                    </div>
                  </div>
                </template>
                <SelectCable v-else/>
              </div>
              <div class="groupBoxs">
                <div class="groupTitle">训练人员</div>
                <div class="rowItem">
                  <a-transfer
                      :data-source="checkboxOptions"
                      :target-keys="targetKeys"
                      :titles="['拍发人员', '收听人员']"
                      :render="item => item.label"
                      @change="handleChange"
                      @selectChange="handlSelectChange"
                  />
                </div>
              </div>
            </div>
          </a-spin>
          <template #footer>
            <div class="w-full layout-center">
              <div
                  :class="{
                  createDrillBtn: true,
                  'btn-animate': !loading,
                  loadingBtn: loading
                }"
                  @click="createDrillInfo"
              >
                <a-spin v-if="loading" size="small"/>
                新增训练
              </div>
            </div>
          </template>
        </a-modal>
      </div>
    </div>
  </div>
</template>
<script>
export default {
  name: 'lineNotify'
}
</script>

<script setup>
import {onMounted, nextTick, ref, provide} from 'vue'
import Pagination from '../../../../components/pagination/Pagination.vue'
import lineNotify from './js/index'
import {message} from 'ant-design-vue'
import {PlusOutlined, PlayCircleOutlined, FileTextOutlined, DeleteOutlined} from '@ant-design/icons-vue'
import NipLeftMenu from '../../../../components/common/NipLeftMenu.vue'
import {global} from "../../../../config/pinia/index.js"
import SelectCable from "../../../../components/cable/SelectCable.vue"
import {getCableAll} from "../../../../common/api/CableApi";

const useGlobalStore = global.useGlobalStore()
const leftMenuWidth = ref(useGlobalStore.leftWidth);
const selectCable = ref(null)
const cableList = ref([])
const content = ref([]);
const ruleId = ref('');
const ruleList = ref([]);
const {
  columns,
  tableData,
  tableList,
  currTablePage,
  loading,
  visibleModal,
  tableLoading,
  seeData,
  roomName,
  trainData,
  formData,
  checkBoxList,
  newCheckBoxList,
  checkboxOptions,
  newCheckboxOptions,
  createDrillInfo,
  cancelTrianModal,
  handlSelectChange,
  targetKeys,
  selectedKeys,
  getSimulationRouterFindRoomList,
  handleChange,
  getAllTeacherList,
  isAverage,
  isRandom,
  deleteModel
} = lineNotify(selectCable)
const userInfo = JSON.parse(window.localStorage.getItem('userInfo'))
const render = $event => {
  console.log($event)
}

const selectIsCable = () => {
  console.log(tableData.value.isCable)
  if (trainData.value.isCable === 1) {
    getCableAll({scope: [0, 1, 2]}).then(res => {
      cableList.value = res.data
      trainData.value.cableId = cableList.value[0].id
      trainData.value.bwType = cableList.value[0].codeType === 0 ? 1 : cableList.value[0].codeType === 1 ? 3 : 4
      selectCable.value = cableList.value[0]
    })
  } else {
    trainData.value.cableId = null
    selectCable.value = null
  }
}
provide("selectCable", selectCable)
provide("formData", trainData)
provide("cableList", cableList)
onMounted(() => {

  getSimulationRouterFindRoomList()
  getAllTeacherList()
})

const getTableList = val => {
  tableList.value = val
}

//处理人员数组
const handlerChange = list => {
  newCheckboxOptions.value = JSON.parse(JSON.stringify(checkboxOptions.value))
  newCheckBoxList.value = []
  list.forEach(a => {
    const index = newCheckboxOptions.value.findIndex(item => item.value == a)
    if (index != -1) {
      newCheckboxOptions.value.splice(index, 1)
    }
  })
  setCategoryOptions(checkBoxList.value)
}
//处理选中人数
const setCategoryOptions = value => {
  if (value.length <= 8) {
    return false
  } else {
    message.error('最多选择8个人')
    nextTick(() => {
      value.splice(value.length - 1, 1)
    })
  }
}

//添加训练
const addLIneBulletin = () => {
  visibleModal.value = true
  const name = `线路通报${dayjs().format('YYMMDDHHmmss')}`
  roomName.value = name
}
const cancelTrainModal = () => {
  visibleModal.value = false
}
</script>

<style scoped lang="less">
  @import "../../postJob/css/addTrainStyle.less";
.header {
  display: flex;
  padding: 10px 16px;
}

.LJ {
  .textColor {
    color: #a9abaa !important;
  }
}
  .contentBox {
    width: 100%;
    height: 100%;
  }
.groupBoxs {
  position: relative;
  border: 1px solid #3d586f;
  margin-top: 20px;
  padding-bottom: 8px;
  padding: 20px;
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

</style>
