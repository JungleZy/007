<template>
  <div class="wrapper">
    <div class="char_room" :style="{ left: roomValue }">
      <TrainLeft :trainData="{}">
        <template v-slot:bottom>
          <div class="time_cont_box" v-if="isShow && !isEnd">
<!--            <count-down class="width-100-per layout-center" color="#e9deb2" ref="trainTimeRef" style="height: 55px" />-->
          </div>
          <div class="lssued_box">
            <div class="title">接收文书</div>
            <div class="content">
              <p v-for="(item, index) in sendLessudTextList" :key="index">{{ item }}</p>
            </div>
          </div>
          <div class="user_list_box">
            <div class="title">发送人分组</div>
            <div class="user_list">
              <div class="send_list">
                <div class="pd_user_infor" v-for="item in sendUserList" :key="item.id">
                  <strong style="width: 30px; font-size: 16px">{{ item.channel + 1 }}</strong>
                  <div class="image"><img :src="`${url}${item.userImg}`" alt="" /></div>
                  <div class="name">{{ item.userName }}</div>
                  <div class="state" style="color: #de940b" v-if="roomDetails.stats == 2">已完成</div>
                  <div class="state" style="color: #af0505" v-else-if="item.socketStatus == 0">离线</div>
                  <div class="state" style="color: #40a9ff" v-else-if="item.socketStatus == 1 && roomDetails.stats == 0">在线</div>
                  <div class="state" style="color: #52c41a" v-else-if="item.socketStatus > 0 && roomDetails.stats == 1">进行中</div>
                  <div class="state" style="color: #52c41a" v-else-if="item.socketStatus == 2 && roomDetails.stats == 0">准备</div>
                  <div class="room_state" style="color: #4ca7f5" v-if="item.status == 1"><FileTextOutlined /></div>
                </div>
              </div>
            </div>
          </div>

          <div class="item_group btn layout-center" style="width: 100%; margin-top: 5px" v-if="buttonState && pinUserState != 1 && roomDetails.stats == 0" @click="handleStart">准备训练</div>
        </template>
      </TrainLeft>
    </div>
    <div class="content_image" v-if="isEnd">
      <img :src="receiveBg" alt="" />
    </div>
    <FillInResult v-if="isShow && !isEnd" @result="fillInTrainResult"></FillInResult>
    <TrainResult style="margin-left: 20px" v-if="!isShow && !isEnd" :result="a" :curr="curr" @switchPage="handlePageTurn" :all="all" :details="details"></TrainResult>
    <div class="char_room" :style="{ left: roomValue }">
      <TrainLeft :trainData="{}">
        <template v-slot:bottom>
          <div class="change_channel" v-if="isEnd">
            <div class="title">切换频段</div>
            <div class="select_box">
              <p>当前频段：{{ pinValue + 1 }}频段</p>
              <a-select v-model:value="pinValue" placeholder="请选择频段" @change="changePin" style="width: 100%">
                <a-select-option :value="item" v-for="item in pinList" :key="item"> {{ item + 1 }}频段 </a-select-option>
              </a-select>
            </div>
          </div>
          <div class="user_list_box">
            <div class="title">接收人分组</div>
            <div class="user_list">
              <div class="send_list">
                <div class="pd_user_infor" v-for="item in putAwayUserList" :key="item.id">
                  <strong style="width: 30px; font-size: 16px">{{ item.channel + 1 }}</strong>
                  <div class="image"><img :src="`${url}${item.userImg}`" alt="" /></div>
                  <div class="name">{{ item.userName }}</div>
                  <div class="state" style="color: #de940b" v-if="roomDetails.stats == 2">已完成</div>
                  <div class="state" style="color: #af0505" v-else-if="item.socketStatus == 0">离线</div>
                  <div class="state" style="color: #40a9ff" v-else-if="item.socketStatus == 1 && roomDetails.stats == 0">在线</div>
                  <div class="state" style="color: #52c41a" v-else-if="item.socketStatus > 0 && roomDetails.stats == 1">进行中</div>
                  <div class="state" style="color: #52c41a" v-else-if="item.socketStatus == 2 && roomDetails.stats == 0">准备</div>
                  <div class="room_state" style="color: #4ca7f5" v-if="item.status == 1"><FileTextOutlined /></div>
                </div>
              </div>
            </div>
          </div>
        </template>
      </TrainLeft>
    </div>
  </div>
