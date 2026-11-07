import assert from 'node:assert/strict'
import {readFileSync} from 'node:fs'
import test from 'node:test'
import vm from 'node:vm'
import {parse, compileScript} from '@vue/compiler-sfc'
import * as Vue from 'vue'
import {renderToString} from '@vue/server-renderer'

const {descriptor} = parse(readFileSync(new URL('../src/components/common/DatagramDeductionDetails.vue', import.meta.url), 'utf8'))
const compiled = compileScript(descriptor, {id: 'datagram-evidence', genDefaultAs: 'component', inlineTemplate: true})
const code = compiled.content.replace(/import \{([^}]+)\} from ['"]vue['"];?/g,
  (_, names) => `const {${names.replace(/\bas\b/g, ':')}} = Vue`)
const context = vm.createContext({Vue})
vm.runInContext(code + '\nglobalThis.component=component', context)
async function render(summary) {
  const app = Vue.createSSRApp(context.component, {summary})
  for (const name of ['a-button', 'a-modal']) app.component(name, {setup: (_, {slots}) => () => Vue.h('div', slots.default?.())})
  app.component('a-empty', {props: ['description'], setup: props => () => Vue.h('p', props.description)})
  return renderToString(app)
}

test('数据报依据展示页码、报底组号、判定与冻结单价计算', async () => {
  const html = await render({evidenceVersion: 1, errorCodeNumber: 1, errorCodeScore: '-7', muchLessGroupsNumber: 2,
    muchLessGroupsScore: '-6', speedScore: '+3', evidence: [
      {pageNumber: 1, kind: 'errorCode', referenceGroup: 6, reference: '0005', captured: 'AAAA', count: 1, unitDeduction: 7, deduction: 7},
      {pageNumber: 2, kind: 'muchLessGroups', referenceGroup: 20, reference: '0019 0020', captured: '', count: 2, unitDeduction: 3, deduction: 6}
    ]})
  for (const text of ['第 1 页', '报底第 6 组', '0005', 'AAAA', '错码 · 1 个', '1 × 7', '扣 7 分',
    '第 2 页', '报底第 20 组', '0019 0020', '未拍发', '多少组 · 2 组', '2 × 3', '扣 6 分', '速率加减分：+3 分'])
    assert.ok(html.includes(text), text)
})

test('汇总区沿用既有 deductInfo 键值展示九类扣分', async () => {
  const html = await render({evidenceVersion: 1, evidence: [], errorCodeNumber: 1, errorCodeScore: '-7',
    muchLessLineNumber: 2, muchLessLineScore: '-12', lessPageNumber: 1, lessPageScore: '-4',
    correctMistakesNumber: 3, correctMistakesScore: '-6'})
  for (const text of ['错码', '1 个', '-7 分', '多少行', '2 行', '-12 分', '少页标', '-4 分', '改错', '3 个', '-6 分'])
    assert.ok(html.includes(text), text)
})

test('历史记录与没有扣分的新记录有明确区分', async () => {
  assert.match(await render({errorCodeNumber: 1, errorCodeScore: '-7'}), /历史成绩未保存逐项扣分依据/)
  assert.match(await render({evidenceVersion: 1, evidence: []}), /所选范围没有扣分项/)
})
