const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const {installPackage, listStoredPackages} = require('../update/installer');
const releases = require('../update/releases');
const {keys, workspace, identity, buildPackage} = require('./update-fixture.cjs');

const running = {version: '3.1.3', release: false};

async function installed(t, fields = {}) {
    const root = workspace(t), signing = keys(), dataPath = path.join(root, 'data');
    const built = await buildPackage(root, signing.privateKey, fields);
    const result = await installPackage({file: built.file, dataPath, identity: identity(), running, publicKeys: signing.publicKeys});
    return {root, dataPath, signing, built, result};
}

test('导入后解出版本目录、留存升级包并把指针置为待确认', async t => {
    const {dataPath, result, signing} = await installed(t);
    assert.equal(result.action, 'installed');
    assert.deepEqual({...releases.readPointer(dataPath), installedAt: undefined},
        {schema: 1, version: '3.2.0', previous: null, state: 'pending', attempts: 0, migrated: false, installedAt: undefined});
    const stored = listStoredPackages(dataPath);
    assert.equal(stored.length, 1);
    assert.equal(stored[0].fileName, 'nip-update-3.2.0-linux-x64-HJ.nipupd');
    assert.equal(fs.statSync(path.join(dataPath, 'updates/linux-x64/HJ', stored[0].fileName)).size, stored[0].size);
    const selected = releases.selectRelease({dataPath, identity: identity(), publicKeys: signing.publicKeys});
    assert.equal(selected.root, path.join(dataPath, 'releases/3.2.0'));
    assert.equal(selected.pending, true);
});

test('低版本、基线不符、起始版本过旧、错产品和被篡改的包均被拒绝且不改指针', async t => {
    const root = workspace(t), signing = keys(), dataPath = path.join(root, 'data');
    const attempt = async (fields, code, local = identity(), publicKeys = signing.publicKeys) => {
        const {file} = await buildPackage(root, signing.privateKey, fields);
        await assert.rejects(installPackage({file, dataPath, identity: local, running, publicKeys}), {code}, code);
    };
    await attempt({version: '3.1.3', minFromVersion: '3.1.0'}, 'not-newer');
    await attempt({version: '3.1.2', minFromVersion: '3.1.0'}, 'not-newer');
    await attempt({runtimeBaseline: 2}, 'baseline');
    await attempt({version: '3.3.0', minFromVersion: '3.2.0'}, 'too-old');
    await attempt({}, 'product', identity({product: 'other'}));
    await attempt({}, 'signature', identity(), keys().publicKeys);
    await attempt({}, 'identity', identity({style: null}));
    assert.equal(releases.readPointer(dataPath), null);
    assert.equal(fs.existsSync(path.join(dataPath, 'releases')), false);
    assert.deepEqual(listStoredPackages(dataPath), []);
});

test('其他平台或风格的包只留存不安装，同组合只留最新一个', async t => {
    const root = workspace(t), signing = keys(), dataPath = path.join(root, 'data');
    const install = async fields => installPackage({file: (await buildPackage(root, signing.privateKey, fields)).file,
        dataPath, identity: identity(), running, publicKeys: signing.publicKeys});
    assert.equal((await install({target: 'windows-ia32'})).action, 'stored');
    assert.equal((await install({style: 'LJ', version: '3.2.0'})).action, 'stored');
    assert.equal((await install({style: 'LJ', version: '3.3.0'})).action, 'stored');
    await assert.rejects(install({style: 'LJ', version: '3.2.5'}), {code: 'not-newer'});
    assert.equal(releases.readPointer(dataPath), null);
    assert.deepEqual(listStoredPackages(dataPath).map(item => `${item.target}/${item.style}/${item.version}`).sort(),
        ['linux-x64/LJ/3.3.0', 'windows-ia32/HJ/3.2.0']);
    assert.deepEqual(fs.readdirSync(path.join(dataPath, 'updates/linux-x64/LJ')).sort(), ['info.json', 'nip-update-3.3.0-linux-x64-LJ.nipupd']);
});

