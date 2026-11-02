import assert from 'node:assert/strict'
import {readFileSync} from 'node:fs'
import {setImmediate} from 'node:timers/promises'
import test from 'node:test'
import vm from 'node:vm'

// 隔离浏览器环境，运行实际串口读取类与通道，只替换 UI/store 的模块依赖。
const serialSource = readFileSync(new URL('../src/common/utils/WebSerial.js', import.meta.url), 'utf8')
  .replace(/^import .*$/gm, '').replace('export default class', 'class')
const channelSource = readFileSync(new URL('../src/common/ws/WebSerialChannel.js', import.meta.url), 'utf8')
  .replace(/^import .*$/gm, '').replaceAll('export default ', '').replaceAll('export ', '')
const injectSource = readFileSync(new URL('../../../keysim/public/inject.js', import.meta.url), 'utf8')
  .replaceAll('__KEYSIM_ORIGIN__', 'http://127.0.0.1:18700')

function portFixture() {
  let controller
  const port = {
    opens: 0,
    closes: 0,
    readable: null,
    writable: null,
    async open(options) {
      assert.equal(options.baudRate, 9600)
      this.opens++
      this.readable = new ReadableStream({start(target) { controller = target }})
    },
    async close() {
      assert.equal(this.readable.locked, false, '关口前必须释放读取锁')
      this.closes++
      this.readable = null
    },
    send(bytes) { controller.enqueue(new Uint8Array(bytes)) }
  }
  return port
}

function channelFixture(t, {ports = [], selection = portFixture(), browserPreference = true, session = new Map()} = {}) {
  const calls = {request: 0, history: 0, scripts: 0}
  const state = {linkStatus: false, devStatus: false}
  const frames = []
  const errors = []
  const events = new EventTarget()
  const sockets = []
  class Socket {
    static OPEN = 1
    readyState = 1
    constructor(url) { this.url = url; sockets.push(this) }
    close() { this.readyState = 3; this.onclose?.() }
    sendToPage(bytes) { this.onmessage({data: new Uint8Array(bytes).buffer}) }
  }
  const context = vm.createContext({
    navigator: {serial: {
      getPorts: async () => { calls.history++; return ports },
      requestPort: () => { calls.request++; return Promise.resolve(selection) }
    }},
    // 复现所有普通浏览器用户点击连接后都会留下的历史标记。
    localStorage: {getItem: key => key === 'serialChrome' && browserPreference ? 'true' : null},
    sessionStorage: {getItem: key => session.get(key) ?? null, setItem: (key, value) => session.set(key, value)},
    window: {
      addEventListener: events.addEventListener.bind(events),
      removeEventListener: events.removeEventListener.bind(events),
      dispatchEvent: events.dispatchEvent.bind(events)
    },
    document: {
      createElement() { calls.scripts++; return {remove() {}} },
      head: {appendChild(script) {
        assert.equal(script.src, 'http://127.0.0.1:18700/inject.js')
        queueMicrotask(() => { vm.runInContext(injectSource, context); script.onload() })
      }}
    },
    console: {error: (...args) => errors.push(args), warn() {}, info() {}},
    ReadableStream, Uint8Array, Event, URL, setTimeout, clearTimeout,
    WebSocket: Socket,
    performance,
    PubSub: {publishSync() {}},
    ref: value => ({value}),
    traffic: {useTrafficStore: () => ({$patch: patch => Object.assign(state, patch)})},
    publishTrafficFrame: frame => frames.push(frame)
  })
  vm.runInContext(`${serialSource}\n${channelSource}`, context)
  t.after(async () => {
    await context.shutdownWebSerialChannel()
    context.window.__keysimSerial?.detach()
  })
  return {context, calls, state, frames, errors, sockets, inject: () => vm.runInContext(injectSource, context)}
}

test('浏览器历史标记不触发模拟器，首次点击立即选择真实设备并接收拍发数据', async t => {
  const port = portFixture()
  const {context, calls, state, frames} = channelFixture(t, {selection: port})
  context.webSerialChannel('reset', 'resetLine')
  assert.equal(calls.request, 1, '必须在任何异步等待之前打开设备选择器')
  assert.equal(calls.history, 0)
  assert.equal(calls.scripts, 0)
  await setImmediate()
  assert.equal(port.opens, 1)
  assert.equal(state.devStatus, true)
  port.send([1, 0, 2, 0, 0])
  await setImmediate()
  assert.deepEqual(frames.map(frame => [frame.t, frame.k]), [[0, 0], [0, 1]])
})

test('刷新时没有已授权设备就保持离线，不弹选择器也不注入虚拟串口', async t => {
  const {context, calls, state} = channelFixture(t)
  context.webSerialChannel('reset', 'reset')
  await setImmediate()
  assert.deepEqual(calls, {request: 0, history: 1, scripts: 0})
  assert.equal(state.devStatus, false)
})

