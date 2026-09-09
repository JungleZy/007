import { keyCode } from '../../../views/manage/preJob/ditto/wording/js/termData'

const forwardTable = {
  letter: {
    A: [0, 1],
    B: [1, 0, 0, 0],
    C: [1, 0, 1, 0],
    D: [1, 0, 0],
    E: [0],
    F: [0, 0, 1, 0],
    G: [1, 1, 0],
    H: [0, 0, 0, 0],
    I: [0, 0],
    J: [0, 1, 1, 1],
    K: [1, 0, 1],
    L: [0, 1, 0, 0],
    M: [1, 1],
    N: [1, 0],
    O: [1, 1, 1],
    P: [0, 1, 1, 0],
    Q: [1, 1, 0, 1],
    R: [0, 1, 0],
    S: [0, 0, 0],
    T: [1],
    U: [0, 0, 1],
    V: [0, 0, 0, 1],
    W: [0, 1, 1],
    X: [1, 0, 0, 1],
    Y: [1, 0, 1, 1],
    Z: [1, 1, 0, 0]
  },
  long: {
    0: [1, 1, 1, 1, 1],
    1: [0, 1, 1, 1, 1],
    2: [0, 0, 1, 1, 1],
    3: [0, 0, 0, 1, 1],
    4: [0, 0, 0, 0, 1],
    5: [0, 0, 0, 0, 0],
    6: [1, 0, 0, 0, 0],
    7: [1, 1, 0, 0, 0],
    8: [1, 1, 1, 0, 0],
    9: [1, 1, 1, 1, 0]
  },
  short: {
    0: [1],
    1: [0, 1],
    2: [0, 0, 1],
    3: [0, 0, 0, 1, 1],
    4: [0, 0, 0, 0, 1],
    5: [0, 0, 0, 0, 0],
    6: [1, 0, 0, 0, 0],
    7: [1, 1, 0, 0, 0],
    8: [1, 0, 0],
    9: [1, 0]
  }
}
const keyCodes = {
  A: [0, 1],
  B: [1, 0, 0, 0],
  C: [1, 0, 1, 0],
  D: [1, 0, 0],
  E: [0],
  F: [0, 0, 1, 0],
  G: [1, 1, 0],
  H: [0, 0, 0, 0],
  I: [0, 0],
  J: [0, 1, 1, 1],
  K: [1, 0, 1],
  L: [0, 1, 0, 0],
  M: [1, 1],
  N: [1, 0],
  O: [1, 1, 1],
  P: [0, 1, 1, 0],
  Q: [1, 1, 0, 1],
  R: [0, 1, 0],
  S: [0, 0, 0],
  T: [1],
  U: [0, 0, 1],
  V: [0, 0, 0, 1],
  W: [0, 1, 1],
  X: [1, 0, 0, 1],
  Y: [1, 0, 1, 1],
  Z: [1, 1, 0, 0],
  0: [1, 1, 1, 1, 1],
  1: [0, 1, 1, 1, 1],
  2: [0, 0, 1, 1, 1],
  3: [0, 0, 0, 1, 1],
  4: [0, 0, 0, 0, 1],
  5: [0, 0, 0, 0, 0],
  6: [1, 0, 0, 0, 0],
  7: [1, 1, 0, 0, 0],
  8: [1, 1, 1, 0, 0],
  9: [1, 1, 1, 1, 0],
  '?': [0, 0, 1, 1, 0, 0],
  '/': [1, 0, 0, 1, 0],
  '<': [1, 0, 1, 1, 0, 1],
  '>': [1, 0, 1, 1, 0, 1],
  '。': [0, 0, 2, 0, 0, 2, 0, 0],
  '.': [0, 1, 0, 1, 0, 1]
}
const NUM_TYPE = {
  LONG: 'long',
  SHORT: 'short'
}
// let osc;
export default class MorseVoice {
  constructor(obj) {
    obj = obj || {
      criterion: 80,
      ratio: {
        dot: 1, // 比例 点长度
        dash: 3, // 比例 划长度
        gap: 1, // 比例 点划间隔
        word: 3, // 比例 词间隔
        suite: 5, // 比例 组间隔
        leaf: 7 // 比例 电报纸间隔
      },
      fre: 1000,
      st: 1.0
    }
    this.criterion = obj.criterion ?? 80
    this.ratio = obj.ratio ?? {
      dot: 1, // 比例 点长度
      dash: 3, // 比例 划长度
      gap: 1, // 比例 点划间隔
      word: 3, // 比例 词间隔
      suite: 5, // 比例 组间隔
      leaf: 7 // 比例 电报纸间隔
    }
    this.fre = obj.fre ?? 1000
    this.st = obj.st ?? 1.0
    this.datum = {}
    this.handleDatum()
    this.audioX = null
    this.isClear = false
    this.nowSignal = -1
    this.clearCallback = null
    this.waitTimer = null
    this.osc = null
  }
  //单词联系
  getCode(str) {
    let arr = []
    let keys = str.split('')
    keys.map((key, k) => {
      if (key === ' ') {
        arr.push(3)
      } else {
        arr.push(...keyCodes[key])
        if (keys[k + 1] && keys[k + 1] !== ' ') {
          arr.push(2)
        }
      }
    })
    return arr
  }
  getSignalStrength(signal) {
    if (signal === 0 || signal === 1) {
      return this.fre
    }
    return 0
  }
  createOscillator(message, index, oscillator, gain, callback) {
    const that = this
    let signal = message[index]
    if (!that.isClear && signal) {
      if (that.getSignalStrength(signal[0]) !== 0) {
        gain.gain.setTargetAtTime(that.st, that.audioX.currentTime, 0.003)
      }

      that.waitTimer = setTimeout(function () {
        gain.gain.setTargetAtTime(0, that.audioX.currentTime, 0.003)
        if (index < message.length) {
          that.createOscillator(message, index + 1, oscillator, gain, callback)
          if (signal[0] !== 2) {
            that.nowSignal = signal[1]
          }
          index += 1
        } else {
          clearTimeout(that.waitTimer)
          if (that.audioX) {
            oscillator.stop()
            that.audioX.close()
            that.audioX = null
          }
        }
        if (callback && signal[0] !== 2) {
          callback(signal[1])
        }
      }, that.datum[signal[0]] - 3)
    } else {
      if (that.clearCallback) {
        that.clearCallback(that.nowSignal)
      }
    }
  }
  /**
   * 开始播放音频
   * @param {*} data 摩尔斯电码
   * @param {*} callback 返回当前播放的数组下标
   */
  play(data, callback) {
    this.isClear = false
    this.nowSignal = -1
    this.clearCallback = null
    let dataResult = []
    for (let i = 0; i < data.length; i++) {
      let td = Number(data[i])
      if (td === 0 || td === 1) {
        dataResult.push([td, i])
        dataResult.push([2, i])
      } else if (td === 2) {
        dataResult = dataResult.slice(0, -1)
        dataResult.push([3, i])
      } else if (td === 3) {
        dataResult = dataResult.slice(0, -1)
        dataResult.push([4, i])
      } else if (td === 4) {
        dataResult = dataResult.slice(0, -1)
        dataResult.push([5, i])
      }
    }
    let gain,
        mIndex = 0
    if (this.audioX === null) {
      this.audioX = new (AudioContext || window.webkitAudioContext)()
    }
    this.osc = this.audioX.createOscillator()
    gain = this.audioX.createGain()
    this.osc.type = 'sine'
    this.osc.frequency.value = this.fre
    this.osc.connect(gain)
    gain.connect(this.audioX.destination)
    this.osc.start()
    gain.gain.value = 0
    this.createOscillator(dataResult, mIndex, this.osc, gain, callback)
  }
  /**
   * 停止播放音频当前音频
   * @param {*} callback 返回当前停止的数组下标
   */
  clear(callback) {
    this.isClear = true
    if (this.audioX && this.audioX.state !== 'closed') {
      clearTimeout(this.waitTimer)
      this.audioX.close()
      this.audioX = null
      this.osc = null
      callback && callback(this.nowSignal)
    } else {
      this.audioX = null
      this.osc = null
      callback && callback(this.nowSignal)
    }
  }
  /**
   * 改变音量大小
   * @param {*} volume 音量
   */
  changeVolume(volume) {
    this.st = volume / 100
  }

