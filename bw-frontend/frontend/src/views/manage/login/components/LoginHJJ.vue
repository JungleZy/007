<template>
  <div class="w-full h-full layout-left-center relative loginbg" style="background-repeat: no-repeat; background-size: cover; background-position: center">
    <!--    <video v-if="cool" src="../../../../assets/HJJ/login/login.mp4" style="height: 100%;width: 100%;object-fit: fill;position: absolute;top: 0;left: 0"-->
    <!--           autoplay muted loop></video>-->
    <!--    <waves-page v-if="cool" class="absolute"/>-->
    <div class="h-full relative login layout-center" style="pointer-events: none">
      <div class="w-full layout-center relative inputs">
        <div class="w-full content primary-color layout-center" style="pointer-events: auto">
          <div class="layout-side-na main">
            <a-form :model="formState" class="w-full" v-if="formState.isLogin" style="width: 100%; display: flex; align-items: center">
              <a-form-item>
                <div class="bg" :class="[focusInput == 0 ? 'activebg' : '']">
                  <a-input class="ipt" size="large" placeholder="请输入用户名" @focus="inputFocus(0)" v-model:value="formState.username">
                    <template #prefix>
                      <img :src="icoUser" style="height: 26px" />
                    </template>
                  </a-input>
                </div>
              </a-form-item>
              <a-form-item style="margin-right: 30px !important; margin-left: 30px !important">
                <div class="bg" :class="[focusInput == 1 ? 'activebg' : '']">
                  <a-input-password class="ipt" size="large" placeholder="请输入密码" @focus="inputFocus(1)" v-model:value="formState.password">
                    <template #prefix>
                      <img :src="icoPwd" style="height: 26px" />
                    </template>
                  </a-input-password>
                </div>
              </a-form-item>
              <a-form-item class="layout-center" style="margin-bottom: 20px">
                <div class="w-full h-full layout-center">
                  <div class="loginBtn cursor-pointer-def layout-center relative" style="background-position: center; background-repeat: no-repeat; background-size: contain" @keyup.enter.native="onLogin" @click="onLogin">
                    <img :src="staticButton" alt="" class="loginImg" />
                    <div v-if="formState.isLoading" style="position: absolute; padding-left: 10px">登录中...</div>
                    <div v-else style="position: absolute; padding-left: 10px">登 录</div>
                  </div>
                </div>
              </a-form-item>
            </a-form>
          </div>
        </div>
      </div>
    </div>
    <div class="w-full userInfo">
      <div style="display: flex">
        <a-checkbox v-model:checked="autoLoginStatus" @mouseenter="remenmberEnter" @mouseleave="remenmberLeave" style="color: #e2d6b0; font-size: 14px">记住信息</a-checkbox>
        <div class="addUser" @click="openModel" @mouseenter="addUserEnter" @mouseleave="addUserLeave">{{ '<注册用户>' }}</div>
      </div>
      <div class="line"></div>
      <div class="lineLight"></div>
    </div>

    <div class="customModalMask" v-show="addTrainModal" @click="addTrainModal = !addTrainModal"></div>
    <div class="customModal" v-show="addTrainModal">
      <div style="position: relative">
        <div class="registerBG">
          <div class="title">用户注册</div>
          <div class="close" @click="addTrainModal = !addTrainModal"></div>
        </div>
        <a-row style="padding-left: 20px" class="center">
          <a-col :span="12">
            <div class="layout-left-center" style="margin-top: 40px">
              <div class="text"><strong>*</strong>用户名：</div>
              <a-input class="input_user" v-model:value="userData.userName" autocomlete="new-password" :class="[rule.userName ? 'error' : '']" placeholder="请输入用户名"></a-input>
            </div>
            <div class="layout-left-center" style="margin-top: 40px">
              <div class="text"><strong>*</strong>身份证号：</div>
              <a-input class="input_user" @blur="idCardMessage" @input="inputIdCard" v-model:value="userData.ID_number" :class="[rule.ID_number ? 'error' : '']" placeholder="请输入身份证号"></a-input>
            </div>
            <div class="layout-left-center" style="margin-top: 40px">
              <div class="text"><strong>*</strong>联系电话：</div>
              <a-input class="input_user" v-model:value="userData.tel" :class="[rule.tel ? 'error' : '']" placeholder="请输入电话号码"></a-input>
            </div>
            <div class="layout-left-center" style="margin-top: 40px">
              <div class="text"><strong>*</strong>登录密码：</div>
              <a-input class="input_user" :type="pwd.type" v-model:value="userData.password" :autocomlete="pwd.auto" :class="[rule.password ? 'error' : '']" placeholder="请输入登录密码" @change="changePassword"></a-input>
            </div>
          </a-col>
          <a-col :span="12">
            <div class="layout-left-center" style="margin-top: 40px">
              <div class="text"><strong>*</strong>真实姓名：</div>
              <a-input class="input_user" v-model:value="userData.name" :class="[rule.name ? 'error' : '']" placeholder="请输入用户姓名"></a-input>
            </div>
            <div class="layout-left-center" style="margin-top: 40px">
              <div class="text"><strong>*</strong>性别：</div>
              <a-select v-model:value="userData.userSex" style="height: 36px" class="input_user">
                <a-select-option value="0">女</a-select-option>
                <a-select-option value="1">男</a-select-option>
              </a-select>
            </div>
            <div class="layout-left-center" style="margin-top: 40px">
              <div class="text"><strong>*</strong>入伍时间：</div>
              <a-date-picker :locale="locale" class="input_user" :disabled-date="disabledDate" v-model:value="userData.eday" :class="[rule.eday ? 'error' : '']" show-time format="YYYY-MM-DD"></a-date-picker>
            </div>
            <div class="layout-left-center" style="margin-top: 40px">
              <div class="text"><strong>*</strong>确认密码：</div>
              <a-input class="input_user" :type="pwd.type" v-model:value="userData.confirmpassword" :autocomlete="pwd.auto" :class="[rule.password ? 'error' : '']" placeholder="请确认登录密码"></a-input>
            </div>
          </a-col>
        </a-row>
        <div class="w-full layout-center bottom" style="">
          <div :class="{ 'registerBtn confirm': true, 'btn-animate': !loading, loadingBtn: loading }" @click="addUser"><a-spin v-if="loading" size="small" /> 立即注册</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
  export default {
    name: "LoginHJJ"
  }