test('刷新可自动复用已授权真实串口或测试工具显式注入的串口', async t => {
  const port = portFixture()
  const {context, calls, state} = channelFixture(t, {ports: [port]})
  context.webSerialChannel('reset', 'reset')
  await setImmediate()
  assert.equal(calls.request, 0)
  assert.equal(port.opens, 1)
  assert.equal(state.linkStatus, true)
  await context.shutdownWebSerialChannel()
  assert.equal(port.closes, 1)
})

test('手动切换设备使用新选择，关闭旧串口后读取新串口', async t => {
  const oldPort = portFixture()
  const newPort = portFixture()
  const {context, calls, state} = channelFixture(t, {ports: [oldPort], selection: newPort})
  context.webSerialChannel('reset', 'reset')
  await setImmediate()
  assert.equal(oldPort.opens, 1)
  context.webSerialChannel('reset', 'resetLine')
  assert.equal(calls.request, 1)
  await setImmediate()
  assert.equal(oldPort.closes, 1)
  assert.equal(newPort.opens, 1)
  assert.equal(state.devStatus, true)
})

test('用户取消首次选择时保持离线，不回退到模拟器', async t => {
  const cancelled = {then(resolve, reject) { reject(new Error('No port selected')) }}
  const {context, calls, state, errors} = channelFixture(t, {selection: cancelled})
  context.webSerialChannel('reset', 'resetLine')
  await setImmediate()
  assert.equal(state.devStatus, false)
  assert.equal(calls.scripts, 0)
  assert.equal(errors.length, 1)
})

test('选择器尚未返回时退出页面，之后返回的串口不得再打开', async t => {
  let select
  const selection = new Promise(resolve => { select = resolve })
  const port = portFixture()
  const {context, state} = channelFixture(t, {selection})
  context.webSerialChannel('reset', 'resetLine')
  await context.shutdownWebSerialChannel()
  select(port)
  await setImmediate()
  assert.equal(port.opens, 0)
  assert.equal(state.devStatus, false)
})

test('默认页面首次打开，无历史连接标记也能接收预置虚拟串口的数据', async t => {
  const {context, inject, sockets, state, frames, calls} = channelFixture(t, {browserPreference: false})
  inject()
  context.webSerialChannel()
  await setImmediate()
  assert.equal(state.devStatus, true)
  sockets[0].sendToPage([1, 0, 2, 0, 0])
  await setImmediate()
  assert.deepEqual(frames.map(frame => [frame.t, frame.k]), [[0, 0], [0, 1]])
  assert.equal(calls.request, 0)
})

test('页面已挂载后才注入，自动开始读取，无需手动再点连接', async t => {
  const {context, inject, sockets, state, frames, calls} = channelFixture(t, {browserPreference: false})
  context.webSerialChannel()
  await setImmediate()
  assert.equal(state.devStatus, false)
  inject()
  await setImmediate()
  assert.equal(state.devStatus, true)
  sockets[0].sendToPage([11])
  await setImmediate()
  assert.equal(frames[0].d[0], 11)
  assert.equal(calls.request, 0)
})

test('模拟器标签页刷新后根据独立会话标记恢复注入并接收数据', async t => {
  const session = new Map()
  const first = channelFixture(t, {browserPreference: false, session})
  first.inject()
  await first.context.shutdownWebSerialChannel()
  first.context.window.__keysimSerial.detach()
  const refreshed = channelFixture(t, {browserPreference: false, session})
  refreshed.context.webSerialChannel()
  await setImmediate()
  assert.equal(refreshed.calls.scripts, 1)
  assert.equal(refreshed.state.devStatus, true)
  refreshed.sockets[0].sendToPage([12])
  await setImmediate()
  assert.equal(refreshed.frames[0].d[0], 12)
})

test('重复注入保持单条连接且每个拍发帧只交付一次', async t => {
  const {context, inject, sockets, frames} = channelFixture(t, {browserPreference: false})
  inject()
  context.webSerialChannel()
  await setImmediate()
  const stream = context.window.__keysimSerial.port.readable
  inject()
  inject()
  await setImmediate()
  assert.equal(sockets.length, 1)
  assert.equal(context.window.__keysimSerial.port.readable, stream)
  sockets[0].sendToPage([13])
  await setImmediate()
  assert.equal(frames.length, 1)
  await context.shutdownWebSerialChannel()
  inject()
  await setImmediate()
  assert.equal(context.window.__keysimSerial.port.readable, null, '退出后的通知不得重新打开串口')
})

