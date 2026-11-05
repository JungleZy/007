import assert from 'node:assert/strict'
import {readFileSync} from 'node:fs'
import test from 'node:test'
import vm from 'node:vm'
import {ref, shallowRef} from 'vue'

const strip = source => source.replace(/import\s+[\s\S]*?from\s+['"][^'"]+['"];?/g, '')
const details = strip(readFileSync(new URL('../src/views/manage/postJob/telegram/train/js/details.js', import.meta.url), 'utf8'))
  .replace('export default function (', 'function createDetails(')
const submission = strip(readFileSync(new URL('../src/common/mixin/useConfirmedSubmission.js', import.meta.url), 'utf8'))
  .replace('export default function', 'function')

function setup() {
  const requests = [], confirmations = [], starts = []
  let failure = false, deferred = null
  const response = page => ({code: 200, data: {attempt: 0, protocolVersion: 1, serverElapsedMs: 0,
    messageKey: [{moresKey: JSON.stringify([String(page)]), patKeys: '[]', moresValue: '[]', moresTime: '[]', patLogs: '[]'}]}})
  const storage = new Map([['token', 'test-token'], ['deviceId', 'test-device']])
  const context = vm.createContext({ref, shallowRef, onMounted() {}, onUnmounted() {},
    useMorse: () => ({codeKey: {}}), countPatStandard: () => ({}), useRouter: () => ({getRoutes: () => []}),
    PubSub: {subscribe() {}}, HAND_KEY_CONTROL_PATTERNS: [],
    useTrainingCapture: () => ({close() {}, bind() {}, open() {}}),
    localStorage: {getItem: key => storage.get(key), setItem: (key, value) => storage.set(key, value), removeItem: key => storage.delete(key)},
    isTerminalCode: code => code === 207 || code === 208, terminalReason: (code, reason) => reason,
    Modal: {confirm: dialog => confirmations.push(dialog), error: dialog => confirmations.push(dialog)},
    beginPostTelegramTrain: async payload => {
      starts.push(payload)
      return {code: 200, data: {status: 1, attempt: 0, protocolVersion: 1, serverElapsedMs: 0}}
    },
    getPostTelegramMsgBody: async ({floorNumber}) => {
      requests.push(floorNumber)
      if (failure) return {code: 202, message: '取页失败'}
      if (deferred) return deferred
      return response(floorNumber)
    }
  })
  vm.runInContext(submission + '\n' + details, context)
  const data = ref({trainId: 'train', status: 0, floorNow: 1, pag: 3, telegraph: {curr: null, next: null, prev: null}})
  const printPages = ref([])
  const state = context.createDetails(data, ref({}), ref(50), ref(true), ref(true), ref(false), printPages)
  return {data, state, printPages, requests, confirmations, response, starts,
    fail: value => {failure = value}, defer: promise => {deferred = promise}}
}

test('个人岗位手键只加载第一页后，按实际目标页加载下一页及末页，往返不重复打印报底', async () => {
  const h = setup()
  await h.state.getPostTrainKeyInfo(1, 'curr')
  assert.equal(h.data.value.telegraph.next, null)
  for (const [direction, page] of [['next', 2], ['next', 3], ['prev', 2], ['prev', 1], ['next', 2]]) {
    assert.equal(await h.state.switchTelegram(direction), true)
    assert.equal(h.data.value.floorNow, page)
    assert.equal(h.data.value.telegraph.curr[0].moresKey, JSON.stringify([String(page)]))
  }
  assert.deepEqual(h.requests, [1, 2, 3, 2, 1, 2])
  assert.deepEqual(JSON.parse(JSON.stringify(h.printPages.value)), [[['1']], [['2']], [['3']]])
})

test('取页失败保留原页和报底，重试成功后才切换', async () => {
  const h = setup()
  await h.state.getPostTrainKeyInfo(1, 'curr')
  const original = h.data.value.telegraph.curr
  h.fail(true)
  assert.equal(await h.state.switchTelegram('next'), false)
  assert.equal(h.data.value.floorNow, 1)
  assert.equal(h.data.value.telegraph.curr, original)
  assert.equal(h.confirmations.length, 1)
  h.fail(false)
  await h.confirmations[0].onOk()
  assert.equal(h.data.value.floorNow, 2)
  assert.equal(h.data.value.telegraph.curr[0].moresKey, '["2"]')
})

test('连续点击不跳页，训练中与边界点击不发起取页请求', async () => {
  const h = setup()
  await h.state.getPostTrainKeyInfo(1, 'curr')
  assert.equal(h.state.switchTelegram('prev'), false)
  let resolve
  h.defer(new Promise(done => {resolve = done}))
  const pending = h.state.switchTelegram('next')
  assert.equal(h.state.switchTelegram('next'), false)
  assert.equal(h.data.value.floorNow, 1)
  resolve(h.response(2))
  await pending
  assert.equal(h.data.value.floorNow, 2)
  h.data.value.status = 1
  assert.equal(h.state.switchTelegram('next'), false)
  h.data.value.status = 3
  assert.equal(h.state.switchTelegram('prev'), false)
  assert.deepEqual(h.requests, [1, 2])
})

test('预览第三页后点击开始，先读取第一页并同步切换页码和报底', async () => {
  const h = setup()
  await h.state.getPostTrainKeyInfo(3, 'curr')
  assert.equal(h.data.value.floorNow, 3)
  assert.equal(await h.state.beginExerciseInfo(), true)
  assert.deepEqual(h.requests, [3, 1])
  assert.equal(h.starts.length, 1)
  assert.equal(h.data.value.floorNow, 1)
  assert.equal(h.data.value.telegraph.curr[0].moresKey, '["1"]')
  assert.equal(h.data.value.status, 1)
})

test('开始时第一页读取失败不显示错误页码，重试后从第一页开始', async () => {
  const h = setup()
  await h.state.getPostTrainKeyInfo(3, 'curr')
  h.fail(true)
  assert.equal(await h.state.beginExerciseInfo(), false)
  assert.equal(h.starts.length, 0)
  assert.equal(h.data.value.floorNow, 3)
  assert.equal(h.data.value.telegraph.curr[0].moresKey, '["3"]')
  h.fail(false)
  await h.confirmations[0].onOk()
  assert.equal(h.data.value.floorNow, 1)
  assert.equal(h.data.value.telegraph.curr[0].moresKey, '["1"]')
})
