const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const {mysqlTarget} = require('../shared/mysql-platforms');
const {backendEnvironment} = require('../service/mysql-tools');
const {assertBinaryTarget} = require('../../scripts/bundle-mysql-tools.cjs');
const {mysql57Sql} = require('../../scripts/mysql57-bootstrap.cjs');

test('Windows ia32 按目标架构选择工具，PE32 与 PE64 不能混用', t => {
    const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'nip-ia32-pe-'));
    t.after(() => fs.rmSync(directory, {recursive: true, force: true}));
    const data = Buffer.alloc(256);
    data.write('MZ');data.writeUInt32LE(128, 60);data.writeUInt32LE(0x4550, 128);data.writeUInt16LE(0x14c, 132);
    const exe = path.join(directory, 'fixture.exe');fs.writeFileSync(exe, data);
    assert.doesNotThrow(() => assertBinaryTarget(exe, 'windows-ia32'));
    assert.throws(() => assertBinaryTarget(exe, 'windows-x64'), /x64/);
    assert.equal(mysqlTarget('win32', 'ia32'), 'windows-ia32');
    assert.equal(backendEnvironment(directory, 'win32', {}, 'ia32').NIP_DATABASE_MYSQL_PATH,
        path.join(directory, 'bin/file/tools/mysql/windows-ia32/mysql.exe'));
});

test('MySQL5.7 仅转换专属初始化副本，保留空采集页的应用层默认值', () => {
    const sql = "COLLATE=utf8mb4_0900_ai_ci;\nCALL mig_20260911_general_add('general_key_pat_user','capture_pages','longtext NOT NULL DEFAULT (''{}'')');";
    const compatible = mysql57Sql(sql);
    assert.match(compatible, /utf8mb4_general_ci/);
    assert.doesNotMatch(compatible, /0900|DEFAULT \(/);
    assert.match(compatible, /'capture_pages','longtext NOT NULL'/);
    assert.match(sql, /0900/);
});

test('32位打包配置使用 Electron22、ia32目标及单独输出目录', () => {
    const config = require('../../build/windows-ia32.cjs');
    assert.equal(config.electronVersion, '22.3.27');
    assert.deepEqual(config.win.target[0].arch, ['ia32']);
    assert.equal(config.nsis.deleteAppDataOnUninstall, false);
    assert.equal(config.directories.output, 'out/win7-ia32');
});
