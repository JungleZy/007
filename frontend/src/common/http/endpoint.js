const protocolFor = websocket => {
  if (typeof window === 'undefined') return websocket ? 'ws:' : 'http:'
  if (window.location?.protocol === 'https:') return websocket ? 'wss:' : 'https:'
  return websocket ? 'ws:' : 'http:'
}

const withProtocol = (raw, websocket) => {
  const value = String(raw || '').trim()
  if (/^(?:https?|wss?|ws):\/\//i.test(value)) return value.replace(/\/+$/, '')
  return `${protocolFor(websocket)}//${value.replace(/^\/+|\/+$/g, '')}`
}

const join = (raw, path, websocket) => {
  const base = withProtocol(raw, websocket)
  const suffix = String(path || '').replace(/^\/+/, '')
  return suffix ? `${base}/${suffix}` : base
}

export const apiUrl = path => join(window.httpUrl, path, false)

export const wsUrl = path => join(window.wsUrl || window.httpUrl, path, true)
