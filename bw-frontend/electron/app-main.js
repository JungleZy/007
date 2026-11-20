/**
 * 应用入口。由根目录 main.js 的启动引导加载：可能来自安装目录里的出厂程序，
 * 也可能来自数据目录下某个版本目录里的 app.asar，因此这里不能依赖 app.getAppPath()。
 */
const {app} = require('electron')
const path = require('node:path')
const context = require("./core/node_core_ctx");
const Index = require("./index");
const {configureGraphics, watchGraphics} = require('./core/graphics');

context.graphics = watchGraphics(app, {graphicsMode: configureGraphics(app)});
app.commandLine.appendSwitch('enable-experimental-web-platform-features');
// 安装根目录：运行时（JRE、MySQL）、随包资源与默认配置始终从这里取，升级不改动它。
context.appPath = app.isPackaged ? path.dirname(app.getPath('exe')) : path.join(__dirname, '..')
// 启动引导选定的版本信息；开发态没有引导，保持为 null。
context.boot = global.__nipBoot || null

new Index()
