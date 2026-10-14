import {test} from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {spawn} from 'node:child_process';
import {once} from 'node:events';

const desktop = fileURLToPath(new URL('../../', import.meta.url));

test('文件上传下载限于资源根，拒绝链接逃逸且不在根外创建目录', async () => {
  const work = fs.mkdtempSync(path.join(os.tmpdir(), 'nip-file-storage-'));
  const root = path.join(work, 'files');
  const outside = path.join(work, 'outside');
  fs.mkdirSync(root);
  fs.mkdirSync(outside);
  fs.writeFileSync(path.join(outside, 'private.txt'), 'private configuration');
  fs.symlinkSync(outside, path.join(root, 'linked'), process.platform === 'win32' ? 'junction' : 'dir');
  const child = spawn(process.execPath, ['-e', `
    const express = require('express');
    const context = require('./electron/service/http/core/node_core_ctx');
    context.appPath = process.argv[1];
    const app = express();
    app.use('/api/file', require('./electron/service/http/routers/file')());
    const server = app.listen(0, '127.0.0.1', () => process.send({port: server.address().port}));
  `, root], {cwd: desktop, stdio: ['ignore', 'ignore', 'pipe', 'ipc']});
  const closed = once(child, 'exit');
  let errors = '';
  child.stderr.on('data', value => { errors += value; });
  try {
    const ready = await new Promise((resolve, reject) => {
      const timer = setTimeout(() => reject(new Error(`File route startup timeout: ${errors}`)), 10000);
      child.once('error', error => { clearTimeout(timer); reject(error); });
      child.once('exit', code => { clearTimeout(timer); reject(new Error(`File route exited ${code}: ${errors}`)); });
      child.on('message', message => { if (message.port) { clearTimeout(timer); resolve(message); } });
    });
    const base = `http://127.0.0.1:${ready.port}/api/file`;
    const content = Buffer.from('Resource byte roundtrip\n用户上传正文\n');
    async function upload(directory, header = false) {
      const body = new FormData();
      body.append('file', new Blob([content], {type: 'text/plain'}), 'document.txt');
      return fetch(`${base}/upload${header ? '' : `?currentPath=${encodeURIComponent(directory)}`}`, {
        method: 'POST', body, headers: header ? {currentPath: directory} : {}, signal: AbortSignal.timeout(5000)
      });
    }
    const uploadResponse = await upload('lessons/documents');
    assert.equal(uploadResponse.status, 200);
    const {data: [relative]} = await uploadResponse.json();
    const downloaded = await fetch(`${base}/getFile/${relative.replaceAll('\\', '/')}`);
    assert.equal(downloaded.status, 200);
    assert.deepEqual(Buffer.from(await downloaded.arrayBuffer()), content);
    assert.deepEqual(fs.readFileSync(path.join(root, relative)), content);

    for (const endpoint of ['getFile', 'getReadStream']) {
      const response = await fetch(`${base}/${endpoint}/linked/private.txt`);
      assert.equal(response.status, 404);
      assert(!(await response.text()).includes('private configuration'));
    }
    for (const [directory, header] of [['linked', false], ['linked/new/nested', true]]) {
      const response = await upload(directory, header);
      assert.equal(response.status, 400, `Must refuse outside-root upload through ${directory}`);
      assert.deepEqual((await response.json()).data, []);
      assert.deepEqual(fs.readdirSync(outside), ['private.txt']);
    }
    assert.equal(fs.readFileSync(path.join(outside, 'private.txt'), 'utf8'), 'private configuration');
  } finally {
    child.kill();
    await closed;
    fs.rmSync(work, {recursive: true, force: true});
  }
});
