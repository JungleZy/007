<template>
  <div class="w-full h-full basicBody relative" style="min-width: 1234px">
    <div class="keyCodeBody">
      <div class="valBox">
        <CutDown :nowTime="validTimes" style="z-index: 99"></CutDown>
        <div class="termDeployBtn" v-if="userRole.id != '2'" @click="jumpDeploy">用语配置</div>
        <div class="deployBox">
          <div class="item">
            <div :class="{ deploy: true, on: trainData.type == 'single' }" @click="handleVoiceData('single')">单词</div>
            <div :class="{ deploy: true, on: trainData.type == 'frase' }" @click="handleVoiceData('frase')">语句</div>
          </div>

          <div class="item">
            <div :class="{ deploy: true, on: trainData.sort == 0 }" @click="changeSort(0)">顺序</div>
            <div :class="{ deploy: true, on: trainData.sort == 1 }" @click="changeSort(1)">倒序</div>
            <div :class="{ deploy: true, on: trainData.sort == 2 }" @click="changeSort(2)">随机</div>
          </div>
        </div>
        <template v-if="trainData.status > 0">
          <template v-if="trainData.playTerm[trainData.playIndex] && trainData.read"
            ><!-- && trainData.status > 0-->
            <div class="text">{{ trainData.playTerm[trainData.playIndex].key }}</div>
            <div class="text">{{ trainData.playTerm[trainData.playIndex].value }}</div>
          </template>
          <div class="imgBox" v-if="!trainData.read">
            <img :src="valHideGif" v-if="trainData.status == 1" class="img" />
            <img :src="valHide" v-else class="img" />
          </div>
        </template>
        <img :src="valEmpty" v-else class="img" style="width: auto; top: -6px" />
      </div>
      <div class="trainDeployBox overflow-auto">
        <div class="basicDeploy">
          <div class="w-full">
            <div class="layout-left-center">
              <div class="group layout-left-center" v-if="trainData.type == 'single'">
                <div class="lab">字头播报：</div>
                <div class="cont">
                  <a-select v-model:value="trainData.prefix" placeholder="字头" @change="changePrefix('prefix')" :disabled="trainData.status > 0" style="width: 80px">
                    <a-select-option v-for="(item, index) in prefixList" :value="item">{{ item == '' ? '全部' : item }}</a-select-option>
                  </a-select>
                </div>
              </div>
              <div class="group layout-left-center">
                <div class="lab">显示用语：</div>
                <div class="cont">
                  <div class="switchBox" @click="switchRead()">
                    <div class="switch">
                      <div class="slider showRead" style="left: 51px"></div>
                      <div :class="{ light: true, active: trainData.read }"></div>
                    </div>
                  </div>
                </div>
              </div>
              <div class="group layout-left-center">
                <div class="lab">用语间隔：</div>
                <div class="cont">
                  <a-select v-model:value="trainData.gap" style="width: 80px" placeholder="间隔(ms)">
                    <a-select-option v-for="(item, index) in gapList" :value="item.value">{{ item.text }}</a-select-option>
                  </a-select>
                </div>
              </div>
              <div class="group layout-left-center">
                <div class="lab">音调频率：</div>
                <div class="cont">
                  <a-input-number v-model:value="frequency" :min="500" :max="3000" :step="10" :precision="0" :formatter="value => `${value}Hz`" :parser="value => value.replace('Hz', '')" />
                </div>
              </div>
            </div>
            <div class="layout-left-center">
              <div class="group layout-left-center" style="margin-bottom: 40px">
                <div class="lab relative">
                  播放码率：
                  <div class="unit">{{ wpmTOmm ? '码/分' : 'WPM' }}</div>
                </div>
                <div class="cont">
                  <Slider :list="wpms" :val="rateWpm" :width="468" :min="40" :max="90" :step="5" @callback="checkWpm"></Slider>
                </div>
              </div>
              <div class="group layout-left-center" style="margin-bottom: 40px">
                <div class="lab">声音强度：</div>
                <div class="cont">
                  <Slider :list="noises" :val="volume" :width="256" :min="0" :max="1" :step="0.2" @callback="checkNoise"></Slider>
                </div>
              </div>
            </div>
          </div>
          <div class="deployBtns">
            <div class="btn oper max" @click="handleTrain(0)" v-if="trainData.status == 0">开始练习</div>
            <div class="btn oper max" @click="handleTrain(1)" v-if="trainData.status == 1">暂停练习</div>
            <div class="btn oper max" @click="handleTrain(2)" v-if="trainData.status == 2">继续练习</div>
            <div class="btn oper max" @click="handleTrain(3)" v-if="trainData.status > 0">结束练习</div>
