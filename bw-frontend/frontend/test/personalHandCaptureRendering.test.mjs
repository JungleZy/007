import assert from 'node:assert/strict'
import {readFileSync} from 'node:fs'
import test from 'node:test'
import vm from 'node:vm'
import {ref, shallowRef} from 'vue'
import useMorse from '../src/common/mixin/useMorse.js'
import {handKeyThresholds} from '../src/common/utils/handKeyTiming.js'
import {HAND_KEY_CONTROL_PATTERNS} from '../src/common/utils/handKeyControlCodes.js'
import {createHandKeyDecoder} from '../src/common/utils/handKeyDecoder.js'

const strip = text => text.replace(/import\s+[\s\S]*?from\s+['"][^'"]+['"];?/g, '')
const source = strip(readFileSync(new URL('../src/views/manage/postJob/telegram/train/js/details.js', import.meta.url), 'utf8'))
  .replace('export default function (', 'function createDetails(')
const standardSource = strip(readFileSync(new URL('../src/views/manage/postJob/telegram/train/js/patStandard.js', import.meta.url), 'utf8'))
  .replace('export default function ()', 'function countPatStandard()')

function setup() {
  const frames = new Map(), timers = new Map(), unmount = []
  let id = 0, clock = 0, reads = 0
  const context = vm.createContext({ref, shallowRef, useMorse, handKeyThresholds, HAND_KEY_CONTROL_PATTERNS,
    sum: a => a.reduce((x, y) => x + y, 0),
    onMounted() {}, onUnmounted: fn => unmount.push(fn), nextTick: fn => fn(),
    useRouter: () => ({getRoutes: () => []}), PubSub: {subscribe() {}, unsubscribe() {}},
    window: {removeEventListener() {}},
    useConfirmedSubmission: () => ({pending: () => false, defer: () => false, busy: ref(false), error: ref(null)}),
    useTrainingCapture: () => ({open() {}, close() {}, elapsed: () => clock}),
    requestAnimationFrame: fn => {frames.set(++id, fn); return id}, cancelAnimationFrame: id => frames.delete(id),
    setTimeout: (fn, delay) => {timers.set(++id, {fn, due: clock + delay}); return id},
    clearTimeout: id => timers.delete(id), setInterval: () => 0, clearInterval() {}
  })
  vm.runInContext(standardSource + '\n' + source, context)
  const data = ref({status: 1, process: 2, type: 0, codeSort: false, floorNow: 1, pag: 1,
    patCodeLog: [], patKeyVal: [], telegraph: {curr: []}})
  const standard = ref({dot: 80, line: 240, codeGap: 80, wordGap: 240, groupGap: 400})
  const state = context.createDetails(data, standard, ref(51), ref(true), ref(true), ref(false), ref([]))
  state.patValBoxRef.value = {get scrollWidth() {reads++; return 100}, scrollLeft: 0}
  state.patKeyBoxRef.value = {get scrollHeight() {reads++; return 200}, scrollTop: 0}
  state.trainTimeRef.value = {}
  const advance = time => {
    for (;;) {
      const entry = [...timers.entries()].filter(([, t]) => t.due <= time).sort((a, b) => a[1].due - b[1].due)[0]
      if (!entry) break
      timers.delete(entry[0]); clock = entry[1].due; entry[1].fn()
    }
    clock = time
  }
  const decoder = createHandKeyDecoder({enabled: () => true,
    threshold: () => handKeyThresholds(standard.value, 51).dotDash, lineLimit: () => standard.value.line * 1.51,
    emit: event => state.handleReceiveKeyCode(event.code, event.diffTime, event.gapTime, event)})
  const edge = (time, k) => {advance(time);decoder.receive({t: 0, k, d: time, receivedAt: time, timeSource: 'fixture'})}
  let position = 0
  const send = (code, after) => {
    for (let i = 0; i < code.length; i++) {
      edge(position, 0);position += code[i] === '1' ? 240 : 80;edge(position, 1)
      position += i === code.length - 1 ? after : 80
    }
  }
  return {state, data, reads: () => reads, frames, advance, send,
    flush: () => {for (const fn of frames.values()) fn();frames.clear()},
    finish: () => advance(position + 1000), unmount: () => unmount.forEach(fn => fn())}
}

test('个人岗位手键渲染暂停时仍完整解码，恢复显示后波形及文字一致', () => {
  const h = setup()
  h.send('10001', 400)
  const alphabet = useMorse().codeKey.short
  const expected = Array(50).fill('1234')
  for (const group of expected) for (let i = 0; i < group.length; i++) {
    h.send(Object.keys(alphabet).find(code => alphabet[code] === group[i]), i === group.length - 1 ? 400 : 240)
  }
  h.finish()
  assert.deepEqual(h.data.value.telegraph.curr.map(group => group.patKeys.join('')), expected)
  assert.equal(h.reads(), 0, '采集处理不能同步读取滚动布局')
  h.flush()
  assert.equal(h.reads(), 2)
  assert.equal(h.state.showPatCodeLog.value.length, 70)
  assert.equal(h.state.patValBoxRef.value.scrollLeft, 100)
  assert.equal(h.state.patKeyBoxRef.value.scrollTop, 200)
  h.unmount()
})

test('退出个人岗位训练后取消尚未执行的显示更新', () => {
  const h = setup()
  h.send('10001', 400)
  assert.equal(h.frames.size, 1)
  h.unmount()
  assert.equal(h.frames.size, 0)
  h.flush()
  assert.equal(h.reads(), 0)
})
