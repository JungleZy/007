const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');
const fs = require('node:fs');
const os = require('node:os');
const {bundleMysqlTools, verifyMysqlTools, assertBinaryTarget} = require('../../scripts/bundle-mysql-tools.cjs');

// 最小 ELF 头仅验证打包门禁，不代表真实可执行文件或国产实机验收。
function elf(machine, glibc = '2.28') {
    const data = Buffer.alloc(128);
    data.set([0x7f, 0x45, 0x4c, 0x46, 2, 1]);
    data.writeUInt16LE(machine, 18);
    data.write(`GLIBC_${glibc}`, 64);
    return data;
}

function staging(t, machine, minLibc = '2.28', binaryLibc = '2.28') {
    const root = fs.mkdtempSync(path.join(os.tmpdir(), 'nip-mysql-target-'));
    t.after(() => fs.rmSync(root, {recursive: true, force: true}));
    const source = path.join(root, 'source');
    fs.mkdirSync(path.join(source, 'bin'), {recursive: true});
    for (const tool of ['mysql', 'mysqldump']) fs.writeFileSync(path.join(source, 'bin', tool), elf(machine, binaryLibc));
    fs.writeFileSync(path.join(source, 'LICENSE'), 'fixture license');
    fs.writeFileSync(path.join(source, 'README'), 'fixture readme');
    fs.writeFileSync(path.join(source, 'runtime.json'), JSON.stringify({clientFamily: 'mysql', clientVersion: '8.0.46', libc: 'glibc', minLibc, testedOn: ['test fixture only']}));
    return {source, project: path.join(root, 'project')};
}

test('Linux 六种目标架构分别校验 ELF 头和工具清单', t => {
    for (const [arch, machine] of Object.entries({x64: 62, arm64: 183, loong64: 258, mips64el: 8, sw64: 0x9916, riscv64: 243})) {
        const {source, project} = staging(t, machine);
        const target = `linux-${arch}`;
        const output = bundleMysqlTools(source, target, project);
        assert.equal(verifyMysqlTools(project, target), output);
        assert.throws(() => assertBinaryTarget(path.join(output, 'mysqldump'), arch === 'x64' ? 'linux-arm64' : 'linux-x64'), /Expected/);
    }
});

test('错误架构、Windows 文件和虚报 glibc 最低版本被拦截', t => {
    const wrongArch = staging(t, 62);
    assert.throws(() => bundleMysqlTools(wrongArch.source, 'linux-arm64', wrongArch.project), /Expected/);
    const incompatible = staging(t, 183, '2.28', '2.38');
    assert.throws(() => bundleMysqlTools(incompatible.source, 'linux-arm64', incompatible.project), /glibc minimum/);
    const windows = path.resolve(__dirname, '../../bin/file/tools/mysql/windows-x64/mysql.exe');
    assert.throws(() => assertBinaryTarget(windows, 'linux-x64'), /Expected/);
});

test('缺失系统兼容信息或 MariaDB 客户端不能冒充已验证的 MySQL 工具包', t => {
    const {source, project} = staging(t, 258);
    fs.writeFileSync(path.join(source, 'runtime.json'), JSON.stringify({clientFamily: 'mysql', clientVersion: '8.0.46'}));
    assert.throws(() => bundleMysqlTools(source, 'linux-loong64', project), /runtime.json/);
    fs.writeFileSync(path.join(source, 'runtime.json'), JSON.stringify({clientFamily: 'mariadb', clientVersion: '10.11'}));
    assert.throws(() => bundleMysqlTools(source, 'linux-loong64', project), /MariaDB/);
});
