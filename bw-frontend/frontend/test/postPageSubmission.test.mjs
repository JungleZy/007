import assert from 'node:assert/strict'
import {readFileSync} from 'node:fs'
import test from 'node:test'
import vm from 'node:vm'
import {computed, createRenderer, createVNode, nextTick, onBeforeUnmount, onMounted, reactive, ref} from 'vue'

const source = readFileSync(new URL('../src/views/manage/postJob/datagram/js/usePageSubmission.js', import.meta.url), 'utf8')
  .replace(/^import[\s\S]*?from ['"][^'"]+['"]\s*/gm, '').replace('export default ', '')
const renderer = createRenderer({createComment: () => ({}), insert() {}, remove() {}, parentNode: () => null, nextSibling: () => null})
const pageSources = Object.fromEntries(['datagramTrain', 'telexTrain'].map(kind => [kind,
  readFileSync(new URL(`../src/views/manage/postJob/datagram/${kind}/js/telexTrain.js`, import.meta.url), 'utf8')
    .replace(/^import .*$/gm, '').replace('export default function', 'function createTraining')]))
const settle = async () => { for (let i = 0; i < 20; i++) await nextTick() }

function mountFlow(t, {pages = 1, saved = {}, status = 0, kind, remainingMs = null} = {}) {
  let now = 10000, detailCount = 0, finished = 0
  const calls = [], shown = [], notices = [], text = reactive(new Map()), timers = new Set()
  const keyboard = new EventTarget(), document = new EventTarget()
  keyboard.localStorage = {getItem: key => key === 'token' ? 'fixture-token' : 'fixture-device'}
  document.querySelector = () => null
  const metadata = {id: 'fixture', attempt: 2, status, protocolVersion: 1, groupNumber: pages * 100, serverElapsedMs: now, remainingMs}
  const receipts = new Map(Object.entries(saved).map(([number, receipt]) => [Number(number), receipt]))
  const detail = () => ({...metadata, serverElapsedMs: now})
  const api = {
    async detail() { detailCount++; return detail() },
    async upload(payload) { receipts.set(payload.pageNumber, {submitted: true, attempt: 2, codeAll: payload.patValue, captureIntervals: payload.captureIntervals}); return {} },
    async finish() { finished++; metadata.status = 3; return detail() }
  }
  const request = (name, action) => async payload => {
    calls.push({name, payload: JSON.parse(JSON.stringify(payload))})
    now += 25 // 每次请求的模拟往返耗时，不能进入正文采集时长。
    return {code: 200, data: await action(payload)}
  }
  const context = vm.createContext({
    computed, ref, onMounted, onBeforeUnmount, nextTick, createVNode, window: keyboard, document,
    useRoute: () => ({query: {id: 'fixture'}, matched: Array.from({length: 5}, () => ({path: '/fixture'}))}),
    useRouter: () => ({push() {}}), PubSub: {subscribe() {}, unsubscribe() {}},
    performance: {now: () => now}, message: {error: value => notices.push(value), info() {}, warning() {}},
    setInterval: callback => { timers.add(callback); return callback }, clearInterval: callback => timers.delete(callback),
    getTelexTrainByID: request('detail', () => api.detail()),
    apiPostTelexPatTrainGetPage: request('page', ({pageNumber}) => ({submitted: false, attempt: 2, ...receipts.get(pageNumber), pageVo: [{pageNumber, key: `PAGE${pageNumber}`}]})),
    postTelexPatTrain: request('begin', () => {metadata.status = 1; return detail()}),
    pausePostTelexPatTrain: request('pause', () => {metadata.status = 2; return detail()}),
    resumePostTelexPatTrain: request('resume', () => {metadata.status = 1; return detail()}),
    resetPostTelexPatTrain: request('reset', () => {metadata.status = 0; metadata.attempt++; receipts.clear(); return detail()}),
    apiPostTelexPatTrainFinishPage: request('upload', payload => api.upload(payload)),
    endTelexPatTrain: request('finish', () => api.finish())
  })
  vm.runInContext(source, context)
  if (kind) vm.runInContext(pageSources[kind], context)
  const trainData = ref({status: 0, duration: 0}), page = ref({current: 1, pageAll: 0})
  let flow
  const app = renderer.createApp({setup() {
    flow = kind ? context.createTraining(ref({autoSetTimeAdd() {}}), ref(false)) : context.usePageSubmission({id: 'fixture', trainData, page, loading: ref(false), countDown: ref({autoSetTimeAdd() {}}),
      getText: number => text.get(number) || '', restoreText: (number, value) => text.set(number, value),
      showPage: (number, rows) => shown.push({number, key: rows[0].key}), onFinished: () => {}})
    return () => null
  }})
  app.mount({})
  t.after(() => app.unmount())
  return {flow, api, calls, shown, notices, text, trainData: kind ? flow.trainData : trainData, page: kind ? flow.page : page,
    tick: () => {for (const timer of timers) timer()},
    type(value) {
      if (kind === 'datagramTrain') {
        flow.pageCodes.value[flow.page.value.current - 1] = value
        flow.textareaChange()
      } else for (const key of value) {
        const event = new Event('keydown', {cancelable: true})
        Object.defineProperty(event, 'key', {value: key === '\n' ? 'Enter' : key})
        keyboard.dispatchEvent(event)
      }
    },
    advance: milliseconds => {now += milliseconds}, detailCount: () => detailCount, finished: () => finished}
}

