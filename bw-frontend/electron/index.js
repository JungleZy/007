const {BrowserWindow, app} = require("electron");
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
				nodeIntegration: true, // 根据Electron版本和安全最佳实践，你可能需要调整或禁用nodeIntegration
				contextIsolation: false, // 与nodeIntegration搭配使用时，通常也需要调整contextIsolation
				webSecurity: false, // 若需要加载本地文件到远程页面，可能需要禁用webSecurity
				enableBlinkFeatures: 'Serial',
			}
		})
		if (app.isPackaged) {
			context.mainWindow.loadFile("./public/dist/index.html").then()
				.catch((err) => {
					console.log(`Please check the ./public/dist/index.html !`);
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