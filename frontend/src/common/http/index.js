import axios from 'axios'
import {message, Modal} from 'ant-design-vue';
//创建axios的一个实例
console.log(window.httpUrl)
const instance = axios.create({
  baseURL: window.httpUrl.indexOf("http")>-1?`${window.httpUrl}`:`http://${window.httpUrl}`, //接口统一域名
  // timeout: 6000, //设置超时
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

//响应拦截器
instance.interceptors.response.use((response) => {
  if (response.data.code === 203 || response.data.code === 204) {
    if(location.href.indexOf('login')>-1){
      return
    }
    Modal.destroyAll()
    Modal.error({
      keyboard: false,
      title: '您的登录唯一凭证异常',
      content: '请点击下方按钮返回登录页面重新登录本系统',
      okText: '返回登录页面',
      onOk() {
        location.href = '#/login';
      }
    })
  }
  if (response.data.code === 205) {
    Modal.destroyAll()
    Modal.error({
      keyboard: false,
      title: '您的登录唯一凭证已过期',
      content: '请点击下方按钮返回登录页面重新登录本系统',
      okText: '返回登录页面',
      onOk() {
        location.href = '#/login';
      }
    })
  }
  if (response.data.code === 206) {
    Modal.destroyAll()
    Modal.error({
      keyboard: false,
      title: '您的账号已被异地登录',
      content: '请点击下方按钮返回登录页面重新登录本系统',
      okText: '返回登录页面',
      onOk() {
        location.href = '#/login';
      }
    })
  }
  return response.data;
}, (error) => {//响应错误
  if (error.response && error.response.status) {
    let msg = "";
    const status = error.response.status
    switch (status) {
      case 400:
        msg = '请求错误';
        break;
      case 401:
        msg = '请求错误';
        break;
      case 404:
        msg = '请求地址出错';
        break;
      case 408:
        msg = '请求超时';
        break;
      case 500:
        msg = '服务器内部错误!';
        break;
      case 501:
        msg = '服务未实现!';
        break;
      case 502:
        msg = '网关错误!';
        break;
      case 503:
        msg = '服务不可用!';
        break;
      case 504:
        msg = '网关超时!';
        break;
      case 505:
        msg = 'HTTP版本不受支持';
        break;
      default:
        msg = '请求失败'
    }
    message.error(msg);
    return Promise.reject(error);
  }
  return Promise.reject(error);
});


export default instance;