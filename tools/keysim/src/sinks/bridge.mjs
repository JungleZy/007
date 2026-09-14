/**
 * 桥接层 sink：在 18765 上冒充仓外硬件桥（TrafficService.exe）。
 * 桌面模式下 MessageWebSocket.js:115 以客户端身份连 ws://localhost:18765/echo?username=…，
 * 只取 parsed.data（:132-133）：带 status 字段的算设备状态，其余一律当流量/拍发帧
 * 交给 publishTrafficFrame（:148-155）。收到 {status:true} 后客户端会回推一条
 * {type:0,fre,volume}（:151,156-157），本 sink 把它记进 received 供断言。
 *
 * 零依赖手写 RFC6455：服务端只需发不掩码的文本帧，并能读客户端掩码文本帧。
 */
import {createHash} from 'node:crypto'
import {createServer} from 'node:http'
import {toBridgeMessages} from './frames.mjs'

const GUID = '258EAFA5-E914-47DA-95CA-C5AB0DC85B11'
const OP_TEXT = 0x1
const OP_CLOSE = 0x8
const OP_PING = 0x9
const OP_PONG = 0xA

const acceptKey = key => createHash('sha1').update(key + GUID).digest('base64')

export function encodeFrame(opcode, payload = '') {
  const data = Buffer.isBuffer(payload) ? payload : Buffer.from(String(payload), 'utf8')
  let header
  if (data.length < 126) {
    header = Buffer.alloc(2)
    header[1] = data.length
  } else if (data.length < 65536) {
    header = Buffer.alloc(4)
    header[1] = 126
    header.writeUInt16BE(data.length, 2)
  } else {
    header = Buffer.alloc(10)
    header[1] = 127
    header.writeBigUInt64BE(BigInt(data.length), 2)
  }
  header[0] = 0x80 | opcode
  return Buffer.concat([header, data])
}

export function createParser({onText, onClose, onPing}) {
  let buffer = Buffer.alloc(0)
  let fragments = []
  let fragmentOpcode = 0
  return chunk => {
    buffer = Buffer.concat([buffer, chunk])
    for (;;) {
      if (buffer.length < 2) return
      const first = buffer[0]
      const second = buffer[1]
      const fin = (first & 0x80) !== 0
      const opcode = first & 0x0f
      const masked = (second & 0x80) !== 0
      let length = second & 0x7f
      let offset = 2
      if (length === 126) {
        if (buffer.length < 4) return
        length = buffer.readUInt16BE(2)
        offset = 4
      } else if (length === 127) {
        if (buffer.length < 10) return
        length = Number(buffer.readBigUInt64BE(2))
        offset = 10
      }
      const maskKey = masked ? buffer.subarray(offset, offset + 4) : null
      if (masked) offset += 4
      if (buffer.length < offset + length) return
      const payload = Buffer.from(buffer.subarray(offset, offset + length))
      if (masked) for (let index = 0; index < payload.length; index++) payload[index] ^= maskKey[index % 4]
      buffer = buffer.subarray(offset + length)
      if (opcode === OP_CLOSE) return onClose()
      if (opcode === OP_PING) { onPing(payload); continue }
      if (opcode === OP_PONG) continue
      if (opcode === 0x0) fragments.push(payload)
      else { fragments = [payload]; fragmentOpcode = opcode }
      if (!fin) continue
      const message = Buffer.concat(fragments)
      fragments = []
      if (fragmentOpcode === OP_TEXT) onText(message.toString('utf8'))
    }
  }
}

/** 按声明时刻回放，带漂移校正；1ms 前视窗把同刻帧一次发出。 */
function replayMessages(messages, {speed = 1, send, stopped}) {
  return new Promise(resolve => {
    if (!messages.length) return resolve({sent: 0})
    const started = performance.now()
    let index = 0
    const tick = () => {
      if (stopped()) return resolve({sent: index, aborted: true})
      const elapsed = performance.now() - started
      while (index < messages.length && messages[index].at / speed <= elapsed + 1) send(messages[index++])
      if (index >= messages.length) return resolve({sent: index})
      setTimeout(tick, Math.max(0, messages[index].at / speed - (performance.now() - started)))
    }
    tick()
  })
}

