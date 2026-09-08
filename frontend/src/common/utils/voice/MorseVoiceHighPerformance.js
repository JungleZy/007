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
const keyCodes = {
	A: [0, 1],
	B: [1, 0, 0, 0],
	C: [1, 0, 1, 0],
	D: [1, 0, 0],
	E: [0],
	F: [0, 0, 1, 0],
	G: [1, 1, 0],
	H: [0, 0, 0, 0],
	I: [0, 0],
	J: [0, 1, 1, 1],
	K: [1, 0, 1],
	L: [0, 1, 0, 0],
	M: [1, 1],
	N: [1, 0],
	O: [1, 1, 1],
	P: [0, 1, 1, 0],
	Q: [1, 1, 0, 1],
	R: [0, 1, 0],
	S: [0, 0, 0],
	T: [1],
	U: [0, 0, 1],
	V: [0, 0, 0, 1],
	W: [0, 1, 1],
	X: [1, 0, 0, 1],
	Y: [1, 0, 1, 1],
	Z: [1, 1, 0, 0],
	0: [1, 1, 1, 1, 1],
	1: [0, 1, 1, 1, 1],
	2: [0, 0, 1, 1, 1],
	3: [0, 0, 0, 1, 1],
	4: [0, 0, 0, 0, 1],
	5: [0, 0, 0, 0, 0],
	6: [1, 0, 0, 0, 0],
	7: [1, 1, 0, 0, 0],
	8: [1, 1, 1, 0, 0],
	9: [1, 1, 1, 1, 0],
	'?': [0, 0, 1, 1, 0, 0],
	'/': [1, 0, 0, 1, 0],
	'<': [1, 0, 1, 1, 0, 1],
	'>': [1, 0, 1, 1, 0, 1],
	'。': [0, 0, 2, 0, 0, 2, 0, 0],
	'.': [0, 1, 0, 1, 0, 1]
}
const NUM_TYPE = {
	LONG: 'long',
	SHORT: 'short'
}
/**
 * MorseVoice 类用于将文本转换为摩尔斯电码并通过音频播放。
 *
 * @class
 * @param {Object} [obj] - 初始化参数对象。
 * @param {number} [obj.criterion=83] - 基准点时长，单位为毫秒。
 * @param {number} [obj.volume=1.0] - 初始音量。
 * @param {number} [obj.frequency=1000] - 初始频率。
 * @param {Object} [obj.ratio] - 点划比例对象。
 * @param {number} [obj.ratio.dot=1] - 点长度比例。
 * @param {number} [obj.ratio.dash=3] - 划长度比例。
 * @param {number} [obj.ratio.gap=1] - 点划间隔比例。
 * @param {number} [obj.ratio.word=3] - 词间隔比例。
 * @param {number} [obj.ratio.suite=5] - 组间隔比例。
 * @param {number} [obj.ratio.leaf=7] - 电报纸间隔比例。
 *
 * @property {number} criterion - 基准点时长，单位为毫秒。
 * @property {number} volume - 当前音量。
 * @property {number} frequency - 当前频率。
 * @property {Object} ratio - 点划比例对象。
 * @property {AudioContext|null} audioContext - 音频上下文。
 * @property {AudioWorkletNode|null} oscillatorNode - 音频工作节点。
 * @property {string} type - 当前状态，可能值为 'none', 'failure', 'ready', 'playing', 'pause'。
 *
 * @example
 * const morseVoice = new MorseVoice();
 * morseVoice.init((params) => {
 *   if (params instanceof Error) {
 *     console.error('初始化失败:', params);
 *   } else {
 *     console.log('初始化成功:', params);
 *   }
 * });
 */
