const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const {execFileSync} = require('node:child_process');
const sources = require('./mysql-tool-sources.json');
const {bundleMysqlTools, verifyMysqlTools} = require('./bundle-mysql-tools.cjs');

const project = path.resolve(__dirname, '..');

async function hashFile(file, algorithm = 'sha256') {
    const hash = crypto.createHash(algorithm);
    for await (const chunk of fs.createReadStream(file)) hash.update(chunk);
    return hash.digest('hex');
}

async function downloadArchive(source, cacheDirectory) {
    fs.mkdirSync(cacheDirectory, {recursive: true});
    const archive = path.join(cacheDirectory, path.basename(new URL(source.url).pathname));
    if (!fs.existsSync(archive)) {
        const partial = `${archive}.partial`;
        console.log(`[mysql] Downloading ${source.url}`);
        execFileSync('curl', ['--fail', '--location', '--show-error', '--retry', '2', '--connect-timeout', '20',
            '--max-time', '1800', '--continue-at', '-', '--output', partial, source.url], {stdio: 'inherit'});
        if (await hashFile(partial) !== source.sha256) throw new Error(`Archive checksum mismatch: ${partial}`);
        fs.renameSync(partial, archive);
    }
    if (await hashFile(archive) !== source.sha256) throw new Error(`Archive checksum mismatch: ${archive}`);
    return archive;
}

function debDataArchive(data) {
    if (data.toString('ascii', 0, 8) !== '!<arch>\n') throw new Error('Invalid Debian package');
    for (let offset = 8; offset + 60 <= data.length;) {
        const name = data.toString('ascii', offset, offset + 16).trim().replace(/\/$/, '');
        const length = Number(data.toString('ascii', offset + 48, offset + 58).trim());
        if (!Number.isSafeInteger(length) || length <= 0 || offset + 60 + length > data.length) throw new Error('Invalid Debian archive entry');
        if (name === 'data.tar.xz' || name === 'data.tar.gz') return {name, data: data.subarray(offset + 60, offset + 60 + length)};
        offset += 60 + length + length % 2;
    }
    throw new Error('No supported Debian data archive');
}

async function prepareTarget(target, cacheDirectory) {
    const source = sources.targets[target];
    if (!source) throw new Error(`Unknown target: ${target}`);
    const destination = path.join(project, 'bin/file/tools/mysql', target);
    if (fs.existsSync(destination)) return verifyMysqlTools(project, target);
    if (!source.url) throw new Error(source.unavailableReason || `Restore bundled ${target} files, or import a trusted local installation with bundle-mysql-tools.cjs`);
    const archive = await downloadArchive(source, cacheDirectory);
    const staging = fs.mkdtempSync(path.join(cacheDirectory, 'prepare-'));
    try {
        const members = ['bin/mysql', 'bin/mysqldump', 'LICENSE', 'README',
            'lib/private/libcrypto.so.3', 'lib/private/libssl.so.3'];
        console.log(`[mysql] Extracting client tools for ${target}`);
        // 已固定摘要的官方归档，只提取指定成员，不解压 mysqld 或数据库数据。
        execFileSync('tar', ['-xf', archive, '-C', staging, ...members.map(name => `${source.archiveRoot}/${name}`)], {stdio: 'inherit'});
        const extracted = path.join(staging, source.archiveRoot);
        const prepared = path.join(staging, 'client');
        fs.mkdirSync(path.join(prepared, 'bin'), {recursive: true});
        fs.mkdirSync(path.join(prepared, 'lib'), {recursive: true});
        for (const name of ['mysql', 'mysqldump']) fs.copyFileSync(path.join(extracted, 'bin', name), path.join(prepared, 'bin', name));
        for (const name of ['LICENSE', 'README']) fs.copyFileSync(path.join(extracted, name), path.join(prepared, name));
        for (const name of ['libcrypto.so.3', 'libssl.so.3']) fs.copyFileSync(path.join(extracted, 'lib/private', name), path.join(prepared, 'lib', name));
        for (const dependency of source.dependencies || []) {
            const deb = await downloadArchive(dependency, cacheDirectory);
            const dataArchive = debDataArchive(fs.readFileSync(deb));
            const depRoot = fs.mkdtempSync(path.join(staging, 'dependency-'));
            const tarball = path.join(depRoot, dataArchive.name);
            fs.writeFileSync(tarball, dataArchive.data);
            execFileSync('tar', ['-xf', tarball, '-C', depRoot, dependency.member, ...(dependency.license ? [dependency.license] : [])], {stdio: 'inherit'});
            fs.copyFileSync(path.join(depRoot, dependency.member), path.join(prepared, 'lib', dependency.soname));
            if (dependency.license) {
                fs.mkdirSync(path.join(prepared, 'licenses'), {recursive: true});
                fs.copyFileSync(path.join(depRoot, dependency.license), path.join(prepared, 'licenses/ncurses-copyright.txt'));
            }
        }
        fs.writeFileSync(path.join(prepared, 'runtime.json'), JSON.stringify({
            clientFamily: 'mysql', clientVersion: sources.version, libc: 'glibc', minLibc: source.minLibc,
            testedOn: [], source: {url: source.url, sha256: source.sha256, upstreamMd5: source.upstreamMd5},
            dependencies: source.dependencies || []
        }, null, 2));
        return bundleMysqlTools(prepared, target, project);
    } finally {
        fs.rmSync(staging, {recursive: true, force: true});
    }
}

function reportStatus() {
    for (const [target, source] of Object.entries(sources.targets)) {
        try {
            const directory = verifyMysqlTools(project, target);
            const {runtime} = JSON.parse(fs.readFileSync(path.join(directory, 'manifest.json'), 'utf8'));
            console.log(`${target}: files verified, MySQL ${runtime.clientVersion}${runtime.minLibc ? `, glibc >= ${runtime.minLibc}, execution ${runtime.testedOn.length ? 'checked (see manifest)' : 'not verified'}` : ''}`);
        } catch (error) {
            console.log(`${target}: unavailable — ${source.unavailableReason || error.message}`);
        }
    }
}

async function main() {
    const args = process.argv.slice(2);
    if (args.includes('--status')) return reportStatus();
    const targets = args.length === 0 || args.includes('--available')
        ? Object.keys(sources.targets).filter(target => sources.targets[target].url || sources.targets[target].bundled)
        : args.includes('--all') ? Object.keys(sources.targets) : args;
    const cache = process.env.NIP_MYSQL_CACHE_DIR || path.join(project, '.cache/mysql-tools');
    for (const target of targets) {
        try {
            console.log(`[mysql] Ready: ${await prepareTarget(target, cache)}`);
        } catch (error) {
            console.error(`[mysql] ${target}: ${error.message}`);
            process.exitCode = 1;
        }
    }
    reportStatus();
}

if (require.main === module) main().catch(error => { console.error(error.message); process.exitCode = 1; });
module.exports = {hashFile, prepareTarget, debDataArchive, downloadArchive};
