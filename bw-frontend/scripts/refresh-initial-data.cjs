const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const {cleanSeed, businessSeed, seedInventory, verifyIntegratedRuntime} = require('./prepare-integrated-runtime.cjs');
const {mysql57Sql} = require('./mysql57-bootstrap.cjs');

function refreshInitialData(target) {
    const project = path.resolve(__dirname, '..');
    const root = verifyIntegratedRuntime(project, target);
    const snapshot = fs.readFileSync(path.resolve(project, '../backend/database/project006.sql'), 'utf8');
    const manifestPath = path.join(root, 'manifest.json');
    const manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf8'));
    const compatible = target === 'windows-ia32' ? mysql57Sql : value => value;
    const files = {
        'bootstrap/seed.sql': compatible(cleanSeed(snapshot)),
        'bootstrap/business-data.sql': compatible(businessSeed(snapshot)),
        'bootstrap/business-data-inventory.json': JSON.stringify(seedInventory(snapshot), null, 2)
    };
    for (const name of manifest.migrations) {
        files[`bootstrap/migrations/${name}`] = compatible(fs.readFileSync(path.resolve(project, '../backend/database/migrations', name), 'utf8'));
    }
    for (const [relative, content] of Object.entries(files)) {
        fs.writeFileSync(path.join(root, relative), content);
        manifest.files[relative] = crypto.createHash('sha256').update(content).digest('hex');
    }
    fs.writeFileSync(manifestPath, JSON.stringify(manifest, null, 2));
    verifyIntegratedRuntime(project, target);
    const output = path.join(project, 'out');
    fs.mkdirSync(output, {recursive: true});
    fs.writeFileSync(path.join(output, '初始化业务资料补充.sql'), files['bootstrap/business-data.sql']);
    fs.writeFileSync(path.join(output, '初始化业务资料清单.json'), files['bootstrap/business-data-inventory.json']);
    console.log(JSON.stringify(seedInventory(snapshot), null, 2));
}
if (require.main === module) refreshInitialData(process.argv[2] || 'windows-x64');
module.exports = {refreshInitialData};