</script>
<script setup>
  import { reactive, ref, inject, nextTick } from 'vue'
  import { UserOutlined, LockOutlined, MobileOutlined, IdcardOutlined, MailOutlined, DownCircleFilled, UpCircleFilled } from '@ant-design/icons-vue'
  import useLogin from '../useLogin.js'
  import icoUser from '../../../../assets/HJJ/login/input-user.png'
  import icoPwd from '../../../../assets/HJJ/login/input-pwd.png'
  import lb from '../../../../assets/HJJ/login/login-bg.png'
  //登录按钮
  import lu from '../../../../assets/HJJ/login/staticButton.png'
  import staticButton from '../../../../assets/HJJ/login/staticButton.png'

  import moment from 'moment'
  import locale from 'ant-design-vue/es/locale/zh_CN'
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

  document.onkeydown = e => {
    //按下回车提交
    e = window.event || e
    if (e.key === 'Enter' || e.key === 'enter') {
      onLogin()
    }
  }
  const inputFocus = index => {
    focusInput.value = index
  }
  const changePassword = e => {
    if (e.target.value !== '') {
      pwd.value.type = 'password'
      pwd.value.auto = 'new-password'
    } else {
      pwd.value.type = 'text'
      pwd.value.auto = 'off'
    }
  }
  const remenmberEnter = () => {
    anime({
      targets: '.lineLight',
      easing: 'easeInOutQuad',
      duration: 250,
      left: '43%',
      opacity: 1
    })
  }
  const remenmberLeave = () => {
    anime({
      targets: '.lineLight',
      easing: 'easeInOutQuad',
      opacity: {
        value: 0,
        duration: 1
      },
      left: {
        value: '35%',
        duration: 250
      }
    })
  }
  const addUserEnter = () => {
    anime({
      targets: '.lineLight',
      easing: 'easeInOutQuad',
      duration: 250,
      left: '48%',
      opacity: 1
    })
  }
  const addUserLeave = () => {
    anime({
      targets: '.lineLight',
      easing: 'easeInOutQuad',
      opacity: {
        value: 0,
        duration: 1
      },
      left: {
        value: '35%',
        duration: 250
      }
    })
  }
  const { onLogin, autoLoginStatus, loading, addUser, inputIdCard, idCardMessage, userData, rule, addTrainModal } = useLogin(formState)
</script>

