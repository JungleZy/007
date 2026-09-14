/**
 * 随机报文生成：字符集与报文形态按前端字母表口径来
 * （字码 A-Z、数码 0-9、混合两者；每组 4 字符，与报底一致）。
 */
import {morseCode} from './tables.mjs'
import {rng} from './timeline.mjs'

export const GROUP_SIZE = 4

/** 该字母表下所有可拍字符（直接取电码表的键，避免另写一份字符集）。 */
export function alphabetChars(alphabet) {
  const table = morseCode[alphabet]
  if (!table) throw new Error(`未知字母表 ${alphabet}`)
  return Object.keys(table).sort()
}

/**
 * 生成 groups 组、每组 GROUP_SIZE 个字符的报文，组间用空格分隔。
 * 传 seed 可复现；不传则用时间种子。
 */
export function randomMessage({alphabet = 'letter', groups = 4, seed = null, groupSize = GROUP_SIZE} = {}) {
  const chars = alphabetChars(alphabet)
  const random = rng(seed === null || seed === undefined ? (Date.now() & 0x7fffffff) : Number(seed))
  const out = []
  for (let group = 0; group < groups; group++) {
    let text = ''
    for (let index = 0; index < groupSize; index++) text += chars[Math.floor(random() * chars.length)]
    out.push(text)
  }
  return out.join(' ')
}

/** 校验手输报文：必须是空格分隔的等长组，且每个字符都在该字母表里。 */
export function validateMessage(text, alphabet = 'letter') {
  const groups = String(text).trim().split(/\s+/).filter(Boolean)
  if (!groups.length) return {ok: false, reason: '报文为空'}
  const chars = new Set(alphabetChars(alphabet))
  const unknown = [...new Set([...groups.join('')].filter(char => !chars.has(char)))]
  if (unknown.length) return {ok: false, reason: `字母表 ${alphabet} 里没有这些字符：${unknown.join(' ')}`}
  return {ok: true, groups}
}
