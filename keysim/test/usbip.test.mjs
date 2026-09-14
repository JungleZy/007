import assert from 'node:assert/strict'
import test from 'node:test'
import {spawn} from 'node:child_process'
import {connect} from 'node:net'
import {buildEmulator} from '../src/providers/usbip.mjs'

const USBIP_VERSION = 0x0111
const OP_REQ_IMPORT = 0x8003
const OP_REP_IMPORT = 0x0003
const CMD_SUBMIT = 1
const CMD_UNLINK = 2
const RET_SUBMIT = 3
const RET_UNLINK = 4
const DIR_OUT = 0
const DIR_IN = 1

/** 按字节数读的小工具：socket 上收多少来多少，测试要精确取 N 字节。 */
const reader = socket => {
  let buffer = Buffer.alloc(0)
  const waiters = []
  socket.on('data', chunk => {
    buffer = Buffer.concat([buffer, chunk])
    while (waiters.length && buffer.length >= waiters[0].want) {
      const waiter = waiters.shift()
      const slice = buffer.subarray(0, waiter.want)
      buffer = buffer.subarray(waiter.want)
      waiter.resolve(Buffer.from(slice))
    }
  })
  return {
    read(want, {timeout = 4000} = {}) {
      if (buffer.length >= want) {
        const slice = buffer.subarray(0, want)
        buffer = buffer.subarray(want)
        return Promise.resolve(Buffer.from(slice))
      }
      return new Promise((resolve, reject) => {
        const waiter = {want, resolve}
        waiters.push(waiter)
        setTimeout(() => {
          const index = waiters.indexOf(waiter)
          if (index >= 0) { waiters.splice(index, 1); reject(new Error(`等 ${want} 字节超时`)) }
        }, timeout)
      })
    },
    get buffered() { return buffer.length }
  }
}

const submitHeader = ({seqnum, devid, direction, ep, length, setup = Buffer.alloc(8)}) => {
  const header = Buffer.alloc(48)
  header.writeUInt32BE(CMD_SUBMIT, 0)
  header.writeUInt32BE(seqnum, 4)
  header.writeUInt32BE(devid, 8)
  header.writeUInt32BE(direction, 12)
  header.writeUInt32BE(ep, 16)
  header.writeUInt32BE(0, 20)        // transfer_flags
  header.writeInt32BE(length, 24)    // transfer_buffer_length
  header.writeInt32BE(0, 28)
  header.writeInt32BE(0, 32)
  header.writeInt32BE(0, 36)
  setup.copy(header, 40)
  return header
}

const setupPacket = (type, request, value, index, length) => {
  const setup = Buffer.alloc(8)
  setup.writeUInt8(type, 0)
  setup.writeUInt8(request, 1)
  setup.writeUInt16LE(value, 2)
  setup.writeUInt16LE(index, 4)
  setup.writeUInt16LE(length, 6)
  return setup
}

const readRet = async stream => {
  const header = await stream.read(48)
  const command = header.readUInt32BE(0)
  const seqnum = header.readUInt32BE(4)
  const direction = header.readUInt32BE(12)
  const status = header.readInt32BE(20)
  const actual = header.readInt32BE(24)
  const data = command === RET_SUBMIT && direction === DIR_IN && actual > 0 ? await stream.read(actual) : Buffer.alloc(0)
  return {command, seqnum, status, actual, data}
}

