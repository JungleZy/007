<template>
  <div class="w-full h-full basicBody relative">
    <CutDown :nowTime="validTime"></CutDown>
    <div class="basicTitle">
      <div class="stageBox layout-center">
        选择阶段：
        <a-select style="width: 150px" v-model:value="stage">
          <a-select-option v-for="(item, i) in phrase" :key="i" :value="i + 1">
            <a-tooltip placement="right">
              <template #title
                ><span style="word-break: keep-all">随机从{{ item.join(',') }}中选取字码播报</span></template
              >
              <div>第{{ i + 1 }}阶段</div>
            </a-tooltip>
          </a-select-option>
        </a-select>
        <a-tooltip placement="bottomLeft">
          <template #title
            ><span style="word-break: keep-all"> 随机从{{ phrase[stage - 1] }}中选取字码播报</span></template
          >
          <QuestionCircleFilled style="margin-left: 6px; color: #e8a829" />
        </a-tooltip>
      </div>
      <div class="item_group btn layout-center mr-2" @click="stageVisible = true" style="height: 34px"><SettingOutlined style="margin-right: 6px" />配置阶段</div>
      <div class="trainBtn" @click="startTrain()" v-if="!voicePlaying">开始练习</div>
      <div class="trainBtn end" @click="stopTrain()" v-else>停止练习</div>
    </div>
    <div class="keyCodeBody">
      <div class="deployBox">
        <div class="layout-left-center" style="margin-top: 10px">
          <div class="group layout-left-center">
            <div class="lab">显示报文：</div>
            <div class="cont">
              <div class="switchBox" @click="switchDelay()">
                <div class="switch">
                  <div class="slider showDelay" style="left: 51px"></div>
                  <div :class="{ light: true, active: showDelay }"></div>
                </div>
              </div>
            </div>
          </div>
          <div class="group layout-left-center" v-if="showDelay">
            <div class="lab">显示延迟：</div>
            <div class="cont">
              <a-select v-model:value="displayDelay" :options="displayDelayOptions" :disabled="voicePlaying" style="width: 90px" />
            </div>
          </div>
          <div class="group layout-left-center">
            <div class="lab">开始延迟：</div>
            <div class="cont">
              <a-input-number v-model:value="startDelay" :min="0" :max="20" :step="1" :precision="0" :disabled="voicePlaying" :formatter="value => `${value}秒`" :parser="value => value.replace('秒', '')" />
            </div>
          </div>
          <div class="group layout-left-center">
            <div class="lab">音调频率：</div>
            <div class="cont">
              <a-input-number v-model:value="fre" :min="500" :max="2000" :step="10" :precision="0" :formatter="value => `${value}Hz`" :parser="value => value.replace('Hz', '')" />
            </div>
          </div>
          <div class="group layout-left-center" v-if="showDelay">
            <div class="lab">单组长度：</div>
            <div class="cont">
              <a-select v-model:value="groupLength" :options="groupLenOptions" style="width: 110px" />
            </div>
          </div>
        </div>
        <div class="layout-left-center">
          <div class="group layout-left-center" style="margin-bottom: 30px">
            <div class="lab relative">
              播放码率：
              <div class="unit" style="color: #c0dbf8">(20-500{{ wpmTOmm ? '码/分' : 'WPM' }})</div>
            </div>
            <div class="cont">
              <Slider v-if="wpmTOmm" :list="wpms2" :val="checkedWpm" type="max" :width="592" :min="20" :max="500" :step="10" @callback="checkWpm"></Slider>
              <Slider v-if="!wpmTOmm" :list="wpms" :val="checkedWpm" :width="592" :min="15" :max="80" :step="5" @callback="checkWpm"></Slider>
            </div>
          </div>
          <div class="group layout-left-center" style="margin-bottom: 30px">
            <div class="lab">信号强度：</div>
            <div class="cont">
              <Slider :list="signalStrength" :val="checkedSignalStrength" :width="256" :min="0.165" :max="0.99" :step="0.165" @callback="checkSignal"></Slider>
            </div>
          </div>
          <div class="group layout-left-center" style="margin-bottom: 30px">
            <div class="lab">噪音强度：</div>
            <div class="cont">
              <Slider :list="noises" :val="checkedNoise" :width="256" :min="0" :max="1" :step="0.2" @callback="checkNoise"></Slider>
            </div>
          </div>
        </div>
      </div>
      <div class="codeBodyBox overflow-auto" ref="codeBodyRef">
        <template v-if="voicePlaying || elapsed.length > 0">
          <div class="hideCodeBody layout-center h-full" v-if="!showDelay">
            <img :src="kochHide" style="width: 80%; max-width: 845px" />
          </div>
          <div class="codeBody" v-else>
            <div class="layout-left-center" v-if="voicePlaying && waitTime > 0" style="height: 57px; font-size: 16px">
              <strong style="font-size: 28px; margin: 0 6px; color: #fc9e44">{{ waitTime }}</strong>
              S后显示
            </div>
            <template v-if="displayDelay > -1 && elapsed.length > 0">
              <template v-for="(code, i) in elapsed" :key="i">
                <div
                  style="width: calc(100% / 49); position: relative"
                  :style="[i % 50 == 49 && i !== 0 ? 'display:none' : '']"
                  :class="{
                    code: true,
                    last: code != ' ' && i == elapsed.length - 1
                  }"
                >
                  <div v-if="i%500==0" class="mark layout-center">
                    <div style="transform: skew(-30deg);position: relative;margin-top: -13px" >第 <span style="font-size: 24px;font-weight: bold">{{Math.floor(i/500)+1}}</span> 页</div>
                  </div>
                  <!--                  <div v-if="i%500==0" style="color: white;position: absolute;width: max-content;top: -18px;left: 27px">第 <span style="font-size: 20px;font-weight: bold">{{Math.floor(i/500)+1}}</span> 页</div>-->
                  <img :src="fileUrl + 'gradient/' + code + '.png'" :data-t="i" class="img" v-if="code != ' '" />
                </div>
              </template>
            </template>
          </div>
        </template>
        <div class="hideCodeBody layout-center h-full" v-else>
          <img :src="kochEmpty" style="width: 80%; max-width: 845px" />
        </div>
      </div>
    </div>

    <!--配置阶段-->
    <a-modal :destroyOnClose="true" :width="750" class="init_modal_style footer-border-none" destroyOnClose="true" v-model:visible="stageVisible">
      <template #title>
        <strong>连贯阶段配置</strong>
      </template>
      <template #footer>
        <div class="w-full layout-center">
          <div class="createDrillBtn" @click="saveStageInfo()"><a-spin v-if="stageLoading" size="small" />保存配置</div>
        </div>
      </template>
      <a-spin :spinning="stageLoading">
        <div class="stageSettingBox">
          <div class="row">
            <div class="lab">连贯阶段：</div>
            <div class="w-full layout-side">
              <a-radio-group v-model:value="currStage" name="stageRadio" style="flex-wrap: wrap">
                <template v-for="(stage, s) in setStage" :key="s">
                  <a-radio :value="s" style="margin: 2px 15px 4px 0; width: 94px">
                    <span style="color: #e2f2ff">第{{ s + 1 }}阶段</span>
                  </a-radio>
                </template>
              </a-radio-group>
              <div class="stageBtn">
                <a-tooltip placement="top" title="添加阶段">
                  <div class="btn" @click="changeStageInfo(1)">
                    <PlusOutlined />
                  </div>
                </a-tooltip>
                <a-tooltip placement="top" title="删除阶段">
                  <div class="btn" @click="changeStageInfo(-1)">
                    <MinusOutlined />
                  </div>
                </a-tooltip>
              </div>
            </div>
          </div>
          <div class="row">
            <div class="lab">字码集：</div>
            <div class="w-full layout-side" style="width: 580px">
              <template v-for="(tag, t) in stayArray" :key="t">
                <div
                  :class="{
                    tag: true,
                    on: setStage[currStage].indexOf(tag) > -1
                  }"
                  @click="selectStageInfo(tag)"
                >
                  {{ tag }}
                </div>
              </template>
            </div>
          </div>
          <div class="row">
            <div class="lab">阶段配置：</div>
            <div class="w-full" style="width: 600px; display: flex; flex-wrap: wrap">
              <template v-for="(tag, t) in setStage[currStage]" :key="t">
                <div :class="{ 'tag on drag': true, curr: dragIndex == t }" @dragenter="enterDragSort($event, t)" @dragstart="startDragSort(t)" @dragend="endDragSort($event, t)" :draggable="true">
                  {{ tag }}
                </div>
              </template>
            </div>
          </div>
        </div>
      </a-spin>
    </a-modal>
  </div>
