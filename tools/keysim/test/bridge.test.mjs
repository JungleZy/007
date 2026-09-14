import assert from 'node:assert/strict'
import test from 'node:test'
import {createParser, encodeFrame, startBridge} from '../src/sinks/bridge.mjs'
import {handKeyPlan, handKeyTimeline} from '../src/handkey.mjs'

const connect = url => new Promise((resolve, reject) => {
  const socket = new WebSocket(url)
  const inbox = []
  socket.addEventListener('message', event => inbox.push(JSON.parse(event.data)))
  socket.addEventListener('error', reject)
  socket.addEventListener('open', () => resolve({socket, inbox}))
})

const maskedTextFrame = (text, {fin = true, opcode = 0x1} = {}) => {
  const payload = Buffer.from(text, 'utf8')
  const mask = Buffer.from([0x12, 0x34, 0x56, 0x78])
  const masked = Buffer.from(payload.map((byte, index) => byte ^ mask[index % 4]))
  const header = Buffer.from([(fin ? 0x80 : 0x00) | opcode, 0x80 | payload.length])
  return Buffer.concat([header, mask, masked])
}

test('桥接完成握手、先播设备在线、并记下客户端回推的频率音量', async () => {
  const bridge = await startBridge({port: 18991})
  try {
    const {socket, inbox} = await connect(`${bridge.url}?username=probe`)
    // 真实客户端收到 status:true 后会回推一条 {type:0,fre,volume}（MessageWebSocket.js:151,156-157）。
    socket.send(JSON.stringify({type: 0, fre: 1200, volume: 0}))
    const pushed = await bridge.waitForMessage(entry => entry.parsed?.type === 0, {timeout: 5000})
    assert.deepEqual(pushed.parsed, {type: 0, fre: 1200, volume: 0})
    assert.deepEqual(inbox[0], {data: {status: true}}, '首帧必须是设备在线通知')
    socket.close()
  } finally {
    await bridge.close()
  }
})

test('回放按声明时刻推进，且帧里声明的 d 差值精确等于计划的点/划/间隔', async () => {
  const bridge = await startBridge({port: 18992, announceStatus: false})
  try {
    const {socket, inbox} = await connect(bridge.url)
    const plan = handKeyPlan({rate: 100})
    const timeline = handKeyTimeline({text: 'A', plan, preamble: false, tail: null})
    const started = performance.now()
    await bridge.replay(timeline)
    const wall = performance.now() - started
    await new Promise(resolve => setTimeout(resolve, 50))

    const edges = inbox.filter(message => message.data.t === 0)
    assert.equal(edges.length, 4, 'A = 一点一划，应为四个边沿')
    const diffs = edges.slice(1).map((message, index) => message.data.d - edges[index].data.d)
    // 声明时刻由模拟器给出，不受投递抖动影响，所以可以精确断言。
    assert.deepEqual(diffs.map(value => Math.round(value * 1000) / 1000),
      [plan.dot, plan.gap, plan.dash].map(value => Math.round(value * 1000) / 1000))
    assert.ok(wall >= timeline.duration - 5, `回放 ${wall.toFixed(1)}ms 明显快于声明的 ${timeline.duration}ms，说明没有按真实节拍发`)
    socket.close()
  } finally {
    await bridge.close()
  }
})

test('超过 125 字节的帧走 16 位长度字段，客户端仍能完整收到', async () => {
  const bridge = await startBridge({port: 18993, announceStatus: false})
  try {
    const {socket, inbox} = await connect(bridge.url)
    const codes = Array.from({length: 80}, (unused, index) => 11 + (index % 20))
    bridge.sendFrame({t: 1, k: 0, d: codes})
    await new Promise(resolve => setTimeout(resolve, 80))
    assert.equal(inbox.length, 1)
    assert.ok(JSON.stringify(inbox[0]).length > 125, '样本不够长，没有走 16 位长度路径')
    assert.deepEqual(inbox[0].data.d, codes)
    socket.close()
  } finally {
    await bridge.close()
  }
})

test('客户端分片发送的文本帧能被重组，不会丢半条报文', () => {
  const received = []
  const parse = createParser({onText: text => received.push(text), onClose: () => {}, onPing: () => {}})
  parse(maskedTextFrame('{"type":0,', {fin: false}))
  parse(maskedTextFrame('"fre":1200}', {fin: true, opcode: 0x0}))
  assert.deepEqual(received, ['{"type":0,"fre":1200}'])
})

test('收到 ping 会交给回调回 pong；close 后端口立即可重新绑定', async () => {
  const pings = []
  const parse = createParser({onText: () => {}, onClose: () => {}, onPing: payload => pings.push(payload.toString('utf8'))})
  parse(maskedTextFrame('beat', {opcode: 0x9}))
  assert.deepEqual(pings, ['beat'], 'ping 必须被识别并交给 pong 回调')

  const bridge = await startBridge({port: 18994, announceStatus: false})
  const {socket} = await connect(bridge.url)
  socket.close()
  await bridge.close()
  // 端口没释放的话这里会 EADDRINUSE
  const again = await startBridge({port: 18994, announceStatus: false})
  assert.equal(again.port, 18994)
  await again.close()
})

test('帧编码遵守服务端不掩码的规定，长度字段按载荷长度选档', () => {
  assert.equal(encodeFrame(0x1, 'ab')[0], 0x81)
  assert.equal(encodeFrame(0x1, 'ab')[1], 2, '短载荷用 7 位长度')
  assert.equal(encodeFrame(0x1, 'x'.repeat(200))[1], 126, '中等载荷用 16 位长度')
  assert.equal((encodeFrame(0x1, 'ab')[1] & 0x80), 0, '服务端帧不得掩码')
})
