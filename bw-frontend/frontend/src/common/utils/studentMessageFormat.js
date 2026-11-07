/**
 * 组训人查看学员拍发报文：把已提交页与实时推送整理成「组间空格、每行十组」的文本。
 *
 * 已提交页以服务端保存为准；当前页尚未提交，服务端没有，只能用组训人打开页面后
 * 经 WebSocket 收到的拍发记录重放，所以实时部分只覆盖打开页面之后的拍发。
 */
import useMorse from '../mixin/useMorse.js'
import {HAND_KEY_CONTROL_PATTERNS} from './handKeyControlCodes.js'

const {codeKey} = useMorse()
const PAGE_CONTROLS = ['开始', '句号', '结束', '换页', '完结']

const parseMaybeJson = value => {
  if (typeof value !== 'string') return value
  const text = value.trim()
  if (!text.startsWith('[') && !text.startsWith('{')) return value
  try { return JSON.parse(text) } catch { return value }
}

/** 组列表 → 每行十组、组间空格 */
export function formatGroupLines(groups) {
  const words = (groups || []).map(group => String(group ?? '').trim()).filter(Boolean)
  const lines = []
  for (let index = 0; index < words.length; index += 10) lines.push(words.slice(index, index + 10).join(' '))
  return lines.join('\n')
}

const joinCharacters = characters => (Array.isArray(characters) ? characters : [characters])
  .map(item => String(item ?? ''))
  .filter(item => item && !PAGE_CONTROLS.includes(item))
  .join('')

/** 手键已提交页：messageBody 每组的 patKeys 是学员端识别出的字符 */
export function handPageGroups(messageBody) {
  const groups = parseMaybeJson(messageBody)
  if (!Array.isArray(groups)) return []
  return groups.map(group => joinCharacters(parseMaybeJson(group?.patKeys) || [])).filter(Boolean)
}

/** 电子键已提交页：每行一组，value 为字符数组 */
export function electronicPageGroups(rows) {
  return [...(rows || [])]
    .sort((a, b) => (a?.sort ?? 0) - (b?.sort ?? 0))
    .map(row => joinCharacters(parseMaybeJson(row?.value) || []))
    .filter(Boolean)
}

/** 数据报已提交页：学员原样文本，保留其换行（回行是评分项），行内空白归一 */
export function datagramPageText(raw) {
  return String(raw ?? '')
    .toUpperCase()
    .split(/\r\n|\r|\n/)
    .map(line => line.trim().split(/\s+/).filter(Boolean).join(' '))
    .filter(Boolean)
    .join('\n')
}

const median = values => {
  const sorted = values.filter(value => Number.isFinite(value) && value > 0).sort((a, b) => a - b)
  if (!sorted.length) return 0
  const middle = Math.floor(sorted.length / 2)
  return sorted.length % 2 ? sorted[middle] : (sorted[middle - 1] + sorted[middle]) / 2
}

/** 手键报文类型 → useMorse 字母表（与学员端 codeCompileKeyInfo 同口径） */
export function handAlphabet(train) {
  return train?.type === 1 ? 'letter' : train?.type === 2 ? 'mix' : train?.codeSort ? 'long' : 'short'
}

/**
 * 手键实时识别：推送帧为 {code: 0点/1划, diff: 按压时长, gap: 本拍之前的间隔}。
 * 学员端的码间隔基准由开始符校准，组训人拿不到；这里取实际码内间隔的中位数作基准，
 * 断字/断组阈值与学员端相同：间隔 > 基准×(1+浮动) 断字，> 基准×(3+浮动) 断组，浮动默认 50%。
 * 开始、结束、换页、改错控制码按 HAND_KEY_CONTROL_PATTERNS 识别；结束与换页后该页已提交，实时部分从下一页重新累计。
 */
export function decodeHandLogs(logs, alphabet = 'short', {float = 50} = {}) {
  const events = (logs || []).filter(log => log && (log.code === 0 || log.code === 1))
  const gaps = events.slice(1).map(log => Number(log.gap))
  const unit = median(gaps.filter(gap => gap < median(gaps) * 2)) || median(gaps) || 80
  const wordGap = unit * (1 + float / 100)
  const groupGap = unit * (3 + float / 100)
  const table = codeKey[alphabet] || codeKey.short

  let groups = []
  let current = []
  let code = ''
  // 仅含一个字的组的码，用于识别「00 00 00」三截换页
  const singleCodeGroups = []

  const closeGroup = () => {
    if (current.length) groups.push(current.join(''))
    current = []
  }
  const applyControl = action => {
    if (action === 'start' || action === 'end' || action === 'turn') {
      groups = []
      current = []
      singleCodeGroups.length = 0
      return
    }
    current.push(action === 'alter' ? '?' : '/')
  }
  const closeCharacter = (endsGroup) => {
    if (!code) return
    const single = HAND_KEY_CONTROL_PATTERNS.find(([, parts]) => parts.length === 1 && parts[0] === code)
    if (single) {
      applyControl(single[0])
    } else {
      current.push(table[code] ?? '#')
    }
    if (endsGroup && current.length === 1 && code === '00') {
      singleCodeGroups.push(code)
      if (singleCodeGroups.length === 3) {
        groups.splice(-2, 2)
        current = []
        applyControl('turn')
        code = ''
        return
      }
    } else if (endsGroup) {
      singleCodeGroups.length = 0
    }
    code = ''
  }

  events.forEach((log, index) => {
    const gap = Number(log.gap) || 0
    if (index > 0 && gap > groupGap) {
      closeCharacter(true)
      closeGroup()
    } else if (index > 0 && gap > wordGap) {
      closeCharacter(false)
    }
    code += String(log.code)
  })
  closeCharacter(true)
  closeGroup()
  return groups
}

/**
 * 电子键实时重放：推送帧 {key}，每帧一个已识别字符；' ' 为学员换组，
 * 开始/句号/结束为页面控制，句号与结束后该页已提交，实时部分从下一页重新累计。
 */
export function replayElectronicLogs(logs) {
  let groups = []
  let current = ''
  for (const log of logs || []) {
    const key = String(log?.key ?? '')
    if (key === ' ') {
      if (current) groups.push(current)
      current = ''
    } else if (PAGE_CONTROLS.includes(key)) {
      groups = []
      current = ''
    } else if (key) {
      current += key === '/' ? '?' : key
    }
  }
  if (current) groups.push(current)
  return groups
}

/**
 * 数据报实时重放：推送帧 {key} 为键盘按键名。可见字符追加，Enter 换行，Backspace 删除，
 * 连按两次 Enter 或 F4/F5 为翻页（该页已提交），完结为交卷，实时部分随之清空。
 * 光标移动、选中替换等编辑无法从按键流还原，以已提交页为准。
 */
export function replayDatagramKeys(logs) {
  let text = ''
  let enters = 0
  for (const log of logs || []) {
    const key = String(log?.key ?? '')
    if (key === 'Enter') {
      enters++
      if (enters >= 2) { text = ''; enters = 0; continue }
      text += '\n'
      continue
    }
    enters = 0
    if (key === 'F4' || key === 'F5' || key === '完结') text = ''
    else if (key === 'Backspace') text = text.slice(0, -1)
    else if (key.length === 1) text += key
  }
  return datagramPageText(text)
}
