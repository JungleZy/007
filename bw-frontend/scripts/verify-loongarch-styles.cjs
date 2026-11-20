const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const asar = require('asar');
const {execFileSync} = require('node:child_process');
const {verifyDependencyClosure} = require('./verify-packaged-runtime.cjs');

const project = path.resolve(__dirname, '..');
for (const style of ['HJ', 'HJJ', 'LJ']) {
    const archive = path.join(project, 'out/kylin-v10-loongarch64', style, 'linux-loong64-unpacked/resources/app.asar');
    const read = file => asar.extractFile(archive, file.split('/').join(path.sep)).toString('utf8');
    assert.equal(JSON.parse(read('public/dist/build-manifest.json')).interfaceStyle, style);
    const assignments = asar.listPackage(archive).map(name => name.replace(/\\/g, '/').replace(/^\//, ''))
        .filter(name => name.startsWith('public/dist/static/js/') && name.endsWith('.js'))
        .flatMap(name => [...read(name).matchAll(/window\.interfaceStyle\s*=\s*["'](HJ|HJJ|LJ)["']/g)].map(match => match[1]));
    assert.deepEqual(assignments, [style], 'Packaged runtime must select the named interface style');
    const dependencies = verifyDependencyClosure(archive);
    if (process.platform === 'win32') {
        const output = execFileSync(path.join(project, 'node_modules/electron/dist/electron.exe'),
            [path.join(__dirname, 'packaged-runtime-probe.cjs'), archive], {
                env: {...process.env, ELECTRON_RUN_AS_NODE: '1', NODE_PATH: '', NODE_OPTIONS: ''}, encoding: 'utf8', timeout: 30000
            });
        assert.ok(output.includes('PACKAGED_RUNTIME_OK'));
    }
    console.log(`${style}: compiled selection, ${dependencies} dependency manifests and ASAR probe verified`);
}
