import assert from 'node:assert/strict'
import {readFileSync} from 'node:fs'
import test from 'node:test'
import vm from 'node:vm'
import {ref, computed} from 'vue'
import * as format from '../src/common/utils/studentMessageFormat.js'

// 组训人查看学员拍发报文：各教员页只读学员已提交的页，并把实时推送交给格式化工具。
function teacher(path, apis) {
  const source = readFileSync(new URL(path, import.meta.url), 'utf8')
    .replace(/import\s+[\s\S]*?from\s+['"][^'"]+['"];?/g, '')
    .replace('export default function ()', 'function createTeacher()')
  const context = vm.createContext({
    ref, computed, onMounted() {}, onUnmounted() {}, onBeforeUnmount() {}, nextTick: callback => callback(),
    useRoute: () => ({query: {id: '12'}}), localStorage: {getItem: () => '{"id":"teacher"}'}, window: {},
    message: {error() {}, success() {}}, hasTrainingSubmission: () => true,
    PublicSocket: () => ({ws_connect() {}, sendMessage() {}, closeWebSocket() {}}),
    PubSub: {subscribe() {}, publish() {}}, wsCode: {}, echarts: {init: () => ({setOption() {}})},
    document: {getElementById: () => ({removeAttribute() {}, addEventListener() {}})},
    ...format, ...apis
  })
  vm.runInContext(source, context)
  return context.createTeacher()
}

test('手键：只按最新详情的 existNumber 读取已提交页，未提交页不调用 findPage', async () => {
  const requested = []
  const state = teacher('../src/views/manage/organization/handkeyZuXun/train/teacher/js/teacher.js', {
    getHandKeyZuXunDetails: async () => ({code: 200, data: {userInfoList: [{userId: 's1', existNumber: [2, 1]}]}}),
    getHandKeyZuXunPageNumber: async ({floorNumber}) => {
      requested.push(floorNumber)
      return {code: 200, data: {submitted: true,
        messageBody: JSON.stringify([{patKeys: JSON.stringify([...`${floorNumber}234`])}])}}
    }
  })
  const pages = await state.loadSubmittedPages({id: 's1', log: []})
  assert.deepEqual(requested, [1, 2])
  assert.deepEqual(JSON.parse(JSON.stringify(pages)), [{pageNumber: 1, text: '1234'}, {pageNumber: 2, text: '2234'}])
})

test('电子键：逐页读取 getPatValue，跳过空页；实时按换组标记分组、句号后只留新一页', async () => {
  const requested = []
  const state = teacher('../src/views/manage/organization/electronKeyZuXun/train/teacher/js/teacher.js', {
    getElectronKeyZuXunPatValue: async ({pageNumber, userId}) => {
      requested.push([pageNumber, userId])
      return {code: 200, data: pageNumber === 1
        ? [{sort: 1, value: '["5","6","7","8"]'}, {sort: 0, value: '["开始","1","2","3","4"]'}] : []}
    }
  })
  state.trainData.value = {totalNumber: 150}
  const pages = await state.loadSubmittedPages({id: 's1'})
  assert.deepEqual(JSON.parse(JSON.stringify(requested)), [[1, 's1'], [2, 's1']])
  assert.deepEqual(JSON.parse(JSON.stringify(pages)), [{pageNumber: 1, text: '1234 5678'}])
  const log = ['1', '2', ' ', '句号', '开始', '9', '0', ' ', 'A'].map(key => ({key, time: 0}))
  assert.equal(state.liveMessage({log}), '90 A')
})
