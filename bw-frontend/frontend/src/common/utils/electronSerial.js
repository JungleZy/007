import {ipcRenderer, ipcApi} from '../../electron/index'
import {traffic} from '../../config/pinia/index.js'
import {PubSub} from './PubSub'

const trafficStore = traffic.useTrafficStore()
const CODES = [
  11, 12, 13, 14, 15, 21, 22, 23, 24, 25, 31, 32, 33, 34, 35, 41, 42, 43, 44, 45
]
let key_lock = false
let liftTimer = null
let cacheArr = []
let isFirst = true
export default class WebSerial {
  constructor(rate, fre) {
    this.port = null
    this.history = []
  }

  async init(callback, type) {
    const that = this
    callback({code: 10})
    if (isFirst) {
      that.handlePort(callback)
    }
    ipcRenderer.ipc.removeAllListeners(ipcApi.ipcApiRoute.linkPort)
    ipcRenderer.ipc.on(ipcApi.ipcApiRoute.linkPort, (event, result) => {
      if (result && result.code && result.code === 500) {
        trafficStore.$patch({
          linkStatus: false,
          devStatus: false
        })
      }
      const value = result
      let pagName = location.hash.split('?')[0].split('/')[
      location.hash.split('/').length - 1
        ]
      let arr = [
        'handKeyBasicTrain',
        'handKeyTrain',
        'examBasicExam',
        'examComplexTrain',
        'handKeyPostJobTrain',
        'examPostJobTrain',
        'handkeyZuXunTrain',
        'electronKeyZuXunTrain',
        'lineNotifyTrain'
      ]
      if (value && arr.indexOf(pagName) > -1) {
        that.handleData(Array.from(value), callback)
      }
    })
  }

  async handlePort(callback) {
    const that = this
    // document.documentElement.addEventListener('mousedown', () => {
    //     if (that.audioX == null || that.audioX.state != 'running') {
    //         that.createVoice()
    //     }
    // })
    callback({code: 10})
  }
  isElectronKey(array) {
    return array.every(a => CODES.includes(a));
  }
  handleData(array, callback) {
    if (this.isElectronKey(array)) {
      //电子键
      callback({
        code: 'Message',
        username: 'server',
        data: {t: 1, k: 0, d: array}
      })
    } else {
      // 手键
      cacheArr.push(...array)
      if (cacheArr[0] === 1 && !key_lock) {
        PubSub.publishSync('receiveSendData', {type: 'pressed', data: true})
        // 按下
        cacheArr = []
        // if (cacheArr.length === 2 && !key_lock) { // 正常按下
        key_lock = true
        callback({
          code: 'Message',
          username: 'server',
          data: {t: 0, k: 0, d: Date.now()}
        })
      } else if (cacheArr[0] === 2 && key_lock) {
        PubSub.publishSync('receiveSendData', {type: 'pressed', data: false})
        // 抬起
        cacheArr = []
        key_lock = false
        callback({
          code: 'Message',
          username: 'server',
          data: {t: 0, k: 1, d: Date.now()}
        })
      } else {
        cacheArr = []
      }
    }
  }
}
