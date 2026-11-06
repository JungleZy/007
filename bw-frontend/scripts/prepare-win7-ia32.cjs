const fs = require('node:fs');
const path = require('node:path');
const {execFileSync} = require('node:child_process');
const components = require('./windows-ia32-components.json');
const {downloadArchive} = require('./prepare-mysql-tools.cjs');
const {bundleMysqlTools, verifyMysqlTools, assertBinaryTarget} = require('./bundle-mysql-tools.cjs');
const {prepareIntegratedRuntime, verifyIntegratedRuntime} = require('./prepare-integrated-runtime.cjs');
const project = path.resolve(__dirname, '..');

async function prepare() {
    if (process.platform !== 'win32') throw new Error('Prepare the Windows ia32 package on a Windows build host');
    const cache = process.env.NIP_MYSQL_CACHE_DIR || path.join(project, '.cache/win7-ia32-downloads');
    fs.mkdirSync(cache, {recursive: true});
    fs.accessSync(path.resolve(project, '../backend/target/quarkus-app-win32/quarkus-run.jar'));
    const seven = require('7zip-bin').path7za;
    const extraction = fs.mkdtempSync(path.join(cache, 'extract-'));
    try {
        const paths = {};
        for (const [name, source] of Object.entries(components).filter(([name]) => name !== 'target')) {
            const archive = await downloadArchive(source, cache);
            const destination = path.join(extraction, name);
            fs.mkdirSync(destination);
            execFileSync(seven, ['x', archive, '-o' + destination, '-y'], {stdio: 'pipe'});
            paths[name] = name === 'electron' ? destination : path.join(destination, fs.readdirSync(destination)[0]);
        }
        assertBinaryTarget(path.join(paths.electron, 'electron.exe'), 'windows-ia32');
        const electronDirectory = path.join(project, '.cache/electron22-ia32');
        fs.mkdirSync(path.dirname(electronDirectory), {recursive: true});
        if (!fs.existsSync(electronDirectory)) fs.cpSync(paths.electron, electronDirectory, {recursive: true});
        // 确认现有 Electron 缓存也与下载来源一致，不能只按目录名判断版本。
        for (const name of ['electron.exe', 'version']) {
            if (!fs.readFileSync(path.join(electronDirectory, name)).equals(fs.readFileSync(path.join(paths.electron, name)))) {
                throw new Error('Cached Electron differs from the pinned Windows ia32 component: ' + name);
            }
        }
        if (fs.existsSync(path.join(project, 'bin/file/tools/mysql/windows-ia32'))) verifyMysqlTools(project, 'windows-ia32');
        else bundleMysqlTools(paths.mysql, 'windows-ia32', project);
        if (fs.existsSync(path.join(project, 'bin/integrated/windows-ia32'))) verifyIntegratedRuntime(project, 'windows-ia32');
        else prepareIntegratedRuntime(paths.mysql, paths.java, 'windows-ia32');
        execFileSync(process.execPath, [path.join(__dirname, 'prepare-ch340.cjs')], {stdio: 'inherit'});
        console.log('Windows ia32 components and CH340 driver ready. Build with: npm run build-e-win7-32 -- --dir');
    } finally {
        fs.rmSync(extraction, {recursive: true, force: true});
    }
}

if (require.main === module) prepare().catch(error => { console.error(error.message); process.exitCode = 1; });
module.exports = {prepare};
