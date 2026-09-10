import {configureRuntime} from './config/runtime.js'

// 业务模块会在导入时读取URL；异步IPC配置完成前不能导入它们。
configureRuntime().then(() => import('./main.js')).catch(error => {
  const errorMask = document.getElementById('errorMask')
  if (errorMask) {
    errorMask.style.display = 'block'
    errorMask.textContent = `应用启动失败：${error.message}`
  }
  console.error('Application initialization failed', error)
})
