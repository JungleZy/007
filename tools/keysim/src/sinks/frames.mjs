/**
 * 帧层 sink：产出 publishTrafficFrame / 18765 桥接共用的帧对象
 * （WebSerial.js:77-94 与 MessageWebSocket.js:148-155 汇合到同一个 traffic:frame 主题）。
 *
 * 默认不声明 receivedAt：publishTrafficFrame 会用页面自己的 performance.now() 打标
 * （useTraffic.js:11），这与真实桥接一致，也让采集区间落在页面时钟上。
 * 点划时长只取 d 的差值（useTraffic.js:63），所以 d 用模拟器自己的时钟即可精确到毫秒，
 * 不受投递抖动影响。
 */

export function toFrames(timeline, {base = 0, declareReceivedAt = false, timeSource = null, pack = false} = {}) {
  const frames = []
  for (const event of timeline.events) {
    const at = base + event.at
    const previous = frames[frames.length - 1]
    if (event.kind === 'code') {
      if (pack && previous && previous.at === at && Array.isArray(previous.frame.d)) {
        previous.frame.d.push(event.code)
        continue
      }
      frames.push({at, kind: 'code', fault: event.fault, frame: {t: 1, k: 0, d: [event.code]}})
      continue
    }
    frames.push({at, kind: event.kind, fault: event.fault, frame: {t: 0, k: event.kind === 'down' ? 0 : 1, d: at}})
  }
  for (const item of frames) {
    if (declareReceivedAt) item.frame.receivedAt = item.at
    if (timeSource) item.frame.timeSource = timeSource
  }
  return frames
}

/** 桥接线上的完整报文：`{data:<帧>}`（MessageWebSocket.js:132-133 只取 parsed.data）。 */
export const toBridgeMessages = (timeline, options) =>
  toFrames(timeline, options).map(item => ({at: item.at, text: JSON.stringify({data: item.frame})}))
