// 桌面端由 electron/preload.js 经 contextBridge 注入 window.electron；Web 端没有该对象。
// 窗口已开启 contextIsolation 并关闭 nodeIntegration，渲染进程不存在 window.require，故不再做该回退。
const Renderer = window.electron || {}

/**
 * ipc
 * 官方api说明：https://www.electronjs.org/zh/docs/latest/api/ipc-renderer
 *
 * 可用方法仅限 preload 白名单（electron/preload.js），其余 ipcRenderer 方法一律不可用：
 * ipc.invoke(channel, param) - 发送异步消息（invoke/handle 模型），返回 Promise
 * ipc.send(channel, ...args) - 通过 channel 向主进程发送异步消息
 * ipc.sendSync(channel, param) - 发送同步消息（send/on 模型），返回主进程回填的 event.returnValue
 * ipc.on(channel, listener) - 监听 channel；listener 签名为 (event, ...args)，其中 event 恒为 null 占位
 * ipc.once(channel, listener) - 同上，仅触发一次
 * ipc.off(channel) - 移除该 channel 上由本渲染进程注册的全部监听
 *
 * 注意：不提供 removeListener —— 渲染侧持有的 listener 与 preload 内注册的 wrapper 不是同一引用，
 * 永远匹配不上；需要解绑请用 ipc.off(channel)。
 */

/**
 * ipc
 */
const ipc = Renderer.ipcRenderer || undefined

/**
 * 是否为EE环境
 */
const isEE = !!ipc

export { Renderer, ipc, isEE }
