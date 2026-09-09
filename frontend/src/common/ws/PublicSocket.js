const activeConnections = new Set()

export const closePublicSockets = () => {
  for (const close of activeConnections) close()
}

export default function publicSocket() {
  let ws = null
  let timer = null
  let flag = true
  let retryCount = 0
  let url
  let fun
  let open

  const ws_connect = (src, onMessage, onOpen = false) => {
    activeConnections.add(closeWebSocket)
    flag = true
    url = src
    fun = onMessage
    open = onOpen
    ws = new WebSocket(`${window.wsUrl}${src}`)
    ws.onopen = event => {
      retryCount = 0
      if (onOpen) onOpen(event)
      console.log('-----连接成功----')
    }
    ws.onerror = () => {}
    ws.onclose = () => {
      if (flag) reconnect()
      else console.log('-----关闭连接----')
    }
    ws.onmessage = event => onMessage(event)
  }

  const reconnect = () => {
    clearTimeout(timer)
    const delay = Math.min(30000, 1000 * (2 ** retryCount++)) + Math.floor(Math.random() * 250)
    timer = setTimeout(() => {
      timer = null
      if (flag) ws_connect(url, fun, open)
    }, delay)
  }

  const closeWebSocket = () => {
    flag = false
    clearTimeout(timer)
    timer = null
    if (ws) ws.close()
    activeConnections.delete(closeWebSocket)
    ws = null
  }

  const sendMessage = obj => {
    if (ws?.readyState === WebSocket.OPEN) ws.send(JSON.stringify(obj))
  }

  return {ws_connect, sendMessage, closeWebSocket}
}
