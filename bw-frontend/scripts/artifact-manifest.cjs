'use strict';

const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const crypto = require('node:crypto');
const { execFileSync } = require('node:child_process');

const repoRoot = path.resolve(__dirname, '../..');
const frontendRoot = path.join(repoRoot, 'bw-frontend/frontend');
const frontendManifest = 'build-manifest.json';
const requiredWebFiles = ['index.html', 'processor.js', 'runtime-config.js'];
// 桌面包只出 `electron-builder --linux --dir` 的未打包目录（deb/nsis 需要 fpm、
// 中文 productName 的资产名规范化与 /opt 权限处理，不在本轮范围）。
const desktopRoot = path.join(repoRoot, 'bw-frontend');
const desktopUnpacked = path.join(desktopRoot, 'out/linux-unpacked');
const desktopManifest = 'desktop-linux-manifest.json';
// 桌面包的可运行性依赖这四个文件：后端二进制（service/index.js 按
// `<appPath>/bin/server/server` 启动）、asar 主体，以及 extraFiles 带入的
// bin/config.json（缺失会让 loadConfig 走 catch，全部 ipc 处理器失效）与 bin/nip.db。
const requiredDesktopFiles = ['bin/config.json', 'bin/nip.db', 'bin/server/server', 'resources/app.asar'];
const nativeTargets = {
  'linux-amd64': { platform: 'linux', architecture: 'x86_64', extension: '' },
  'linux-arm64': { platform: 'linux', architecture: 'arm64', extension: '' },
  'windows-amd64': { platform: 'windows', architecture: 'x86_64', extension: '.exe' },
};

function assert(condition, message) {
  if (!condition) throw new Error(message);
}

function gitMetadata() {
  const git = (...args) => execFileSync('git', args, { cwd: repoRoot, encoding: 'utf8' }).trim();
  const sourceCommit = git('rev-parse', '--verify', 'HEAD');
  assert(/^[0-9a-f]{40}$/.test(sourceCommit), 'Git HEAD must be a full SHA-1 commit');
  return { sourceCommit, sourceDirty: git('status', '--porcelain', '--untracked-files=all') !== '' };
}

function versionString(value) {
  assert(typeof value === 'string' && /^[0-9][0-9A-Za-z.+-]*$/.test(value), 'Missing or invalid artifact version');
  return value;
}

function frontendVersion() {
  return versionString(JSON.parse(fs.readFileSync(path.join(frontendRoot, 'package.json'), 'utf8')).version);
}

function desktopVersion() {
  return versionString(JSON.parse(fs.readFileSync(path.join(desktopRoot, 'package.json'), 'utf8')).version);
}

function hashFile(filename, buffer) {
  const fd = fs.openSync(filename, 'r');
  const hash = crypto.createHash('sha256');
  try {
    let bytes;
    while ((bytes = fs.readSync(fd, buffer, 0, buffer.length, null)) !== 0) hash.update(buffer.subarray(0, bytes));
  } finally {
    fs.closeSync(fd);
  }
  return hash.digest('hex');
}

function inventory(directory, manifestName) {
  assert(fs.lstatSync(directory).isDirectory(), 'Artifact root must be a directory');
  const files = [];
  const buffer = Buffer.allocUnsafe(1024 * 1024);
  function visit(relative) {
    for (const entry of fs.readdirSync(path.join(directory, relative), { withFileTypes: true })) {
      const file = relative ? `${relative}/${entry.name}` : entry.name;
      assert(!file.includes('\\'), `Non-POSIX artifact path: ${file}`);
      if (file === manifestName) {
        assert(entry.isFile(), 'Manifest must be a regular file');
        continue;
      }
      if (entry.isDirectory()) visit(file);
      else {
        assert(entry.isFile(), `Artifact must contain only regular files: ${file}`);
        const filename = path.join(directory, file);
        files.push({ path: file, size: fs.statSync(filename).size, sha256: hashFile(filename, buffer) });
      }
    }
  }
  visit('');
  files.sort((a, b) => a.path < b.path ? -1 : a.path > b.path ? 1 : 0);
  assert(files.length > 0, 'Artifact contains no files');
  return files;
}

function writeManifest(directory, component, version, extra = {}, manifestName = frontendManifest) {
  const files = inventory(directory, manifestName);
  if (component === 'frontend') {
    for (const file of requiredWebFiles) assert(files.some(entry => entry.path === file && entry.size > 0), `Missing Web artifact: ${file}`);
  }
  const manifest = { schemaVersion: 1, component, version: versionString(version), ...gitMetadata(), ...extra, files };
  fs.writeFileSync(path.join(directory, manifestName), `${JSON.stringify(manifest, null, 2)}\n`);
  return manifest;
}

