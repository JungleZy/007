/**
 * 故障注入。这些不是臆造的畸形输入，而是评审已记录的真实串口故障模式
 * （docs/plans/2026-09-10-customer-issue-fix-plan.md T05、docs/reviews/2026-09-10-customer-issue-analysis.md §6）：
 * 分包/粘包、重复按下、缺抬起、未知字节、≤10ms 抖动。
 * 时间轴级故障改事件序列，字节级故障只改分包方式（见 sinks/bytes.mjs）。
 */
import {rng} from './timeline.mjs'

/** 客户端对重复按下的容忍窗口：max(2000, lineLimit*8)（useTraffic.js:44）。 */
export const DUP_DOWN_WINDOW = 2000

const clone = timeline => ({...timeline, events: timeline.events.map(event => ({...event}))})
const round = value => Math.round(value * 1000) / 1000

/**
 * 在第 index 个按压前插入一次重复按下（无抬起）。
 * 客户端应忽略它并继续用真正那次按下的时刻计时（useTraffic.js:42-47）。
 */
export function dupDown(timeline, {index = 0, offset = 5} = {}) {
  const next = clone(timeline)
  const target = next.events.filter(event => event.kind === 'down')[index]
  if (!target) throw new Error(`时间轴里没有第 ${index} 次按下`)
  next.events.push({at: round(Math.max(0, target.at - offset)), kind: 'down', fault: 'dup-down'})
  next.events.sort((a, b) => a.at - b.at)
  return Object.freeze(next)
}

/** 丢掉第 index 次抬起：客户端要在下一次新按下时自恢复。 */
export function missingUp(timeline, {index = 0} = {}) {
  const next = clone(timeline)
  const target = next.events.filter(event => event.kind === 'up')[index]
  if (!target) throw new Error(`时间轴里没有第 ${index} 次抬起`)
  next.events = next.events.filter(event => event !== target)
  return Object.freeze(next)
}

/** 插入一次 ≤10ms 的抖动按压：必须被丢弃且不污染基准（useTraffic.js:64）。 */
export function microPress(timeline, {at = 0, duration = 6} = {}) {
  const next = clone(timeline)
  next.events.push({at: round(at), kind: 'down', fault: 'micro-press'})
  next.events.push({at: round(at + duration), kind: 'up', fault: 'micro-press'})
  next.events.sort((a, b) => a.at - b.at)
  return Object.freeze(next)
}

/** 插入一个既非 1/2 也不在 CODES 里的字节：客户端应告警跳过而不静默丢帧（WebSerial.js:86-89）。 */
export function unknownByte(timeline, {at = 0, byte = 7} = {}) {
  const next = clone(timeline)
  next.events.push({at: round(at), kind: 'code', code: byte, fault: 'unknown-byte'})
  next.events.sort((a, b) => a.at - b.at)
  return Object.freeze(next)
}

export const TIMELINE_FAULTS = Object.freeze({dupDown, missingUp, microPress, unknownByte})

/** 按名字批量套用；spec 形如 {dupDown:{index:1}, microPress:{at:120}}。 */
export function applyFaults(timeline, spec = {}) {
  return Object.entries(spec).reduce((current, [name, options]) => {
    const fault = TIMELINE_FAULTS[name]
    if (!fault) throw new Error(`未知故障 ${name}，可用：${Object.keys(TIMELINE_FAULTS)}`)
    return fault(current, options === true ? undefined : options)
  }, timeline)
}

/**
 * 字节分包策略：
 * - 'exact'  一帧一次 read（理想串口）
 * - 'split'  每帧拆成单字节多次 read（分包）
 * - 'merge'  把同一时刻附近的帧并成一次 read（粘包）
 * - 'random' 由 seed 决定的混合切分
 */
export function chunkBytes(frames, {mode = 'exact', window = 0, seed = 1} = {}) {
  if (mode === 'exact') return frames.map(frame => ({at: frame.at, bytes: frame.bytes}))
  if (mode === 'split') {
    return frames.flatMap(frame => [...frame.bytes].map(byte => ({at: frame.at, bytes: Uint8Array.of(byte)})))
  }
  if (mode === 'merge') {
    const chunks = []
    for (const frame of frames) {
      const last = chunks[chunks.length - 1]
      if (last && frame.at - last.at <= window) {
        last.bytes = Uint8Array.from([...last.bytes, ...frame.bytes])
        continue
      }
      chunks.push({at: frame.at, bytes: Uint8Array.from(frame.bytes)})
    }
    return chunks
  }
  if (mode === 'random') {
    const random = rng(seed)
    const chunks = []
    for (const frame of frames) {
      const last = chunks[chunks.length - 1]
      const roll = random()
      if (last && roll < 0.34 && frame.at - last.at <= Math.max(window, 1)) {
        last.bytes = Uint8Array.from([...last.bytes, ...frame.bytes])
      } else if (roll < 0.67) {
        for (const byte of frame.bytes) chunks.push({at: frame.at, bytes: Uint8Array.of(byte)})
      } else {
        chunks.push({at: frame.at, bytes: Uint8Array.from(frame.bytes)})
      }
    }
    return chunks
  }
  throw new Error(`未知分包方式 ${mode}`)
}
