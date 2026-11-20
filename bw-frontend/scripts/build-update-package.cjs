/**
 * 生成升级包（升级规格 U02）。每个 target 与风格的组合出一个包。
 *
 *   node scripts/build-update-package.cjs --target <target> --style <HJ|HJJ|LJ> --app-asar <app.asar> [--runtime <运行组件目录>] [--output <目录>]
 *
 * --app-asar 取本轮完整构建产物里的 resources/app.asar；--runtime 默认 bin/integrated/<target>。
 * 版本号、风格都从产物本身读出并与参数、源码核对，不接受手工指定版本。
 *
 *   node scripts/build-update-package.cjs --record-release
 * 发布完成后执行，把当前迁移记为「已发布」。此后这些文件被删除或改动都会让出包失败。
 */
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const {execFileSync} = require('node:child_process');
const {createPackage, readHeader, extractPayload, packageFileName, sha256File, compareVersions, STYLES} = require('../electron/shared/update-package');
const {RUNTIME_BASELINE} = require('../electron/shared/update-baseline');
const {parseTarget} = require('../electron/shared/mysql-platforms');
const {checkProductVersion} = require('./check-product-version.cjs');

const project = path.resolve(__dirname, '..');
const repository = path.resolve(project, '..');
const policyFile = path.join(project, 'build/update-release.json');
const migrationSource = path.join(repository, 'backend/database/migrations');

// 换行归一后再取摘要：Windows 检出可能是 CRLF，不应因此误报「已发布迁移被改动」。
const sourceDigest = file => crypto.createHash('sha256').update(fs.readFileSync(file, 'utf8').replace(/\r\n/g, '\n')).digest('hex');

function currentMigrations(directory = migrationSource) {
    return Object.fromEntries(fs.readdirSync(directory).filter(name => name.endsWith('.sql')).sort()
        .map(name => [name, sourceDigest(path.join(directory, name))]));
}

/** 已发布的迁移只能追加：不得删除、改名或改内容。 */
function assertMigrationsAppendOnly(released, current) {
    const problems = Object.entries(released).flatMap(([name, digest]) =>
        !current[name] ? [`已发布的迁移被删除或改名：${name}`] : current[name] !== digest ? [`已发布的迁移内容被改动：${name}`] : []);
    if (problems.length) throw new Error(`迁移只能追加，不能改动已发布的文件：\n  ${problems.join('\n  ')}`);
}

function walk(root, relative = '') {
    return fs.readdirSync(path.join(root, relative), {withFileTypes: true}).sort((a, b) => a.name < b.name ? -1 : 1).flatMap(entry => {
        const name = relative ? `${relative}/${entry.name}` : entry.name;
        if (entry.isDirectory()) return walk(root, name);
        if (!entry.isFile()) throw new Error(`升级包只能包含普通文件：${name}`);
        return [name];
    });
}

