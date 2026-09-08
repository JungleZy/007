<template>
  <div class="w-full h-full classHours boxBorderShadow content-mask-bg" style="padding: 20px">
    <div class="w-full layout-center-top" style="height: 80px">
      <div  class="layout-center transScale fs_dispose item">
        <div class="h-full layout-center itemType"  v-for="(item, index) in typeTags" :key="index" :class="{ isActiveTag: item.isActive, isNoActiveTag: !item.isActive }" @click="selectTag(item)">
          <span style="margin-bottom: 16px"> {{ item.name }}</span>
        </div>
      </div>
    </div>
    <div class="w-full layout-right-center" style="height: 30px">
      <div style="width: 168px; margin-right: 20px" class="layout-left-center h-full">
        <a-month-picker v-model:value="theDate" placeholder="请选择查阅时间" style="height: 100%" :locale="locale" :disabled="timeTags[0].isActive == true" @change="changeDate"></a-month-picker>
      </div>
      <div style="width: 168px; position: relative; margin-right: 50px" class="layout-left-center h-full">
        <div
          class="h-full layout-center"
          style="width: 56px; position: absolute; top: 0; font-size: 10px; cursor: pointer"
          :class="{ isTimeActiveTag: item.isActive, isTimeNoActiveTag: !item.isActive }"
          @click="selectTimeTag(item)"
          :style="{ left: index === 0 ? 56 * index + 1 + 'px' : index === timeTags.length - 1 ? 56 * index - 1 + 'px' : 56 * index + 'px', 'z-index': item.isActive ? 1 : 0 }"
          v-for="(item, index) in timeTags"
          :key="index"
        >
          <span :class="{ isTimeActiveTagText: item.isActive, isTimeNoActiveTagText: !item.isActive }">
            {{ item.name }}
          </span>
        </div>
      </div>
    </div>
    <div class="w-full chartBac" style="height: 280px; margin-top: 10px">
      <div class="w-full" id="container" style="height: 260px; width: calc(100% - 30px)"></div>
    </div>
    <div class="w-full iconTable" style="height: calc(100% - 280px - 110px); overflow: auto">
      <a-table :columns="columns" :dataSource="tableData" :pagination="false">
        <template #expandIcon="props">
          <template v-if="props.record.children && props.record.children.length > 0">
            <CaretUpOutlined @click="props.onExpand" v-if="props.expanded" />
            <CaretDownOutlined @click="props.onExpand" v-else />
          </template>
        </template>
      </a-table>
    </div>
  </div>
</template>

<script>
export default {
  name: 'ClassHoursManagement'
}
</script>

<script setup>
import { onMounted, reactive, toRefs, ref, watch } from 'vue'
import { selectTimeTagsFunc } from './js/selectTimeTag.js'
import { selectTypeTagsFunc } from './js/selectTypeTag.js'
import useChart from './js/useChart.js'
import locale from 'ant-design-vue/es/date-picker/locale/zh_CN'
import moment from 'moment'
import 'moment/dist/locale/zh-cn'
import { CaretDownOutlined, CaretUpOutlined } from '@ant-design/icons-vue'

let theDate = ref('')
// 默认选择今天
theDate.value = moment(new Date())
let currentTimeTag = ref('月')
let currentTypeTag = ref('基础理论')
// 渲染表格要用到的数据
let columns = ref([
  {
    title: '知识点名称',
    dataIndex: 'knowledgeName',
    align: 'center',
    width: '40%'
  },
  {
    title: '学分',
    dataIndex: 'classHour',
    align: 'center',
    width: '30%'
  },
  {
    title: '用时(小时)',
    dataIndex: 'useHour',
    align: 'center',
    width: '30%'
  }
])
let tableData = ref([
  {
    key: 1,
    knowledgeName: '父级1',
    classHour: 100,
    useHour: 56,
    children: [
      {
        key: 2,
        knowledgeName: '默认课件名称1',
        classHour: 100,
        useHour: 8
      },
      {
        key: 2,
        knowledgeName: '默认课件名称2',
        classHour: 100,
        useHour: 12
      },
      {
        key: 2,
        knowledgeName: '默认课件名称3',
        classHour: 100,
        useHour: 26
      },
      {
        key: 2,
        knowledgeName: '默认课件名称4',
        classHour: 100,
        useHour: 10
      }
    ]
  },
  {
    key: 11,
    knowledgeName: '父级2',
    classHour: 100,
    useHour: 56,
    children: [
      {
        key: 22,
        knowledgeName: '默认课件名称1',
        classHour: 100,
        useHour: 8
      },
      {
        key: 33,
        knowledgeName: '默认课件名称2',
        classHour: 100,
        useHour: 12
      },
      {
        key: 44,
        knowledgeName: '默认课件名称3',
        classHour: 100,
        useHour: 26
      },
      {
        key: 55,
        knowledgeName: '默认课件名称4',
        classHour: 100,
        useHour: 10
      }
    ]
  }
])

