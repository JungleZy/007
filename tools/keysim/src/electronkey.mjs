/**
 * 电子键（双手电键盘）：设备送的是**已成形的点划组**单字节码，客户端不做时长判定。
 * 右手键（31-35/42-45）起头入缓冲，左手键（21-25）收尾并立即成字
 * （electronKeyZuXun 学生端 handKeyTrain.js:315-347）。
 * 只有"单笔成字"（右手键后没有左手键）才依赖 800ms 静默超时提交（:39,339-345），
 * 而后续任意数据键或 41/14 都会立刻提交挂起的缓冲（:205-217,298-301），
 * 所以整页只有最后一个字可能付这 800ms。
 */
import {ELECTRON_FN, ELECTRON_LEFT, ELECTRON_RIGHT, ELECTRON_TEXT, codeOnKey, electronAlphabet, elementsOf} from './tables.mjs'
import {createTimeline} from './timeline.mjs'

export {ELECTRON_FN, ELECTRON_LEFT, ELECTRON_RIGHT, electronAlphabet}

/** 800ms 是页尾单笔字的提交超时，取值同 handKeyTrain.js:39 的 pauseDuration。 */
export const PAUSE_DURATION = 800

const byLength = table => Object.entries(table).sort((a, b) => b[1].length - a[1].length)
const RIGHT_BY_LEN = byLength(ELECTRON_RIGHT)
const LEFT_BY_LEN = byLength(ELECTRON_LEFT)
const codeFor = (table, elements) => table.find(([, value]) => value === elements)?.[0]

/**
 * 点划串 -> 键码序列。优先两笔（右手起头 + 左手收尾，立即成字），
 * 仅当只能单笔时返回一个键码（其提交要靠后续键或 800ms 超时）。
 */
export function chordOf(elements) {
  const target = String(elements)
  for (let split = 1; split < target.length; split++) {
    const right = codeFor(RIGHT_BY_LEN, target.slice(0, split))
    const left = codeFor(LEFT_BY_LEN, target.slice(split))
    if (right && left) return [Number(right), Number(left)]
  }
  const single = codeFor(RIGHT_BY_LEN, target)
  if (single) return [Number(single)]
  throw new Error(`点划串 ${target} 无法用电子键键位拍出（右手键 ${Object.values(ELECTRON_RIGHT)} / 左手键 ${Object.values(ELECTRON_LEFT)}）`)
}

/** 该字符能否在 F2 模式成字：codeOnKey 只收字母与开始符，数码必须走 F1 直出。 */
export function isChordable(alphabet, char) {
  if (ELECTRON_TEXT[char] === undefined) return true
  try {
    return Object.values(codeOnKey).includes(char) && !!chordOf(elementsOf(alphabet, char))
  } catch {
    return false
  }
}

/**
 * rate 单位是"组/分"，与客户端显示口径和服务端重算口径一致
 * （patNumber*60000/4/elapsed，handKeyTrain.js:390；服务端 FOUR_CHARACTER_GROUPS_PER_MINUTE）。
 */
export function electronKeyPlan({rate = 20, strokeGap = 30, groupSize = 4} = {}) {
  const perGroup = 60000 / Number(rate)
  const perChar = perGroup / groupSize
  if (perChar <= strokeGap * 2) {
    throw new Error(`码率 ${rate} 组/分 只给每字 ${perChar.toFixed(1)}ms，装不下两笔（每笔间隔 ${strokeGap}ms）`)
  }
  return Object.freeze({rate, unit: 'groups', perGroup, perChar, strokeGap, groupSize})
}

export function splitGroups(text, groupSize = 4) {
  if (Array.isArray(text)) return text.map(group => [...String(group)])
  const trimmed = String(text).trim()
  if (/\s/.test(trimmed)) return trimmed.split(/\s+/).filter(Boolean).map(group => [...group])
  return [...trimmed].reduce((groups, char, index) => {
    if (index % groupSize === 0) groups.push([])
    groups[groups.length - 1].push(char)
    return groups
  }, [])
}

/**
 * 生成一次电子键拍发的完整时间轴。
 * tail: 'page' 用 F1+句号 提交本页并翻页；'end' 用 F3+回车 提交并结束；null 只留 800ms 静默。
 */
export function electronKeyTimeline({
  text,
  alphabet = 'letter',
  plan = electronKeyPlan(),
  jitter = 0,
  seed = 1,
  preamble = true,
  tail = 'end'
} = {}) {
  const groups = splitGroups(text, plan.groupSize)
  const timeline = createTimeline('electron', plan, {jitter, seed})
  let mode = null

  const key = code => {
    timeline.code(code)
    timeline.wait(plan.strokeGap)
  }
  const setMode = next => {
    if (mode === next) return
    key(next === 'F1' ? ELECTRON_FN.F1 : ELECTRON_FN.F2)
    mode = next
  }

  if (preamble) {
    timeline.mark('preamble')
    setMode('F1')
    key(ELECTRON_FN.start)
    timeline.char('开始', timeline.cursor, {codes: [ELECTRON_FN.start], kind: 'control', groupIndex: null})
  }

  timeline.mark('body')
  let target = timeline.cursor
  groups.forEach((group, groupIndex) => {
    group.forEach(char => {
      target += plan.perChar
      const startedAt = timeline.cursor
      const codes = []
      if (ELECTRON_TEXT[char] !== undefined && !isChordable(alphabet, char)) {
        setMode('F1')
        codes.push(ELECTRON_TEXT[char])
      } else {
        setMode('F2')
        codes.push(...chordOf(elementsOf(alphabet, char)))
      }
      for (const code of codes) key(code)
      timeline.char(char, startedAt, {codes, groupIndex, kind: 'char'})
      if (target > timeline.cursor) timeline.wait(target - timeline.cursor, {exact: true})
    })
    // 回车推进到下一组报底槽位（handKeyTrain.js:277-296）。
    key(ELECTRON_FN.enter)
    if (groupIndex < groups.length - 1 && target > timeline.cursor) {
      timeline.wait(target - timeline.cursor, {exact: true})
    }
  })

  if (tail === 'page') {
    timeline.mark('page')
    setMode('F1')
    key(ELECTRON_FN.period)
  } else if (tail === 'end') {
    timeline.mark('end')
    key(ELECTRON_FN.F3)
    key(ELECTRON_FN.enter)
  } else {
    // 页尾单笔字只能等 800ms 超时提交。
    timeline.wait(PAUSE_DURATION + plan.strokeGap, {exact: true})
  }

  return timeline.build({alphabet, tail, groups: groups.map(group => group.join(''))})
}