function verifyManifest(directory, options = {}) {
  const { allowDirty = false, component = 'frontend', manifestName = frontendManifest, target } = options;
  const source = options.expectedCommit === undefined ? gitMetadata() : { sourceCommit: options.expectedCommit };
  assert(/^[0-9a-f]{40}$/.test(source.sourceCommit), 'Expected source commit must be a full Git SHA');
  const manifestPath = path.join(directory, manifestName);
  assert(fs.lstatSync(manifestPath).isFile(), 'Manifest must be a regular file');
  const manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf8'));
  assert(manifest.schemaVersion === 1 && manifest.component === component, 'Manifest schema/component mismatch');
  versionString(manifest.version);
  if (component === 'frontend') assert(manifest.version === frontendVersion(), 'Frontend version mismatch');
  if (component === 'desktop') assert(manifest.version === desktopVersion(), 'Desktop version mismatch');
  assert(manifest.sourceCommit === source.sourceCommit, 'Artifact source commit mismatch');
  assert(typeof manifest.sourceDirty === 'boolean', 'Manifest sourceDirty must be boolean');
  assert(allowDirty || manifest.sourceDirty === false, 'Dirty-source artifact is not releasable');
  if (source.sourceDirty !== undefined) assert(manifest.sourceDirty === source.sourceDirty, 'Artifact source state is stale');
  if (target) {
    const expected = nativeTargets[target];
    assert(expected && manifest.platform === expected.platform && manifest.architecture === expected.architecture, 'Native platform/architecture mismatch');
  }
  const actual = inventory(directory, manifestName);
  assert(Array.isArray(manifest.files) && manifest.files.length === actual.length, 'Artifact inventory mismatch');
  actual.forEach((file, index) => {
    const listed = manifest.files[index];
    assert(listed && listed.path === file.path && listed.size === file.size && listed.sha256 === file.sha256,
      `Artifact missing, changed, unsorted, or unlisted: ${file.path}`);
  });
  if (component === 'frontend') {
    for (const file of requiredWebFiles) assert(actual.some(entry => entry.path === file && entry.size > 0), `Missing Web artifact: ${file}`);
  }
  if (component === 'desktop') {
    for (const file of requiredDesktopFiles) assert(actual.some(entry => entry.path === file && entry.size > 0), `Missing desktop artifact: ${file}`);
  }
  if (target) {
    const filename = `quarkus-${manifest.version}-${target}${nativeTargets[target].extension}`;
    assert(actual.length === 1 && actual[0].path === filename && actual[0].size > 0, 'Native binary inventory mismatch');
  }
  return manifest;
}

function packageWeb(outputDirectory) {
  const dist = path.join(frontendRoot, 'dist');
  const manifest = verifyManifest(dist);
  fs.mkdirSync(outputDirectory, { recursive: true });
  assert(fs.readdirSync(outputDirectory).length === 0, 'Web archive destination must be empty');
  const archive = `frontend-${manifest.version}-${manifest.sourceCommit}.zip`;
  execFileSync('zip', ['-q', '-r', path.resolve(outputDirectory, archive), '.'], { cwd: dist, stdio: 'inherit' });
  fs.copyFileSync(path.join(dist, frontendManifest), path.join(outputDirectory, 'frontend-build-manifest.json'));
}

function packageNative(target) {
  assert(nativeTargets[target], `Unsupported native target: ${target}`);
  const buildDirectory = path.join(repoRoot, 'backend/target');
  const version = versionString(fs.readFileSync(path.join(buildDirectory, 'build-version.txt'), 'utf8').trim());
  const extension = nativeTargets[target].extension;
  const candidates = fs.readdirSync(buildDirectory).filter(file => file.endsWith(`-runner${extension}`));
  assert(candidates.length === 1, 'Expected exactly one native runner');
  const source = path.join(buildDirectory, candidates[0]);
  assert(fs.lstatSync(source).isFile() && fs.statSync(source).size > 0, 'Native runner is missing or empty');
  const outputDirectory = path.join(buildDirectory, `artifact-${target}`);
  fs.mkdirSync(outputDirectory);
  fs.copyFileSync(source, path.join(outputDirectory, `quarkus-${version}-${target}${extension}`));
  const { platform, architecture } = nativeTargets[target];
  writeManifest(outputDirectory, 'backend', version, { platform, architecture }, `backend-${target}-manifest.json`);
  verifyManifest(outputDirectory, { component: 'backend', target, manifestName: `backend-${target}-manifest.json` });
}

