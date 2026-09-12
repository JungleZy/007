// 发布校验契约测试（T5-3 / DELIVERY-04、05）。
//
// 仓内原先没有针对 `artifact-manifest.cjs` 的测试手法：前端三个 test/*.test.mjs 与
// questionImport 都不涉及 git，而 `verifyRelease` 的每一条断言都依赖「仓库 HEAD +
// 干净工作树 + 三个版本号文件」。因此这里在临时目录里造一个**完整的最小仓库**
// （与真实布局同构：bw-frontend/scripts、bw-frontend/package.json、
// bw-frontend/frontend/package.json、backend/target），把被测脚本原样拷进去后
// `require`，让脚本自己算出的 repoRoot 指向这个临时仓库。
//
// 产物一律由被测脚本自己的命令生成（native / frontend / package-desktop），
// 不手写 manifest —— 手写的 manifest 只能证明测试自己自洽。
import { test, after } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';
import { createRequire } from 'node:module';
import { execFileSync } from 'node:child_process';

const here = path.dirname(fileURLToPath(import.meta.url));
const scriptSource = path.join(here, '..', 'artifact-manifest.cjs');
const require_ = createRequire(import.meta.url);
const roots = [];

after(() => {
  for (const root of roots) fs.rmSync(root, { recursive: true, force: true });
});

// 本机可能没有 zip 二进制（GitHub 的 ubuntu runner 自带）。用 python3 的 zipfile
// 提供等价替身并前置进 PATH，这样被测脚本的 execFileSync('zip', ...) 代码路径不变。
const zipShim = `import os, sys, zipfile

arguments = [item for item in sys.argv[1:] if not item.startswith('-')]
archive, roots = arguments[0], arguments[1:] or ['.']
with zipfile.ZipFile(archive, 'w', zipfile.ZIP_DEFLATED) as bundle:
    for root in roots:
        for base, directories, files in os.walk(root):
            directories.sort()
            for name in sorted(files):
                full = os.path.join(base, name)
                bundle.write(full, os.path.relpath(full, root))
`;

function ensureZip(root) {
  try {
    execFileSync('zip', ['-h'], { stdio: 'ignore' });
    return;
  } catch {
    // 落到替身。
  }
  const binary = path.join(root, 'shim-bin');
  fs.mkdirSync(binary, { recursive: true });
  fs.writeFileSync(path.join(binary, 'zip.py'), zipShim);
  fs.writeFileSync(path.join(binary, 'zip'), '#!/bin/sh\nexec python3 "$(dirname "$0")/zip.py" "$@"\n', { mode: 0o755 });
  process.env.PATH = `${binary}${path.delimiter}${process.env.PATH}`;
}

function writeFile(filename, content) {
  fs.mkdirSync(path.dirname(filename), { recursive: true });
  fs.writeFileSync(filename, content);
}

