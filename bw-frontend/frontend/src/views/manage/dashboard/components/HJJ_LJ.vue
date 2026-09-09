<template>
  <div
    class="h-full w-full overflow-auto dashboard-page relative"
  >
    <div
      class="w-full h-full absolute overflow-hidden layout-center"
      @click="handleKB"
      style="z-index: 2"
    >
      <div class="homeTitle">
        <NipMenusHJJ @handleMenuClick="handleMenuClick" />
      </div>
      <!--      <nip-menus @handleMenuClick="handleMenuClick"/>-->
    </div>
    <div class="dashboardTR">
      <div class="userInfo layout-left-center" style="cursor: pointer" @click="openEditPasswordModel">
        <img
          :src="fileUrl + userInfo.userImg"
          v-if="userInfo.userImg && userInfo.userImg != ''"
          class="avatarImg"
        />
        <img
          :src="pagMan"
          v-else-if="userInfo.userSex == 1"
          class="avatarImg"
        />
        <img :src="pagWoman" v-else class="avatarImg" />
        <span
          class="nobr"
          :title="userInfo.userName"
          style="padding-left: 10px; max-width: 80px"
        >{{ userInfo.userName }}</span
        >
      </div>
      <a-popover class="min" trigger="click" overlayClassName="noPadding">
        <template #content>
          <div class="settingBox" :class="[interfaceStyle=='HJJ'?'HJJ':'LJ']">
            <div
              class="selectitem"
              v-if="userRole.id != '2'"
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
            <!--            <div class="border-top-style selectitem" @click="closeAnimation">-->
            <!--              <a-tooltip placement="left" >-->
            <!--                <template #title>-->
            <!--                  <div style="font-size: 12px;">{{cool?'关闭特效':'开启特效'}}</div>-->
            <!--                </template>-->
            <!--                <div v-if="cool" class="item-red layout-center iconItemThree"></div>-->
            <!--                <div v-else  class="item-red layout-center iconItemFour"></div>-->
            <!--              </a-tooltip>-->
            <!--            </div>-->
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
        <div class="ipc absolute">
          <div class="ico"></div>
        </div>
      </a-popover>
    </div>
    <div class="explain absolute" @click="noOff = !noOff">使用说明</div>
    <a-modal
      v-model:visible="noOff"
      :maskClosable="false"
      :footer="null"
      width="100%"
      wrapClassName="full-modal"
      class="noBottomBg"
    >
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
      <div></div>
      </template>
      <Personal></Personal>
    </a-modal>
    <NetSetting v-if="isOpen"></NetSetting>
  </div>
</template>
<script>
  export default {
    name: 'HJJ',
  }
