/**
 * 字节层 sink：还原真实串口线上的帧。
 * 协议同 WebSerial.handleData（WebSerial.js:69-98）：
 * - 首字节 1 = 按下，整帧 2 字节；
 * - 首字节 2 = 抬起，整帧 3 字节；
 * - 首字节落在 CODES=[11..45] = 电子键单字节码，整帧 1 字节；
 * - 其余首字节告警跳过。
 * 填充字节内容不参与解析（客户端只按帧长跳过），统一填 0。
 */
import {chunkBytes} from '../faults.mjs'

export const DOWN = Object.freeze([1, 0])
export const UP = Object.freeze([2, 0, 0])

export function toBytes(timeline) {
  return timeline.events.map(event => {
    if (event.kind === 'down') return {at: event.at, bytes: Uint8Array.from(DOWN), kind: event.kind, fault: event.fault}
    if (event.kind === 'up') return {at: event.at, bytes: Uint8Array.from(UP), kind: event.kind, fault: event.fault}
    return {at: event.at, bytes: Uint8Array.of(event.code), kind: event.kind, fault: event.fault}
  })
}

/** 帧 -> 按分包方式切好的 read 序列（'exact' | 'split' | 'merge' | 'random'）。 */
export function toByteStream(timeline, {mode = 'exact', window = 0, seed = 1} = {}) {
  return chunkBytes(toBytes(timeline), {mode, window, seed})
}

export const toHex = chunks => chunks.map(chunk => [...chunk.bytes].map(byte => byte.toString(16).padStart(2, '0')).join(' '))
