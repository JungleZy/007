<template>
  <verify-license>
    <div class="version" v-if="route.name==='Login'&&ipc">
      {{dataChecked?'单机版':'局域网版'}}
    </div>
    <div :class="{ 'w-full h-full overflow-hidden ': true, cool: !cool ,
    HJ:interfaceStyle=='HJ',HJJ:interfaceStyle=='HJJ',LJ:interfaceStyle=='LJ',GD:interfaceStyle==='GD'}">
      <a-config-provider :locale="locale">
        <router-view/>
      </a-config-provider>
    </div>
    <net-setting v-show="isOpen" ref="childRef" @changeChecked="changeChecked"/>
    <GlobalStyle></GlobalStyle>
    <NipPagePermission />
    <!--    <net-status/>-->
  </verify-license>
</template>

<script>
import zhCN from 'ant-design-vue/es/locale/zh_CN'

export default {
  data() {
    return {
      locale: zhCN
    }
  }
}
</script>
<script setup>
import {ref, onMounted, createVNode, provide,watch} from 'vue'
import {ExclamationCircleOutlined} from '@ant-design/icons-vue'
import {Modal} from 'ant-design-vue'
import {useRoute, useRouter} from 'vue-router'
import VerifyLicense from './components/common/VerifyLicense.vue'
import NetSetting from './components/common/NetSetting.vue'
// import NetStatus from './components/common/NetStatus.vue'
import useFontSize from "./common/mixin/useFontSize.js";
import GlobalStyle from "./components/GlobalStyle.vue";
// import confim from "./components/model/confirm";
import {userLoginOut} from "./common/api/UserApi";
import {ipcRenderer, ipcApi} from './electron/index'
import NipPagePermission from "./components/common/NipPagePermission.vue";

// Modal.confirm = confim
// Modal.error = confim

// // 在 main.js 或 App.vue 中添加<br/>
// window.addEventListener('error', (event) => {console.error('全局错误:', event.error)})
// window.addEventListener('unhandledrejection', (event) => {console.error('未处理的 Promise 拒绝:', event.reason)})
// setInterval(() => {console.log('Memory:', performance.memory ? performance.memory.usedJSHeapSize / 1048576 : 'N/A', 'MB')}, 1000)

const router = useRouter()
const route = useRoute()
const ipc = ref(ipcRenderer.isEE)
const isOpen = ref(false)
const interfaceStyle = window.interfaceStyle
const childRef = ref(null);

// const isClear = localStorage.getItem('code')
// if(isClear==null){
//   window.indexedDB.deleteDatabase('WisdomJ233')
//   localStorage.setItem('code',1)
//   window.location.reload()
// }


const cool = ref(false)
const wpmTOmm = ref(true) //是否使用码/分
const routeName = ref('')
const dataChecked = ref()
onMounted(() => {
  if (childRef.value) {
    dataChecked.value = childRef.value.dataChecked;
    console.log(dataChecked.value)
  }
  localforage.getItem('cool').then((e) => {
    cool.value = e
  })
  if (window.localStorage.getItem('fs')) {
    let body = document.getElementsByClassName('wisdom')[0]
    body.style.fontSize = window.localStorage.getItem('fs') * 2 + 14 + 'px'
  }
})
const changeChecked = (data)=>{
  console.log(route)
  dataChecked.value = data
}
const closeAnimation = () => {
  cool.value = !cool.value
  localforage.setItem('cool', cool.value)
}
const openSettingWindow = () => {
  isOpen.value = !isOpen.value
}
provide('openSettingWindow', openSettingWindow)
provide('closeAnimation', closeAnimation)
provide('isOpen', isOpen)
provide('cool', cool)
provide('wpmTOmm', wpmTOmm)
</script>
<style lang="less">
  .version{
    position: absolute;left: calc(50% - 50px);z-index: 999;top: 0;
    width: 100px;text-align: center;
    font-weight: bold;
  }
.maskBG {
  width: 100%;
  height: 100%;
  background: rgba(0, 0, 0, 0.6);
  position: absolute;
  z-index: 2;
  top: 0;
}
  *{
    //阻止系统高对比度的影响
    forced-color-adjust: none;
  }
* {
  scrollbar-face-color: red !important;
}

@keyframes op {
  0% {
    opacity: 0;
  }
  100% {
    opacity: 1;
  }
}

video {
  /*transition: all 5s;*/
  animation: op 1s linear;
}


.top-close-zoom {
  position: fixed;
  top: 0;
  right: 0;
  z-index: 9;
  font-size: 22px;
  height: 50px;
}

.top-close-zoom .item,
.top-close-zoom .item-red {
  z-index: 9;
  font-size: 22px;
  cursor: pointer;
  width: 52px;
  height: 52px;
  transition: all 0.5s;
}

.top-close-zoom .item-red:hover {
  color: red;
}

