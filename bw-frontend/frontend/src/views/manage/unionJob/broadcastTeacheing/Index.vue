<template>
  <div class="content-mask-bg contentBox layout-left-top">
    <!--    <div class="item_group btn addButton" @click="addDrillModal=true"><PlusOutlined/>新增训练</div>-->
    <NipLeftMenu v-if="route.name==='ReceiveZuXunList'"></NipLeftMenu>
    <div :style="{width: route.name==='ReceiveZuXunList'?'calc(100% - '+(leftMenuWidth+10)+'px)':'100%'}">
      <div class="header">
        <div class="item_group btn addButton" @click="addDrillModal = true"><PlusOutlined />新增训练</div>
      </div>
      <div class="w-full" style="height: calc(100% - 70px)">
        <div class="table_list_box overflow-auto" style=" padding: 0 10px">
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
                  <div class="table_btn" :title="record.stats == 2 ? '查看报底' : record.stats == 1 ? '继续训练' : '开始训练'" @click="intoTrainRoom(record)">
                    <FileTextOutlined v-if="record.stats == 2" />
                    <PlayCircleOutlined v-else-if="record.stats == 1" />
                    <PlayCircleOutlined v-else />
                  </div>
                  <div class="table_btn" v-if="record.createUserId===userInfo.id&&record.stats!==1">
                    <DeleteOutlined style="color: red;" title="删除" @click="deleteModel(record)" />
                  </div>
                </div>
              </div>
            </template>
          </a-table>
        </div>
        <div class="table_pagination">
          <div class="total">共{{ cacheData.length }}条数据</div>
          <div class="item prev" @click="changeListPage(currPage - 1)"></div>
          <template v-for="(item, i) in totalPage" :key="i">
            <div :class="{ item: true, active: item == currPage }" v-if="item > currPage - 3 && item < currPage + 3" @click="changeListPage(item)">
              {{ item }}
            </div>
          </template>
          <div class="item next" @click="changeListPage(currPage + 1)"></div>
        </div>
      </div>
    </div>
    <!--新增训练-->
    <a-modal :destroyOnClose="true" :width="isHeader?1000:600" class="init_modal_style footer-border-none shadow" v-model:visible="addDrillModal" @cancel="cancelTrainModal">
      <template #title>
        <strong>{{route.name==='ReceiveZuXunList'?'新增收报组训':'新增通播教学训练'}}</strong>
      </template>
      <template #footer>
        <div class="w-full layout-center">
          <div @click="addTelexTrain" class="btn-animate" :class="{ createDrillBtn: true }">生成训练</div>
        </div>
      </template>

      <div class="configurationBox">
        <div class="groupBoxs layout-left-top">
          <div class="groupTitle">训练配置</div>
          <div :style="{width: isHeader?'50%':'100%'}">
            <div class="rowItem" style="margin: 16px 0 0">
              <div class="lab">训练名称：</div>
              <a-input v-model:value="formData.roomName" style=" border-radius: 4px; width: 400px" />
            </div>
            <div class="rowItem">
              <div class="lab">是否添加报头：</div>
              <a-switch checked-children="是" un-checked-children="否" @change="isHeaderChange" v-model:checked="isHeader" />
            </div>
            <div class="rowItem">
              <div class="lab">重发开始符：</div>
              <a-switch checked-children="是" un-checked-children="否" v-model:checked="formData.isStartSign" />
              <div class="msg">
                <WarningOutlined style="margin-right: 8px; font-size: 16px"/>
                每页报底开头重发开始符号！
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
            <div class="rowItem">
              <div class="lab">播报码率：</div>
              <div class="item layout-left-center" style="width: 100%; align-items: center">
                <div>20</div>
                <div class="formSlider" style="width: 266px; margin: 0 5px">
                  <a-slider v-model:value="formData.mainSignal" :min="20" :max="500" :step="2"></a-slider>
                </div>
                <div>500</div>
              </div>
            </div>
            <div class="rowItem">
              <div class="lab">人员选择：</div>
              <div class="item item_box"  style="align-items: center;flex-direction: row;display: flex;width: 80%">
                <a-select v-model:value="checkUser" @change="changeUserList" placeholder="请选择参训人员" style="width: 350px" mode="multiple">
                  <a-select-option v-for="(item, index) in userList" :key="item.id" :value="item.userName + index">{{ item.userName }}</a-select-option>
                </a-select>
                <a-checkbox v-model:checked="checked" @change="changeChecked" style="flex-shrink: 0;margin-left: 10px">全选</a-checkbox>
              </div>
            </div>
          </div>
          <div style="width: 50%;" v-if="isHeader">
            <div class="rowItem" style="margin-top: 16px">
              <div class="lab">报头信息</div>
            </div>
            <div class="rowItem" >
              <div class="lab">号数(NR)：</div>
              <a-input-number v-model:value="messageHeader.nr"  :min="1" :max="1000" :step="1"  />
            </div>
            <div class="rowItem" >
              <div class="lab">组数(CK)：</div>
              <div>{{formData.bwCount}}</div>
            </div>
            <div class="rowItem">
              <div class="lab">等级：</div>
              <a-input-number v-model:value="messageHeader.plb" :min="1" :max="1000" :step="1"  />
            </div>
            <div class="rowItem">
              <div class="lab">年月：</div>
              <a-input style="width: 88px" v-model:value="messageHeader.year"   />
            </div>
            <div class="rowItem">
              <div class="lab">时分：</div>
              <a-input style="width: 88px" v-model:value="messageHeader.date"   />
            </div>
            <div class="rowItem">
              <div class="lab">附注(RMKS)：</div>
              <a-textarea v-model:value="messageHeader.remaks" style="width: 80%"/>
            </div>
          </div>
        </div>
        <div class="groupBoxs">
          <div class="groupTitle">报文配置</div>
          <template v-if="formData.isCable===0">
            <div class="rowItem" style="margin: 16px 0 0">
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
              <div class="textColor" style="margin-left: 5px">组</div>
              <div class="item" style="margin-left: 10px" v-if="formData.bwType == 1">
                <a-switch v-model:checked="formData.isShort" checked-children="长码" un-checked-children="短码"></a-switch>
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
          <SelectCable class="mt-2" v-else/>
        </div>

      </div>
    </a-modal>
  </div>
