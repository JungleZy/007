import assert from 'node:assert/strict'
import {readFileSync} from 'node:fs'
import vm from 'node:vm'
import test from 'node:test'
import {ref} from 'vue'
import {createLineHandControl} from '../src/views/manage/unionJob/lineNotify/js/handControl.js'
import {decodePatRecord} from '../src/views/manage/unionJob/lineNotify/js/decodePatRecord.js'

const source = readFileSync(new URL('../src/views/manage/unionJob/lineNotify/js/Issue.js', import.meta.url), 'utf8')
  .replace(/^import .*$/gm, '').replace('export default function', 'function')
const settle = async () => { for (let index = 0; index < 15; index++) await Promise.resolve() }

function clock() {
  let now = 0, next = 0
  const timers = new Map()
  return {
    get now() { return now },
    setTimer: (fn, delay) => { timers.set(++next, {fn, at: now + delay}); return next },
    clearTimer: id => timers.delete(id),
    advance(ms) {
      const end = now + ms
      while (true) {
        const due = [...timers.entries()].filter(([, timer]) => timer.at <= end).sort((a, b) => a[1].at - b[1].at)[0]
        if (!due) break
        timers.delete(due[0]); now = due[1].at; due[1].fn()
      }
      now = end
    }
  }
}

function sender(time, receive) {
  return {
    word(bits, gap = 210) {
      time.advance(gap)
      for (let index = 0; index < bits.length; index++) {
        if (index) time.advance(42)
        receive({t: 0, k: 0, d: time.now, receivedAt: time.now})
        time.advance(bits[index] === '0' ? 42 : 126)
        receive({t: 0, k: 1, d: time.now, receivedAt: time.now})
      }
    },
    turn() { for (let index = 0; index < 3; index++) this.word('00') }
  }
}

test('完整三截分页符在末尾静默后只触发一次，普通字母I不被误当分页', () => {
  const time = clock(), controls = [], strokes = []
  let page = 1
  const decoder = createLineHandControl({...time, getPage: () => page, onStroke: stroke => strokes.push(stroke),
    onControl: action => { controls.push(action); if (action === 'turn') page++ }})
  const input = sender(time, frame => decoder.receive(frame))
  input.word('10001', 0)
  input.word('00'); input.word('00', 126); input.word('00', 126)
  time.advance(210)
  assert.equal(controls.filter(action => action === 'turn').length, 0)
  input.turn()
  assert.equal(page, 1)
  time.advance(210)
  assert.equal(page, 2)
  time.advance(1000)
  assert.equal(controls.filter(action => action === 'turn').length, 1)
  assert.equal(strokes.at(-1).pageNumber, 1)
  input.word('01')
  assert.equal(strokes.at(-1).pageNumber, 2)
  decoder.reset()
})

test('两截分页符和重置后的残留信号不能导致翻页', () => {
  const time = clock(), controls = []
  const decoder = createLineHandControl({...time, getPage: () => 1, onStroke() {}, onControl: action => controls.push(action)})
  const input = sender(time, frame => decoder.receive(frame))
  input.word('10001', 0)
  input.word('00'); input.word('00'); time.advance(210)
  assert.equal(controls.includes('turn'), false)
  decoder.reset()
  input.word('00'); time.advance(210)
  assert.equal(controls.includes('turn'), false)
})

