/**
 * 升级包的安装与留存（规格 §4.3、U04）。单机导入和组网下载走同一入口 installPackage。
 */
const path = require('node:path');
const crypto = require('node:crypto');
const {UpdateError, compareVersions, sha256File, readHeader, extractPayload, packageFileName} = require('../shared/update-package');
const {releaseDirectory, writePointer} = require('./releases');

let fs;
try { fs = require('original-fs'); } catch { fs = require('node:fs'); }

const storeRoot = dataPath => path.join(dataPath, 'updates');
const storeDirectory = (dataPath, target, style) => path.join(storeRoot(dataPath), target, style);

function readInfo(directory) {
    try {
        const info = JSON.parse(fs.readFileSync(path.join(directory, 'info.json'), 'utf8'));
        return fs.statSync(path.join(directory, info.fileName)).size === info.size ? info : null;
    } catch { return null; }
}

/** 留存包列表；后端的版本端点读取同一份 info.json。 */
function listStoredPackages(dataPath) {
    const result = [];
    let targets = [];
    try { targets = fs.readdirSync(storeRoot(dataPath), {withFileTypes: true}); } catch { return result; }
    for (const target of targets.filter(entry => entry.isDirectory())) {
        for (const style of fs.readdirSync(path.join(storeRoot(dataPath), target.name), {withFileTypes: true}).filter(entry => entry.isDirectory())) {
            const info = readInfo(storeDirectory(dataPath, target.name, style.name));
            if (info) result.push(info);
        }
    }
    return result;
}

/** 每个 target 与风格的组合只留最新一个包。 */
function storePackage(file, manifest, dataPath) {
    const directory = storeDirectory(dataPath, manifest.target, manifest.style);
    fs.mkdirSync(directory, {recursive: true});
    const fileName = packageFileName(manifest);
    const destination = path.join(directory, fileName);
    if (path.resolve(file) !== path.resolve(destination)) {
        const temporary = `${destination}.tmp-${crypto.randomBytes(6).toString('hex')}`;
        try {
            fs.copyFileSync(file, temporary);
            fs.renameSync(temporary, destination);
        } finally { fs.rmSync(temporary, {force: true}); }
    }
    const info = {fileName, version: manifest.version, target: manifest.target, style: manifest.style,
        size: fs.statSync(destination).size, sha256: sha256File(destination)};
    const temporary = path.join(directory, 'info.json.tmp');
    fs.writeFileSync(temporary, JSON.stringify(info, null, 2));
    fs.renameSync(temporary, path.join(directory, 'info.json'));
    for (const name of fs.readdirSync(directory)) {
        if (name !== fileName && name !== 'info.json') fs.rmSync(path.join(directory, name), {recursive: true, force: true});
    }
    return info;
}

/**
 * 校验并安装升级包。
 * - 与本机 target、风格一致：解到版本目录，指针置为待确认，重启后生效。
 * - 不一致：只验签和校验内容，留存用于组网分发。
 * 任何一步失败都不改指针，已解出的目录会被清理。
 *
 * @param running {version, release} 当前运行的版本，以及它是否来自版本目录
 */
async function installPackage({file, dataPath, identity, running, publicKeys}) {
    if (!identity.style) throw new UpdateError('identity', '无法识别本机的界面风格，不能安装升级包');
    const header = readHeader(file, publicKeys);
    const manifest = header.manifest;
    if (manifest.product !== identity.product) throw new UpdateError('product', '该升级包不属于本软件');
    const foreign = manifest.target !== identity.target || manifest.style !== identity.style;
    if (foreign) {
        const stored = readInfo(storeDirectory(dataPath, manifest.target, manifest.style));
        if (stored && compareVersions(manifest.version, stored.version) < 0) {
            throw new UpdateError('not-newer', `已留存更新的 ${manifest.target} ${manifest.style} 升级包（${stored.version}）`);
        }
        await extractPayload(file, header, null);
        return {action: 'stored', ...storePackage(file, manifest, dataPath)};
    }
    if (manifest.runtimeBaseline !== identity.runtimeBaseline) {
        throw new UpdateError('baseline', '该版本包含运行环境变更，不能用升级包安装，请使用完整安装包');
    }
    if (compareVersions(running.version, manifest.minFromVersion) < 0) {
        throw new UpdateError('too-old', `当前版本 ${running.version} 低于升级包支持的最低版本 ${manifest.minFromVersion}，请使用完整安装包`);
    }
    if (compareVersions(manifest.version, running.version) <= 0) {
        throw new UpdateError('not-newer', `升级包版本 ${manifest.version} 不高于当前版本 ${running.version}`);
    }
    const directory = releaseDirectory(dataPath, manifest.version);
    await extractPayload(file, header, directory);
    try {
        const info = storePackage(file, manifest, dataPath);
        writePointer(dataPath, {version: manifest.version, previous: running.release ? running.version : null,
            state: 'pending', attempts: 0, migrated: false, installedAt: new Date().toISOString()});
        return {action: 'installed', ...info};
    } catch (error) {
        fs.rmSync(directory, {recursive: true, force: true});
        throw error;
    }
}

module.exports = {installPackage, listStoredPackages, storeRoot, storeDirectory};
