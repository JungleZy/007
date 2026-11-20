import test from 'node:test'
import assert from 'node:assert/strict'
import { updateResultMessage, versionMismatchMessage } from '../src/common/utils/softwareUpdate.js'

test('每种版本不一致状态都给出含双方版本的说明', () => {
  for (const state of ['server-newer', 'server-newer-no-package', 'server-older']) {
    const text = versionMismatchMessage({state, mismatch: true, localVersion: '3.1.3', serverVersion: '3.2.0'})
    assert.match(text, /3\.1\.3/, state)
    assert.match(text, /3\.2\.0/, state)
  }
  assert.match(versionMismatchMessage({state: 'server-legacy', mismatch: true, localVersion: '3.1.3', serverVersion: null}), /旧版本.*3\.1\.3/)
})

test('只有在本机安装成功才提示重启；仅留存、取消和失败都不提示', () => {
  assert.equal(updateResultMessage({ok: true, action: 'installed', version: '3.2.0'}).restart, true)
  const stored = updateResultMessage({ok: true, action: 'stored', version: '3.2.0', target: 'windows-ia32', style: 'LJ'})
  assert.equal(stored.restart, false)
  assert.match(stored.text, /未在本机安装/)
  assert.equal(updateResultMessage({ok: true, action: 'canceled'}), null)
  assert.deepEqual(updateResultMessage({ok: false, code: 'corrupt', message: '升级包已损坏'}), {type: 'error', text: '升级包已损坏', restart: false})
})
