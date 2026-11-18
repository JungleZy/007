const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const {execFileSync} = require('node:child_process');
const asar = require('asar');
const {verifyDependencyClosure} = require('./verify-packaged-runtime.cjs');

const project = path.resolve(__dirname, '..');
const output = path.join(project, 'out/win7-ia32');
const original = path.join(output, 'win-ia32-unpacked/resources/app.asar');
const patchDirectory = path.join(output, 'win7-update-patch');
const archive = path.join(patchDirectory, 'payload/app.asar');
const zip = path.join(output, 'Win7-HJ-3.1.3全量更新补丁.zip');

const digest = file => crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex');
const copy = (source, target) => fs.cpSync(source, target, {recursive: true, force: true});

async function build() {
    if (!fs.existsSync(original)) throw new Error(`旧版 app.asar 不存在：${original}`);
    if (!fs.existsSync(path.join(project, 'public/dist'))) throw new Error('缺少最新前端 public/dist，请先执行 npm run build');

    const oldHash = digest(original);
    const stage = fs.mkdtempSync(path.join(output, '.update-patch-'));
    try {
        const app = path.join(stage, 'app');
        fs.rmSync(patchDirectory, {recursive: true, force: true});
        fs.mkdirSync(path.dirname(archive), {recursive: true});
        asar.extractAll(original, app);

        copy(path.join(project, 'main.js'), path.join(app, 'main.js'));
        copy(path.join(project, 'electron'), path.join(app, 'electron'));
        copy(path.join(project, 'public/dist'), path.join(app, 'public/dist'));
        copy(path.join(project, 'package.json'), path.join(app, 'package.json'));

        await asar.createPackage(app, archive);
        verifyDependencyClosure(archive);
        const fixedHash = digest(archive);

        let repair = fs.readFileSync(path.join(project, 'support/installed-runtime/repair-installed.ps1'), 'utf8')
            .replace(/\$fixedHash = '[a-f0-9]+'/g, `$fixedHash = '${fixedHash}'`)
            .replace(/\$oldHash = '[a-f0-9]+'/g, `$oldHash = '${oldHash}'`)
            .replaceAll('fix1', 'update');
        fs.writeFileSync(path.join(patchDirectory, 'repair-installed.ps1'), repair);
        for (const file of ['repair-installed.cmd', 'check-installed.cmd', 'probe-installed.cjs']) {
            fs.copyFileSync(path.join(project, 'support/installed-runtime', file), path.join(patchDirectory, file));
        }
        fs.writeFileSync(path.join(patchDirectory, 'expected-release.json'), JSON.stringify({
            release: 'win7-hj-3.1.3-update',
            fromArchiveSha256: oldHash,
            archiveSha256: fixedHash,
            sourceCommit: execFileSync('git', ['rev-parse', 'HEAD'], {cwd: project, encoding: 'utf8'}).trim()
        }, null, 2));
        fs.writeFileSync(path.join(patchDirectory, '使用说明.txt'), '\ufeff适用于 Win7 HJ 3.1.3 旧完整包。\r\n1. 退出软件及报错弹窗，完整解压本补丁。\r\n2. 双击 repair-installed.cmd，出现 REPAIR_OK 后重新启动软件。\r\n3. 若安装在其他目录，将软件 EXE 拖到 repair-installed.cmd 上运行。\r\n补丁只替换 resources\\app.asar，原文件自动备份，不修改数据库、账号和用户数据。\r\n');

        const executable = path.join(output, 'win-ia32-unpacked/多功能报务综合训练系统.exe');
        const result = execFileSync(executable, [path.join(project, 'scripts/packaged-runtime-probe.cjs'), archive], {
            encoding: 'utf8',
            timeout: 30000,
            env: {...process.env, ELECTRON_RUN_AS_NODE: '1', NODE_OPTIONS: '', NODE_PATH: ''}
        });
        if (!result.includes('PACKAGED_RUNTIME_OK')) throw new Error('更新包运行时探测失败');

        const seven = require('7zip-bin').path7za;
        for (let attempt = 0; ; attempt++) {
            fs.rmSync(zip, {force: true});
            try {
                execFileSync(seven, ['a', '-tzip', '-mx=5', zip, ...fs.readdirSync(patchDirectory)], {
                    cwd: patchDirectory,
                    stdio: 'pipe'
                });
                break;
            } catch (error) {
                if (attempt >= 3) throw error;
                await new Promise(resolve => setTimeout(resolve, 1000));
            }
        }
        execFileSync(seven, ['t', zip], {stdio: 'pipe'});
        const report = {
            file: path.basename(zip),
            bytes: fs.statSync(zip).size,
            sha256: digest(zip),
            acceptedArchive: oldHash,
            patchedArchive: fixedHash,
            sourceCommit: execFileSync('git', ['rev-parse', 'HEAD'], {cwd: project, encoding: 'utf8'}).trim(),
            scope: 'resources/app.asar; database and user data are untouched'
        };
        fs.writeFileSync(path.join(output, 'win7-update-patch-report.json'), JSON.stringify(report, null, 2));
        console.log(JSON.stringify(report, null, 2));
    } finally {
        fs.rmSync(stage, {recursive: true, force: true});
    }
}

if (require.main === module) build().catch(error => { console.error(error.stack); process.exitCode = 1; });
