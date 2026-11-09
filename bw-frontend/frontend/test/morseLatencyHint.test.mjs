import assert from 'node:assert/strict'
import test from 'node:test'
import MorseVoice, {LATENCY_HINT} from '../src/common/utils/voice/MorseVoiceHighPerformance.js'

function installFakes() {
	const contexts = []
	globalThis.document = {baseURI: 'file:///app/index.html'}
	globalThis.AudioContext = class {
		constructor(options) {
			this.options = options
			this.state = 'running'
			this.destination = {}
			this.audioWorklet = {addModule: () => Promise.resolve()}
			contexts.push(this)
		}
		resume() { return Promise.resolve() }
		close() { this.state = 'closed'; return Promise.resolve() }
	}
	globalThis.AudioWorkletNode = class {
		constructor() { this.port = {postMessage() {}} }
		connect() {}
		disconnect() {}
	}
	return contexts
}

test('收报模式用playback缓冲，手键侧音模式用最低延迟', async () => {
	const contexts = installFakes()
	const voice = new MorseVoice()
	voice.configure({model: true})
	assert.equal(await voice.init(), true)
	assert.equal(contexts[0].options.latencyHint, LATENCY_HINT.playback)

	voice.configure({model: false})
	assert.equal(contexts[0].state, 'closed', '切到手键须销毁大缓冲context')
	assert.equal(voice.isReady(), false)
	assert.equal(await voice.init(), true)
	assert.equal(contexts[1].options.latencyHint, LATENCY_HINT.sidetone)
	assert.equal(voice.isReady(), true)
})

test('同一模式下调整码速、音量等不重建context', async () => {
	const contexts = installFakes()
	const voice = new MorseVoice()
	await voice.init()
	voice.configure({criterion: 60, volume: 0.5, frequency: 800, model: true})
	assert.equal(contexts.length, 1)
	assert.equal(voice.isReady(), true)
})

test('初始化途中被模式切换取代时不向用户报失败', async () => {
	installFakes()
	const voice = new MorseVoice()
	const messages = []
	voice.on('message', data => messages.push(data))
	const pending = voice.init()
	voice.configure({model: false})
	assert.equal(await pending, false)
	assert.deepEqual(messages.filter(data => data.type === 'failure'), [])
	assert.equal(await voice.init(), true)
})
