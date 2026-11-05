import assert from 'node:assert/strict'
import test from 'node:test'
import {handKeyGroupCount} from '../src/common/utils/handKeyGroupCount.js'

const rule = {other: {quantoGroup: {l: 15, max: 60}}}
const page = amounts => ({deductionEvidence: amounts.map(amount => ({deductions: {quantoGroup: amount}}))})

test('旧成绩有两处15分少组依据，数量从0恢复为2', () => {
  assert.equal(handKeyGroupCount({quantoGroup: 30, quantoGroupNumber: 0}, rule, [page([]), page([15, 15])], 2), 2)
})
test('封顶后仍按原始依据计算6组，不按60分反推4组', () => {
  assert.equal(handKeyGroupCount({quantoGroup: 60, quantoGroupNumber: 0}, rule, [page([45, 45])], 1), 6)
})
test('缺页、旧记录无依据或金额不一致时不猜算数量', () => {
  for (const records of [[page([30])], [null, page([30])], [page([]), page([15])]]) {
    assert.equal(handKeyGroupCount({quantoGroup: 30, quantoGroupNumber: 0}, rule, records, 2), '--')
  }
})
test('正确的新成绩数量与真实零数量直接展示', () => {
  assert.equal(handKeyGroupCount({quantoGroup: 60, quantoGroupNumber: 7}, rule, [], 2), 7)
  assert.equal(handKeyGroupCount({quantoGroup: 0, quantoGroupNumber: 0}, rule, [], 2), 0)
})
