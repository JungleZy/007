const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const net = require('node:net');
const {fork} = require('node:child_process');

test('文件服务子进程实际监听传入的配置端口', async t => {
    const root = fs.mkdtempSync(path.join(os.tmpdir(), 'nip-file-port-'));
    const socket = net.createServer();
    await new Promise(resolve => socket.listen(0, '127.0.0.1', resolve));
    const port = socket.address().port;
    await new Promise(resolve => socket.close(resolve));
    fs.writeFileSync(path.join(root, 'marker.txt'), 'port-test');
    const child = fork(path.resolve(__dirname, '../service/http/index.js'), [root, root, String(port)], {stdio: ['ignore', 'ignore', 'pipe', 'ipc']});
    t.after(async () => {
        if (child.exitCode === null) {
            const stopped = new Promise(resolve => child.once('close', resolve));
            child.kill(); await stopped;
        }
        fs.rmSync(root, {recursive: true, force: true});
    });
    await new Promise((resolve, reject) => {
        const timer = setTimeout(() => reject(new Error('File service startup timeout')), 10000);
        child.on('message', message => {if (message?.type === 'ready') {clearTimeout(timer);assert.equal(message.port, port);resolve();}});
        child.once('error', error => {clearTimeout(timer); reject(error);});
        child.once('exit', code => {clearTimeout(timer); reject(new Error('File service exited '+code));});
    });
    const result = await fetch(`http://127.0.0.1:${port}/api/file/getFile/marker.txt`);
    assert.equal(result.status, 200);
    assert.equal(await result.text(), 'port-test');
});
