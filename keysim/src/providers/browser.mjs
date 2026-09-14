/**
 * 一键打开被测页面：keysim 自己拉起浏览器，并在任何页面脚本之前把
 * navigator.serial 换成本机虚拟串口——不需要去页面控制台粘任何东西。
 *
 * Chromium 的 Web Serial 只枚举 udev 设备，看不到 PTY（见 providers/device.mjs 的边界说明），
 * 所以 Web 模式的"插入串口"就落在这里：页面拿到的是一个已授权的虚拟端口，
 * 之后仍由生产代码 WebSerial.handleData 解析字节。
 */
import {readFile} from 'node:fs/promises'
import {fileURLToPath} from 'node:url'

const INJECT = fileURLToPath(new URL('../../public/inject.js', import.meta.url))
const CHANNELS = ['chrome', 'msedge', 'chromium']

let launched = null

export async function probeBrowser() {
  try {
    await import('playwright-core')
  } catch {
    return {available: false, reason: '缺少 playwright-core（cd keysim && npm install）'}
  }
  return {available: true, reason: null}
}

export function browserState() {
  return {open: Boolean(launched), url: launched ? launched.url : null}
}

/**
 * 启动浏览器并打开 url；origin 是 keysim 控制台地址，注入脚本据此连回虚拟串口。
 * headless 默认 false —— 目的是让人接着做业务操作。
 */
export async function openBrowser({url, origin, headless = false} = {}) {
  if (!url) throw new Error('缺少被测页面地址')
  const probe = await probeBrowser()
  if (!probe.available) throw new Error(probe.reason)
  if (launched) await closeBrowser()

  const {chromium} = await import('playwright-core')
  const inject = await readFile(INJECT, 'utf8')
  let browser = null
  const failures = []
  for (const channel of CHANNELS) {
    try {
      browser = await chromium.launch({channel, headless, args: ['--no-sandbox']})
      break
    } catch (error) {
      failures.push(`${channel}: ${error.message.split('\n')[0]}`)
    }
  }
  if (!browser) throw new Error(`没找到可用的浏览器（${failures.join(' / ')}）`)

  const context = await browser.newContext({viewport: null})
  await context.addInitScript(`window.__keysimOrigin = ${JSON.stringify(origin)}`)
  await context.addInitScript(inject)
  const page = await context.newPage()
  await page.goto(url, {waitUntil: 'domcontentloaded'}).catch(error => {
    throw new Error(`打开 ${url} 失败：${error.message.split('\n')[0]}`)
  })
  launched = {browser, context, page, url}
  return browserState()
}

export async function closeBrowser() {
  if (!launched) return browserState()
  const {browser, context} = launched
  launched = null
  await context.close().catch(() => {})
  await browser.close().catch(() => {})
  return browserState()
}
