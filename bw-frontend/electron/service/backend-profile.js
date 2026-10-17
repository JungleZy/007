'use strict';

const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const http = require('node:http');
const {BACKEND_PORT} = require('../shared/ports');

class BackendStartupError extends Error {}
const fail = message => new BackendStartupError(message);

function hashBinary(filename) {
  const hash = crypto.createHash('sha256');
  const buffer = Buffer.allocUnsafe(1024 * 1024);
  const fd = fs.openSync(filename, 'r');
  try {
    let count;
    while ((count = fs.readSync(fd, buffer, 0, buffer.length, null))) hash.update(buffer.subarray(0, count));
  } finally { fs.closeSync(fd); }
  return hash.digest('hex');
}

function loadBackendProfile(serverPath, required = false) {
  const filename = path.join(path.dirname(serverPath), 'backend.json');
  try { fs.lstatSync(filename); } catch (error) {
    if (error.code === 'ENOENT' && !required) return null; // Existing Java packages deliberately have no profile.
    throw fail('无法读取后端启动配置，请重新安装完整的个人版安装包。');
  }
  try {
    if (!fs.lstatSync(filename).isFile() || fs.statSync(filename).size > 16384) throw new Error();
    const profile = JSON.parse(fs.readFileSync(filename, 'utf8'));
    if (!profile || Object.keys(profile).sort().join(',') !== 'binarySha256,featureFlags,kind,schemaVersion' ||
        profile.schemaVersion !== 1 || profile.kind !== 'rust' || !/^[a-f0-9]{64}$/.test(profile.binarySha256) ||
        !Array.isArray(profile.featureFlags) || !profile.featureFlags.length ||
        profile.featureFlags.some(flag => typeof flag !== 'string' || !/^RUST_PHASE[A-Z0-9_]+_ENABLED$/.test(flag)) ||
        new Set(profile.featureFlags).size !== profile.featureFlags.length) throw new Error();
    if (!fs.lstatSync(serverPath).isFile() || hashBinary(serverPath) !== profile.binarySha256) throw new Error();
    return profile;
  } catch {
    throw fail('Rust 后端配置或二进制校验失败，请重新安装完整的个人版安装包；不会回退到 Java。');
  }
}

function rustEnvironment(profile, inherited = process.env) {
  const env = {...inherited};
  // Windows environment names are case-insensitive; remove aliases before setting package-owned values.
  for (const key of Object.keys(env)) {
    if (/^RUST_BACKEND_ADDR$/i.test(key) || /^RUST_PHASE.*_ENABLED$/i.test(key)) delete env[key];
  }
  const databaseKeys = Object.keys(env).filter(key => /^RUST_BACKEND_DATABASE_URL$/i.test(key));
  const databaseUrl = env.RUST_BACKEND_DATABASE_URL ?? env[databaseKeys[0]];
  if (typeof databaseUrl !== 'string' || !databaseUrl.trim()) {
    throw fail('未设置 RUST_BACKEND_DATABASE_URL。请在启动本应用的环境中设置个人 MySQL 连接串后重启；不要将密码写入安装目录或启动配置。');
  }
  try {
    const parsed = new URL(databaseUrl);
    if (parsed.protocol !== 'mysql:' || !parsed.hostname) throw new Error();
  } catch {
    throw fail('RUST_BACKEND_DATABASE_URL 必须是有效的 MySQL 连接串；请检查配置，不要把密码写入安装包。');
  }
  for (const key of databaseKeys) delete env[key];
  env.RUST_BACKEND_DATABASE_URL = databaseUrl;
  env.RUST_BACKEND_ADDR = `127.0.0.1:${BACKEND_PORT}`;
  for (const flag of profile.featureFlags) env[flag] = 'true';
  return env;
}

function startupMessage(error) {
  // Never forward child stderr, OS errors, URLs, or user-supplied configuration to logs/dialogs.
  return error instanceof BackendStartupError ? error.message : '无法安全初始化用户数据或启动服务。请检查用户数据目录权限及安装完整性。';
}
function forwardRustDiagnostics(stream, databaseUrl, output) {
  const url = new URL(databaseUrl);
  const values = new Set([databaseUrl, url.href]);
  for (const value of [url.username, url.password]) {
    if (!value) continue;
    values.add(value);
    try { values.add(decodeURIComponent(value)); } catch {}
  }
  for (const value of [...values]) values.add(JSON.stringify(value).slice(1, -1));
  const secrets = [...values].filter(Boolean).sort((left, right) => right.length - left.length);
  let pending = '';
  const emit = text => {
    text = text.replace(/mysql:\/\/\S+/gi, '[database-url]');
    for (const secret of secrets) text = text.replaceAll(secret, '[redacted]');
    if (text) output(`[backend] ${text}`);
  };
  stream.setEncoding('utf8');
  stream.on('data', chunk => {
    pending += chunk;
    const end = pending.lastIndexOf('\n');
    if (end < 0) return;
    emit(pending.slice(0, end));
    pending = pending.slice(end + 1);
  });
  stream.on('end', () => { emit(pending); pending = ''; });
}


function waitForRustStartup(child, port = BACKEND_PORT, timeout = 60000) {
  return new Promise((resolve, reject) => {
    let request;
    let retry;
    let settled = false;
    const finish = error => {
      if (settled) return;
      settled = true;
      clearTimeout(deadline);
      clearTimeout(retry);
      if (request) request.destroy();
      child.removeListener('error', onError);
      child.removeListener('exit', onExit);
      if (error && child.exitCode === null && child.signalCode === null) {
        const force = setTimeout(() => child.kill('SIGKILL'), 2000);
        child.once('exit', () => { clearTimeout(force); reject(error); });
        child.kill();
      } else if (error) reject(error);
      else resolve(child);
    };
    const onError = () => finish(fail('无法启动 Rust 后端，请检查安装完整性和系统执行权限。'));
    const onExit = () => finish(fail('Rust 后端在启动期间退出。请检查个人 MySQL 连接配置及数据库可用性。'));
    const deadline = setTimeout(() => finish(fail('Rust 后端启动超时。请检查个人 MySQL 连接配置及数据库可用性。')), timeout);
    child.once('error', onError);
    child.once('exit', onExit);
    const probe = () => {
      if (settled) return;
      if (child.exitCode !== null || child.signalCode !== null) return onExit();
      request = http.get({host: '127.0.0.1', port, path: '/ready', timeout: 1000}, response => {
        let body = '';
        response.on('data', chunk => { if (body.length < 4096) body += chunk; });
        response.on('end', () => {
          let ready = false;
          try { const value = JSON.parse(body); ready = response.statusCode === 200 && value.status === 'ready' && value.dependencies === true; } catch {}
          if (ready) finish();
          else if (!settled) retry = setTimeout(probe, 200);
        });
        response.on('error', () => { if (!settled) retry = setTimeout(probe, 200); });
      });
      request.on('timeout', () => request.destroy());
      request.on('error', () => { if (!settled) retry = setTimeout(probe, 200); });
    };
    probe();
  });
}

module.exports = {BackendStartupError, hashBinary, loadBackendProfile, rustEnvironment, startupMessage, forwardRustDiagnostics, waitForRustStartup};
