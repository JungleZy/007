const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const asar = require('asar');
const {assertDependencyLayout, verifyDependencyClosure} = require('../../scripts/verify-packaged-runtime.cjs');

function temporary(t) {
    const root = fs.mkdtempSync(path.join(os.tmpdir(), 'nip-dependency-layout-'));
    t.after(() => fs.rmSync(root, {recursive: true, force: true}));
    return root;
}

async function archiveFixture(t, packages) {
    const root = temporary(t);
    const source = path.join(root, 'source');
    for (const [name, metadata] of Object.entries(packages)) {
        const file = path.join(source, name, 'package.json');
        fs.mkdirSync(path.dirname(file), {recursive: true});
        fs.writeFileSync(file, JSON.stringify(metadata));
    }
    const archive = path.join(root, 'app.asar');
    await asar.createPackage(source, archive);
    return archive;
}

test('打包前拒绝重复根依赖树，允许正常包内嵌套依赖', t => {
    const project = temporary(t);
    fs.mkdirSync(path.join(project, 'node_modules/outer/node_modules/inner'), {recursive: true});
    assert.doesNotThrow(() => assertDependencyLayout(project));
    fs.mkdirSync(path.join(project, 'node_modules/node_modules'));
    assert.throws(() => assertDependencyLayout(project), /node_modules\/node_modules/);
});

test('包内 scoped 间接依赖必须位于可解析的 node_modules 层级', async t => {
    const packages = {
        '': {name: 'application', dependencies: {outer: '1'}},
        'node_modules/outer': {name: 'outer', dependencies: {'@vendor/inner': '1'}}
    };
    const good = await archiveFixture(t, {...packages, 'node_modules/@vendor/inner': {name: '@vendor/inner'}});
    assert.equal(verifyDependencyClosure(good), 3);
    const misplaced = await archiveFixture(t, {...packages, 'node_modules/node_modules/@vendor/inner': {name: '@vendor/inner'}});
    assert.throws(() => verifyDependencyClosure(misplaced), /invalid node_modules\/node_modules/);
    const missing = await archiveFixture(t, packages);
    assert.throws(() => verifyDependencyClosure(missing), /Missing packaged dependency @vendor\/inner/);
});

test('依赖闭包支持包内多版本及缺失的可选平台依赖', async t => {
    const archive = await archiveFixture(t, {
        '': {dependencies: {outer: '1', shared: '2'}},
        'node_modules/outer': {dependencies: {shared: '1'}, optionalDependencies: {platformAddon: '1'}},
        'node_modules/shared': {version: '2'},
        'node_modules/outer/node_modules/shared': {version: '1'}
    });
    assert.equal(verifyDependencyClosure(archive), 4);
});
