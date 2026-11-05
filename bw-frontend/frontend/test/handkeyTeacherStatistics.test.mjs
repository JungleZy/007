import assert from 'node:assert/strict'
import {readFileSync} from 'node:fs'
import test from 'node:test'
import vm from 'node:vm'
import {ref, computed} from 'vue'

const source = readFileSync(new URL('../src/views/manage/organization/handkeyZuXun/train/teacher/js/teacher.js', import.meta.url), 'utf8')
  .replace(/import\s+[\s\S]*?from\s+['"][^'"]+['"];?/g, '')
  .replace('export default function ()', 'function createTeacher()')

function setup() {
  let status = 3, count = 1, fail = false, calls = 0
  const charts = []
  const context = vm.createContext({
    ref, computed, onMounted() {}, onUnmounted() {}, onBeforeUnmount() {}, nextTick: callback => callback(),
    useRoute: () => ({query: {id: '12'}}), localStorage: {getItem: () => '{"id":"teacher"}'}, window: {},
    hasTrainingSubmission: user => user?.existPageNumber > 0,
    PublicSocket: () => ({ws_connect() {}, sendMessage() {}, closeWebSocket() {}}),
    document: {getElementById: () => ({removeAttribute() {}})},
    echarts: {init: () => ({setOption: option => charts.push(option)})},
    getHandKeyZuXunDetails: async () => ({code: 200, data: {status, validTime: 20,
      userInfoList: [{userId: 'student', existPageNumber: 1, isFinish: 1, score: -44}]}}),
    resetHandKeyZuXunStatistics: async () => {
      calls++
      if (fail) throw new Error('网络异常')
      return {code: 200, data: {schoolReport: {belowStandard: {peopleNumber: count},
        nice: {peopleNumber: 0}, good: {peopleNumber: 0}}, errorInfoVO: {},
        userTendencyVO: count ? [{userId: 'student', userName: '学员', thisScore: -44}] : []}}
    }
  })
  vm.runInContext(source, context)
  const state = context.createTeacher()
  state.trainTimeRef.value = {}
  return {state, charts, calls: () => calls, status: value => {status = value},
    count: value => {count = value}, fail: value => {fail = value}}
}

test('待结算状态进入教员页加载已有成绩，刷新和最终结算不会累加趋势数据', async () => {
  const h = setup()
  await h.state.refreshSettlement()
  assert.equal(h.calls(), 1)
  assert.equal(h.state.hasStatistics.value, true)
  assert.equal(h.charts[0].series[0].data[0].value, 1)
  await h.state.refreshSettlement()
  h.state.getChartDataSource(3)
  assert.deepEqual(Array.from(h.charts.at(-1).series[0].data), [-44])
  h.status(2)
  await h.state.refreshSettlement()
  assert.deepEqual(Array.from(h.charts.at(-1).series[0].data), [-44])
})

test('没有已结算成绩时进入明确的空数据状态', async () => {
  const h = setup()
  h.count(0)
  await h.state.refreshSettlement()
  assert.equal(h.state.hasStatistics.value, false)
  assert.equal(h.state.statisticsLoading.value, false)
  assert.equal(h.charts.length, 0)
})

test('统计请求失败显示错误并可重新加载', async () => {
  const h = setup()
  h.fail(true)
  await h.state.refreshSettlement()
  assert.equal(h.state.statisticsError.value, '网络异常')
  assert.equal(h.state.statisticsLoading.value, false)
  h.fail(false)
  await h.state.refreshStatistics()
  assert.equal(h.state.statisticsError.value, '')
  assert.equal(h.state.hasStatistics.value, true)
  assert.equal(h.charts.length, 1)
})
