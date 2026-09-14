/**
 * 在被测页面里把 navigator.serial 换成 keysim 的虚拟串口。
 * 用法（被测页面控制台）：
 *   fetch('http://127.0.0.1:18700/inject.js').then(r => r.text()).then(eval)
 * 之后页面走的仍是生产代码：字节 -> WebSerial.handleData -> publishTrafficFrame -> 点划判定。
 *
 * 说明：Web Serial 需要用户手势才能 requestPort()，所以这里让 getPorts() 直接返回
 * 一个已授权端口——与真实场景里"上次已授权过该串口"的状态一致（WebSerial.js:21-29）。
 */
(() => {
  const origin = (document.currentScript && document.currentScript.src)
    ? new URL(document.currentScript.src).origin
    : (window.__keysimOrigin || 'http://127.0.0.1:18700')
  const wsUrl = origin.replace(/^http/, 'ws') + '/ws/serial'

  if (window.__keysimSerial) {
    console.info('[keysim] 虚拟串口已注入，重连中')
    window.__keysimSerial.reconnect()
    return '[keysim] 已重连'
  }

  let controller = null
  let stream = null
  const pending = []

  const port = {
    open: async () => {
      stream = new ReadableStream({start(target) {
        controller = target
        while (pending.length) target.enqueue(pending.shift())
      }})
    },
    close: async () => { controller = null; stream = null },
    get readable() { return stream },
    writable: null,
    getInfo: () => ({usbVendorId: 0x4b53, usbProductId: 0x494d}),
    addEventListener() {}, removeEventListener() {}
  }

  Object.defineProperty(navigator, 'serial', {
    configurable: true,
    value: {
      getPorts: async () => [port],
      requestPort: async () => port,
      addEventListener() {}, removeEventListener() {}
    }
  })

  let socket = null
  let closed = false
  const connect = () => {
    socket = new WebSocket(wsUrl)
    socket.binaryType = 'arraybuffer'
    socket.onopen = () => console.info('[keysim] 虚拟串口已连上', wsUrl)
    socket.onmessage = event => {
      if (typeof event.data === 'string') return
      const bytes = new Uint8Array(event.data)
      if (controller) controller.enqueue(bytes)
      else pending.push(bytes)
    }
    socket.onclose = () => {
      if (closed) return
      console.warn('[keysim] 虚拟串口断开，2s 后重连')
      setTimeout(connect, 2000)
    }
    socket.onerror = () => {}
  }
  connect()

  window.__keysimSerial = {
    port,
    bytes: bytes => { const data = new Uint8Array(bytes); controller ? controller.enqueue(data) : pending.push(data) },
    reconnect: () => { try { socket && socket.close() } catch {} connect() },
    detach: () => { closed = true; try { socket && socket.close() } catch {} }
  }
  console.info('[keysim] navigator.serial 已替换为虚拟串口；页面若已初始化串口，请在串口页重新连接一次')
  return '[keysim] 虚拟串口已注入'
})()
