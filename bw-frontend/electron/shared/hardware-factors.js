const fsp = require('node:fs').promises;
const path = require('node:path');
const crypto = require('node:crypto');

/**
 * 设备码的纯逻辑部分：因子清洗、Linux 采集、机器级安装标识兜底、槽位组装。
 * 不依赖 electron，便于在 node --test 下用假根目录验证。
 *
 * 兼容性约束：对「原来就能取到」的因子，取值与哈希必须与旧实现逐字节一致，
 * 否则已发出的授权会失配。新增来源只允许排在原来源之后，用于填补原本为空的槽位。
 */

const SENTINEL = '00000000';
const NAMES = ['primary', 'systemUuid', 'diskSerial', 'boardSerial'];
const MIN_FACTORS = 2;
const INSTALL_ID_FILE = 'install-id';
const INSTALL_ID_PATTERN = /^[0-9a-f-]{16,64}$/i;
// 硬件因子不足时，安装标识按此顺序顶替空槽：先占 DMI 两槽（国产 arm64 上最常缺），
// 把磁盘槽、主机标识槽留给日后可能恢复的真实因子。
const FALLBACK_ORDER = [1, 3, 2, 0];

// 白牌机 / 组装机的 BIOS 常返回这些占位串，跨机器完全相同，必须剔除，
// 否则会把大量不同机器判成同一台。
const JUNK = new Set([
    '', '0', '00', '000', 'none', 'null', 'n/a', 'na', 'unknown', 'default string',
    'to be filled by o.e.m.', 'to be filled by o.e.m', 'system serial number',
    'chassis serial number', 'not specified', 'not available', 'invalid',
    '0000000000000000', 'ffffffff-ffff-ffff-ffff-ffffffffffff',
    '00000000-0000-0000-0000-000000000000'
]);

function hash8(value) {
    if (value == null) return SENTINEL;
    const s = String(value).trim();
    if (!s || JUNK.has(s.toLowerCase())) return SENTINEL;
    if (/^0+$/.test(s.replace(/[-\s]/g, ''))) return SENTINEL;
    return crypto.createHash('sha256').update(s).digest('hex').slice(0, 8);
}

function usable(value) {
    return hash8(value) !== SENTINEL;
}

function formatCode(hex32) {
    return hex32.match(/.{1,4}/g).join('-');
}

// 设备树属性以 NUL 结尾，一并去掉。
async function readText(file) {
    try {
        const text = await fsp.readFile(file, 'utf-8');
        return text.replace(/\0/g, '').trim() || null;
    } catch (e) {
        return null;
    }
}

function parseSnapshot(text) {
    const out = {};
    if (!text) return out;
    text.split(/\r?\n/).forEach((line) => {
        const i = line.indexOf('=');
        if (i > 0) out[line.slice(0, i).trim()] = line.slice(i + 1).trim();
    });
    return out;
}

// -------- Linux 磁盘 --------

async function diskById(root) {
    let names;
    try {
        names = await fsp.readdir(path.join(root, 'dev/disk/by-id'));
    } catch (e) {
        return {named: null, wwn: null};
    }
    const whole = names.filter((n) => !n.includes('-part'));
    // 以下选择顺序与旧实现一致，不可调整。
    const cands = whole.filter((n) => !n.startsWith('wwn-') && !n.startsWith('usb-'));
    let named = null;
    for (const prefix of ['nvme-', 'ata-', 'scsi-', 'mmc-']) {
        const hit = cands.filter((n) => n.startsWith(prefix)).sort();
        if (hit.length) {
            named = hit[0];
            break;
        }
    }
    if (!named) {
        const rest = cands.sort();
        named = rest.length ? rest[0] : null;
    }
    const wwn = whole.filter((n) => n.startsWith('wwn-')).sort();
    return {named, wwn: wwn.length ? wwn[0] : null};
}

const SYSFS_SKIP = /^(loop|ram|zram|dm-|md|sr|fd|nbd)/;
const SYSFS_CLASSES = ['nvme', 'sd', 'mmcblk', 'vd', ''];
const SYSFS_ATTRS = ['device/serial', 'device/cid', 'wwid', 'device/wwid'];

