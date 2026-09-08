<template>
  <div class="h-full w-full overflow-hidden">
    <div class="h-full w-full relative cornerBox" v-show="step === -1">
      <div class="w-full layout-center animate-div-in absolute corner" ref="videoBoxRef" style="height: calc(100% - 56px)">
        <video id="video" autoplay="autoplay"></video>
        <div class="w-full h-full layout-center absolute" v-if="!linkCamera">
          <img :src="noLinkCamera" style="max-width: 80%; max-height: 80%" />
        </div>
        <template v-else>
          <canvas id="canvas" :width="video_w" :height="video_h" style="opacity: 0"></canvas>
        </template>
      </div>
      <div class="w-full layout-center absolute" style="bottom: 0; pointer-events: none">
        <div class="operBtn" @click="photoGraphInfo"><img :src="photoIco" class="ico" />拍照</div>
      </div>
    </div>
    <div class="h-full w-full relative cornerBox" v-show="step === 0">
      <div class="w-full layout-center animate-div-in absolute corner" style="height: calc(100% - 56px); background-color: #091425">
        <img id="image" :src="videoImage" class="w-full" style="display: none" />
      </div>
      <div class="w-full layout-center absolute" style="bottom: 0; pointer-events: none">
        <div class="operBtn" @click="getData"><img :src="cropIco" class="ico" />裁剪</div>
        <div class="relative" style="margin-left: 16px; pointer-events: auto">
          <div class="operBtn" @click="openPicRotate"><img :src="rotateIco" class="ico" />旋转</div>
          <div class="rotateBox" v-if="isOpenPicRotate">
            <div class="resetBtn" @click="resetPicRotate">复位</div>
            <div class="formSlider w-full">
              <a-slider v-model:value="slider" :step="1" :min="-360" :max="360" @change="picRotate"></a-slider>
            </div>
          </div>
        </div>
        <div class="operBtn reset" @click="afreshPhoto" style="margin-left: 16px"><img :src="resetPhotoIco" class="ico" />重新拍照</div>
      </div>
    </div>
    <div :class="{ 'h-full w-full relative': true, cornerBox: !ocrLoading }" v-show="step === 1">
      <div :class="{ 'w-full layout-center animate-div-in absolute': true, corner: !ocrLoading }" style="height: calc(100% - 56px)">
        <div class="w-full h-full layout-center relative" style="background-color: #091425">
          <div class="w-full h-full layout-center overflow-hidden p-2">
            <div class="relative">
              <div id="sm" class="cutImgBox" v-if="ocrLoading">
                <div class="absolute lt"></div>
                <div class="absolute rt"></div>
                <div class="absolute rb"></div>
                <div class="absolute lb"></div>
                <div class="line">
                  <div id="smLiner" class="green" style="width: 1px; height: 100%; background: #04d1f0; box-shadow: 0 0 10px rgba(4, 209, 240, 0.8)"></div>
                </div>
              </div>
              <img :src="cutResult" style="max-height: 100%; max-width: 100%; width: auto; height: auto; object-fit: contain" />
            </div>
          </div>
        </div>
      </div>
      <div class="w-full layout-center absolute" style="bottom: 0; pointer-events: none">
        <template v-if="!ocrLoading">
          <div class="operBtn" @click="handleOcr"><img :src="scanIco" class="ico" />扫描</div>
          <div class="operBtn reset" @click="afreshData"><img :src="resetCropIco" class="ico" />重新裁剪</div>
          <div class="operBtn reset" @click="afreshPhoto"><img :src="resetPhotoIco" class="ico" />重新拍照</div>
        </template>
      </div>
    </div>
    <div class="h-full w-full relative cornerBox" v-show="step == 2 || step == 3" ref="telegraphTableRef">
      <div class="w-full layout-center animate-div-in absolute overflow-auto corner" v-if="props.type" style="padding: 2px; height: calc(100% - 56px)">
        <div class="keyTableHead">
          <div class="key">1</div>
          <div class="key">2</div>
          <div class="key">3</div>
          <div class="key">4</div>
          <div class="key">5</div>
          <div class="key">6</div>
          <div class="key">7</div>
          <div class="key">8</div>
          <div class="key">9</div>
          <div class="key">10</div>
        </div>
        <div class="keyTable">
          <div class="keyRow" v-for="(tr, i) in ocrResult" :key="i">
            <a-input v-for="(td, j) in tr" :key="j" v-model:value="td[0]" class="keyCol"
                     :readonly="step == 3 && props.editTelegraph.length == 0"
                     @keydown="keyDownStart" @focus="getFocus((i*10)+j)"></a-input>
            <div class="ser">{{ i + 1 }}</div>
          </div>
        </div>
      </div>
      <div class="w-full layout-center animate-div-in absolute overflow-auto corner" v-else style="height: calc(100% - 56px); padding: 1px; background-color: #091425">
        <div class="w-full h-full layout-center overflow-hidden p-2">
          <img :src="cutResult" style="max-height: 100%; max-width: 100%; width: auto; height: auto; object-fit: contain" />
        </div>
      </div>
      <div class="w-full layout-center absolute" style="bottom: 0; pointer-events: none">
        <div class="operBtn max" @click="saveOcrScore" v-if="step == 2 || props.editTelegraph.length > 0"><img :src="saveIco" class="ico" />确认提交</div>
        <template v-if="isAuto">
          <div class="operBtn reset" @click="handleOcr" v-if="step == 2"><img :src="resetScanIco" class="ico" />重新扫描</div>
          <div class="operBtn reset" @click="afreshData" v-if="step == 2"><img :src="resetCropIco" class="ico" />重新裁剪</div>
          <div class="operBtn reset" @click="afreshPhoto" v-if="step == 2"><img :src="resetPhotoIco" class="ico" />重新拍照</div>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, defineProps, onBeforeUnmount, ref, onUpdated, watch, nextTick } from 'vue'