// 处理图表 根据接口改变页面渲染需要用到的数据
let { getChartDataSource } = useChart(currentTimeTag, currentTypeTag, theDate, tableData)
let changeDate = () => {
  getChartDataSource()
}
// 处理时间 传currentTimeTag是为了改变它
let { timeTags, selectTimeTag } = selectTimeTagsFunc(currentTimeTag, getChartDataSource)
// 处理类型
let { typeTags, selectTag } = selectTypeTagsFunc(currentTypeTag, getChartDataSource)
onMounted(() => {
  getChartDataSource()
})
</script>

<style lang="less" scoped>
  // 年月日
  @blueColor: #6ebdff;
  // 年月日
  @grayColor: #7b90af;
  @backgroundcolor: rgba(24, 45, 86, 0.7);
  @whiteColor: #e2f2ff;

  // 年月日
  @blueColorHJJ: #e9deb2;
  // 年月日
  @grayColorHJJ: #6e7481;
  @backgroundcolorHJJ: rgba(23, 31, 41, 0.7);
  @whiteColorHJJ: #e2f2ff;

  // 年月日
  @blueColorLJ: #e9deb2;
  // 年月日
  @grayColorLJ: #a9abaa;
  @backgroundcolorLJ: rgba(38,41,36,0.3);
  @whiteColorLJ: #ffffff;
  :deep(.ant-input-disabled) {
    color: rgba(255, 255, 255, 0.5) !important;
    border: 1px solid rgba(255, 255, 255, 0.5) !important;
    cursor: not-allowed !important;
  }

  .HJ{
    .item{
      width: 1026px; height: 73px
    }
    .itemType{
      width: 332px; font-size: 23px; font-weight: bold; cursor: pointer
    }
    .classHours {
      // 学时管理表格更改
      .ant-table-thead > tr:first-child > th:first-child {
        padding-left: 30px;
      }

      .chartBac {
        background: url('../../../../../assets/HJ/basicTheory/studyManage/chart-bg.png')
        no-repeat 25px 150px;
        background-size: calc(100% - 40px) 62px;
      }

      .isActiveTag {
        background: url('../../../../../assets/HJ/basicTheory/studyManage/tab-bg-on.png')
        no-repeat 100% 100%;
      }

      .isNoActiveTag {
        background: url('../../../../../assets/HJ/basicTheory/studyManage/tab-bg.png')
        no-repeat 100% 100%;
      }

      .isTimeActiveTag {
        border: 1px solid @blueColor;
      }

      .isTimeNoActiveTag {
        border: 1px solid @grayColor;
      }

      .isTimeActiveTagText {
        color: @blueColor;
      }

      .isTimeNoActiveTagText {
        color: @grayColor;
      }

      .ant-calendar-picker-icon {
        color: @blueColor !important;
      }

      // 时间选择框
    }

    .boxBorderShadow {
      border: 1px solid rgba(0, 0, 0, 0.2);
      box-shadow: 0px 2px 6px 0px rgba(0, 0, 0, 0.4);
    }

    .innerBackgroundColor {
      background-color: @backgroundcolor;
    }

    .iconTable {
      .anticon svg {
        margin-bottom: 5px;
        margin-right: 10px;
      }

      .ant-table-tbody > tr > td {
        /*background-color: #204268;*/
        border: none !important;
      }

      .ant-table-row-level-1 {
        .ant-table-row-cell-break-word {
          background-color: #1f2e4b;
        }
      }
    }
  }
  .HJJ{
    .item{
      width: 600px; height: 44px
    }
    .itemType{
      width: 159px; font-size: 23px; font-weight: bold; cursor: pointer
    }
    .classHours {
      // 学时管理表格更改
      .ant-table-thead > tr:first-child > th:first-child {
        padding-left: 30px;
      }

      .chartBac {
        background: url('../../../../../assets/HJJ/basicTheory/studyManage/chart-bg.png') no-repeat 25px 150px;
        background-size: calc(100% - 40px) 62px;
      }

      .isActiveTag {
        background: url('../../../../../assets/HJJ/receive/trainBtn-bg-hover.png') no-repeat 100% 100%;
      }

      .isNoActiveTag {
        background: url('../../../../../assets/HJJ/receive/trainbtn-bg.png') no-repeat 100% 100%;
      }

      .isTimeActiveTag {
        border: 1px solid @blueColorHJJ;
      }

      .isTimeNoActiveTag {
        border: 1px solid @grayColorHJJ;
      }

      .isTimeActiveTagText {
        color: @blueColorHJJ;
      }

      .isTimeNoActiveTagText {
        color: @grayColorHJJ;
      }

      .ant-calendar-picker-icon {
        color: @blueColorHJJ !important;
      }

      // 时间选择框
    }

    .boxBorderShadow {
      border: 1px solid rgba(0, 0, 0, 0.2);
      box-shadow: 0px 2px 6px 0px rgba(0, 0, 0, 0.4);
    }

    .innerBackgroundColor {
      background-color: @backgroundcolorHJJ;
    }

    .iconTable {
      .anticon svg {
        margin-bottom: 5px;
        margin-right: 10px;
      }

      .ant-table-tbody > tr > td {
        /*background-color: #204268;*/
        border: none !important;
      }

      .ant-table-row-level-1 {
        .ant-table-row-cell-break-word {
          background-color: #1f2e4b;
        }
      }
    }
  }
  .LJ{
    .item{
      width: 600px; height: 44px
    }
    .itemType{
      width: 159px; font-size: 23px; font-weight: bold; cursor: pointer
    }
    .classHours {
      // 学时管理表格更改
      .ant-table-thead > tr:first-child > th:first-child {
        padding-left: 30px;
      }

      .chartBac {
        background: url("../../../../../assets/LJ/basicTheory/studyManage/chart-bg.png") no-repeat 25px 150px;
        background-size: calc(100% - 40px) 62px;
      }

      .isActiveTag {
        background: url("../../../../../assets/LJ/receive/trainBtn-bg-hover.png")  no-repeat center/100% 100%;
      }

      .isNoActiveTag {
        background: url("../../../../../assets/LJ/receive/trainbtn-bg.png") no-repeat center/100% 100%;
      }

      .isTimeActiveTag {
        border: 1px solid @blueColorLJ;
      }

      .isTimeNoActiveTag {
        border: 1px solid @grayColorLJ;
      }

      .isTimeActiveTagText {
        font-size:15px;
        color: @blueColorLJ;
      }

      .isTimeNoActiveTagText{
        font-size:15px;
        color: @grayColorLJ;
      }

      .ant-calendar-picker-icon {
        color: @blueColorLJ !important;
      }

      // 时间选择框
    }

    .boxBorderShadow {
      /*border: 1px solid rgba(0, 0, 0, 0.2);*/
      /*box-shadow: 0px 2px 6px 0px rgba(0, 0, 0, 0.4);*/
    }

    .innerBackgroundColor {
      background-color: @backgroundcolorLJ;
    }

    .iconTable {
      .anticon svg {
        margin-bottom: 5px;
        margin-right: 10px;
      }

      .ant-table-tbody > tr > td {
        /*background-color: #204268;*/
        border: none !important;
      }

      .ant-table-row-level-1 {
        .ant-table-row-cell-break-word {
          background-color: #1f2e4b;
        }
      }
    }
  }
  .KJ{
    .item{
      width: 600px; height: 44px
    }
    .itemType{
      width: 159px; font-size: 23px; font-weight: bold; cursor: pointer
    }
    .classHours {
      // 学时管理表格更改
      .ant-table-thead > tr:first-child > th:first-child {
        padding-left: 30px;
      }

      .chartBac {
        background: url("../../../../../assets/KJ/basicTheory/studyManage/chart-bg.png") no-repeat 25px 150px;
        background-size: calc(100% - 40px) 62px;
      }

      .isActiveTag {
        background: url("../../../../../assets/KJ/receive/trainBtn-bg-hover.png")  no-repeat center/100% 100%;
      }

      .isNoActiveTag {
        background: url("../../../../../assets/KJ/receive/trainbtn-bg.png") no-repeat center/100% 100%;
      }

      .isTimeActiveTag {
        border: 1px solid @blueColorLJ;
      }

      .isTimeNoActiveTag {
        border: 1px solid @grayColorLJ;
      }

      .isTimeActiveTagText {
        font-size:15px;
        color: @blueColorLJ;
      }

      .isTimeNoActiveTagText{
        font-size:15px;
        color: @grayColorLJ;
      }

      .ant-calendar-picker-icon {
        color: @blueColorLJ !important;
      }

      // 时间选择框
    }

    .boxBorderShadow {
      /*border: 1px solid rgba(0, 0, 0, 0.2);*/
      /*box-shadow: 0px 2px 6px 0px rgba(0, 0, 0, 0.4);*/
    }

    .innerBackgroundColor {
      background-color: @backgroundcolorLJ;
    }

    .iconTable {
      .anticon svg {
        margin-bottom: 5px;
        margin-right: 10px;
      }

      .ant-table-tbody > tr > td {
        /*background-color: #204268;*/
        border: none !important;
      }

      .ant-table-row-level-1 {
        .ant-table-row-cell-break-word {
          background-color: #1f2e4b;
        }
      }
    }
  }
  @media (max-width: 1340px) {
    .transScale {
      transform: scale(0.9);
    }
  }
  @media (max-width: 1100px) {
    .transScale {
      transform: scale(0.8);
    }
  }
</style>