// 版本号三处独立可调，正是本任务要守的那条契约。
function createFixture({ frontend = '1.1.0', desktop = '1.1.0', backend = '1.1.0' } = {}) {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'release-fixture-'));
  roots.push(root);
  ensureZip(root);
  const repo = path.join(root, 'repo');
  const artifacts = path.join(root, 'artifacts');
  const script = path.join(repo, 'bw-frontend/scripts/artifact-manifest.cjs');

  // 与真实仓库同构的忽略规则：native 二进制、electron 产物、前端 dist 都不入库，
  // 所以把它们放进工作树也不会让 gitMetadata() 判为 dirty。
  writeFile(path.join(repo, '.gitignore'), [
    'backend/target/',
    'bw-frontend/out/',
    'bw-frontend/bin/server/',
    'bw-frontend/frontend/dist/',
    '',
  ].join('\n'));
  writeFile(path.join(repo, 'bw-frontend/package.json'), `${JSON.stringify({ name: 'desktop', version: desktop }, null, 2)}\n`);
  writeFile(path.join(repo, 'bw-frontend/frontend/package.json'), `${JSON.stringify({ name: 'web', version: frontend }, null, 2)}\n`);
  writeFile(path.join(repo, 'bw-frontend/bin/config.json'), '{}\n');
  writeFile(path.join(repo, 'bw-frontend/bin/nip.db'), '{"_id":1}\n');
  fs.mkdirSync(path.dirname(script), { recursive: true });
  fs.copyFileSync(scriptSource, script);

  const git = (...args) => execFileSync('git', ['-c', 'user.email=fixture@example.com', '-c', 'user.name=Fixture', ...args],
    { cwd: repo, encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }).trim();
  git('init', '-q', '-b', 'main');
  git('add', '-A');
  git('commit', '-q', '-m', 'fixture');
  const sourceCommit = git('rev-parse', '--verify', 'HEAD');

  const cli = (...args) => execFileSync(process.execPath, [script, ...args], { cwd: repo, stdio: ['ignore', 'ignore', 'pipe'] });

  // 后端 native 产物：packageNative 从 target/ 里挑唯一的 *-runner / *-runner.exe。
  const runner = crypto.randomBytes(4096);
  writeFile(path.join(repo, 'backend/target/build-version.txt'), backend);
  writeFile(path.join(repo, 'backend/target/nip-runner'), runner);
  writeFile(path.join(repo, 'backend/target/nip-runner.exe'), runner);
  for (const target of ['linux-amd64', 'linux-arm64', 'windows-amd64']) {
    cli('native', target);
    fs.cpSync(path.join(repo, 'backend/target', `artifact-${target}`), path.join(artifacts, target), { recursive: true });
  }

  // Web 产物：dist 必须含 requiredWebFiles，manifest 与 zip 都由脚本自己产出。
  const dist = path.join(repo, 'bw-frontend/frontend/dist');
  writeFile(path.join(dist, 'index.html'), '<!doctype html><title>fixture</title>\n');
  writeFile(path.join(dist, 'processor.js'), 'registerProcessor();\n');
  writeFile(path.join(dist, 'runtime-config.js'), 'window.runtimeConfig = {};\n');
  writeFile(path.join(dist, 'assets/app.js'), 'export default 1;\n');
  cli('frontend');
  cli('package-web', path.join(artifacts, 'frontend'));

  // 桌面 --dir 产物：bin/ 由 extraFiles 带入（含 CI staging 进去的 server/server），
  // resources/app.asar 是 asar:true 的主体。
  const unpacked = path.join(repo, 'bw-frontend/out/linux-unpacked');
  writeFile(path.join(unpacked, 'bin/config.json'), '{}\n');
  writeFile(path.join(unpacked, 'bin/nip.db'), '{"_id":1}\n');
  writeFile(path.join(unpacked, 'bin/server/server'), runner);
  writeFile(path.join(unpacked, 'resources/app.asar'), crypto.randomBytes(2048));
  writeFile(path.join(unpacked, 'nip'), crypto.randomBytes(1024));
  cli('package-desktop', path.join(artifacts, 'linux-amd64'), path.join(artifacts, 'desktop-linux'));

  const { verifyRelease, packageDesktop } = require_(script);
  let outputs = 0;
  return {
    root,
    repo,
    artifacts,
    unpacked,
    sourceCommit,
    packageDesktop,
    release(releaseRef, artifactsDirectory = artifacts) {
      const output = path.join(root, `release-assets-${++outputs}`);
      verifyRelease(artifactsDirectory, output, sourceCommit, releaseRef);
      return fs.readdirSync(output).sort();
    },
  };
}

test('完整且版本一致的产物集通过校验，桌面包进入发布资产', () => {
  const fixture = createFixture();
  const assets = fixture.release('refs/tags/v1.1.0');
  assert.deepEqual(assets, [
    'backend-linux-amd64-manifest.json',
    'backend-linux-arm64-manifest.json',
    'backend-windows-amd64-manifest.json',
    'desktop-linux-1.1.0-' + fixture.sourceCommit + '.zip',
    'desktop-linux-manifest.json',
    'frontend-1.1.0-' + fixture.sourceCommit + '.zip',
    'frontend-build-manifest.json',
    'quarkus-1.1.0-linux-amd64',
    'quarkus-1.1.0-linux-arm64',
    'quarkus-1.1.0-windows-amd64.exe',
  ].sort());
});

