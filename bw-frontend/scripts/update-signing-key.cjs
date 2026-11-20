/**
 * 生成升级包签名密钥对（Ed25519）。
 *
 *   node scripts/update-signing-key.cjs <私钥输出路径>
 *
 * 私钥必须放在仓库之外并离线备份；丢失后只能靠完整安装包更换公钥。
 * 命令输出公钥 PEM，需手工加入 electron/shared/update-keys.js 的 PUBLIC_KEYS。
 */
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');

function generate(output) {
    const repository = path.resolve(__dirname, '../..');
    const target = path.resolve(output);
    if (!path.relative(repository, target).startsWith('..')) throw new Error('私钥不能放在仓库目录内');
    if (fs.existsSync(target)) throw new Error(`私钥文件已存在，不覆盖：${target}`);
    const {publicKey, privateKey} = crypto.generateKeyPairSync('ed25519');
    fs.mkdirSync(path.dirname(target), {recursive: true, mode: 0o700});
    fs.writeFileSync(target, privateKey.export({type: 'pkcs8', format: 'pem'}), {mode: 0o600, flag: 'wx'});
    return publicKey.export({type: 'spki', format: 'pem'});
}

if (require.main === module) {
    const [output] = process.argv.slice(2);
    if (!output) throw new Error('Usage: node scripts/update-signing-key.cjs <private-key-output-path>');
    process.stdout.write(generate(output));
}
module.exports = {generate};