function packageDesktop(nativeDirectory, outputDirectory) {
  // 桌面包里的后端二进制在本地打包时是打包者手工放进 bin/server/server 的（DELIVERY-04）。
  // 这里先按 backend 契约校验 CI 下载来的 native 产物，再逐字节确认包内那份就是它，
  // 并把来源版本与 SHA 写进 manifest，让「包里装的是哪次后端构建」可审计。
  const target = 'linux-amd64';
  const native = verifyManifest(nativeDirectory, { component: 'backend', target, manifestName: `backend-${target}-manifest.json` });
  assert(fs.lstatSync(desktopUnpacked).isDirectory(), 'Desktop --dir output is missing; run electron-builder --linux --dir first');
  const server = path.join(desktopUnpacked, 'bin', 'server', 'server');
  assert(fs.lstatSync(server).isFile() && fs.statSync(server).size > 0, 'Desktop package is missing bin/server/server');
  assert(hashFile(server, Buffer.allocUnsafe(1024 * 1024)) === native.files[0].sha256,
    'Desktop package embeds a backend binary other than the verified native artifact');
  const { platform, architecture } = nativeTargets[target];
  const provenance = { platform, architecture, backendVersion: native.version, backendSha256: native.files[0].sha256 };
  writeManifest(desktopUnpacked, 'desktop', desktopVersion(), provenance, desktopManifest);
  const manifest = verifyManifest(desktopUnpacked, { component: 'desktop', manifestName: desktopManifest });
  fs.mkdirSync(outputDirectory, { recursive: true });
  assert(fs.readdirSync(outputDirectory).length === 0, 'Desktop archive destination must be empty');
  const archive = `desktop-linux-${manifest.version}-${manifest.sourceCommit}.zip`;
  execFileSync('zip', ['-q', '-r', path.resolve(outputDirectory, archive), '.'], { cwd: desktopUnpacked, stdio: 'inherit' });
  fs.copyFileSync(path.join(desktopUnpacked, desktopManifest), path.join(outputDirectory, desktopManifest));
}

// tag 去掉 `refs/tags/v` 前缀后就是发布版本号。非 `v` 前缀的 tag 一律拒绝：
// 与其让 tag 和产物版本各说各话，不如拒发。
function releaseVersion(releaseRef) {
  const prefix = 'refs/tags/v';
  assert(typeof releaseRef === 'string' && releaseRef.startsWith(prefix) && releaseRef.length > prefix.length,
    `Release ref must be ${prefix}<version>, got: ${releaseRef}`);
  return versionString(releaseRef.slice(prefix.length));
}

// Web 与桌面两个组件都以「zip + 旁挂 manifest」交付，校验步骤完全一致：
// 先拒绝穿越路径，再解包逐文件比对 manifest，最后确认旁挂 manifest 与包内一字不差。
function verifyArchivedComponent(directory, component, archivePrefix, manifestName, sidecarName, expectedCommit) {
  const sidecarPath = path.join(directory, sidecarName);
  const sidecar = JSON.parse(fs.readFileSync(sidecarPath, 'utf8'));
  const archiveName = `${archivePrefix}-${versionString(sidecar.version)}-${expectedCommit}.zip`;
  assert(JSON.stringify(fs.readdirSync(directory).sort()) === JSON.stringify([archiveName, sidecarName].sort()), `${component} archive set mismatch`);
  const archivePath = path.resolve(directory, archiveName);
  const unpacked = fs.mkdtempSync(path.join(os.tmpdir(), `${archivePrefix}-release-`));
  try {
    // Reject traversal before extraction; inventory verification rejects symlinks and unexpected files.
    const entries = execFileSync('unzip', ['-Z1', archivePath], { encoding: 'utf8' }).trim().split('\n');
    assert(entries.every(entry => entry && !entry.startsWith('/') && !entry.includes('\\') && !entry.split('/').includes('..')), `Unsafe ${component} archive path`);
    execFileSync('unzip', ['-q', archivePath, '-d', unpacked], { stdio: 'inherit' });
    const manifest = verifyManifest(unpacked, { expectedCommit, component, manifestName });
    assert(fs.readFileSync(sidecarPath).equals(fs.readFileSync(path.join(unpacked, manifestName))), `${component} manifest sidecar mismatch`);
    return { manifest, assets: [archivePath, sidecarPath] };
  } finally {
    fs.rmSync(unpacked, { recursive: true, force: true });
  }
}

