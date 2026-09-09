<template>
  <!--  ReceiveBasicTrain-->
  <div class="w-full h-full basicBody relative">
    <CutDown :nowTime="validTime"></CutDown>
    <div class="configureBox" v-if="showSetting != ''">
      <template v-if="showSetting == 'freq'">
        <div class="flex" style="justify-content: space-between">
          <div class="flex">
            <div v-for="(item, i) in freqGather.gather" :key="i" :class="{ typeBtn: true, active: item.type == freqGather.curr.type }" @click=";(freqGather.curr = item), (frequency = item.min)">
              {{ item.name }}
            </div>
          </div>
          <div class="auditionBtn" @click="auditionInfo">试听</div>
        </div>
        <div class="flex" style="justify-content: space-between; padding: 8px 0 16px">
          <span style="font-size: 12px">频率</span>
          <span>{{ frequency }}Hz</span>
        </div>
        <div class="volumeSlider voice">
          <a-slider v-model:value="frequency" :min="freqGather.curr.min" :max="freqGather.curr.max" :step="freqGather.curr.step"></a-slider>
        </div>
        <div class="flex text" style="justify-content: space-between">
          <div>{{ freqGather.curr.min }}</div>
          <div>{{ freqGather.curr.max }}</div>
        </div>
      </template>
      <template v-if="showSetting == 'volume'">
        <div class="flex" style="justify-content: space-between">
          <img v-if="audioVolume == 0" :src="volumeNone" alt="" />
          <img v-else-if="audioVolume < 50" :src="volumeMini" alt="" />
          <img v-else-if="audioVolume == 100" :src="volumeBig" alt="" />
          <img v-else :src="volumeSmall" alt="" />
          <span>{{ audioVolume }}%</span>
        </div>
        <div class="volumeSlider voice" style="margin-top: 16px">
          <a-slider v-model:value="audioVolume" :min="0" :max="100" :step="5" :tooltipVisible="false"></a-slider>
        </div>
        <div class="flex text" style="justify-content: space-between">
          <div>0</div>
          <div>100</div>
        </div>
      </template>
      <template v-if="showSetting == 'rate'">
        <div class="flex" style="justify-content: space-between">
          <span>码率</span>
          <span>{{ speedRate }}{{ wpmTOmm ? '码/分' : 'WPM' }}</span>
        </div>
        <div class="volumeSlider voice" style="margin-top: 16px">
          <a-slider v-model:value="speedRate" :min="20" :max="500" :step="5" :tooltipVisible="false"></a-slider>
        </div>
        <div class="flex text" style="justify-content: space-between">
          <div>20</div>
          <div>500</div>
        </div>
      </template>
      <template v-if="showSetting == 'disturb'">
        <div class="flex" style="justify-content: space-between">
          <span>播报干扰</span>
          <div class="auditionBtn" @click="onOffDisturb()" v-if="checkedDisturb.length > 0" style="background-color: #692609; color: #fff">停止干扰</div>
        </div>
        <div class="disturbBox">
          <a-checkbox-group v-model:value="checkedDisturb" style="display: flex; align-items: flex-end; flex-wrap: wrap">
            <a-checkbox v-for="item of disturbList" :key="item.type" :value="item.type" @change="changeDisturbInfo(item)" style="margin: 8px 0 0 0; width: 100px">{{ item.name }}</a-checkbox>
          </a-checkbox-group>
        </div>
        <div class="flex" style="justify-content: space-between; margin-top: 15px">
          <span>干扰强度</span>
          <span>{{ disturbVol }} db</span>
        </div>
        <div class="volumeSlider voice" style="margin-top: 16px">
          <a-slider v-model:value="disturbVol" :min="0" :max="100" :step="5" :tooltipVisible="false"></a-slider>
        </div>
        <div class="flex text" style="justify-content: space-between">
          <div>0</div>
          <div>100</div>
        </div>
      </template>
    </div>
    <div class="basicTitle">
      <div class="trainBtnBox">
        <div class="btns">
          <div :class="{ btnIco: true, active: showSetting == 'freq' }" @click="showSetting = showSetting == 'freq' ? '' : 'freq'">
            <div :class="{ ico1: true, active: showSetting == 'freq' }"></div>
          </div>
          <div :class="{ btnIco: true, active: showSetting == 'volume' }" @click="showSetting = showSetting == 'volume' ? '' : 'volume'">
            <div :class="{ ico2: true, active: showSetting == 'volume' }"></div>
          </div>
          <div :class="{ btnIco: true, active: showSetting == 'rate' }" @click="showSetting = showSetting == 'rate' ? '' : 'rate'">
            <div :class="{ ico3: true, active: showSetting == 'rate' }"></div>
          </div>
          <div :class="{ btnIco: true, active: showSetting == 'disturb' }" @click="showSetting = showSetting == 'disturb' ? '' : 'disturb'">
            <div :class="{ ico4: true, active: showSetting == 'disturb' }"></div>
          </div>
        </div>
      </div>
      <div class="text">隐藏电码：</div>
      <div class="switchBox" @click="switchLoop(3)">
        <div class="switch">
          <div class="slider hideCode"></div>
          <div :class="{ light: true, active: hideCode }"></div>
        </div>
      </div>
      <div class="text">循环播报：</div>
      <div class="switchBox" @click="switchLoop(1)">
        <div class="switch">
          <div class="slider loop"></div>
          <div :class="{ light: true, active: loop }"></div>
        </div>
      </div>
      <div :class="{ bbType: true, on: playType === '' }" v-if="!short" @click="queuePlayVoiceInfo('')">播报</div>
      <div :class="{ bbType: true, on: playType === 'hear' }" @click="queuePlayVoiceInfo('hear')">听报识别</div>
      <div class="trainBtn end" v-if="playType != null" @click="stopTrain()">停止练习</div>
    </div>
    <div class="deployBox layout-center">
      <div class="item">
        <div :class="{ deploy: true, on: keyArray.switchCode === 0 }" @click="changeCode(0)">长码</div>
        <div :class="{ deploy: true, on: keyArray.switchCode === 1 }" @click="changeCode(1)">短码</div>
        <div :class="{ deploy: true, on: keyArray.switchCode === 2 }" @click="changeCode(2)">字母</div>
      </div>
    </div>
    <div class="layout-center keyCodeBody">
      <template v-if="playType == 'hear'">
        <div class="myvideo layout-center" v-if="hearPlayVoice"><img :src="receivePlayGif" alt="" /></div>
        <div class="playEnd layout-center" v-else>请点击您听到的字码</div>
      </template>
      <div class="keyCode w-full h-full" :class="{ numberCode: !(keyArray.switchCode == 2) }">
        <div class="keyItem layout-center" :class="{ keyItem1: keyArray.switchCode == 2, keyItem2: keyArray.switchCode !== 2 }" v-for="(key, index) in keyArray.list" :key="index">
          <div
            :class="{ bg: true, active: index === currKeyIndex, sltOk: identify.index == index && identify.status == 'success', sltUn: identify.index == index && identify.status == 'error', noAfter: key.key == '4' || key.key == '9' || key.key == 'I' || key.key == 'R' || key.key == 'Z' }"
            @click="playVoiceInfo(key, index)"
          >
            <div class="w-full h-full bg-content">
              <div class="item">
                <img :src="fileUrl + 'gradient/' + key.key + '.png'" :class="{ key: true, success: identify.index == index && identify.status == 'success', error: identify.index == index && identify.status == 'error' }" />
                <div class="layout-center w-full" style="height: 4px">
                  <template v-if="!hideCode">
                    <div v-for="(val, j) in key.value.split('')" :key="j" :class="{ val: true, on: j <= keyArray.index && index === currKeyIndex, success: identify.index == index && identify.status == 'success', error: identify.index == index && identify.status == 'error' }" :data="val"></div>
                  </template>
                </div>
              </div>
              <div class="bbContent" v-if="index === currKeyIndex">
                <img :src="bbGif" class="bb" />
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'ReceiveBasicTrain'
}
</script>
<script setup>
import { ref, onMounted, onUnmounted, watch, inject } from 'vue'
import { useRoute } from 'vue-router'
import basicTrain from './js/basicTrain.js'
import Number from '../../../../../components/number/Number.vue'
import bb from '../../../../../assets/HJ/receive/bb.png'
import bbGif from '../../../../../assets/HJ/receive/bb.gif'
import volumeNone from '../../../../../assets/HJ/train/volume-none.png'
import volumeMini from '../../../../../assets/HJ/train/volume-mini.png'
import volumeBig from '../../../../../assets/HJ/train/volume-big.png'
import volumeSmall from '../../../../../assets/HJ/train/volume-small.png'
import receivePlayGif from '../../../../../assets/HJ/receive/receivePlay.gif'
import CutDown from '../../../../../components/cutDown/CutDown.vue'
const wpmTOmm = inject('wpmTOmm')
const showSetting = ref('')
const fileUrl = ref(window.fileUrl + '/006/code/')
const {
  downTimeRef,
  hearPlayVoice,
  validTime,
  frequency,
  freqGather,
  audioVolume,
  downTime,
  speedRate,
  loop,
  short,
  hideCode,
  currKeyIndex,
  keyArray,
  playType,
  identify,
  disturbList,
  checkedDisturb,
  disturbVol,
  auditionInfo,
  playVoiceInfo,
  switchLoop,
  queuePlayVoiceInfo,
  stopTrain,
  changeCode,
  onOffDisturb,
  changeDisturbInfo
} = basicTrain(wpmTOmm)
</script>

<style scoped lang="less">
 @import "./css/receiveBasicTrain";
 @import "./css/icon";
</style>