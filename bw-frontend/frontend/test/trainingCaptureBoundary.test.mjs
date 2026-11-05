import assert from 'node:assert/strict'
import {readFileSync} from 'node:fs'
import test from 'node:test'
import vm from 'node:vm'
import {ref} from 'vue'

const source = readFileSync(new URL('../src/common/mixin/useTrainingCapture.js', import.meta.url), 'utf8')
  .replace(/^import .*$/gm, '').replace('export default function', 'function')
function setup() {
  let now = 100
  const context = vm.createContext({ref, performance: {now: () => now}})
  vm.runInContext(source, context)
  const capture = context.useTrainingCapture()
  capture.bind({protocolVersion: 1, attempt: 0, serverElapsedMs: 500})
  return {capture, clock: value => {now = value}}
}

test('本机低延迟提交不把结束毫秒向未来取整，首次提交满足服务端有效上界', () => {
  const {capture, clock} = setup()
  capture.open()
  clock(180.2)
  const intervals = capture.snapshot().captureIntervals
  const receivedBound = Math.floor(500 + 80.2 + 0.1)
  assert.equal(intervals[0].endedMs, 580)
  assert.ok(intervals[0].endedMs <= receivedBound)
})

test('排队事件的区间同样不超出服务端整毫秒边界', () => {
  const {capture, clock} = setup()
  const span = capture.stamp(100.2, 180.2)
  capture.recordQueued(span)
  clock(180.3)
  assert.equal(capture.snapshot().captureIntervals[0].endedMs, 580)
})

test('连续开关、重试快照保持有序且不产生零长度区间', () => {
  const {capture, clock} = setup()
  capture.open()
  clock(100.5)
  assert.equal(capture.snapshot().captureIntervals.length, 0)
  capture.open()
  clock(120.8)
  const first = JSON.stringify(capture.snapshot())
  clock(150)
  assert.equal(JSON.stringify(capture.snapshot()), first, '重试不得扩展已提交采集时间')
  capture.open()
  clock(170.9)
  const intervals = capture.snapshot().captureIntervals
  assert.equal(intervals.length, 2)
  assert.ok(intervals[1].startedMs >= intervals[0].endedMs)
  assert.ok(intervals.every(i => i.endedMs > i.startedMs))
})