function verifyRelease(artifactsDirectory, outputDirectory, expectedCommit, releaseRef) {
  assert(expectedCommit, 'Release verification requires an explicit source SHA');
  assert(/^[0-9a-f]{40}$/.test(expectedCommit), 'Expected source commit must be a full Git SHA');
  const tagVersion = releaseVersion(releaseRef);
  const expectedDirectories = ['frontend', 'desktop-linux', ...Object.keys(nativeTargets)].sort();
  assert(JSON.stringify(fs.readdirSync(artifactsDirectory).sort()) === JSON.stringify(expectedDirectories), 'Release artifact set mismatch');
  const assets = [];
  const web = verifyArchivedComponent(path.join(artifactsDirectory, 'frontend'), 'frontend', 'frontend', frontendManifest, 'frontend-build-manifest.json', expectedCommit);
  assets.push(...web.assets);
  const desktop = verifyArchivedComponent(path.join(artifactsDirectory, 'desktop-linux'), 'desktop', 'desktop-linux', desktopManifest, desktopManifest, expectedCommit);
  assets.push(...desktop.assets);
  let backendVersion;
  const backendBinaries = {};
  for (const target of Object.keys(nativeTargets)) {
    const directory = path.join(artifactsDirectory, target);
    const manifestName = `backend-${target}-manifest.json`;
    const manifest = verifyManifest(directory, { expectedCommit, component: 'backend', target, manifestName });
    if (backendVersion !== undefined) assert(manifest.version === backendVersion, 'Native artifact versions disagree');
    backendVersion = manifest.version;
    backendBinaries[target] = manifest.files[0].sha256;
    assets.push(path.join(directory, manifest.files[0].path), path.join(directory, manifestName));
  }
  // 仓内三个版本号互不相干（backend/pom.xml、bw-frontend/frontend/package.json、
  // bw-frontend/package.json —— 桌面资产名用的是第三个），这正是 DELIVERY-04 的缺陷本身，
  // 所以发布流程只负责拒绝不一致，绝不放宽。失败信息一次性报出三者的当前值与期望值：
  // 发版当天要能一眼看出改哪个文件，而不是逐个试。
  const declared = [
    ['backend/pom.xml', backendVersion],
    ['bw-frontend/frontend/package.json', frontendVersion()],
    ['bw-frontend/package.json', desktopVersion()],
  ];
  const drifted = declared.filter(([, version]) => version !== tagVersion);
  const report = declared
    .map(([source, version]) => `\n  ${version === tagVersion ? 'OK      ' : 'MISMATCH'} ${source} declares ${version}, expected ${tagVersion}`)
    .join('');
  assert(drifted.length === 0,
    `Release version mismatch for ${releaseRef}: ${drifted.length} of ${declared.length} version files disagree with the tag.${report}`
    + `\n  Fix: set every MISMATCH file above to ${tagVersion} in one commit, then re-tag.`);
  // 桌面包必须装着本次发布的 linux-amd64 后端二进制，而不是某次别的构建。
  assert(desktop.manifest.backendSha256 === backendBinaries['linux-amd64'],
    'Desktop package does not embed the released linux-amd64 backend binary');
  fs.mkdirSync(outputDirectory, { recursive: true });
  assert(fs.readdirSync(outputDirectory).length === 0, 'Release asset destination must be empty');
  for (const asset of assets) fs.copyFileSync(asset, path.join(outputDirectory, path.basename(asset)));
  console.log(`Verified ${assets.length} release assets for ${releaseRef} from ${expectedCommit}`);
}

function main(args) {
  const [command, ...parameters] = args;
  if (command === 'frontend' && parameters.length === 0) {
    writeManifest(path.join(frontendRoot, 'dist'), 'frontend', frontendVersion());
  } else if (command === 'verify' && parameters.length >= 1 && parameters.length <= 2) {
    verifyManifest(path.resolve(parameters[0]), parameters.length === 2 ? { expectedCommit: parameters[1] } : { allowDirty: true });
    console.log('Artifact manifest verified');
  } else if (command === 'package-web' && parameters.length === 1) {
    packageWeb(path.resolve(parameters[0]));
  } else if (command === 'native' && parameters.length === 1) {
    packageNative(parameters[0]);
  } else if (command === 'package-desktop' && parameters.length === 2) {
    packageDesktop(path.resolve(parameters[0]), path.resolve(parameters[1]));
  } else if (command === 'release' && parameters.length === 4) {
    verifyRelease(path.resolve(parameters[0]), path.resolve(parameters[1]), parameters[2], parameters[3]);
  } else {
    throw new Error('Usage: artifact-manifest.cjs frontend | verify <dist> [sourceSHA] | package-web <empty-output-dir> | native <target> | package-desktop <native-artifact-dir> <empty-output-dir> | release <artifact-dir> <empty-output-dir> <sourceSHA> <releaseRef>');
  }
}

module.exports = { verifyManifest, verifyRelease, packageDesktop };

if (require.main === module) {
  try {
    main(process.argv.slice(2));
  } catch (error) {
    console.error(`Artifact validation failed: ${error.message}`);
    process.exitCode = 1;
  }
}
