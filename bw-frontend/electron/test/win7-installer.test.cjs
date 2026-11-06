const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const {uninstallFileList, nsisLiteral} = require('../../scripts/build-win7-compatible-installer.cjs');

test('Win7 兼容安装器使用内置命令，不调用截图中崩溃的 System 插件', () => {
    const script = fs.readFileSync(path.resolve(__dirname, '../../build/win7-compatible-installer.nsi'), 'utf8');
    assert.doesNotMatch(script, /\b(?:System|UAC|StdUtils|nsDialogs)::/);
    assert.doesNotMatch(script, /\$"/);
    assert.match(script, /ExecWait '"\$PLUGINSDIR\\extract\.exe"/);
    assert.match(script, /RequestExecutionLevel user/);
    assert.match(script, /nip-traffic-system-win7/);
});

test('卸载清单只删除随包文件，保留用户新增文件和目录，不递归删根目录', () => {
    const script = uninstallFileList(['app.exe', 'resources/app.asar', 'bin/runtime/java.exe']);
    assert.match(script, /Delete "\$INSTDIR\\resources\\app.asar"/);
    assert.doesNotMatch(script, /RMDir \/r|APPDATA|userData|\*|Delete "\$INSTDIR"/i);
    assert.ok(script.indexOf('RMDir "$INSTDIR\\bin\\runtime"') < script.indexOf('RMDir "$INSTDIR\\bin"'));
    assert.throws(() => uninstallFileList(['../user-data/file']), /Invalid/);
    assert.equal(nsisLiteral('file$1.txt'), 'file$$1.txt');
});
