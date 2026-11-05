import assert from 'node:assert/strict'
import {readFileSync} from 'node:fs'
import test from 'node:test'
import vm from 'node:vm'
import {computed, nextTick, ref, reactive, watch, effectScope} from 'vue'
import {parse, compileScript} from '@vue/compiler-sfc'
import {decodePatRecord, inferPatStandard} from '../src/views/manage/unionJob/lineNotify/js/decodePatRecord.js'
import {DEFAULT_HAND_KEY_STANDARD, DEFAULT_HAND_KEY_SKEW, handKeyThresholds} from '../src/common/utils/handKeyTiming.js'

const sequence = (bits, firstGap = 0, pageNumber = 1) => [...bits].map((bit, index) => ({
  diff: bit === '0' ? 60 : 160, gap: index ? 60 : firstGap, pageNumber
}))

// 实际故障记录的前32次按键，匿名化并保留0.1ms精度：开始符 + 7287 + 2410。
const recordedStart = [
  [122.5, 0], [41.4, 41.4], [41.2, 41.8], [41.4, 41.9], [121.1, 41.5],
  [120, 214.9], [124.2, 42.8], [41.1, 41.6], [41.9, 41.8], [41.3, 41.5],
  [41.7, 133.4], [42.2, 42.5], [124, 43.2], [117.2, 134.2], [43.2, 42.3],
  [42.7, 43], [122.2, 131.2], [121.2, 41.6], [42.4, 41.9], [42.2, 43.5],
  [41.7, 42.8], [41.8, 221.4], [43.2, 42.5], [121.7, 41.3], [44.3, 132.5],
  [42, 42.7], [43.9, 43.6], [44.5, 41.6], [119.2, 43.2], [44, 132.4],
  [125.8, 43.2], [121.6, 131.9]
].map(([diff, gap]) => ({diff, gap, pageNumber: 1}))
const groupText = tokens => {
  const groups = []
  for (const token of tokens) {
    if (token.groupStart) groups.push('')
    groups[groups.length - 1] += token.character
  }
  return groups
}

test('实际42ms点/121ms划自动校准，避免固定80ms基准造成大面积错译和合组', () => {
  const standard = inferPatStandard(recordedStart)
  assert.ok(Math.abs(standard.dot - 41.33) < .1)
  assert.ok(Math.abs(standard.codeGap - 41.65) < .1)
  const decoded = decodePatRecord(recordedStart)
  assert.deepEqual(groupText(decoded), ['开始', '7287', '2410'])
  assert.deepEqual([decoded[1].startStroke, decoded[1].endStroke], [5, 9])
  assert.notDeepEqual(groupText(decodePatRecord(recordedStart, {standard: DEFAULT_HAND_KEY_STANDARD})), ['开始', '7287', '2410'])
})

test('没有开始符也可按时长分布校准，不同速度保留相同译码', () => {
  for (const scale of [.6, 1, 2, 4]) {
    const strokes = recordedStart.slice(5).map(stroke => ({...stroke, diff: stroke.diff * scale, gap: stroke.gap * scale}))
    assert.deepEqual(groupText(decodePatRecord(strokes)), ['7287', '2410'])
  }
})

test('没有开始符且字符很短时，码内/字间/组间三种间隔不会混成同一基准', () => {
  const records = Array.from({length: 80}, (_, index) => ({diff: index % 2 ? 240 : 80,
    gap: index === 0 ? 0 : index % 8 === 0 ? 400 : index % 2 === 0 ? 240 : 80, pageNumber: 1}))
  assert.equal(inferPatStandard(records).codeGap, 80)
  assert.deepEqual(groupText(decodePatRecord(records)), Array(10).fill('1111'))
})

test('控制符沿用岗位手键码表，多截换页符必须各自独立成组', () => {
  const controls = decodePatRecord([
    ...sequence('10001'), ...sequence('001100', 300), ...sequence('001011', 300),
    ...sequence('000000', 300), ...sequence('01010', 300)
  ])
  assert.deepEqual(controls.map(item => item.character), ['开始', '?', '/', '换页', '结束'])
  assert.deepEqual(controls.map(item => item.control), ['start', 'alter', 'next', 'turn', 'end'])
  const separated = decodePatRecord([...sequence('00'), ...sequence('00', 300), ...sequence('00', 300)], {bwType: 3})
  assert.equal(separated.length, 1)
  assert.equal(separated[0].character, '换页')
  assert.deepEqual([separated[0].startStroke, separated[0].endStroke], [0, 5])
  assert.deepEqual(decodePatRecord([...sequence('00'), ...sequence('00', 160), ...sequence('00', 160)], {bwType: 3})
    .map(item => item.character), ['I', 'I', 'I'])
})

