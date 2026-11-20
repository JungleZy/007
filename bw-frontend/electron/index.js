const {BrowserWindow, app, dialog, clipboard} = require("electron");
const path = require("node:path");
const context = require("./core/node_core_ctx");
const {serialApiHandle} = require("./serial");
const {loadConfig} = require("./core");
const {onBackendService, onHttpService} = require("./service");
const {IntegratedRuntime} = require('./service/integrated-runtime');
const {dataDirectory, localAddress} = require('./shared/locations');
const {UpdateService} = require('./update/service');
const {storeRoot} = require('./update/installer');
const releases = require('./update/releases');
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
      context.dataPath = this.integrated ? dataDirectory(app.getPath('userData')) : null
      const boot = this.integrated ? context.boot : null
      this.boot = boot
      context.update = new UpdateService({boot, dataPath: context.dataPath, dialog,
        getSettings: async () => (await context.db.findOne({_id: 2})).text})
      if (this.integrated) {
        this.startupWindow = new BrowserWindow({width: 560, height: 300, resizable: false, autoHideMenuBar: true,
          webPreferences: {nodeIntegration: false, contextIsolation: true, sandbox: true}})
        await this.startupWindow.loadFile(path.join(__dirname, 'startup.html'))
        this.startupWindow.on('closed', () => { if (!this.started) app.quit() })
      }
      const settings = await loadConfig()
      if (this.quitting) return
      if (boot?.broken && !(await this.resolveBrokenRelease(boot.broken))) return
      let initialAdmin
      if (this.integrated && localAddress(settings.text.dataUrl.url)) {
        this.runtime = new IntegratedRuntime({appPath: context.appPath, dataPath: context.dataPath,
          backendPort: Number(settings.text.dataUrl.port) || undefined,
          releaseRoot: boot?.releaseRoot || undefined,
          updateDirectory: storeRoot(context.dataPath),
          restoreBackup: releases.pendingRestore(context.dataPath),
          onMigrationsStarting: backup => releases.markMigrated(context.dataPath, backup),
          onRestored: () => releases.clearRestore(context.dataPath),
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
      // 本机服务就绪且主窗口已创建：新版本确认可用，此后不再回退。
      if (boot?.pending) releases.confirm(context.dataPath)
      if (boot?.notice) dialog.showMessageBox(context.mainWindow, {type: 'warning', title: '软件升级', message: boot.notice,
        detail: `日志目录：${path.join(context.dataPath, 'logs')}`})
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
      this.failed = true
      if (!this.quitting) this.reportStartupFailure(error)
      app.quit()
    })
    app.on('before-quit', event => {
      if (this.closed) return
      event.preventDefault()
      if (this.quitting) return
      this.quitting = true
      // 新版本尚未确认时用户主动退出，不算一次失败的启动。
      if (!this.started && !this.failed && this.boot?.pending) {
        try { releases.abortAttempt(context.dataPath) } catch { /* 不影响退出 */ }
      }
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

	/**
	 * 启动失败的处理（规格 U05）。新版本首次启动失败时：
	 * 还没动过数据库就自动回到升级前的版本；已开始迁移则不自动回退，由用户决定是否恢复备份。
	 */
	reportStartupFailure(error) {
		const logs = context.dataPath ? `日志目录：${path.join(context.dataPath, 'logs')}` : ''
		const pointer = this.boot?.pending ? releases.readPointer(context.dataPath) : null
		if (!pointer || pointer.version !== this.boot.version) {
			dialog.showErrorBox('系统启动失败', `${error.message}\n${logs}`)
			return
		}
		if (!pointer.migrated) {
			releases.rollback(context.dataPath, error.message)
			dialog.showMessageBoxSync({type: 'error', title: '升级未成功',
				message: `升级到 ${pointer.version} 未成功，已恢复到升级前的版本，软件将重新启动。`,
				detail: `${error.message}\n${logs}`})
			app.relaunch()
			return
		}
		const choice = dialog.showMessageBoxSync({type: 'error', title: '升级未成功',
			message: `升级到 ${pointer.version} 后启动失败。数据库已开始更新，不能自动回退。`,
			detail: `${error.message}\n\n${logs}\n升级前的数据库备份：${pointer.backup}\n\n`
				+ '可以退出后再次启动重试。选择“恢复备份并回退”会用上述备份覆盖当前数据库，并回到升级前的版本。',
			buttons: ['退出', '恢复备份并回退'], defaultId: 0, cancelId: 0})
		if (choice === 1) {
			releases.requestRestore(context.dataPath, pointer.backup)
			releases.rollback(context.dataPath, error.message)
			app.relaunch()
		}
	}

	/**
	 * 指针所指版本的文件损坏且不能自动回退时，当前运行的是出厂版本。
	 * 返回 false 表示已安排重启或退出，不再继续启动。
	 */
	async resolveBrokenRelease(broken) {
		const result = await dialog.showMessageBox(this.startupWindow, {type: 'error', title: '程序文件损坏',
			message: `版本 ${broken.version} 的程序文件已损坏，无法加载。`,
			detail: `${broken.reason}\n\n请重新导入该版本或更高版本的升级包。也可以先用出厂版本 ${this.boot.identity.version} 启动；`
				+ '如果数据库已被新版本更新过，出厂版本会拒绝使用它。',
			buttons: ['导入升级包', `以 ${this.boot.identity.version} 启动`, '退出'], defaultId: 0, cancelId: 2})
		if (result.response === 1) return true
		if (result.response === 0) {
			const imported = await context.update.importFromDialog(this.startupWindow)
			if (imported.ok && imported.action === 'installed') app.relaunch()
			else if (!imported.ok) dialog.showErrorBox('导入升级包失败', imported.message)
		}
		app.quit()
		return false
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
			// 不用 app.getAppPath()：升级后本文件来自版本目录里的 app.asar，而它恒指向安装目录。
			const indexPath = path.join(__dirname, '..', 'public', 'dist', 'index.html');
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
			// 自动渲染下本机没有可用WebGL时，记住改用软件渲染并重启一次；before-quit 会先正常停止本地服务。
			if (context.graphics?.needsSoftwareRelaunch()) {
				app.relaunch()
				app.quit()
				return
			}
			context.mainWindow.maximize();
			context.mainWindow.show()
		})
		serialApiHandle()
	}
}

module.exports = Index
