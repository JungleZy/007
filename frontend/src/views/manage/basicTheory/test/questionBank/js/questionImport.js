const SECTION_HEADERS = ['一、单项选择题', '二、不定项选择题', '三、判断题', '四、填空题', '五、简答题']
const OPTION_PATTERN = /([A-D])\s*[、.]\s*/g

const answerText = line => {
  const separator = line.includes('：') ? '：' : ':'
  const index = line.indexOf(separator)
  return (index < 0 ? line : line.slice(index + 1)).trim()
}

const parseOptions = line => {
  const matches = [...line.matchAll(OPTION_PATTERN)]
  return matches.map((match, index) => ({
    value: String(index),
    label: line.slice(match.index + match[0].length, matches[index + 1]?.index ?? line.length).trim()
  }))
}

const answerIndexes = (answer, optionCount, topic) => {
  const letters = [...answer.toUpperCase()].filter(letter => letter.trim())
  if (!letters.length || letters.some(letter => letter < 'A' || letter > 'D')) {
    throw new Error(`题目“${topic}”答案无效`)
  }
  const indexes = letters.map(letter => letter.charCodeAt(0) - 'A'.charCodeAt(0))
  if (indexes.some(index => index >= optionCount)) throw new Error(`题目“${topic}”答案超出选项范围`)
  return indexes
}

export const parseWordQuestions = (text, levelId) => {
  if (!levelId || levelId === '-1') throw new Error('请先选择要导入的题库')
  if (typeof text !== 'string' || !text.trim()) throw new Error('文档内容为空')
  const lines = text.replace(/\r\n?/g, '\n').split('\n').map(line => line.trim()).filter(Boolean)
  const questions = []
  let sectionType = 0
  for (let index = 0; index < lines.length;) {
    const headerIndex = SECTION_HEADERS.findIndex(header => lines[index].includes(header))
    if (headerIndex >= 0) {
      sectionType = headerIndex + 1
      index++
      continue
    }
    if (sectionType === 0) {
      index++
      continue
    }
    if (!/^\d+[、.]\s*/.test(lines[index])) throw new Error(`第 ${index + 1} 行格式无法识别`)
    const topic = lines[index].replace(/^\d+[、.]\s*/, '').trim()
    const hasOptions = sectionType === 1 || sectionType === 2
    let answerIndex = index + 1
    while (answerIndex < lines.length && !lines[answerIndex].includes('答案') &&
        !/^\d+[、.]\s*/.test(lines[answerIndex]) &&
        !SECTION_HEADERS.some(header => lines[answerIndex].includes(header))) {
      answerIndex++
    }
    if (answerIndex >= lines.length || !lines[answerIndex].includes('答案')) {
      throw new Error(`题目“${topic}”缺少答案`)
    }
    const optionLine = hasOptions ? lines.slice(index + 1, answerIndex).join(' ') : ''
    const options = hasOptions
      ? parseOptions(optionLine)
      : sectionType === 3 ? [{value: '1', name: '对'}, {value: '2', name: '错'}] : []
    if (hasOptions && options.length < 2) throw new Error(`题目“${topic}”选项不足 2 个`)
    let answer = answerText(lines[answerIndex])
    if (sectionType === 1 || sectionType === 2) {
      const indexes = answerIndexes(answer, options.length, topic)
      answer = sectionType === 1 ? String(indexes[0]) : indexes.map(String)
    } else if (sectionType === 3) {
      answer = answer === '对' ? '1' : answer === '错' ? '2' : answer
      if (!['1', '2'].includes(answer)) throw new Error(`题目“${topic}”判断答案无效`)
    } else {
      answer = [answer]
    }
    questions.push({
      type: sectionType,
      topic: sectionType === 4 ? topic.replaceAll('（）', '$_$') : topic,
      options: JSON.stringify(options),
      answer: JSON.stringify(answer),
      analysis: '',
      levelId
    })
    index = answerIndex + 1
  }
  if (!questions.length) throw new Error('未识别到有效题目')
  return questions
}

export const parseSpreadsheetRows = (rows, defaultLevelId) => rows.map(row => {
  const type = Number(row.type)
  const topic = String(row.topic ?? '').trim()
  const levelId = String(row.levelId || defaultLevelId || '').trim()
  if (!Number.isInteger(type) || type < 1 || type > 5) throw new Error('测验类型必须是 1 到 5')
  if (!topic || !levelId) throw new Error('题目和所属题库不能为空')
  let options = row.options
  if (typeof options === 'string' && options.trim().startsWith('[')) options = JSON.parse(options)
  const optionObjects = Array.isArray(options)
    ? options.map((option, index) => typeof option === 'string'
      ? {value: String(index), label: option.replace(/^[A-D][、.]\s*/, '')}
      : option)
    : []
  let answer = String(row.answer ?? '').trim()
  if (type === 1 || type === 2) {
    const indexes = answer.match(/^[A-D]+$/i)
      ? answerIndexes(answer, optionObjects.length, topic)
      : answer.split(/[,，\s]+/).filter(Boolean).map(value => String(Number(value)))
    if (!indexes.length || indexes.some(value => Number(value) < 0 || Number(value) >= optionObjects.length)) {
      throw new Error(`题目“${topic}”答案无效`)
    }
    answer = type === 1 ? String(indexes[0]) : indexes.map(String)
  } else if (type === 3) {
    answer = answer === '对' ? '1' : answer === '错' ? '2' : answer
    if (!['1', '2'].includes(answer)) throw new Error(`题目“${topic}”判断答案无效`)
  } else {
    answer = answer.startsWith('[') ? JSON.parse(answer) : [answer]
  }
  return {type, topic, options: JSON.stringify(optionObjects), answer: JSON.stringify(answer), analysis: String(row.analysis ?? ''), levelId}
})