</script>
<script setup>
  import NetSetting from "../../../../components/common/NetSetting.vue";
  import Personal from "../../../../components/personal/Personal.vue";
  import useNumRain from '../../../../common/utils/useNumRain.js'
  import { useRouter } from 'vue-router'
  import {changePassword} from '../../../../common/api/UserApi'
  import { createVNode, onMounted, ref, inject,provide } from 'vue'
  import { Ws } from '../../../../common/ws/Ws.js'
  import { Modal } from 'ant-design-vue'
  import { ExclamationCircleOutlined } from '@ant-design/icons-vue'
  import pagMan from '../../../../assets/HJJ/home-avatar-woman.png'
  import pagWoman from '../../../../assets/HJJ/home-avatar-man.png'
  import Instructions from "../../../../components/instructions/instructions.vue"
  import NipMenusHJJ from "../../../../components/dashboard/NipMenusHJJ_LJ.vue";
  import {message} from 'ant-design-vue'
  import {ipcRenderer, ipcApi} from '../../../../electron/index'
  import {closeSession} from '../../../../common/session/logout.js'
  // import homeTitle from '../../../../assets/HJJ/homeTitle.png'
  const interfaceStyle = window.interfaceStyle
  const isOpen = ref(false);
  const cool = inject('cool')
  const ipc = ref(ipcRenderer.isEE)
  const router = useRouter()
  const fileUrl = ref('')
  const systemPath = ref('')
  const userInfo = ref({})
  const userRole = ref({})
  const userModalVis = ref(false)
  const isShow = ref(false)
  const noOff = ref(false)
  useNumRain('numRain')
  const settingData = ref({
    dataUrl: "",
    fileUrl: "",
  });
  onMounted(() => {
    if (ipc.value) {
      getSettingData();
    }
    userInfo.value = JSON.parse(window.localStorage.getItem('userInfo'))
    userRole.value = JSON.parse(window.localStorage.getItem('userRole'))
    let routerData = JSON.parse(window.localStorage.getItem('userRouter'))
    fileUrl.value = window.fileUrl
    routerData.forEach((r) => {
      if (r.name === 'SystemManage') systemPath.value = r.path
    })
  })
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

  const getSettingData = async () => {
    const data = await ipcRenderer.ipc.invoke(ipcApi.ipcApiRoute.getConfig)
    settingData.value.dataUrl = data.dataUrl.url
    settingData.value.fileUrl = data.fileUrl.url
  };
  const openUserModal = () => {
    userModalVis.value = !userModalVis.value
  }
  let path = ''
  const handleMenuClick = (e, m) => {
    const q = e.children.find((item) => item.meta.isMenu)
    let path = ''
    if (q.children.length > 0) {
      const c = q.children.find((item) => item.meta.isMenu)
      path = `/preview/${m.path}/${e.path}/${q.path}/${c.path}`
    } else {
      path = `/preview/${m.path}/${e.path}/${q.path}`
    }
    if (path.indexOf('basicTheory') > -1) {
      router.push({
        path: path,
        query: {
          studyType: 0,
          key: 0,
        },
      })
    } else {
      router.push({
        path: path,
        query: {
          key: 0,
        },
      })
    }
  }
  const setPath = (e) => {
    let i = 0
    if (e.children[0]) {
      if (e.children[0].component == undefined) {
        i++
      }
      path = path + '/' + e.path
      setPath(e.children[i])
    }
  }
  const jumpSystemManage = () => {
    router.push(`/preview/${systemPath.value}`)
  }
  const goHandKeyTrain = (data) => {
    router.push({ path: data.path, query: { id: data.id } })
  }
  const handleKB = () => {
    userModalVis.value = false
  }
  const closeAnimation = () => {
    window.cool = !window.cool
    cool.value = window.cool
    localStorage.setItem('cool', cool.value)
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
      onCancel() {},
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
      },
    })
  }
  const openSetting = () => {
    isOpen.value = !isOpen.value;
  };
  provide('isOpen', isOpen)
  provide('noOff', noOff)
</script>

