const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const {execFileSync} = require('node:child_process');
const {build, Platform, Arch} = require('electron-builder');
const {packageFiles, uninstallFileList} = require('./build-win7-compatible-installer.cjs');
const {verifyDependencyClosure} = require('./verify-packaged-runtime.cjs');
const project = path.resolve(__dirname, '..');
const output = path.join(project, 'out/releases');
const seven = require('7zip-bin').path7za;
async function main() {
    const nsis = await require('app-builder-lib/out/binDownload').getBinFromUrl('nsis', '3.0.4.1', 'VKMiizYdmNdJOWpRGz4trl4lD++BvYP2irAXpMilheUP0pc93iKlWAoP843Vlraj8YG19CVn0j+dCo/hURz9+Q==');
    const metadata = require('../package.json');
    const reports = [];
    for (const platform of ['Win7', 'Win10']) for (const style of ['HJJ', 'HJ', 'LJ']) {
        const arch = platform === 'Win7' ? 'ia32' : 'x64';
        const stem = `${metadata.productName}-${platform}-${style}-${metadata.version}-${arch}`;
        console.log('START ' + stem);
        const stage = fs.mkdtempSync(path.join(output, '.exe-stage-'));
        try {
            const payload = path.join(stage, 'app');
            execFileSync(seven, ['x', '-y', path.join(output, stem + '.zip'), '-o' + payload], {stdio: 'pipe'});
            verifyDependencyClosure(path.join(payload, 'resources/app.asar'));
            const installer = path.join(output, stem + '.exe');
            if (platform === 'Win7') {
                const uninstall = path.join(stage, 'uninstall.nsh');
                fs.writeFileSync(uninstall, '\ufeff' + uninstallFileList(packageFiles(payload)));
                execFileSync(path.join(nsis, 'Bin/makensis.exe'), ['/V2', '/WX', '/INPUTCHARSET', 'UTF8',
                    `/DPRODUCT_NAME=${metadata.productName}`, `/DAPP_EXE=${metadata.productName}.exe`, `/DAPP_VERSION=${metadata.version}`,
                    `/DINSTALLER_FILE=${installer}`, `/DPAYLOAD_ARCHIVE=${path.join(output, stem + '.zip')}`,
                    `/DEXTRACTOR_EXE=${path.join(project, 'node_modules/7zip-bin/win/ia32/7za.exe')}`,
                    `/DUNINSTALL_FILES=${uninstall}`, path.join(project, 'build/win7-compatible-installer.nsi')],
                {stdio: 'inherit', env: {...process.env, NSISDIR: nsis}});
            } else {
                await build({projectDir: project, prepackaged: payload, targets: Platform.WINDOWS.createTarget('nsis', Arch.x64),
                    config: {extends: null, directories: {output}, win: {icon: 'build/icons/icon.ico', artifactName: stem + '.exe'},
                        nsis: {oneClick: false, allowToChangeInstallationDirectory: true, deleteAppDataOnUninstall: false}}});
            }
            execFileSync(seven, ['t', installer], {stdio: 'pipe'});
            const hash = crypto.createHash('sha256');
            for await (const chunk of fs.createReadStream(installer)) hash.update(chunk);
            reports.push({file: path.basename(installer), bytes: fs.statSync(installer).size, sha256: hash.digest('hex')});
            fs.writeFileSync(path.join(output, 'exe-release-report.json'), JSON.stringify(reports, null, 2));
            console.log('DONE ' + stem);
        } finally { fs.rmSync(stage, {recursive: true, force: true}); }
    }
}
main().catch(error => { console.error(error); process.exitCode = 1; });
