/**
 * 主进程与渲染进程通信频道定义
 * Definition of communication channels between main process and rendering process
 */
const ipcApiRoute = {
  closeApp: 'controller.system.closeApp',
  getConfig: 'controller.system.getConfig',
  changeConfig: 'controller.system.changeConfig',
  getLocalIP: 'controller.system.getLocalIP',
  getSerialPorts: 'controller.serialPort.getSerialPortList',
  linkPort: 'controller.serialPort.linkPort',
  // 授权信息的机器级 / 用户级文件副本
  licenseRead: 'controller.license.read',
  licenseWrite: 'controller.license.write',
  licenseClear: 'controller.license.clear',
  licenseFingerprint: 'controller.license.fingerprint',
}
/**
 * 自定义频道
 * custom chennel
 */
const specialIpcRoute = {}

export {ipcApiRoute, specialIpcRoute}
