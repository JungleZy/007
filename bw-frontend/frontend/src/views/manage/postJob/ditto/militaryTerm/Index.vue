<template>
  <div class="w-full h-full overflow-hidden layout-side">
    <div class="w-full h-full">
      <div class="w-full h-full grouping_content content-mask-bg">
        <div class="w-full table_search_box" style="padding: 10px 10px 0px 0px">
          <div class="item_group btn" @click="handleTrainModalInfo"><PlusOutlined />&nbsp;新增训练</div>
          <div class="item_group btn" v-if="userRole.id != '2'" @click="basicDeploy">评分规则</div>
        </div>
        <div class="w-full" style="max-height: calc(100% - 70px)">
          <div class="table_list_box overflow-auto" style="height: calc(100% - 35px);">
            <a-table :columns="columns" :loading="tableLoading" :rowKey="record => record.id" :pagination="false" :data-source="tableList">
              <template #types="{ text }">
                <template v-if="text.length == 0">全部类型</template>
                <template v-else>
                  <template v-for="txt in text">【{{ txt }}】</template>
                </template>
              </template>
              <template #totalNumber="{ text }">
                {{ text }}
              </template>
              <template #duration="{ text }">
                {{ text == 0 ? '--' : partTimeFormatInfo(parseInt(text * 1000), 'chinese') }}
              </template>
              <template #score="{ text }">
                {{ text ? text : '--' }}
              </template>
              <template #status="{ text }">
                <span class="tag finish" v-if="text >= 2">已完成</span>
                <span class="tag oper" v-else-if="text == 1">进行中</span>
                <span class="tag" v-else>未开始</span>
              </template>
              <template #action="{ record }">
                <div class="flex layout-center">
                  <div class="table_action_btn">
                    <div class="table_btn" :title="record.status >= 2 ? '查看成绩' : '开始训练'" @click="startTrain(record)">
                      <FileTextOutlined v-if="record.status == 2" />
                      <PlayCircleOutlined v-else />
                    </div>
                    <div class="table_btn" >
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

    <a-modal
      :destroyOnClose="true"
      :width="480"
      class="init_modal_style footer-border-none"
      v-model:visible="addTrainModal"
      @cancel="addTrainModal = false"
    >
      <template #title>
        <strong>军语密语生成设置</strong>
      </template>
      <template #footer>
        <div class="w-full layout-center">
          <div :class="{ createDrillBtn: true, 'btn-animate': !loading, loadingBtn: loading }" @click="handleCreateTrain">
            <a-spin v-if="loading" size="small" />
            生成训练
          </div>
        </div>
      </template>
      <a-spin :spinning="loading">
        <div class="configurationBox" style="padding: 10px 0 0">
          <div class="rowItem">
            <div class="lab">军语类型：</div>
            <div class="item" style="padding-left: 2px">
              <a-radio-group v-model:value="formData.checkType" @change="selectTypeInfo">
                <a-radio :value="0">全部类型</a-radio>
                <a-radio :value="1">自定义</a-radio>
              </a-radio-group>
            </div>
          </div>
          <div class="rowItem" v-if="formData.checkType == 1">
            <div class="lab">军语类型：</div>
            <div class="item">
              <a-select v-model:value="formData.type" mode="multiple" placeholder="请选择军语类型" style="width: 220px; text-align: left">
                <a-select-option v-for="item in typeList" :key="item.key" :value="item.id">{{ item.key }}</a-select-option>
              </a-select>
            </div>
          </div>
          <div class="rowItem">
            <div class="lab">军语总数：</div>
            <div class="item">
              <a-input-number v-model:value="formData.number" :min="1" :max="1000" placeholder="数量" style="width: 156px"></a-input-number>
            </div>
          </div>
        </div>
      </a-spin>
    </a-modal>
    <GradeModal v-model:gradingRuleModal="basicTrainDeployModal" :type="1"></GradeModal>
  </div>
</template>

<script>
export default {
  name: 'DittoPostMilitaryTerm'
}
</script>
<script setup>
  import {createVNode, onMounted, ref} from 'vue'
import { useRouter } from 'vue-router'
import {message, Modal} from 'ant-design-vue'
import GradeModal from '../component/GradeModal.vue'
  import {
    PlusOutlined,
    PlayCircleOutlined,
    FileTextOutlined,
    CloseCircleOutlined,
    DeleteOutlined,
    ExclamationCircleOutlined
  } from '@ant-design/icons-vue'
import termList from './js/list.js'
import { timeFormatInfo, partTimeFormatInfo } from '../../../../../common/utils/Utils.js'
import { getMilitaryType, addPostMilitaryTrain } from '../../../../../common/api/MilitaryTermApi.js'
import { apiPostTrainGlobalRuleAddRule, apiPostTrainGlobalRuleDeleteById, apiPostTrainGlobalRuleType } from '../../../../../common/api/postWording'
const router = useRouter()
// 评分规则是全局配置，后端已加 @RequireAdmin：普通人员点开也只会拿到 207，入口同步隐藏。
const userRole = ref(JSON.parse(localStorage.getItem('userRole')))
const fileUrl = ref(window.fileUrl)
const addTrainModal = ref(false)
const loading = ref(false)
const typeList = ref([])
const formData = ref({
  name: '',
  checkType: 0,
  type: [],
  number: 100
})
const trainPath = ref('')
const basicTrainDeployModal = ref(false)
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
onMounted(() => {
  router.getRoutes().forEach(r => {
    if (r.name === 'DittoPostMilitaryTermTrain') {
      trainPath.value = r.path
    }
  })
})

const { columns, tableData, tableList, currTablePage, selectTablePage, tableLoading,deleteHistory } = termList()

/**
 * 切换军语类型
 */
const selectTypeInfo = () => {
  if (formData.value.checkType === 0) {
    formData.value.type = []
  }
}

/**
 * 打开新增训练弹窗
 */
const handleTrainModalInfo = () => {
  addTrainModal.value = true
  if (typeList.value.length === 0) {
    getMilitaryType().then(res => {
      if (res.code === 200) {
        typeList.value = res.data.filter(item => item.child.length > 0)
      }
    })
  }
}

/**
 * 生成训练
 * @returns {boolean}
 */
const handleCreateTrain = () => {
  if (!formData.value.number || formData.value.number === '' || formData.value.number <= 0) {
    message.error('请输入训练军语数量！')
    return false
  }
  let name = '军语密语-' + timeFormatInfo(new Date().getTime(), 'string')
  loading.value = true
  addPostMilitaryTrain({
    name: name,
    types: formData.value.type,
    totalNumber: formData.value.number
  }).then(res => {
    loading.value = false
    if (res.code === 200) {
      message.success('军语密语训练生成成功！')
      router.push({ path: trainPath.value, query: { id: res.data.id } })
    } else {
      message.error(res.message)
    }
  })
}

/**
 * 开始训练
 * @param item
 */
const startTrain = item => {
  router.push({ path: trainPath.value, query: { id: item.id } })
}
/**
 * 开始基础练习配置
 */
const basicDeploy = () => {
  basicTrainDeployModal.value = true
}
</script>
<style scoped lang="less">
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
  text-align: center;
}
.configurationBox .rowItem .item + .item {
  margin-left: 30px;
}
.configurationBox .rowItem.mini .item {
  width: 170px;
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
</style>
