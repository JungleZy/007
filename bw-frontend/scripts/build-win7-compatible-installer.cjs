const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const {execFileSync} = require('node:child_process');
const {verifyIntegratedRuntime} = require('./prepare-integrated-runtime.cjs');
const {verifyMysqlTools, assertBinaryTarget} = require('./bundle-mysql-tools.cjs');
const {verifyDependencyClosure} = require('./verify-packaged-runtime.cjs');
const project = path.resolve(__dirname, '..');

function packageFiles(directory, relative = '') {
    return fs.readdirSync(path.join(directory, relative), {withFileTypes: true}).flatMap(entry => {
        const name = relative ? `${relative}/${entry.name}` : entry.name;
        if (entry.isDirectory()) return packageFiles(directory, name);
        if (!entry.isFile()) throw new Error(`Unexpected non-file in package: ${name}`);
        return [name];
    });
}

function nsisLiteral(value) {
    return value.replace(/\$/g, () => '$$').replace(/"/g, () => '$\\"').replace(/\r|\n/g, '');
}

function uninstallFileList(files) {
    const directories = new Set();
    const lines = files.map(file => {
        if (file.startsWith('/') || file.split('/').includes('..') || file.includes('\\')) throw new Error('Invalid package path');
        let parent = path.posix.dirname(file);
        while (parent !== '.') { directories.add(parent); parent = path.posix.dirname(parent); }
        return `Delete "$INSTDIR\\${nsisLiteral(file.replace(/\//g, '\\'))}"`;
    });
    for (const directory of [...directories].sort((a, b) => b.split('/').length - a.split('/').length || b.localeCompare(a))) {
        lines.push(`RMDir "$INSTDIR\\${nsisLiteral(directory.replace(/\//g, '\\'))}"`);
    }
    return lines.join('\n') + '\n';
}

async function digest(file) {
    const hash = crypto.createHash('sha256');
    for await (const chunk of fs.createReadStream(file)) hash.update(chunk);
    return hash.digest('hex');
}

async function build() {
    if (process.platform !== 'win32') throw new Error('Build the Win7 compatibility installer on Windows');
    const output = path.join(project, 'out/win7-ia32');
    const payload = path.join(output, 'win-ia32-unpacked');
    const metadata = require('../package.json');
    const exe = `${metadata.productName}.exe`;
    verifyIntegratedRuntime(payload, 'windows-ia32');
    verifyMysqlTools(payload, 'windows-ia32');
    verifyDependencyClosure(path.join(payload, 'resources/app.asar'));
    // Refuse stale unpacked output: rebuilding the installer alone cannot pick up source fixes.
    const asar = require('asar');
    for (const name of ['main.js', 'electron/core/graphics.js', 'electron/service/integrated-runtime.js',
        'electron/serial/nativeSerialPort.js', 'electron/serial/index.js']) {
        if (!asar.extractFile(path.join(payload, 'resources/app.asar'), path.normalize(name))
            .equals(fs.readFileSync(path.join(project, name)))) {
            throw new Error('Packaged source is stale; rebuild the Win7 application directory: ' + name);
        }
    }
    assertBinaryTarget(path.join(payload, exe), 'windows-ia32');
    const driverRoot = path.join(payload, 'drivers/ch340');
    const driverManifest = JSON.parse(fs.readFileSync(path.join(driverRoot, 'manifest.json'), 'utf8'));
    for (const [name, expected] of Object.entries(driverManifest.files)) {
        if (await digest(path.join(driverRoot, name)) !== expected) throw new Error('CH340 driver checksum mismatch: ' + name);
    }
    const nsis = process.env.ELECTRON_BUILDER_NSIS_DIR;
    if (!nsis) throw new Error('Set ELECTRON_BUILDER_NSIS_DIR to the verified NSIS compiler directory');
    const compiler = path.join(nsis, 'Bin/makensis.exe');
    fs.accessSync(compiler);
    const source = path.join(project, 'build/win7-compatible-installer.nsi');
    if (/\b(?:System|UAC|StdUtils|nsDialogs)::/.test(fs.readFileSync(source, 'utf8'))) throw new Error('Compatibility installer must not use plugin API calls');
    const files = packageFiles(payload);
    const uninstall = path.join(output, 'compat-uninstall-files.nsh');
    fs.writeFileSync(uninstall, '\ufeff' + uninstallFileList(files));
    const installer = path.join(output, `${metadata.productName}-HJ-win7-${metadata.version}-ia32-compat2.exe`);
    const zip = path.join(output, `${metadata.productName}-HJ-win7-${metadata.version}-ia32-unpacked.zip`);
    const seven = require('7zip-bin').path7za;
    const extractor = path.resolve(path.dirname(require.resolve('7zip-bin/package.json')), 'win/ia32/7za.exe');
    assertBinaryTarget(extractor, 'windows-ia32');
    const payloadArchive = path.join(output, 'compat2-application.7z');
    console.log('Building direct extraction ZIP and installer payload...');
    fs.rmSync(zip, {force: true});
    execFileSync(seven, ['a', '-tzip', '-mx=5', zip, '.\\*'], {cwd: payload, stdio: 'pipe'});
    fs.rmSync(payloadArchive, {force: true});
    execFileSync(seven, ['a', '-t7z', '-mx=5', '-md=16m', '-ms=on', payloadArchive, '.\\*'], {cwd: payload, stdio: 'pipe'});
    console.log('Building compatibility installer without System.dll...');
    execFileSync(compiler, ['/V2', '/WX', '/INPUTCHARSET', 'UTF8',
        `/DPRODUCT_NAME=${metadata.productName}`, `/DAPP_EXE=${exe}`, `/DAPP_VERSION=${metadata.version}`,
        `/DINSTALLER_FILE=${installer}`, `/DPAYLOAD_ARCHIVE=${payloadArchive}`, `/DEXTRACTOR_EXE=${extractor}`, `/DUNINSTALL_FILES=${uninstall}`, source],
    {stdio: 'inherit', env: {...process.env, NSISDIR: nsis}});
    const check = execFileSync(seven, ['l', installer, '-ir!System.dll'], {encoding: 'utf8'});
    if (/\d+\s+\d+\s+.*System\.dll/i.test(check)) throw new Error('Unexpected System.dll in compatibility installer');
    const report = {interfaceStyle: 'HJ', appVersion: metadata.version, driver: driverManifest.source,
        driverInstall: 'Interactive installer launches signed vendor SETUP.EXE with elevation; silent installs provide Start menu shortcut',
        systemPluginIncluded: false, files: []};
    for (const file of [installer, zip]) report.files.push({name: path.basename(file), bytes: fs.statSync(file).size, sha256: await digest(file)});
    fs.writeFileSync(path.join(output, 'win7-compat2-report.json'), JSON.stringify(report, null, 2));
    console.log(JSON.stringify(report, null, 2));
}

if (require.main === module) build().catch(error => { console.error(error.message); process.exitCode = 1; });
module.exports = {packageFiles, uninstallFileList, nsisLiteral};
