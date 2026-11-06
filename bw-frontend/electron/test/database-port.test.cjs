const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const net = require('node:net');
const {IntegratedRuntime} = require('../service/integrated-runtime');
const {changeSavedDatabasePort} = require('../../support/database-port/set-database-port.cjs');

function fixture(t, port = 13306) {
    const root = fs.mkdtempSync(path.join(os.tmpdir(), 'nip-port-'));
    t.after(() => fs.rmSync(root, {recursive: true, force: true}));
    fs.mkdirSync(path.join(root, 'config'));
    fs.mkdirSync(path.join(root, 'mysql'));
    const state = {version: 1, phase: 'ready', port, rootPassword: 'fixture-root', appPassword: 'fixture-app', marker: 'preserve'};
    const file = path.join(root, 'config/database-instance.json');
    fs.writeFileSync(file, JSON.stringify(state));
    return {root, state, file};
}

async function listen(t) {
    const server = net.createServer();
    await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
    t.after(async () => { if (server.listening) await new Promise(resolve => server.close(resolve)); });
    return server;
}

test('已安装配置工具只修改端口，保留凭据和其他字段，并支持重复执行', async t => {
    const {file, state} = fixture(t);
    const original = fs.readFileSync(file);
    const changed = await changeSavedDatabasePort(file, {portAvailable: async () => true});
    assert.deepEqual(JSON.parse(fs.readFileSync(file)), {...state, port: 6912});
    assert.deepEqual(fs.readFileSync(changed.backup), original);
    const second = await changeSavedDatabasePort(file, {portAvailable: async () => { throw new Error('must not probe for a no-op'); }});
    assert.equal(second.changed, false);
    assert.equal(fs.readdirSync(path.dirname(file)).filter(name => name.endsWith('.bak')).length, 1);
});

test('目标端口占用或本实例进程仍运行时，端口工具不修改配置', async t => {
    const {file, state} = fixture(t);
    await assert.rejects(changeSavedDatabasePort(file, {portAvailable: async () => false}), /6912/);
    fs.writeFileSync(path.join(path.dirname(file), 'mysql.pid'), '12345');
    await assert.rejects(changeSavedDatabasePort(file, {portAvailable: async () => true, processAlive: () => true}), /仍在运行/);
    assert.deepEqual(JSON.parse(fs.readFileSync(file)), state);
    assert.equal(fs.readdirSync(path.dirname(file)).some(name => name.endsWith('.bak')), false);
});

test('自动迁移遇到无关旧端口服务时只更新自己的配置，不发送关闭命令', async t => {
    const server = await listen(t);
    const candidate = await listen(t);
    const nextPort = candidate.address().port;
    await new Promise(resolve => candidate.close(resolve));
    const {root, state, file} = fixture(t, server.address().port);
    const runtime = new IntegratedRuntime({appPath: '.', dataPath: root});
    runtime.state = state;
    runtime.databasePort = state.port;
    runtime.progress = () => {};
    runtime.sql = async statement => {
        assert.equal(statement, 'SELECT @@datadir;');
        return path.join(root, 'another-database');
    };
    runtime.shutdown = async () => { throw new Error('Unrelated service must not be stopped'); };
    await runtime.migrateDatabasePort(nextPort);
    assert.equal(server.listening, true);
    assert.deepEqual(JSON.parse(fs.readFileSync(file)), {...state, port: nextPort});
});

test('自动迁移先正常关闭已验证属主的旧实例，再保存新端口', async t => {
    const server = await listen(t);
    const candidate = await listen(t);
    const nextPort = candidate.address().port;
    await new Promise(resolve => candidate.close(resolve));
    const {root, state, file} = fixture(t, server.address().port);
    const pidFile = path.join(root, 'config/mysql.pid');
    fs.writeFileSync(pidFile, '12345');
    const runtime = new IntegratedRuntime({appPath: '.', dataPath: root});
    runtime.state = state;
    runtime.databasePort = state.port;
    runtime.progress = () => {};
    runtime.sql = async statement => statement.includes('datadir') ? path.join(root, 'mysql') : pidFile;
    let stopped = false;
    runtime.shutdown = async () => {
        assert.equal(runtime.ownsDatabase, true);
        assert.equal(JSON.parse(fs.readFileSync(file)).port, state.port);
        await new Promise(resolve => server.close(resolve));
        runtime.ownsDatabase = false;
        stopped = true;
    };
    await runtime.migrateDatabasePort(nextPort);
    assert.equal(stopped, true);
    assert.equal(runtime.databasePid, null);
    assert.deepEqual(JSON.parse(fs.readFileSync(file)), {...state, port: nextPort});
});

test('自动迁移的新端口被占用时，旧配置和旧实例保持原状', async t => {
    const target = await listen(t);
    const {root, state, file} = fixture(t);
    const runtime = new IntegratedRuntime({appPath: '.', dataPath: root});
    runtime.state = state;
    runtime.databasePort = state.port;
    runtime.sql = async () => { throw new Error('No old instance query expected'); };
    await assert.rejects(runtime.migrateDatabasePort(target.address().port), /原配置未修改/);
    assert.deepEqual(JSON.parse(fs.readFileSync(file)), state);
});
