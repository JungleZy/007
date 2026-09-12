/**
 * 渲染进程预加载脚本。
 *
 * 窗口已开启 contextIsolation 并关闭 nodeIntegration，渲染进程不再有 require / process，
 * 本文件是主世界访问主进程的唯一通道：只向 window.electron.ipcRenderer 暴露
 * { invoke, send, sendSync, on, once, off } 这一组方法，别的一律不给。
 *
 * 几条不可改的约定（改了会直接弄坏生产页面，见每处说明）：
 * 1. 必须暴露 sendSync —— 网络设置页（components/common/NetSetting.vue 的 getConfig /
 *    getLocalIP / changeConfig）与串口连接（components/common/NipSerial.vue、
 *    views/manage/main/components/PreviewHJ.vue 的 linkPort）共 5 处同步调用；
 *    缺了它网络设置页进不去（改不了后端地址）、串口连不上，直接 TypeError。
 *    对应的 ipcMain.on 处理器回填的 event.returnValue 都是纯数据（对象 / 数组 / 字符串），
 *    结构化克隆安全，可以经 contextBridge 回传。
 * 2. on / once 的回调必须保持 (event, data) 两参签名 —— 唯一活的消费点
 *    components/common/NipSerial.vue 就是这么写的。这里用 null 占位第一个参数：
 *    既保住签名，又不把真的 IpcRendererEvent 代理进主世界（它的 sender 就是完整的
 *    ipcRenderer，等于把隔离白开）。按官方样例把 event 整个剔掉会让串口列表永远拿不到数据。
 * 3. 不暴露 removeListener —— 渲染侧持有的 listener 与本文件注册的 wrapper 不是同一个引用，
 *    removeListener 永远匹配不上。取而代之的是 off(channel)：按 channel 整体摘除，
 *    wrapper 映射维护在本文件内。
 */
const {contextBridge, ipcRenderer} = require('electron')

/** channel -> 本文件为该 channel 注册过的 wrapper 集合，off(channel) 据此摘除。 */
const listeners = new Map()

const assertChannel = (channel) => {
	if (typeof channel !== 'string' || !channel) throw new TypeError('ipc channel 必须是非空字符串')
	return channel
}

const drop = (channel, wrapper) => {
	const registered = listeners.get(channel)
	if (!registered) return
	registered.delete(wrapper)
	if (registered.size === 0) listeners.delete(channel)
}

const subscribe = (method, channel, listener) => {
	assertChannel(channel)
	if (typeof listener !== 'function') throw new TypeError('ipc listener 必须是函数')
	// once 触发后 ipcRenderer 会自行解绑，这里同步清掉映射，避免 off 去摘已失效的 wrapper。
	const wrapper = method === 'once'
		? (_event, ...args) => {
			drop(channel, wrapper)
			listener(null, ...args)
		}
		: (_event, ...args) => listener(null, ...args)
	let registered = listeners.get(channel)
	if (!registered) listeners.set(channel, registered = new Set())
	registered.add(wrapper)
	ipcRenderer[method](channel, wrapper)
}

contextBridge.exposeInMainWorld('electron', {
	ipcRenderer: {
		invoke: (channel, ...args) => ipcRenderer.invoke(assertChannel(channel), ...args),
		send: (channel, ...args) => {
			ipcRenderer.send(assertChannel(channel), ...args)
		},
		sendSync: (channel, ...args) => ipcRenderer.sendSync(assertChannel(channel), ...args),
		// 不返回 ipcRenderer 自身（原生 on/once 会返回 this），避免链式写法把实例带进主世界。
		on: (channel, listener) => {
			subscribe('on', channel, listener)
		},
		once: (channel, listener) => {
			subscribe('once', channel, listener)
		},
		off: (channel) => {
			assertChannel(channel)
			const registered = listeners.get(channel)
			if (!registered) return
			// 只摘本文件注册过的 wrapper，不用 removeAllListeners，免得连带清掉 preload 自身的监听。
			for (const wrapper of registered) ipcRenderer.removeListener(channel, wrapper)
			listeners.delete(channel)
		}
	}
})