import { CheckOutlined, CloseOutlined, TranslationOutlined, ReloadOutlined, UploadOutlined, SyncOutlined } from '@ant-design/icons-vue'
import 'cropperjs/dist/cropper.css'
import Cropper from 'cropperjs'
import OCR from './OCR.js'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import photoIco from '../../../assets/HJ/postTrain/btn-ico-photo.png'
import cropIco from '../../../assets/HJ/postTrain/btn-ico-crop.png'
import rotateIco from '../../../assets/HJ/postTrain/btn-ico-rotate.png'
import scanIco from '../../../assets/HJ/postTrain/btn-ico-scan.png'
import saveIcoHJ from '../../../assets/HJ/postTrain/btn-ico-save.png'
import saveIcoHJJ from '../../../assets/HJJ/postTrain/btn-ico-save.png'
import saveIcoLJ from '../../../assets/LJ/postTrain/btn-ico-save.png'

import resetPhotoIco from '../../../assets/HJ/postTrain/btn-ico-reset-photo.png'
import resetCropIco from '../../../assets/HJ/postTrain/btn-ico-reset-crop.png'
import resetScanIco from '../../../assets/HJ/postTrain/btn-ico-reset-scan.png'
import noLinkCamera from '../../../assets/HJ/postTrain/no-link-camera.png'
import thumbManual from '../../../assets/HJ/postTrain/thume-manual.png'
import addNextHJ from "../../../assets/HJ/postTrain/addNext.png";
import addNextHJJ from "../../../assets/HJJ/postTrain/addNext.png";
import addNextLJ from "../../../assets/LJ/postTrain/addNext.png";


let cropper,
  mediaStreamTrack = null,
  video
const props = defineProps({
  type: {
    type: Boolean,
    default: true
  },
  editTelegraph: {
    type: Array,
    default: []
  }
})
const emits = defineEmits(['save'])
const step = ref(-1)
const cutResult = ref(null)
const videoBoxRef = ref(null)
const videoImage = ref('')
const telegraphTableRef = ref(null)
const tdHeight = ref(null)
const ocrResult = ref([])
const ocrLoading = ref(false)
const rotate = ref(0)
const slider = ref(0)
const isOpenPicRotate = ref(false)
const resetPhoto = ref(false)
const video_w = ref(500)
const video_h = ref(500)
const turnOn = ref(false)
const linkCamera = ref(false)
const route = useRoute()
const isAuto = ref(true)

