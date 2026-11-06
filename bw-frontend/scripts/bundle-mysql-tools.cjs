const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const {execFileSync} = require('node:child_process');
const {mysqlTarget, parseTarget} = require('../electron/shared/mysql-platforms');

const bundleRoot = 'bin/file/tools/mysql';
const elfMachines = {x64: 62, arm64: 183, loong64: 258, mips64el: 8, sw64: 0x9916, riscv64: 243};

function sha256(file) {
    return crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex');
}

function assertBinaryTarget(file, target) {
    const {platform, architecture} = parseTarget(target);
    const data = fs.readFileSync(file);
    if (platform === 'windows') {
        const offset = data.length >= 64 ? data.readUInt32LE(60) : -1;
        if (offset < 0 || offset + 6 > data.length || data.toString('ascii', 0, 2) !== 'MZ'
            || data.readUInt32LE(offset) !== 0x4550 || data.readUInt16LE(offset + 4) !== (architecture === 'ia32' ? 0x14c : 0x8664)) {
            throw new Error(`Expected Windows ${architecture} PE executable: ${file}`);
        }
    } else if (data.length < 64 || data.toString('hex', 0, 4) !== '7f454c46'
        || data[4] !== 2 || data[5] !== 1 || data.readUInt16LE(18) !== elfMachines[architecture]) {
        throw new Error(`Expected 64-bit little-endian ELF for ${target}: ${file}`);
    }
}

function inventory(directory, relative = '') {
    const result = [];
    for (const item of fs.readdirSync(path.join(directory, relative), {withFileTypes: true})) {
        const name = relative ? `${relative}/${item.name}` : item.name;
        if (name === 'manifest.json') continue;
        if (item.isDirectory()) result.push(...inventory(directory, name));
        else if (item.isFile()) result.push(name);
        else throw new Error(`Bundle must contain regular files, not links: ${name}`);
    }
    return result.sort();
}

function requiredFiles(target) {
    if (target === 'windows-ia32') return ['mysqldump.exe', 'mysql.exe', 'LICENSE.mysql', 'README.mysql'];
    return target.startsWith('windows-')
        ? ['mysqldump.exe', 'mysql.exe', 'libcrypto-3-x64.dll', 'libssl-3-x64.dll', 'LICENSE.mysql', 'README.mysql']
        : ['mysqldump', 'mysql', 'LICENSE.mysql', 'README.mysql'];
}

function validateRuntime(runtime, target) {
    const versionPattern = target === 'windows-ia32' ? /^5\.7\.44$/ : /^8\.\d+(?:\.\d+)?$/;
    if (runtime?.clientFamily !== 'mysql' || !versionPattern.test(runtime.clientVersion)) {
        throw new Error('This SQL transfer implementation requires MySQL 8 client tools; MariaDB is not interchangeable');
    }
    if (target.startsWith('linux-') && (runtime.libc !== 'glibc' || !/^2\.\d+$/.test(runtime.minLibc)
        || !Array.isArray(runtime.testedOn)
        || runtime.testedOn.some(value => typeof value !== 'string' || !value.trim()))) {
        throw new Error('Linux runtime.json must specify glibc, minLibc and testedOn (empty means execution not verified)');
    }
}

function verifyDirectory(directory, target) {
    const manifest = JSON.parse(fs.readFileSync(path.join(directory, 'manifest.json'), 'utf8'));
    if (manifest.schemaVersion !== 2 || manifest.target !== target) throw new Error(`MySQL manifest target mismatch: ${target}`);
    validateRuntime(manifest.runtime, target);
    const files = inventory(directory);
    if (JSON.stringify(files) !== JSON.stringify(Object.keys(manifest.files).sort())) throw new Error('MySQL bundle inventory mismatch');
    for (const name of requiredFiles(target)) {
        if (!files.includes(name)) throw new Error(`MySQL bundle is missing: ${name}`);
    }
    for (const name of files) {
        const file = path.join(directory, name);
        if (sha256(file) !== manifest.files[name]) throw new Error(`MySQL bundle is missing or changed: ${name}`);
        if (target.startsWith('linux-') && (name === 'mysql' || name === 'mysqldump' || name.startsWith('lib/'))) {
            assertBinaryTarget(file, target);
            const glibc = [...fs.readFileSync(file).toString('latin1').matchAll(/GLIBC_2\.(\d+)/g)]
                .map(match => Number(match[1]));
            if (glibc.some(minor => minor > Number(manifest.runtime.minLibc.split('.')[1]))) {
                throw new Error(`Declared glibc minimum is lower than binary requirements: ${name}`);
            }
        }
    }
    for (const name of target.startsWith('windows-') ? ['mysql.exe', 'mysqldump.exe'] : ['mysql', 'mysqldump']) {
        assertBinaryTarget(path.join(directory, name), target);
    }
    return directory;
}

