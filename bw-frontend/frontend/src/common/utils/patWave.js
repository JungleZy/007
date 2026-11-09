// 手键页顶部实时码条：每个点划、间隔按“毫秒/10”像素绘制，
// 宽度上下限与样式一致（._top .val 2~80px，._top .gap 0~240px）。
// 历史按绘制宽度截断而不是按条数：拍得越快单个点划越窄，固定条数会铺不满码条。
export const PAT_WAVE_KEEP_WIDTH = 3000 // px，大于全屏码条宽度并留出滚动余量

const clamp = (value, min, max) => Math.min(max, Math.max(min, Number(value) || 0))

export const patWaveWidth = code => clamp(code.diff / 10, 2, 80) + clamp(code.gap / 10, 0, 240)

// 原地删除最旧的记录，只保留恰好铺满keepWidth的最近记录。
export function trimPatWave(log, keepWidth = PAT_WAVE_KEEP_WIDTH) {
	let total = 0
	let keep = 0
	for (let i = log.length - 1; i >= 0 && total < keepWidth; i--) {
		total += patWaveWidth(log[i])
		keep++
	}
	if (keep < log.length) log.splice(0, log.length - keep)
	return log
}
