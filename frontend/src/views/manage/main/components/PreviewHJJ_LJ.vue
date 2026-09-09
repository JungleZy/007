<template>
  <div class="w-full h-full overflow-hidden preview-page relative">
    <img :src="home2" class="homeBg" style="z-index: -10" v-if="routeMatched == 3 && !cool" alt=""/>
    <img :src="otherBg" class="homeBg" style="z-index: -10" v-if="routeMatched !== 3 && !cool" alt=""/>
    <div class="w-full top overflow-hidden relative titleText" v-if="routeName !== 'Dashboard'"
         style="min-width: 1490px">
      <div class="w-full h-full top-main layout-left-top absolute">
        <img :src="logoTitle" class="logoText absolute"/>
        <div class="title_left h-full relative">
          <div class="goback absolute" @click="goBack">
            <img :src="goback" alt=""/>
          </div>
          <div class="menu absolute layout-left-top">
            <div class="item" @click="handleBackDashboard">
              <div class="iconH"></div>
              <div class="homeTitleText">主&nbsp;页</div>
            </div>
          </div>
          <div class="explain absolute layout-left-bottom" @click="noOff = !noOff">使用说明</div>
        </div>
        <div class="title_center h-full layout-side">
          <div class="center h-full"></div>
        </div>
        <div class="title_right h-full relative">
          <a-popover class="min" v-model:visible="isShow" trigger="click" overlayClassName="noPadding">
            <template #content>
              <div class="settingBox">
                <div class="selectitem" v-if="userRole.id != '2'" @click="jumpSystemManage">
                  <a-tooltip placement="left">
                    <template #title>
                      <div style="font-size: 12px">系统管理</div>
                    </template>
                    <div class="item layout-center systemSetIcon"></div>
                  </a-tooltip>
                </div>
                <div class="border-top-style selectitem" @click="reloadWindow">
                  <a-tooltip placement="left">
                    <template #title>
                      <div style="font-size: 12px">重载页面</div>
                    </template>
                    <div class="item layout-center iconItemOne"></div>
                  </a-tooltip>
                </div>
                <div class="border-top-style selectitem" v-if="ipc" @click="openSetting">
                  <a-tooltip placement="left">
                    <template #title>
                      <div style="font-size: 12px">网络设置</div>
                    </template>
                    <div id="openSettingWindow" class="item layout-center iconItemTwo"></div>
                  </a-tooltip>
                </div>
                <!--                <div class="border-top-style selectitem" @click="closeAnimation">-->
                <!--                  <a-tooltip placement="left" >-->
                <!--                    <template #title>-->
                <!--                      <div style="font-size: 12px;">{{cool?'关闭特效':'开启特效'}}</div>-->
                <!--                    </template>-->
                <!--                    <div v-if="cool" class="item-red layout-center iconItemThree"></div>-->
                <!--                    <div v-else  class="item-red layout-center iconItemFour"></div>-->
                <!--                  </a-tooltip>-->
                <!--                </div>-->
                <div class="border-top-style selectitem" @click="handleLoginOut">
                  <a-tooltip placement="left">
                    <template #title>
                      <div style="font-size: 12px">退出登录</div>
                    </template>
                    <div class="item-red layout-center iconSingOut"></div>
                  </a-tooltip>
                </div>
                <div class="border-top-style selectitem" v-if="ipc" @click="closeWindow">
                  <a-tooltip placement="left">
                    <template #title>
                      <div style="font-size: 12px">关闭软件</div>
                    </template>
                    <div class="item-red layout-center iconItemFive"></div>
                  </a-tooltip>
                </div>
              </div>
            </template>
            <div class="ipc absolute" @click="setting">
              <img :src="settingPic" alt=""/>
            </div>
          </a-popover>
        </div>
      </div>
      <div class="message absolute"></div>
      <div class="userInfo layout-left-center" style="cursor: pointer" @click="openEditPasswordModel">
        <img :src="fileUrl + userInfo.userImg" v-if="userInfo.userImg && userInfo.userImg != ''" class="avatarImg"/>
        <img :src="pagMan" v-else-if="userInfo.userSex == 1" class="avatarImg"/>
        <img :src="pagWoman" v-else class="avatarImg"/>
        <span class="nobr" :title="userInfo.userName" style="padding-left: 10px; max-width: 100px">{{
						userInfo.userName
					}}</span>
      </div>
      <NipSerial/>
    </div>
    <div class="w-full content relative"
         :style="{ height: 'calc(100% - ' + (routeName !== 'Dashboard' ? 68 : 0) + 'px)', paddingTop: (routeName !== 'Dashboard' ? 5 : 0) + 'px' }"
         :class="{ isHasBackground: isShowBackground }" style="top: -14px">
      <router-view/>
    </div>
    <a-modal v-model:visible="noOff" :maskClosable="false" :footer="null" width="100%" wrapClassName="full-modal"
             class="noBottomBg">
      <div style="height: 100%">
        <Instructions></Instructions>
      </div>
    </a-modal>
    <a-modal
      :destroyOnClose="true"
      :width="960"
      class="init_modal_style footer-border-none"
      v-model:visible="editPasswordModel"
      @cancel="cancelEditPasswordModel"
    >
      <template #title>
        <strong>个人中心</strong>
      </template>
      <template #footer>
        <div> </div>
      </template>

      <Personal/>
    </a-modal>

  </div>
</template>

<script>
  export default {
    name: "PreviewHJJ"
  }