/** 起模拟器并完成 import 握手，之后测试就相当于内核的 vhci_hcd。 */
async function attachAsKernel() {
  const build = await buildEmulator()
  if (!build.ok) return {skip: build.error}
  const child = spawn(build.binary, ['--port', '0'], {stdio: ['pipe', 'pipe', 'pipe']})
  const events = []
  let buffered = ''
  const ready = await new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error('模拟器未就绪')), 8000)
    child.stdout.on('data', chunk => {
      buffered += chunk
      let index
      while ((index = buffered.indexOf('\n')) >= 0) {
        const line = buffered.slice(0, index).trim()
        buffered = buffered.slice(index + 1)
        if (!line) continue
        const event = JSON.parse(line)
        events.push(event)
        if (event.type === 'ready') { clearTimeout(timer); resolve(event) }
      }
    })
    child.on('error', error => { clearTimeout(timer); reject(error) })
  })

  const socket = connect({host: '127.0.0.1', port: ready.usbipPort})
  await new Promise((resolve, reject) => { socket.once('connect', resolve); socket.once('error', reject) })
  const stream = reader(socket)
  const request = Buffer.alloc(8 + 32)
  request.writeUInt16BE(USBIP_VERSION, 0)
  request.writeUInt16BE(OP_REQ_IMPORT, 2)
  request.writeUInt32BE(0, 4)
  request.write(ready.busid, 8, 'ascii')
  socket.write(request)

  const head = await stream.read(8)
  assert.equal(head.readUInt16BE(2), OP_REP_IMPORT, 'import 回应码不对')
  assert.equal(head.readUInt32BE(4), 0, 'import 被拒绝')
  const device = await stream.read(312)
  return {
    child, socket, stream, ready, events, device,
    /** 宿主往设备灌串口字节（和 keysim 用的是同一条协议） */
    feed(bytes) {
      const payload = Buffer.from(bytes)
      const header = Buffer.alloc(4)
      header.writeUInt32BE(payload.length, 0)
      child.stdin.write(Buffer.concat([header, payload]))
    },
    async close() {
      socket.destroy()
      child.stdin.end()
      child.kill('SIGTERM')
      await new Promise(resolve => { child.once('close', resolve); setTimeout(resolve, 1500) })
    }
  }
}

test('USB/IP import 后交回的设备是一台全速 CDC 串口，参数与描述符一致', async t => {
  const session = await attachAsKernel()
  if (session.skip) { t.skip(session.skip); return }
  try {
    const {device} = session
    assert.equal(device.subarray(256, 288).toString('ascii').replace(/\0+$/, ''), '1-1', 'busid 不对')
    assert.equal(device.readUInt32BE(288), 1, 'busnum 不对')
    assert.equal(device.readUInt32BE(292), 2, 'devnum 不对')
    assert.equal(device.readUInt32BE(296), 2, 'speed 应为 USB_SPEED_FULL(2)')
    assert.equal(device.readUInt16BE(300), 0x1209, 'idVendor 不对')
    assert.equal(device[306], 0x02, 'bDeviceClass 应为 CDC(0x02)')
    assert.equal(device[310], 1, 'bNumConfigurations 应为 1')
    assert.equal(device[311], 2, 'bNumInterfaces 应为 2（通信 + 数据）')

    // 设备描述符
    session.socket.write(submitHeader({
      seqnum: 1, devid: session.ready.devid, direction: DIR_IN, ep: 0, length: 18,
      setup: setupPacket(0x80, 0x06, 0x0100, 0, 18)
    }))
    const descriptor = await readRet(session.stream)
    assert.equal(descriptor.status, 0)
    assert.equal(descriptor.actual, 18)
    assert.equal(descriptor.data[4], 0x02, 'bDeviceClass 应为 CDC')
    assert.equal(descriptor.data.readUInt16LE(8), 0x1209)
  } finally {
    await session.close()
  }
})

