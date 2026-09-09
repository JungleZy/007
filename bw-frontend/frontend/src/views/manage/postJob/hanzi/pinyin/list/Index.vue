<template>
  <div class="w-full h-full overflow-hidden layout-side">
    <div class="w-full h-full">
      <div class="w-full h-full grouping_content content-mask-bg">
        <div class="w-full table_search_box" style="padding: 10px 10px 0px 0px">
          <!--          <div class="item_group btn" @click="goWordTrain">单字训练</div>-->
          <div class="item_group btn" @click="addDrillModalInfo"><PlusOutlined />&nbsp;新增训练</div>
          <!--          <div class="item_group btn"  v-per="'grad'" @click="gradingRuleModalInfo"><SettingOutlined />&nbsp;评分规则</div>-->
          <div class="item_group btn"  @click="gradingRuleModalInfo"><SettingOutlined />&nbsp;评分规则</div>
        </div>

        <div class="w-full" style="max-height: calc(100% - 70px)">
          <div class="table_list_box overflow-auto" style="height: calc(100% - 35px);">
            <a-table :columns="columns" :loading="tableLoading" :rowKey="record => record.id" :pagination="false" :data-source="tableList">
              <template #type="{ text }">
                {{ text === 3 ? '军语词组' : '文章训练' }}
              </template>
              <template #speed="{ text }">
                {{ text === null ? '--' : text + '组/分' }}
              </template>
              <template #status="{ text }">
                <span class="tag finish" v-if="text === 2">已完成</span>
                <span class="tag pause" v-else-if="text === 3">已暂停</span>
                <span class="tag oper" v-else-if="text === 1">进行中</span>
                <span class="tag" v-else>未开始</span>
              </template>
              <template #action="{ record }">
                <div class="flex layout-center">
                  <div class="table_action_btn">
                    <div
                      class="table_btn"
                      :title="record.status === 2 ? '查看成绩' : record.status === 3 ? '继续训练' : '开始训练'"
                      @click="startTrain(record.id)"
                    >
                      <FileTextOutlined v-if="record.status === 2" />
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
          <div class="table_pagination">
            <div class="total">共{{ tableData.length }}条数据</div>
            <div class="item prev" @click="selectTablePage('-')"></div>
            <template v-for="(item, i) in Math.ceil(tableData.length / 10)" :key="i">
              <div
                :class="{ item: true, active: item === currTablePage }"
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
      :width="720"
      class="init_modal_style footer-border-none"
      v-model:visible="addDrillModal"
      @cancel="cancelTrainModal"
    >
      <template #title>
        <strong>新增拼音训练</strong>
      </template>
      <template #footer>
        <div></div>
      </template>
      <a-spin :spinning="loading">
        <div class="configurationBox" style="padding: 0 20px">
          <div class="layout-left-top" style="flex-wrap: wrap; justify-content: space-around">
            <div v-for="(v, index) of items" style="margin: 30px 0px 10px 0px" :key="index">
              <div class="hanziItems" :class="[v.type, v.value === trainData.type ? 'activeWZ' : '']" @click="clisckItem(v.value)">
                <div class="layout-right-top" v-if="index === 0">
                  <div class="cardText">726组</div>
                </div>
              </div>
              <div class="w-full" style="text-align: center">
                {{ v.text }}
              </div>
            </div>
          </div>
          <div v-if="article.length > 0" class="layout-left-center">
            <div class="wzLine"></div>
            <div class="layout-side wzBox">
              <div
                style="display: flex; justify-content: space-around; flex-wrap: wrap; width: 50%"
                @click="selectWZ(v.id)"
                v-for="(v, index) of article"
                :key="index"
              >
                <div class="wzCard layout-center relative">
                  <div class="layout-right-top" style="position: absolute; right: 10px; top: 10px">
                    <!--                    <div class="triangle"></div>-->
                    <div class="cardText">{{ v.wordSize }}字</div>
                  </div>
                  <div class="text" :title="v.name">{{ v.name }}</div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </a-spin>
    </a-modal>
    <GradeModal v-model:gradingRuleModal="gradingRuleModal" :type="2"></GradeModal>
  </div>
</template>

<script>
  export default {
    name: 'Index'
  }
