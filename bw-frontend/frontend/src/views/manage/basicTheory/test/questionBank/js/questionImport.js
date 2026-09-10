const SECTION_HEADERS = ['一、单项选择题', '二、不定项选择题', '三、判断题', '四、填空题', '五、简答题']
const OPTION_PATTERN = /([A-D])\s*[、.]\s*/g

const answerText = line => {
  const index = line.search(/[:：]/)
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
  const bankId = String(levelId ?? '').trim()
  if (!bankId || bankId === '-1') throw new Error('请先选择要导入的题库')
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
      if (sectionType === 1 && indexes.length !== 1) throw new Error(`题目“${topic}”单选题只能有一个答案`)
      answer = sectionType === 1 ? String(indexes[0]) : indexes.map(String)
    } else if (sectionType === 3) {
      answer = answer === '对' ? '1' : answer === '错' ? '2' : answer
      if (!['1', '2'].includes(answer)) throw new Error(`题目“${topic}”判断答案无效`)
    } else if (sectionType === 4) {
      answer = /^答案（JSON）[:：]/.test(lines[answerIndex])
        ? JSON.parse(answer)
        : answer.split('；').map(value => value.trim())
      if (!Array.isArray(answer)) throw new Error(`题目“${topic}”填空答案须为JSON数组`)
    } else {
      answer = [answer]
    }
    questions.push({
      type: sectionType,
      topic: sectionType === 4 ? topic.replaceAll('（）', '$_$') : topic,
      options: JSON.stringify(options),
      answer: JSON.stringify(answer),
      analysis: '',
      levelId: bankId
    })
    index = answerIndex + 1
  }
  if (!questions.length) throw new Error('未识别到有效题目')
  return questions
}

export const parseSpreadsheetRows = (rows, selectedLevelId) => {
  const bankId = String(selectedLevelId ?? '').trim()
  if (!bankId || bankId === '-1') throw new Error('请先选择要导入的题库')
  if (!Array.isArray(rows) || !rows.length) throw new Error('未识别到有效题目')
  return rows.map((row, index) => {
    const rowNumber = Number.isInteger(row?.__rowNum__) ? row.__rowNum__ + 1 : index + 2
    try {
      if (!row || typeof row !== 'object') throw new Error('题目行为空')
      const type = Number(row.type)
      const topic = String(row.topic ?? '').trim()
      const fileBankId = String(row.levelId ?? '').trim()
      if (fileBankId && fileBankId !== bankId) {
        throw new Error('所属题库与当前选择不一致，请重新导出模板或清空levelId列后导入当前题库')
      }
      if (!Number.isInteger(type) || type < 1 || type > 5) throw new Error('测验类型必须是1到5')
      if (!topic) throw new Error('题目不能为空')
      let options = row.options
      if (typeof options === 'string' && options.trim().startsWith('[')) options = JSON.parse(options)
      const optionObjects = Array.isArray(options)
        ? options.map((option, optionIndex) => typeof option === 'string'
          ? {value: String(optionIndex), label: option.replace(/^[A-D][、.]\s*/, '').trim()}
          : typeof option?.value === 'number' ? {...option, value: String(option.value)} : option)
        : []
      let answer = String(row.answer ?? '').trim()
      if (type === 1 || type === 2) {
        if (!optionObjects.length || optionObjects.some((option, optionIndex) =>
          !option || typeof option.label !== 'string' || !option.label.trim() || String(option.value) !== String(optionIndex))) {
          throw new Error('选择题选项须为非空JSON数组；对象选项的value必须从0连续编号')
        }
        const indexes = /^[A-D]+$/i.test(answer)
          ? answerIndexes(answer, optionObjects.length, topic)
          : answer.split(/[,，\s]+/).filter(Boolean).map(Number)
        if (!indexes.length || indexes.some(value => !Number.isInteger(value) || value < 0 || value >= optionObjects.length)) {
          throw new Error('选择题答案必须是有效选项字母或从0开始的整数编号')
        }
        if (type === 1 && indexes.length !== 1) throw new Error('单选题只能有一个答案')
        if (new Set(indexes).size !== indexes.length) throw new Error('选择题答案不能重复')
        answer = type === 1 ? String(indexes[0]) : indexes.map(String)
      } else if (type === 3) {
        answer = answer === '对' ? '1' : answer === '错' ? '2' : answer
        if (!['1', '2'].includes(answer)) throw new Error('判断题答案必须是对、错、1或2')
      } else {
        answer = answer.startsWith('[') ? JSON.parse(answer) : [answer]
      }
      return {type, topic, options: JSON.stringify(optionObjects), answer: JSON.stringify(answer), analysis: String(row.analysis ?? ''), levelId: bankId}
    } catch (error) {
      throw new Error(`第 ${rowNumber} 行：${error.message}`, {cause: error})
    }
  })
}
