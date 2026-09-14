import assert from 'node:assert/strict'
import test from 'node:test'
import WebSerial from '../../../bw-frontend/frontend/src/common/utils/WebSerial.js'
import {ELECTRON_FN, ELECTRON_LEFT, ELECTRON_RIGHT, ELECTRON_TEXT, codeOnKey, elementsOf} from '../src/tables.mjs'
import {chordOf, electronKeyPlan, electronKeyTimeline} from '../src/electronkey.mjs'
import {toByteStream} from '../src/sinks/bytes.mjs'

const LETTERS = [...'ABCDEFGHIJKLMNOPQRSTUVWXYZ']
const strokes = codes => codes.map(code => ELECTRON_RIGHT[code] ?? ELECTRON_LEFT[code]).join('')

test('26 个字母都能拍出，且键位拼出的点划串经前端 codeOnKey 还原回原字母', () => {
  for (const letter of LETTERS) {
    const codes = chordOf(elementsOf('letter', letter))
    assert.equal(strokes(codes), elementsOf('letter', letter), `${letter} 的键位拼不出它的点划串`)
    assert.equal(codeOnKey[strokes(codes)], letter, `${letter} 经 codeOnKey 还原失败`)
  }
})

test('键序恒为"右手起头（>=30）+ 可选左手收尾（<30）"，这是客户端成字的唯一顺序', () => {
  // 客户端逻辑：右手键装缓冲、左手键收尾成字；左手键遇空缓冲直接落 '#'（handKeyTrain.js:320-347）。
  for (const letter of LETTERS) {
    const codes = chordOf(elementsOf('letter', letter))
    assert.ok(codes.length === 1 || codes.length === 2, `${letter} 用了 ${codes.length} 笔`)
    assert.ok(codes[0] >= 30, `${letter} 的第一笔 ${codes[0]} 不是右手键`)
    if (codes[1] !== undefined) assert.ok(codes[1] < 30, `${letter} 的第二笔 ${codes[1]} 不是左手键`)
  }
})

test('只有单元素字母才是单笔，其余一律两笔——单笔才需要等 800ms 超时', () => {
  const single = LETTERS.filter(letter => chordOf(elementsOf('letter', letter)).length === 1)
  assert.deepEqual(single.sort(), ['E', 'T'], '除 E/T 外不应有字母落到单笔提交')
})

test('数码必须走 F1 直出：短码数码的点划串在 codeOnKey 里会还原成字母，F2 装配拿不回数码', () => {
  for (const digit of [...'0123456789']) {
    assert.ok(ELECTRON_TEXT[digit] !== undefined, `${digit} 缺少 F1 直出键位`)
    assert.notEqual(codeOnKey[elementsOf('short', digit)], digit, `${digit} 竟能被 F2 装配还原，前提变了`)
  }
  const timeline = electronKeyTimeline({text: '1234 5678', alphabet: 'short', plan: electronKeyPlan({rate: 20})})
  const modeKeys = timeline.events.filter(event => [ELECTRON_FN.F1, ELECTRON_FN.F2].includes(event.code))
  assert.deepEqual(modeKeys.map(event => event.code), [ELECTRON_FN.F1], '数码页不应切到 F2')
  for (const char of timeline.chars.filter(item => item.kind === 'char')) {
    assert.deepEqual(char.codes, [ELECTRON_TEXT[char.value]], `${char.value} 未走 F1 直出键位`)
  }
})

test('模式键只在真正换模式时发；混合报里字母与数码交替也不重复切换', () => {
  const timeline = electronKeyTimeline({text: 'AB12 CD34', alphabet: 'mix', plan: electronKeyPlan({rate: 15})})
  const modeKeys = timeline.events.filter(event => [ELECTRON_FN.F1, ELECTRON_FN.F2].includes(event.code)).map(event => event.code)
  // 开始键要求 F1（handKeyTrain.js:236），随后每次字母/数码切换各一次，不出现连续相同模式键。
  assert.equal(modeKeys[0], ELECTRON_FN.F1, '开始键必须在 F1 下发')
  for (let index = 1; index < modeKeys.length; index++) {
    assert.notEqual(modeKeys[index], modeKeys[index - 1], '出现了多余的重复模式切换')
  }
})

test('收尾键序与客户端提交入口对齐：page 用 F1+句号，end 用 F3+回车', () => {
  const plan = electronKeyPlan({rate: 20})
  const page = electronKeyTimeline({text: 'AE', plan, tail: 'page'})
  const end = electronKeyTimeline({text: 'AE', plan, tail: 'end'})
  const codes = timeline => timeline.events.filter(event => event.kind === 'code').map(event => event.code)
  assert.deepEqual(codes(page).slice(-2), [ELECTRON_FN.F1, ELECTRON_FN.period])
  assert.deepEqual(codes(end).slice(-2), [ELECTRON_FN.F3, ELECTRON_FN.enter])
})

test('单字节码经真实 WebSerial 解析后逐个成帧，同 read 里的连续相同码不被合并', () => {
  const timeline = electronKeyTimeline({text: 'EE', plan: electronKeyPlan({rate: 20}), preamble: false, tail: null})
  const expected = timeline.events.filter(event => event.kind === 'code').map(event => event.code)
  const serial = new WebSerial(9600)
  const frames = []
  // 全部字节并进一次 read，复现粘包
  const merged = Uint8Array.from(toByteStream(timeline).flatMap(chunk => [...chunk.bytes]))
  serial.handleData(merged, event => frames.push(event.data))
  assert.deepEqual(frames.map(frame => frame.d[0]), expected)
  assert.equal(expected.filter(code => code === 31).length, 2, 'EE 应拍出两次同一个右手键')
})

test('码率过高以至于一个字装不下两笔时直接拒绝，而不是产出挤在一起的时间轴', () => {
  assert.throws(() => electronKeyPlan({rate: 400}), /装不下两笔/)
})
