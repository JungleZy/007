import assert from 'node:assert/strict'
import test from 'node:test'
import {open} from 'node:fs/promises'
import {startServer} from '../src/server.mjs'
import {probeDevice} from '../src/providers/device.mjs'

const post = async (base, path, body) => {
  const response = await fetch(`${base}${path}`, {
    method: 'POST', headers: {'content-type': 'application/json'}, body: JSON.stringify(body ?? {})
  })
  return response.json()
}

/** 像任何串口客户端那样按路径打开设备读字节，只用 fs，不用任何串口库。 */
const readDevice = async (path, {want, timeout = 20000}) => {
  const handle = await open(path, 'r')
  const bytes = []
  const buffer = Buffer.alloc(4096)
  const deadline = Date.now() + timeout
  try {
    while (bytes.length < want && Date.now() < deadline) {
      const {bytesRead} = await handle.read(buffer, 0, buffer.length, null)
      if (bytesRead > 0) bytes.push(...buffer.subarray(0, bytesRead))
    }
  } finally {
    await handle.close()
  }
  return bytes
}

test('开启虚拟串口后系统里出现一个真串口设备，按路径打开就能读到拍发字节', async t => {
  const probe = await probeDevice()
  if (!probe.available) {
    t.skip(`本机不支持 PTY 虚拟串口：${probe.reason}`)
    return
  }
  const links = ['/tmp/keysim-test-tty0']
  const server = await startServer({port: 0, bridgePort: 0, deviceLinks: links})
  try {
    const opened = await post(server.url, '/api/port', {open: true})
    assert.equal(opened.ok, true)
    const device = opened.state.device
    assert.ok(device.path && device.path.startsWith('/dev/'), `没拿到设备节点：${JSON.stringify(device)}`)
    assert.deepEqual(device.links, links, '符号链接没建起来')
    assert.match(device.systemLinkHint, /^sudo ln -sfn \/dev\//, '应给出让桌面壳列表可见的现成命令')

    // 'AB'：开始符 5 次按压 + A(01) 2 次 + B(1000) 4 次 = 11 次按压
    // 按下帧 2 字节 + 抬起帧 3 字节 => 55 字节
    const reading = readDevice(links[0], {want: 55})
    await new Promise(resolve => setTimeout(resolve, 200))
    const sent = await post(server.url, '/api/send', {
      key: 'hand', text: 'AB', alphabet: 'letter', rate: 300, tail: 'none', skew: 51, singlePage: true
    })
    assert.equal(sent.ok, true, `拍发未启动：${sent.error}`)

    const bytes = await reading
    assert.equal(bytes.length, 55, `设备上读到 ${bytes.length} 字节，应为 11 个按下/抬起帧共 55 字节`)
    assert.deepEqual(bytes.slice(0, 5), [1, 0, 2, 0, 0], '首帧应是按下(01 00) 接抬起(02 00 00)')
    // 逐帧校验帧长约定：1 开头两字节、2 开头三字节（WebSerial.handleData 的口径）
    let offset = 0
    let presses = 0
    while (offset < bytes.length) {
      const head = bytes[offset]
      assert.ok(head === 1 || head === 2, `位置 ${offset} 出现非法帧头 ${head}`)
      offset += head === 1 ? 2 : 3
      presses += head === 1 ? 1 : 0
    }
    assert.equal(offset, bytes.length, '帧长不自洽，说明设备上的字节流被截断')
    assert.equal(presses, 11, '应恰好 11 次按下')
  } finally {
    await server.close()
  }
})

test('关闭虚拟串口后设备节点与符号链接一起消失', async t => {
  const probe = await probeDevice()
  if (!probe.available) {
    t.skip(`本机不支持 PTY 虚拟串口：${probe.reason}`)
    return
  }
  const links = ['/tmp/keysim-test-tty1']
  const server = await startServer({port: 0, bridgePort: 0, deviceLinks: links})
  try {
    const opened = await post(server.url, '/api/port', {open: true})
    const path = opened.state.device.path
    await assert.doesNotReject(open(links[0], 'r').then(handle => handle.close()), '开启后应能打开设备')

    const closed = await post(server.url, '/api/port', {open: false})
    assert.equal(closed.state.device.path, null, '关闭后状态里不应再有设备')
    await new Promise(resolve => setTimeout(resolve, 300))
    await assert.rejects(open(links[0], 'r'), '关闭后符号链接应已移除')
    await assert.rejects(open(path, 'r'), '关闭后 PTY 节点应已回收')
  } finally {
    await server.close()
  }
})
