class EventEmitter {
	constructor() {
		this.listeners = {}
	}

	// 添加事件监听器
	on(event, callback) {
		if (!this.listeners[event]) {
			this.listeners[event] = []
		}
		this.listeners[event].push(callback)
	}

	// 移除事件监听器
	off(event, callback) {
		if (this.listeners[event]) {
			this.listeners[event] = this.listeners[event].filter(
				(listener) => listener !== callback
			)
		}
	}

	// 触发事件
	emit(event, ...args) {
		if (this.listeners[event]) {
			this.listeners[event].forEach((listener) => listener(...args))
		}
	}
}

const forwardTable = {
	letter: {
		'!': [0, 2, 1, 2, 0, 2, 1, 2, 0],//结束
		'#': [1, 2, 0, 2, 0, 2, 0, 2, 1],//开始
		'。': [0, 2, 0, 2, 0, 2, 0, 2, 0,2,0],//句号
		'?': [0, 2, 0, 2, 1, 2, 1, 2, 0,2,0],//问好
		'-': [0, 2, 0, 2, 1, 2, 0, 2, 1,2,1],//改错前一组
		A: [0, 2, 1],
		B: [1, 2, 0, 2, 0, 2, 0],
		C: [1, 2, 0, 2, 1, 2, 0],
		D: [1, 2, 0, 2, 0],
		E: [0],
		F: [0, 2, 0, 2, 1, 2, 0],
		G: [1, 2, 1, 2, 0],
		H: [0, 2, 0, 2, 0, 2, 0],
		I: [0, 2, 0],
		J: [0, 2, 1, 2, 1, 2, 1],
		K: [1, 2, 0, 2, 1],
		L: [0, 2, 1, 2, 0, 2, 0],
		M: [1, 2, 1],
		N: [1, 2, 0],
		O: [1, 2, 1, 2, 1],
		P: [0, 2, 1, 2, 1, 2, 0],
		Q: [1, 2, 1, 2, 0, 2, 1],
		R: [0, 2, 1, 2, 0],
		S: [0, 2, 0, 2, 0],
		T: [1],
		U: [0, 2, 0, 2, 1],
		V: [0, 2, 0, 2, 0, 2, 1],
		W: [0, 2, 1, 2, 1],
		X: [1, 2, 0, 2, 0, 2, 1],
		Y: [1, 2, 0, 2, 1, 2, 1],
		Z: [1, 2, 1, 2, 0, 2, 0]
	},
	long: {
		'!': [0, 2, 1, 2, 0, 2, 1, 2, 0],//结束
		'#': [1, 2, 0, 2, 0, 2, 0, 2, 1],//开始
		'。': [0, 2, 0, 2, 0, 2, 0, 2, 0,2,0],//句号
		'?': [0, 2, 0, 2, 1, 2, 1, 2, 0,2,0],//问好
		'-': [0, 2, 0, 2, 1, 2, 0, 2, 1,2,1],//改错前一组
		0: [1, 2, 1, 2, 1, 2, 1, 2, 1],
		1: [0, 2, 1, 2, 1, 2, 1, 2, 1],
		2: [0, 2, 0, 2, 1, 2, 1, 2, 1],
		3: [0, 2, 0, 2, 0, 2, 1, 2, 1],
		4: [0, 2, 0, 2, 0, 2, 0, 2, 1],
		5: [0, 2, 0, 2, 0, 2, 0, 2, 0],
		6: [1, 2, 0, 2, 0, 2, 0, 2, 0],
		7: [1, 2, 1, 2, 0, 2, 0, 2, 0],
		8: [1, 2, 1, 2, 1, 2, 0, 2, 0],
		9: [1, 2, 1, 2, 1, 2, 1, 2, 0]
	},
	short: {
		'!': [0, 2, 1, 2, 0, 2, 1, 2, 0],//结束
		'#': [1, 2, 0, 2, 0, 2, 0, 2, 1],//开始
		'。': [0, 2, 0, 2, 0, 2, 0, 2, 0,2,0],//句号
		'?': [0, 2, 0, 2, 1, 2, 1, 2, 0,2,0],//问好
		'-': [0, 2, 0, 2, 1, 2, 0, 2, 1,2,1],//改错前一组
		0: [1],
		1: [0, 2, 1],
		2: [0, 2, 0, 2, 1],
		3: [0, 2, 0, 2, 0, 2, 1, 2, 1],
		4: [0, 2, 0, 2, 0, 2, 0, 2, 1],
		5: [0, 2, 0, 2, 0, 2, 0, 2, 0],
		6: [1, 2, 0, 2, 0, 2, 0, 2, 0],
		7: [1, 2, 1, 2, 0, 2, 0, 2, 0],
		8: [1, 2, 0, 2, 0],
		9: [1, 2, 0]
	},
	mix: {
		'!': [0, 2, 1, 2, 0, 2, 1, 2, 0],//结束
		'#': [1, 2, 0, 2, 0, 2, 0, 2, 1],//开始
		'。': [0, 2, 0, 2, 0, 2, 0, 2, 0, 2, 0],//句号
		'?': [0, 2, 0, 2, 1, 2, 1, 2, 0, 2, 0],//问好
		'-': [0, 2, 0, 2, 1, 2, 0, 2, 1,2,1],//改错前一组
		A: [0, 2, 1],
		B: [1, 2, 0, 2, 0, 2, 0],
		C: [1, 2, 0, 2, 1, 2, 0],
		D: [1, 2, 0, 2, 0],
		E: [0],
		F: [0, 2, 0, 2, 1, 2, 0],
		G: [1, 2, 1, 2, 0],
		H: [0, 2, 0, 2, 0, 2, 0],
		I: [0, 2, 0],
		J: [0, 2, 1, 2, 1, 2, 1],
		K: [1, 2, 0, 2, 1],
		L: [0, 2, 1, 2, 0, 2, 0],
		M: [1, 2, 1],
		N: [1, 2, 0],
		O: [1, 2, 1, 2, 1],
		P: [0, 2, 1, 2, 1, 2, 0],
		Q: [1, 2, 1, 2, 0, 2, 1],
		R: [0, 2, 1, 2, 0],
		S: [0, 2, 0, 2, 0],
		T: [1],
		U: [0, 2, 0, 2, 1],
		V: [0, 2, 0, 2, 0, 2, 1],
		W: [0, 2, 1, 2, 1],
		X: [1, 2, 0, 2, 0, 2, 1],
		Y: [1, 2, 0, 2, 1, 2, 1],
		Z: [1, 2, 1, 2, 0, 2, 0],
		0: [1, 2, 1, 2, 1, 2, 1, 2, 1],
		1: [0, 2, 1, 2, 1, 2, 1, 2, 1],
		2: [0, 2, 0, 2, 1, 2, 1, 2, 1],
		3: [0, 2, 0, 2, 0, 2, 1, 2, 1],
		4: [0, 2, 0, 2, 0, 2, 0, 2, 1],
		5: [0, 2, 0, 2, 0, 2, 0, 2, 0],
		6: [1, 2, 0, 2, 0, 2, 0, 2, 0],
		7: [1, 2, 1, 2, 0, 2, 0, 2, 0],
		8: [1, 2, 1, 2, 1, 2, 0, 2, 0],
		9: [1, 2, 1, 2, 1, 2, 1, 2, 0]
	}
}
const NUM_TYPE = {
	LONG: 'long',
	SHORT: 'short'
}
export const DEFAULT_RATIO = Object.freeze({dot: 1, dash: 3, gap: 1, word: 3, suite: 5, leaf: 7})

