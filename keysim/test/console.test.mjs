import assert from 'node:assert/strict'
import test from 'node:test'
import {startServer} from '../src/server.mjs'
import {randomMessage, validateMessage} from '../src/random.mjs'

/** 端口一律由系统分配：固定端口会让 undici 复用上一轮已关闭的连接。 */
const HTTP = 0
const BRIDGE = 0

const post = async (base, path, body) => {
  const response = await fetch(`${base}${path}`, {
    method: 'POST', headers: {'content-type': 'application/json'}, body: JSON.stringify(body ?? {})
  })
  return response.json()
}

/** 以"被测应用"的身份连桌面桥接。 */
const connectDesktop = bridgeUrl => new Promise((resolve, reject) => {
  const socket = new WebSocket(`${bridgeUrl}?username=test`)
  const frames = []
  socket.addEventListener('message', event => frames.push(JSON.parse(event.data)))
  socket.addEventListener('error', reject)
  socket.addEventListener('open', () => resolve({socket, frames}))
})

/** 以"被测页面里的虚拟串口"的身份连注入通道，收的是字节。 */
const bridgeUrl = server => server.serial.state().bridgeUrl

const connectInject = base => new Promise((resolve, reject) => {
  const socket = new WebSocket(`${base.replace('http', 'ws')}/ws/serial`)
  socket.binaryType = 'arraybuffer'
  const bytes = []
  socket.addEventListener('message', event => bytes.push(...new Uint8Array(event.data)))
  socket.addEventListener('error', reject)
  socket.addEventListener('open', () => resolve({socket, bytes}))
})

test('随机报文按字母表取字符、按组切分；越表字符会被校验拦下', () => {
  const letters = randomMessage({alphabet: 'letter', groups: 5, seed: 3})
  assert.match(letters, /^[A-Z]{4}( [A-Z]{4}){4}$/, `字码报文形状异常：${letters}`)
  const digits = randomMessage({alphabet: 'short', groups: 3, seed: 3})
  assert.match(digits, /^[0-9]{4}( [0-9]{4}){2}$/, `数码报文形状异常：${digits}`)
  assert.equal(randomMessage({alphabet: 'letter', groups: 4, seed: 9}), randomMessage({alphabet: 'letter', groups: 4, seed: 9}), '同种子必须可复现')

  assert.equal(validateMessage('ABCD EFGH', 'letter').ok, true)
  assert.equal(validateMessage('ABCD 1234', 'letter').ok, false, '字码表里没有数字，应被拦下')
  assert.match(validateMessage('ABCD 1234', 'letter').reason, /没有这些字符/)
  assert.equal(validateMessage('   ', 'letter').ok, false)
})

test('控制台开关虚拟串口：关着连不上，开着能连上且先收到设备在线通知', async () => {
  const server = await startServer({port: HTTP, bridgePort: BRIDGE})
  try {
    const closed = await fetch(`${server.url}/api/state`).then(response => response.json())
    assert.equal(closed.state.open, false, '初始应为未开启')
    await assert.rejects(connectDesktop(bridgeUrl(server)), '串口未开启时不应有人能连上桥接')

    const opened = await post(server.url, '/api/port', {open: true})
    assert.equal(opened.state.open, true)
    const desktop = await connectDesktop(bridgeUrl(server))
    await new Promise(resolve => setTimeout(resolve, 150))
    assert.deepEqual(desktop.frames[0], {data: {status: true}}, '首帧必须是设备在线通知')
    assert.equal((await fetch(`${server.url}/api/state`).then(r => r.json())).state.bridgeClients, 1)

    desktop.socket.close()
    const shut = await post(server.url, '/api/port', {open: false})
    assert.equal(shut.state.open, false)
    await assert.rejects(connectDesktop(bridgeUrl(server)), '关闭后应再次连不上')
  } finally {
    await server.close()
  }
})

test('一次拍发同时喂两条通道：桌面收到帧、浏览器收到等量字节，内容一致', async () => {
  const server = await startServer({port: HTTP, bridgePort: BRIDGE})
  try {
    await post(server.url, '/api/port', {open: true})
    const desktop = await connectDesktop(bridgeUrl(server))
    const inject = await connectInject(server.url)
    await new Promise(resolve => setTimeout(resolve, 150))

    // 'AB' = 开始符 5 次按压 + A(01) 2 次 + B(1000) 4 次 = 11 次按压 = 22 个边沿
    const sent = await post(server.url, '/api/send', {key: 'hand', text: 'AB', alphabet: 'letter', rate: 300, tail: 'none', skew: 51, singlePage: true})
    assert.equal(sent.ok, true, `拍发未启动：${sent.error}`)
    assert.equal(sent.events, 22, '开始符 + AB 应产生 22 个边沿事件')

    const deadline = Date.now() + 15000
    while (desktop.frames.length < 23 && Date.now() < deadline) await new Promise(resolve => setTimeout(resolve, 100))
    await new Promise(resolve => setTimeout(resolve, 300))

    const edges = desktop.frames.filter(frame => frame.data?.t === 0)
    assert.equal(edges.length, 22, '桌面通道应收到 22 个边沿帧')
    assert.deepEqual(edges.map(frame => frame.data.k).slice(0, 4), [0, 1, 0, 1], '边沿必须按下/抬起交替')
    // 字节通道：按下 2 字节、抬起 3 字节（WebSerial.handleData 的帧长约定）
    assert.equal(inject.bytes.length, 11 * 2 + 11 * 3, '注入通道字节数应等于 11 个按下帧 + 11 个抬起帧')
    assert.deepEqual(inject.bytes.slice(0, 5), [1, 0, 2, 0, 0], '首帧应是按下(01 00)接抬起(02 00 00)')

    desktop.socket.close()
    inject.socket.close()
  } finally {
    await server.close()
  }
})

test('报文不合法时拒绝启动拍发，且不占用串口', async () => {
  const server = await startServer({port: HTTP, bridgePort: BRIDGE})
  try {
    await post(server.url, '/api/port', {open: true})
    const bad = await post(server.url, '/api/send', {key: 'hand', text: 'ABCD 1234', alphabet: 'letter', rate: 100})
    assert.equal(bad.ok, false)
    assert.match(bad.error, /没有这些字符/)
    assert.equal((await fetch(`${server.url}/api/state`).then(r => r.json())).state.replay.running, false, '被拒的拍发不应占用串口')
  } finally {
    await server.close()
  }
})

test('停止能在回放中途中断，已发帧数少于总数', async () => {
  const server = await startServer({port: HTTP, bridgePort: BRIDGE})
  try {
    await post(server.url, '/api/port', {open: true})
    const desktop = await connectDesktop(bridgeUrl(server))
    const sent = await post(server.url, '/api/send', {key: 'hand', text: 'ABCD EFGH', alphabet: 'letter', rate: 70, tail: 'none', skew: 51})
    assert.equal(sent.ok, true)
    await new Promise(resolve => setTimeout(resolve, 900))
    const stopped = await post(server.url, '/api/stop')
    assert.equal(stopped.ok, true)
    assert.equal(stopped.stopped, true, '回放中应能停下来')
    assert.equal(stopped.state.replay.running, false)
    const seen = desktop.frames.filter(frame => frame.data?.t === 0).length
    assert.ok(seen > 0 && seen < sent.events, `停止时应只发出一部分帧，实际 ${seen}/${sent.events}`)
    desktop.socket.close()
  } finally {
    await server.close()
  }
})
