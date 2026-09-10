import { defineStore } from 'pinia' //引入pinia

//这里官网是单独导出  是可以写成默认导出的  官方的解释为大家一起约定仓库用use打头的单词 固定统一小仓库的名字不易混乱
export const useTrafficStore = defineStore('traffic', {
  state: () => {
    return {
      linkStatus: false, // 链路连接状态
      devStatus: false // 设备连接状态
    }
  }
})
export const useTrafficDataStore = defineStore('trafficData', {
  state: () => {
    return {
      message: {}
    }
  }
})
export const useTrafficAudioStore = defineStore('trafficAudio', {
  state: () => {
    return {
      audioStatus: false
    }
  }
})
