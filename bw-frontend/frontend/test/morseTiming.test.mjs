import assert from 'node:assert/strict'
import test from 'node:test'
import {CALIBRATION_TEXT, DEFAULT_RATIO, calculateTiming, forwardTable} from '../src/common/utils/voice/MorseVoiceHighPerformance.js'

const SEGMENTS = ['dot', 'dash', 'gap', 'word', 'suite', 'leaf']

// 按校准页的真实结构统计各段出现次数：400 字符、每 4 字符一组、每组后跟一个组间隔。
const segmentCounts = type => {
  const counts = [0, 0, 0, 0, 0, 0]
  for (const group of CALIBRATION_TEXT[type].trim().split(' ')) {
    for (const char of group) for (const symbol of forwardTable[type][char]) counts[symbol]++
    counts[3] += group.length - 1
    counts[4] += 1
  }
  return counts
}

// 规则：某段的毫秒时长 = 基准时长 × 该段配比；整页时长即各段时长之和。
const segmentMilliseconds = ({criterion, ratio}, type) =>
  segmentCounts(type).map((count, index) => count * ratio[SEGMENTS[index]] * criterion)

test('组/分与字符/分是同一码速的两种单位，1 组 = 4 字符', () => {
  const byGroups = calculateTiming({rate: 30, unit: 'groups', type: 'short'})
  const byCharacters = calculateTiming({rate: 120, unit: 'characters', type: 'short'})
  assert.equal(byGroups.criterion, byCharacters.criterion, '30 组/分与 120 字符/分必须得到同一基准时长')
  assert.deepEqual(byGroups.ratio, byCharacters.ratio, '单位换算不得改变点划配比')
})

test('400 字符校准页的各段之和等于按码速应耗的总时长，且不随点划配比变化', () => {
  const charactersPerMinute = 100
  const expected = 400 / charactersPerMinute * 60000
  for (const ratio of [DEFAULT_RATIO, {...DEFAULT_RATIO, dash: 4}]) {
    const timing = calculateTiming({rate: charactersPerMinute, unit: 'characters', type: 'short', ratio})
    const total = segmentMilliseconds(timing, 'short').reduce((sum, value) => sum + value, 0)
    assert.ok(Math.abs(total - expected) < 1e-6, `点划比 1:${ratio.dash} 时整页时长为 ${total}ms，应为 ${expected}ms`)
  }
})

test('低速模式把点划固定在 35 字符/分，只拉长间隔来满足更慢的设定速度', () => {
  const timing = calculateTiming({rate: 20, unit: 'characters', type: 'short', lowRate: true})
  const capped = calculateTiming({rate: 35, unit: 'characters', type: 'short'})
  assert.equal(timing.criterion, capped.criterion, '低速模式下点划本身仍按 35 字符/分发送')
  assert.deepEqual(
    [timing.ratio.dot, timing.ratio.dash, timing.ratio.gap],
    [DEFAULT_RATIO.dot, DEFAULT_RATIO.dash, DEFAULT_RATIO.gap],
    '低速模式只拉长间隔，不得改变点、划、点划间隔本身的配比'
  )
  const expected = 400 / 20 * 60000
  const total = segmentMilliseconds(timing, 'short').reduce((sum, value) => sum + value, 0)
  assert.ok(Math.abs(total - expected) < 1e-6, `低速整页时长为 ${total}ms，应为设定的 ${expected}ms`)
  assert.throws(
    () => calculateTiming({rate: 36, unit: 'characters', type: 'short', lowRate: true}),
    /低速模式平均速度不得超过35字符\/分/,
    '超过 35 字符/分不属于低速模式，不能按低速拉长间隔'
  )
})