test('模拟器注入后可切换真实设备，再切回虚拟设备且不重放离线数据', async t => {
  const real = portFixture()
  const session = new Map()
  const {context, inject, calls, state, frames, sockets} = channelFixture(t, {selection: real, session})
  inject()
  await context.webSerialChannel()
  const virtual = context.window.__keysimSerial.port
  assert.equal(state.devStatus, true)
  const switching = context.webSerialChannel('reset', 'resetLine', 'real')
  assert.equal(calls.request, 1, '注入后仍同步调用浏览器原生选择器')
  await switching
  assert.equal(real.opens, 1)
  assert.equal(virtual.readable, null)
  assert.equal(session.get('serialSource'), 'real')
  sockets[0].sendToPage([11])
  real.send([12])
  await setImmediate()
  assert.deepEqual(frames.map(frame => frame.d[0]), [12])
  inject()
  await setImmediate()
  assert.equal(virtual.readable, null, '重新注入不能抢占用户选择的真实设备')
  await context.webSerialChannel('reset', undefined, 'virtual')
  assert.equal(real.closes, 1)
  assert.equal(session.get('serialSource'), 'virtual')
  sockets[0].sendToPage([13])
  await setImmediate()
  assert.deepEqual(frames.map(frame => frame.d[0]), [12, 13])
})

test('取消真实设备选择后原虚拟设备保持在线并继续交付拍发数据', async t => {
  const cancelled = {then(resolve, reject) { reject(Object.assign(new Error('取消选择'), {name: 'NotFoundError'})) }}
  const session = new Map()
  const {context, inject, state, sockets, frames} = channelFixture(t, {selection: cancelled, session})
  inject()
  await context.webSerialChannel()
  const stream = context.window.__keysimSerial.port.readable
  await context.webSerialChannel('reset', 'resetLine', 'real')
  assert.equal(state.devStatus, true)
  assert.equal(context.window.__keysimSerial.port.readable, stream)
  assert.equal(session.get('serialSource'), 'virtual')
  sockets[0].sendToPage([14])
  await setImmediate()
  assert.equal(frames[0].d[0], 14)
})

test('从模拟器切到真实设备后刷新，只恢复真实串口', async t => {
  const session = new Map([['serialSource', 'real'], ['keysimSerialOrigin', 'http://127.0.0.1:18700']])
  const real = portFixture()
  const {context, inject, calls, state} = channelFixture(t, {ports: [real], session})
  // 模拟浏览器启动脚本每次刷新都重新注入。
  inject()
  await context.webSerialChannel()
  assert.equal(real.opens, 1)
  assert.equal(context.window.__keysimSerial.port.readable, null)
  assert.equal(calls.request, 0)
  assert.equal(calls.scripts, 0)
  assert.equal(state.devStatus, true)
})

test('真实设备打开尚未完成时退出页面，打开后立即关闭且保持离线', async t => {
  const real = portFixture()
  const open = real.open.bind(real)
  let finishOpen
  real.open = async options => {
    await new Promise(resolve => { finishOpen = resolve })
    await open(options)
  }
  const {context, state} = channelFixture(t, {selection: real})
  const connecting = context.webSerialChannel('reset', 'resetLine', 'real')
  await setImmediate()
  const closing = context.shutdownWebSerialChannel()
  finishOpen()
  await Promise.all([connecting, closing])
  assert.equal(real.closes, 1)
  assert.equal(real.readable, null)
  assert.equal(state.devStatus, false)
})

test('没有原生串口接口时选择真实设备不会误连虚拟串口', async t => {
  const {context, inject, state} = channelFixture(t)
  delete context.navigator.serial
  inject()
  await context.webSerialChannel()
  const stream = context.window.__keysimSerial.port.readable
  await context.webSerialChannel('reset', 'resetLine', 'real')
  assert.equal(state.devStatus, true)
  assert.equal(context.window.__keysimSerial.port.readable, stream)
  assert.match(vm.runInContext('serialNotice.value', context), /不支持真实串口/)
})

test('已运行的模拟器只公开 port 时，仍可双向切换且调用原生选择器', async t => {
  const real = portFixture()
  const {context, inject, calls, state, frames, sockets} = channelFixture(t, {selection: real})
  const native = context.navigator.serial
  // Chromium 的原生 serial 是 Navigator.prototype 上的 getter。
  Object.setPrototypeOf(context.navigator, Object.defineProperty({}, 'serial', {
    get() {
      assert.equal(this, context.navigator, '原生 getter 必须绑定 navigator')
      return native
    }
  }))
  inject()
  delete context.window.__keysimSerial.nativeSerial
  delete context.window.__keysimSerial.serial
  await context.webSerialChannel()
  assert.equal(state.devStatus, true)
  sockets[0].sendToPage([11])
  await setImmediate()
  const selecting = context.webSerialChannel('reset', 'resetLine', 'real')
  assert.equal(calls.request, 1)
  await selecting
  assert.equal(real.opens, 1)
  assert.equal(context.window.__keysimSerial.port.readable, null)
  real.send([12])
  await setImmediate()
  await context.webSerialChannel('reset', undefined, 'virtual')
  assert.equal(state.devStatus, true)
  assert.equal(real.closes, 1)
  sockets[0].sendToPage([13])
  await setImmediate()
  assert.deepEqual(frames.map(frame => frame.d[0]), [11, 12, 13])
})
