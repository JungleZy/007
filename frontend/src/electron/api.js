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
  getVersion:'controller.system.getVersion',
  changeVersion:'controller.system.changeVersion',
  getMac:'controller.system.getMac',
}
/**
 * 自定义频道
 * custom chennel
 */
const specialIpcRoute = {}

export {ipcApiRoute, specialIpcRoute}