<!--            <div class="btn oper end max" @click="handleTrain(4)">退出练习</div>-->
          </div>
        </div>
        <div class="disturbDeploy">
          <div :class="{ disturbItem: true, play: item.play }" v-for="(item, i) in disturbList" :key="i">
            <div class="text">{{ item.name }}</div>
            <div class="cont">
              <div class="l" style="width: 18px; height: 18px; flex-shrink: 0">
                <a-spin v-if="item.loading"></a-spin>
                <template v-else>
                  <img :src="pause" v-if="item.play" @click="changeAudioPlay(item, false)" />
                  <img :src="play" v-else @click="changeAudioPlay(item, true)" />
                </template>
              </div>
              <div class="volumeSlider voice">
                <a-slider v-model:value="item.volume" :min="0" :max="100" :step="5" :tooltipVisible="false" @change="changeVolume(item)"></a-slider>
              </div>
              <img v-if="item.volume == 0 " :src="volumeNone" class="r" @click="changeMute(item)" />
              <img v-else-if="item.volume < 50" :src="volumeMini" class="r" @click="changeMute(item)" />
              <img v-else-if="item.volume == 100" :src="volumeBig" class="r" @click="changeMute(item)" />
              <img v-else :src="volumeSmall" class="r" @click="changeMute(item)" />
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'Wording'
}
</script>
<script setup>
import { ref, onMounted, inject } from 'vue'
import { LoadingOutlined } from '@ant-design/icons-vue'
import { useRouter } from 'vue-router'
import termTrain from './js/termTrain.js'
import Slider from '../../../../../components/slider/Slider.vue'
import timeBg from '../../../../../assets/HJ/term/time-bg.png'
import deployGap from '../../../../../assets/HJ/term/deploy-gap.png'
import volumeNone from '../../../../../assets/HJ/train/volume-none.png'
import volumeMini from '../../../../../assets/HJ/train/volume-mini.png'
import volumeBig from '../../../../../assets/HJ/train/volume-big.png'
import volumeSmall from '../../../../../assets/HJ/train/volume-small.png'
import play from '../../../../../assets/HJ/term/play-ico.png'
import pause from '../../../../../assets/HJ/term/pause-ico.png'
import valEmpty from '../../../../../assets/HJ/term/val-empty.png'
import valHideGif from '../../../../../assets/HJ/term/val-hide.gif'
import valHide from '../../../../../assets/HJ/term/val-hide.png'
import CutDown from '../../../../../components/cutDown/CutDown.vue'
import {getPreTermTrainTotal} from "../../../../../common/api/TelegramApi";
const wpmTOmm = inject('wpmTOmm')
const router = useRouter()
const drillPath = ref('')
const userRole = ref(JSON.parse(localStorage.getItem('userRole')))
onMounted(()=>{
  getPreTermTrainTotal({type:0}).then(res=>{
    // timer= setInterval(()=>{
    //   totalTime.value++
    // },1000)
  })
})
router.getRoutes().forEach(r => {
  if (r.name === 'TermDeploy') {
    drillPath.value = r.path
  }
})

const { fileUrl, trainData, validTimes, wpms, noises, gapList, prefixList, frequency, rateWpm, volume, disturbList, handleTrain, handleVoiceData, switchRead, changeAudioPlay, changeVolume, changeMute, changeSort, changePrefix } = termTrain(wpmTOmm)

const checkWpm = val => {
  rateWpm.value = val
}
const checkNoise = val => {
  volume.value = val
}

const jumpDeploy = () => {
  router.push({ path: drillPath.value })
}
</script>

<style scoped lang="less">
@import "./css/index";
</style>