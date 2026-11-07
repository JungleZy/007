import assert from 'node:assert/strict'
import test from 'node:test'
import useMorse from '../src/common/mixin/useMorse.js'
import {
  formatGroupLines, handPageGroups, electronicPageGroups, datagramPageText,
  handAlphabet, decodeHandLogs, replayElectronicLogs, replayDatagramKeys
} from '../src/common/utils/studentMessageFormat.js'

const {morseCode} = useMorse()

test('格式化为组间空格、每行十组', () => {
  const groups = Array.from({length: 12}, (_, i) => String(1000 + i))
  assert.equal(formatGroupLines(groups), `${groups.slice(0, 10).join(' ')}\n1010 1011`)
})

test('手键已提交页取学员识别出的 patKeys，跳过页面控制与空组', () => {
  const body = JSON.stringify([
    {moresKey: '["1","2","3","4"]', patKeys: '["开始"]'},
    {moresKey: '["1","2","3","4"]', patKeys: '["1","2","3","4"]'},
    {moresKey: '["5","6","7","8"]', patKeys: '["5","?","6","7","8"]'},
    {moresKey: '["9","0","1","2"]', patKeys: '[]'}
  ])
  assert.deepEqual(handPageGroups(body), ['1234', '5?678'])
})

test('电子键已提交页按 sort 排序并拼接字符', () => {
  const rows = [
    {sort: 1, value: '["5","6","7","8"]'},
    {sort: 0, value: '["开始","1","2","3","4"]'},
    {sort: 2, value: '["句号"]'}
  ]
  assert.deepEqual(electronicPageGroups(rows), ['1234', '5678'])
})

test('数据报已提交页保留学员换行、归一行内空白并大写', () => {
  assert.equal(datagramPageText('ab12  cd34\r\n\n  ef56 gh78-1 '), 'AB12 CD34\nEF56 GH78-1')
})

test('手键字母表与学员端同口径', () => {
  assert.equal(handAlphabet({type: 0, codeSort: 0}), 'short')
  assert.equal(handAlphabet({type: 0, codeSort: 1}), 'long')
  assert.equal(handAlphabet({type: 1}), 'letter')
  assert.equal(handAlphabet({type: 2}), 'mix')
})

/** 按标准时长把字符串报文转成推送帧：点 80、划 240、码内 80、字间 240、组间 560 */
function keyed(groups, alphabet = 'letter') {
  const frames = []
  let gap = 0
  groups.forEach((group, g) => {
    const characters = Array.isArray(group) ? group : [...group]
    characters.forEach((character, c) => {
      const code = character.startsWith('=') ? character.slice(1) : morseCode[alphabet][character].value
      ;[...code].forEach((element, e) => {
        frames.push({code: Number(element), diff: element === '0' ? 80 : 240, gap})
        gap = 80
      })
      gap = c === characters.length - 1 ? 560 : 240
    })
    if (g === groups.length - 1) gap = 0
  })
  return frames
}

test('手键实时识别按间隔断字断组并查字母表', () => {
  assert.deepEqual(decodeHandLogs(keyed(['ABCD', 'EFGH']), 'letter'), ['ABCD', 'EFGH'])
})

test('手键实时识别：开始符清空校准内容，换页后只保留新一页', () => {
  const frames = keyed([['=10001'], 'ABCD', ['=000000'], ['=10001'], 'WXYZ'])
  assert.deepEqual(decodeHandLogs(frames, 'letter'), ['WXYZ'])
})

test('手键实时识别：三截「00 00 00」换页', () => {
  const frames = keyed(['ABCD', ['=00'], ['=00'], ['=00'], 'EFGH'])
  assert.deepEqual(decodeHandLogs(frames, 'letter'), ['EFGH'])
})

test('手键实时识别：改错码显示为 ?', () => {
  assert.deepEqual(decodeHandLogs(keyed([['A', 'B', '=001100', 'C']]), 'letter'), ['AB?C'])
})

test('电子键实时重放：空格换组，句号后只保留新一页', () => {
  const logs = ['开始', '1', '2', '3', '4', ' ', '5', '6', '句号', '开始', '7', '8', '/', '9', ' ', '0']
    .map(key => ({key}))
  assert.deepEqual(replayElectronicLogs(logs), ['78?9', '0'])
})

test('数据报实时重放：可见字符、Enter 换行、Backspace 删除，双 Enter 翻页清空', () => {
  const keys = ['a', 'b', 'Shift', 'c', 'Backspace', 'd', ' ', 'e', 'Enter', 'f', 'Enter', 'Enter', 'g', 'h', 'Enter', 'i']
  assert.equal(replayDatagramKeys(keys.map(key => ({key}))), 'GH\nI')
  assert.equal(replayDatagramKeys(['x', 'y', 'Enter', 'z'].map(key => ({key}))), 'XY\nZ')
})
