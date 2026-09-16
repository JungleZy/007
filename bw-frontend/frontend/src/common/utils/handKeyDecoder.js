/**
 * 手键边沿 → 点划/间隔事件的纯状态机。
 *
 * useTraffic.handKeyEvents 的唯一实现，抽出来是为了无 Vue 依赖可单测
 * （test/microPressTolerance.test.mjs）。行为口径：
 * - 重复按下在 max(2000, lineLimit*8) 窗口内忽略（dupDown 容忍）；
 * - ≤10ms 按压整拍作废（硬件抖动）：不产生点划，并把被假按下吞掉的上一拍
 *   抬起时刻还回去，让下一次真实按下重新量出完整间隔——不还回时
 *   成字定时器被假按下清排、间隔被劈半，两字粘连成错码（2026-09-16 故障矩阵实测）；
 * - 缺抬起在下一次新按下时自恢复（missingUp）。
 */
export function createHandKeyDecoder({enabled, threshold, lineLimit, report = () => {}, emit, onPressChange = () => {}}) {
  let down = null, up = null, clock = null
  let downReceivedAt = null
  // 本次按下吞掉的上一拍抬起时刻（微按压丢弃时还回）
  let upConsumedByDown = null

  const reset = () => {
    down = null
    up = null
    onPressChange(false)
  }

  const receive = data => {
    if (data.t !== 0 || ![0, 1].includes(data.k)) return
    if (!enabled()) { reset(); return }
    const time = Number(data.d)
    if (!Number.isFinite(time)) return
    if (clock !== data.timeSource) { reset(); clock = data.timeSource }
    if (data.k === 0) {
      if (down !== null) {
        // Ignore repeated down edges; recover a missing up on the next fresh press.
        if (time - down <= Math.max(2000, lineLimit() * 8)) return
        console.warn('Hand key missing release; recovering on fresh press')
        reset()
      }
      onPressChange(true)
      down = time
      downReceivedAt = data.receivedAt
      upConsumedByDown = up
      if (up !== null && time >= up) {
        const gapTime = Object.freeze([up, time])
        report(2, time - up)
        emit({code: -1, diffTime: Object.freeze([]), gapTime, receivedAt: data.receivedAt, timeSource: data.timeSource})
      }
      up = null
      return
    }
    onPressChange(false)
    const start = down
    down = null
    if (start === null || time < start) { up = null; return }
    const duration = time - start
    if (duration <= 10) {
      // 微按压（硬件抖动）整拍作废：不产生点划，还回上一拍抬起时刻
      up = upConsumedByDown
      return
    }
    up = time
    const code = duration <= threshold() ? 0 : 1
    report(duration > lineLimit() ? 11 : code, duration)
    emit({code, diffTime: Object.freeze([start, time]), gapTime: Object.freeze([]), receivedAt: data.receivedAt, startedAt: downReceivedAt, timeSource: data.timeSource})
  }

  return {receive, reset}
}
