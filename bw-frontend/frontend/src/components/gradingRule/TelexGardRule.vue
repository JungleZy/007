<template>
  <div class="w-full layout-side" style="height: 680px; margin-top: 25px; color: #bbcdef">
    <div class="h-full border-r pr-1 borderColor" style=" width: 200px">
      <div class="item_group btn big mb-2" @click="addGradingRuleInfo" style="justify-content: center"><PlusOutlined /> 新增评分规则</div>
      <div class="w-full overflow-auto" style="height: calc(100% - 40px)">
        <div class="w-full layout-left-center" v-for="(r, index) in ruleList" :key="index">
          <div class="w-full h-full cursor-pointer p-1 ruleItem layout-side" :class="[pickRuleIndex === index ? 'rule-active' : 'rule-un-active']" @click="pickGradingRuleInfo(index)">
            <div>{{ r?.title }}</div>
            <a-popconfirm
                title="是否删除该评分规则?"
                ok-text="是"
                cancel-text="否"
                @confirm="deleteGard(r,index)"
            >
              <DeleteOutlined style="color: red;" title="删除" />
            </a-popconfirm>
          </div>
        </div>
      </div>
    </div>
    <div class="h-full grading-list overflow-auto relative" style="width: calc(100% - 200px)">
      <div class="w-full pl-1 pr-1 layout-left-center" v-if="pickRuleIndex > -1">
        <a-alert v-if="ruleList[pickRuleIndex].content.rateUnit !== 'CHARACTERS_PER_MINUTE'" class="w-full" type="warning" show-icon message="旧规则单位未确认或不匹配。请逐项核对速度及每单位加扣分，必要时手动修改数值，再确认单位并提交；系统不会自动乘除4。" />
        <a-row class="w-full">
          <a-checkbox :checked="ruleList[pickRuleIndex].content.rateUnit === 'CHARACTERS_PER_MINUTE'" @change="event => ruleList[pickRuleIndex].content.rateUnit = event.target.checked ? 'CHARACTERS_PER_MINUTE' : undefined">
            已核对速度及每单位加扣分，确认使用字符/分钟（提交规则后生效）
          </a-checkbox>
        </a-row>
        <a-row class="w-full">
          <a-col :span="3" class="layout-right-center pr-1">规则名称</a-col>
          <a-col :span="5" class="layout-left-center">
            <a-input v-model:value="ruleList[pickRuleIndex].title" />
          </a-col>
          <a-col :span="3" class="layout-right-center pr-1">基准分</a-col>
          <a-col :span="3" class="layout-left-center">
            <a-input-number v-model:value="ruleList[pickRuleIndex].score" :min="1" :max="1000" :step="1" :precision="0" />
          </a-col>
          <a-col :span="2" class="layout-left-center">&nbsp;&nbsp;分</a-col>
          <a-col :span="3" class="layout-right-center pr-1">默认规则</a-col>
          <a-col :span="5" class="layout-left-center">
            <a-switch checked-children="是" un-checked-children="否" v-model:checked="ruleList[pickRuleIndex].isDefault" />
          </a-col>
        </a-row>
        <a-row class="w-full">
          <a-col :span="3" class="layout-right-center pr-1">设定速度</a-col>
          <a-col :span="3" class="layout-left-center">
            <a-input-number :min="1" :step="1" :precision="0" v-model:value="ruleList[pickRuleIndex].content.wpm.base" />
          </a-col>
          <a-col :span="2" class="layout-left-center">&nbsp;&nbsp;字符/分钟</a-col>
          <a-col :span="3" class="layout-right-center pr-1">低于扣</a-col>
          <a-col :span="3" class="layout-left-center">
            <a-input-number :min="0" :max="100" :step="1" :precision="0" v-model:value="ruleList[pickRuleIndex].content.wpm.l" />
          </a-col>
          <a-col :span="2" class="layout-left-center">&nbsp;&nbsp;分</a-col>
          <a-col :span="3" class="layout-right-center pr-1">高于加</a-col>
          <a-col :span="3" class="layout-left-center">
            <a-input-number :min="0" :max="100" :step="1" :precision="0" v-model:value="ruleList[pickRuleIndex].content.wpm.r" />
          </a-col>
          <a-col :span="2" class="layout-left-center">&nbsp;&nbsp;分</a-col>
        </a-row>
        <a-row class="w-full" style="padding: 0">
          <a-col :span="12">
            <a-row class="w-full border-r border-l border-b border-t borderColor" >
              <a-col :span="24">
                <a-row class="w-full">
                  <a-col :span="6" class="layout-center">类型</a-col>
                  <a-col :span="18" class="layout-center">单个扣分</a-col>
                </a-row>
              </a-col>
            </a-row>
            <a-row class="w-full border-l border-b border-r borderColor" >
              <a-col :span="24">
                <a-row class="w-full">
                  <a-col :span="6" class="layout-center cursor-pointer-def">
                    <a-tooltip class="layout-left-center" color="orange">
                      <template #title>错码</template>
                      错码&nbsp;<QuestionCircleOutlined style="color: orange" />
                    </a-tooltip>
                  </a-col>
                  <a-col :span="18" class="layout-center">
                    <a-input-number :min="0" :max="1000" :step="1" :precision="0" :formatter="value => `${value}分`" :parser="value => value.replace('分', '')" v-model:value="ruleList[pickRuleIndex].content.other.errorCode" />
                  </a-col>
                </a-row>
                <a-row class="w-full">
                  <a-col :span="6" class="layout-center cursor-pointer-def">
                    <a-tooltip class="layout-left-center" color="orange">
                      <template #title>拍发时进行了修改报文的操作</template>
                      改错&nbsp;<QuestionCircleOutlined style="color: orange" />
                    </a-tooltip>
                  </a-col>
                  <a-col :span="18" class="layout-center">
                    <a-input-number :min="0" :max="1000" :step="1" :precision="0" :formatter="value => `${value}分`" :parser="value => value.replace('分', '')" v-model:value="ruleList[pickRuleIndex].content.other.correctMistakes" />
                  </a-col>
                </a-row>
                <a-row class="w-full">
                  <a-col :span="6" class="layout-center cursor-pointer-def">
                    <a-tooltip class="layout-left-center" color="orange">
                      <template #title>不规范改错</template>
                      不规&nbsp;<QuestionCircleOutlined style="color: orange" />
                    </a-tooltip>
                  </a-col>
                  <a-col :span="18" class="layout-center">
                    <a-input-number :min="0" :max="1000" :step="0.5"  :formatter="value => `${Number(value)}分`" :parser="value => value.replace('分', '')" v-model:value="ruleList[pickRuleIndex].content.other.nonStandart" />
                  </a-col>
                </a-row>
                <a-row class="w-full">
                  <a-col :span="6" class="layout-center cursor-pointer-def">
                    <a-tooltip class="layout-left-center" color="orange">
                      <template #title>拍发与报底比较，多拍、少拍组数</template>
                      多少组&nbsp;<QuestionCircleOutlined style="color: orange" />
                    </a-tooltip>
                  </a-col>
                  <a-col :span="18" class="layout-center">
                    <a-input-number :min="0" :max="1000" :step="1" :precision="0" :formatter="value => `${value}分`" :parser="value => value.replace('分', '')" v-model:value="ruleList[pickRuleIndex].content.other.muchLessGroups" />
                  </a-col>
                </a-row>

                <a-row class="w-full">
                  <a-col :span="6" class="layout-center cursor-pointer-def">
                    <a-tooltip class="layout-left-center" color="orange">
                      <template #title>少拍回行键</template>
                      少回行&nbsp;<QuestionCircleOutlined style="color: orange" />
                    </a-tooltip>
                  </a-col>
                  <a-col :span="18" class="layout-center">
                    <a-input-number :min="0" :max="1000" :step="1" :precision="0" :formatter="value => `${value}分`" :parser="value => value.replace('分', '')" v-model:value="ruleList[pickRuleIndex].content.other.lessReturnLine" />
                  </a-col>
                </a-row>
              </a-col>
            </a-row>
          </a-col>
          <a-col :span="12">
            <a-row class="w-full border-r border-l border-b border-t borderColor" >
              <a-col :span="24">
                <a-row class="w-full">
                  <a-col :span="6" class="layout-center">类型</a-col>
                  <a-col :span="18" class="layout-center">单个扣分</a-col>
                </a-row>
              </a-col>
            </a-row>
            <a-row class="w-full border-l border-b border-r borderColor" >
              <a-col :span="24">
                <a-row class="w-full">
                  <a-col :span="6" class="layout-center cursor-pointer-def">
                    <a-tooltip class="layout-left-center" color="orange">
                      <template #title>拍发与报底比较，多拍、少拍行数</template>
                      多少行&nbsp;<QuestionCircleOutlined style="color: orange" />
                    </a-tooltip>
                  </a-col>
                  <a-col :span="18" class="layout-center">
                    <a-input-number :min="0" :max="1000" :step="1" :precision="0" :formatter="value => `${value}分`" :parser="value => value.replace('分', '')" v-model:value="ruleList[pickRuleIndex].content.other.muchLessLine" />
                  </a-col>
                </a-row>
                <a-row class="w-full">
                  <a-col :span="6" class="layout-center cursor-pointer-def">
                    <a-tooltip class="layout-left-center" color="orange">
                      <template #title>标准一组报文为四码，与报文比较多拍、少拍码数</template>
                      多少码&nbsp;<QuestionCircleOutlined style="color: orange" />
                    </a-tooltip>
                  </a-col>
                  <a-col :span="18" class="layout-center">
                    <a-input-number :min="0" :max="1000" :step="1" :precision="0" :formatter="value => `${value}分`" :parser="value => value.replace('分', '')" v-model:value="ruleList[pickRuleIndex].content.other.muchLessCode" />
                  </a-col>
                </a-row>
                <a-row class="w-full">
                  <a-col :span="6" class="layout-center cursor-pointer-def">
                    <a-tooltip class="layout-left-center" color="orange">
                      <template #title>分页时没有输页标</template>
                      少页标&nbsp;<QuestionCircleOutlined style="color: orange" />
                    </a-tooltip>
                  </a-col>
                  <a-col :span="18" class="layout-center">
                    <a-input-number :min="0" :max="1000" :step="1" :precision="0" :formatter="value => `${value}分`" :parser="value => value.replace('分', '')" v-model:value="ruleList[pickRuleIndex].content.other.lessPage" />
                  </a-col>
                </a-row>
                <a-row class="w-full">
                  <a-col :span="6" class="layout-center cursor-pointer-def">
                    <a-tooltip class="layout-left-center" color="orange">
                      <template #title>标错页数</template>
                      标页错&nbsp;<QuestionCircleOutlined style="color: orange" />
                    </a-tooltip>
                  </a-col>
                  <a-col :span="18" class="layout-center">
                    <a-input-number :min="0" :max="1000" :step="1" :precision="0" :formatter="value => `${value}分`" :parser="value => value.replace('分', '')" v-model:value="ruleList[pickRuleIndex].content.other.errorPage" />
                  </a-col>
                </a-row>
                <a-row class="w-full" style="height: 44px"> </a-row>
              </a-col>
            </a-row>
          </a-col>
        </a-row>
        <div class="pr-2 pt-1 layout-right-center" style="position: absolute; bottom: 0px; right: calc(50% - 109px)">
          <a-button @click="handleCancel" class="layout-center" danger>
            <DeleteOutlined />
            取消编辑
          </a-button>
          <a-button @click="handleSubmit" class="layout-center">
            <CheckOutlined />
            提交规则
          </a-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
  export default {
    name: 'TelexGradingRule'
  }
