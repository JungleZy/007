import assert from 'node:assert/strict'
import test from 'node:test'
import {PAT_WAVE_KEEP_WIDTH, patWaveWidth, trimPatWave} from '../src/common/utils/patWave.js'

const width = log => log.reduce((total, code) => total + patWaveWidth(code), 0)
const strokes = (count, dot) => Array.from({length: count}, (_, i) => ({code: i % 2, diff: i % 2 ? dot * 3 : dot, gap: dot}))

test('快速拍发时保留的记录仍能铺满码条，不再固定70个', () => {
	const fast = trimPatWave(strokes(2000, 40))
	assert.ok(fast.length > 70)
	assert.ok(width(fast) >= PAT_WAVE_KEEP_WIDTH)
	assert.ok(width(fast.slice(1)) < PAT_WAVE_KEEP_WIDTH, '只多保留恰好铺满所需的记录')
})

test('慢速拍发保留条数相应减少，最新记录始终在末尾', () => {
	const log = strokes(500, 200)
	const last = log.at(-1)
	trimPatWave(log)
	assert.ok(log.length < 500)
	assert.equal(log.at(-1), last)
	assert.ok(width(log) >= PAT_WAVE_KEEP_WIDTH)
})

test('记录不足保留宽度时不删除', () => {
	const log = strokes(10, 60)
	trimPatWave(log)
	assert.equal(log.length, 10)
})
