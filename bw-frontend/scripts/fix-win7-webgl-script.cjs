const fs = require('node:fs');
const path = require('node:path');
const {execFileSync} = require('node:child_process');
const output = path.resolve(__dirname, '../out/win7-ia32');
const seven = require('7zip-bin').path7za;
const source = path.join(output, 'Win7-WebGL修复补丁.zip');
const stage = fs.mkdtempSync(path.join(output, '.webgl-script-'));
try {
    execFileSync(seven, ['x', '-y', '-o' + stage, source, 'repair-installed.ps1'], {stdio: 'pipe'});
    const file = path.join(stage, 'repair-installed.ps1');
    const old = fs.readFileSync(file, 'utf8');
    const before = '$stream.Dispose(); $algorithm.Dispose()';
    if (!old.includes(before)) throw new Error('Unexpected original repair script');
    const fixed = old.replace(before, '$stream.Close(); $algorithm.Clear()');
    fs.writeFileSync(file, fixed);
    fs.writeFileSync(path.join(stage, '使用说明.txt'), '退出软件。把本压缩包中的 repair-installed.ps1 复制到之前完整解压的Win7-WebGL补丁目录，覆盖同名文件。\r\n保留原目录中的payload、repair-installed.cmd和其他文件，再双击repair-installed.cmd。\r\n出现REPAIR_OK后重新启动软件。不需要重新复制payload，也不修改业务数据库。\r\n');
    const small = path.join(output, 'Win7-WebGL补丁脚本兼容修正.zip');
    fs.rmSync(small, {force: true});
    execFileSync(seven, ['a', '-tzip', small, 'repair-installed.ps1', '使用说明.txt'], {cwd: stage, stdio: 'pipe'});
    execFileSync(seven, ['t', small], {stdio: 'pipe'});
    const full = path.join(output, 'Win7-WebGL修复补丁-pscompat.zip');
    fs.copyFileSync(source, full);
    execFileSync(seven, ['u', '-tzip', full, 'repair-installed.ps1'], {cwd: stage, stdio: 'pipe'});
    execFileSync(seven, ['t', full], {stdio: 'pipe'});
    // Run the actual fixed hash helper against known content using PowerShell.
    const start = fixed.indexOf('function FileHash(');
    const end = fixed.indexOf('\ntry {', start);
    const test = fixed.slice(start, end) + '\n$actual = FileHash (Join-Path $PSScriptRoot "hash-input.txt")\nif ($actual -ne "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad") { throw "Hash mismatch" }\nWrite-Output "HASH_HELPER_OK"\n';
    fs.writeFileSync(path.join(stage, 'hash-input.txt'), 'abc');
    fs.writeFileSync(path.join(stage, 'test-hash.ps1'), test);
    console.log(execFileSync('powershell.exe', ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', path.join(stage, 'test-hash.ps1')], {encoding: 'utf8'}));
    const crypto = require('node:crypto');
    const hash = file => crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex');
    const report = {small: {file: path.basename(small), bytes: fs.statSync(small).size, sha256: hash(small)},
        full: {file: path.basename(full), bytes: fs.statSync(full).size, sha256: hash(full)},
        scope: 'Only repair-installed.ps1 changed; payload and accepted hashes preserved. Hash helper tested on build-host PowerShell; Win7 rerun pending.'};
    fs.writeFileSync(path.join(output, 'webgl-pscompat-report.json'), JSON.stringify(report, null, 2));
    console.log(JSON.stringify(report, null, 2));
} finally {fs.rmSync(stage, {recursive: true, force: true});}
