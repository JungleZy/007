const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const net = require('node:net');
const path = require('node:path');
const crypto = require('node:crypto');
const {IntegratedRuntime} = require('../service/integrated-runtime');
const {workspace} = require('./update-fixture.cjs');

const sha = text => crypto.createHash('sha256').update(text).digest('hex');

// 搭一个已完成首次初始化的实例：数据库由桩代替，返回值按语句定制。
async function readyInstance(t) {
    const root = workspace(t, 'nip-update-runtime-');
    const server = net.createServer();
    await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
    t.after(() => new Promise(resolve => server.close(resolve)));
    const base = {appPath: root, dataPath: path.join(root, 'data'), databasePort: server.address().port, startBackend: false};
    const events = [];
    const create = (extra = {}, applied = {}) => {
        const runtime = new IntegratedRuntime({...base, ...extra});
        runtime.sql = async statement => {
            events.push(statement);
            if (statement === 'SELECT @@datadir;') return runtime.datadir;
            if (statement === 'SELECT id FROM nip_desktop_migrations;') return Object.keys(applied).join('\n');
            const one = /^SELECT digest FROM nip_desktop_migrations WHERE id='([^']+)';$/.exec(statement);
            return one ? applied[one[1]] || '' : '';
        };
        runtime.run = async (executable, args, options) => {
            events.push({run: path.basename(executable), args, options});
            if (options.outputFile) fs.writeFileSync(options.outputFile, '-- dump');
            return '';
        };
        runtime.ownedDatabasePid = async () => 12345;
        return runtime;
    };
    const first = create();
    for (const file of ['manifest.json', `database/bin/mysqld${first.suffix}`, `runtime/bin/java${first.suffix}`, 'backend/quarkus-run.jar']) {
        const destination = path.join(first.assets, file);
        fs.mkdirSync(path.dirname(destination), {recursive: true});
        fs.writeFileSync(destination, file === 'manifest.json'
            ? JSON.stringify({target: first.target, databaseVersion: '5.7.44', migrations: [], files: {}}) : 'fixture');
    }
    fs.mkdirSync(first.tools, {recursive: true});
    fs.writeFileSync(path.join(first.tools, `mysql${first.suffix}`), 'fixture');
    fs.mkdirSync(first.datadir, {recursive: true});
    first.assetText = () => '-- seed';
    await first.initialize();
    events.length = 0;
    return {root, base, create, events, target: first.target};
}

function releaseDirectory(root, target, migrations) {
    const directory = path.join(root, 'release');
    const files = [];
    for (const [name, text] of Object.entries({'backend/quarkus-run.jar': 'jar', ...Object.fromEntries(Object.entries(migrations).map(([id, sql]) => [`bootstrap/migrations/${id}`, sql]))})) {
        fs.mkdirSync(path.dirname(path.join(directory, name)), {recursive: true});
        fs.writeFileSync(path.join(directory, name), text);
        files.push({path: name, size: text.length, sha256: sha(text)});
    }
    fs.writeFileSync(path.join(directory, 'manifest.json'), JSON.stringify({target, migrations: Object.keys(migrations), files}));
    return directory;
}

test('已升级版本的迁移取自版本目录：先备份、再通知、后执行，且只补未执行项', async t => {
    const {root, create, events, target} = await readyInstance(t);
    const releaseRoot = releaseDirectory(root, target, {'01-old.sql': 'ALTER TABLE a ADD b int;', '02-new.sql': 'ALTER TABLE a ADD c int;'});
    let notified;
    const runtime = create({releaseRoot, onMigrationsStarting: backup => { notified = backup; events.push('notified'); }},
        {'01-old.sql': sha('ALTER TABLE a ADD b int;')});
    await runtime.initialize();
    const dump = events.findIndex(event => event.run?.startsWith('mysqldump'));
    const migration = events.indexOf('ALTER TABLE a ADD c int;');
    assert.ok(dump >= 0 && dump < events.indexOf('notified') && events.indexOf('notified') < migration);
    assert.equal(path.dirname(notified), path.join(runtime.dataPath, 'backups'));
    assert.equal(events.includes('ALTER TABLE a ADD b int;'), false);
    assert.ok(events.some(event => typeof event === 'string' && event.includes("INSERT INTO nip_desktop_migrations VALUES ('02-new.sql'")));
});

test('没有待执行迁移时不备份也不通知', async t => {
    const {root, create, events, target} = await readyInstance(t);
    const releaseRoot = releaseDirectory(root, target, {'01-old.sql': 'ALTER TABLE a ADD b int;'});
    let notified = false;
    await create({releaseRoot, onMigrationsStarting: () => { notified = true; }}, {'01-old.sql': sha('ALTER TABLE a ADD b int;')}).initialize();
    assert.equal(notified, false);
    assert.equal(events.some(event => event.run), false);
});

test('数据库已被更高版本更新时拒绝启动，不执行任何迁移', async t => {
    const {root, create, events, target} = await readyInstance(t);
    const releaseRoot = releaseDirectory(root, target, {'01-old.sql': 'ALTER TABLE a ADD b int;'});
    const runtime = create({releaseRoot}, {'09-future.sql': 'f'.repeat(64)});
    await assert.rejects(runtime.initialize(), /更高版本/);
    assert.equal(events.includes('ALTER TABLE a ADD b int;'), false);
    assert.equal(events.some(event => event.run), false);
});

test('版本目录里的迁移文件与清单摘要不符时拒绝执行', async t => {
    const {root, create, events, target} = await readyInstance(t);
    const releaseRoot = releaseDirectory(root, target, {'01-new.sql': 'ALTER TABLE a ADD b int;'});
    fs.writeFileSync(path.join(releaseRoot, 'bootstrap/migrations/01-new.sql'), 'DROP TABLE a;');
    await assert.rejects(create({releaseRoot}).initialize(), /校验失败/);
    assert.equal(events.includes('DROP TABLE a;'), false);
});

test('恢复升级前备份：只接受 backups 目录内的文件，恢复后再检查迁移', async t => {
    const {root, base, create, events} = await readyInstance(t);
    const outside = path.join(root, 'evil.sql');
    fs.writeFileSync(outside, '-- x');
    await assert.rejects(create({restoreBackup: outside}).initialize(), /备份不可用/);
    await assert.rejects(create({restoreBackup: path.join(base.dataPath, 'backups/missing.sql')}).initialize(), /备份不可用/);
    assert.equal(events.some(event => typeof event === 'string' && event.includes('DROP DATABASE')), false);
    const backup = path.join(base.dataPath, 'backups/before-upgrade-1.sql');
    fs.writeFileSync(backup, '-- dump');
    let restored = false;
    await create({restoreBackup: backup, onRestored: () => { restored = true; }}).initialize();
    const drop = events.findIndex(event => typeof event === 'string' && event.startsWith('DROP DATABASE project006;'));
    const load = events.findIndex(event => event.run?.startsWith('mysql') && event.options.inputFile === backup);
    assert.ok(drop >= 0 && drop < load && load < events.indexOf('SELECT id FROM nip_desktop_migrations;'));
    assert.equal(events[load].args.at(-1), 'project006');
    assert.equal(restored, true);
});