test('按实际点划区分短码、长码和字母，保留最后一个字符', () => {
  assert.equal(decodePatRecord(sequence('01'), {bwType: 1})[0].character, '1')
  assert.equal(decodePatRecord(sequence('01111'), {bwType: 2})[0].character, '1')
  assert.equal(decodePatRecord(sequence('01'), {bwType: 3})[0].character, 'A')
  assert.equal(decodePatRecord(sequence('00000'), {bwType: 2})[0].character, '5')
})

test('按前置间隔分字、分组，跨页记录不会合并为一个字符', () => {
  const decoded = decodePatRecord([
    ...sequence('01'), ...sequence('1000', 160), ...sequence('1010', 5000), ...sequence('100', 0, 2)
  ], {bwType: 3})
  assert.deepEqual(decoded.map(item => item.character), ['A', 'B', 'C', 'D'])
  assert.deepEqual(decoded.map(item => item.groupStart), [true, false, true, true])
  assert.equal(decoded[3].pageNumber, 2)
})

test('错误码显示井号，微按压不产生假字符也不吞掉字间隔', () => {
  const unknown = decodePatRecord(sequence('010101'), {bwType: 3})[0]
  assert.equal(unknown.character, '#')
  assert.equal(unknown.unknown, true)
  assert.equal(decodePatRecord(sequence('00'), {bwType: 1})[0].unknown, false, '码表内的井号不应标成未识别')
  const decoded = decodePatRecord([
    ...sequence('0'), {diff: 5, gap: 100, pageNumber: 1}, {diff: 160, gap: 60, pageNumber: 1}
  ], {bwType: 3})
  assert.deepEqual(decoded.map(item => item.character), ['E', 'T'])
  assert.deepEqual(decodePatRecord([{diff: 5, gap: 0, pageNumber: 1}]), [])
})

test('调整点划和字间分界适配慢速拍发，原始记录不被修改', () => {
  const records = [{diff: 120, gap: 0, pageNumber: 1}, {diff: 320, gap: 160, pageNumber: 1}]
  const before = structuredClone(records)
  assert.equal(decodePatRecord(records, {bwType: 3, standard: {dot: 160, codeGap: 160}, skew: 50})[0].character, 'A')
  assert.deepEqual(records, before)
})

test('沿用岗位手键的80ms基准和50%偏移，400ms组间隔正确整理连续报文', () => {
  assert.deepEqual(handKeyThresholds(DEFAULT_HAND_KEY_STANDARD, DEFAULT_HAND_KEY_SKEW), {
    dotDash: 120, characterGap: 120, groupGap: 280
  })
  const groups = [
    ['01', '001', '00011', '00001'],
    ['00000', '10000', '11000', '100'],
    ['10', '010101', '1']
  ]
  const strokes = groups.flatMap((group, groupIndex) => group.flatMap((code, characterIndex) =>
    [...code].map((bit, index) => ({diff: bit === '0' ? 80 : 240, pageNumber: 1,
      gap: index ? 80 : characterIndex ? 240 : groupIndex ? 400 : 0}))))
  const decoded = decodePatRecord(strokes)
  const text = []
  for (const item of decoded) {
    if (item.groupStart) text.push('')
    text[text.length - 1] += item.character
  }
  assert.deepEqual(text, ['1234', '5678', '9#0'])
})

test('字间和组间边界使用岗位手键的严格大于判断', () => {
  const decoded = decodePatRecord([
    {diff: 120, gap: 0, pageNumber: 1}, {diff: 120.01, gap: 120, pageNumber: 1},
    {diff: 80, gap: 280, pageNumber: 1}, {diff: 240, gap: 280.01, pageNumber: 1}
  ], {bwType: 3, standard: DEFAULT_HAND_KEY_STANDARD})
  assert.deepEqual(decoded.map(item => item.character), ['A', 'E', 'T'])
  assert.deepEqual(decoded.map(item => item.groupStart), [true, false, true])
})

function component(name, globals, props = {}) {
  const {descriptor} = parse(readFileSync(new URL(`../src/views/manage/unionJob/lineNotify/components/${name}.vue`, import.meta.url), 'utf8'))
  const source = compileScript(descriptor, {id: `test-${name}`}).content
    .replace(/^import .*$/gm, '').replace('export default', 'globalThis.component =')
  const context = vm.createContext({ref, computed, nextTick, DEFAULT_HAND_KEY_STANDARD, DEFAULT_HAND_KEY_SKEW, inferPatStandard,
    watch() {}, onMounted() {}, onUnmounted() {}, onBeforeUnmount() {}, ...globals})
  vm.runInContext(source, context)
  return context.component.setup(props, {expose() {}})
}

