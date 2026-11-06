const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const {parseTarget} = require('../electron/shared/mysql-platforms');
const {assertBinaryTarget} = require('./bundle-mysql-tools.cjs');
const {mysql57Sql} = require('./mysql57-bootstrap.cjs');

const project = path.resolve(__dirname, '..');
const backend = path.resolve(project, '../backend');
const {businessDataGroups, systemTables} = require('./initial-data-tables.cjs');
const seedTables = new Set([...systemTables, ...Object.values(businessDataGroups).flat()]);

function dataStatements(snapshot, tables = seedTables) {
    const columns = new Map([...snapshot.matchAll(/^CREATE TABLE `([^`]+)`\s*\([\s\S]*?^\) ENGINE[^\r\n]*;/gm)]
        .map(match => [match[1], [...match[0].matchAll(/^\s+`([^`]+)` /gm)].map(column => '`' + column[1] + '`')]));
    return snapshot.split(/\r?\n/).flatMap(line => {
        const match = /^INSERT INTO `([^`]+)` VALUES (.*);$/.exec(line);
        if (!match || !tables.has(match[1])) return [];
        if (!columns.has(match[1])) throw new Error(`Missing columns for seed table ${match[1]}`);
        // 显式列名允许在已执行新增列迁移的库中补充同一份资料。
        return [`INSERT IGNORE INTO \`${match[1]}\` (${columns.get(match[1]).join(',')}) VALUES ${match[2]};`];
    });
}

function businessSeed(snapshot) {
    const tables = new Set(Object.values(businessDataGroups).flat());
    return 'SET NAMES utf8mb4;\nSTART TRANSACTION;\n' + dataStatements(snapshot, tables).join('\n') + '\nCOMMIT;\n';
}

function seedInventory(snapshot) {
    return Object.fromEntries(Object.entries(businessDataGroups).map(([group, tables]) => [group,
        Object.fromEntries(tables.map(table => [table, snapshot.split(/\r?\n/).filter(line => line.startsWith('INSERT INTO `' + table + '` VALUES ')).length]))]));
}