</template>

<script>
export default {
  name: 'receiveKochTrain'
}
</script>
<script setup>
import { QuestionCircleFilled, SettingOutlined, PlusOutlined, MinusOutlined, ExclamationCircleOutlined } from '@ant-design/icons-vue'
import Number from '../../../../../components/number/Number.vue'
import Slider from '../../../../../components/slider/Slider.vue'
import kochTrain from './js/kochTrain.js'
import prevIco from '../../../../../assets/HJ/receive/ico-prev.png'
import nextIco from '../../../../../assets/HJ/receive/ico-next.png'
import kochEmpty from '../../../../../assets/HJ/receive/kochEmpty.png'
import kochHide from '../../../../../assets/HJ/receive/kochHide.png'
import CutDown from '../../../../../components/cutDown/CutDown.vue'
import { createVNode, inject, ref } from 'vue'
import { Modal } from 'ant-design-vue'
const wpmTOmm = inject('wpmTOmm')
const stayArray = ref(['A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9'])
const dragIndex = ref(null)
const {
  codeBodyRef,
  fileUrl,
  validTime,
  stage,
  wpms,
  wpms2,
  signalStrength,
  checkedWpm,
  checkedSignalStrength,
  elapsed,
  startDelay,
  showDelay,
  displayDelay,
  displayDelayOptions,
  fre,
  noises,
  checkedNoise,
  voicePlaying,
  groupLength,
  groupLenOptions,
  phrase,
  setStage,
  currStage,
  stageVisible,
  stageLoading,
  waitTime,
  switchDelay,
  startTrain,
  stopTrain,
  nextTrain,
  lastTrain,
  saveStageInfo
} = kochTrain(wpmTOmm)

