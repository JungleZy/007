<template>
  <div class="h-full w-full pt-5">
    <a-slider v-model:value="fre" :tooltip-visible="true" :min="100" :max="10000" :step="100" @change="changeFre" />
    <a-button @click="startAudio(true)" v-if="status === 0">开始</a-button>
    <a-button @click="pauseAudio" v-if="status === 1">暂停</a-button>
    <a-button @click="continueAudio" v-if="status === 2">继续</a-button>
    <a-button @click="reloadAudio" v-if="status > 0">重启</a-button>
  </div>
</template>

<script>
export default {
  name: 'VicoDemo'
}
</script>
<script setup>
import { onUnmounted, ref, onMounted } from 'vue'
import MorseVoice from '../../../common/utils/MorseVoice.js'
import PCMPlayer from '../../../common/utils/pcm.js'

const fre = ref(750)
let voice = new MorseVoice({
  criterion: 15, // 点长度
  ratio: {
    dot: 1, // 比例 点长度
    dash: 3, // 比例 划长度
    gap: 1, // 比例 点划间隔
    word: 3, // 比例 词间隔
    suite: 5 // 比例 组间隔
  },
  fre: fre.value
})
const song = ref([1, 0, 1, 2, 1, 0, 1, 2, 1, 0, 1, 3, 1, 1, 2, 1, 1, 2, 1, 0, 1, 2, 1, 0, 1, 2, 1, 1, 2, 1, 0, 1, 0])
const num = ref(0)
const status = ref(0)
const dq = ref(0)
const startAudio = flag => {
  let s = song.value
  if (flag) {
    num.value = 0
    status.value = 1
  } else {
    if (dq.value > 0) {
      s = song.value.slice(dq.value, song.value.length)
    }
  }

  voice.play(s, res => {
    if (res === s.length - 1) {
      status.value = 0
    }
  })
}
const pauseAudio = () => {
  status.value = 2
  voice.clear(res => {
    dq.value = res
  })
}
const continueAudio = () => {
  status.value = 1
  startAudio(false)
}
const reloadAudio = () => {
  voice.clear(res => {
    startAudio(true)
  })
}
const changeFre = value => {
  voice.changeFre(value)
}
onUnmounted(() => {
  voice.clear()
})
let ws
onMounted(() => {
  ws = new WebSocket(`ws://10.10.0.232:8081/webSocket`)
  ws.onmessage = e => {
    let player = new PCMPlayer({
      encoding: '8bitInt',
      channels: 1,
      sampleRate: 44100,
      flushingTime: 1000
    })
    let data = new Uint8Array(e.data)
    player.feed(data)
  }
})
</script>