async function harness({pages = 2, deferSecond = false, failSecond = false} = {}) {
  const time = clock(), requests = []
  let receive, releaseSecond, fail = failSecond, finishCalls = 0, userStatus = 0
  const data = page => ({code: 200, data: {pageVos: Array.from({length: 100}, (_, index) => ({key: `${page}:${index}`}))}})
  const state = ref({telegraph: {prev: 1, allPage: 0, curr: [], nextCurr: [], preCurr: []}, patCodeLog: [], currPatKeyIndex: -1})
  const context = vm.createContext({ref, onMounted() {}, onUnmounted() {}, nextTick: fn => Promise.resolve().then(fn),
    decodePatRecord, createLineHandControl: options => createLineHandControl({...options, setTimer: time.setTimer, clearTimer: time.clearTimer}),
    useRoute: () => ({query: {id: '87', roomState: 'send'}}),
    localStorage: {getItem: () => JSON.stringify({id: 'sender'})}, document: {hidden: false},
    setTimeout: () => 1, clearTimeout() {}, setInterval: () => 1, clearInterval() {},
    SocketConnection: class {send() {} close() {}}, message: {error() {}, success() {}}, Modal: {},
    useTraffic: (voice, handler) => { receive = handler },
    usePatRecord: () => ({restore: async () => {}, recordReady: ref(true), recordError: ref(''),
      append: stroke => state.value.patCodeLog.push(stroke), flush: async () => true}),
    getRoomUserList: async () => ({code: 200, data: {sendUserList: [{id: 'sender'}], receiveUserList: []}}),
    apiSimulationRouterRoomDetail: async () => ({code: 200, data: {stats: 1, bwCount: pages * 100, bwType: 1, currentUserStatus: userStatus, totalTime: 0}}),
    apiSimulationRouterFindPage: async params => {
      requests.push(params.pageNumber)
      if (params.pageNumber === 2 && deferSecond) return new Promise(resolve => { releaseSecond = () => resolve(data(2)) })
      if (params.pageNumber === 2 && fail) return {code: 500, message: '下一页暂不可用'}
      return data(params.pageNumber)
    },
    apiSimulationRouterSendFinish: async () => { finishCalls++; userStatus = 1; return {code: 200, data: {currentUserStatus: 1}} }
  })
  vm.runInContext(source, context)
  const page = context.issue(state, ref(null))
  await page.getApiRoomUserList()
  await settle()
  return {time, page, state, requests, input: sender(time, frame => receive(frame)),
    release: () => releaseSecond(), allowSecond: () => { fail = false }, get finishCalls() { return finishCalls }}
}

test('拍发端收到分页符后切换下一页报底，分页符留在旧页记录', async () => {
  const h = await harness()
  h.input.word('10001', 0)
  h.input.turn(); h.time.advance(210)
  await settle()
  assert.equal(h.state.value.telegraph.prev, 2)
  assert.equal(h.state.value.telegraph.curr[0].key, '2:0')
  assert.equal(h.state.value.patCodeLog.at(-1).pageNumber, 1)
  h.input.word('01')
  assert.equal(h.state.value.patCodeLog.at(-1).pageNumber, 2)
  assert.equal(h.finishCalls, 0)
  assert.equal(h.requests.filter(page => page === 2).length, 1, '复用已预取的下一页报底')
})

test('下一页请求未返回时仍持续采集，并把后续按键记在下一页', async () => {
  const h = await harness({deferSecond: true})
  h.input.word('10001', 0)
  h.input.turn(); h.time.advance(210)
  h.input.word('01')
  assert.equal(h.state.value.telegraph.prev, 1, '成功读取之前保留当前报底')
  assert.equal(h.state.value.patCodeLog.at(-1).pageNumber, 2)
  h.release()
  await settle()
  assert.equal(h.state.value.telegraph.prev, 2)
  assert.equal(h.state.value.telegraph.curr[0].key, '2:0')
  assert.equal(h.requests.filter(page => page === 2).length, 1)
})

test('翻页请求失败后可恢复目标页，不丢后续拍发记录', async () => {
  const h = await harness({failSecond: true})
  h.input.word('10001', 0)
  h.input.turn(); h.time.advance(210)
  await settle()
  assert.match(h.page.recoveryError.value, /下一页/)
  assert.equal(h.state.value.telegraph.prev, 1)
  h.input.word('01')
  assert.equal(h.state.value.patCodeLog.at(-1).pageNumber, 2)
  h.allowSecond()
  await h.page.getApiRoomUserList()
  assert.equal(h.state.value.telegraph.prev, 2)
  assert.equal(h.page.recoveryError.value, '')
})

test('普通组间隔不会擅自翻页或结束，末页完整分页符只结束一次', async () => {
  const h = await harness({pages: 1})
  h.input.word('10001', 0)
  for (let index = 0; index < 101; index++) h.input.word('01')
  h.time.advance(210)
  await settle()
  assert.equal(h.finishCalls, 0)
  assert.equal(h.state.value.telegraph.prev, 1)
  h.input.turn(); h.time.advance(210)
  await settle()
  assert.equal(h.finishCalls, 1)
  h.input.word('01010'); h.time.advance(210)
  await settle()
  assert.equal(h.finishCalls, 1)
})
