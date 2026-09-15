import assert from 'node:assert/strict'
import test from 'node:test'
import {registerHooks} from 'node:module'
import {createRenderer, nextTick} from 'vue'
import {createMemoryHistory, createRouter} from 'vue-router'
import {Modal} from 'ant-design-vue'
import {PubSub} from '../src/common/utils/PubSub.js'

// Replace only the HTTP boundary; execute the real composable and confirmation guard.
const hooks = registerHooks({
  resolve(specifier, context, nextResolve) {
    if (specifier.endsWith('/common/api/TelegramApi.js')) {
      return {url: 'data:text/javascript,' + encodeURIComponent(`
        export const findTexPatTrainById = () => globalThis.datagramStartApi.load();
        export const saveTexPatTrain = payload => globalThis.datagramStartApi.save(payload);
      `), shortCircuit: true}
    }
    if (specifier.endsWith('/useConfirmedSubmission') || specifier.endsWith('/telexTrain/js/enum')) {
      return nextResolve(specifier + '.js', context)
    }
    return nextResolve(specifier, context)
  }
})
const {default: telexTrain} = await import('../src/views/manage/preJob/datagram/telexTrain/js/telexTrain.js')
hooks.deregister()

const renderer = createRenderer({
  createComment: () => ({}), insert() {}, remove() {},
  parentNode: () => null, nextSibling: () => null
})
const settle = async () => {
  for (let i = 0; i < 8; i++) await nextTick()
}
const deferred = () => {
  let resolve, reject
  const promise = new Promise((yes, no) => { resolve = yes; reject = no })
  return {promise, resolve, reject}
}

async function mountTraining(t, status = 0) {
  const timers = new Map()
  let timerId = 0
  const modals = []
  const requests = []
  const keyboard = new EventTarget()
  const focus = {focus() {}}
  const original = Object.fromEntries(['window', 'document', 'localStorage', 'setInterval', 'clearInterval', 'datagramStartApi'].map(key => [key, Object.getOwnPropertyDescriptor(globalThis, key)]))
  const error = Modal.error
  const confirm = Modal.confirm
  Object.assign(globalThis, {
    window: keyboard,
    document: {getElementsByClassName: () => [focus], querySelectorAll: () => [focus]},
    localStorage: {getItem: key => key === 'token' ? 'datagram-token' : 'datagram-device'},
    setInterval: callback => { timers.set(++timerId, callback); return timerId },
    clearInterval: id => timers.delete(id),
    datagramStartApi: {
      load: async () => ({code: 200, data: {id: 'datagram', status, duration: 0, speed: 0, accuracy: 0, errorNumber: 0, content: JSON.stringify([{text: 'ABCD', value: '', isFocus: false}])}}),
      save: payload => {
        const response = deferred()
        requests.push({payload, ...response})
        return response.promise
      }
    }
  })
  Modal.error = options => modals.push(options)
  Modal.confirm = options => modals.push(options)
  const router = createRouter({history: createMemoryHistory(), routes: [{path: '/', component: {render: () => null}}]})
  await router.push('/?id=datagram')
  await router.isReady()
  let training
  const app = renderer.createApp({setup() { training = telexTrain(); return () => null }})
  app.use(router)
  app.mount({})
  let mounted = true
  const unmount = () => {
    if (!mounted) return
    app.unmount()
    mounted = false
  }
  t.after(() => {
    unmount()
    Modal.error = error
    Modal.confirm = confirm
    for (const [key, descriptor] of Object.entries(original)) {
      if (descriptor) Object.defineProperty(globalThis, key, descriptor)
      else delete globalThis[key]
    }
  })
  await settle()
  return {training, requests, timers, modals, unmount, key: () => keyboard.dispatchEvent(new Event('keydown')), tick: () => { for (const callback of timers.values()) callback() }}
}

const accept = request => request.resolve({code: 200, data: {...request.payload}})

test('首键等待确认，连续首键只启动一次，确认期间录入不被旧响应覆盖', async t => {
  const page = await mountTraining(t)
  page.key()
  page.key()
  assert.equal(page.requests.length, 1)
  assert.equal(page.training.trainData.value.status, 0)
  page.tick()
  assert.equal(page.training.trainData.value.duration, 0)
  page.training.activeMessage.value.value = 'ab'
  page.training.changeMessage(page.training.activeMessage.value)
  accept(page.requests[0])
  await settle()
  assert.equal(page.training.trainData.value.status, 1)
  assert.equal(page.training.message.value[0].value, 'AB')
  page.key()
  await page.training.beginTrain()
  page.tick()
  assert.equal(page.training.trainData.value.duration, 1)
  page.unmount()
  page.tick()
  assert.equal(page.training.trainData.value.duration, 1)
})

