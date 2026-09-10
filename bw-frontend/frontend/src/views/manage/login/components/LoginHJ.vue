<template>
  <div
    class="w-full h-full overflow-hidden layout-left-center relative loginbg"
    style=" background-repeat: no-repeat;background-size: 100% 100%;background-position: center;">
    <video
      v-if="cool"
      src="../../../../assets/HJ/login/login.mp4"
      style="
        height: 100%;
        width: 100%;
        object-fit: fill;
        position: absolute;
        top: 0;
        left: 0;
      "
      autoplay
      muted
      loop
    ></video>
    <!--    <waves-page v-if="cool" class="absolute"/>-->
    <div class="h-full login layout-center" style="pointer-events: none">
      <!--      :style="{background:'url('+lm+')'}"-->
      <div
        class="w-full layout-center relative loginBox"
        style="
          height: 70%;
          background-repeat: no-repeat;
          background-size: cover;
          background-position: center;
        "
      >
        <div class="w-full layout-center absolute" style="top: 30px">
        </div>
        <div
          class="w-full content primary-color layout-center"
          style="pointer-events: auto"
        >
          <div class="layout-side-na main">
            <a-form
              :model="formState"
              class="w-full"
              v-if="formState.isLogin"
              style="width: 432px"
            >
              <a-form-item style="margin-bottom: 50px">
                <div class="bg" :class="[focusInput == 0 ? 'activebg' : '']">
                  <a-input
                    class="ipt"
                    size="large"
                    placeholder="请输入用户名"
                    @focus="inputFocus(0)"
                    v-model:value="formState.username"
                  >
                    <template #prefix>
                      <img :src="icoUser" style="height: 26px" />
                    </template>
                  </a-input>
                </div>
              </a-form-item>
              <a-form-item style="margin-bottom: 20px">
                <div class="bg" :class="[focusInput == 1 ? 'activebg' : '']">
                  <a-input-password
                    class="ipt"
                    size="large"
                    placeholder="请输入密码"
                    @focus="inputFocus(1)"
                    v-model:value="formState.password"
                  >
                    <template #prefix>
                      <img :src="icoPwd" style="height: 26px" />
                    </template>
                  </a-input-password>
                </div>
              </a-form-item>
              <a-form-item class="layout-side" style="height: 30px">
                <div class="w-full h-full layout-side">
                  <div class="addUser fs_dispose" @click="openModel">
                    注册用户
                  </div>
                  <a-checkbox v-model:checked="autoLoginStatus"
                  ><span class="fs_dispose" style="font-size: 14px"
                  >记住信息</span
                  ></a-checkbox
                  >
                </div>
              </a-form-item>
              <a-form-item class="layout-center" style="margin: 0">
                <div class="w-full h-full layout-center">
                  <div
                    class="loginBtn cursor-pointer-def layout-center fs_dispose"
                    :style="{
                      background: 'url(' + (cool ? lu : staticButton) + ')'
                    }"
                    @keyup.enter.native="onLogin"
                    @click="onLogin"
                  >
                    <!--                    {{ formState.buttonMsg }}-->
                    {{ formState.isLoading ? '登 录 中...' : '登 录' }}
                  </div>
                </div>
              </a-form-item>
            </a-form>
          </div>
        </div>
      </div>
    </div>

    <div
      class="customModalMask"
      v-show="addTrainModal"
      @click="addTrainModal = !addTrainModal"
    ></div>
    <div class="customModal" v-show="addTrainModal">
      <div class="title">
        <div class="text fs_dispose">注册用户</div>
      </div>
      <a-row style="padding-left: 20px" class="fs_dispose_min">
        <a-col :span="12">
          <div class="layout-left-center" style="margin-top: 20px">
            <div class="text"><strong>*</strong>用户名：</div>
            <a-input
              class="input_user"
              v-model:value="userData.userName"
              autocomlete="new-password"
              :class="[rule.userName ? 'error' : '']"
              placeholder="请输入用户名"
            ></a-input>
          </div>
          <div class="layout-left-center" style="margin-top: 20px">
            <div class="text"><strong>*</strong>身份证号：</div>
            <a-input
              class="input_user"
              v-model:value="userData.ID_number"
              :class="[rule.ID_number ? 'error' : '']"
              placeholder="请输入身份证号"
            ></a-input>
          </div>
          <div class="layout-left-center" style="margin-top: 20px">
            <div class="text"><strong>*</strong>联系电话：</div>
            <a-input
              class="input_user"
              v-model:value="userData.tel"
              :class="[rule.tel ? 'error' : '']"
              placeholder="请输入电话号码"
            ></a-input>
          </div>
          <div class="layout-left-center" style="margin-top: 20px">
            <div class="text"><strong>*</strong>登录密码：</div>
            <a-input
              class="input_user"
              :type="pwd.type"
              v-model:value="userData.password"
              :autocomlete="pwd.auto"
              :class="[rule.password ? 'error' : '']"
              placeholder="请输入登录密码"
              @change="changePassword"
            ></a-input>
          </div>
        </a-col>
        <a-col :span="12">
          <div class="layout-left-center" style="margin-top: 20px">
            <div class="text"><strong>*</strong>真实姓名：</div>
            <a-input
              class="input_user"
              v-model:value="userData.name"
              :class="[rule.name ? 'error' : '']"
              placeholder="请输入用户姓名"
            ></a-input>
          </div>
          <div class="layout-left-center" style="margin-top: 20px">
            <div class="text"><strong>*</strong>性别：</div>
            <a-select v-model:value="userData.userSex" class="input_user">
              <a-select-option value="0">女</a-select-option>
              <a-select-option value="1">男</a-select-option>
            </a-select>
          </div>
          <div class="layout-left-center" style="margin-top: 20px">
            <div class="text"><strong>*</strong>入伍时间：</div>
            <a-date-picker
              :locale="locale"
              class="input_user"
              inputReadOnly
              :disabled-date="disabledDate"
              v-model:value="userData.eday"
              :class="[rule.eday ? 'error' : '']"
              format="YYYY-MM-DD"
            />
          </div>
          <div class="layout-left-center" style="margin-top: 20px">
            <div class="text"><strong>*</strong>确认密码：</div>
            <a-input
              class="input_user"
              :type="pwd.type"
              v-model:value="userData.confirmpassword"
              :autocomlete="pwd.auto"
              :class="[rule.password ? 'error' : '']"
              placeholder="请确认登录密码"
            ></a-input>
          </div>
        </a-col>
      </a-row>
      <div class="w-full layout-center" style="margin-top: 30px">
        <div
          :class="{
            'registerBtn confirm fs_dispose': true,
            'btn-animate': !loading,
            loadingBtn: loading
          }"
          @click="addUser"
        >
          <a-spin v-if="loading" size="small" /> 立即注册
        </div>
        <div
          class="registerBtn cancel btn-animate fs_dispose"
          @click="addTrainModal = !addTrainModal"
        >
          取消
        </div>
      </div>
    </div>
  </div>
