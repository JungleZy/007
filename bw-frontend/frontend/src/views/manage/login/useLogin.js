import { userLogin, addSignin } from '../../../common/api/UserApi.js'
import { message } from 'ant-design-vue'
import { isNavigationFailure, NavigationFailureType, useRouter } from 'vue-router'
import { readLoginDeviceId } from '../../../common/utils/machineCode'
import { explainAuthFailure } from '../../../common/http/index.js'
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
    }).catch((error) => {
      console.error('[login] 自动登录资料读取失败', error)
      message.warning('无法读取已保存的自动登录资料，请检查浏览器存储或手动输入；离线授权状态不因此改变')
    })

    nextTick().then(() => {
      fontSizeDispose()
    })
  })
  watch(autoLoginStatus, () => {
    if (!autoLoginStatus.value) {
      localforage.removeItem('autoLoginInfo').catch((error) => {
        console.error('[login] 自动登录资料清除失败', error)
        message.error('自动登录资料未能清除，请检查浏览器存储权限')
      })
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
  const onLogin = async () => {
    if (formState.isLoading) return
    if (!formState.username.trim()) {
      message.error('请输入用户名')
      return
    }
    if (!formState.password) {
      message.error('请输入密码')
      return
    }
    formState.isLoading = true
    formState.buttonMsg = '请稍后，正在登录中...'
    try {
      const deviceId = await readLoginDeviceId()
      const res = await userLogin({
        userAccount: formState.username,
        password: formState.password,
        deviceId
      })
      if (res.code !== 200) {
        message.error(explainAuthFailure(res.code) || res.message || res.description || '登录失败，请检查账号和密码')
        return
      }
      await createRouter(res.data)
      message.success('登录成功')
    } catch (error) {
      console.error('[login] 登录未完成', error)
      message.error(error.message || '登录未完成，请检查网络及本地存储后重试')
    } finally {
      formState.isLoading = false
      formState.buttonMsg = '登录'
    }
  }

  const createRouter = async (data) => {
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
    if (!data.token || !data.deviceId) throw new Error('登录响应缺少会话凭证，请联系管理员')
    try {
      const storage = window.localStorage
      const entries = [
        ['deviceId', data.deviceId],
        ['userInfo', JSON.stringify(data.user)],
        ['userRole', JSON.stringify(data.role)],
        ['userRouter', JSON.stringify(data.menus)]
      ]
      storage.removeItem('token')
      for (const [key, value] of entries) storage.setItem(key, value)
      storage.setItem('token', data.token)
    } catch (error) {
      console.error('[login] 会话保存失败', error)
      throw new Error('服务器已响应，但本地登录凭证保存失败，未进入系统；请恢复存储权限后重新登录。这不是离线授权失效')
    }
    try {
      if (autoLoginStatus.value) {
        await localforage.setItem('autoLoginInfo', {
          username: formState.username,
          password: formState.password
        })
      } else {
        await localforage.removeItem('autoLoginInfo')
      }
    } catch (error) {
      console.error('[login] 自动登录设置保存失败', error)
      message.warning('本次会话已保存，但自动登录设置未保存，下次请手动登录')
    }
    document.onkeydown = null
    const failure = await router.replace('/preview')
    const atHome = router.currentRoute.value.path === '/preview'
    const duplicated = isNavigationFailure(failure, NavigationFailureType.duplicated)
    if (!atHome || (failure && !duplicated)) {
      throw new Error('登录跳转未完成，请重新进入首页；若仍无法进入，请联系管理员', {cause: failure})
    }
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