</template>

<script>
export default {
  name: 'LineNotifyTrain'
}
</script>
<script setup>
import { ref, onUnmounted, onMounted, nextTick } from 'vue'
import { apiSimulationRouterRoomChannels, apiSimulationRouterChangeChannel, apiSimulationRouterRoomDetail, getRoomUserList, apiSimulationRouterSendFinish } from '../../../../../common/api/UserApi'
import { message, Modal } from 'ant-design-vue'
import FillInResult from '../../disturbCode/FillInResult.vue'
import TrainResult from '../../disturbCode/TrainResult.vue'
import { useRouter, useRoute } from 'vue-router'
import { FileTextOutlined } from '@ant-design/icons-vue'
import { getDisturbCodeTrainData, reportIntoTrainRoom, uploadUnionTrainResult, apiSimulationRouterFindPage } from '../../../../../common/api/UnionApi'
import { log } from '@antv/g2plot/lib/utils'
import iconImage from "../../../postJob/js/iconImage";

const {receiveBg} = iconImage()
const route = useRoute()
const pinValue = ref('')
const pinUserId = ref('')
const pinUserState = ref(0)
const isShow = ref(true)
const roomValue = ref('0px')
const roomState = ref(true)
const pinList = ref([])
const pageAll = ref(0)
const sendLessudTextList = ref([])
const buttonState = ref(true)
const sendUserList = ref([])
const putAwayUserList = ref([])
const url = window.fileUrl
const trainData = ref(0)
const trainTimeRef = ref(null)
const isEnd = ref(true)
const roomDetails = ref({ stats: 0 })
const details = ref(null)
const all = ref(0)
const curr = ref(1)
const disturbList = ref([
  {
    type: 1,
    name: '白噪音',
    url: '/006/noise/noise0.wav',
    buffer: null,
    ctx: null,
    xhr: null,
    gain: null,
    source: null
  },
  {
    type: 2,
    name: '俄语',
    url: '/006/noise/noise1.wav',
    buffer: null,
    ctx: null,
    xhr: null,
    gain: null,
    source: null
  },
  {
    type: 3,
    name: '日语',
    url: '/006/noise/noise2.wav',
    buffer: null,
    ctx: null,
    xhr: null,
    gain: null,
    source: null
  },
  {
    type: 4,
    name: '英语',
    url: '/006/noise/noise3.wav',
    buffer: null,
    ctx: null,
    xhr: null,
    gain: null,
    source: null
  },
  {
    type: 5,
    name: '战场音',
    url: '/006/noise/noise4.wav',
    buffer: null,
    ctx: null,
    xhr: null,
    gain: null,
    source: null
  },
  {
    type: 6,
    name: '防空警报',
    url: '/006/noise/noise5.wav',
    buffer: null,
    ctx: null,
    xhr: null,
    gain: null,
    source: null
  }
])
const disturbVolume = ref(60)

// 训练用时
const autoTime = ref(null)
const trainTime = () => {
  autoTime.value = setInterval(() => {
    trainData.value++
    nextTick(() => {
      if (trainTimeRef.value) {
        trainTimeRef.value.autoSetTimeAdd(trainData.value)
      }
    })
  }, 1000)
}

const user = JSON.parse(localStorage.getItem('userInfo'))
const handlePageTurn = val => {
  if (val == 'prev') {
    curr.value--
  } else {
    curr.value++
  }
  apiSimulationRouterFindPage({
    pageNumber: curr.value,
    roomId: Number(route.query.id),
    userId: user.id
  }).then(res => {
    if (res.code === 200) {
      a.value = res.data
    }
  })
}

const getDisturbAudioSource = () => {
  disturbList.value.map(item => {
    item.ctx = new (AudioContext || window.webkitAudioContext)()
    item.xhr = new XMLHttpRequest()
    item.xhr.open('GET', window.fileUrl + item.url, true)
    item.xhr.responseType = 'arraybuffer'
    item.xhr.onload = () => {
      item.ctx.decodeAudioData(item.xhr.response, buffer => {
        item.buffer = buffer
      })
    }
    item.xhr.send()
  })
}

