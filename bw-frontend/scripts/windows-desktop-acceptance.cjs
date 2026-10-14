'use strict';
// Native Windows acceptance, not a release builder. Invoke through windows-desktop-acceptance.ps1.
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const net = require('node:net');
const crypto = require('node:crypto');
const assert = require('node:assert/strict');
const {spawn, spawnSync} = require('node:child_process');
const {createFixture} = require('./windows-desktop-fixture.cjs');

const [artifactArg, outputArg, mysqlArg, playwrightArg] = process.argv.slice(2);
assert.equal(process.platform, 'win32', 'Native Windows is required');
assert.equal(process.arch, 'x64', 'Native Windows x64 is required');
assert(artifactArg && outputArg && mysqlArg && playwrightArg, 'Required: rust artifact, output, mysqld, playwright-core');
const repo = path.resolve(__dirname, '../..');
const desktop = path.join(repo, 'bw-frontend');
const artifact = path.resolve(artifactArg);
const output = path.resolve(outputArg);
const mysqld = path.resolve(mysqlArg);
const mysql = path.join(path.dirname(mysqld), 'mysql.exe');
const {chromium} = require(path.resolve(playwrightArg));
fs.mkdirSync(output, {recursive: true});
const work = fs.realpathSync.native(fs.mkdtempSync(path.join(os.tmpdir(), 'nip-rust-desktop-')));
const install = path.join(work, 'installed');
const profile = path.join(work, 'profile');
const machine = path.join(work, 'program-data');
const runtime = path.join(profile, 'runtime', 'bin');
let mutableRoot;
const trackedPackage = fs.readFileSync(path.join(desktop, 'package.json'));
const packageMetadata = JSON.parse(trackedPackage);
const sha256 = value => crypto.createHash('sha256').update(value).digest('hex');
const hash = file => sha256(fs.readFileSync(file));
const writeJson = (name, value) => fs.writeFileSync(path.join(output, name), JSON.stringify(value, null, 2));
const report = {schema: 'rust-native-windows-desktop/v1', status: 'RUNNING', started: new Date().toISOString(),
  sourceCommit: process.env.GITHUB_SHA || null, runnerImage: process.env.ImageVersion || null,
  platform: {os: os.release(), arch: process.arch, node: process.version}, work,
  boundaries: {unsignedValidationOnly: true, publishing: false, productionGate: false, fieldGate: false,
    trustedSigningGate: false, historicalCustomerUpgradeProven: false,
    upgrade: 'Two monotonically increasing validation-only metadata versions from the SAME SOURCE, with the real A state placed in the legacy layout before B. Not a historical customer installer upgrade.',
    license: 'Existing development activation UI only. Not ordinary encrypted/device-bound license acceptance.'},
  backend: {sourceArtifact: 'rust-backend-windows-x86_64-gnu', needsJob: 'rust-backend-windows-gnu', sha256: hash(artifact)},
  installers: [], runs: [], cleanup: []};
