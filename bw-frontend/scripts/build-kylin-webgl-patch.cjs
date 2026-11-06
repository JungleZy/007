const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const asar = require('asar');
const {execFileSync} = require('node:child_process');
const {verifyDependencyClosure} = require('./verify-packaged-runtime.cjs');
const project = path.resolve(__dirname, '..');
const digest = file => crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex');

async function build() {
    const output = path.join(project, 'out/kylin-v10-arm64');
    const base = path.join(output, 'linux-arm64-unpacked/resources/app.asar');
    const main = asar.extractFile(base, 'main.js').toString();
    if (!main.includes("appendSwitch('disable-software-rasterizer')")) throw new Error('Unexpected original graphics configuration');
    for (const file of ['libEGL.so', 'libGLESv2.so', 'libvk_swiftshader.so']) {
        if (!fs.existsSync(path.join(output, 'linux-arm64-unpacked', file))) throw new Error('Missing software renderer: ' + file);
    }
    const stage = fs.mkdtempSync(path.join(output, '.kylin-webgl-'));
    try {
        const app = path.join(stage, 'app');
        const patch = path.join(output, 'kylin-webgl-patch');
        fs.mkdirSync(path.join(patch, 'payload'), {recursive: true});
        asar.extractAll(base, app);
        for (const file of ['main.js', 'electron/core/graphics.js']) fs.copyFileSync(path.join(project, file), path.join(app, file));
        const archive = path.join(patch, 'payload/app.asar');
        await asar.createPackage(app, archive);
        verifyDependencyClosure(archive);
        for (const entry of asar.listPackage(base)) {
            const relative = entry.replace(/^[/\\]+/, '');
            if (relative === 'main.js' || asar.statFile(base, relative).files) continue;
            if (!asar.extractFile(base, relative).equals(asar.extractFile(archive, relative))) throw new Error('Unexpected change: ' + relative);
        }
        const oldHash = digest(base), fixedHash = digest(archive);
        const script = fs.readFileSync(path.join(project, 'support/kylin-install/repair-webgl.sh'), 'utf8')
            .replace('@OLD_HASH@', oldHash).replace('@FIXED_HASH@', fixedHash).replace(/\r\n/g, '\n');
        fs.writeFileSync(path.join(patch, 'repair-webgl.sh'), script);
        fs.writeFileSync(path.join(patch, '使用说明.txt'), '先正常退出软件，等待数据库退出。完整解压补丁，在解压目录打开终端：\nsudo bash ./repair-webgl.sh\n出现 WEBGL_PATCH_OK 或 WEBGL_PATCH_ALREADY_INSTALLED 后，以普通用户启动：\nnip-traffic-system-kylin-arm64\n原程序自动备份为 resources/app.asar.before-webgl，不修改数据库。\n如仍有图形问题，可在终端尝试 NIP_GRAPHICS_MODE=auto nip-traffic-system-kylin-arm64，并反馈完整输出。\n');
        const zip = path.join(output, '麒麟ARM64-WebGL修复补丁.zip');
        for (let attempt = 0; ; attempt++) {
            fs.rmSync(zip, {force: true});
            try {
                execFileSync(require('7zip-bin').path7za, ['a', '-tzip', '-mx=1', zip, ...fs.readdirSync(patch)], {cwd: patch, stdio: 'pipe'});
                break;
            } catch (error) {
                if (attempt >= 3) throw error;
                await new Promise(resolve => setTimeout(resolve, 1000));
            }
        }
        execFileSync(require('7zip-bin').path7za, ['t', zip], {stdio: 'pipe'});
        const report = {file: path.basename(zip), bytes: fs.statSync(zip).size, sha256: digest(zip), acceptedArchive: oldHash,
            patchedArchive: fixedHash, changedFiles: ['main.js', 'electron/core/graphics.js'],
            validation: 'Dependency closure and unchanged file comparison passed; Kylin ARM64 WebGL runtime pending'};
        fs.writeFileSync(path.join(output, 'kylin-webgl-patch-report.json'), JSON.stringify(report, null, 2));
        console.log(JSON.stringify(report, null, 2));
    } finally { fs.rmSync(stage, {recursive: true, force: true}); }
}
build().catch(error => {console.error(error); process.exitCode = 1;});
