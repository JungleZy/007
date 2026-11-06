process.env['ELECTRON_DISABLE_SECURITY_WARNINGS'] = 'true'
const {app, BrowserWindow} = require('electron')
const path = require('node:path')
const context = require("./electron/core/node_core_ctx");
const Index = require("./electron/index");
const {configureGraphics} = require('./electron/core/graphics');

configureGraphics(app);
app.commandLine.appendSwitch('enable-experimental-web-platform-features');
context.appPath = app.isPackaged ? path.dirname(app.getPath('exe')) : __dirname

new Index()
