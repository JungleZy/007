import assert from 'node:assert/strict'
import test from 'node:test'
import useMorse from '../src/common/mixin/useMorse.js'
import {matchHandKeyControl} from '../src/common/utils/handKeyControlMatch.js'

// 点80ms、划240ms，点划分界 80×1.5=120ms，容错带 84~156ms
const THRESHOLD = 120
const {codeKey} = useMorse()
const isCharacterIn = alphabet => value => (codeKey[alphabet][value] ?? '#') !== '#'
const signalOf = durations => durations.map(diff => ({code: diff > THRESHOLD ? 1 : 0, diff}))
const token = (durations, alphabet = 'short', groupEnd = false) => {
	const signal = signalOf(durations)
	const code = signal.map(item => item.code).join('')
	return {code, signal, groupEnd, invalid: !isCharacterIn(alphabet)(code)}
}
const match = (durations, {candidates = [], alphabet = 'short'} = {}) => {
	const {code, signal} = token(durations, alphabet)
	return matchHandKeyControl({code, signal, candidates, isCharacter: isCharacterIn(alphabet), threshold: THRESHOLD})
}

test('标准拍发仍按原口径识别全部控制符', () => {
	assert.deepEqual(match([80, 80, 240, 240, 80, 80]), {action: 'alter', used: 0})
	assert.deepEqual(match([80, 80, 240, 80, 240, 240]), {action: 'next', used: 0})
	assert.deepEqual(match([80, 80, 80, 80, 80, 80]), {action: 'turn', used: 0})
	assert.deepEqual(match([240, 80, 80, 80, 240]), {action: 'start', used: 0})
	assert.deepEqual(match([80, 240, 80, 240, 80]), {action: 'end', used: 0})
})

test('改错符的划按短了一点，不再识别成#', () => {
	assert.deepEqual(match([80, 80, 240, 110, 80, 80]), {action: 'alter', used: 0})
	assert.deepEqual(match([80, 80, 100, 80, 240, 240]), {action: 'next', used: 0})
})

test('句号某个点按长了一点，不再识别成#', () => {
	assert.deepEqual(match([80, 80, 80, 140, 80, 80]), {action: 'turn', used: 0})
})

test('明显的划不会被当成点：句号里夹一个正常划仍不识别', () => {
	assert.equal(match([80, 80, 80, 240, 80, 80]), null)
})

test('数码报中途停顿被拆成几个无效字时，合并识别改错与句号', () => {
	const prior = token([80, 80, 240, 240])
	assert.equal(prior.invalid, true)
	assert.deepEqual(match([80, 80], {candidates: [prior]}), {action: 'alter', used: 1})
	const dots = [token([80, 80]), token([80, 80])]
	assert.deepEqual(match([80, 80], {candidates: dots}), {action: 'turn', used: 2})
})

test('字母报的I、H是正文，不会被合并成句号', () => {
	const prior = [token([80, 80], 'letter', true), token([80, 80], 'letter', true)]
	assert.equal(match([80, 80], {candidates: [token([80, 80], 'letter'), token([80, 80], 'letter')], alphabet: 'letter'}), null)
	assert.deepEqual(match([80, 80], {candidates: prior, alphabet: 'letter'}), {action: 'turn', used: 2}, '独立成组的三截仍按原口径识别')
	assert.equal(match([80, 80], {candidates: [token([80, 80, 80, 80], 'letter')], alphabet: 'letter'}), null)
})

test('合法字符不并入，无效字之前的有效字截断合并', () => {
	const valid = token([80, 240])
	assert.equal(valid.invalid, false)
	assert.equal(match([80, 80, 240, 240], {candidates: [valid]}), null)
})

test('同时像句号又像改错时视为有歧义，不识别', () => {
	assert.equal(match([80, 80, 130, 130, 90, 80]).action, 'alter', '精确码001100按原口径识别')
	assert.equal(match([80, 80, 110, 150, 80, 80]), null)
})

test('开始符与结束符不放宽', () => {
	assert.equal(match([240, 80, 80, 80, 110]), null)
	assert.equal(match([80, 240, 80, 110, 80]), null)
})

test('整组恰好是三截“..”才按句号处理', async () => {
	const {matchGroupedTurn} = await import('../src/common/utils/handKeyControlMatch.js')
	const dots = groupIndex => ({code: '00', groupIndex})
	assert.equal(matchGroupedTurn([dots(5), dots(5), dots(5)], 3).length, 3)
	assert.equal(matchGroupedTurn([dots(5), dots(5), dots(5)], 4), null, '组里还有别的字')
	assert.equal(matchGroupedTurn([dots(4), dots(5), dots(5)], 3), null, '跨组不算')
	assert.equal(matchGroupedTurn([dots(5), {code: '000', groupIndex: 5}, dots(5)], 3), null)
})
