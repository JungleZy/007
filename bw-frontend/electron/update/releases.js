/**
 * 版本目录与指针（规格 §4.1、U03、U05）。
 *
 *   <数据目录>/releases/current.json          当前版本指针
 *   <数据目录>/releases/<version>/            已解出的版本
 *   <数据目录>/releases/last-failure.json     最近一次升级失败的记录
 *   <数据目录>/releases/pending-restore.json  待执行的「恢复升级前备份」请求
 *
 * 指针字段：version、previous（null 表示出厂版本）、state（pending|confirmed）、
 * attempts（待确认状态下已尝试启动的次数）、migrated（本次升级是否已执行过迁移）、backup。
 */
const path = require('node:path');
const asarAwareFs = require('node:fs');
const {PRODUCT, UpdateError, compareVersions, verifyReleaseDirectory} = require('../shared/update-package');
const {mysqlTarget} = require('../shared/mysql-platforms');

let fs;
try { fs = require('original-fs'); } catch { fs = require('node:fs'); }

const root = dataPath => path.join(dataPath, 'releases');
const pointerFile = dataPath => path.join(root(dataPath), 'current.json');
const failureFile = dataPath => path.join(root(dataPath), 'last-failure.json');
const restoreFile = dataPath => path.join(root(dataPath), 'pending-restore.json');
const releaseDirectory = (dataPath, version) => path.join(root(dataPath), version);

function readJson(file) {
    try { return JSON.parse(fs.readFileSync(file, 'utf8')); } catch { return null; }
}

function writeJson(file, data) {
    fs.mkdirSync(path.dirname(file), {recursive: true});
    const temporary = `${file}.tmp`;
    fs.writeFileSync(temporary, JSON.stringify(data, null, 2));
    fs.renameSync(temporary, file);
}

function readPointer(dataPath) {
    const pointer = readJson(pointerFile(dataPath));
    if (!pointer || typeof pointer.version !== 'string' || !['pending', 'confirmed'].includes(pointer.state)) return null;
    try { compareVersions(pointer.version, pointer.version); } catch { return null; }
    return pointer;
}

const writePointer = (dataPath, pointer) => writeJson(pointerFile(dataPath), {schema: 1, ...pointer});
const clearPointer = dataPath => fs.rmSync(pointerFile(dataPath), {force: true});

/**
 * 本机身份取自安装目录里的出厂程序：升级包的 target、风格、基线都与它比对。
 * 风格由前端构建写入 public/dist/build-manifest.json；读不到时返回 null，升级入口据此拒绝。
 */
function machineIdentity(factoryRoot, runtimeBaseline, {platform = process.platform, architecture = process.arch} = {}) {
    const version = JSON.parse(asarAwareFs.readFileSync(path.join(factoryRoot, 'package.json'), 'utf8')).version;
    let style = null;
    try {
        const built = JSON.parse(asarAwareFs.readFileSync(path.join(factoryRoot, 'public/dist/build-manifest.json'), 'utf8'));
        if (typeof built.interfaceStyle === 'string') style = built.interfaceStyle;
    } catch { /* 开发态没有构建产物 */ }
    return {product: PRODUCT, version, style, target: mysqlTarget(platform, architecture), runtimeBaseline};
}

function recordFailure(dataPath, version, reason) {
    writeJson(failureFile(dataPath), {version, reason, at: new Date().toISOString()});
}

const readFailure = dataPath => readJson(failureFile(dataPath));
const clearFailure = dataPath => fs.rmSync(failureFile(dataPath), {force: true});

/** 把指针改回上一版本（没有上一版本则回到出厂版本），并记录失败原因。 */
function rollback(dataPath, reason) {
    const pointer = readPointer(dataPath);
    if (!pointer) return null;
    recordFailure(dataPath, pointer.version, reason);
    if (pointer.previous) writePointer(dataPath, {version: pointer.previous, previous: null, state: 'confirmed', attempts: 0, migrated: false});
    else clearPointer(dataPath);
    return pointer;
}

function assertMatchesMachine(manifest, identity) {
    if (manifest.product !== identity.product || manifest.target !== identity.target || manifest.style !== identity.style) {
        throw new UpdateError('corrupt', '版本目录与本机的产品、平台或风格不一致');
    }
    if (manifest.runtimeBaseline !== identity.runtimeBaseline) throw new UpdateError('baseline', '版本目录与本机运行环境基线不一致');
}