</template>

<script>
  export default {
    name: ''
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
  import SelectCable from "../../../../components/cable/SelectCable.vue"
  import NipLeftMenu from '../../../../components/common/NipLeftMenu.vue'
  import broaddcastTeacheing from './js/broaddcastTeacheing'
  import useBroadcast from './js/useBroadcast'
  import {provide, ref} from "vue";
  import {getCableAll} from "../../../../common/api/CableApi";
import {useRoute} from 'vue-router'
import {global} from "../../../../config/pinia";
  const { intoTrainRoom } = useBroadcast() //引入内容为弹窗内容

  const route = useRoute()
  const useGlobalStore = global.useGlobalStore()
  const leftMenuWidth = ref(useGlobalStore.leftWidth);
  const selectCable = ref(null)
  const cableList = ref([])
  const content = ref([]);
  const ruleId = ref('');
  const ruleList = ref([]);
  const fileUrl = window.fileUrl
const userInfo = JSON.parse(localStorage.getItem('userInfo'))

  const { columns, tableData, totalPage, cacheData, currPage, tableLoading, addDrillModal,isHeader,
    messageHeader,  userList, checkUser, formData,trainData, isRandom, isAverage, checked, selectUserLIst,deleteModel,
    cancelTrainModal, addTelexTrain,isHeaderChange,changeListPage,
    changeChecked, changeUserList } = broaddcastTeacheing(selectCable) //引入内容为弹窗内容

  const selectIsCable = () => {
    if (formData.value.isCable === 1) {
      getCableAll({scope: [0, 2]}).then(res => {
        cableList.value = res.data
        formData.value.cableId = cableList.value[0].id
        formData.value.bwType = cableList.value[0].codeType===0?1:cableList.value[0].codeType===1?3:4
        formData.value.isShort = cableList.value[0].codeSort===1?true:false
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
    /*padding: 20px;*/
    width: 100%;
    height: 100%;
  }
  .header {
    display: flex;
    padding: 10px 16px;
  }

  .addButton {
    width: max-content;
  }
  .avatarImg {
    height: 24px;
    width: 24px;
    border-radius: 50%;
  }
</style>
