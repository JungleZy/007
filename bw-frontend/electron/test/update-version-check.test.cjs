const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const http = require('node:http');
const path = require('node:path');
const crypto = require('node:crypto');
const {fetchServerInfo, evaluate, downloadPackage} = require('../update/version-check');
const {workspace} = require('./update-fixture.cjs');

const local = {version: '3.2.0', target: 'linux-x64', style: 'HJ'};
const pack = (extra = {}) => ({target: 'linux-x64', style: 'HJ', version: '3.3.0', size: 4, sha256: 'x', fileName: 'p', ...extra});

async function server(t, handler) {
    const instance = http.createServer(handler);
    await new Promise(resolve => instance.listen(0, '127.0.0.1', resolve));
    t.after(() => new Promise(resolve => { instance.closeAllConnections?.(); instance.close(resolve); }));
    return {url: '127.0.0.1', port: instance.address().port};
}

test('版本一致放行，其余不一致情况一律阻断', () => {
    const ok = (version, packages = []) => ({status: 'ok', version, packages});
    assert.deepEqual(evaluate(local, ok('3.2.0')), {state: 'same', blocked: false, localVersion: '3.2.0', serverVersion: '3.2.0'});
    assert.equal(evaluate(local, ok('3.1.9')).state, 'server-older');
    assert.equal(evaluate(local, ok('3.1.9')).blocked, true);
    const newer = evaluate(local, ok('3.3.0', [pack()]));
    assert.equal(newer.state, 'server-newer');
    assert.equal(newer.blocked, true);
    assert.equal(newer.package.version, '3.3.0');
    // 其他平台、其他风格、或与服务端版本不一致的包都不能用来对齐版本。
    for (const packages of [[], [pack({target: 'windows-ia32'})], [pack({style: 'LJ'})], [pack({version: '3.2.5'})]]) {
        const result = evaluate(local, ok('3.3.0', packages));
        assert.equal(result.state, 'server-newer-no-package');
        assert.equal(result.blocked, true);
    }
    assert.deepEqual(evaluate(local, {status: 'legacy'}), {state: 'server-legacy', blocked: true, localVersion: '3.2.0', serverVersion: null});
    assert.equal(evaluate(local, {status: 'unreachable'}).blocked, false);
});

test('服务端应答分为可用、无版本端点的旧版本、不可达三类', async t => {
    const good = await server(t, (request, response) => {
        assert.equal(request.url, '/api/update/info');
        response.setHeader('Content-Type', 'application/json');
        response.end(JSON.stringify({code: 200, data: {version: '3.3.0', packages: [pack()]}}));
    });
    assert.deepEqual(await fetchServerInfo(good), {status: 'ok', version: '3.3.0', packages: [pack()]});
    const legacy = await server(t, (request, response) => { response.statusCode = 404; response.end('Not Found'); });
    assert.deepEqual(await fetchServerInfo(legacy), {status: 'legacy'});
    const rejected = await server(t, (request, response) => response.end(JSON.stringify({code: 203, message: 'token不能为空'})));
    assert.deepEqual(await fetchServerInfo(rejected), {status: 'legacy'});
    const failing = await server(t, (request, response) => { response.statusCode = 500; response.end('{}'); });
    assert.deepEqual(await fetchServerInfo(failing), {status: 'unreachable'});
    const silent = await server(t, () => {});
    assert.deepEqual(await fetchServerInfo(silent, {timeout: 200}), {status: 'unreachable'});
    assert.deepEqual(await fetchServerInfo({url: '127.0.0.1', port: 1}, {timeout: 500}), {status: 'unreachable'});
});

test('下载按 target 与风格取包，摘要不符、大小不符或中断时不留文件', async t => {
    const root = workspace(t), body = crypto.randomBytes(300000);
    const expected = {size: body.length, sha256: crypto.createHash('sha256').update(body).digest('hex')};
    let mode = 'ok';
    const address = await server(t, (request, response) => {
        const url = new URL(request.url, 'http://x');
        assert.equal(url.pathname, '/api/update/package');
        assert.equal(url.searchParams.get('target'), 'linux-x64');
        assert.equal(url.searchParams.get('style'), 'HJ');
        if (mode === 'missing') { response.setHeader('Content-Type', 'application/json'); response.end('{"code":202}'); return; }
        response.setHeader('Content-Type', 'application/octet-stream');
        if (mode === 'cut') { response.write(body.subarray(0, 1000)); setTimeout(() => response.destroy(), 20); return; }
        response.end(mode === 'long' ? Buffer.concat([body, body]) : body);
    });
    const directory = path.join(root, 'incoming');
    const file = await downloadPackage(address, local, expected, directory);
    assert.ok(fs.readFileSync(file).equals(body));
    fs.rmSync(file);
    await assert.rejects(downloadPackage(address, local, {...expected, sha256: '0'.repeat(64)}, directory), {code: 'download'});
    for (mode of ['long', 'cut', 'missing']) await assert.rejects(downloadPackage(address, local, expected, directory), {code: 'download'}, mode);
    assert.deepEqual(fs.readdirSync(directory), []);
});
