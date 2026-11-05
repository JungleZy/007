const path = require('node:path');
const {mysqlTarget} = require('../shared/mysql-platforms');

function backendEnvironment(appPath, platform = process.platform, environment = process.env, architecture = process.arch) {
    const env = {...environment};
    if (platform === 'win32' || platform === 'linux') {
        const target = mysqlTarget(platform, architecture);
        const directory = path.join(appPath, 'bin', 'file', 'tools', 'mysql', target);
        const suffix = platform === 'win32' ? '.exe' : '';
        env.NIP_DATABASE_MYSQL_PATH ||= path.join(directory, `mysql${suffix}`);
        env.NIP_DATABASE_MYSQLDUMP_PATH ||= path.join(directory, `mysqldump${suffix}`);
    }
    return env;
}

module.exports = {backendEnvironment};
