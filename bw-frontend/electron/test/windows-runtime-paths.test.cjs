const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const {windowsRuntimePaths} = require('../service/windows-runtime-paths');

test('Windows 中文路径映射保持真实数据，旧实例重复清理不影响新实例', {skip: process.platform !== 'win32'}, t => {
    const root = fs.mkdtempSync(path.join(os.tmpdir(), 'nip-paths-'));
    const app = path.join(root, '中文 软件');
    const data = path.join(root, '中文 数据');
    fs.mkdirSync(app);
    fs.mkdirSync(data);
    let first, second;
    t.after(() => {
        first?.dispose();
        second?.dispose();
        fs.rmSync(root, {recursive: true, force: true});
    });
    first = windowsRuntimePaths(app, data);
    assert.match(first.appPath + first.dataPath, /^[\x20-\x7e]+$/);
    fs.writeFileSync(path.join(first.dataPath, 'record.txt'), 'keep');
    first.dispose();
    assert.equal(fs.readFileSync(path.join(data, 'record.txt'), 'utf8'), 'keep');
    second = windowsRuntimePaths(app, data);
    first.dispose();
    assert.equal(fs.readFileSync(path.join(second.dataPath, 'record.txt'), 'utf8'), 'keep');
});