export default class MorseVoice extends EventEmitter {
	constructor(obj) {
		super()
		obj = obj || {
			criterion: 83,
			volume: 1.0,
			frequency: 1000,
			ratio: {
				dot: 1, // 比例 点长度
				dash: 3, // 比例 划长度
				gap: 1, // 比例 点划间隔
				word: 3, // 比例 词间隔
				suite: 5, // 比例 组间隔
				leaf: 7 // 比例 电报纸间隔
			}
		}
		this.criterion = obj.criterion ?? 83
		this.volume = obj.volume ?? 1.0
		this.frequency = obj.frequency ?? 1000
		this.ratio = obj.ratio ?? {
			dot: 1, // 比例 点长度
			dash: 3, // 比例 划长度
			gap: 1, // 比例 点划间隔
			word: 3, // 比例 词间隔
			suite: 5, // 比例 组间隔
			leaf: 7 // 比例 电报纸间隔
		}
		this.audioContext = null
		this.oscillatorNode = null
		// none:未初始化，failure:初始化失败，ready:初始化完成,等待播放中，playing:播放中，pause:暂停中
		this.type = 'none'
	}

	/**
	 * 初始化 MorseVoice 音频上下文并设置音频工作节点。
	 *
	 * @property {number} frequency - 振荡器的初始频率。
	 * @property {number} volume - 振荡器的初始音量。
	 * @property {number} criterion - 基准点时长，毫秒。
	 * @property {number} ratio - 点划比例。
	 *
	 * @example
	 * const morseVoice = new MorseVoice();
	 * morseVoice.init();
	 */
	init() {
		try {
			if (this.type === 'none' || this.type === 'failure') {
				this.audioContext = new AudioContext()
				this.audioContext.audioWorklet.addModule('processor.js').then(() => {
					this.oscillatorNode = new AudioWorkletNode(
						this.audioContext,
						'sine-processor',
						{processorOptions:{bufferSize:64}}
					)
					this.oscillatorNode.connect(this.audioContext.destination)
					this.oscillatorNode.port.onmessage = (event) => {
						const data = event.data
						// console.log("data",data);
						if (data.type === 'pause') {
							this.type = data.status
						} else if (data.type === 'playing' && data.status === 'finish') {
							this.type = 'ready'

							this.emit('message', data)
							this.emit('message', {
								type: this.type
							})
						} else {
							this.emit('message', data)
						}
					}
					// 设置初始参数
					this.updateParam('frequency', this.frequency) // 频率
					this.updateParam('volume', this.volume) // 音量
					this.updateParam('sampleRate', this.audioContext.sampleRate)
					this.type = 'ready'
					this.emit('message', {
						type: this.type,
						status: 'initialized',
						volume: this.volume,
						frequency: this.frequency,
						criterion: this.criterion,
						ratio: this.ratio
					})
				})
			}
		} catch (e) {
			this.type = 'failure'
			this.emit('message', {type: this.type, data: e})
		}
	}

	/**
	 *  模式开关，开启为收报模式，关闭为发报模式
	 *
	 * @param {string} data -。
	 */
	changeModel(data) {
		this.oscillatorNode?.port.postMessage({
			type: 'model',
			data: data
		})
	}

	/**
	 *  手键音频 true响起 false 停止
	 *
	 * @param {string} data -。
	 */
	changePressed(data) {
		this.oscillatorNode.port.postMessage({
			type: 'pressed',
			data: data
		})
	}

	/**
	 * 播放给定的数据作为莫尔斯电码。
	 *
	 * @param {string} data - 这些数据被转换成摩尔斯电码并播放。
	 */
	play(data) {
		console.time()
		const morseCode = this.convert(data)
		console.timeEnd()
		console.log("morseCode");
		console.log(morseCode);
		this.oscillatorNode.port.postMessage({
			type: 'start',
			morseCode: morseCode.result
		})
		this.type = 'playing'
	}
  addCode(data){
    const morseCode = this.convert(data)
    console.log("morseCode");
    console.log(morseCode);
    this.oscillatorNode.port.postMessage({
      type: 'addCode',
      morseCode:morseCode.result
    })
  }

