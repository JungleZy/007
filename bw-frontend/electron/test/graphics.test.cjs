const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const EventEmitter = require('node:events');
const {configureGraphics, watchGraphics} = require('../core/graphics');

const SOFTWARE = [['use-gl', 'angle'], ['use-angle', 'swiftshader']];

function fakeApp({webgl = 'enabled'} = {}) {
    const userData = fs.mkdtempSync(path.join(os.tmpdir(), 'nip-graphics-'));
    const app = new EventEmitter();
    app.switches = [];
    app.commandLine = {appendSwitch: (...args) => app.switches.push(args)};
    app.getPath = name => { assert.equal(name, 'userData'); return userData; };
    app.getGPUFeatureStatus = () => ({webgl});
    return app;
}

test('默认自动渲染，入口不得恢复全局禁用GPU的旧参数', () => {
    const app = fakeApp();
    assert.equal(configureGraphics(app, {mode: undefined}), 'auto');
    assert.deepEqual(app.switches, []);
    // main.js 是启动引导，图形配置在它加载的应用入口 electron/app-main.js 里。
    for (const name of ['../../main.js', '../app-main.js']) {
        const entry = fs.readFileSync(path.resolve(__dirname, name), 'utf8');
        assert.doesNotMatch(entry, /disableHardwareAcceleration|disable-gpu|disable-software-rasterizer/);
    }
    assert.match(fs.readFileSync(path.resolve(__dirname, '../app-main.js'), 'utf8'), /configureGraphics\(app\)/);
});

test('部署环境可显式选择软件渲染', () => {
    const app = fakeApp();
    assert.equal(configureGraphics(app, {mode: 'software'}), 'software');
    assert.deepEqual(app.switches, SOFTWARE);
});

test('WebGL不可用时记住软件渲染并要求重启，下次启动生效', () => {
    const app = fakeApp({webgl: 'unavailable_off'});
    const watcher = watchGraphics(app, {graphicsMode: configureGraphics(app, {mode: undefined}), mode: undefined});
    assert.equal(watcher.needsSoftwareRelaunch(), true);
    assert.equal(configureGraphics(app, {mode: undefined}), 'software');
    assert.deepEqual(app.switches, SOFTWARE);
});

test('GPU进程崩溃后下次启动改用软件渲染，正常退出不触发', () => {
    const app = fakeApp();
    watchGraphics(app, {graphicsMode: 'auto', mode: undefined});
    app.emit('child-process-gone', {}, {type: 'GPU', reason: 'clean-exit'});
    app.emit('child-process-gone', {}, {type: 'Utility', reason: 'crashed'});
    assert.equal(configureGraphics(app, {mode: undefined}), 'auto');
    app.emit('child-process-gone', {}, {type: 'GPU', reason: 'crashed'});
    assert.equal(configureGraphics(app, {mode: undefined}), 'software');
});

test('WebGL可用或显式指定auto时不回退，auto可覆盖已有回退标记', () => {
    const ok = fakeApp({webgl: 'enabled'});
    assert.equal(watchGraphics(ok, {graphicsMode: 'auto', mode: undefined}).needsSoftwareRelaunch(), false);

    const forced = fakeApp({webgl: 'unavailable_off'});
    assert.equal(watchGraphics(forced, {graphicsMode: 'auto', mode: 'auto'}).needsSoftwareRelaunch(), false);
    assert.equal(configureGraphics(forced, {mode: undefined}), 'auto');

    const marked = fakeApp({webgl: 'unavailable_off'});
    watchGraphics(marked, {graphicsMode: 'auto', mode: undefined}).needsSoftwareRelaunch();
    assert.equal(configureGraphics(marked, {mode: 'auto'}), 'auto');
    assert.deepEqual(marked.switches, []);
});
