import assert from 'node:assert/strict'
import {readFileSync} from 'node:fs'
import test from 'node:test'
import vm from 'node:vm'
import {parse, compileScript} from '@vue/compiler-sfc'
import * as Vue from 'vue'
import {renderToString} from '@vue/server-renderer'

const {descriptor} = parse(readFileSync(new URL('../src/components/common/ElectronicKeyDeductionDetails.vue', import.meta.url), 'utf8'))
const compiled = compileScript(descriptor, {id: 'electronic-evidence', genDefaultAs: 'component', inlineTemplate: true})
const code = compiled.content.replace(/import \{([^}]+)\} from ['"]vue['"];?/g,
  (_, names) => `const {${names.replace(/\bas\b/g, ':')}} = Vue`)
const context = vm.createContext({Vue})
vm.runInContext(code + '\nglobalThis.component=component', context)
async function render(summary, rule) {
  const app = Vue.createSSRApp(context.component, {summary, rule})
  for (const name of ['a-button', 'a-modal']) app.component(name, {setup: (_, {slots}) => () => Vue.h('div', slots.default?.())})
  app.component('a-empty', {props: ['description'], setup: props => () => Vue.h('p', props.description)})
  return renderToString(app)
}

test('电子键依据展示页码、组范围、数量及单价计算，不把缺行描述成错码', async () => {
  const html = await render({evidenceVersion: 1, lackLineNumber: 1, lackLineScore: '-10', speedScore: '-3', evidence: [
    {pageNumber: 2, referenceGroup: 31, referenceThrough: 40, reference: '1234 5678', captured: '', kind: 'lackLine', count: 1, unitDeduction: 10, deduction: 10}
  ]})
  for (const text of ['第 2 页', '报底第 31—40 组', '1234 5678', '未拍发', '少行 · 1 行', '1 × 10', '扣 10 分', '不再按错码计分']) assert.ok(html.includes(text), text)
})

test('两端串组只在首端显示扣分，另一端说明配对已计分', async () => {
  const html = await render({evidenceVersion: 1, evidence: [
    {pageNumber: 1, referenceGroup: 6, capturedGroup: 6, reference: '1234', captured: '5678', kind: 'bunchGroup', count: 1, partnerReferenceGroup: 16, unitDeduction: 10, deduction: 10},
    {pageNumber: 1, referenceGroup: 16, capturedGroup: 16, reference: '5678', captured: '1234', kind: 'bunchGroup', count: 0, partnerReferenceGroup: 6, unitDeduction: 10, deduction: 0}
  ]})
  assert.match(html, /报底第 16 组互换/)
  assert.match(html, /此处不重复扣分/)
  assert.equal(html.match(/扣 10 分/g).length, 1)
})

test('历史记录与没有扣分的新记录有明确区分', async () => {
  assert.match(await render({errorNumber: 1, errorScore: '-15'}), /历史成绩未保存逐组扣分依据/)
  assert.match(await render({evidenceVersion: 1, evidence: []}), /所选范围没有扣分项/)
})

test('码率依据以冻结单位比较实际值与基准，并说明历史四码组换算', async () => {
  const current = await render({scoringRateUnit: 'CHARACTERS_PER_MINUTE', scoringSpeedNumber: 236, speedScore: '+86'},
    {rateUnit: 'CHARACTERS_PER_MINUTE', wpm: {base: 150, r: 1, l: 1}})
  assert.match(current, /计分码率 236 码\/分，冻结基准 150 码\/分/)
  assert.match(current, /速率加减分：\+86 分/)
  const historical = await render({speedNumber: '59', speedScore: '-91'},
    {rateUnit: 'FOUR_CHARACTER_GROUPS_PER_MINUTE', wpm: {base: 150, r: 1, l: 1}})
  assert.match(historical, /计分码率 59 四码组\/分，冻结基准 150 四码组\/分/)
  assert.match(historical, /该基准相当于 600 码\/分/)
})
