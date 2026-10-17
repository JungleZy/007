import {test} from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {createRequire} from 'node:module';

const require = createRequire(import.meta.url);
const {stageRustBackend, unsignedBuildEnvironment, buildRustInstaller} = require('../build-personal-rust.cjs');
const {loadBackendProfile} = require('../../electron/service/backend-profile');

function fixture(t) {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'nip-personal-builder-'));
  t.after(() => fs.rmSync(root, {recursive: true, force: true}));
  fs.mkdirSync(path.join(root, 'bin'));
  // Minimal PE headers exercise artifact validation/staging, never executable behavior.
  const pe = Buffer.alloc(512);
  pe.write('MZ'); pe.writeUInt32LE(64, 60); pe.writeUInt32LE(0x4550, 64);
  pe.writeUInt16LE(0x8664, 68); pe.writeUInt16LE(240, 84); pe.writeUInt16LE(2, 86);
  pe.writeUInt16LE(0x20b, 88); pe.writeUInt32LE(16, 196);
  const artifact = path.join(root, 'rust.exe');
  fs.writeFileSync(artifact, pe);
  return {root, artifact, pe};
}

test('staging writes a consumable hash-bound non-secret Rust profile and cleans only its directory', t => {
  const {root, artifact} = fixture(t);
  const sentinel = path.join(root, 'bin/nip.db');
  fs.writeFileSync(sentinel, 'existing customer state');
  const staged = stageRustBackend(artifact, root);
  const server = path.join(staged.directory, 'server.exe');
  const profile = loadBackendProfile(server);
  assert.equal(profile.kind, 'rust');
  fs.appendFileSync(server, 'changed after staging');
  assert.throws(() => loadBackendProfile(server), /校验失败/);
  staged.cleanup();
  assert(!fs.existsSync(staged.directory));
  assert.equal(fs.readFileSync(sentinel, 'utf8'), 'existing customer state');
  assert(fs.existsSync(artifact));
});

test('pre-existing developer backend is never overwritten or removed', t => {
  const {root, artifact} = fixture(t);
  const directory = path.join(root, 'bin/server');
  fs.mkdirSync(directory);
  fs.writeFileSync(path.join(directory, 'server.exe'), 'developer Java backend');
  assert.throws(() => stageRustBackend(artifact, root), {code: 'EEXIST'});
  assert.equal(fs.readFileSync(path.join(directory, 'server.exe'), 'utf8'), 'developer Java backend');
});

test('wrong architecture, DLL, truncated and signed artifacts are refused before staging', t => {
  const {root, artifact, pe} = fixture(t);
  const cases = [pe.subarray(0, 30), Buffer.from('not a Windows executable')];
  const arm = Buffer.from(pe); arm.writeUInt16LE(0xaa64, 68); cases.push(arm);
  const dll = Buffer.from(pe); dll.writeUInt16LE(0x2002, 86); cases.push(dll);
  const signed = Buffer.from(pe); signed.writeUInt32LE(400, 232); signed.writeUInt32LE(80, 236); cases.push(signed);
  for (const candidate of cases) {
    fs.writeFileSync(artifact, candidate);
    assert.throws(() => stageRustBackend(artifact, root));
    assert(!fs.existsSync(path.join(root, 'bin/server')));
  }
});

test('explicit signing credentials, database secret and publication tokens cannot reach personal builder', () => {
  const original = {PATH: '/tools', CSC_LINK: 'secret-certificate', CSC_KEY_PASSWORD: 'secret-password',
    WIN_CSC_LINK: 'windows-certificate', WIN_CSC_KEY_PASSWORD: 'windows-password', csc_identity_auto_discovery: 'true',
    RUST_BACKEND_DATABASE_URL: 'mysql://private:secret@host/db', DB_USER: 'private-user', DB_PASSWORD: 'private-password',
    MYSQL_PWD: 'private-mysql-password', GH_TOKEN: 'publish-token', GITHUB_TOKEN: 'another-token'};
  assert.deepEqual(unsignedBuildEnvironment(original), {PATH: '/tools', CSC_IDENTITY_AUTO_DISCOVERY: 'false'});
  assert.equal(original.CSC_LINK, 'secret-certificate');
});

test('nonempty output is rejected without changing its contents or invoking a build', async t => {
  const {root, artifact} = fixture(t);
  const output = path.join(root, 'output');
  fs.mkdirSync(output);
  fs.writeFileSync(path.join(output, 'keep.txt'), 'personal evidence');
  await assert.rejects(buildRustInstaller({artifact, output}));
  assert.deepEqual(fs.readdirSync(output), ['keep.txt']);
  assert.equal(fs.readFileSync(path.join(output, 'keep.txt'), 'utf8'), 'personal evidence');
});
