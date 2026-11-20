// 升级相关测试共用的夹具：生成一个最小但结构完整的升级包。
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const crypto = require('node:crypto');
const {createPackage, PRODUCT} = require('../shared/update-package');

function workspace(t, name = 'nip-update-') {
    const root = fs.mkdtempSync(path.join(os.tmpdir(), name));
    t.after(() => fs.rmSync(root, {recursive: true, force: true}));
    return root;
}

const identity = (extra = {}) => ({product: PRODUCT, version: '3.1.3', style: 'HJ', target: 'linux-x64', runtimeBaseline: 1, ...extra});

async function buildPackage(root, fields = {}, content = {}) {
    const source = fs.mkdtempSync(path.join(root, 'source-'));
    const files = {'app.asar': 'asar-bytes', 'backend/quarkus-run.jar': 'jar-bytes', 'backend/lib/empty.jar': '',
        'bootstrap/seed.sql': 'CREATE TABLE a(id int);', 'bootstrap/migrations/2026-01-01-01-first.sql': 'ALTER TABLE a ADD b int;', ...content};
    const entries = Object.entries(files).map(([name, text]) => {
        const file = path.join(source, ...name.split('/'));
        fs.mkdirSync(path.dirname(file), {recursive: true});
        fs.writeFileSync(file, text);
        return {path: name, source: file};
    });
    const output = path.join(root, `package-${crypto.randomBytes(4).toString('hex')}.nipupd`);
    const manifest = await createPackage({output, entries, fields: {version: '3.2.0', target: 'linux-x64', style: 'HJ',
        runtimeBaseline: 1, minFromVersion: '3.1.3', migrations: ['2026-01-01-01-first.sql'], sourceCommit: 'test', ...fields}});
    return {file: output, manifest, files};
}

module.exports = {workspace, identity, buildPackage};
