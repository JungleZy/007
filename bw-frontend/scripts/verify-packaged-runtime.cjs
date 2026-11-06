const fs = require('node:fs');
const path = require('node:path');
const asar = require('asar');
const {execFile} = require('node:child_process');
const {promisify} = require('node:util');
const {Arch} = require('builder-util');

function assertDependencyLayout(projectDirectory) {
    if (fs.existsSync(path.join(projectDirectory, 'node_modules/node_modules'))) {
        throw new Error('Invalid dependency layout: node_modules/node_modules exists. Restore a clean dependency installation before packaging.');
    }
}

function verifyDependencyClosure(archive) {
    const entries = new Set(asar.listPackage(archive).map(name => name.replace(/\\/g, '/').replace(/^\/+/, '')));
    if ([...entries].some(name => name.startsWith('node_modules/node_modules/'))) {
        throw new Error('Packaged dependency layout contains an invalid node_modules/node_modules root');
    }
    const readPackage = name => JSON.parse(asar.extractFile(archive, name.split('/').join(path.sep)).toString('utf8'));
    const visited = new Set();
    function resolvePackage(directory, dependency) {
        let current = directory;
        while (true) {
            if (path.posix.basename(current) !== 'node_modules') {
                const candidate = path.posix.join(current, 'node_modules', dependency, 'package.json');
                if (entries.has(candidate)) return candidate;
            }
            if (!current) return null;
            const parent = path.posix.dirname(current);
            current = parent === '.' ? '' : parent;
        }
    }
    function visit(manifestPath) {
        if (visited.has(manifestPath)) return;
        visited.add(manifestPath);
        const manifest = readPackage(manifestPath);
        const directory = manifestPath === 'package.json' ? '' : path.posix.dirname(manifestPath);
        const optional = manifest.optionalDependencies || {};
        for (const dependency of Object.keys({...manifest.dependencies, ...optional})) {
            const resolved = resolvePackage(directory, dependency);
            if (!resolved) {
                if (Object.hasOwn(optional, dependency)) continue;
                throw new Error(`Missing packaged dependency ${dependency}, required by ${manifestPath}`);
            }
            visit(resolved);
        }
    }
    visit('package.json');
    return visited.size;
}

async function verifyPackagedRuntime(context) {
    const archive = path.join(context.appOutDir, 'resources/app.asar');
    const count = verifyDependencyClosure(archive);
    const architecture = typeof context.arch === 'number' ? Arch[context.arch] : context.arch;
    const runnable = architecture === process.arch || (process.platform === 'win32' && process.arch === 'x64' && architecture === 'ia32');
    if (context.electronPlatformName !== process.platform || !runnable) {
        console.log(`[desktop] Verified ${count} packaged dependency manifests; target execution requires ${context.electronPlatformName}/${architecture}`);
        return;
    }
    const executable = context.electronPlatformName === 'win32'
        ? path.join(context.appOutDir, `${context.packager.appInfo.productFilename}.exe`)
        : path.join(context.appOutDir, context.packager.executableName);
    const {stdout} = await promisify(execFile)(executable, [path.join(__dirname, 'packaged-runtime-probe.cjs'), archive], {
        cwd: context.appOutDir, timeout: 30000, windowsHide: true,
        env: {...process.env, ELECTRON_RUN_AS_NODE: '1', NODE_PATH: '', NODE_OPTIONS: ''}
    });
    if (!stdout.includes('PACKAGED_RUNTIME_OK')) throw new Error('Packaged Electron runtime probe did not complete');
    console.log(`[desktop] Packaged Electron dependency loading and NeDB persistence verified (${count} manifests)`);
}

module.exports = {assertDependencyLayout, verifyDependencyClosure, verifyPackagedRuntime};
