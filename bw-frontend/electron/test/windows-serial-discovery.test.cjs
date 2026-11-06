const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

function loadNative(execFile) {
    const box = {module: {exports: {}}, process: {platform: 'win32', env: {SystemRoot: 'C:\\Windows'}},
        require: name => name === 'child_process' ? {execFile} : require(name)};
    vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../serial/nativeSerialPort.js'), 'utf8'), box);
    return box.module.exports;
}

test('Win7枚举全部登记COM口、去重并按编号排序，不依赖WMI或显示名称', async () => {
    const api = loadNative((exe, args, options, callback) => {
        assert.match(exe, /reg\.exe$/);
        assert.equal(args[1], 'HKLM\\HARDWARE\\DEVICEMAP\\SERIALCOMM');
        assert.equal(options.timeout, 8000);
        callback(null, '\r\nHKEY_LOCAL_MACHINE\\HARDWARE\\DEVICEMAP\\SERIALCOMM\r\n'
            + '    \\Device\\Serial0    REG_SZ    COM1\r\n'
            + '    \\Device\\USBSER000    REG_SZ    COM12\r\n'
            + '    \\Device\\BthModem0    REG_SZ    COM4\r\n'
            + '    \\Device\\duplicate    REG_SZ    com12\r\n');
    });
    assert.deepEqual(Array.from(await api.list()), ['COM1', 'COM4', 'COM12']);
});

test('无串口时返回空列表，查询超时会结束而不永久等待', async () => {
    assert.equal((await loadNative((e,a,o,cb)=>cb({code: 1}, '')).list()).length, 0);
    await assert.rejects(loadNative((e,a,o,cb)=>cb({killed: true}, '')).list(), /超时/);
});

async function select(ports, saved) {
    const notices = [];
    const box = {module: {exports: {}}, console: {warn() {}}, require: name => name === 'electron'
        ? {dialog: {showErrorBox: (...args)=>notices.push(args)}}
        : {db: {findOne: async()=>saved ? {text: saved} : null}}};
    vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../serial/index.js'), 'utf8')
        + '\nmodule.exports.selectForTest = onSelectSerialPort;', box);
    let selected;
    await box.module.exports.selectForTest({preventDefault() {}}, ports, {}, value=>selected=value);
    return {selected, notices};
}

test('多个串口时按用户COM号选择，不误连其他端口；失效选择不会自动跳转', async () => {
    const ports = [{portId:'onboard',portName:'COM1'}, {portId:'ch340',portName:'COM12',vendorId:'1a86'},
        {portId:'another',portName:'COM4',displayName:'USB Serial'}];
    assert.equal((await select(ports, 'com12')).selected, 'ch340');
    assert.equal((await select(ports, 'COM9')).selected, '');
    assert.equal((await select(ports, '')).selected, '');
    assert.equal((await select(ports.slice(0,2), '')).selected, 'ch340');
});