test('单页进入只取一次报底，加载结束仍同步服务器时钟，加载时间不计入采集', async t => {
  const p = mountFlow(t)
  assert.equal(await p.flow.initialise(), true)
  assert.deepEqual(p.calls.map(call => call.name), ['detail', 'page', 'detail'])
  assert.deepEqual(p.shown, [{number: 1, key: 'PAGE1'}])
  p.advance(5000)
  assert.equal(p.flow.elapsedMilliseconds(), 0)
  await p.flow.startTest()
  p.advance(1000)
  assert.equal(p.flow.elapsedMilliseconds(), 1000)
})

test('恢复多页训练：每页只读一次，恢复已确认内容和区间，显示第一张未确认报底', async t => {
  const saved = {
    1: {submitted: true, attempt: 2, codeAll: '1234', captureIntervals: [{startedMs: 0, endedMs: 1000}]},
    2: {submitted: true, attempt: 2, codeAll: '5678', captureIntervals: [{startedMs: 1000, endedMs: 2000}]}
  }
  const p = mountFlow(t, {pages: 3, saved, status: 1})
  assert.equal(await p.flow.initialise(), true)
  assert.deepEqual(p.calls.filter(call => call.name === 'page').map(call => call.payload.pageNumber), [1, 2, 3])
  assert.deepEqual(p.shown, [{number: 3, key: 'PAGE3'}])
  assert.deepEqual([...p.text.values()], ['1234', '5678', ''])
  assert.equal(p.flow.elapsedMilliseconds(), 2000)
  p.text.set(3, '9012')
  p.advance(1000)
  assert.equal(await p.flow.endTest(), true)
  assert.deepEqual(p.calls.filter(call => call.name === 'upload').map(call => call.payload.pageNumber), [3])
  assert.equal(p.finished(), 1)
})

test('加载中轮次改变时保持锁定，不把缓存报底当成新一轮数据继续采集', async t => {
  const p = mountFlow(t, {status: 1})
  const original = p.api.detail
  p.api.detail = async () => {
    const detail = await original()
    return p.detailCount() > 1 ? {...detail, attempt: 3} : detail
  }
  assert.equal(await p.flow.initialise(), false)
  assert.match(p.flow.failure.value, /轮次已变更/)
  assert.equal(p.flow.inputLocked.value, true)
  p.advance(1000)
  assert.equal(p.flow.elapsedMilliseconds(), 0)
})

