// 干扰音循环播放。每次播放独占一个AudioContext，停止时必须close：
// 只stop音源而不close，会让输出流一直挂在系统混音器上，反复开关后堆积，
// 在国产低性能机器上与摩尔斯音频线程争抢CPU，造成点划长短波动和卡顿。
// 播放状态仍写回item.ctx/xhr/source/gain，页面现有的音量调节与判空逻辑不变。

export function startDisturbAudio(item, {url, volume, onStarted}) {
	stopDisturbAudio(item)
	const Context = globalThis.AudioContext || globalThis.webkitAudioContext
	const ctx = new Context()
	const xhr = new XMLHttpRequest()
	item.ctx = ctx
	item.xhr = xhr
	xhr.open('GET', url, true)
	xhr.responseType = 'arraybuffer'
	xhr.onload = () => {
		// 加载期间已被停止或重新开始：不能在已关闭或已被替换的context上继续出声。
		if (item.ctx !== ctx) return
		ctx.decodeAudioData(xhr.response).then(buffer => {
			if (item.ctx !== ctx) return
			const source = ctx.createBufferSource()
			source.buffer = buffer
			source.loop = true
			const gain = ctx.createGain()
			gain.gain.value = Number(volume)
			gain.connect(ctx.destination)
			source.connect(gain)
			source.start(0)
			item.source = source
			item.gain = gain
			onStarted?.()
		}).catch(error => {
			if (item.ctx === ctx) console.warn('干扰音频解码失败', url, error)
		})
	}
	xhr.send()
}

export function stopDisturbAudio(item) {
	const {ctx, xhr, source} = item
	item.ctx = null
	item.xhr = null
	item.source = null
	item.gain = null
	xhr?.abort()
	try { source?.stop(0) } catch { /* 音源未启动或已停止 */ }
	ctx?.close().catch(() => {})
}

// 预加载式页面（每类干扰常驻一个context）在卸载时调用，释放全部输出流。
export function releaseDisturbAudio(list) {
	list.forEach(stopDisturbAudio)
}