let saveIco
const interfaceStyle = window.interfaceStyle
if(interfaceStyle==='HJ'){
  saveIco = saveIcoHJ
}else if(interfaceStyle==='HJJ'){
  saveIco = saveIcoHJJ
}else {
  saveIco = saveIcoLJ
}
const focusIndex = ref(0)
const getFocus = (index)=>{
  focusIndex.value = index
}
const keyDownStart = e=>{
  if(e.code === 'Space'||e.key==='Enter') {
    if(e.preventDefault){
      e.preventDefault()
    }else {
      window.event.returnValue == false
    }
  }
  if(e.key==='Enter'&&focusIndex.value%10===9&&focusIndex.value!==99){
    focusIndex.value++
    const dom = document.querySelectorAll('.keyCol')
    nextTick(()=>{
      dom[focusIndex.value].focus()
    })
  }
  if(e.code === 'Space'&&focusIndex.value%10!==9){
    focusIndex.value++
    const dom = document.querySelectorAll('.keyCol')
    nextTick(()=>{
      dom[focusIndex.value].focus()
    })
  }
}
watch(props, () => {
  if (props.editTelegraph.length > 0) {
    step.value = 3
    ocrResult.value = props.editTelegraph
  }
})

onMounted(() => {
  if (route.query.type === '1') {
    video_w.value = videoBoxRef.value.clientWidth
    video_h.value = videoBoxRef.value.clientHeight
    initPhotoMedia()
  } else {
    step.value = 2
    isAuto.value = false
    cutResult.value = window.fileUrl+'/006/img/thume-manual.png'
    for (let i = 0; i < 10; i++) {
      ocrResult.value.push([[''], [''], [''], [''], [''], [''], [''], [''], [''], ['']])
    }
  }
})

onBeforeUnmount(() => {
  if (turnOn.value && linkCamera.value) {
    closeMedia()
  }
})

onUpdated(() => {
  const sm = document.getElementById('sm')
  tdHeight.value = parseInt((telegraphTableRef.value.clientHeight - 60) / ocrResult.value.length)
  if (sm !== null) {
    anime({
      targets: '.green',
      loop: true,
      direction: 'alternate',
      translateX: sm.clientWidth,
      duration: 3000,
      easing: 'linear'
    })
  } else {
    removeAnime()
  }
})

/** 初始化开启摄像头 */
const initPhotoMedia = () => {
  video = document.getElementById('video')
  if (!navigator.mediaDevices) {
    linkCamera.value = false
    turnOn.value = false
    return false
  }
  let promise = navigator.mediaDevices.getUserMedia({
    video: { width: video_w.value, height: video_h.value },
    audio: false
  })

  promise
    .then(mediaStream => {
      turnOn.value = true
      linkCamera.value = true
      mediaStreamTrack = mediaStream.getVideoTracks()
      video.srcObject = mediaStream
      video.play()
    })
    .catch(res => {
      linkCamera.value = false
    })
}

/** 拍照 */
const photoGraphInfo = () => {
  if (!linkCamera.value) {
    message.error('摄像头未连接！')
    return false
  }
  video = document.getElementById('video')
  let canvas = document.getElementById('canvas')
  let ctx = canvas.getContext('2d')
  ctx.drawImage(video, 0, 0, video_w.value, video_h.value)

  videoImage.value = document.getElementById('canvas').toDataURL()
  closeMedia()
  nextTick(() => {
    step.value = 0
    createCropper()
  })
}

/** 关闭摄像头 */
const closeMedia = () => {
  let stream = document.getElementById('video').srcObject
  let tracks = stream.getTracks()

  turnOn.value = false
  tracks.forEach(function (track) {
    track.stop()
  })
  document.getElementById('video').srcObject = null
}

/** 创建剪裁图片实例 */
const createCropper = () => {
  if (resetPhoto.value && cropper) {
    cropper.replace(videoImage.value)
  } else {
    let image = document.getElementById('image')
    cropper = new Cropper(image, {
      preview: '.img-preview',
      dragMode: 'move'
    })
  }
}

