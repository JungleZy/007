import assert from 'node:assert/strict'
import {readFileSync} from 'node:fs'
import test from 'node:test'
import vm from 'node:vm'
import {parse, compileScript} from '@vue/compiler-sfc'
import * as Vue from 'vue'
import {renderToString} from '@vue/server-renderer'

const {descriptor} = parse(readFileSync(new URL('../src/components/common/HandKeyDeductionDetails.vue', import.meta.url), 'utf8'))
const compiled = compileScript(descriptor, {id: 'deduction-details', genDefaultAs: 'component', inlineTemplate: true})
const code = compiled.content.replace(/import \{([^}]+)\} from ['"]vue['"];?/g,
  (_, names) => `const {${names.replace(/\bas\b/g, ':')}} = Vue`)
const context = vm.createContext({Vue})
vm.runInContext(code + '\nglobalThis.component = component', context)

async function render(resolver) {
  const app = Vue.createSSRApp(context.component, {page: 2, resolver, summary: {quantoCode: 4, wpmScore: 0}})
  for (const name of ['a-button', 'a-modal']) app.component(name, {setup: (_, {slots}) => () => Vue.h('div', slots.default?.())})
  app.component('a-empty', {props: ['description'], setup: props => () => Vue.h('p', props.description)})
  return renderToString(app)
}

test('扣分依据显示页组定位、报底答案、封顶前扣分及实测时长', async () => {
  const html = await render({deductionEvidence: [{referenceGroup: 3, capturedGroup: 3, capturedThrough: 3,
    reference: '1234', captured: '123', deductions: {quantoCode: 3}}], timingEvidence: [{capturedGroup: 3,
    character: 2, signal: 1, item: 'dotMinScore', actualMillis: 160, minimumMillis: 70, maximumMillis: 130, deduction: 2}]})
  for (const expected of ['第 2 页', '报底第 3 组', '1234', '123', '多少码：3 分', '160 ms', '70—130 ms', '封顶前', '跳过缺失或多余位置后']) {
    assert.ok(html.includes(expected), expected)
  }
  assert.match(html, /4 <small>分<\/small>/)
})

test('旧成绩与无扣分的新成绩分别说明，不伪造定位依据', async () => {
  assert.match(await render(null), /历史成绩不会据现有规则重新推算/)
  assert.match(await render({deductionEvidence: [], timingEvidence: []}), /该页没有逐组扣分/)
})

test('串组明细列出配对位置，另一端提示不重复扣分', async () => {
  const html = await render({deductionEvidence: [
    {referenceGroup: 6, capturedGroup: 5, capturedThrough: 5, reference: '1234', captured: '5678', partnerReferenceGroup: 16, deductions: {bunchGroup: 10}},
    {referenceGroup: 16, capturedGroup: 15, capturedThrough: 15, reference: '5678', captured: '1234', partnerReferenceGroup: 6, deductions: {}}
  ]})
  assert.match(html, /与报底第 16 组串组/)
  assert.match(html, /与报底第 6 组串组/)
  assert.match(html, /此处不重复扣分/)
})
