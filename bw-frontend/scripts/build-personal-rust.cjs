'use strict';

const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const assert = require('node:assert/strict');
const {spawn, execFileSync} = require('node:child_process');
const {hashBinary, loadBackendProfile} = require('../electron/service/backend-profile');

const desktop = path.resolve(__dirname, '..');
const repo = path.dirname(desktop);
const flagsSource = path.join(repo, 'backend-rust/crates/app/src/platform/flags.rs');

function windowsExecutable(file, {unsigned = false, x64 = true} = {}) {
  const fd = fs.openSync(file, 'r');
  const readAt = (size, offset) => {
    const bytes = Buffer.alloc(size);
    assert.equal(fs.readSync(fd, bytes, 0, size, offset), size, 'Truncated Windows executable');
    return bytes;
  };
  try {
    assert(fs.fstatSync(fd).isFile(), 'Windows artifact must be a regular file');
    const dos = readAt(64, 0);
    assert.equal(dos.toString('ascii', 0, 2), 'MZ', 'Expected a Windows PE executable');
    const offset = dos.readUInt32LE(60);
    const pe = readAt(24, offset);
    assert.equal(pe.readUInt32LE(0), 0x4550, 'Invalid PE signature');
    assert.equal(pe.readUInt16LE(4), x64 ? 0x8664 : 0x14c, 'Unexpected Windows executable architecture');
    assert(pe.readUInt16LE(22) & 2, 'PE is not executable');
    assert(!(pe.readUInt16LE(22) & 0x2000), 'Expected executable, not DLL');
    const optional = readAt(pe.readUInt16LE(20), offset + 24);
    const directoryOffset = x64 ? 112 : 96;
    assert(optional.length >= directoryOffset + 40 && optional.readUInt16LE(0) === (x64 ? 0x20b : 0x10b), 'Invalid PE optional header');
    if (unsigned) assert(optional.readUInt32LE(directoryOffset - 4) > 4 && optional.readUInt32LE(directoryOffset + 32) === 0 && optional.readUInt32LE(directoryOffset + 36) === 0,
      'Personal artifacts must not carry an Authenticode certificate');
  } finally { fs.closeSync(fd); }
}

function canonicalFlags() {
  const source = fs.readFileSync(flagsSource, 'utf8').replace(/\r\n/g, '\n');
  const declarations = [...source.matchAll(/^pub\(crate\) const [A-Z0-9_]+: &str = "(RUST_PHASE[A-Z0-9_]+_ENABLED)";$/gm)];
  const flags = declarations.map(match => match[1]);
  assert(flags.length > 0 && new Set(flags).size === flags.length, 'Canonical feature declarations are empty or duplicated');
  assert.equal(flags.length, source.split(/\r?\n/).filter(line => line.trim() && !line.trim().startsWith('//')).length,
    'Canonical flag format changed; update the staging parser before packaging');
  return flags;
}

function stageRustBackend(artifact, root = desktop) {
  windowsExecutable(artifact, {unsigned: true});
  const profile = {schemaVersion: 1, kind: 'rust', binarySha256: hashBinary(artifact), featureFlags: canonicalFlags()};
  const directory = path.join(root, 'bin/server');
  // mkdir is exclusive: even a competing build or dangling link must not be overwritten.
  fs.mkdirSync(directory);
  let staged = true;
  const cleanup = () => {
    if (!staged) return;
    fs.rmSync(directory, {recursive: true, force: true});
    staged = false;
  };
  try {
    const binary = path.join(directory, 'server.exe');
    fs.copyFileSync(artifact, binary, fs.constants.COPYFILE_EXCL);
    fs.writeFileSync(path.join(directory, 'backend.json'), `${JSON.stringify(profile, null, 2)}\n`, {flag: 'wx'});
    loadBackendProfile(binary, true);
    return {directory, profile, cleanup};
  } catch (error) { cleanup(); throw error; }
}

