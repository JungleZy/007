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
  grantAccess: 'controller.serialPort.grantAccess',
  // 授权信息的机器级 / 用户级文件副本
  licenseRead: 'controller.license.read',
  licenseWrite: 'controller.license.write',
  licenseClear: 'controller.license.clear',
  licenseFingerprint: 'controller.license.fingerprint',
  // 软件升级：版本状态、组网版本检查、导入与下载升级包
  updateStatus: 'controller.update.status',
  updateCheck: 'controller.update.check',
  updateImport: 'controller.update.import',
  updateDownload: 'controller.update.download',
  updateRestart: 'controller.update.restart',
}

export {ipcApiRoute}
