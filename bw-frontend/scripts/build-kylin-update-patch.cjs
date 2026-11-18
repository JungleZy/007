const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const {execFileSync} = require('node:child_process');
const asar = require('asar');
const {verifyDependencyClosure} = require('./verify-packaged-runtime.cjs');

const project = path.resolve(__dirname, '..');
const output = path.join(project, 'out/kylin-v10-arm64');
const original = path.join(output, 'linux-arm64-unpacked/resources/app.asar');
const patchDirectory = path.join(output, 'kylin-update-patch');
const archive = path.join(patchDirectory, 'payload/app.asar');
const zip = path.join(output, '麒麟ARM64-3.1.2全量更新补丁.zip');

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
        const script = fs.readFileSync(path.join(project, 'support/kylin-install/repair-webgl.sh'), 'utf8')
            .replaceAll('@OLD_HASH@', oldHash)
            .replaceAll('@FIXED_HASH@', fixedHash)
            .replaceAll('WEBGL_PATCH', 'UPDATE_PATCH')
            .replaceAll('before-webgl', 'before-update')
            .replaceAll('.webgl.', '.update.')
            .replace('WEBGL_PATCH_OK', 'UPDATE_PATCH_OK')
            .replace('WEBGL_PATCH_ALREADY_INSTALLED', 'UPDATE_PATCH_ALREADY_INSTALLED');
        fs.writeFileSync(path.join(patchDirectory, 'repair-update.sh'), script.replace(/\r\n/g, '\n'));
        fs.writeFileSync(path.join(patchDirectory, '使用说明.txt'), '先正常退出软件，等待数据库退出。完整解压补丁，在解压目录打开终端：\nsudo bash ./repair-update.sh\n出现 UPDATE_PATCH_OK 或 UPDATE_PATCH_ALREADY_INSTALLED 后，以普通用户启动：\nnip-traffic-system-kylin-arm64\n原程序自动备份为 resources/app.asar.before-update，不修改数据库、账号和用户数据。\n');
        fs.writeFileSync(path.join(patchDirectory, 'expected-release.json'), JSON.stringify({
            release: 'kylin-arm64-update',
            fromArchiveSha256: oldHash,
            archiveSha256: fixedHash,
            sourceCommit: execFileSync('git', ['rev-parse', 'HEAD'], {cwd: project, encoding: 'utf8'}).trim()
        }, null, 2));

        const report = {
            file: path.basename(zip),
            bytes: 0,
            sha256: '',
            acceptedArchive: oldHash,
            patchedArchive: fixedHash,
            sourceCommit: execFileSync('git', ['rev-parse', 'HEAD'], {cwd: project, encoding: 'utf8'}).trim(),
            scope: 'resources/app.asar; ARM64 Electron, Java, MySQL, database and user data are untouched'
        };
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
        report.bytes = fs.statSync(zip).size;
        report.sha256 = digest(zip);
        fs.writeFileSync(path.join(output, 'kylin-update-patch-report.json'), JSON.stringify(report, null, 2));
        console.log(JSON.stringify(report, null, 2));
    } finally {
        fs.rmSync(stage, {recursive: true, force: true});
    }
}

if (require.main === module) build().catch(error => { console.error(error.stack); process.exitCode = 1; });
