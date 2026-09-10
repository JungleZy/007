<template>
  <div
    class="w-full h-full studyScore boxBorderShadow innerBackgroundColor"
    style="position: relative"
  >
    <!--背景边框S-->
    <div class="w-full h-full layout-side" style="position: relative" v-if="interfaceStyle==='HJ'">
      <div class="h-full zuo" style="width: 256px; position: relative">
        <div
          class="zuoUp"
          style="
            width: 604px;
            height: 68px;
            top: 0;
            left: 50px;
            position: absolute;
          "
        >
          <span
            class="fs_dispose"
            style="
              position: absolute;
              color: #ffffff;
              font-size: 20px;
              font-weight: bold;
              font-style: oblique;
              left: 360px;
              top: 27px;
            "
          >
            参加次数：{{ cishu.all }}次
          </span>
        </div>
      </div>
      <div class="h-full you" style="width: 256px; position: relative">
        <div
          class="youUp"
          style="
            width: 604px;
            height: 68px;
            top: 0;
            right: 50px;
            position: absolute;
          "
        >
          <span
            class="fs_dispose"
            style="
              position: absolute;
              color: #ffffff;
              font-size: 20px;
              font-weight: bold;
              font-style: oblique;
              right: 360px;
              top: 27px;
            "
          >
            及格次数：{{ cishu.good }}次
          </span>
        </div>
      </div>
    </div >
    <div class="layout-left-center" style="position: absolute;top: 0px;right: 0px" v-else>
      <div class="tag">
        <div>参加次数</div><div class="num">{{cishu.all }}次</div>
      </div>
      <div class="tag">
        <div>合格次数</div><div class="num">{{cishu.good }}次</div>
      </div>
    </div>
    <!--背景边框E-->
    <div class="w-full h-full" style="position: absolute; left: 0; top: 0">
      <!--切换大标签S-->
      <div class="w-full layout-center-top itemTagBox">
        <div
          style=""
          class="layout-side-n transScale fs_dispose itamTag"
        >
          <div
            class="h-full layout-center tags"
            v-for="(item, index) in typeTags"
            :key="index"
            :class="{
              isActiveTag: item.isActive,
              isNoActiveTag: !item.isActive
            }"
            @click="selectTag(item)"
          >
            <span class="text" > {{ item.name }}</span>
          </div>
        </div>
      </div>
      <!--切换大标签E-->
      <!--时间选择S-->
      <div style="width: 100%; height: 30px" class="layout-center">
        <div class="timeBox" style="width: 1026px; height: 100%">
          <div class="w-full layout-right-center" style="height: 100%">
            <div
              style="width: 168px; margin-right: 20px"
              class="layout-left-center h-full"
              :style="[timeTags[0].isActive == true ? 'cursor: default' : '']"
            >
              <a-month-picker
                v-model:value="theDate"
                placeholder="请选择查阅时间"
                style="height: 100%"
                :locale="locale"
                :disabled="timeTags[0].isActive == true"
                @change="changeDate"
              ></a-month-picker>
            </div>
            <!--年月切换S-->
            <div
              style="width: 112px; position: relative"
              class="layout-left-center h-full"
            >
              <div
                class="h-full layout-center"
                style="width: 56px; position: absolute; top: 0; cursor: pointer"
                :class="{
                  isTimeActiveTag: item.isActive,
                  isTimeNoActiveTag: !item.isActive
                }"
                @click="selectTimeTag(item)"
                :style="{
                  left:
                    index === 0
                      ? 56 * index + 1 + 'px'
                      : index === timeTags.length - 1
                      ? 56 * index - 1 + 'px'
                      : 56 * index + 'px',
                  'z-index': item.isActive ? 1 : 0,
                  fontSize: 12 + fs * 2 + 'px',
                  height: 30 + fs * 3 + 'px'
                }"
                v-for="(item, index) in timeTags"
                :key="index"
              >
                <span
                  :class="{
                    isTimeActiveTagText: item.isActive,
                    isTimeNoActiveTagText: !item.isActive
                  }"
                >
                  {{ item.name }}
                </span>
              </div>
            </div>
            <!--年月切换E-->
          </div>
        </div>
      </div>
      <!--时间选择E-->
      <!--内容区S-->
      <div class="w-full layout-center" style="height: calc(100% - 190px)">
        <div
          style="
            width: calc(100% - 60px);
            height: calc(100% - 100px);
            position: relative;
            margin-bottom: 50px;
          "
        >
          <!--背景板子S-->
          <div
            class="w-full chartBac"
            style="height: 62px; position: absolute; left: 0; bottom: 48px"
            v-if="isShowMuBan"
          ></div>
          <!--背景板子E-->
          <!--图表S-->
          <div class="w-full h-full" id="containerScore"></div>
          <!--图表E-->
        </div>
      </div>
      <!--内容区E-->
    </div>
  </div>
