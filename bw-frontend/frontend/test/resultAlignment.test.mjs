import assert from 'node:assert/strict'
import test from 'node:test'
import {alignResultPage} from '../src/common/utils/resultAlignment.js'

test('占位与空白单元格保留原始位置，不压缩报底和答案', () => {
  const {groups} = alignResultPage({pageVos: [{key: '1234'}, {key: '--'}, {key: '5678'}], value: ['1234', '', '5678']})
  assert.deepEqual(
    groups.map(group => [group.type, group.expected, group.actual, group.expectedIndex, group.actualIndex]),
    [['equal', '1234', '1234', 0, 0], ['empty', '', '', null, null], ['equal', '5678', '5678', 2, 2]],
    '空格仍占一格，第三格的答案不能前移到第二格'
  )
})

test('连续填写的错组保持填写位置，不能按相似内容挪到其它报底格', () => {
  const {groups} = alignResultPage({pageVos: [{key: '1234'}, {key: '5678'}, {key: '9012'}], value: ['1234', '9012']})
  assert.deepEqual(
    groups.map(group => [group.type, group.expected, group.expectedIndex, group.actualIndex]),
    [['equal', '1234', 0, 0], ['replace', '5678', 1, 1], ['delete', '9012', 2, null]],
    '填写在第二格的9012仍展示在第二格，不能移到第三格'
  )
})

test('各行零散填写的错误答案仍显示在原行原列，不集中到最后一行', () => {
  const pageVos = Array.from({length: 100}, (_, index) => ({key: String(1000 + index)}))
  const value = Array(100).fill('')
  const positions = [0, 4, 10, 23, 37, 52, 68, 81, 94]
  positions.forEach(index => { value[index] = 'ABCD' })
  const original = JSON.stringify({pageVos, value})
  const {groups} = alignResultPage({pageVos, value})
  assert.equal(groups.length, 100)
  assert.deepEqual(groups.flatMap((group, index) => group.actual ? [index] : []), positions)
  for (let index = 0; index < 100; index++) {
    assert.equal(groups[index].expected, pageVos[index].key)
    assert.equal(groups[index].actual, value[index])
    assert.equal(groups[index].type, positions.includes(index) ? 'replace' : 'delete')
  }
  assert.equal(JSON.stringify({pageVos, value}), original)
})

test('多余页中的空格及末尾答案保留位置，不向前聚拢', () => {
  const {groups, extraPage} = alignResultPage({pageVos: [], value: ['', '', '1234', '', '5678']})
  assert.equal(extraPage, true)
  assert.deepEqual(groups.map(group => group.actual), ['', '', '1234', '', '5678'])
  assert.deepEqual(groups.map(group => group.type), ['empty', 'empty', 'insert', 'empty', 'insert'])
})

test('省略的尾部答案和稀疏数组都按原格位置留空', () => {
  const value = []
  value[11] = '1234'
  const {groups} = alignResultPage({pageVos: Array.from({length: 100}, () => ({key: '1234'})), value})
  assert.equal(groups.length, 100)
  assert.deepEqual(groups.flatMap((group, index) => group.actual ? [index] : []), [11])
  assert.equal(groups[11].type, 'equal')
  assert.equal(groups[99].actual, '')
})

test('组内单字差异定位到该字符，不牵连同组其余字符', () => {
  const {groups} = alignResultPage({pageVos: [{key: '1234'}], value: ['1244']})
  assert.deepEqual(
    groups[0].characters.map(character => [character.type, character.expected, character.actual, character.expectedIndex]),
    [['equal', '1', '1', 0], ['equal', '2', '2', 1], ['replace', '3', '4', 2], ['equal', '4', '4', 3]],
    '仅第三位错抄时，其余三位必须仍判为正确'
  )
})
