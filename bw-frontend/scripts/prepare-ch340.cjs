const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const {execFileSync} = require('node:child_process');
const project = path.resolve(__dirname, '..');
const archive = path.join(project, '.cache/CH341SER.ZIP');
const expected = '59967d9ce371d0bf3df02dec0b66c8dfbf9ca576da0572ff4404148a7c381807';
if (process.platform !== 'win32') throw new Error('Prepare the signed Windows driver on a Windows build host');
fs.mkdirSync(path.dirname(archive), {recursive: true});
if (!fs.existsSync(archive)) {
    execFileSync('curl.exe', ['--fail', '--location', '--max-time', '120', '--output', archive,
        'https://www.wch-ic.com/download/file?id=5'], {stdio: 'inherit'});
}
const hash = file => crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex');
if (hash(archive) !== expected) throw new Error('CH341SER.ZIP checksum mismatch; use the verified official download');
const stage = fs.mkdtempSync(path.join(project, '.cache/ch340-stage-'));
try {
    execFileSync(require('7zip-bin').path7za, ['x', '-y', '-o' + stage, archive], {stdio: 'pipe'});
    const source = path.join(stage, 'CH341SER');
    const signatures = execFileSync('powershell.exe', ['-NoProfile', '-Command',
        "$ErrorActionPreference='Stop'; foreach ($name in @('SETUP.EXE','CH341SER.CAT','CH341SER.sys','CH341S64.sys')) { $sig=Get-AuthenticodeSignature -LiteralPath (Join-Path $env:NIP_CH340_STAGE $name); if ($sig.Status -ne 'Valid') { throw ('Invalid signature: '+$name) }; $name+': '+$sig.SignerCertificate.Subject }"],
    {env: {...process.env, NIP_CH340_STAGE: source}, encoding: 'utf8'});
    const inf = fs.readFileSync(path.join(source, 'CH341SER.INF'), 'utf8');
    if (!inf.includes('4.0.2026.02') || !inf.includes('VID_1A86&PID_7523')) throw new Error('Unexpected CH340 driver');
    for (const file of ['install-driver.cmd', 'README.txt']) fs.copyFileSync(path.join(project, 'support/ch340', file), path.join(source, file));
    const files = {};
    function walk(dir, prefix = '') {
        for (const item of fs.readdirSync(dir, {withFileTypes: true})) {
            const name = prefix + item.name;
            if (item.isDirectory()) walk(path.join(dir, item.name), name + '/');
            else files[name] = hash(path.join(dir, item.name));
        }
    }
    walk(source);
    fs.writeFileSync(path.join(source, 'manifest.json'), JSON.stringify({source: {url: 'https://www.wch-ic.com/download/file?id=5',
        sha256: expected, driverVersion: '4.0.2026.02'}, signatures, files}, null, 2));
    const destination = path.join(project, '.cache/ch340-release');
    fs.rmSync(destination, {recursive: true, force: true});
    fs.cpSync(source, destination, {recursive: true});
    console.log(signatures + 'CH340_STAGE_OK');
} finally {fs.rmSync(stage, {recursive: true, force: true});}