const handlerZoom = () => {
  roomState.value = !roomState.value
  if (roomState.value) {
    roomValue.value = '0px'
  } else {
    roomValue.value = '-300px'
  }
}
let gain, osc
let audio = null
const voiceFreq = ref(800)
const initVoice = () => {
  audio = new (AudioContext || window.webkitAudioContext)()
  gain = audio.createGain()
  gain.gain.value = 0
  osc = audio.createOscillator()
  gain.connect(audio.destination)
  gain.gain.setTargetAtTime(0, audio.currentTime, 0.003)
  osc.frequency.value = voiceFreq.value
  osc.type = 'sine'
  osc.connect(gain)
  osc.start(0)
}
const playVoice = () => {
  gain.gain.setTargetAtTime(1, audio.currentTime, 0.003)
}
const stopVoice = () => {
  gain.gain.setTargetAtTime(0, audio.currentTime, 0.003)
}
const clearVoice = () => {
  osc?.stop(0)
  osc?.disconnect(gain)
  gain?.disconnect(audio.destination)
  gain = null
  osc = null
}

const changePin = val => {
  const userId = JSON.parse(localStorage.getItem('userInfo'))
  const roomId = route.query.id
  apiSimulationRouterChangeChannel({
    channel: val,
    roomId: route.query.id,
    userId: userId.id
  }).then(res => {
    message.success('切换成功')
    const obj = {
      topic: 'change',
      body: {
        id: userId.id,
        val: val
      }
    }
    ws.send(JSON.stringify(obj))
  })
}

let ws = null
const once = fn => {
  let hasRun = false
  return () => {
    if (hasRun) {
      hasRun = true
      fn.call()
    }
  }
}
//初始化
const init = () => {
  const userId = JSON.parse(localStorage.getItem('userInfo'))
  const roomId = route.query.id
  if (pinUserState.value == 1) return
  ws = new WebSocket(`ws://${window.httpUrl}/simulation/${userId.id}/${roomId}`)
  ws.onopen = function onopen() {
    putAwayUserList.value.forEach(item => {
      if (item.id == pinUserId.value) {
        item.socketStatus = 1
      }
    })
  }
  //预加载噪音
  if (roomDetails.value.stats != 2) {
    getDisturbAudioSource()
  }

  ws.onmessage = function messages(res) {
    const data = JSON.parse(res.data)
    const newData = JSON.parse(data.data)
    if (data.code == -1) return
    if (newData.topic == 'begin') {
      trainTime()
      roomDetails.value.stats = 1
    }
    else if (newData.topic == 'ready') {
      putAwayUserList.value.forEach(item => {
        if (item.id == newData.body.id) {
          item.socketStatus = 2
        }
      })
      sendUserList.value.forEach(item => {
        if (item.id == newData.body.id) {
          item.socketStatus = 2
        }
      })
    }
    else if (newData.topic == 'play') {
      if (newData.body.type == 1) {
        if (newData.body.value == '0') {
          playVoice()
        } else {
          stopVoice()
        }
      } else if (newData.body.type == 2) {
        sendLessudTextList.value.push(newData.body.value)
      } else if (newData.body.type == 3) {
        trainTime()
      } else if (newData.body.type == 4) {
        pinUserState.value = 1
        isEnd.value = false
        clearInterval(autoTime.value)
        getApiRoomUserList()
      } else if (newData.body.type == 5) {
        disturbList.value.map(item => {
          if (item.type == newData.body._type) {
            disturbVolume.value = newData.body.volume
            if (newData.body.value) {
              changeAudioPlay(item, true)
            } else {
              changeAudioPlay(item, false)
            }
          }
        })
      } else if (newData.body.type == 6) {
        disturbList.value.map(item => {
          if (item.ctx && item.gain) {
            item.gain.gain.value = Number(newData.body.value / 100)
          }
        })
      }
    }
    else if (newData.topic == 'online') {
      sendUserList.value.forEach(item => {
        if (item.id == newData.body.id) {
          item.socketStatus = 1
        }
      })
      putAwayUserList.value.forEach(item => {
        if (item.id == newData.body.id) {
          item.socketStatus = 1
        }
      })
    }
    else if (newData.topic == 'offline') {
      sendUserList.value.forEach(item => {
        if (item.id == newData.body.id) {
          item.socketStatus = 0
        }
      })
      putAwayUserList.value.forEach(item => {
        if (item.id == newData.body.id) {
          item.socketStatus = 0
        }
      })
    }
    else if (newData.topic == 'over') {
      getRoomUserList(route.query.id).then(res => {
        if (res.code == 200) {
          sendUserList.value = res.data.sendUserList
          putAwayUserList.value = res.data.receiveUserList
        }
      })
    }
    else if (newData.topic == 'end') {
      isEnd.value = false
      clearInterval(autoTime.value)
      apiSimulationRouterRoomDetail({
        roomId: route.query.id
      }).then(res => {
        roomDetails.value = res.data
      })
    }
    else if (newData.topic == 'change') {
      putAwayUserList.value.forEach(item => {
        if (item.id == newData.body.id) {
          item.channel = 0
          item.channel = newData.body.val
        }
      })
    }
  }
}

