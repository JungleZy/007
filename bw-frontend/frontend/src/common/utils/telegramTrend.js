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
