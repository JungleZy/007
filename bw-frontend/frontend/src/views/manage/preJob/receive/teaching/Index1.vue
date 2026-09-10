<template>
  <!--  示范教学-->
  <div class="w-full h-full overflow-hidden layout-side" style="position: relative">
    <div class="w-full h-full">
      <div class="w-full" style="height: 100%; position: relative">
        <canvas id="rc" class="w-full h-full" style="border: 0"></canvas>
        <div v-if="progress < 100" class="layout-right-center" style="padding: 20px; position: absolute; top: 0; left: 0; right: 0; bottom: 0; background: rgba(23, 31, 41, 0.3)">
          <!--          <img :src="csGif" v-if="cool" style="width: 100%" alt="">-->
          <div class="loadingBG"></div>
          <!--          <img :src="csPng" v-else style="width: 100%" alt="">-->
          <div
            class="layout-left-center"
            style="background-image: linear-gradient(90deg, rgba(0, 0, 0, 0) 0%, rgba(23, 31, 41, 0.3) 3%); height: 100px; position: absolute; top: calc(50% - 59px); width: 100%; z-index: 99; transition: all 0.5s"
            :style="{ right: -10 - (progress > 83 ? 83 : progress) + '%' }"
          >
            <img :src="tou" style="margin-left: -30px" alt="" />
          </div>
          <div class="loadings" :style="{ color: progress > 95 ? '#60dc65' : progress > 75 ? '#fff59d' : progress > 50 ? '#f7bb79' : progress > 25 ? '#f7bb79' : 'red' }">
            {{ progress > 95 ? '已完成' : progress > 75 ? '正在生成模型...' : progress > 50 ? '正在烘焙光效...' : progress > 25 ? '正在搭建场景...' : '正在加载配置文件...' }}
          </div>
        </div>
      </div>
    </div>
    <div v-if="isWindow" class="window" :style="{ top: '200px', left: '100px' }">
      <a-textarea :disabled="true" style="resize: none; color: white" :rows="8" v-model:value="text"></a-textarea>
    </div>
  </div>
</template>

<script>
export default {
  name: 'ReceiveTeaching'
}
</script>
<script setup>
import * as BABYLON from '@babylonjs/core/Legacy/legacy'
import '@babylonjs/loaders'
import { onMounted, ref, onBeforeUnmount, inject } from 'vue'
import { rightLittleFinger1, zuoshouzhou, zuoxiaozhi, read, read2 } from './js/jsonData'
import * as dat from 'dat.gui'
import csGif from '../../../../../assets/HJ/train/cs.gif'
import csPng from '../../../../../assets/HJ/train/cs.png'
import tou from '../../../../../assets/HJ/train/tou.png'

