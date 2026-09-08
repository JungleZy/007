<template>
  <!--  示范教学-->
  <div class="w-full h-full overflow-hidden">
    <video controls loop :src="datagramMp4" style="width: 100%;height: 100%;object-fit: fill;"></video>
    <!--<div class="w-full h-full ">
      <div class="w-full h-full" style="position: relative">
        <canvas id="rc" class="w-full h-full" style="border: 0"></canvas>
        <div v-if="activeS==0" style="padding: 5px;position: absolute;top: 0;right: 0;">
          <span class="posture" :class="{labelS:activeTwo==0}" @click="patHair(0,0,false)">立姿</span>
          <span class="posture" :class="{labelS:activeTwo==1}" @click="patHair(0,1,false)">跪姿</span>
        </div>
        <div v-if="progress<100" class="layout-right-center"
             style="padding: 20px;position: absolute;top: 0;left: 0;right: 0;bottom: 0;background: #122548 ">
          <img :src="cs" v-if="cool" style="width: 100%" alt="">
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
    name: "DemonstrationTeaching"
  }
</script>
<script setup>
  import * as BABYLON from '@babylonjs/core/Legacy/legacy'
  import * as dat from 'dat.gui'
  import '@babylonjs/loaders'
  import {onMounted, ref, onBeforeUnmount, inject} from "vue";
  import {rightLittleFinger1, rightLittleFinger2, rightHand, gear,foreArm,handKey, handKeyPlay, electronicKey, electronicKeyPlay, keyboard, keyboardPlay} from "./js/jsonData";
  import ydjj03 from '../../../../../assets/HJ/explain/ydjj03.png'
  import ydjj09 from '../../../../../assets/HJ/explain/ydjj09.png'
  import cs from '../../../../../assets/HJ/train/cs.gif'
  import csPng from "../../../../../assets/HJ/train/cs.png";
  import tou from '../../../../../assets/HJ/train/tou.png'


  const progress = ref(0);
  const scene = ref(null);
  const armature = ref(null);
  const camera = ref(null);
  const activeS = ref(0);
  const activeTwo = ref(2);
  const transformNode = ref(null);
  const list = ref([]);
  const actionList = ref(null)
  const cool = inject('cool');
  const num = ref(0);
  const topLeft=ref({});
  const isWindow=ref(false);
  const text=ref('')
  const datagramMp4 = ref(window.fileUrl+'/006/video/datagram.mp4');
  onBeforeUnmount(() => {
    scene.value = null;
    document.onkeydown = null
  })
  onMounted(() => {
    // init();
  });
  const init = () => {
    let interval = setInterval(() => {
      progress.value = progress.value + 1
      if (progress.value > 80) {
        clearInterval(interval);
      }
    }, 100);
    let canvas = document.getElementById("rc");
    let engine = new BABYLON.Engine(canvas, true, {
      preserveDrawingBuffer: true,
      stencil: true,
      disableWebGL2Support: false,
    });
    scene.value = new BABYLON.Scene(engine);
    scene.value.onPointerObservable.add(function (e) {
      isWindow.value=false;
      setTimeout(()=>{
        isWindow.value=true;
        textModel(e.pickInfo.pickedMesh.id)
      })
    },BABYLON.PointerEventTypes.POINTERPICK)

    camera.value = new BABYLON.ArcRotateCamera("camera1", Math.PI / 2, Math.PI / 4, 3, new BABYLON.Vector3(0, 1.9, 0), scene.value);
    camera.value.attachControl(canvas, true);

    camera.value.lowerRadiusLimit = 2.14;
    camera.value.upperRadiusLimit = 400;
    camera.value.wheelDeltaPercentage = 0.01;
    camera.value.setPosition(new BABYLON.Vector3(13.38,13.35,13.84))
    let light = new BABYLON.HemisphericLight("light1", new BABYLON.Vector3(0, 1, 0), scene.value);
    light.intensity = 0.6;
    light.specular = BABYLON.Color3.Black();
    let time = new Date().getHours();
    let light2 = new BABYLON.DirectionalLight("dir01", new BABYLON.Vector3((12 - time) / 2, -0.5, -1.0), scene.value);
    light2.position = new BABYLON.Vector3(0, 5, 5);

    // Shadows
    let shadowGenerator = new BABYLON.ShadowGenerator(1024, light2);
    shadowGenerator.useBlurExponentialShadowMap = true;
    shadowGenerator.blurKernel = 32;

    BABYLON.SceneLoader.ImportMesh("", window.fileUrl + "/006/model/", "classroom3.glb", scene.value, (newMeshes, particleSystems, skeletons) => {
          if (scene.value) {
            let skeleton = skeletons[0];
            transformNode.value = newMeshes[0];
            armature.value = skeletons[0].bones;
            for (let j of skeleton.getScene().rootNodes[3].getChildren()[9].getChildren()) {
              if (j.id === "mixamorig9:Hips") {
                actionList.value = j;
              }
            }

            //   let plane=BABYLON.Mesh.CreatePlane('plane',2,scene.value);
            //   plane.position.y=20;
            //   plane.position.z=50;
            //   plane.position.x=-5;
            // console.log(BABYLON.GUI);
            // let adv=BABYLON.GUI.AdvancedDynamicTexture.CreateForMesh(plane);
            //   let button1=BABYLON.GUI.Button.CreateSimpleButton("btn",'asd')
            // button1.onPointerUpObservable.add(()=>{
            //    // console.log(123)
            // })
            // adv.addController(button1)
            patHair(2, 0,true)
            //解决模型反光问题
            for (let i = 0; i < scene.value.materials.length; i++) {
              if (scene.value.materials[i].name === 'Ch31_body.001' || scene.value.materials[i].name === 'Ch31_hair.001') {
                scene.value.materials[i].metallicF0Factor = 0
                scene.value.materials[i].metallic = 0
              }
            }
            shadowGenerator.addShadowCaster(scene.value.meshes[0], true);
            for (let index = 0; index < newMeshes.length; index++) {
              if (newMeshes[index].receiveShadows){
                newMeshes[index].receiveShadows = false;
              }
            }
            let helper = scene.value.createDefaultEnvironment({
              enableGroundShadow: true,
              skyboxTexture: window.fileUrl + '/006/model/dds/backgroundSkybox.dds',
              groundTexture: window.fileUrl + '/006/model/dds/backgroundGround.png',
              environmentTexture: window.fileUrl + '/006/model/dds/environmentSpecular.env'
            });
            helper.setMainColor(BABYLON.Color3.Gray());
            helper.ground.position.y += 0.001;
            clearInterval(interval);
            let ff = setInterval(() => {
              progress.value = progress.value + 1
              if (progress.value > 100) {
                clearInterval(ff);
                //   let animateCameraToPosition = function (cam, speed, frameCount, newPos) {
                //     let ease = new BABYLON.CubicEase();
                //     ease.setEasingMode(BABYLON.EasingFunction.EASINGMODE_EASEINOUT);   //帧/秒  总帧数
                //     BABYLON.Animation.CreateAndStartAnimation('at5', cam, 'position', speed, frameCount, cam.position, newPos, 0, ease);
                //   }
                //   animateCameraToPosition(camera.value, 50, 200, new BABYLON.Vector3(207, 150, -150));
                //   animateCameraToPosition(camera.value, 50, 200, new BABYLON.Vector3(-3.38,3.35,3.84));
              }
            }, 30);
          }
        }
    );
    engine.runRenderLoop(() => {
      if (scene.value && scene.value.activeCamera) {
        scene.value.render();
      }
    })
  }
  const patHair = (e, it,bool) => {
    isWindow.value=false;
    if (bool) {
      let animateCameraToPosition = function (cam, speed, frameCount, newPos) {
        let ease = new BABYLON.CubicEase();
        ease.setEasingMode(BABYLON.EasingFunction.EASINGMODE_EASEINOUT);   //帧/秒  总帧数
        BABYLON.Animation.CreateAndStartAnimation('at5', cam, 'position', speed, frameCount, cam.position, newPos, 0, ease);
      }
      animateCameraToPosition(camera.value, 60, 120, new BABYLON.Vector3(13.38,13.35,13.84))
      camera.value.setPosition(new BABYLON.Vector3(13.38,13.35,13.84))
      animateCameraToPosition(camera.value, 60, 120, new BABYLON.Vector3(-3.38,3.35,3.84))
    }
    activeS.value = e;
    activeTwo.value = it;
    for (let j of transformNode.value.getChildren()) {
      if (j.id === "Klavesnice" || j.id === "Gear" || j.id === "dizuo" || j.id === "paper" || j.id === "pen" || j.id === "electronic key"|| j.id === "headset"|| j.id === "maozi"|| j.id === "锥体.001"|| j.id === "锥体") {
        if (e === 0) {
          if (j.id === "Gear") {
            j.setEnabled(true);
            j.position = new BABYLON.Vector3(-0.635,1.54, 0.93);
            j.rotation = new BABYLON.Vector3(3.141, 0.00399, 1.551);
            j.scaling = new BABYLON.Vector3(0.14, 0.14, 0.14);
            let d = BABYLON.Animation.Parse(gear);
            d.loopMode = 1
            j.animations.push(d)
            scene.value.beginAnimation(j, 0, 60, true)
            for (let k of armature.value) {
              if (k.id === 'mixamorig9:RightHand') {
                // let gui=new dat.GUI();
                // gui.domElement.id='dataGUI'
                // let con=BABYLON.Vector3.Zero()
                //    con=new BABYLON.Vector3(-0.2200616415339849,-0.7609221129375321, 0.12354577851195803)
                // gui.add(con,'x',-Math.PI,Math.PI)
                // gui.add(con,'y',-Math.PI,Math.PI)
                // gui.add(con,'z',-Math.PI,Math.PI)
                // scene.value.registerBeforeRender(()=>{
                //    console.log(con)
                //   k.rotation=con
                // })
                // let d = BABYLON.Animation.Parse(rightHand);
                // d.loopMode = 1
                // k.animations.push(d)
                // scene.value.beginAnimation(k, 0, 60, true)
              }else  if (k.id === 'mixamorig9:RightForeArm') {
                let d = BABYLON.Animation.Parse(foreArm);
                d.loopMode = 1
                k.animations.push(d)
                scene.value.beginAnimation(k, 0, 60, true)
              } else if (k.id === "mixamorig9:RightHandIndex1"
                  || k.id === "mixamorig9:RightHandMiddle1"
                  || k.id === "mixamorig9:RightHandPinky1"
                  || k.id === "mixamorig9:RightHandRing1"
                  || k.id === "mixamorig9:LeftHandIndex1"
                  || k.id === "mixamorig9:LeftHandMiddle1"
                  || k.id === "mixamorig9:LeftHandPinky1"
                  || k.id === "mixamorig9:LeftHandRing1") {
                scene.value.beginAnimation(k, 0, 0, false)
              }
            }
          }
          else if (j.id === "dizuo") {
            j.setEnabled(true);
            j.position = new BABYLON.Vector3(-0.5678196413536747,1.4693717181547727,0.7481300567138942);
            j.rotation = new BABYLON.Vector3(3.141592653589793, 1.5760207048219304, 0.005541006998369458);
            j.scaling = new BABYLON.Vector3(0.018,0.060,0.060);
          }
          else if (j.id === "paper") {
            j.setEnabled(true);
            j.position = new BABYLON.Vector3(0.1, 1.435, 0.91);
          }
          else if (j.id === "maozi") {
            j.setEnabled(true);
            j.position=new BABYLON.Vector3(0.028,2.35, 0.214)
            j.rotation=new BABYLON.Vector3(1.981,0.206, 0.074)
          }
          else if (j.id === "锥体.001") {
            j.setEnabled(true);
            j.position=new BABYLON.Vector3(-0.562,1.628, 0.908)
          }
          else if (j.id === "锥体") {
            j.setEnabled(true);
            j.position=new BABYLON.Vector3(-0.587,1.628,0.290)
          }
          else {
            j.setEnabled(false);
          }
        } else if (e === 1) {
          if (j.id === "electronic key") {
            j.setEnabled(true);
            j.position = new BABYLON.Vector3(-0.4440413644649883,1.5069213289303889,0.9092275558092622);
            // j.scaling = new BABYLON.Vector3(0.02, 0.01, 0.02);
            for (let k of armature.value) {
              if (k.id === 'mixamorig9:RightHand'
                  || k.id === "mixamorig9:LeftHandIndex1"
                  || k.id === "mixamorig9:LeftHandPinky1"
                  || k.id === "mixamorig9:LeftHandRing1"
                  || k.id === "mixamorig9:LeftHandMiddle1"
                  ||k.id === 'mixamorig9:RightForeArm') {
                scene.value.beginAnimation(k, 0, 0, false)
              } else if (k.id === "mixamorig9:RightHandIndex1" || k.id === "mixamorig9:RightHandRing1") {
                k.animations = [];
                let d = BABYLON.Animation.Parse(rightLittleFinger1);
                d.loopMode = 1
                k.animations.push(d)
                scene.value.beginAnimation(k, 0, 60, true)
              } else if (k.id === "mixamorig9:RightHandMiddle1" || k.id === "mixamorig9:RightHandPinky1") {
                k.animations = [];
                let d = BABYLON.Animation.Parse(rightLittleFinger2);
                d.loopMode = 1
                k.animations.push(d)
                scene.value.beginAnimation(k, 0, 60, true)
              }
            }
          }
          else if (j.id === "paper") {
            j.setEnabled(true);
            j.position = new BABYLON.Vector3(0.2, 1.435, 0.91);
          }
          else if (j.id === "maozi") {
            j.setEnabled(true);
            j.position=new BABYLON.Vector3(0.028,2.35, 0.214)
            j.rotation=new BABYLON.Vector3(1.981,0.206, 0.074)
          }
          else if (j.id === "锥体.001") {
            j.setEnabled(true);
            j.position=new BABYLON.Vector3(-0.451,1.642,0.853)
          }
          else if (j.id === "锥体") {
            j.setEnabled(true);
            j.position=new BABYLON.Vector3(-0.480,1.692,0.365)
          }
          else {
            j.setEnabled(false);
          }
        } else if (e === 2) {
          if (j.id === "Klavesnice") {
            j.setEnabled(true);
          }
          else if (j.id === "maozi") {
            j.setEnabled(true);
            j.setEnabled(true);
            j.position=new BABYLON.Vector3(0.009803548455238342,2.383,0.15557554364204407)
            j.rotation=new BABYLON.Vector3(1.72,0,0)
          }
          else if (j.id === "锥体.001") {
            j.setEnabled(true);
            j.position=new BABYLON.Vector3(-0.008,1.546,1.161)
          }
          else if (j.id === "锥体") {
            j.setEnabled(true);
            j.position=new BABYLON.Vector3(-0.425,1.708,0.548)
          }
          else {
            j.setEnabled(false);
          }
          for (let k of armature.value) {
            if (k.id === 'mixamorig9:RightHand'||k.id === 'mixamorig9:RightForeArm') {
              scene.value.beginAnimation(k, 0, 0, false)
            }
            for (let k of armature.value) {
              if (k.id === 'mixamorig9:RightHand') {
                scene.value.beginAnimation(k, 0, 0, false)
              } else if (k.id === "mixamorig9:RightHandIndex1" || k.id === "mixamorig9:RightHandRing1") {
                k.animations = [];
                let d = BABYLON.Animation.Parse(rightLittleFinger2);
                d.loopMode = 1
                k.animations.push(d)
                scene.value.beginAnimation(k, 0, 60, true)
              } else if (k.id === "mixamorig9:RightHandMiddle1" || k.id === "mixamorig9:RightHandPinky1") {
                k.animations = [];
                let d = BABYLON.Animation.Parse(rightLittleFinger1);
                d.loopMode = 1
                k.animations.push(d)
                scene.value.beginAnimation(k, 0, 60, true)
              } else if (k.id === "mixamorig9:LeftHandIndex1" || k.id === "mixamorig9:LeftHandRing1") {
                k.animations = [];
                let d = BABYLON.Animation.Parse(rightLittleFinger2);
                d.loopMode = 1
                k.animations.push(d)
                scene.value.beginAnimation(k, 0, 60, true)
              } else if (k.id === "mixamorig9:LeftHandMiddle1" || k.id === "mixamorig9:LeftHandPinky1") {
                k.animations = [];
                let d = BABYLON.Animation.Parse(rightLittleFinger1);
                d.loopMode = 1
                k.animations.push(d)
                scene.value.beginAnimation(k, 0, 60, true)
              }
            }
          }
        }
      }
    }
    dgAction(e, it, actionList.value)
  }
  const dgAction = (e, it, val) => {
    if (val.getChildren()) {
      for (let j of val.getChildren()) {
        num.value++
        if (num.value < 65) {
          j.rotationss = j.rotationQuaternion.toEulerAngles()
        }
        if (e === 0) {
          if (j.name === "mixamorig9:RightArm") {
            j.rotation = new BABYLON.Vector3(0.174,-0.785,-0.757)
          } else if (j.name === "mixamorig9:RightForeArm") {
            j.rotation = new BABYLON.Vector3(0.0875, 0.921, -1.542)
          } else if (j.name === "mixamorig9:RightHand") {
            j.rotation = new BABYLON.Vector3(-0.14382543744959086, -0.9425089946533909, 0.28213912639243643)
          } else if (j.name === "mixamorig9:RightHandThumb1") {
            j.rotation = new BABYLON.Vector3(-0.5562745853451623, -1.4560523704866057, -0.5123829860699702)
          } else if (j.name === "mixamorig9:RightHandThumb3") {
            j.rotation = new BABYLON.Vector3(0, 0, -0.02930319202081133)
          } else if (j.name === "mixamorig9:RightHandIndex1") {
            j.rotation = new BABYLON.Vector3(-0.2042680236786012, -0.10585030587109445, -0.18239741972137713)
          } else if (j.name === "mixamorig9:RightHandIndex2") {
            j.rotation = new BABYLON.Vector3(1.5890939857595558, -0.025056253717370858, -0.05670625841299648)
          } else if (j.name === "mixamorig9:RightHandIndex3") {
            j.rotation = new BABYLON.Vector3(0.8615689530189439, 0.36670559409570824, 0.28247353300239153)
          } else if (j.name === "mixamorig9:RightHandMiddle1") {
            if (it === 0) {
              j.rotation = new BABYLON.Vector3(0.2514456499079212, 0.11718873861844337, 0)
            } else {
              j.rotation = new BABYLON.Vector3(0.9728626348870044, -0.0336627612289484,-0.20330187293388402)
            }
          } else if (j.name === "mixamorig9:RightHandPinky1") {
            j.rotation = new BABYLON.Vector3(1.4463549636698488, 0.41768620205642426, 0)
          } else if (j.name === "mixamorig9:RightHandRing1") {
            j.rotation = new BABYLON.Vector3(1.7196927123634818, 0.1668219024795544, 0)
          } else if (j.name === "mixamorig9:RightHandMiddle2") {
            if (it === 0) {
              j.rotation = new BABYLON.Vector3(1.4194948732465047, 0, 0)
            } else {
              j.rotation = new BABYLON.Vector3(0.9036849835656016, 0, 0)
            }
          } else if (j.name === "mixamorig9:RightHandPinky2") {
            j.rotation = new BABYLON.Vector3(1.3198337370297635, 0, 0)
          } else if (j.name === "mixamorig9:RightHandRing2") {
            j.rotation = new BABYLON.Vector3(1.3198337370297635, 0, 0)
          } else if (j.name === "mixamorig9:RightHandMiddle3") {
            if (it === 0) {
              j.rotation = new BABYLON.Vector3(0.7022945792083299, 0, 0)
            } else {
              j.rotation = new BABYLON.Vector3(1.3198337370297635, 0, 0)
            }
          } else if (j.name === "mixamorig9:RightHandPinky3") {
            j.rotation = new BABYLON.Vector3(1.3198337370297635, 0, 0)
          } else if (j.name === "mixamorig9:RightHandRing3") {
            j.rotation = new BABYLON.Vector3(1.3198337370297635, 0, 0)
          } else if (j.name === "mixamorig9:Head") {
            j.rotation = new BABYLON.Vector3(0.32178583716978526, 0.15860569695038373, 0.014623220286205552)
          } else if (j.name === "mixamorig9:LeftForeArm") {
            j.rotation = new BABYLON.Vector3(0.719375480416959,-0.16071150905426546, 0.9298441924018057)
          } else if (j.name === "mixamorig9:LeftHand") {
            j.rotation = new BABYLON.Vector3(0.315358092320281, 1.3063883803299507, 0.47520168716055045)
          }
        } else if (e === 1) {
          if (j.name === "mixamorig9:RightHand") {
            j.rotation = new BABYLON.Vector3(-0.5802223398702391, -1.079766691157647, -0.07300361644330478)
          } else if (j.name === "mixamorig9:RightArm") {
            j.rotation = new BABYLON.Vector3(0.548320512250164,-0.1760796184668827,-1.075737845325150)
          } else if (j.name === "mixamorig9:LeftForeArm") {
            j.rotation = new BABYLON.Vector3(0.719375480416959,-0.16071150905426546, 0.9298441924018057)
          } else if (j.name === "mixamorig9:LeftHand") {
            j.rotation = new BABYLON.Vector3(0.315358092320281, 1.3063883803299507, 0.47520168716055045)
          } else if (j.name === "mixamorig9:Head") {
            j.rotation = new BABYLON.Vector3(0.32178583716978526, 0.15860569695038373, 0.014623220286205552)
          } else if (j.name === "mixamorig9:RightHandMiddle2" || j.name === "mixamorig9:RightHandPinky2" || j.name === "mixamorig9:RightHandRing2" || j.name === "mixamorig9:RightHandIndex2") {

            j.rotation = new BABYLON.Vector3(0.9507098792033464, 0, 0)
          } else {
            j.rotation = j.rotationss
          }
        } else {
          if (j.name === "mixamorig9:RightHand") {
            j.rotation = new BABYLON.Vector3(-0.42749765683567853, -1.079766691157647, -0.07300361644330478)
          } else if (j.name === "mixamorig9:LeftHand") {
            j.rotation = new BABYLON.Vector3(-0.3942629339476431, 0.9720561244840145, -0.07104767281327273)
          } else if (j.name === "mixamorig9:RightHandMiddle2"
              || j.name === "mixamorig9:RightHandPinky2"
              || j.name === "mixamorig9:RightHandRing2"
              || j.name === "mixamorig9:RightHandIndex2"
              || j.name === "mixamorig9:LeftHandMiddle2"
              || j.name === "mixamorig9:LeftHandPinky2"
              || j.name === "mixamorig9:LeftHandRing2"
              || j.name === "mixamorig9:LeftHandIndex2") {
            j.rotation = new BABYLON.Vector3(0.9507098792033464, 0, 0)
          } else {
            j.rotation = j.rotationss
          }
        }
        dgAction(e, it, j)
      }
    }
  }
  const textModel=(pickName)=>{
    let animateCameraToPosition = function (cam, speed, frameCount, newPos) {
      let ease = new BABYLON.CubicEase();
      ease.setEasingMode(BABYLON.EasingFunction.EASINGMODE_EASEINOUT);   //帧/秒  总帧数
      BABYLON.Animation.CreateAndStartAnimation('at5', cam, 'position', speed, frameCount, cam.position, newPos, 0, ease);
    }
    if (pickName=='锥体.001' && activeS.value==0 ) {
      animateCameraToPosition(camera.value, 60, 120, new BABYLON.Vector3(3.38,3.35,3.84))
      text.value=handKey;
    }else if (pickName=='锥体'&& activeS.value==0 ) {
      animateCameraToPosition(camera.value, 60, 120, new BABYLON.Vector3(3.38,3.35,1.8))
      text.value=handKeyPlay;
    }
    else if (pickName=='锥体.001' && activeS.value==1 ) {
      animateCameraToPosition(camera.value, 60, 120, new BABYLON.Vector3(3.38,3.35,3.84))
      text.value=electronicKey;
    }
    else if (pickName=='锥体' && activeS.value==1 ) {
      animateCameraToPosition(camera.value, 60, 120, new BABYLON.Vector3(3.38,3.35,1.8))
      text.value=electronicKeyPlay;
    }
    else if ((pickName=='锥体.001') && activeS.value==2 ) {
      animateCameraToPosition(camera.value, 60, 120, new BABYLON.Vector3(1.38,3.35,3))
      text.value=keyboard;
    }
    else if (pickName=='锥体'&& activeS.value==2 ) {
      animateCameraToPosition(camera.value, 60, 120, new BABYLON.Vector3(3.38,3.35,1.8))
      text.value=keyboardPlay;
    }else {
      isWindow.value=false;
    }

  }