.top-close-zoom .item:hover {
  color: green;
}

.HJ, .GD {
  .setting-modal {
    width: 250px;
    position: fixed;
    z-index: 10;
    right: 40px;
    top: 65px;
  }

  .setting-modal .content {
    background: #0c1c3c;
    color: #fff;
    border: 2px solid #063c7e;
    border-radius: 8px;
    position: relative;
  }

  .setting-modal .content:after {
  }

  .iconItemOne {
    background-image: url('./assets/HJ/main/ico2_03.png');
  }

  .iconItemOne:hover {
    background-image: url('./assets/HJ/main/icohover_03.png');
  }

  .iconItemTwo {
    background-image: url('./assets/HJ/main/ico2_06.png');
  }

  .iconItemTwo:hover {
    background-image: url('./assets/HJ/main/icohover_06.png');
  }

  .iconItemThree {
    background-image: url('./assets/HJ/main/ico2_09.png');
  }

  .iconItemThree:hover {
    background-image: url('./assets/HJ/main/icohover_09.png');
  }

  .iconItemFour {
    background-image: url('./assets/HJ/main/ico2_08.png');
  }

  .iconItemFour:hover {
    background-image: url('./assets/HJ/main/icohover_08.png');
  }

  .iconItemFive {
    background-image: url('./assets/HJ/main/ico2_11.png');
  }

  .iconItemFive:hover {
    background-image: url('./assets/HJ/main/icohover_11.png');
  }

  .iconItemSix {
    background: url('./assets/HJ/main/ico2_13.png') no-repeat center;
  }

  .iconItemSix:hover {
    background: url('./assets/HJ/main/icohover_13.png') no-repeat center;
  }

  .iconSingOut {
    background-image: url('./assets/HJ/main/singout.png');
  }

  .iconSingOut:hover {
    background-image: url('./assets/HJ/main/singout_hover.png');
  }
}

.HJJ {
  .iconItemOne {
    background-image: url('./assets/HJJ/main/ico2_03.png');
  }

  .iconItemOne:hover {
    background-image: url('./assets/HJJ/main/icohover_03.png');
  }

  .iconItemTwo {
    background-image: url('./assets/HJJ/main/ico2_06.png');
  }

  .iconItemTwo:hover {
    background-image: url('./assets/HJJ/main/icohover_06.png');
  }

  .iconItemThree {
    background-image: url('./assets/HJJ/main/ico2_09.png');
  }

  .iconItemThree:hover {
    background-image: url('./assets/HJJ/main/icohover_09.png');
  }

  .iconItemFour {
    background-image: url('./assets/HJJ/main/ico2_08.png');
  }

  .iconItemFour:hover {
    background-image: url('./assets/HJJ/main/icohover_08.png');
  }

  .iconItemFive {
    background-image: url('./assets/HJJ/main/ico2_11.png');
  }

  .iconItemFive:hover {
    background-image: url('./assets/HJJ/main/icohover_11.png');
  }

  .iconItemSix {
    background: url('./assets/HJ/main/ico2_13.png') no-repeat center;
  }

  .iconItemSix:hover {
    background: url('./assets/HJ/main/icohover_13.png') no-repeat center;
  }

  .iconSingOut {
    background-image: url('./assets/HJJ/main/singout.png');
  }

  .iconSingOut:hover {
    background-image: url('./assets/HJJ/main/singout_hover.png');
  }
}

.LJ {
  .iconItemOne {
    background-image: url('./assets/LJ/main/ico2_03.png');
  }

  .iconItemOne:hover {
    background-image: url('./assets/LJ/main/icohover_03.png');
  }

  .iconItemTwo {
    background-image: url('./assets/LJ/main/ico2_06.png');
  }

  .iconItemTwo:hover {
    background-image: url('./assets/LJ/main/icohover_06.png');
  }

  .iconItemThree {
    background-image: url('./assets/LJ/main/ico2_09.png');
  }

  .iconItemThree:hover {
    background-image: url('./assets/LJ/main/icohover_09.png');
  }

  .iconItemFour {
    background-image: url('./assets/LJ/main/ico2_08.png');
  }

  .iconItemFour:hover {
    background-image: url('./assets/LJ/main/icohover_08.png');
  }

  .iconItemFive {
    background-image: url('./assets/LJ/main/ico2_11.png');
  }

  .iconItemFive:hover {
    background-image: url('./assets/LJ/main/icohover_11.png');
  }

  .iconItemSix {
    background: url('./assets/HJ/main/ico2_13.png') no-repeat center;
  }

  .iconItemSix:hover {
    background: url('./assets/HJ/main/icohover_13.png') no-repeat center;
  }

  .iconSingOut {
    background-image: url('./assets/LJ/main/singout.png');
  }

  .iconSingOut:hover {
    background-image: url('./assets/LJ/main/singout_hover.png');
  }
}
</style>