	/**
	 * 暂停播放莫尔斯电码。
	 * 如果当前状态为播放中，则发送暂停消息。
	 */
	pause() {
		if (this.type === 'playing' && this.oscillatorNode) {
			this.oscillatorNode.port.postMessage({type: 'pause', data: true})
		}
	}

	/**
	 * 继续播放声音的方法。
	 * 如果当前类型为 'pause' 且存在 oscillatorNode，则向 oscillatorNode 的端口发送消息以取消暂停。
	 *
	 * @method
	 */
	continue() {
		if (this.type === 'pause' && this.oscillatorNode) {
			this.oscillatorNode.port.postMessage({type: 'pause', data: false})
		}
	}

	/**
	 * 停止播放莫尔斯电码。
	 * 如果当前状态为播放中或暂停中，则发送停止消息。
	 */
	stop() {
		if (
			(this.type === 'playing' || this.type === 'pause') &&
			this.oscillatorNode
		) {
			this.oscillatorNode.port.postMessage({type: 'stop'})
		}
	}

	/**
	 * 清除音频上下文和振荡器节点。
	 * 如果存在振荡器节点，则断开连接并将其设置为 null。
	 * 如果存在音频上下文，则关闭并将其设置为 null。
	 * 调用回调函数并传递清除类型。
	 */
	clear() {
		if (this.oscillatorNode) {
			this.oscillatorNode.port.onmessage = null // 清除事件监听
			this.oscillatorNode.disconnect()
			this.oscillatorNode = null
		}
		if (this.audioContext) {
			this.audioContext.close().then(() => {
				this.audioContext = null
			})
		}
		this.type = 'none'
		this.emit('message', {
			type: 'clear'
		})
	}

	updateParam(type, value) {
		this[type] = value
		if (this.oscillatorNode) {
			this.oscillatorNode.port.postMessage({type, data: value})
		}
	}

	/**
	 * 改变音量大小
	 * @param {*} volume 音量
	 */
	changeVolume(volume) {
		this.updateParam('volume', parseFloat(volume))
	}

	/**
	 * 改变音频频率
	 * @param {*} frequency 频率
	 */
	changeFrequency(frequency) {
		this.updateParam('frequency', parseFloat(frequency))
	}

	/**
	 * 改变基准时长（点时长）
	 * 单位毫秒
	 * @param {*} criterion 点时长
	 */
	changeCriterion(criterion) {
		this.updateParam('criterion', parseInt(criterion, 10))
	}

	/**
	 * 改变点、划、词、组比例
	 * @param {*} ratio
	 */
	changeRatio(ratio) {
		this.updateParam('ratio', ratio)
	}

	/**
	 * 将输入的文本转换为摩尔斯电码。
	 *
	 * @param {Object} obj - 包含要转换的数据和数字类型的对象。
	 * @param {string} obj.numType - 数字类型，可以是 'long' 或 'short'。
	 * @param {string} obj.data - 要转换的文本数据。
	 * @returns {Object} 包含摩尔斯电码结果和基础结果的对象。
	 */
	convert(obj) {
		const {numType = 'long', data} = obj
		const list = [...data]
		const result = []
		let resultBase = []

		list.forEach((char, index) => {
			const trimmedChar = char.trim()
			const nextChar = list[index + 1]
			if (trimmedChar === '') {
				result.push({key: ' ', value: [4]})
				resultBase.push({key: ' ', value: [4]})
			}
			if (trimmedChar === '/') {
				result.push({key: '/', value: [0, 2, 0, 3, 0, 2, 0, 3, 0, 2, 0, 3]})
				resultBase.push({key: '/', value: [0, 2, 0, 3, 0, 2, 0, 3, 0, 2, 0, 3]})
			} else {
				const isLetter = isNaN(trimmedChar)
				const table = isLetter ? forwardTable.letter : forwardTable[numType]
				const morseCode = table[trimmedChar.toUpperCase()] || []
				if (morseCode.length > 0) {
					const value =
						nextChar === undefined || nextChar !== ' '
							? [...morseCode, 3]
							: morseCode
					result.push({key: trimmedChar, value})
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