// Calibration is a 400-character page, cycling the selected alphabet in four-code groups.
export const CALIBRATION_TEXT = Object.fromEntries(['short', 'long', 'letter', 'mix'].map(type => {
	const alphabet = type === 'letter' ? 'ABCDEFGHIJKLMNOPQRSTUVWXYZ' : type === 'mix' ? 'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789' : '0123456789'
	let text = ''
	for (let i = 0; i < 400; i++) text += alphabet[i % alphabet.length] + (i % 4 === 3 ? ' ' : '')
	return [type, text]
}))
const calibrationCounts = Object.fromEntries(Object.entries(CALIBRATION_TEXT).map(([type, text]) => {
	const counts = [0, 0, 0, 300, 100, 0]
	for (const char of text) {
		if (char === ' ') continue
		for (const symbol of forwardTable[type][char]) counts[symbol]++
	}
	return [type, counts]
}))

export function calculateTiming({rate, unit = 'characters', type = 'short', lowRate = false, ratio = DEFAULT_RATIO}) {
	rate = Number(rate)
	if (!Number.isFinite(rate) || rate <= 0) throw new Error('播报速度必须大于零')
	const resolved = {...DEFAULT_RATIO, ...ratio}
	if (Object.values(resolved).some(value => !Number.isFinite(Number(value)) || Number(value) <= 0)) throw new Error('音频比例必须为正数')
	for (const key of Object.keys(resolved)) resolved[key] = Number(resolved[key])
	if (unit === 'wpm' && !lowRate) return {criterion: 1200 / rate, ratio: resolved}
	if (!['characters', 'groups', 'wpm'].includes(unit) || !calibrationCounts[type]) throw new Error('未知播报单位或报文类型')
	const charactersPerMinute = unit === 'groups' ? rate * 4 : rate
	if (lowRate && charactersPerMinute > 35) throw new Error('低速模式平均速度不得超过35字符/分')
	const counts = calibrationCounts[type]
	const keys = ['dot', 'dash', 'gap', 'word', 'suite', 'leaf']
	const units = counts.reduce((sum, count, i) => sum + count * resolved[keys[i]], 0)
	const criterion = 400 * 60000 / ((lowRate ? 35 : charactersPerMinute) * units)
	if (lowRate) {
		const symbolUnits = counts.slice(0, 3).reduce((sum, count, i) => sum + count * resolved[keys[i]], 0)
		const stretch = (400 * 60000 / charactersPerMinute / criterion - symbolUnits) / (units - symbolUnits)
		for (const key of ['word', 'suite', 'leaf']) resolved[key] *= stretch
	}
	return {criterion, ratio: resolved}
}