test('拍发端初始和新增译码后都滚动到底部，包括已选中旧报文时', async t => {
  const scope = effectScope()
  t.after(() => scope.stop())
  const props = reactive({strokes: [...recordedStart], bwType: 1, followLatest: true})
  const panel = scope.run(() => component('PatRecordPanel', {decodePatRecord, watch}, props))
  let top = 0, height = 900
  panel.decodedBox.value = {get scrollHeight() { return height },
    get scrollTop() { return top }, set scrollTop(value) { top = Math.max(0, Math.min(height - 120, value)) }}
  await nextTick(); await nextTick()
  assert.equal(top, 780)
  top = 0
  height = 1200
  panel.selected.value = {startStroke: 0, endStroke: 4}
  props.strokes.push(...sequence('01', 300))
  await nextTick(); await nextTick()
  assert.equal(top, 1080)
})

test('其他人员查看记录时保留自己的滚动位置', async t => {
  const scope = effectScope()
  t.after(() => scope.stop())
  const props = reactive({strokes: [...recordedStart], bwType: 1, followLatest: false})
  const panel = scope.run(() => component('PatRecordPanel', {decodePatRecord, watch}, props))
  const box = {scrollTop: 35, scrollHeight: 900}
  panel.decodedBox.value = box
  await nextTick(); await nextTick()
  props.strokes.push(...sequence('01', 300))
  await nextTick(); await nextTick()
  assert.equal(box.scrollTop, 35)
})

test('记录面板默认自动校准实际记录，手动调整后可切回自动校准', () => {
  const panel = component('PatRecordPanel', {decodePatRecord}, {strokes: recordedStart, bwType: 1})
  assert.equal(panel.autoCalibrate.value, true)
  const groups = () => panel.decodedGroups.value.map(group => group.map(item => item.character).join('')).join('|')
  assert.equal(groups(), '开始|7287|2410')
  panel.setStandard('dot', 80)
  panel.setStandard('codeGap', 80)
  assert.equal(panel.autoCalibrate.value, false)
  assert.notEqual(groups(), '开始|7287|2410')
  panel.autoCalibrate.value = true
  assert.equal(groups(), '开始|7287|2410')
})

test('译码字符保留原始记录范围，忽略微按压后定位索引仍准确', () => {
  const records = [
    {diff: 5, gap: 0, pageNumber: 1}, ...sequence('01'),
    {diff: 5, gap: 0, pageNumber: 1}, ...sequence('1000', 160),
    ...sequence('010101', 5000), ...sequence('1', 0, 2)
  ]
  const decoded = decodePatRecord(records, {bwType: 3})
  assert.deepEqual(decoded.map(item => [item.character, item.startStroke, item.endStroke]), [
    ['A', 1, 2], ['B', 4, 7], ['#', 8, 13], ['T', 14, 14]
  ])
  assert.deepEqual(decoded.map(item => item.groupStart), [true, false, true, true])
})

test('报文按组间间隔分块，调整分界会更新分组', () => {
  const panel = component('PatRecordPanel', {decodePatRecord}, {
    strokes: [...sequence('01'), ...sequence('1000', 160), ...sequence('1010', 500)], bwType: 3
  })
  panel.autoCalibrate.value = false
  panel.codeGap.value = 100
  assert.equal(panel.decodedGroups.value.map(group => group.map(item => item.character).join('')).join('|'), 'AB|C')
  panel.codeGap.value = 40
  assert.equal(panel.decodedGroups.value.map(group => group.map(item => item.character).join('')).join('|'), 'A|B|C')
})

test('点击译码字符按实际波形位置滚动并选中，已有滚动和压缩间隔不会造成偏移', () => {
  const panel = component('PatRecordPanel', {decodePatRecord})
  let offset = 350
  let waveBroughtIntoView = false
  const bounds = new Map([[1, [1000, 1010]], [2, [1040, 1060]], [3, [1950, 1970]], [4, [10, 20]], [5, [800, 1200]]])
  panel.wave.value = {
    clientWidth: 200, scrollWidth: 2000, clientLeft: 1,
    get scrollLeft() { return offset },
    getBoundingClientRect: () => ({left: 100}),
    querySelector: selector => {
      const range = bounds.get(Number(selector.match(/"(\d+)"/)[1]))
      return range ? {getBoundingClientRect: () => ({left: 101 + range[0] - offset, right: 101 + range[1] - offset})} : null
    },
    scrollTo: ({left}) => { offset = left },
    scrollIntoView: () => { waveBroughtIntoView = true }
  }
  panel.locateCharacter({startStroke: 1, endStroke: 2})
  assert.equal(offset, 930)
  assert.equal(waveBroughtIntoView, true, '定位时应将弹框中已滚出视口的波形带回可见区域')
  assert.equal(panel.selected.value.startStroke, 1)
  assert.equal(panel.selected.value.endStroke, 2)
  panel.locateCharacter({startStroke: 1, endStroke: 2})
  assert.equal(offset, 930, '重复定位不能叠加当前滚动距离')
  panel.locateCharacter({startStroke: 3, endStroke: 3})
  assert.equal(offset, 1800, '尾部字符定位不能超过最大滚动距离')
  panel.locateCharacter({startStroke: 4, endStroke: 4})
  assert.equal(offset, 0)
  panel.locateCharacter({startStroke: 5, endStroke: 5})
  assert.equal(offset, 784, '超出视口的长码从起始按键处展示')
})

