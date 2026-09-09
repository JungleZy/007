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
    this.key_lock = false
    this.cacheArr = []
    this.code = []
    this.codeDown = []
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
        // console.log('初始化失败:', err)
        callback({code: -1}) // 统一错误回调
      }
    } else {
      callback({code: -1})
    }
  }

  async resetPort() {
    let that = this
    that.port = await navigator.serial.requestPort()
    await that.handlePort(callback)
  }

  async handlePort(callback) {
    const that = this
    try {
      await that.port.open({baudRate: that.baudRate})
    } catch (err) {
      // alert('当前串口已在别处被打开')
    }
    that.keepReading = true
    that.reader = that.port.readable.getReader()
    callback({code: 10})
    // 监听来自串行设备的数据
    while (that.port.readable && that.keepReading) {
      const {value, done} = await that.reader.read()
      if (done || that.keepReading === false) {
        // 允许稍后关闭串口。
        that.reader.releaseLock()
        // await that.port.close()
        break
      }
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
        'lineNotifyTrain',
        'handkeyZuXunTrain',
        'electronKeyZuXunTrain',
        'lineNotifyTrain'
      ]
      if (value && arr.indexOf(pagName) > -1) {
        that.handleData(Array.from(value), callback)
      }
    }
  }

  isElectronKey(array) {
    return array.every(a => CODES.includes(a));
  }

  handleElectronKey(array, callback) {
    // console.log(array);
    callback({
      code: 'Message',
      username: 'server',
      data: {t: 1, k: 0, d: array}
    });
  }
  handleHandKey(array, callback) {
    // console.log(array);
    if(array.length===1&&array[0]===2){
      this.code.push(...array)
      return
    }
    if(this.code.length!==0){
      this.cacheArr.push(...this.code)
      this.code = []
    }
    // 手键
    this.cacheArr.push(...array)
    if (this.cacheArr[0] === 1 && !this.key_lock) {
      // console.log("按下"+Date.now())
      // console.log(this.cacheArr)
      // 按下
      PubSub.publishSync('receiveSendData', {type: 'pressed', data: true})
      this.cacheArr= []
      this.key_lock = true
      callback({
        code: 'Message',
        username: 'server',
        data: {t: 0, k: 0, d: Date.now()}
      })
    } else if (this.cacheArr[0] === 2 && this.key_lock) {
      // console.log("抬起"+Date.now())
      // console.log(this.cacheArr)
      // 抬起
      PubSub.publishSync('receiveSendData', {type: 'pressed', data: false})
      this.cacheArr = []
      this.key_lock = false
      callback({
        code: 'Message',
        username: 'server',
        data: {t: 0, k: 1, d: Date.now()}
      })
    } else {
      this.cacheArr = []
    }
  }


  handleData(array, callback) {
    // console.log(array);
    // 手键 分包处理
    if((array[0]===1&&array.length!==2)||(array[0]===2&&array.length!==3)){
      this.codeDown.push(...array)
      return;
    }
   // 手键粘包处理
    if(this.codeDown.length>8){
      this.codeDown = array
    }
    if(this.codeDown.length!==0||array.length>3){
      // console.log("this.codeDown");
      // console.log(JSON.stringify(this.codeDown));
      // console.log(JSON.stringify(array));
      // console.log("this.codeDown");
      this.codeDown.push(...array)
      this.record(callback)
      // this.codeDown = []
      return
    }


    if (this.isElectronKey(array)) {
      this.handleElectronKey(array, callback)
    } else {
      this.handleHandKey(array, callback)
    }
  }

  record(callback){
    if(this.codeDown[0]===1){
      this.handleHandKey(this.codeDown.splice(0,2), callback)
      this.record(callback)
    }else if (this.codeDown[0]===2){
      this.handleHandKey(this.codeDown.splice(0,3), callback)
      this.record(callback)
    }
  }
  async close() {
    if (this.port) {
      this.keepReading = false;
      try {
        await this.reader.cancel();
      } catch (e) {
        console.warn('关闭读取器时发生错误:', e);
      } finally {
        if (this.port.readable) {
          this.reader.releaseLock();
        }
        console.log(this.port)
        await this.port.close();
        this.port = null;
      }
    }
  }
}
