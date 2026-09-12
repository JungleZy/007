<template>
  <div class="w-full h-full overflow-hidden layout-side">
    <div class="w-full h-full">
      <div class="w-full h-full grouping_content content-mask-bg">
        <div class="w-full table_search_box">
          <div class="item_group btn" @click="addDrillModalInfo">
            <PlusOutlined/>&nbsp;新增训练
          </div>
          <!--          <div class="item_group btn" v-if="userRole.id != '2'" @click="gradingRuleModalInfo"><SettingOutlined />&nbsp;评分规则</div>-->
          <div class="item_group btn" @click="gradingRuleModalInfo">
            <SettingOutlined/>&nbsp;评分规则
          </div>
        </div>

        <div class="w-full" style="max-height: calc(100% - 70px)">
          <div class="table_list_box overflow-auto" style="height: calc(100% - 35px);">
            <a-table :columns="columns" :loading="tableLoading" :rowKey="record => record.id" :pagination="false"
                     :data-source="tableList">
              <template #type="{ text }">
                {{ text == 0 ? '数码报' : text == 1 ? '字码报' : text == 2 ? '混合报' : '--' }}
              </template>
              <template #isCable="{text}">
                {{ text == 0 ? '随机报' : '固定报' }}
              </template>
              <template #validTime="{ text, record }">
                {{ (record.activeMillis ?? text * 1000) > 0 ? partTimeFormatInfo(record.activeMillis ?? text * 1000, 'chinese') : '--' }}
              </template>
              <template #speed="{ text }">
                {{ text }}
              </template>
              <template #score="{ text }">
                {{ text ? text : '100' }}
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
                    <div class="table_btn" >
                      <DeleteOutlined style="color: red;" title="删除" @click="deleteModel(record)"/>
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
              <div :class="{ item: true, active: item == currTablePage }"
                   v-if="item > currTablePage - 3 && item < currTablePage + 3" @click="selectTablePage(item)">
                {{ item }}
              </div>
            </template>
            <div class="item next" @click="selectTablePage('+')"></div>
          </div>
        </div>
      </div>
    </div>

    <!--新增训练-->
    <a-modal :destroyOnClose="true" :width="560" class="init_modal_style footer-border-none"
             v-model:visible="addDrillModal" @cancel="cancelTrainModal">
      <template #title>
        <strong>手键拍发生成设置</strong>
      </template>
      <template #footer>
        <div class="w-full layout-center">
          <div :class="{ createDrillBtn: true, 'btn-animate': !loading, loadingBtn: loading }" @click="createDrillInfo">
            <a-spin v-if="loading" size="small"/>
            生成训练
          </div>
        </div>
      </template>
      <a-spin :spinning="loading">
        <div class="configurationBox">
          <div class="groupBoxs">
            <div class="groupTitle">训练配置</div>
            <div class="rowItem" style="margin-top: 16px">
              <div class="lab">训练名称：</div>
              <div class="item">
                <a-input v-model:value="formData.name" style="width: 200px; text-align: left">
                </a-input>
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
              <div class="lab">评分规则：</div>
              <div class="item">
                <a-select v-model:value="formData.ruleId" placeholder="请选择评分规则"
                          style="width: 200px; text-align: left">
                  <a-select-option v-for="(item, index) in ruleList" :value="item.id" :key="index">{{
                    item.title
                    }}
                  </a-select-option>
                </a-select>
              </div>
            </div>
            <template v-if="formData.isCable ==0">
              <div class="rowItem">
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
                  <a-input-number v-model:value="formData.messageNumber" :min="1" :max="100000" :step="1"
                                  placeholder="数量" style="width: 120px; "></a-input-number>
                </div>
                <div class="text" style=" margin-left: 5px">组</div>
                <div class="item" style="margin: 0" v-if="formData.type == 0">
                  <a-switch v-model:checked="formData.codeSort" checked-children="长码"
                            un-checked-children="短码"></a-switch>
                </div>
              </div>
              <!--              <div class="rowItem">-->
              <!--                <div class="lab">随机：</div>-->
              <!--                <div class="item layout-left-center">-->
              <!--                  <a-switch v-model:checked="formData.isRandom" checked-children="是" un-checked-children="否"></a-switch>-->
              <!--                </div>-->
              <!--              </div>-->
              <div class="rowItem" v-show="formData.isRandom">
                <div class="lab">平均报：</div>
                <div class="item layout-left-center">
                  <a-switch v-model:checked="formData.isAverage" checked-children="是"
                            un-checked-children="否"></a-switch>
                </div>
              </div>
            </template>
            <SelectCable v-else/>
          </div>
        </div>
      </a-spin>
    </a-modal>
    <!--手键拍发评分规则管理-->
    <a-modal :destroyOnClose="true" :width="980" class="init_modal_style footer-border-none" centered
             v-model:visible="gradingRuleModal" @cancel="cancelGradingRuleModal">
      <template #title>
        <strong>手键拍发评分规则管理</strong>
      </template>
      <template #footer>
        <div class="w-full layout-center"></div>
      </template>
      <HandKeyGradingRule/>
    </a-modal>
  </div>
</template>

<script>
  export default {
    name: 'HandKeyPostJob'
  }
