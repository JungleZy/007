/**
 * 虚拟串口：一个开关，两条真实链路，同一份时间轴同时下发，互不抢占。
 *
 * - 桥接通道（默认 18765）：桌面模式下被测应用主动连过来
 *   （MessageWebSocket.js:115 连 ws://localhost:18765/echo），线上跑的是帧级 JSON `{data:…}`。
 * - 注入通道（/ws/serial）：Web 模式下被测页面里的假 navigator.serial 连回来，
 *   线上跑的是**字节**，与真串口一致，客户端仍由生产代码 WebSerial.handleData 解析。
 *
 * 两条通道由同一个调度器驱动，保证"同一次拍发"在桌面与 Web 两种模式下节拍一致。
 */
import {startBridge} from './sinks/bridge.mjs'
import {DEFAULT_LINKS, linkIntoSystem, openDevice, probeDevice, probeElevation, SYSTEM_LINK, unlinkFromSystem} from './providers/device.mjs'
import {probeKernelBackends, startKernelSerial} from './providers/kernel.mjs'
import {open as openFile} from 'node:fs/promises'
import {toBridgeMessages} from './sinks/frames.mjs'
import {toByteStream} from './sinks/bytes.mjs'

export function createVirtualSerial({bridgePort = 18765, deviceLinks = DEFAULT_LINKS, onEvent = () => {}} = {}) {
  const injectClients = new Set()
  let bridge = null
  let device = null
  let deviceProbe = null
  let systemLinked = null
  let elevation = null
  let kernel = null          // 内核级后端（浏览器/桌面都能直接选到的那种）
  let kernelHandle = null    // 我们写入的那一端
  let kernelBackends = null
  let replay = null

  const emit = (type, payload = {}) => onEvent({type, at: Date.now(), ...payload})

  const state = () => ({
    open: bridge !== null,
    kernel: {
      id: kernel ? kernel.id : null,
      title: kernel ? kernel.title : null,
      /** 被测程序（浏览器选择框 / 桌面串口列表）应该选的那个口 */
      selectable: kernel ? kernel.theirs : null,
      backends: kernelBackends
    },
    device: {
      path: device ? device.path : null,
      links: device ? device.links : [],
      warnings: device ? device.warnings : [],
      available: deviceProbe ? deviceProbe.available : null,
      reason: deviceProbe ? deviceProbe.reason : null,
      systemLink: SYSTEM_LINK,
      systemLinked,
      elevation,
      systemLinkHint: device ? device.systemLinkHint() : null
    },
    bridgePort: bridge ? bridge.port : bridgePort,
    bridgeUrl: bridge ? bridge.url : `ws://127.0.0.1:${bridgePort}/echo`,
    bridgeClients: bridge ? bridge.clients.size : 0,
    injectClients: injectClients.size,
    replay: replay ? {running: true, sent: replay.sent, total: replay.total, key: replay.key, text: replay.text}
      : {running: false, sent: 0, total: 0}
  })

  return {
    state,
    /** 注入通道的 socket 由 server 在 upgrade 时交进来。 */
    attachInject(socket) {
      injectClients.add(socket)
      emit('log', {level: 'info', message: `浏览器虚拟串口已接入（当前 ${injectClients.size} 个）`})
      emit('state', {state: state()})
      socket.on('close', () => {
        injectClients.delete(socket)
        emit('log', {level: 'info', message: `浏览器虚拟串口断开（剩 ${injectClients.size} 个）`})
        emit('state', {state: state()})
      })
    },
    async probe() {
      deviceProbe = await probeDevice()
      elevation = await probeElevation()
      kernelBackends = await probeKernelBackends()
      return {device: deviceProbe, elevation, kernel: kernelBackends}
    },
    /** 把设备接进系统串口列表：root 直接建链接，否则弹一次系统授权框。 */
    async linkSystem(target = SYSTEM_LINK) {
      if (!device) return {ok: false, error: '虚拟串口未开启'}
      const result = await linkIntoSystem(device.path, target)
      systemLinked = result.ok ? result.target : null
      emit('log', {
        level: result.ok ? 'ok' : 'warn',
        message: result.ok
          ? `已接入系统串口列表：${result.target} → ${device.path}${result.elevated ? '（经系统授权）' : ''}`
          : `接入系统串口列表失败：${result.error}${result.hint ? `；可手动执行 ${result.hint}` : ''}`
      })
      emit('state', {state: state()})
      return result
    },
    async unlinkSystem(target = SYSTEM_LINK) {
      const result = await unlinkFromSystem(device ? device.path : null, target)
      if (result.ok) systemLinked = null
      emit('log', {level: result.ok ? 'ok' : 'warn', message: result.ok ? `已移出系统串口列表：${target}` : `移出失败：${result.error}`})
      emit('state', {state: state()})
      return result
    },
    async open() {
      if (bridge) return state()
      // 真设备优先：开出来就是系统里的一个字符设备，任何串口客户端都能打开
      // 先试内核级：成功的话被测程序能在浏览器/桌面的串口列表里直接选中它
      kernelBackends = await probeKernelBackends()
      const started = await startKernelSerial()
      if (started.id) {
        kernel = started
        try {
          kernelHandle = await openFile(started.ours, 'r+')
          emit('log', {level: 'ok', message: `内核级虚拟串口已就绪（${started.title}）：被测程序请选 ${started.theirs}`})
        } catch (error) {
          kernelHandle = null
          kernel = null
          await started.stop().catch(() => {})
          emit('log', {level: 'warn', message: `内核级虚拟串口打开失败，退回 PTY：${error.message}`})
        }
      } else {
        const why = started.attempts.map(item => `${item.id}: ${item.error}`).join('；')
        emit('log', {level: 'info', message: `没有可用的内核级虚拟串口后端（${why}），使用 PTY 设备；浏览器选择框看不到 PTY，Web 模式请用「打开并预置虚拟串口」`})
      }

      deviceProbe = await probeDevice()
      elevation = await probeElevation()
      if (deviceProbe.available) {
        try {
          device = await openDevice({
            links: deviceLinks,
            onEvent: event => {
              if (event.type === 'device') emit('log', {level: 'ok', message: `虚拟串口设备已就绪：${event.path}${event.links.length ? ' → ' + event.links.join(' , ') : ''}`})
              else if (event.type === 'rx') emit('log', {level: 'recv', message: `设备对端写入：${event.hex}`})
              else emit('log', {level: event.type === 'error' ? 'error' : 'warn', message: event.message})
            }
          })
        } catch (error) {
          device = null
          emit('log', {level: 'warn', message: `虚拟串口设备创建失败，仅启用桥接/注入通道：${error.message}`})
        }
      } else {
        emit('log', {level: 'warn', message: `本机不具备创建虚拟串口设备的条件：${deviceProbe.reason}`})
      }
      bridge = await startBridge({
        port: bridgePort,
        log: message => emit('log', {level: 'info', message})
      })
      bridge.onConnect(() => {
        emit('log', {level: 'info', message: `桌面桥接已接入（当前 ${bridge.clients.size} 个）`})
        emit('state', {state: state()})
      })
      bridge.onMessage(entry => emit('log', {level: 'recv', message: `桥接收到：${entry.text.slice(0, 160)}`}))
      emit('log', {level: 'ok', message: `虚拟串口已开启：${bridge.url}`})
      emit('state', {state: state()})
      return state()
    },
    async close() {
      await this.stop()
      if (kernelHandle) { await kernelHandle.close().catch(() => {}); kernelHandle = null }
      if (kernel) {
        const result = await kernel.stop().catch(error => ({ok: false, error: error.message}))
        emit('log', {level: result.ok ? 'warn' : 'warn', message: result.ok ? '内核级虚拟串口已移除' : `移除内核级虚拟串口失败：${result.error}`})
        kernel = null
      }
      if (systemLinked) await unlinkFromSystem(device ? device.path : null, systemLinked).catch(() => {})
      systemLinked = null
      if (device) {
        await device.close()
        device = null
        emit('log', {level: 'warn', message: '虚拟串口设备已移除'})
      }
      if (bridge) {
        await bridge.close()
        bridge = null
        emit('log', {level: 'warn', message: '虚拟串口已关闭'})
      }
      for (const socket of injectClients) socket.destroy()
      injectClients.clear()
      emit('state', {state: state()})
      return state()
    },
    /**
     * 按时间轴同时向两条通道回放。带漂移校正，1ms 前视窗把同刻事件一次发出。
     * speed>1 整体加速（注意客户端的静默/800ms 定时器不会跟着缩放）。
     */
    send(timeline, {speed = 1, key = timeline.key, text = ''} = {}) {
      if (!bridge) throw new Error('虚拟串口未开启')
      if (replay) throw new Error('上一次拍发还在进行')
      const frames = toBridgeMessages(timeline)
      const chunks = toByteStream(timeline)
      if (frames.length !== chunks.length) throw new Error('帧与字节序列长度不一致，时间轴异常')
      const items = frames.map((frame, index) => ({at: frame.at, text: frame.text, bytes: chunks[index].bytes}))

      return new Promise(resolve => {
        const started = performance.now()
        replay = {sent: 0, total: items.length, key, text, cancel: false, timer: null, finish: null}
        emit('log', {level: 'ok', message: `开始拍发：${key === 'hand' ? '手键' : '电子键'} ${items.length} 帧，约 ${(timeline.duration / 1000 / speed).toFixed(1)}s`})
        emit('state', {state: state()})
        const finish = reason => {
          if (!replay) return
          const sent = replay.sent
          clearTimeout(replay.timer)
          replay = null
          emit('log', {level: reason === 'done' ? 'ok' : 'warn', message: reason === 'done' ? `拍发完成，共 ${sent} 帧` : `拍发已中止，已发 ${sent} 帧`})
          emit('state', {state: state()})
          resolve({sent, aborted: reason !== 'done'})
        }
        replay.finish = finish
        const tick = () => {
          if (!replay || replay.cancel) return finish('cancelled')
          const elapsed = performance.now() - started
          while (replay.sent < items.length && items[replay.sent].at / speed <= elapsed + 1) {
            const item = items[replay.sent++]
            bridge.broadcast(item.text)
            if (kernelHandle) kernelHandle.write(Buffer.from(item.bytes)).catch(() => {})
            if (device) device.write(item.bytes)
            for (const socket of injectClients) if (socket.writable) socket.write(binaryFrame(item.bytes))
          }
          if (replay.sent % 20 === 0) emit('progress', {sent: replay.sent, total: items.length})
          if (replay.sent >= items.length) {
            emit('progress', {sent: replay.sent, total: items.length})
            return finish('done')
          }
          replay.timer = setTimeout(tick, Math.max(0, items[replay.sent].at / speed - (performance.now() - started)))
        }
        tick()
      })
    },
    /** 立刻停：清掉待发定时器并当场收尾，返回后 state() 已经是"未在回放"。 */
    async stop() {
      if (!replay) return false
      replay.cancel = true
      replay.finish?.('cancelled')
      return true
    }
  }
}

/** 注入通道发的是二进制帧（opcode 0x2），载荷就是串口字节。 */
function binaryFrame(bytes) {
  const data = Buffer.from(bytes)
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
  header[0] = 0x82
  return Buffer.concat([header, data])
}