</script>
<script setup>
  import NipSerial from "../../../../components/common/NipSerial.vue";
  import Personal from '../../../../components/personal/Personal.vue'
  //火箭军
  import pagManHJJ from '../../../../assets/HJJ/pag-avatar-woman.png'
  import pagWomanHJJ from '../../../../assets/HJJ/pag-avatar-man.png'
  import home2HJJ from '../../../../assets/HJJ/main/home2.png'
  import otherBgHJJ from '../../../../assets/HJJ/main/otherBg.png'
  import ico_state_wsHJJ from '../../../../assets/HJJ/ico/ico-state-ws.png'
  import ico_state_ws_onHJJ from '../../../../assets/HJJ/ico/ico-state-ws-on.png'
  import ico_state_devHJJ from '../../../../assets/HJJ/ico/ico-state-dev.png'
  import ico_state_dev_onHJJ from '../../../../assets/HJJ/ico/ico-state-dev-on.png'
  import logoTitleHJJ from '../../../../assets/HJJ/home/title-1.png'
  import addressHJJ from '../../../../assets/HJJ/home/addres.png'
  import gobackHJJ from '../../../../assets/HJJ/home/goBack.png'
  import settingPicHJJ from '../../../../assets/HJJ/home/setting.png'
  //陆军
  import pagManLJ from '../../../../assets/LJ/pag-avatar-woman.png'
  import pagWomanLJ from '../../../../assets/LJ/pag-avatar-man.png'
  import home2LJ from '../../../../assets/LJ/main/home2.jpg'
  import otherBgLJ from '../../../../assets/LJ/main/otherBg.png'

  import logoTitleLJ from '../../../../assets/LJ/home/title-1.png'
  import addressLJ from '../../../../assets/LJ/home/addres.png'
  import gobackLJ from '../../../../assets/LJ/home/goBack.png'
  import settingPicLJ from '../../../../assets/LJ/home/setting.png'
  //陆军
  import pagManKJ from '../../../../assets/KJ/pag-avatar-woman.png'
  import pagWomanKJ from '../../../../assets/KJ/pag-avatar-man.png'
  import home2KJ from '../../../../assets/KJ/main/home2.jpg'
  import otherBgKJ from '../../../../assets/KJ/main/otherBg.png'

  import logoTitleKJ from '../../../../assets/KJ/home/title-1.png'
  import addressKJ from '../../../../assets/KJ/home/addres.png'
  import gobackKJ from '../../../../assets/KJ/home/goBack.png'
  import settingPicKJ from '../../../../assets/KJ/home/setting.png'

  //国动
  import home2GD from '../../../../assets/GD/main/home2.jpg'
  import logoTitleGD from '../../../../assets/GD/home/title-1.png'
  import otherBgGD from '../../../../assets/GD/main/otherBg.jpg'
  import useTraffic from '../../../../common/mixin/useTraffic.js'
  import {useRoute, useRouter, onBeforeRouteUpdate} from 'vue-router'
  import {useStore} from 'vuex'
  import getBackByRouter from '../js/getBackByRouter.js'
  import {createVNode, ref, onBeforeMount, watch, onMounted, nextTick, inject, provide} from 'vue'
  import {Modal, message} from 'ant-design-vue'
  import {
    ExclamationCircleOutlined,
    CloseOutlined,
    GlobalOutlined,
    ReloadOutlined,
    PauseCircleOutlined,
    PlayCircleOutlined
  } from '@ant-design/icons-vue'
  import {Ws} from '../../../../common/ws/Ws'
  import messageWebSocket from '../../../../common/ws/MessageWebSocket.js'
  import routeConfig from '../js/routeConfig.js'
  import Instructions from '../../../../components/instructions/instructions.vue'
  import {ipcRenderer, ipcApi} from '../../../../electron/index'
  import {changePassword} from "../../../../common/api/UserApi";
  import {closeSession} from '../../../../common/session/logout.js'

  let pagMan, pagWoman, home2, otherBg, logoTitle,
      goback, settingPic
  const interfaceStyle = window.interfaceStyle
  if (interfaceStyle === "HJJ") {
    pagMan = pagManHJJ
    pagWoman = pagWomanHJJ
    home2 = home2HJJ
    otherBg = otherBgHJJ
    logoTitle = logoTitleHJJ
    goback = gobackHJJ
    settingPic = settingPicHJJ
  } else if (interfaceStyle === "LJ") {
    pagMan = pagManLJ
    pagWoman = pagWomanLJ
    home2 = home2LJ
    otherBg = otherBgLJ
    logoTitle = logoTitleLJ
    goback = gobackLJ
    settingPic = settingPicLJ
  }else if (interfaceStyle === "KJ"){
    pagMan = pagManKJ
    pagWoman = pagWomanKJ
    home2 = home2KJ
    otherBg = otherBgKJ
    logoTitle = logoTitleKJ
    goback = gobackKJ
    settingPic = settingPicKJ
  }else {
    pagMan = pagManLJ
    pagWoman = pagWomanLJ
    home2 = home2GD
    otherBg = otherBgGD
    logoTitle = logoTitleGD
    goback = gobackLJ
    settingPic = settingPicLJ
  }

  const cool = inject('cool')
  const router = useRouter()
  const route = useRoute()
  const systemPath = ref('')
  const store = useStore()
  const userRole = ref({})
  const userInfo = ref({})
  const routeName = ref('')
  const noOff = ref(false)
  const routeMatched = ref('')
  const fileUrl = ref('')
  const linkWsIndex = ref(0)

  const tUrl = ref('')
  tUrl.value = window.fileUrl + '/006/TrafficService.exe'
  const {wsOnline, devOnline} = useTraffic()
  const ipc = ref(ipcRenderer.isEE)
  let {isShowBackground} = getBackByRouter()
  const openSettingWindow = inject('openSettingWindow')
  const isShow = ref(false)
  const openSetting = () => {
    isShow.value = false
    openSettingWindow()
  }
  const {routePaths, lineDevicePaths} = routeConfig()
  const editPasswordModel = ref(false)


  const openEditPasswordModel = () => {
    editPasswordModel.value = true
  }
  const cancelEditPasswordModel = () => {
    editPasswordModel.value = false
  }


  onMounted(() => {
    userRole.value = JSON.parse(localStorage.getItem('userRole'))
    userInfo.value = JSON.parse(window.localStorage.getItem('userInfo'))
    let routerData = JSON.parse(window.localStorage.getItem('userRouter'))
    fileUrl.value = window.fileUrl
    Ws.getInstance().run().then()
    if (route.matched.length === 2) {
      router.push('/preview/dashboard')
    } else {
      router.push(route.fullPath)
    }

    routerData.forEach(r => {
      if (r.name === 'SystemManage') systemPath.value = r
    })

  })
  const setting = () => {
    isShow.value = true
  }

  const jumpSystemManage = () => {
    router.push(`/preview/${systemPath.value.path}/${systemPath.value.children[0].path}`)
  }
  const closeAnimation = () => {
    window.cool = !window.cool
    cool.value = window.cool
    localStorage.setItem('cool', cool.value)
  }
  nextTick(() => {
    routeName.value = route.name
    routeMatched.value = route.matched.length
  })
  watch(route, () => {
    routeName.value = route.name
    routeMatched.value = route.matched.length
    // if(route.matched.length==5){
    //   router.push(route.matched[2].path)
    // }
  })

  window.addEventListener('click', () => {
    if (
        routeMatched.value > 2 &&
        lineDevicePaths.value.indexOf(route.path) > -1 &&
        linkWsIndex.value === 0
    ) {
      linkWsIndex.value++
      // messageWebSocket('reset')
    }
  })

  const handleBackDashboard = () => {
    router.push('/preview/dashboard')
    sessionStorage.setItem('activefist', 0)
  }
  const handBackMenu = () => {
    router.push(route.matched[2].path)
  }
  const goBack = () => {
    if(route.name==='electronKeyZuXunTrain'){
      router.push({
        path:route.matched[3].path+"/electronKeyZuXunList"
      })
    }else if(route.name==='HandkeyZuXunTrain'){
      router.push({
        path:route.matched[3].path+"/handkeyZuXunList"
      })
    }else if(route.name==='datagramZuXunTrain'){
      router.push({
        path:route.matched[3].path+"/datagramZuXunList"
      })
    } else if (route.path.indexOf('trainScore') > -1) {
      router.push(route.matched[4].path + '/telexPost')
    } else if (route.path.indexOf('datagramTrainScore') > -1) {
      router.push(route.matched[4].path + '/datagramPost')
    } else if (route.path.indexOf('patExamTrainScore') > -1) {
      router.push(route.matched[4].path + '/examPostList')
    } else if (route.path.indexOf('patTrainScore') > -1) {
      router.push(route.matched[4].path + '/handKeyPostJob')
    } else if (route.path.indexOf('receivePostScore') > -1) {
      router.push(route.matched[4].path + '/receivePostPractise')
    }else {
      router.go(-1)
    }
  }
  onBeforeMount(() => {
    handlePermissions(route.meta.permissions)
  })
  onBeforeRouteUpdate(to => {
    handlePermissions(to.meta.permissions)
  })
  const handlePermissions = meta => {
    store.commit('setPermissions', meta)
  }
  const reloadWindow = () => {
    window.location.reload(true)
  }
  const closeWindow = () => {
    Modal.confirm({
      title: () => '是否确认退出多功能报务综合训练系统？',
      icon: () => createVNode(ExclamationCircleOutlined),
      onOk() {
        ipcRenderer.ipc.invoke(ipcApi.ipcApiRoute.closeApp)
      },
      onCancel() {
      }
    })
  }

  const handleLoginOut = () => {
    Modal.confirm({
      class: 'init_modal_style',
      content: '是否退出当前账号？',
      icon: () => createVNode(ExclamationCircleOutlined),
      okType: 'danger',
      okText: () => '退出',
      cancelText: () => '取消',
      maskClosable: true,
      onOk: () => {
        closeSession(router)
      }
    })
  }
  provide('noOff', noOff)
