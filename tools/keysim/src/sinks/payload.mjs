/**
 * 载荷层 sink：直接产出后端 uploadResult 的请求体。
 *
 * 服务端不信客户端算的码率与用时，只信 captureIntervals，并校验逐符原始时长之和
 * 不得超过采集区间（手键 GeneralTickerPatService.java:596-600 + measurePage:837-873，
 * 电子键 GeneralKeyPatService.java:517-539）。采集区间的上界是
 * captureBound = 服务端时钟里距 captureStartedAt 的毫秒数（:813-816），
 * 所以时间轴要么实时回放，要么用 fitScale 压到真实可用窗口内。
 */

const floor = value => Math.max(0, Math.floor(value))
const ceil = value => Math.max(0, Math.ceil(value))
const ms = value => Math.round(value * 1000) / 1000

/** 把时间轴压进真实可用的 elapsedMs 窗口；返回 ≤1 的缩放系数。 */
export function fitScale(timeline, elapsedMs, {margin = 20} = {}) {
  const usable = elapsedMs - margin
  if (!Number.isFinite(usable) || usable <= 0) throw new Error(`可用采集窗口 ${elapsedMs}ms 太短`)
  return Math.min(1, usable / Math.max(1, timeline.duration))
}

const bodyChars = timeline => timeline.chars.filter(char => char.kind === 'char')

/** 采集区间：首字按下到末事件，落在服务端 elapsed 坐标上（useTrainingCapture.js:44-52）。 */
export function captureIntervalsFor(timeline, {serverElapsedMs = 0, scale = 1, startAt = null, endAt = null} = {}) {
  const chars = bodyChars(timeline)
  if (!chars.length) return []
  const from = startAt ?? chars[0].startedAt
  const to = endAt ?? timeline.duration
  const startedMs = floor(serverElapsedMs + from * scale)
  const endedMs = ceil(serverElapsedMs + to * scale)
  return endedMs > startedMs ? [{startedMs, endedMs}] : []
}

const stringifyItem = item => {
  const copy = {...item}
  for (const field of ['moresTime', 'moresValue', 'patKeys', 'patLogs', 'moresKey']) {
    if (typeof copy[field] !== 'string') copy[field] = JSON.stringify(copy[field] ?? [])
  }
  return copy
}

/**
 * 手键 uploadResult 请求体，字段与构造顺序同 handKeyTrain.js:632-641。
 * page 传 findPage 返回的本页报底项（messageKey），未拍到的组保留原值 + 空数组。
 */
export function handUploadPayload({
  timeline,
  trainId,
  floorNumber = 1,
  page = [],
  capture = {},
  scale = 1,
  offSize = 50
} = {}) {
  const plan = timeline.plan ?? {}
  const groups = new Map()
  bodyChars(timeline).forEach(char => {
    const index = char.groupIndex ?? 0
    if (!groups.has(index)) groups.set(index, {patKeys: [], moresValue: [], moresTime: [], patLogs: []})
    const group = groups.get(index)
    group.patKeys.push(char.value)
    group.moresValue.push(char.codes)
    group.moresTime.push(char.durations.map(duration => ms(duration * scale)))
    const logs = []
    if (char.leadingGap) logs.push({name: '间隔', key: 2, value: ms(char.leadingGap * scale)})
    char.codes.forEach((code, index) => {
      logs.push({name: code === 1 ? '划' : '点', key: code, value: ms(char.durations[index] * scale)})
      const gap = char.gaps[index]
      if (gap !== undefined) logs.push({name: '间隔', key: 2, value: ms(gap * scale)})
    })
    group.patLogs.push(logs)
  })

  const total = Math.max(page.length, groups.size ? Math.max(...groups.keys()) + 1 : 0)
  const messageBody = []
  for (let index = 0; index < total; index++) {
    const keyed = groups.get(index)
    messageBody.push(stringifyItem({
      moresKey: '#',
      patKeys: [],
      moresValue: [],
      moresTime: [],
      patLogs: [],
      ...page[index],
      ...(keyed ?? {})
    }))
  }

  const standard = {
    dot: Math.round(plan.dot * scale),
    line: Math.round(plan.dash * scale),
    codeGap: Math.round(plan.gap * scale),
    wordGap: Math.round(plan.word * scale),
    groupGap: Math.round(plan.group * scale),
    offSize
  }
  return {
    trainId,
    floorNumber,
    messageBody,
    attempt: capture.attempt,
    captureIntervals: captureIntervalsFor(timeline, {serverElapsedMs: capture.serverElapsedMs ?? 0, scale}),
    standard: [standard],
    finishInfo: JSON.stringify({...standard, patLogs: []})
  }
}

/**
 * 电子键 uploadResult 请求体，字段与构造顺序同 electronKeyZuXun 学生端 handKeyTrain.js:467-473。
 * time 是逐字有效用时（capture.between），服务端按 Σtime ≤ 采集区间校验。
 */
export function electronUploadPayload({
  timeline,
  trainId,
  pageNumber = 1,
  page = [],
  capture = {},
  scale = 1
} = {}) {
  const groups = new Map()
  let previousEnd = null
  bodyChars(timeline).forEach(char => {
    const index = char.groupIndex ?? 0
    if (!groups.has(index)) groups.set(index, {value: [], time: []})
    const group = groups.get(index)
    group.value.push(char.value)
    const span = previousEnd === null ? 0 : char.endedAt - previousEnd
    previousEnd = char.endedAt
    group.time.push(Math.round(span * scale))
  })

  const pageValue = [...groups.entries()].sort((a, b) => a[0] - b[0]).map(([sort, group]) => ({
    id: null,
    pageNumber,
    sort,
    trainId,
    ...page[sort],
    key: JSON.stringify(page[sort]?.key ?? ['#']),
    value: JSON.stringify(group.value),
    time: JSON.stringify(group.time)
  }))

  return {
    trainId,
    pageNumber,
    pageValue,
    attempt: capture.attempt,
    captureIntervals: captureIntervalsFor(timeline, {serverElapsedMs: capture.serverElapsedMs ?? 0, scale})
  }
}
