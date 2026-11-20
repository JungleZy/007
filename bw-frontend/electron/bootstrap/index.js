/**
 * 启动引导（规格 U03）。只存在于安装目录的出厂程序里并始终由它执行：
 * 读取版本指针，校验版本目录，然后加载对应版本的应用入口。
 *
 * 保持尽量小：这里的缺陷无法靠升级包修复，只能发完整安装包。
 */
const path = require('node:path');
const fs = require('node:fs');
const {PUBLIC_KEYS, RUNTIME_BASELINE} = require('../shared/update-keys');
const {dataDirectory} = require('../shared/locations');
const {machineIdentity, selectRelease, rollback, readPointer} = require('../update/releases');

function prepare(app, factoryRoot) {
    const identity = machineIdentity(factoryRoot, RUNTIME_BASELINE);
    const boot = {identity, publicKeys: PUBLIC_KEYS, releaseRoot: null, version: identity.version, pending: false, notice: null, broken: null};
    if (!app.isPackaged && process.env.NIP_INTEGRATED_DEV !== '1') return boot;
    const dataPath = dataDirectory(app.getPath('userData'));
    const log = message => {
        try {
            fs.mkdirSync(path.join(dataPath, 'logs'), {recursive: true});
            fs.appendFileSync(path.join(dataPath, 'logs/startup.log'), `${new Date().toISOString()} [bootstrap] ${message}\n`);
        } catch { /* 日志写不了不影响启动 */ }
    };
    try {
        const selected = selectRelease({dataPath, identity, publicKeys: PUBLIC_KEYS, log});
        Object.assign(boot, {releaseRoot: selected.root, version: selected.version, pending: selected.pending,
            notice: selected.notice, broken: selected.broken || null});
    } catch (error) {
        log(`版本选择失败，使用出厂版本：${error.stack || error.message}`);
    }
    boot.dataPath = dataPath;
    boot.log = log;
    return boot;
}

function start() {
    const {app, dialog} = require('electron');
    // 先拿单实例锁再读指针：否则在新版本首次启动尚未确认时再点一次图标，
    // 第二个进程会把「上次启动未确认」误判为失败并回退正在启动的版本。
    // 应用入口稍后会再请求一次同一把锁（已持有时恒为 true）并注册 second-instance 处理。
    if (!app.requestSingleInstanceLock()) {
        app.quit();
        return;
    }
    const factoryRoot = path.join(__dirname, '../..');
    const boot = prepare(app, factoryRoot);
    global.__nipBoot = boot;
    if (!boot.releaseRoot) {
        require('../app-main.js');
        return;
    }
    try {
        require(path.join(boot.releaseRoot, 'app.asar', 'electron', 'app-main.js'));
    } catch (error) {
        // 新版本的入口加载到一半可能已注册了部分监听，不能在同一进程里改用别的版本，只能重启。
        boot.log(`版本 ${boot.version} 入口加载失败：${error.stack || error.message}`);
        const pointer = readPointer(boot.dataPath);
        if (pointer && pointer.version === boot.version && !pointer.migrated) {
            rollback(boot.dataPath, `程序入口加载失败：${error.message}`);
            app.relaunch();
            app.exit(0);
            return;
        }
        app.whenReady().then(() => {
            dialog.showErrorBox('系统启动失败', `版本 ${boot.version} 的程序无法加载：${error.message}\n\n`
                + '数据库已按该版本更新，不能自动回退。请重新导入该版本的升级包，或使用完整安装包。\n'
                + `日志目录：${path.join(boot.dataPath, 'logs')}`);
            app.exit(1);
        });
    }
}

module.exports = {start, prepare};