</template>

<script>
export default {
  name: 'ScoreStatistics'
}
</script>
<script setup>
import { onMounted, ref } from 'vue'
import { selectTypeTagsFunc } from './js/selectTypeTag.js'
import { selectTimeTagsFunc } from './js/selectTimeTag.js'
import locale from 'ant-design-vue/es/date-picker/locale/zh_CN'
import moment from 'moment'
import 'moment/dist/locale/zh-cn'
import scoreUseChart from './js/scoreUseChart'

let isShowMuBan = ref(false)
let currentTypeTag = ref('成绩分布')
let currentTimeTag = ref('月')
let theDate = ref('')
const fs = ref(JSON.parse(localStorage.getItem('fs')));
const interfaceStyle = window.interfaceStyle
// 默认选择今天
theDate.value = moment(new Date())
// 处理图表渲染函数 isShowMuBan可以在函数改变值,在这个vue文件里同样可以收到它的改变
let { getChartDataSource, cishu } = scoreUseChart(currentTimeTag, currentTypeTag, theDate, isShowMuBan)
// 处理类型
let { typeTags, selectTag } = selectTypeTagsFunc(currentTypeTag, getChartDataSource)
// 处理时间 传currentTimeTag是为了改变它
let { timeTags, selectTimeTag } = selectTimeTagsFunc(currentTimeTag, getChartDataSource)
let changeDate = () => {
  getChartDataSource()
}

onMounted(() => {
  getChartDataSource()
})
</script>