const started = new Map();
const owned = new Map();
let browser;
let page;
let database;
let activeApp;
let fixture;
let failure;
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
async function until(description, condition, timeout = 60000) {
  const deadline = Date.now() + timeout;
  let last;
  while (Date.now() < deadline) {
    try { const result = await condition(); if (result) return result; } catch (error) { last = error; }
    await delay(250);
  }
  throw new Error(`Timed out: ${description}${last ? `: ${last.message}` : ''}`);
}
function ps(script) {
  const host = process.env.DESKTOP_ACCEPTANCE_POWERSHELL;
  assert(host && path.isAbsolute(host), 'Invoke through the PowerShell acceptance wrapper');
  const result = spawnSync(host, ['-NoProfile', '-NonInteractive', '-EncodedCommand',
    Buffer.from("$ErrorActionPreference='Stop'; " + script, 'utf16le').toString('base64')],
  {encoding: 'utf8', windowsHide: true, timeout: 30000, maxBuffer: 16 * 1024 * 1024});
  assert.equal(result.status, 0, result.stderr || String(result.error));
  return result.stdout.replace(/^\uFEFF/, '').trim();
}
const psQuote = value => `'${String(value).replace(/'/g, "''")}'`;
function processes() {
  return JSON.parse(ps("@{processes=@(Get-CimInstance Win32_Process | Select-Object ProcessId,ParentProcessId,ExecutablePath,CommandLine,CreationDate);listeners=@(Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue | Select-Object LocalAddress,LocalPort,OwningProcess)} | ConvertTo-Json -Depth 5 -Compress"));
}
function captureOwnership(rootPid) {
  const snapshot = processes();
  const ids = new Set([rootPid]);
  let added = true;
  while (added) {
    added = false;
    for (const item of snapshot.processes) if (ids.has(item.ParentProcessId) && !ids.has(item.ProcessId)) {
      ids.add(item.ProcessId); added = true;
    }
  }
  const children = snapshot.processes.filter(item => ids.has(item.ProcessId));
  for (const item of children) owned.set(item.ProcessId, item);
  return {...snapshot, children, ids};
}
function begin(file, args, name, options = {}) {
  const out = fs.openSync(path.join(output, `${name}.stdout.log`), 'a');
  const err = fs.openSync(path.join(output, `${name}.stderr.log`), 'a');
  const child = spawn(file, args, {cwd: desktop, windowsHide: true, ...options, stdio: ['ignore', out, err]});
  fs.closeSync(out); fs.closeSync(err);
  child.done = new Promise((resolve, reject) => {
    child.once('error', reject);
    child.once('exit', (code, signal) => resolve({code, signal}));
  });
  child.done.catch(() => {});
  if (child.pid) started.set(child.pid, child);
  return child;
}
async function command(file, args, name, options) {
  const child = begin(file, args, name, options);
  const result = await child.done;
  assert.equal(result.code, 0, `${name} exited ${result.code}; see logs`);
  return result;
}
async function freePort() {
  const server = net.createServer();
  await new Promise((resolve, reject) => { server.once('error', reject); server.listen(0, '127.0.0.1', resolve); });
  const port = server.address().port;
  await new Promise(resolve => server.close(resolve));
  return port;
}
function assertNoListeners(ports) {
  const listeners = processes().listeners.filter(item => ports.includes(item.LocalPort));
  assert.deepEqual(listeners, [], `Ports already occupied; will not terminate their owners: ${JSON.stringify(listeners)}`);
}
function signature(file) {
  return JSON.parse(ps(`$s=Get-AuthenticodeSignature -LiteralPath ${psQuote(file)}; @{status=[string]$s.Status;statusMessage=$s.StatusMessage;signer=if($s.SignerCertificate){$s.SignerCertificate.Subject}else{$null}} | ConvertTo-Json -Compress`));
}
async function screenshot(label) {
  await page.screenshot({path: path.join(output, `${label}.png`), fullPage: true});
  // Capture the actual Windows desktop too, not only a viewport rendering.
  ps(`Add-Type -AssemblyName System.Windows.Forms; Add-Type -AssemblyName System.Drawing; $r=[System.Windows.Forms.SystemInformation]::VirtualScreen; $b=New-Object System.Drawing.Bitmap($r.Width,$r.Height); $g=[System.Drawing.Graphics]::FromImage($b); try {$g.CopyFromScreen($r.Left,$r.Top,0,0,$b.Size);$b.Save(${psQuote(path.join(output, `${label}-windows.png`))})} finally {$g.Dispose();$b.Dispose()}`);
}
function readDb(file) {
  const records = new Map();
  for (const line of fs.readFileSync(file, 'utf8').trim().split(/\r?\n/)) {
    if (!line) continue;
    const row = JSON.parse(line);
    if (row.$$deleted) records.delete(row._id);
    else if (row._id !== undefined) records.set(row._id, row);
  }
  return records;
}
const expectedConfig = {dataUrl: {url: '127.0.0.1', port: '18001'}, fileUrl: {url: '127.0.0.1', port: '8000'}};
function licenseProof() {
  const files = [path.join(machine, 'NipTrafficSystem/license.json'), path.join(profile, 'license.json')];
  return files.map(file => {
    const record = JSON.parse(fs.readFileSync(file, 'utf8'));
    assert.equal(record.license, 'wjkj2025~');
    assert(record.machineCode, 'Missing real hardware device code');
    return {file, machineCodeSha256: sha256(record.machineCode), licenseSha256: sha256(record.license), bytes: fs.statSync(file).size};
  });
}
async function buildInstallers() {
  const [major, minor, patch] = packageMetadata.version.split('.').map(Number);
  assert([major, minor, patch].every(Number.isInteger), 'Expected canonical three-part package version');
  const serverDir = path.join(desktop, 'bin/server');
  assert(!fs.existsSync(serverDir), 'Refusing to overwrite a developer backend directory');
  fs.mkdirSync(serverDir);
  fs.copyFileSync(artifact, path.join(serverDir, 'server.exe'));
  assert.equal(hash(path.join(serverDir, 'server.exe')), report.backend.sha256);
  try {
    for (let index = 1; index <= 2; index++) {
      const version = `${major}.${minor}.${patch + index}-validation.${index}`;
      const buildDir = path.join(output, `build-${index}`);
      await command(process.execPath, [path.join(desktop, 'node_modules/electron-builder/cli.js'), '--win', 'nsis', '--x64', '--publish', 'never',
        `--config.extraMetadata.version=${version}`, '--config.appId=org.nip.rust-desktop-validation',
        '--config.productName=NIP UNSIGNED VALIDATION ONLY', '--config.win.executableName=NipRustValidation',
        '--config.win.artifactName=NIP-UNSIGNED-VALIDATION-${version}-${arch}.${ext}',
        `--config.directories.output=${buildDir}`, '--config.nsis.runAfterFinish=false',
        '--config.nsis.createDesktopShortcut=false', '--config.nsis.createStartMenuShortcut=false'], `build-${index}`,
      {env: {...process.env, CSC_IDENTITY_AUTO_DISCOVERY: 'false'}});
      assert.deepEqual(fs.readFileSync(path.join(desktop, 'package.json')), trackedPackage, 'Tracked version was modified');
      const packagedServer = path.join(buildDir, 'win-unpacked/bin/server/server.exe');
      assert.equal(hash(packagedServer), report.backend.sha256, 'Packaged Rust differs from needed job artifact');
      const installers = fs.readdirSync(buildDir).filter(name => name.endsWith('.exe'));
      assert.equal(installers.length, 1, 'Expected one NSIS installer');
      const installer = path.join(buildDir, installers[0]);
      const authenticode = signature(installer);
      assert.equal(authenticode.status, 'NotSigned', 'Validation installer unexpectedly signed or corrupt');
      report.installers.push({version, path: installer, sha256: hash(installer), bytes: fs.statSync(installer).size,
        authenticode, packagedRustSha256: hash(packagedServer), asarSha256: hash(path.join(buildDir, 'win-unpacked/resources/app.asar'))});
      writeJson('acceptance.json', report);
    }
  } finally {
    fs.rmSync(serverDir, {recursive: true, force: true});
  }
}
async function installVersion(index) {
  const installer = report.installers[index];
  await command(installer.path, ['/S', `/D=${install}`], `install-${index + 1}`, {windowsVerbatimArguments: true});
  const server = path.join(install, 'bin/server/server.exe');
  assert.equal(hash(server), report.backend.sha256, 'Installed backend is not the exact native artifact');
  const exe = path.join(install, 'NipRustValidation.exe');
  assert(fs.existsSync(exe), 'NSIS did not install the application');
  assert.equal(hash(path.join(install, 'resources/app.asar')), installer.asarSha256, 'Installed app.asar is not the selected version');
  installer.installedRustSha256 = hash(server);
  installer.backendAuthenticode = signature(server);
  assert.equal(installer.backendAuthenticode.status, 'NotSigned');
  installer.applicationAuthenticode = signature(exe);
  assert.equal(installer.applicationAuthenticode.status, 'NotSigned');
  return exe;
}
async function startMysql() {
  const port = await freePort();
  const datadir = path.join(work, 'mysql-data');
  fs.mkdirSync(datadir);
  await command(mysqld, ['--no-defaults', '--initialize-insecure', `--basedir=${path.dirname(path.dirname(mysqld))}`, `--datadir=${datadir}`], 'mysql-initialize');
  database = begin(mysqld, ['--no-defaults', '--console', '--bind-address=127.0.0.1', `--port=${port}`,
    '--mysqlx=0', '--skip-log-bin', '--local-infile=0', '--innodb-buffer-pool-size=64M',
    `--basedir=${path.dirname(path.dirname(mysqld))}`, `--datadir=${datadir}`, `--log-error=${path.join(output, 'mysql-error.log')}`], 'mysql');
  await until('private MySQL startup', () => {
    assert(database.exitCode === null, 'MySQL exited before startup');
    const result = spawnSync(mysql, ['--no-defaults', '--protocol=TCP', '-h127.0.0.1', `-P${port}`, '-uroot', '--batch', '--skip-column-names', '-e', 'SELECT @@datadir;'],
      {encoding: 'utf8', timeout: 5000, windowsHide: true, env: {...process.env, MYSQL_PWD: ''}});
    return result.status === 0 && path.resolve(result.stdout.trim()).toLowerCase() === path.resolve(datadir).toLowerCase();
  });
  const ownership = captureOwnership(database.pid);
  // Windows mysqld starts a same-executable supervised child. The observed
  // listener must belong to our captured tree, not a pre-existing MySQL service.
  const listeners = ownership.listeners.filter(item => item.LocalPort === port);
  assert.equal(listeners.length, 1, `Expected one private MySQL listener: ${JSON.stringify(listeners)}`);
  const listenerOwner = ownership.children.find(item => item.ProcessId === listeners[0].OwningProcess);
  assert(listenerOwner && listenerOwner.ExecutablePath?.toLowerCase() === mysqld.toLowerCase() &&
    listeners[0].LocalAddress === '127.0.0.1', `Private MySQL listener is not our owned server: ${JSON.stringify(listeners)}`);
  fixture = createFixture({repo, mysql, port, schema: `rust_desktop_${crypto.randomBytes(8).toString('hex')}`, output});
  const password = crypto.randomBytes(24).toString('hex');
  fixture.sql(`CREATE USER 'desktop_fixture'@'127.0.0.1' IDENTIFIED BY '${password}'; GRANT ALL PRIVILEGES ON \`${fixture.report.schema}\`.* TO 'desktop_fixture'@'127.0.0.1';`);
  report.database = {...fixture.report, supervisorPid: database.pid, pid: listenerOwner.ProcessId,
    processTree: ownership.children, listeners, datadir, tool: mysqld,
    version: ps(`& ${psQuote(mysqld)} --version; if($LASTEXITCODE -ne 0){throw 'mysqld version failed'}`),
    runnerDocumentation: 'https://github.com/actions/runner-images/blob/main/images/windows/Windows2022-Readme.md#database-tools'};
  return `mysql://desktop_fixture:${password}@127.0.0.1:${port}/${fixture.report.schema}`;
}
async function launch(exe, index, databaseUrl) {
  assertNoListeners([18001, 8000]);
  const cdpPort = await freePort();
  const env = {...process.env, ProgramData: machine, ALLUSERSPROFILE: machine,
    APPDATA: path.join(work, 'appdata'), LOCALAPPDATA: path.join(work, 'localappdata'),
    RUST_BACKEND_ADDR: '127.0.0.1:18001', RUST_BACKEND_DATABASE_URL: databaseUrl,
    RUST_BACKEND_DB_TIMEOUT_SECS: '30', RUST_LOG: 'rust_backend=info', ELECTRON_ENABLE_LOGGING: '1'};
  // Prove the installed desktop does not borrow MinGW DLLs or Java from CI tools.
  for (const key of Object.keys(env)) if (/^path$/i.test(key)) delete env[key];
  const windows = process.env.SystemRoot;
  assert(windows && path.isAbsolute(windows), 'Missing native Windows SystemRoot');
  env.Path = [install, path.join(install, 'bin/server'), path.join(windows, 'System32'), windows,
    path.join(windows, 'System32/Wbem'), path.join(windows, 'System32/WindowsPowerShell/v1.0')].join(';');
  for (const key of Object.keys(env)) if (/^RUST_PHASE.*_ENABLED$/.test(key)) delete env[key];
  const source = fs.readFileSync(path.join(repo, 'backend-rust/crates/app/src/main.rs'), 'utf8');
  const flags = [...new Set(source.match(/RUST_PHASE[A-Z0-9_]+_ENABLED/g))];
  assert(flags.length >= 20, 'Full-domain feature list unexpectedly incomplete');
  for (const key of flags) env[key] = 'true';
  for (const dir of [profile, machine, env.APPDATA, env.LOCALAPPDATA]) fs.mkdirSync(dir, {recursive: true});
  activeApp = begin(exe, [`--user-data-dir=${profile}`, `--remote-debugging-port=${cdpPort}`, '--remote-debugging-address=127.0.0.1', '--enable-logging=stderr'], `electron-${index}`, {env});
  await until('installed Electron CDP endpoint', async () => {
    assert(activeApp.exitCode === null, 'Electron exited before CDP');
    const response = await fetch(`http://127.0.0.1:${cdpPort}/json/version`);
    return response.ok;
  });
  browser = await chromium.connectOverCDP(`http://127.0.0.1:${cdpPort}`);
  page = await until('real packaged renderer', () => browser.contexts().flatMap(context => context.pages()).find(candidate => candidate.url().startsWith('file:')));
  assert(page.url().includes('/resources/app.asar/public/dist/index.html'), `Not the installed packaged renderer: ${page.url()}`);
  const run = {version: report.installers[Math.min(index, 2) - 1].version, pid: activeApp.pid, cdpPort, url: page.url(), flags,
    requests: [], console: [], pageErrors: []};
  report.runs.push(run);
  page.on('console', message => run.console.push({type: message.type(), text: message.text()}));
  page.on('pageerror', error => run.pageErrors.push(error.message));
  page.on('response', response => {
    const request = response.request();
    const headers = request.headers();
    run.requests.push({url: response.url(), status: response.status(), method: request.method(), resourceType: request.resourceType(),
      contentType: headers['content-type'] || null, tokenPresent: !!headers.token, deviceIdPresent: !!headers.deviceid});
  });
  await until('actual Rust and file child listeners', async () => {
    const health = await fetch('http://127.0.0.1:18001/ready');
    const file = await fetch('http://127.0.0.1:8000/api/file');
    return health.ok && file.ok;
  });
  const ownership = captureOwnership(activeApp.pid);
  const rust = ownership.children.filter(item => item.ExecutablePath?.toLowerCase() === path.join(install, 'bin/server/server.exe').toLowerCase());
  assert.equal(rust.length, 1, 'Electron must own exactly one installed Rust child');
  assert.equal(rust[0].ParentProcessId, activeApp.pid, 'Rust is not a direct Electron child');
  for (const port of [18001, 8000]) {
    const listeners = ownership.listeners.filter(item => item.LocalPort === port);
    assert(listeners.length && listeners.every(item => ownership.ids.has(item.OwningProcess)), `Unowned listener ${port}`);
    assert(listeners.every(item => ['127.0.0.1', '::1'].includes(item.LocalAddress)), `Non-loopback listener ${port}`);
    if (port === 18001) assert(listeners.every(item => item.OwningProcess === rust[0].ProcessId));
    if (port === 8000) for (const listener of listeners) {
      const child = ownership.children.find(item => item.ProcessId === listener.OwningProcess);
      assert.equal(child.ParentProcessId, activeApp.pid, 'File service is not a direct Electron child');
      assert.match(child.CommandLine, /[\\/]electron[\\/]service[\\/]http[\\/]index\.js/, 'Listener is not the packaged Node file service');
    }
  }
  run.ownership = {children: ownership.children, listeners: ownership.listeners.filter(item => [18001, 8000, cdpPort].includes(item.LocalPort))};
  run.rustPid = rust[0].ProcessId;
  run.desktopChildPath = env.Path;
  mutableRoot = fs.existsSync(path.join(runtime, 'nip.db')) ? runtime : path.join(install, 'bin');
  run.observedMutableRoot = mutableRoot;
  if (index > 1) assert.equal(mutableRoot, runtime, 'Upgraded app did not migrate legacy state into private userData/runtime/bin');
  return run;
}
async function activate() {
  await page.locator('#licenseCodeDiv').waitFor({state: 'visible', timeout: 60000});
  await screenshot('01-development-license-form');
  await page.locator('#licenseCodeDiv').fill('wjkj2025~');
  await page.getByRole('button', {name: /开\s*始\s*授\s*权/}).click();
  await page.locator('input[placeholder="请输入用户名"]:visible').waitFor({timeout: 60000});
  await until('both private license copies', () => licenseProof());
  report.activation = licenseProof();
  await screenshot('02-authorized-login');
}
async function networkSettings(change, label) {
  await page.locator('#openSettingWindow:visible').click();
  const modal = page.locator('.ant-modal-content:visible').filter({hasText: '网络配置'});
  await modal.waitFor();
  const switches = modal.getByRole('switch');
  if (change) {
    for (let index = 0; index < 2; index++) if (await switches.nth(index).getAttribute('aria-checked') === 'true') await switches.nth(index).click();
    const inputs = modal.locator('input');
    assert.equal(await inputs.count(), 10);
    for (const [index, value] of ['127', '0', '0', '1', '18001', '127', '0', '0', '1', '8000'].entries()) await inputs.nth(index).fill(value);
    await screenshot(`${label}-edited`);
    const reload = page.waitForEvent('domcontentloaded');
    await modal.getByRole('button', {name: /^确\s*定$/}).click();
    await reload;
    await page.locator('input[placeholder="请输入用户名"]:visible').waitFor({timeout: 60000});
    await until('saved isolated network database', () => JSON.stringify(readDb(path.join(mutableRoot, 'nip.db')).get(2)?.text) === JSON.stringify(expectedConfig));
  } else {
    assert.equal(await switches.nth(0).getAttribute('aria-checked'), 'false');
    assert.equal(await switches.nth(1).getAttribute('aria-checked'), 'false');
    assert.deepEqual(await modal.locator('input').evaluateAll(inputs => inputs.map(input => input.value)), ['127', '0', '0', '1', '18001', '127', '0', '0', '1', '8000']);
    await screenshot(label);
    await modal.getByRole('button', {name: /^取\s*消$/}).click();
  }
  report.networkConfig = readDb(path.join(mutableRoot, 'nip.db')).get(2).text;
  assert.deepEqual(report.networkConfig, expectedConfig);
}
async function login(account, password, label) {
  await page.locator('input[placeholder="请输入用户名"]:visible').fill(account);
  await page.locator('input[placeholder="请输入密码"]:visible').fill(password);
  const response = page.waitForResponse(response => response.url().endsWith('/api/user/login') && response.request().method() === 'POST');
  await page.locator('.loginBtn:visible').click();
  const result = await response;
  assert.equal(result.status(), 200);
  const body = await result.json();
  assert.equal(body.code, 200, body.message);
  assert(body.data.token && body.data.deviceId && body.data.menus.length, 'Real login returned incomplete identity/menus');
  await page.waitForURL(/#\/preview(?:\/|$)/, {timeout: 60000});
  await screenshot(label);
  return {code: body.code, userId: body.data.user.id, menus: body.data.menus.length, realForm: true, noStoredTokenInjection: true};
}
async function openUpload() {
  // Ordinary router navigation, never Vue internals, IPC writes, or forged session state.
  await page.goto(page.url().split('#')[0] + '#/preview/acceptanceUpload?type=0&studyType=0');
  await page.locator('input[type=file][accept=".txt,.md,.csv"]').waitFor({state: 'attached', timeout: 60000});
  await page.locator('iframe.tox-edit-area__iframe').waitFor({timeout: 60000});
}
async function downloadInRenderer(url, expected, label) {
  const result = await page.evaluate(async url => {
    const response = await fetch(url, {cache: 'no-store'});
    const bytes = new Uint8Array(await response.arrayBuffer());
    let binary = '';
    for (const byte of bytes) binary += String.fromCharCode(byte);
    return {status: response.status, base64: btoa(binary), url: response.url};
  }, url);
  const actual = Buffer.from(result.base64, 'base64');
  assert.equal(result.status, 200);
  assert(actual.length > 0);
  assert.deepEqual(actual, expected, `${label}: downloaded bytes differ`);
  fs.writeFileSync(path.join(output, `${label}.download`), actual);
  return {url: result.url, status: result.status, bytes: actual.length, sha256: sha256(actual), rendererFetch: true};
}
async function uploadFiles() {
  const text = 'Rust multipart 上传验收\nalpha beta gamma\n<b>这行必须作为文本显示</b>\n';
  const textPath = path.join(work, 'acceptance-document.txt');
  fs.writeFileSync(textPath, text);
  await openUpload();
  const response = page.waitForResponse(response => response.url().endsWith('/api/theoryKnowledge/uploadFileToNip') && response.request().method() === 'POST');
  await page.locator('input[type=file][accept=".txt,.md,.csv"]').setInputFiles(textPath);
  const received = await response;
  const requestHeaders = received.request().headers();
  assert.match(requestHeaders['content-type'] || '', /^multipart\/form-data;\s*boundary=/i);
  assert(requestHeaders.token && requestHeaders.deviceid, 'Document upload did not carry the actual login identity');
  const body = await received.json();
  assert.equal(received.status(), 200);
  assert.equal(body.code, 200);
  assert.equal(body.data.wordContent, text);
  const editor = page.frameLocator('iframe.tox-edit-area__iframe').locator('body');
  await until('literal uploaded document rendered by TinyMCE', async () => (await editor.innerText()).includes('<b>这行必须作为文本显示</b>'));
  assert.equal(await editor.locator('b').count(), 0);
  await screenshot('05-rust-document-upload');
  report.documentUpload = {endpoint: '/api/theoryKnowledge/uploadFileToNip', code: body.code, sourceSha256: sha256(text),
    bytes: Buffer.byteLength(text), wordContent: body.data.wordContent, literalMarkup: true,
    boundary: 'Rust text-import UI. This is distinct from the Node file resource roundtrip below.'};
  // Deterministic real PNG; Ant Upload's actual file input sends multipart to Node.
  const png = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/l9sAAAAASUVORK5CYII=', 'base64');
  const pngPath = path.join(work, 'acceptance-resource.png');
  fs.writeFileSync(pngPath, png);
  const uploadResponse = page.waitForResponse(response => response.url().includes('/api/file/upload') && response.request().method() === 'POST');
  await page.locator('.editBookPic input[type=file]').setInputFiles(pngPath);
  const uploaded = await uploadResponse;
  assert.match(uploaded.request().headers()['content-type'] || '', /^multipart\/form-data;\s*boundary=/i);
  assert.equal(uploaded.status(), 200);
  const uploadBody = await uploaded.json();
  assert.equal(uploadBody.data?.length, 1);
  const relative = uploadBody.data[0].replace(/\\/g, '/');
  assert(relative && !relative.split('/').some(part => part === '..') && !path.isAbsolute(relative));
  const url = `http://127.0.0.1:8000/api/file/getFile/${relative.split('/').map(encodeURIComponent).join('/')}`;
  report.resourceUpload = {relative, sourceSha256: sha256(png), bytes: png.length,
    beforeUpgrade: await downloadInRenderer(url, png, 'resource-before-upgrade')};
  assert.equal(hash(path.join(mutableRoot, 'file', relative)), sha256(png), 'Upload not confined to the observed file root');
  await screenshot('06-node-resource-roundtrip');
  report.resourcePathSafety = [];
  for (const relative of ['..%2fserver%2fserver.exe', '..%2fnip.db', '..%2f..%2fresources%2fapp.asar']) {
    const url = `http://127.0.0.1:8000/api/file/getFile/${relative}`;
    const status = await page.evaluate(async url => (await fetch(url, {cache: 'no-store'})).status, url);
    report.resourcePathSafety.push({url, status});
    assert.equal(status, 404, 'File service exposed data outside its file root');
  }
  return {png, relative, url};
}
async function shutdown(run, index) {
  await page.goto(page.url().split('#')[0] + '#/login');
  await page.locator('.iconItemFive:visible').click();
  const modal = page.locator('.ant-modal-confirm:visible');
  await modal.getByRole('button', {name: /^退\s*出$/}).waitFor();
  await screenshot(`exit-${index}-confirmation`);
  // Capture descendants before the normal UI exit; cleanup cannot satisfy this assertion.
  const before = captureOwnership(run.pid);
  await modal.getByRole('button', {name: /^退\s*出$/}).click();
  await until('UI-requested Electron exit', () => activeApp.exitCode !== null || activeApp.signalCode !== null);
  const result = await activeApp.done;
  await until('all application descendants and listeners gone without harness kill', () => {
    const snapshot = processes();
    return !snapshot.processes.some(item => before.ids.has(item.ProcessId)) &&
      !snapshot.listeners.some(item => [18001, 8000, run.cdpPort].includes(item.LocalPort));
  }, 30000);
  const log = fs.readFileSync(path.join(output, `electron-${index}.stdout.log`), 'utf8') + fs.readFileSync(path.join(output, `electron-${index}.stderr.log`), 'utf8');
  run.shutdown = {trigger: 'Real close-software control and confirmation -> controller.system.closeApp',
    electronExitCode: result.code, electronSignal: result.signal, childPidsGone: before.children.map(item => item.ProcessId),
    listenersGone: [18001, 8000, run.cdpPort], harnessKillBeforeAssertion: false,
    rustMode: log.includes('rust backend stopped gracefully') ? 'Rust graceful shutdown log observed' : 'Windows child termination; no Rust graceful-shutdown log observed (not a graceful Rust exit claim)'};
  assert.equal(result.code, 0, 'Normal application exit was nonzero');
  browser = null; page = null; activeApp = null;
}
function seedLegacy() {
  const legacy = path.join(install, 'bin');
  const observedRoot = mutableRoot;
  if (mutableRoot === runtime) {
    // Simulate only the historical layout, using the state actually produced by A.
    // No changes to the machine/user license copies or Chromium profile.
    for (const name of ['nip.db', 'nip.db~', 'file', 'config.json']) {
      const source = path.join(runtime, name);
      if (!fs.existsSync(source)) continue;
      const target = path.join(legacy, name);
      fs.rmSync(target, {recursive: true, force: true});
      fs.renameSync(source, target);
    }
    fs.renameSync(path.join(profile, 'runtime'), path.join(work, 'runtime-A-after-layout-move'));
  }
  const sentinel = {_id: 'native-windows-legacy-sentinel', note: 'synthetic legacy data created before installer replacement'};
  fs.appendFileSync(path.join(legacy, 'nip.db'), JSON.stringify(sentinel) + '\n');
  if (!fs.existsSync(path.join(legacy, 'config.json'))) fs.writeFileSync(path.join(legacy, 'config.json'), '{}\n');
  const config = JSON.parse(fs.readFileSync(path.join(legacy, 'config.json'), 'utf8'));
  config.nativeWindowsLegacy = 'must-survive-installer-replacement';
  fs.writeFileSync(path.join(legacy, 'config.json'), JSON.stringify(config) + '\n');
  fs.mkdirSync(path.join(legacy, 'file/legacy'), {recursive: true});
  fs.writeFileSync(path.join(legacy, 'file/legacy/acceptance.txt'), 'Pre-upgrade legacy resource\nWindows 原生保留验收\n');
  const files = {};
  function collect(relative) {
    const source = path.join(legacy, relative);
    if (!fs.existsSync(source)) return;
    const stat = fs.lstatSync(source);
    assert(!stat.isSymbolicLink(), 'Legacy fixture must not contain links');
    if (stat.isDirectory()) for (const name of fs.readdirSync(source)) collect(`${relative}/${name}`);
    else files[relative] = fs.readFileSync(source);
  }
  for (const name of ['nip.db', 'nip.db~', 'file', 'config.json']) collect(name);
  assert.deepEqual(readDb(path.join(legacy, 'nip.db')).get(2).text, expectedConfig);
  report.legacy = {seededBeforeUpgradeInstaller: true, originalAStateRoot: observedRoot,
    layoutSimulation: 'Only this run\'s actual A mutable state moved to install/bin; activation and Chromium profile untouched', sentinel,
    files: Object.fromEntries(Object.entries(files).map(([name, value]) => [name, {sha256: sha256(value), bytes: value.length}]))};
  return files;
}
function assertLegacyBeforeLaunch(files) {
  // Deliberately before launching B: a post-launch migration cannot hide installer data loss.
  for (const [relative, bytes] of Object.entries(files)) assert.deepEqual(fs.readFileSync(path.join(install, 'bin', relative)), bytes, `Installer erased legacy ${relative}`);
  report.legacy.preservedImmediatelyAfterInstallerBeforeFirstLaunch = true;
}
async function cleanup() {
  if (activeApp) { try { captureOwnership(activeApp.pid); } catch (_) {} }
  for (const [pid, original] of [...owned.entries()].reverse()) {
    try {
      const current = processes().processes.find(item => item.ProcessId === pid);
      if (!current || current.CreationDate !== original.CreationDate || current.ExecutablePath !== original.ExecutablePath) continue;
      ps(`Stop-Process -Id ${pid} -Force -ErrorAction Stop`);
      report.cleanup.push({pid, action: 'failure/final cleanup only; not acceptance shutdown proof'});
    } catch (error) { report.cleanup.push({pid, error: error.message}); }
  }
  for (const [pid, child] of started) if (child.exitCode === null && child.signalCode === null) {
    child.kill(); report.cleanup.push({pid, action: 'owned child handle cleanup'});
  }
  if (database) await until('owned MySQL stopped', () => database.exitCode !== null || database.signalCode !== null, 15000);
  // Remove only this run's NSIS registration/install, and only after evidence collection.
  if (fs.existsSync(install)) {
    const uninstallers = fs.readdirSync(install).filter(name => /^Uninstall.*\.exe$/i.test(name));
    if (uninstallers.length === 1) {
      await command(path.join(install, uninstallers[0]), ['/S'], 'uninstall-validation');
    }
  }
  fs.rmSync(work, {recursive: true, force: true, maxRetries: 10, retryDelay: 500});
}
(async () => {
  try {
    const existingValidation = ps("$found=@(); foreach($root in @('HKCU:\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall','HKLM:\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall','HKLM:\\Software\\WOW6432Node\\Microsoft\\Windows\\CurrentVersion\\Uninstall')) { if(Test-Path $root) {$found+=@(Get-ChildItem $root | Get-ItemProperty | Where-Object {$_.DisplayName -eq 'NIP UNSIGNED VALIDATION ONLY'})}}; $found.Count");
    assert.equal(existingValidation, '0', 'Existing validation installation detected; refusing to replace another run\'s data');
    assertNoListeners([18001, 8000]);
    const databaseUrl = await startMysql();
    await buildInstallers();
    const account = 'uiwin' + crypto.randomBytes(4).toString('hex');
    const password = crypto.randomBytes(18).toString('hex');
    const exe = await installVersion(0);
    const first = await launch(exe, 1, databaseUrl);
    await activate();
    await networkSettings(true, '03-network-config');
    const registration = await fetch('http://127.0.0.1:18001/api/user/signin', {method: 'POST', headers: {'content-type': 'application/json'},
      body: JSON.stringify({userAccount: account, userName: '验收教员', idCard: '110101199001011234', password, userSex: 1, eday: '2020-01-01'})});
    assert.equal(registration.status, 200);
    assert.equal((await registration.json()).code, 200, 'Real synthetic registration failed');
    first.login = await login(account, password, '04-login-before-upgrade');
    const resource = await uploadFiles();
    await shutdown(first, 1);
    const licenses = licenseProof();
    const legacy = seedLegacy();
    writeJson('acceptance.json', report);
    const upgraded = await installVersion(1);
    assertLegacyBeforeLaunch(legacy);
    const backupParent = `${install}.nip-data-backups`;
    const backups = fs.readdirSync(backupParent).filter(name => fs.existsSync(path.join(backupParent, name, 'complete.json')));
    assert.equal(backups.length, 1, 'Expected one durable pre-uninstall snapshot in this private fresh-install fixture');
    const backup = path.join(backupParent, backups[0]);
    for (const [relative, bytes] of Object.entries(legacy)) {
      assert.deepEqual(fs.readFileSync(path.join(backup, 'bin', relative)), bytes, `Recovery snapshot lost ${relative}`);
    }
    report.legacy.recoverySnapshot = {path: backup, manifestSha256: hash(path.join(backup, 'complete.json')),
      verifiedFiles: Object.keys(legacy), retainedOutsideInstallDirectory: true};
    fs.copyFileSync(path.join(backup, 'complete.json'), path.join(output, 'legacy-backup-manifest.json'));
    assert.deepEqual(licenseProof(), licenses, 'Installer changed private activation');
    assert.deepEqual(readDb(path.join(install, 'bin/nip.db')).get(2).text, expectedConfig, 'Installer changed legacy network config');
    assert.equal(hash(path.join(install, 'bin/file', resource.relative)), sha256(resource.png));
    const second = await launch(upgraded, 2, databaseUrl);
    assert.deepEqual(readDb(path.join(runtime, 'nip.db')).get('native-windows-legacy-sentinel'), report.legacy.sentinel);
    assert.equal(JSON.parse(fs.readFileSync(path.join(runtime, 'config.json'), 'utf8')).nativeWindowsLegacy, 'must-survive-installer-replacement');
    report.legacy.migratedIntoPrivateRuntime = true;
    await page.locator('input[placeholder="请输入用户名"]:visible').waitFor({timeout: 60000});
    assert.equal(await page.locator('#licenseCodeDiv').count(), 0, 'Upgrade requested reactivation');
    assert.deepEqual(licenseProof().map(item => [item.machineCodeSha256, item.licenseSha256]), licenses.map(item => [item.machineCodeSha256, item.licenseSha256]));
    await screenshot('07-activation-preserved');
    await networkSettings(false, '08-network-preserved');
    second.login = await login(account, password, '09-login-after-upgrade');
    report.resourceUpload.afterUpgrade = await downloadInRenderer(resource.url, resource.png, 'resource-after-upgrade');
    report.legacy.download = await downloadInRenderer('http://127.0.0.1:8000/api/file/getFile/legacy/acceptance.txt', legacy['file/legacy/acceptance.txt'], 'legacy-after-upgrade');
    await screenshot('10-upgrade-resource-persistence');
    await shutdown(second, 2);
    // Existing runtime wins: old install/bin data must never reset a newer user profile.
    const staleConfig = {_id: 2, text: {dataUrl: {url: '192.0.2.1', port: '1'}, fileUrl: {url: '192.0.2.1', port: '2'}}};
    fs.writeFileSync(path.join(install, 'bin/nip.db'), JSON.stringify(staleConfig) + '\n');
    fs.mkdirSync(path.dirname(path.join(install, 'bin/file', resource.relative)), {recursive: true});
    fs.writeFileSync(path.join(install, 'bin/file', resource.relative), 'STALE LEGACY MUST NOT REPLACE USER UPLOAD');
    fs.writeFileSync(path.join(install, 'bin/config.json'), JSON.stringify({nativeWindowsLegacy: 'STALE'}));
    const third = await launch(upgraded, 3, databaseUrl);
    await page.locator('input[placeholder="请输入用户名"]:visible').waitFor({timeout: 60000});
    assert.equal(await page.locator('#licenseCodeDiv').count(), 0);
    await networkSettings(false, '11-runtime-authority');
    assert.equal(JSON.parse(fs.readFileSync(path.join(runtime, 'config.json'), 'utf8')).nativeWindowsLegacy, 'must-survive-installer-replacement');
    report.resourceUpload.afterStaleLegacyRestart = await downloadInRenderer(resource.url, resource.png, 'resource-after-stale-legacy');
    report.legacy.existingRuntimeWinsOverStaleLegacy = true;
    await shutdown(third, 3);
    assert(report.runs.every(run => run.pageErrors.length === 0), 'Renderer JavaScript errors observed; see evidence');
    report.customerLibraryBoundary = 'No customer resource library or iconfont was supplied. Missing requests are retained, not replaced by invented assets or counted as a complete field-resource gate.';
    report.status = 'PASS_NATIVE_UNSIGNED_SAME_SOURCE_DESKTOP_ACCEPTANCE';
  } catch (error) {
    failure = error;
    report.status = 'FAIL'; report.error = error.stack;
    if (page) { try { await screenshot('failure'); } catch (captureError) { report.failureScreenshotError = captureError.message; } }
    try {
      if (activeApp) captureOwnership(activeApp.pid);
      const snapshot = processes();
      writeJson('failure-processes.json', {
        processes: snapshot.processes.filter(item => owned.has(item.ProcessId)),
        listeners: snapshot.listeners.filter(item => owned.has(item.OwningProcess))
      });
    } catch (_) {}
    console.error(error.stack);
  } finally {
    report.unavailableCustomerResources = report.runs.flatMap(run => run.requests.filter(request =>
      request.url.includes('/api/file/getFile/') && request.status >= 400));
    writeJson('acceptance.json', report);
    try { await cleanup(); } catch (error) { report.cleanup.push({error: error.stack}); report.status = 'FAIL'; failure ||= error; }
    report.finished = new Date().toISOString();
    const evidence = [];
    function inventory(dir) {
      for (const entry of fs.readdirSync(dir, {withFileTypes: true})) {
        const full = path.join(dir, entry.name);
        if (entry.isDirectory()) { if (!entry.name.startsWith('build-')) inventory(full); }
        else if (!['acceptance.json', 'evidence-hashes.json'].includes(entry.name)) evidence.push({path: path.relative(output, full), bytes: fs.statSync(full).size, sha256: hash(full)});
      }
    }
    inventory(output); writeJson('evidence-hashes.json', evidence); writeJson('acceptance.json', report);
    if (failure) process.exitCode = 1;
  }
})();
