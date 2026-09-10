<template>
  <div style="width: 100%; padding-bottom: 30px;height: 100%" class="layout-center">
    <div class="box relative">
      <div class="content overflow-auto layout-center">
        <a-spin size="large" :spinning="loading" tip="通信呼叫中...">
          <table style="display: flow-root">
            <tr v-for="(item, index) of tableData" :key="index">
              <td
                class="tableTd"
                @click="cliceTd(index, num)"
                v-for="(v, num) of item"
                :key="num"
                :colspan="v.colspan"
                :rowspan="v.rowspan"
                style="min-width: 60px"
                :style="{ height: v.height + 'px', width: v.width + 'px' }"
                :class="[
                  (v.hasOwnProperty('equipmentValue') && v.equipmentValue != v.value && trainData.trainStatus == 1) || (v.equipmentValue == '' && v.equipmentValue != 0) || (v.equipmentValue == '' && v.equipmentValue != 0 && trainData.trainStatus == 1) ? 'redText' : '',
                  isTitle(v.value) ? 'tableTitle' : ''
                ]"
                :title="Array.isArray(v.equipmentValue) ? v.equipmentValue.toString() : v.equipmentValue"
              >
                {{ v.value == 'table' ? '' : v.value }}
                <table v-if="v.value == 'table'" style="width: 100%">
                  <tr v-for="(item2, index2) of tableData2" :key="index2">
                    <td
                      class="tableTd"
                      @click.stop="cliceTd(index2, num2)"
                      :class="[(v2.equipmentValue && v2.equipmentValue != v2.value && trainData.trainStatus == 1) || (v2.equipmentValue == '' && trainData.trainStatus == 1) ? 'redText' : '', isTitle(v2.value) ? 'tableTitle' : '']"
                      v-for="(v2, num2) of item2"
                      :key="num2"
                      :colspan="v2.colspan"
                      :rowspan="v2.rowspan"
                      :style="{ height: v2.height + 'px', width: 75 + 'px' }"
                      :title="v2.equipmentValue"
                    >
                      {{ v2.value }}
                    </td>
                  </tr>
                </table>
              </td>
            </tr>
          </table>
        </a-spin>
      </div>
      <div class="endTrain" @click="establishMQTT">通信呼叫</div>
<!--      <div class="endTrain" @click="establishMQTT">通信呼叫<span v-if="trainData.trainStatus == 1">{{ formData.score > 0 ? formData.score : 0 }}</span></div>-->
      <div v-if="trainData.trainStatus == 1" class="endTrain">分数：{{ formData.score > 0 ? formData.score : 0 }}</div>
<!--      <div class="interference layout-left-top">-->
<!--        <div class="item layout-center" :class="[item.play ? 'play' : '']" v-for="item of disturbList" :key="item">-->
<!--          {{ item.name }}-->
<!--          <div class="l" style="width: 18px; height: 18px; flex-shrink: 0; margin-left: 10px">-->
<!--            <a-spin v-if="item.loading"></a-spin>-->
<!--            <template v-else>-->
<!--              <img :src="pause" v-if="item.play" @click="changeAudioPlay(item, false)" />-->
<!--              <img :src="play" v-else @click="changeAudioPlay(item, true)" />-->
<!--            </template>-->
<!--          </div>-->
<!--        </div>-->
<!--      </div>-->
    </div>
  </div>
</template>

<script>
export default {
  name: 'Equipment'
}
</script>
<script setup>
import { ref, onMounted, nextTick, onUnmounted, onBeforeUnmount,defineProps} from 'vue'
import { message } from 'ant-design-vue'
import Paho from '../../../../common/mqtt/paho-mqtt'
import { useRoute, useRouter } from 'vue-router'
import table from './js/table'
import equipment_171 from './js/171'
import equipment_125W_400W from './js/125W_400W'
import equipment_134A from './js/134A'
import equipment_173 from './js/173'
import equipment_121C from './js/121C'
import { getDetails, addTrain, generalGroupNetRuleFindAll } from '../../../../common/api/equipment'
import play from '../../../../assets/HJ/term/play-ico.png'
import pause from '../../../../assets/HJ/term/pause-ico.png'

