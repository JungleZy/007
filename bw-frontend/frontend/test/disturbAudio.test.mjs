import assert from 'node:assert/strict'
import test from 'node:test'
import {releaseDisturbAudio, startDisturbAudio, stopDisturbAudio} from '../src/common/utils/disturbAudio.js'

function installFakes() {
	const contexts = [], requests = []
	globalThis.AudioContext = class {
		constructor() { this.closed = false; this.started = []; this.destination = {}; contexts.push(this) }
		decodeAudioData() { return Promise.resolve({}) }
		createBufferSource() {
			const ctx = this
			return {connect() {}, start() { ctx.started.push(this) }, stop() { this.stopped = true }}
		}
		createGain() { return {gain: {value: 0}, connect() {}} }
		close() { this.closed = true; return Promise.resolve() }
	}
	globalThis.XMLHttpRequest = class {
		constructor() { requests.push(this) }
		open() {}
		send() {}
		abort() { this.aborted = true }
	}
	return {contexts, requests}
}

const flush = () => new Promise(resolve => setImmediate(resolve))

test('停止干扰音会关闭AudioContext，反复开关不堆积输出流', async () => {
	const {contexts, requests} = installFakes()
	const item = {}
	for (let i = 0; i < 5; i++) {
		startDisturbAudio(item, {url: '/noise.mp3', volume: 0.5})
		requests.at(-1).onload()
		await flush()
		stopDisturbAudio(item)
	}
	assert.equal(contexts.length, 5)
	assert.ok(contexts.every(ctx => ctx.closed))
	assert.ok(contexts.every(ctx => ctx.started.length === 1 && ctx.started[0].stopped))
	assert.equal(item.ctx, null)
})

test('重复开始会先释放上一路，加载中途停止不会再出声', async () => {
	const {contexts, requests} = installFakes()
	const item = {}
	startDisturbAudio(item, {url: '/a.mp3', volume: 1})
	startDisturbAudio(item, {url: '/a.mp3', volume: 1})
	assert.ok(contexts[0].closed && requests[0].aborted)
	stopDisturbAudio(item)
	requests[1].onload()
	await flush()
	assert.equal(contexts[1].started.length, 0)
	assert.ok(contexts[1].closed)
})

test('预加载页面卸载时释放每一路context', () => {
	const {contexts} = installFakes()
	const list = [{ctx: new AudioContext()}, {ctx: new AudioContext()}, {}]
	releaseDisturbAudio(list)
	assert.ok(contexts.every(ctx => ctx.closed))
})