</template>

<script>
  export default {
    name: "LoginHJ"
  }
</script>
<script setup>
  import { reactive, ref, inject, nextTick } from 'vue'
  import {
    UserOutlined,
    LockOutlined,
    MobileOutlined,
    IdcardOutlined,
    MailOutlined,
    DownCircleFilled,
    UpCircleFilled
  } from '@ant-design/icons-vue'
  import useLogin from '../useLogin.js'
  import icoUser from '../../../../assets/HJ/login/input-user.png'
  import icoPwd from '../../../../assets/HJ/login/input-pwd.png'
  import lb from '../../../../assets/HJ/login/login-bg.png'
  import lm from '../../../../assets/HJ/login/login-mask.png'
  import ll from '../../../../assets/HJ/login/login-logo.png'
  import lu from '../../../../assets/HJ/login/login-button.gif'
  import staticButton from '../../../../assets/HJ/login/staticButton.png'
  import WavesPage from '../../../../components/common/Waves.vue'
  import moment from 'moment'
  import locale from 'ant-design-vue/es/locale/zh_CN'
  import { fontSizeDispose } from '../../../../common/utils/Utils'
  const formState = reactive({
    buttonMsg: '',
    username: '',
    password: '',
    isLoading: false,
    isLogin: true,
    choose: false
  })
  const focusInput = ref(-1)
  const cool = inject('cool')
  const pwd = ref({ type: 'text', auto: 'off' })
  const disabledDate = (current = Moment) => {
    return current && current > moment().endOf('day')
  }
  const openModel = () => {
    addTrainModal.value = true
  }
  document.onkeydown = (e) => {
    //按下回车提交
    e = window.event || e
    if (e.key === 'Enter' || e.key === 'enter') {
      onLogin()
    }
  }
  const inputFocus = (index) => {
    focusInput.value = index
  }
  const changePassword = (e) => {
    if (e.target.value !== '') {
      pwd.value.type = 'password'
      pwd.value.auto = 'new-password'
    } else {
      pwd.value.type = 'text'
      pwd.value.auto = 'off'
    }
  }
  const {
    onLogin,
    autoLoginStatus,
    loading,
    addUser,
    userData,
    rule,
    addTrainModal
  } = useLogin(formState)
</script>
<style scoped>
@import "../css/HJ.css";
</style>