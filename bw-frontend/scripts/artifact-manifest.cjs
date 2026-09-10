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

function verifyRelease(artifactsDirectory, outputDirectory, expectedCommit) {
  assert(expectedCommit, 'Release verification requires an explicit source SHA');
  assert(/^[0-9a-f]{40}$/.test(expectedCommit), 'Expected source commit must be a full Git SHA');
  const expectedDirectories = ['frontend', ...Object.keys(nativeTargets)].sort();
  assert(JSON.stringify(fs.readdirSync(artifactsDirectory).sort()) === JSON.stringify(expectedDirectories), 'Release artifact set mismatch');
  const assets = [];
  const webDirectory = path.join(artifactsDirectory, 'frontend');
  const sidecarPath = path.join(webDirectory, 'frontend-build-manifest.json');
  const sidecar = JSON.parse(fs.readFileSync(sidecarPath, 'utf8'));
  const archiveName = `frontend-${versionString(sidecar.version)}-${expectedCommit}.zip`;
  assert(JSON.stringify(fs.readdirSync(webDirectory).sort()) === JSON.stringify([archiveName, 'frontend-build-manifest.json'].sort()), 'Web archive set mismatch');
  const archivePath = path.resolve(webDirectory, archiveName);
  const unpacked = fs.mkdtempSync(path.join(os.tmpdir(), 'frontend-release-'));
  try {
    // Reject traversal before extraction; inventory verification rejects symlinks and unexpected files.
    const entries = execFileSync('unzip', ['-Z1', archivePath], { encoding: 'utf8' }).trim().split('\n');
    assert(entries.every(entry => entry && !entry.startsWith('/') && !entry.includes('\\') && !entry.split('/').includes('..')), 'Unsafe Web archive path');
    execFileSync('unzip', ['-q', archivePath, '-d', unpacked], { stdio: 'inherit' });
    verifyManifest(unpacked, { expectedCommit });
    assert(fs.readFileSync(sidecarPath).equals(fs.readFileSync(path.join(unpacked, frontendManifest))), 'Web manifest sidecar mismatch');
  } finally {
    fs.rmSync(unpacked, { recursive: true, force: true });
  }
  assets.push(archivePath, sidecarPath);
  let backendVersion;
  for (const target of Object.keys(nativeTargets)) {
    const directory = path.join(artifactsDirectory, target);
    const manifestName = `backend-${target}-manifest.json`;
    const manifest = verifyManifest(directory, { expectedCommit, component: 'backend', target, manifestName });
    if (backendVersion !== undefined) assert(manifest.version === backendVersion, 'Native artifact versions disagree');
    backendVersion = manifest.version;
    assets.push(path.join(directory, manifest.files[0].path), path.join(directory, manifestName));
  }
  fs.mkdirSync(outputDirectory, { recursive: true });
  assert(fs.readdirSync(outputDirectory).length === 0, 'Release asset destination must be empty');
  for (const asset of assets) fs.copyFileSync(asset, path.join(outputDirectory, path.basename(asset)));
  console.log(`Verified ${assets.length} release assets from ${expectedCommit}`);
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
  } else if (command === 'release' && parameters.length === 3) {
    verifyRelease(path.resolve(parameters[0]), path.resolve(parameters[1]), parameters[2]);
  } else {
    throw new Error('Usage: artifact-manifest.cjs frontend | verify <dist> [sourceSHA] | package-web <empty-output-dir> | native <target> | release <artifact-dir> <empty-output-dir> <sourceSHA>');
  }
}

module.exports = { verifyManifest };

if (require.main === module) {
  try {
    main(process.argv.slice(2));
  } catch (error) {
    console.error(`Artifact validation failed: ${error.message}`);
    process.exitCode = 1;
  }
}