test('配置描述符满足 cdc-acm 绑定条件：ACM 通信接口 + 数据接口 + 三个端点', async t => {
  const session = await attachAsKernel()
  if (session.skip) { t.skip(session.skip); return }
  try {
    session.socket.write(submitHeader({
      seqnum: 2, devid: session.ready.devid, direction: DIR_IN, ep: 0, length: 128,
      setup: setupPacket(0x80, 0x06, 0x0200, 0, 128)
    }))
    const {status, data} = await readRet(session.stream)
    assert.equal(status, 0)
    assert.equal(data.readUInt16LE(2), data.length, 'wTotalLength 与实际长度不一致')
    assert.equal(data[4], 2, 'bNumInterfaces 应为 2')

    // 逐个描述符走一遍，确认 cdc-acm 需要的三件套都在
    const interfaces = []
    const endpoints = []
    const functional = []
    for (let offset = 0; offset < data.length; offset += data[offset]) {
      const type = data[offset + 1]
      if (type === 0x04) interfaces.push([data[offset + 5], data[offset + 6], data[offset + 7]])
      if (type === 0x05) endpoints.push([data[offset + 2], data[offset + 3]])
      if (type === 0x24) functional.push(data[offset + 2])
      assert.ok(data[offset] > 0, '出现零长度描述符，配置集损坏')
    }
    assert.deepEqual(interfaces, [[0x02, 0x02, 0x01], [0x0a, 0x00, 0x00]], '接口类不符合 CDC-ACM')
    assert.deepEqual(endpoints, [[0x82, 0x03], [0x01, 0x02], [0x81, 0x02]], '端点不符合"中断 IN + bulk OUT + bulk IN"')
    assert.ok(functional.includes(0x00) && functional.includes(0x02) && functional.includes(0x06),
      `缺少 CDC 功能描述符（Header/ACM/Union），实际 ${functional}`)
  } finally {
    await session.close()
  }
})

test('bulk IN 没数据时挂起不空转；宿主一喂字节就把同一个 URB 补完', async t => {
  const session = await attachAsKernel()
  if (session.skip) { t.skip(session.skip); return }
  try {
    // 没有数据：URB 必须挂着（真实设备行为），不能立刻回 0 长度
    session.socket.write(submitHeader({seqnum: 10, devid: session.ready.devid, direction: DIR_IN, ep: 1, length: 64}))
    await new Promise(resolve => setTimeout(resolve, 400))
    assert.equal(session.stream.buffered, 0, '无数据时不该立刻回应 bulk IN')

    // 一喂就该补完，内容原样
    const bytes = [1, 0, 2, 0, 0, 1, 0, 2, 0, 0]
    session.feed(bytes)
    const completion = await readRet(session.stream)
    assert.equal(completion.command, RET_SUBMIT)
    assert.equal(completion.seqnum, 10)
    assert.equal(completion.status, 0)
    assert.deepEqual([...completion.data], bytes, '串口字节被改动了')
  } finally {
    await session.close()
  }
})

test('主机往串口写的数据会回报给宿主；未完成的 URB 能被 UNLINK 撤销', async t => {
  const session = await attachAsKernel()
  if (session.skip) { t.skip(session.skip); return }
  try {
    const payload = Buffer.from([0x41, 0x54, 0x0d])
    session.socket.write(Buffer.concat([
      submitHeader({seqnum: 20, devid: session.ready.devid, direction: DIR_OUT, ep: 1, length: payload.length}),
      payload
    ]))
    const written = await readRet(session.stream)
    assert.equal(written.status, 0)
    assert.equal(written.actual, payload.length)
    // 回报走 stdout，和 TCP 上的 RET_SUBMIT 不同步，轮询等一下
    let rx = null
    for (let attempt = 0; attempt < 20 && !rx; attempt++) {
      rx = session.events.find(event => event.type === 'rx')
      if (!rx) await new Promise(resolve => setTimeout(resolve, 100))
    }
    assert.ok(rx && rx.hex === '41 54 0d', `宿主没收到主机写入，实际 ${JSON.stringify(rx)}`)

    // 中断 IN 一直挂着（没有串口状态变化），UNLINK 必须能撤销它
    session.socket.write(submitHeader({seqnum: 30, devid: session.ready.devid, direction: DIR_IN, ep: 2, length: 8}))
    await new Promise(resolve => setTimeout(resolve, 200))
    const unlink = Buffer.alloc(48)
    unlink.writeUInt32BE(CMD_UNLINK, 0)
    unlink.writeUInt32BE(31, 4)
    unlink.writeUInt32BE(session.ready.devid, 8)
    unlink.writeUInt32BE(30, 20)
    session.socket.write(unlink)
    const result = await readRet(session.stream)
    assert.equal(result.command, RET_UNLINK)
    assert.equal(result.status, -104, 'UNLINK 掉已挂起的 URB 应回 -ECONNRESET')
  } finally {
    await session.close()
  }
})