// udev 没有生成 by-id（精简系统 / 部分国产发行版）时，直接读内核导出的序列号。
// 这些属性普通用户可读。sdX 的编号跨启动可能互换，因此同一类盘里取「值」最小的
// 那个，而不是「设备名」最小的那个。
async function diskSysfs(root) {
    const base = path.join(root, 'sys/block');
    let names;
    try {
        names = (await fsp.readdir(base)).filter((n) => !SYSFS_SKIP.test(n));
    } catch (e) {
        return null;
    }
    const values = new Map();
    for (const name of names) {
        const dir = path.join(base, name);
        let real = dir;
        try {
            real = await fsp.realpath(dir);
        } catch (e) { /* 用原路径继续 */ }
        if (/\/usb\d*\//.test(real)) continue;
        if (await readText(path.join(dir, 'removable')) === '1') continue;
        for (const attr of SYSFS_ATTRS) {
            const value = await readText(path.join(dir, attr));
            if (usable(value)) {
                values.set(name, value);
                break;
            }
        }
    }
    const taken = new Set();
    for (const prefix of SYSFS_CLASSES) {
        const hit = [];
        for (const [name, value] of values) {
            if (taken.has(name) || !name.startsWith(prefix)) continue;
            taken.add(name);
            hit.push(value);
        }
        if (hit.length) return hit.sort()[0];
    }
    return null;
}

/**
 * @param options.root       文件系统根，测试时指向假目录
 * @param options.machineDir 机器级目录（含安装时由 root 落盘的 hwid 快照）
 */
async function collectLinux(options) {
    const root = (options && options.root) || '/';
    const machineDir = options && options.machineDir;
    const at = (p) => path.join(root, p);
    const snap = machineDir ? parseSnapshot(await readText(path.join(machineDir, 'hwid'))) : {};
    const byId = await diskById(root);
    // 占位串（Default string 等）不能挡住后面的有效来源，逐个取第一个可用值。
    const firstUsable = async (...sources) => {
        for (const source of sources) {
            const value = typeof source === 'function' ? await source() : source;
            if (usable(value)) return value;
        }
        return null;
    };
    return {
        primary: await readText(at('etc/machine-id')) || await readText(at('var/lib/dbus/machine-id')),
        systemUuid: await firstUsable(snap.product_uuid, () => readText(at('sys/class/dmi/id/product_uuid'))),
        diskSerial: byId.named || await diskSysfs(root) || byId.wwn,
        // 设备树启动的 arm64 主板没有 DMI，整机序列号在 serial-number 属性里。
        boardSerial: await firstUsable(
            snap.product_serial,
            snap.board_serial,
            () => readText(at('sys/class/dmi/id/product_serial')),
            () => readText(at('sys/class/dmi/id/board_serial')),
            () => readText(at('sys/firmware/devicetree/base/serial-number'))
        )
    };
}

// -------- 机器级安装标识 --------

function describe(e) {
    return `${(e && e.code) || ''} ${(e && e.message) || e}`.trim();
}

async function readInstallId(dir) {
    const text = await readText(path.join(dir, INSTALL_ID_FILE));
    return text && INSTALL_ID_PATTERN.test(text) ? text.toLowerCase() : null;
}

async function createInstallId(dir) {
    const file = path.join(dir, INSTALL_ID_FILE);
    const id = crypto.randomBytes(16).toString('hex');
    await fsp.mkdir(dir, {recursive: true});
    try {
        await fsp.writeFile(file, id + '\n', {flag: 'wx', mode: 0o644});
    } catch (e) {
        if (!e || e.code !== 'EEXIST') throw e;
        const existing = await readInstallId(dir);
        if (existing) return existing;
        // 已有文件为空或损坏（可能属于 root）：目录可写即可用 rename 整体替换。
        const tmp = `${file}.${process.pid}.tmp`;
        await fsp.writeFile(tmp, id + '\n', {mode: 0o644});
        await fsp.rename(tmp, file);
    }
    return id;
}

/**
 * 读取（必要时生成）持久化的安装标识。按 dirs 顺序先读后写：机器级目录优先，
 * 不可写时退到用户级目录。它不是硬件标识，只在硬件因子不足时顶替空槽。
 * @returns {{id: string|null, error: string|null}}
 */
async function ensureInstallId(dirs) {
    const usableDirs = (dirs || []).filter(Boolean);
    for (const dir of usableDirs) {
        const id = await readInstallId(dir);
        if (id) return {id, error: null};
    }
    const errors = [];
    for (const dir of usableDirs) {
        try {
            return {id: await createInstallId(dir), error: null};
        } catch (e) {
            errors.push(`${dir}: ${describe(e)}`);
        }
    }
    return {id: null, error: errors.join('；') || '没有可用的存储目录'};
}

// -------- 组装 --------

/**
 * @param raw       {primary, systemUuid, diskSerial, boardSerial}
 * @param installId 仅在硬件因子不足时传入
 */
function assemble(raw, installId) {
    const slots = NAMES.map((name) => hash8(raw && raw[name]));
    const factors = {};
    NAMES.forEach((name, i) => {
        factors[name] = slots[i] !== SENTINEL;
    });
    const hardware = slots.filter((s) => s !== SENTINEL).length;
    let fallback = false;
    if (hardware < MIN_FACTORS && installId) {
        let need = MIN_FACTORS - hardware;
        for (const i of FALLBACK_ORDER) {
            if (!need) break;
            if (slots[i] !== SENTINEL) continue;
            slots[i] = hash8(`install:${i}:${installId}`);
            need--;
            fallback = true;
        }
    }
    const available = slots.filter((s) => s !== SENTINEL).length;
    return {
        slots,
        available,
        hardware,
        factors,
        fallback,
        code: available >= MIN_FACTORS ? formatCode(slots.join('')) : null
    };
}

module.exports = {SENTINEL, MIN_FACTORS, hash8, readText, collectLinux, ensureInstallId, assemble};
