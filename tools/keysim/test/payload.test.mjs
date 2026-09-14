import assert from 'node:assert/strict'
import test from 'node:test'
import {electronKeyPlan, electronKeyTimeline} from '../src/electronkey.mjs'
import {handKeyPlan, handKeyTimeline} from '../src/handkey.mjs'
import {electronUploadPayload, fitScale, handUploadPayload} from '../src/sinks/payload.mjs'

const intervalMillis = intervals => intervals.reduce((total, interval) => total + interval.endedMs - interval.startedMs, 0)

/** 复现服务端 measurePage 的校验（GeneralTickerPatService.java:837-873），逐条断言。 */
const measureHandPage = payload => {
  let characters = 0
  let symbolMillis = 0
  for (const group of payload.messageBody) {
    const patKeys = JSON.parse(group.patKeys)
    const moresValue = JSON.parse(group.moresValue)
    const moresTime = JSON.parse(group.moresTime)
    assert.equal(moresValue.length, patKeys.length, '原始码与字符数量不一致')
    assert.equal(moresTime.length, patKeys.length, '时长与字符数量不一致')
    patKeys.forEach((character, index) => {
      assert.equal([...character].length, 1, `拍发事件 ${character} 不是单个字符`)
      const signal = moresValue[index]
      const durations = moresTime[index]
      assert.ok(signal.length > 0, '原始点划不能为空')
      assert.equal(durations.length, signal.length, '点划与时长数量不一致')
      signal.forEach(code => assert.ok(code === 0 || code === 1, `原始码 ${code} 不是点或划`))
      durations.forEach(value => {
        assert.ok(Number.isFinite(value) && value >= 0, `时长 ${value} 不是有限非负数`)
        symbolMillis += value
      })
      if (character.trim() && character !== '?') characters++
    })
  }
  return {characters, symbolMillis}
}

test('手键载荷满足服务端 measurePage 的全部约束，且原始时长之和不超过采集区间', () => {
  const plan = handKeyPlan({rate: 100})
  const timeline = handKeyTimeline({text: 'ABCD EFGH', plan, tail: 'turn'})
  const payload = handUploadPayload({timeline, trainId: 75, floorNumber: 1, capture: {attempt: 0, serverElapsedMs: 2000}})
  const {characters, symbolMillis} = measureHandPage(payload)
  assert.equal(characters, 8, '正文字符数应为报文实际字数，控制符不入 messageBody')
  const duration = intervalMillis(payload.captureIntervals)
  assert.ok(duration > 0, '有字符就必须有非零采集区间，否则服务端判 202')
  assert.ok(symbolMillis <= duration + payload.captureIntervals.length,
    `原始时长之和 ${symbolMillis} 超过采集区间 ${duration}，服务端会判 202`)
  assert.ok(payload.captureIntervals[0].startedMs >= 2000, '采集区间必须落在服务端 elapsed 坐标上')
})

test('手键载荷里的 patLogs 与点划一一对应，且前导间隔归到下一个字', () => {
  const plan = handKeyPlan({rate: 100})
  const timeline = handKeyTimeline({text: 'AB', plan, preamble: false})
  const payload = handUploadPayload({timeline, trainId: 1, capture: {attempt: 0}})
  const logs = JSON.parse(payload.messageBody[0].patLogs)
  const signal = JSON.parse(payload.messageBody[0].moresValue)
  // 首字没有前导间隔；第二个字的 patLogs 第一项是字间隔（handKeyTrain.js:217-244 先编译再入队）。
  assert.equal(logs[0].filter(entry => entry.key !== 2).length, signal[0].length)
  assert.notEqual(logs[0][0].key, 2, '首字不应有前导间隔')
  assert.equal(logs[1][0].key, 2, '第二个字的第一项应是字间隔')
  assert.equal(logs[1].filter(entry => entry.key !== 2).length, signal[1].length)
})

test('压缩到更短的真实窗口后，原始时长之和仍不超过采集区间', () => {
  const plan = handKeyPlan({rate: 100})
  const timeline = handKeyTimeline({text: 'ABCD EFGH IJKL', plan})
  const elapsed = 3000
  const scale = fitScale(timeline, elapsed)
  assert.ok(scale < 1, '样本时间轴应比 3s 长，否则这个用例没在测压缩')
  const payload = handUploadPayload({timeline, trainId: 1, capture: {attempt: 0, serverElapsedMs: 0}, scale})
  const {symbolMillis} = measureHandPage(payload)
  const duration = intervalMillis(payload.captureIntervals)
  assert.ok(duration <= elapsed, `采集区间 ${duration} 超出真实窗口 ${elapsed}，服务端 captureBound 会拒`)
  assert.ok(symbolMillis <= duration + payload.captureIntervals.length,
    `压缩后原始时长之和 ${symbolMillis} 超过采集区间 ${duration}`)
})

test('电子键载荷按页内位置严格递增，逐字用时之和不超过采集区间', () => {
  const plan = electronKeyPlan({rate: 20})
  const timeline = electronKeyTimeline({text: 'ABCD EFGH IJKL', plan, tail: 'page'})
  const payload = electronUploadPayload({timeline, trainId: 88, pageNumber: 1, capture: {attempt: 3, serverElapsedMs: 500}})
  assert.deepEqual(payload.pageValue.map(group => group.sort), [0, 1, 2])
  let rawMillis = 0
  for (const group of payload.pageValue) {
    const value = JSON.parse(group.value)
    const time = JSON.parse(group.time)
    assert.equal(time.length, value.length, '逐字用时与字符数量不一致')
    time.forEach(milliseconds => {
      assert.ok(Number.isFinite(milliseconds) && milliseconds >= 0, `用时 ${milliseconds} 不是有限非负数`)
      rawMillis += milliseconds
    })
  }
  const active = intervalMillis(payload.captureIntervals)
  assert.ok(rawMillis <= active + payload.captureIntervals.length,
    `逐字用时之和 ${rawMillis} 超过采集区间 ${active}，服务端会判 202`)
  assert.equal(payload.attempt, 3, 'attempt 必须原样带回，否则服务端判 208 轮次已变化')
})

test('电子键节拍能让服务端重算出目标码率（组/分，按 4 字符一组）', () => {
  const rate = 20
  const timeline = electronKeyTimeline({text: 'ABCD EFGH IJKL MNOP', plan: electronKeyPlan({rate}), tail: 'page'})
  const payload = electronUploadPayload({timeline, trainId: 1, capture: {attempt: 0, serverElapsedMs: 0}})
  const characters = payload.pageValue.reduce((total, group) => total + JSON.parse(group.value).length, 0)
  // 服务端：FOUR_CHARACTER_GROUPS_PER_MINUTE.rate(chars, millis) = chars*60000/(millis*4)，HALF_UP 0 位。
  const active = intervalMillis(payload.captureIntervals)
  const computed = Math.round(characters * 60000 / (active * 4))
  assert.equal(computed, rate, `服务端会算出 ${computed} 组/分，与设定的 ${rate} 不符`)
})