/**
 * 开始干扰/停止干扰
 * @param item
 * @param status
 */
const changeAudioPlay = (item, status) => {
  if (status) {
    item.source = item.ctx.createBufferSource()
    item.source.buffer = item.buffer
    item.source.loop = true
    item.gain = item.ctx.createGain()
    item.gain.gain.value = Number(disturbVolume.value / 100)
    item.gain.connect(item.ctx.destination)
    item.source.connect(item.gain)
    item.source.start(0)
  } else {
    item.source.stop(0)
    item.source = null
    item.gain = null
  }
}
//准备训练
const handleStart = () => {
  // isEnd.value = false
  // findTrainDataInfo()
  buttonState.value = false
  const obj = {
    topic: 'ready',
    body: {
      id: pinUserId.value,
      value: '2'
    }
  }
  ws.send(JSON.stringify(obj))
  putAwayUserList.value.forEach(item => {
    if (item.id == pinUserId.value) {
      item.socketStatus = 2
    }
  })
}

const overBulletin = () => {
  Modal.confirm({
    content: '确定要结束训练吗?',
    onOk() {
      //清楚定时器
      clearInterval(autoTime.value)
      isEnd.value = false
      getSimulationRouterRoomDetail()
    }
  })
}

const getSimulationRouterRoomChannels = () => {
  apiSimulationRouterRoomChannels({
    roomId: route.query.id
  }).then(res => {
    pinList.value = res.data
  })
}

const getSimulationRouterRoomDetail = () => {
  apiSimulationRouterRoomDetail({
    roomId: route.query.id
  }).then(res => {
    pinValue.value = res.data.currentUserChannel
    pinUserId.value = res.data.currentUserId
    roomDetails.value = res.data
    getApiRoomUserList()
    if (res.data.stats == 1 && trainTimeRef.value) {
      if (res.data.totalTime && res.data.totalTime != '') {
        trainData.value = parseInt(res.data.totalTime)
      } else {
        trainData.value = 0
      }
      trainTime()
    } else if (res.data.stats == 2 && res.data.totalTime && res.data.totalTime > 0) {
      nextTick(() => {
        if (trainTimeRef.value) {
          trainTimeRef.value.autoSetTimeAdd(res.data.totalTime)
        }
      })
    }
  })
}

const getApiRoomUserList = () => {
  getRoomUserList(route.query.id).then(res => {
    if (res.code == 200) {
      sendUserList.value = res.data.sendUserList
      putAwayUserList.value = res.data.receiveUserList
      putAwayUserList.value.forEach(item => {
        if (item.id == pinUserId.value) {
          pinUserState.value = item.status
          if (item.status == 0 && roomDetails.value.stats == 2) {
            isEnd.value = false
          }
        }
      })
      if (!ws) {
        init()
      }
    }
  })
}

const userInfo = ref(JSON.parse(window.localStorage.getItem('userInfo')))

const fillInTrainResult = res => {
  let arr,
    _res = []
  res.map(pag => {
    arr = []
    pag.map(row => {
      row.map(col => {
        arr.push(col[0])
      })
    })
    _res.push(JSON.stringify(arr))
  })

  uploadUnionTrainResult({
    userId: userInfo.value.id,
    roomId: route.query.id,
    contentValue: _res
  }).then(res => {
    if (res.code == 200) {
      findTrainDataInfo()
      putAwayUserList.value.forEach(item => {
        if (item.id == pinUserId.value) {
          item.status = 1
        }
      })
      const obj = {
        topic: 'over',
        body: {
          id: pinUserId.value,
          value: '4'
        }
      }
      ws.send(JSON.stringify(obj))
    }
  })
}

