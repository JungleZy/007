/**
 * 手键（直键）：设备只送按下/抬起两种边沿，点划由客户端按按压时长判定
 * （useTraffic.js:63-68）。阈值不是常量，而是现场从开始符 '10001' 校准出来的
 * （handKeyTrain.js:335-366），所以时间轴必须先发一遍合格的开始符。
 */
import {HAND_CONTROL, elementsOf, handAlphabet, timingOf} from './tables.mjs'
import {assertHandPlan, createTimeline} from './timeline.mjs'

export {HAND_CONTROL, handAlphabet}

/**
 * 由目标码率推出点/划/各级间隔，节拍模型与播报侧同源（calculateTiming）。
 * pageTurns:false 用于单页训练（不会经历每页重算的 codeGap≥60 夹紧），可用更高码率。
 */
export function handKeyPlan({rate = 100, unit = 'characters', type = 'letter', ratio, lowRate = false, skew = 50, jitter = 0, pageTurns = true} = {}) {
  const plan = timingOf({rate, unit, type, ratio, lowRate})
  return assertHandPlan({...plan, rate, unit, alphabet: type, skew}, {skew, jitter, pageTurns})
}

/**
 * 把一串点划按"按压 + 符内间隔"铺到时间轴上，不含字间隔。
 * 返回该字的电码、逐段按压时长与符内间隔，供 payload sink 复现 moresValue/moresTime/patLogs。
 */
export function appendElements(timeline, plan, elements) {
  const symbols = [...elements]
  const durations = []
  const gaps = []
  symbols.forEach((element, index) => {
    durations.push(timeline.press(element === '1' ? plan.dash : plan.dot))
    if (index < symbols.length - 1) gaps.push(timeline.wait(plan.gap))
  })
  return {codes: symbols.map(Number), durations, gaps}
}

/** 报文文本 -> 组数组；空格/换行分组，其余按字符切开。 */
export function splitGroups(text) {
  if (Array.isArray(text)) return text.map(group => [...String(group)])
  return String(text).trim().split(/\s+/).filter(Boolean).map(group => [...group])
}

/**
 * 生成一次手键拍发的完整时间轴。
 * tail: 'turn' 追加翻页符（三个 '00'），'end' 追加结束符 '01010'，null 只留静默。
 */
export function handKeyTimeline({
  text,
  alphabet = 'letter',
  plan = handKeyPlan({type: alphabet}),
  jitter = 0,
  seed = 1,
  preamble = true,
  tail = null
} = {}) {
  const groups = splitGroups(text)
  const timeline = createTimeline('hand', plan, {jitter, seed})

  // 客户端把"字间隔"事件算进下一个字的 patLogs（handKeyTrain.js:217-244 先编译再入队），
  // 所以每个字记录它的前导间隔。
  let leadingGap = null
  const emitChar = (value, elements, {groupIndex = null, kind = 'char'} = {}) => {
    const startedAt = timeline.cursor
    const detail = appendElements(timeline, plan, elements)
    timeline.char(value, startedAt, {...detail, elements, groupIndex, kind, leadingGap})
    leadingGap = null
  }

  if (preamble) {
    timeline.mark('preamble')
    emitChar('开始', HAND_CONTROL.start, {kind: 'control'})
    // 开始符校验在第 5 次抬起时即触发，但正文首字仍需要一个组间隔把它与开始符分开。
    leadingGap = timeline.wait(plan.group)
  }

  timeline.mark('body')
  groups.forEach((group, groupIndex) => {
    group.forEach((char, charIndex) => {
      emitChar(char, elementsOf(alphabet, char), {groupIndex})
      if (charIndex < group.length - 1) leadingGap = timeline.wait(plan.word)
    })
    if (groupIndex < groups.length - 1) leadingGap = timeline.wait(plan.group)
  })

  if (tail === 'turn') {
    leadingGap = timeline.wait(plan.group)
    timeline.mark('turn')
    HAND_CONTROL.turn.forEach((elements, index) => {
      emitChar('翻页', elements, {kind: 'control'})
      if (index < HAND_CONTROL.turn.length - 1) leadingGap = timeline.wait(plan.word)
    })
  } else if (tail === 'end') {
    leadingGap = timeline.wait(plan.group)
    timeline.mark('end')
    emitChar('结束', HAND_CONTROL.end, {kind: 'control'})
  }

  // 末字要靠静默定时器（handKeyTrain.js:309-324）编译，实时回放必须留足这段静默。
  timeline.wait(plan.group, {exact: true})
  return timeline.build({alphabet, tail, groups: groups.map(group => group.join(''))})
}
