import assert from 'node:assert/strict'
import {readFileSync} from 'node:fs'
import test from 'node:test'
import vm from 'node:vm'
import {ref} from 'vue'
import useMorse from '../src/common/mixin/useMorse.js'
import {HAND_KEY_CONTROL_PATTERNS} from '../src/common/utils/handKeyControlCodes.js'

const modes = {
  personal: '../src/views/manage/postJob/telegram/train/js/details.js',
  group: '../src/views/manage/organization/handkeyZuXun/train/student/js/handKeyTrain.js'
}
for (const [mode, path] of Object.entries(modes)) {
  for (const pageGroups of [99, 100, 101]) {
  test(`${mode}改错保留邻组，${pageGroups}组后仅按翻页符切换下一页`, () => {
    const source = readFileSync(new URL(path, import.meta.url), 'utf8')
    const extract = name => {
      const start = source.indexOf(`const ${name} =`)
      return source.slice(start, source.indexOf('\n  /**', start))
    }
    const pages = [[], []], saved = []
    const data = ref({type: 0, codeSort: false, floorNow: 1, pag: 2, patKeyVal: [],
      telegraph: mode === 'personal' ? {curr: pages[0]} : pages})
    const cache = ref([]), keys = ref([]), logs = ref([]), cursor = ref(-1), started = ref(false)
    const context = vm.createContext({trainData: data, cachePatCode: cache, cachePatLogs: logs, cacheKey: keys,
      currPatKeyIndex: cursor, startStatus: started, patNumber: ref(0), patKeyBoxRef: ref(null),
      codeKey: useMorse().codeKey, patterns: HAND_KEY_CONTROL_PATTERNS,
      submission: {busy: ref(false), error: ref(null)}, scheduleView() {}, pageResetPatStandard() {},
      statisticsTelegraphData: () => {
        const page = mode === 'personal' ? data.value.telegraph.curr : data.value.telegraph[data.value.floorNow - 1]
        saved.push(JSON.parse(JSON.stringify(page)))
        data.value.floorNow++
        if (mode === 'personal') data.value.telegraph.curr = pages[1]
        cursor.value = -1; keys.value = []; started.value = false
      }
    })
    vm.runInContext(`let controlCandidates=[];let correctionResume=null;\n${extract('codeCompileKeyInfo')}\n${extract('updateMoresKeyInfo')}\nglobalThis.compile=codeCompileKeyInfo`, context)
    const word = code => {cache.value = [...code].map(v => ({code: Number(v), diff: 80, gap: 80}));context.compile('word')}
    const codebook = useMorse().codeKey.short
    const group = text => {for (const character of text) word(Object.keys(codebook).find(code => codebook[code] === character));context.compile('group')}
    word('10001')
    group('1111');group('2222');group('3333');group('9999');group('5555')
    const rowsBefore = data.value.patKeyVal.length
    word('001011')
    assert.equal(data.value.patKeyVal.length, rowsBefore)
    assert.equal(data.value.patKeyVal.at(-1).at(-1), '/')
    group('4444');group('6666')
    const page = mode === 'personal' ? data.value.telegraph.curr : data.value.telegraph[0]
    assert.equal(page[2].patKeys.join(''), '3333')
    assert.equal(page[3].patKeys.join(''), '9999?4444')
    assert.equal(page[4].patKeys.join(''), '5555')
    assert.equal(page[5].patKeys.join(''), '6666')
    for (let index = 6; index < pageGroups; index++) group('1234')
    assert.equal(data.value.floorNow, 1, '没有翻页符就不自行按100组切页')
    for (let index = 0; index < 3; index++) {word('00');context.compile('group')}
    assert.equal(saved.length, 1)
    word('10001');group('8932')
    const second = mode === 'personal' ? data.value.telegraph.curr : data.value.telegraph[1]
    assert.equal(data.value.floorNow, 2)
    assert.equal(second[0].patKeys.join(''), '8932')
    assert.ok(!saved[0].some(item => item.patKeys.includes('8')))
    const history = data.value.patKeyVal.flat().join(' ')
    assert.match(history, /3333 9999 5555 \/ 4444 6666/)
  })
  }
}
