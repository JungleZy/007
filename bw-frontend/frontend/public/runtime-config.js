// Web部署时只修改此文件，无需重新构建。不要在这里放凭据。
// 裸主机：HTTP使用服务默认端口；HTTPS使用/data、/push、/file等反代前缀。
// 完整URL：按指定协议、端口、基础路径使用，例如https://api.example.com/data。
// fileUrl是文件服务基础地址（如https://files.example.com/file），不含/api/file/getFile。
// ueditorUrl/ocrUrl使用完整URL时须包含实际请求路径；不再额外拼接/ueditor或/ocr。
// Electron的数据/文件地址由桌面IPC配置提供，不读取这里的Web地址。
// mqttUrl保留外部设备程序所需的裸主机；mqttWsUrl是浏览器MQTT的完整WS/WSS地址。
// Electron如需MQTT，在打包前配置mqttWsUrl；空值表示尚未配置，不猜测本机代理。
(() => {
  const host = location.protocol === 'https:' ? location.host : location.hostname
  window.serverConfig = {
    httpUrl: host,
    wsUrl: host,
    fileUrl: host,
    ueditorUrl: host,
    ocrUrl: host,
    mqttUrl: location.hostname,
    mqttWsUrl: location.protocol === 'file:' ? ''
      : `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.protocol === 'https:' ? host : `${host}:8083`}/mqtt`
  }
})()
