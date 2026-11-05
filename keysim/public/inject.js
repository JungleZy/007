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
    : (window.__keysimOrigin || '__KEYSIM_ORIGIN__')
  const wsUrl = origin.replace(/^http/, 'ws') + '/ws/serial'
  const notifyReady = () => {
    // 只标记此标签页；普通浏览器的真实设备连接不会开启模拟模式。
    try { sessionStorage.setItem('keysimSerialOrigin', origin) } catch {}
    window.dispatchEvent(new Event('keysim-serial-ready'))
  }

  if (window.__keysimSerial?.version === 2) {
    console.info('[keysim] 虚拟串口已注入，重连中')
    window.__keysimSerial.reconnect()
    notifyReady()
    return '[keysim] 已重连'
  }

  // 保留原生接口，页面可以随时从模拟器切回真实设备。
  const nativeSerial = window.__keysimSerial?.nativeSerial || navigator.serial
  window.__keysimSerial?.detach()
  let controller = null
  let stream = null
  const pending = []
  const timings = new WeakMap()
  let opened = false

  const port = {
    open: async () => {
      opened = true
      stream = new ReadableStream({start(target) {
        controller = target
        while (pending.length) target.enqueue(pending.shift())
      }, cancel() { controller = null }})
    },
    close: async () => { controller = null; stream = null; pending.length = 0 },
    get readable() { return stream },
    writable: null,
    getInfo: () => ({usbVendorId: 0x4b53, usbProductId: 0x494d}),
    getFrameTiming: bytes => timings.get(bytes),
    addEventListener() {}, removeEventListener() {}
  }

  const serial = {
    getPorts: async () => [port],
    requestPort: async () => port,
    addEventListener() {}, removeEventListener() {}
  }

  // Chromium normally allows an own-property override here.  Keep a
  // prototype fallback for versions/pages where navigator.serial is exposed
  // as a non-writable inherited property.  A failed defineProperty used to
  // abort this IIFE before __keysimSerial and the WebSocket were installed,
  // leaving the training page with the real requestPort() path.
  let installed = false
  for (const target of [navigator, typeof Navigator !== 'undefined' ? Navigator.prototype : null]) {
    if (!target || installed) continue
    try {
      Object.defineProperty(target, 'serial', {
        configurable: true,
        enumerable: true,
        get: () => serial
      })
      installed = navigator.serial === serial
    } catch (error) {
      console.warn('[keysim] 无法覆盖 navigator.serial', error)
    }
  }
  if (!installed) {
    console.error('[keysim] 虚拟串口注入失败：navigator.serial 仍是浏览器原生对象')
    return '[keysim] 注入失败'
  }

  let socket = null
  let closed = false
  let reconnectTimer = null
  let timing = null
  const connect = () => {
    if (closed || (socket && socket.readyState <= WebSocket.OPEN)) return
    socket = new WebSocket(wsUrl)
    socket.binaryType = 'arraybuffer'
    socket.onopen = () => console.info('[keysim] 虚拟串口已连上', wsUrl)
    socket.onmessage = event => {
      if (typeof event.data === 'string') {
        timing = null
        try {
          const data = JSON.parse(event.data)
          if (data.type === 'serial-timing' && Number.isFinite(data.at) && data.at >= 0 && typeof data.clock === 'string') {
            timing = Object.freeze({at: data.at, timeSource: data.clock})
          }
        } catch {}
        return
      }
      const bytes = new Uint8Array(event.data)
      if (timing) timings.set(bytes, timing)
      timing = null
      if (controller) controller.enqueue(bytes)
      else if (!opened) pending.push(bytes)
    }
    socket.onclose = () => {
      timing = null
      if (closed) return
      console.warn('[keysim] 虚拟串口断开，2s 后重连')
      reconnectTimer = setTimeout(connect, 2000)
    }
    socket.onerror = () => {}
  }
  connect()

  window.__keysimSerial = {
    version: 2,
    port,
    serial,
    nativeSerial,
    bytes: bytes => { const data = new Uint8Array(bytes); if (controller) controller.enqueue(data); else if (!opened) pending.push(data) },
    reconnect: () => { closed = false; clearTimeout(reconnectTimer); connect() },
    detach: () => { closed = true; clearTimeout(reconnectTimer); try { socket && socket.close() } catch {} }
  }
  notifyReady()
  console.info('[keysim] 虚拟串口已就绪，已通知页面连接')
  return '[keysim] 虚拟串口已注入'
})()
