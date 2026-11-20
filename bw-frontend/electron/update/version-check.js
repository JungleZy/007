/**
 * 组网时与服务端比对版本，并从服务端取升级包（规格 U08）。
 */
const http = require('node:http');
const https = require('node:https');
const path = require('node:path');
const crypto = require('node:crypto');
const {UpdateError, compareVersions, PACKAGE_EXTENSION} = require('../shared/update-package');

let fs;
try { fs = require('original-fs'); } catch { fs = require('node:fs'); }

const INFO_PATH = '/api/update/info';
const PACKAGE_PATH = '/api/update/package';

function serverBase(setting) {
    const address = new URL(String(setting.url).includes('://') ? setting.url : `http://${setting.url}`);
    if (!['http:', 'https:'].includes(address.protocol)) throw new Error('服务地址协议不受支持');
    address.port = String(setting.port);
    return address;
}

function request(url, timeout) {
    return new Promise((resolve, reject) => {
        const call = (url.protocol === 'https:' ? https : http).get(url, {timeout}, resolve);
        call.on('timeout', () => call.destroy(new Error('timeout')));
        call.on('error', reject);
    });
}

/**
 * 查询服务端版本。
 * - unreachable：连不上或服务端故障，无法判断版本
 * - legacy：服务端可达但没有版本端点，即尚无升级功能的旧版本
 */
async function fetchServerInfo(setting, {timeout = 5000} = {}) {
    let response;
    try { response = await request(new URL(INFO_PATH, serverBase(setting)), timeout); }
    catch { return {status: 'unreachable'}; }
    const chunks = [];
    let size = 0;
    try {
        for await (const chunk of response) {
            if ((size += chunk.length) > 1024 * 1024) { response.destroy(); break; }
            chunks.push(chunk);
        }
    } catch { return {status: 'unreachable'}; }
    if (response.statusCode >= 500) return {status: 'unreachable'};
    try {
        const body = JSON.parse(Buffer.concat(chunks).toString('utf8'));
        compareVersions(body.data.version, body.data.version);
        if (response.statusCode !== 200 || body.code !== 200 || !Array.isArray(body.data.packages)) throw new Error('unexpected');
        return {status: 'ok', version: body.data.version, packages: body.data.packages};
    } catch { return {status: 'legacy'}; }
}

/**
 * 根据服务端应答得出处理结论。blocked 为 true 时不得进入登录与训练。
 * 只有版本与服务端一致的包才可用：装上别的版本仍然对不上。
 */
function evaluate(local, server) {
    if (server.status === 'unreachable') return {state: 'unreachable', blocked: false, localVersion: local.version};
    if (server.status === 'legacy') return {state: 'server-legacy', blocked: true, localVersion: local.version, serverVersion: null};
    const base = {localVersion: local.version, serverVersion: server.version};
    const order = compareVersions(server.version, local.version);
    if (order === 0) return {state: 'same', blocked: false, ...base};
    if (order < 0) return {state: 'server-older', blocked: true, ...base};
    const available = server.packages.find(item => item.target === local.target && item.style === local.style && item.version === server.version);
    return available
        ? {state: 'server-newer', blocked: true, ...base, package: available}
        : {state: 'server-newer-no-package', blocked: true, ...base};
}

/** 下载升级包到 destinationDirectory，大小或摘要不符则删除并报错。 */
async function downloadPackage(setting, local, expected, destinationDirectory, {timeout = 30000, onProgress = () => {}} = {}) {
    const url = new URL(PACKAGE_PATH, serverBase(setting));
    url.searchParams.set('target', local.target);
    url.searchParams.set('style', local.style);
    fs.mkdirSync(destinationDirectory, {recursive: true});
    const destination = path.join(destinationDirectory, `download-${crypto.randomBytes(6).toString('hex')}${PACKAGE_EXTENSION}`);
    let fd = null;
    try {
        const response = await request(url, timeout);
        if (response.statusCode !== 200 || !/octet-stream/.test(response.headers['content-type'] || '')) {
            response.destroy();
            throw new UpdateError('download', '服务端没有可用的升级包');
        }
        const hash = crypto.createHash('sha256');
        let size = 0;
        fd = fs.openSync(destination, 'wx');
        for await (const chunk of response) {
            if ((size += chunk.length) > expected.size) throw new UpdateError('download', '下载的升级包大小与服务端声明不符');
            hash.update(chunk);
            fs.writeSync(fd, chunk);
            onProgress(size, expected.size);
        }
        fs.closeSync(fd);
        fd = null;
        if (size !== expected.size || hash.digest('hex') !== expected.sha256) throw new UpdateError('download', '下载的升级包校验失败，请重试');
        return destination;
    } catch (error) {
        if (fd !== null) fs.closeSync(fd);
        fs.rmSync(destination, {force: true});
        throw error instanceof UpdateError ? error : new UpdateError('download', `下载升级包失败：${error.message}`);
    }
}

module.exports = {INFO_PATH, PACKAGE_PATH, fetchServerInfo, evaluate, downloadPackage};