function unsignedBuildEnvironment(inherited = process.env) {
  const env = {...inherited};
  for (const key of Object.keys(env)) {
    if (/^(?:WIN_)?CSC_/i.test(key) || /^(?:RUST_BACKEND_DATABASE_URL|DB_USER|DB_PASSWORD|MYSQL_PWD)$/i.test(key) ||
        /^(?:GH_TOKEN|GITHUB_TOKEN|GITHUB_RELEASE_TOKEN|BT_TOKEN)$/i.test(key)) delete env[key];
  }
  env.CSC_IDENTITY_AUTO_DISCOVERY = 'false';
  return env;
}

function runBuilder(file, args, options) {
  return new Promise((resolve, reject) => {
    const child = spawn(file, args, {...options, stdio: 'inherit'});
    child.once('error', () => reject(new Error('Unable to start electron-builder')));
    child.once('exit', code => code === 0 ? resolve() : reject(new Error(`electron-builder exited ${code}`)));
  });
}

function sourceMetadata() {
  const git = args => execFileSync('git', args, {cwd: repo, encoding: 'utf8'}).trim();
  const names = git(['ls-files', '--cached', '--others', '--exclude-standard', '-z', '--',
    'bw-frontend/electron', 'bw-frontend/scripts', 'bw-frontend/build', 'bw-frontend/main.js',
    'bw-frontend/package.json', 'bw-frontend/package-lock.json', 'bw-frontend/frontend',
    'bw-frontend/LICENSE', 'bw-frontend/bin', 'bw-frontend/public/images', 'bw-frontend/public/html',
    'backend-rust/crates', 'backend-rust/Cargo.toml', 'backend-rust/Cargo.lock']);
  const files = [...new Set(names.split('\0').filter(Boolean))].sort().filter(name => fs.existsSync(path.join(repo, name)))
    .map(name => ({path: name, sha256: hashBinary(path.join(repo, name))}));
  return {commit: git(['rev-parse', 'HEAD']), dirty: git(['status', '--porcelain', '--untracked-files=all']) !== '',
    sha256: crypto.createHash('sha256').update(JSON.stringify(files)).digest('hex'), files,
    flagsSourceSha256: hashBinary(flagsSource)};
}