const checkWpm = val => {
  checkedWpm.value = val
}
const checkSignal = val => {
  checkedSignalStrength.value = val
}
const checkNoise = val => {
  checkedNoise.value = val
}

const changeStageInfo = val => {
  if (val > 0) {
    setStage.value.push([])
  } else {
    let last = setStage.value[setStage.value.length - 1]
    if (last.length > 0) {
      Modal.confirm({
        content: '最后一个阶段已配置字码，您确定需要删除该阶段？',
        icon: () => createVNode(ExclamationCircleOutlined),
        okText: () => '确定',
        cancelText: () => '取消',
        maskClosable: true,
        onOk: () => {
          setStage.value = setStage.value.filter((item, i) => i < setStage.value.length - 1)
          if (currStage.value == setStage.value.length) {
            currStage.value--
          }
        }
      })
    } else {
      setStage.value = setStage.value.filter((item, i) => i < setStage.value.length - 1)
      if (currStage.value == setStage.value.length) {
        currStage.value--
      }
    }
  }
}
const selectStageInfo = tag => {
  if (setStage.value[currStage.value].indexOf(tag) == -1) {
    setStage.value[currStage.value].push(tag)
  } else {
    setStage.value[currStage.value] = setStage.value[currStage.value].filter(item => item != tag)
  }
}
const startDragSort = i => {
  dragIndex.value = i
}
const endDragSort = (e, i) => {
  e.preventDefault()
  dragIndex.value = null
}
const enterDragSort = (e, i) => {
  e.preventDefault()
  if (dragIndex.value != i) {
    let source = setStage.value[currStage.value][dragIndex.value]
    setStage.value[currStage.value].splice(dragIndex.value, 1)
    setStage.value[currStage.value].splice(i, 0, source)
    dragIndex.value = i
  }
}
</script>

<style scoped lang="less">
  :deep{
    .ant-slider-track{
      background-color: transparent!important;
    }
    .ant-slider-handle{
      border:0px solid #33b34ba1
    }
    .ant-slider-handle:focus {
      border-color: transparent;
      border-radius: 0px 0px 45% 45%;
      box-shadow: 0 0 0 5px rgba(26, 196, 50, 0);
    }
  }
 @import "./css/receiveKochTrain";
 @import "./css/icon";

</style>