/**
 * 启动时决定运行哪个版本。返回 root 为 null 表示使用安装目录里的出厂版本。
 * notice 是需要在界面上告知用户的情况（已自动回退、当前版本文件损坏等）。
 */
function selectRelease({dataPath, identity, publicKeys, log = () => {}}) {
    const factory = extra => ({root: null, version: identity.version, pending: false, notice: null, ...extra});
    let notice = null;
    for (let round = 0; round < 3; round++) {
        const pointer = readPointer(dataPath);
        if (!pointer) return factory({notice});
        if (compareVersions(pointer.version, identity.version) <= 0) {
            // 重装了不低于该版本的完整安装包：以出厂版本为准。
            log(`出厂版本 ${identity.version} 不低于已升级版本 ${pointer.version}，清除版本指针`);
            clearPointer(dataPath);
            return factory({notice});
        }
        const pending = pointer.state === 'pending';
        if (pending && pointer.attempts >= 1 && !pointer.migrated) {
            log(`版本 ${pointer.version} 上次启动未完成确认，回退`);
            rollback(dataPath, '升级后的版本未能正常启动');
            notice = `升级到 ${pointer.version} 未成功，已恢复到升级前的版本。`;
            continue;
        }
        const directory = releaseDirectory(dataPath, pointer.version);
        try {
            const manifest = verifyReleaseDirectory(directory, publicKeys);
            if (manifest.version !== pointer.version) throw new UpdateError('corrupt', '版本目录与指针版本不一致');
            assertMatchesMachine(manifest, identity);
        } catch (error) {
            log(`版本 ${pointer.version} 校验失败：${error.message}`);
            if (pending && !pointer.migrated) {
                rollback(dataPath, `升级文件校验失败：${error.message}`);
                notice = `升级到 ${pointer.version} 未成功，已恢复到升级前的版本。`;
                continue;
            }
            // 已确认或已执行迁移的版本不能自动回退（数据库可能已是新结构），交给界面处理。
            return factory({notice, broken: {version: pointer.version, reason: error.message, backup: pointer.backup || null, migrated: !!pointer.migrated}});
        }
        if (pending) writePointer(dataPath, {...pointer, attempts: (pointer.attempts || 0) + 1});
        return {root: directory, version: pointer.version, pending, notice};
    }
    return factory({notice});
}

/** 新版本启动成功后确认，并清理当前与上一版本之外的目录。 */
function confirm(dataPath) {
    const pointer = readPointer(dataPath);
    if (!pointer) return;
    if (pointer.state === 'pending') writePointer(dataPath, {...pointer, state: 'confirmed', attempts: 0});
    clearFailure(dataPath);
    const keep = new Set([pointer.version, pointer.previous].filter(Boolean));
    for (const entry of fs.readdirSync(root(dataPath), {withFileTypes: true})) {
        if (entry.isDirectory() && !keep.has(entry.name)) fs.rmSync(path.join(root(dataPath), entry.name), {recursive: true, force: true});
    }
}

/** 记录「本次升级已开始执行迁移」。此后启动失败不再自动回退。 */
function markMigrated(dataPath, backup) {
    const pointer = readPointer(dataPath);
    if (pointer && pointer.state === 'pending') writePointer(dataPath, {...pointer, migrated: true, backup});
}

/** 用户在确认前主动退出，不算一次失败的启动。 */
function abortAttempt(dataPath) {
    const pointer = readPointer(dataPath);
    if (pointer && pointer.state === 'pending' && pointer.attempts > 0) writePointer(dataPath, {...pointer, attempts: pointer.attempts - 1});
}

const requestRestore = (dataPath, backup) => writeJson(restoreFile(dataPath), {backup, requestedAt: new Date().toISOString()});
const pendingRestore = dataPath => readJson(restoreFile(dataPath))?.backup || null;
const clearRestore = dataPath => fs.rmSync(restoreFile(dataPath), {force: true});

module.exports = {
    releaseDirectory, readPointer, writePointer, clearPointer, machineIdentity, selectRelease,
    rollback, confirm, markMigrated, abortAttempt, readFailure, requestRestore, pendingRestore, clearRestore
};