</script>
<script setup>
  import { ref, onMounted } from 'vue'
  import * as gr from '../../common/api/GradingRuleApi.js'
  import { PlusOutlined, QuestionCircleOutlined, StopOutlined, CheckOutlined, DeleteOutlined } from '@ant-design/icons-vue'
  import { message, Modal } from 'ant-design-vue'
  import { deepClone } from '../../common/utils/Utils.js'

  const ruleList = ref([])
  const pickRuleIndex = ref(-1)
  const marks = ref({
    20: '低偏移',
    50: '中偏移',
    80: '高偏移'
  })

  onMounted(() => {
    getGradingRule()
  })
  const getGradingRule = () => {
    gr.getGradingRuleListByType({ type: 2 }).then(res => {
      if (res.code === 200) {
        res.data.forEach(d => {
          d.isDefault = d.isDefault === 0
          d.content = JSON.parse(d.content)
        })
        ruleList.value = res.data
        if (ruleList.value.length > 0) {
          pickRuleIndex.value = 0
        }
      } else {
        message.error(res.message)
      }
    })
  }
  const addGradingRuleInfo = () => {
    ruleList.value.push({
      type: 2,
      title: '评分规则' + (ruleList.value.length + 1),
      score: 100,
      status: 0,
      isDefault: false, //是否为默认规则
      content: {
        rateUnit: 'CHARACTERS_PER_MINUTE',
        wpm: {
          base: 50, //设定速度
          r: 0, //低于扣分
          l: 1 //高于加分
        },
        other: {
          errorCode: 35, //错码
          muchLessGroups: 10, //多少组
          correctMistakes: 2, //改错
          lessPage: 10, //少页标
          lessReturnLine: 10, //少回行
          muchLessLine: 10, //多少行
          muchLessCode: 20, //多少码
          errorPage: 10, //标页错
          nonStandart: 10 //不归
        }
      }
    })
    pickRuleIndex.value = ruleList.value.length - 1
  }
  const pickGradingRuleInfo = index => {
    pickRuleIndex.value = index
  }
  const js = type => {
    if (type === 0) {
      return '0~' + ((1200 / ruleList.value[pickRuleIndex.value].content.wpm.base) * (1 + ruleList.value[pickRuleIndex.value].content.skew / 100)).toFixed(0)
    }
    if (type === 1) {
      return (
          ((1200 / ruleList.value[pickRuleIndex.value].content.wpm.base) * (1 + ruleList.value[pickRuleIndex.value].content.skew / 100) + 1).toFixed(0) + '~' + ((1200 / ruleList.value[pickRuleIndex.value].content.wpm.base) * 3 * (1 + ruleList.value[pickRuleIndex.value].content.skew / 100)).toFixed(0)
      )
    }
    if (type === 2) {
      return (
          ((1200 / ruleList.value[pickRuleIndex.value].content.wpm.base) * 3 * (1 + ruleList.value[pickRuleIndex.value].content.skew / 100) + 1).toFixed(0) +
          '~' +
          ((1200 / ruleList.value[pickRuleIndex.value].content.wpm.base) * 5 * (1 + ruleList.value[pickRuleIndex.value].content.skew / 100)).toFixed(0)
      )
    }
  }
  const handleCancel = () => {
    if (ruleList.value[pickRuleIndex.value].id === undefined) {
      const pi = pickRuleIndex.value
      pickRuleIndex.value = -1
      ruleList.value.splice(pi, 1)
    } else {
      getGradingRule()
    }
  }
  const handleSubmit = () => {
    let data = deepClone(ruleList.value[pickRuleIndex.value])
    if (data.content.rateUnit !== 'CHARACTERS_PER_MINUTE') {
      message.warning('请先核对速度及每单位加扣分，并确认字符/分钟单位后重新提交规则')
      return
    }
    data.isDefault = data.isDefault ? 0 : 1
    data.content = JSON.stringify(data.content)
    gr.saveGradingRule(data).then(res => {
      if (res.code === 200) {
        message.success('评分规则更新成功')
        getGradingRule()
      } else {
        message.error(res.message)
      }
    })
  }
  const deleteGard = (r,index)=>{
    if(r.isDefault){
      message.error('默认规则不能删除！')
      return
    }
    if(r.id){
      gr.deleteGradingRule({id: r.id}).then(res=>{
        if (res.code === 200) {
          message.success('删除成功！')
          getGradingRule()
        }else {
          message.error(res.message)
        }
      })
    }else {
      message.success('删除成功！')
      getGradingRule()
    }
  }
