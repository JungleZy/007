const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

function load() {
    const names = ['ttyUSB0', 'ttyACM0', 'ttyCH0', 'ttyCH341USB0', 'ttyCH9344USB0'];
    const calls = [];
    const box = {module: {exports: {}}, console, process: {platform: 'linux', env: {USER: 'tester'}},
        require: name => name === 'child_process' ? {
            exec(command, callback) {
                callback(null, command.startsWith('ls ') ? names.concat(['tty0', 'ttyCH0;id']).map(n => '/dev/' + n).join('\n') : '660 dialout');
            },
            execFileSync(executable, args) { calls.push([executable, Array.from(args)]); }
        } : name === 'fs' ? {readdirSync: () => names, realpathSync: p => p,
            statSync: () => ({isCharacterDevice: () => true})} : require(name)};
    vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../serial/nativeSerialPort.js'), 'utf8'), box);
    return {api: box.module.exports, calls, names};
}

test('Linux扫描同时列出USB、ACM和WCH驱动串口，不执行自动授权', async () => {
    const {api, calls, names} = load();
    assert.deepEqual(Array.from(await api.list(), port => port.path), names.map(n => '/dev/' + n));
    assert.equal(calls.length, 0);
});

test('WCH串口可显式授权，非法路径在提权前拒绝', () => {
    const {api, calls} = load();
    assert.equal(api.grantAccess('/dev/ttyCH341USB0').ok, true);
    assert.deepEqual(calls[1], ['pkexec', ['chmod', '666', '/dev/ttyCH341USB0']]);
    calls.length = 0;
    for (const invalid of ['/dev/ttyCH0;id', '/dev/../dev/ttyCH0', '/dev/ttyCH999', '/dev/tty0']) {
        assert.equal(api.grantAccess(invalid).ok, false);
    }
    assert.equal(calls.length, 0);
});
