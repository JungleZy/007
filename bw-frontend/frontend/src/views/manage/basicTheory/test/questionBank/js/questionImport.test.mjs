import assert from 'node:assert/strict'
import test from 'node:test'
import {parseSpreadsheetRows, parseWordQuestions} from './questionImport.js'
import * as XLSX from 'xlsx'
import {createQuestionWorkbook, createQuestionTemplateWorkbook} from './questionExport.js'

test('template needs no bank and provides importable examples for every question type', () => {
  const columns = ['type', 'topic', 'options', 'answer', 'analysis', 'levelId'].map(field => ({field, title: field}))
  const workbook = createQuestionTemplateWorkbook(columns)
  const restored = XLSX.read(XLSX.write(workbook, {type: 'buffer', bookType: 'xlsx'}), {type: 'buffer'})
  const rows = XLSX.utils.sheet_to_json(restored.Sheets['题库模板'], {defval: ''})
  assert.equal(rows.length, 5)
  assert.ok(rows.every(row => !Object.hasOwn(row, 'levelId')))
  assert.equal(rows[2]['正确答案'], '对')
  assert.equal(rows[0]['选项B'], '3')
  for (const bankId of ['bank-a', 'bank-b']) {
    const questions = parseSpreadsheetRows(rows, bankId)
    assert.deepEqual(questions.map(question => question.type), [1, 2, 3, 4, 5])
    assert.ok(questions.every(question => question.levelId === bankId))
    assert.deepEqual(questions.map(question => JSON.parse(question.answer)), ['1', ['0', '2'], '1', ['12', '7'], ['有线通信、无线通信。']])
  }
})

const choice = {
  type: 2,
  topic: '多选题',
  options: '["A.甲","B.乙","C.丙"]',
  answer: 'AC',
  levelId: 'bank-a'
}

test('spreadsheet choices use the option identifiers consumed by question rendering', () => {
  const [question] = parseSpreadsheetRows([choice], 'bank-a')
  const answer = JSON.parse(question.answer)
  assert.deepEqual(answer, ['0', '2'])
  const selected = JSON.parse(question.options)
    .filter(option => answer.includes(option.value))
    .map(option => option.label)
  assert.deepEqual(selected, ['甲', '丙'])
})

test('a nonnumeric answer cannot become a persisted NaN option identifier', () => {
  assert.throws(() => parseSpreadsheetRows([{...choice, answer: 'not-a-number'}], 'bank-a'))
})

test('a workbook cannot redirect import away from the selected question bank', () => {
  assert.throws(() => parseSpreadsheetRows([choice], 'bank-b'))
})

test('DOCX fill-in answers preserve multiple blanks and literal separators', () => {
  const [question] = parseWordQuestions('四、填空题\n1、含有$_$和$_$\n答案（JSON）：["甲；乙","丙"]', 'bank-a')
  assert.deepEqual(JSON.parse(question.answer), ['甲；乙', '丙'])
  const [delimited] = parseWordQuestions('四、填空题\n1、含有$_$和$_$\n答案：甲；乙', 'bank-a')
  assert.deepEqual(JSON.parse(delimited.answer), ['甲', '乙'])
})

test('ordinary bracket-leading DOCX answers remain literal text', () => {
  const [range] = parseWordQuestions('四、填空题\n1、范围为$_$\n答案：[A-Z]', 'bank-a')
  assert.deepEqual(JSON.parse(range.answer), ['[A-Z]'])
  const [interval] = parseWordQuestions('四、填空题\n1、区间为$_$\n答案：[0,1]', 'bank-a')
  assert.deepEqual(JSON.parse(interval.answer), ['[0,1]'])
})

test('numeric object option identifiers still match normalized choice answers', () => {
  const [question] = parseSpreadsheetRows([{...choice, options: [{value: 0, label: '甲'}, {value: 1, label: '乙'}], answer: 'A'}], 'bank-a')
  const answer = JSON.parse(question.answer)
  const selected = JSON.parse(question.options).filter(option => answer.includes(option.value))
  assert.deepEqual(selected.map(option => option.label), ['甲'])
})

test('Excel export can be reimported with all five question types and analyses intact', () => {
  const options = JSON.stringify([{value: '0', label: '甲'}, {value: '1', label: '乙'}])
  const questions = [
    {type: 1, topic: '单选', options, answer: '"0"'},
    {type: 2, topic: '多选', options, answer: '["0","1"]'},
    {type: 3, topic: '判断', options: '[]', answer: '"2"'},
    {type: 4, topic: '填写$_$及$_$', options: '[]', answer: '["甲；乙","[0,1]"]'},
    {type: 5, topic: '=这是题干文本', options: '[]', answer: '["第一行\\n第二行\\n001"]'}
  ].map(question => ({...question, analysis: '解析\n保留换行', levelId: '00123'}))
  const original = structuredClone(questions)
  const workbook = createQuestionWorkbook(questions)
  const bytes = XLSX.write(workbook, {type: 'buffer', bookType: 'xlsx'})
  const restored = XLSX.read(bytes, {type: 'buffer'})
  const sheet = restored.Sheets[restored.SheetNames[0]]
  const rows = XLSX.utils.sheet_to_json(sheet, {defval: ''})
  assert.deepEqual(parseSpreadsheetRows(rows, '00123'), original)
  assert.deepEqual(questions, original)
  assert.equal(sheet.B6.t, 's')
  assert.equal(sheet.B6.f, undefined)
  assert.equal(rows[2]['正确答案'], '错')
  assert.equal(rows[1]['正确答案'], 'A、B')
  assert.ok(parseSpreadsheetRows(rows.map(row => ({...row, levelId: ''})), 'another-bank')
    .every(question => question.levelId === 'another-bank'))
})
