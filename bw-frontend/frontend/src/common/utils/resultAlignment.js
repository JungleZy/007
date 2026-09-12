// Display-only, page-local edit alignment. Never use these operations as a score.
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
  const expected = (Array.isArray(result?.pageVos) ? result.pageVos : [])
    .map((group, index) => ({ text: String(group?.key ?? ''), index }))
    .filter(group => group.text !== '' && group.text !== '--')
  // Empty form cells are not telegram groups; retain original indices for display.
  const actual = (Array.isArray(result?.value) ? result.value : [])
    .map((text, index) => ({ text: String(text ?? ''), index }))
    .filter(group => group.text !== '')
  const groups = alignSequence(expected.map(group => group.text), actual.map(group => group.text))
    .map(group => ({
      ...group,
      expectedIndex: group.expectedIndex === null ? null : expected[group.expectedIndex].index,
      actualIndex: group.actualIndex === null ? null : actual[group.actualIndex].index,
      characters: alignSequence(Array.from(group.expected), Array.from(group.actual))
    }))
  return { groups, extraPage: expected.length === 0 && Array.isArray(result?.value) && result.value.length > 0 }
}
