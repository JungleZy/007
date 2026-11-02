import assert from 'node:assert/strict'
import test from 'node:test'
import {normalizeTelegramTrendGroups} from '../src/common/utils/telegramTrend.js'

const event = (key, value) => ({key, value})

test('字符间隔归到前一个字符，组间隔归到前一组', () => {
  const result = normalizeTelegramTrendGroups([
    {patLog: [[event(0, 10)], [event(2, 30), event(0, 11)]]},
    {patLog: [[event(2, 700), event(0, 20)], [event(2, 40), event(1, 21)]]},
    {patLog: [[event(2, 800), event(0, 30)]]}
  ])

  assert.deepEqual(result[0].patLog, [
    [event(0, 10)],
    [event(0, 11)]
  ])
  assert.deepEqual(result[1].patLog, [
    [event(0, 20)],
    [event(1, 21)]
  ])
  assert.deepEqual(result[0].wordGap, [[event(2, 30)], []])
  assert.deepEqual(result[1].wordGap, [[event(2, 40)], []])
  assert.deepEqual(result[0].groupGap, [event(2, 700)])
  assert.deepEqual(result[1].groupGap, [event(2, 800)])
})

test('五点长码的最后一个点不会被当成间隔丢弃', () => {
  const fiveDots = [
    event(0, 10), event(2, 30), event(0, 11), event(2, 31), event(0, 12),
    event(2, 32), event(0, 13), event(2, 33), event(0, 14)
  ]
  const word = normalizeTelegramTrendGroups([{patLog: [fiveDots]}])[0].patLog[0]

  assert.equal(word.filter(item => item.key === 0).length, 5)
  assert.equal(word[word.length - 1].key, 0)
})
