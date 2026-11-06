const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const {execFileSync} = require('node:child_process');

// MySQL Windows 版本的部分文件路径仍经过本地代码页，UTF-8 中文 my.ini 路径会失败。
// 保持数据实际存放在用户目录，只给 MySQL 提供受 ACL 保护的 ASCII junction 路径。
function windowsRuntimePaths(appPath, dataPath) {
    if (process.platform !== 'win32' || /^[\x20-\x7e]+$/.test(appPath + dataPath)) {
        return {appPath, dataPath, dispose() {}};
    }
    const sid = execFileSync('whoami.exe', ['/user', '/fo', 'csv', '/nh'], {encoding: 'utf8', windowsHide: true})
        .match(/S-1-5-\d+(?:-\d+)+/)?.[0];
    if (!sid) throw new Error('无法获取当前用户标识，不能准备数据库目录');
    const identity = crypto.createHash('sha256').update(sid + path.resolve(dataPath)).digest('hex').slice(0, 24);
    const base = process.env.ProgramData || path.join(process.env.SystemDrive || 'C:', 'ProgramData');
    const root = path.join(base, 'NipTrafficRuntimeLinks', identity);
    if (!/^[\x20-\x7e]+$/.test(root)) throw new Error('数据库兼容路径必须位于英文系统目录');
    fs.mkdirSync(root, {recursive: true});
    execFileSync('icacls.exe', [root, '/inheritance:r', '/grant:r',
        `*${sid}:(OI)(CI)F`, '*S-1-5-18:(OI)(CI)F', '*S-1-5-32-544:(OI)(CI)F'],
    {stdio: 'ignore', windowsHide: true});
    const aliases = [];
    for (const [name, target] of [['app', appPath], ['data', dataPath]]) {
        const alias = path.join(root, name);
        try {
            const stat = fs.lstatSync(alias);
            if (!stat.isSymbolicLink()) throw new Error('数据库兼容目录已被其他文件占用');
            fs.unlinkSync(alias);
        } catch (error) { if (error.code !== 'ENOENT') throw error; }
        fs.symlinkSync(path.resolve(target), alias, 'junction');
        aliases.push(alias);
    }
    let disposed = false;
    return {
        appPath: path.join(root, 'app'), dataPath: path.join(root, 'data'),
        dispose() {
            if (disposed) return;
            disposed = true;
            for (const alias of aliases) {
                try { if (fs.lstatSync(alias).isSymbolicLink()) fs.unlinkSync(alias); }
                catch (error) { if (error.code !== 'ENOENT') throw error; }
            }
            try { fs.rmdirSync(root); } catch (error) { if (error.code !== 'ENOENT' && error.code !== 'ENOTEMPTY') throw error; }
        }
    };
}

module.exports = {windowsRuntimePaths};
