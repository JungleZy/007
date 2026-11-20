/**
 * 升级包的读写与校验（规格 docs/specs/2026-11-19-software-update-spec.md §4.2）。
 *
 * 构建脚本、启动引导和运行期导入共用本模块，因此只依赖 Node 内置能力。
 *
 * 容器布局：
 *   8 字节标识 | u32 manifest 长度 | manifest(JSON) | u32 签名长度 | 签名 | gzip(按 manifest.files 顺序拼接的文件内容)
 * manifest 在最前，签名只覆盖 manifest 原始字节；各文件的大小和 sha256 都在 manifest 里，
 * 所以验签通过后才开始解任何文件，解出的每个文件再逐一比对摘要。
 */
const path = require('node:path');
const zlib = require('node:zlib');
const crypto = require('node:crypto');

// Electron 的 fs 把 *.asar 当目录；升级包和版本目录里的 app.asar 必须按普通文件读写。
let fs;
try { fs = require('original-fs'); } catch { fs = require('node:fs'); }

const MAGIC = Buffer.from('NIPUPD1\n', 'latin1');
const FORMAT_VERSION = 1;
const PRODUCT = 'nip-traffic-system';
const STYLES = ['HJ', 'HJJ', 'LJ'];
const PACKAGE_EXTENSION = '.nipupd';
const MAX_MANIFEST_BYTES = 16 * 1024 * 1024;
const MAX_SIGNATURE_BYTES = 1024;
const REQUIRED_FILES = ['app.asar', 'backend/quarkus-run.jar', 'bootstrap/seed.sql'];

class UpdateError extends Error {
    constructor(code, message) {
        super(message);
        this.name = 'UpdateError';
        this.code = code;
    }
}

function parseVersion(value) {
    const match = /^(\d+)\.(\d+)\.(\d+)$/.exec(String(value));
    if (!match) throw new UpdateError('version', `版本号格式无效：${value}`);
    return match.slice(1).map(Number);
}

function compareVersions(left, right) {
    const a = parseVersion(left), b = parseVersion(right);
    for (let index = 0; index < 3; index++) if (a[index] !== b[index]) return a[index] < b[index] ? -1 : 1;
    return 0;
}

function sha256File(file) {
    const hash = crypto.createHash('sha256');
    const buffer = Buffer.allocUnsafe(1024 * 1024);
    const fd = fs.openSync(file, 'r');
    try {
        let bytes;
        while ((bytes = fs.readSync(fd, buffer, 0, buffer.length, null)) !== 0) hash.update(buffer.subarray(0, bytes));
    } finally { fs.closeSync(fd); }
    return hash.digest('hex');
}

function safeRelativePath(value) {
    return typeof value === 'string' && value.length > 0 && value.length <= 512
        && !value.includes('\\') && !value.includes(':') && !value.includes('\0') && !value.startsWith('/')
        && value.split('/').every(part => part && part !== '.' && part !== '..' && !/[. ]$/.test(part));
}

function validateManifest(manifest) {
    const fail = message => { throw new UpdateError('manifest', `升级包清单无效：${message}`); };
    if (!manifest || typeof manifest !== 'object' || Array.isArray(manifest)) fail('不是对象');
    if (manifest.formatVersion !== FORMAT_VERSION) throw new UpdateError('format', '升级包格式版本不受支持，请使用新的完整安装包');
    if (typeof manifest.product !== 'string' || !manifest.product) fail('缺少产品标识');
    parseVersion(manifest.version);
    parseVersion(manifest.minFromVersion);
    if (compareVersions(manifest.minFromVersion, manifest.version) > 0) fail('最低起始版本高于目标版本');
    if (typeof manifest.target !== 'string' || !/^[a-z0-9]+-[a-z0-9]+$/.test(manifest.target)) fail('目标平台无效');
    if (!STYLES.includes(manifest.style)) fail('风格无效');
    if (!Number.isInteger(manifest.runtimeBaseline) || manifest.runtimeBaseline < 1) fail('运行时基线无效');
    if (!Array.isArray(manifest.migrations) || manifest.migrations.some(name => typeof name !== 'string' || !/^[A-Za-z0-9._-]+\.sql$/.test(name))) fail('迁移列表无效');
    if (new Set(manifest.migrations).size !== manifest.migrations.length) fail('迁移列表重复');
    if (!Array.isArray(manifest.files) || !manifest.files.length) fail('缺少文件列表');
    const seen = new Set();
    for (const file of manifest.files) {
        if (!file || !safeRelativePath(file.path)) fail(`文件路径不安全：${file && file.path}`);
        if (!Number.isSafeInteger(file.size) || file.size < 0) fail(`文件大小无效：${file.path}`);
        if (typeof file.sha256 !== 'string' || !/^[0-9a-f]{64}$/.test(file.sha256)) fail(`文件摘要无效：${file.path}`);
        // Windows 文件系统不区分大小写，大小写不同的同名条目会互相覆盖。
        const key = file.path.toLowerCase();
        if (seen.has(key) || key === 'manifest.json' || key === 'manifest.sig') fail(`文件重复或占用保留名：${file.path}`);
        seen.add(key);
    }
    for (const required of [...REQUIRED_FILES, ...manifest.migrations.map(name => `bootstrap/migrations/${name}`)]) {
        if (!seen.has(required.toLowerCase())) fail(`缺少必需文件：${required}`);
    }
    return manifest;
}

