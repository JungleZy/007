import assert from 'node:assert/strict'
import test from 'node:test'
import {createMorseController} from '../src/common/utils/morseController.js'
import {DEFAULT_RATIO, calculateTiming} from '../src/common/utils/voice/MorseVoiceHighPerformance.js'

// 假音频端：记录收到的指令序列，并可控制 message/addCode 是否被接受。
const newController = (accept = () => true) => {
  const calls = []
  const controller = createMorseController({operation: command => (calls.push(command), accept(command))})
  return {controller, drain: () => calls.splice(0, calls.length)}
}

const timing = (type, rate = 15) => calculateTiming({rate, unit: 'groups', type})

test('控制器构造时按 15 组/分、默认点划配比下发唯一一次初始 configure', () => {
  const {drain} = newController()
  assert.deepEqual(drain(), [{
    type: 'configure',
    data: {...calculateTiming({rate: 15, unit: 'groups', type: 'short', ratio: DEFAULT_RATIO}), frequency: 1000, volume: 1}
  }], '初始配置的码速、配比、频率、音量必须逐字保持')
})

test('changeCriterion 只在速度可用且确有变化时重下配置', () => {
  const {controller, drain} = newController()
  drain()
  for (const speed of ['abc', NaN, Infinity, -Infinity, 0, -5, '0']) controller.changeCriterion(speed)
  assert.deepEqual(drain(), [], '非有限值或非正速度（首字符成码前的测速）不得改变播报配置')
  controller.changeCriterion(15)
  assert.deepEqual(drain(), [], '速率与报文类型都未变时不得重复下发 configure')
  controller.changeCriterion('20')
  assert.deepEqual(drain(), [{type: 'configure', data: {...timing('short', 20), frequency: 1000, volume: 1, model: true}}], '速率变化应按新码速重配')
  controller.changeCriterion(20)
  assert.deepEqual(drain(), [], '同一速率重复设置不再下发')
  controller.changeCriterion(20, 'mix')
  assert.deepEqual(drain(), [{type: 'configure', data: {...timing('mix', 20), frequency: 1000, volume: 1, model: true}}], '速率不变但报文类型变化仍须重配')
})

test('changePattern 把外部报文类型编码映射为播报类型，未知值一律当短码', () => {
  for (const [pattern, expected] of [[1, 'letter'], [2, 'mix'], [0, 'short'], [undefined, 'short'], [9, 'short']]) {
    const {controller, drain} = newController()
    drain()
    controller.changePattern(pattern)
    controller.voiceCode({code: '.-'})
    const message = drain().find(command => command.type === 'message')
    assert.equal(message.data.numType, expected, `changePattern(${pattern}) 应把默认报文类型定为 ${expected}`)
  }
})

test('voiceCode 的类型决议链决定重配的时值，且时值未变时不重复配置', () => {
  const {controller, drain} = newController()
  drain()
  for (const code of ['', undefined, []]) assert.equal(controller.voiceCode({code}), false, '空码应直接判失败')
  assert.deepEqual(drain(), [], '空码不得触达音频端')

  controller.changePattern(1)
  controller.voiceCode({numType: 'short', code: '.-'})
  assert.deepEqual(drain()[0], {type: 'configure', data: timing('letter')}, '字母报文优先于 numType')

  controller.voiceCode({numType: 'short', code: '-.'})
  assert.deepEqual(drain().map(command => command.type), ['addCode'], '时值类型未变时不得重下 configure')

  controller.changePattern(0)
  controller.voiceCode({numType: 'short', code: '.-'})
  assert.deepEqual(drain()[0], {type: 'configure', data: timing('short')}, '非字母报文下 numType=short 决定短码时值')

  controller.changePattern(2)
  controller.voiceCode({numType: 'long', code: '.-'})
  assert.deepEqual(drain()[0], {type: 'configure', data: timing('mix')}, '混合报文在 numType 非短码时按混合时值')

  controller.changePattern(0)
  controller.voiceCode({numType: 'long', code: '.-'})
  assert.deepEqual(drain()[0], {type: 'configure', data: timing('long')}, '其余情况兜底为长码时值')
})

test('播报状态机：被拒不算起播，续码走 addCode，进程终止或 clear 后重新起播', () => {
  let accepted = false
  const {controller, drain} = newController(() => accepted)
  drain()

  assert.equal(controller.voiceCode({code: '.-'}), false, '音频端拒收时 voiceCode 应如实返回失败')
  assert.deepEqual(drain().map(command => command.type), ['message'])
  assert.equal(controller.voiceCode({code: '.-'}), false)
  assert.deepEqual(drain().map(command => command.type), ['message'], '上一段未被接受则仍未起播，须继续用 message')

  accepted = true
  assert.equal(controller.voiceCode({code: '.-'}), true)
  assert.deepEqual(drain().map(command => command.type), ['message'])
  controller.voiceCode({code: '-.'})
  assert.deepEqual(drain().map(command => command.type), ['addCode'], '已在播时后续码段应追加而非重新起播')

  controller.handleProcessEvent({status: 'playing', type: 'progress'})
  controller.voiceCode({code: '-.'})
  assert.deepEqual(drain().map(command => command.type), ['addCode'], '播报进行中的回报不得中断续播')

  for (const event of [{status: 'finish'}, {type: 'stopped'}, {type: 'failure'}]) {
    controller.handleProcessEvent(event)
    controller.voiceCode({code: '.-'})
    assert.deepEqual(drain().map(command => command.type), ['message'], `收到 ${JSON.stringify(event)} 后应重新起播`)
    controller.voiceCode({code: '-.'})
    drain()
  }

  controller.clear()
  assert.deepEqual(drain(), [{type: 'stop'}], 'clear 必须停播')
  controller.voiceCode({code: '.-'})
  assert.deepEqual(drain().map(command => command.type), ['message'], 'clear 之后应重新起播')
})
