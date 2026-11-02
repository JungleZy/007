import assert from 'node:assert/strict'
import {readFileSync} from 'node:fs'
import test from 'node:test'
import vm from 'node:vm'
import {parse, compileScript} from '@vue/compiler-sfc'
import {computed, createRenderer, nextTick, onBeforeUnmount, onMounted, ref} from 'vue'
import * as keys from '../src/views/manage/preJob/telegram/examTrain/js/keyCode.js'
import {createMorseController} from '../src/common/utils/morseController.js'
import {calculateTiming} from '../src/common/utils/voice/MorseVoiceHighPerformance.js'

// 执行实际页面 setup 和实际播报控制器，仅替换设备、时钟、HTTP 与音频输出边界。
const filename = new URL('../src/views/manage/preJob/telegram/examTrain/ExamBasicTrain.vue', import.meta.url)
const {descriptor} = parse(readFileSync(filename, 'utf8'))
const compiled = compileScript(descriptor, {id: 'electronic-basic-rate'}).content
const defaults = Object.fromEntries([...compiled.matchAll(/^\s*import (\w+) from /gm)].map(match => [match[1], '']))
const source = compiled.replace(/^\s*import .*$/gm, '').replace('export default', 'globalThis.component =')
const renderer = createRenderer({createComment: () => ({}), insert() {}, remove() {}, parentNode: () => null, nextSibling: () => null})

async function openPractice(t, type) {
  const commands = []
  const timers = new Map()
  let time = 10000, timerId = 0, keyHandler
  const controller = createMorseController({operation: command => { commands.push(command); return true }})
  const anime = () => {}
  anime.stagger = () => 0
  const context = vm.createContext({
    ...defaults, ...keys, computed, ref, onMounted, onBeforeUnmount,
    useRoute: () => ({query: {type}}), useRouter: () => ({go() {}}),
    useControl: () => ({...controller, onKey: handler => { keyHandler = handler }}),
    createFromIconfontCN: () => ({}), window: {interfaceStyle: 'HJ', iconUrl: '', fileUrl: ''},
    performance: {now: () => time}, anime, console,
    setInterval: callback => { timers.set(++timerId, callback); return timerId },
    clearInterval: id => timers.delete(id),
    getExamBasicTrain: async () => ({code: 200, data: {id: 'practice', totalTime: 0, totalNum: 0, totalError: 0}}),
    clearExamBasicTrain: async () => ({code: 200}),
    saveExamBasicTrain: async () => ({code: 200}),
    message: {success() {}}, audioOperation: () => true, partTimeFormatInfo: () => '',
  })
  vm.runInContext(source, context)
  const app = renderer.createApp({...context.component, render: () => null})
  app.mount({})
  t.after(() => app.unmount())
  await nextTick()
  commands.length = 0
  const page = app._instance.setupState
  return {
    page,
    commands,
    press(code, elapsed) { time = 10000 + elapsed; keyHandler({code: String(code)}) },
    tick(elapsed) { time = 10000 + elapsed; for (const callback of timers.values()) callback() }
  }
}

for (const type of [0, 1]) {
  test(`${type === 0 ? '基础练习' : '单字练习'}：零时长显示零码率，当前拍发先按码/分调速再播音`, async t => {
    const {page, commands, press, tick} = await openPractice(t, type)
    assert.equal(page.currentRate, 0)
    press(21, 500)
    assert.equal(page.currentRate, 120, '0.5 秒拍发一码 = 120 码/分')
    assert.deepEqual(commands.map(command => command.type), ['configure', 'message'])
    assert.equal(commands[0].data.criterion, calculateTiming({rate: 120, unit: 'characters', type: 'short'}).criterion)
    commands.length = 0
    press(21, 750)
    assert.equal(page.currentRate, 160, '0.75 秒累计两码 = 160 码/分')
    assert.deepEqual(commands.map(command => command.type), ['configure', 'addCode'])
    assert.equal(commands[0].data.criterion, calculateTiming({rate: 160, unit: 'characters', type: 'short'}).criterion)
    commands.length = 0
    tick(1500)
    assert.equal(page.currentRate, 80, '停顿后页面与音频一起更新实时码率')
    assert.equal(commands[0].data.criterion, calculateTiming({rate: 80, unit: 'characters', type: 'short'}).criterion)
  })
}

test('单字练习切换字码时，字母播音使用当前码率和字母时值', async t => {
  const {page, commands, press} = await openPractice(t, 1)
  page.selectTab(1)
  const letter = page.letterText
  const strokes = [...page.letter]
  commands.length = 0
  strokes.forEach((stroke, index) => press(stroke.coding, (index + 1) * 500))
  assert.equal(page.currentRate, 120)
  const voice = commands.find(command => command.type === 'message')
  assert.equal(voice.data.numType, 'letter')
  assert.equal(voice.data.data, letter)
  assert.equal(commands[0].data.criterion, calculateTiming({rate: 120, unit: 'characters', type: 'letter'}).criterion)
})

test('清空后时长与码率立即归零，下一次拍发重新计速', async t => {
  const {page, press} = await openPractice(t, 0)
  press(21, 500)
  page.clearTrainHistory()
  await nextTick()
  assert.equal(page.dataTime, 0)
  assert.equal(page.currentRate, 0)
  press(21, 1000)
  assert.equal(page.currentRate, 120)
})
