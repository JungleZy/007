/**
 * 时间轴：模拟器唯一的中间表示。所有 sink 都只消费它。
 * 事件 {at, kind, code?}：kind 为 'down' | 'up' | 'code'，at 为相对毫秒。
 * 硬件不带时间戳（WebSerial.js:70-72 只有 JS 接收时刻），所以"何时发帧"必须由这里决定，
 * 一次性灌帧会让客户端把全部按压时长算错。
 */

/** 确定性伪随机，抖动可复现（同 seed 同结果）。 */
export function rng(seed = 1) {
  let state = seed >>> 0 || 1
  return () => {
    state = (state + 0x6D2B79F5) >>> 0
    let t = Math.imul(state ^ (state >>> 15), 1 | state)
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}

const round = value => Math.round(value * 1000) / 1000

export function createTimeline(key, plan = {}, {jitter = 0, seed = 1} = {}) {
  const events = []
  const chars = []
  const marks = []
  const random = rng(seed)
  let cursor = 0

  const jit = value => jitter <= 0 ? value : round(value * (1 + (random() * 2 - 1) * jitter))

  return {
    key,
    plan,
    events,
    chars,
    marks,
    jitter,
    get cursor() { return cursor },
    /** 空走一段时间（间隔）；返回实际走过的毫秒。 */
    wait(ms, {exact = false} = {}) {
      const applied = exact ? ms : jit(ms)
      cursor = round(cursor + applied)
      return applied
    },
    /** 一次按压：手键的按下+抬起边沿对；返回实际按压毫秒。 */
    press(ms) {
      const duration = jit(ms)
      events.push({at: round(cursor), kind: 'down'})
      cursor = round(cursor + duration)
      events.push({at: cursor, kind: 'up'})
      return duration
    },
    /** 一个电子键单字节码事件。 */
    code(value) {
      events.push({at: round(cursor), kind: 'code', code: Number(value)})
      return cursor
    },
    /** 记录一个已拍发字符，供 payload sink 派生逐字电码/用时。 */
    char(value, startedAt = cursor, detail = {}) {
      chars.push({value, startedAt: round(startedAt), endedAt: round(cursor), ...detail})
    },
    mark(name) {
      marks.push({name, at: round(cursor)})
    },
    /** 结束：事件按时刻稳定排序，便于 sink 直接顺序消费。 */
    build(extra = {}) {
      events.sort((a, b) => a.at - b.at)
      return Object.freeze({
        key, plan, jitter,
        events: Object.freeze(events),
        chars: Object.freeze(chars),
        marks: Object.freeze(marks),
        duration: events.length ? events[events.length - 1].at : 0,
        ...extra
      })
    }
  }
}

/**
 * 手键节拍可行性校验。这些不是风格偏好，是客户端判定的硬边界：
 * - dot ≤ 10ms 的按压被直接丢弃（useTraffic.js:64）；
 * - 试机要求划的时长 > 点的两倍（handKeyTrain.js:360）；
 * - 抖动必须落在评分规则 skew 的容差内，否则开始符校验不通过（handKeyTrain.js:357-359）；
 * - 翻页会触发每页重算并把 codeGap 夹到 ≥60ms（patStandard.js:41-43），此后字间隔须
 *   > 60×(1+skew/100)、组间隔须 > 60×(3+skew/100)，否则翻页后再也编译不出字码；
 *   单页训练不经历这次夹紧，用 pageTurns:false 放开该项。
 */
export function assertHandPlan(plan, {skew = 50, jitter = 0, pageTurns = true} = {}) {
  const problems = []
  const low = 1 - jitter
  const high = 1 + jitter
  if (plan.dot * low <= 10) problems.push(`点时长 ${round(plan.dot * low)}ms ≤ 10ms，会被 useTraffic.js:64 丢弃`)
  if (plan.dash * low <= plan.dot * high * 2) problems.push(`划 ${round(plan.dash * low)}ms 未超过点 ${round(plan.dot * high)}ms 的两倍，开始符校验必失败`)
  if (jitter * 100 > skew) problems.push(`抖动 ±${jitter * 100}% 超过规则容差 ±${skew}%，开始符校验必失败`)
  const clampedGap = pageTurns ? Math.max(60, plan.gap) : plan.gap
  if (plan.word <= clampedGap * (1 + skew / 100)) {
    problems.push(`字间隔 ${round(plan.word)}ms 未超过 codeGap 阈值 ${round(clampedGap * (1 + skew / 100))}ms，无法成字`)
  }
  if (plan.group <= clampedGap * (3 + skew / 100)) {
    problems.push(`组间隔 ${round(plan.group)}ms 未超过组阈值 ${round(clampedGap * (3 + skew / 100))}ms，无法成组`)
  }
  if (problems.length) throw new Error(`手键节拍不可用：\n- ${problems.join('\n- ')}`)
  return plan
}
