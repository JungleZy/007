const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const {configureGraphics} = require('../core/graphics');

test('Win7默认启用SwiftShader而不禁用GPU进程和软件渲染', () => {
    const switches = [];
    const app = {commandLine: {appendSwitch: (...args) => switches.push(args)}};
    assert.equal(configureGraphics(app, {platform: 'win32', release: '6.1.7601', mode: undefined}), 'software');
    assert.deepEqual(switches, [['use-gl', 'angle'], ['use-angle', 'swiftshader']]);
    const entry = fs.readFileSync(path.resolve(__dirname, '../../main.js'), 'utf8');
    assert.doesNotMatch(entry, /disableHardwareAcceleration|disable-gpu|disable-software-rasterizer/);
});

test('新版Windows和Linux使用自动渲染，也可显式选择软件兼容模式', () => {
    const switches = [];
    const app = {commandLine: {appendSwitch: (...args) => switches.push(args)}};
    for (const platform of ['win32', 'linux']) {
        assert.equal(configureGraphics(app, {platform, arch: 'x64', release: '10.0.26200', mode: undefined}), 'auto');
    }
    assert.deepEqual(switches, []);
    assert.equal(configureGraphics(app, {platform: 'linux', release: '5.4', mode: 'software'}), 'software');
    assert.equal(switches[1][1], 'swiftshader');
});

test('麒麟ARM64默认软件WebGL且允许显式选择自动渲染', () => {
    const switches = [];
    const app = {commandLine: {appendSwitch: (...args) => switches.push(args)}};
    assert.equal(configureGraphics(app, {platform: 'linux', arch: 'arm64', mode: undefined}), 'software');
    assert.deepEqual(switches, [['use-gl', 'angle'], ['use-angle', 'swiftshader']]);
    switches.length = 0;
    assert.equal(configureGraphics(app, {platform: 'linux', arch: 'arm64', mode: 'auto'}), 'auto');
    assert.deepEqual(switches, []);
});
