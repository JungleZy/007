// 使用岗位手键生产代码中的成字/改错函数验证模拟器真实帧，避免另写一套回退规则。
import assert from 'node:assert/strict'
import {readFileSync} from 'node:fs'
import {spawnSync} from 'node:child_process'
import {fileURLToPath} from 'node:url'
import vm from 'node:vm'
import useMorse from '../../bw-frontend/frontend/src/common/mixin/useMorse.js'
import {HAND_KEY_CONTROL_PATTERNS} from '../../bw-frontend/frontend/src/common/utils/handKeyControlCodes.js'
import {handKeyThresholds} from '../../bw-frontend/frontend/src/common/utils/handKeyTiming.js'
import {inferPatStandard} from '../../bw-frontend/frontend/src/views/manage/unionJob/lineNotify/js/decodePatRecord.js'

const binary = process.argv[2] || fileURLToPath(new URL(`../target/debug/keysim${process.platform === 'win32' ? '.exe' : ''}`, import.meta.url))
const source = readFileSync(new URL('../../bw-frontend/frontend/src/views/manage/postJob/telegram/train/js/details.js', import.meta.url), 'utf8')
const productionFunction = name => {
  const start = source.indexOf(`const ${name} =`)
  assert.ok(start >= 0, `岗位手键函数 ${name} 不存在`)
  return source.slice(start, source.indexOf('\n  /**', start))
}
const emit = (text, extra, sink) => {
  const result = spawnSync(binary, ['hand', '--alphabet', 'short', '--text', text, '--tail', 'end', '--seed', '7', ...extra, '--sink', sink], {encoding: 'utf8', maxBuffer: 32 * 1024 * 1024})
  assert.equal(result.status, 0, result.stderr)
  return JSON.parse(result.stdout)
}
const ref = value => ({value})
const hundred = Array.from({length: 100}, (_, index) => String(index).padStart(4, '0')).join(' ')
const cases = [
  ['当前组内改错', '12?1234 5678', []],
  ['当前组结束后改错', '1234 ? 5678 9012', []],
  ['组内回退前一组', '1234 56/9012 3456', []],
  ['组结束后回退前一组', '1234 5678 / 9012 3456', []],
  ['四码后紧贴前一组改错符', '1111 2222 3333 999 5555/4444 6666', []],
  ['首组回退钳制', '1234 / 5678', []],
  ['手动句号分页后结束', `${hundred} 。`, []],
  ['100组边界当前组改错', `${hundred} ? 9999 。0100`, []],
  ['100组边界前一组改错', `${hundred} / 9998 。0100`, []],
  ['第十行末组内改错', `${hundred.split(' ').slice(0, 99).join(' ')} 00?0099 。0100`, []],
  ['分页后不跨页回退', `${hundred} 。0100 / 7777`, []],
  ['201组自动改错与分页', `${Array(100).fill('1234').join(' ')} 。${Array(100).fill('1234').join(' ')} 。1234`, ['--correct-current', '--correct-previous']],
  ['手工和自动同时使用', '12?1234 5678 / 9012 3456', ['--correct-current', '--correct-previous']]
]

for (const [name, text, extra] of cases) {
  const frames = emit(text, extra, 'frames')
  const payload = emit(text, extra, 'payload')
  const strokes = []
  let down = null, previousUp = null
  for (const {frame} of frames) {
    if (frame.k === 0) down = frame.d
    else if (down !== null) {
      strokes.push({diff: frame.d - down, gap: previousUp === null ? 0 : down - previousUp, pageNumber: 1})
      previousUp = frame.d
      down = null
    }
  }
  const thresholds = handKeyThresholds(inferPatStandard(strokes), 51)
  const trainData = ref({type: 0, codeSort: false, patKeyVal: [], telegraph: {curr: []}, floorNow: 1,
    pag: Math.ceil(payload.messageBody.length / 100)})
  const cacheKey = ref([]), cachePatCode = ref([]), cachePatLogs = ref([]), currPatKeyIndex = ref(-1)
  const pages = []
  let finished = false
  const context = vm.createContext({trainData, cacheKey, cachePatCode, cachePatLogs, currPatKeyIndex,
    startStatus: ref(false), patNumber: ref(0), patKeyBoxRef: ref(null), scheduleView() {}, codeKey: useMorse().codeKey,
    patterns: HAND_KEY_CONTROL_PATTERNS, submission: {busy: ref(false), error: ref(null)}, pageResetPatStandard() {},
    statisticsTelegraphData: action => {
      const page = trainData.value.telegraph.curr
      while (page.length && !page.at(-1).patKeys.length) page.pop()
      if (action === 'turn') {
        assert.equal(page.length, 100, `${name}：分页时必须完成十行、每行十组`)
      }
      pages.push(page)
      if (action === 'turn') {
        trainData.value.floorNow++
        trainData.value.telegraph.curr = []
        currPatKeyIndex.value = 0
        cacheKey.value = []
      } else finished = true
    }
  })
   vm.runInContext(`let controlCandidates = [];let correctionResume = null;\n${productionFunction('codeCompileKeyInfo')}\n${productionFunction('updateMoresKeyInfo')}\nglobalThis.compile = codeCompileKeyInfo;`, context)
  for (const stroke of strokes) {
    if (finished) break
    if (stroke.gap > thresholds.characterGap) context.compile('word')
    if (stroke.gap > thresholds.groupGap) context.compile('group')
    cachePatCode.value.push({code: stroke.diff <= thresholds.dotDash ? 0 : 1, diff: stroke.diff, gap: 0})
  }
  if (!finished) context.compile('word')
  const actual = JSON.parse(JSON.stringify(pages.flatMap(page => page.map(group => group.patKeys))))
  const expected = payload.messageBody.map(group => JSON.parse(group.patKeys))
  assert.deepEqual(actual, expected, `${name}：实际帧经岗位手键处理后的逐组历史与模拟器载荷不一致`)
  console.log(`${name}：${frames.length}帧，${actual.length}组，${pages.length}页，岗位手键处理结果一致`)
}
