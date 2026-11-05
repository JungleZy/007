import assert from 'node:assert/strict'
import {readFileSync} from 'node:fs'
import test from 'node:test'
import vm from 'node:vm'
import {ref} from 'vue'

const source = readFileSync(new URL('../src/components/BroadcastTeachTrain/js/useBroadTeacher.js', import.meta.url), 'utf8')
  .replace(/^import .*$/gm, '').replace('export default function', 'function')

async function setup(cache, serverTime = null) {
  const store = new Map([['userInfo', JSON.stringify({id: 'teacher'})], ['broad84', JSON.stringify(cache)]])
  const localStorage = {getItem: key => store.get(key) ?? null, setItem: (key, value) => store.set(key, value), removeItem: key => store.delete(key)}
  const server = {stats: 1, totalTime: serverTime, bwCount: 1, bwType: 1, mainSignal: 60, receiveUser: []}
  const sent = [], timers = new Map()
  let mount, refresh, socket, timerId = 0
  class Socket {
    isOpen = true
    connect(url, onMessage) { this.onMessage = onMessage }
    send(value) { sent.push(JSON.parse(value)) }
    close() {}
    constructor() { socket = this }
  }
  const context = vm.createContext({
    ref, onMounted: callback => { mount = callback }, onBeforeUnmount() {},
    useRoute: () => ({query: {id: '84'}}), useMorse: () => ({}),
    localStorage, window: {localStorage, wsUrl: 'ws://test', addEventListener() {}, removeEventListener() {}},
    SocketConnection: Socket, PubSub: {subscribe() {}, unsubscribe() {}},
    operationMorseVoice: () => ({operation() {}, ensureReady: async () => true}), calculateTiming: () => ({}),
    message: {success() {}},
    getRoomDetail: async () => ({code: 200, data: {...server}}),
    findRoomHeader: async () => ({code: 200, data: null}),
    findUserPageBaoWenInfo: async () => ({code: 200, data: {pageVos: [{key: '1234'}]}}),
    useSimulationRecovery: load => {
      const recoveryError = ref('')
      refresh = async () => {
        try { await load(); recoveryError.value = '' }
        catch (error) { recoveryError.value = error.message }
      }
      return {refresh, recoveryError, socketStatus: ref('open'), stop() {}, onState() {}}
    },
    setInterval: callback => { timers.set(++timerId, callback); return timerId },
    clearInterval: id => timers.delete(id)
  })
  vm.runInContext(source, context)
  const state = context.useBroadTeacher(ref({autoSetTimeAdd() {}}))
  await mount()
  return {state, server, sent, store, socket, refresh, tick: () => [...timers.values()].forEach(callback => callback())}
}

for (const cachedTime of [undefined, null, 'invalid', -1]) {
  test(`旧缓存耗时 ${String(cachedTime)} 恢复后可结束，发送合法整数秒`, async () => {
    const {state, sent, tick} = await setup({totalTime: cachedTime}, 7)
    assert.equal(state.trainData.value.totalTime, 7)
    state.trainTime()
    tick()
    state.closeTrainInfo()
    assert.deepEqual(sent, [{type: 4, count: 8}])
  })
}

test('缺失耗时从零开始，字符串耗时按数字累加，重复启动不重复计时', async () => {
  const empty = await setup({})
  empty.state.closeTrainInfo()
  assert.deepEqual(empty.sent, [{type: 4, count: 0}])
  const restored = await setup({totalTime: '12'})
  restored.state.trainTime()
  restored.state.trainTime()
  restored.tick()
  restored.state.closeTrainInfo()
  assert.deepEqual(restored.sent, [{type: 4, count: 13}])
})

test('结束被拒时保留训练状态与缓存，服务器确认结束后再清理', async () => {
  const {state, server, store, socket, refresh} = await setup({totalTime: 15})
  state.closeTrainInfo()
  await refresh()
  assert.equal(state.trainData.value.status, 1)
  assert.ok(store.has('broad84'))
  socket.onMessage({data: JSON.stringify({code: -1, data: '消息格式错误'})})
  assert.equal(state.recoveryError.value, '消息格式错误')
  assert.equal(state.trainData.value.status, 1)
  assert.ok(store.has('broad84'))
  server.stats = 2
  server.totalTime = 15
  await refresh()
  assert.equal(state.trainData.value.status, 2)
  assert.equal(store.has('broad84'), false)
})

test('断线结束不发送、不假结束，恢复后可以重试', async () => {
  const {state, socket, sent, store} = await setup({totalTime: 9})
  socket.isOpen = false
  state.closeTrainInfo()
  assert.equal(sent.length, 0)
  assert.equal(state.trainData.value.status, 1)
  assert.ok(store.has('broad84'))
  assert.match(state.recoveryError.value, /连接已断开/)
  socket.isOpen = true
  state.closeTrainInfo()
  assert.deepEqual(sent, [{type: 4, count: 9}])
})
