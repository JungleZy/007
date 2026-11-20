import {test} from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';

const source = fs.readFileSync(new URL('../src/config/runtime.js', import.meta.url), 'utf8')
    .replace(/^import .*\r?\n/gm, '').replace('export async function', 'async function');

for (const style of ['HJ', 'HJJ', 'LJ', undefined]) {
    test(`桌面启动按构建风格初始化界面与 body 样式：${style || '默认 HJJ'}`, async () => {
        const classes = [];
        const context = {
            URL, isEE: true, window: {location: {protocol: 'file:'}},
            ipcApiRoute: {getConfig: 'getConfig'},
            ipc: {invoke: async () => ({dataUrl: {url: 'localhost', port: 18001}, fileUrl: {url: 'localhost', port: 8000}})},
            endpointUrl: (base, route) => new URL(route, (base.includes('://') ? base : 'http://' + base) + '/').href,
            localforage: {config() {}}, localStorage: {getItem: () => null, setItem() {}},
            document: {body: {classList: {add: value => classes.push(value)}}, getElementById: () => null}
        };
        if (style) context.__NIP_INTERFACE_STYLE__ = style;
        await vm.runInNewContext(source + '\nconfigureRuntime()', context);
        assert.equal(context.window.interfaceStyle, style || 'HJJ');
        assert.deepEqual(classes, [style || 'HJJ']);
    });
}
