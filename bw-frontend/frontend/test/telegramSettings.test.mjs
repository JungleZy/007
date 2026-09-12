import assert from 'node:assert/strict'
import test from 'node:test'
import {parseTelegramBasicSettings, settingMilliseconds} from '../src/common/utils/telegramSettings.js'

// 接口形态：key 区分点(0)/划(1)，value 是分级配置的 JSON 字符串；type 0 为异常区间，>0 为正区间。
const savedSettings = () => [
  {key: 0, type: 0, value: JSON.stringify({type: 0, name: '异常', msg: '超出范围', min: '<40.5', max: '>120'})},
  {key: 0, type: 0, value: JSON.stringify({type: 1, name: '优', msg: '优秀', min: '40.5', max: '83.33'})},
  {key: 0, type: 0, value: JSON.stringify({type: 2, name: '良', msg: '良好', min: '83.33', max: '120'})},
  {key: 1, type: 0, value: JSON.stringify({type: 0, name: '异常', msg: '超出范围', min: '<121.25', max: '>360'})},
  {key: 1, type: 0, value: JSON.stringify({type: 1, name: '优', msg: '优秀', min: '121.25', max: '240'})},
  {key: 1, type: 0, value: JSON.stringify({type: 2, name: '良', msg: '良好', min: '240', max: '360'})}
]

test('分级毫秒边界按数值原样回读，小数不被截断', () => {
  const rows = parseTelegramBasicSettings(savedSettings())
  const ranges = key => rows.filter(row => row.key === key && row.value.type > 0).map(row => [row.value.min, row.value.max])
  assert.deepEqual(ranges('0'), [[40.5, 83.33], [83.33, 120]], '点的正区间必须回读为与保存值逐位一致的毫秒数')
  assert.deepEqual(ranges('1'), [[121.25, 240], [240, 360]], '划的正区间必须回读为与保存值逐位一致的毫秒数')
})

test('Number() 会静默强转的写法不得成为毫秒阈值', () => {
  assert.equal(settingMilliseconds(' 83.33 '), 83.33, '两侧空白不改变真实毫秒值')
  const coercible = [
    ['', 'Number("") 为 0'],
    [null, 'Number(null) 为 0'],
    ['1e3', 'Number("1e3") 为 1000'],
    [Infinity, 'Infinity 可以参与大小比较'],
    [NaN, 'NaN 的比较恒为假'],
    [-1, '负毫秒不存在']
  ]
  for (const [raw, hint] of coercible) {
    assert.throws(() => settingMilliseconds(raw), /配置毫秒值必须为有限的非负数/, `${hint}，必须被拒绝而不是落成阈值`)
  }
})

test('异常区间未覆盖正区间外边界时整份配置被拒绝且不被就地改写', () => {
  const settings = savedSettings()
  settings[0].value = JSON.stringify({type: 0, name: '异常', msg: '超出范围', min: '<40.5', max: '>119'})
  const untouched = JSON.parse(JSON.stringify(settings))
  assert.throws(() => parseTelegramBasicSettings(settings), /异常区间必须覆盖正区间的外边界/, '异常上界 119 小于正区间上界 120，必须整份拒绝')
  assert.deepEqual(settings, untouched, '解析失败不得把传入配置里的毫秒字符串就地改写成数字')
})