</script>
<script setup>
  import {useRouter} from 'vue-router'
  import {ref, provide, createVNode} from 'vue'
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
  import telegramList from './js/telegram'
  import useMorse from '../../../../../common/mixin/useMorse.js'
  import {timeFormatInfo, partTimeFormatInfo} from '../../../../../common/utils/Utils.js'
  import {savePostTelegramTrain} from '../../../../../common/api/TelegramApi.js'
  import {getGradingRuleListByType} from '../../../../../common/api/GradingRuleApi.js'
  import HandKeyGradingRule from '../../../../../components/gradingRule/HandKey.vue'
  import {getCableAll} from "../../../../../common/api/CableApi.js";
  import SelectCable from "../../../../../components/cable/SelectCable.vue"

  const {baseCode} = useMorse()
  const router = useRouter()
  const drillPath = ref('')
  const scorePath = ref('')
  const deleteModel = (v) => {
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
  const userRole = ref(JSON.parse(localStorage.getItem('userRole')))
  router.getRoutes().forEach(r => {
    if (r.name === 'HandKeyPostJobTrain') {
      drillPath.value = r.path
    }
    if (r.name === 'PatTrainScore') {
      scorePath.value = r.path
    }
  })

  const loading = ref(false)
  const addDrillModal = ref(false)
  const gradingRuleModal = ref(false)
  const isAverage = ref(false)
  const ruleId = ref(null)
  const ruleList = ref([])
  const formData = ref({
    name: '',
    isCable: 0,
    cableId: null,
    startPage: 1,
    type: 0,
    codeSort: false,
    isAverage: false,
    isRandom: true,
    messageNumber: 100,
    ruleId: null,
    messageBody: []
  })
  const selectCable = ref(null)
  const cableList = ref([])
  const {columns, tableData, tableList, currTablePage, selectTablePage, tableLoading, deleteHistory} = telegramList()
  provide("selectCable", selectCable)
  provide("formData", formData)
  provide("cableList", cableList)
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
  /**
   * 切换训练类型
   */
  const selectType = () => {
    formData.value.codeSort = formData.value.type > 0
  }

  /**
   * 新增训练
   */
  const addDrillModalInfo = () => {
    formData.value.name = '手键拍发-' + timeFormatInfo(new Date().getTime(), 'string')
    addDrillModal.value = true
    getAllRuleInfo()
  }


  /**
   * 获取规则分类
   */
  const getAllRuleInfo = () => {
    getGradingRuleListByType({type: 0}).then(res => {
      if (res.code === 200) {
        ruleList.value = res.data
        res.data.forEach(item=>{
          if(item.isDefault===0){
            formData.value.ruleId = item.id
          }
        })

      }
    })
  }

  /**
   * 关闭弹窗
   */
  const cancelTrainModal = () => {
    formData.value = {
      name: '',
      isCable: 0,
      type: 0,
      codeSort: false,
      isAverage: false,
      isRandom: true,
      messageNumber: 100,
      ruleId: null,
      messageBody: []
    }
  }

  const gradingRuleModalInfo = () => {
    gradingRuleModal.value = true
  }

  const cancelGradingRuleModal = () => {
    gradingRuleModal.value = false
  }

  /**
   * 生成训练报底和报文数据
   */
  const generteTrainData = () => {
    let res = [],
        bwArr = [],
        code,
        createNumber = 0,
        word = [],
        len,
        arr = []
    if (!formData.value.messageNumber || formData.value.messageNumber === 0) {
      message.error('报文组数不能为空！')
      loading.value = false
      return false
    }
    if (!formData.value.ruleId || formData.value.ruleId === '') {
      message.error('评分规则不能为空！')
      loading.value = false
      return false
    }
    code = baseCode[formData.value.type === 0 ? '0_9' : formData.value.type === 1 ? 'A_Z' : 'mix']
    len = code.length
    createNumber = formData.value.messageNumber > 200 ? 200 : formData.value.messageNumber
    if (formData.value.isAverage) {
      arr = []
      bwArr = []
      for (let i = 0; i < createNumber * 4; i++) {
        arr.push(code[i % len])
        if (arr.length === createNumber * 4) {
          arr.sort((x, y) => (Math.random() > 0.5 ? -1 : 1))
          word = []
          for (let a = 0; a < arr.length; a++) {
            word.push(arr[a])
            if (word.length == 4) {
              bwArr.push({
                moresKey: JSON.stringify(word),
                moresValue: '[]',
                moresTime: '[]',
                patKeys: '[]'
              })
              word = []
            }
            if (bwArr.length >= 100 || a === arr.length - 1) {
              res.push(bwArr)
              bwArr = []
            }
          }
        }
      }
    } else {
      bwArr = []
      for (let j = 0; j < createNumber; j++) {
        if (formData.value.isRandom) {
          word = []
          for (let k = 0; k < 4; k++) {
            word.push(code[parseInt(Math.random() * len)])
          }
        } else {
          word = []
          for (let k = 0; k < 4; k++) {
            word.push(code[(bwArr.length * 4 + k) % len])
          }
        }
        bwArr.push({
          moresKey: JSON.stringify(word),
          moresValue: '[]',
          moresTime: '[]',
          patKeys: '[]'
        })
        if (bwArr.length >= 100 || j === createNumber - 1) {
          res.push(bwArr)
          bwArr = []
        }
      }
    }

    return res
  }

  /**
   * 生成训练
   */
  const createDrillInfo = () => {
    if (!formData.value.messageNumber) {
      message.error('报文组数不能为空！')
      return
    }
    if (loading.value) return false
    loading.value = true
    formData.value.name = '手键拍发-' + timeFormatInfo(new Date().getTime(), 'string')
    if (!formData.value.ruleId || formData.value.ruleId === '') {
      message.error('评分规则不能为空！')
      loading.value = false
      return
    }

    savePostTelegramTrain(formData.value).then(res => {
      loading.value = false
      if (res.code === 200) {
        message.success('生成训练成功！')
        router.push({path: drillPath.value, query: {id: res.data.id}})
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
    if (item.status === 2) {
      router.push({path: scorePath.value, query: {id: item.id}})
    } else {
      router.push({path: drillPath.value, query: {id: item.id}})
    }
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
