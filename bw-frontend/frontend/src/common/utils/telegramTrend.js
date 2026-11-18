const isGap = event => Number(event?.key) === 2

/**
 * 将手键评分页保存的逐字符拍发日志整理成时间轴日志。
 *
 * messageBody 中的 patLogs 已按目标字符分组，但字符之间的间隔会记录在
 * 后一个字符的开头。这里把普通字符间隔移到前一个字符，把下一组开头的
 * 间隔移到上一组最后一个字符，交给评分页分别绘制字间隔和组间隔。
 */
export function normalizeTelegramTrendGroups(groups) {
  const result = (Array.isArray(groups) ? groups : []).map(group => ({
    ...group,
    patLog: [],
    wordGap: [],
    groupGap: []
  }))

  result.forEach((group, groupIndex) => {
    const rawWords = Array.isArray(groups[groupIndex]?.patLog)
      ? groups[groupIndex].patLog
      : []
    const words = []
    const wordGaps = []

    rawWords.forEach(rawWord => {
      const events = (Array.isArray(rawWord) ? rawWord : [])
        .filter(Boolean)
        .map(event => ({...event}))
      let firstCode = 0
      while (firstCode < events.length && isGap(events[firstCode])) firstCode++
      let lastCode = events.length
      while (lastCode > firstCode && isGap(events[lastCode - 1])) lastCode--

      const leadingGaps = events.slice(0, firstCode)
      const trailingGaps = events.slice(lastCode)
      if (leadingGaps.length > 0) {
        if (words.length > 0) {
          wordGaps[wordGaps.length - 1].push(...leadingGaps)
        } else if (groupIndex > 0) {
          const previous = result[groupIndex - 1]
          previous.groupGap.push(...leadingGaps)
        }
      }

      words.push(events.slice(firstCode, lastCode))
      wordGaps.push(trailingGaps)
    })

    group.patLog = words
    group.wordGap = wordGaps
  })

  return result
}

const CORRECTION_KEYS = ['?', '/']

const parseJson = value => {
  if (typeof value !== 'string') return value
  try {
    return JSON.parse(value)
  } catch {
    return null
  }
}

const list = value => {
  const parsed = parseJson(value)
  return Array.isArray(parsed) ? parsed : []
}

// 一个字符的点划间隔时长序列。时长是客户端单调时钟的小数毫秒，足以在一页内唯一标识这次拍发。
const signature = events => {
  const items = list(events).filter(Boolean)
  return items.length > 0
    ? items.map(event => `${Number(event.key)}:${Number(event.value)}`).join(',')
    : null
}

// 改错后真正生效的内容：最后一个改错符之后的字符。
const effectiveText = keys => {
  let start = 0
  keys.forEach((key, index) => {
    if (CORRECTION_KEYS.includes(key)) start = index + 1
  })
  return keys.slice(start).join('')
}

/**
 * 让评分页底部的拍发记录与上方报文展示区使用同一套对位结果。
 *
 * messageBody 按拍发先后把各组依次填进报底格子，中途漏行、漏组、多组后，
 * 后面的拍发都会落在错误的格子里；报文展示区用的则是结算比对后的
 * resolver.resolverMessage（下标即报底位置）。resolver.resolverPatLogs 与它同下标，
 * 保存着该位置实际采用的拍发日志，这里据此把每个原始字符归位：
 *
 * - 被某个报底位置采用的字符归该位置；同组内的改错痕迹跟随所在组；
 * - 整组被改错作废的拍发并入其后的目标组；
 * - 其余未被采用的整组是多拍，按拍发先后插在拍发记录中，不占报底位置。
 *
 * 历史记录没有 resolver 或没有拍发日志时，原样返回。
 *
 * @returns {{slots: Array, trend: Array}} slots 与报底等长，供报文展示区使用；
 *          trend 是拍发记录的展示顺序，slot 为报底下标，多拍组的 slot 为 null、extra 为 true；
 *          rawIndex 是这段拍发在 messageBody 中原本所在的组，patErr 标出与报底不符的字符。
 */
