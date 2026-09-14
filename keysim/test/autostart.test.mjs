import assert from 'node:assert/strict'
import test from 'node:test'
import {open} from 'node:fs/promises'
import {startServer} from '../src/server.mjs'
import {probeDevice} from '../src/providers/device.mjs'

test('启动即开：serve 起来时虚拟串口与设备已经就绪，不需要再点一次', async t => {
  const probe = await probeDevice()
  const links = ['/tmp/keysim-autostart-tty']
  const server = await startServer({port: 0, bridgePort: 0, deviceLinks: links, autoStart: true})
  try {
    const state = (await fetch(`${server.url}/api/state`).then(response => response.json())).state
    assert.equal(state.open, true, '启动后串口应已开启')
    if (!probe.available) {
      t.diagnostic(`本机无 PTY 能力（${probe.reason}），只校验桥接通道`)
      assert.ok(state.bridgeUrl.startsWith('ws://'), '桥接地址应可用')
      return
    }
    assert.ok(state.device.path?.startsWith('/dev/'), `设备节点缺失：${JSON.stringify(state.device)}`)
    // 设备真能打开——这才算"像插了个串口"
    const handle = await open(links[0], 'r')
    await handle.close()
  } finally {
    await server.close()
  }
})

test('自动开启失败不拖垮控制台：桥接端口被占时仍能提供状态与日志', async () => {
  const holder = await startServer({port: 0, bridgePort: 0, autoStart: true})
  const takenPort = holder.serial.state().bridgePort
  try {
    const second = await startServer({port: 0, bridgePort: takenPort, autoStart: true})
    try {
      const state = (await fetch(`${second.url}/api/state`).then(response => response.json())).state
      assert.equal(state.open, false, '桥接端口被占时不应报告已开启')
      assert.equal(state.replay.running, false)
    } finally {
      await second.close()
    }
  } finally {
    await holder.close()
  }
})
