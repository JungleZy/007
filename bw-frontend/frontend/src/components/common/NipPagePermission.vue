<template>
  <div class="maskBG layout-center-v" v-if="maskShow && routeName!=='Login'" @click="maskBG">
    <div class="w-full layout-center">
      <svg t="1742175849693" class="icon cursor-pointer-def" viewBox="0 0 1024 1024"
           xmlns="http://www.w3.org/2000/svg" width="64" height="64">
        <path d="M512 512m-512 0a512 512 0 1 0 1024 0 512 512 0 1 0-1024 0Z" fill="#3589FD"></path>
        <path
            d="M466.087564 2.029382A518.050909 518.050909 0 0 1 512 0c282.763636 0 512 229.227055 512 512 0 8.685382-0.195491 17.314909-0.633018 25.897891-15.127273 1.349818-30.440727 2.029382-45.912437 2.029382-282.763636 0-512-229.227055-512-512 0-8.685382 0.2048-17.314909 0.633019-25.897891z"
            fill="#FFFFFF" opacity=".1"></path>
        <path
            d="M176.872727 899.099927V497.943273c0-29.891491 28.011055-52.177455 57.902546-51.860946 41.3696 0.437527 86.965527-7.233164 132.654545-23.868509 49.030982-17.845527 91.322182-43.501382 123.680582-72.899491 12.483491-11.357091 32.200145-11.357091 44.683636 0 32.367709 29.398109 74.658909 55.053964 123.680582 72.890182 44.869818 16.346764 89.665164 24.036073 130.438982 23.887127C819.563055 445.998545 847.127273 468.191418 847.127273 497.850182v401.259054C757.322473 976.914618 640.158255 1024 512 1024s-245.322473-47.085382-335.127273-124.900073z m540.383418-237.325963L482.816 937.890909 347.257018 814.117236l50.213237-54.998109 78.550109 71.717237L660.48 613.571491l56.766836 48.202473z"
            fill="#E0EDFE"></path>
        <path
            d="M727.412364 652.250764l-244.475346 285.221236-144.532945-129.321891 49.654691-55.5008 87.840581 78.596655L670.868945 603.787636l56.543419 48.472437z"
            fill="#3589FD"></path>
      </svg>
    </div>
    <div class="w-full layout-center mt-2">{{ audioMessage }}（点击重试）</div>
  </div>
</template>

<script>
export default {
  name: "NipPagePermission"
}
</script>
<script setup>
import {onMounted, onUnmounted, ref, watch} from "vue";
import MorseVoice from "../../common/utils/MorseVoice.js";
import {audioOperation} from "../../common/utils/MorseVoice.js";
import {ipcRenderer} from "../../electron";
import {useRoute} from "vue-router";
import {PubSub} from "../../common/utils/PubSub.js";

import {message} from 'ant-design-vue'
const morseVoice = MorseVoice('new')
const ipc = ref(ipcRenderer.isEE)
const maskShow = ref(false)
const audioMessage = ref('点击启用音频')
const voiceList = ref([
  'receiveBasicTrain',
  'receiveTrain',
  'receiveKochTrain',
  'handKeyBasicTrain',
  'handKeyTrain',
  'examBasicExam',
  'examComplexTrain',
  'ReceivePostTrain',
  'handKeyPostJobTrain',
  'examPostJobTrain',
  'wordingTrain',
  'disturbCodeTrain',
  'broadcastTeachTrain',
  'lineNotifyTrain',
  'electronKeyZuXunTrain',
  'handkeyZuXunTrain',

  'examBasicExam',
  'examComplexTrain',
  'examPostJobTrain',
  'electronKeyZuXunTrain'
])
const modelList = ref([
  'handKeyBasicTrain',
  'handKeyTrain',
  'handKeyPostJobTrain',
  'lineNotifyTrain',
  'handkeyZuXunTrain',

])
const routeName = ref('')
const route = useRoute()
const pageName = () => location.hash.split('?')[0].split('/').pop()
const needsAudio = () => voiceList.value.includes(String(route.name)) || voiceList.value.includes(pageName())
const changeMode = () => {
  audioOperation({type: 'model', data: !(modelList.value.includes(String(route.name)) || modelList.value.includes(pageName()))})
  maskShow.value = needsAudio() && !morseVoice.isReady()
}
const audioSubscription = PubSub.subscribe('receiveProcessData', data => {
  if (data.type === 'failure' || data.type === 'suspended') {
    audioMessage.value = data.message || '音频不可用，请检查权限'
    if (data.type === 'failure') message.error({key: 'morse-audio', content: audioMessage.value})
    maskShow.value = data.type === 'failure' || needsAudio()
  } else if (data.status === 'initialized') {
    maskShow.value = false
    audioMessage.value = '点击恢复音频'
  }
})
const maskBG = async () => {
  audioMessage.value = '正在启用音频'
  const ready = await morseVoice.init()
  maskShow.value = needsAudio() && !ready
}
watch(() => route.fullPath, () => {
  routeName.value = route.name
  morseVoice.stop()
  changeMode()
})
onMounted(() => {
  routeName.value = route.name
  changeMode()
  if (ipc.value) maskBG()
})
onUnmounted(() => {
  PubSub.unsubscribe(audioSubscription)
  morseVoice.destroy()
})
</script>

<style scoped lang="less">
.maskBG {
  width: 100%;
  height: 100%;
  background: rgba(0, 0, 0, 0.6);
  position: absolute;
  z-index: 2;
  top: 0;
}
</style>