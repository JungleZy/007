import {PubSub} from './PubSub.js'

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
    this.connectionTask = Promise.resolve()
  }

  async init(callback, type, isCurrent = () => true, serial = navigator.serial) {
    if (serial) {
      const that = this
      try {
        let port
        // 点击连接时立即请求选择器，不能先等待关口、枚举或加载脚本，
        // 否则可能丢失浏览器的用户激活。serialChrome 仅记录浏览器重连偏好。
        if (type === 'resetLine') {
          const selection = serial.requestPort()
          port = await selection
        } else {
          // 自动重连只复用已授权串口；模拟器由测试工具显式注入后也走这里。
          that.history = await serial.getPorts()
          if (that.history.length === 0) {
            callback({code: -1})
            return
          }
          port = that.history[0]
        }
        if (!isCurrent()) return
        const connect = async () => {
          if (!isCurrent()) return
          await that.closePort()
          if (!isCurrent()) return
          callback({code: 0})
          that.port = port
          await that.handlePort(callback, isCurrent)
        }
        that.connectionTask = that.connectionTask.then(connect, connect)
        await that.connectionTask
        return
      } catch (err) {
        console.error('串口连接或读取失败', err)
        // 取消选择时旧串口仍在读取，保持其在线状态与数据通路。
        callback({code: that.reader ? 10 : -1, error: err})
      }
    } else {
      callback({code: this.reader ? 10 : -1, error: new Error('当前环境不支持真实串口，请使用支持 Web Serial 的浏览器或桌面端')})
    }
  }

  async handlePort(callback, isCurrent) {
    await this.port.open({baudRate: this.baudRate})
    if (!isCurrent()) {
      await this.closePort()
      return
    }
    this.keepReading = true
    const reader = this.port.readable.getReader()
    this.reader = reader
    callback({code: 10})
    this.readTask = this.readFrames(reader, callback).catch(error => {
      console.error('串口读取失败', error)
      callback({code: -1, error})
    })
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
    // 真实串口帧不携带时间戳。注入端可在保留原始字节的同时提供独立发送时刻，
    // 以免页面忙碌时把点划长度误算成 JS 消息处理间隔。
    const receivedAt = performance.now()
    const timing = this.port?.getFrameTiming?.(bytes)
    const sourceTimed = Number.isFinite(timing?.at) && typeof timing?.timeSource === 'string'
    const timestamp = sourceTimed ? timing.at : receivedAt
    const timeSource = sourceTimed ? timing.timeSource : 'js-receive'
    this.pending.push(...bytes)
    let offset = 0
    while (offset < this.pending.length) {
      const code = this.pending[offset]
      if (CODES.includes(code)) {
        callback(Object.freeze({code: 'Message', data: Object.freeze({
          t: 1, k: 0, d: Object.freeze([code]), receivedAt, timeSource
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
        t: 0, k: code === 1 ? 0 : 1, d: timestamp, receivedAt, timeSource
      })}))
      offset += length
    }
    this.pending.splice(0, offset)
  }
  async close() {
    await this.connectionTask.catch(() => {})
    await this.closePort()
  }

  async closePort() {
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
