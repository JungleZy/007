const {ipcMain, app} = require('electron')
const os = require('os')
const context = require('../core/node_core_ctx')

// 全部走 invoke/handle：渲染侧只用 ipc.invoke 取值，主进程 return 即返回。
// 2026-09-19 之前 getConfig 同时注册了 on（回填 event.returnValue + event.reply）与 handle 两套，
// changeConfig / getLocalIP 只有 on 一套且靠 sendSync 取值 —— sendSync 会阻塞渲染进程，
// 而这三条都不是必须同步的路径（网络设置页取值/保存、本机 IP 枚举）。
// event.reply 一并去掉：渲染侧从未 ipc.on 过这三个 channel（唯二的 on 消费者是
// controller.serialPort.getSerialPortList / grantAccess，仍保持 send/on 模型）。
ipcMain.handle("controller.system.getConfig", async () => {
  const findOne = await context.db.findOne({_id: 2});
  return findOne.text
})

ipcMain.handle("controller.system.changeConfig", async (event, args) => {
  // db.update 返回的是 numAffected（数字），原实现对它取 .text 恒得 undefined，
  // 渲染侧因此永远拿不到写入结果。这里回布尔成功标志，NetSetting 据此决定是否重载。
  const numAffected = await context.db.update({_id: 2}, {$set: {text: args}})
  return numAffected > 0
})

ipcMain.handle("controller.system.getLocalIP", async () => {
  const result = []
  const networkInterfaces = os.networkInterfaces();
  for (const name of Object.keys(networkInterfaces)) {
    for (const net of networkInterfaces[name]) {
      if (net.family === 'IPv4' && !net.internal && net.address) {
        result.push(net.address)
      }
    }
  }
  return result
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