test('结束上传失败后重试原正文和区间，确认前不结算，重试等待不计时', async t => {
  const p = mountFlow(t)
  await p.flow.initialise()
  await p.flow.startTest()
  p.text.set(1, '1234 5678')
  p.advance(2000)
  const upload = p.api.upload
  let attempts = 0
  p.api.upload = async payload => {if (++attempts === 1) throw new Error('断网'); return upload(payload)}
  assert.equal(await p.flow.endTest(), false)
  assert.equal(p.finished(), 0)
  assert.equal(p.flow.inputLocked.value, true)
  p.advance(5000)
  p.tick()
  assert.equal(p.trainData.value.speed, 240, '上传失败后的等待不降低已冻结的码率')
  assert.equal(await p.flow.retry(), true)
  const uploads = p.calls.filter(call => call.name === 'upload')
  assert.equal(uploads.length, 2)
  assert.deepEqual(uploads[0].payload, uploads[1].payload)
  const duration = uploads[1].payload.captureIntervals.reduce((sum, span) => sum + span.endedMs - span.startedMs, 0)
  assert.equal(duration, 2000)
  assert.equal(p.finished(), 1)
  assert.equal(p.trainData.value.status, 3)
})

test('结束结算失败时只重试结算，已确认页不再上传', async t => {
  const p = mountFlow(t)
  await p.flow.initialise()
  await p.flow.startTest()
  p.text.set(1, '1234')
  p.advance(2000)
  const finish = p.api.finish
  let attempts = 0
  p.api.finish = async () => {if (++attempts === 1) throw new Error('结算暂时失败'); return finish()}
  assert.equal(await p.flow.endTest(), false)
  assert.equal(await p.flow.retry(), true)
  assert.equal(p.calls.filter(call => call.name === 'upload').length, 1)
  assert.equal(p.calls.filter(call => call.name === 'finish').length, 2)
  assert.equal(p.text.get(1), '1234')
})

for (const kind of ['datagramTrain', 'telexTrain']) {
  const name = kind === 'datagramTrain' ? '数据报' : '电传'
  test(`${name}：按毫秒实时计速，停顿时无需再次按键，暂停和恢复只计有效时长`, async t => {
    const p = mountFlow(t, {kind})
    await settle()
    assert.equal(p.flow.ready.value, true)
    assert.equal(p.trainData.value.speed, 0)
    await p.flow.startTest()
    p.advance(500)
    p.type('ABCD / ')
    p.tick()
    assert.equal(p.trainData.value.speed, 480, '0.5秒拍发4码，空格和改错斜线不计数')
    const requests = p.calls.length
    p.advance(1500)
    p.tick()
    assert.equal(p.trainData.value.speed, 120, '没有新按键，2秒时自动降到120码/分')
    assert.equal(p.calls.length, requests, '实时刷新不额外请求后端')

    await p.flow.pauseTest()
    p.advance(5000)
    p.tick()
    assert.equal(p.trainData.value.speed, 120)
    assert.equal(p.trainData.value.duration, 2)
    await p.flow.resumeTest()
    p.advance(2000)
    p.tick()
    assert.equal(p.trainData.value.speed, 60)
    assert.equal(p.trainData.value.duration, 4)

    await p.flow.resetTest()
    p.tick()
    assert.equal(p.trainData.value.speed, 0)
    assert.equal(p.trainData.value.duration, 0)
  })

  test(`${name}：多页恢复后累计正文和时长，翻页等待与停顿计时分别处理`, async t => {
    const saved = {1: {submitted: true, attempt: 2, codeAll: 'ABCD', captureIntervals: [{startedMs: 0, endedMs: 2000}]}}
    const p = mountFlow(t, {kind, pages: 2, saved, status: 1})
    await settle()
    assert.equal(p.page.value.current, 2)
    assert.equal(p.trainData.value.speed, 120)
    p.advance(2000)
    p.type('EFGH')
    p.tick()
    assert.equal(p.trainData.value.speed, 120, '两页累计8码/4秒')
    await p.flow.prev()
    p.tick()
    assert.equal(p.trainData.value.speed, 120, '上传和翻页的请求时间不参与计速')
    p.advance(4000)
    p.tick()
    assert.equal(p.trainData.value.speed, 60, '只切换报底不会重置累计码率')
  })
}

test('倒计时截止后速率冻结在截止时刻，结束等待不使速率继续下降', async t => {
  const p = mountFlow(t, {status: 1, remainingMs: 1000})
  await p.flow.initialise()
  p.text.set(1, '1234')
  p.advance(1500)
  p.tick()
  await settle()
  assert.equal(p.trainData.value.speed, 240)
  p.advance(5000)
  p.tick()
  assert.equal(p.trainData.value.speed, 240)
  assert.equal(p.trainData.value.duration, 1)
})