async function buildRustInstaller({artifact, output, validationVersion, run = runBuilder}) {
  artifact = path.resolve(artifact);
  output = path.resolve(output);
  assert(!fs.existsSync(path.join(desktop, 'bin/server')), 'Refusing to overwrite existing bin/server');
  fs.mkdirSync(output, {recursive: true});
  assert(fs.lstatSync(output).isDirectory() && !fs.lstatSync(output).isSymbolicLink() && fs.readdirSync(output).length === 0,
    'Installer output directory must be empty and not a link');
  const packageBytes = fs.readFileSync(path.join(desktop, 'package.json'));
  const canonicalVersion = JSON.parse(packageBytes).version;
  const validation = validationVersion !== undefined;
  if (validation) assert(/^\d+\.\d+\.\d+-validation\.\d+$/.test(validationVersion), 'Invalid validation-only version');
  const version = validationVersion || canonicalVersion;
  const source = sourceMetadata();
  const stage = stageRustBackend(artifact);
  try {
    const identity = validation ? {appId: 'org.nip.rust-desktop-validation', name: 'nip-rust-validation', productName: 'NIP UNSIGNED VALIDATION ONLY', executable: 'NipRustValidation'} :
      {appId: 'org.nip.rust-desktop-personal', name: 'nip-rust-personal', productName: 'NIP Rust Personal', executable: 'NipRustPersonal'};
    const args = [path.join(desktop, 'node_modules/electron-builder/cli.js'), '--win', 'nsis', '--x64', '--publish', 'never',
      `--config.appId=${identity.appId}`, `--config.productName=${identity.productName}`, `--config.win.executableName=${identity.executable}`,
      `--config.extraMetadata.name=${identity.name}`, `--config.extraMetadata.productName=${identity.productName}`,
      '--config.extraMetadata.backendRuntime=rust',
      '--config.win.artifactName=NIP-Rust-' + (validation ? 'VALIDATION' : 'PERSONAL') + '-${version}-${arch}.${ext}',
      `--config.directories.output=${output}`, '--config.nsis.runAfterFinish=false'];
    if (validation) args.push(`--config.extraMetadata.version=${version}`,
      '--config.nsis.createDesktopShortcut=false', '--config.nsis.createStartMenuShortcut=false');
    await run(process.execPath, args, {cwd: desktop, env: unsignedBuildEnvironment()});
    assert(fs.readFileSync(path.join(desktop, 'package.json')).equals(packageBytes), 'Canonical package metadata was modified');
    const unpacked = path.join(output, 'win-unpacked');
    const packagedMetadata = JSON.parse(require('asar').extractFile(path.join(unpacked, 'resources/app.asar'), 'package.json').toString('utf8'));
    assert(packagedMetadata.name === identity.name && packagedMetadata.productName === identity.productName && packagedMetadata.version === version && packagedMetadata.backendRuntime === 'rust',
      'Packaged application identity does not match its isolated personal/validation data namespace');
    const packagedServer = path.join(unpacked, 'bin/server/server.exe');
    const packagedExecutable = path.join(unpacked, `${identity.executable}.exe`);
    const profile = loadBackendProfile(packagedServer, true);
    assert.deepEqual(profile, stage.profile, 'Packaged backend profile differs from staged profile');
    assert(fs.lstatSync(packagedExecutable).isFile(), 'Packaged personal executable is missing');
    windowsExecutable(packagedExecutable, {unsigned: true});
    const installers = fs.readdirSync(output).filter(name => name.endsWith('.exe'));
    assert.equal(installers.length, 1, 'Expected exactly one NSIS installer');
    const installer = path.join(output, installers[0]);
    // NSIS uses an x86 bootstrapper even for an x64 application.
    windowsExecutable(installer, {unsigned: true, x64: false});
    const metadata = {schemaVersion: 1, scope: validation ? 'unsigned-validation-only' : 'unsigned-personal-only',
      publishing: false, distribution: false, signing: 'unsigned', packageTarget: 'nsis', version, canonicalVersion, identity, source,
      installer: {file: path.basename(installer), sha256: hashBinary(installer), bytes: fs.statSync(installer).size},
      packageRoot: 'win-unpacked',
      backend: {sha256: hashBinary(packagedServer), sourceArtifactSha256: stage.profile.binarySha256,
        profileSha256: hashBinary(path.join(unpacked, 'bin/server/backend.json')), featureFlags: profile.featureFlags},
      asarSha256: hashBinary(path.join(unpacked, 'resources/app.asar')),
      environment: {required: ['RUST_BACKEND_DATABASE_URL'],
        instructions: 'Set RUST_BACKEND_DATABASE_URL to your existing personal MySQL connection URL in the environment that launches the installed executable. Restart the application after changing it. Never put credentials in backend.json, installation files, build arguments, or this metadata. The package sets its own loopback address and feature flags. No database is created or migrated by this builder.'}};
    fs.writeFileSync(path.join(output, 'personal-rust-package.json'), `${JSON.stringify(metadata, null, 2)}\n`, {flag: 'wx'});
    return {installer, unpacked, metadata};
  } finally { stage.cleanup(); }
}

if (require.main === module) {
  const [artifact, output, ...extra] = process.argv.slice(2);
  if (!artifact || !output || extra.length) {
    console.error('Usage: node scripts/build-personal-rust.cjs <Windows-x64-Rust.exe> <empty-output-directory>');
    process.exitCode = 1;
  } else buildRustInstaller({artifact, output}).catch(() => {
    console.error('Personal Rust packaging failed. Check the artifact, empty output directory, dependencies and builder diagnostics. No installer was published.');
    process.exitCode = 1;
  });
}

module.exports = {windowsExecutable, canonicalFlags, stageRustBackend, unsignedBuildEnvironment, buildRustInstaller};
