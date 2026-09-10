<template>
  <div class="container" ref="keycode">

    <div class="switchbox" @click="openarea">
      <div class="switch">
        <div class="slider"></div>
        <div class="light" :class="[area?'lightActive':'']"></div>
      </div>
    </div>
    <!--    <a-switch style="position: absolute;top:25px;right: 230px;z-index: 999" v-model:checked="area"  @change="changeSwitch" checked-children="开" un-checked-children="关"></a-switch>-->
    <div class="center"></div>
    <div class="keys" style="padding-top: 30px">
      <div style="display: flex;" class="lineBox">
        <div style="padding: 5px;position: relative" v-for="v of secondKey" :class="[v.finger&&area?v.finger:'']">
          <div class="pormptAreaBox" style="left: -20px" v-if="v.text2==1&&area" >
            <div v-if="v.finger" class="hand areahand"   :style="[fingerPosition(v.finger)]"></div>
            <div class="pormptBg" :class="[v.finger?v.finger:'']">小指(左手)</div>
            <div class="lineBg"  style="background:rgb(255 0 0)"></div>
          </div>
          <div class="pormptAreaBox" style="left: 0px" v-if="v.text2==2&&area" >
            <div v-if="v.finger" class="hand areahand"  :style="[fingerPosition(v.finger)]"></div>
            <div class="pormptBg" :class="[v.finger?v.finger:'']">无名指(左手)</div>
            <div class="lineBg" style="background: rgb(0 255 52)"></div>
          </div>
          <div class="pormptAreaBox" style="right: -20px" v-if="v.text2=='3'&&area" >
            <div v-if="v.finger" class="hand areahand"  :style="[fingerPosition(v.finger)]"></div>
            <div class="pormptBg" :class="[v.finger?v.finger:'']">中指(左手)</div>
            <div class="lineBg" style="background: rgb(255 138 0)"></div>
          </div>
          <div class="pormptAreaBox" style="left: -20px" v-if="v.text2=='5'&&area" >
            <div v-if="v.finger" class="hand areahand"  :style="[fingerPosition(v.finger)]"></div>
            <div class="pormptBg" :class="[v.finger?v.finger:'']" >食指(左手)</div>
            <div class="lineBg" style="background: #ffd100"></div>
          </div>
          <div class="pormptAreaBox" style="right: -20px" v-if="v.text2=='6'&&area" >
            <div v-if="v.finger" class="hand areahand"  :style="[fingerPosition(v.finger)]"></div>
            <div class="pormptBg l4"  >食指(右手)</div>
            <div class="lineBg" style="background: #ffd100"></div>
          </div>
          <div class="pormptAreaBox" style="left: -20px" v-if="v.text2=='8'&&area" >
            <div v-if="v.finger" class="hand areahand"  :style="[fingerPosition(v.finger)]"></div>
            <div class="pormptBg" :class="[v.finger?v.finger:'']" >中指(右手)</div>
            <div class="lineBg" style="background: rgb(255 138 0)"></div>
          </div>
          <div class="pormptAreaBox" style="left: -10px" v-if="v.text2=='9'&&area" >
            <div v-if="v.finger" class="hand areahand"  :style="[fingerPosition(v.finger)]"></div>
            <div class="pormptBg" :class="[v.finger?v.finger:'']">无名指(右手)</div>
            <div class="lineBg" style="background: rgb(0 255 52)"></div>
          </div>
          <div class="pormptAreaBox" style="right: -20px" v-if="v.text2=='0'&&area" >
            <div v-if="v.finger" class="hand areahand"  :style="[fingerPosition(v.finger)]"></div>
            <div class="pormptBg" :class="[v.finger?v.finger:'']">小指(右手)</div>
            <div class="lineBg"  style="background:rgb(255 0 0)"></div>
          </div>
          <div class="keybox" :style="[v.style2?v.style2:'']"
               :class="[activeKey==v.keyCode?keyStyle:'',targetKey==v.keyCode?'targetKey':'']">
            <div class="key key2" :style="[v.style?v.style:'']" >
              <div v-if="targetKey==v.keyCode&&!area"  class="pormptBox">
                {{keyBoardText}}
                <div v-if="v.finger" class="hand" :style="[fingerPosition(v.finger)]"></div>
              </div>
              <img v-if="targetKey==v.keyCode" style="position: absolute;top: 5px;right: 5px" :src="pointp" alt="">
              <div>{{v.text}}</div>
              <div v-if="v.text2">{{v.text2}}</div>
            </div>
          </div>
        </div>
      </div>
      <div style="display: flex">
        <div style="">
          <div style="display: flex;">
            <div style="padding: 5px" v-for="v of thirdKey" :class="[v.finger&&area?v.finger:'']">
              <div class="keybox" :style="[v.style2?v.style2:'']"
                   :class="[activeKey==v.keyCode?keyStyle:'',targetKey==v.keyCode?'targetKey':'']">
                <div class="key key2"   :style="[v.style?v.style:'']" >
                  <div v-if="targetKey==v.keyCode&&!area"  class="pormptBox">
                    {{keyBoardText}}<div v-if="v.finger" class="hand" :style="[fingerPosition(v.finger)]"></div>
                  </div>
                  <img v-if="targetKey==v.keyCode" style="position: absolute;top: 5px;right: 5px" :src="pointp" alt="">
                  <div>{{v.text}}</div>
                  <div v-if="v.text2">{{v.text2}}</div>
                </div>
              </div>
            </div>
          </div>
          <div style="display: flex;">
            <div style="padding: 5px" v-for="v of fourthKey" :class="[v.finger&&area?v.finger:'']">
              <div class="keybox" :style="[v.style2?v.style2:'']"
                   :class="[activeKey==v.keyCode?keyStyle:'',targetKey==v.keyCode?'targetKey':'']">
                <div class="key key2"   :style="[v.style?v.style:'']">
                  <div v-if="targetKey==v.keyCode&&!area"  class="pormptBox">
                    {{keyBoardText}}
                    <div v-if="v.finger" class="hand" :style="[fingerPosition(v.finger)]"></div>
                  </div>
                  <img v-if="targetKey==v.keyCode" style="position: absolute;top: 5px;right: 5px" :src="pointp" alt="">
                  <div :style="[v.tstyle?v.tstyle:'']">{{v.text}}</div>
                  <div :style="[v.tstyle2?v.tstyle2:'']" v-if="v.text2">{{v.text2}}</div>
                </div>
              </div>
            </div>
          </div>
        </div>
        <div style="padding-top: 5px;padding-left: 5px">
          <div class="keybox"  :class="[activeKey==107?keyStyle:'']">
            <div class="key" style="height: 128px">
              <div v-if="activeKey==107"  class="pormptBox">
                {{keyBoardText}}
              </div>
              <img v-if="activeKey==107" style="position: absolute;top: 5px;right: 5px" :src="pointp" alt="">
              <div>+</div>
            </div>
          </div>
        </div>
      </div>
      <div style="display: flex">
        <div style="">
          <div style="display: flex;">
            <div style="padding: 5px;" v-for="v of fifthKey" :class="[v.finger&&area?v.finger:'']">
              <div class="keybox" :style="[v.style2?v.style2:'']"
                   :class="[activeKey==v.keyCode&&(v.location?v.location==location:true)?keyStyle:'',targetKey==v.keyCode?'targetKey':'']">
                <div class="key key2"   :style="[v.style?v.style:'']" >
                  <div v-if="targetKey==v.keyCode&&!area"  class="pormptBox">
                    {{keyBoardText}}<div v-if="v.finger" class="hand" :style="[fingerPosition(v.finger)]"></div>
                  </div>
                  <img v-if="targetKey==v.keyCode" style="position: absolute;top: 5px;right: 5px" :src="pointp" alt="">
                  <div>{{v.text}}</div>
                  <div v-if="v.text2">{{v.text2}}</div>
                </div>
              </div>
            </div>
          </div>
          <div style="display: flex;">
            <div style="padding: 5px;" v-for="v of lastKey" :class="[v.finger&&area?v.finger:'']">
              <div class="keybox" :style="[v.style2?v.style2:'']"
                   :class="[activeKey==v.keyCode?keyStyle:'',targetKey==v.keyCode?'targetKey':'']">
                <div class="key key2"   :style="[v.style?v.style:'']" >
                  <div v-if="targetKey==v.keyCode&&!area"  class="pormptBox">
                    {{keyBoardText}}<div v-if="v.finger" class="hand" :style="[fingerPosition(v.finger)]"></div>
                  </div>
                  <img v-if="targetKey==v.keyCode" style="position: absolute;top: 5px;right: 5px" :src="pointp" alt="">
                  <div>{{v.text}}</div>
                  <div v-if="v.text2">{{v.text2}}</div>
                </div>
              </div>
            </div>
          </div>
        </div>
        <div style="padding: 5px">
          <div class="keybox" :class="[activeKey==13?keyStyle:'',targetKey==13?'targetKey':'']">
            <div class="key" style="height: 128px" >
              <div v-if="targetKey==13"  class="pormptBox">
                {{keyBoardText}}
              </div>
              <img v-if="targetKey==13" style="position: absolute;top: 5px;right: 5px" :src="pointp" alt="">
              <div>Enther</div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
  import {ref,onBeforeUnmount,getCurrentInstance,nextTick,toRefs,onMounted} from 'vue'
  import {first,second,third,fourth,fifth,last} from './js/enum.js'
  import point from '../../../assets/HJ/telexTrain/point.png'
  export default {
    name: "Keyboard",
    props: {
      targetKey:Number,
      keyBoardText:String,
      telexPat:Object
    },
    setup(props,content){
      onMounted(()=>{
        // scaleKeyBoard()
      })
      const pointp = ref(point)
      //定位手指的位置
      const fingerPosition = (position)=>{
        if(position==undefined){
          return ""
        }
        let location = 'background-position:'
        const base = 62
        switch (position.substring(0,2)) {
          case "l1":
            location = location+base+"px"
            break
          case "l2":
            location = location+base*2+"px"
            break
          case "l3":
            location = location+base*3+"px"
            break
          case "l4":
            location = location+base*4+"px"
            break
          case "r1":
            location = location+base*6+"px"
            break
          case "r2":
            location = location+base*7+"px"
            break
          case "r3":
            location = location+base*8+"px"
            break
          case "r4":
            location = location+base*9+"px"
            break
        }
        return location
      }
      const firstKey = ref(first)
      const secondKey = ref(second)
      const thirdKey = ref(third)
      const fourthKey = ref(fourth)
      const fifthKey = ref(fifth)
      const lastKey = ref(last)
      const activeKey = ref("")
      const location = ref("")
      const keyStyle = ref("")
      const area = ref(false)
      const _this = getCurrentInstance()
      const { targetKey,telexPat } = toRefs(props)
      //是否显示手指提示区域
      const openarea = ()=>{
        if(area.value){
          area.value = false
          anime({
            targets:['.slider'],
            duration:100,
            left:0,
          })
        }else {
          area.value = true
          anime({
            targets:['.slider'],
            duration:100,
            left:51,
          })
        }
      }
      const changeSwitch = ()=>{
        area.value!=area.value
      }
      let flag = true
      const keyCodeDown = (v) =>{
        if (flag){
          flag = false
          telexPat.value.count++
          anime({
            targets:['.errorKey', '.activeKey'],
            duration:1000,
            scale:1,
          })
          if(v.preventDefault){
            v.preventDefault()
          }else {
            window.event.returnValue == false
          }
          activeKey.value = v.keyCode
          location.value = v.location
          if( v.keyCode == targetKey.value){
            telexPat.value.theSuccessCount++
            keyStyle.value = "successKey"
            anime({
              targets:['.targetKey'],
              duration:1000,
              scale:0.8,
            })
          }else {
            telexPat.value.mistake++
            telexPat.value.themistake++
            keyStyle.value = "errorKey"
            nextTick(()=>{
              anime({
                targets:['.errorKey'],
                duration:1000,
                scale:0.8,
              })
            })
          }
        }
        content.emit("keyboardClick",v)
      }
      const scaleKeyBoard = ()=>{
        const screenW = document.body.clientWidth
        if(screenW<1919){
          anime({
            targets:['.keys'],
            duration:1000,
            scale:0.8,
          })
        }else {
          anime({
            targets:['.keys'],
            duration:1000,
            scale:1,
          })
        }
      }
      const keyCodeUp = (v)=>{
        flag = true
        anime({
          targets:['.errorKey', '.activeKey','.successKey'],
          duration:1000,
          scale:1,
        })

      }
      window.addEventListener('keydown',keyCodeDown)
      window.addEventListener('keyup',keyCodeUp)
      window.onresize = ()=>{
        // scaleKeyBoard()
      }
      onBeforeUnmount(()=>{
        window.removeEventListener("keydown", keyCodeDown)
        window.removeEventListener("keyup", keyCodeUp)
      })
      return{
        firstKey,secondKey,thirdKey,fourthKey,fifthKey,lastKey,activeKey,location,
        keyCodeDown,keyStyle,targetKey,telexPat,area,fingerPosition,changeSwitch,openarea,pointp
      }
    }
  }