const route = useRoute()
const router = useRouter()
const disturbList = ref([
  {
    type: 1,
    name: '白噪音',
    url: '/006/noise/noise0.wav',
    prevPlay: false,
    play: false,
    mute: false,
    volume: 0,
    playVolume: 50,
    loading: false,
    ctx: null,
    xhr: null,
    gain: null,
    source: null
  },
  {
    type: 2,
    name: '俄语',
    url: '/006/noise/noise1.wav',
    prevPlay: false,
    play: false,
    mute: false,
    volume: 0,
    playVolume: 50,
    loading: false,
    ctx: null,
    xhr: null,
    gain: null,
    source: null
  },
  {
    type: 3,
    name: '日语',
    url: '/006/noise/noise2.wav',
    prevPlay: false,
    play: false,
    mute: false,
    volume: 0,
    playVolume: 50,
    loading: false,
    ctx: null,
    xhr: null,
    gain: null,
    source: null
  },
  {
    type: 4,
    name: '英语',
    url: '/006/noise/noise3.wav',
    prevPlay: false,
    play: false,
    mute: false,
    volume: 0,
    playVolume: 50,
    loading: false,
    ctx: null,
    xhr: null,
    gain: null,
    source: null
  },
  {
    type: 5,
    name: '战场音',
    url: '/006/noise/noise4.wav',
    prevPlay: false,
    play: false,
    mute: false,
    volume: 0,
    playVolume: 50,
    loading: false,
    ctx: null,
    xhr: null,
    gain: null,
    source: null
  },
  {
    type: 6,
    name: '防空警报',
    url: '/006/noise/noise5.wav',
    prevPlay: false,
    play: false,
    mute: false,
    volume: 0,
    playVolume: 50,
    loading: false,
    ctx: null,
    xhr: null,
    gain: null,
    source: null
  }
])
const {trainDataP} = defineProps({trainDataP: Object})
console.log("trainDataP")
console.log(trainDataP)
console.log("trainDataP")
//联络文件样式
const { llwjData, llwjData2, dpData_220, tpData_220, dptpData, zdkzData, zsyData, dpData_171, tpData_171, dpData_173, tpData_173 } = table()
const { initData_171, endTrain_171, titleHeader_171 } = equipment_171()
const { initData_125W_400W, endTrain_400W, endTrain_125W, titleHeader } = equipment_125W_400W()
const { initData_134A, endTrain_134A, titleHeader_134A } = equipment_134A()
const { initData_173, endTrain_173, titleHeader_173 } = equipment_173()
const { initData_121C, endTrain_121C, titleHeader_121C } = equipment_121C()
const formData = ref()