test('业务失败和网络失败保留输入与开始状态，弹窗重试确认后才开始计时', async t => {
  const page = await mountTraining(t, 2)
  page.training.activeMessage.value.value = 'AB'
  const first = page.training.beginTrain()
  page.requests[0].resolve({code: 500})
  assert.equal(await first, false)
  page.tick()
  assert.equal(page.training.trainData.value.status, 2)
  assert.equal(page.training.message.value[0].value, 'AB')
  assert.equal(page.training.trainData.value.duration, 0)
  const retry = page.modals.at(-1).onOk()
  page.requests[1].reject(new Error('offline'))
  assert.equal(await retry, false)
  assert.equal(page.training.message.value[0].value, 'AB')
  const confirmed = page.modals.at(-1).onOk()
  accept(page.requests[2])
  assert.equal(await confirmed, true)
  page.tick()
  assert.equal(page.training.trainData.value.status, 1)
  assert.equal(page.training.trainData.value.duration, 1)
})

for (const code of [207, 208]) {
  test(`${code}拒绝不启动、不丢输入，也不继续提交`, async t => {
    const page = await mountTraining(t)
    page.training.activeMessage.value.value = 'AB'
    const started = page.training.beginTrain()
    page.requests[0].resolve({code})
    assert.equal(await started, false)
    page.key()
    assert.equal(await page.training.beginTrain(), false)
    page.tick()
    assert.equal(page.training.trainData.value.status, 0)
    assert.equal(page.training.trainData.value.duration, 0)
    assert.equal(page.training.message.value[0].value, 'AB')
    assert.equal(page.requests.length, 1)
  })
}

test('开始请求未返回时离开页面，迟到的200不得复活训练计时', async t => {
  const page = await mountTraining(t)
  const started = page.training.beginTrain()
  page.unmount()
  accept(page.requests[0])
  assert.equal(await started, false)
  page.tick()
  assert.equal(page.training.trainData.value.status, 0)
  assert.equal(page.training.trainData.value.duration, 0)
  page.key()
  assert.equal(page.requests.length, 1)
})

test('结束失败保留定稿，拒绝继续编辑，退出重试只提交仍然可见的答案', async t => {
  const page = await mountTraining(t, 1)
  let closed = false
  const closeSubscription = PubSub.subscribe('callback_closeTelexTrainPage', () => { closed = true })
  t.after(() => PubSub.unsubscribe(closeSubscription))
  await page.training.beginTrain()
  page.training.changeMessage(page.training.activeMessage.value, 'ABCD')
  page.training.keyCodeDown2({key: 'A', keyCode: 65})
  const ended = page.training.endExerciseInfo()
  page.requests[0].resolve({code: 500})
  assert.equal(await ended, false)
  assert.equal(closed, false)

  // Dismissing the retry prompt has no success effect; both UI input paths stay frozen.
  page.training.changeMessage(page.training.activeMessage.value, 'BBBB')
  const inputEvent = {target: {innerHTML: 'BBBB'}}
  page.training.divChange(inputEvent, page.training.message.value[0])
  for (const keyHandler of [page.training.keyCodeDown, page.training.keyCodeDown2]) {
    const event = new Event('keydown', {cancelable: true})
    keyHandler(event)
    assert.equal(event.defaultPrevented, true)
  }
  assert.equal(page.training.message.value[0].value, 'ABCD')
  assert.equal(inputEvent.target.innerHTML, 'ABCD')
  assert.equal(await page.training.beginTrain(), false)
  page.tick()
  assert.equal(page.training.trainData.value.duration, 0)

  const retried = page.training.endExerciseInfo()
  assert.equal(JSON.parse(page.requests[1].payload.content)[0].value, page.training.message.value[0].value)
  accept(page.requests[1])
  assert.equal(await retried, true)
  await settle()
  assert.equal(page.training.message.value[0].value, 'ABCD')
  assert.equal(page.training.trainData.value.status, 3)
  assert.equal(closed, true)
})
