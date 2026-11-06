const fs = require('node:fs');
const path = require('node:path');
const {Arch} = require('builder-util');
const {mysqlTarget} = require('../electron/shared/mysql-platforms');
const {verifyMysqlTools} = require('./bundle-mysql-tools.cjs');
const {verifyIntegratedRuntime} = require('./prepare-integrated-runtime.cjs');
const {verifyPackagedRuntime} = require('./verify-packaged-runtime.cjs');

function copyTargetTools(projectDirectory, appOutDir, target) {
    const source = verifyMysqlTools(projectDirectory, target);
    const root = path.join(appOutDir, 'bin/file/tools/mysql');
    // extraFiles 排除了整个工具库；此处只允许全新目录，避免意外带入其他架构。
    if (fs.existsSync(root) && fs.readdirSync(root).length) throw new Error(`Unexpected existing MySQL tools in package: ${root}`);
    fs.mkdirSync(root, {recursive: true});
    fs.cpSync(source, path.join(root, target), {recursive: true, errorOnExist: true, force: false});
    verifyMysqlTools(appOutDir, target);
    if (target.startsWith('linux-')) {
        for (const name of ['mysql', 'mysqldump']) fs.chmodSync(path.join(root, target, name), 0o755);
    }
    console.log(`[desktop] Included MySQL tools: ${target}`);
}

module.exports = async function stageMysqlTools(context) {
    // 先执行实际包内依赖加载，再复制大型运行组件；失败时不得生成安装包。
    await verifyPackagedRuntime(context);
    const arch = typeof context.arch === 'number' ? Arch[context.arch] : context.arch;
    const target = mysqlTarget(context.electronPlatformName, arch);
    const project = path.resolve(__dirname, '..');
    copyTargetTools(project, context.appOutDir, target);
    const source = verifyIntegratedRuntime(project, target);
    const destination = path.join(context.appOutDir, 'bin/integrated', target);
    fs.cpSync(source, destination, {recursive: true, errorOnExist: true, force: false});
    verifyIntegratedRuntime(context.appOutDir, target);
};
module.exports.copyTargetTools = copyTargetTools;
