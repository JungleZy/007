/**
 * 控制台服务：零依赖 HTTP + SSE + 注入通道的 WebSocket 升级。
 * 页面在 public/，接口在 /api/*，事件流在 /api/events。
 */
import {createServer} from 'node:http'
import {readFile} from 'node:fs/promises'
import {createHash} from 'node:crypto'
import {extname, join, normalize} from 'node:path'
import {fileURLToPath} from 'node:url'
import {createVirtualSerial} from './serial.mjs'
import {browserState, closeBrowser, openBrowser, probeBrowser} from './providers/browser.mjs'
import {createParser} from './sinks/bridge.mjs'
import {electronKeyPlan, electronKeyTimeline} from './electronkey.mjs'
import {handKeyPlan, handKeyTimeline} from './handkey.mjs'
import {applyFaults, TIMELINE_FAULTS} from './faults.mjs'
import {randomMessage, validateMessage} from './random.mjs'
import {ALPHABETS} from './tables.mjs'
import {toByteStream, toHex} from './sinks/bytes.mjs'
import {toFrames} from './sinks/frames.mjs'

const PUBLIC = fileURLToPath(new URL('../public/', import.meta.url))
const GUID = '258EAFA5-E914-47DA-95CA-C5AB0DC85B11'
const MIME = {
  '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8', '.svg': 'image/svg+xml', '.ico': 'image/x-icon'
}

const json = (response, code, body) => {
  const text = JSON.stringify(body)
  response.writeHead(code, {'content-type': 'application/json; charset=utf-8', 'access-control-allow-origin': '*'})
  response.end(text)
}

const readBody = request => new Promise((resolve, reject) => {
  const chunks = []
  request.on('data', chunk => {
    chunks.push(chunk)
    if (chunks.reduce((sum, part) => sum + part.length, 0) > 2_000_000) reject(new Error('请求体过大'))
  })
  request.on('end', () => {
    const raw = Buffer.concat(chunks).toString('utf8')
    if (!raw) return resolve({})
    try { resolve(JSON.parse(raw)) } catch (error) { reject(new Error(`请求体不是合法 JSON：${error.message}`)) }
  })
  request.on('error', reject)
})

/** 由控制台参数生成一份时间轴；两种键型的参数口径不同，这里统一入口。 */
export function buildTimeline(options = {}) {
  const {
    key = 'hand', text = '', alphabet = 'letter', rate, jitter = 0, seed = 1,
    preamble = true, tail, skew = 50, singlePage = true, faults = []
  } = options
  const checked = validateMessage(text, alphabet)
  if (!checked.ok) throw new Error(checked.reason)
  const spec = Object.fromEntries(faults.filter(name => TIMELINE_FAULTS[name]).map(name => [name, true]))
  if (key === 'hand') {
    const plan = handKeyPlan({
      rate: Number(rate ?? 100), type: alphabet, skew: Number(skew), jitter: Number(jitter), pageTurns: !singlePage
    })
    return applyFaults(handKeyTimeline({
      text, alphabet, plan, jitter: Number(jitter), seed: Number(seed), preamble,
      tail: tail === undefined ? 'turn' : tail === 'none' ? null : tail
    }), spec)
  }
  const plan = electronKeyPlan({rate: Number(rate ?? 20)})
  return applyFaults(electronKeyTimeline({
    text, alphabet, plan, jitter: Number(jitter), seed: Number(seed), preamble,
    tail: tail === undefined ? 'page' : tail === 'none' ? null : tail
  }), spec)
}