// 报错文案本身就是契约：发版当天必须能从一条信息里看出三个文件各自的当前值与期望值，
// 否则「隐性前置」会拖到发版失败时才被发现。
function releaseFailure(fixture, releaseRef) {
  try {
    fixture.release(releaseRef);
  } catch (error) {
    return error.message;
  }
  throw new Error(`expected ${releaseRef} to be rejected`);
}

test('tag 与三个版本号不一致时拒发，且报错点名三个文件的当前值与期望值', () => {
  const aligned = createFixture();
  const allDrift = releaseFailure(aligned, 'refs/tags/v9.9.9');
  assert.match(allDrift, /Release version mismatch for refs\/tags\/v9\.9\.9: 3 of 3 version files disagree/);
  assert.match(allDrift, /MISMATCH backend\/pom\.xml declares 1\.1\.0, expected 9\.9\.9/);
  assert.match(allDrift, /MISMATCH bw-frontend\/frontend\/package\.json declares 1\.1\.0, expected 9\.9\.9/);
  assert.match(allDrift, /MISMATCH bw-frontend\/package\.json declares 1\.1\.0, expected 9\.9\.9/);
  assert.match(allDrift, /Fix: set every MISMATCH file above to 9\.9\.9 in one commit, then re-tag\./);

  // 「v1.1.0 的 tag 挂 3.1.0 的桌面包」——只断言后端或前端版本时漏掉的正是这一条。
  // 这也是当前仓库的真实状态：另两处 1.1.0，bw-frontend/package.json 是 3.1.0。
  const desktopDrift = releaseFailure(createFixture({ desktop: '3.1.0' }), 'refs/tags/v1.1.0');
  assert.match(desktopDrift, /1 of 3 version files disagree/);
  assert.match(desktopDrift, /MISMATCH bw-frontend\/package\.json declares 3\.1\.0, expected 1\.1\.0/);
  assert.match(desktopDrift, /OK\s+backend\/pom\.xml declares 1\.1\.0, expected 1\.1\.0/);
  assert.match(desktopDrift, /OK\s+bw-frontend\/frontend\/package\.json declares 1\.1\.0, expected 1\.1\.0/);

  const backendDrift = releaseFailure(createFixture({ backend: '1.2.0' }), 'refs/tags/v1.1.0');
  assert.match(backendDrift, /1 of 3 version files disagree/);
  assert.match(backendDrift, /MISMATCH backend\/pom\.xml declares 1\.2\.0, expected 1\.1\.0/);
});

test('非 refs/tags/v 前缀的 ref 拒发', () => {
  const fixture = createFixture();
  assert.throws(() => fixture.release('refs/heads/main'), /Release ref must be refs\/tags\/v<version>/);
  assert.throws(() => fixture.release('refs/tags/1.1.0'), /Release ref must be refs\/tags\/v<version>/);
});

test('产物目录集合精确匹配：缺少 desktop-linux 拒发', () => {
  const fixture = createFixture();
  const partial = path.join(fixture.root, 'artifacts-without-desktop');
  fs.cpSync(fixture.artifacts, partial, { recursive: true });
  fs.rmSync(path.join(partial, 'desktop-linux'), { recursive: true, force: true });
  assert.throws(() => fixture.release('refs/tags/v1.1.0', partial), /Release artifact set mismatch/);
});

test('桌面包里的后端二进制必须就是校验过的 native 产物', () => {
  const fixture = createFixture();
  fs.writeFileSync(path.join(fixture.unpacked, 'bin/server/server'), crypto.randomBytes(4096));
  assert.throws(() => fixture.packageDesktop(path.join(fixture.artifacts, 'linux-amd64'), path.join(fixture.root, 'desktop-retry')),
    /Desktop package embeds a backend binary other than the verified native artifact/);
});