</script>

<style scoped lang="less">
  .homeTitle {
    max-height: 577px;
    max-width: 1916px;
    height: 100%;
    width: 100%;
    position: absolute;
    top: 14%;
    left: 0;
  }

  .homeBg {
    height: 100%;
    width: 100%;
    z-index: -2;
    position: absolute;
    top: 0;
    left: 0;
  }

  .myvideo {
    height: 100%;
    width: 100%;
    z-index: -1;
    position: absolute;
    top: 0;
    left: 0;
    object-fit: fill;
  }

  .avatarImg {
    height: 28px;
    width: 28px;
    border-radius: 50%;
  }


  .preview-page .top {
    height: 82px;
  }

  .preview-page .top .top-main {
    top: -14px;
    animation: growttb 0.3s linear;
  }

  .preview-page .bottom {
    height: 30px;
  }

  @keyframes growttb {
    0% {
      top: -84px;
    }
    100% {
      top: 0;
    }
  }

  @keyframes growttbBack {
    0% {
      top: 0;
    }
    100% {
      top: -84px;
    }
  }

  @keyframes shine {
    0% {
      background-position-x: 400%;
    }
    50% {
      background-position-x: 0%;
    }
    100% {
      background-position-x: -400%;
    }
  }

  .title_left,
  .title_right {
    width: 400px;
  }

  .selectitem {
    /*padding:10px 5px;*/
    cursor: pointer;
    transition: all 0.5s;
  }

  .selectitem .item,
  .selectitem .item-red {
    z-index: 9;
    cursor: pointer;
    width: 60px;
    height: 60px;
    transition: all 0.5s;
  }

  .settingBox {
    color: rgb(192 230 255);
    font-size: 22px;
  }

  .userInfo {
    position: absolute;
    height: 45px;
    line-height: 45px;
    right: 7%;
    top: 8px;
  }

  .explain {
    padding-left: 44px;
    width: 120px;
    height: 25px;
    left: 188px;
    top: 42px;
    font-size: 12px;
    color: #bfcde0;
    line-height: 28px;
    cursor: pointer;
  }

  .HJJ {
    .selectitem:hover {
      background: #3c4a67;
    }

    .border-top-style {
      border-top: 1px solid;
      border-image: linear-gradient(90deg, rgba(24, 45, 86, 0.7) 15%, rgba(69, 92, 138, 1) 50%, rgba(31, 46, 75, 0.7) 85%) 2 2 2 2;
    }

    .isHasBackground {
      background-color: rgba(6, 6, 6, 0.1);
    }

    .preview-page .top .top-main .shine {
      background: linear-gradient(-45deg, transparent 40%, rgba(205, 213, 128, 0.8) 5%, transparent 41%);
      background-size: 600% 100%;
      -webkit-animation: shine 30s infinite;
      animation: shine 30s infinite;
      -webkit-animation-delay: 0s;
      animation-delay: 0s;
      -webkit-animation-timing-function: linear;
      animation-timing-function: linear;
    }

    .titleText .title_left {
      background: url('../../../../assets/HJJ/home/bg.png');
    }

    .bread {
      position: absolute;
      width: max-content;
      bottom: 0px;
      left: 110px;
      color: #718db1;
      font-size: 12px;
    }

    .title_left .menu {
      width: 100px;
      height: 42px;
      left: 90px;
      font-size: 15px;
      top: 10px;
    }

    .title_left .menu .item {
      height: 100%;
      width: 100px;
      line-height: 42px;
      text-align: center;
      color: #8eaed7;
      padding-top: 5px;
      cursor: pointer;
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: center;
    }

    .homeTitleText {
      width: 100%;
      line-height: 17px;
      padding-right: 2px;
      color: #6f7d96;
      font-weight: bold;
      font-size: 17px;
    }

    .title_left .menu .item:hover .homeTitleText {
      color: #e6ac71 !important;
    }

    .title_left .menu .item:hover::before {
      content: '';
      position: absolute;
      width: 96px;

      height: 47px;
      background: url('../../../../assets/HJJ/home/itemHover.png');
      top: 10px;
    }

    .title_left .menu .item::after {
      content: '';
      position: absolute;
      width: 40px;
      height: 52px;
      background: url('../../../../assets/HJJ/home/spilt.png');
      top: 6px;
      left: 80px;
    }

    .iconH {
      width: 30px;
      height: 30px;
      margin-right: 5px;
      background: url('../../../../assets/HJJ/home/home.png');
    }

    .title_left .menu .item .iconM {
      width: 30px;
      height: 30px;
      margin-right: 5px;
      background: url('../../../../assets/HJJ/home/menu.png');
    }

    .title_left .menu .item:hover {
      color: white;
    }

    .title_left .menu .item:hover .iconH {
      background: url('../../../../assets/HJJ/home/home_hover.png');
    }

    .title_left .menu .item:hover .iconM {
      background: url('../../../../assets/HJJ/home/menu_hover.png');
    }

    .title_center {
      flex: 1;
    }

    .title_center .center {
      background: url('../../../../assets/HJJ/home/center.png');
      background-size: 100% 100%;
      width: 100%;
    }

    .titleText .title_right {
      background: url('../../../../assets/HJJ/home/bg.png');
    }

    .titleText .logoText {
      left: calc(50% - 210px);
      bottom: 18px;
      z-index: 9;
    }

    .message {
      top: 20px;
      left: 550px;
      font-size: 15px;
      height: 40px;
      line-height: 35px;
      color: #8eaed7;
    }

    .goback {
      background: url('../../../../assets/HJJ/home/gobackBg.png');
      height: 52px;
      width: 99px;
      top: 16px;
      left: 0px;
      cursor: pointer;
      display: flex;
      align-items: center;
      padding: 4px 0 0 15px;
    }

    .signOut {
      background: url('../../../../assets/HJJ/home/signOut.png');
      height: 44px;
      width: 77px;
      top: 8px;
      right: 15px;
      cursor: pointer;
    }

    .signOut:hover {
      background: url('../../../../assets/HJJ/home/signOut_hover.png');
    }

    .ipc {
      background: url('../../../../assets/HJJ/home/rightBg.png');
      height: 52px;
      width: 99px;
      top: 16px;
      right: 0px;
      cursor: pointer;
      transition: all 0.5s;
      display: flex;
      align-items: center;
      justify-content: flex-end;
      padding-right: 15px;
    }

    .iconItemOne {
      background: url('../../../../assets/HJJ/main/ico2_03.png') no-repeat center;
    }

    .iconItemOne:hover {
      background: url('../../../../assets/HJJ/main/icohover_03.png') no-repeat center;
    }

    .iconItemTwo {
      background: url('../../../../assets/HJJ/main/ico2_06.png') no-repeat center;
    }

    .iconItemTwo:hover {
      background: url('../../../../assets/HJJ/main/icohover_06.png') no-repeat center;
    }

    .iconItemThree {
      background: url('../../../../assets/HJJ/main/ico2_09.png') no-repeat center;
    }

    .iconItemThree:hover {
      background: url('../../../../assets/HJJ/main/icohover_09.png') no-repeat center;
    }

    .iconItemFour {
      background: url('../../../../assets/HJJ/main/ico2_08.png') no-repeat center;
    }

    .iconItemFour:hover {
      background: url('../../../../assets/HJJ/main/icohover_08.png') no-repeat center;
    }

    .iconItemFive {
      background: url('../../../../assets/HJJ/main/ico2_11.png') no-repeat center;
    }

    .iconItemFive:hover {
      background: url('../../../../assets/HJJ/main/icohover_11.png') no-repeat center;
    }

    .iconSingOut {
      background: url('../../../../assets/HJJ/main/singout.png') no-repeat center;
    }

    .iconSingOut:hover {
      background: url('../../../../assets/HJJ/main/singout_hover.png') no-repeat center;
    }

    .systemSetIcon {
      background: url('../../../../assets/HJJ/main/system-icon.png') no-repeat center;
    }

    .systemSetIcon:hover {
      background: url('../../../../assets/HJJ/main/system-icon-hover.png') no-repeat center;
    }

    .explain {
      background: url('../../../../assets/HJJ/main/sysm.png') no-repeat center;
    }

  }

  .LJ {
    .pageAll {
      padding: 0 10px 22px 10px;
      background-image: url("../../../../assets/LJ/leftBottom.png"), url("../../../../assets/LJ/rightBottom.png"), url("../../../../assets/LJ/bottom.png") !important;
      background-repeat: no-repeat;
      background-position: left bottom, right bottom, bottom;
    }

    .selectitem:hover {
      background: #2a4b49;
    }

    .border-top-style {
      border-top: 1px solid;
      border-image: linear-gradient(90deg, rgba(24, 86, 61, 0.7) 15%, rgb(69, 138, 84) 50%, rgba(31, 75, 37, 0.7) 85%) 2 2 2 2;
    }

    .myvideo {
      height: 100%;
      width: 100%;
      z-index: -1;
      position: absolute;
      top: 0;
      left: 0;
      object-fit: fill;
    }

    .avatarImg {
      height: 28px;
      width: 28px;
      border-radius: 50%;
    }

    .isHasBackground {
      background-color: rgba(6, 6, 6, 0.1);
    }

    .preview-page .top .top-main .shine {
      background: linear-gradient(-45deg, transparent 40%, rgba(205, 213, 128, 0.8) 5%, transparent 41%);
      background-size: 600% 100%;
      -webkit-animation: shine 30s infinite;
      animation: shine 30s infinite;
      -webkit-animation-delay: 0s;
      animation-delay: 0s;
      -webkit-animation-timing-function: linear;
      animation-timing-function: linear;
    }

    .titleText .title_left {
      background: url('../../../../assets/LJ/home/bg.png');
    }

    .bread {
      position: absolute;
      width: max-content;
      bottom: 0px;
      left: 110px;
      color: #718db1;
      font-size: 12px;
    }

    .title_left .menu {
      width: 100px;
      height: 42px;
      left: 90px;
      font-size: 15px;
      top: 10px;
    }

    .title_left .menu .item {
      height: 100%;
      width: 100px;
      line-height: 42px;
      text-align: center;
      color: #8eaed7;
      padding-top: 5px;
      cursor: pointer;
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: center;
    }

    .homeTitleText {
      width: 100%;
      line-height: 17px;
      padding-right: 2px;
      color: #698c91;
      font-weight: bold;
      font-size: 17px;
    }

    .title_left .menu .item:hover .homeTitleText {
      color: #e6ac71 !important;
    }

    .title_left .menu .item:hover::before {
      content: '';
      position: absolute;
      width: 96px;

      height: 47px;
      background: url('../../../../assets/LJ/home/itemHover.png');
      top: 10px;
    }

    .title_left .menu .item::after {
      content: '';
      position: absolute;
      width: 40px;
      height: 52px;
      background: url('../../../../assets/LJ/home/spilt.png');
      top: 6px;
      left: 80px;
    }

    .iconH {
      width: 30px;
      height: 30px;
      margin-right: 5px;
      background: url('../../../../assets/LJ/home/home.png');
    }

    .title_left .menu .item .iconM {
      width: 30px;
      height: 30px;
      margin-right: 5px;
      background: url('../../../../assets/LJ/home/menu.png');
    }

    .title_left .menu .item:hover {
      color: white;
    }

    .title_left .menu .item:hover .iconH {
      background: url('../../../../assets/LJ/home/home_hover.png');
    }

    .title_left .menu .item:hover .iconM {
      background: url('../../../../assets/LJ/home/menu_hover.png');
    }

    .title_center {
      flex: 1;
    }

    .title_center .center {
      background: url('../../../../assets/LJ/home/center.png');
      background-size: 100% 100%;
      width: 100%;
    }

    /*.title_right{*/
    /*  width: calc((100% - 1287px) / 2);*/
    /*}*/

    .titleText .title_right {
      background: url('../../../../assets/LJ/home/bg.png');
    }

    .titleText .logoText {
      left: calc(50% - 210px);
      bottom: 18px;
      z-index: 9;
    }

    .message {
      top: 20px;
      left: 550px;
      font-size: 15px;
      height: 40px;
      line-height: 35px;
      color: #8eaed7;
    }

    .goback {
      background: url('../../../../assets/LJ/home/gobackBg.png');
      height: 52px;
      width: 99px;
      top: 16px;
      left: 0px;
      cursor: pointer;
      display: flex;
      align-items: center;
      padding: 4px 0 0 15px;
    }

    .signOut {
      background: url('../../../../assets/LJ/home/signOut.png');
      height: 44px;
      width: 77px;
      top: 8px;
      right: 15px;
      cursor: pointer;
    }

    .signOut:hover {
      background: url('../../../../assets/LJ/home/signOut_hover.png');
    }

    .ipc {
      background: url('../../../../assets/LJ/home/rightBg.png');
      height: 52px;
      width: 99px;
      top: 16px;
      right: 0px;
      cursor: pointer;
      transition: all 0.5s;
      display: flex;
      align-items: center;
      justify-content: flex-end;
      padding-right: 15px;
    }

    .iconItemOne {
      background: url('../../../../assets/LJ/main/ico2_03.png') no-repeat center;
    }

    .iconItemOne:hover {
      background: url('../../../../assets/LJ/main/icohover_03.png') no-repeat center;
    }

    .iconItemTwo {
      background: url('../../../../assets/LJ/main/ico2_06.png') no-repeat center;
    }

    .iconItemTwo:hover {
      background: url('../../../../assets/LJ/main/icohover_06.png') no-repeat center;
    }

    .iconItemThree {
      background: url('../../../../assets/LJ/main/ico2_09.png') no-repeat center;
    }

    .iconItemThree:hover {
      background: url('../../../../assets/LJ/main/icohover_09.png') no-repeat center;
    }

    .iconItemFour {
      background: url('../../../../assets/LJ/main/ico2_08.png') no-repeat center;
    }

    .iconItemFour:hover {
      background: url('../../../../assets/LJ/main/icohover_08.png') no-repeat center;
    }

    .iconItemFive {
      background: url('../../../../assets/LJ/main/ico2_11.png') no-repeat center;
    }

    .iconItemFive:hover {
      background: url('../../../../assets/LJ/main/icohover_11.png') no-repeat center;
    }

    .iconSingOut {
      background: url('../../../../assets/LJ/main/singout.png') no-repeat center;
    }

    .iconSingOut:hover {
      background: url('../../../../assets/LJ/main/singout_hover.png') no-repeat center;
    }

    .systemSetIcon {
      background: url('../../../../assets/LJ/main/system-icon.png') no-repeat center;
    }

    .systemSetIcon:hover {
      background: url('../../../../assets/LJ/main/system-icon-hover.png') no-repeat center;
    }

    .explain {
      background: url('../../../../assets/LJ/main/sysm.png') no-repeat center;
    }
  }

  .GD {
    .pageAll {
      padding: 0 10px 22px 10px;
      background-image: url("../../../../assets/GD/leftBottom.png"), url("../../../../assets/GD/rightBottom.png"), url("../../../../assets/GD/bottom.png") !important;
      background-repeat: no-repeat;
      background-position: left bottom, right bottom, bottom;
    }

    .selectitem:hover {
      background: #2a4b49;
    }

    .border-top-style {
      border-top: 1px solid;
      border-image: linear-gradient(90deg, rgba(24, 86, 61, 0.7) 15%, rgb(69, 138, 84) 50%, rgba(31, 75, 37, 0.7) 85%) 2 2 2 2;
    }

    .myvideo {
      height: 100%;
      width: 100%;
      z-index: -1;
      position: absolute;
      top: 0;
      left: 0;
      object-fit: fill;
    }

    .avatarImg {
      height: 28px;
      width: 28px;
      border-radius: 50%;
    }

    .isHasBackground {
      background-color: rgba(6, 6, 6, 0.1);
    }

    .preview-page .top .top-main .shine {
      background: linear-gradient(-45deg, transparent 40%, rgba(205, 213, 128, 0.8) 5%, transparent 41%);
      background-size: 600% 100%;
      -webkit-animation: shine 30s infinite;
      animation: shine 30s infinite;
      -webkit-animation-delay: 0s;
      animation-delay: 0s;
      -webkit-animation-timing-function: linear;
      animation-timing-function: linear;
    }

    .titleText .title_left {
      background: url('../../../../assets/GD/home/bg.png');
    }

    .bread {
      position: absolute;
      width: max-content;
      bottom: 0px;
      left: 110px;
      color: #718db1;
      font-size: 12px;
    }

    .title_left .menu {
      width: 100px;
      height: 42px;
      left: 90px;
      font-size: 15px;
      top: 10px;
    }

    .title_left .menu .item {
      height: 100%;
      width: 100px;
      line-height: 42px;
      text-align: center;
      color: #8eaed7;
      padding-top: 5px;
      cursor: pointer;
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: center;
    }

    .homeTitleText {
      width: 100%;
      line-height: 17px;
      padding-right: 2px;
      color: #fff;
      font-weight: bold;
      font-size: 17px;
    }

    .title_left .menu .item:hover .homeTitleText {
      color: #e6ac71 !important;
    }

    .title_left .menu .item:hover::before {
      content: '';
      position: absolute;
      width: 96px;

      height: 47px;
      background: url('../../../../assets/GD/home/itemHover.png');
      top: 10px;
    }

    .title_left .menu .item::after {
      content: '';
      position: absolute;
      width: 40px;
      height: 52px;
      background: url('../../../../assets/GD/home/spilt.png');
      top: 6px;
      left: 80px;
    }

    .iconH {
      width: 30px;
      height: 30px;
      margin-right: 5px;
      background: url('../../../../assets/GD/home/home.png');
    }

    .title_left .menu .item .iconM {
      width: 30px;
      height: 30px;
      margin-right: 5px;
      background: url('../../../../assets/GD/home/menu.png');
    }

    .title_left .menu .item:hover {
      color: white;
    }

    .title_left .menu .item:hover .iconH {
      background: url('../../../../assets/GD/home/home_hover.png');
    }

    .title_left .menu .item:hover .iconM {
      background: url('../../../../assets/GD/home/menu_hover.png');
    }

    .title_center {
      flex: 1;
    }

    .title_center .center {
      background: url('../../../../assets/GD/home/center.png');
      background-size: 100% 100%;
      width: 100%;
    }

    /*.title_right{*/
    /*  width: calc((100% - 1287px) / 2);*/
    /*}*/

    .titleText .title_right {
      background: url('../../../../assets/GD/home/bg.png');
    }

    .titleText .logoText {
      left: calc(50% - 255px);
      bottom: 18px;
      z-index: 9;
    }

    .message {
      top: 20px;
      left: 550px;
      font-size: 15px;
      height: 40px;
      line-height: 35px;
      color: #8eaed7;
    }

    .goback {
      background: url('../../../../assets/GD/home/gobackBg.png');
      height: 52px;
      width: 99px;
      top: 16px;
      left: 0px;
      cursor: pointer;
      display: flex;
      align-items: center;
      padding: 4px 0 0 15px;
    }

    .signOut {
      background: url('../../../../assets/GD/home/signOut.png');
      height: 44px;
      width: 77px;
      top: 8px;
      right: 15px;
      cursor: pointer;
    }

    .signOut:hover {
      background: url('../../../../assets/GD/home/signOut_hover.png');
    }

    .ipc {
      background: url('../../../../assets/GD/home/rightBg.png');
      height: 52px;
      width: 99px;
      top: 16px;
      right: 0px;
      cursor: pointer;
      transition: all 0.5s;
      display: flex;
      align-items: center;
      justify-content: flex-end;
      padding-right: 15px;
    }


    .iconItemOne {
      background: url('../../../../assets/GD/main/ico2_03.png') no-repeat center;
    }

    .iconItemOne:hover {
      background: url('../../../../assets/GD/main/icohover_03.png') no-repeat center;
    }

    .iconItemTwo {
      background: url('../../../../assets/GD/main/ico2_06.png') no-repeat center;
    }

    .iconItemTwo:hover {
      background: url('../../../../assets/GD/main/icohover_06.png') no-repeat center;
    }

    .iconItemThree {
      background: url('../../../../assets/GD/main/ico2_09.png') no-repeat center;
    }

    .iconItemThree:hover {
      background: url('../../../../assets/GD/main/icohover_09.png') no-repeat center;
    }

    .iconItemFour {
      background: url('../../../../assets/GD/main/ico2_08.png') no-repeat center;
    }

    .iconItemFour:hover {
      background: url('../../../../assets/GD/main/icohover_08.png') no-repeat center;
    }

    .iconItemFive {
      background: url('../../../../assets/GD/main/ico2_11.png') no-repeat center;
    }

    .iconItemFive:hover {
      background: url('../../../../assets/GD/main/icohover_11.png') no-repeat center;
    }

    .iconSingOut {
      background: url('../../../../assets/GD/main/singout.png') no-repeat center;
    }

    .iconSingOut:hover {
      background: url('../../../../assets/GD/main/singout_hover.png') no-repeat center;
    }

    .systemSetIcon {
      background: url('../../../../assets/GD/main/system-icon.png') no-repeat center;
    }

    .systemSetIcon:hover {
      background: url('../../../../assets/GD/main/system-icon-hover.png') no-repeat center;
    }

    .explain {
      background: url('../../../../assets/GD/main/sysm.png') no-repeat center;
    }
  }

  .KJ {
    .pageAll {
      padding: 0 10px 22px 10px;
      background-image: url("../../../../assets/KJ/leftBottom.png"), url("../../../../assets/KJ/rightBottom.png"), url("../../../../assets/KJ/bottom.png") !important;
      background-repeat: no-repeat;
      background-position: left bottom, right bottom, bottom;
    }

    .selectitem:hover {
      background: #2a4b49;
    }

    .border-top-style {
      border-top: 1px solid;
      border-image: linear-gradient(90deg, rgba(24, 86, 61, 0.7) 15%, rgb(69, 138, 84) 50%, rgba(31, 75, 37, 0.7) 85%) 2 2 2 2;
    }

    .myvideo {
      height: 100%;
      width: 100%;
      z-index: -1;
      position: absolute;
      top: 0;
      left: 0;
      object-fit: fill;
    }

    .avatarImg {
      height: 28px;
      width: 28px;
      border-radius: 50%;
    }

    .isHasBackground {
      background-color: rgba(6, 6, 6, 0.1);
    }

    .preview-page .top .top-main .shine {
      background: linear-gradient(-45deg, transparent 40%, rgba(205, 213, 128, 0.8) 5%, transparent 41%);
      background-size: 600% 100%;
      -webkit-animation: shine 30s infinite;
      animation: shine 30s infinite;
      -webkit-animation-delay: 0s;
      animation-delay: 0s;
      -webkit-animation-timing-function: linear;
      animation-timing-function: linear;
    }

    .titleText .title_left {
      background: url('../../../../assets/KJ/home/bg.png');
    }

    .bread {
      position: absolute;
      width: max-content;
      bottom: 0px;
      left: 110px;
      color: #718db1;
      font-size: 12px;
    }

    .title_left .menu {
      width: 100px;
      height: 42px;
      left: 90px;
      font-size: 15px;
      top: 10px;
    }

    .title_left .menu .item {
      height: 100%;
      width: 100px;
      line-height: 42px;
      text-align: center;
      color: #8eaed7;
      padding-top: 5px;
      cursor: pointer;
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: center;
    }

    .homeTitleText {
      width: 100%;
      line-height: 17px;
      padding-right: 2px;
      color: #698c91;
      font-weight: bold;
      font-size: 17px;
    }

    .title_left .menu .item:hover .homeTitleText {
      color: #e6ac71 !important;
    }

    .title_left .menu .item:hover::before {
      content: '';
      position: absolute;
      width: 96px;

      height: 47px;
      background: url('../../../../assets/KJ/home/itemHover.png');
      top: 10px;
    }

    .title_left .menu .item::after {
      content: '';
      position: absolute;
      width: 40px;
      height: 52px;
      background: url('../../../../assets/KJ/home/spilt.png');
      top: 6px;
      left: 80px;
    }

    .iconH {
      width: 30px;
      height: 30px;
      margin-right: 5px;
      background: url('../../../../assets/KJ/home/home.png');
    }

    .title_left .menu .item .iconM {
      width: 30px;
      height: 30px;
      margin-right: 5px;
      background: url('../../../../assets/KJ/home/menu.png');
    }

    .title_left .menu .item:hover {
      color: white;
    }

    .title_left .menu .item:hover .iconH {
      background: url('../../../../assets/KJ/home/home_hover.png');
    }

    .title_left .menu .item:hover .iconM {
      background: url('../../../../assets/KJ/home/menu_hover.png');
    }

    .title_center {
      flex: 1;
    }

    .title_center .center {
      background: url('../../../../assets/KJ/home/center.png');
      background-size: 100% 100%;
      width: 100%;
    }

    /*.title_right{*/
    /*  width: calc((100% - 1287px) / 2);*/
    /*}*/

    .titleText .title_right {
      background: url('../../../../assets/KJ/home/bg.png');
    }

    .titleText .logoText {
      left: calc(50% - 210px);
      bottom: 18px;
      z-index: 9;
    }

    .message {
      top: 20px;
      left: 550px;
      font-size: 15px;
      height: 40px;
      line-height: 35px;
      color: #8eaed7;
    }

    .goback {
      background: url('../../../../assets/KJ/home/gobackBg.png');
      height: 52px;
      width: 99px;
      top: 16px;
      left: 0px;
      cursor: pointer;
      display: flex;
      align-items: center;
      padding: 4px 0 0 15px;
    }

    .signOut {
      background: url('../../../../assets/KJ/home/signOut.png');
      height: 44px;
      width: 77px;
      top: 8px;
      right: 15px;
      cursor: pointer;
    }

    .signOut:hover {
      background: url('../../../../assets/KJ/home/signOut_hover.png');
    }

    .ipc {
      background: url('../../../../assets/KJ/home/rightBg.png');
      height: 52px;
      width: 99px;
      top: 16px;
      right: 0px;
      cursor: pointer;
      transition: all 0.5s;
      display: flex;
      align-items: center;
      justify-content: flex-end;
      padding-right: 15px;
    }

    .iconItemOne {
      background: url('../../../../assets/KJ/main/ico2_03.png') no-repeat center;
    }

    .iconItemOne:hover {
      background: url('../../../../assets/KJ/main/icohover_03.png') no-repeat center;
    }

    .iconItemTwo {
      background: url('../../../../assets/KJ/main/ico2_06.png') no-repeat center;
    }

    .iconItemTwo:hover {
      background: url('../../../../assets/KJ/main/icohover_06.png') no-repeat center;
    }

    .iconItemThree {
      background: url('../../../../assets/KJ/main/ico2_09.png') no-repeat center;
    }

    .iconItemThree:hover {
      background: url('../../../../assets/KJ/main/icohover_09.png') no-repeat center;
    }

    .iconItemFour {
      background: url('../../../../assets/KJ/main/ico2_08.png') no-repeat center;
    }

    .iconItemFour:hover {
      background: url('../../../../assets/KJ/main/icohover_08.png') no-repeat center;
    }

    .iconItemFive {
      background: url('../../../../assets/KJ/main/ico2_11.png') no-repeat center;
    }

    .iconItemFive:hover {
      background: url('../../../../assets/KJ/main/icohover_11.png') no-repeat center;
    }

    .iconSingOut {
      background: url('../../../../assets/KJ/main/singout.png') no-repeat center;
    }

    .iconSingOut:hover {
      background: url('../../../../assets/KJ/main/singout_hover.png') no-repeat center;
    }

    .systemSetIcon {
      background: url('../../../../assets/KJ/main/system-icon.png') no-repeat center;
    }

    .systemSetIcon:hover {
      background: url('../../../../assets/KJ/main/system-icon-hover.png') no-repeat center;
    }

    .explain {
      background: url('../../../../assets/KJ/main/sysm.png') no-repeat center;
    }
  }
</style>