async function buildUpdatePackage({target, style, appAsar, runtime, output, policy, sourceCommit,
    runtimeBaseline = RUNTIME_BASELINE, productVersion, releasedSource = currentMigrations()}) {
    parseTarget(target);
    if (!STYLES.includes(style)) throw new Error(`风格必须是 ${STYLES.join('、')}`);
    const asar = require('asar');
    const inside = name => asar.extractFile(appAsar, path.normalize(name));
    const version = JSON.parse(inside('package.json').toString('utf8')).version;
    if (version !== productVersion) throw new Error(`app.asar 的版本 ${version} 与源码版本 ${productVersion} 不一致，请重新构建应用`);
    const builtStyle = JSON.parse(inside('public/dist/build-manifest.json').toString('utf8')).interfaceStyle;
    if (builtStyle !== style) throw new Error(`app.asar 内的界面风格是 ${builtStyle}，与 --style ${style} 不一致`);
    inside('electron/app-main.js');
    const runtimeManifest = JSON.parse(fs.readFileSync(path.join(runtime, 'manifest.json'), 'utf8'));
    if (runtimeManifest.target !== target) throw new Error(`运行组件目录属于 ${runtimeManifest.target}，与 --target ${target} 不一致`);
    if (runtimeManifest.applicationVersion !== version) {
        throw new Error(`运行组件按版本 ${runtimeManifest.applicationVersion} 准备，与当前版本 ${version} 不一致，请重新准备运行组件`);
    }
    assertMigrationsAppendOnly(policy.releasedMigrations, releasedSource);
    const missing = Object.keys(policy.releasedMigrations).filter(name => !runtimeManifest.migrations.includes(name));
    if (missing.length) throw new Error(`运行组件缺少已发布的迁移：${missing.join('、')}`);
    if (compareVersions(policy.minFromVersion, version) > 0) throw new Error('最低起始版本高于当前版本');
    const entries = [{path: 'app.asar', source: appAsar}];
    for (const directory of ['backend', 'bootstrap']) {
        for (const name of walk(path.join(runtime, directory))) {
            const source = path.join(runtime, directory, name);
            const listed = runtimeManifest.files[`${directory}/${name}`];
            if (listed !== sha256File(source)) throw new Error(`运行组件文件与其清单不符：${directory}/${name}`);
            entries.push({path: `${directory}/${name}`, source});
        }
    }
    fs.mkdirSync(output, {recursive: true});
    const fields = {version, target, style, runtimeBaseline, minFromVersion: policy.minFromVersion, migrations: runtimeManifest.migrations, sourceCommit};
    const file = path.join(output, packageFileName(fields));
    const manifest = await createPackage({output: file, fields, entries});
    // 把刚出的包按现场导入的方式完整读一遍：写盘出错在这里就失败，而不是到现场才发现。
    try { await extractPayload(file, readHeader(file), null); }
    catch (error) { fs.rmSync(file, {force: true}); throw new Error(`升级包未通过自检：${error.message}`); }
    const report = {file: path.basename(file), bytes: fs.statSync(file).size, sha256: sha256File(file), version, target, style,
        runtimeBaseline, minFromVersion: policy.minFromVersion, migrations: manifest.migrations.length, files: manifest.files.length, sourceCommit};
    fs.writeFileSync(`${file}.json`, `${JSON.stringify(report, null, 2)}\n`);
    return report;
}

function options(args) {
    const result = {};
    for (let index = 0; index < args.length; index++) {
        if (!args[index].startsWith('--')) throw new Error(`无法识别的参数：${args[index]}`);
        const name = args[index].slice(2);
        if (name === 'record-release') result[name] = true;
        else if (index + 1 >= args.length) throw new Error(`参数 --${name} 缺少取值`);
        else result[name] = args[++index];
    }
    return result;
}

async function main(args) {
    const parsed = options(args);
    const policy = JSON.parse(fs.readFileSync(policyFile, 'utf8'));
    if (parsed['record-release']) {
        const current = currentMigrations();
        assertMigrationsAppendOnly(policy.releasedMigrations, current);
        fs.writeFileSync(policyFile, `${JSON.stringify({...policy, releasedMigrations: current}, null, 2)}\n`);
        console.log(`已记录 ${Object.keys(current).length} 个已发布迁移`);
        return;
    }
    for (const required of ['target', 'style', 'app-asar']) if (!parsed[required]) throw new Error(`缺少参数 --${required}`);
    const report = await buildUpdatePackage({
        target: parsed.target, style: parsed.style, appAsar: path.resolve(parsed['app-asar']),
        runtime: path.resolve(parsed.runtime || path.join(project, 'bin/integrated', parsed.target)),
        output: path.resolve(parsed.output || path.join(project, 'out/updates')), policy,
        productVersion: checkProductVersion(),
        sourceCommit: execFileSync('git', ['rev-parse', 'HEAD'], {cwd: repository, encoding: 'utf8'}).trim()
    });
    console.log(JSON.stringify(report, null, 2));
}

if (require.main === module) main(process.argv.slice(2)).catch(error => { console.error(error.message); process.exitCode = 1; });
module.exports = {buildUpdatePackage, assertMigrationsAppendOnly, currentMigrations};
