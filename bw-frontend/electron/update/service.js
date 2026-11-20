/**
 * 升级功能的主进程门面：IPC 控制器与启动流程都经它操作，保证同一时间只有一个升级任务。
 */
const path = require('node:path');
const {UpdateError, PACKAGE_EXTENSION} = require('../shared/update-package');
const {localAddress} = require('../shared/locations');
const {readPointer, readFailure} = require('./releases');
const {installPackage, listStoredPackages, storeRoot} = require('./installer');
const {fetchServerInfo, evaluate, downloadPackage} = require('./version-check');

let fs;
try { fs = require('original-fs'); } catch { fs = require('node:fs'); }

class UpdateService {
    /** boot 为启动引导给出的版本信息；开发态没有引导时为 null，此时升级功能不可用。 */
    constructor({boot, dataPath, getSettings, dialog, relaunch}) {
        this.boot = boot;
        this.dataPath = dataPath;
        this.getSettings = getSettings;
        this.dialog = dialog;
        this.relaunch = relaunch;
        this.busy = false;
    }

    get available() { return !!(this.boot && this.dataPath); }

    running() { return {version: this.boot.version, release: !!this.boot.releaseRoot}; }

    local() { return {...this.boot.identity, version: this.boot.version}; }

    status() {
        if (!this.available) return {available: false};
        const pointer = readPointer(this.dataPath);
        const {target, style, runtimeBaseline} = this.boot.identity;
        return {available: true, version: this.boot.version, target, style, runtimeBaseline,
            pendingVersion: pointer && pointer.state === 'pending' && pointer.version !== this.boot.version ? pointer.version : null,
            lastFailure: readFailure(this.dataPath), packages: listStoredPackages(this.dataPath)};
    }

    async exclusive(task) {
        if (!this.available) return {ok: false, code: 'unavailable', message: '当前运行方式不支持软件升级'};
        if (this.busy) return {ok: false, code: 'busy', message: '已有升级任务正在进行'};
        this.busy = true;
        try { return {ok: true, ...(await task())}; }
        catch (error) {
            if (error instanceof UpdateError) return {ok: false, code: error.code, message: error.message};
            return {ok: false, code: 'error', message: `升级失败：${error.message}`};
        } finally { this.busy = false; }
    }

    install(file) {
        return installPackage({file, dataPath: this.dataPath, identity: this.boot.identity, running: this.running(), publicKeys: this.boot.publicKeys});
    }

    /** 让用户选择升级包并安装。 */
    importFromDialog(window) {
        return this.exclusive(async () => {
            const picked = await this.dialog.showOpenDialog(window, {title: '选择升级包', properties: ['openFile'],
                filters: [{name: '升级包', extensions: [PACKAGE_EXTENSION.slice(1)]}]});
            if (picked.canceled || !picked.filePaths.length) return {action: 'canceled'};
            return this.install(picked.filePaths[0]);
        });
    }

    /** 数据服务指向远端时与服务端比对版本；本机模式无需比对。 */
    async check() {
        if (!this.available) return {state: 'local', mismatch: false};
        const setting = (await this.getSettings()).dataUrl;
        if (localAddress(setting.url)) return {state: 'local', mismatch: false, localVersion: this.boot.version};
        return evaluate(this.local(), await fetchServerInfo(setting));
    }

    /** 从服务端下载与其版本一致的升级包并安装。 */
    downloadAndInstall() {
        return this.exclusive(async () => {
            const setting = (await this.getSettings()).dataUrl;
            if (localAddress(setting.url)) throw new UpdateError('download', '当前使用本机数据服务，没有可下载的服务端');
            const result = evaluate(this.local(), await fetchServerInfo(setting));
            if (result.state !== 'server-newer') throw new UpdateError('download', '服务端没有适用于本机的升级包');
            const file = await downloadPackage(setting, this.local(), result.package, path.join(storeRoot(this.dataPath), '.incoming'));
            try { return await this.install(file); }
            finally { fs.rmSync(file, {force: true}); }
        });
    }
}

module.exports = {UpdateService};