function cleanSeed(snapshot) {
    const definitions = [...snapshot.matchAll(/^CREATE TABLE `([^`]+)`\s*\([\s\S]*?^\) ENGINE[^\r\n]*;/gm)];
    const declared = [...snapshot.matchAll(/^CREATE TABLE /gm)].length;
    if (definitions.length !== declared || declared < 100) throw new Error('Incomplete database schema extraction');
    const ddl = definitions.map(match => match[0].replace('CREATE TABLE ', 'CREATE TABLE IF NOT EXISTS '));
    const inserts = dataStatements(snapshot);
    return 'SET NAMES utf8mb4;\nSET FOREIGN_KEY_CHECKS=0;\n' + ddl.join('\n') + '\nSTART TRANSACTION;\n'
        + inserts.join('\n') + '\nCOMMIT;\nSET FOREIGN_KEY_CHECKS=1;\n';
}

function hash(file) {
    return crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex');
}

function inventory(root, relative = '') {
    const result = {};
    for (const entry of fs.readdirSync(path.join(root, relative), {withFileTypes: true})) {
        const name = relative ? `${relative}/${entry.name}` : entry.name;
        if (name === 'manifest.json') continue;
        if (entry.isDirectory()) Object.assign(result, inventory(root, name));
        else if (entry.isFile()) result[name] = hash(path.join(root, name));
        else throw new Error(`Unexpected link in runtime assets: ${name}`);
    }
    return result;
}

function verifyIntegratedRuntime(root, target) {
    const directory = path.join(root, 'bin/integrated', target);
    const manifest = JSON.parse(fs.readFileSync(path.join(directory, 'manifest.json'), 'utf8'));
    if (manifest.target !== target || manifest.schemaVersion !== 1) throw new Error('Integrated runtime target mismatch');
    const suffix = target.startsWith('windows-') ? '.exe' : '';
    for (const file of [`database/bin/mysqld${suffix}`, `runtime/bin/java${suffix}`, 'backend/quarkus-run.jar', 'bootstrap/seed.sql']) {
        if (!manifest.files[file]) throw new Error(`Missing integrated runtime asset: ${file}`);
    }
    if (suffix && target !== 'windows-ia32') {
        for (const name of ['msvcp140.dll', 'vcruntime140.dll', 'vcruntime140_1.dll']) {
            if (!manifest.files[`runtime/bin/${name}`]) throw new Error(`Missing bundled Windows runtime dependency: ${name}`);
        }
    }
    const files = inventory(directory);
    if (JSON.stringify(Object.keys(files).sort()) !== JSON.stringify(Object.keys(manifest.files).sort())) throw new Error('Integrated runtime inventory mismatch');
    for (const [name, digest] of Object.entries(files)) if (digest !== manifest.files[name]) throw new Error(`Integrated runtime checksum mismatch: ${name}`);
    if (target === 'windows-ia32') {
        if (manifest.databaseVersion !== '5.7.44') throw new Error('Windows ia32 requires MySQL 5.7.44 runtime assets');
        for (const name of Object.keys(files).filter(name => /\.(exe|dll)$/i.test(name))) {
            assertBinaryTarget(path.join(directory, name), target);
        }
    }
    assertBinaryTarget(path.join(directory, `database/bin/mysqld${suffix}`), target);
    assertBinaryTarget(path.join(directory, `runtime/bin/java${suffix}`), target);
    return directory;
}

function prepareIntegratedRuntime(mysqlHome, javaHome, target) {
    parseTarget(target);
    const suffix = target.startsWith('windows-') ? '.exe' : '';
    assertBinaryTarget(path.join(mysqlHome, `bin/mysqld${suffix}`), target);
    assertBinaryTarget(path.join(javaHome, `bin/java${suffix}`), target);
    const javaRelease = fs.readFileSync(path.join(javaHome, 'release'), 'utf8');
    if (!/JAVA_VERSION="21(?:\.|"|\+)/.test(javaRelease)) throw new Error('A Java 21 runtime is required');
    const jarRoot = path.join(backend, target === 'windows-ia32' ? 'target/quarkus-app-win32' : 'target/quarkus-app');
    fs.accessSync(path.join(jarRoot, 'quarkus-run.jar'));
    const parent = path.join(project, 'bin/integrated');
    fs.mkdirSync(parent, {recursive: true});
    const targetRoot = path.join(parent, target);
    if (fs.existsSync(targetRoot)) throw new Error(`Runtime already exists; archive it before rebuilding: ${targetRoot}`);
    const stage = fs.mkdtempSync(path.join(parent, '.preparing-'));
    const copy = (source, destination) => fs.cpSync(source, destination, {
        recursive: true, dereference: true,
        filter: file => !/\.(pdb|lib|a)$|[\\/]debug[\\/]|-debug\./i.test(file)
    });
    try {
        fs.mkdirSync(path.join(stage, 'database/bin'), {recursive: true});
        fs.copyFileSync(path.join(mysqlHome, `bin/mysqld${suffix}`), path.join(stage, `database/bin/mysqld${suffix}`));
        if (suffix) {
            for (const file of fs.readdirSync(path.join(mysqlHome, 'bin'))) {
                if (/\.dll$/i.test(file) && !/-debug\./i.test(file)) copy(path.join(mysqlHome, 'bin', file), path.join(stage, 'database/bin', file));
            }
        }
        for (const name of ['lib', 'share', 'LICENSE', 'README']) copy(path.join(mysqlHome, name), path.join(stage, 'database', name));
        if (fs.existsSync(path.join(mysqlHome, 'dependency-licenses'))) {
            copy(path.join(mysqlHome, 'dependency-licenses'), path.join(stage, 'database/dependency-licenses'));
        }
        for (const name of ['bin', 'conf', 'lib', 'legal', 'release']) copy(path.join(javaHome, name), path.join(stage, 'runtime', name));
        copy(jarRoot, path.join(stage, 'backend'));
        fs.mkdirSync(path.join(stage, 'bootstrap/migrations'), {recursive: true});
        const snapshot = fs.readFileSync(path.join(backend, 'database/project006.sql'), 'utf8');
        const compatible = target === 'windows-ia32' ? mysql57Sql : value => value;
        fs.writeFileSync(path.join(stage, 'bootstrap/seed.sql'), compatible(cleanSeed(snapshot)));
        fs.writeFileSync(path.join(stage, 'bootstrap/business-data.sql'), compatible(businessSeed(snapshot)));
        fs.writeFileSync(path.join(stage, 'bootstrap/business-data-inventory.json'), JSON.stringify(seedInventory(snapshot), null, 2));
        const migrations = fs.readdirSync(path.join(backend, 'database/migrations')).filter(name => name.endsWith('.sql')).sort();
        for (const name of migrations) fs.writeFileSync(path.join(stage, 'bootstrap/migrations', name), compatible(fs.readFileSync(path.join(backend, 'database/migrations', name), 'utf8')));
        const manifest = {schemaVersion: 1, target, databaseVersion: target === 'windows-ia32' ? '5.7.44' : '8.0', applicationVersion: require('../package.json').version, migrations, files: inventory(stage)};
        fs.writeFileSync(path.join(stage, 'manifest.json'), JSON.stringify(manifest, null, 2));
        fs.renameSync(stage, targetRoot);
        verifyIntegratedRuntime(project, target);
        console.log(`Prepared integrated runtime: ${targetRoot}`);
    } finally {
        fs.rmSync(stage, {recursive: true, force: true});
    }
}

if (require.main === module) {
    const [mysqlHome, javaHome, target] = process.argv.slice(2);
    if (!mysqlHome || !javaHome || !target) throw new Error('Usage: node scripts/prepare-integrated-runtime.cjs <mysql-home> <java21-home> <target>');
    prepareIntegratedRuntime(mysqlHome, javaHome, target);
}
module.exports = {cleanSeed, businessSeed, seedInventory, verifyIntegratedRuntime, prepareIntegratedRuntime};
