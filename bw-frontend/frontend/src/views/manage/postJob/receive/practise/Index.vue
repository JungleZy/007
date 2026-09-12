<template>
  <div class="w-full h-full overflow-hidden layout-side">
    <div class="w-full h-full">
      <div class="w-full h-full grouping_content content-mask-bg">
        <div class="w-full table_search_box">
          <div class="item_group btn" @click="addDrillModalInfo"><PlusOutlined />&nbsp;新增训练</div>
          <!-- 码速配置是全局写端点，后端已加 @RequireAdmin：普通人员点开也只会拿到 207，入口同步隐藏。
               读端点 findAll 不受影响，新增训练弹窗仍能取到档位。 -->
          <div class="item_group btn" v-if="userRole.id != '2'" @click="basicDeploy">基础配置</div>
        </div>

        <div class="w-full" style="max-height: calc(100% - 70px)">
          <div class="table_list_box" style="height: calc(100% - 35px);">
            <a-table :columns="columns" :loading="tableLoading" :rowKey="record => record.id" :pagination="false" :data-source="tableData.data">
              <template #type="{ text }">
                {{ text == 0 ? '数码报' : text == 1 ? '字码报' : '混合报' }}
              </template>
              <template #isCable="{ text }">
                {{ text.isCable === 1 ? '固定报' :'随机报' }}
              </template>
              <template #codeMessageBody="{ text }">
                {{ text.length }}
              </template>
              <template #totalNumber="{ record }">
                {{record.totalNumber}}
              </template>
              <template #validTime="{ text }">
                {{ text == 0 ? '--' : partTimeFormatInfo(parseInt(text * 1000), 'chinese') }}
              </template>
              <template #rate="{ text }">
                {{ text ? text + (wpmTOmm ? '码/分' : 'WPM') : '--' }}
              </template>
              <template #status="{ text }">
                <span class="tag finish" v-if="text == 3">已完成</span>
                <span class="tag pause" v-else-if="text == 2">待评分</span>
                <!--<span class="tag oper" v-else-if="text==1">进行中</span>-->
                <span class="tag" v-else>未开始</span>
              </template>
              <template #score="{ text }">
                {{ text == null ? '100' : text }}
              </template>
              <template #action="{ record }">
                <div class="flex layout-center">
                  <div class="table_action_btn">
                    <div class="table_btn" :title="record.status == 3 ? '查看成绩' : record.status == 2 ? '上传评分' : '开始训练'" @click="startTrain(record)">
                      <FileTextOutlined v-if="record.status == 3" />
                      <FundViewOutlined v-else-if="record.status == 2" />
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
          <Pagination
            v-if="tableData.totalPage"
            :totalNumber="tableData.totalNumber"
            :pageAll="tableData.totalPage"
            :currentPage="currTablePage"
            :selectTablePage="selectTablePage"
          ></Pagination>
        </div>
      </div>
    </div>

    <!--新增训练-->
    <a-modal :destroyOnClose="true" :width="isHeader?900:700" class="init_modal_style footer-border-none" v-model:visible="addDrillModal" @cancel="cancelTrainModal">
      <template #title>
        <strong>训练配置</strong>
      </template>
      <template #footer>
        <div class="w-full layout-center">
          <div :class="{ createDrillBtn: true, 'btn-animate': !loading, loadingBtn: loading }" @click="createDrillInfo">
            <a-spin v-if="loading" size="small" />
            生成训练
          </div>
        </div>
      </template>
      <a-spin :spinning="loading">
        <div class="configurationBox" style="padding-bottom: 0">
          <div class="groupBoxs layout-left-top">
            <div class="groupTitle">训练配置</div>
            <div :style="{width:isHeader?'50%':'100%'}">
              <div class="rowItem" style="margin-top: 16px">
                <div class="lab">训练名称：</div>
                <div class="item">
                  <a-input v-model:value="formData.name" style="width: 200px; text-align: left">
                  </a-input>
                </div>
              </div>
              <div class="rowItem">
                <div class="lab" style="width: max-content">是否添加报头：</div>
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
                <a-radio-group v-model:value="formData.isCable" @change="selectIsCable">
                  <a-radio :value="0">随机报</a-radio>
                  <a-radio :value="1">固定报</a-radio>
                </a-radio-group>
              </div>
              <div class="rowItem">
                <div class="lab">低速训练：</div>
                <div class="item layout-left-center">
                  <a-switch v-model:checked="formData.isLowRate" checked-children="是" un-checked-children="否" @change="lowRateTrain()"></a-switch>
                </div>
              </div>
              <div class="rowItem" v-if="!formData.isLowRate">
                <div class="lab">播报码率：</div>
                <div class="item" style="width: 200px">
                  <a-select v-model:value="playRate" placeholder="请选择播报码率" @change="selectRateInfo" style="width: 210px; text-align: left">
                    <a-select-option v-for="(item, index) in basicDeployData" :value="item.speed" :key="index">{{ item.name }} ({{ item.speed }}{{ wpmTOmm ? '码/分' : 'WPM' }})</a-select-option>
                    <a-select-option :value="0">自定义</a-select-option>
                  </a-select>
                </div>
              </div>
              <div class="rowItem" v-if="playRate == 0 || formData.isLowRate">
                <div class="lab">{{ formData.isLowRate ? '平均速度（符号35字符/分）' : '自定义码率' }}：</div>
                <div class="item flex" style="width: 100%; align-items: center">
                  <div>{{ formData.isLowRate ? '1' : '40' }}</div>
                  <div class="formSlider" style="width: 266px">
                    <a-slider v-model:value="formData.rate" v-if="formData.isLowRate" :min="1" :max="35"></a-slider>
                    <a-slider v-model:value="formData.rate" v-else :min="40" :max="500"></a-slider>
                  </div>
                  <div>{{ formData.isLowRate ? '35' : '500' }}</div>
                </div>
              </div>
              <div class="rowItem">
                <div class="lab">领发报文：</div>
                <div class="item layout-left-center">
                  <a-switch v-model:checked="formData.isRatio" checked-children="是" un-checked-children="否" ></a-switch>
                </div>
              </div>
              <div class="rowItem" v-if="formData.isRatio">
                <div class="lab">点划比例：</div>
                <div class="item layout-left-center" style="color: white">
                  <div style="padding: 0 8px;font-size: 12px;color:white;border-right: 1px solid #354971;background: #141e28;line-height: 32px;width: 32px ">  1   </div>
                  ：
                  <a-input-number v-model:value="formData.ratio" :min="3" :max="7" placeholder="数量"  style="width: 60px;"></a-input-number>
                </div>
              </div>
              <div class="rowItem" style="padding-top: 0">
                <div class="lab">播报干扰：</div>
                <div class="item layout-left-center" style="width: 360px">
                  <a-checkbox-group v-model:value="disturbArray" style="display: flex; align-items: flex-end; flex-wrap: wrap">
                    <a-checkbox v-for="item of disturbList" :key="item.type" :value="item.type" style="margin: 8px 0 0 0; width: 100px; display: flex">{{ item.name }}</a-checkbox>
                  </a-checkbox-group>
                </div>
              </div>
            </div>
            <div v-if="isHeader" style="width: 50%">
              <div class="rowItem" style="margin-top: 16px">
                <div class="lab">报头信息</div>
              </div>
              <div class="rowItem" >
                <div class="lab">号数(NR)：</div>
                <a-input-number v-model:value="messageHeader.nr"  :min="1" :max="1000" :step="1"  />
              </div>
              <div class="rowItem" >
                <div class="lab">组数(CK)：</div>
                <div>{{formData.totalNumber}}</div>
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
            <template v-if="formData.isCable ==0">
              <div class="rowItem" style="margin-top: 16px">
                <div class="lab">类型：</div>
                <div class="item" style="padding-left: 2px">
                  <a-radio-group v-model:value="formData.type" @change="changeTrainType">
                    <a-radio :value="0">数码报</a-radio>
                    <a-radio :value="1">字码报</a-radio>
                    <a-radio :value="2">混合报</a-radio>
                  </a-radio-group>
                </div>
              </div>
              <div class="rowItem">
                <div class="lab">{{ formData.type == 0 ? '数码报' : formData.type == 1 ? '字码报' : '混合报' }}：</div>
                <div class="item relative">
                  <a-input-number v-model:value="formData.totalNumber" :min="1" :max="100000" placeholder="数量" style="width: 120px;"></a-input-number>
                </div>
                <div class="item" style="margin: 0" v-if="formData.type == 0">
                  <a-switch v-model:checked="numberType" checked-children="长码" un-checked-children="短码"></a-switch>
                </div>
              </div>
              <div class="rowItem">
                <div class="lab">随机：</div>
                <div class="item layout-left-center">
                  <a-switch v-model:checked="formData.isRandom" checked-children="是" un-checked-children="否"></a-switch>
                </div>
              </div>
              <div class="rowItem" v-if="formData.isRandom">
                <div class="lab">平均报：</div>
                <div class="item layout-left-center">
                  <a-switch v-model:checked="formData.isAvg" checked-children="是" un-checked-children="否"></a-switch>
                </div>
              </div>
            </template>
            <SelectCable class="mt-2" v-else/>
          </div>
        </div>
      </a-spin>
    </a-modal>

    <!--基础练习配置-->
    <a-modal :destroyOnClose="true" :width="530" class="init_modal_style footer-border-none" v-model:visible="basicTrainDeployModal" @cancel="cancelTrainModal">
      <template #title>
        <strong>基础配置</strong>
      </template>
      <template #footer>
        <div class="w-full layout-center">
          <div :class="{ createDrillBtn: true, 'btn-animate': !loading, loadingBtn: loading }" @click="basicTrainDeployModal = false"><a-spin v-if="loading" size="small" /> 取消编辑</div>
          <div :class="{ createDrillBtn: true, 'btn-animate': !loading, loadingBtn: loading }" @click="saveDeploy"><a-spin v-if="loading" size="small" /> 保存配置</div>
        </div>
      </template>
      <a-spin :spinning="loading">
        <div class="configurationBox">
          <div class="groupBoxs">
            <div class="groupTitle">播报码率配置</div>
            <div class="rowItem title mini">
              <div class="item" style="width: 80px">类型</div>
              <div class="item">码率({{ wpmTOmm ? '码/分' : 'WPM' }})</div>
              <div class="item" style="width: 220px">文案</div>
            </div>
            <div class="rowItem mini" v-for="(item, index) in basicDeployData" :key="index">
              <div class="item" style="width: 80px">
                <a-input v-model:value="item.name" placeholder="类型" style="width: 100%; text-align: center"></a-input>
              </div>
              <div class="item flex">
                <a-input-number v-model:value="item.speed" :min="20" :max="500" placeholder="码率" style="width: 100px"></a-input-number>
              </div>
              <div class="item relative" style="width: 220px">
                <a-input v-model:value="item.text" placeholder="请输入文案说明" style="width: 100%"></a-input>
                <div class="close" @click="closeBasicNorm(index)"><CloseCircleOutlined /></div>
              </div>
            </div>
            <div class="rowItem mini">
              <div class="item" style="width: auto">
                <div class="item_group btn layout-center" @click="createBasicNorm"><PlusOutlined style="margin-right: 6px" /> 新增码率</div>
              </div>
            </div>
          </div>
          <div class="groupBoxs">
            <div class="groupTitle">低速配置</div>
            <div class="rowItem mini mt-2">
              <div class="item">符号速度固定35字符/分；降低平均速度只扩展字、组和页间隔。</div>
              <div class="auditionBtn" @click="auditionInfo">试听</div>
            </div>
          </div>
        </div>
      </a-spin>
    </a-modal>
  </div>