export function receiveTiming(data, rate = data.rate, unit = 'characters') {
	return calculateTiming({rate, unit, type: Number(data.type) === 0 ? 'letter' : Number(data.type) === 2 ? 'mix' : Number(data.codeShort) === 1 ? 'short' : 'long', lowRate: Number(data.isLowRate) === 1, ratio: {...DEFAULT_RATIO, dash: data.ratio ?? 3}})
}

export default class MorseVoice extends EventEmitter {
	constructor(obj = {}) {
		super()
		this.criterion = obj.criterion ?? 83
		this.volume = obj.volume ?? 1
		this.frequency = obj.frequency ?? 1000
		this.ratio = {...DEFAULT_RATIO, ...obj.ratio}
		this.model = true
		this.audioContext = null
		this.oscillatorNode = null
		this.type = 'none'
		this.initializing = null
		this.generation = 0
	}

	async init() {
		// Electron may request resume before a user gesture; another click must retry it.
		if (this.initializing && this.audioContext?.state === 'suspended') {
			this.audioContext.resume().catch(error => this.emit('message', {type: 'failure', message: error.message}))
		}
		if (this.initializing) return this.initializing
		const generation = this.generation
		this.initializing = (async () => {
			try {
				const Context = globalThis.AudioContext || globalThis.webkitAudioContext
				if (!Context || !globalThis.AudioWorkletNode) throw new Error('当前环境不支持AudioWorklet，请使用支持音频的安全上下文')
				if (!this.audioContext || this.audioContext.state === 'closed') this.audioContext = new Context()
				const context = this.audioContext
				if (!context.audioWorklet) throw new Error('音频工作线程不可用，请使用HTTPS或本机环境')
				if (context.state !== 'running') await context.resume()
				if (context.state !== 'running') throw new Error('请点击启用或恢复音频')
				if (!this.oscillatorNode) {
					await context.audioWorklet.addModule(new URL('processor.js', document.baseURI).href)
					if (generation !== this.generation) return false
					this.oscillatorNode = new AudioWorkletNode(context, 'sine-processor')
					this.oscillatorNode.onprocessorerror = () => {
						this.destroy()
						this.type = 'failure'
						this.emit('message', {type: 'failure', message: '音频工作线程已停止，请点击重新启用音频'})
					}
					this.oscillatorNode.connect(context.destination)
					this.oscillatorNode.port.onmessage = ({data}) => {
						if (data.type === 'pause') this.type = data.status
						if (data.type === 'playing' && data.status === 'finish') this.type = 'ready'
						if (data.type === 'failure') this.type = 'failure'
						this.emit('message', data)
					}
					context.onstatechange = () => {
						if (context.state !== 'running') this.emit('message', {type: 'suspended', message: '音频已暂停，请点击恢复音频'})
					}
				}
				if (context.state !== 'running') throw new Error('音频已暂停，请点击恢复音频')
				this.pushState()
				if (!['playing', 'pause'].includes(this.type)) this.type = 'ready'
				this.emit('message', {type: 'ready', status: 'initialized'})
				return true
			} catch (error) {
				this.type = 'failure'
				this.emit('message', {type: 'failure', message: error.message})
				return false
			}
		})()
		try { return await this.initializing } finally { this.initializing = null }
	}

