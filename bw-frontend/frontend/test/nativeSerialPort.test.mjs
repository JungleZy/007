import assert from 'node:assert/strict'
import childProcess from 'node:child_process'
import fs from 'node:fs'
import {createRequire} from 'node:module'
import {describe, test} from 'node:test'

const require = createRequire(import.meta.url)
const modulePath = require.resolve('../../electron/serial/nativeSerialPort.js')

function serialFixture(t, {devices = ['ttyUSB0', 'ttyACM1'], canonical, characterDevice = true, failure} = {}) {
  const commands = []
  t.mock.method(fs, 'readdirSync', () => devices)
  t.mock.method(fs, 'realpathSync', path => canonical ?? path)
  t.mock.method(fs, 'statSync', () => ({isCharacterDevice: () => characterDevice}))
  t.mock.method(childProcess, 'execFileSync', (command, args) => {
    commands.push([command, ...args])
    if (failure) throw failure
  })
  delete require.cache[modulePath]
  const serial = require(modulePath)
  t.after(() => { delete require.cache[modulePath] })
  return {serial, commands}
}

describe('Linux serial authorization', {skip: process.platform !== 'linux'}, () => {
  test('authorizes enumerated USB and ACM character devices with separate process arguments', t => {
    const {serial, commands} = serialFixture(t)
    const result = serial.grantAccess(['/dev/ttyUSB0', {path: '/dev/ttyACM1'}], 'operator')

    assert.equal(result.ok, true)
    assert.deepEqual(result.granted, ['/dev/ttyUSB0', '/dev/ttyACM1'])
    assert.deepEqual(commands, [
      ['pkexec', 'usermod', '-a', '-G', 'dialout', 'operator'],
      ['pkexec', 'chmod', '666', '/dev/ttyUSB0'],
      ['pkexec', 'chmod', '666', '/dev/ttyACM1']
    ])
  })

  test('rejects traversal, injected arguments, malformed and absent targets before any elevation', t => {
    const {serial, commands} = serialFixture(t)
    for (const target of [
      '/dev/../etc/passwd',
      '/dev/ttyUSB0/../../etc/passwd',
      '/dev/./ttyUSB0',
      '/dev//ttyUSB0',
      '/dev/ttyUSB0/../ttyACM1',
      '/dev/ttyUSB0;id',
      '/dev/ttyUSB0 /etc/passwd',
      '/dev/ttyUSB0\n',
      '/dev/ttyUSB0\0',
      '/dev/ttyS0',
      '/dev/ttyUSB9',
      'ttyUSB0',
      '..',
      '',
      null,
      {},
      []
    ]) {
      const result = serial.grantAccess(target, 'operator')
      assert.equal(result.ok, false, JSON.stringify(target))
      assert.deepEqual(result.granted, [])
    }
    assert.deepEqual(commands, [], 'invalid requests must never invoke pkexec, including usermod')
  })

  test('validates the entire batch before granting its first valid device', t => {
    const {serial, commands} = serialFixture(t)
    const result = serial.grantAccess(['/dev/ttyUSB0', '/dev/../etc/passwd'], 'operator')
    assert.equal(result.ok, false)
    assert.deepEqual(result.granted, [])
    assert.deepEqual(commands, [])
  })

  test('rejects enumerated device names that resolve through a symlink', t => {
    const {serial, commands} = serialFixture(t, {canonical: '/etc/passwd'})
    const result = serial.grantAccess('/dev/ttyUSB0', 'operator')
    assert.equal(result.ok, false)
    assert.deepEqual(result.granted, [])
    assert.deepEqual(commands, [])
  })

  test('rejects ordinary files masquerading as serial devices', t => {
    const {serial, commands} = serialFixture(t, {characterDevice: false})
    const result = serial.grantAccess('/dev/ttyUSB0', 'operator')
    assert.equal(result.ok, false)
    assert.deepEqual(result.granted, [])
    assert.deepEqual(commands, [])
  })

  test('returns authorization cancellation as a failure without granting devices', t => {
    const {serial, commands} = serialFixture(t, {failure: new Error('authorization cancelled')})
    const result = serial.grantAccess('/dev/ttyUSB0', 'operator')
    assert.equal(result.ok, false)
    assert.match(result.message, /authorization cancelled/)
    assert.deepEqual(result.granted, [])
    assert.equal(commands.length, 1, 'cancelled usermod must not proceed to chmod')
  })
})
