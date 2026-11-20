const path = require('node:path');

/** 整合部署的持久数据目录。MySQL5.7 不能打开 MySQL8 的物理数据目录，32位安装使用独立目录。 */
function dataDirectory(userData, architecture = process.arch) {
    return path.join(userData, architecture === 'ia32' ? 'system-win32-mysql57' : 'system');
}

/** 服务地址是否指向本机回环；只有这种地址才会启动本机后端与数据库。 */
function localAddress(value) {
    try { return ['localhost', '127.0.0.1', '[::1]'].includes(new URL(String(value).includes('://') ? value : `http://${value}`).hostname); }
    catch { return false; }
}

module.exports = {dataDirectory, localAddress};
