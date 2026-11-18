import assert from 'node:assert/strict'
import test from 'node:test'
import {alignTelegramTrendGroups, normalizeTelegramTrendGroups} from '../src/common/utils/telegramTrend.js'

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

// 每个字符一条独立时长的日志，模拟真实拍发中不会重复的小数毫秒。
let tick = 0
const pat = text => {
  const keys = text.split('')
  const patLog = keys.map(() => [event(2, 100 + ++tick / 7), event(0, 20 + ++tick / 7)])
  return {patKeys: keys, patLog, moresValue: keys.map(() => [0]), moresTime: patLog.map(word => [word[1].value])}
}
const page = (sources, pats) => sources.map((source, index) => ({
  moresKey: source.split(''),
  ...(pats[index] ?? pat(''))
}))
const resolverOf = entries => ({
  resolverMessage: entries.map(entry => entry?.message ?? ''),
  resolverPatLogs: entries.map(entry => JSON.stringify(entry?.from.patLog ?? []))
})
const shown = groups => groups.map(group => group.patKeys.join(''))

test('漏拍一行后，拍发记录与报文展示区一样把后续拍发对到下一行', () => {
  const sources = Array.from({length: 30}, (_, index) => String(1000 + index))
  // 第二行整行漏拍：第三行的拍发按先后顺序被存进了第二行的格子。
  const pats = [...sources.slice(0, 10), ...sources.slice(20)].map(pat)
  const resolver = resolverOf(sources.map((message, index) => {
    if (index >= 10 && index < 20) return null
    return {message, from: pats[index < 10 ? index : index - 10]}
  }))

  const {slots, trend} = alignTelegramTrendGroups(page(sources, pats), resolver)

  assert.deepEqual(shown(slots.slice(10, 20)), Array(10).fill(''), '第二行在两处都应是漏拍')
  assert.deepEqual(shown(slots.slice(20)), sources.slice(20), '第三行的拍发归第三行')
  assert.deepEqual(trend.map(group => group.slot), sources.map((_, index) => index))
  assert.equal(trend[20].rawIndex, 10, '保留原始所在组，用于取该行的间隔标准')
  assert.ok(slots[20].patErr.every(error => !error))
})

test('多拍的组不占报底位置，按拍发先后留在拍发记录里', () => {
  const sources = ['1111', '2222', '3333']
  const pats = ['1111', '9999', '2222', '3333'].map(pat)
  // 结算把第一个多拍组的日志记在命中的报底位置上，这里照此构造。
  const resolver = resolverOf([
    {message: '1111', from: pats[0]},
    {message: '2222', from: pats[1]},
    {message: '3333', from: pats[3]}
  ])
  const groups = [...page(sources, pats), {moresKey: '#', ...pats[3]}]

  const {slots, trend} = alignTelegramTrendGroups(groups, resolver)

  assert.deepEqual(shown(slots), ['1111', '2222', '3333', ''])
  assert.deepEqual(trend.map(group => [group.slot, group.patKeys.join(''), Boolean(group.extra)]), [
    [0, '1111', false], [null, '9999', true], [1, '2222', false], [2, '3333', false], [3, '', false]
  ])
})

test('改错痕迹跟随它最终拍对的那一组，漏组后也不留在原格子', () => {
  const sources = ['1111', '2222', '3333']
  // 漏拍第一组；第二组先拍错整组，改错后重拍。
  const pats = ['2229', '?2222', '3333'].map(pat)
  const corrected = {...pats[1], patLog: pats[1].patLog.slice(1)}
  const resolver = resolverOf([null, {message: '2222', from: corrected}, {message: '3333', from: pats[2]}])

  const {slots, trend} = alignTelegramTrendGroups(page(sources, pats), resolver)

  assert.deepEqual(shown(slots), ['', '2229?2222', '3333'])
  assert.deepEqual(slots[1].patErr, [true, true, true, true, true, false, false, false, false])
  assert.deepEqual(trend.map(group => group.slot), [0, 1, 2])
})

test('历史记录没有比对日志时保持原始顺序', () => {
  const groups = page(['1111', '2222'], ['2222'].map(pat))
  const {slots, trend} = alignTelegramTrendGroups(groups, {resolverMessage: ['', '2222']})

  assert.equal(slots, groups)
  assert.deepEqual(shown(trend), ['2222', ''])
})