/** 裁剪 */
const getData = () => {
  let cc = cropper.getCroppedCanvas()
  cutResult.value = cc.toDataURL('image/jpeg')
  ocrLoading.value = false
  step.value = 1
  cropper.destroy()
  cropper = null
}

/** 旋转 */
const openPicRotate = () => {
  isOpenPicRotate.value = !isOpenPicRotate.value
}

/** 调整旋转角度 */
const picRotate = e => {
  cropper.rotate(e - rotate.value)
  rotate.value = e
}

/** 复位 */
const resetPicRotate = () => {
  slider.value = 0
  cropper.rotate(0 - rotate.value)
  rotate.value = 0
}

/** 重新拍照 */
const afreshPhoto = () => {
  ocrResult.value = []
  cutResult.value = null
  resetPhoto.value = true
  removeAnime()
  step.value = -1
  initPhotoMedia()
}

/** 重新裁剪 */
const afreshData = () => {
  cutResult.value = null
  removeAnime()
  step.value = 0
  createCropper()
}

/** 重新扫描 */
const afreshOcr = () => {
  ocrResult.value = []
  cutResult.value = null
  removeAnime()
  step.value = 0
  createCropper()
}

/** 扫描 */
const handleOcr = () => {
  step.value = 1
  console.time()
  ocrLoading.value = true
  new OCR().dispose(cutResult.value, res => {
    ocrResult.value = res.map(row => {
      if (row.length < 10) {
        for (let i = 0; i < 10 - row.length; i++) {
          row.push([''])
        }
      } else {
        row = row.filter((ro, r) => r < 10)
      }
      return row
    })
    if (res.length < 10) {
      for (let i = 0; i < 10 - res.length; i++) {
        ocrResult.value.push([[''], [''], [''], [''], [''], [''], [''], [''], [''], ['']])
      }
    }
    console.timeEnd()
    removeAnime()
    step.value = 2
  })
}

/** 清除动画效果 */
const removeAnime = () => {
  anime.remove('.green')
  anime.set('.green', {
    translateX: () => {
      return 0
    }
  })
  ocrLoading.value = false
}