let mqttClint = null
const tableData = ref()
const tableData2 = ref()
const trainData = ref({
  trainStatus: 0
})
const scoreList = ref([])
const docCode = ref('')
const loading = ref(false)
const interval = ref(null)
const trainID = ref(JSON.parse(window.localStorage.getItem('userInfo')))
const numValue = ref(0)
let titleHeaders = ref([]) //需要高亮的表格
let f = ref('0000020001/源设备ID/0001/0008') //发送主题
let s = ref('目的设备ID/0000020001/0001/0008') //收取主题
const mqttUrl = window.mqttUrl
onMounted(() => {
  // 43.89 400W   43.138 125W   43.89 134A    185  / 113  173
  // getDetails({ id:Math.random() }).then(res => {
    //订阅主题
    f = '0000020001/' + trainDataP.deviceId + '/0001/0008'
    s = trainDataP.deviceId + '/0000020001/0001/0008'
    trainData.value = trainDataP
    formData.value = JSON.parse(trainDataP.content)
    trainData.value.trainStatus = trainDataP.trainStatus
    if (trainDataP.trainType == 0) {
      tableData.value = llwjData
      tableData2.value = llwjData2
      titleHeaders.value = titleHeader(formData)
      if (trainDataP.deviceName === '125W') {
        docCode.value = '125W01'
      } else if (trainDataP.deviceName === '400W') {
        docCode.value = '400W01'
      }
      initData_125W_400W(formData, tableData, tableData2, trainDataP.trainStatus == 1)
    } else if (trainDataP.trainType == 1) {
      //定频
      switch (trainDataP.deviceName) {
        case '134A':
          tableData.value = dpData_220
          titleHeaders.value = titleHeader_134A(1)
          initData_134A(formData, tableData, trainDataP.trainStatus == 1, trainDataP.trainType)
          docCode.value = '134A01'
          break
        case '171':
          tableData.value = dpData_171
          titleHeaders.value = titleHeader_171()
          initData_171(formData, tableData, trainDataP.trainStatus == 1)
          docCode.value = '17101'
          break
        case '173':
          tableData.value = dpData_173
          titleHeaders.value = titleHeader_173(1)
          initData_173(formData, tableData, trainDataP.trainStatus == 1)
          docCode.value = '17301'
          break
        case '121C':
          tableData.value = dpData_171
          titleHeaders.value = titleHeader_121C(1)
          initData_121C(formData, tableData, trainDataP.trainStatus == 1)
          docCode.value = '121C01'
      }
    } else if (trainDataP.trainType == 2) {
      switch (trainDataP.deviceName) {
        case '134A':
          tableData.value = tpData_220
          titleHeaders.value = titleHeader_134A(2)
          initData_134A(formData, tableData, trainDataP.trainStatus == 1, trainDataP.trainType)
          docCode.value = '134A02'
          break
        case '173':
          tableData.value = tpData_173
          titleHeaders.value = titleHeader_173(2)
          initData_173(formData, tableData, trainDataP.trainStatus == 1)
          docCode.value = '17302'

          break
        case '121C':
          tableData.value = tpData_171
          titleHeaders.value = titleHeader_121C(2)
          initData_121C(formData, tableData, trainDataP.trainStatus == 1)
          docCode.value = '121C02'
      }
    } else if (trainDataP.trainType == 3) {
      tableData.value = zdkzData
      switch (trainDataP.deviceName) {
        case '134A':
          tableData.value = zdkzData
          // initData_134A(formData,tableData,res.data.trainStatus==1)
          break
      }
    } else if (trainDataP.trainType == 4) {
      switch (trainDataP.deviceName) {
        case '134A':
          tableData.value = zsyData
          titleHeaders.value = titleHeader_134A(3)
          initData_134A(formData, tableData, trainDataP.trainStatus == 1, trainDataP.trainType)
          docCode.value = '134A03'
          break
      }
    }
    queryDoc()
  // })
})
const establishMQTT = () => {
  if (loading.value) {
    message.error('通信呼叫中，请不要重复点击')
    return
  }
  if (mqttClint) {
    try {
      mqttClint.disconnect()
      mqttClint = null
    } catch (e) {
      mqttClint = null
    }
  }
  mqttClint = new Paho.MQTT.Client(mqttUrl, 8083, '')
  mqttClint.connect({ userName: '', password: '' })
  mqttClint.onMessageArrived = w => {
    const messages = JSON.parse(w.payloadString)
    console.log("messages")
    console.log(messages)
    console.log("messages")
    loading.value = false
    endTrain(messages)
  }
  loading.value = true
  setTimeout(() => {
    try {
      mqttClint.subscribe(s, { qos: 1 })
    } catch (e) {
      loading.value = false
      message.error('通信呼叫失败，请刷新后尝试')
    }
    finishTrain()
  }, 2000)
}
const queryDoc = () => {
  generalGroupNetRuleFindAll().then(res => {
    if (res.code === 200) {
      let list = res.data
      list.forEach(e => {
        if (e.code === docCode.value) {
          scoreList.value = JSON.parse(e.xyScore)
        }
      })
    }
  })
}
//判断表格是否为表头，添加样式
const isTitle = value => {
  if (titleHeaders.value.some(items => items == value)) {
    return true
  } else {
    return false
  }
}
const cliceTd = (i, n) => {}

//通知设备结束训练
const finishTrain = () => {
  mqttClint.send(f, JSON.stringify({ 指令: '查询', trainID: trainID.value.id }), 1)
  setTimeout(() => {
    if (loading.value) {
      loading.value = false
      message.error('通信呼叫失败，请刷新后尝试')
    }
  }, 2000)

  // if (!loading.value) {
  //       mqttClint.send(f, JSON.stringify({指令: '查询', trainID: trainID.value.id}), 1)
  //   } else {
  //       message.error('通信呼叫中，请不要重复点击')
  //       return
  //   }
  //   setTimeout(() => {
  //       if (loading.value) {
  //           interval.value = setInterval(() => {
  //               if (!loading.value) {
  //                   clearInterval(interval.value)
  //               }
  //               mqttClint.send(f, JSON.stringify({指令: '查询', trainID: trainID.value.id}), 1)
  //               numValue.value = numValue.value + 1
  //               if (numValue.value > 1) {
  //                   numValue.value = 0;
  //                   clearInterval(interval.value)
  //                   loading.value = false
  //                   message.error('通信呼叫失败，请刷新后尝试')
  //               }
  //           }, 2000)
  //       }
  //   }, 2000)
}
//结束训练获取参数后逻辑
const endTrain = messages => {
  if (!(messages['训练Id'] === trainID.value.id || messages['训练ID'] === trainID.value.id)) {
    return
  }
  if (trainData.value.deviceName == '171') {
    endTrain_171(formData, tableData, messages, scoreList)
  } else if (trainData.value.deviceName == '400W') {
    endTrain_400W(formData, tableData, tableData2, messages, trainData.value.trainName, scoreList)
  } else if (trainData.value.deviceName == '134A') {
    endTrain_134A(formData, tableData, messages, trainData.value.trainType, scoreList)
  } else if (trainData.value.deviceName == '125W') {
    endTrain_125W(formData, tableData, tableData2, messages, scoreList)
  } else if (trainData.value.deviceName == '173') {
    endTrain_173(formData, tableData, messages, scoreList)
  } else if (trainData.value.deviceName == '121C') {
    endTrain_121C(formData, tableData, messages, scoreList)
  }
  trainData.value.content = JSON.stringify(formData.value)
  trainData.value.trainStatus = 1
  addTrain(trainData.value).then(res => {
    trainData.value.trainStatus = 1
  })
}
/**
 * 开始干扰/停止干扰
 * @param item
 * @param status
 */