</script>
<style scoped lang="less">

  .HJJ{
    .switchbox{
      position: absolute;top:20px;right: 230px;z-index: 99;
      cursor: pointer;
    }
    .switch{
      position: relative;
      height: 34px;
      width: 88px;
      background-image: url("../../../assets/HJJ/telexTrain/switch/switch.png");
    }
    .slider{
      background-image: url("../../../assets/HJJ/telexTrain/switch/slider.png");
      background-repeat: no-repeat;
      position: absolute;
      height: 34px;
      width: 39px;
      transition: all linear .1s;
    }
    .light{
      background-image: url("../../../assets/HJJ/telexTrain/switch/light.png");
      background-repeat: no-repeat;
      position: absolute;
      height: 20px;
      width: 20px;
      right: -5px;
      top: 7px;
    }
    .lightActive{
      background-image: url("../../../assets/HJJ/telexTrain/switch/lightActive.png");
    }
    .hand{
      height: 62px;
      width: 62px;
      background-image: url("../../../assets/HJJ/telexTrain/hand.png");
    }
    .pormptBg{
      background: #0a1a35;
      width: max-content;
      padding: 5px;
      border-radius: 5px;
      font-size: 12px;
    }
    .pormptAreaBox{
      position: absolute;bottom: 80px;display: flex;flex-direction: column;align-items: center;z-index: 99;
    }
    .lineBg{
      background: #0a9a89;
      height: 10px;
      width: 1px;
    }
    .l1{
      background: #2b0505;
      box-shadow: inset 0 -5px 10px -5px rgb(255 0 0);
      border-radius: 3px;
    }
    .l2{
      background: #051d1d;
      border-radius: 3px;
      box-shadow: inset 0 -5px 10px -5px rgb(0 255 52);
    }
    .l3{
      background: #2d1603;
      border-radius: 3px;
      box-shadow: inset 0 -5px 10px -5px rgb(255 138 0);
    }
    .l4{
      background: #212300;
      border-radius: 3px;
      position: relative;
      box-shadow: inset 0 -5px 10px -5px #ffd100;
    }

    .rsplit::before{
      content: '';
      position: absolute;
      width: calc(50% + 1px);
      height: 1px;
      background: #00ffdc;
      bottom: 0px;
      left: 0px;
    }
    .rsplit::after{
      content: '';
      position: absolute;
      width: 1px;
      height: 100%;
      background:  #00ffdc;
      left: 0px;
      top: 0px;
    }
    .rsplitLast::before{
      content: '';
      position: absolute;
      width: 1px;
      height: 100%;
      background:  #00ffdc;
      left: 0px;
      top: 0px;
    }
    .r4{
      background: #212300;
      border-radius: 3px;
      position: relative;
      box-shadow: inset 0 -5px 10px -5px #ffd100;
    }
    .r3{
      background: #2d1603;
      border-radius: 3px;
      box-shadow: inset 0 -5px 10px -5px rgb(255 138 0);
    }
    .r2{
      background: #051d1d;
      border-radius: 3px;
      box-shadow: inset 0 -5px 10px -5px rgb(0 255 52);
    }
    .r1{
      background: #2b0505;
      box-shadow: inset 0 -5px 10px -5px rgb(255 0 0);
      border-radius: 3px;
    }

    .container{
      width: 100%;height: 476px;
      position: relative;
      margin: 0 auto;
      /*padding-top: 30px;*/
      background-image: url("../../../assets/HJJ/telexTrain/keyboard-left.png"),url("../../../assets/HJJ/telexTrain/keyboard-right.png");
      background-repeat: no-repeat,no-repeat;
      background-position: top left , top right;
      display: flex;
      align-items: center;
      justify-content: center;
      position: relative;
      color: #d6e4ff;
      animation: keyBoard 1s;
    }
    .center{
      width: calc(100% - 860px);
      height: 100%;
      position: absolute;
      left: 430px;
      background:url("../../../assets/HJJ/telexTrain/keyboard-center.png") repeat;
    }
    .keybox{
      padding: 5px 6px 10px 6px;
      background-image: radial-gradient(closest-side at 5px 5px, #516c82, rgba(0, 0, 0, 0)), radial-gradient(closest-side at 90% 5px, #516c82, rgba(0, 0, 0, 0)), radial-gradient(closest-side at 5px 90%, #0e1c39, rgba(0, 0, 0, 0)), radial-gradient(closest-side at 90% 90%, #0e1c39, rgba(0, 0, 0, 0)), linear-gradient(0deg, #152443, #516c82);
      box-shadow: 0 0 5px #37556d;
      border: 1px solid #091731;
      border-radius: 5px;
      position: relative;
      transition: all 0.1s;
    }
    .key{
      box-shadow: 0 0 10px #091731;
      height: 50px;
      width: 50px;
      color: #d6e4ff;
      font-weight: bold;
      border: 1px solid #3d5f79;
      display: flex;
      align-items: center;
      justify-content: center;
      cursor: pointer;
      background-image: linear-gradient(0deg, #30495f, #212f40);
      border-radius: 5px;
    }
    .key2{
      flex-direction: column;
    }
    .activeKey,.targetKey{
      box-shadow: 0 0 10px #736d61;
      background-image: radial-gradient(closest-side at 5px 5px, #7a7366, rgba(0, 0, 0, 0)), radial-gradient(closest-side at 90% 5px, #7a7366, rgba(0, 0, 0, 0)), radial-gradient(closest-side at 5px 90%, #2d2418, rgba(0, 0, 0, 0)), radial-gradient(closest-side at 90% 90%, #2d2418, rgba(0, 0, 0, 0)), linear-gradient(0deg, #3c2e1a, #6f6a5f);
      border: solid 1px #292116;
    }
    .activeKey .key{
      background-image: linear-gradient(0deg,#0060f3,#002166);
      /*border-image: linear-gradient( #394c74,#375490) 5 5;*/
      border-color: #385081;
    }
    .targetKey .key{
      background-image: linear-gradient(0deg, #746e62, #383b3e);
      border-color: #726d60;
      color: #fbb354;
    }
    .errorKey{
      box-shadow: 0 0 10px #ff0000;
      background-image:radial-gradient(closest-side at 5px 5px, #923033,rgba(0,0,0,0)),
      radial-gradient(closest-side at 90% 5px, #923033,rgba(0,0,0,0)),
      radial-gradient(closest-side at 5px 90%, #49120e,rgba(0,0,0,0)),
      radial-gradient(closest-side at 90% 90%, #49120e,rgba(0,0,0,0)),
      linear-gradient(0deg,#6c1605,#931606);
      border-color: #310b09;
    }
    .errorKey .key{
      background-image: linear-gradient(0deg,#420d0d,#b12525);
      /*border-image: linear-gradient( #965656,#ae1414) 5 5;*/
      border-color: #a13939;
    }
    .successKey{
      box-shadow: 0 0 10px #0a9a89;
      background-image:radial-gradient(closest-side at 5px 5px, #0ca77c,rgba(0,0,0,0)),
      radial-gradient(closest-side at 90% 5px, #0e9295,rgba(0,0,0,0)),
      radial-gradient(closest-side at 5px 90%, #023125,rgba(0,0,0,0)),
      radial-gradient(closest-side at 90% 90%, #023125,rgba(0,0,0,0)),
      linear-gradient(0deg,#033c2c,#06938b);
      border-color: #03311d;
    }
    .successKey .key{
      background-image: linear-gradient(0deg,#0aaeaf,#006666);
      /*border-image: linear-gradient( #965656,#ae1414) 5 5;*/
      border-color: #2f9596;
    }
    .pormptBox{
      position: absolute;bottom: 80px;right:50px;z-index: 99;
      background-image: url("../../../assets/HJJ/telexTrain/left-top.png"),
      url("../../../assets/HJJ/telexTrain/left-bottom.png"),
      url("../../../assets/HJJ/telexTrain/right-top.png"),
      url("../../../assets/HJJ/telexTrain/right-bottom.png"),
      linear-gradient(0deg, rgba(15,20,32,0.5), rgba(15,20,32,0.5));;
      background-position: left top,left bottom,right top,right bottom;
      background-repeat: no-repeat;
      width: max-content;
      border-radius: 8px;
      box-shadow: 0 0 10px rgba(250,170,66,0.5)  inset;
      color: #faaa42;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 20px 10px;
    }
  }
  .LJ{
    .switchbox{
      position: absolute;top:20px;right: 230px;z-index: 99;
      cursor: pointer;
    }
    .switch{
      position: relative;
      height: 34px;
      width: 88px;
      background-image: url("../../../assets/LJ/telexTrain/switch/switch.png");
    }
    .slider{
      background-image: url("../../../assets/LJ/telexTrain/switch/slider.png");
      background-repeat: no-repeat;
      position: absolute;
      height: 34px;
      width: 39px;
      transition: all linear .1s;
    }
    .light{
      background-image: url("../../../assets/LJ/telexTrain/switch/light.png");
      background-repeat: no-repeat;
      position: absolute;
      height: 20px;
      width: 20px;
      right: -5px;
      top: 7px;
    }
    .lightActive{
      background-image: url("../../../assets/LJ/telexTrain/switch/lightActive.png");
    }
    .hand{
      height: 62px;
      width: 62px;
      background-image: url("../../../assets/LJ/telexTrain/hand.png");
    }
    .pormptBg{
      background: #0a1a35;
      width: max-content;
      padding: 5px;
      border-radius: 5px;
      font-size: 12px;
    }
    .pormptAreaBox{
      position: absolute;bottom: 80px;display: flex;flex-direction: column;align-items: center;z-index: 99;
    }
    .lineBg{
      background: #0a9a89;
      height: 10px;
      width: 1px;
    }
    .l1{
      background: #2b0505;
      box-shadow: inset 0 -5px 10px -5px rgb(255 0 0);
      border-radius: 3px;
    }
    .l2{
      background: #051d1d;
      border-radius: 3px;
      box-shadow: inset 0 -5px 10px -5px rgb(0 255 52);
    }
    .l3{
      background: #2d1603;
      border-radius: 3px;
      box-shadow: inset 0 -5px 10px -5px rgb(255 138 0);
    }
    .l4{
      background: #212300;
      border-radius: 3px;
      position: relative;
      box-shadow: inset 0 -5px 10px -5px #ffd100;
    }

    .rsplit::before{
      content: '';
      position: absolute;
      width: calc(50% + 1px);
      height: 1px;
      background: #00ffdc;
      bottom: 0px;
      left: 0px;
    }
    .rsplit::after{
      content: '';
      position: absolute;
      width: 1px;
      height: 100%;
      background:  #00ffdc;
      left: 0px;
      top: 0px;
    }
    .rsplitLast::before{
      content: '';
      position: absolute;
      width: 1px;
      height: 100%;
      background:  #00ffdc;
      left: 0px;
      top: 0px;
    }
    .r4{
      background: #212300;
      border-radius: 3px;
      position: relative;
      box-shadow: inset 0 -5px 10px -5px #ffd100;
    }
    .r3{
      background: #2d1603;
      border-radius: 3px;
      box-shadow: inset 0 -5px 10px -5px rgb(255 138 0);
    }
    .r2{
      background: #051d1d;
      border-radius: 3px;
      box-shadow: inset 0 -5px 10px -5px rgb(0 255 52);
    }
    .r1{
      background: #2b0505;
      box-shadow: inset 0 -5px 10px -5px rgb(255 0 0);
      border-radius: 3px;
    }

    .container{
      width: 100%;height: 476px;
      position: relative;
      margin: 0 auto;
      /*padding-top: 30px;*/
      background-image: url("../../../assets/LJ/telexTrain/keyboard-left.png"),url("../../../assets/LJ/telexTrain/keyboard-right.png");
      background-repeat: no-repeat,no-repeat;
      background-position: top left , top right;
      display: flex;
      align-items: center;
      justify-content: center;
      position: relative;
      color: #d6e4ff;
      animation: keyBoard 1s;
    }
    .center{
      width: calc(100% - 860px);
      height: 100%;
      position: absolute;
      left: 430px;
      background:url("../../../assets/LJ/telexTrain/keyboard-center.png") repeat;
    }
    .keybox{
      padding: 5px 6px 10px 6px;
      background-image: radial-gradient(closest-side at 5px 5px, #567967, rgba(0, 0, 0, 0)), radial-gradient(closest-side at 90% 5px, #567967, rgba(0, 0, 0, 0)), radial-gradient(closest-side at 5px 90%, #171b1a, rgba(0, 0, 0, 0)), radial-gradient(closest-side at 90% 90%, #171b1a, rgba(0, 0, 0, 0)), linear-gradient(0deg, #202522, #505f5b);
      box-shadow: 0 0 5px #40674a;
      border: 1px solid #181f1c;
      border-radius: 5px;
      position: relative;
      transition: all 0.1s;
    }
    .key{
      box-shadow: 0 0 10px #34403a;
      height: 50px;
      width: 50px;
      font-weight: bold;
      border: 1px solid #3d5f79;
      display: flex;
      align-items: center;
      justify-content: center;
      background-image: linear-gradient(0deg, #3b4c45, #232f2a);
      color: #ffffff;
      border: 1px solid #545f50;
    }
    .key2{
      flex-direction: column;
    }
    .activeKey,.targetKey{
      box-shadow: 0 0 10px #736d61;
      background-image: radial-gradient(closest-side at 5px 5px, #7a7366, rgba(0, 0, 0, 0)), radial-gradient(closest-side at 90% 5px, #7a7366, rgba(0, 0, 0, 0)), radial-gradient(closest-side at 5px 90%, #2d2418, rgba(0, 0, 0, 0)), radial-gradient(closest-side at 90% 90%, #2d2418, rgba(0, 0, 0, 0)), linear-gradient(0deg, #3c2e1a, #6f6a5f);
      border: solid 1px #292116;
    }
    .activeKey .key{
      background-image: linear-gradient(0deg,#0060f3,#002166);
      /*border-image: linear-gradient( #394c74,#375490) 5 5;*/
      border-color: #385081;
    }
    .targetKey .key{
      background-image: linear-gradient(0deg, #746e62, #383e38);
      border-color: #726d60;
      color: #fbb354;
    }
    .errorKey{
      box-shadow: 0 0 10px #ff0000;
      background-image:radial-gradient(closest-side at 5px 5px, #923033,rgba(0,0,0,0)),
      radial-gradient(closest-side at 90% 5px, #923033,rgba(0,0,0,0)),
      radial-gradient(closest-side at 5px 90%, #49120e,rgba(0,0,0,0)),
      radial-gradient(closest-side at 90% 90%, #49120e,rgba(0,0,0,0)),
      linear-gradient(0deg,#6c1605,#931606);
      border-color: #310b09;
    }
    .errorKey .key{
      background-image: linear-gradient(0deg,#420d0d,#b12525);
      /*border-image: linear-gradient( #965656,#ae1414) 5 5;*/
      border-color: #a13939;
    }
    .successKey{
      box-shadow: 0 0 10px #0a9a89;
      background-image:radial-gradient(closest-side at 5px 5px, #0ca77c,rgba(0,0,0,0)),
      radial-gradient(closest-side at 90% 5px, #0e9295,rgba(0,0,0,0)),
      radial-gradient(closest-side at 5px 90%, #023125,rgba(0,0,0,0)),
      radial-gradient(closest-side at 90% 90%, #023125,rgba(0,0,0,0)),
      linear-gradient(0deg,#033c2c,#06938b);
      border-color: #03311d;
    }
    .successKey .key{
      background-image: linear-gradient(0deg,#0aaeaf,#006666);
      /*border-image: linear-gradient( #965656,#ae1414) 5 5;*/
      border-color: #2f9596;
    }
    .pormptBox{
      position: absolute;bottom: 80px;right:50px;z-index: 99;
      background-image: url("../../../assets/LJ/telexTrain/left-top.png"),
      url("../../../assets/LJ/telexTrain/left-bottom.png"),
      url("../../../assets/LJ/telexTrain/right-top.png"),
      url("../../../assets/LJ/telexTrain/right-bottom.png"),
      linear-gradient(0deg, rgb(1 2 1 / 50%), rgb(19 32 15 / 50%));;
      background-position: left top,left bottom,right top,right bottom;
      background-repeat: no-repeat;
      width: max-content;
      border-radius: 8px;
      box-shadow: 0 0 10px rgba(250,170,66,0.5)  inset;
      color: #faaa42;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 20px 10px;
    }
  }
  .HJ,.GD{
    .switchbox{
      position: absolute;top:20px;right: 230px;z-index: 99;
      cursor: pointer;
    }
    .switch{
      position: relative;
      height: 34px;
      width: 88px;
      background-image: url("../../../assets/HJ/telexTrain/switch/switch.png");
    }
    .slider{
      background-image: url("../../../assets/HJ/telexTrain/switch/slider.png");
      background-repeat: no-repeat;
      position: absolute;
      height: 34px;
      width: 39px;
      transition: all linear .1s;
    }
    .light{
      background-image: url("../../../assets/HJ/telexTrain/switch/light.png");
      background-repeat: no-repeat;
      position: absolute;
      height: 20px;
      width: 20px;
      right: -5px;
      top: 7px;
    }
    .lightActive{
      background-image: url("../../../assets/HJ/telexTrain/switch/lightActive.png");
    }
    .hand{
      height: 62px;
      width: 62px;
      background-image: url("../../../assets/HJ/telexTrain/hand.png");
    }
    .pormptBg{
      background: #0a1a35;
      width: max-content;
      padding: 5px;
      border-radius: 5px;
      font-size: 12px;
    }
    .pormptAreaBox{
      position: absolute;bottom: 80px;display: flex;flex-direction: column;align-items: center;z-index: 99;
    }
    .lineBg{
      background: #0a9a89;
      height: 10px;
      width: 1px;
    }
    .l1{
      background: #2b0505;
      box-shadow: inset 0 -5px 10px -5px rgb(255 0 0);
      border-radius: 3px;
    }
    .l2{
      background: #051d1d;
      border-radius: 3px;
      box-shadow: inset 0 -5px 10px -5px rgb(0 255 52);
    }
    .l3{
      background: #2d1603;
      border-radius: 3px;
      box-shadow: inset 0 -5px 10px -5px rgb(255 138 0);
    }
    .l4{
      background: #212300;
      border-radius: 3px;
      position: relative;
      box-shadow: inset 0 -5px 10px -5px #ffd100;
    }

    .rsplit::before{
      content: '';
      position: absolute;
      width: calc(50% + 1px);
      height: 1px;
      background: #00ffdc;
      bottom: 0px;
      left: 0px;
    }
    .rsplit::after{
      content: '';
      position: absolute;
      width: 1px;
      height: 100%;
      background:  #00ffdc;
      left: 0px;
      top: 0px;
    }
    .rsplitLast::before{
      content: '';
      position: absolute;
      width: 1px;
      height: 100%;
      background:  #00ffdc;
      left: 0px;
      top: 0px;
    }
    .r4{
      background: #212300;
      border-radius: 3px;
      position: relative;
      box-shadow: inset 0 -5px 10px -5px #ffd100;
    }
    .r3{
      background: #2d1603;
      border-radius: 3px;
      box-shadow: inset 0 -5px 10px -5px rgb(255 138 0);
    }
    .r2{
      background: #051d1d;
      border-radius: 3px;
      box-shadow: inset 0 -5px 10px -5px rgb(0 255 52);
    }
    .r1{
      background: #2b0505;
      box-shadow: inset 0 -5px 10px -5px rgb(255 0 0);
      border-radius: 3px;
    }

    .container{
      width: 100%;height: 476px;
      position: relative;
      margin: 0 auto;
      /*padding-top: 30px;*/
      background-image: url("../../../assets/HJ/telexTrain/keyboard-left.png"),url("../../../assets/HJ/telexTrain/keyboard-right.png");
      background-repeat: no-repeat,no-repeat;
      background-position: top left , top right;
      display: flex;
      align-items: center;
      justify-content: center;
      position: relative;
      color: #d6e4ff;
      animation: keyBoard 1s;
    }
    .center{
      width: calc(100% - 860px);
      height: 100%;
      position: absolute;
      left: 430px;
      background:url("../../../assets/HJ/telexTrain/keyboard-center.png") repeat;
    }
    .keybox{
      padding: 5px 6px 10px 6px;
      background-image:radial-gradient(closest-side at 5px 5px, #466897,rgba(0,0,0,0)),
      radial-gradient(closest-side at 90% 5px, #466897,rgba(0,0,0,0)),
      radial-gradient(closest-side at 5px 90%, #0e1c39,rgba(0,0,0,0)),
      radial-gradient(closest-side at 90% 90%, #0e1c39,rgba(0,0,0,0)),
      linear-gradient(0deg,#152443,#385887);
      /*margin: 5px;*/
      box-shadow: 0 0 5px #1553c4;
      border: 1px solid #091731;
      border-radius: 5px;
      position: relative;
      transition: all .1s;
    }
    .key{
      box-shadow: 0 0 10px #091731;
      height: 50px;
      width: 50px;
      color: #d6e4ff;
      font-weight: bold;
      border: 1px solid #365086;
      /*border-image: linear-gradient( red,#32466f) 100 5;*/
      display: flex;
      align-items: center;
      justify-content: center;
      cursor: pointer;
      background-image: linear-gradient(0deg,#203d6a,#0d1e42);
      border-radius: 5px;
    }
    .key2{
      flex-direction: column;
    }
    .activeKey,.targetKey{
      box-shadow: 0 0 10px #0054ff;
      background-image:radial-gradient(closest-side at 5px 5px, #0e4d95,rgba(0,0,0,0)),
      radial-gradient(closest-side at 90% 5px, #0e4d95,rgba(0,0,0,0)),
      radial-gradient(closest-side at 5px 90%, #031e51,rgba(0,0,0,0)),
      radial-gradient(closest-side at 90% 90%, #031e51,rgba(0,0,0,0)),
      linear-gradient(0deg,#05266c,#063f93)
    }
    .activeKey .key{
      background-image: linear-gradient(0deg,#0060f3,#002166);
      /*border-image: linear-gradient( #394c74,#375490) 5 5;*/
      border-color: #385081;
    }
    .targetKey .key{
      background-image: linear-gradient(0deg,#0060f3,#002166);
      /*border-image: linear-gradient( #394c74,#375490) 5 5;*/
      border-color: #385081;
    }
    .errorKey{
      box-shadow: 0 0 10px #ff0000;
      background-image:radial-gradient(closest-side at 5px 5px, #923033,rgba(0,0,0,0)),
      radial-gradient(closest-side at 90% 5px, #923033,rgba(0,0,0,0)),
      radial-gradient(closest-side at 5px 90%, #49120e,rgba(0,0,0,0)),
      radial-gradient(closest-side at 90% 90%, #49120e,rgba(0,0,0,0)),
      linear-gradient(0deg,#6c1605,#931606);
      border-color: #310b09;
    }
    .errorKey .key{
      background-image: linear-gradient(0deg,#420d0d,#b12525);
      /*border-image: linear-gradient( #965656,#ae1414) 5 5;*/
      border-color: #a13939;
    }
    .successKey{
      box-shadow: 0 0 10px #0a9a89;
      background-image:radial-gradient(closest-side at 5px 5px, #0ca77c,rgba(0,0,0,0)),
      radial-gradient(closest-side at 90% 5px, #0e9295,rgba(0,0,0,0)),
      radial-gradient(closest-side at 5px 90%, #023125,rgba(0,0,0,0)),
      radial-gradient(closest-side at 90% 90%, #023125,rgba(0,0,0,0)),
      linear-gradient(0deg,#033c2c,#06938b);
      border-color: #03311d;
    }
    .successKey .key{
      background-image: linear-gradient(0deg,#0aaeaf,#006666);
      /*border-image: linear-gradient( #965656,#ae1414) 5 5;*/
      border-color: #2f9596;
    }
    .pormptBox{
      position: absolute;bottom: 80px;right:50px;z-index: 99;
      background-image: url("../../../assets/HJ/telexTrain/left-top.png"),
      url("../../../assets/HJ/telexTrain/left-bottom.png"),
      url("../../../assets/HJ/telexTrain/right-top.png"),
      url("../../../assets/HJ/telexTrain/right-bottom.png"),
      linear-gradient(0deg, rgba(15,20,32,0.5), rgba(15,20,32,0.5));;
      background-position: left top,left bottom,right top,right bottom;
      background-repeat: no-repeat;
      width: max-content;
      border-radius: 8px;
      box-shadow: 0 0 10px rgba(250,170,66,0.5)  inset;
      color: #faaa42;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 20px 10px;
    }
  }
  .KJ{
    .switchbox{
      position: absolute;top:20px;right: 230px;z-index: 99;
      cursor: pointer;
    }
    .switch{
      position: relative;
      height: 34px;
      width: 88px;
      background-image: url("../../../assets/KJ/telexTrain/switch/switch.png");
    }
    .slider{
      background-image: url("../../../assets/KJ/telexTrain/switch/slider.png");
      background-repeat: no-repeat;
      position: absolute;
      height: 34px;
      width: 39px;
      transition: all linear .1s;
    }
    .light{
      background-image: url("../../../assets/KJ/telexTrain/switch/light.png");
      background-repeat: no-repeat;
      position: absolute;
      height: 20px;
      width: 20px;
      right: -5px;
      top: 7px;
    }
    .lightActive{
      background-image: url("../../../assets/KJ/telexTrain/switch/lightActive.png");
    }
    .hand{
      height: 62px;
      width: 62px;
      background-image: url("../../../assets/KJ/telexTrain/hand.png");
    }
    .pormptBg{
      background: #0a1a35;
      width: max-content;
      padding: 5px;
      border-radius: 5px;
      font-size: 12px;
    }
    .pormptAreaBox{
      position: absolute;bottom: 80px;display: flex;flex-direction: column;align-items: center;z-index: 99;
    }
    .lineBg{
      background: #0a9a89;
      height: 10px;
      width: 1px;
    }
    .l1{
      background: #2b0505;
      box-shadow: inset 0 -5px 10px -5px rgb(255 0 0);
      border-radius: 3px;
    }
    .l2{
      background: #051d1d;
      border-radius: 3px;
      box-shadow: inset 0 -5px 10px -5px rgb(0 255 52);
    }
    .l3{
      background: #2d1603;
      border-radius: 3px;
      box-shadow: inset 0 -5px 10px -5px rgb(255 138 0);
    }
    .l4{
      background: #212300;
      border-radius: 3px;
      position: relative;
      box-shadow: inset 0 -5px 10px -5px #ffd100;
    }

    .rsplit::before{
      content: '';
      position: absolute;
      width: calc(50% + 1px);
      height: 1px;
      background: #00ffdc;
      bottom: 0px;
      left: 0px;
    }
    .rsplit::after{
      content: '';
      position: absolute;
      width: 1px;
      height: 100%;
      background:  #00ffdc;
      left: 0px;
      top: 0px;
    }
    .rsplitLast::before{
      content: '';
      position: absolute;
      width: 1px;
      height: 100%;
      background:  #00ffdc;
      left: 0px;
      top: 0px;
    }
    .r4{
      background: #212300;
      border-radius: 3px;
      position: relative;
      box-shadow: inset 0 -5px 10px -5px #ffd100;
    }
    .r3{
      background: #2d1603;
      border-radius: 3px;
      box-shadow: inset 0 -5px 10px -5px rgb(255 138 0);
    }
    .r2{
      background: #051d1d;
      border-radius: 3px;
      box-shadow: inset 0 -5px 10px -5px rgb(0 255 52);
    }
    .r1{
      background: #2b0505;
      box-shadow: inset 0 -5px 10px -5px rgb(255 0 0);
      border-radius: 3px;
    }

    .container{
      width: 100%;height: 476px;
      position: relative;
      margin: 0 auto;
      /*padding-top: 30px;*/
      background-image: url("../../../assets/KJ/telexTrain/keyboard-left.png"),url("../../../assets/KJ/telexTrain/keyboard-right.png");
      background-repeat: no-repeat,no-repeat;
      background-position: top left , top right;
      display: flex;
      align-items: center;
      justify-content: center;
      position: relative;
      color: #d6e4ff;
      animation: keyBoard 1s;
    }
    .center{
      width: calc(100% - 860px);
      height: 100%;
      position: absolute;
      left: 430px;
      background:url("../../../assets/KJ/telexTrain/keyboard-center.png") repeat;
    }
    .keybox{
      padding: 5px 6px 10px 6px;
      background-image:radial-gradient(closest-side at 5px 5px, #466897,rgba(0,0,0,0)),
      radial-gradient(closest-side at 90% 5px, #466897,rgba(0,0,0,0)),
      radial-gradient(closest-side at 5px 90%, #0e1c39,rgba(0,0,0,0)),
      radial-gradient(closest-side at 90% 90%, #0e1c39,rgba(0,0,0,0)),
      linear-gradient(0deg,#152443,#385887);
      /*margin: 5px;*/
      box-shadow: 0 0 5px #1553c4;
      border: 1px solid #091731;
      border-radius: 5px;
      position: relative;
      transition: all .1s;
    }
    .key{
      box-shadow: 0 0 10px #091731;
      height: 50px;
      width: 50px;
      color: #d6e4ff;
      font-weight: bold;
      border: 1px solid #365086;
      /*border-image: linear-gradient( red,#32466f) 100 5;*/
      display: flex;
      align-items: center;
      justify-content: center;
      cursor: pointer;
      background-image: linear-gradient(0deg,#203d6a,#0d1e42);
      border-radius: 5px;
    }
    .key2{
      flex-direction: column;
    }
    .activeKey,.targetKey{
      box-shadow: 0 0 10px #736d61;
      background-image: radial-gradient(closest-side at 5px 5px, #7a7366, rgba(0, 0, 0, 0)), radial-gradient(closest-side at 90% 5px, #7a7366, rgba(0, 0, 0, 0)), radial-gradient(closest-side at 5px 90%, #2d2418, rgba(0, 0, 0, 0)), radial-gradient(closest-side at 90% 90%, #2d2418, rgba(0, 0, 0, 0)), linear-gradient(0deg, #3c2e1a, #6f6a5f);
      border: solid 1px #292116;
    }
    .activeKey .key{
      background-image: linear-gradient(0deg,#0060f3,#002166);
      /*border-image: linear-gradient( #394c74,#375490) 5 5;*/
      border-color: #385081;
    }
    .targetKey .key{
      background-image: linear-gradient(0deg, #746e62, #383e38);
      border-color: #726d60;
      color: #fbb354;
    }
    .errorKey{
      box-shadow: 0 0 10px #ff0000;
      background-image:radial-gradient(closest-side at 5px 5px, #923033,rgba(0,0,0,0)),
      radial-gradient(closest-side at 90% 5px, #923033,rgba(0,0,0,0)),
      radial-gradient(closest-side at 5px 90%, #49120e,rgba(0,0,0,0)),
      radial-gradient(closest-side at 90% 90%, #49120e,rgba(0,0,0,0)),
      linear-gradient(0deg,#6c1605,#931606);
      border-color: #310b09;
    }
    .errorKey .key{
      background-image: linear-gradient(0deg,#420d0d,#b12525);
      /*border-image: linear-gradient( #965656,#ae1414) 5 5;*/
      border-color: #a13939;
    }
    .successKey{
      box-shadow: 0 0 10px #0a9a89;
      background-image:radial-gradient(closest-side at 5px 5px, #0ca77c,rgba(0,0,0,0)),
      radial-gradient(closest-side at 90% 5px, #0e9295,rgba(0,0,0,0)),
      radial-gradient(closest-side at 5px 90%, #023125,rgba(0,0,0,0)),
      radial-gradient(closest-side at 90% 90%, #023125,rgba(0,0,0,0)),
      linear-gradient(0deg,#033c2c,#06938b);
      border-color: #03311d;
    }
    .successKey .key{
      background-image: linear-gradient(0deg,#0aaeaf,#006666);
      /*border-image: linear-gradient( #965656,#ae1414) 5 5;*/
      border-color: #2f9596;
    }
    .pormptBox{
      position: absolute;bottom: 80px;right:50px;z-index: 99;
      background-image: url("../../../assets/KJ/telexTrain/left-top.png"),
      url("../../../assets/KJ/telexTrain/left-bottom.png"),
      url("../../../assets/KJ/telexTrain/right-top.png"),
      url("../../../assets/KJ/telexTrain/right-bottom.png"),
      linear-gradient(0deg, rgb(1 2 1 / 50%), rgb(19 32 15 / 50%));;
      background-position: left top,left bottom,right top,right bottom;
      background-repeat: no-repeat;
      width: max-content;
      border-radius: 8px;
      box-shadow: 0 0 10px rgba(250,170,66,0.5)  inset;
      color: #faaa42;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 20px 10px;
    }
  }
  .areahand{
    transform: scale(0.9);
  }
  @keyframes keyBoard {
    0%{
      transform: rotateX(0deg);
    }
    20%{
      transform: rotateX(-90deg);
    }
    100%{
      transform: rotateX(0deg);
    }
  }


  @media (max-width: 1740px) {
    .keys {
      transform: scale(.9);
    }
    .center {
      width: calc(100% - 860px + 44px);
      left: calc(430px - 22px);
      background-size: 100% 100%;
    }
  }
  @media (max-width: 1600px) {
    .keys {
      transform: scale(.8);
    }
    .center {
      width: calc(100% - 860px + 86px);
      left: calc(430px - 43px);
      background-size: 100% 100%;
    }
  }
  @media (max-width: 1450px) {
    .keys {
      transform: scale(.7);
    }
    .center {
      width: calc(100% - 860px + 130px);
      left: calc(430px - 65px);
      background-size: 100% 100%;
    }
  }
  @media (max-width: 1300px) {
    .keys {
      transform: scale(.6);
    }
    .center {
      width: calc(100% - 860px + 174px);
      left: calc(430px - 87px);
      background-size: 100% 100%;
    }
  }
  @media (max-width: 1120px) {
    .keys {
      transform: scale(.5);
    }
    .center {
      width: calc(100% - 860px + 216px);
      left: calc(430px - 108px);
      background-size: 100% 100%;
    }
  }
</style>