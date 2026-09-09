process.env['ELECTRON_DISABLE_SECURITY_WARNINGS'] = 'true'
const {app, BrowserWindow} = require('electron')
const path = require('node:path')
const context = require("./electron/core/node_core_ctx");
const Index = require("./electron/index");

app.disableHardwareAcceleration();
app.commandLine.appendSwitch('enable-experimental-web-platform-features');
app.commandLine.appendSwitch('--ignore-certificate-errors', 'true');
app.commandLine.appendSwitch('disable-gpu')  // 禁用 GPU 加速<br/>
app.commandLine.appendSwitch('disable-software-rasterizer')
context.appPath = app.isPackaged ? path.dirname(app.getPath('exe')) : __dirname

new Index()