test('从已升级版本再次升级时记录上一版本，确认后只保留当前与上一版本', async t => {
    const {root, dataPath, signing} = await installed(t);
    releases.confirm(dataPath);
    const next = await buildPackage(root, signing.privateKey, {version: '3.3.0'});
    await installPackage({file: next.file, dataPath, identity: identity(), running: {version: '3.2.0', release: true}, publicKeys: signing.publicKeys});
    fs.mkdirSync(path.join(dataPath, 'releases/3.0.0'));
    assert.equal(releases.readPointer(dataPath).previous, '3.2.0');
    releases.confirm(dataPath);
    assert.deepEqual(fs.readdirSync(path.join(dataPath, 'releases')).sort(), ['3.2.0', '3.3.0', 'current.json']);
    assert.equal(releases.readPointer(dataPath).state, 'confirmed');
});

test('没有指针时使用出厂版本；出厂版本不低于指针版本时清除指针', async t => {
    const {dataPath, signing} = await installed(t);
    const empty = path.join(dataPath, 'none');
    assert.equal(releases.selectRelease({dataPath: empty, identity: identity(), publicKeys: signing.publicKeys}).root, null);
    const selected = releases.selectRelease({dataPath, identity: identity({version: '3.2.0'}), publicKeys: signing.publicKeys});
    assert.equal(selected.root, null);
    assert.equal(selected.version, '3.2.0');
    assert.equal(releases.readPointer(dataPath), null);
});

test('待确认版本文件被改动时回退到出厂版本并给出提示', async t => {
    const {dataPath, signing} = await installed(t);
    fs.appendFileSync(path.join(dataPath, 'releases/3.2.0/app.asar'), 'x');
    const selected = releases.selectRelease({dataPath, identity: identity(), publicKeys: signing.publicKeys});
    assert.equal(selected.root, null);
    assert.match(selected.notice, /3\.2\.0 未成功/);
    assert.equal(releases.readPointer(dataPath), null);
    assert.equal(releases.readFailure(dataPath).version, '3.2.0');
});

test('待确认版本上次启动未确认：未迁移则回退，已迁移则继续加载', async t => {
    const {dataPath, signing} = await installed(t);
    const select = () => releases.selectRelease({dataPath, identity: identity(), publicKeys: signing.publicKeys});
    assert.equal(select().pending, true);
    releases.markMigrated(dataPath, '/backups/before.sql');
    assert.equal(select().root, path.join(dataPath, 'releases/3.2.0'));
    assert.equal(releases.readPointer(dataPath).backup, '/backups/before.sql');
    releases.writePointer(dataPath, {...releases.readPointer(dataPath), migrated: false});
    const rolled = select();
    assert.equal(rolled.root, null);
    assert.match(rolled.notice, /已恢复到升级前的版本/);
});

test('用户在确认前主动退出不计为失败的启动', async t => {
    const {dataPath, signing} = await installed(t);
    const select = () => releases.selectRelease({dataPath, identity: identity(), publicKeys: signing.publicKeys});
    select();
    releases.abortAttempt(dataPath);
    assert.equal(select().root, path.join(dataPath, 'releases/3.2.0'));
});

test('回退到上一已升级版本；已确认版本损坏时不自动回退并上报', async t => {
    const {root, dataPath, signing} = await installed(t);
    releases.confirm(dataPath);
    const next = await buildPackage(root, signing.privateKey, {version: '3.3.0'});
    await installPackage({file: next.file, dataPath, identity: identity(), running: {version: '3.2.0', release: true}, publicKeys: signing.publicKeys});
    releases.rollback(dataPath, 'boom');
    const pointer = releases.readPointer(dataPath);
    assert.equal(pointer.version, '3.2.0');
    assert.equal(pointer.state, 'confirmed');
    fs.rmSync(path.join(dataPath, 'releases/3.2.0/bootstrap/seed.sql'));
    const selected = releases.selectRelease({dataPath, identity: identity(), publicKeys: signing.publicKeys});
    assert.equal(selected.root, null);
    assert.equal(selected.broken.version, '3.2.0');
    assert.equal(releases.readPointer(dataPath).version, '3.2.0');
});

test('版本目录与本机平台、风格或基线不一致时不加载', async t => {
    const {dataPath, signing} = await installed(t);
    for (const local of [identity({style: 'LJ'}), identity({target: 'linux-arm64'}), identity({runtimeBaseline: 2})]) {
        releases.writePointer(dataPath, {version: '3.2.0', previous: null, state: 'confirmed', attempts: 0, migrated: false});
        const selected = releases.selectRelease({dataPath, identity: local, publicKeys: signing.publicKeys});
        assert.equal(selected.root, null);
        assert.equal(selected.broken.version, '3.2.0');
    }
});
