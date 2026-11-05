import useMorse from '../../../../../common/mixin/useMorse.js'
import {DEFAULT_HAND_KEY_STANDARD, DEFAULT_HAND_KEY_SKEW, handKeyThresholds} from '../../../../../common/utils/handKeyTiming.js'
import {HAND_KEY_CONTROL_PATTERNS, HAND_KEY_CONTROL_LABELS} from '../../../../../common/utils/handKeyControlCodes.js'

const {codeKey} = useMorse()
const controlCodes = Object.fromEntries(HAND_KEY_CONTROL_PATTERNS.filter(([, parts]) => parts.length === 1)
  .map(([action, parts]) => [parts[0], action]))
const average = values => values.reduce((total, value) => total + value, 0) / values.length

// 从已保存的真实时长恢复拍发基准。优先使用岗位手键的开始符 10001 校准；
// 没有开始符时按点/划及短间隔的两个时长簇估计，不读取报底或期望答案。
export function inferPatStandard(strokes) {
  const valid = strokes.filter(stroke => Number.isFinite(stroke.diff) && stroke.diff > 10
    && Number.isFinite(stroke.gap) && stroke.gap >= 0)
  if (!valid.length) return {...DEFAULT_HAND_KEY_STANDARD}
  const lowCluster = values => {
    if (!values.length) return []
    const sorted = [...values].sort((a, b) => a - b)
    let low = sorted[Math.floor((sorted.length - 1) * .1)], high = sorted[Math.floor((sorted.length - 1) * .9)]
    if (low === high) { low = sorted[0]; high = sorted[sorted.length - 1] }
    let lower = sorted, upper = []
    for (let round = 0; round < 12 && low < high; round++) {
      const split = (low + high) / 2
      lower = sorted.filter(value => value <= split)
      upper = sorted.filter(value => value > split)
      if (!lower.length || !upper.length) break
      const nextLow = average(lower), nextHigh = average(upper)
      if (Math.abs(nextLow - low) + Math.abs(nextHigh - high) < .01) break
      low = nextLow; high = nextHigh
    }
    // 间隔有“码内/字间/组间”三个层次；第一次二分的低簇可能仍混有字间隔。
    if (lower.length < sorted.length && lower[lower.length - 1] > lower[0] * 2) return lowCluster(lower)
    return lower
  }
  const start = valid.slice(0, 5)
  if (start.length === 5 && start.every(stroke => stroke.pageNumber === start[0].pageNumber)) {
    const dots = start.slice(1, 4).map(stroke => stroke.diff)
    const dot = average(dots), line = (start[0].diff + start[4].diff) / 2
    const gaps = start.slice(1).map(stroke => stroke.gap)
    const gap = average(gaps)
    const consistent = (values, base) => values.every(value => value >= base * .5 && value <= base * 1.5)
    if (dot > 10 && gap > 10 && line > dot * 2 && line < dot * 5
        && consistent(dots, dot) && consistent([start[0].diff, start[4].diff], line)
        && consistent(gaps, gap) && (!valid[5] || valid[5].gap > Math.max(dot, gap) * 1.5)) {
      const codeGap = Math.max(dot, gap)
      return {dot, line, codeGap, wordGap: codeGap * 3, groupGap: codeGap * 5}
    }
  }
  const durations = valid.map(stroke => stroke.diff)
  const shortDurations = lowCluster(durations)
  const shortGaps = lowCluster(valid.filter((stroke, index) => index > 0
    && stroke.pageNumber === valid[index - 1].pageNumber && stroke.gap > 10).map(stroke => stroke.gap))
  const shortDuration = average(shortDurations)
  const gap = shortGaps.length ? average(shortGaps) : shortDuration
  const dot = Math.min(shortDuration, gap)
  const codeGap = gap <= dot * 2 ? Math.max(dot, gap) : dot
  const lines = durations.filter(value => value > dot * 1.5 && value <= dot * 5)
  return {dot, line: lines.length ? average(lines) : dot * 3, codeGap, wordGap: codeGap * 3, groupGap: codeGap * 5}
}

// 根据实际按键和间隔译码，不利用报底补字或纠错。gap 是本次按下之前的间隔。
export function decodePatRecord(strokes, {bwType = 1, standard, skew = DEFAULT_HAND_KEY_SKEW} = {}) {
  const {dotDash, characterGap, groupGap} = handKeyThresholds(standard || inferPatStandard(strokes), skew)
  const dictionary = codeKey[{1: 'short', 2: 'long', 3: 'letter', 4: 'mix'}[bwType]]
  if (!dictionary) return []
  const result = []
  let code = '', page = null, groupStart = true, skippedTime = 0
  let startStroke = -1, endStroke = -1
  const flush = () => {
    if (!code) return
    const control = controlCodes[code]
    result.push({code, character: control ? HAND_KEY_CONTROL_LABELS[control] : dictionary[code] || '#',
      unknown: !control && !dictionary[code], control,
      pageNumber: page, groupStart: groupStart || !!control, startStroke, endStroke})
    code = ''
    groupStart = !!control
  }
  for (let index = 0; index < strokes.length; index++) {
    const stroke = strokes[index]
    if (!Number.isFinite(stroke.diff) || !Number.isFinite(stroke.gap) || stroke.diff < 0 || stroke.gap < 0) continue
    if (stroke.pageNumber !== page) {
      flush()
      page = stroke.pageNumber
      groupStart = true
      skippedTime = 0
    }
    // 微按压不参与译码，但保留其占用时间，防止后续字符被错误合并。
    if (stroke.diff <= 10) {
      skippedTime += stroke.gap + stroke.diff
      continue
    }
    const gap = stroke.gap + skippedTime
    skippedTime = 0
    if (gap > characterGap) flush()
    if (gap > groupGap) groupStart = true
    if (!code) startStroke = index
    endStroke = index
    code += stroke.diff <= dotDash ? '0' : '1'
  }
  flush()
  const compiled = []
  for (let index = 0; index < result.length; index++) {
    const parts = result.slice(index, index + 3)
    if (parts.length === 3 && parts.every(item => item.code === '00' && item.groupStart
        && item.pageNumber === parts[0].pageNumber) && (!result[index + 3] || result[index + 3].groupStart)) {
      compiled.push({...parts[0], code: '00/00/00', character: HAND_KEY_CONTROL_LABELS.turn,
        control: 'turn', unknown: false, endStroke: parts[2].endStroke})
      index += 2
    } else compiled.push(result[index])
  }
  return compiled
}
