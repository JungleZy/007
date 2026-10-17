import {test} from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import net from 'node:net';
import http from 'node:http';
import {spawn} from 'node:child_process';
import {once} from 'node:events';
import {PassThrough} from 'node:stream';
import {createRequire} from 'node:module';

const require = createRequire(import.meta.url);
const {loadBackendProfile, hashBinary, rustEnvironment, startupMessage, forwardRustDiagnostics, waitForRustStartup} = require('../../electron/service/backend-profile');
const {canonicalFlags} = require('../build-personal-rust.cjs');
const {onBackendService} = require('../../electron/service');
const context = require('../../electron/core/node_core_ctx');
const {BACKEND_PORT} = require('../../electron/shared/ports');

function fixture(t) {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'nip-backend-profile-'));
  t.after(() => fs.rmSync(root, {recursive: true, force: true}));
  const directory = path.join(root, 'bin/server');
  fs.mkdirSync(directory, {recursive: true});
  const binary = path.join(directory, process.platform === 'win32' ? 'server.exe' : 'server');
  fs.writeFileSync(binary, 'a binary whose integrity is checked before execution');
  const filename = path.join(directory, 'backend.json');
  const profile = {schemaVersion: 1, kind: 'rust', binarySha256: hashBinary(binary), featureFlags: canonicalFlags()};
  const save = value => fs.writeFileSync(filename, JSON.stringify(value));
  save(profile);
  return {root, binary, filename, profile, save};
}

function environment(t, changes) {
  const previous = {...process.env};
  for (const key of Object.keys(process.env)) if (/^RUST_BACKEND_DATABASE_URL$/i.test(key)) delete process.env[key];
  Object.assign(process.env, changes);
  t.after(() => {
    for (const key of Object.keys(process.env)) if (!(key in previous)) delete process.env[key];
    Object.assign(process.env, previous);
  });
}

function serviceContext(t, root) {
  const previous = {appPath: context.appPath, backendService: context.backendService};
  context.appPath = root;
  context.backendService = null;
  t.after(() => Object.assign(context, previous));
}

test('profile-less packages keep Java selection; corrupt, unknown and altered Rust packages never fall back', t => {
  const f = fixture(t);
  assert.deepEqual(loadBackendProfile(f.binary), f.profile);
  for (const value of [{...f.profile, kind: 'java'}, {...f.profile, schemaVersion: 2},
    {...f.profile, featureFlags: ['PATH']}, {...f.profile, featureFlags: []}, {...f.profile, password: 'private-secret'}]) {
    f.save(value);
    assert.throws(() => loadBackendProfile(f.binary), error => /校验失败/.test(startupMessage(error)) && !startupMessage(error).includes('private-secret'));
  }
  fs.writeFileSync(f.filename, '{private-secret');
  assert.throws(() => loadBackendProfile(f.binary), /校验失败/);
  f.save(f.profile);
  fs.appendFileSync(f.binary, 'tampering');
  assert.throws(() => loadBackendProfile(f.binary), /校验失败/);
  fs.unlinkSync(f.filename);
  assert.throws(() => loadBackendProfile(f.binary, true), /无法读取后端启动配置/);
  assert.equal(loadBackendProfile(f.binary), null);
});

test('package address and flags take precedence without mutating inherited environment or persisting the database secret', t => {
  const {profile} = fixture(t);
  const secret = 'mysql://private-user:private-password@localhost/personal';
  const inherited = {RUST_BACKEND_DATABASE_URL: secret, RUST_BACKEND_ADDR: '0.0.0.0:9', rust_backend_addr: '[::]:9',
    [profile.featureFlags[0]]: 'false', [profile.featureFlags[0].toLowerCase()]: 'false', RUST_PHASE_UNKNOWN_ENABLED: 'true', PATH: '/tools', RUST_LOG: 'info'};
  const env = rustEnvironment(profile, inherited);
  assert.equal(env.RUST_BACKEND_ADDR, `127.0.0.1:${BACKEND_PORT}`);
  assert.equal(env.rust_backend_addr, undefined);
  assert.equal(env.RUST_PHASE_UNKNOWN_ENABLED, undefined);
  assert.equal(env[profile.featureFlags[0].toLowerCase()], undefined);
  for (const flag of profile.featureFlags) assert.equal(env[flag], 'true');
  assert.equal(env.RUST_BACKEND_DATABASE_URL, secret);
  assert.equal(env.PATH, '/tools');
  assert.equal(env.RUST_LOG, 'info');
  assert.equal(inherited[profile.featureFlags[0]], 'false');
  assert(!startupMessage(new Error(secret)).includes(secret));
  assert.throws(() => rustEnvironment(profile, {RUST_BACKEND_DATABASE_URL: '   '}), /RUST_BACKEND_DATABASE_URL/);
  assert.throws(() => rustEnvironment(profile, {RUST_BACKEND_DATABASE_URL: 'postgres://private:private-password@localhost/db'}),
    error => /MySQL/.test(startupMessage(error)) && !startupMessage(error).includes('private-password'));
});

