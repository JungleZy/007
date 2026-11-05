import assert from 'node:assert/strict'
import {readFileSync} from 'node:fs'
import test from 'node:test'
import vm from 'node:vm'
import {parse, compileScript} from '@vue/compiler-sfc'
import {computed, createRenderer, h, nextTick, onMounted, ref} from 'vue'
import {alignResultPage} from '../src/common/utils/resultAlignment.js'

const {descriptor} = parse(readFileSync(new URL('../src/views/manage/unionJob/disturbCode/TrainResult.vue', import.meta.url), 'utf8'))
const source = compileScript(descriptor, {id: 'simulation-result-requests'}).content
  .replace(/^import .*$/gm, '').replace('export default', 'globalThis.component =')
const renderer = createRenderer({createComment: () => ({}), insert() {}, remove() {}, parentNode: () => null, nextSibling: () => null})

test('仿真训练详情与翻页直接展示报底答案，不拿房间ID查询个人收报报头', async t => {
  const requests = [], switches = []
  const context = vm.createContext({computed, ref, onMounted, alignResultPage,
    useRoute: () => ({query: {id: '82'}}),
    findHeader: async id => {requests.push(id); return {code: 200, data: null}}
  })
  vm.runInContext(source, context)
  const state = ref({curr: 1, result: {pageVos: [{key: '1234'}], value: ['1254']}})
  const Result = {...context.component, render: () => null}
  let result
  const app = renderer.createApp({setup: () => () => h(Result, {...state.value, all: 2,
    ref: instance => {result = instance}, onSwitchPage: direction => switches.push(direction)})})
  t.after(() => app.unmount())
  app.mount({})
  await nextTick()
  assert.deepEqual(requests, [], '房间82不是个人收报训练ID，不应发起masthead请求')
  const setup = () => result.$.setupState
  assert.equal(setup().displayRows[0][0].expected, '1234')
  assert.equal(setup().displayRows[0][0].actual, '1254')
  setup().pageTurn('next')
  assert.deepEqual(switches, ['next'])
  state.value = {curr: 2, result: {pageVos: [{key: '5678'}], value: ['5678']}}
  await nextTick()
  assert.equal(setup().displayRows[0][0].expected, '5678')
  assert.equal(setup().displayRows[0][0].actual, '5678')
  assert.deepEqual(requests, [])
})
