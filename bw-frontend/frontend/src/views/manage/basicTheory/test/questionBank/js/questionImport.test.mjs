import assert from 'node:assert/strict'
import test from 'node:test'
import {parseSpreadsheetRows, parseWordQuestions} from './questionImport.js'

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
