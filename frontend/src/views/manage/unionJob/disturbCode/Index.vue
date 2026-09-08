<template>
  <div class="content-mask-bg contentBox">
    <div class="header">
      <div class="item_group btn addButton" @click="addTrainModalInfo()"><PlusOutlined />新增训练</div>
    </div>

    <div class="w-full" style="max-height: calc(100% - 70px)">
      <div class="table_list_box overflow-auto" style="height: calc(100% - 35px); padding: 0 10px">
        <a-table :columns="columns" :loading="tableLoading" :rowKey="record => record.id" :pagination="false" :data-source="tableData">
          <template #bdType="{ text }">
            {{ text == 1 ? '平均报底' : text == 2 ? '乱码报底' : '--' }}
          </template>
          <template #bwType="{ text }">
            {{ text == 1 ? '数字短码' : text == 2 ? '数字长码' : text == 3 ? '字码' : text == 4 ? '混合报' : '--' }}
          </template>
          <template #bwCount="{ text }"> {{ text }} 组 </template>
          <template #user="{ record }">
            <div class="layout-center">
              <img :src="fileUrl + record.userImg" class="avatarImg" />
              <span class="nobr" :title="record.userName" style="padding-left: 10px; max-width: 100px">{{ record.userName }}</span>
            </div>
          </template>
          <template #stats="{ text }">
            <span class="tag finish" v-if="text == 2">已结束</span>
            <span class="tag oper" v-else-if="text == 1">进行中</span>
            <span class="tag" v-else>未开始</span>
          </template>
          <template #action="{ record }">
            <div class="flex layout-center">
              <div class="table_action_btn">
                <div
                  class="table_btn"
                  :title="record.stats == 2 ? '查看报底' : record.stats == 1 ? '继续训练' : '开始训练'"
                  @click="intoTrainRoom(record)"
                >
                  <FileTextOutlined v-if="record.stats == 2" />
                  <PlayCircleOutlined v-else-if="record.stats == 1" />
                  <PlayCircleOutlined v-else />
                </div>
                <div class="table_btn" v-if="record.createUserId===userInfo.id&&record.stats != 1">
                  <DeleteOutlined style="color: red;" title="删除" @click="deleteModel(record)" />
                </div>
              </div>
            </div>
          </template>
        </a-table>
      </div>
      <div class="table_pagination">
        <div class="total">共{{ tableData.length }}条数据</div>
        <div class="item prev" @click="changeListPage(currPage - 1)"></div>
        <template v-for="(item, i) in totalPage" :key="i">
          <div :class="{ item: true, active: item == currPage }" v-if="item > currPage - 3 && item < currPage + 3" @click="changeListPage(item)">
            {{ item }}
          </div>
        </template>
        <div class="item next" @click="changeListPage(currPage + 1)"></div>
      </div>
    </div>

    <!--新增训练-->
    <a-modal
      :destroyOnClose="true"
      :width="560"
      class="init_modal_style footer-border-none"
      v-model:visible="addTrainModal"
      @cancel="cancelTrainModal"
    >
      <template #title>
        <strong>抗干扰收报仿真训练</strong>
      </template>
      <template #footer>
        <div class="w-full layout-center">
          <div @click="addAntiDisturbTrain" class="createDrillBtn">生成训练</div>
        </div>
      </template>
      <div class="configurationBox">
        <div class="groupBoxs">
          <div class="groupTitle">训练配置</div>
          <div class="rowItem" style="margin: 20px 0 0">
            <div class="lab">训练名称：</div>
            <div class="item" style="padding-left: 2px; width: 280px">
              <a-input v-model:value="formData.roomName" placeholder="请输入训练名称"></a-input>
            </div>
          </div>
          <div class="rowItem">
            <div class="lab">报文：</div>
            <div class="item" style="padding-left: 2px">
              <a-radio-group v-model:value="formData.isCable" @change="selectIsCable">
                <a-radio :value="0">随机报</a-radio>
                <a-radio :value="1">固定报</a-radio>
              </a-radio-group>
            </div>
          </div>
         <template v-if="formData.isCable===0">
           <div class="rowItem">
             <div class="lab">报文类型：</div>
             <div class="item" style="padding-left: 2px">
               <a-radio-group v-model:value="formData.bwType">
                 <a-radio :value="1">数码报</a-radio>
                 <a-radio :value="3">字码报</a-radio>
                 <a-radio :value="4">混合报</a-radio>
               </a-radio-group>
             </div>
           </div>
           <div class="rowItem">
             <div class="lab">报文组数：</div>
             <div class="item">
               <a-input-number :step="1" :min="1" :precision="0" v-model:value="formData.bwCount"></a-input-number>
             </div>
             <div class="textColor" style=" margin-left: 5px">组</div>
             <div class="item" style="margin: 0 20px" v-if="formData.bwType == 1">
               <a-switch v-model:checked="formData.numberType" checked-children="长码" un-checked-children="短码"></a-switch>
             </div>
           </div>
           <div class="rowItem">
             <div class="lab">随机：</div>
             <div class="item layout-left-center">
               <a-switch v-model:checked="isRandom" checked-children="是" un-checked-children="否"></a-switch>
             </div>
           </div>
           <div class="rowItem" v-if="isRandom">
             <div class="lab">平均报：</div>
             <div class="item layout-left-center">
               <a-switch v-model:checked="isAverage" checked-children="是" un-checked-children="否"></a-switch>
             </div>
           </div>
         </template>
          <SelectCable v-else/>
        </div>

        <div class="groupBoxs">
          <div class="groupTitle">线路配置</div>
          <div class="rowItem" style="margin: 20px 0 0">
            <div class="lab">
              <a-checkbox v-model:checked="formData.mainSignal['1'].checked" style="margin-right: 10px"></a-checkbox>
              第一路：
            </div>
            <div class="item relative">
              <span class="absolute">报速</span>
              <a-input-number
                v-model:value="formData.mainSignal['1'].rate"
                :min="20"
                :max="500"
                :step="1"
                placeholder="码率"
                style="width: 120px; padding-left: 40px"
              ></a-input-number>
            </div>
            <div class="textColor" style=" margin-left: 5px">码/分</div>
          </div>
          <div class="rowItem">
            <div class="lab">
              <a-checkbox v-model:checked="formData.mainSignal['2'].checked" style="margin-right: 10px"></a-checkbox>
              第二路：
            </div>
            <div class="item relative">
              <span class="absolute">报速</span>
              <a-input-number
                v-model:value="formData.mainSignal['2'].rate"
                :min="20"
                :max="500"
                :step="1"
                placeholder="码率"
                style="width: 120px; padding-left: 40px"
              ></a-input-number>
            </div>
            <div class="textColor" style="margin-left: 5px">码/分</div>
          </div>
          <div class="rowItem">
            <div class="lab">
              <a-checkbox v-model:checked="formData.mainSignal['3'].checked" style="margin-right: 10px"></a-checkbox>
              第三路：
            </div>
            <div class="item relative">
              <span class="absolute">报速</span>
              <a-input-number
                v-model:value="formData.mainSignal['3'].rate"
                :min="20"
                :max="500"
                :step="1"
                placeholder="码率"
                style="width: 120px; padding-left: 40px"
              ></a-input-number>
            </div>
            <div class="textColor" style="margin-left: 5px">码/分</div>
          </div>
        </div>
      </div>
    </a-modal>
  </div>
