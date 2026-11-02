export function formatDuration(value) {
  const numeric = Number(value)
  const seconds = Number.isFinite(numeric) ? Math.max(0, Math.floor(numeric)) : 0
  return [Math.floor(seconds / 3600), Math.floor(seconds / 60) % 60, seconds % 60]
    .map(part => String(part).padStart(2, '0'))
    .join(':')
}

// 综合分析接口以小时返回；先取整到秒，避免浮点运算导致少显示一秒。
export function formatHours(value) {
  return formatDuration(Math.round(Number(value) * 3600))
}