test('missing database rejects the awaited service before creating any backend child', async t => {
  const f = fixture(t);
  serviceContext(t, f.root);
  environment(t, {});
  await assert.rejects(onBackendService(), /RUST_BACKEND_DATABASE_URL/);
  assert.equal(context.backendService, null);
});

test('busy Rust port rejects without touching its owner; no-profile Java preserves existing-service behavior', async t => {
  const f = fixture(t);
  serviceContext(t, f.root);
  environment(t, {RUST_BACKEND_DATABASE_URL: 'mysql://private:secret@localhost/personal'});
  const owner = net.createServer(socket => socket.end('original owner'));
  await new Promise((resolve, reject) => { owner.once('error', reject); owner.listen(BACKEND_PORT, '127.0.0.1', resolve); });
  t.after(() => new Promise(resolve => owner.close(resolve)));
  await assert.rejects(onBackendService(), /已被占用/);
  assert.equal(context.backendService, null);
  assert(owner.listening);
  const socket = net.connect(BACKEND_PORT, '127.0.0.1');
  let received = '';
  socket.on('data', data => { received += data; });
  await once(socket, 'end');
  assert.equal(received, 'original owner');
  fs.unlinkSync(f.filename);
  await onBackendService();
  assert.equal(context.backendService, null);
  assert(owner.listening);
});

test('spawn errors and early exits produce safe actionable startup errors', async t => {
  const missing = spawn(path.join(os.tmpdir(), `absent-backend-${process.pid}-${Date.now()}`), [], {stdio: 'ignore'});
  await assert.rejects(waitForRustStartup(missing), /无法启动 Rust/);
  const failed = spawn(process.execPath, ['-e', 'process.stderr.write("mysql://private:secret@db/personal");process.exit(7)'], {stdio: 'ignore'});
  t.after(() => { if (failed.exitCode === null) failed.kill(); });
  await assert.rejects(waitForRustStartup(failed), error => /启动期间退出/.test(startupMessage(error)) && !startupMessage(error).includes('secret'));
});

test('process-only readiness times out, terminates only the owned child, and preserves the other HTTP listener', async t => {
  const owner = http.createServer((request, response) => {
    response.writeHead(200, {'content-type': 'application/json'});
    response.end(JSON.stringify({status: 'ready', readiness: 'process-only', dependencies: false}));
  });
  await new Promise(resolve => owner.listen(0, '127.0.0.1', resolve));
  t.after(() => new Promise(resolve => owner.close(resolve)));
  const child = spawn(process.execPath, ['-e', 'setInterval(()=>{},1000)'], {stdio: 'ignore'});
  const exited = once(child, 'exit');
  await assert.rejects(waitForRustStartup(child, owner.address().port, 150), /启动超时/);
  await exited;
  assert(owner.listening);
  assert(child.exitCode !== null || child.signalCode !== null);
});

test('backend diagnostics preserve actionable failures while redacting secrets split across stream chunks', async () => {
  const stream = new PassThrough();
  const lines = [];
  forwardRustDiagnostics(stream, 'mysql://private-user:private-password@localhost/personal', line => lines.push(line));
  const ended = once(stream, 'end');
  stream.write('WARN Access denied for user private-user; password=private-');
  stream.write('password; url=mysql://private-user:private-password@localhost/personal\n');
  stream.end('ERROR pool unavailable\n');
  await ended;
  const output = lines.join('\n');
  assert.match(output, /WARN Access denied/);
  assert.match(output, /ERROR pool unavailable/);
  for (const secret of ['private-user', 'private-password', 'mysql://']) assert(!output.includes(secret));
});