export function alignTelegramTrendGroups(groups, resolver) {
  const raw = Array.isArray(groups) ? groups : []
  const fallback = () => ({
    slots: raw,
    trend: raw.map((group, slot) => ({...group, slot, rawIndex: slot}))
  })
  const messages = resolver?.resolverMessage
  const logs = resolver?.resolverPatLogs
  if (!Array.isArray(messages) || !Array.isArray(logs)) return fallback()

  const chars = []
  const firstChar = []
  raw.forEach((group, g) => {
    firstChar[g] = chars.length
    const keys = list(group.patKeys)
    const patLog = list(group.patLog)
    const values = list(group.moresValue)
    const times = list(group.moresTime)
    keys.forEach((key, c) => {
      chars.push({
        g, key, owner: null, position: chars.length,
        patLog: list(patLog[c]),
        moresValue: list(values[c]),
        moresTime: list(times[c]),
        signature: signature(patLog[c])
      })
    })
  })
  firstChar[raw.length] = chars.length

  const find = (wanted, from) => {
    for (let index = from; index < chars.length; index++) {
      if (chars[index].signature === wanted) return index
    }
    return -1
  }

  const isCorrection = index => index < chars.length && CORRECTION_KEYS.includes(chars[index].key)
  const textOf = (start, end) => chars.slice(start, end + 1).map(item => item.key).join('')
  // 在 [from, to) 内按内容找一段未被改错作废的拍发。
  const findText = (range, from, to) => {
    const length = range.message.length
    for (let start = from; start + length <= to; start++) {
      if (textOf(start, start + length - 1) === range.message && !isCorrection(start + length)) {
        range.start = start
        range.end = start + length - 1
        return
      }
    }
  }

  // 比对按拍发先后推进，采用的日志在原始序列中只会向后出现。
  let cursor = 0
  const ranges = []
  messages.forEach((message, slot) => {
    if (!message || slot >= raw.length) return
    const range = {slot, message, start: -1, end: -1}
    list(logs[slot]).forEach(events => {
      const wanted = signature(events)
      if (wanted === null) return
      const index = find(wanted, cursor)
      if (index < 0) return
      if (range.start < 0) range.start = index
      range.end = index
      cursor = index + 1
    })
    ranges.push(range)
  })
  if (!ranges.some(range => range.start >= 0)) return fallback()

  const located = ranges.filter(range => range.start >= 0)
  // 多组时结算把第一个多拍组的日志记在命中的报底位置上；真正命中的拍发在其后、下一个位置之前。
  located.forEach((range, index) => {
    if (textOf(range.start, range.end) === range.message) return
    findText(range, range.end + 1, index + 1 < located.length ? located[index + 1].start : chars.length)
  })
  // 历史结算个别位置没有保存拍发日志，只能在前后已定位的拍发之间按内容找。
  ranges.forEach((range, index) => {
    if (range.start >= 0) return
    const previous = ranges.slice(0, index).reverse().find(item => item.start >= 0)
    const next = ranges.slice(index + 1).find(item => item.start >= 0)
    findText(range, previous ? previous.end + 1 : 0, next ? next.start : chars.length)
  })
  ranges.forEach(range => {
    if (range.start < 0) return
    const target = raw[range.slot].moresKey
    for (let index = range.start; index <= range.end; index++) {
      chars[index].owner = range.slot
      chars[index].matched = chars[index].key === target[index - range.start]
    }
  })

  // 未被采用的连续字符（不跨组）要么是改错痕迹，要么是多拍：
  // 改错后没有留下有效内容、或紧接着就被改错的是痕迹，跟随同组内的目标位置，
  // 整组作废的并入其后的目标；留下有效内容的是多拍，自成一组。
  // owner >= 0 为报底下标，owner < 0 为第 -(owner + 1) 个多拍组。
  let extraCount = 0
  for (let end = chars.length - 1; end >= 0; end--) {
    if (chars[end].owner !== null) continue
    const g = chars[end].g
    let start = end
    while (start > firstChar[g] && chars[start - 1].owner === null) start--
    const keys = chars.slice(start, end + 1).map(item => item.key)
    let owner = null
    if (effectiveText(keys) === '' || isCorrection(end + 1)) {
      if (end + 1 < firstChar[g + 1]) owner = chars[end + 1].owner
      else if (start > firstChar[g]) owner = chars[start - 1].owner
      else if (end + 1 < chars.length) owner = chars[end + 1].owner
    }
    if (owner === null) owner = -(++extraCount)
    for (let index = start; index <= end; index++) chars[index].owner = owner
    end = start
  }

  const collect = items => ({
    patKeys: items.map(item => item.key),
    // 改错痕迹与多拍一律不算拍对，被采用的字符按位与报底比较。
    patErr: items.map(item => !item.matched),
    patLog: items.map(item => item.patLog),
    patLogs: items.map(item => item.patLog),
    moresValue: items.map(item => item.moresValue),
    moresTime: items.map(item => item.moresTime)
  })
  const owned = raw.map(() => [])
  const extraChars = Array.from({length: extraCount}, () => [])
  chars.forEach(item => {
    if (item.owner >= 0) owned[item.owner].push(item)
    else extraChars[-item.owner - 1].push(item)
  })
  const slots = raw.map((group, slot) => ({
    ...group,
    ...collect(owned[slot]),
    slot,
    rawIndex: owned[slot].length > 0 ? owned[slot][0].g : slot,
    position: owned[slot].length > 0 ? owned[slot][0].position : -1
  }))
  // 多拍组是倒序编号的，翻转后即拍发先后。
  const extras = extraChars.reverse().map(items => ({
    ...raw[items[0].g],
    ...collect(items),
    moresKey: '#', slot: null, rawIndex: items[0].g, position: items[0].position, extra: true
  }))

  const trend = []
  let extra = 0
  slots.forEach(slot => {
    if (slot.position >= 0) {
      while (extra < extras.length && extras[extra].position < slot.position) trend.push(extras[extra++])
    }
    trend.push(slot)
  })
  while (extra < extras.length) trend.push(extras[extra++])

  return {slots, trend}
}
