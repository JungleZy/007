process.env['ELECTRON_DISABLE_SECURITY_WARNINGS'] = 'true'
// 启动引导：决定加载安装目录里的出厂程序，还是数据目录下已升级的版本。
// 本文件与 electron/bootstrap 只随完整安装包更新，改动后须提升 RUNTIME_BASELINE。
require('./electron/bootstrap').start()