const progress = ref(0)
const scene = ref(null)
const camera = ref(null)
const armature = ref(null)
const transformNode = ref(null)
const cool = inject('cool')
const isWindow = ref(false)
const text = ref('')
onBeforeUnmount(() => {
  scene.value = null
})
onMounted(() => {
  let interval = setInterval(() => {
    progress.value = progress.value + 1
    if (progress.value > 80) {
      clearInterval(interval)
    }
  }, 100)
  let canvas = document.getElementById('rc')
  let engine = new BABYLON.Engine(canvas, true, {
    preserveDrawingBuffer: true,
    stencil: true,
    disableWebGL2Support: false
  })
  scene.value = new BABYLON.Scene(engine)
  scene.value.clearColor = new BABYLON.Color4(0, 0, 0, 0)
  scene.value.onPointerObservable.add(function (e) {
    isWindow.value = false
    setTimeout(() => {
      isWindow.value = true
      textModel(e.pickInfo.pickedMesh.id)
    })
  }, BABYLON.PointerEventTypes.POINTERPICK)
  camera.value = new BABYLON.ArcRotateCamera('camera1', Math.PI / 2, Math.PI / 4, 3, new BABYLON.Vector3(0, 0.5, 0), scene.value)
  camera.value.attachControl(canvas, true)

  camera.value.lowerRadiusLimit = 2.14
  camera.value.upperRadiusLimit = 400
  camera.value.wheelDeltaPercentage = 0.01
  camera.value.setPosition(new BABYLON.Vector3(-3.48, 3.35, 3.84))
  let light = new BABYLON.HemisphericLight('light1', new BABYLON.Vector3(0, 1, 0), scene.value)
  light.intensity = 0.6
  light.specular = BABYLON.Color3.Black()
  let time = new Date().getHours()
  let light2 = new BABYLON.DirectionalLight('dir01', new BABYLON.Vector3((12 - time) / 2, -0.5, -1.0), scene.value)
  light2.position = new BABYLON.Vector3(0, 5, 5)

  // Shadows
  let shadowGenerator = new BABYLON.ShadowGenerator(1024, light2)
  shadowGenerator.useBlurExponentialShadowMap = true
  shadowGenerator.blurKernel = 32

  BABYLON.SceneLoader.ImportMesh('', window.fileUrl + '/006/model1/', 'wobi.glb', scene.value, (newMeshes, particleSystems, skeletons) => {
    if (scene.value) {
      let skeleton = skeletons[0]
      transformNode.value = newMeshes[0]
      //解决模型反光问题
      for (let i = 0; i < scene.value.materials.length; i++) {
        if (scene.value.materials[i].name === 'Ch31_body.002' || scene.value.materials[i].name === 'Ch31_hair.002' || scene.value.materials[i].name === 'yiling.002' || scene.value.materials[i].name === '材质.014') {
          scene.value.materials[i].metallicF0Factor = 0
          scene.value.materials[i].metallic = 0
        }
      }
      shadowGenerator.addShadowCaster(scene.value.meshes[0], true)
      for (let index = 0; index < newMeshes.length; index++) {
        if (newMeshes[index].receiveShadows) {
          newMeshes[index].receiveShadows = false
        }
      }
      let helper = scene.value.createDefaultEnvironment({
        enableGroundShadow: true,
        skyboxTexture: window.fileUrl + '/006/model/dds/backgroundSkybox.dds',
        groundTexture: window.fileUrl + '/006/model/dds/backgroundGround.png',
        environmentTexture: window.fileUrl + '/006/model/dds/environmentSpecular.env'
      })
      helper.setMainColor(BABYLON.Color3.Gray())
      helper.ground.position.y += 0.001
      clearInterval(interval)
      let ff = setInterval(() => {
        progress.value = progress.value + 1
        if (progress.value > 100) {
          clearInterval(ff)
          // let animateCameraToPosition = function (cam, speed, frameCount, newPos) {
          //   let ease = new BABYLON.CubicEase();
          //   ease.setEasingMode(BABYLON.EasingFunction.EASINGMODE_EASEINOUT);
          //   BABYLON.Animation.CreateAndStartAnimation('at5', cam, 'position', speed, frameCount, cam.position, newPos, 0, ease);
          // }
          // animateCameraToPosition(camera.value, 50, 200, new BABYLON.Vector3(207, 150, -150));
          // animateCameraToPosition(camera.value, 50, 200, new BABYLON.Vector3(-3.48,3.35,3.84));
        }
      }, 30)
    }
  })
  engine.runRenderLoop(() => {
    if (scene.value && scene.value.activeCamera) {
      scene.value.render()
    }
  })
})
const textModel = pickName => {
  let animateCameraToPosition = function (cam, speed, frameCount, newPos) {
    let ease = new BABYLON.CubicEase()
    ease.setEasingMode(BABYLON.EasingFunction.EASINGMODE_EASEINOUT) //帧/秒  总帧数
    BABYLON.Animation.CreateAndStartAnimation('at5', cam, 'position', speed, frameCount, cam.position, newPos, 0, ease)
  }
  if (pickName == '锥体.003') {
    animateCameraToPosition(camera.value, 60, 120, new BABYLON.Vector3(3.38, 3.35, 1.8))
    text.value = read
  } else if (pickName == '锥体.002') {
    animateCameraToPosition(camera.value, 60, 120, new BABYLON.Vector3(3.38, 3.35, 3.84))
    text.value = read2
  } else {
    isWindow.value = false
  }
}
</script>
<style>
.loadingBG {
  width: 100%;
  background: url('../../../../../assets/HJ/train/loadingBg.png') repeat;
  height: 24px;
}
.window {
  position: absolute;
  background: rgba(0, 0, 0, 0.6);
  border-bottom: 1px solid #5e3e0a;
  border-top: 1px solid #5e3e0a;
  width: 410px;
  padding: 8px;
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
    transform: scale(1.2);
  }
  to {
    transform: scale(1);
  }
}
</style>
