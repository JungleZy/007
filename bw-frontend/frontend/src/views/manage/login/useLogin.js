import { userLogin, addSignin } from '../../../common/api/UserApi.js'
import { message } from 'ant-design-vue'
import { useRouter } from 'vue-router'
import FingerprintJS from '@fingerprintjs/fingerprintjs'
import { nextTick, onMounted, ref, watch } from 'vue'
import { fontSizeDispose } from '../../../common/utils/Utils'

export default function (formState) {
  const router = useRouter()
  const autoLoginStatus = ref(false)
  const loading = ref(false)

  onMounted(() => {
    localforage.getItem('autoLoginInfo').then((ali) => {
      if (ali !== null) {
        autoLoginStatus.value = true
        formState.username = ali.username
        formState.password = ali.password
      }
    })

    nextTick().then(() => {
      fontSizeDispose()
    })
  })
  watch(autoLoginStatus, () => {
    if (!autoLoginStatus.value) {
      localforage.removeItem('autoLoginInfo')
    }
  })
  const userData = ref({
    userName: '',
    name: '',
    tel: '',
    ID_number: '',
    password: '',
    userSex: '1',
    eday: '',
    confirmpassword: ''
  })
  const rule = ref({
    userName: false,
    name: false,
    tel: false,
    ID_number: false,
    password: false,
    eday: false
  })
  const addTrainModal = ref(false)
  const onLogin = () => {
    if (formState.isLoading) {
      return
    }
    formState.buttonMsg = '请稍后，正在登录中...'
    if (!formState.username.trim()) {
      message.error('请输入用户名')
      return
    }
    if (!formState.password) {
      message.error('请输入密码')
      return
    }
    formState.isLoading = true
    FingerprintJS.load()
        .then((fp) => {
          fp.get()
              .then((result) => {
                userLogin({
                  userAccount: formState.username,
                  password: formState.password,
                  deviceId: result.visitorId
                }).then((res) => {
                  if (res.code === 200) {
                    formState.buttonMsg = '登录成功，正在跳转...'
                    message.success('登录成功，正在跳转...')
                    createRouter(res.data)
                  } else {
                    message.error(res.description)
                    formState.isLoading = false
                    formState.buttonMsg = '登录'
                  }
                })
              })
              .catch((error) => {
                // console.log(error);
                message.error('浏览器唯一标识获取失败，请检查浏览器')
                formState.isLoading = false
                formState.buttonMsg = '登录'
              })
        })
        .catch((error) => {
          // console.log(error);
          message.error('浏览器唯一标识获取失败，请检查浏览器')
          formState.isLoading = false
          formState.buttonMsg = '登录'
        })
  }

  const createRouter = (data) => {
    const interfaceStyle = window.interfaceStyle
    if(interfaceStyle=="HJ"){
      //过滤火报务路由
      data.menus = data.menus.filter(item=>item.path.indexOf("HJJ")<0)
      data.menus.forEach(item=>{
        if(item.name=='NetworkUsing'){
          item.children[0].children = item.children[0].children.filter(r=>r.path.indexOf("HJJ")<0)
        }
      })
    }else {
      //过滤海报务路由
      data.menus = data.menus.filter(item=>item.path.indexOf("HJBW")<0)
      data.menus.forEach(item=>{
        if(item.name=='NetworkUsing'){
          item.children[0].children = item.children[0].children.filter(r=>r.path.indexOf("HJBW")<0)
        }
      })
    }

    console.log(data.menus)
    window.localStorage.setItem('token', data.token)
    window.localStorage.setItem('deviceId', data.deviceId)
    window.localStorage.setItem('userInfo', JSON.stringify(data.user))
    window.localStorage.setItem('userRole', JSON.stringify(data.role)) // 注意当登录接口换成V2版本时请校对返回的参数，该处会从role->roles
    window.localStorage.setItem('userRouter', JSON.stringify(data.menus))
    if (autoLoginStatus.value) {
      localforage.setItem('autoLoginInfo', {
        username: formState.username,
        password: formState.password
      })
    } else {
      localforage.removeItem('autoLoginInfo')
    }
    setTimeout(() => {
      document.onkeydown = null
      router.replace('/preview').then()
    }, 1000)
  }
  const addUser = () => {
    if (loading.value) return false
    const reg5 = /^[\u4e00-\u9fa50-9A-Za-z]{6,13}$/
    if (userData.value.userName.trim() == '') {
      message.error('请输入用户名！')
      rule.value.userName = true
      return false
    } else if (!reg5.test(userData.value.userName)) {
      message.error('请输入4-16位长度的合法用户名！')
      rule.value.userName = true
      return false
    } else {
      rule.value.userName = false
    }
    const reg4 = /^[\u4e00-\u9fa5]+$/
    if (userData.value.name === '') {
      message.error('请输入用户真实姓名！')
      rule.value.name = true
      return false
    } else if (!reg4.test(userData.value.name)) {
      message.error('真实姓名只能有汉字组成！')
      rule.value.name = true
      return false
    } else {
      rule.value.name = false
    }
    const reg = /^1[3456789]\d{9}$/
    if (!userData.value.tel || userData.value.tel === '') {
      message.error('请输入电话号码！')
      rule.value.tel = true
      return false
    } else if (!reg.test(userData.value.tel)) {
      message.error('请输入有效的电话号码！')
      rule.value.tel = true
      return false
    } else {
      rule.value.tel = false
    }
    const reg2 =
        /(^[1-9]\d{5}(18|19|20)\d{2}((0[1-9])|(1[0-2]))(([0-2][1-9])|10|20|30|31)\d{3}[0-9Xx]$)/
    if (!userData.value.ID_number || userData.value.ID_number === '') {
      message.error('请输入身份证号！')
      rule.value.ID_number = true
      return false
    } else if (!reg2.test(userData.value.ID_number)) {
      message.error('请输入合法的身份证号！')
      rule.value.ID_number = true
      return false
    } else {
      rule.value.ID_number = false
    }
    if (userData.value.eday == '') {
      message.error('请选择入伍时间！')
      rule.value.eday = true
      return false
    } else {
      rule.value.eday = false
    }
    const reg3 = /^[0-9A-Z]{6,13}$/
    if (userData.value.password == '' && !reg3.test(userData.value.password)) {
      message.error('请输入数字或字母组合的6-13位长度的密码！')
      rule.value.password = true
      return false
    } else {
      rule.value.password = false
    }
    if (
        !userData.value.confirmpassword ||
        userData.value.confirmpassword == ''
    ) {
      message.error('请输入确认密码！')
      rule.value.password = true
      return false
    } else if (userData.value.password !== userData.value.confirmpassword) {
      message.error('两次密码不一致！')
      rule.value.password = true
      return false
    } else {
      rule.value.password = false
    }
    const data = {
      userAccount: userData.value.userName,
      phone: userData.value.tel,
      userName: userData.value.name,
      idCard: userData.value.ID_number,
      userSex: userData.value.userSex,
      password: userData.value.password,
      eday: userData.value.eday.format('YYYY-MM-DD'),
      userImg: '/userImages/pag-avatar-man.png'
    }
    if (data.userSex == 0) {
      data.userImg = '/userImages/pag-avatar-woman.png'
    }
    loading.value = true
    addSignin(data).then((res) => {
      if(res.code===200){
        userData.value.userName = ''
        userData.value.tel = ''
        userData.value.ID_number = ''
        userData.value.password = ''
        userData.value.confirmpassword = ''
        userData.value.userSex = '1'
        userData.value.eday = ''
        userData.value.name = ''
        addTrainModal.value = false
        message.success('添加成功！')
      }else {
        loading.value = false
        message.error(res.message)
      }

    })
    // console.log(111111111)
  }
  //限制身份证号只能数字或字母最长18位
  const inputIdCard = e => {
    userData.value.ID_number = e.target.value.replace(/[^a-zA-Z\d]/g, '')
    if (userData.value.ID_number.length > 18) {
      const newM = userData.value.ID_number.substr(0, 18)
      userData.value.ID_number = newM
    }
  }
  //身份证验证
  const idCardMessage = () => {
    const that = this
    if (!userData.value.ID_number) {
      return
    }
    let reg = /(^\d{15}$)|(^\d{18}$)|(^\d{17}(\d|X|x)$)/
    if (reg.test(userData.value.ID_number) === false) {
      message.error('身份证号格式错误！')
      userData.value.ID_number = ''
      return
    }
    const dataTure = userData.value.ID_number.substr(6, 8)
    if (parseInt(dataTure) >= parseInt(moment().format('YYYYMMDD'))) {
      message.error('身份证号格式错误！')
      userData.value.ID_number = ''
      return
    }
    const y = userData.value.ID_number.substr(6, 4)
    const m = userData.value.ID_number.substr(10, 2)
    const d = userData.value.ID_number.substr(12, 2)
    if (parseInt(m) === 0 || parseInt(m) > 12) {
      message.error('身份证号格式错误！')
      userData.value.ID_number = ''
      return
    }
    if (parseInt(d) === 0 || parseInt(m) > 31) {
      message.error('身份证号格式错误！')
      userData.value.ID_number = ''
      return
    }
    if (parseInt(userData.value.ID_number.substr(16, 1)) % 2 === 0) {
      userData.value.userSex = '0'
    } else {
      userData.value.userSex = '1'
    }
  }
  return {
    userData,
    rule,
    addTrainModal,
    loading,
    addUser,
    onLogin,
    autoLoginStatus,
    inputIdCard,
    idCardMessage
  }
}
