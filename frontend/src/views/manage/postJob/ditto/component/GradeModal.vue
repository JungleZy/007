<template>
  <a-modal :destroyOnClose="true" :width="530" class="init_modal_style footer-border-none" v-model:visible="props.gradingRuleModal" @cancel="handleCancel">
    <template #title>
      <strong>评分规则</strong>
    </template>
    <a-spin :spinning="loading">
      <div class="configurationBox">
        <div class="groupBoxs">
          <div class="groupTitle">评分配置</div>
          <div class="rowItem title mini">
            <div class="item" style="width: 80px">类型</div>
            <div class="item">正确率区间</div>
            <div class="item" style="width: 160px">文案</div>
          </div>
          <div class="rowItem mini" v-for="(item, index) in basicDeployData" :key="index">
            <div class="item" style="width: 80px">
              <a-input v-model:value="item.level" placeholder="类型" style="width: 100%; text-align: center"></a-input>
            </div>
            <div class="item flex">
              <a-input-number v-model:value="item.start" placeholder="正确率" :min="0" :max="100" style="width: 80px"></a-input-number>
              <span>&nbsp;_&nbsp;</span>
              <a-input-number v-model:value="item.end" placeholder="正确率" :min="0" :max="100" style="width: 80px"></a-input-number>
            </div>
            <div class="item relative" style="width: 160px">
              <a-input v-model:value="item.description" placeholder="请输入文案说明" style="width: 100%"></a-input>
              <div class="close" @click="closeBasicNorm(item)"><CloseCircleOutlined /></div>
            </div>
          </div>
          <div class="rowItem mini">
            <div class="item" style="width: auto">
              <div class="item_group btn layout-center" @click="createBasicNorm"><PlusOutlined style="margin-right: 6px" /> 新增</div>
            </div>
          </div>
        </div>
      </div>
    </a-spin>
    <template #footer>
      <div class="w-full layout-center">
        <div :class="{ createDrillBtn: true, 'btn-animate': !loading, loadingBtn: loading }" @click="props.gradingRuleModal = false"><a-spin v-if="loading" size="small" /> 取消编辑</div>
        <div :class="{ createDrillBtn: true, 'btn-animate': !loading, loadingBtn: loading }" @click="saveDeploy"><a-spin v-if="loading" size="small" /> 保存配置</div>
      </div>
    </template>
  </a-modal>
</template>

<script setup>
import { message, Modal } from 'ant-design-vue'
import { ref, defineEmits, watch, onMounted } from 'vue'
import { CloseCircleOutlined, PlusOutlined } from '@ant-design/icons-vue'
import { apiPostTrainGlobalRuleAddRule, apiPostTrainGlobalRuleDeleteById, apiPostTrainGlobalRuleType } from '../../../../../common/api/postWording'

//type 0 勤务用语 1军语密语 2拼音训练 3英语训练 4五笔训练
const props = defineProps(['gradingRuleModal', 'type'])
const emit = defineEmits(['update:gradingRuleModal'])

const gradingRuleModal = ref(false)
const loading = ref(false)
const basicDeployData = ref([])
onMounted(() => {
  gradingRuleModal.value = props.gradingRuleModal
  getGradeTypeList()
})

//关闭弹窗
const handleCancel = () => {
  emit('update:gradingRuleModal', false)
}
//新增码率
const createBasicNorm = () => {
  basicDeployData.value.push({ level: '', start: 0, end: 0, description: '' })
}
//删除
const closeBasicNorm = item => {
  apiPostTrainGlobalRuleDeleteById({ id: item.id, type: props.type }).then(res => {
    if (res.code == 200) {
      basicDeployData.value = basicDeployData.value.filter((j, i) => j.id !== item.id)
    }
  })
}
//基础练习数据处理
const handleBasicData = () => {
  let res = []
  for (let item of basicDeployData.value) {
    if (!item.level) {
      message.error('请输入评分类型')
      return false
    }
    if (item.start == null || item.end == null) {
      message.error('正确率区间不能为空')
      return false
    }
    if (item.start > item.end) {
      message.error('请输入正确率区间前面的一个值必须小于或等于后面的值')
      return false
    } else {
      res.push({
        type: props.type,
        id: item.id,
        level: item.level,
        accuracy: `${item.start}~${item.end}`,
        description: item.description
      })
    }
  }
  return res
}
//保存基础练习配置
const saveDeploy = () => {
  let data = handleBasicData()
  if (data) {
    apiPostTrainGlobalRuleAddRule(data).then(res => {
      if (res.code == 200) {
        message.success('保存成功！')
        gradingRuleModal.value = false
        emit('update:gradingRuleModal', false)
      } else {
        message.error('保存失败！')
      }
    })
  }
}
//获取评分列表
const getGradeTypeList = () => {
  apiPostTrainGlobalRuleType({ type: props.type }).then(res => {
    const list = res.data ?? []
    basicDeployData.value = list.map(item => {
      const obj = {
        ...item,
        start: Number(item.accuracy.split('~')[0]),
        end: Number(item.accuracy.split('~')[1])
      }
      return obj
    })
  })
}
</script>

<style lang="less">
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
