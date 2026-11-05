// 将模拟器实际输出帧送入线路通报的生产composable，验证报底页码与采集页码。
import assert from 'node:assert/strict'
import {spawnSync} from 'node:child_process'
import {readFileSync} from 'node:fs'
import {fileURLToPath} from 'node:url'
import vm from 'node:vm'
import {createRequire} from 'node:module'
import {createLineHandControl} from '../../bw-frontend/frontend/src/views/manage/unionJob/lineNotify/js/handControl.js'
import {decodePatRecord} from '../../bw-frontend/frontend/src/views/manage/unionJob/lineNotify/js/decodePatRecord.js'

const {ref} = createRequire(new URL('../../bw-frontend/frontend/package.json', import.meta.url))('vue')
const binary = process.argv[2] || fileURLToPath(new URL(`../target/debug/keysim${process.platform === 'win32' ? '.exe' : ''}`, import.meta.url))
const source = readFileSync(new URL('../../bw-frontend/frontend/src/views/manage/unionJob/lineNotify/js/Issue.js', import.meta.url), 'utf8')
  .replace(/^import .*$/gm, '').replace('export default function', 'function')
const settle = async () => { for (let index = 0; index < 20; index++) await Promise.resolve() }

for (const scenario of [
  {groups: 100, configured: 200, tail: 'none', style: 'machine', expectedPage: 2, finished: false},
  {groups: 100, configured: 200, tail: 'none', style: 'human', expectedPage: 2, finished: false},
  {groups: 101, configured: 101, tail: 'end', style: 'machine', expectedPage: 2, finished: true},
  {groups: 200, configured: 200, tail: 'end', style: 'machine', expectedPage: 2, finished: true},
  {groups: 201, configured: 201, tail: 'end', style: 'human', expectedPage: 3, finished: true, corrections: true}
]) {
  const args = ['hand', '--alphabet', 'short', '--random', String(scenario.groups), '--tail', scenario.tail,
    '--style', scenario.style, '--rate', '120', '--seed', '7', '--sink', 'frames']
  if (scenario.corrections) args.push('--correct-current', '--correct-previous')
  const result = spawnSync(binary, args, {encoding: 'utf8', maxBuffer: 32 * 1024 * 1024})
  assert.equal(result.status, 0, result.stderr)
  const frames = JSON.parse(result.stdout)
  let now = 0, nextTimer = 0, receive, finished = 0, userStatus = 0
  const timers = new Map(), requested = [], forwarded = []
  const setTimer = (fn, delay) => { timers.set(++nextTimer, {fn, at: now + delay}); return nextTimer }
  const advance = time => {
    while (true) {
      const due = [...timers.entries()].filter(([, timer]) => timer.at <= time).sort((a, b) => a[1].at - b[1].at)[0]
      if (!due) break
      timers.delete(due[0]); now = due[1].at; due[1].fn()
    }
    now = time
  }
  const state = ref({telegraph: {prev: 1, allPage: 0, curr: [], nextCurr: [], preCurr: []}, patCodeLog: [], currPatKeyIndex: -1})
  const context = vm.createContext({ref, onMounted() {}, onUnmounted() {}, nextTick: fn => Promise.resolve().then(fn),
    decodePatRecord, createLineHandControl: options => createLineHandControl({...options, setTimer, clearTimer: id => timers.delete(id)}),
    useRoute: () => ({query: {id: 'test-room', roomState: 'send'}}),
    localStorage: {getItem: () => JSON.stringify({id: 'sender'})}, document: {hidden: false},
    setTimeout: () => 1, clearTimeout() {}, setInterval: () => 1, clearInterval() {},
    SocketConnection: class {send(value) { forwarded.push(JSON.parse(value)) } close() {}}, message: {error() {}, success() {}}, Modal: {},
    useTraffic: (voice, callback) => { receive = callback },
    usePatRecord: () => ({restore: async () => {}, recordReady: ref(true), recordError: ref(''),
      append: stroke => state.value.patCodeLog.push(stroke), flush: async () => true}),
    getRoomUserList: async () => ({code: 200, data: {sendUserList: [{id: 'sender'}], receiveUserList: []}}),
    apiSimulationRouterRoomDetail: async () => ({code: 200, data: {stats: 1, bwCount: scenario.configured, bwType: 1, currentUserStatus: userStatus}}),
    apiSimulationRouterFindPage: async ({pageNumber}) => {
      requested.push(pageNumber)
      return {code: 200, data: {pageVos: Array.from({length: Math.min(100, scenario.configured - (pageNumber - 1) * 100)}, (_, index) => ({key: `${pageNumber}:${index}`}))}}
    },
    apiSimulationRouterSendFinish: async () => { finished++; userStatus = 1; return {code: 200, data: {currentUserStatus: 1}} }
  })
  vm.runInContext(source, context)
  const page = context.issue(state, ref(null))
  await page.getApiRoomUserList()
  for (const {frame} of frames) {
    advance(frame.d)
    receive({...frame, receivedAt: now})
    await settle()
  }
  advance(now + 2000)
  await settle()
  assert.equal(state.value.telegraph.prev, scenario.expectedPage, JSON.stringify(scenario))
  assert.equal(state.value.telegraph.curr[0].key, `${scenario.expectedPage}:0`)
  assert.equal(finished, Number(scenario.finished))
  assert.equal(page.recoveryError.value, '')
  assert.ok(requested.every(number => number >= 1 && number <= Math.ceil(scenario.configured / 100)))
  if (!scenario.finished) {
    assert.equal(state.value.patCodeLog.length, frames.filter(({frame}) => frame.k === 1).length)
    assert.equal(forwarded.length, frames.length)
  } else if (scenario.groups > 100) {
    assert.ok(state.value.patCodeLog.some(stroke => stroke.pageNumber === scenario.expectedPage))
  }
  console.log(`${scenario.groups}组/${scenario.style}/${scenario.tail}${scenario.corrections ? '/双改错' : ''}：报底第${state.value.telegraph.prev}页，结束${finished}次，实际帧处理通过`)
}