<style lang="less" scoped>
  :deep(.ant-input-disabled) {
    color: rgba(255, 255, 255, 0.5) !important;
    border: 1px solid rgba(255, 255, 255, 0.5) !important;
    cursor: not-allowed !important;
  }
  .HJ{
    @blueColor: #6ebdff;
    @grayColor: #7b90af;
    @backgroundcolor: rgba(24, 45, 86, 0.7);
    @whiteColor: #e2f2ff;
    @defaultTextColor: #b4d5f0;
    .itemTagBox{height: 80px;margin-top: 80px}
    .itamTag{width: 1026px; height: 73px; font-size: 23px}
    .tags{width: 332px; font-weight: bold; cursor: pointer;.text{margin-bottom: 12px}}
    .studyScore {
      // 背景
      .zuo {
        background-image: url('../../../../../assets/HJ/basicTheory/studyManage/analyze/zuo.png');
        background-repeat: no-repeat;
        background-size: 288px 100%;
        // 左上角为起点
        background-position: left top;

        .zuoUp {
          background-image: url('../../../../../assets/HJ/basicTheory/studyManage/score/rightUp.png');
          background-repeat: no-repeat;
          background-size: 100% 100%;
          // 左上角为起点
          background-position: left top;
        }
      }

      .you {
        background-image: url('../../../../../assets/HJ/basicTheory/studyManage/analyze/you.png');
        background-repeat: no-repeat;
        // 宽度 长度
        background-size: 288px 100%;
        // 左上角为起点
        background-position: right top;

        .youUp {
          background-image: url('../../../../../assets/HJ/basicTheory/studyManage/score/leftUp.png');
          background-repeat: no-repeat;
          background-size: 100% 100%;
          // 左上角为起点
          background-position: right top;
        }
      }

      .isActiveTag {
        background: url('../../../../../assets/HJ/basicTheory/studyManage/tab-bg-on.png')
        no-repeat 100% 100%;
      }

      .isNoActiveTag {
        background: url('../../../../../assets/HJ/basicTheory/studyManage/tab-bg.png')
        no-repeat 100% 100%;
      }

      // 时间选择框
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

      // 柱状图背景
      .chartBac {
        background-image: url('../../../../../assets/HJ/basicTheory/studyManage/chart-bg.png');
        background-repeat: no-repeat;
        background-size: calc(100% - 0px) 100%;
        // 左上角为起点
        background-position: 0px top;
      }
    }
    .boxBorderShadow {
      border: 1px solid transparent;
      box-shadow: 0px 2px 6px 0px rgba(0, 0, 0, 0.4);
    }
    .innerBackgroundColor {
      background-color: @backgroundcolor;
    }
  }
  .HJJ{
    @blueColor: #e9deb2;
    @grayColor: #6e7481;
    @backgroundcolor: rgba(23, 31, 41, 0.7);
    @whiteColor: #e2f2ff;
    @defaultTextColor: #b4d5f0;
    .tag {
      background: url('../../../../../assets/HJJ/basicTheory/studyManage/tagBg.png');
      width: 98px;
      height: 76px;
      text-align: center;
      padding-top: 5px;
      color: #bfcde0;
      font-size: 13px;
      margin-right: 20px;
      .num {
        color: @blueColor;
        font-size: 20px;
        font-weight: bold;
        margin-top: 10px;
      }
    }
    .studyScore {
      // 背景
      .zuo {
        background-image: url('../../../../../assets/HJJ/basicTheory/studyManage/analyze/zuo.png');
        background-repeat: no-repeat;
        background-size: 288px 100%;
        // 左上角为起点
        background-position: left top;

        .zuoUp {
          background-image: url('../../../../../assets/HJJ/basicTheory/studyManage/score/rightUp.png');
          background-repeat: no-repeat;
          background-size: 100% 100%;
          // 左上角为起点
          background-position: left top;
        }
      }

      .you {
        background-image: url('../../../../../assets/HJJ/basicTheory/studyManage/analyze/you.png');
        background-repeat: no-repeat;
        // 宽度 长度
        background-size: 288px 100%;
        // 左上角为起点
        background-position: right top;

        .youUp {
          background-image: url('../../../../../assets/HJJ/basicTheory/studyManage/score/leftUp.png');
          background-repeat: no-repeat;
          background-size: 100% 100%;
          // 左上角为起点
          background-position: right top;
        }
      }

      .isActiveTag {
        background: url('../../../../../assets/HJJ/receive/trainBtn-bg-hover.png') no-repeat 100% 100%;
      }

      .isNoActiveTag {
        background: url('../../../../../assets/HJJ/receive/trainbtn-bg.png') no-repeat 100% 100%;
      }

      // 时间选择框
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

      // 柱状图背景
      .chartBac {
        background-image: url('../../../../../assets/HJJ/basicTheory/studyManage/chart-bg.png');
        background-repeat: no-repeat;
        background-size: calc(100% - 0px) 100%;
        // 左上角为起点
        background-position: 0px top;
      }
    }
    .boxBorderShadow {
      border: 1px solid transparent;
      box-shadow: 0px 2px 6px 0px rgba(0, 0, 0, 0.4);
    }
    .innerBackgroundColor {
      background-color: @backgroundcolor;
    }
    .itemTagBox{height: 80px;margin-top: 20px}
    .itamTag{width: 600px; height: 44px}
    .tags{width: 159px; font-size: 23px; font-weight: bold; cursor: pointer;.text{margin-bottom: 3px}}
  }
  .LJ{
    @blueColor: #e9deb2;
    @grayColor: #a9abaa;
    @backgroundcolor: rgba(38,41,36,0.3);
    @whiteColor: #ffffff;
    @defaultTextColor: #b4d5f0;
    .tag{
      background: url("../../../../../assets/LJ/basicTheory/studyManage/tagBg.png");
      width: 98px;
      height: 76px;
      text-align: center;
      padding-top: 5px;
      color: #ffffff;
      font-size: 13px;
      margin-right: 20px;
      .num{
        color: @blueColor;
        font-size: 20px;
        font-weight: bold;
        margin-top: 10px;
      }
    }
    .studyScore {
      // 背景
      .zuo {
        background-image: url("../../../../../assets/LJ/basicTheory/studyManage/analyze/zuo.png");
        background-repeat: no-repeat;
        background-size: 288px 100%;
        // 左上角为起点
        background-position: left top;

        .zuoUp {
          background-image: url("../../../../../assets/LJ/basicTheory/studyManage/score/rightUp.png");
          background-repeat: no-repeat;
          background-size: 100% 100%;
          // 左上角为起点
          background-position: left top;
        }
      }

      .you {
        background-image: url("../../../../../assets/LJ/basicTheory/studyManage/analyze/you.png");
        background-repeat: no-repeat;
        // 宽度 长度
        background-size: 288px 100%;
        // 左上角为起点
        background-position: right top;

        .youUp {
          background-image: url("../../../../../assets/LJ/basicTheory/studyManage/score/leftUp.png");
          background-repeat: no-repeat;
          background-size: 100% 100%;
          // 左上角为起点
          background-position: right top;
        }
      }

      .isActiveTag {
        background: url("../../../../../assets/LJ/receive/trainBtn-bg-hover.png") no-repeat center/100% 100%;
      }

      .isNoActiveTag {
        background: url("../../../../../assets/LJ/receive/trainbtn-bg.png")no-repeat center/100% 100%;
      }

      // 时间选择框
      .isTimeActiveTag {
        border: 1px solid @blueColor;
      }

      .isTimeNoActiveTag {
        border: 1px solid @grayColor;
      }

      .isTimeActiveTagText {
        font-size:15px;
        color: @blueColor;
      }

      .isTimeNoActiveTagText{
        font-size:15px;
        color: @grayColor;
      }

      .ant-calendar-picker-icon {
        color: @blueColor !important;
      }

      // 柱状图背景
      .chartBac {
        background-image: url("../../../../../assets/LJ/basicTheory/studyManage/chart-bg.png");
        background-repeat: no-repeat;
        background-size: calc(100% - 0px) 100%;
        // 左上角为起点
        background-position: 0px top;
      }
    }
    .boxBorderShadow {
      border: 1px solid transparent;
      /*box-shadow: 0px 2px 6px 0px rgba(0, 0, 0, 0.4);*/
    }
    .innerBackgroundColor {
      background-color: @backgroundcolor;
    }
    .itemTagBox{height: 80px;margin-top: 20px}
    .itamTag{width: 700px; height: 44px}
    .tags{width: 203px;height:54px;font-size: 20px;font-weight: bold;cursor: pointer;color: #ffffff;text-shadow: 1px 1px 1px #000;;.text{margin-bottom: 3px}}
  }
  .KJ{
    @blueColor: #e9deb2;
    @grayColor: #a9abaa;
    @backgroundcolor: rgba(38,41,36,0.3);
    @whiteColor: #ffffff;
    @defaultTextColor: #b4d5f0;
    .tag{
      background: url("../../../../../assets/KJ/basicTheory/studyManage/tagBg.png");
      width: 98px;
      height: 76px;
      text-align: center;
      padding-top: 5px;
      color: #ffffff;
      font-size: 13px;
      margin-right: 20px;
      .num{
        color: @blueColor;
        font-size: 20px;
        font-weight: bold;
        margin-top: 10px;
      }
    }
    .studyScore {
      // 背景
      .zuo {
        background-image: url("../../../../../assets/KJ/basicTheory/studyManage/analyze/zuo.png");
        background-repeat: no-repeat;
        background-size: 288px 100%;
        // 左上角为起点
        background-position: left top;

        .zuoUp {
          background-image: url("../../../../../assets/KJ/basicTheory/studyManage/score/rightUp.png");
          background-repeat: no-repeat;
          background-size: 100% 100%;
          // 左上角为起点
          background-position: left top;
        }
      }

      .you {
        background-image: url("../../../../../assets/KJ/basicTheory/studyManage/analyze/you.png");
        background-repeat: no-repeat;
        // 宽度 长度
        background-size: 288px 100%;
        // 左上角为起点
        background-position: right top;

        .youUp {
          background-image: url("../../../../../assets/KJ/basicTheory/studyManage/score/leftUp.png");
          background-repeat: no-repeat;
          background-size: 100% 100%;
          // 左上角为起点
          background-position: right top;
        }
      }

      .isActiveTag {
        background: url("../../../../../assets/KJ/receive/trainBtn-bg-hover.png") no-repeat center/100% 100%;
      }

      .isNoActiveTag {
        background: url("../../../../../assets/KJ/receive/trainbtn-bg.png")no-repeat center/100% 100%;
      }

      // 时间选择框
      .isTimeActiveTag {
        border: 1px solid @blueColor;
      }

      .isTimeNoActiveTag {
        border: 1px solid @grayColor;
      }

      .isTimeActiveTagText {
        font-size:15px;
        color: @blueColor;
      }

      .isTimeNoActiveTagText{
        font-size:15px;
        color: @grayColor;
      }

      .ant-calendar-picker-icon {
        color: @blueColor !important;
      }

      // 柱状图背景
      .chartBac {
        background-image: url("../../../../../assets/KJ/basicTheory/studyManage/chart-bg.png");
        background-repeat: no-repeat;
        background-size: calc(100% - 0px) 100%;
        // 左上角为起点
        background-position: 0px top;
      }
    }
    .boxBorderShadow {
      border: 1px solid transparent;
      /*box-shadow: 0px 2px 6px 0px rgba(0, 0, 0, 0.4);*/
    }

    .itemTagBox{height: 80px;margin-top: 20px}
    .itamTag{width: 700px; height: 44px}
    .tags{width: 203px;height:54px;font-size: 20px;font-weight: bold;cursor: pointer;color: #ffffff;text-shadow: 1px 1px 1px #000;;.text{margin-bottom: 3px}}
  }



  @media (max-width: 1340px) {
    .transScale {
      transform: scale(0.9);
    }
    .timeBox {
      width: 924px !important;
    }
    .zuoUp,
    .youUp {
      transform: scale(0.9);
      top: -3px !important;
    }
    .zuoUp {
      left: 20px !important;
    }
    .youUp {
      right: 20px !important;
    }
  }

  @media (max-width: 1220px) {
    .zuoUp,
    .youUp {
      transform: scale(0.8);
      top: -7px !important;
    }
    .zuoUp {
      left: -10px !important;
    }
    .youUp {
      right: -10px !important;
    }
  }

  @media (max-width: 1100px) {
    .transScale {
      transform: scale(0.8);
    }
    .zuoUp,
    .youUp {
      transform: scale(0.72);
      top: -9px !important;
    }
    .zuoUp {
      left: -30px !important;
    }
    .youUp {
      right: -30px !important;
    }
  }
</style>
