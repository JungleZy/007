import assert from 'node:assert/strict'
import {readFileSync} from 'node:fs'
import test from 'node:test'
import vm from 'node:vm'

const source = readFileSync(new URL('../src/common/utils/copyReference.js', import.meta.url), 'utf8')
  .replace(/export /g, '')
const {formatReferenceText} = vm.runInNewContext(source + '\n({formatReferenceText})')

test('手键JSON报底与电子键数组报底输出一致，保留前导零并每十组换行', () => {
  const groups = Array.from({length: 12}, (_, index) => String(index).padStart(4, '0').split(''))
  const expected = '0000 0001 0002 0003 0004 0005 0006 0007 0008 0009\n0010 0011'
  assert.equal(formatReferenceText(groups), expected)
  assert.equal(formatReferenceText(groups.map(group => JSON.stringify(group))), expected)
  assert.equal(groups[0].join(''), '0000')
})

test('字母及混合报保留原字符，空格子和占位格不作为模拟器报文复制', () => {
  assert.equal(formatReferenceText(['["A","B","0","1"]', ['C', 'D'], '#', null, '']), 'AB01 CD')
  assert.equal(formatReferenceText([]), '')
  assert.equal(formatReferenceText(undefined), '')
})

test('手键按页复制附带显式翻页符，拼接后删组也保留页界，空页不生成标记', () => {
  const first = formatReferenceText(['8105', '6975'], true)
  const second = formatReferenceText(['9563', '2045'], true)
  assert.equal(first, '8105 6975\n。')
  assert.equal((first + '\n' + second).replace('8105 ', ''), '6975\n。\n9563 2045\n。')
  assert.equal(formatReferenceText([], true), '')
  assert.equal(formatReferenceText(['9563'], false), '9563')
})

test('可用剪贴板直接复制完整文本', async () => {
  let copied
  const context = vm.createContext({navigator: {clipboard: {writeText: async text => {copied = text}}}})
  vm.runInContext(source, context)
  await context.copyReferenceText('0012 ABCD\n3456')
  assert.equal(copied, '0012 ABCD\n3456')
})

for (const success of [true, false]) {
  test(`剪贴板API拒绝后传统复制${success ? '成功' : '失败'}均清理临时输入框并恢复焦点`, async () => {
    let selected = false, removed = false, focused = false
    const input = {style: {}, select() {selected = true}, remove() {removed = true}}
    const context = vm.createContext({
      navigator: {clipboard: {writeText: async () => {throw new Error('denied')}}},
      document: {activeElement: {focus() {focused = true}}, createElement: () => input,
        body: {appendChild() {}}, execCommand: action => {assert.equal(action, 'copy'); return success}}
    })
    vm.runInContext(source, context)
    if (success) await context.copyReferenceText('0012 ABCD')
    else await assert.rejects(context.copyReferenceText('0012 ABCD'), /复制失败/)
    assert.equal(input.value, '0012 ABCD')
    assert.equal(selected && removed && focused, true)
  })
}
