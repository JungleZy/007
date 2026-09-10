const fs = require('node:fs');
const path = require('node:path');
const {execFileSync} = require('node:child_process');
const {verifyManifest} = require('./artifact-manifest.cjs');

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
