import assert from 'node:assert/strict'
import test from 'node:test'
import WebSerial from '../../bw-frontend/frontend/src/common/utils/WebSerial.js'
import {HAND_CONTROL, forwardElements} from '../src/tables.mjs'
import {handKeyPlan, handKeyTimeline} from '../src/handkey.mjs'
import {applyFaults} from '../src/faults.mjs'
import {toByteStream} from '../src/sinks/bytes.mjs'
import {toFrames} from '../src/sinks/frames.mjs'

/** 把字节流喂给生产代码 WebSerial.handleData，收集它解出的帧。 */
const parseWithWebSerial = chunks => {
  const serial = new WebSerial(9600)
  const frames = []
  for (const chunk of chunks) serial.handleData(chunk.bytes, event => frames.push(event.data))
  return frames
}

test('控制符点划串与前端 forwardTable 同源，不是模拟器自己编的一套', () => {
  // initSymbol（handKeyTrain.js:41-48）与 forwardTable（MorseVoiceHighPerformance.js:33-37）
  // 是仓内两份独立定义，必须一致，否则模拟器发的开始/结束/改错符对不上客户端识别。
  assert.equal(HAND_CONTROL.start, forwardElements('#'), '开始符')
  assert.equal(HAND_CONTROL.end, forwardElements('!'), '结束符')
  assert.equal(HAND_CONTROL.alter, forwardElements('?'), '当前组改错符')
  assert.equal(HAND_CONTROL.next, forwardElements('-'), '前一组改错符')
})

test('开始符必须能通过试机校验：点划间隔各自齐整且划超过点的两倍', () => {
  // 校验规则见 handKeyTrain.js:357-360：三类时长都要落在 ±skew% 内，且划的中位数与最小值都 > 点×2。
  const skew = 50
  for (const jitter of [0, 0.2]) {
    const plan = handKeyPlan({rate: 100, skew, jitter})
    const timeline = handKeyTimeline({text: 'A', plan, jitter, seed: 7})
    const preamble = timeline.chars.find(char => char.value === '开始')
    const pick = wanted => preamble.codes
      .map((code, index) => [code, preamble.durations[index]])
      .filter(([code]) => code === wanted)
      .map(([, value]) => value)
    const dots = pick(0)
    const dashes = pick(1)
    const gaps = preamble.gaps
    const median = values => values.reduce((sum, value) => sum + value, 0) / values.length
    for (const [name, values] of [['点', dots], ['划', dashes], ['间隔', gaps]]) {
      const center = median(values)
      assert.ok(Math.min(...values) >= center * (1 - skew / 100), `${name} 最小值超出 -${skew}% 容差（抖动 ${jitter}）`)
      assert.ok(Math.max(...values) <= center * (1 + skew / 100), `${name} 最大值超出 +${skew}% 容差（抖动 ${jitter}）`)
    }
    assert.ok(median(dashes) > median(dots) * 2, `划中位数未超过点的两倍（抖动 ${jitter}）`)
    assert.ok(Math.min(...dashes) > median(dots) * 2, `最短的划未超过点的两倍（抖动 ${jitter}）`)
  }
})

test('抖动超过评分规则容差时直接拒绝出节拍，而不是产出一份注定试机失败的时间轴', () => {
  assert.throws(() => handKeyPlan({rate: 100, skew: 50, jitter: 0.6}), /抖动/)
})

test('字节流经真实 WebSerial 解析后，帧序列与帧层 sink 一致；分包粘包都能还原', () => {
  const plan = handKeyPlan({rate: 100})
  const timeline = handKeyTimeline({text: 'AB CD', plan, tail: 'end'})
  const expected = toFrames(timeline).map(item => [item.frame.t, item.frame.k])
  assert.ok(expected.length > 20, '样本太小，不足以覆盖边界')
  for (const mode of ['exact', 'split', 'merge', 'random']) {
    const parsed = parseWithWebSerial(toByteStream(timeline, {mode, window: 5, seed: 3}))
    assert.deepEqual(parsed.map(frame => [frame.t, frame.k]), expected, `分包方式 ${mode} 下帧序列不一致`)
  }
})

test('未知字节被跳过但不吞掉后续帧，重复按下与缺抬起仍保留其余边沿', () => {
  const plan = handKeyPlan({rate: 100})
  const clean = handKeyTimeline({text: 'A', plan, preamble: false, tail: null})
  const cleanFrames = parseWithWebSerial(toByteStream(clean))

  const withUnknown = applyFaults(clean, {unknownByte: {at: clean.chars[0].startedAt - 1, byte: 7}})
  const unknownFrames = parseWithWebSerial(toByteStream(withUnknown))
  assert.deepEqual(unknownFrames.map(frame => [frame.t, frame.k]), cleanFrames.map(frame => [frame.t, frame.k]),
    '未知字节应被跳过且不影响其余帧')

  const withDup = applyFaults(clean, {dupDown: {index: 0}})
  assert.equal(parseWithWebSerial(toByteStream(withDup)).length, cleanFrames.length + 1, '重复按下应多出一个按下帧交由客户端去重')

  const withMissing = applyFaults(clean, {missingUp: {index: 0}})
  const missingFrames = parseWithWebSerial(toByteStream(withMissing))
  assert.equal(missingFrames.length, cleanFrames.length - 1)
  assert.equal(missingFrames.filter(frame => frame.k === 1).length, cleanFrames.filter(frame => frame.k === 1).length - 1)
})

test('抖动可复现：同一 seed 给出同一份时间轴，不同 seed 给出不同时间轴', () => {
  const plan = handKeyPlan({rate: 100, jitter: 0.2})
  const build = seed => handKeyTimeline({text: 'ABCD', plan, jitter: 0.2, seed}).events.map(event => event.at)
  assert.deepEqual(build(11), build(11))
  assert.notDeepEqual(build(11), build(12))
})
