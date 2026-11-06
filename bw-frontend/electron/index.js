const {BrowserWindow, app, dialog, clipboard} = require("electron");
const path = require("node:path");
const context = require("./core/node_core_ctx");
const {serialApiHandle} = require("./serial");
const {loadConfig} = require("./core");
const {onBackendService, onHttpService} = require("./service");
const {IntegratedRuntime} = require('./service/integrated-runtime');
require('./controller/index')

class Index {
	constructor() {
    const gotTheLock = app.requestSingleInstanceLock()
    if(!gotTheLock){
      app.quit()
      return
    }else {
      app.on("second-instance",(event,commandLine,workingDirectory)=>{
        if(context.mainWindow && !context.mainWindow.isDestroyed()){
          if(context.mainWindow.isMinimized()) context.mainWindow.restore()
          context.mainWindow.focus()
        }
      })
    }
		app.whenReady().then(async () => {
      this.integrated = app.isPackaged || process.env.NIP_INTEGRATED_DEV === '1'
      // MySQL5.7 不能打开 MySQL8 的物理数据目录，32位安装使用独立持久目录。
      context.dataPath = this.integrated ? path.join(app.getPath('userData'), process.arch === 'ia32' ? 'system-win32-mysql57' : 'system') : null
      if (this.integrated) {
        this.startupWindow = new BrowserWindow({width: 560, height: 300, resizable: false, autoHideMenuBar: true,
          webPreferences: {nodeIntegration: false, contextIsolation: true, sandbox: true}})
        await this.startupWindow.loadFile(path.join(__dirname, 'startup.html'))
        this.startupWindow.on('closed', () => { if (!this.started) app.quit() })
      }
      const settings = await loadConfig()
      if (this.quitting) return
      const localAddress = value => {
        try { return ['localhost', '127.0.0.1', '[::1]'].includes(new URL(String(value).includes('://') ? value : `http://${value}`).hostname) }
        catch { return false }
      }
      let initialAdmin
      if (this.integrated && localAddress(settings.text.dataUrl.url)) {
        this.runtime = new IntegratedRuntime({appPath: context.appPath, dataPath: context.dataPath,
          backendPort: Number(settings.text.dataUrl.port) || undefined,
          onProgress: message => {
            if (this.startupWindow && !this.startupWindow.isDestroyed()) {
              this.startupWindow.webContents.executeJavaScript(`document.getElementById('progress').textContent=${JSON.stringify(message)}`).catch(() => {})
            }
          }})
        initialAdmin = await this.runtime.start()
        context.backendService = this.runtime.backend
      } else if (!this.integrated) {
        onBackendService()
      }
      if (this.quitting) return
      if (localAddress(settings.text.fileUrl.url)) await onHttpService(Number(settings.text.fileUrl.port) || undefined)
      this.started = true
			this.createWindow()
      this.startupWindow?.close()
      this.startupWindow = null
      if (initialAdmin) {
        const result = await dialog.showMessageBox(context.mainWindow, {type: 'info', title: '初始化完成',
          message: '已创建初始管理员账号，请保存登录信息。',
          detail: `账号：${initialAdmin.account}\n初始密码：${initialAdmin.password}\n登录后可在系统中修改密码。`,
          buttons: ['复制登录信息', '我已保存'], defaultId: 0, cancelId: 1})
        if (result.response === 0) clipboard.writeText(`账号：${initialAdmin.account}\n密码：${initialAdmin.password}`)
        this.runtime.acknowledgeInitialAdmin()
      }

      app.on('activate', () => {
        if (BrowserWindow.getAllWindows().length === 0) this.createWindow()
      })
		}).catch(error => {
      if (!this.quitting) dialog.showErrorBox('系统启动失败', `${error.message}\n${context.dataPath ? `日志目录：${path.join(context.dataPath, 'logs')}` : ''}`)
      app.quit()
    })
    app.on('before-quit', event => {
      if (this.closed) return
      event.preventDefault()
      if (this.quitting) return
      this.quitting = true
      Promise.resolve().then(async () => {
        if (this.runtime) await this.runtime.stop()
        else context.backendService?.kill()
      }).catch(error => dialog.showErrorBox('关闭服务失败', error.message)).finally(() => {
        context.httpService?.kill()
        this.closed = true
        app.quit()
      })
    })
		app.on('window-all-closed', function () {
			if (process.platform !== 'darwin') app.quit()
		})
	}

	createWindow() {
		context.mainWindow = new BrowserWindow({
			transparent: false,
			frame: false,
			show: false,
			fullscreen: true,
			titleBarStyle: 'hidden',
			resizable: false, //可否缩放
			autoHideMenuBar: true, // 隐藏菜单栏
			webPreferences: {
				// 渲染进程不需要 Node：所有主进程能力经 preload 的 window.electron.ipcRenderer 白名单走。
				nodeIntegration: false,
				contextIsolation: true,
				preload: path.join(__dirname, 'preload.js'), // 开发态为源码目录，打包态为 app.asar/electron，两者都由 Electron 直接解析
				// webSecurity 必须保持 false：打包页走 loadFile（file://），而摩尔斯发音的唯一实现
				// frontend/src/common/utils/voice/MorseVoiceHighPerformance.js:223 用
				// audioWorklet.addModule(new URL('processor.js', document.baseURI))，AudioWorklet 模块脚本
				// 恒以 CORS 模式拉取，file:// 是不透明源、拿不到 ACAO；失败会落到该文件 :247-251 的 catch，
				// 直接把状态置成 failure 且没有任何降级路径 —— 打开 webSecurity 等于整套发音不可用。
				// 要拿这条安全收益必须先把打包页改成 app:// privileged scheme（另立项，见 Spec §1.2）。
				webSecurity: false,
				enableBlinkFeatures: 'Serial',
			}
		})
		if (app.isPackaged) {
			const indexPath = path.join(app.getAppPath(), 'public', 'dist', 'index.html');
			context.mainWindow.loadFile(indexPath).catch((err) => {
				console.error(`Failed to load packaged frontend: ${indexPath}`, err);
				dialog.showErrorBox('应用启动失败', `无法加载应用资源：\n${indexPath}\n\n${err.message}`);
				app.once('will-quit', () => app.exit(1));
				app.quit();
			});
		} else {
			context.mainWindow.loadURL('http://localhost:18000')
			context.mainWindow.webContents.openDevTools();
		}

		context.mainWindow.on('ready-to-show', () => {
			context.mainWindow.maximize();
			context.mainWindow.show()
		})
		serialApiHandle()
	}
}

module.exports = Index
