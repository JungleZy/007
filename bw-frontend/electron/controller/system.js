const {ipcMain, app} = require('electron')
const os = require('os')
const context = require('../core/node_core_ctx')

ipcMain.on("controller.system.getConfig", async (event) => {
  const findOne = await context.db.findOne({_id: 2});
  const result = findOne.text
  event.returnValue = result;
  event.reply(`controller.system.getConfig`, result);
})
ipcMain.handle("controller.system.getConfig", async (event) => {
  const findOne = await context.db.findOne({_id: 2});
  return findOne.text
})
ipcMain.on("controller.system.changeConfig", async (event,args) => {
  const findOne = await context.db.update({_id: 2}, {$set: {text: args}})
  const result = findOne.text
  event.returnValue = result;
  event.reply(`controller.system.changeConfig`, result);
})

ipcMain.on("controller.system.getLocalIP", async (event) => {
  let result = []
  const networkInterfaces = os.networkInterfaces();
  for (const name of Object.keys(networkInterfaces)) {
    for (const net of networkInterfaces[name]) {
      if (net.family === 'IPv4' && !net.internal && net.address) {
        result.push(net.address)
      }
    }
  }
  event.returnValue = result;
  event.reply(`controller.system.getLocalIP`, result);
})
ipcMain.handle("controller.system.closeApp", async (event) => {
  if(context.httpService){
    context.httpService.kill()
  }
  if (context.backendService){
    context.backendService.kill()
  }
  app.quit()
})

/*
 * 已删除（2026-09-09）：controller.system.getVersion / changeVersion / getMac
 *
 * 这三个接口构成了「MAC 地址绑定」授权路径，是「未到期却要求重新授权」的主因：
 *   - 取 MAC 的循环没有 break，拿到的是【最后一个】有非内部 IPv4 的网卡；
 *   - MAC 与上次记录不一致时，直接 remove(_id:5) + remove(_id:6)，
 *     把授权码和设备码一起删掉。
 * 关 WiFi、插 USB 网卡、启用虚拟机网卡、网卡枚举顺序变化都会触发。
 *
 * 替代方案见 electron/controller/fingerprint.js：改用 MachineGuid / 整机 UUID /
 * 磁盘序列号 / 整机序列号 四因子，且明确不采集 MAC。
 * 调用方 frontend/src/common/utils/VerifyLicenseDB.js 已一并删除。
 */

