<template>
  <div class="w-full h-full overflow-hidden preview-page relative">
    <video
      :src="pageBg"
      v-if="
        ((routeMatched !== 3 && routeName !== 'Dashboard') ||
          routeName === 'EquipmentOperation') &&
        cool
      "
      class="myvideo"
      autoplay
      muted
      loop
    ></video>
    <video
      :src="homeMenu"
      v-else-if="(routeMatched === 3 || routeName === 'Dashboard') && cool"
      class="myvideo"
      autoplay
      muted
      loop
    ></video>
    <!--    <img :src="home" class="homeBg" style="z-index: -10" v-if="routeMatched==3&&routeName==='Dashboard'&&!cool" alt="">-->
    <img :src="homeHJ" class="homeBg" style="z-index: -10" v-if="routeMatched === 3 && !cool" alt=""/>
    <img :src="otherBgHJ" class="homeBg" style="z-index: -10" v-if="(routeMatched !== 3 || routeName === 'EquipmentOperation') && !cool" alt=""/>
    <div class="w-full top overflow-hidden relative titleText" style="min-width: 1024px" v-if="routeName !== 'Dashboard'">
      <div class="w-full h-full top-main absolute">
        <img :src="logoTitle" class="logoText absolute" alt=""/>
        <div class="title_left h-full relative">
          <div class="goback absolute" @click="goBack"></div>
          <div class="menu absolute layout-left-top fs_dispose">
            <div class="item layout-center" @click="handleBackDashboard">
              <div class="iconH"></div>
              主页
            </div>
            <div class="item layout-center" @click="handBackMenu">
              <div class="iconM"></div>
              主菜单
            </div>
          </div>
          <div class="bread layout-left-center">
            <img :src="address" alt="" style="margin-right: 5px"/>
            <template v-for="(item, index) in route.matched">
              <span v-if="index > 1">
                {{
                  item.name === 'BasicTheory' && route.query.studyType === 1
                      ? '职掌装备'
                      : item.name === 'BasicTheory' && route.query.studyType === 2
                          ? '值勤业务'
                          : item.meta.title
                }}
                <span
                  style="padding: 0 5px"
                  v-if="index > 1 && index < route.matched.length - 1"
                >
                  >
                </span>
              </span>
            </template>
          </div>
        </div>
        <div class="title_center h-full layout-side">
          <div class="tile h-full"></div>
          <div class="center h-full"></div>
          <div class="tile h-full"></div>
        </div>
        <div class="title_right h-full relative">
          <div class="userInfo layout-left-center relative" style="cursor: pointer" @click="openEditPasswordModel">
            <img
              :src="fileUrl + userInfo.userImg"
              v-if="userInfo.userImg && userInfo.userImg !== ''"
              class="avatarImg"
              alt=""/>
            <img
              :src="pagMan"
              v-else-if="userInfo.userSex === 1"
              class="avatarImg"
              alt=""/>
            <img :src="pagWoman" v-else class="avatarImg" alt=""/>
            <span
              class="nobr"
              :title="userInfo.userName"
              style="padding-left: 10px; width: calc(100% - 28px)"
            >{{ userInfo.userName }}</span
            >
          </div>
          <NipSerial></NipSerial>