/** 确认提交 */
const saveOcrScore = () => {
  step.value = 3
  emits('save', cutResult.value, ocrResult.value)
}
</script>
<style scoped lang="less">
.HJ{
  .keyTable {
    height: calc(100% - 36px);
    display: flex;
    flex-direction: column;
    /*border: 1px solid #354971;*/
  }
  .keyRow {
    display: flex;
    flex: 1;
  }
  .keyRow .ser {
    width: 20px;
    margin-left: 10px;
    display: flex;
    align-items: center;
    flex-shrink: 0;
    color: #8eafca;
  }
  .keyCol {
    text-align: center;
    font-size: 18px;
  }
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
    text-align: center;
  }

  .animate-div-in {
    animation: fadeIn 0.3s;
  }
  .animate-div-out {
    animation: fadeOut 1s;
  }
  @keyframes fadeIn {
    0% {
      top: -300px;
      opacity: 0;
    }
    100% {
      top: 0;
      opacity: 1;
    }
  }
  @keyframes fadeOut {
    0% {
      left: 0;
    }
    100% {
      left: -300px;
    }
  }
  .corner {
    position: relative;
  }
  .cornerBox:after,
  .cornerBox:before,
  .corner:after,
  .corner:before {
    content: '';
    width: 10px;
    height: 10px;
    position: absolute;
    z-index: 9;
  }
  .cornerBox:before {
    background: url('../../../assets/HJ/postTrain/ico-lt.png') no-repeat center;
    left: 0;
    top: 0;
  }
  .cornerBox:after {
    background: url('../../../assets/HJ/postTrain/ico-rt.png') no-repeat center;
    right: 30px;
    top: 0;
  }
  .corner:after {
    background: url('../../../assets/HJ/postTrain/ico-lb.png') no-repeat center;
    left: 0;
    bottom: 0;
  }
  .corner:before {
    background: url('../../../assets/HJ/postTrain/ico-rb.png') no-repeat center;
    right: 30px;
    bottom: 0;
  }
  .cutImgBox {
    width: 100%;
    height: 100%;
    background-color: rgba(0, 0, 0, 0.35);
    position: absolute;
  }
  .cutImgBox .line {
    width: 100%;
    height: 100%;
    position: absolute;
    top: 0;
    left: 0;
  }
  .cutImgBox .absolute {
    width: 16px;
    height: 16px;
    border: 3px solid #04d1f0;
  }
  .cutImgBox .lt {
    left: -3px;
    top: -3px;
    border-right: none;
    border-bottom: none;
  }
  .cutImgBox .rt {
    right: -3px;
    top: -3px;
    border-left: none;
    border-bottom: none;
  }
  .cutImgBox .lb {
    left: -3px;
    bottom: -3px;
    border-right: none;
    border-top: none;
  }
  .cutImgBox .rb {
    right: -3px;
    bottom: -3px;
    border-left: none;
    border-top: none;
  }
  .operBtn {
    width: 110px;
    height: 34px;
    background: url('../../../assets/HJ/postTrain/operBtnBg.png') no-repeat center;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 15px;
    color: #fff;
    cursor: pointer;
    pointer-events: auto;
  }
  .operBtn + .operBtn {
    margin-left: 16px;
  }

  .operBtn.max {
    /*background: url("../../../assets/HJ/postTrain/operBtnMaxBg.png") no-repeat center;*/
  }
  .operBtn.max:hover {
    /*background: url("../../../assets/HJ/postTrain/operBtnMaxBg-on.png") no-repeat center;*/
  }
  .operBtn.reset {
    /*background: url("../../../assets/HJ/postTrain/operBtnResetBg.png") no-repeat center;*/
  }
  .operBtn.reset:hover {
    /*background: url("../../../assets/HJ/postTrain/operBtnResetBg-on.png") no-repeat center;*/
  }
  .operBtn .ico {
    margin-right: 6px;
  }
  .rotateBox {
    width: 240px;
    background-color: rgba(32, 66, 107, 0.5);
    border: 1px solid #20426b;
    padding-left: 10px;
    position: absolute;
    left: 0;
    bottom: 40px;
    display: flex;
    align-items: center;
  }
  .rotateBox .resetBtn {
    background-color: #009afd;
    line-height: 20px;
    flex-shrink: 0;
    font-size: 12px;
    padding: 0 10px;
    border-radius: 2px;
    cursor: pointer;
  }
  .keyTableHead {
    width: 100%;
    height: 36px;
    line-height: 36px;
    display: flex;
    align-items: stretch;
    padding-right: 30px;
    z-index: 10;
    position: relative;
  }
  .keyTableHead .key {
    background-color: #70c9ff;
    width: 10%;
    color: #3f6089;
    font-weight: bold;
    font-size: 18px;
    text-align: center;
  }
}
  .HJJ{
    .keyTable {
      height: calc(100% - 36px);
      display: flex;
      flex-direction: column;
      /*border: 1px solid #354971;*/
    }
    .keyRow {
      display: flex;
      flex: 1;
    }
    .keyRow .ser {
      width: 20px;
      margin-left: 10px;
      display: flex;
      align-items: center;
      flex-shrink: 0;
      color: #8eafca;
    }
    .keyCol {
      text-align: center;
      font-size: 18px;
    }
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
      text-align: center;
    }

    .animate-div-in {
      animation: fadeIn 0.3s;
    }
    .animate-div-out {
      animation: fadeOut 1s;
    }
    @keyframes fadeIn {
      0% {
        top: -300px;
        opacity: 0;
      }
      100% {
        top: 0;
        opacity: 1;
      }
    }
    @keyframes fadeOut {
      0% {
        left: 0;
      }
      100% {
        left: -300px;
      }
    }
    .corner {
      position: relative;
    }
    .cornerBox:after,
    .cornerBox:before,
    .corner:after,
    .corner:before {
      content: '';
      width: 10px;
      height: 10px;
      position: absolute;
      z-index: 9;
    }
    .cornerBox:before {
      background: url('../../../assets/HJJ/postTrain/ico-lt.png') no-repeat center;
      left: 0;
      top: 0;
    }
    .cornerBox:after {
      background: url('../../../assets/HJJ/postTrain/ico-rt.png') no-repeat center;
      right: 30px;
      top: 0;
    }
    .corner:after {
      background: url('../../../assets/HJJ/postTrain/ico-lb.png') no-repeat center;
      left: 0;
      bottom: 0;
    }
    .corner:before {
      background: url('../../../assets/HJJ/postTrain/ico-rb.png') no-repeat center;
      right: 30px;
      bottom: 0;
    }
    .cutImgBox {
      width: 100%;
      height: 100%;
      background-color: rgba(0, 0, 0, 0.35);
      position: absolute;
    }
    .cutImgBox .line {
      width: 100%;
      height: 100%;
      position: absolute;
      top: 0;
      left: 0;
    }
    .cutImgBox .absolute {
      width: 16px;
      height: 16px;
      border: 3px solid #04d1f0;
    }
    .cutImgBox .lt {
      left: -3px;
      top: -3px;
      border-right: none;
      border-bottom: none;
    }
    .cutImgBox .rt {
      right: -3px;
      top: -3px;
      border-left: none;
      border-bottom: none;
    }
    .cutImgBox .lb {
      left: -3px;
      bottom: -3px;
      border-right: none;
      border-top: none;
    }
    .cutImgBox .rb {
      right: -3px;
      bottom: -3px;
      border-left: none;
      border-top: none;
    }
    .operBtn {
      width: 110px;
      height: 34px;
      background: url('../../../assets/HJJ/postTrain/operBtnBg.png') no-repeat center;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 15px;
      color: #fff;
      cursor: pointer;
      pointer-events: auto;
    }
    .operBtn + .operBtn {
      margin-left: 16px;
    }

    .operBtn.max {
      /*background: url("../../../assets/HJJ/postTrain/operBtnMaxBg.png") no-repeat center;*/
    }
    .operBtn.max:hover {
      /*background: url("../../../assets/HJJ/postTrain/operBtnMaxBg-on.png") no-repeat center;*/
    }
    .operBtn.reset {
      /*background: url("../../../assets/HJJ/postTrain/operBtnResetBg.png") no-repeat center;*/
    }
    .operBtn.reset:hover {
      /*background: url("../../../assets/HJJ/postTrain/operBtnResetBg-on.png") no-repeat center;*/
    }
    .operBtn .ico {
      margin-right: 6px;
    }
    .rotateBox {
      width: 240px;
      background-color: rgba(32, 66, 107, 0.5);
      border: 1px solid #20426b;
      padding-left: 10px;
      position: absolute;
      left: 0;
      bottom: 40px;
      display: flex;
      align-items: center;
    }
    .rotateBox .resetBtn {
      background-color: #009afd;
      line-height: 20px;
      flex-shrink: 0;
      font-size: 12px;
      padding: 0 10px;
      border-radius: 2px;
      cursor: pointer;
    }
    .keyTableHead {
      width: 100%;
      height: 36px;
      line-height: 36px;
      display: flex;
      align-items: stretch;
      padding-right: 30px;
      z-index: 10;
      position: relative;
    }
    .keyTableHead .key {
      background-color: #4c7595;
      width: 10%;
      color: #161e29;
      font-weight: bold;
      font-size: 18px;
      text-align: center;
    }
  }
  .LJ{
    .keyTable {
      height: calc(100% - 36px);
      display: flex;
      flex-direction: column;
      /*border: 1px solid #26332e;*/
    }
    .keyRow {
      display: flex;
      flex: 1;
    }
    .keyRow .ser {
      width: 20px;
      margin-left: 10px;
      display: flex;
      align-items: center;
      flex-shrink: 0;
      color: #a9abaa;
    }
    .keyCol {
      text-align: center;
      font-size: 18px;
    }
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
      text-align: center;
    }

    .animate-div-in {
      animation: fadeIn 0.3s;
    }
    .animate-div-out {
      animation: fadeOut 1s;
    }
    @keyframes fadeIn {
      0% {
        top: -300px;
        opacity: 0;
      }
      100% {
        top: 0;
        opacity: 1;
      }
    }
    @keyframes fadeOut {
      0% {
        left: 0;
      }
      100% {
        left: -300px;
      }
    }
    .corner {
      position: relative;
    }
    .cornerBox:after,
    .cornerBox:before,
    .corner:after,
    .corner:before {
      content: '';
      width: 10px;
      height: 10px;
      position: absolute;
      z-index: 9;
    }
    .cornerBox:before {
      background: url('../../../assets/LJ/postTrain/ico-lt.png') no-repeat center;
      left: 0;
      top: 0;
    }
    .cornerBox:after {
      background: url('../../../assets/LJ/postTrain/ico-rt.png') no-repeat center;
      right: 30px;
      top: 0;
    }
    .corner:after {
      background: url('../../../assets/LJ/postTrain/ico-lb.png') no-repeat center;
      left: 0;
      bottom: 0;
    }
    .corner:before {
      background: url('../../../assets/LJ/postTrain/ico-rb.png') no-repeat center;
      right: 30px;
      bottom: 0;
    }
    .cutImgBox {
      width: 100%;
      height: 100%;
      background-color: rgba(0, 0, 0, 0.35);
      position: absolute;
    }
    .cutImgBox .line {
      width: 100%;
      height: 100%;
      position: absolute;
      top: 0;
      left: 0;
    }
    .cutImgBox .absolute {
      width: 16px;
      height: 16px;
      border: 3px solid #04d1f0;
    }
    .cutImgBox .lt {
      left: -3px;
      top: -3px;
      border-right: none;
      border-bottom: none;
    }
    .cutImgBox .rt {
      right: -3px;
      top: -3px;
      border-left: none;
      border-bottom: none;
    }
    .cutImgBox .lb {
      left: -3px;
      bottom: -3px;
      border-right: none;
      border-top: none;
    }
    .cutImgBox .rb {
      right: -3px;
      bottom: -3px;
      border-left: none;
      border-top: none;
    }
    .operBtn {
      width: 110px;
      height: 34px;
      background: url('../../../assets/LJ/postTrain/operBtnBg.png') no-repeat center;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 15px;
      color: #fff;
      cursor: pointer;
      pointer-events: auto;
    }
    .operBtn + .operBtn {
      margin-left: 16px;
    }

    .operBtn.max {
      /*background: url("../../../assets/LJ/postTrain/operBtnMaxBg.png") no-repeat center;*/
    }
    .operBtn.max:hover {
      /*background: url("../../../assets/LJ/postTrain/operBtnMaxBg-on.png") no-repeat center;*/
    }
    .operBtn.reset {
      /*background: url("../../../assets/LJ/postTrain/operBtnResetBg.png") no-repeat center;*/
    }
    .operBtn.reset:hover {
      /*background: url("../../../assets/LJ/postTrain/operBtnResetBg-on.png") no-repeat center;*/
    }
    .operBtn .ico {
      margin-right: 6px;
    }
    .rotateBox {
      width: 240px;
      background-color: rgba(32, 66, 107, 0.5);
      border: 1px solid #20426b;
      padding-left: 10px;
      position: absolute;
      left: 0;
      bottom: 40px;
      display: flex;
      align-items: center;
    }
    .rotateBox .resetBtn {
      background-color: #009afd;
      line-height: 20px;
      flex-shrink: 0;
      font-size: 12px;
      padding: 0 10px;
      border-radius: 2px;
      cursor: pointer;
    }
    .keyTableHead {
      width: 100%;
      height: 36px;
      line-height: 36px;
      display: flex;
      align-items: stretch;
      padding-right: 30px;
      z-index: 10;
      position: relative;
    }
    .keyTableHead .key {
      background-color: #38403d;
      width: 10%;
      color: #a9abaa;
      font-weight: bold;
      font-size: 18px;
      text-align: center;
    }
  }
