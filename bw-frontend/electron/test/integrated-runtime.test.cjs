const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const {EventEmitter} = require('node:events');
const net = require('node:net');
const crypto = require('node:crypto');
const {IntegratedRuntime} = require('../service/integrated-runtime');
const {cleanSeed, businessSeed, seedInventory} = require('../../scripts/prepare-integrated-runtime.cjs');

test('初始化只保留 schema 和基础配置，不复制用户、会话或训练记录', () => {
    const snapshot = fs.readFileSync(path.resolve(__dirname, '../../../backend/database/project006.sql'), 'utf8');
    const seed = cleanSeed(snapshot);
    assert.match(seed, /CREATE TABLE IF NOT EXISTS `t_user`/);
    assert.match(seed, /INSERT IGNORE INTO `t_menus`/);
    assert.doesNotMatch(seed, /INSERT (?:IGNORE )?INTO `(?:t_user|t_user_role|t_post_telegram_train|simulation_router_room_user)`/);
    assert.equal((seed.match(/^CREATE TABLE IF NOT EXISTS/gm) || []).length, (snapshot.match(/^CREATE TABLE /gm) || []).length);
});

test('课程、章节测验、题库分类、岗前资料、评分、报底和文章均进入种子，补充 SQL 使用显式列', () => {
    const snapshot = fs.readFileSync(path.resolve(__dirname, '../../../backend/database/project006.sql'), 'utf8');
    const seed = cleanSeed(snapshot);
    const supplement = businessSeed(snapshot);
    for (const table of ['t_theory_knowledge', 't_theory_knowledge_swf', 't_theory_knowledge_test_content',
        't_theory_question', 't_theory_question_level', 't_entering_exercise_word_stock', 't_key_points',
        't_military_term_data', 't_post_radiotelephone_term_data', 't_grading_rule', 't_post_train_global_rule',
        't_general_group_net_rule', 'device_scoring_rule', 't_device', 't_cable', 't_cable_floor', 't_post_entering_exercise_word_stock']) {
        assert.ok(seed.includes('INSERT IGNORE INTO `' + table + '` (`id`,'), table);
        assert.ok(supplement.includes('INSERT IGNORE INTO `' + table + '` (`id`,'), table);
    }
    assert.doesNotMatch(supplement, /CREATE TABLE|DROP TABLE|DELETE FROM|INSERT IGNORE INTO `t_user`|INSERT IGNORE INTO `t_menus`/);
    const inventory = seedInventory(snapshot);
    assert.equal(inventory.theoryQuestionBank.t_theory_question, 6662);
    assert.equal(inventory.articles.t_post_entering_exercise_word_stock, 44);
});

test('缺少实例配置时保留已有数据库文件，不尝试初始化', async t => {
    const root = fs.mkdtempSync(path.join(os.tmpdir(), 'nip-runtime-guard-'));
    t.after(() => fs.rmSync(root, {recursive: true, force: true}));
    const runtime = new IntegratedRuntime({appPath: root, dataPath: path.join(root, 'data'), startBackend: false});
    for (const file of ['manifest.json', `database/bin/mysqld${runtime.suffix}`, `runtime/bin/java${runtime.suffix}`, 'backend/quarkus-run.jar']) {
        const destination = path.join(runtime.assets, file);
        fs.mkdirSync(path.dirname(destination), {recursive: true});
        fs.writeFileSync(destination, file === 'manifest.json' ? JSON.stringify({target: runtime.target}) : 'fixture');
    }
    fs.mkdirSync(runtime.tools, {recursive: true});
    fs.writeFileSync(path.join(runtime.tools, `mysql${runtime.suffix}`), 'fixture');
    fs.mkdirSync(runtime.datadir, {recursive: true});
    fs.writeFileSync(path.join(runtime.datadir, 'existing-data'), 'keep');
    await assert.rejects(runtime.start(), /已有数据库文件/);
    assert.equal(fs.readFileSync(path.join(runtime.datadir, 'existing-data'), 'utf8'), 'keep');
    assert.equal(fs.existsSync(runtime.stateFile), false);
});

test('关闭数据库必须等待进程结束，不能只等端口释放', async () => {
    const runtime = new IntegratedRuntime({appPath: '.', dataPath: '.', databasePort: 0});
    const child = new EventEmitter();
    child.exitCode = null;
    child.signalCode = null;
    runtime.database = child;
    runtime.ownsDatabase = true;
    let closed = false;
    runtime.sql = async statement => {
        assert.equal(statement, 'SHUTDOWN;');
        setTimeout(() => { closed = true; child.exitCode = 0; child.emit('close', 0); }, 40);
    };
    await runtime.shutdown();
    assert.equal(closed, true);
    assert.equal(runtime.ownsDatabase, false);
});

test('新实例admin使用123456a的密码摘要，重启不重置账户密码', async t => {
    const root = fs.mkdtempSync(path.join(os.tmpdir(), 'nip-admin-default-'));
    t.after(() => fs.rmSync(root, {recursive: true, force: true}));
    // Simulate an owned running database; SQL is captured without touching a real database.
    const server = net.createServer();
    await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
    t.after(() => new Promise(resolve => server.close(resolve)));
    const options = {appPath: root, dataPath: path.join(root, 'data'),
        databasePort: server.address().port, startBackend: false};
    const runtime = new IntegratedRuntime(options);
    for (const file of ['manifest.json', `database/bin/mysqld${runtime.suffix}`,
        `runtime/bin/java${runtime.suffix}`, 'backend/quarkus-run.jar']) {
        const destination = path.join(runtime.assets, file);
        fs.mkdirSync(path.dirname(destination), {recursive: true});
        fs.writeFileSync(destination, file === 'manifest.json'
            ? JSON.stringify({target: runtime.target, databaseVersion: '5.7.44', migrations: []}) : 'fixture');
    }
    fs.mkdirSync(runtime.tools, {recursive: true});
    fs.writeFileSync(path.join(runtime.tools, `mysql${runtime.suffix}`), 'fixture');
    fs.mkdirSync(runtime.datadir, {recursive: true});
    const statements = [];
    const stubDatabase = instance => {
        instance.sql = async statement => {
            statements.push(statement);
            return statement === 'SELECT @@datadir;' ? runtime.datadir : '';
        };
        instance.ownedDatabasePid = async () => 12345;
        instance.assetText = () => '-- fixture seed';
    };
    stubDatabase(runtime);
    assert.deepEqual(await runtime.initialize(), {account: 'admin', password: '123456a'});
    const createAdmin = statements.find(statement => statement.includes('INSERT IGNORE INTO t_user '));
    const encoded = createAdmin.match(/pbkdf2_sha256\$1\$600000\$[^']+/)[0];
    const [, , iterations, salt, expected] = encoded.split('$');
    assert.equal(crypto.pbkdf2Sync('123456a', Buffer.from(salt, 'base64url'), Number(iterations), 32, 'sha256')
        .toString('base64url'), expected);
    assert.notEqual(runtime.state.rootPassword, '123456a');
    assert.notEqual(runtime.state.appPassword, '123456a');
    runtime.acknowledgeInitialAdmin();
    statements.length = 0;
    const restarted = new IntegratedRuntime(options);
    stubDatabase(restarted);
    assert.equal(await restarted.initialize(), null);
    assert.equal(statements.some(statement => /(?:INSERT|UPDATE).*t_user/.test(statement)), false);
});