  /**
   * 改变音频频率
   * @param {*} fre 频率
   */

  changeFre(fre) {
    console.log(fre)
    this.fre = fre
    if (this.osc) {
      this.osc.frequency.value = fre
    }
  }

  /**
   * 改变基准时长（点时长）
   * 单位毫秒
   * @param {*} criterion 点时长
   */

  changeCriterion(criterion) {
    this.criterion = criterion
    this.handleDatum()
  }

  /**
   * 改变点、划、词、组比例
   * @param {*} ratio
   */

  changeRatio(ratio) {
    this.ratio = ratio
    this.handleDatum()
  }

  /**
   * 改变信号强度
   * @param {*} st
   */
  changeSt(st) {
    this.st = st
  }

  handleDatum() {
    this.datum = {
      0: this.criterion * this.ratio['dot'],
      1: this.criterion * this.ratio['dash'],
      2: this.criterion * this.ratio['gap'],
      3: this.criterion * this.ratio['word'],
      4: this.criterion * this.ratio['suite'],
      5: this.criterion * this.ratio['leaf']
    }
  }

  /**
   * 莫尔斯码转换
   * @param {*} data
   * @param {*} codeType
   * @returns
   */

  convert(obj) {
    obj.numType = obj.numType ?? 'long'
    const list = [...obj.data]
    let result = []
    list.forEach(e => {
      e = e.replace(/\s/g, '')
      if (e === '') {
        result.push({ key: ' ', value: [3] })
      } else {
        if (isNaN(e)) {
          result.push({
            key: e,
            value: [...forwardTable['letter'][e.toUpperCase()], 2]
          })
        } else {
          result.push({ key: e, value: [...forwardTable[obj.numType][e], 2] })
        }
      }
    })
    result[result.length - 1].value.pop()
    return result
  }
}
export { forwardTable, NUM_TYPE }
