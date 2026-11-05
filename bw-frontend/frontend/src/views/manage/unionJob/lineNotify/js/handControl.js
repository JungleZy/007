import {createHandKeyDecoder} from '../../../../../common/utils/handKeyDecoder.js'
import {handKeyThresholds, DEFAULT_HAND_KEY_STANDARD} from '../../../../../common/utils/handKeyTiming.js'
import {HAND_KEY_CONTROL_PATTERNS} from '../../../../../common/utils/handKeyControlCodes.js'
import {inferPatStandard} from './decodePatRecord.js'

// 实时控制符识别：先按实际时长成字，再以完整组间隔确认三截分页符。
export function createLineHandControl({onControl, onStroke, getPage, setTimer = setTimeout, clearTimer = clearTimeout}) {
  const single = Object.fromEntries(HAND_KEY_CONTROL_PATTERNS.filter(([, parts]) => parts.length === 1)
    .map(([action, parts]) => [parts[0], action]))
  let standard = {...DEFAULT_HAND_KEY_STANDARD}
  let history = [], word = [], group = [], turnParts = 0
  let leadingGap = 0, lastUp = null, wordTimer = null, groupTimer = null
  const thresholds = () => handKeyThresholds(standard)
  const clearTimers = () => {
    clearTimer(wordTimer)
    clearTimer(groupTimer)
    wordTimer = groupTimer = null
  }
  const flushWord = () => {
    if (!word.length) return
    const code = word.map(stroke => stroke.diff <= thresholds().dotDash ? '0' : '1').join('')
    word = []
    if (single[code]) {
      group = []
      turnParts = 0
      onControl(single[code])
    } else group.push(code)
  }
  const flushGroup = () => {
    flushWord()
    if (!group.length) return
    turnParts = group.length === 1 && group[0] === '00' ? turnParts + 1 : 0
    group = []
    if (turnParts === 3) {
      turnParts = 0
      onControl('turn')
    }
  }
  const schedule = elapsed => {
    clearTimers()
    if (!word.length && !group.length) return
    const limits = thresholds()
    if (word.length) wordTimer = setTimer(() => { wordTimer = null; flushWord() }, Math.max(0, limits.characterGap - elapsed) + 1)
    groupTimer = setTimer(() => { groupTimer = null; flushGroup() }, Math.max(0, limits.groupGap - elapsed) + 1)
  }
  const decoder = createHandKeyDecoder({
    enabled: () => true,
    threshold: () => thresholds().dotDash,
    lineLimit: () => standard.line * 1.5,
    onPressChange: pressed => { if (pressed) { clearTimers(); leadingGap = 0 } },
    emit: event => {
      if (event.code === -1) {
        leadingGap = event.gapTime[1] - event.gapTime[0]
        const limits = thresholds()
        if (leadingGap > limits.characterGap) flushWord()
        if (leadingGap > limits.groupGap) flushGroup()
      } else {
        const stroke = {diff: event.diffTime[1] - event.diffTime[0], gap: leadingGap, pageNumber: getPage()}
        lastUp = event.diffTime[1]
        history.push(stroke)
        if (history.length > 128) history.shift()
        standard = inferPatStandard(history)
        word.push(stroke)
        onStroke(stroke)
      }
    }
  })
  return {
    receive(frame) {
      decoder.receive({...frame, t: 0})
      if (frame.k === 1 && lastUp !== null) schedule(Math.max(0, frame.d - lastUp))
    },
    seed(strokes) {
      if (!history.length && strokes.length) {
        history = strokes.slice(-128)
        standard = inferPatStandard(history)
      }
    },
    reset() {
      clearTimers()
      decoder.reset()
      word = []; group = []; turnParts = 0; lastUp = null
    }
  }
}
