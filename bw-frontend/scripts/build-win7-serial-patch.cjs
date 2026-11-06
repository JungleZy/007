const fs = require('node:fs'), path = require('node:path'), crypto = require('node:crypto');
const {execFileSync} = require('node:child_process');
const asar = require('asar');
const {verifyDependencyClosure} = require('./verify-packaged-runtime.cjs');
const project = path.resolve(__dirname, '..');
const digest = file => crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex');
async function build() {
    const output = path.join(project, 'out/win7-ia32');
    const original = path.join(output, 'win-ia32-unpacked/resources/app.asar');
    const stage = fs.mkdtempSync(path.join(output, '.serial-patch-'));
    const changed = ['electron/serial/nativeSerialPort.js', 'electron/serial/index.js'];
    try {
        const app = path.join(stage, 'app'), patch = path.join(output, 'win7-serial-patch');
        asar.extractAll(original, app);
        if (!asar.extractFile(original, path.normalize(changed[0])).toString().includes('Get-WmiObject')) throw new Error('Unexpected base release');
        for (const file of changed) fs.copyFileSync(path.join(project, file), path.join(app, file));
        fs.mkdirSync(path.join(patch, 'payload'), {recursive: true});
        const archive = path.join(patch, 'payload/app.asar');
        await asar.createPackage(app, archive);
        verifyDependencyClosure(archive);
        for (const item of asar.listPackage(original)) {
            const name = item.replace(/^[/\\]+/, '');
            if (asar.statFile(original, name).files || changed.includes(name.replace(/\\/g, '/'))) continue;
            if (!asar.extractFile(original, name).equals(asar.extractFile(archive, name))) throw new Error('Unexpected change: ' + name);
        }
        const support = path.join(project, 'support/installed-runtime');
        const oldHash = digest(original), fixedHash = digest(archive);
        let repair = fs.readFileSync(path.join(support, 'repair-installed.ps1'), 'utf8')
            .replace(/\$fixedHash = '[a-f0-9]+'/g, `$fixedHash = '${fixedHash}'`)
            .replace(/\$oldHash = '[a-f0-9]+'/g, `$oldHash = '${oldHash}'`)
            .replace("'Programs\\nip-traffic-system'", "'Programs\\nip-traffic-system-win7'").replaceAll('fix1', 'serial');
        fs.writeFileSync(path.join(patch, 'repair-installed.ps1'), repair);
        for (const file of ['repair-installed.cmd', 'probe-installed.cjs', 'probe-serial.cjs']) fs.copyFileSync(path.join(support, file), path.join(patch, file));
        const check = fs.readFileSync(path.join(support, 'check-installed.cmd'), 'utf8')
            .replaceAll('Programs\\nip-traffic-system', 'Programs\\nip-traffic-system-win7')
            .replaceAll('probe-installed.cjs', 'probe-serial.cjs').replaceAll('installed-runtime-report.json', 'serial-report.json');
        fs.writeFileSync(path.join(patch, 'check-serial.cmd'), check);
        fs.writeFileSync(path.join(patch, 'expected-release.json'), JSON.stringify({release:'win7-hj-3.1.2-serial',archiveSha256:fixedHash}));
        fs.writeFileSync(path.join(patch, '使用说明.txt'), '适用于HJ Win7 3.1.2完整安装包。\r\n1. 退出软件，完整解压补丁，双击repair-installed.cmd。\r\n2. 出现REPAIR_OK后启动软件，刷新真实串口列表，选择设备管理器中CH340对应的COM号。其他COM口可正常共存，不要卸载其他串口。\r\n3. 如仍无串口或无法连接，双击check-serial.cmd，把serial-report.json和设备管理器中CH340的COM号、设备状态发回。\r\n安装在其他位置时，把软件EXE拖到对应CMD上。补丁会备份原程序，不修改数据库。\r\n');
        const exe = path.join(output, 'win-ia32-unpacked/多功能报务综合训练系统.exe');
        const result = execFileSync(exe, [path.join(project,'scripts/packaged-runtime-probe.cjs'),archive],
            {env:{...process.env,ELECTRON_RUN_AS_NODE:'1',NODE_OPTIONS:'',NODE_PATH:''},encoding:'utf8',timeout:30000});
        if(!result.includes('PACKAGED_RUNTIME_OK'))throw new Error('Runtime probe failed');
        const zip = path.join(output, 'Win7-HJ-3.1.2串口修复补丁.zip'), seven = require('7zip-bin').path7za;
        for(let attempt=0;;attempt++) {
            fs.rmSync(zip,{force:true});
            try {execFileSync(seven,['a','-tzip','-mx=1',zip,...fs.readdirSync(patch)],{cwd:patch,stdio:'pipe'});break;}
            catch(error){if(attempt>=3)throw error;await new Promise(resolve=>setTimeout(resolve,1000));}
        }
        execFileSync(seven,['t',zip],{stdio:'pipe'});
        const report={file:path.basename(zip),bytes:fs.statSync(zip).size,sha256:digest(zip),acceptedArchive:oldHash,patchedArchive:fixedHash,changedFiles:changed};
        fs.writeFileSync(path.join(output,'serial-patch-report.json'),JSON.stringify(report,null,2));
        console.log(JSON.stringify(report,null,2));
    } finally {fs.rmSync(stage,{recursive:true,force:true});}
}
build().catch(error=>{console.error(error);process.exitCode=1;});
