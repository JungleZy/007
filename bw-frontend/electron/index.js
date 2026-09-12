const {BrowserWindow, app, dialog} = require("electron");
const path = require("node:path");
const context = require("./core/node_core_ctx");
const {serialApiHandle} = require("./serial");
const {loadConfig} = require("./core");
const {onBackendService, onHttpService} = require("./service");
require('./controller/index')

class Index {
	constructor() {
		loadConfig()
		onBackendService()
		onHttpService()
    const gotTheLock = app.requestSingleInstanceLock()
    if(!gotTheLock){
      app.quit()
    }else {
      app.on("second-instance",(event,commandLine,workingDirectory)=>{
        if(context.mainWindow){
          if(context.mainWindow.isMinimized()) context.mainWindow.restore(context.mainWindow.focus)
        }
      })
    }
		app.whenReady().then(() => {
			this.createWindow()

			app.on('activate', function () {
				if (BrowserWindow.getAllWindows().length === 0) createWindow()
			})
		})
		app.on('will-quit', () => {
			// 关闭所有子进程
			if (context.backendService) {
				context.backendService.kill()
			}
			if (context.httpService) {
				context.httpService.kill()
			}
		});
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