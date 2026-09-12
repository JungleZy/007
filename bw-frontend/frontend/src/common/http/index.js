import axios from 'axios'
import {message, Modal} from 'ant-design-vue';
import {apiUrl} from './endpoint.js'
//创建axios的一个实例
console.log(window.httpUrl)
const instance = axios.create({
  baseURL: apiUrl(),
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json;charset=UTF-8;'
  }
});
//请求拦截器
instance.interceptors.request.use((config) => {
  // 每次发送请求之前判断是否存在token，如果存在，则统一在http请求的header都加上token，不用每次请求都手动添加了
  let token
  let deviceId
  try {
    token = window.localStorage.getItem('token');
    deviceId = window.localStorage.getItem('deviceId');
  } catch (error) {
    const failure = new Error('无法读取本地登录凭证，请恢复浏览器存储权限后重新登录；这不代表离线授权失效')
    failure.loginStorageError = true
    throw failure
  }
  if (config.expectedToken !== undefined || config.expectedDeviceId !== undefined) {
    if (!config.expectedToken || !config.expectedDeviceId
        || config.expectedToken !== token || config.expectedDeviceId !== deviceId) {
      const failure = new Error('登录会话已变更，本次提交已停止；请返回原会话处理尚未提交的数据')
      failure.sessionChanged = true
      failure.config = config
      throw failure
    }
  }
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
const authMessages = {
  203: {title: 'token不能为空', detail: '请求未携带后端登录凭证，可能尚未登录或本地会话记录已被清理。请重新登录。'},
  204: {title: '设备标识不能为空', detail: '请求未携带当前会话的设备标识，请重新登录。不会在请求途中生成新标识替换有效会话。'},
  206: {title: '账号登录凭证异常', detail: '后端登录凭证与当前记录不匹配，可能在其他位置登录、已退出或会话记录已变更；无法仅凭此响应确定原因。请重新登录。'}
}

export function explainAuthFailure(code) {
  const explanation = authMessages[code]
  return explanation ? `${explanation.title}：${explanation.detail} 此提示不是离线授权校验结果。` : ''
}

//响应拦截器
instance.interceptors.response.use((response) => {
  const code = response.data?.code
  if (code === 203 || code === 204 || code === 206) {
    const explanation = authMessages[code]
    if (location.hash.split('?')[0] === '#/login' && !response.config?.skipErrorToast) {
      message.error(explainAuthFailure(code))
    }
    if (location.hash.split('?')[0] !== '#/login' && !authPromptOpen) {
      authPromptOpen = true
      Modal.error({
        keyboard: false,
        title: explanation.title,
        content: `${explanation.detail} 后端登录与本机离线授权相互独立，请勿因此清除授权信息。`,
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
  if (error.sessionChanged) {
    if (!error.config?.skipErrorToast) message.error(error.message)
  } else if (error.loginStorageError) {
    message.error(error.message)
  } else if (error.response && error.response.status) {
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