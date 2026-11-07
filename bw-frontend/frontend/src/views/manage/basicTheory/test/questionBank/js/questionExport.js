import * as XLSX from 'xlsx'

const typeNames = ['单选题', '多选题', '判断题', '填空题', '简答题']
const letter = index => String.fromCharCode(65 + Number(index))

export const createQuestionWorkbook = (questions, isTemplate = false) => {
  const parsed = questions.map(question => ({...question,
    options: JSON.parse(question.options || '[]'), answer: JSON.parse(question.answer)}))
  const optionCount = Math.max(4, ...parsed.map(question => question.type <= 2 ? question.options.length : 0))
  const optionHeaders = Array.from({length: optionCount}, (_, index) => `选项${letter(index)}`)
  const headers = ['题型', '题目', ...optionHeaders, '正确答案', '解析']
  const rows = parsed.map(question => {
    const type = Number(question.type)
    const answer = question.answer
    const row = {'题型': typeNames[type - 1], '题目': type === 4 ? question.topic.replaceAll('$_$', '（___）') : question.topic}
    optionHeaders.forEach((header, index) => {
      row[header] = type <= 2 ? question.options[index]?.label ?? '' : ''
    })
    row['正确答案'] = type === 1 ? letter(answer)
      : type === 2 ? answer.map(letter).join('、')
        : type === 3 ? String(answer) === '1' ? '对' : '错'
          : Array.isArray(answer) ? answer.join('\n') : String(answer ?? '')
    row['解析'] = question.analysis || ''
    return row
  })
  const sheet = XLSX.utils.json_to_sheet(rows, {header: headers})
  sheet['!cols'] = headers.map(header => ({wch: header === '题型' ? 12 : header.startsWith('选项') ? 24 : 50}))
  const guide = XLSX.utils.aoa_to_sheet([
    ['项目', '填写说明'],
    ['填写位置', '在第一个工作表填写题目，每行一道题，请保留中文列名。'],
    ...(isTemplate ? [['示例使用', '五种题型各有一条示例，请替换或删除后导入；保留的示例也会导入。']] : []),
    ['题型', '填写单选题、多选题、判断题、填空题或简答题。'],
    ['选择题', '选项从A开始连续填写，每个选项单独一列；正确答案填A或A、C。选项超过D时可增加选项E等列。'],
    ['判断题', '正确答案填写对或错，无需填写选项。'],
    ['填空题', '题目用（___）标记空位；多个答案在同一个正确答案单元格中按空位顺序换行填写（Alt+Enter）。'],
    ['简答题', '正确答案直接填写参考答案，可以换行。'],
    ['解析', '填写答案说明，可以留空。'],
    ['所属题库', '下载模板无需选择节点；导入前在页面选择目标二级知识节点。'],
    ['导入规则', '只导入第一个工作表；导入会新增题目，不会覆盖原题，请避免重复导入。']
  ])
  guide['!cols'] = [{wch: 18}, {wch: 110}]
  const workbook = XLSX.utils.book_new()
  XLSX.utils.book_append_sheet(workbook, sheet, isTemplate ? '题库模板' : '题库')
  XLSX.utils.book_append_sheet(workbook, guide, '填写说明')
  return workbook
}

export const createQuestionTemplateWorkbook = () => {
  const options = JSON.stringify(['2', '3', '4', '5'].map((label, index) => ({value: String(index), label})))
  return createQuestionWorkbook([
    {type: 1, topic: '【单选示例】下列哪个数等于1加2？', options, answer: '"1"', analysis: '1加2等于3。'},
    {type: 2, topic: '【多选示例】下列哪些数是偶数？', options, answer: '["0","2"]', analysis: '2和4是偶数。'},
    {type: 3, topic: '【判断示例】地球绕太阳公转。', answer: '"1"'},
    {type: 4, topic: '【填空示例】一年有（___）个月，一周有（___）天。', answer: '["12","7"]'},
    {type: 5, topic: '【简答示例】请列举两种通信方式。', answer: '["有线通信、无线通信。"]'}
  ], true)
}