function verifySignature(manifestBytes, signature, publicKeys) {
    for (const key of publicKeys) {
        try { if (crypto.verify(null, manifestBytes, key, signature)) return; } catch { /* 换下一把公钥 */ }
    }
    throw new UpdateError('signature', '升级包签名校验失败，文件可能已损坏或来源不可信');
}

/** 读取并验签包头；返回值里的 manifest 已通过结构校验。 */
function readHeader(file, publicKeys) {
    let fd;
    try { fd = fs.openSync(file, 'r'); } catch { throw new UpdateError('format', '无法读取升级包文件'); }
    try {
        const exact = (length, position) => {
            const buffer = Buffer.alloc(length);
            if (fs.readSync(fd, buffer, 0, length, position) !== length) throw new UpdateError('format', '升级包文件不完整');
            return buffer;
        };
        if (!exact(MAGIC.length, 0).equals(MAGIC)) throw new UpdateError('format', '不是本软件的升级包文件');
        let offset = MAGIC.length;
        const manifestLength = exact(4, offset).readUInt32BE(0);
        if (!manifestLength || manifestLength > MAX_MANIFEST_BYTES) throw new UpdateError('format', '升级包文件不完整');
        const manifestBytes = exact(manifestLength, offset += 4);
        const signatureLength = exact(4, offset += manifestLength).readUInt32BE(0);
        if (!signatureLength || signatureLength > MAX_SIGNATURE_BYTES) throw new UpdateError('format', '升级包文件不完整');
        const signature = exact(signatureLength, offset += 4);
        verifySignature(manifestBytes, signature, publicKeys);
        let manifest;
        try { manifest = JSON.parse(manifestBytes.toString('utf8')); } catch { throw new UpdateError('manifest', '升级包清单无效：无法解析'); }
        return {manifest: validateManifest(manifest), manifestBytes, signature, payloadOffset: offset + signatureLength};
    } finally { fs.closeSync(fd); }
}

/**
 * 逐文件校验负载；给出 destination 时同时解到该目录。
 * 解包先写同级临时目录，全部摘要通过后才改名到位，中断不会留下半成品目标目录。
 */
async function extractPayload(file, header, destination) {
    const stage = destination ? `${destination}.partial-${crypto.randomBytes(6).toString('hex')}` : null;
    const files = header.manifest.files;
    let index = -1, remaining = 0, hash = null, fd = null;
    const closeCurrent = () => { if (fd !== null) { fs.closeSync(fd); fd = null; } };
    const finishCurrent = () => {
        closeCurrent();
        if (index >= 0 && hash.digest('hex') !== files[index].sha256) throw new UpdateError('corrupt', `升级包内容校验失败：${files[index].path}`);
    };
    const openNext = () => {
        finishCurrent();
        index += 1;
        if (index >= files.length) return false;
        remaining = files[index].size;
        hash = crypto.createHash('sha256');
        if (stage) {
            const target = path.join(stage, ...files[index].path.split('/'));
            fs.mkdirSync(path.dirname(target), {recursive: true});
            fd = fs.openSync(target, 'wx');
        }
        return true;
    };
    const source = fs.createReadStream(file, {start: header.payloadOffset});
    const gunzip = zlib.createGunzip();
    source.on('error', error => gunzip.destroy(error));
    source.pipe(gunzip);
    try {
        if (stage) {
            fs.mkdirSync(path.dirname(stage), {recursive: true});
            fs.mkdirSync(stage);
        }
        let active = openNext();
        for await (let chunk of gunzip) {
            while (chunk.length) {
                while (active && remaining === 0) active = openNext();
                if (!active) throw new UpdateError('corrupt', '升级包内容多于清单');
                const part = chunk.subarray(0, Math.min(chunk.length, remaining));
                hash.update(part);
                if (fd !== null) fs.writeSync(fd, part);
                remaining -= part.length;
                chunk = chunk.subarray(part.length);
            }
        }
        while (active && remaining === 0) active = openNext();
        if (active) throw new UpdateError('corrupt', '升级包内容不完整');
        if (stage) {
            fs.writeFileSync(path.join(stage, 'manifest.json'), header.manifestBytes);
            fs.writeFileSync(path.join(stage, 'manifest.sig'), header.signature);
            fs.rmSync(destination, {recursive: true, force: true});
            fs.renameSync(stage, destination);
        }
    } catch (error) {
        source.destroy();
        gunzip.destroy();
        closeCurrent();
        if (stage) fs.rmSync(stage, {recursive: true, force: true});
        if (error instanceof UpdateError) throw error;
        throw new UpdateError('corrupt', `升级包内容无法读取：${error.message}`);
    }
}

