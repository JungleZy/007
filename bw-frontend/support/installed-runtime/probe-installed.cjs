const fs = require('node:fs');
const path = require('node:path');
const Module = require('node:module');
const crypto = require('node:crypto');
const rawFs = process.versions.electron ? require('original-fs') : fs;
const archive = path.resolve(process.argv[2]);
const output = path.resolve(process.argv[3]);
const report = {electron: process.versions.electron, node: process.versions.node, executable: process.execPath};
try {
    const expected = JSON.parse(fs.readFileSync(path.join(__dirname, 'expected-release.json'), 'utf8'));
    report.expectedRelease = expected.release;
    report.archive = archive;
    report.archiveSha256 = crypto.createHash('sha256').update(rawFs.readFileSync(archive)).digest('hex');
    report.matchesFix1 = report.archiveSha256 === expected.archiveSha256;
    report.archiveModified = rawFs.statSync(archive).mtime.toISOString();
    const load = Module.createRequire(path.join(archive, 'package.json'));
    const resolve = Module._resolveFilename;
    Module._resolveFilename = function (request, ...args) {
        const resolved = resolve.call(this, request, ...args);
        if (!Module.isBuiltin(request) && !resolved.startsWith(archive + path.sep)) throw new Error(`Dependency outside archive: ${request}: ${resolved}`);
        return resolved;
    };
    report.sealdAtCorrectPath = fs.existsSync(path.join(archive, 'node_modules/@seald-io/nedb/package.json'));
    report.sealdAtWrongNestedPath = fs.existsSync(path.join(archive, 'node_modules/node_modules/@seald-io/nedb/package.json'));
    report.version = load('./package.json').version;
    load('./electron/core/index.js');
    for (const dependency of Object.keys(load('./package.json').dependencies || {})) load(dependency);
    report.success = true;
} catch (error) { report.success = false; report.error = error.stack; process.exitCode = 1; }
fs.writeFileSync(output, JSON.stringify(report, null, 2));
if (!process.argv.includes('--quiet')) console.log(JSON.stringify(report, null, 2));
console.log(report.success ? 'INSTALLED_RUNTIME_OK' : 'INSTALLED_RUNTIME_FAILED');
