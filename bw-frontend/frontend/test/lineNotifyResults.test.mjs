import assert from 'node:assert/strict'
import {readFileSync} from 'node:fs'
import test from 'node:test'
import vm from 'node:vm'
import {ref} from 'vue'
import {createLineHandControl} from '../src/views/manage/unionJob/lineNotify/js/handControl.js'
import {decodePatRecord} from '../src/views/manage/unionJob/lineNotify/js/decodePatRecord.js'

const load = name => readFileSync(new URL(`../src/views/manage/unionJob/lineNotify/js/${name}.js`, import.meta.url), 'utf8')
  .replace(/^import .*$/gm, '').replace('export default function', 'function')
const recordSource = load('usePatRecord')
const issueSource = load('Issue')
const stroke = {diff: 60, gap: 0, pageNumber: 1}
const second = {diff: 180, gap: 60, pageNumber: 2}

function recordHarness({store = new Map(), remote = [], save} = {}) {
  const state = ref({patCodeLog: []})
  let persisted = structuredClone(remote)
  const writes = []
  const context = vm.createContext({ref, onUnmounted() {},
    localStorage: {getItem: key => store.get(key) ?? null, setItem: (key, value) => store.set(key, value), removeItem: key => store.delete(key)},
    window: {addEventListener() {}, removeEventListener() {}}, setTimeout: () => 1, clearTimeout() {},
    getLinePatRecord: async () => ({code: 200, data: structuredClone(persisted)}),
    saveLinePatRecord: async (room, records) => {
      writes.push(JSON.parse(JSON.stringify(records)))
      if (save) await save(records)
      persisted = JSON.parse(JSON.stringify(records))
      return {code: 200, data: structuredClone(persisted)}
    }
  })
  vm.runInContext(recordSource, context)
  return {record: context.usePatRecord(state, '84', 'sender'), state, writes, store}
}

test('拍发记录保存后重新进入可恢复时长、间隔和页码', async () => {
  const first = recordHarness()
  await first.record.restore()
  first.record.append(stroke)
  first.record.append(second)
  assert.equal(await first.record.flush(), true)
  assert.equal(first.store.has('linePat:84:sender'), false)
  const reopened = recordHarness({remote: first.writes[0]})
  await reopened.record.restore()
  assert.deepEqual(JSON.parse(JSON.stringify(reopened.state.value.patCodeLog)), [stroke, second])
})

test('保存失败保留本机记录，重进后合并服务器前缀并重试', async () => {
  const first = recordHarness({save: async () => { throw new Error('网络断开') }})
  await first.record.restore()
  first.record.append(stroke)
  first.record.append(second)
  assert.equal(await first.record.flush(), false)
  assert.equal(first.record.recordError.value, '网络断开')
  const reopened = recordHarness({store: first.store, remote: [stroke]})
  await reopened.record.restore()
  assert.equal(reopened.state.value.patCodeLog.length, 2)
  assert.equal(await reopened.record.flush(), true)
  assert.deepEqual(reopened.writes[0], [stroke, second])
})

test('保存过程中新增拍发，结束前再次保存等待完整记录落库', async () => {
  let release
  let saves = 0
  const harness = recordHarness({save: () => ++saves === 1 ? new Promise(resolve => { release = resolve }) : Promise.resolve()})
  await harness.record.restore()
  harness.record.append(stroke)
  const pending = harness.record.flush()
  harness.record.append(second)
  const finish = harness.record.flush()
  release()
  await pending
  assert.equal(await finish, true)
  assert.deepEqual(harness.writes, [[stroke], [stroke, second]])
})

test('冲突缓存不覆盖服务器已有拍发记录', async () => {
  const store = new Map([['linePat:84:sender', JSON.stringify([second])]])
  const harness = recordHarness({store, remote: [stroke]})
  await assert.rejects(harness.record.restore(), /不一致/)
  assert.equal(harness.record.recordReady.value, false)
  assert.equal(harness.writes.length, 0)
  assert.ok(store.has('linePat:84:sender'))
})

test('拍发人员可切换不同频道的收报结果，再返回本人拍发记录', async () => {
  const pages = []
  let onKey
  const receivers = [{id: 'receiver1', channel: 1, status: 1}, {id: 'receiver9', channel: 9, status: 1}]
  const state = ref({telegraph: {prev: 1, allPage: 1, curr: []}, patCodeLog: [stroke], currPatKeyIndex: -1})
  const context = vm.createContext({ref, onMounted() {}, onUnmounted() {}, nextTick: callback => callback(),
    decodePatRecord,
    createLineHandControl: options => createLineHandControl({...options, setTimer: () => 1, clearTimer() {}}),
    useRoute: () => ({query: {id: '84', roomState: 'send'}}),
    localStorage: {getItem: () => JSON.stringify({id: 'sender'})}, document: {hidden: false, getElementById: () => null},
    setTimeout: () => 1, clearTimeout() {}, clearInterval() {}, setInterval: () => 1,
    useTraffic: (voice, callback) => { onKey = callback }, deepClone: value => structuredClone(value), message: {error() {}}, Modal: {},
    SocketConnection: class {close() {} send() {}},
    usePatRecord: () => ({restore: async () => {}, recordError: ref(''), recordReady: ref(true), flush: async () => true,
      append: stroke => state.value.patCodeLog.push(stroke)}),
    getRoomUserList: async () => ({code: 200, data: {sendUserList: [{id: 'sender', channel: 1}], receiveUserList: receivers}}),
    apiSimulationRouterRoomDetail: async () => ({code: 200, data: {stats: 2, bwCount: 100, totalTime: 20}}),
    apiSimulationRouterFindPage: async params => {
      pages.push(params.userId)
      return {code: 200, data: {pageVos: [{key: params.userId}], value: [params.userId]}}
    }
  })
  vm.runInContext(issueSource, context)
  const page = context.issue(state, ref(null))
  await page.getApiRoomUserList()
  assert.equal(pages.at(-1), 'sender')
  for (const receiver of receivers) {
    await page.handleActivePutAway(receiver)
    assert.equal(page.activePutAway.value, true)
    assert.equal(page.a.value.value[0], receiver.id)
    assert.equal(pages.at(-1), receiver.id)
  }
  await page.showOwnRecord()
  assert.equal(page.activePutAway.value, false)
  assert.equal(state.value.telegraph.curr[0].key, 'sender')
  assert.equal(state.value.patCodeLog.length, 1)
  state.value.stats = 1
  await page.handleActivePutAway(receivers[0])
  assert.equal(page.activePutAway.value, false, '训练进行中不展示他人答案')
  onKey({k: 0, d: 100.25})
  onKey({k: 1, d: 160.75})
  assert.equal(state.value.patCodeLog[1].diff, 60.5, '保存真实按键时长，小数毫秒不截断')
  onKey({t: 1, d: [0, 1]})
  assert.equal(state.value.patCodeLog.length, 2, '非手键帧不污染记录')
})
