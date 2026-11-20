const {ipcMain, BrowserWindow, app} = require("electron");
const context = require("../core/node_core_ctx");

// 升级包的可信边界是签名而不是登录角色：这些通道在登录前的版本阻断页也要可用。
const service = () => context.update

ipcMain.handle("controller.update.status", async () => service()?.status() || {available: false})

ipcMain.handle("controller.update.check", async () => service()?.check() || {state: 'local', blocked: false})

ipcMain.handle("controller.update.import", async event =>
  service()?.importFromDialog(BrowserWindow.fromWebContents(event.sender)) || {ok: false, code: 'unavailable', message: '当前运行方式不支持软件升级'})

ipcMain.handle("controller.update.download", async () =>
  service()?.downloadAndInstall() || {ok: false, code: 'unavailable', message: '当前运行方式不支持软件升级'})

ipcMain.handle("controller.update.restart", async () => {
  // before-quit 会先正常停止本机服务，再由 relaunch 拉起新进程。
  app.relaunch()
  app.quit()
})
