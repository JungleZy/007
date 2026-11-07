import assert from 'node:assert/strict'
import {readFileSync} from 'node:fs'
import test from 'node:test'
import vm from 'node:vm'
import {ref, computed} from 'vue'

// 电子键组训教员页：教员结束训练后进入收尾补交（status=3），须照常加载已出成绩，
// 不能因只在 status=2 拉统计而整块空白（与手键组训 handkeyTeacherStatistics 同口径）。
const source = readFileSync(new URL('../src/views/manage/organization/electronKeyZuXun/train/teacher/js/teacher.js', import.meta.url), 'utf8')
  .replace(/import\s+[\s\S]*?from\s+['"][^'"]+['"];?/g, '')
  .replace('export default function ()', 'function createTeacher()')

function setup() {
  let status = 3, count = 1, fail = false, calls = 0
  const charts = []
  const context = vm.createContext({
    ref, computed, onMounted() {}, onUnmounted() {}, onBeforeUnmount() {}, nextTick: callback => callback(),
    useRoute: () => ({query: {id: '12'}}), localStorage: {getItem: () => '{"id":"teacher"}'}, window: {},
    message: {error() {}, success() {}},
    hasTrainingSubmission: user => user?.existPageNumber > 0,
    PublicSocket: () => ({ws_connect() {}, sendMessage() {}, closeWebSocket() {}}),
    PubSub: {subscribe() {}, publish() {}}, wsCode: {},
    document: {getElementById: () => ({removeAttribute() {}, addEventListener() {}})},
    echarts: {init: () => ({setOption: option => charts.push(option)})},
    getElectronKeyZuXunDetails: async () => ({code: 200, data: {status, validTime: 20,
      userInfoList: [
        {userId: 'student', existPageNumber: 1, isFinish: 1, score: 38},
        {userId: 'absent', existPageNumber: 0, isFinish: 0, score: 7}]}}),
    resetElectronKeyZuXunStatistics: async () => {
      calls++
      if (fail) throw new Error('网络异常')
      return {code: 200, data: {schoolReport: {belowStandard: {peopleNumber: count},
        nice: {peopleNumber: 0}, good: {peopleNumber: 0}},
        errorCollect: {errorNumber: 1, lackNumber: 0, moreNumber: 0, lackLineNumber: 0, moreLineNumber: 0,
          lackGroupNumber: 0, moreGroupNumber: 0},
        userTendencyVO: count ? [{userId: 'student', userName: '学员', thisScore: 38}] : []}}
    }
  })
  vm.runInContext(source, context)
  const state = context.createTeacher()
  state.trainTimeRef.value = {}
  return {state, charts, calls: () => calls, status: value => {status = value},
    count: value => {count = value}, fail: value => {fail = value}}
}

test('收尾补交状态进入教员页即加载已出成绩，刷新与最终结算不累加趋势', async () => {
  const h = setup()
  await h.state.refreshSettlement()
  assert.equal(h.calls(), 1, 'status=3 也要请求统计')
  assert.equal(h.state.hasStatistics.value, true)
  assert.equal(h.charts[0].series[0].data[0].value, 1)
  await h.state.refreshSettlement()
  h.state.getChartDataSource(3)
  assert.deepEqual(Array.from(h.charts.at(-1).series[0].data), [38])
  h.status(2)
  await h.state.refreshSettlement()
  assert.deepEqual(Array.from(h.charts.at(-1).series[0].data), [38])
  assert.equal(h.state.trainData.value.userInfoList.find(user => user.userId === 'absent').score, 7,
    '未进入趋势的人员保留原成绩字段，不被清空')
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
