<template>
  <div class="content-mask-bg contentBox layout-left-center">
    <div v-if="loading" class="layout-center"
         style="position: absolute;z-index: 1001;top: 0;width: 100%;height: 100%;background: rgba(0,0,0,0.3)">
      <a-spin size="large" tip="生成训练中..."/>
    </div>

    <NipLeftMenu></NipLeftMenu>
    <div class="content-mask-bg contentBox w-full" :style="{width: 'calc(100% - '+(leftMenuWidth+10)+'px)'}">
      <div class="header">
        <div class="item_group btn addButton" @click="addDrillModal = true">
          <PlusOutlined/>
          新增训练
        </div>
      </div>
      <div class="w-full" style="height: calc(100% - 70px)">
        <div class="table_list_box overflow-auto" style="padding: 0 10px">
          <a-table :columns="columns" :loading="tableLoading" :rowKey="record => record.id" :pagination="false"
                   :data-source="tableData">
            <template #isAverage="{ text }">
              {{ text ? '平均报底' : '乱码报底' }}
            </template>
            <template #type="{ text }">
              {{ text == 0 ? '数码' : text == 1 ? '字码' : text == 2 ? '混合报' : '--' }}
            </template>
            <template #messageNumber="{ text }"> {{ text }} 组</template>
            <template #user="{ record }">
              <div class="layout-center">
                <img :src="fileUrl + record.userImg" class="avatarImg"/>
                <span class="nobr" :title="record.userName"
                      style="padding-left: 10px; max-width: 100px">{{ record.userName }}</span>
              </div>
            </template>
            <template #status="{ text }">
              <span class="tag finish" v-if="text == 2">已结束</span>
              <span class="tag oper" v-else-if="text == 1">进行中</span>
              <span class="tag" v-else>未开始</span>
            </template>
            <template #ident="{ record }">
              <span class="tag finish" v-if="record.createUser == userInfo.id">组训人</span>
              <span class="tag oper" v-else>参训人</span>
            </template>
            <template #action="{ record }">
              <div class="flex layout-center">
                <div class="table_action_btn">
                  <div class="table_btn" :title="record.status == 2 ? '查看报底' : record.status == 1 ? '继续训练' : '开始训练'"
                       @click="startTrain(record)">
                    <FileTextOutlined v-if="record.status == 2"/>
                    <PlayCircleOutlined v-else-if="record.status == 1"/>
                    <PlayCircleOutlined v-else/>
                  </div>
                  <div class="table_btn" v-if="record.createUser===userInfo.id&&record.status != 1">
                    <DeleteOutlined style="color: red;" title="删除" @click="deleteModel(record)" />
                  </div>
                </div>
              </div>
            </template>
          </a-table>
        </div>
        <Pagination
          :totalNumber="totalAll"
          :pageAll="totalPage"
          :currentPage="currPage"
          :selectTablePage="changeListPage"
        ></Pagination>
      </div>
    </div>

    <!--新增训练-->
    <a-modal :destroyOnClose="true" :width="560" class="init_modal_style footer-border-none shadow"
             v-model:visible="addDrillModal" @cancel="cancelTrainModal">
      <template #title>
        <strong>新增手键训练</strong>
      </template>
      <template #footer>
        <div class="w-full layout-center">
          <div @click="addTelexTrain" class="btn-animate" :class="{ createDrillBtn: true }">生成训练</div>
        </div>
      </template>

      <div class="configurationBox">
        <div class="groupBoxs">
          <div class="groupTitle">训练配置</div>
          <div class="rowItem" style="margin-top: 16px">
            <div class="lab">训练名称：</div>
            <div class="item w-full">
              <a-input v-model:value="formData.name" placeholder="请输入训练名称" style="width: 400px"></a-input>
            </div>
          </div>
          <div class="rowItem">
            <div class="lab">报文：</div>
            <a-radio-group v-model:value="formData.isCable" @change="selectIsCable">
              <a-radio :value="0">随机报</a-radio>
              <a-radio :value="1">固定报</a-radio>
            </a-radio-group>
          </div>
          <div class="rowItem">
            <div class="lab">评分规则：</div>
            <div class="item">
              <a-select v-model:value="formData.ruleId" placeholder="请选择评分规则" style="width: 210px; text-align: left">
                <a-select-option v-for="(item, index) in ruleList" :value="item.id" :key="index">{{
                  item.title
                  }}
                </a-select-option>
              </a-select>
            </div>
          </div>
          <div class="rowItem">
            <div class="lab">人员选择：</div>
            <div class="item item_box" style="align-items: center;flex-direction: row;display: flex;width: 100%">
              <a-select v-model:value="checkUser" @change="changeUserList" placeholder="请选择参训人员" style="width: 350px"
                        mode="multiple">
                <a-select-option v-for="(item, index) in userList" :key="item.id" :value="item.userName + index">
                  {{ item.userName }}
                </a-select-option>
              </a-select>
              <a-checkbox v-model:checked="checked" @change="changeChecked" style="flex-shrink: 0;margin-left: 10px">
                全选
              </a-checkbox>
            </div>
          </div>
        </div>
        <div class="groupBoxs">
          <div class="groupTitle">报文配置</div>
          <template v-if="formData.isCable===0">
            <div class="rowItem" style="margin-top: 16px">
              <div class="lab">类型：</div>
              <div class="item" style="padding-left: 2px">
                <a-radio-group v-model:value="formData.type" @change="selectType">
                  <a-radio :value="0">数码报</a-radio>
                  <a-radio :value="1">字码报</a-radio>
                  <a-radio :value="2">混合报</a-radio>
                </a-radio-group>
              </div>
            </div>
            <div class="rowItem">
              <div class="lab">{{ formData.type == 0 ? '数码报' : formData.type == 1 ? '字码报' : '混合报' }}：</div>
              <div class="item relative">
                <a-input-number v-model:value="formData.messageNumber" :min="1" :max="100000" :step="1" placeholder="数量"
                                style="width: 120px; "></a-input-number>
              </div>
              <div style=" margin-left: 5px">组</div>
              <div class="item" style="margin: 0" v-if="formData.type == 0">
                <a-switch v-model:checked="formData.codeSort" checked-children="长码" un-checked-children="短码"></a-switch>
              </div>
            </div>
            <!--            <div class="rowItem">-->
            <!--              <div class="lab">随机：</div>-->
            <!--              <div class="item layout-left-center">-->
            <!--                <a-switch v-model:checked="formData.isRandom" checked-children="是" un-checked-children="否"></a-switch>-->
            <!--              </div>-->
            <!--            </div>-->
            <div class="rowItem" v-if="formData.isRandom">
              <div class="lab">平均报：</div>
              <div class="item layout-left-center">
                <a-switch v-model:checked="formData.isAverage" checked-children="是" un-checked-children="否"></a-switch>
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
  PlayCircleOutlined, DeleteOutlined
} from '@ant-design/icons-vue'
  import NipLeftMenu from '../../../../../components/common/NipLeftMenu.vue'
  import SelectCable from "../../../../../components/cable/SelectCable.vue"
  import Pagination from "../../../../../components/common/Pagination.vue";
  import broaddcastTeacheing from './js/list.js'
  import {provide, ref} from "vue";
  import {useRoute, useRouter} from 'vue-router'
  import {global} from "../../../../../config/pinia/index.js"
  import {getCableAll} from "../../../../../common/api/CableApi";

  const selectCable = ref(null)
  const cableList = ref([])
  const content = ref([]);
  const ruleId = ref('');
  const useGlobalStore = global.useGlobalStore()
  const leftMenuWidth = ref(useGlobalStore.leftWidth);
  const route = useRoute()
  const router = useRouter()
  const userInfo = JSON.parse(localStorage.getItem('userInfo'))
  /**
   * 开始训练
   * @param item
   */
  const startTrain = item => {
    let isFinish = item.userInfoList.filter(use => use.userId == userInfo.id)[0].isFinish
    console.log(isFinish)
    console.log(route.matched)
    router.push({
      path: route.matched[3].path + '/handkeyZuXunTrain',
      query: {
        id: item.id,
        teacher: (item.createUser == userInfo.id ? '1' : undefined),
        status: item.status == 1 && isFinish == 1 ? 2 : item.status
      }
    })
  }
  const fileUrl = window.fileUrl
  const {
    columns, tableData, totalPage, cacheData, currPage, tableLoading, addDrillModal,totalAll,
    changeListPage, cancelTrainModal, trainData, addTelexTrain, userList, checkUser, formData, ruleList, loading,
    isRandom, isAverage, checked, changeChecked, selectUserLIst, changeUserList,deleteModel
  } = broaddcastTeacheing(selectCable) //引入内容为弹窗内容
  /**
   * 切换训练类型
   */
  const selectType = () => {
    formData.value.codeSort = formData.value.type > 0
  }

  const selectIsCable = () => {
    if (formData.value.isCable === 1) {
      getCableAll({scope: [1, 2]}).then(res => {
        cableList.value = res.data
        formData.value.cableId = cableList.value[0].id
        formData.value.type = cableList.value[0].codeType
        selectCable.value = cableList.value[0]
      })
    } else {
      formData.value.cableId = null
      selectCable.value = null
    }
  }
  provide("selectCable", selectCable)
  provide("formData", formData)
  provide("cableList", cableList)
</script>

<style scoped lang="less">
  @import '../../../unionJob/css/unionJob';

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
