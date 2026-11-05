export const DEFAULT_HAND_KEY_STANDARD = Object.freeze({dot: 80, line: 240, codeGap: 80, wordGap: 240, groupGap: 400})
export const DEFAULT_HAND_KEY_SKEW = 50

// 个人岗位手键与线路通报复盘共用的点划、成字、成组判定阈值。
export function handKeyThresholds(standard, skew = DEFAULT_HAND_KEY_SKEW) {
  const offset = skew / 100
  return {
    dotDash: standard.dot * (1 + offset),
    characterGap: standard.codeGap * (1 + offset),
    groupGap: standard.codeGap * (3 + offset)
  }
}
