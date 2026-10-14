import {test} from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {createRequire} from 'node:module';

const require = createRequire(import.meta.url);
const {prepareWindowsRuntime} = require('../../electron/core/runtime-storage');
const Datastore = require('nedb-promises');

function fixture(t) {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'nip-runtime-migration-'));
  t.after(() => fs.rmSync(root, {recursive: true, force: true}));
  const install = path.join(root, '旧 安装');
  const userData = path.join(root, '用户 profile');
  fs.mkdirSync(path.join(install, 'bin/file/课程'), {recursive: true});
  fs.mkdirSync(userData);
  return {root, install, userData};
}

test('迁移保留网络和串口设置、上传文件与原授权，旧默认值不能覆盖后续用户修改', async t => {
  const {install, userData} = fixture(t);
  const config = {_id: 2, text: {dataUrl: {url: '10.0.0.7', port: '18001'}, fileUrl: {url: '127.0.0.1', port: '8000'}}};
  const serial = {_id: 3, text: {port: 'COM7'}};
  const original = [config, serial].map(row => JSON.stringify(row)).join('\n') + '\n';
  fs.writeFileSync(path.join(install, 'bin/nip.db'), original);
  fs.writeFileSync(path.join(install, 'bin/config.json'), '{"legacy":true}');
  fs.writeFileSync(path.join(install, 'bin/file/课程/内容.txt'), '学习记录与上传正文');
  fs.writeFileSync(path.join(userData, 'license.json'), 'existing activation copy');
  const runtime = prepareWindowsRuntime(install, userData);
  const db = Datastore.create(path.join(runtime, 'bin/nip.db'));
  assert.deepEqual(await db.findOne({_id: 2}), config);
  assert.deepEqual(await db.findOne({_id: 3}), serial);
  assert.equal(fs.readFileSync(path.join(runtime, 'bin/file/课程/内容.txt'), 'utf8'), '学习记录与上传正文');
  assert.equal(fs.readFileSync(path.join(install, 'bin/nip.db'), 'utf8'), original);

  await db.update({_id: 2}, {$set: {'text.dataUrl.url': '10.0.0.99'}});
  fs.writeFileSync(path.join(runtime, 'bin/file/课程/内容.txt'), 'updated after upgrade');
  fs.writeFileSync(path.join(install, 'bin/nip.db'), '{"_id":2,"text":{}}\n');
  fs.writeFileSync(path.join(install, 'bin/config.json'), '{"legacy":false}');
  prepareWindowsRuntime(install, userData);
  const reopened = Datastore.create(path.join(runtime, 'bin/nip.db'));
  assert.equal((await reopened.findOne({_id: 2})).text.dataUrl.url, '10.0.0.99');
  assert.equal(fs.readFileSync(path.join(runtime, 'bin/file/课程/内容.txt'), 'utf8'), 'updated after upgrade');
  assert.equal(fs.readFileSync(path.join(runtime, 'bin/config.json'), 'utf8'), '{"legacy":true}');
  assert.equal(fs.readFileSync(path.join(userData, 'license.json'), 'utf8'), 'existing activation copy');
});

test('保留 NeDB 崩溃恢复 sidecar，不把未完成写入重置为默认配置', async t => {
  const {install, userData} = fixture(t);
  const saved = {_id: 2, text: {dataUrl: {url: '10.1.2.3', port: '18001'}}};
  fs.writeFileSync(path.join(install, 'bin/nip.db~'), JSON.stringify(saved) + '\n');
  const runtime = prepareWindowsRuntime(install, userData);
  const db = Datastore.create(path.join(runtime, 'bin/nip.db'));
  assert.deepEqual(await db.findOne({_id: 2}), saved);
});

test('遇到链接中止整批迁移，源数据保留且不发布半份 runtime', t => {
  const {root, install, userData} = fixture(t);
  fs.writeFileSync(path.join(install, 'bin/nip.db'), '{"_id":2,"text":{"saved":true}}\n');
  const outside = path.join(root, 'outside');
  fs.mkdirSync(outside);
  fs.writeFileSync(path.join(outside, 'private.txt'), 'outside data');
  fs.symlinkSync(outside, path.join(install, 'bin/file/link'), process.platform === 'win32' ? 'junction' : 'dir');
  assert.throws(() => prepareWindowsRuntime(install, userData), /链接或特殊文件/);
  assert.equal(fs.existsSync(path.join(userData, 'runtime')), false);
  assert.equal(JSON.parse(fs.readFileSync(path.join(install, 'bin/nip.db'), 'utf8')).text.saved, true);
  assert.deepEqual(fs.readdirSync(outside), ['private.txt']);
});
