import assert from 'node:assert/strict'
import {readFileSync} from 'node:fs'
import test from 'node:test'
import vm from 'node:vm'
import {ref} from 'vue'
import {createElectronicAudioTempo} from '../src/common/utils/electronicAudioTempo.js'
import {createMorseController} from '../src/common/utils/morseController.js'
import {calculateTiming} from '../src/common/utils/voice/MorseVoiceHighPerformance.js'
import {codeInKey, codeOnKey} from '../src/views/manage/postJob/telegram/examTrain/js/keyCode.js'

test('最近正文节奏不把开训等待算成慢速，停顿和换页后重新采样', () => {
  const tempo = createElectronicAudioTempo()
  assert.equal(tempo.record(30000), null)
  assert.equal(tempo.record(30200), 300)
  assert.equal(tempo.record(30400), 300)
  assert.equal(tempo.record(50000), null)
  assert.equal(tempo.record(50300), 200)
  tempo.reset()
  assert.equal(tempo.record(60000), null)
  assert.equal(tempo.record(60400), 150)
})

test('重复与倒序时刻不产生无穷播速，旧的慢速样本逐步退出窗口', () => {
  const tempo = createElectronicAudioTempo()
  tempo.record(0);tempo.record(1000)
  assert.equal(tempo.record(1000), null)
  assert.equal(tempo.record(999), null)
  let rate
  for (let i = 1; i <= 8; i++) rate = tempo.record(1000 + i * 100)
  assert.equal(rate, 600)
})

function openPersonal() {
  const commands = [], timers = []
  let receive, time = 0
  const controller = createMorseController({operation: command => {commands.push(command); return true}})
  const source = readFileSync(new URL('../src/views/manage/postJob/telegram/examTrain/js/examTrain.js', import.meta.url), 'utf8')
    .replace(/import\s+[\s\S]*?from\s+['"][^'"]+['"];?/g, '').replace('export default function ()', 'function createPage()')
  const context = vm.createContext({ref, codeInKey, codeOnKey, createElectronicAudioTempo,
    onMounted() {}, onUnmounted() {}, nextTick: fn => fn(),
    useRoute: () => ({query: {}}), useRouter: () => ({getRoutes: () => []}),
    PubSub: {subscribe() {}}, message: {error: text => {throw new Error(text)}},
    useControl: () => ({...controller, wsOnline: ref(true), devOnline: ref(true), onKey: fn => {receive = fn}}),
    useConfirmedSubmission: () => ({pending: () => false, defer: () => false, error: ref(null), busy: ref(false)}),
    useTrainingCapture: () => ({stamp: (start, end) => ({startedMs: start, endedMs: end}),
      open() {}, elapsed: () => time, between: (start, end) => Math.max(0, end - start)}),
    setInterval: fn => {timers.push(fn); return timers.length}, clearInterval() {},
    setTimeout: () => 1, clearTimeout() {}
  })
  vm.runInContext(source, context)
  const state = context.createPage()
  Object.assign(state.trainData.value, {status: 1, messageType: 0, content: [{key: ['1','2','3','4'], value: [], time: [], pageNumber: 1}]})
  state.patKeyBoxRef.value = {};state.patValBoxRef.value = {}
  state.playSpeed.value = 70;state.changePlaySpeed();commands.length = 0
  return {state, commands, press: (code, at) => {time = at;receive({code, receivedAt: at})}}
}

test('个人岗位等候30秒后快拍，首字保留设定播速，第二字先调速再播报', () => {
  const h = openPersonal()
  h.press('44', 20000)
  h.press('21', 30000)
  assert.deepEqual(h.commands.map(command => command.type), ['message'])
  assert.equal(h.state.playSpeed.value, 70, '不被约2码/分的累计均速拖慢')
  h.commands.length = 0
  h.press('22', 30200)
  assert.deepEqual(h.commands.map(command => command.type), ['configure', 'addCode'])
  assert.equal(h.commands[0].data.criterion, calculateTiming({rate: 300, unit: 'characters', type: 'short'}).criterion)
  assert.equal(h.state.playSpeed.value, 300)
  assert.ok(h.state.trainData.value.speed * 4 < 5, '成绩显示仍保持累计采集口径')
})

test('控制键不会按累计低速重新配置音频，长停顿后首字保持播速', () => {
  const h = openPersonal()
  h.press('44', 20000);h.press('21', 30000);h.press('22', 30200)
  h.commands.length = 0
  h.press('12', 40000)
  assert.equal(h.commands.length, 0)
  h.press('23', 50000)
  assert.equal(h.state.playSpeed.value, 300)
  assert.equal(h.commands.some(command => command.type === 'configure'), false)
  h.press('24', 50300)
  assert.equal(h.state.playSpeed.value, 200)
})

test('电子键组训同样在当前正文播报前调速，下一页开始重新采样', async () => {
  let time = 0
  const commands = []
  const controller = createMorseController({operation: command => {commands.push(command);return true}})
  controller.changeCriterion(70, 'short', 'characters');commands.length = 0
  const source = readFileSync(new URL('../src/views/manage/organization/electronKeyZuXun/train/student/js/handKeyTrain.js', import.meta.url), 'utf8')
    .replace(/import\s+[\s\S]*?from\s+['"][^'"]+['"];?/g, '').replace('export default function (', 'function createPage(')
  const context = vm.createContext({ref, codeInKey, codeOnKey, createElectronicAudioTempo,
    watch() {}, onMounted() {}, onUnmounted() {}, nextTick: fn => fn(),
    useRouter: () => ({getRoutes: () => []}), localStorage: {getItem: () => '{"id":"student"}'},
    PublicSocket: () => ({ws_connect() {}, sendMessage() {}, closeWebSocket() {}}),
    message: {error: text => {throw new Error(text)}},
    useConfirmedSubmission: () => ({pending: () => false, defer: (fn, first) => {if(first) fn();return false},
      error: ref(null), busy: ref(false), run: async fn => {await fn(action => action({}));return true}}),
    startTrainUser: async () => ({code: 200}),
    useTrainingCapture: () => ({metadata: ref({attempt: 0}), stamp: (start, end) => ({startedMs: start, endedMs: end}),
      open() {}, close() {}, recordQueued() {}, elapsed: () => time, between: (start, end) => Math.max(0, end - start)}),
    setTimeout: () => 1, clearTimeout() {}
  })
  vm.runInContext(source, context)
  const data = ref({status: 1, process: 1, floorNow: 1, messageType: 0, patKeyVal: [], patCodeLog: [],
    telegraph: [[{key: ['1','2','3','4'], value: [], time: [], pageNumber: 1}]]})
  const state = context.createPage(data, ref(true), ref(true), ref(false), () => {}, controller.voiceCode, controller.changeCriterion)
  state.patValBoxRef.value = {};state.patKeyBoxRef.value = {}
  const press = async (code, at) => {time = at;await state.handleReceiveKeyCode(code, at)}
  await press('44', 20000);await press('21', 30000)
  assert.deepEqual(commands.map(c => c.type), ['message'])
  commands.length = 0
  await press('22', 30200)
  assert.deepEqual(commands.map(c => c.type), ['configure', 'addCode'])
  assert.equal(commands[0].data.criterion, calculateTiming({rate: 300, unit: 'characters', type: 'short'}).criterion)
  data.value.process = 1
  await press('44', 31000)
  commands.length = 0
  await press('23', 31100)
  assert.equal(commands.some(c => c.type === 'configure'), false, '新页首字不沿用跨页间隔估算速度')
})
