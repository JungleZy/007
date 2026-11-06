const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const express = require('express');

test('用户文件优先于随包资源，两个根目录均不能越界读取', async t => {
    const root = fs.mkdtempSync(path.join(os.tmpdir(), 'nip-file-roots-'));
    const mutable = path.join(root, 'user-files');
    const bundled = path.join(root, 'bundled-files');
    fs.mkdirSync(mutable);
    fs.mkdirSync(bundled);
    fs.writeFileSync(path.join(bundled, 'sample.txt'), 'bundled');
    fs.writeFileSync(path.join(root, 'private.txt'), 'private');
    const context = require('../service/http/core/node_core_ctx');
    context.appPath = mutable;
    context.bundledPath = bundled;
    const controller = require('../service/http/controllers/file');
    const app = express();
    app.get('/files/*', controller.getFile);
    const server = app.listen(0, '127.0.0.1');
    await new Promise(resolve => server.once('listening', resolve));
    t.after(async () => {
        await new Promise(resolve => server.close(resolve));
        fs.rmSync(root, {recursive: true, force: true});
    });
    const base = `http://127.0.0.1:${server.address().port}/files/`;
    assert.equal(await (await fetch(base + 'sample.txt')).text(), 'bundled');
    fs.writeFileSync(path.join(mutable, 'sample.txt'), 'user');
    assert.equal(await (await fetch(base + 'sample.txt')).text(), 'user');
    assert.equal((await fetch(base + '%2e%2e%2fprivate.txt')).status, 404);
    fs.symlinkSync(root, path.join(bundled, 'escape'), process.platform === 'win32' ? 'junction' : 'dir');
    assert.equal((await fetch(base + 'escape/private.txt')).status, 404);
});
