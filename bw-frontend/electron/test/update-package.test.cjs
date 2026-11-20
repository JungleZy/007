const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const {readHeader, extractPayload, verifyReleaseDirectory, compareVersions, validateManifest, createPackage} = require('../shared/update-package');
const {workspace, buildPackage} = require('./update-fixture.cjs');

test('升级包打包后可原样解出，含空文件', async t => {
    const root = workspace(t);
    const {file, files} = await buildPackage(root);
    const header = readHeader(file);
    const destination = path.join(root, 'out/3.2.0');
    await extractPayload(file, header, destination);
    for (const [name, text] of Object.entries(files)) assert.equal(fs.readFileSync(path.join(destination, name), 'utf8'), text);
    assert.equal(verifyReleaseDirectory(destination).version, '3.2.0');
    assert.deepEqual(fs.readdirSync(path.join(root, 'out')), ['3.2.0']);
});

test('包头被改动一个字节即拒绝', async t => {
    const root = workspace(t);
    const {file} = await buildPackage(root);
    const bytes = fs.readFileSync(file);
    const position = bytes.indexOf('"3.2.0"');
    bytes[position + 1] = '4'.charCodeAt(0);
    fs.writeFileSync(file, bytes);
    assert.throws(() => readHeader(file), {code: 'corrupt'});
    fs.writeFileSync(file, 'not a package');
    assert.throws(() => readHeader(file), {code: 'format'});
});

test('负载被改动或截断时解包失败且不留下目标目录', async t => {
    const root = workspace(t);
    // 不可压缩的内容保证改动落在数据而不是 gzip 尾部的填充上。
    const {file} = await buildPackage(root, {}, {'app.asar': require('node:crypto').randomBytes(200000).toString('hex')});
    const header = readHeader(file);
    const original = fs.readFileSync(file);
    const destination = path.join(root, 'out/3.2.0');
    for (const damage of [bytes => { bytes[header.payloadOffset + 50000] ^= 0xff; return bytes; }, bytes => bytes.subarray(0, bytes.length - 40000)]) {
        fs.writeFileSync(file, damage(Buffer.from(original)));
        await assert.rejects(extractPayload(file, header, destination), {code: 'corrupt'});
        assert.deepEqual(fs.readdirSync(path.join(root, 'out')), []);
    }
});

test('清单拒绝路径穿越、保留名、重复和缺少必需文件', async t => {
    const root = workspace(t);
    const {manifest} = await buildPackage(root);
    const withFile = name => ({...manifest, files: [...manifest.files, {path: name, size: 0, sha256: '0'.repeat(64)}]});
    for (const name of ['../evil', '/etc/passwd', 'a/../../b', 'a\\b', 'C:/x', 'manifest.json', 'APP.ASAR', 'a/./b', 'dir/']) {
        assert.throws(() => validateManifest(withFile(name)), {code: 'manifest'}, name);
    }
    assert.throws(() => validateManifest({...manifest, files: manifest.files.filter(file => file.path !== 'backend/quarkus-run.jar')}), {code: 'manifest'});
    assert.throws(() => validateManifest({...manifest, migrations: ['missing.sql']}), {code: 'manifest'});
    assert.throws(() => validateManifest({...manifest, formatVersion: 2}), {code: 'format'});
    await assert.rejects(createPackage({output: path.join(root, 'x.nipupd'), entries: [], fields: {}}), {code: 'version'});
    assert.equal(fs.existsSync(path.join(root, 'x.nipupd')), false);
});

test('版本目录中任一文件被改动即校验失败', async t => {
    const root = workspace(t);
    const {file} = await buildPackage(root);
    const destination = path.join(root, '3.2.0');
    await extractPayload(file, readHeader(file), destination);
    fs.appendFileSync(path.join(destination, 'backend/quarkus-run.jar'), 'x');
    assert.throws(() => verifyReleaseDirectory(destination), {code: 'corrupt'});
});

test('版本号按三段数值比较', () => {
    assert.equal(compareVersions('3.1.10', '3.1.9'), 1);
    assert.equal(compareVersions('3.2.0', '3.10.0'), -1);
    assert.equal(compareVersions('3.1.3', '3.1.3'), 0);
    assert.throws(() => compareVersions('3.1', '3.1.3'), {code: 'version'});
    assert.throws(() => compareVersions('3.1.3-1', '3.1.3'), {code: 'version'});
});
