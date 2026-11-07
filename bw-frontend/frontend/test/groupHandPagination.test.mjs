import assert from 'node:assert/strict'
import {readFileSync} from 'node:fs'
import test from 'node:test'
import vm from 'node:vm'
import {ref} from 'vue'

const strip = source => source.replace(/import\s+[\s\S]*?from\s+['"][^'"]+['"];?/g, '')
const source = strip(readFileSync(new URL('../src/views/manage/organization/handkeyZuXun/train/student/js/handKeyTrain.js', import.meta.url), 'utf8'))
  .replace('export default function (', 'function createTrain(')
const submission = strip(readFileSync(new URL('../src/common/mixin/useConfirmedSubmission.js', import.meta.url), 'utf8'))
  .replace('export default function', 'function')

function setup() {
  const requests = [], retries = [], bound = []
  let fail = false, deferred = null
  const context = vm.createContext({ref, watch() {}, onMounted() {}, onUnmounted() {},
    useRouter: () => ({getRoutes: () => []}), useMorse: () => ({codeKey: {}}), countPatStandard: () => ({}),
    PublicSocket: () => ({ws_connect() {}, sendMessage() {}, closeWebSocket() {}}),
    localStorage: {getItem: key => key === 'userInfo' ? '{"id":"student"}' : 'session'},
    useTrainingCapture: () => ({bind: data => bound.push(data.page), close() {}}),
    isTerminalCode: () => false,
    Modal: {confirm: dialog => retries.push(dialog)},
    getHandKeyZuXunPageNumber: async request => {
      requests.push({...request})
      if (fail) return {code: 202, message: '获取报底失败'}
      if (deferred) await deferred
      return {code: 200, data: {page: request.floorNumber, messageKey: [{moresKey: JSON.stringify([String(request.floorNumber)])}]}}
    }
  })
  vm.runInContext(submission + '\n' + source, context)
  const data = ref({trainId: 12, floorNow: 1, pag: 2, status: 0, telegraph: []})
  const state = context.createTrain(data, ref({}), ref(50), ref(true), ref(true), ref(false), () => {})
  return {data, state, requests, retries, bound, fail: value => {fail = value}, defer: promise => {deferred = promise}}
}

test('手键组训两页报底：下一页实际请求第二页，成功后切换，再返回第一页', async () => {
  const h = setup()
  await h.state.getPostTrainKeyInfo(1)
  assert.equal(await h.state.switchTelegram('next'), true)
  assert.deepEqual(h.requests[1], {id: 12, userId: 'student', floorNumber: 2})
  assert.equal(h.data.value.floorNow, 2)
  assert.equal(h.data.value.telegraph[1][0].moresKey, '["2"]')
  assert.equal(await h.state.switchTelegram('next'), false)
  assert.equal(await h.state.switchTelegram('prev'), true)
  assert.equal(h.data.value.floorNow, 1)
  assert.deepEqual(h.bound, [1, 2, 1])
})

test('取页失败保持当前页，原请求重试成功才切换', async () => {
  const h = setup()
  await h.state.getPostTrainKeyInfo(1)
  h.fail(true)
  assert.equal(await h.state.switchTelegram('next'), false)
  assert.equal(h.data.value.floorNow, 1)
  assert.equal(h.data.value.telegraph[1], undefined)
  h.fail(false)
  assert.equal(await h.retries[0].onOk(), true)
  assert.equal(h.data.value.floorNow, 2)
  assert.equal(h.data.value.telegraph[1][0].moresKey, '["2"]')
})

test('等待响应时不提前切页或重复请求，训练及收尾阶段不允许手工翻页', async () => {
  const h = setup()
  let resolve
  h.defer(new Promise(done => {resolve = done}))
  const request = h.state.switchTelegram('next')
  assert.equal(h.data.value.floorNow, 1)
  assert.equal(h.state.switchTelegram('next'), false)
  assert.equal(h.requests.length, 1)
  resolve()
  await request
  assert.equal(h.data.value.floorNow, 2)
  for (const status of [1, 3]) {
    h.data.value.status = status
    assert.equal(h.state.switchTelegram('prev'), false)
  }
  assert.equal(h.requests.length, 1)
})