export function startServer({port = 18700, host = '127.0.0.1', bridgePort = 18765, deviceLinks, autoStart = false, openUrl = null} = {}) {
  const listeners = new Set()
  const broadcast = event => {
    const payload = `data: ${JSON.stringify(event)}\n\n`
    for (const response of listeners) response.write(payload)
  }
  const serial = createVirtualSerial({bridgePort, deviceLinks, onEvent: broadcast})

  const routes = {
    'GET /api/state': async () => ({
      ok: true,
      state: {...serial.state(), browser: {...browserState(), ...(await probeBrowser())}},
      alphabets: ALPHABETS,
      faults: Object.keys(TIMELINE_FAULTS)
    }),
    'POST /api/device/link': async body => {
      const result = body.remove ? await serial.unlinkSystem(body.target) : await serial.linkSystem(body.target)
      return {...result, state: serial.state()}
    },
    'POST /api/browser/open': async body => {
      const state = await openBrowser({url: body.url, origin: `http://${host}:${server.address().port}`, headless: body.headless === true})
      broadcast({type: 'log', level: 'ok', message: `已打开被测页面并预置虚拟串口：${state.url}`, at: Date.now()})
      return {ok: true, browser: state}
    },
    'POST /api/browser/close': async () => {
      const state = await closeBrowser()
      broadcast({type: 'log', level: 'warn', message: '被测页面已关闭', at: Date.now()})
      return {ok: true, browser: state}
    },
    'POST /api/device/probe': async () => ({ok: true, probe: await serial.probe(), state: serial.state()}),
    'POST /api/port': async body => {
      const state = body.open === false ? await serial.close() : await serial.open()
      return {ok: true, state}
    },
    'POST /api/message/random': async body => ({
      ok: true,
      text: randomMessage({alphabet: body.alphabet ?? 'letter', groups: Number(body.groups ?? 4), seed: body.seed ?? null})
    }),
    'POST /api/message/validate': async body => {
      const checked = validateMessage(body.text ?? '', body.alphabet ?? 'letter')
      return {ok: checked.ok, reason: checked.reason ?? null, groups: checked.groups ?? []}
    },
    'POST /api/preview': async body => {
      const timeline = buildTimeline(body)
      return {
        ok: true,
        key: timeline.key,
        duration: timeline.duration,
        events: timeline.events.length,
        chars: timeline.chars.length,
        plan: timeline.plan,
        head: toHex(toByteStream(timeline)).slice(0, 12),
        frames: toFrames(timeline).slice(0, 6).map(item => ({at: item.at, frame: item.frame}))
      }
    },
    'POST /api/send': async body => {
      const timeline = buildTimeline(body)
      const promise = serial.send(timeline, {speed: Number(body.speed ?? 1), text: body.text})
      promise.catch(error => broadcast({type: 'log', level: 'error', message: `拍发失败：${error.message}`, at: Date.now()}))
      return {ok: true, duration: timeline.duration, events: timeline.events.length, state: serial.state()}
    },
    'POST /api/stop': async () => ({ok: true, stopped: await serial.stop(), state: serial.state()})
  }

  const server = createServer(async (request, response) => {
    const url = new URL(request.url, `http://${host}:${port}`)
    if (request.method === 'OPTIONS') {
      response.writeHead(204, {
        'access-control-allow-origin': '*',
        'access-control-allow-headers': 'content-type',
        'access-control-allow-methods': 'GET,POST,OPTIONS'
      })
      return response.end()
    }
    if (url.pathname === '/api/events') {
      response.writeHead(200, {
        'content-type': 'text/event-stream; charset=utf-8',
        'cache-control': 'no-cache',
        connection: 'keep-alive',
        'access-control-allow-origin': '*'
      })
      response.write(`data: ${JSON.stringify({type: 'state', state: serial.state(), at: Date.now()})}\n\n`)
      listeners.add(response)
      request.on('close', () => listeners.delete(response))
      return
    }
    const route = routes[`${request.method} ${url.pathname}`]
    if (route) {
      try {
        const body = request.method === 'POST' ? await readBody(request) : {}
        return json(response, 200, await route(body))
      } catch (error) {
        return json(response, 200, {ok: false, error: error.message})
      }
    }
    if (request.method !== 'GET') return json(response, 405, {ok: false, error: '方法不支持'})

    const requested = url.pathname === '/' ? '/index.html' : url.pathname
    const file = join(PUBLIC, normalize(requested).replace(/^(\.\.[/\\])+/, ''))
    try {
      const content = await readFile(file)
      response.writeHead(200, {
        'content-type': MIME[extname(file)] ?? 'application/octet-stream',
        'access-control-allow-origin': '*',
        'cache-control': 'no-store'
      })
      response.end(content)
    } catch {
      response.writeHead(404, {'content-type': 'text/plain; charset=utf-8'})
      response.end('not found')
    }
  })

  // 注入通道：被测页面里的假 navigator.serial 连到这里收字节
  server.on('upgrade', (request, socket) => {
    const url = new URL(request.url, `http://${host}:${port}`)
    const key = request.headers['sec-websocket-key']
    if (url.pathname !== '/ws/serial' || !key) return socket.end('HTTP/1.1 400 Bad Request\r\n\r\n')
    socket.write([
      'HTTP/1.1 101 Switching Protocols',
      'Upgrade: websocket',
      'Connection: Upgrade',
      `Sec-WebSocket-Accept: ${createHash('sha1').update(key + GUID).digest('base64')}`,
      '\r\n'
    ].join('\r\n'))
    socket.setNoDelay(true)
    const parser = createParser({onText: () => {}, onClose: () => socket.end(), onPing: () => {}})
    socket.on('data', chunk => { try { parser(chunk) } catch { /* 注入通道只下行 */ } })
    socket.on('error', () => socket.destroy())
    serial.attachInject(socket)
  })

  return new Promise((resolve, reject) => {
    server.once('error', reject)
    server.listen(port, host, async () => {
      if (autoStart) {
        try {
          await serial.open()
        } catch (error) {
          broadcast({type: 'log', level: 'error', message: `自动开启虚拟串口失败：${error.message}`, at: Date.now()})
        }
      }
      if (openUrl) {
        try {
          await openBrowser({url: openUrl, origin: `http://${host}:${server.address().port}`})
        } catch (error) {
          broadcast({type: 'log', level: 'warn', message: `自动打开被测页面失败：${error.message}`, at: Date.now()})
        }
      }
      resolve({
      url: `http://${host}:${server.address().port}`,
      port: server.address().port,
      serial,
      close: async () => {
        await closeBrowser()
        await serial.close()
        for (const response of listeners) response.end()
        listeners.clear()
        return new Promise(done => server.close(done))
      }
      })
    })
  })
}
