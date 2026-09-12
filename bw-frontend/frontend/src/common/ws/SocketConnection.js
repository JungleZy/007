// Reserved transport frames; business messages remain unchanged JSON payloads.
export const HEARTBEAT_PING = '__nip_heartbeat_ping__'
export const HEARTBEAT_PONG = '__nip_heartbeat_pong__'
const HEARTBEAT_INTERVAL = 30000
const STALE_TIMEOUT = 90000

// 握手凭据注入点（SEC-06）：浏览器的 WebSocket 构造器无法设置请求头，
// 后端 6 个带身份语义的端点只能从 query 读 token/deviceId。
// 这里是全仓唯一注入点——PublicSocket.ws_connect 与 7 处直连都经过本类，
// 且重连复用已带凭据的 this.url，因此训练页无需各自改造。
function withCredentials(url) {
  let token = null
  let deviceId = null
  try {
    token = window.localStorage.getItem('token')
    deviceId = window.localStorage.getItem('deviceId')
  } catch (error) {
    // 存储权限被禁：照原样连接，由服务端拒绝并给出拒因帧
    return url
  }
  if (!token || !deviceId) return url
  const separator = url.includes('?') ? '&' : '?'
  return `${url}${separator}token=${encodeURIComponent(token)}&deviceId=${encodeURIComponent(deviceId)}`
}

export default class SocketConnection {
  constructor() {
    this.socket = null
    this.active = false
    this.generation = 0
    this.retryCount = 0
    this.reconnectTimer = null
    this.heartbeatTimer = null
  }

  connect(url, onMessage, onOpen, onState) {
    this.close()
    this.active = true
    this.url = withCredentials(url)
    this.onMessage = onMessage
    this.onOpen = onOpen
    this.onState = onState
    this.open(this.generation)
  }

  open(generation) {
    if (!this.active || generation !== this.generation) return
    let socket
    try {
      socket = new WebSocket(this.url)
    } catch (error) {
      this.onState?.('offline')
      this.reconnect(generation)
      return
    }
    this.socket = socket
    const current = () => this.active && generation === this.generation && socket === this.socket
    let lastReply = Date.now()
    const disconnected = () => {
      if (!current()) return
      this.onState?.('offline')
      this.releaseSocket()
      this.reconnect(generation)
    }
    // Also bound CONNECTING, which otherwise need not deliver onclose promptly.
    this.heartbeatTimer = setInterval(() => {
      if (!current()) return
      if (Date.now() - lastReply >= STALE_TIMEOUT) {
        disconnected()
      } else if (socket.readyState === WebSocket.OPEN) {
        try {
          socket.send(HEARTBEAT_PING)
        } catch (error) {
          disconnected()
        }
      }
    }, HEARTBEAT_INTERVAL)
    socket.onopen = event => {
      if (!current()) return
      lastReply = Date.now()
      this.retryCount = 0
      this.onState?.('open')
      this.onOpen?.(event)
    }
    socket.onclose = disconnected
    socket.onerror = disconnected
    socket.onmessage = event => {
      if (!current()) return
      if (event.data === HEARTBEAT_PONG) {
        lastReply = Date.now()
        return
      }
      if (event.data === HEARTBEAT_PING) return
      this.onMessage?.(event)
    }
  }

  reconnect(generation) {
    if (!this.active || generation !== this.generation || this.reconnectTimer !== null) return
    const delay = Math.min(30000, 1000 * (2 ** Math.min(this.retryCount++, 5)) + Math.floor(Math.random() * 250))
    this.reconnectTimer = setTimeout(() => {
      if (!this.active || generation !== this.generation) return
      this.reconnectTimer = null
      this.open(generation)
    }, delay)
  }

  releaseSocket() {
    clearInterval(this.heartbeatTimer)
    this.heartbeatTimer = null
    const socket = this.socket
    this.socket = null
    if (!socket) return
    socket.onopen = socket.onclose = socket.onerror = socket.onmessage = null
    if (socket.readyState === WebSocket.OPEN || socket.readyState === WebSocket.CONNECTING) socket.close()
  }

  close() {
    this.active = false
    this.generation++
    clearTimeout(this.reconnectTimer)
    this.reconnectTimer = null
    this.retryCount = 0
    this.releaseSocket()
    this.onState?.('closed')
  }

  send(message) {
    if (this.socket?.readyState === WebSocket.OPEN) this.socket.send(message)
  }

  get isOpen() {
    return this.socket?.readyState === WebSocket.OPEN
  }
}
