// Display-only character alignment within each original form cell. Never use as a score.
const MAX_ALIGNMENT_CELLS = 65536

function alignSequence(expected, actual) {
  const width = actual.length + 1
  if ((expected.length + 1) * width > MAX_ALIGNMENT_CELLS) {
    throw new RangeError('本页内容超出对齐显示范围，请查看原始报底和答案')
  }
  const costs = new Uint32Array((expected.length + 1) * width)
  for (let i = 0; i <= expected.length; i++) costs[i * width] = i
  for (let j = 0; j <= actual.length; j++) costs[j] = j
  for (let i = 1; i <= expected.length; i++) {
    for (let j = 1; j <= actual.length; j++) {
      costs[i * width + j] = Math.min(
        costs[(i - 1) * width + j] + 1,
        costs[i * width + j - 1] + 1,
        costs[(i - 1) * width + j - 1] + (expected[i - 1] === actual[j - 1] ? 0 : 1)
      )
    }
  }
  const operations = []
  let i = expected.length
  let j = actual.length
  while (i || j) {
    const equal = i > 0 && j > 0 && expected[i - 1] === actual[j - 1]
    if (i && j && costs[i * width + j] === costs[(i - 1) * width + j - 1] + (equal ? 0 : 1)) {
      operations.push({ type: equal ? 'equal' : 'replace', expected: expected[--i], actual: actual[--j], expectedIndex: i, actualIndex: j })
    } else if (i && costs[i * width + j] === costs[(i - 1) * width + j] + 1) {
      operations.push({ type: 'delete', expected: expected[--i], actual: '', expectedIndex: i, actualIndex: null })
    } else {
      operations.push({ type: 'insert', expected: '', actual: actual[--j], expectedIndex: null, actualIndex: j })
    }
  }
  return operations.reverse()
}

export function alignResultPage(result) {
  const expected = Array.isArray(result?.pageVos) ? result.pageVos : []
  const actual = Array.isArray(result?.value) ? result.value : []
  // 上传的数组下标就是填写格位置，空白格也必须保留；不能跨格重排答案。
  const groups = Array.from({length: Math.max(expected.length, actual.length)}, (_, index) => {
    const key = String(expected[index]?.key ?? '')
    const expectedText = key === '--' ? '' : key
    const actualText = String(actual[index] ?? '')
    const type = !expectedText && !actualText ? 'empty'
      : !expectedText ? 'insert' : !actualText ? 'delete'
        : expectedText === actualText ? 'equal' : 'replace'
    return {
      type, expected: expectedText, actual: actualText,
      expectedIndex: expectedText ? index : null,
      actualIndex: actualText ? index : null,
      characters: alignSequence(Array.from(expectedText), Array.from(actualText))
    }
  })
  return {groups, extraPage: !groups.some(group => group.expected) && groups.some(group => group.actual)}
}
