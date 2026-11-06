const fs = require('node:fs');
const path = require('node:path');
const net = require('node:net');
const crypto = require('node:crypto');
const {execFileSync} = require('node:child_process');
const TARGET_PORT = 6912;

function portAvailable(port) {
    return new Promise(resolve => {
        const server = net.createServer();
        server.once('error', () => resolve(false));
        server.listen({host: '127.0.0.1', port, exclusive: true}, () => server.close(() => resolve(true)));
    });
}

function processAlive(pid) {
    try { process.kill(pid, 0); return true; }
    catch (error) { if (error.code === 'ESRCH') return false; return true; }
}

async function changeSavedDatabasePort(configFile, options = {}) {
    const available = options.portAvailable || portAvailable;
    const alive = options.processAlive || processAlive;
    const original = fs.readFileSync(configFile);
    let state;
    try { state = JSON.parse(original.toString('utf8')); }
    catch { throw new Error('数据库实例配置无法解析，已停止修改'); }
    if (state.version !== 1 || typeof state.rootPassword !== 'string' || !state.rootPassword
        || typeof state.appPassword !== 'string' || !state.appPassword) throw new Error('数据库实例配置无效，已停止修改');
    if (state.port === TARGET_PORT) return {changed: false, port: TARGET_PORT, configFile};
    if (state.port !== 13306) throw new Error(`当前保存的端口为 ${state.port}，不是本次处理的旧默认端口 13306`);
    if (!(await available(TARGET_PORT))) throw new Error('6912 端口已被占用，配置未修改');
    const pidFile = path.join(path.dirname(configFile), 'mysql.pid');
    if (fs.existsSync(pidFile)) {
        const pid = Number(fs.readFileSync(pidFile, 'utf8').trim());
        if (!Number.isSafeInteger(pid) || pid <= 0 || alive(pid)) throw new Error('本应用数据库进程可能仍在运行，请正常退出软件或重启后再运行此工具');
    }
    if (options.beforeWrite) await options.beforeWrite();
    if (!fs.readFileSync(configFile).equals(original)) throw new Error('实例配置在检查期间发生变化，已停止修改，请退出软件后重试');
    const suffix = Date.now() + '-' + crypto.randomBytes(4).toString('hex');
    const backup = `${configFile}.before-port-6912-${suffix}.bak`;
    const temporary = `${configFile}.${suffix}.tmp`;
    fs.writeFileSync(backup, original, {flag: 'wx', mode: 0o600});
    try {
        fs.writeFileSync(temporary, JSON.stringify({...state, port: TARGET_PORT}, null, 2), {flag: 'wx', mode: 0o600});
        fs.renameSync(temporary, configFile);
    } finally { fs.rmSync(temporary, {force: true}); }
    return {changed: true, previousPort: state.port, port: TARGET_PORT, configFile, backup};
}

function ensureApplicationClosed() {
    const tasklist = path.join(process.env.SystemRoot || 'C:\\Windows', 'System32/tasklist.exe');
    const output = execFileSync(tasklist, ['/FI', `IMAGENAME eq ${path.basename(process.execPath)}`, '/FO', 'CSV', '/NH'], {encoding: 'utf8', windowsHide: true});
    const pids = [...output.matchAll(/^"[^"\r\n]*","(\d+)"/gm)].map(match => Number(match[1]));
    if (!pids.includes(process.pid)) throw new Error('无法确认软件进程状态，配置未修改');
    if (pids.some(pid => pid !== process.pid)) throw new Error('请先退出软件和错误弹窗，再运行端口设置工具');
}

async function main() {
    const archive = path.resolve(process.argv[2]);
    const metadata = JSON.parse(fs.readFileSync(path.join(archive, 'package.json'), 'utf8'));
    if (metadata.name !== 'nip-traffic-system' || !process.env.APPDATA) throw new Error('未识别到本软件的安装环境');
    const names = [...new Set([metadata.productName, metadata.name].filter(Boolean))];
    const candidates = names.map(name => path.join(process.env.APPDATA, name, 'system/config/database-instance.json')).filter(file => fs.existsSync(file));
    if (candidates.length !== 1) throw new Error(candidates.length ? '发现多个数据库实例配置，已停止自动修改' : '未找到数据库实例配置，请先启动软件一次，退出后再运行此工具');
    const result = await changeSavedDatabasePort(candidates[0], {beforeWrite: ensureApplicationClosed});
    fs.writeFileSync(path.join(__dirname, 'port-change-report.json'), JSON.stringify(result, null, 2));
    console.log(JSON.stringify(result, null, 2));
    console.log('PORT_6912_READY - Start the application again.');
}

if (require.main === module) main().catch(error => {
    fs.writeFileSync(path.join(__dirname, 'port-change-report.json'), JSON.stringify({success: false, message: error.message}, null, 2));
    console.error(error.message);
    process.exitCode = 1;
});
module.exports = {changeSavedDatabasePort};
