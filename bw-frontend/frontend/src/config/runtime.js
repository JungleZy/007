import {endpointUrl} from '../common/http/endpoint.js'
import {ipc, isEE} from '../electron/ipcRenderer.js'

const securePage = window.location.protocol === 'https:'

function serviceAddress(raw, websocket = false) {
  if (typeof raw !== 'string' || !raw.trim() || raw.startsWith('/')) {
    throw new Error('服务地址必须是主机名或完整URL')
  }
  if (raw.includes('://') && !/^(?:https?|wss?):\/\//i.test(raw)) {
    throw new Error('服务地址仅支持HTTP/HTTPS或WS/WSS')
  }
  const address = new URL(endpointUrl(raw, '', websocket))
  const protocols = websocket ? ['ws:', 'wss:'] : ['http:', 'https:']
  if (!protocols.includes(address.protocol) || address.username || address.password || address.search || address.hash) {
    throw new Error('服务地址协议或基础路径不合法')
  }
  if (securePage && address.protocol !== (websocket ? 'wss:' : 'https:')) {
    throw new Error('HTTPS页面必须配置HTTPS/WSS服务地址')
  }
  return address
}

function webAddress(raw, port, proxyPath, websocket = false) {
  const address = serviceAddress(raw, websocket)
  // 完整URL是部署者指定的完整基础地址，不再隐式追加端口或代理前缀。
  if (!raw.includes('://')) {
    const explicitPort = /:\d+$/.test(raw.trim().split('/')[0])
    if (!securePage && !address.port && !explicitPort) address.port = String(port)
    if (address.pathname === '/') address.pathname = proxyPath
  }
  return address.href.replace(/\/+$/, '')
}

function desktopAddress(setting) {
  if (!setting || !Number.isInteger(Number(setting.port)) || Number(setting.port) < 1 || Number(setting.port) > 65535) {
    throw new Error('桌面服务配置缺少有效端口')
  }
  const address = serviceAddress(setting.url)
  address.port = String(setting.port)
  return address.href.replace(/\/+$/, '')
}

export async function configureRuntime() {
  let fileBase
  if (isEE) {
    const settings = await ipc.invoke('controller.system.getConfig')
    window.httpUrl = desktopAddress(settings?.dataUrl)
    fileBase = desktopAddress(settings?.fileUrl)
    const socket = new URL(window.httpUrl)
    socket.protocol = socket.protocol === 'https:' ? 'wss:' : 'ws:'
    window.wsUrl = socket.href.replace(/\/+$/, '')
    window.ocrUrl = 'ws://localhost:13300'
  } else {
    if (!['http:', 'https:'].includes(window.location.protocol)) {
      throw new Error('Web模式请通过HTTP/HTTPS站点访问，不能直接打开本地HTML')
    }
    const config = window.serverConfig
    if (!config) throw new Error('未加载runtime-config.js，请检查部署资源')
    window.httpUrl = webAddress(config.httpUrl, 18001, securePage ? '/data' : '')
    window.wsUrl = webAddress(config.wsUrl, 18001, securePage ? '/push' : '', true)
    fileBase = webAddress(config.fileUrl, 8000, securePage ? '/file' : '')
    window.ueditorUrl = webAddress(config.ueditorUrl, 8003, '/ueditor')
    window.ocrUrl = webAddress(config.ocrUrl, 8080, '/ocr')
  }
  window.mqttUrl = window.serverConfig?.mqttUrl
  const mqttWsUrl = window.serverConfig?.mqttWsUrl
  window.mqttWsUrl = mqttWsUrl ? serviceAddress(mqttWsUrl, true).href : ''
  window.audioUrl = fileBase
  window.fileUrl = endpointUrl(fileBase, 'api/file/getFile')
  window.uploadFileUrl = endpointUrl(fileBase, 'api/file/upload')
  window.iconUrl = endpointUrl(window.fileUrl, 'icon/font/iconfont.js')

  localforage.config({name: 'WisdomJ233'})
  window.interfaceStyle = 'HJJ'
  window.cool = false
  if (localStorage.getItem('cool') === null) localStorage.setItem('cool', 'false')
  document.body.classList.add(window.interfaceStyle)
  const errorMask = document.getElementById('errorMask')
  if (errorMask) errorMask.style.display = navigator.userAgent.includes('Chrome') ? 'none' : 'block'
  // 浏览器升级包跟随运行时文件服务地址，避免写死开发机地址
  const chromeDownload = document.getElementById('chromeDownload')
  if (chromeDownload) chromeDownload.href = endpointUrl(window.fileUrl, 'tools/chrome.exe')
}

