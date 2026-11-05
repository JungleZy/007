import assert from 'node:assert/strict'
import {readFileSync} from 'node:fs'
import test from 'node:test'
import vm from 'node:vm'
import {ref, computed} from 'vue'

const participation = readFileSync(new URL('../src/common/utils/trainingParticipation.js', import.meta.url), 'utf8')
  .replace('export const', 'const')

for (const kind of ['datagramZuXun', 'electronKeyZuXun', 'handkeyZuXun']) {
  test(`${kind}保留全员，选中未参训人员显示提示，已参训人员可查看成绩`, () => {
    const source = readFileSync(new URL(`../src/views/manage/organization/${kind}/train/teacher/js/teacher.js`, import.meta.url), 'utf8')
      .replace(/import\s+[\s\S]*?from\s+['"][^'"]+['"];?/g, '')
      .replace('export default function ()', 'function createTeacher()')
    const context = vm.createContext({
      ref, computed, onMounted() {}, onUnmounted() {}, onBeforeUnmount() {}, nextTick() {},
      useRoute: () => ({query: {id: '12'}}),
      localStorage: {getItem: () => JSON.stringify({id: 'teacher'})}, window: {},
      PublicSocket: () => ({ws_connect() {}, sendMessage() {}, closeWebSocket() {}})
    })
    vm.runInContext(participation + '\n' + source, context)
    const state = context.createTeacher()
    const absent = {userId: 'absent', isFinish: 1, score: 100, existPageNumber: 0, existNumber: [], pageAnalyzeVOS: []}
    const zero = {userId: 'zero', isFinish: 1, score: 0, existPageNumber: 1}
    const negative = {userId: 'negative', isFinish: 1, score: -10, pageAnalyzeVOS: [{pageNumber: 1}]}
    const pending = {userId: 'pending', isFinish: 0, existNumber: [1]}
    state.trainData.value = {status: 1, userInfoList: [absent, zero, negative, pending]}
    assert.equal(state.trainData.value.userInfoList.length, 4)
    state.seeStudentScore(zero)
    assert.equal(state.activeUserId.value, null)
    state.trainData.value.status = 2
    assert.equal(state.trainData.value.userInfoList.length, 4)
    state.seeStudentScore(absent)
    assert.equal(state.activeUserId.value, 'absent')
    assert.equal(state.selectedHasSubmission.value, false)
    state.seeStudentScore(pending)
    assert.equal(state.activeUserId.value, 'pending')
    assert.equal(state.selectedHasSubmission.value, true)
    assert.equal(state.isFinish.value, 0)
    state.trainData.value.userInfoList[3].isFinish = 1
    assert.equal(state.isFinish.value, 1)
    state.seeStudentScore(zero)
    assert.equal(state.activeUserId.value, 'zero')
    assert.equal(state.selectedHasSubmission.value, true)
    state.seeStudentScore(negative)
    assert.equal(state.activeUserId.value, 'negative')
    assert.equal(state.selectedHasSubmission.value, true)
    state.seeStudentScore(absent)
    assert.equal(state.selectedHasSubmission.value, false)
    state.seeStudentScore(absent)
    assert.equal(state.activeUserId.value, null)
  })
}
