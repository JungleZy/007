<template>
  <!--  示范教学-->
  <div class="w-full h-full overflow-hidden">
    <video controls loop :src="receiveMp4" style="width: 100%;height: 100%;object-fit: fill;"></video>
    <!--<div class="w-full h-full ">
      <div class="w-full" style="height: 100%;position: relative">
        <canvas id="rc" class="w-full h-full" style="border: 0"></canvas>
        <div v-if="progress<100" class="layout-right-center"
             style="padding: 20px;position: absolute;top: 0;left: 0;right: 0;bottom: 0;background: #122548 ">
          <img :src="csGif" v-if="cool" style="width: 100%" alt="">
          <img :src="csPng" v-else style="width: 100%" alt="">
          <div class="layout-left-center"
               style="background-image: linear-gradient(90deg,rgba(0,0,0,0) 0%,#122548 3%);height: 300px;position: absolute;top:calc(50% - 158px );width: 100%;z-index: 99;transition: all 0.5s"
               :style="{right:(-10 - (progress> 83?83:progress))+'%'}">
            <img :src="tou" style="margin-left: -30px" alt="">
          </div>
          <div class="loadings"
               :style="{color:progress>95?'#60dc65':progress>75?'#fff59d':progress>50?'#f7bb79':progress>25?'#f7bb79':'red'}">
            {{
              progress > 95 ? '已完成' : progress > 75 ? '正在生成模型...' : progress > 50 ? '正在烘焙光效...' : progress > 25 ? '正在搭建场景...' : '正在加载配置文件...'
            }}
          </div>
        </div>

      </div>
    </div>
    <div v-if="isWindow" class="window" :style="{top:'200px',left:'100px'}">
      <a-textarea :disabled="true" style="resize: none;color: white" :rows="8" v-model:value="text"></a-textarea>
    </div>-->
  </div>
</template>

<script>
export default {
  name: "ReceiveTeaching"
}
</script>
<script setup>
import * as BABYLON from '@babylonjs/core/Legacy/legacy'
import '@babylonjs/loaders'
import {onMounted, ref, onBeforeUnmount, inject} from "vue"
import {read, read2} from "./js/jsonData"
import * as dat from 'dat.gui'

const progress = ref(0);
const scene = ref(null);
const camera = ref(null);
const transformNode = ref(null);
const isWindow=ref(false);
const receiveMp4 = ref(window.fileUrl+'/006/video/receive.mp4');
const text=ref('')
onBeforeUnmount(() => {
  scene.value = null
})
onMounted(() => {
  // init();
});

const textModel=(pickName)=>{
  let animateCameraToPosition = function (cam, speed, frameCount, newPos) {
    let ease = new BABYLON.CubicEase();
    ease.setEasingMode(BABYLON.EasingFunction.EASINGMODE_EASEINOUT);   //帧/秒  总帧数
    BABYLON.Animation.CreateAndStartAnimation('at5', cam, 'position', speed, frameCount, cam.position, newPos, 0, ease);
  }
  if (pickName=='锥体.001') {
    animateCameraToPosition(camera.value, 60, 120, new BABYLON.Vector3(3.38,3.35,1.8))
    text.value=read;
  }else if (pickName=='锥体') {
    animateCameraToPosition(camera.value, 60, 120, new BABYLON.Vector3(3.38,3.35,3.84))
    text.value=read2;
  }else {
    isWindow.value=false;
  }

}
</script>
<style>
  .window{
    position:absolute;
    background: rgba(0,0,0,0.6);
    border-bottom:1px solid #5e3e0a ;
    border-top:1px solid #5e3e0a ;
    width: 410px;
    padding:8px;
    overflow: auto;
  }
canvas:focus-visible {
  outline: 0;
}

.loadings {
  position: absolute;
  width: 200px;
  text-align: center;
  bottom: 20%;
  left: calc(50% - 100px);
  font-size: 20px;
  animation: myf 1s infinite;
  animation-direction: alternate;
}

@keyframes myf {
  from {
    transform: scale(1.2)
  }
  to {
    transform: scale(1)
  }
}
</style>