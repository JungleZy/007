<template>
  <div class="h-full w-full overflow-auto">
    <div style="width: 800px; height: 600px" class="mt-4 ml-4">
      <ocr-comp :image="imgbase64" :type="false" @callback="callback"></ocr-comp>
    </div>
    <div style="width: 800px" class="ml-4" v-if="ocrResult.length > 0">
      <table class="w-full h-full">
        <tr v-for="(tr, index1) in ocrResult">
          <td v-for="(td, index2) in tr" contenteditable :style="{ color: td[1] < 0.9 ? 'red' : '#000' }">
            {{ td[0] }}
          </td>
        </tr>
      </table>
    </div>
    <!-- <button @click="openMedia">开启摄像头</button>
    <video id="video" width="1920" height="1080" autoplay="autoplay"></video>
    <canvas id="canvas" width="500" height="500"></canvas>
    <button @click="takePhoto">拍照</button>
    <img id="imgTag" src="" alt="imgTag" />
    <button @click="closeMedia">关闭摄像头</button> -->
  </div>
</template>

<script>
export default {
  name: ''
}
</script>
<script setup>
import { onMounted, ref } from 'vue'
import { imgbase64 } from './index.js'
import OcrComp from '../../../common/utils/ocr/OcrComp.vue'

let mediaStreamTrack = null // 视频对象(全局)
let video
const ocrResult = ref([])
onMounted(() => {
  // let img = new Image()
  // img.src = imgbase64
  // let myCanvas = document.getElementById('myCanvas').getContext('2d')
  // img.onload = () => {
  //   myCanvas.drawImage(img, 0, 0)
  // }
})
const openMedia = () => {
  let constraints = {
    video: { width: 500, height: 500 },
    audio: false
  }
  //获得video摄像头
  video = document.getElementById('video')
  let promise = navigator.mediaDevices.getUserMedia(constraints)
  promise.then(mediaStream => {
    // mediaStreamTrack = typeof mediaStream.stop === 'function' ? mediaStream : mediaStream.getTracks()[1];
    mediaStreamTrack = mediaStream.getVideoTracks()
    video.srcObject = mediaStream
    video.play()
  })
}
// 拍照
const takePhoto = () => {
  //获得Canvas对象
  let video = document.getElementById('video')
  let canvas = document.getElementById('canvas')
  let ctx = canvas.getContext('2d')
  ctx.drawImage(video, 0, 0, 500, 500)

  // toDataURL  ---  可传入'image/png'---默认, 'image/jpeg'
  let img = document.getElementById('canvas').toDataURL()
  // 这里的img就是得到的图片
  document.getElementById('imgTag').src = img
}
// 关闭摄像头
const closeMedia = () => {
  let stream = document.getElementById('video').srcObject
  let tracks = stream.getTracks()

  tracks.forEach(function (track) {
    track.stop()
  })

  document.getElementById('video').srcObject = null
}
const callback = (res, ocr) => {
  ocrResult.value = []
  ocrResult.value = ocr
}
</script>
<style scoped>
table {
  border-collapse: collapse;
  table-layout: fixed;
  width: 100%;
  background-color: #eee;
}

th {
  background-color: #eee;
  border: 1px solid red;
  color: red;
  text-align: center;
  padding: 4px;
}

tr:last-of-type th {
  width: 100px;
}

tr:last-of-type th:last-of-type {
  width: 30px;
}

td {
  border: 1px solid red;
  overflow: hidden;
  color: #000;
  padding: 2px;
  text-align: center;
}
</style>