</template>

<script>
  export default {
    name: 'ReceivePostPractise'
  }
</script>
<script setup>
  import { useRouter } from 'vue-router'
  import {ref, onMounted, inject, provide, createVNode} from 'vue'
  import {
    PlayCircleOutlined,
    DeleteOutlined,
    FileTextOutlined,
    PlusOutlined,
    FundViewOutlined,
    CloseCircleOutlined,
    WarningOutlined,
    createFromIconfontCN, ExclamationCircleOutlined
  } from '@ant-design/icons-vue'
  import {message, Modal} from 'ant-design-vue'
  import telegramList from './js/telegram'
  import useMorse from '../../../../../common/mixin/useMorse.js'
  import { timeFormatInfo, partTimeFormatInfo } from '../../../../../common/utils/Utils.js'
  import { getPostReceiveSetting, savePostReceiveSetting, saveReceivePostTrain,saveHeader } from '../../../../../common/api/ReceiveApi.js'
  import operationMorseVoice from '../../../../../common/utils/voice/operationMorseVoice'
  import {calculateTiming} from '../../../../../common/utils/voice/MorseVoiceHighPerformance'
  import {getCableAll} from "../../../../../common/api/CableApi";
  import SelectCable from "../../../../../components/cable/SelectCable.vue"

  import Pagination from '../../../../../components/common/Pagination.vue'
  const wpmTOmm = inject('wpmTOmm')
  const { baseCode, morseCode } = useMorse()
  const {operation, ensureReady} = operationMorseVoice()
  const router = useRouter()
  const userRole = ref(JSON.parse(localStorage.getItem('userRole')))
  const drillPath = ref('')
  const scorePath = ref('')
  const changeAnswer = (e)=>{
    console.log(e.target.innerText)
  }
  router.getRoutes().forEach(r => {
    if (r.name === 'ReceivePostTrain') {
      drillPath.value = r.path
    }
    if (r.name === 'ReceivePostScore') {
      scorePath.value = r.path
    }
  })
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
  const loading = ref(false)
  const addDrillModal = ref(false)
  const numberType = ref(false)
  const isHeader = ref(false)
  const messageHeader = ref({
    nr:'11',
    plb:'55',
    date:'',
    year:'',
    remaks:''
  })
  const telegraghNumber = ref(100)
  const disturbArray = ref([])
  const formData = ref({
    name: '收报训练-' + timeFormatInfo(new Date().getTime(), 'string'),
    rate: 60,
    type: 0,
    codeShort: 1,
    disturb: '[]',
    isLowRate: false,
    codeMessageBody: [],
    isAvg: false,
    isRandom: true,
    totalNumber: 100,
    ratio:3,
    isRatio:false,
    isCable:0,
    startPage: 1,
    isStartSign:true
  })
  const disturbList = ref([
    { type: 1, name: '白噪音' },
    { type: 2, name: '俄语' },
    { type: 3, name: '日语' },
    { type: 4, name: '英语' },
    { type: 5, name: '战场音' },
    { type: 6, name: '防空警报' }
  ])

  const playRate = ref(60)
  const basicTrainDeployModal = ref(false)
  const basicDeployData = ref([])

  const selectCable = ref(null)
  const cableList = ref([])
  const content = ref([]);
  const ruleId = ref('');
  const ruleList = ref([]);
  provide("selectCable",selectCable)
  provide("formData",formData)
  provide("cableList",cableList)

  const selectIsCable = () => {
    if (formData.value.isCable === 1) {
      getCableAll({scope: [0, 2]}).then(res => {
        cableList.value = res.data
        formData.value.cableId = cableList.value[0].id
        formData.value.type = cableList.value[0].codeType
        selectCable.value = cableList.value[0]
      })
    } else {
      formData.value.cableId = null
      selectCable.value = null
    }
    console.log(formData.value)
  }
  const { columns, tableData, currTablePage, selectTablePage, tableLoading,deleteHistory } = telegramList()

  /**
   * 新增训练
   */
  const addDrillModalInfo = () => {
    addDrillModal.value = true
    getBasicSettingInfo()
  }

  /**
   * 切换训练类型
   */
  const changeTrainType = () => {
    if (formData.value.type === 1) {
      numberType.value = false
    } else {
      numberType.value = true
    }
  }

  /**
   * 关闭弹窗
   */
  const cancelTrainModal = () => {
    isHeader.value = false
    messageHeader.value = {
      nr:'11',
      plb:'55',
      date:'',
      year:'',
      remaks:''
    }
    formData.value = {
      name: '收报训练-' + timeFormatInfo(new Date().getTime(), 'string'),
      rate: 60,
      type: 0,
      codeShort: 1,
      disturb: '[]',
      isLowRate: false,
      codeMessageBody: [],
      isAvg: false,
      isRandom: true,
      totalNumber: 100,
      ratio:3,
      isRatio:false,
      isCable:0,
      isStartSign:true
    }
  }

  /**
   * 选择播报码率
   */
  const selectRateInfo = () => {
    if (playRate.value > 0) {
      formData.value.rate = playRate.value
    } else {
      formData.value.rate = 60
    }
  }

  /**
   * 开始低速训练
   */
  const lowRateTrain = () => {
    formData.value.rate = formData.value.isLowRate ? 35 : 60
  }

  /**
   * 生成训练
   */
  const createDrillInfo = () => {
    if (loading.value) return false
    if (!formData.value.totalNumber) {
      message.error('请输入报文数量')
      return
    }
    if(isHeader.value){
      const reg = /^[0-9]+$/
      if (messageHeader.value.nr==='') {
        message.error('请输入报头号数！')
        return
      }
      if (messageHeader.value.plb==='') {
        message.error('请输入报头等级！')
        return
      }
      if(!reg.test(messageHeader.value.year)){
        message.error('请检查报头年月！')
        return
      }
      if(!reg.test(messageHeader.value.date)){
        message.error('请检查报头时分！')
        return
      }
      if (messageHeader.value.remaks!=='') {
        const reg = /^[A-Za-z0-9 ]+$/
        if(!reg.test(messageHeader.value.remaks)){
          message.error('请输数字加字母组合的附注！')
          return
        }
      }
    }
    loading.value = true
    formData.value.isLowRate = formData.value.isLowRate ? 1 : 0
    formData.value.disturb = JSON.stringify(disturbArray.value)
    // formData.value.name = '收报训练-' + timeFormatInfo(new Date().getTime(), 'string')
    formData.value.codeShort = numberType.value ? 1 : 0
    if (!formData.value.codeMessageBody) return false
    const data = {
      ...formData.value,
      isAvg: formData.value.isAvg ? 1 : 0,
      isRandom: formData.value.isRandom ? 1 : 0,
      isStartSign: formData.value.isStartSign ? 1 : 0,
    }
    if (data.isAvg == 1) {
      data.isRandom = 0
    }
    if (data.isCable === 1) {
      //  data.totalNumber = selectCable.value.groupCount - ((data.startPage - 1) * 100)
    }
    saveReceivePostTrain(data).then(res => {
      loading.value = false
      let trainId = res.data.id
      if (res.code === 200) {
        message.success('生成训练成功！')
        //生成报头
        if(isHeader.value){
          const header = generateMessageHeader(res.data)
          saveHeader({trainId:res.data.id,content:header}).then(res=>{
            if(res.code===200){
              router.push({ path: drillPath.value, query: { id: trainId} })
            }else {
              message.error('报头生成失败！')
            }
          })
        }else {
          router.push({ path: drillPath.value, query: { id: res.data.id } })
        }
      } else {
        message.error(res.message)
      }
    })
  }
  const isHeaderChange = ()=>{
    if(isHeader.value){
      const now = new Date();
      const month = String(now.getMonth() + 1).padStart(2, '0')
      const date = String(now.getDate()).padStart(2, '0')
      const hours =  String(now.getHours()).padStart(2, '0')
      const minutes = String(now.getMinutes()).padStart(2, '0')
      messageHeader.value.year = `${month}${date}`
      messageHeader.value.date = `${hours}${minutes}`
    }
  }
  //生成报头数据
  const generateMessageHeader = (data)=>{

    let header = `NR${messageHeader.value.nr} CK${data.totalNumber} ${messageHeader.value.plb} ${messageHeader.value.year.replaceAll("0","T")} ${messageHeader.value.date.replaceAll("0","T")}`
    if(messageHeader.value.remaks)header+=' RMKS '+messageHeader.value.remaks
    return header.toUpperCase()
  }
  /**
   * 开始训练/上传评分/查看成绩
   * @param item
   */
  const startTrain = item => {
    if (item.status === 3) {
      router.push({ path: scorePath.value, query: { id: item.id } })
    } else {
      router.push({ path: drillPath.value, query: { id: item.id } })
    }
  }

  /**
   * 开始基础练习配置
   */
  const basicDeploy = () => {
    basicTrainDeployModal.value = true
    getBasicSettingInfo()
  }

  /**
   * 获取基础练习配置
   */
  const getBasicSettingInfo = () => {
    getPostReceiveSetting().then(res => {
      if (res.code === 200) {
        basicDeployData.value = []
        if (res.data.length === 0) {
          basicDeployData.value.push({ name: '', speed: '', text: '' })
        } else {
          res.data.map((item, i) => {
            if (i === 1) {
              formData.value.rate = item.rate
              playRate.value = item.rate
            }
            basicDeployData.value.push({ id: item.id, name: item.type, speed: item.rate, text: item.text })
          })
        }
      }
    })
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
    let res = [],
        flag = false
    for (let item of basicDeployData.value) {
      if (item.name === '' || item.speed === '' || item.text === '') {
        flag = true
      }
      res.push({
        type: item.name,
        rate: item.speed,
        text: item.text
      })
    }
    if (flag) {
      return false
    } else {
      return res
    }
  }

  /**
   * 保存基础练习配置
   */
  const saveDeploy = () => {
    let data = handleBasicData()
    if (!data) {
      message.warning('配置输入框不能为空！')
      return false
    }
    savePostReceiveSetting({
      paramList: data,
    }).then(res => {
      loading.value = false
      if (res.code === 200) {
        message.success('播报码率配置成功！')
        basicTrainDeployModal.value = false
      } else {
        message.error(res.message)
      }
    })
  }

  const auditionInfo = async () => {
    if (!await ensureReady()) return
    operation({type: 'configure', data: {...calculateTiming({rate: 35, type: 'short'}), frequency: 1000, volume: 1, model: true}})
    operation({type: 'message', data: {numType: 'short', data: '5555 '}})
  }
</script>

<style scoped lang="less">
  @import "../../css/addTrainStyle.less";
  :deep(.ant-modal-footer ){
    border-top: none !important;
  }
  .editDiv{
    min-width: 80px;
    height: 30px;
    border-bottom: 1px solid #ffffff;
    line-height: 30px;
    text-align: center;
  }


</style>
