import assert from 'node:assert/strict'
import {readFileSync} from 'node:fs'
import test from 'node:test'
import vm from 'node:vm'

const injection = readFileSync(new URL('../../../keysim/public/inject.js', import.meta.url), 'utf8')
const serialSource = readFileSync(new URL('../src/common/utils/WebSerial.js', import.meta.url), 'utf8')
  .replace(/^import .*$/gm, '').replace('export default class', 'class')
const decoderSource = readFileSync(new URL('../src/common/utils/handKeyDecoder.js', import.meta.url), 'utf8')
  .replace('export function', 'function')

async function setup() {
  let socket, receivedAt = 10000
  class Socket {
    static OPEN = 1
    readyState = 1
    constructor() {socket = this}
    close() {}
  }
  const context = vm.createContext({
    window: {dispatchEvent() {}}, navigator: {}, document: {currentScript: {src: 'http://127.0.0.1:18700/inject.js'}},
    URL, Event, Uint8Array, ReadableStream, WebSocket: Socket, console: {info() {}, warn() {}, error() {}},
    sessionStorage: {setItem() {}}, setTimeout, clearTimeout,
    performance: {now: () => receivedAt}, PubSub: {publishSync() {}}
  })
  vm.runInContext(injection + '\n' + serialSource + '\n' + decoderSource, context)
  const port = context.window.__keysimSerial.port
  await port.open()
  const reader = port.readable.getReader()
  const serial = vm.runInContext('new WebSerial(9600)', context)
  serial.port = port
  const decoded = []
  const decoder = context.createHandKeyDecoder({enabled: () => true, threshold: () => 120,
    lineLimit: () => 360, emit: event => decoded.push(event)})
  return {decoded,
    async receive(at, down, timed = true) {
      if (timed) socket.onmessage({data: JSON.stringify({type: 'serial-timing', at, clock: 'keysim-test'})})
      socket.onmessage({data: Uint8Array.from(down ? [1, 0] : [2, 0, 0]).buffer})
      const {value} = await reader.read()
      serial.handleData(value, frame => decoder.receive(frame.data))
    },
    now: time => {receivedAt = time},
    async close() {await reader.cancel();reader.releaseLock();await port.close()}
  }
}

test('浏览器阻塞后集中处理消息，注入字节仍保留真实点划长度和字间隔', async () => {
  const h = await setup()
  try {
    // 模拟浏览器忙碌后在同一个接收时刻处理积压消息。
    for (const [at, down] of [[0, true], [80, false], [160, true], [400, false], [640, true], [720, false]]) {
      await h.receive(at, down)
    }
    assert.deepEqual(h.decoded.filter(e => e.code !== -1).map(e => e.code), [0, 1, 0])
    assert.deepEqual(h.decoded.filter(e => e.code !== -1).map(e => e.diffTime[1] - e.diffTime[0]), [80, 240, 80])
    assert.deepEqual(h.decoded.filter(e => e.code === -1).map(e => e.gapTime[1] - e.gapTime[0]), [80, 240])
    assert.ok(h.decoded.every(e => e.receivedAt === 10000 && e.timeSource === 'keysim-test'))
  } finally {await h.close()}
})

test('未携带源时间的真实串口路径继续按接收时刻计时', async () => {
  const h = await setup()
  try {
    h.now(100)
    await h.receive(0, true, false)
    h.now(180)
    await h.receive(0, false, false)
    assert.equal(h.decoded.length, 1)
    assert.equal(h.decoded[0].diffTime[1] - h.decoded[0].diffTime[0], 80)
    assert.equal(h.decoded[0].timeSource, 'js-receive')
  } finally {await h.close()}
})

test('旧注入通道在主线程阻塞时会将按压压成零并丢弃，复现漏码原因', async () => {
  const h = await setup()
  try {
    await h.receive(0, true, false)
    await h.receive(80, false, false)
    assert.equal(h.decoded.length, 0)
  } finally {await h.close()}
})