</script>

<style lang="less">
  .HJ {
    .borderColor{border-color: #364e7b;}
    .rule-un-active {
      border-left: 3px solid transparent;
    }

    .ruleItem:hover,
    .rule-active {
      background: #24578c !important;
      color: #ffffff;
      border-left: 3px solid #70c9ff;
    }

    .grading-list .ant-row {
      padding: 5px 0;
    }

    .grading-list .ant-slider-mark-text-active {
      color: #91d5ff !important;
    }

    .grading-list .ant-slider-mark-text {
      color: #bbcdef !important;
    }
  }
  .HJJ{
    .borderColor{border-color: rgb(54, 78, 123);}
    .rule-un-active {
      border-left: 3px solid transparent;
    }

    .ruleItem:hover,
    .rule-active {
      background: #4c7595 !important;
      color: #ffffff;
      border-left: 3px solid #70c9ff;
    }

    .grading-list .ant-row {
      padding: 5px 0;
    }

    .grading-list .ant-slider-mark-text-active {
      color: #91d5ff !important;
    }

    .grading-list .ant-slider-mark-text {
      color: #bbcdef !important;
    }
  }
  .LJ{
    .borderColor{border-color:rgb(95, 101, 100);}
    .rule-un-active {
      border-left: 3px solid transparent;
    }

    .ruleItem{
      overflow: hidden;
      white-space: nowrap;
      text-overflow: ellipsis;
    }

    .ruleItem:hover,
    .rule-active {
      color: #ffffff;
      background-color: #533e1a;
      border-left: 3px solid #939797;
    }

    .grading-list .ant-row {
      padding: 5px 0;
    }

    .grading-list .ant-slider-mark-text-active {
      color: #91d5ff !important;
    }

    .grading-list .ant-slider-mark-text {
      color: #a9abaa !important;
    }
  }
  .KJ{
    .borderColor{border-color:rgba(80,141,230,0.6);}
    .rule-un-active {
      border-left: 3px solid transparent;
    }

    .ruleItem{
      overflow: hidden;
      white-space: nowrap;
      text-overflow: ellipsis;
    }

    .ruleItem:hover,
    .rule-active {
      color: #ffffff;
      background-color: #533e1a;
      border-left: 3px solid #939797;
    }

    .grading-list .ant-row {
      padding: 5px 0;
    }

    .grading-list .ant-slider-mark-text-active {
      color: #91d5ff !important;
    }

    .grading-list .ant-slider-mark-text {
      color: #a9abaa !important;
    }
  }
</style>
