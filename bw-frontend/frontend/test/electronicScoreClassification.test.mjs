import assert from 'node:assert/strict'
import {readFileSync} from 'node:fs'
import test from 'node:test'
import vm from 'node:vm'

for (const path of [
  '../src/views/manage/postJob/telegram/examTrain/js/trainScore.js',
  '../src/views/manage/organization/electronKeyZuXun/train/student/js/trainScore.js'
]) {
  const source = readFileSync(new URL(path, import.meta.url), 'utf8').replace(/\r\n/g, '\n')
  const start = source.indexOf('const formatData =')
  const end = source.indexOf('\n  }', source.indexOf('return arr', start)) + 4
  const moreLine = {value: []}
  const context = vm.createContext({moreLine})
  vm.runInContext(source.slice(start, end) + '\nglobalThis.format = formatData', context)
  const row = (value, key = ['1','2','3','4']) => ({key: JSON.stringify(key),
    value: JSON.stringify(value), time: '[100]', sort: 0, pageNumber: 1})

  test(`${path}以服务端分类展示带改错历史的串组和多少码`, () => {
    for (const [marker, flag] of [['bunchGroup', 'bunchGroup'], ['lack', 'lackCode'], ['more', 'moreCode']]) {
      const value = [marker, '9', '?', '1', '3', '4']
      const result = context.format([row(value)])[0]
      assert.equal(result[flag], true)
      assert.equal(result.error, undefined)
      assert.equal(result.warning, undefined)
      assert.deepEqual(Array.from(result.value), value.slice(1))
    }
  })

  test(`${path}失败改错仍显示错码，额外报文带改错时不会读取空报底导致白屏`, () => {
    const wrong = context.format([row(['9', '?', '1', '2', '9', '4'])])[0]
    assert.equal(wrong.error, true)
    assert.equal(wrong.warning, undefined)
    assert.doesNotThrow(() => context.format([row(['9', '?', '1'], null)]))
    assert.equal(moreLine.value.length, 1)
  })
}
