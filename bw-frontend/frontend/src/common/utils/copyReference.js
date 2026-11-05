export function formatReferenceText(groups, pageBreak = false) {
  const words = (groups || []).map(group => {
    const value = typeof group === 'string' && group.trim().startsWith('[') ? JSON.parse(group) : group
    return (Array.isArray(value) ? value.join('') : String(value ?? '')).trim()
  }).filter(word => word && word !== '#')
  const lines = []
  for (let index = 0; index < words.length; index += 10) lines.push(words.slice(index, index + 10).join(' '))
  const text = lines.join('\n')
  return text && pageBreak ? `${text}\n。` : text
}

export async function copyReferenceText(text) {
  if (navigator.clipboard?.writeText) {
    try {
      await navigator.clipboard.writeText(text)
      return
    } catch {
      // 非安全上下文或剪贴板权限受限时，尝试由本次用户点击执行传统复制。
    }
  }
  const previous = document.activeElement
  const input = document.createElement('textarea')
  input.value = text
  input.readOnly = true
  input.style.cssText = 'position:fixed;left:-9999px;top:0;opacity:0'
  document.body.appendChild(input)
  try {
    input.select()
    if (!document.execCommand('copy')) throw new Error('复制失败，请检查浏览器剪贴板权限')
  } finally {
    input.remove()
    previous?.focus({preventScroll: true})
  }
}
