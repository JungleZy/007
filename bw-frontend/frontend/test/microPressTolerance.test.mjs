import assert from 'node:assert/strict'
import test from 'node:test'
import {createHandKeyDecoder} from '../src/common/utils/handKeyDecoder.js'

// 帧工厂：t=0 手键边沿帧，k=0 按下 / k=1 抬起，d 为 JS 接收时刻
const frame = (k, d) => ({t: 0, k, d, receivedAt: d, timeSource: 'test'})

const collect = () => {
  const emitted = []
  const decoder = createHandKeyDecoder({enabled: () => true, threshold: () => 55.6, lineLimit: () => 166, emit: e => emitted.push(e)})
  return {decoder, emitted}
}

test('微按压（≤10ms）整拍作废：不产生点划、不劈开真实间隔', () => {
  const {decoder, emitted} = collect()
  // 一拍 36.8ms 的点
  decoder.receive(frame(0, 0))
  decoder.receive(frame(1, 36.8))
  // 110.4ms 字间隔中间插入一拍 6ms 微按压（硬件抖动）
  decoder.receive(frame(0, 92))
  decoder.receive(frame(1, 98))
  // 真实的下一拍点
  decoder.receive(frame(0, 147.2))
  decoder.receive(frame(1, 184))

  const marks = emitted.filter(e => e.code === 0 || e.code === 1)
  const gaps = emitted.filter(e => e.code === -1).map(e => e.gapTime[1] - e.gapTime[0])
  assert.equal(marks.length, 2, `微按压不得产生点划事件，实际 ${marks.length} 个`)
  assert.ok(
    gaps.some(g => Math.abs(g - 110.4) < 0.001),
    `真实间隔必须完整出现（还回机制），实际 ${JSON.stringify(gaps)}`
  )
})

test('缺抬起后的重复按下仍在容忍窗口内被忽略（dupDown 语义不受影响）', () => {
  const {decoder, emitted} = collect()
  decoder.receive(frame(0, 0))
  decoder.receive(frame(1, 36.8))
  // 重复按下（无抬起）：应被忽略，用真正那次按下计时
  decoder.receive(frame(0, 48))
  decoder.receive(frame(1, 85.6))
  const marks = emitted.filter(e => e.code === 0 || e.code === 1)
  assert.equal(marks.length, 2, `重复按下不得新增点划，实际 ${marks.length} 个`)
})
