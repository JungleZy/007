<template>
  <div class="w-full h-full overflow-hidden layout-side">
    <div class="w-full h-full">
      <div class="w-full h-full grouping_content content-mask-bg">
        <div class="w-full table_search_box" style="padding: 10px 10px 0px 0px">
          <div class="item_group btn" @click="handleTrainModalInfo"><PlusOutlined />&nbsp;新增训练</div>
          <div class="item_group btn" v-if="userRole.id != '2'" @click="gradingRuleModalInfo"><SettingOutlined />&nbsp;评分规则</div>
        </div>
        <div class="w-full table_list_box" style="max-height: calc(100% - 70px)">
          <div class="table_list_box overflow-auto" style="height: calc(100% - 35px);">
            <a-table :columns="columns" :loading="tableLoading" :rowKey="record => record.id" :pagination="false" :data-source="tableList">
              <template #type="{ record }">
                {{ record.trainType == 0 ? (record.type == 0 ? '单词训练' : '短语训练') : record.type == 0 ? '用语填空' : '含义填空' }}
              </template>
              <template #duration="{ text }">
                {{ text == 0 ? '--' : partTimeFormatInfo(parseInt(text * 1000), 'chinese') }}
              </template>
              <template #speed="{ text }">
                {{ text == null ? '--' : text + '码/分' }}
              </template>
              <template #status="{ text }">
                <span class="tag finish" v-if="text == 2">已完成</span>
                <span class="tag oper" v-else-if="text == 1">进行中</span>
                <span class="tag" v-else>未开始</span>
              </template>
              <template #action="{ record }">
                <div class="flex layout-center">
                  <div class="table_action_btn">
                    <div class="table_btn" :title="record.status == 0 ? '开始训练' : '查看成绩'" @click="startTrain(record.id)">
                      <FileTextOutlined v-if="record.status == 2" />
                      <PlayCircleOutlined v-else />
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
              <div :class="{ item: true, active: item == currTablePage }" v-if="item > currTablePage - 3 && item < currTablePage + 3" @click="selectTablePage(item)">
                {{ item }}
              </div>
            </template>
            <div class="item next" @click="selectTablePage('+')"></div>
          </div>
        </div>
      </div>
    </div>
    <a-modal :destroyOnClose="true" :width="560" class="init_modal_style footer-border-none" v-model:visible="addTrainModal" @cancel="cancelTrainModal">
      <template #title>
        <strong>勤务用语生成设置</strong>
      </template>
      <template #footer>
        <div class="w-full layout-center">
          <div :class="{ createDrillBtn: true, 'btn-animate': !loading, loadingBtn: loading }" @click="handleCreateTrain">
            <a-spin v-if="loading" size="small" />
            生成训练
          </div>
        </div>
      </template>
      <a-spin :spinning="false">
        <div class="configurationBox" style="padding-bottom: 0">
          <div class="rowItem">
            <div class="lab">训练类型：</div>
            <div class="item" style="padding-left: 2px">
              <a-radio-group v-model:value="formData.trainType">
                <a-radio :value="0">听报</a-radio>
                <a-radio :value="1">用语/含义——（翻译）</a-radio>
              </a-radio-group>
            </div>
          </div>
          <div class="rowItem">
            <div class="lab">训练内容：</div>
            <div class="item" style="padding-left: 2px">
              <a-radio-group v-model:value="formData.type">
                <a-radio :value="0">{{ formData.trainType == 0 ? '单词' : '用语' }}</a-radio>
                <a-radio :value="1">{{ formData.trainType == 0 ? '语句' : '含义' }}</a-radio>
              </a-radio-group>
            </div>
          </div>
          <div class="rowItem">
            <div class="lab">数量：</div>
            <div class="item relative">
              <a-input-number v-model:value="formData.number" :min="1" :max="1000" style="width: 100px"></a-input-number>
            </div>
          </div>
          <div class="rowItem">
            <div class="lab">播报码率：</div>
            <div class="item" style="width: 160px">
              <a-input-number v-model:value="formData.speed" :min="10" :max="500" style="width: 100px"></a-input-number>
              (码/分)
            </div>
          </div>
          <div class="rowItem" v-if="formData.trainType===0">
            <div class="lab">干扰类型：</div>
            <div class="item" style="width: 300px">
              <a-checkbox-group v-model:value="formData.disturb" style="display: flex; align-items: flex-end; flex-wrap: wrap">
                <a-checkbox v-for="v of interfereOption" :value="v.value" :key="v.value" style="margin: 5px 0 0 0; width: 100px">{{ v.label }}</a-checkbox>
              </a-checkbox-group>
            </div>
          </div>
        </div>
      </a-spin>
    </a-modal>
    <GradeModal v-model:gradingRuleModal="gradingRuleModal" :type="0"></GradeModal>
  </div>
</template>

<script>
export default {
  name: 'DittoPostWording'
}
</script>
<script setup>
  import {createVNode, nextTick, onMounted, ref, watch} from 'vue'
import { useRouter } from 'vue-router'
  import {ExclamationCircleOutlined, PlusOutlined} from '@ant-design/icons-vue'
import GradeModal from '../component/GradeModal.vue'
import { FileTextOutlined, PlayCircleOutlined, SettingOutlined, CloseCircleOutlined,DeleteOutlined } from '@ant-design/icons-vue'
import { partTimeFormatInfo } from '../../../../../common/utils/Utils.js'
import wordList from './js/wording.js'
  import {Modal} from "ant-design-vue";

const router = useRouter()
// 评分规则是全局配置，后端已加 @RequireAdmin：普通人员点开也只会拿到 207，入口同步隐藏。
const userRole = ref(JSON.parse(localStorage.getItem('userRole')))
const fileUrl = ref(window.fileUrl)
const addTrainModal = ref(false)
const loading = ref(false)
const trainPath = ref('')
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
const { columns, tableData, tableList, currTablePage, selectTablePage, tableLoading, selectType, initContent, formData, addWordTrain, article, getGradeTypeList, addGradeType, basicDeployData, deleteGradeType, gradingRuleModal,deleteHistory } = wordList()

const interfereOption = ref([
  {
    label: '白噪音',
    value: 1
  },
  {
    label: '俄语',
    value: 2
  },
  {
    label: '日语',
    value: 3
  },
  {
    label: '英语',
    value: 4
  },
  {
    label: '战场音',
    value: 5
  },
  {
    label: '防空警报',
    value: 6
  }
])

onMounted(() => {
  router.getRoutes().forEach(r => {
    if (r.name === 'DittoPostWordingTrain') {
      trainPath.value = r.path
    }
  })
})

const gradingRuleModalInfo = () => {
  gradingRuleModal.value = true
}
const handleTrainModalInfo = () => {
  addTrainModal.value = true
}
const cancelTrainModal = () => {
  addTrainModal.value = false
}
const handleCreateTrain = () => {
  addWordTrain()
}
const startTrain = id => {
  router.push({
    path: trainPath.value,
    query: {
      id
    }
  })
}
</script>

<style scoped lang="less">
  .HJ,.HJJ{
    .configurationBox .rowItem .item {
      font-size: 13px;
      color: #a9abaa;
      width: 170px;
    }
  }
  .LJ{
    .configurationBox .rowItem .item {
      font-size: 13px;
      color: #a9abaa!important;
      width: 170px;
    }
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
