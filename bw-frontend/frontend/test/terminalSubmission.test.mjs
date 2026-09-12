import assert from 'node:assert/strict'
import test from 'node:test'
import {Modal} from 'ant-design-vue'
import {isTerminalCode, terminalReason} from '../src/common/http/terminalCode.js'

// useConfirmedSubmission 直接读 localStorage 并弹 ant-design-vue 的模态：
// 前者在 Node 里必须给出实现，后者把静态方法换成记录器即可，生产代码不为测试改形。
const store = new Map()
globalThis.localStorage = {
  getItem: key => (store.has(key) ? store.get(key) : null),
  setItem: (key, value) => store.set(key, String(value)),
  removeItem: key => store.delete(key)
}
store.set('token', 'terminal-code-token')
store.set('deviceId', 'terminal-code-device')

const modals = []
Modal.error = options => modals.push({kind: 'error', ...options})
Modal.confirm = options => modals.push({kind: 'confirm', ...options})

const {default: useConfirmedSubmission} = await import('../src/common/mixin/useConfirmedSubmission.js')

test('终态码只认信封里的数字 code，202 仍然可重试', () => {
  assert.equal(isTerminalCode(208), true, '208 是业务终态')
  assert.equal(isTerminalCode(207), true, '无权限也是终态，重试不会长出权限')
  assert.equal(isTerminalCode(202), false, '参数错误改正后重试可以成功，必须保留重试入口')
  assert.equal(isTerminalCode(500), false, '服务器错误可重试')
  assert.equal(isTerminalCode(200), false)
  assert.equal(isTerminalCode('208'), false, '字符串码说明取错了字段，不得当成终态放过')
  assert.equal(isTerminalCode(undefined), false)
})

test('终态拒因优先用后端文案，后端没给才兜底', () => {
  assert.equal(terminalReason(208, '训练已完成，不能修改'), '训练已完成，不能修改')
  assert.equal(terminalReason(208, '   '), '该操作已不可执行', '空白文案不得原样显示给用户')
  assert.equal(terminalReason(207, null), '没有执行该操作的权限')
  assert.equal(terminalReason(202, ''), '', '非终态码没有终态兜底文案')
})

test('终态码提示不可重试、停止后续提交，且不清除已录入内容', async () => {
  modals.length = 0
  const submission = useConfirmedSubmission()
  let calls = 0
  const action = () => {
    calls++
    return Promise.resolve({code: 208, message: '训练已完成，不能修改'})
  }
  submission.saveSnapshot('terminal-page', {patValue: '1234 5678', pageNumber: 2})

  assert.equal(await submission.run(request => request(action)), false)

  assert.equal(calls, 1, '终态拒绝只会发生一次请求')
  assert.deepEqual(modals.map(m => m.kind), ['error'], '终态必须是不可重试的提示，不能是「重试」确认框')
  assert.equal(modals[0].okText, '我知道了')
  assert.match(modals[0].content, /训练已完成，不能修改/, '拒因文案必须可见')
  assert.match(modals[0].content, /重试不会成功/, '必须明说重试无意义')
  assert.equal(submission.terminal.value, '训练已完成，不能修改')

  assert.equal(await submission.run(request => request(action)), false, '终态后不得再发起提交')
  assert.equal(await submission.retry(), false, '终态后手动重试也必须被拒绝')
  assert.equal(calls, 1, '终态后一次也不许重发')
  assert.equal(submission.defer(() => {}), false, '终态后不得继续拦住页面切换')
  assert.deepEqual(submission.loadSnapshot('terminal-page'), {patValue: '1234 5678', pageNumber: 2},
    '终态拒绝不得清除用户已填写的内容')
})

test('服务器错误与网络失败仍然引导重试，重试可以成功', async () => {
  modals.length = 0
  const submission = useConfirmedSubmission()
  const responses = [{code: 500, message: '服务器错误'}, {code: 200}]
  let calls = 0
  const action = () => Promise.resolve(responses[calls++])

  assert.equal(await submission.run(request => request(action)), false)

  assert.deepEqual(modals.map(m => m.kind), ['confirm'], '可重试失败必须给重试入口')
  assert.equal(modals[0].okText, '重试')
  assert.equal(submission.terminal.value, '', '500 不是终态')
  assert.equal(submission.error.value, '服务器错误')

  assert.equal(await submission.retry(), true, '重试必须真的重发')
  assert.equal(calls, 2)
  assert.equal(submission.error.value, '')
  assert.equal(submission.pending(), false, '重试成功后不再有待提交数据')
})