</template>

<script>
export default {
  name: 'Index'
}
</script>
<script setup>
import {
  PlusOutlined,
  WarningOutlined,
  FileTextOutlined,
  FundViewOutlined,
  PlayCircleOutlined,
  DeleteOutlined
} from '@ant-design/icons-vue'
import disturbCode from './js/disturbCode'
import telegramList from './js/telegram'
import {provide, ref} from 'vue'
import SelectCable from "../../../../components/cable/SelectCable.vue"
import {getCableAll} from "../../../../common/api/CableApi";
const selectCable = ref(null)
const cableList = ref([])
const content = ref([]);
const ruleId = ref('');
const ruleList = ref([]);
const userInfo = JSON.parse(localStorage.getItem('userInfo'));

const fileUrl = ref(window.fileUrl)
const { columns, tableData, totalPage, currPage, tableLoading, changeListPage ,deleteModel} = telegramList()
const { addTrainModal, formData, isRandom, isAverage, cancelTrainModal, addAntiDisturbTrain, intoTrainRoom, addTrainModalInfo } =
  disturbCode(selectCable)
const selectIsCable = () => {
  if (formData.value.isCable === 1) {
    getCableAll({scope: [0, 2]}).then(res => {
      cableList.value = res.data
      formData.value.cableId = cableList.value[0].id
      formData.value.numberType = cableList.value[0].codeSort===1?true:false
      formData.value.bwType = cableList.value[0].codeType===0?1:cableList.value[0].codeType===1?3:4
      selectCable.value = cableList.value[0]
    })
  } else {
    formData.value.cableId = null
    selectCable.value = null
  }
}
provide("selectCable",selectCable)
provide("formData",formData)
provide("cableList",cableList)
</script>

<style scoped lang="less">
@import '../css/unionJob';
.contentBox {
  width: 100%;
  height: 100%;
}
.addButton {
  width: max-content;
}
.header {
  display: flex;
  padding: 10px 16px;
}
.avatarImg {
  width: 24px;
  height: 24px;
  border-radius: 50%;
}
</style>
