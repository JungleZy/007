// 电码表与节拍模型一律从前端生产代码导入，模拟器不持有第二套约定。
// 三个来源都是零依赖纯模块，可在 node 下直接 import。
import useMorse from '../../bw-frontend/frontend/src/common/mixin/useMorse.js'
import {codeInKey, codeOnKey} from '../../bw-frontend/frontend/src/views/manage/organization/electronKeyZuXun/train/student/js/keyCode.js'
import {calculateTiming, forwardTable, DEFAULT_RATIO} from '../../bw-frontend/frontend/src/common/utils/voice/MorseVoiceHighPerformance.js'

export {codeInKey, codeOnKey, calculateTiming, forwardTable, DEFAULT_RATIO}

const {morseCode, codeKey, baseCode} = useMorse()
export {morseCode, codeKey, baseCode}

export const ALPHABETS = Object.freeze(['letter', 'short', 'long', 'mix'])

/** 手键报文字母表判定，口径同 handkeyZuXun 学生端 handKeyTrain.js:485。 */
export function handAlphabet({type, codeSort} = {}) {
  return Number(type) === 1 ? 'letter' : Number(type) === 2 ? 'mix' : codeSort ? 'long' : 'short'
}

/** 电子键报文字母表判定，口径同 electronKeyZuXun 学生端 handKeyTrain.js:391。 */
export function electronAlphabet({messageType} = {}) {
  return Number(messageType) === 1 ? 'letter' : Number(messageType) === 2 ? 'mix' : 'short'
}

/** 字符 -> 点划串（'0'=点，'1'=划）。 */
export function elementsOf(alphabet, char) {
  const entry = morseCode[alphabet]?.[char]
  if (!entry) throw new Error(`字母表 ${alphabet} 没有字符 ${char}`)
  return entry.value
}

/** 点划串 -> 字符，未命中返回 undefined（客户端落 '#'）。 */
export function charOf(alphabet, elements) {
  return codeKey[alphabet]?.[elements]
}

/**
 * 手键控制符点划串，取值同 handkeyZuXun 学生端 handKeyTrain.js:41-48 的 initSymbol。
 * turn 是三个独立字符（原值 '00,00,00' 以逗号分字），其余各为一个字符。
 */
export const HAND_CONTROL = Object.freeze({
  start: '10001',
  end: '01010',
  turn: Object.freeze(['00', '00', '00']),
  alter: '001100',
  next: '001011'
})

/** forwardTable 用 0=点 1=划 2=符内间隔 表示同一批控制符，用于交叉校验 HAND_CONTROL。 */
export function forwardElements(char) {
  const symbols = forwardTable.letter[char]
  if (!symbols) throw new Error(`forwardTable.letter 没有 ${char}`)
  return symbols.filter(symbol => symbol !== 2).join('')
}

const electronKeysBy = predicate => Object.freeze(Object.fromEntries(
  Object.entries(codeInKey)
    .filter(([code, entry]) => Array.isArray(entry._code) && entry._code.length > 0 && predicate(Number(code)))
    .map(([code, entry]) => [code, entry._code.join('')])
))

/**
 * 电子键是双手组合键：右手键（>=30）起头并装入缓冲，左手键（<30）收尾并立即成字。
 * 判定在 electronKeyZuXun 学生端 handKeyTrain.js:320-347（`Number(val) < 30` 分支）。
 */
export const ELECTRON_RIGHT = electronKeysBy(code => code >= 30)
export const ELECTRON_LEFT = electronKeysBy(code => code > 20 && code < 30)

/** 功能键码，取值同 keyCode.js:93-114。 */
export const ELECTRON_FN = Object.freeze({
  control: 11, F1: 12, F2: 13, F3: 14, F4: 15,
  enter: 41, query: 42, slash: 43, start: 44, period: 45
})

/** F1/F3 直出字符（数码）：文本 -> 键码，同 codeInKey[val].text。 */
export const ELECTRON_TEXT = Object.freeze(Object.fromEntries(
  Object.entries(codeInKey)
    .filter(([code, entry]) => Number(code) > 20 && Number(code) <= 35 && entry.text)
    .map(([code, entry]) => [entry.text, Number(code)])
))

/**
 * 节拍模型直接复用播报侧 calculateTiming：criterion 是基准毫秒，
 * 各段时长 = criterion × ratio[段]。段名 dot/dash/gap/word/suite/leaf。
 */
export function timingOf({rate = 100, unit = 'characters', type = 'letter', ratio = DEFAULT_RATIO, lowRate = false} = {}) {
  const {criterion, ratio: resolved} = calculateTiming({rate, unit, type, lowRate, ratio})
  return Object.freeze({
    criterion,
    ratio: Object.freeze({...resolved}),
    dot: criterion * resolved.dot,
    dash: criterion * resolved.dash,
    gap: criterion * resolved.gap,
    word: criterion * resolved.word,
    group: criterion * resolved.suite,
    page: criterion * resolved.leaf
  })
}
