const fs = require('node:fs');
const path = require('node:path');
const {execFileSync} = require('node:child_process');
const {verifyManifest} = require('./artifact-manifest.cjs');
const {verifyMysqlTools} = require('./bundle-mysql-tools.cjs');
const {mysqlTarget} = require('../electron/shared/mysql-platforms');
const {Arch} = require('builder-util');
const {verifyIntegratedRuntime} = require('./prepare-integrated-runtime.cjs');
const {assertDependencyLayout} = require('./verify-packaged-runtime.cjs');

const projectDirectory = path.resolve(__dirname, '..');
const frontendDirectory = path.join(projectDirectory, 'frontend');
const sourceDirectory = path.join(frontendDirectory, 'dist');
const targetDirectory = path.join(projectDirectory, 'public', 'dist');
const preparedPackagers = new WeakSet();

function buildFrontend() {
    const npmCli = process.env.npm_execpath;
    const options = {cwd: frontendDirectory, stdio: 'inherit'};
    if (npmCli && /\.(?:c?js)$/i.test(npmCli)) {
        execFileSync(process.execPath, [npmCli, 'run', 'build'], options);
    } else {
        const windows = process.platform === 'win32';
        execFileSync(windows ? 'npm.cmd' : 'npm', ['run', 'build'], {
            ...options,
            shell: windows
        });
    }
}

module.exports = function prepareDesktop(context) {
    assertDependencyLayout(projectDirectory);
    // 必须取打包目标架构，不能取构建机的 process.arch（交叉打包时可能不同）。
    const arch = typeof context.arch === 'number' ? Arch[context.arch] : context.arch;
    if (context.electronPlatformName === 'win32' && arch === 'ia32'
        && context.packager.config.electronVersion !== '22.3.27') {
        throw new Error('Windows ia32 requires the validated Electron 22.3.27 build configuration: npm run build-e-win7-32');
    }
    if (context.electronPlatformName === 'win32' && arch === 'ia32') {
        const driver = path.join(projectDirectory, '.cache/ch340-release');
        const manifest = JSON.parse(fs.readFileSync(path.join(driver, 'manifest.json'), 'utf8'));
        for (const required of ['SETUP.EXE', 'CH341SER.INF', 'CH341SER.CAT', 'CH341SER.sys', 'CH341S64.sys', 'install-driver.cmd']) {
            if (!manifest.files[required]) throw new Error('Missing CH340 driver asset: ' + required);
        }
        for (const [name, expected] of Object.entries(manifest.files)) {
            const actual = require('node:crypto').createHash('sha256').update(fs.readFileSync(path.join(driver, name))).digest('hex');
            if (actual !== expected) throw new Error('CH340 driver asset changed; rerun scripts/prepare-ch340.cjs: ' + name);
        }
    }
    verifyMysqlTools(projectDirectory, mysqlTarget(context.electronPlatformName, arch));
    verifyIntegratedRuntime(projectDirectory, mysqlTarget(context.electronPlatformName, arch));
    // One frontend build per builder invocation, shared by all requested architectures.
    const packager = context.packager.info;
    if (preparedPackagers.has(packager)) return;

    try {
        fs.rmSync(targetDirectory, {recursive: true, force: true});
        fs.rmSync(sourceDirectory, {recursive: true, force: true});
        buildFrontend();
        const manifest = verifyManifest(sourceDirectory, {allowDirty: true});
        fs.cpSync(sourceDirectory, targetDirectory, {recursive: true, force: false, errorOnExist: true});
        verifyManifest(targetDirectory, {allowDirty: true});
        preparedPackagers.add(packager);
        console.log(`[desktop] Prepared frontend ${manifest.version} from ${manifest.sourceCommit}${manifest.sourceDirty ? ' (dirty source)' : ''}`);
    } catch (error) {
        fs.rmSync(targetDirectory, {recursive: true, force: true});
        throw new Error(`Desktop packaging aborted: frontend preparation failed. ${error.message}`, {cause: error});
    }
};
