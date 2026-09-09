import axios from 'axios'
import {message, Modal} from 'ant-design-vue';
//创建axios的一个实例
console.log(window.httpUrl)
const instance = axios.create({
  baseURL: window.httpUrl.indexOf("http")>-1?`${window.httpUrl}`:`http://${window.httpUrl}`,
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json;charset=UTF-8;'
  }
});
//请求拦截器
instance.interceptors.request.use((config) => {
  // 每次发送请求之前判断是否存在token，如果存在，则统一在http请求的header都加上token，不用每次请求都手动添加了
  const token = window.localStorage.getItem('token');
  const deviceId = window.localStorage.getItem('deviceId');
  token && (config.headers.token = token);
  deviceId && (config.headers.deviceId = deviceId);
  //若请求方式为post，则将data参数转为JSON字符串
  if (config.method === 'POST') {
    config.data = JSON.stringify(config.data);
  }
  return config;
}, (error) =>
  // 对请求错误做些什么
  Promise.reject(error));

let authPromptOpen = false

//响应拦截器
instance.interceptors.response.use((response) => {
  const code = response.data?.code
  if (code === 203 || code === 204 || code === 206) {
    if (location.hash.split('?')[0] !== '#/login' && !authPromptOpen) {
      authPromptOpen = true
      Modal.error({
        keyboard: false,
        title: '您的登录唯一凭证异常',
        content: '请点击下方按钮返回登录页面重新登录本系统',
        okText: '返回登录页面',
        afterClose() {
          authPromptOpen = false
        },
        onOk() {
          location.href = '#/login'
        }
      })
    }
  }
  if (typeof code === 'number' && code !== 200 && code !== 203 && code !== 204 && code !== 206
      && !response.config?.skipErrorToast) {
    message.error(response.data.message || '请求失败')
  }
  return response.data
}, (error) => {
  if (error.response && error.response.status) {
    let msg = ''
    const status = error.response.status
    switch (status) {
      case 400:
        msg = '请求错误'
        break
      case 401:
        msg = '请求错误'
        break
      case 404:
        msg = '请求地址出错'
        break
      case 408:
        msg = '请求超时'
        break
      case 500:
        msg = '服务器内部错误!'
        break
      case 501:
        msg = '服务未实现!'
        break
      case 502:
        msg = '网关错误!'
        break
      case 503:
        msg = '服务不可用!'
        break
      case 504:
        msg = '网关超时!'
        break
      case 505:
        msg = 'HTTP版本不受支持'
        break
      default:
        msg = '请求失败'
    }
    if (!error.config?.skipErrorToast) message.error(msg)
  } else if (!error.config?.skipErrorToast) {
    message.error(error.code === 'ECONNABORTED' || error.code === 'ETIMEDOUT'
      ? '请求超时，请稍后重试'
      : '网络连接失败，请检查网络')
  }
  return Promise.reject(error)
})


export default instance;