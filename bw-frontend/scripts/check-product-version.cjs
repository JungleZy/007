/**
 * 产品版本号只有一个来源：bw-frontend/package.json。
 * 前端、锁文件、后端 pom 必须与它一致；application.yml 不得另写字面版本。
 * 组网时客户端按版本号与服务端比对（升级规格 U01、U08），版本各说各话会让机器互相拒绝。
 */
const fs = require('node:fs');
const path = require('node:path');

function declaredVersions(repository) {
    const read = file => fs.readFileSync(path.join(repository, file), 'utf8');
    const json = file => JSON.parse(read(file));
    const pom = /<artifactId>[^<]+<\/artifactId>\s*<version>([^<]+)<\/version>/.exec(read('backend/pom.xml'));
    const openapi = /@Info\([^)]*version = "([^"]+)"/.exec(read('backend/src/main/java/com/nip/common/MainApplication.java'));
    const yml = /^version:\s*(.+?)\s*$/m.exec(read('backend/src/main/resources/application.yml'));
    return {
        product: json('bw-frontend/package.json').version,
        declared: [
            ['bw-frontend/package-lock.json', json('bw-frontend/package-lock.json').version],
            ['bw-frontend/frontend/package.json', json('bw-frontend/frontend/package.json').version],
            ['bw-frontend/frontend/package-lock.json', json('bw-frontend/frontend/package-lock.json').version],
            ['backend/pom.xml', pom && pom[1]],
            ['backend/src/main/java/com/nip/common/MainApplication.java (OpenAPI)', openapi && openapi[1]]
        ],
        applicationYml: yml && yml[1]
    };
}

function checkProductVersion(repository = path.resolve(__dirname, '../..')) {
    const {product, declared, applicationYml} = declaredVersions(repository);
    if (!/^\d+\.\d+\.\d+$/.test(product)) throw new Error(`产品版本号必须是三段数字：bw-frontend/package.json 声明 ${product}`);
    const problems = declared.filter(([, version]) => version !== product).map(([file, version]) => `${file} 声明 ${version}`);
    if (applicationYml !== '${quarkus.application.version}') {
        problems.push(`backend/src/main/resources/application.yml 的 version 必须写成 \${quarkus.application.version}，当前为 ${applicationYml}`);
    }
    if (problems.length) throw new Error(`产品版本不一致，应统一为 ${product}：\n  ${problems.join('\n  ')}`);
    return product;
}

if (require.main === module) {
    try { console.log(`产品版本一致：${checkProductVersion()}`); }
    catch (error) { console.error(error.message); process.exitCode = 1; }
}
module.exports = {checkProductVersion};