export function startBridge({port = 18765, path = '/echo', host = '127.0.0.1', announceStatus = true, log = () => {}} = {}) {
  const clients = new Set()
  const received = []
  const messageHandlers = new Set()
  const connectHandlers = new Set()
  let closed = false

  const server = createServer((request, response) => {
    response.writeHead(426, {'content-type': 'text/plain; charset=utf-8'})
    response.end('仅支持 WebSocket 升级')
  })

  server.on('upgrade', (request, socket) => {
    const requestPath = new URL(request.url, `http://${host}:${port}`).pathname
    const key = request.headers['sec-websocket-key']
    if (requestPath !== path || !key) {
      socket.end('HTTP/1.1 400 Bad Request\r\n\r\n')
      return
    }
    socket.write([
      'HTTP/1.1 101 Switching Protocols',
      'Upgrade: websocket',
      'Connection: Upgrade',
      `Sec-WebSocket-Accept: ${acceptKey(key)}`,
      '\r\n'
    ].join('\r\n'))
    socket.setNoDelay(true)
    clients.add(socket)
    log(`桥接已接入：${request.url}`)

    const parser = createParser({
      onText(text) {
        let parsed = text
        try { parsed = JSON.parse(text) } catch { /* 非 JSON 原样保留 */ }
        const entry = {at: performance.now(), text, parsed}
        received.push(entry)
        for (const handler of messageHandlers) handler(entry)
      },
      onClose() { socket.end(encodeFrame(OP_CLOSE)) },
      onPing(payload) { socket.write(encodeFrame(OP_PONG, payload)) }
    })
    socket.on('data', chunk => { try { parser(chunk) } catch (error) { log(`帧解析失败：${error.message}`) } })
    socket.on('error', () => clients.delete(socket))
    socket.on('close', () => clients.delete(socket))

    if (announceStatus) socket.write(encodeFrame(OP_TEXT, JSON.stringify({data: {status: true}})))
    for (const handler of connectHandlers) handler(socket, request)
  })

  const broadcast = text => {
    const frame = encodeFrame(OP_TEXT, text)
    for (const socket of clients) if (socket.writable) socket.write(frame)
    return clients.size
  }

  const bridge = {
    port, path,
    url: `ws://${host}:${port}${path}`,
    clients,
    received,
    onMessage(handler) { messageHandlers.add(handler); return () => messageHandlers.delete(handler) },
    onConnect(handler) { connectHandlers.add(handler); return () => connectHandlers.delete(handler) },
    /** 设备在线/离线通知：true 会触发客户端回推频率与音量。 */
    sendStatus(status = true) { return broadcast(JSON.stringify({data: {status}})) },
    /** 发一个原始帧（自动包一层 {data:…}）。 */
    sendFrame(frame) { return broadcast(JSON.stringify({data: frame})) },
    broadcast,
    /** 按时间轴实时回放；speed>1 整体加速（注意客户端的 800ms/静默定时器不随之缩放）。 */
    replay(timeline, {speed = 1, base = 0, pack = false} = {}) {
      const messages = toBridgeMessages(timeline, {base, pack})
      return replayMessages(messages, {speed, send: message => broadcast(message.text), stopped: () => closed})
    },
    waitForClient({timeout = 15000} = {}) {
      if (clients.size) return Promise.resolve([...clients][0])
      return new Promise((resolve, reject) => {
        const timer = setTimeout(() => { dispose(); reject(new Error(`等待桥接客户端超时（${timeout}ms）`)) }, timeout)
        const dispose = bridge.onConnect(socket => { clearTimeout(timer); dispose(); resolve(socket) })
      })
    },
    waitForMessage(predicate = () => true, {timeout = 15000} = {}) {
      const hit = received.find(entry => predicate(entry))
      if (hit) return Promise.resolve(hit)
      return new Promise((resolve, reject) => {
        const timer = setTimeout(() => { dispose(); reject(new Error(`等待桥接报文超时（${timeout}ms）`)) }, timeout)
        const dispose = bridge.onMessage(entry => {
          if (!predicate(entry)) return
          clearTimeout(timer)
          dispose()
          resolve(entry)
        })
      })
    },
    close() {
      closed = true
      for (const socket of clients) socket.destroy()
      clients.clear()
      return new Promise(resolve => server.close(resolve))
    }
  }

  return new Promise((resolve, reject) => {
    server.once('error', reject)
    server.listen(port, host, () => resolve(bridge))
  })
}
