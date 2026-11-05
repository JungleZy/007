const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');
const fs = require('node:fs');
const os = require('node:os');
const {backendEnvironment} = require('../service/mysql-tools');
const {verifyMysqlTools} = require('../../scripts/bundle-mysql-tools.cjs');
const {mysqlTarget} = require('../shared/mysql-platforms');

test('安装目录变化时后端工具路径自动跟随，并保留显式部署配置', () => {
    const root = path.resolve('another installation');
    const input = {DB_USER: 'example'};
    const env = backendEnvironment(root, 'win32', input, 'x64');
    assert.equal(env.NIP_DATABASE_MYSQLDUMP_PATH, path.join(root, 'bin/file/tools/mysql/windows-x64/mysqldump.exe'));
    assert.equal(env.NIP_DATABASE_MYSQL_PATH, path.join(root, 'bin/file/tools/mysql/windows-x64/mysql.exe'));
    assert.deepEqual(input, {DB_USER: 'example'});
    assert.equal(backendEnvironment(root, 'win32', {NIP_DATABASE_MYSQL_PATH: 'custom-mysql'}, 'x64').NIP_DATABASE_MYSQL_PATH, 'custom-mysql');
    for (const [architecture, target] of [['x86_64', 'x64'], ['aarch64', 'arm64'], ['loongarch64', 'loong64'], ['mips64le', 'mips64el'], ['sw64', 'sw64'], ['riscv64', 'riscv64']]) {
        const linux = backendEnvironment(root, 'linux', input, architecture);
        assert.equal(linux.NIP_DATABASE_MYSQLDUMP_PATH, path.join(root, `bin/file/tools/mysql/linux-${target}/mysqldump`));
        assert.equal(linux.DB_USER, 'example');
    }
    assert.throws(() => mysqlTarget('linux', 'mips64'), /Unsupported/);
    assert.throws(() => mysqlTarget('win32', 'arm64'), /Unsupported/);
    assert.throws(() => mysqlTarget('linux', 'unknown'), /Unsupported/);
});

test('随包工具校验可通过，搬移后不依赖原安装目录，文件损坏时拒绝打包', t => {
    const project = path.resolve(__dirname, '../..');
    const source = verifyMysqlTools(project, 'windows-x64');
    const temp = fs.mkdtempSync(path.join(os.tmpdir(), 'nip-mysql-bundle-'));
    t.after(() => fs.rmSync(temp, {recursive: true, force: true}));
    const destination = path.join(temp, 'bin/file/tools/mysql/windows-x64');
    fs.cpSync(source, destination, {recursive: true});
    assert.equal(verifyMysqlTools(temp, 'windows-x64'), destination);
    assert.throws(() => verifyMysqlTools(temp, 'linux-arm64'), /Missing MySQL tools bundle for linux-arm64/);
    if (process.platform === 'win32') {
        const {execFileSync} = require('node:child_process');
        for (const tool of ['mysql.exe', 'mysqldump.exe']) {
            const version = execFileSync(path.join(destination, tool), ['--no-defaults', '--version'], {
                encoding: 'utf8', env: {...process.env, PATH: path.join(process.env.SystemRoot, 'System32')}
            });
            assert.match(version, /8\.0\./);
        }
    }
    fs.appendFileSync(path.join(destination, 'mysqldump.exe'), 'corrupt');
    assert.throws(() => verifyMysqlTools(temp, 'windows-x64'), /missing or changed/);
});