<!--          <a-tooltip>-->
<!--            <template #title>-->
<!--              <div style="font-size: 12px">-->
<!--                {{ wsOnline ? '报训软件已连接' : '报训软件未连接' }}-->
<!--              </div>-->
<!--            </template>-->
<!--            <img-->
<!--              class="equipment1"-->
<!--              :src="ico_state_ws_on"-->
<!--              v-if="wsOnline"-->
<!--              @click="linkWsOnInfo"-->
<!--              alt=""/>-->
<!--            <img-->
<!--              class="equipment1"-->
<!--              :src="ico_state_ws"-->
<!--              v-else-->
<!--              @click="linkWsOnInfo"-->
<!--              style="cursor: pointer"-->
<!--              alt=""/>-->
<!--            &lt;!&ndash;<a :href="tUrl"></a>&ndash;&gt;-->
<!--          </a-tooltip>-->
<!--          <a-tooltip>-->
<!--            <template #title>-->
<!--              <div style="font-size: 12px">-->
<!--                {{ devOnline ? '拍发设备已连接' : '拍发设备未连接' }}-->
<!--              </div>-->
<!--            </template>-->
<!--            <img class="equipment2" :src="ico_state_dev_on" v-if="devOnline" alt=""/>-->
<!--            <img class="equipment2" :src="ico_state_dev" v-else alt=""/>-->
<!--          </a-tooltip>-->
          <!--          <div v-if="!ipc" class="signOut absolute" @click="handleLoginOut"></div>-->
          <a-popover
            class="min"
            v-model:visible="isShow"
            trigger="click"
            overlayClassName="noPadding ZIndex"
          >
            <template #content>
              <div class="settingBox">
                <div
                  class="selectitem"
                  v-if="userRole.id !== '2'"
                  @click="jumpSystemManage"
                >
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
                <div
                  class="border-top-style selectitem"
                  v-if="ipc"
                  @click="openSetting"
                >
                  <a-tooltip placement="left">
                    <template #title>
                      <div style="font-size: 12px">网络设置</div>
                    </template>
                    <div
                      id="openSettingWindow"
                      class="item layout-center iconItemTwo"
                    ></div>
                  </a-tooltip>
                </div>
                <div
                  class="border-top-style selectitem"
                  @click="closeAnimation"
                >
                  <a-tooltip placement="left">
                    <template #title>
                      <div style="font-size: 12px">
                        {{ cool ? '关闭特效' : '开启特效' }}
                      </div>
                    </template>
                    <div
                      v-if="cool"
                      class="item-red layout-center iconItemThree"
                    ></div>
                    <div
                      v-else
                      class="item-red layout-center iconItemFour"
                    ></div>
                  </a-tooltip>
                </div>
                <div
                  class="border-top-style selectitem"
                  @click="fontSizeVisible = true"
                >
                  <a-tooltip placement="left">
                    <template #title>
                      <div style="font-size: 12px">字体大小</div>
                    </template>
                    <div class="item layout-center iconItemSix"></div>
                  </a-tooltip>
                </div>
                <div
                  class="border-top-style selectitem"
                  @click="noOff = !noOff"
                >
                  <a-tooltip placement="left">
                    <template #title>
                      <div style="font-size: 12px">使用说明</div>
                    </template>
                    <div class="item layout-center iconItemSeven"></div>
                  </a-tooltip>
                </div>
                <div
                  class="border-top-style selectitem"
                  @click="handleLoginOut"
                >
                  <a-tooltip placement="left">
                    <template #title>
                      <div style="font-size: 12px">退出登录</div>
                    </template>
                    <div class="item-red layout-center iconSingOut"></div>
                  </a-tooltip>
                </div>
                <div
                  class="border-top-style selectitem"
                  v-if="ipc"
                  @click="closeWindow"
                >
                  <a-tooltip placement="left">
                    <template #title>
                      <div style="font-size: 12px">关闭软件</div>
                    </template>
                    <div class="item-red layout-center iconItemFive"></div>
                  </a-tooltip>
                </div>
              </div>
            </template>
            <div class="ipc absolute" @click="setting"></div>
          </a-popover>
        </div>
      </div>
      <div class="message absolute"></div>
    </div>
    <div class="dashboardTR" v-else-if="interfaceStyle=='HJ'">
      <div class="userInfo layout-left-center" style="cursor: pointer" @click="openEditPasswordModel">
        <img
          :src="fileUrl + userInfo.userImg"
          v-if="userInfo.userImg && userInfo.userImg !== ''"
          class="avatarImg"
          alt=""/>
        <img
          :src="pagMan"
          v-else-if="userInfo.userSex === 1"
          class="avatarImg"
          alt=""/>
        <img :src="pagWoman" v-else class="avatarImg" alt=""/>
        <span
          class="nobr"
          :title="userInfo.userName"
          style="padding-left: 10px; width: calc(100% - 28px)"
        >{{ userInfo.userName }}</span
        >
      </div>
      <a-popover
        class="min"
        trigger="click"
        overlayClassName="noPadding ZIndex"
      >
        <template #content>
          <div class="settingBox">
            <div
              class="selectitem"
              v-if="userRole.id !== '2'"
              @click="jumpSystemManage"
            >
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
            <div
              class="border-top-style selectitem"
              v-if="ipc"
              @click="openSetting"
            >
              <a-tooltip placement="left">
                <template #title>
                  <div style="font-size: 12px">网络设置</div>
                </template>
                <div
                  id="openSettingWindow"
                  class="item layout-center iconItemTwo"
                ></div>
              </a-tooltip>
            </div>
            <div class="border-top-style selectitem" @click="closeAnimation">
              <a-tooltip placement="left">
                <template #title>
                  <div style="font-size: 12px">
                    {{ cool ? '关闭特效' : '开启特效' }}
                  </div>
                </template>
                <div
                  v-if="cool"
                  class="item-red layout-center iconItemThree"
                ></div>
                <div v-else class="item-red layout-center iconItemFour"></div>
              </a-tooltip>
            </div>
            <div
              class="border-top-style selectitem"
              @click="fontSizeVisible = true"
            >
              <a-tooltip placement="left">
                <template #title>
                  <div style="font-size: 12px">字体大小</div>
                </template>
                <div class="item layout-center iconItemSix"></div>
              </a-tooltip>
            </div>
            <div class="border-top-style selectitem" @click="noOff = !noOff">
              <a-tooltip placement="left">
                <template #title>
                  <div style="font-size: 12px">使用说明</div>
                </template>
                <div class="item layout-center iconItemSeven"></div>
              </a-tooltip>
            </div>
            <div class="border-top-style selectitem" @click="handleLoginOut">
              <a-tooltip placement="left">
                <template #title>
                  <div style="font-size: 12px">退出登录</div>
                </template>
                <div class="item-red layout-center iconSingOut"></div>
              </a-tooltip>
            </div>
            <div
              class="border-top-style selectitem"
              v-if="ipc"
              @click="closeWindow"
            >
              <a-tooltip placement="left">
                <template #title>
                  <div style="font-size: 12px">关闭软件</div>
                </template>
                <div class="item-red layout-center iconItemFive"></div>
              </a-tooltip>
            </div>
          </div>
        </template>
        <div class="ipc absolute"></div>
      </a-popover>
    </div>
    <div
      class="w-full content"
      :style="{
        height: 'calc(100% - ' + (routeName !== 'Dashboard' ? 60 : 0) + 'px)',
        paddingTop: (routeName !== 'Dashboard' ? 12 : 0) + 'px'
      }"
      :class="{ isHasBackground: isShowBackground }"
    >
      <router-view v-slot="{ Component }">
        <transition name="router_animate">
          <component :is="Component"/>
        </transition>
      </router-view>
    </div>
    <a-modal
      :destroyOnClose="true"
      :width="350"
      title="字体大小配置"
      class="init_modal_style footer-border-none"
      destroyOnClose="true"
      v-model:visible="fontSizeVisible"
    >
      <template #footer>
        <div class="layout-right-center">
          <a-button @click="fontSizeVisible = false">取消</a-button>
          <a-button @click="settingFontSize">确定</a-button>
        </div>
      </template>
      <div>
        <div class="fontSizeBox" style="padding: 10px; color: white">
          <a-slider
            v-model:value="fontSizeScale"
            :min="0"
            :max="3"
            :step="1"
            :marks="fontSizeText"
            :tooltipVisible="false"
          ></a-slider>
        </div>
        <div
          class="modelText"
          :style="{ fontSize: fontSizeScale * 2 + 14 + 'px', color: '#fff' }"
        >
          <div class="tit">示例</div>
          <div class="cont">
            捍卫祖国领海&nbsp;&nbsp;&nbsp;&nbsp;建设强大海军
          </div>
        </div>
      </div>
    </a-modal>
    <a-modal
      :destroyOnClose="true"
      :width="760"
      class="init_modal_style footer-border-none"
      v-model:visible="editPasswordModel"
      @cancel="cancelEditPasswordModel"
    >
      <template #title>
        <strong>个人中心</strong>
      </template>
      <template #footer>
        <div class="w-full layout-center">
          <div></div>
        </div>
      </template>
      <Personal></Personal>
    </a-modal>
    <Instructions v-if="noOff"></Instructions>
