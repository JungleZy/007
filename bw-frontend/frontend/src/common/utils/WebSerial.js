import {PubSub} from './PubSub'

const CODES = [
  11, 12, 13, 14, 15, 21, 22, 23, 24, 25, 31, 32, 33, 34, 35, 41, 42, 43, 44, 45
]
export default class WebSerial {
  constructor(rate, fre) {
    this.baudRate = rate
    this.keepReading = false
    this.port = null
    this.history = []
    this.pending = []
    this.reader = null
    this.readTask = null
  }

  async init(callback, type) {
    if ('serial' in navigator) {
      const that = this
      try {
        that.history = await navigator.serial.getPorts()
        if (that.history.length === 0 || type === 'resetLine') {
          callback({code: 0})
          that.port = await navigator.serial.requestPort()
          await that.handlePort(callback)
        } else {
          callback({code: 1})
          that.port = that.history[0]
          await that.handlePort(callback)
        }
      } catch (err) {
        console.error('串口连接或读取失败', err)
        callback({code: -1}) // 统一错误回调
      }
    } else {
      callback({code: -1})
    }
  }

  async resetPort(callback) {
    let that = this
    that.port = await navigator.serial.requestPort()
    await that.handlePort(callback)
  }

  async handlePort(callback) {
    await this.port.open({baudRate: this.baudRate})
    this.keepReading = true
    const reader = this.port.readable.getReader()
    this.reader = reader
    callback({code: 10})
    this.readTask = this.readFrames(reader, callback)
    await this.readTask
  }

  async readFrames(reader, callback) {
    try {
      while (this.keepReading) {
        const {value, done} = await reader.read()
        if (done || !this.keepReading) break
        if (value) this.handleData(value, callback)
      }
    } finally {
      reader.releaseLock()
      if (this.reader === reader) this.reader = null
    }
  }

  handleData(bytes, callback) {
    // The documented wire framing has a 2-byte down and 3-byte up frame.
    // These frames carry no decoded device timestamp: timing is JS receipt only.
    const receivedAt = performance.now()
    this.pending.push(...bytes)
    let offset = 0
    while (offset < this.pending.length) {
      const code = this.pending[offset]
      if (CODES.includes(code)) {
        callback(Object.freeze({code: 'Message', data: Object.freeze({
          t: 1, k: 0, d: Object.freeze([code]), receivedAt, timeSource: 'js-receive'
        })}))
        offset++
        continue
      }
      const length = code === 1 ? 2 : code === 2 ? 3 : 0
      if (!length) {
        console.warn('Unknown serial frame byte', code)
        offset++
        continue
      }
      if (this.pending.length - offset < length) break
      PubSub.publishSync('receiveSendData', {type: 'pressed', data: code === 1})
      callback(Object.freeze({code: 'Message', data: Object.freeze({
        t: 0, k: code === 1 ? 0 : 1, d: receivedAt, receivedAt, timeSource: 'js-receive'
      })}))
      offset += length
    }
    this.pending.splice(0, offset)
  }
  async close() {
    this.keepReading = false
    this.pending = []
    const port = this.port
    if (this.reader) await this.reader.cancel()
    if (this.readTask) {
      try { await this.readTask } catch (error) { console.error('串口读取已终止', error) }
      this.readTask = null
    }
    if (port && (port.readable || port.writable)) await port.close()
    if (this.port === port) this.port = null
  }
}