<style scoped lang="less">
  .homeTitle {
    width: 100%;
    position: absolute;
    top: 45%;
    left: 0;
    /*background: url("../../../../assets/HJJ/homeTitle.png") no-repeat;*/
    display: flex;
    align-items: flex-end;
  }
  .selectitem {
    cursor: pointer;
    transition: all 0.5s;
  }
  .userInfo {
    position: absolute;
    height: 45px;
    line-height: 45px;
    right: 110px;
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
  .HJJ{
    .selectitem:hover {
      background: #3c4a67;
    }
    .border-top-style {
      border-top: 1px solid;
      border-image: linear-gradient(
        90deg,
        rgba(24, 45, 86, 0.7) 15%,
        rgba(69, 92, 138, 1) 50%,
        rgba(31, 46, 75, 0.7) 85%
      )
      2 2 2 2;
    }
    .dashboardTR {
      width: 250px;
      height: 51px;
      background: url('../../../../assets/HJJ/bg.png') no-repeat center;
      position: absolute;
      right: 0;
      top: 15px;
      z-index: 9;
    }
    .avatarImg {
      height: 28px;
      width: 28px;
      border-radius: 50%;
    }
    .ico {
      background: url('../../../../assets/HJJ/home/setting.png');
      height: 35px;
      width: 33px;
      transition: all 0.5s;
    }
    .ipc {
      top: 0px;
      right: 0px;
      cursor: pointer;
      background: url('../../../../assets/HJJ/home/rightBg.png');
      width: 99px;
      height: 52px;
      display: flex;
      align-items: center;
      justify-content: center;
      padding-left: 30px;
    }
    .ipc:hover {
      /*background: url("../../../../assets/HJJ/home/setting_hover.png");*/
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
      padding-left: 44px;
      width: 120px;
      height: 25px;
      left: -20px;
      top: 15px;
      font-size: 12px;
      color: #bfcde0;
      line-height: 28px;
      z-index: 99;
      cursor: pointer;
    }
  }
  .LJ{
    .selectitem:hover {
      background: #2a4b49;
    }
    .border-top-style {
      border-top: 1px solid;
      border-image: linear-gradient(90deg, rgba(24, 86, 61, 0.7) 15%, rgb(69, 138, 84) 50%, rgba(31, 75, 37, 0.7) 85%) 2 2 2 2;
    }
    .dashboardTR {
      width: 250px;
      height: 51px;
      background: url('../../../../assets/LJ/bg.png') no-repeat center;
      position: absolute;
      right: 0;
      top: 15px;
      z-index: 9;
    }
    .avatarImg {
      height: 28px;
      width: 28px;
      border-radius: 50%;
    }
    .ico {
      background: url('../../../../assets/LJ/home/setting.png');
      height: 35px;
      width: 33px;
      transition: all 0.5s;
    }
    .ipc {
      top: 0px;
      right: 0px;
      cursor: pointer;
      background: url('../../../../assets/LJ/home/rightBg.png');
      width: 99px;
      height: 52px;
      display: flex;
      align-items: center;
      justify-content: center;
      padding-left: 30px;
    }
    .ipc:hover {
      /*background: url("../../../../assets/LJ/home/setting_hover.png");*/
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
      padding-left: 44px;
      width: 120px;
      height: 25px;
      left: -20px;
      top: 15px;
      font-size: 12px;
      color: #bfcde0;
      line-height: 28px;
      z-index: 99;
      cursor: pointer;
    }
  }
  .GD{
    .selectitem:hover {
      background: #2a4b49;
    }
    .border-top-style {
      border-top: 1px solid;
      border-image: linear-gradient(90deg, rgba(24, 86, 61, 0.7) 15%, rgb(69, 138, 84) 50%, rgba(31, 75, 37, 0.7) 85%) 2 2 2 2;
    }
    .dashboardTR {
      width: 250px;
      height: 51px;
      background: url('../../../../assets/GD/bg.png') no-repeat center;
      position: absolute;
      right: 0;
      top: 15px;
      z-index: 9;
    }
    .avatarImg {
      height: 28px;
      width: 28px;
      border-radius: 50%;
    }
    .ico {
      background: url('../../../../assets/GD/home/setting.png');
      height: 35px;
      width: 33px;
      transition: all 0.5s;
    }
    .ipc {
      top: 0px;
      right: 0px;
      cursor: pointer;
      background: url('../../../../assets/GD/home/rightBg.png');
      width: 99px;
      height: 52px;
      display: flex;
      align-items: center;
      justify-content: center;
      padding-left: 30px;
    }
    .ipc:hover {
      /*background: url("../../../../assets/GD/home/setting_hover.png");*/
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
      padding-left: 44px;
      width: 120px;
      height: 25px;
      left: -20px;
      top: 15px;
      font-size: 12px;
      color: #bfcde0;
      line-height: 28px;
      z-index: 99;
      cursor: pointer;
    }
  }
  .KJ{
    .selectitem:hover {
      background: #2a4b49;
    }
    .border-top-style {
      border-top: 1px solid;
      border-image: linear-gradient(90deg, rgba(24, 86, 61, 0.7) 15%, rgb(69, 138, 84) 50%, rgba(31, 75, 37, 0.7) 85%) 2 2 2 2;
    }
    .dashboardTR {
      width: 250px;
      height: 51px;
      background: url('../../../../assets/KJ/bg.png') no-repeat center;
      position: absolute;
      right: 0;
      top: 15px;
      z-index: 9;
    }
    .avatarImg {
      height: 28px;
      width: 28px;
      border-radius: 50%;
    }
    .ico {
      background: url('../../../../assets/KJ/home/setting.png');
      height: 35px;
      width: 33px;
      transition: all 0.5s;
    }
    .ipc {
      top: 0px;
      right: 0px;
      cursor: pointer;
      background: url('../../../../assets/KJ/home/rightBg.png');
      width: 99px;
      height: 52px;
      display: flex;
      align-items: center;
      justify-content: center;
      padding-left: 30px;
    }
    .ipc:hover {
      /*background: url("../../../../assets/KJ/home/setting_hover.png");*/
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
      padding-left: 44px;
      width: 120px;
      height: 25px;
      left: -20px;
      top: 15px;
      font-size: 12px;
      color: #bfcde0;
      line-height: 28px;
      z-index: 99;
      cursor: pointer;
    }
  }
</style>