	pushState() {
		this.oscillatorNode?.port.postMessage({type: 'state', data: {criterion: this.criterion, ratio: this.ratio, frequency: this.frequency, volume: this.volume, model: this.model}})
	}

	configure(state) {
		for (const key of ['criterion', 'ratio', 'frequency', 'volume', 'model']) {
			if (state[key] !== undefined) this[key] = key === 'ratio' ? {...DEFAULT_RATIO, ...state[key]} : state[key]
		}
		this.pushState()
	}

	isReady() { return !!this.oscillatorNode && this.audioContext?.state === 'running' && this.type !== 'failure' }

	checkReady() {
		if (this.isReady()) return true
		this.emit('message', {type: 'failure', message: '音频尚未就绪，请点击启用或恢复音频后重试'})
		return false
	}

	changeModel(data) { this.updateParam('model', data) }
	changePressed(data) {
		if (data && !this.checkReady()) return false
		this.oscillatorNode?.port.postMessage({type: 'pressed', data})
		return true
	}
	play(data) {
		if (!this.checkReady()) return false
		this.oscillatorNode.port.postMessage({type: 'start', morseCode: this.convert(data).result})
		this.type = 'playing'
		return true
	}
	addCode(data) {
		if (!this.checkReady()) return false
		this.oscillatorNode.port.postMessage({type: 'addCode', morseCode: this.convert(data).result})
		this.type = 'playing'
		return true
	}
	pause() {
		if (this.type === 'playing') {
			this.oscillatorNode?.port.postMessage({type: 'pause', data: true})
			this.type = 'pause'
		}
	}
	continue() {
		if (!this.checkReady()) return false
		this.oscillatorNode.port.postMessage({type: 'pause', data: false})
		this.type = 'playing'
		return true
	}
	stop() {
		this.oscillatorNode?.port.postMessage({type: 'stop'})
		if (this.isReady()) this.type = 'ready'
	}
	clear() { this.stop() }
	destroy() {
		this.generation++
		this.oscillatorNode?.disconnect()
		if (this.oscillatorNode) this.oscillatorNode.port.onmessage = null
		this.oscillatorNode = null
		if (this.audioContext) {
			this.audioContext.onstatechange = null
			this.audioContext.close().catch(error => this.emit('message', {type: 'failure', message: error.message}))
		}
		this.audioContext = null
		this.type = 'none'
	}
	updateParam(type, value) { this.configure({[type]: value}) }
	changeVolume(value) { this.updateParam('volume', Number(value)) }
	changeFrequency(value) { this.updateParam('frequency', Number(value)) }
	changeCriterion(value) { this.updateParam('criterion', Number(value)) }
	changeRatio(value) { this.updateParam('ratio', value) }

	convert(obj) {
		const {numType = 'long', data, sourceOffset = 0} = obj
		const list = [...data]
		const result = []
		let resultBase = []

		list.forEach((char, index) => {
			const trimmedChar = String(char).trim()
			const nextChar = list[index + 1]
			if (trimmedChar === '') {
				if (list[index - 1] !== '/') {
					const gap = char === '\n' ? 5 : 4
					result.push({key: ' ', value: [gap], sourceIndex: sourceOffset + index})
					resultBase.push({key: ' ', value: [gap]})
				}
				return
			}
			if (trimmedChar === '/') {
				result.push({key: '/', value: [0, 2, 0, 3, 0, 2, 0, 3, 0, 2, 0, 5], sourceIndex: sourceOffset + index})
				resultBase.push({key: '/', value: [0, 2, 0, 3, 0, 2, 0, 3, 0, 2, 0, 5]})
			} else {
				const isLetter = isNaN(trimmedChar)
				const table = isLetter ? forwardTable.letter : forwardTable[numType]
				const morseCode = table[trimmedChar.toUpperCase()] || []
				if (morseCode.length > 0) {
					const value =
						nextChar === undefined || String(nextChar).trim() !== ''
							? [...morseCode, 3]
							: morseCode
					result.push({key: trimmedChar, value, sourceIndex: sourceOffset + index})
					resultBase.push({
						key: trimmedChar,
						value: value.filter((num) => num === 0 || num === 1)
					})
				}
			}
		})

		return {result, resultBase}
	}
}
export {forwardTable, NUM_TYPE}
