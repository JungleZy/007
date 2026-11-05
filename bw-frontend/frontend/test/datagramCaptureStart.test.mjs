import assert from 'node:assert/strict'
import {readFileSync} from 'node:fs'
import test from 'node:test'
import vm from 'node:vm'
import {ref} from 'vue'

const captureSource = readFileSync(new URL('../src/common/mixin/useTrainingCapture.js', import.meta.url), 'utf8')
  .replace(/^import .*$/gm, '').replace('export default function', 'function')
const source = readFileSync(new URL('../src/views/manage/organization/datagramZuXun/train/student/js/datagramTrain.js', import.meta.url), 'utf8')
  .replace(/^import .*$/gm, '').replace('export default function (', 'function createTrain(')

function setup(status = 0) {
  let clock = 0, serverStart = status === 1 ? 0 : null, socket, timerId = 0, requests = 0, fail = false
  const timers = new Map(), listeners = new Map(), uploads = [], errors = []
  const localStorage = {getItem: key => key === 'userInfo' ? JSON.stringify({id: 'student'}) : null}
  const context = vm.createContext({
    ref, onUnmounted() {}, nextTick: callback => callback(),
    performance: {now: () => clock}, localStorage,
    window: {localStorage, addEventListener: (key, callback) => listeners.set(key, callback), removeEventListener() {}},
    document: {getElementsByTagName: () => [{focus() {}}]},
    message: {success() {}, error: value => errors.push(value)},
    useRouter: () => ({getRoutes: () => [], push() {}}),
    PublicSocket: () => ({ws_connect: (url, callback) => { socket = callback }, sendMessage() {}, closeWebSocket() {}}),
    setInterval: callback => { timers.set(++timerId, callback); return timerId },
    clearInterval: id => timers.delete(id),
    getDatagramZuXunPageNumber: async () => {
      requests++
      return fail ? {code: 202, message: '同步失败'} : {code: 200, data: {
        messageVO: [], protocolVersion: 1, attempt: 0,
        serverElapsedMs: serverStart === null ? 0 : clock - serverStart,
        savedCaptureIntervals: []
      }}
    },
    uploadDatagramResult: async payload => {
      uploads.push(payload)
      for (const interval of payload.captureIntervals) {
        assert.ok(interval.startedMs >= 0)
        assert.ok(interval.endedMs > interval.startedMs)
        assert.ok(interval.endedMs <= clock - serverStart, '采集区间不得包含等待教员的时间')
      }
      return {code: 200}
    },
    startTrainUser: async () => ({code: 200}),
    finishDatagramZuXun: async () => ({code: 200})
  })
  vm.runInContext(captureSource + '\n' + source, context)
  const state = context.createTrain(ref({trainId: 'train', status, floorNow: 1, telegraph: [], pag: 2}), ref(false), () => {})
  state.trainTimeRef.value = {}
  state.connectWebsocket()
  return {
    state, uploads, errors, listeners,
    requests: () => requests,
    fail: value => { fail = value },
    advance: milliseconds => { clock += milliseconds },
    begin: () => { serverStart = clock; socket({data: JSON.stringify({data: JSON.stringify({topic: 'begin'})})}) },
    tick: async () => {
      clock += 1000
      for (const callback of [...timers.values()]) callback()
      await new Promise(resolve => setImmediate(resolve))
    }
  }
}

test('准备后等待一分钟，正式开训重新同步，上传区间位于服务端有效时间内', async () => {
  const h = setup()
  h.state.readyTrainPat()
  assert.equal(h.requests(), 0)
  assert.equal(h.listeners.has('keydown'), false)
  h.advance(60000)
  h.begin()
  for (let i = 0; i < 5; i++) await h.tick()
  assert.equal(h.requests(), 1)
  assert.equal(h.state.cutTimer.value, 'begin')
  h.state.pageCodes.value[0] = '1234'
  h.advance(2000)
  assert.equal(await h.state.endTrain(), true)
  assert.equal(h.uploads.length, 1)
  assert.equal(h.uploads[0].captureIntervals[0].startedMs, 5000)
  assert.equal(h.uploads[0].captureIntervals[0].endedMs, 7000)
})

test('开训同步失败保持输入关闭，可重试同步后提交', async () => {
  const h = setup()
  h.state.readyTrainPat()
  h.fail(true)
  h.begin()
  for (let i = 0; i < 5; i++) await h.tick()
  assert.equal(h.state.cutTimer.value, null)
  assert.equal(h.state.readyPat.value, false)
  assert.equal(h.listeners.has('keydown'), false)
  assert.deepEqual(h.errors, ['同步失败'])
  h.fail(false)
  assert.equal(await h.state.readyTrainPat(), true)
  h.state.pageCodes.value[0] = '5678'
  h.advance(1000)
  assert.equal(await h.state.endTrain(), true)
})

test('加入已开始的训练时同步当前时间轴后正常提交', async () => {
  const h = setup(1)
  h.advance(60000)
  assert.equal(await h.state.readyTrainPat(), true)
  assert.equal(h.state.cutTimer.value, 'begin')
  h.state.pageCodes.value[0] = '9012'
  h.advance(2000)
  assert.equal(await h.state.endTrain(), true)
  assert.equal(h.uploads[0].captureIntervals[0].startedMs, 60000)
  assert.equal(h.uploads[0].captureIntervals[0].endedMs, 62000)
})
