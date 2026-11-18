import {HAND_KEY_CONTROL_PATTERNS} from './handKeyControlCodes.js'

// 改错（alter/next）与换页（句号，turn）是六拍长符号，人工拍发时最容易被
// 识别成'#'：①某一拍按得偏长/偏短，越过点划分界；②中途停顿稍长，被拆成
// 几个无效字。这两类只对这三个符号放宽；开始、结束符保持严格匹配。
const TOLERANT_ACTIONS = new Set(['alter', 'next', 'turn'])

// 点划分界允许在 threshold×(1±0.3) 内按本符号自身的按压时长重新划定。
export const CONTROL_TIMING_TOLERANCE = 0.3
// 容错合并最多并入此前两个候选字（如句号被拆成三截“..”）。页面至少保留这么多候选。
const MAX_MERGED_CANDIDATES = 2
// 页面保留的候选字个数：整组句号需要回看三截。
export const CONTROL_CANDIDATE_LIMIT = 3

const singlePatterns = HAND_KEY_CONTROL_PATTERNS.filter(([action, parts]) => TOLERANT_ACTIONS.has(action) && parts.length === 1)

/**
 * 是否存在一条分界 T（threshold×(1±tolerance) 以内），使“时长≤T为点、>T为划”恰好得到 pattern。
 * 原分界本身在区间内，所以精确匹配一定成立；明显的划不会被当成点，反之亦然。
 */
export function fitsControlTiming(durations, pattern, threshold, tolerance = CONTROL_TIMING_TOLERANCE) {
	if (durations.length !== pattern.length || !(threshold > 0)) return false
	let maxDot = 0
	let minDash = Infinity
	for (let i = 0; i < pattern.length; i++) {
		const duration = Number(durations[i])
		if (!Number.isFinite(duration)) return false
		if (pattern[i] === '0') maxDot = Math.max(maxDot, duration)
		else minDash = Math.min(minDash, duration)
	}
	const lowest = Math.max(maxDot, threshold * (1 - tolerance))
	return lowest <= threshold * (1 + tolerance) && lowest < minDash
}

/**
 * 识别手键控制符。返回 {action, used}：used 为需要撤销并并入本符号的此前候选字个数；无匹配返回 null。
 * - 先按原口径严格匹配（多截控制符的前几截必须独立成组）。
 * - 严格匹配失败、且本字不是合法字符时，才对改错/句号容错：可并入紧邻的、同样无效（'#'）的候选字，
 *   按时长重判点划。合法字符（如字母报的 I='00'、H='0000'）绝不并入，避免正文被误判成换页。
 *   同时满足多个控制符（如'000100'既像句号又像改错）时视为有歧义，不识别。
 */
export function matchHandKeyControl({code, signal, candidates, isCharacter, threshold, tolerance = CONTROL_TIMING_TOLERANCE}) {
	const strict = HAND_KEY_CONTROL_PATTERNS.find(([, parts]) => {
		if (parts[parts.length - 1] !== code || parts.length > candidates.length + 1) return false
		return parts.slice(0, -1).every((part, i) => {
			const candidate = candidates[candidates.length - parts.length + 1 + i]
			return candidate.code === part && candidate.groupEnd === true
		})
	})
	if (strict) return {action: strict[0], used: strict[1].length - 1}
	if (isCharacter(code)) return null

	const current = signal.map(item => item.diff)
	for (let used = 0; used <= Math.min(candidates.length, MAX_MERGED_CANDIDATES); used++) {
		const prior = candidates.slice(candidates.length - used)
		if (prior.some(candidate => !candidate.invalid)) return null
		const durations = prior.flatMap(candidate => candidate.signal.map(item => item.diff)).concat(current)
		const actions = new Set(singlePatterns
			.filter(([, [pattern]]) => fitsControlTiming(durations, pattern, threshold, tolerance))
			.map(([action]) => action))
		if (actions.size > 1) return null
		if (actions.size === 1) return {action: [...actions][0], used}
	}
	return null
}

/**
 * 句号的习惯拍法“.. .. ..”：三截之间只隔字间隔，三截自成一组。
 * 字母报、长码数码报里“..”是合法字母I，逐字无法与正文区分，只能等断组后确认：
 * 整组恰好是三个“..”才视为句号（正文按组定长，不会出现单独成组的III）。
 * 返回需要撤销的三个候选字，不是句号则返回 null。
 */
export function matchGroupedTurn(candidates, groupKeyCount) {
	if (groupKeyCount !== 3 || candidates.length < 3) return null
	const last = candidates.slice(-3)
	const sameGroup = last.every(candidate => candidate.code === '00' && !candidate.groupEnd && candidate.groupIndex === last[0].groupIndex)
	return sameGroup ? last : null
}