<!--    <shortcut-menu/>-->
    <div v-if="serialShow" style="" class="serialBox">
      <div style="width: 100%;height: 100%">
        <div style="height: 30px" class="layout-side-n"><div>{{localSerial?'当前串口：'+localSerial:''}}</div><CloseOutlined @click="serialShow=false"/></div>
        <div style="width: 100%;height: calc(100% - 30px);overflow: auto">
          <div class="listItem" v-for="(v,index) of serialProtList" :key="index" @click="linkPort(v)">{{v}}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
  export default {
    name: "PreviewHJ"
  }
</script>
<script setup>
  import NipSerial from "../../../../components/common/NipSerial.vue";
  import Personal from "../../../../components/personal/Personal.vue";
  import useTraffic from './../../../../common/mixin/useTraffic.js'
  import pagMan from '../../../../assets/HJ/pag-avatar-woman.png'
  import pagWoman from '../../../../assets/HJ/pag-avatar-man.png'

  import homeHJ from '../../../../assets/HJ/main/home2.jpg'
  import otherBgHJ from '../../../../assets/HJ/main/otherBg.jpg'

  import homeMenu from '../../../../assets/HJ/homeMenu.mp4'
  import pageBg from '../../../../assets/HJ/pageBg.mp4'
  import ico_state_ws from '../../../../assets/HJ/ico/ico-state-ws.png'
  import ico_state_ws_on from '../../../../assets/HJ/ico/ico-state-ws-on.png'
  import ico_state_dev from '../../../../assets/HJ/ico/ico-state-dev.png'
  import ico_state_dev_on from '../../../../assets/HJ/ico/ico-state-dev-on.png'
  import logoTitle from '../../../../assets/HJ/home/title-1.png'
  import address from '../../../../assets/HJ/home/addres.png'
  import {useRoute, useRouter, onBeforeRouteUpdate} from 'vue-router'
  import {useStore} from 'vuex'
  import getBackByRouter from '../js/getBackByRouter.js'
  import {createVNode, ref, provide, watch, onMounted, nextTick, inject} from 'vue'
  import {Modal, message} from 'ant-design-vue'
  import {
    ExclamationCircleOutlined,
    CloseOutlined,
    GlobalOutlined,
    ReloadOutlined,
    PauseCircleOutlined,
    PlayCircleOutlined, PlusOutlined
  } from '@ant-design/icons-vue'
  import {Ws} from '../../../../common/ws/Ws'
  import messageWebSocket from '../../../../common/ws/MessageWebSocket.js'
  import {fontSizeDispose} from '../../../../common/utils/Utils'
  import routeConfig from '../js/routeConfig.js'
  import Instructions from '../../../../components/instructions/instructions.vue'
  import ShortcutMenu from '../../../../components/common/ShortcutMenu.vue'
  import useFontSize from "../../../../common/mixin/useFontSize.js";
  import {ipcRenderer, ipcApi} from '../../../../electron/index'
  import {changePassword} from "../../../../common/api/UserApi";
  import {closeSession} from '../../../../common/session/logout.js'

  const interfaceStyle = window.interfaceStyle
  const cool = inject('cool')
  const router = useRouter()
  const route = useRoute()
  const systemPath = ref('')
  const store = useStore()
  const userRole = ref({})
  const userInfo = ref({})
  const routeName = ref('')
  const routeMatched = ref(0)
  const fileUrl = ref('')
  const tUrl = ref('')
  const {wsOnline, devOnline} = useTraffic()
  const ipc = ref(ipcRenderer.isEE)
  let {isShowBackground} = getBackByRouter()
  const openSettingWindow = inject('openSettingWindow')
  const isShow = ref(false)
  const fontSizeText = ref({0: '标准', 1: '较大', 2: '大', 3: '特大'})
  const linkWsIndex = ref(0)
  const noOff = ref(false)
  const serialProtList = ref([])//桌面端串口列表
  const serialShow = ref(false)
  const localSerial = ref(localStorage.getItem('serial'))
  const openSetting = () => {
    isShow.value = false
    openSettingWindow()
  }
  const {routePaths, lineDevicePaths} = routeConfig()
  const {fontSizeScale, fontSizeVisible, reloadWindow, settingFontSize} = useFontSize()
  const editPasswordModel = ref(false)
  const editPasswordData = ref({
    oldPassword:'',
    newPassword:'',
    newPasswordV:'',
  })
  const openEditPasswordModel = ()=>{
    editPasswordModel.value = true
  }
  const cancelEditPasswordModel = ()=>{
    editPasswordModel.value = false
  }
  const editPassword = ()=>{
    editPasswordData.value.userId = userInfo.value.id
    changePassword(editPasswordData.value).then(res=>{
      if(res.data){
        message.success('修改密码成功')
        router.replace('/login').then()
      }else {
        message.error(res.message)
      }
    })
  }
  onMounted(() => {
    if(localSerial.value!==null){
      linkPort(localSerial.value)
      // ipcRenderer.ipc.sendSync(ipcApi.ipcApiRoute.linkPort,localSerial.value)
    }
    if(localStorage.getItem('serialChrome')){
      messageWebSocket('reset', 'reset')
    }else {
      messageWebSocket()
    }
    userRole.value = JSON.parse(localStorage.getItem('userRole'))
    userInfo.value = JSON.parse(window.localStorage.getItem('userInfo'))
    let routerData = JSON.parse(window.localStorage.getItem('userRouter'))
    fileUrl.value = window.fileUrl
    tUrl.value = window.fileUrl + '/006/TrafficService.exe'
    Ws.getInstance().run().then()
    if (route.matched.length === 2) {
      router.push('/preview/dashboard')
    } else {
      router.push(route.fullPath)
    }

    routerData.forEach((r) => {
      if (r.name === 'SystemManage') systemPath.value = r
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

    nextTick(() => {
      fontSizeDispose()
    })
  })
  const setting = () => {
    isShow.value = true
  }
  const linkWsOnInfo = async () => {
    //桌面端
    // if (ipc.value){
    //   serialProtList.value = await ipcRenderer.ipc.sendSync(ipcApi.ipcApiRoute.getSerialPorts)
    //   serialShow.value = true
    //   localSerial.value = localStorage.getItem('serial')
    // }else {
      localStorage.setItem('serialChrome',true)
      messageWebSocket('reset', 'reset')
    // }
  }
  //连接串口
  const linkPort = (portName)=>{
    const data = ipcRenderer.ipc.sendSync(ipcApi.ipcApiRoute.linkPort,portName)
    console.log(data)
    if(data&&data.code&&data.code==200){
      localStorage.setItem('serial',portName)
      messageWebSocket('reset')
      serialShow.value = false
    }
  }

  const jumpSystemManage = () => {
    router.push(
        `/preview/${systemPath.value.path}/${systemPath.value.children[0].path}`
    )
  }
  const closeAnimation = () => {
    cool.value = !cool.value
    localforage.setItem('cool', cool.value)
  }
  nextTick(() => {
    routeName.value = route.name
    routeMatched.value = route.matched.length
    // console.log(route.matched)
  })
  watch(route, () => {
    routeName.value = route.name
    routeMatched.value = route.matched.length
    // if(route.matched.length==5){
    //   router.push(route.matched[2].path)
    // }
  })

  const handleBackDashboard = () => {
    router.push('/preview/dashboard')
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
    }
    else if (route.path.indexOf('trainScore') > -1) {
      router.push(route.matched[4].path + '/telexPost')
    } else if (route.path.indexOf('datagramTrainScore') > -1) {
      router.push(route.matched[4].path + '/datagramPost')
    } else if (route.path.indexOf('patExamTrainScore') > -1) {
      router.push(route.matched[4].path + '/examPostList')
    } else if (route.path.indexOf('patTrainScore') > -1) {
      router.push(route.matched[4].path + '/handKeyPostJob')
    } else if (route.path.indexOf('receivePostScore') > -1) {
      router.push(route.matched[4].path + '/receivePostPractise')
    } else {
      if (routePaths.value.some((item) => item.children.indexOf(route.path) > -1)) {
        // console.log('1111')
        let routeItem = routePaths.value.filter(
            (item) => item.children.indexOf(route.path) > -1
        )[0]
        router.push(routeItem.path)
      } else {
        // console.log('2222')
        router.go(-1)
      }
    }
  }
  // onBeforeMount(() => {
  //   handlePermissions(route.meta.permissions);
  // });
  // onBeforeRouteUpdate((to) => {
  //   handlePermissions(to.meta.permissions);
  // });
  // const handlePermissions = (meta) => {
  //   store.commit('setPermissions', meta);
  // }
  const closeWindow = () => {
    Modal.confirm({
      title: () => '是否确认退出海军报务兵综合训练系统？',
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
  .serialBox{
    position: absolute;width: 300px;height: 300px;background: rgb(78 98 159);z-index: 99;top:calc(50% - 150px);left:calc(50% - 100px);overflow: auto;
    padding: 10px;
    border-radius: 5px;
  }
  .listItem{
    padding: 10px 5px;cursor: pointer;
    background: rgba(20, 37, 71, 1);
    margin-bottom: 2px;
    border-radius: 3px;
  }
  .listItem:hover{
    background: rgba(20, 37, 71,0.5)
  }

  .router_animate-enter-active {
    /*animation: slideInLeft 0.5s;*/
  }

  .router_animate-leave-active {
    /*animation: slideOutRight 0.3s;*/
  }

  .selectitem {
    /*padding:10px 5px;*/
    cursor: pointer;
    transition: all 0.5s;
  }

  .selectitem:hover {
    background: #183a65;
  }

  .border-top-style {
    border-top: 1px solid;
    border-image: linear-gradient(90deg,
    rgba(24, 45, 86, 0.7) 15%,
    rgba(69, 92, 138, 1) 50%,
    rgba(31, 46, 75, 0.7) 85%) 2 2 2 2;
  }

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

  .isHasBackground {
    background-color: rgba(6, 6, 6, 0.1);
  }

  .preview-page .top {
    height: 60px;
  }

  .preview-page .top .top-main {
    top: 0;
    display: flex;
    align-items: self-start;
    justify-content: space-between;
  }

  .preview-page .content {
  }

  .preview-page .bottom {
    height: 30px;
  }

  .preview-page .top .top-main .shine {
    background: linear-gradient(-45deg,
    transparent 40%,
    rgba(205, 213, 128, 0.8) 5%,
    transparent 41%);
    background-size: 600% 100%;
    -webkit-animation: shine 30s infinite;
    animation: shine 30s infinite;
    -webkit-animation-delay: 0s;
    animation-delay: 0s;
    -webkit-animation-timing-function: linear;
    animation-timing-function: linear;
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
      background-position-x: 0;
    }
    100% {
      background-position-x: -400%;
    }
  }

  .title_left {
    width: 678px;
    background: url('../../../../assets/HJ/home/left.png') no-repeat;
  }

  .titleText .title_left {
    width: 680px;
    background: url('../../../../assets/HJ/home/left-1.png') no-repeat;
  }

  .bread {
    position: absolute;
    width: max-content;
    bottom: 0;
    left: 110px;
    color: #718db1;
    font-size: 12px;
  }

  .title_left .menu {
    width: 300px;
    height: 42px;
    left: 130px;
    font-size: 15px;
  }

  .title_left .menu .item {
    width: 50%;
    height: 100%;
    line-height: 42px;
    text-align: center;
    color: #8eaed7;
    cursor: pointer;
    position: relative;
  }

  .title_left .menu .item:hover::before {
    content: '';
    position: absolute;
    width: 120px;
    height: 24px;
    background: url('../../../../assets/HJ/home/itemHover.png');
    bottom: 0;
  }

  .title_left .menu .item .iconH {
    width: 20px;
    height: 20px;
    margin-right: 5px;
    background: url('../../../../assets/HJ/home/home.png');
  }

  .title_left .menu .item .iconM {
    width: 20px;
    height: 20px;
    margin-right: 5px;
    background: url('../../../../assets/HJ/home/menu.png');
  }

  .title_left .menu .item:hover {
    color: white;
  }

  .title_left .menu .item:hover .iconH {
    background: url('../../../../assets/HJ/home/home_hover.png');
  }

  .title_left .menu .item:hover .iconM {
    background: url('../../../../assets/HJ/home/menu_hover.png');
  }

  .title_center {
    flex: 1;
  }

  .title_center .tile {
    background: url('../../../../assets/HJ/home/center-tile.png') repeat;
    width: calc((100% - 130px) / 2);
  }

  .titleText .title_center .tile {
    background: url('../../../../assets/HJ/home/center-title-1.png') repeat;
  }

  .title_center .center {
    background: url('../../../../assets/HJ/home/center.png');
    width: 130px;
  }

  .titleText .title_center .center {
    background: url('../../../../assets/HJ/home/center-1.png');
  }

  .title_right {
    width: 680px;
  }

  .titleText .title_right {
    background: url('../../../../assets/HJ/home/right-1.png') no-repeat;
  }

  .titleText .logoText {
    left: calc(50% - 170px);
    bottom: 8px;
    z-index: 1;
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
    background: url('../../../../assets/HJ/home/goBack.png');
    height: 44px;
    width: 77px;
    top: 8px;
    left: 15px;
    cursor: pointer;
  }

  .goback:hover {
    background: url('../../../../assets/HJ/home/goBack_hover.png');
  }

  .signOut {
    background: url('../../../../assets/HJ/home/signOut.png');
    height: 44px;
    width: 77px;
    top: 8px;
    right: 15px;
    cursor: pointer;
  }

  .signOut:hover {
    background: url('../../../../assets/HJ/home/signOut_hover.png');
  }

  .userInfo {
    position: absolute!important;
    height: 45px!important;
    line-height: 45px!important;
    right: 130px!important;
  }

  .equipment1 {
    position: absolute;
    left: 270px;
    top: 12px;
  }

  .equipment2 {
    position: absolute;
    left: 325px;
    top: 12px;
  }

  .settingBox {
    color: rgb(192 230 255);
    font-size: 22px;
  }

  .selectitem .item,
  .selectitem .item-red {
    z-index: 9;
    cursor: pointer;
    width: 60px;
    height: 60px;
    transition: all 0.5s;
  }

  .dashboardTR {
    width: 315px;
    height: 60px;
    background: url('../../../../assets/HJ/bg.png') no-repeat center;
    position: absolute;
    right: 0;
    top: 0;
    z-index: 9;
  }

  .avatarImg {
    height: 28px;
    width: 28px;
    border-radius: 50%;
  }

  .ipc {
    background: url('../../../../assets/HJ/home/setting.png');
    height: 44px;
    width: 77px;
    top: 8px;
    right: 15px;
    cursor: pointer;
    transition: all 0.5s;
  }

  .ipc:hover {
    background: url('../../../../assets/HJ/home/setting_hover.png');
  }

  .settingBox {
    color: rgb(192 230 255);
    font-size: 22px;
  }

  .selectitem .item,
  .selectitem .item-red {
    z-index: 9;
    cursor: pointer;
    width: 60px;
    height: 60px;
    transition: all 0.5s;
  }

  .iconItemOne {
    background: url('../../../../assets/HJ/main/ico2_03.png') no-repeat center;
  }

  .iconItemOne:hover {
    background: url('../../../../assets/HJ/main/icohover_03.png') no-repeat center;
  }

  .iconItemTwo {
    background: url('../../../../assets/HJ/main/ico2_06.png') no-repeat center;
  }

  .iconItemTwo:hover {
    background: url('../../../../assets/HJ/main/icohover_06.png') no-repeat center;
  }

  .iconItemThree {
    background: url('../../../../assets/HJ/main/ico2_09.png') no-repeat center;
  }

  .iconItemThree:hover {
    background: url('../../../../assets/HJ/main/icohover_09.png') no-repeat center;
  }

  .iconItemFour {
    background: url('../../../../assets/HJ/main/ico2_08.png') no-repeat center;
  }

  .iconItemFour:hover {
    background: url('../../../../assets/HJ/main/icohover_08.png') no-repeat center;
  }

  .iconItemFive {
    background: url('../../../../assets/HJ/main/ico2_11.png') no-repeat center;
  }

  .iconItemFive:hover {
    background: url('../../../../assets/HJ/main/icohover_11.png') no-repeat center;
  }

  .iconItemSix {
    background: url('../../../../assets/HJ/main/ico2_13.png') no-repeat center;
  }

  .iconItemSix:hover {
    background: url('../../../../assets/HJ/main/icohover_13.png') no-repeat center;
  }

  .iconItemSeven {
    background: url('../../../../assets/HJ/main/ico2_14.png') no-repeat center;
  }

  .iconItemSeven:hover {
    background: url('../../../../assets/HJ/main/icohover_14.png') no-repeat center;
  }

  .iconSingOut {
    background: url('../../../../assets/HJ/main/singout.png') no-repeat center;
  }

  .iconSingOut:hover {
    background: url('../../../../assets/HJ/main/singout_hover.png') no-repeat center;
  }

  .systemSetIcon {
    background: url('../../../../assets/HJ/main/system-icon.png') no-repeat center;
  }

  .systemSetIcon:hover {
    background: url('../../../../assets/HJ/main/system-icon-hover.png') no-repeat center;
  }

  @media (max-width: 1490px) {
    .titleText .title_left {
      width: 426px;
      background: url('../../../../assets/HJ/home/1024/left-1-min.png') no-repeat;
    }

    .titleText .title_right {
      width: 426px;
      background: url('../../../../assets/HJ/home/1024/right-1-min.png') no-repeat;
    }

    .titleText .title_center .center {
      width: 76px;
      background: url('../../../../assets/HJ/home/1024/center-1-min.png') no-repeat;
    }

    .titleText .title_center .tile {
      background: url('../../../../assets/HJ/home/1024/center-title-1-min.png') repeat;
      width: calc((100% - 76px) / 2);
    }

    .goback {
      left: 1px;
      top: 6px;
    }

    .bread {
      left: 76px;
    }

    .title_left .menu {
      width: 240px;
      left: 90px;
    }

    .ipc {
      right: 1px;
      top: 6px;
    }

    .userInfo {
      right: 85px;
      left: 226px;
    }

    .dashboardTR .userInfo {
      right: 130px;
      left: 35px;
    }

    .equipment1 {
      left: 108px;
    }

    .equipment2 {
      left: 161px;
    }
  }

  @media (max-width: 1140px) {
    .titleText .logoText {
      transform: scale(0.8);
    }
  }
</style>