const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const asar = require('asar');
const {execFileSync} = require('node:child_process');
const {verifyDependencyClosure} = require('./verify-packaged-runtime.cjs');
const project = path.resolve(__dirname, '..');
const digest = file => crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex');

async function build() {
    const output = path.join(project, 'out/win7-ia32');
    const base = path.join(output, 'win-ia32-unpacked/resources/app.asar');
    verifyDependencyClosure(base);
    const oldMain = asar.extractFile(base, 'main.js').toString('utf8');
    if (!oldMain.includes("appendSwitch('disable-software-rasterizer')")) throw new Error('Source package is not the known WebGL-disabled build');
    const stage = fs.mkdtempSync(path.join(output, '.webgl-patch-'));
    try {
        const app = path.join(stage, 'app');
        const patch = path.join(stage, 'patch');
        asar.extractAll(base, app);
        fs.copyFileSync(path.join(project, 'main.js'), path.join(app, 'main.js'));
        fs.copyFileSync(path.join(project, 'electron/core/graphics.js'), path.join(app, 'electron/core/graphics.js'));
        fs.mkdirSync(path.join(patch, 'payload'), {recursive: true});
        const archive = path.join(patch, 'payload/app.asar');
        await asar.createPackage(app, archive);
        verifyDependencyClosure(archive);
        // 除入口和新增图形配置外，保持用户已经验证的compat2应用文件逐字节一致。
        for (const entry of asar.listPackage(base)) {
            const relative = entry.replace(/^[/\\]+/, '');
            if (relative === 'main.js' || asar.statFile(base, relative).files) continue;
            if (!asar.extractFile(base, relative).equals(asar.extractFile(archive, relative))) throw new Error('Unexpected patch change: ' + relative);
        }
        const oldHash = digest(base), fixedHash = digest(archive);
        const support = path.join(project, 'support/installed-runtime');
        const defaultDirectory = "if (!$InstallDir) { $InstallDir = Join-Path $env:LOCALAPPDATA 'Programs\\nip-traffic-system' }";
        let repair = fs.readFileSync(path.join(support, 'repair-installed.ps1'), 'utf8');
        if (!repair.includes(defaultDirectory)) throw new Error('Repair template has changed');
        repair = repair.replace(/\$fixedHash = '[a-f0-9]+'/g, `$fixedHash = '${fixedHash}'`)
            .replace(/\$oldHash = '[a-f0-9]+'/g, `$oldHash = '${oldHash}'`)
            .replace(defaultDirectory, "if (!$InstallDir) { $InstallDir = Join-Path $env:LOCALAPPDATA 'Programs\\nip-traffic-system-win7'; if (!(Test-Path -LiteralPath (Join-Path $InstallDir 'resources\\app.asar'))) { $InstallDir = Join-Path $env:LOCALAPPDATA 'Programs\\nip-traffic-system' } }")
            .replaceAll('fix1', 'webgl');
        fs.writeFileSync(path.join(patch, 'repair-installed.ps1'), repair);
        for (const file of ['repair-installed.cmd', 'check-installed.cmd', 'probe-installed.cjs']) fs.copyFileSync(path.join(support, file), path.join(patch, file));
        fs.writeFileSync(path.join(patch, 'expected-release.json'), JSON.stringify({release: 'win7-webgl', productName: require('../package.json').productName, archiveSha256: fixedHash}, null, 2));
        fs.writeFileSync(path.join(patch, '使用说明.txt'), '\ufeff请先退出软件及报错弹窗，解压本补丁后双击 repair-installed.cmd。\r\n出现 REPAIR_OK 后重新启动软件。\r\n若安装在其他目录，将软件EXE拖到 repair-installed.cmd 上运行。\r\n仅替换应用文件，原文件自动备份，不修改数据库；失败时提供 repair-result.txt。\r\n');
        const result = execFileSync(path.join(output, 'win-ia32-unpacked/多功能报务综合训练系统.exe'),
            [path.join(project, 'scripts/packaged-runtime-probe.cjs'), archive],
            {encoding: 'utf8', timeout: 30000, env: {...process.env, ELECTRON_RUN_AS_NODE: '1', NODE_OPTIONS: '', NODE_PATH: ''}});
        if (!result.includes('PACKAGED_RUNTIME_OK')) throw new Error('Patched runtime dependency probe failed');
        const zip = path.join(output, 'Win7-WebGL修复补丁.zip');
        for (let attempt = 0; ; attempt++) {
            fs.rmSync(zip, {force: true});
            try {
                execFileSync(require('7zip-bin').path7za, ['a', '-tzip', '-mx=5', zip, ...fs.readdirSync(patch)], {cwd: patch, stdio: 'pipe'});
                break;
            } catch (error) {
                // Windows在Electron刚退出后可能短暂保留归档扫描句柄，不能交付跳过文件的ZIP。
                if (attempt >= 3) throw error;
                await new Promise(resolve => setTimeout(resolve, 1000));
            }
        }
        execFileSync(require('7zip-bin').path7za, ['t', zip], {stdio: 'pipe'});
        const report = {file: path.basename(zip), bytes: fs.statSync(zip).size, sha256: digest(zip), acceptedArchive: oldHash, patchedArchive: fixedHash, changedFiles: ['main.js', 'electron/core/graphics.js']};
        fs.writeFileSync(path.join(output, 'webgl-patch-report.json'), JSON.stringify(report, null, 2));
        console.log(JSON.stringify(report, null, 2));
    } finally { fs.rmSync(stage, {recursive: true, force: true}); }
}

if (require.main === module) build().catch(error => { console.error(error.stack); process.exitCode = 1; });
