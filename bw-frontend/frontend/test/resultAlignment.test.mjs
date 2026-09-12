import assert from 'node:assert/strict'
import test from 'node:test'
import {alignResultPage} from '../src/common/utils/resultAlignment.js'

test('占位与空白单元格不参与比对，比对结果仍指回原始单元格下标', () => {
  const {groups} = alignResultPage({pageVos: [{key: '1234'}, {key: '--'}, {key: '5678'}], value: ['1234', '', '5678']})
  assert.deepEqual(
    groups.map(group => [group.type, group.expected, group.actual, group.expectedIndex, group.actualIndex]),
    [['equal', '1234', '1234', 0, 0], ['equal', '5678', '5678', 2, 2]],
    '跳过占位格后，剩余组必须回指报底与报文中的原始下标'
  )
})

test('漏抄一组只判该组缺失，其后各组仍与原文对齐', () => {
  const {groups} = alignResultPage({pageVos: [{key: '1234'}, {key: '5678'}, {key: '9012'}], value: ['1234', '9012']})
  assert.deepEqual(
    groups.map(group => [group.type, group.expected, group.expectedIndex, group.actualIndex]),
    [['equal', '1234', 0, 0], ['delete', '5678', 1, null], ['equal', '9012', 2, 1]],
    '漏抄第二组后，第三组必须仍判为正确而不是整页错位'
  )
})

test('组内单字差异定位到该字符，不牵连同组其余字符', () => {
  const {groups} = alignResultPage({pageVos: [{key: '1234'}], value: ['1244']})
  assert.deepEqual(
    groups[0].characters.map(character => [character.type, character.expected, character.actual, character.expectedIndex]),
    [['equal', '1', '1', 0], ['equal', '2', '2', 1], ['replace', '3', '4', 2], ['equal', '4', '4', 3]],
    '仅第三位错抄时，其余三位必须仍判为正确'
  )
})