</script>
<style scoped>
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

  .title1 {
    width: calc((100% - 655px) / 2);
    box-sizing: border-box;
  }

  .title2 {
    max-width: 655px;
    min-width: 655px;
    box-sizing: border-box;
    position: relative;
    padding-top: 6px;
  }

  .titleL {
    width: 266px;
    height: 90px;
    background-image: url("../../../../../assets/HJ/explain/ydjj05.png");
    background-repeat: no-repeat;
    position: absolute;
    z-index: 99;
    top: 0;
    left: 0;
  }

  .titleR {
    width: 266px;
    height: 90px;
    background-image: url("../../../../../assets/HJ/explain/ydjj07.png");
    background-repeat: no-repeat;
    position: absolute;
    z-index: 99;
    top: 0;
    right: 0;
  }

  .titles {
    box-sizing: border-box;
    width: calc(100% - 192px);
    border-top: 2px solid #71cbff;
  }

  @media (max-width: 1600px) {
    .titles {
      width: 100%;
    }

    .title1 img {
      display: none;
    }
  }

  .label {
    font-size: 18px;
    font-weight: 700;
    width: 209px;
    height: 47px;
    text-align: center;
    border-top: 2px solid #71cbff;
    line-height: 47px;
    border-bottom: 2px solid #71cbff;
    cursor: pointer;
    background-image: linear-gradient(0deg, #3169af, #0d4c93);
    transition: all 0.5s;
  }

  .label:hover {
    background-image: linear-gradient(0deg, #f6bd70, #ffaa3c);
    color: #814200;
  }

  .labelS {
    background-image: linear-gradient(0deg, #f6bd70, #ffaa3c) !important;
    color: #814200 !important;
  }

  .label2 {
    border-left: 2px solid #71cbff;
    border-right: 2px solid #71cbff;
  }

  .label3 {
    width: 320px;
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

  .posture {
    font-size: 18px;
    padding: 5px 10px;
    color: #fff;
    font-weight: 700;
    background-image: linear-gradient(0deg, #3169af, #0d4c93);
    cursor: pointer;
  }

  :hover.posture {
    background-image: linear-gradient(0deg, #f6bd70, #ffaa3c);
    color: #814200;
  }

</style>