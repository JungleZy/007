import assert from 'node:assert/strict'
import test from 'node:test'
import {BACKENDS, probeKernelBackends, startKernelSerial} from '../src/providers/kernel.mjs'

test('三个内核级后端都给出明确结论；不可用时必须带原因，且缺前置的要给装法', async () => {
  const backends = await probeKernelBackends()
  assert.deepEqual(backends.map(item => item.id), BACKENDS.map(item => item.id))
  for (const backend of backends) {
    assert.equal(typeof backend.available, 'boolean', `${backend.id} 没给出可用性结论`)
    if (!backend.available) assert.ok(backend.reason, `${backend.id} 不可用却没说原因`)
    // 平台不匹配不需要装法；缺内核模块这类前置必须给出可执行的装法
    if (!backend.available && /模块|未安装/.test(backend.reason)) {
      assert.ok(backend.install, `${backend.id} 缺前置却没给装法`)
    }
    assert.ok(backend.theirs, `${backend.id} 没说被测程序该选哪个口`)
  }
})

test('没有可用后端时 startKernelSerial 不抛异常也不假装成功，而是逐个报清原因', async () => {
  const started = await startKernelSerial()
  if (started.id) {
    assert.ok(started.ours && started.theirs, '声称启动成功就必须给出两端设备')
    assert.equal(typeof started.stop, 'function')
    await started.stop()
    return
  }
  assert.equal(started.id, null)
  assert.ok(Array.isArray(started.attempts) && started.attempts.length === BACKENDS.length, '每个后端都要有一条失败记录')
  for (const attempt of started.attempts) assert.ok(attempt.error, `${attempt.id} 失败却没有原因`)
})
