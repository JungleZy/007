const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const asar = require('asar');
const {buildUpdatePackage, assertMigrationsAppendOnly, currentMigrations} = require('../../scripts/build-update-package.cjs');
const {checkProductVersion} = require('../../scripts/check-product-version.cjs');
const {installPackage} = require('../update/installer');
const {selectRelease} = require('../update/releases');
const {workspace, identity} = require('./update-fixture.cjs');

const sha = text => crypto.createHash('sha256').update(text).digest('hex');

async function buildInputs(root, {version = '3.2.0', style = 'HJ'} = {}) {
    const app = path.join(root, 'app');
    for (const [name, text] of Object.entries({'package.json': JSON.stringify({version}), 'electron/app-main.js': '// entry',
        'public/dist/build-manifest.json': JSON.stringify({interfaceStyle: style})})) {
        fs.mkdirSync(path.dirname(path.join(app, name)), {recursive: true});
        fs.writeFileSync(path.join(app, name), text);
    }
    const appAsar = path.join(root, 'app.asar');
    await asar.createPackage(app, appAsar);
    const runtime = path.join(root, 'runtime');
    const files = {'backend/quarkus-run.jar': 'jar', 'backend/lib/main/a.jar': 'lib', 'bootstrap/seed.sql': 'seed',
        'bootstrap/migrations/01-first.sql': 'ALTER TABLE a ADD b int;', 'runtime/bin/java': 'java', 'database/bin/mysqld': 'mysqld'};
    for (const [name, text] of Object.entries(files)) {
        fs.mkdirSync(path.dirname(path.join(runtime, name)), {recursive: true});
        fs.writeFileSync(path.join(runtime, name), text);
    }
    fs.writeFileSync(path.join(runtime, 'manifest.json'), JSON.stringify({target: 'linux-x64', applicationVersion: version,
        migrations: ['01-first.sql'], files: Object.fromEntries(Object.entries(files).map(([name, text]) => [name, sha(text)]))}));
    return {appAsar, runtime};
}

test('出包只含外壳、后端与迁移，不含运行时；产物可被安装并在启动时选中', async t => {
    const root = workspace(t), inputs = await buildInputs(root);
    const policy = {minFromVersion: '3.1.3', releasedMigrations: {'01-first.sql': 'x'}};
    const report = await buildUpdatePackage({...inputs, target: 'linux-x64', style: 'HJ', output: path.join(root, 'out'), policy, productVersion: '3.2.0', sourceCommit: 'abc', releasedSource: {'01-first.sql': 'x'}});
    assert.equal(report.file, 'nip-update-3.2.0-linux-x64-HJ.nipupd');
    assert.equal(report.files, 5);
    assert.deepEqual(JSON.parse(fs.readFileSync(path.join(root, 'out', `${report.file}.json`), 'utf8')), report);
    const dataPath = path.join(root, 'data');
    await installPackage({file: path.join(root, 'out', report.file), dataPath, identity: identity(), running: {version: '3.1.3', release: false}});
    const selected = selectRelease({dataPath, identity: identity()});
    assert.deepEqual(fs.readdirSync(selected.root).sort(), ['app.asar', 'backend', 'bootstrap', 'manifest.json']);
});

test('版本、风格、平台或运行组件与产物不一致时拒绝出包', async t => {
    const root = workspace(t), inputs = await buildInputs(root);
    const base = {...inputs, target: 'linux-x64', style: 'HJ', output: path.join(root, 'out'),
        productVersion: '3.2.0', sourceCommit: 'abc', releasedSource: {}, policy: {minFromVersion: '3.1.3', releasedMigrations: {}}};
    await assert.rejects(buildUpdatePackage({...base, productVersion: '3.2.1'}), /与源码版本/);
    await assert.rejects(buildUpdatePackage({...base, style: 'LJ'}), /界面风格/);
    await assert.rejects(buildUpdatePackage({...base, target: 'linux-arm64'}), /运行组件目录属于/);
    await assert.rejects(buildUpdatePackage({...base, policy: {minFromVersion: '3.3.0', releasedMigrations: {}}}), /最低起始版本/);
    fs.writeFileSync(path.join(inputs.runtime, 'backend/quarkus-run.jar'), 'changed');
    await assert.rejects(buildUpdatePackage(base), /与其清单不符/);
    assert.deepEqual(fs.existsSync(path.join(root, 'out')) ? fs.readdirSync(path.join(root, 'out')) : [], []);
});

test('已发布的迁移被删除或改动时拒绝出包，新增迁移允许', async t => {
    const released = {'01.sql': 'a', '02.sql': 'b'};
    assertMigrationsAppendOnly(released, {...released, '03.sql': 'c'});
    assert.throws(() => assertMigrationsAppendOnly(released, {'01.sql': 'a'}), /被删除或改名：02\.sql/);
    assert.throws(() => assertMigrationsAppendOnly(released, {'01.sql': 'a', '02.sql': 'changed'}), /内容被改动：02\.sql/);
    const root = workspace(t), inputs = await buildInputs(root);
    await assert.rejects(buildUpdatePackage({...inputs, target: 'linux-x64', style: 'HJ', output: path.join(root, 'out'), productVersion: '3.2.0', sourceCommit: 'abc', releasedSource: {'01-first.sql': 'x'},
        policy: {minFromVersion: '3.1.3', releasedMigrations: {'01-first.sql': 'x', '00-gone.sql': 'y'}}}), /被删除或改名/);
});

test('仓库里已发布的迁移记录与当前迁移文件一致，产品版本各处一致', () => {
    const policy = JSON.parse(fs.readFileSync(path.resolve(__dirname, '../../build/update-release.json'), 'utf8'));
    assertMigrationsAppendOnly(policy.releasedMigrations, currentMigrations());
    assert.match(checkProductVersion(), /^\d+\.\d+\.\d+$/);
});
