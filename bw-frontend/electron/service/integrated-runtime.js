const fs = require('node:fs');
const path = require('node:path');
const net = require('node:net');
const http = require('node:http');
const crypto = require('node:crypto');
const {spawn} = require('node:child_process');
const {mysqlTarget} = require('../shared/mysql-platforms');
const {backendEnvironment} = require('./mysql-tools');
const {BACKEND_PORT, DATABASE_PORT, PREVIOUS_DATABASE_PORT} = require('../shared/ports');
const {windowsRuntimePaths} = require('./windows-runtime-paths');

const delay = milliseconds => new Promise(resolve => setTimeout(resolve, milliseconds));
const sqlString = value => "'" + String(value).replace(/\\/g, '\\\\').replace(/'/g, "''") + "'";
const hash = value => crypto.createHash('sha256').update(value).digest('hex');

function atomicJson(file, data) {
    const temporary = file + '.tmp';
    fs.writeFileSync(temporary, JSON.stringify(data, null, 2), {mode: 0o600});
    fs.renameSync(temporary, file);
}

function passwordHash(password) {
    const salt = crypto.randomBytes(16);
    const key = crypto.pbkdf2Sync(password, salt, 600000, 32, 'sha256');
    return `pbkdf2_sha256$1$600000$${salt.toString('base64url')}$${key.toString('base64url')}`;
}

function portFree(port) {
    return new Promise(resolve => {
        const server = net.createServer();
        server.once('error', () => resolve(false));
        server.listen({host: '127.0.0.1', port, exclusive: true}, () => server.close(() => resolve(true)));
    });
}

function backendReady(port) {
    return new Promise(resolve => {
        const request = http.get({host: '127.0.0.1', port, path: '/api/database/status', timeout: 2000}, response => {
            let body = '';
            response.on('data', data => { if (body.length < 4096) body += data; });
            response.on('end', () => {
                try { resolve(response.statusCode === 200 && JSON.parse(body).code === 203); }
                catch { resolve(false); }
            });
        });
        request.on('timeout', () => request.destroy());
        request.on('error', () => resolve(false));
    });
}

function waitForExit(child, timeout) {
    if (child.exitCode !== null || child.signalCode) return Promise.resolve();
    return new Promise((resolve, reject) => {
        const onClose = () => { clearTimeout(timer); resolve(); };
        const timer = setTimeout(() => {
            child.removeListener('close', onClose);
            reject(new Error('服务仍在关闭，请稍后再启动'));
        }, timeout);
        child.once('close', onClose);
    });
}

class IntegratedRuntime {
    constructor({appPath, dataPath, databasePort = DATABASE_PORT, backendPort = BACKEND_PORT, onProgress = () => {}, startBackend = true}) {
        this.appPath = appPath;
        this.dataPath = dataPath;
        this.databasePort = databasePort;
        this.requestedDatabasePort = databasePort;
        this.backendPort = backendPort;
        this.onProgress = onProgress;
        this.startBackend = startBackend;
        this.target = mysqlTarget(process.platform, process.arch);
        this.mysql57 = this.target === 'windows-ia32';
        this.collation = this.mysql57 ? 'utf8mb4_general_ci' : 'utf8mb4_0900_ai_ci';
        this.assets = path.join(appPath, 'bin/integrated', this.target);
        this.tools = path.join(appPath, 'bin/file/tools/mysql', this.target);
        this.suffix = process.platform === 'win32' ? '.exe' : '';
        this.stateFile = path.join(dataPath, 'config/database-instance.json');
        this.datadir = path.join(dataPath, 'mysql');
        this.operations = new Set();
        this.stopping = false;
        this.ownsDatabase = false;
    }

    log(message) {
        let safe = String(message);
        for (const secret of [this.state?.rootPassword, this.state?.appPassword, this.state?.adminPassword]) {
            if (secret) safe = safe.split(secret).join('[redacted]');
        }
        fs.appendFileSync(path.join(this.dataPath, 'logs/startup.log'), `${new Date().toISOString()} ${safe}\n`);
    }

    progress(message) { this.log(message); this.onProgress(message); }
    active() { if (this.stopping) throw new Error('应用正在关闭'); }

    environment(executable, extra = {}) {
        const env = {...process.env, ...extra};
        if (process.platform === 'win32') {
            // 随 Java 运行环境提供的 VC 运行库同时供 MySQL 使用，客户无需单独配置 PATH。
            env.PATH = path.join(this.assets, 'runtime/bin') + path.delimiter + (env.PATH || '');
        }
        if (process.platform === 'linux') {
            const directory = path.dirname(executable);
            const libraries = [path.join(directory, 'lib'), path.join(directory, '../lib/private'), path.join(directory, '../lib')]
                .filter(file => fs.existsSync(file));
            env.LD_LIBRARY_PATH = [...libraries, env.LD_LIBRARY_PATH].filter(Boolean).join(':');
        }
        return env;
    }

    run(executable, args, {input = '', env = {}, timeout = 120000, outputFile, allowStop = false} = {}) {
        if (!allowStop) this.active();
        return new Promise((resolve, reject) => {
            let fd;
            try { if (outputFile) fd = fs.openSync(outputFile, 'wx', 0o600); }
            catch (error) { reject(error); return; }
            const child = spawn(executable, args, {
                cwd: this.dataPath, windowsHide: true, env: this.environment(executable, env),
                stdio: ['pipe', fd === undefined ? 'pipe' : fd, 'pipe']
            });
            if (fd !== undefined) fs.closeSync(fd);
            this.operations.add(child);
            let output = '', errors = '', timedOut = false;
            child.stdout?.on('data', data => { if (output.length < 1024 * 1024) output += data; });
            child.stderr.on('data', data => { if (errors.length < 16384) errors += data; });
            const timer = setTimeout(() => { timedOut = true; child.kill(); }, timeout);
            child.once('error', error => { clearTimeout(timer); this.operations.delete(child); reject(error); });
            child.once('close', code => {
                clearTimeout(timer);
                this.operations.delete(child);
                if (code === 0 && !timedOut) resolve(output.trim());
                else {
                    this.log(errors);
                    reject(new Error(`${path.basename(executable)} ${timedOut ? '执行超时' : '执行失败'}，详见启动日志`));
                }
            });
            child.stdin.on('error', () => {});
            child.stdin.end(input);
        });
    }

    sql(statement, {database = false, allowStop = false, timeout = 120000} = {}) {
        return this.run(path.join(this.tools, `mysql${this.suffix}`), [
            '--no-defaults', '--protocol=TCP', '--host=127.0.0.1', `--port=${this.databasePort}`,
            '--user=root', '--connect-timeout=2', '--default-character-set=utf8mb4',
            '--batch', '--raw', '--skip-column-names', '--binary-mode=1', '--local-infile=0'
        ], {input: (database ? 'USE project006;\n' : '') + statement, env: {MYSQL_PWD: this.state.rootPassword}, allowStop, timeout});
    }

    sameDatabaseDirectory(actual) {
        try {
            const normalize = value => {
                const resolved = fs.realpathSync(value).replace(/\\/g, '/');
                return process.platform === 'win32' ? resolved.toLowerCase() : resolved;
            };
            return !!actual && normalize(actual) === normalize(this.datadir);
        } catch { return false; }
    }

    async ownedDatabasePid() {
        const pidFile = await this.sql('SELECT @@pid_file;', {timeout: 5000});
        const relative = path.relative(fs.realpathSync(this.dataPath), fs.realpathSync(pidFile));
        if (relative.startsWith('..') || path.isAbsolute(relative)) throw new Error('已有数据库进程标识不在应用数据目录内');
        const pid = Number(fs.readFileSync(pidFile, 'utf8').trim());
        if (!Number.isSafeInteger(pid) || pid <= 0) throw new Error('已有数据库进程标识无效');
        return pid;
    }

    async migrateDatabasePort(nextPort) {
        if (this.databasePort === nextPort) return;
        if (!(await portFree(nextPort))) throw new Error(`新的数据库端口 ${nextPort} 已被占用，原配置未修改`);
        if (!(await portFree(this.databasePort))) {
            const actual = await this.sql('SELECT @@datadir;', {timeout: 5000}).catch(() => '');
            if (this.sameDatabaseDirectory(actual)) {
                this.databasePid = await this.ownedDatabasePid();
                this.ownsDatabase = true;
                this.progress('正在关闭本应用的旧端口数据库实例');
                await this.shutdown();
                this.databasePid = null;
            }
        }
        this.active();
        const updated = {...this.state, port: nextPort};
        atomicJson(this.stateFile, updated);
        this.state = updated;
        this.databasePort = nextPort;
        this.progress(`数据库端口已更新为 ${nextPort}`);
    }

    start() {
        this.startTask = this.initialize().catch(async error => { await this.shutdown().catch(() => {}); throw error; });
        return this.startTask;
    }

    async initialize() {
        for (const name of ['config', 'logs', 'backups', 'files']) fs.mkdirSync(path.join(this.dataPath, name), {recursive: true});
        this.progress('正在检查运行组件');
        this.manifest = JSON.parse(fs.readFileSync(path.join(this.assets, 'manifest.json'), 'utf8'));
        if (this.manifest.target !== this.target) throw new Error('运行组件架构不匹配');
        if (this.mysql57 && this.manifest.databaseVersion !== '5.7.44') throw new Error('32位数据库组件版本不匹配');
        this.mysqlPaths = windowsRuntimePaths(this.appPath, this.dataPath);
        this.mysqlAssets = path.join(this.mysqlPaths.appPath, 'bin/integrated', this.target);
        this.tools = path.join(this.mysqlPaths.appPath, 'bin/file/tools/mysql', this.target);
        this.mysqld = path.join(this.mysqlAssets, `database/bin/mysqld${this.suffix}`);
        this.java = path.join(this.assets, `runtime/bin/java${this.suffix}`);
        for (const file of [this.mysqld, this.java, path.join(this.assets, 'backend/quarkus-run.jar'), path.join(this.tools, `mysql${this.suffix}`)]) fs.accessSync(file);
        if (this.startBackend && !(await portFree(this.backendPort))) throw new Error(`后端端口 ${this.backendPort} 已被其他程序占用`);
        if (fs.existsSync(this.stateFile)) {
            this.state = JSON.parse(fs.readFileSync(this.stateFile, 'utf8'));
            if (this.state.version !== 1 || !this.state.rootPassword || !this.state.appPassword
                || !Number.isInteger(this.state.port) || this.state.port < 1 || this.state.port > 65535) throw new Error('数据库实例配置无效，已保留原数据');
            this.databasePort = this.state.port;
        } else {
            if (fs.existsSync(this.datadir) && fs.readdirSync(this.datadir).length) throw new Error('发现已有数据库文件但缺少实例配置，已停止初始化以保护数据');
            this.state = {version: 1, phase: 'new', port: this.databasePort,
                rootPassword: crypto.randomBytes(32).toString('hex'), appPassword: crypto.randomBytes(32).toString('hex'),
                adminPassword: '123456a'};
            atomicJson(this.stateFile, this.state);
        }
        this.active();
        if (this.databasePort === PREVIOUS_DATABASE_PORT && this.requestedDatabasePort === DATABASE_PORT) {
            await this.migrateDatabasePort(DATABASE_PORT);
        }
        const free = await portFree(this.databasePort);
        if (!free) {
            const actual = await this.sql('SELECT @@datadir;').catch(() => '');
            if (!this.sameDatabaseDirectory(actual)) throw new Error(`数据库端口 ${this.databasePort} 被其他实例占用，未修改该实例`);
            this.databasePid = await this.ownedDatabasePid();
            this.ownsDatabase = true;
            this.progress('已连接本应用的数据库实例');
        } else {
            const configFile = path.join(this.mysqlPaths.dataPath, 'config/my.ini');
            const quotePath = value => '"' + value.replace(/\\/g, '/').replace(/"/g, '\\"') + '"';
            const socketOption = process.platform === 'linux'
                ? `socket=${quotePath(path.join(this.mysqlPaths.dataPath, 'config/mysql.sock'))}\n` : '';
            fs.writeFileSync(configFile, `[mysqld]\nbasedir=${quotePath(path.join(this.mysqlAssets, 'database'))}\ndatadir=${quotePath(path.join(this.mysqlPaths.dataPath, 'mysql'))}\npid-file=${quotePath(path.join(this.mysqlPaths.dataPath, 'config/mysql.pid'))}\n${socketOption}port=${this.databasePort}\nbind-address=127.0.0.1\n${this.mysql57 ? '' : 'mysqlx=0\n'}character-set-server=utf8mb4\ncollation-server=${this.collation}\ninnodb-buffer-pool-size=128M\nmax-allowed-packet=64M\n`, {mode: 0o600});
            if (!fs.existsSync(path.join(this.datadir, 'mysql'))) {
                if (fs.existsSync(this.datadir) && fs.readdirSync(this.datadir).length) throw new Error('数据库首次初始化未完成，请查看日志；已有文件未被删除');
                this.progress('首次启动：正在初始化数据库');
                await this.run(this.mysqld, [`--defaults-file=${configFile}`, '--initialize-insecure'], {timeout: 180000});
            }
            this.active();
            const args = [`--defaults-file=${configFile}`];
            this.rootInit = path.join(this.mysqlPaths.dataPath, 'config/root-init.sql');
            if (this.state.phase === 'new') {
                fs.writeFileSync(this.rootInit,
                    `ALTER USER 'root'@'localhost' IDENTIFIED BY ${sqlString(this.state.rootPassword)};\n`, {mode: 0o600});
                args.push(`--init-file=${this.rootInit}`);
            }
            if (process.platform === 'win32') args.push('--console', ...(this.mysql57 ? ['--log-syslog=0'] : ['--no-monitor']));
            this.database = spawn(this.mysqld, args, {cwd: this.dataPath, windowsHide: true, env: this.environment(this.mysqld), stdio: ['ignore', 'pipe', 'pipe']});
            this.ownsDatabase = true;
            this.database.stdout.on('data', data => this.log(data));
            this.database.stderr.on('data', data => this.log(data));
            this.database.on('error', error => { this.databaseError = error; });
            this.progress('正在等待数据库就绪');
            await this.wait(async () => {
                if (this.databaseError || this.database.exitCode !== null) throw new Error('数据库启动失败，请查看启动日志');
                return this.sql('SELECT 1;').then(value => value === '1', () => false);
            }, 90000);
            this.ownsDatabase = true;
            fs.rmSync(this.rootInit, {force: true});
        }
        const firstBoot = this.state.phase !== 'ready';
        await this.sql(`CREATE DATABASE IF NOT EXISTS project006 CHARACTER SET utf8mb4 COLLATE ${this.collation};`);
        await this.sql('CREATE TABLE IF NOT EXISTS nip_desktop_migrations (id varchar(128) PRIMARY KEY, digest char(64) NOT NULL);', {database: true});
        if (firstBoot) {
            this.progress('正在创建业务表和基础设置');
            await this.sql(this.assetText('bootstrap/seed.sql'), {database: true, timeout: 900000});
        }
        const pending = [];
        for (const name of this.manifest.migrations) {
            const sql = this.assetText(`bootstrap/migrations/${name}`);
            const digest = hash(sql);
            const applied = await this.sql(`SELECT digest FROM nip_desktop_migrations WHERE id=${sqlString(name)};`, {database: true});
            if (applied && applied !== digest) throw new Error(`已执行的迁移文件发生变化：${name}，请检查升级包`);
            if (!applied) pending.push({name, sql, digest});
        }
        if (!firstBoot && pending.length) {
            this.progress('升级前正在备份现有数据库');
            const backup = path.join(this.dataPath, 'backups', `before-upgrade-${Date.now()}.sql`);
            await this.run(path.join(this.tools, `mysqldump${this.suffix}`), ['--no-defaults', '--protocol=TCP', '--host=127.0.0.1',
                `--port=${this.databasePort}`, '--user=root', '--single-transaction', '--quick', '--hex-blob', '--no-tablespaces', '--set-gtid-purged=OFF', 'project006'],
                {env: {MYSQL_PWD: this.state.rootPassword}, outputFile: backup, timeout: 900000});
        }
        for (const migration of pending) {
            this.log(`Migration: ${migration.name}`);
            this.progress('正在更新数据库设置');
            await this.sql(migration.sql, {database: true});
            await this.sql(`INSERT INTO nip_desktop_migrations VALUES (${sqlString(migration.name)},${sqlString(migration.digest)});`, {database: true});
        }
        if (firstBoot) {
            await this.sql(`CREATE USER IF NOT EXISTS 'nip_app'@'localhost' IDENTIFIED BY ${sqlString(this.state.appPassword)};
GRANT ALL PRIVILEGES ON project006.* TO 'nip_app'@'localhost';
USE project006;
INSERT IGNORE INTO t_role (id,title,is_admin,is_default) VALUES ('1','系统管理员',0,1);
INSERT IGNORE INTO t_user (id,user_account,user_name,password,status) VALUES ('desktop-admin','admin','系统管理员',${sqlString(passwordHash(this.state.adminPassword))},0);
INSERT IGNORE INTO t_user_role (id,user_id,role_id) VALUES ('desktop-admin','desktop-admin','1');`);
            this.state.phase = 'ready';
            atomicJson(this.stateFile, this.state);
        }
        this.active();
        if (this.startBackend) {
            this.progress('正在启动业务服务');
            const env = backendEnvironment(this.mysqlPaths.appPath);
            Object.assign(env, {DB_USER: 'nip_app', DB_PASSWORD: this.state.appPassword,
                QUARKUS_DATASOURCE_JDBC_URL: `jdbc:mysql://127.0.0.1:${this.databasePort}/project006?rewriteBatchedStatements=true&characterEncoding=utf8&useSSL=true&allowPublicKeyRetrieval=true`,
                QUARKUS_HTTP_PORT: String(this.backendPort), QUARKUS_HTTP_HOST: '0.0.0.0'});
            if (this.mysql57) Object.assign(env, {NIP_DATABASE_CLIENT_MAJOR: '5', QUARKUS_DATASOURCE_DB_VERSION: '5.7'});
            this.backend = spawn(this.java, [...(this.mysql57 ? ['-Xms64m', '-Xmx512m', '-XX:MaxMetaspaceSize=192m'] : []), '-jar', path.join(this.assets, 'backend/quarkus-run.jar')], {
                cwd: this.dataPath, windowsHide: true, env: this.environment(this.java, env), stdio: ['ignore', 'pipe', 'pipe']
            });
            this.backend.stdout.on('data', data => this.log(data));
            this.backend.stderr.on('data', data => this.log(data));
            this.backend.on('error', error => { this.backendError = error; });
            await this.wait(async () => {
                if (this.backendError || this.backend.exitCode !== null) throw new Error('业务服务启动失败，请查看启动日志');
                return backendReady(this.backendPort);
            }, 120000);
        }
        this.progress('本地服务已就绪');
        return this.state.adminPassword ? {account: 'admin', password: this.state.adminPassword} : null;
    }

    assetText(relative) {
        const content = fs.readFileSync(path.join(this.assets, relative), 'utf8');
        if (hash(content) !== this.manifest.files[relative]) throw new Error(`初始化资源校验失败：${relative}`);
        return content;
    }

    async wait(check, timeout) {
        const end = Date.now() + timeout;
        while (Date.now() < end) {
            this.active();
            if (await check()) return;
            await delay(500);
        }
        throw new Error('等待服务启动超时，请查看启动日志');
    }

    acknowledgeInitialAdmin() {
        delete this.state.adminPassword;
        atomicJson(this.stateFile, this.state);
    }

    async shutdown() {
        if (this.backend && this.backend.exitCode === null) {
            const child = this.backend;
            child.kill();
            await waitForExit(child, 10000);
        }
        if (this.ownsDatabase) {
            if (this.database && this.database.exitCode !== null) {
                this.ownsDatabase = false;
                return;
            }
            await this.sql('SHUTDOWN;', {allowStop: true});
            // MySQL 先关闭监听端口，再释放 InnoDB 文件；必须等待进程退出才能重启/删除数据目录。
            if (this.database) await waitForExit(this.database, 30000);
            else if (this.databasePid) {
                const deadline = Date.now() + 30000;
                const alive = () => {
                    try { process.kill(this.databasePid, 0); return true; }
                    catch (error) { if (error.code === 'ESRCH') return false; throw error; }
                };
                while (Date.now() < deadline && alive()) await delay(250);
                if (alive()) throw new Error('已有数据库进程仍在关闭，请稍后再启动');
            }
            const end = Date.now() + 30000;
            while (Date.now() < end && !(await portFree(this.databasePort))) await delay(250);
            if (!(await portFree(this.databasePort))) throw new Error('数据库尚未完全关闭，请查看日志');
            this.ownsDatabase = false;
        }
    }

    async stop() {
        this.stopping = true;
        for (const child of this.operations) child.kill();
        await this.startTask?.catch(() => {});
        await this.shutdown();
        this.mysqlPaths?.dispose();
    }
}

module.exports = {IntegratedRuntime, passwordHash, portFree, backendReady};