test('波形滚轮支持横向滚动，到达边界后不阻止页面继续滚动', () => {
  const panel = component('PatRecordPanel', {decodePatRecord})
  let offset = 0, prevented = 0
  panel.wave.value = {scrollWidth: 1000, clientWidth: 200,
    get scrollLeft() { return offset }, set scrollLeft(value) { offset = Math.max(0, Math.min(800, value)) }}
  const wheel = deltaY => ({deltaY, deltaX: 0, deltaMode: 0, preventDefault: () => prevented++})
  panel.scrollWave(wheel(120))
  assert.equal(offset, 120)
  assert.equal(prevented, 1)
  panel.scrollWave(wheel(1000))
  assert.equal(offset, 800)
  panel.scrollWave(wheel(120))
  assert.equal(prevented, 2, '到达右端不再拦截滚轮')
  panel.scrollWave(wheel(-120))
  assert.equal(offset, 680)
})

test('共用记录弹框传递目标用户，切换或关闭后忽略迟到响应', async () => {
  const requests = []
  const page = component('PatRecordDialog', {
    PatRecordPanel: {},
    getLinePatRecord: (roomId, userId) => new Promise(resolve => requests.push({roomId, userId, resolve}))
  }, {roomId: '86', bwType: 1})
  const first = page.open({id: 'sender1', userName: '甲'})
  const second = page.open({id: 'sender2', userName: '乙'})
  assert.deepEqual(requests.map(({roomId, userId}) => [roomId, userId]), [['86', 'sender1'], ['86', 'sender2']])
  requests[1].resolve({code: 200, data: sequence('1')})
  await second
  requests[0].resolve({code: 200, data: sequence('00')})
  await first
  assert.equal(page.senderRecords.value.length, 1)
  assert.equal(page.selectedSender.value.id, 'sender2')
  const third = page.open({id: 'sender1'})
  page.close()
  requests[2].resolve({code: 200, data: sequence('000')})
  await third
  assert.equal(page.patRecordVisible.value, false)
  assert.equal(page.senderRecords.value.length, 0)
})

test('组训端和接收端都能在结束后打开拍发记录', () => {
  const opened = []
  const sender = {id: 'sender', userName: '拍发人员'}
  const storage = {getItem: () => JSON.stringify({id: 'viewer'})}
  const common = {TrainResult: {}, PatRecordPanel: {}, PatRecordDialog: {}, FileTextOutlined: {},
    window: {fileUrl: '', localStorage: storage}, localStorage: storage}
  const teacher = component('Issue', {...common, prev: '', next: '', issue: () => ({}),
    useRoute: () => ({query: {id: '86', roomState: 'createUser'}})})
  const receiver = component('ListenIn', {...common, FillInResult: {}, iconImage: () => ({}), SocketConnection: class {},
    useRoute: () => ({query: {id: '86', roomState: 'putAway'}})})
  for (const [page, data] of [[teacher, teacher.trainData], [receiver, receiver.roomDetails]]) {
    page.patRecordDialog.value = {open: value => opened.push(value.id)}
    data.value.stats = 1
    page.viewSenderRecord(sender)
    assert.equal(opened.length, 0)
    data.value.stats = 2
    page.viewSenderRecord(sender)
    assert.equal(opened.pop(), sender.id)
  }
})

test('记录弹框显示拒绝原因并允许重试，失败时不保留旧人员内容', async () => {
  let rejected = true
  const page = component('PatRecordDialog', {PatRecordPanel: {}, getLinePatRecord: async () =>
    rejected ? {code: 207, message: '无权查看拍发记录'} : {code: 200, data: sequence('01')}
  }, {roomId: '86', bwType: 1})
  await page.open({id: 'sender'})
  assert.equal(page.patRecordError.value, '无权查看拍发记录')
  assert.equal(page.senderRecords.value.length, 0)
  rejected = false
  await page.open(page.selectedSender.value)
  assert.equal(page.patRecordError.value, '')
  assert.equal(page.senderRecords.value.length, 2)
})