</script>
<script setup>
  import NipUEditor from '../../../../../../components/common/NipUEditor.vue'
  import { useRouter, useRoute } from 'vue-router'
  import {ref, onMounted, provide, createVNode} from 'vue'
  import GradeModal from '../../../ditto/component/GradeModal.vue'
  import {
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
  import { timeFormatInfo, partTimeFormatInfo } from '../../../../../../common/utils/Utils.js'
  import homophone from '../../../../../../common/utils/Homophone.js'
  import { apiPostTrainGlobalRuleAddRule, apiPostTrainGlobalRuleDeleteById, apiPostTrainGlobalRuleType } from '../../../../../../common/api/postWording'
  const router = useRouter()
  const route = useRoute()
  const loading = ref(false)
  const addDrillModal = ref(false)
  const basicTrainDeployModal = ref(false)
  const hp = new homophone()
  const items = ref([
    { type: 'LYCZ', text: '军语词组', value: 3 },
    { type: 'WZLX', text: '文章练习', value: 4 }
  ])
  const gradingRuleModal = ref(false)
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
  const clisckItem = index => {
    trainData.value.type = index
    if (index === 4) {
      getArticle()
      return false
    }
    addTelexTrain()
  }
  const {
    columns,
    tableData,
    tableList,
    currTablePage,
    selectTablePage,
    tableLoading,
    selectType,
    initContent,
    trainData,
    addTelexTrain,
    article,
    getArticle,
    deleteHistory
  } = telegramList(addDrillModal)

  const selectWZ = (id, index) => {
    trainData.value.wordId = id
    addTelexTrain()
  }
  const startTrain = id => {
    router.push({
      path: route.matched[4].path + '/postPractice',
      query: {
        id
      }
    })
  }

  const gradingRuleModalInfo = () => {
    gradingRuleModal.value = true
  }

  //开始训练
  const goWordTrain = () => {
    router.push({
      path: route.matched[4].path + '/postPractice'
    })
  }
  /**
   * 新增训练
   */
  const addDrillModalInfo = () => {
    addDrillModal.value = true
  }
  /**
   * 关闭弹窗
   */
  const cancelTrainModal = () => {
    article.value = []
    trainData.value.type = null
    addDrillModal.value = false
  }
</script>

<style scoped>
  .triangle {
    border-right: 0px solid transparent;
    border-left: 16px solid transparent;
    border-top: 16px solid #ffa800;
    border-bottom: 0px solid transparent;
    height: 0px;
    width: 0px;
  }
  .cardText {
    height: 16px;
    min-width: 40px;
    /*background: #ffa800;*/
    color: #bfcde0;
    text-align: center;
    font-weight: bold;
    font-size: 12px;
    line-height: 16px;
  }
  .wzBox {
    width: 100%;
    max-height: 300px;
    overflow: auto;
  }
  .wzLine {
    background: url('../../../../../../assets/HJ/hanzi/line.png');
    width: 100%;
    height: 20px;
    margin-bottom: 15px;
  }
  .HJ .wzCard {
    background: url('../../../../../../assets/HJ/hanzi/wzDetails.png');
    width: 273px;
    height: 100px;
    margin: 10px;
    cursor: pointer;
  }
  .HJ .wzCardAc {
    background: url('../../../../../../assets/HJ/hanzi/wzDetails_avtive.png');
    width: 273px;
    height: 100px;
    margin: 10px;
    cursor: pointer;
  }
  .HJ .wzCard:hover {
    background: url('../../../../../../assets/HJ/hanzi/wzDetails_avtive.png');
  }
  .LJ .wzCard {
    background: url('../../../../../../assets/LJ/hanzi/wzDetails.png');
    width: 283px;
    height: 108px;
    margin: 10px;
    cursor: pointer;
  }
  .LJ .wzCardAc {
    background: url('../../../../../../assets/LJ/hanzi/wzDetails_active.png');
    width: 283px;
    height: 108px;
    margin: 10px;
    cursor: pointer;
  }
  .LJ .wzCard:hover {
    background: url('../../../../../../assets/LJ/hanzi/wzDetails_active.png');
  }

  .wzCard {
    background: url('../../../../../../assets/HJJ/hanzi/wzDetails.png');
    width: 283px;
    height: 108px;
    margin: 10px;
    cursor: pointer;
  }
  .wzCardAc {
    background: url('../../../../../../assets/HJJ/hanzi/wzDetails_active.png');
    width: 283px;
    height: 108px;
    margin: 10px;
    cursor: pointer;
  }
  .wzCard:hover {
    background: url('../../../../../../assets/HJ/hanzi/wzDetails_active.png');
  }
  .wzCard .text {
    width: 80%;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    text-align: center;
  }
  .hanziItems {
    width: 175px;
    height: 118px;
    margin: 10px;
    cursor: pointer;
  }
  .WZLX {
    background-image: url('../../../../../../assets/HJ/hanzi/WZ.png');
  }
  .activeWZ,
  .WZLX:hover {
    background-image: url('../../../../../../assets/HJ/hanzi/WZ-active.png');
  }
  .LYCZ {
    background-image: url('../../../../../../assets/HJ/hanzi/LYCZ.png');
  }
  .LYCZ:hover {
    background-image: url('../../../../../../assets/HJ/hanzi/LYCZ-active.png');
  }


  .LJ .WZLX {
    background-image: url('../../../../../../assets/LJ/hanzi/WZ.png');
  }
  .LJ .activeWZ,
  .LJ .WZLX:hover {
    background-image: url('../../../../../../assets/LJ/hanzi/WZ-active.png');
  }
  .LJ .LYCZ {
    background-image: url('../../../../../../assets/LJ/hanzi/LYCZ.png');
  }
  .LJ .LYCZ:hover {
    background-image: url('../../../../../../assets/LJ/hanzi/LYCZ-active.png');
  }
  .grouping_content {
    display: flex;
    flex-direction: column;
    padding: 0;
  }
  .configurationBox {
    padding: 10px 20px 20px;
    color: #fff;
  }

  .init_modal_style >>> .ant-modal-footer {
    border-top: none !important;
  }
</style>
