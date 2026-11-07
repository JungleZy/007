// 音频跟随最近正文的节奏；成绩码率仍由完整采集区间计算。
export function createElectronicAudioTempo() {
  let previous = null
  const intervals = []
  const reset = () => { previous = null; intervals.length = 0 }
  const record = at => {
    if (!Number.isFinite(at)) return null
    if (previous === null) { previous = at; return null }
    const elapsed = at - previous
    if (elapsed <= 0) return null
    previous = at
    // 长停顿或等待翻页不代表当前拍发的点划节拍；重新收集字符间隔。
    if (elapsed > 2000) { intervals.length = 0; return null }
    intervals.push(elapsed)
    if (intervals.length > 8) intervals.shift()
    return 60000 * intervals.length / intervals.reduce((sum, value) => sum + value, 0)
  }
  return {record, reset}
}