/** 校验已解出的版本目录：签名、清单结构以及清单内每个文件的大小与摘要。 */
function verifyReleaseDirectory(directory, publicKeys) {
    let manifestBytes, signature;
    try {
        manifestBytes = fs.readFileSync(path.join(directory, 'manifest.json'));
        signature = fs.readFileSync(path.join(directory, 'manifest.sig'));
    } catch { throw new UpdateError('corrupt', '版本目录缺少清单或签名'); }
    verifySignature(manifestBytes, signature, publicKeys);
    let manifest;
    try { manifest = validateManifest(JSON.parse(manifestBytes.toString('utf8'))); }
    catch (error) { throw error instanceof UpdateError ? error : new UpdateError('manifest', '版本目录清单无法解析'); }
    for (const file of manifest.files) {
        const target = path.join(directory, ...file.path.split('/'));
        let stat;
        try { stat = fs.lstatSync(target); } catch { throw new UpdateError('corrupt', `版本目录缺少文件：${file.path}`); }
        if (!stat.isFile() || stat.size !== file.size || sha256File(target) !== file.sha256) {
            throw new UpdateError('corrupt', `版本目录文件已被改动：${file.path}`);
        }
    }
    return manifest;
}

/**
 * 生成升级包。entries 为 [{path, source}]，顺序即包内顺序；fields 为除 files 外的清单字段。
 */
async function createPackage({output, fields, entries, privateKey}) {
    const files = entries.map(entry => {
        const stat = fs.lstatSync(entry.source);
        if (!stat.isFile()) throw new UpdateError('manifest', `升级包只能包含普通文件：${entry.path}`);
        return {path: entry.path, size: stat.size, sha256: sha256File(entry.source)};
    });
    const manifest = validateManifest({formatVersion: FORMAT_VERSION, product: PRODUCT, ...fields, files});
    const manifestBytes = Buffer.from(JSON.stringify(manifest, null, 2), 'utf8');
    const signature = crypto.sign(null, manifestBytes, privateKey);
    const length = value => { const buffer = Buffer.alloc(4); buffer.writeUInt32BE(value); return buffer; };
    const temporary = `${output}.tmp-${crypto.randomBytes(6).toString('hex')}`;
    try {
        fs.writeFileSync(temporary, Buffer.concat([MAGIC, length(manifestBytes.length), manifestBytes, length(signature.length), signature]), {flag: 'wx'});
        const gzip = zlib.createGzip({level: 6});
        const sink = fs.createWriteStream(temporary, {flags: 'a'});
        const done = new Promise((resolve, reject) => {
            sink.once('finish', resolve);
            sink.once('error', reject);
            gzip.once('error', reject);
        });
        gzip.pipe(sink);
        const buffer = Buffer.allocUnsafe(1024 * 1024);
        for (let index = 0; index < entries.length; index++) {
            const fd = fs.openSync(entries[index].source, 'r');
            try {
                let bytes, total = 0;
                while ((bytes = fs.readSync(fd, buffer, 0, buffer.length, null)) !== 0) {
                    total += bytes;
                    if (!gzip.write(Buffer.from(buffer.subarray(0, bytes)))) await new Promise(resolve => gzip.once('drain', resolve));
                }
                if (total !== files[index].size) throw new UpdateError('manifest', `打包期间文件发生变化：${entries[index].path}`);
            } finally { fs.closeSync(fd); }
        }
        gzip.end();
        await done;
        fs.renameSync(temporary, output);
    } catch (error) {
        fs.rmSync(temporary, {force: true});
        throw error;
    }
    return manifest;
}

function packageFileName(manifest) {
    return `nip-update-${manifest.version}-${manifest.target}-${manifest.style}${PACKAGE_EXTENSION}`;
}

module.exports = {
    FORMAT_VERSION, PRODUCT, STYLES, PACKAGE_EXTENSION, UpdateError,
    parseVersion, compareVersions, sha256File, safeRelativePath, validateManifest, verifySignature,
    readHeader, extractPayload, verifyReleaseDirectory, createPackage, packageFileName
};
