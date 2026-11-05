import assert from 'node:assert/strict'
import {readFileSync} from 'node:fs'
import test from 'node:test'
import vm from 'node:vm'
import {ref, computed} from 'vue'

const source = readFileSync(new URL('../src/views/manage/organization/electronKeyZuXun/train/student/js/trainScore.js', import.meta.url), 'utf8')
  .replace(/^import .*$/gm, '').replace('export default function', 'function')
const row = (pageNumber, value = '["1"]', time = '[100]') => ({
  pageNumber, sort: 0, key: '["1"]', value, time
})

function setup(load, pages = 2) {
  const requests = [], errors = []
  const context = vm.createContext({
    ref, computed, onMounted() {}, onUnmounted() {}, watch() {}, nextTick() {},
    useRoute: () => ({query: {id: '12'}}),
    localStorage: {getItem: () => JSON.stringify({id: 'student'})},
    message: {error: text => errors.push(text)},
    getElectronKeyZuXunPageNumber: async request => {
      requests.push({...request})
      return load(request.pageNumber)
    }
  })
  vm.runInContext(source, context)
  const state = context.telegramList(ref('total'), 'student')
  state.loading.value = false
  Object.assign(state.scoreData.value, {pag: pages, content: [{pageNumber: 1, key: ['1'], value: ['1'], time: [100]}]})
  return {state, requests, errors}
}

test('未缓存第二页时，翻到末页请求第二页并刷新报底、拍发明细和统计', async () => {
  const h = setup(page => ({code: 200, data: {messageVO: [row(page, '["1","2"]')]}}))
  assert.equal(await h.state.switchTelegram(1), false)
  assert.deepEqual(h.requests, [{trainId: '12', userId: 'student', pageNumber: 2}])
  assert.equal(h.state.scoreData.value.currPage, 2)
  assert.equal(h.state.scoreData.value.content[0].pageNumber, 2)
  assert.equal(h.state.trendLogKeyData.value, h.state.scoreData.value.content)
  assert.equal(h.state.scoreData.value.total.dm, 1)
  assert.equal(h.state.loading.value, false)
  assert.equal(await h.state.switchTelegram(1), false)
  assert.equal(h.requests.length, 1)
})

test('跨三页往返始终显示目标页，未拍发页显示报底', async () => {
  const h = setup(page => ({code: 200, data: {messageVO: [row(page, null, null)]}}), 3)
  for (const [direction, expected] of [[1, 2], [1, 3], [-1, 2], [-1, 1], [1, 2]]) {
    assert.equal(await h.state.switchTelegram(direction), false)
    assert.equal(h.state.scoreData.value.currPage, expected)
    assert.equal(h.state.scoreData.value.content[0].pageNumber, expected)
    assert.equal(h.state.scoreData.value.content[0].key.join(''), '1')
    assert.equal(h.state.scoreData.value.content[0].omission, true)
  }
  assert.deepEqual(h.requests.map(request => request.pageNumber), [2, 3, 2, 1, 2])
})

for (const failure of ['business', 'network']) {
  test(`翻页${failure}失败保留当前页，解除加载并允许重试`, async () => {
    let fail = true
    const h = setup(page => {
      if (fail) {
        if (failure === 'network') throw new Error('网络异常')
        return {code: 202, message: '获取报底失败'}
      }
      return {code: 200, data: {messageVO: [row(page)]}}
    })
    const previous = h.state.scoreData.value.content
    assert.equal(await h.state.switchTelegram(1), false)
    assert.equal(h.state.scoreData.value.currPage, 1)
    assert.equal(h.state.scoreData.value.content, previous)
    assert.equal(h.state.loading.value, false)
    assert.equal(h.errors.length, 1)
    fail = false
    assert.equal(await h.state.switchTelegram(1), false)
    assert.equal(h.state.scoreData.value.currPage, 2)
  })
}

test('连续点击下一页只发起一次请求，响应前保留原页', async () => {
  let resolve
  const h = setup(() => new Promise(done => { resolve = done }))
  const pending = h.state.switchTelegram(1)
  assert.equal(await h.state.switchTelegram(1), false)
  assert.equal(h.requests.length, 1)
  assert.equal(h.state.scoreData.value.currPage, 1)
  resolve({code: 200, data: {messageVO: [row(2)]}})
  assert.equal(await pending, false)
  assert.equal(h.state.scoreData.value.currPage, 2)
})

test('通过实际防抖指令连续点击上下页，每次完成后按钮均可再次点击', async () => {
  const main = readFileSync(new URL('../src/main.js', import.meta.url), 'utf8')
  const start = main.indexOf("app.directive('debounce',")
  const end = main.indexOf("app.component('ButtonStyle'", start)
  let directive
  vm.runInNewContext(main.slice(start, end), {
    app: {directive: (name, value) => { directive = value }}, setTimeout, clearTimeout
  })
  const h = setup(page => ({code: 200, data: {messageVO: [row(page)]}}), 3)
  const buttons = new Map()
  for (const direction of [-1, 1]) {
    const element = {addEventListener(name, callback) { this.click = callback }}
    directive.mounted(element, {value: {fn: h.state.switchTelegram, data: direction}})
    buttons.set(direction, element)
  }
  for (const [direction, expected] of [[1, 2], [1, 3], [-1, 2], [-1, 1], [1, 2], [1, 3]]) {
    buttons.get(direction).click()
    await new Promise(resolve => setImmediate(resolve))
    assert.equal(h.state.scoreData.value.currPage, expected, '成功翻页后按钮必须解除点击锁')
    assert.equal(h.state.scoreData.value.content[0].pageNumber, expected)
  }
  assert.deepEqual(h.requests.map(request => request.pageNumber), [2, 3, 2, 1, 2, 3])
})