const fileUrl = ref(window.fileUrl)
const changeAudioPlay = (item, status) => {
  item.play = status
  if (status) {
    item.loading = true
    item.ctx = new (AudioContext || window.webkitAudioContext)()
    item.xhr = new XMLHttpRequest()
    item.xhr.open('GET', fileUrl.value + item.url, true)
    item.xhr.responseType = 'arraybuffer'
    item.xhr.onload = () => {
      item.volume = item.playVolume
      item.ctx.decodeAudioData(item.xhr.response, buffer => {
        item.source = item.ctx.createBufferSource()
        item.source.buffer = buffer
        item.source.loop = true
        item.gain = item.ctx.createGain()
        item.gain.gain.value = Number(item.playVolume / 100)
        item.gain.connect(item.ctx.destination)
        item.source.connect(item.gain)
        item.source.start(0)
        item.loading = false
      })
    }
    item.xhr.send()
  } else {
    item.volume = 0
    item.source.stop(0)
    item.ctx = null
    item.xhr = null
    item.source = null
    item.gain = null
  }
}

onUnmounted(() => {
  disturbList.value.map(item => {
    if (item.play) {
      item.prevPlay = item.play
      changeAudioPlay(item, false)
    }
  })
})

onBeforeUnmount(() => {
  if (mqttClint) {
    try {
      mqttClint.disconnect()
      mqttClint = null
    } catch (e) {
      mqttClint = null
    }
  }
})
window.onbeforeunload = function (event) {
  if (mqttClint) {
    try {
      mqttClint.disconnect()
      mqttClint = null
    } catch (e) {
      mqttClint = null
    }
  }
}
</script>
<style scoped lang="less">
.play {
  color: #e9deb2;
}

.interference {
  position: absolute;
  top: -35px;
  right: 0%;

  .item {
    padding: 0 20px;
    font-size: 16px;
  }
}

.endTrain {
  position: absolute;
  padding: 10px;
  background: #0a2950;
  cursor: pointer;
  bottom: -22px;
  background: url('../../../../assets/HJ/postTrain/btnBg_end.png');
  width: 246px;
  height: 40px;
  text-align: center;
  left: calc(50% - 123px);
  font-size: 16px;
  color: #e9deb2;
  font-weight: bold;
  line-height: 20px;
}

.box {
  width: 98%;
  height: calc(100% - 40px);
  border: 6px solid #57606b;
  padding: 10px 10px 30px 10px;
  position: relative;
  border-radius: 15px;
}

.redText {
  color: red !important;
}

.box:before {
  content: '';
  width: calc(100% + 12px);
  height: calc(100% + 12px);
  position: absolute;
  background-image: url('../../../../assets/HJ/equipment/leftTop.png'), url('../../../../assets/HJ/equipment/leftBottom.png'), url('../../../../assets/HJ/equipment/rightTop.png'), url('../../../../assets/HJ/equipment/rightBottom.png');
  background-repeat: no-repeat;
  background-position: left top, left bottom, right top, right bottom;
  top: -6px;
  left: -6px;
}

.content {
  width: 100%;
  height: 100%;
  position: relative;
}

.tableTd {
  border: 1px solid rgba(255, 255, 255, 0.5);
  text-align: center;
  background: #18222d;
  color: #bfcde0;
  font-size: 14px;
}

.tableTitle {
  background: #31566e !important;
  color: #70a3b8 !important;
}
</style>
