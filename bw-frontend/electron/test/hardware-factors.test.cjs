const test = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const {collectLinux, ensureInstallId, assemble} = require('../shared/hardware-factors');

const slot = value => crypto.createHash('sha256').update(value).digest('hex').slice(0, 8);

function fixture(t, files) {
    const root = fs.mkdtempSync(path.join(os.tmpdir(), 'nip-factors-'));
    t.after(() => fs.rmSync(root, {recursive: true, force: true}));
    for (const [name, content] of Object.entries(files)) {
        const file = path.join(root, name);
        fs.mkdirSync(path.dirname(file), {recursive: true});
        fs.writeFileSync(file, content);
    }
    return root;
}

test('原有因子来源的设备码保持不变，已发授权不失配', async t => {
    const root = fixture(t, {
        'etc/machine-id': 'machine-fixture\n',
        'dev/disk/by-id/ata-DISK_B': '',
        'dev/disk/by-id/nvme-DISK_A': '',
        'dev/disk/by-id/nvme-DISK_A-part1': '',
        'dev/disk/by-id/wwn-0x5000': '',
        'dev/disk/by-id/usb-STICK': '',
        'sys/block/sda/device/serial': 'SYSFS-SERIAL\n',
        'state/hwid': 'product_uuid=uuid-fixture\nproduct_serial=Default string\nboard_serial=board-fixture\n'
    });
    const result = assemble(await collectLinux({root, machineDir: path.join(root, 'state')}));
    assert.deepEqual(result.slots,
        [slot('machine-fixture'), slot('uuid-fixture'), slot('nvme-DISK_A'), slot('board-fixture')]);
    assert.equal(result.fallback, false);
    assert.equal(result.code.replace(/-/g, ''), result.slots.join(''));
});

test('无 DMI、无快照、by-id 只有 wwn 的 arm64 机器仍能取到足够的硬件因子', async t => {
    const withWwn = fixture(t, {
        'etc/machine-id': 'machine-fixture\n',
        'dev/disk/by-id/wwn-0x5000c500a1b2c3d4': '',
        'dev/disk/by-id/wwn-0x5000c500a1b2c3d4-part1': '',
        'sys/firmware/devicetree/base/serial-number': 'DT-SERIAL\0'
    });
    const first = assemble(await collectLinux({root: withWwn}));
    assert.deepEqual(first.slots,
        [slot('machine-fixture'), '00000000', slot('wwn-0x5000c500a1b2c3d4'), slot('DT-SERIAL')]);
    assert.equal(first.fallback, false);

    // 没有 by-id 时读 /sys/block；sdX 编号可能互换，结果不能依赖设备名。
    const disks = {'etc/machine-id': 'machine-fixture\n', 'sys/block/loop0/device/serial': 'LOOP'};
    const a = fixture(t, {...disks, 'sys/block/sda/device/wwid': 'naa.BBBB', 'sys/block/sdb/device/wwid': 'naa.AAAA',
        'sys/block/sdc/device/wwid': 'naa.0000-stick', 'sys/block/sdc/removable': '1\n'});
    const b = fixture(t, {...disks, 'sys/block/sda/device/wwid': 'naa.AAAA', 'sys/block/sdb/device/wwid': 'naa.BBBB'});
    const fromA = assemble(await collectLinux({root: a}));
    assert.equal(fromA.slots[2], slot('naa.AAAA'));
    assert.equal(fromA.code, assemble(await collectLinux({root: b})).code);
});

test('硬件因子不足时用持久化安装标识兜底，设备码跨启动稳定', async t => {
    const root = fixture(t, {'etc/machine-id': 'machine-fixture\n'});
    const raw = await collectLinux({root});
    const bare = assemble(raw);
    assert.equal(bare.code, null);
    assert.deepEqual(bare.factors, {primary: true, systemUuid: false, diskSerial: false, boardSerial: false});

    const machine = path.join(root, 'machine');
    const user = path.join(root, 'user');
    const created = await ensureInstallId([machine, user]);
    assert.equal(created.error, null);
    const again = await ensureInstallId([machine, user]);
    assert.equal(again.id, created.id);
    assert.equal(fs.existsSync(path.join(user, 'install-id')), false);

    const result = assemble(raw, created.id);
    assert.equal(result.fallback, true);
    assert.equal(result.hardware, 1);
    assert.equal(result.slots[0], slot('machine-fixture'));
    assert.equal(result.slots.filter(value => value !== '00000000').length, 2);
    assert.equal(result.code, assemble(raw, again.id).code);

    // 机器级目录不可用时退到用户级目录；安装脚本留下的空文件会被替换。
    fs.writeFileSync(path.join(root, 'blocked'), '');
    fs.mkdirSync(path.join(root, 'empty'));
    fs.writeFileSync(path.join(root, 'empty/install-id'), '');
    const fallback = await ensureInstallId([path.join(root, 'blocked/dir'), path.join(root, 'empty')]);
    assert.match(fallback.id, /^[0-9a-f]{32}$/);
    assert.equal(fs.readFileSync(path.join(root, 'empty/install-id'), 'utf-8').trim(), fallback.id);

    const none = await ensureInstallId([path.join(root, 'blocked/dir')]);
    assert.equal(none.id, null);
    assert.match(none.error, /blocked/);
    assert.equal(assemble({}, none.id).code, null);
});
