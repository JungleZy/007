const fs = require('node:fs');
const path = require('node:path');
const asar = require('asar');
const crypto = require('node:crypto');
const {parseArgs} = require('node:util');
const prepareDesktop = require('./prepare-desktop.cjs');
const stageTools = require('./stage-mysql-tools.cjs');
const {assertBinaryTarget} = require('./bundle-mysql-tools.cjs');

const project = path.resolve(__dirname, '..');

// Preserve Node's nested dependency resolution; never copy development dependencies.
function copyDependencies(source, destination, copied = new Set()) {
    const manifest = JSON.parse(fs.readFileSync(path.join(source, 'package.json'), 'utf8'));
    for (const name of Object.keys({...manifest.dependencies, ...manifest.optionalDependencies})) {
        let directory = source;
        let resolved;
        while (true) {
            const candidate = path.join(directory, 'node_modules', name);
            if (fs.existsSync(path.join(candidate, 'package.json'))) {
                resolved = candidate;
                break;
            }
            if (directory === project) break;
            const parent = path.dirname(directory);
            if (parent === directory || !parent.startsWith(project)) break;
            directory = parent;
        }
        if (!resolved) {
            if (Object.hasOwn(manifest.optionalDependencies || {}, name)) continue;
            throw new Error(`Missing production dependency: ${name} (${source})`);
        }
        const relative = path.relative(project, resolved);
        if (copied.has(relative)) continue;
        copied.add(relative);
        fs.cpSync(resolved, path.join(destination, relative), {
            recursive: true, dereference: true,
            filter: file => file === resolved || !path.relative(resolved, file).split(path.sep).includes('node_modules')
        });
        copyDependencies(resolved, destination, copied);
    }
}

async function main() {
    const {values} = parseArgs({options: {style: {type: 'string'}}});
    const style = values.style;
    if (style && !['HJ', 'HJJ', 'LJ'].includes(style)) throw new Error('Style must be HJ, HJJ or LJ');
    if (style) process.env.NIP_INTERFACE_STYLE = style;
    const electron = process.env.NIP_LOONGARCH_ELECTRON_DIR || path.join(project, '.cache/kylin-loongarch64-components/electron');
    assertBinaryTarget(path.join(electron, 'electron'), 'linux-loong64');
    if (fs.readFileSync(path.join(electron, 'version'), 'utf8').trim() !== '30.5.1') {
        throw new Error('This LoongArch configuration requires Electron 30.5.1');
    }
    const output = path.join(project, 'out/kylin-v10-loongarch64', style || '', 'linux-loong64-unpacked');
    if (fs.existsSync(output)) throw new Error('Archive the previous output before building: ' + output);
    const context = {arch: 'loong64', electronPlatformName: 'linux', appOutDir: output,
        packager: {config: {electronVersion: '30.5.1'}, info: {}, executableName: 'nip-traffic-system'}};
    prepareDesktop(context);
    fs.mkdirSync(path.dirname(output), {recursive: true});
    const staging = fs.mkdtempSync(path.join(path.dirname(output), '.app-'));
    try {
        for (const name of ['main.js', 'electron', 'public/dist', 'public/images', 'public/html', 'package.json', 'LICENSE']) {
            fs.cpSync(path.join(project, name), path.join(staging, name), {recursive: true});
        }
        copyDependencies(project, staging);
        if (fs.readdirSync(staging, {recursive: true}).some(name => name.endsWith('.node'))) {
            throw new Error('Native Node modules require an explicit LoongArch rebuild and unpack rule');
        }
        fs.cpSync(electron, output, {recursive: true});
        fs.renameSync(path.join(output, 'electron'), path.join(output, 'nip-traffic-system'));
        fs.mkdirSync(path.join(output, 'resources'), {recursive: true});
        fs.writeFileSync(path.join(output, 'resources/runtime-sources.json'), JSON.stringify({
            components: require('./kylin-loongarch64-components.json'),
            mysqlPackages: require('./loongarch-mysql-rpms.json')
        }, null, 2));
        fs.rmSync(path.join(output, 'resources/default_app.asar'), {force: true});
        if (style) {
            const assignments = fs.readdirSync(path.join(staging, 'public/dist/static/js'))
                .filter(name => name.endsWith('.js'))
                .flatMap(name => [...fs.readFileSync(path.join(staging, 'public/dist/static/js', name), 'utf8')
                    .matchAll(/window\.interfaceStyle\s*=\s*["'](HJ|HJJ|LJ)["']/g)].map(match => match[1]));
            if (assignments.length !== 1 || assignments[0] !== style) throw new Error('Compiled runtime style mismatch');
            const manifestFile = path.join(staging, 'public/dist/build-manifest.json');
            const frontend = JSON.parse(fs.readFileSync(manifestFile, 'utf8'));
            frontend.interfaceStyle = style;
            fs.writeFileSync(manifestFile, JSON.stringify(frontend, null, 2) + '\n');
        }
        await asar.createPackage(staging, path.join(output, 'resources/app.asar'));
        if (style) {
            const frontend = JSON.parse(fs.readFileSync(path.join(staging, 'public/dist/build-manifest.json'), 'utf8'));
            fs.writeFileSync(path.join(output, 'resources/interface-style.json'), JSON.stringify({
                interfaceStyle: style,
                appAsarSha256: crypto.createHash('sha256').update(fs.readFileSync(path.join(output, 'resources/app.asar'))).digest('hex'),
                sourceCommit: frontend.sourceCommit
            }, null, 2));
        }
        fs.cpSync(path.join(project, 'bin'), path.join(output, 'bin'), {recursive: true, filter: file => {
            const relative = path.relative(path.join(project, 'bin'), file).split(path.sep).join('/');
            return !['server', 'integrated', 'file/tools/mysql'].some(excluded => relative === excluded || relative.startsWith(excluded + '/'));
        }});
        await stageTools(context);
        console.log('Prepared LoongArch desktop payload: ' + output);
    } catch (error) {
        fs.rmSync(output, {recursive: true, force: true});
        throw error;
    } finally {
        fs.rmSync(staging, {recursive: true, force: true});
    }
}

if (require.main === module) main().catch(error => {console.error(error); process.exitCode = 1;});
module.exports = {copyDependencies};
