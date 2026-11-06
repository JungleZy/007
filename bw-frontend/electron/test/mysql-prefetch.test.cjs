const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const {hashFile, downloadArchive, debDataArchive, prepareTarget} = require('../../scripts/prepare-mysql-tools.cjs');
const {copyTargetTools} = require('../../scripts/stage-mysql-tools.cjs');

function temporary(t) {
    const root = fs.mkdtempSync(path.join(os.tmpdir(), 'nip-prefetch-test-'));
    t.after(() => fs.rmSync(root, {recursive: true, force: true}));
    return root;
}

test('缓存下载包必须与锁定摘要一致，损坏缓存不能进入解压流程', async t => {
    const cache = temporary(t);
    const archive = path.join(cache, 'client.tar.xz');
    fs.writeFileSync(archive, 'fixture archive');
    const source = {url: 'https://example.invalid/client.tar.xz', sha256: await hashFile(archive)};
    assert.equal(await downloadArchive(source, cache), archive);
    fs.appendFileSync(archive, 'changed');
    await assert.rejects(downloadArchive(source, cache), /checksum mismatch/);
});

test('Debian 依赖解析拒绝截断归档，并按二进制数据提取 data.tar', () => {
    const payload = Buffer.from([0, 1, 255, 10]);
    const header = Buffer.alloc(60, ' ');
    header.write('data.tar.xz/', 0);
    header.write(String(payload.length), 48);
    header.write('`\n', 58);
    const archive = Buffer.concat([Buffer.from('!<arch>\n'), header, payload]);
    assert.deepEqual(debDataArchive(archive), {name: 'data.tar.xz', data: payload});
    assert.throws(() => debDataArchive(archive.subarray(0, archive.length - 1)), /Invalid/);
});

test('打包库有多个平台时，输出目录仅包含指定的目标平台工具', t => {
    const output = temporary(t);
    const project = path.resolve(__dirname, '../..');
    for (const target of ['windows-x64', 'linux-x64', 'linux-arm64']) {
        const destination = path.join(output, target);
        copyTargetTools(project, destination, target);
        assert.deepEqual(fs.readdirSync(path.join(destination, 'bin/file/tools/mysql')), [target]);
    }
});

test('没有核验下载来源的平台明确失败，不会下载其他 CPU 的包替代', async t => {
    await assert.rejects(prepareTarget('linux-sw64', temporary(t)), /SW64/);
});