.KJ{
  .keyTable {
    height: calc(100% - 36px);
    display: flex;
    flex-direction: column;
    /*border: 1px solid #26332e;*/
  }
  .keyRow {
    display: flex;
    flex: 1;
  }
  .keyRow .ser {
    width: 20px;
    margin-left: 10px;
    display: flex;
    align-items: center;
    flex-shrink: 0;
    color: #a9abaa;
  }
  .keyCol {
    text-align: center;
    font-size: 18px;
  }
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
    text-align: center;
  }

  .animate-div-in {
    animation: fadeIn 0.3s;
  }
  .animate-div-out {
    animation: fadeOut 1s;
  }
  @keyframes fadeIn {
    0% {
      top: -300px;
      opacity: 0;
    }
    100% {
      top: 0;
      opacity: 1;
    }
  }
  @keyframes fadeOut {
    0% {
      left: 0;
    }
    100% {
      left: -300px;
    }
  }
  .corner {
    position: relative;
  }
  .cornerBox:after,
  .cornerBox:before,
  .corner:after,
  .corner:before {
    content: '';
    width: 10px;
    height: 10px;
    position: absolute;
    z-index: 9;
  }
  .cornerBox:before {
    background: url('../../../assets/KJ/postTrain/ico-lt.png') no-repeat center;
    left: 0;
    top: 0;
  }
  .cornerBox:after {
    background: url('../../../assets/KJ/postTrain/ico-rt.png') no-repeat center;
    right: 30px;
    top: 0;
  }
  .corner:after {
    background: url('../../../assets/KJ/postTrain/ico-lb.png') no-repeat center;
    left: 0;
    bottom: 0;
  }
  .corner:before {
    background: url('../../../assets/KJ/postTrain/ico-rb.png') no-repeat center;
    right: 30px;
    bottom: 0;
  }
  .cutImgBox {
    width: 100%;
    height: 100%;
    background-color: rgba(0, 0, 0, 0.35);
    position: absolute;
  }
  .cutImgBox .line {
    width: 100%;
    height: 100%;
    position: absolute;
    top: 0;
    left: 0;
  }
  .cutImgBox .absolute {
    width: 16px;
    height: 16px;
    border: 3px solid #04d1f0;
  }
  .cutImgBox .lt {
    left: -3px;
    top: -3px;
    border-right: none;
    border-bottom: none;
  }
  .cutImgBox .rt {
    right: -3px;
    top: -3px;
    border-left: none;
    border-bottom: none;
  }
  .cutImgBox .lb {
    left: -3px;
    bottom: -3px;
    border-right: none;
    border-top: none;
  }
  .cutImgBox .rb {
    right: -3px;
    bottom: -3px;
    border-left: none;
    border-top: none;
  }
  .operBtn {
    width: 110px;
    height: 34px;
    background: url('../../../assets/KJ/postTrain/operBtnBg.png') no-repeat center;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 15px;
    color: #fff;
    cursor: pointer;
    pointer-events: auto;
  }
  .operBtn + .operBtn {
    margin-left: 16px;
  }

  .operBtn.max {
    /*background: url("../../../assets/KJ/postTrain/operBtnMaxBg.png") no-repeat center;*/
  }
  .operBtn.max:hover {
    /*background: url("../../../assets/KJ/postTrain/operBtnMaxBg-on.png") no-repeat center;*/
  }
  .operBtn.reset {
    /*background: url("../../../assets/KJ/postTrain/operBtnResetBg.png") no-repeat center;*/
  }
  .operBtn.reset:hover {
    /*background: url("../../../assets/KJ/postTrain/operBtnResetBg-on.png") no-repeat center;*/
  }
  .operBtn .ico {
    margin-right: 6px;
  }
  .rotateBox {
    width: 240px;
    background-color: rgba(32, 66, 107, 0.5);
    border: 1px solid #20426b;
    padding-left: 10px;
    position: absolute;
    left: 0;
    bottom: 40px;
    display: flex;
    align-items: center;
  }
  .rotateBox .resetBtn {
    background-color: #009afd;
    line-height: 20px;
    flex-shrink: 0;
    font-size: 12px;
    padding: 0 10px;
    border-radius: 2px;
    cursor: pointer;
  }
  .keyTableHead {
    width: 100%;
    height: 36px;
    line-height: 36px;
    display: flex;
    align-items: stretch;
    padding-right: 30px;
    z-index: 10;
    position: relative;
  }
  .keyTableHead .key {
    background-color: rgba(80,141,230,0.6);
    width: 10%;
    color: #a9abaa;
    font-weight: bold;
    font-size: 18px;
    text-align: center;
  }
}
</style>
