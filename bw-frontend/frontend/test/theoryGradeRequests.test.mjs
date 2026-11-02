import assert from 'node:assert/strict'
import test from 'node:test'
import {registerHooks} from 'node:module'
import {createRenderer, nextTick} from 'vue'
import {createMemoryHistory, createRouter} from 'vue-router'

// Exercise the real results-page composable with only its HTTP boundary replaced.
const hooks = registerHooks({
  resolve(specifier, context, nextResolve) {
    if (specifier === '../moment' && context.parentURL?.includes('/moment/dist/locale/')) {
      return nextResolve(specifier + '.js', context)
    }
    if (specifier.endsWith('/common/api/TestApi')) {
      return {url: 'data:text/javascript,' + encodeURIComponent(`
        export const findTheoryKnowledgeExamById = data => globalThis.theoryGradeApi.detail(data);
        export const getExamineAnalyse = data => globalThis.theoryGradeApi.analyse(data);
        export const teacherUploadScore = () => { throw new Error('Unexpected score write'); };
        export const teacherStartTheoryKnowledgeExam = () => { throw new Error('Unexpected exam write'); };
      `), shortCircuit: true}
    }
    return nextResolve(specifier, context)
  }
})
const {default: startGrade} = await import('../src/views/manage/basicTheory/test/test/startGrade/js/startGrade.js')
hooks.deregister()

const renderer = createRenderer({
  createComment: () => ({}), insert() {}, remove() {},
  parentNode: () => null, nextSibling: () => null
})

async function openResults(t, roleId) {
  const requests = []
  const original = Object.fromEntries(['window', 'localStorage', 'theoryGradeApi']
    .map(key => [key, Object.getOwnPropertyDescriptor(globalThis, key)]))
  const candidate = {
    id: 'candidate-row', user_id: 'student', state: 4, score: 10,
    start_time: '2026-10-29 10:00:00', end_time: '2026-10-29 10:10:00',
    content: JSON.stringify({singleChoice: [{id: 'q1', answer: '0'}]})
  }
  const analysis = {scoreList: [{userAccount: 'student', score: 10}], errorTop3: []}
  Object.assign(globalThis, {
    window: {fileUrl: ''},
    localStorage: {getItem: key => JSON.stringify(key === 'userRole' ? {id: roleId} : {id: 'student'})},
    theoryGradeApi: {
      detail: async data => {
        requests.push({endpoint: 'detail', data})
        return {code: 200, data: {
          exam: {state: 4, duration: '60'}, user: [candidate],
          paper: {
            singleChoiceList: JSON.stringify([{id: 'q1', type: 1, score: 10, answer: '"0"', options: '[]'}]),
            multipleChoiceList: '[]', judgeList: '[]', completionList: '[]', shortAnswer: '[]'
          }
        }}
      },
      analyse: async data => {
        requests.push({endpoint: 'analyse', data})
        return {code: 200, data: analysis}
      }
    }
  })
  let app
  t.after(() => {
    app?.unmount()
    for (const [key, descriptor] of Object.entries(original)) {
      if (descriptor) Object.defineProperty(globalThis, key, descriptor)
      else delete globalThis[key]
    }
  })
  const router = createRouter({history: createMemoryHistory(), routes: [{path: '/', component: {render: () => null}}]})
  await router.push('/?id=exam&state=4')
  await router.isReady()
  let page
  app = renderer.createApp({setup() { page = startGrade(); return () => null }})
  app.use(router)
  app.mount({})
  page.initPaper()
  for (let i = 0; i < 8; i++) await nextTick()
  return {page, requests, analysis}
}

for (const roleId of ['2', 2]) {
  test(`学员查看本人评分结果只加载答卷，不请求整场分析（角色 ${JSON.stringify(roleId)}）`, async t => {
    const {page, requests} = await openResults(t, roleId)
    assert.deepEqual(requests, [{endpoint: 'detail', data: {id: 'exam'}}])
    assert.equal(page.activeUser.value.user_id, 'student')
    assert.equal(page.activeUser.value.score, 10)
    assert.equal(page.questions.value.singleChoice[0].correctAnswer, '0')
    assert.equal(page.analyseData.value, null)
  })
}

test('教员查看评分结果仍加载整场考核分析', async t => {
  const {page, requests, analysis} = await openResults(t, '1')
  assert.deepEqual(requests, [
    {endpoint: 'detail', data: {id: 'exam'}},
    {endpoint: 'analyse', data: {examId: 'exam'}}
  ])
  assert.deepEqual(page.analyseData.value, analysis)
})