<style scoped>
  .cool .loginbg {
    background: url('../../../../assets/HJJ/login/staticBG-hj.png');
  }
  .loginbg {
    background: url('../../../../assets/HJJ/login/staticBG-hj.png');
  }
  .inputs {
    height: 70%;
    background-repeat: no-repeat;
    background-size: cover;
    background-position: center;
    display: flex;
    align-items: flex-end;
  }
  .userInfo {
    position: absolute;
    bottom: 30px;
    color: #6e6b61;
    display: flex;
    flex-direction: column;
    align-items: center;
  }
  .line {
    background: url('../../../../assets/HJJ/login/line.png');
    height: 32px;
    width: 100%;
    max-width: 1187px;
    position: relative;
  }
  .lineLight {
    position: absolute;
    top: 32px;
    background: url('../../../../assets/HJJ/login/line-l.png');
    width: 186px;
    height: 11px;
    left: 35%;
    opacity: 0;
  }
  .loginImg {
    opacity: 0.6;
  }
  .loginBtn:hover .loginImg {
    opacity: 1;
  }
  .error {
    border: 1px solid red !important;
  }
  .input_user {
    width: 250px;
    height: 36px;
  }
  .text {
    color: white;
    width: 90px;
    text-align: right;
  }
  .text strong {
    color: #f00;
    margin-right: 5px;
  }
  .addUser {
    color: #6e6b61;
    padding-left: 20px;
    cursor: pointer;
  }
  .addUser:hover {
    color: #e2d6b0;
  }
  .bg {
    background-image: url('../../../../assets/HJJ/login/inputBG.png');
    background-size: 100% 100%;
    padding: 3px 20px;
    transition: all 1s;
    width: 406px;
    height: 56px;
  }
  .activebg {
    background-image: url('../../../../assets/HJJ/login/inputBG.png');
  }
  .bg >>> .ant-input-affix-wrapper,
  .bg >>> .ant-input-affix-wrapper:focus,
  .bg >>> .ant-input-affix-wrapper:hover,
  .activebg >>> .ant-input-affix-wrapper:focus {
    border-right-width: 0 !important;
    box-shadow: none !important;
  }
  .login {
    z-index: 3;
    width: 100%;
    height: 100%;
  }

  .login .top {
    font-size: 28px;
    font-weight: bolder;
    color: #1a1a1a;
    padding-left: 20px;
  }

  .login .main {
    justify-content: flex-start;
    display: flex;
    align-items: flex-end;
    padding-bottom: 50px;
  }

  .login .register {
    height: calc(100vh - 100px);
    margin-top: 130px;
  }

  .login .loginTab {
    font-size: 18px;
    font-weight: bolder;
    color: #0076fe;
    padding-left: 36px;
    margin-bottom: 30px;
    position: relative;
  }

  .login .loginTab:before {
    content: '';
    width: 4px;
    height: 16px;
    background-color: #0076fe;
    position: absolute;
    left: 20px;
    top: 7px;
  }

  .login .ipt {
    border: none;
    /*background-color: #e2fcff;*/
    /*background-image:url("../../../../assets/HJJ/login/inputBG.png");*/
    /*background-size: 100% 100%;*/
    padding: 12px 20px;
    line-height: 26px;
    /*border-radius: 30px;*/
    background: rgba(0, 0, 0, 0);
    width: 100%;
  }
  .login .ipt >>> .ant-input {
    background-color: rgba(0, 0, 0, 0) !important;
    padding-left: 15px;
    box-shadow: none !important;
    border: none;
    color: #ffffff;
  }
  .login .ipt >>> .ant-input:-webkit-autofill {
    -webkit-text-fill-color: #ffffff;
    color: #ffffff;
  }
  .login .ipt >>> .ant-input::placeholder {
    color: #6e6b61 !important;
  }
  .login .ipt >>> .ant-input:focus,
  .login .ipt >>> .ant-input:hover {
    border: 0px !important;
  }
  .login .ipt >>> .anticon {
    color: #6e6b61 !important;
  }

  .login .ipt >>> .ant-calendar-picker-icon {
    width: auto;
    height: auto;
    left: 20px;
    margin-top: -10px;
  }

  .login .ipt >>> .ant-calendar-picker-input.ant-input {
    padding-left: 35px;
  }

  .login .ipt >>> .ant-select-selector {
    border: none !important;
    background-color: transparent;
  }

  .login .desc {
    padding: 0 20px;
    margin-bottom: 30px;
  }

  .login .desc .text {
    color: #0076fe;
    font-size: 14px;
  }

  .login .chooseText {
    color: #999;
    position: relative;
    padding: 0 56px;
    cursor: pointer;
  }

  .login .chooseText:before,
  .login .chooseText:after {
    content: '';
    width: 40px;
    height: 1px;
    background-color: #e6ebf1;
    position: absolute;
    top: 10px;
  }

  .login .chooseText:before {
    left: 0;
  }

  .login .chooseText:after {
    right: 0;
  }

  .login .loginBtn {
    height: 56px;
    width: 151px;
    font-size: 18px;
    font-weight: bold;
    color: #e2d6b0;
    letter-spacing: 10px;
    text-align: center;
  }
</style>