function verifyMysqlTools(projectDirectory, target) {
    parseTarget(target);
    const directory = path.join(projectDirectory, bundleRoot, target);
    if (!fs.existsSync(path.join(directory, 'manifest.json'))) {
        throw new Error(`Missing MySQL tools bundle for ${target}: ${directory}. Supply a validated target bundle before packaging.`);
    }
    return verifyDirectory(directory, target);
}

function bundleMysqlTools(source, target, project = path.resolve(__dirname, '..')) {
    parseTarget(target);
    const windows = target.startsWith('windows-');
    let runtime;
    if (windows && process.platform === 'win32') {
        const version = execFileSync(path.join(source, 'bin/mysqldump.exe'), ['--no-defaults', '--version'], {encoding: 'utf8'});
        runtime = {clientFamily: 'mysql', clientVersion: version.match(target === 'windows-ia32' ? /Distrib\s+(5\.7\.\d+)/ : /Ver\s+(8\.\d+\.\d+)/)?.[1]};
    } else {
        runtime = JSON.parse(fs.readFileSync(path.join(source, 'runtime.json'), 'utf8'));
    }
    validateRuntime(runtime, target);
    const parent = path.join(project, bundleRoot);
    fs.mkdirSync(parent, {recursive: true});
    const staging = fs.mkdtempSync(path.join(parent, '.staging-'));
    try {
        for (const name of requiredFiles(target)) {
            const original = name.endsWith('.mysql') ? path.join(source, name.replace('.mysql', '')) : path.join(source, 'bin', name);
            fs.copyFileSync(original, path.join(staging, name));
        }
        if (!windows && fs.existsSync(path.join(source, 'lib'))) {
            // 只接收已整理好的共享库目录，链接解引用后纳入完整清单。
            fs.cpSync(path.join(source, 'lib'), path.join(staging, 'lib'), {recursive: true, dereference: true});
        }
        if (fs.existsSync(path.join(source, 'licenses'))) {
            fs.cpSync(path.join(source, 'licenses'), path.join(staging, 'licenses'), {recursive: true, dereference: true});
        }
        if (!windows) {
            fs.chmodSync(path.join(staging, 'mysql'), 0o755);
            fs.chmodSync(path.join(staging, 'mysqldump'), 0o755);
        }
        const files = Object.fromEntries(inventory(staging).map(name => [name, sha256(path.join(staging, name))]));
        fs.writeFileSync(path.join(staging, 'manifest.json'), JSON.stringify({schemaVersion: 2, target, runtime, files}, null, 2) + '\n');
        verifyDirectory(staging, target);
        const destination = path.join(parent, target);
        if (fs.existsSync(destination)) throw new Error(`Target bundle already exists: ${destination}. Archive it before replacing.`);
        fs.renameSync(staging, destination);
        return destination;
    } finally {
        fs.rmSync(staging, {recursive: true, force: true});
    }
}

if (require.main === module) {
    const source = process.argv[2];
    if (!source) throw new Error('Usage: node scripts/bundle-mysql-tools.cjs <prepared MySQL directory> <os-architecture>');
    const target = process.argv[3] || mysqlTarget(process.platform, process.arch);
    console.log(`Bundled and verified MySQL client tools: ${bundleMysqlTools(source, target)}`);
}

module.exports = {verifyMysqlTools, assertBinaryTarget, bundleMysqlTools};