const a = ref(null)
//结束回显
const findTrainDataInfo = () => {
  getDisturbCodeTrainData({
    roomId: Number(route.query.id)
  }).then(res => {
    let total = Math.ceil(res.data.bwCount / 100)
    let num = total > res.data.existPageNumber ? total : res.data.existPageNumber
    all.value = num
    details.value = res.data
  })
  const userId = JSON.parse(localStorage.getItem('userInfo'))
  apiSimulationRouterFindPage({
    pageNumber: 1,
    roomId: Number(route.query.id),
    userId: userId.id
  }).then(res => {
    if (res.code === 200) {
      isShow.value = false
      a.value = res.data
    }
  })
}
onMounted(async () => {
  getSimulationRouterRoomDetail()
  getSimulationRouterRoomChannels()
  initVoice()
  setTimeout(() => {
    if (pinUserState.value) {
      isEnd.value = false
      findTrainDataInfo()
    }
  }, 500)
})
onUnmounted(() => {
  clearVoice()
  if (ws) {
    ws.close()
  }
  disturbList.value.map(item => {
    if (item.gain != null) {
      item.source.stop(0)
      item.source = null
      item.gain = null
    }
    item.ctx = null
    item.xhr = null
  })
})
</script>

<style lang="less" scoped>

.wrapper {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: row;
  position: relative;
  overflow: hidden;
  .char_room {
    display: flex;
    flex-direction: column;
    height: 100%;
    transition: all 0.2s linear;
    .user_list_box {
      height: calc(100% - 176px);
      overflow: auto;
      .user_list {
        display: flex;
        flex-direction: column;
        .send_list {
          display: flex;
          flex-direction: column;
          .send {
            font-size: 14px;
            line-height: 30px;
            padding-left: 10px;
            background: #272f39;
          }
          .pd_user_infor {
            display: flex;
            flex-direction: row;
            align-items: center;
            position: relative;
            padding: 5px 10px;
            .image {
              width: 35px;
              height: 35px;
              border-radius: 35px;
              overflow: hidden;
              margin-right: 10px;
              img {
                width: 100%;
                height: 100%;
                margin-right: 10px;
              }
            }
            .state {
              position: absolute;
              right: 17px;
            }
            .room_state {
              position: absolute;
              top: 10px;
              right: 60px;
            }
          }
        }
      }
    }
    .lssued_box {
      display: flex;
      flex-direction: column;
      .content {
        height: 99px;
        overflow: auto;
        p {
          margin: 5px;
        }
      }
    }
    .button {
      width: 100%;
      bottom: 0;
      width: 100%;
      height: 40px;
      line-height: 40px;
      text-align: center;
      background: red;
    }
    .title {
      display: flex;
      align-items: center;
      height: 40px;
      padding: 0 16px;
      font-weight: bolder;
      font-size: 16px;
      background: url('../../../../../assets/HJ/train/militaryBg.png') no-repeat center;
      background-size: 100% 100%;
    }
    .chart_content {
      flex: 1;
      height: 0;
      overflow: auto;
      padding: 0 10px;
    }

    .zoom_button {
      position: absolute;
      top: calc(49% - 40px);
      right: -40px;
      width: 40px;
      height: 80px;
      padding: 10px;
      background: #0a1a35;
      display: flex;
      align-items: center;
      justify-content: center;
    }
  }
  .change_channel {
    display: flex;
    flex-direction: column;
    .select_box {
      height: 99px;
      padding: 5px 10px;
      p {
        margin-bottom: 5px;
      }
    }
  }
  .content_image {
    width: 100%;
    height: 100%;
    img {
      width: 100%;
      height: 100%;
    }
  }
}
.time_cont_box {
  width: 260px;
  position: absolute;
  top: 20px;
  left: calc(50% - 130px);
}

/deep/.basicExercise {
  height: 100% !important;
}
/deep/.trainTime {
  display: none;
}
</style>
