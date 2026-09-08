<template>
  <div class="w-full h-full overflow-hidden" style="padding: 0 12px 20px; display: flex">
    <div class="w-full h-full unionBox">
      <div class="unionTitle">
        <div class="title">{{ roomInfo.name }}-{{ roomInfo.type }}-{{ roomInfo.status }}</div>
      </div>
      <div class="state layout-center">
        <div class="dataItem room">
          <div>当前状态：<span style="font-weight: bold; font-size: 16px; color: #f6bd70">【未检测】</span></div>
          <div>
            人数: <span style="font-weight: bold; font-size: 16px">{{ roomInfo.users?.length }}/{{ roomInfo.nnt }}</span>
          </div>
        </div>
      </div>
      <div class="w-full layout-center centerBox">
        <div class="center layout-side">
          <div class="leftBox">
            <div class="addBtn" @click="joinGroup(true, 1)"></div>
            <template v-for="user in roomInfo.users">
              <div class="imgBorder" v-if="user.type === 1" :class="[roomInfo.admin === user.id ? 'homeowner' : '']">
                <img :src="fileUrl + user.userImg" :title="user.name" />
              </div>
            </template>
          </div>
          <div class="centerBtn">
            <div class="send"><div class="text">发送席</div></div>
            <div class="receive"><div class="text">接收席</div></div>
          </div>
          <div class="rightBox">
            <div class="addBtn-y" @click="joinGroup(true, 2)"></div>
            <template v-for="user in roomInfo.users">
              <div class="imgBorder" v-if="user.type === 2" :class="[roomInfo.admin === user.id ? 'homeowner' : '']">
                <img :src="fileUrl + user.userImg" :title="user.name" />
              </div>
            </template>
          </div>
        </div>
      </div>
      <div class="bottom layout-left-center">
        <div class="addBtn" @click="joinGroup(false, 0)"></div>
        <template v-for="user in roomInfo.users">
          <div class="imgBorder" v-if="user.type === 0" :class="[roomInfo.admin === user.id ? 'homeowner' : '']">
            <img :src="fileUrl + user.userImg" :title="user.name" />
          </div>
          <!--          <div class="item" >-->
          <!--            <div class="homeowner" v-if="roomInfo.admin===user.id"></div>-->
          <!--          </div>-->
        </template>
      </div>
      <div class="establishRoom" v-if="roomInfo.admin === userInfo.id" @click="handleStatusInspect(1)">{{ roomInfo.status < 2 ? '状态检测' : '结束训练' }}</div>
      <div class="establishRoom2" v-if="roomInfo.admin != userInfo.id">未检测</div>
      <div class="dismiss" title="解散" v-if="roomInfo.admin === userInfo.id" @click="handleRemoveRoom"></div>
      <div class="chart" title="聊天" @click="showMessageBox" :class="[messageBox == false && messageType == true ? 'twinkle' : '']"></div>
      <div class="setup" title="设置" v-if="roomInfo.admin === userInfo.id" @click="handleOpenSetting"></div>
      <div class="signout" title="退出" @click="handleExitRoom"></div>
      <div class="w-full h-full layout-center" v-if="roomInfo.status === 1 && isOpenInspectWindow" style="background: transparent; z-index: 99; position: fixed; top: 0; pointer-events: none">
        <div style="width: 720px; background: whitesmoke; border-radius: 8px; pointer-events: auto">
          <div class="w-full p-1 layout-center" style="color: #0a1429; font-size: 20px; font-weight: bold">状态检测阶段</div>
          <div class="w-full p-1 overflow-auto">
            <template v-if="processedUsers.length === 0">
              <div class="w-full layout-center" style="color: #0a1429; height: 78px">当前无人员进入训练席位</div>
            </template>
            <template v-else>
              <template v-for="user of processedUsers">
                <div class="pr-1 pl-1" style="width: 90px; float: left" v-if="user.type > 0">
                  <div class="w-full layout-center relative">
                    <img :src="fileUrl + user.userImg" style="width: 50px; height: 50px; border-radius: 50px" />
                    <div class="absolute" style="top: -1px; left: 11px">
                      <div class="hz hz-1" style="border-color: #fd9f31" v-if="user.status === 0"></div>
                      <div class="hz hz-2 layout-center" style="border-color: #2fff00; color: #2fff00; font-size: 28px" v-if="user.status === 1"><CheckOutlined /></div>
                      <div class="hz hz-3 layout-center" style="border-color: #f60404; color: #f60404; font-size: 28px" v-if="user.status === 2"><CloseOutlined /></div>
                    </div>
                  </div>
                  <div class="w-full layout-center truncate pt-1" style="color: #0a1429">
                    {{ user.name }}
                  </div>
                </div>
              </template>
            </template>
          </div>
          <div class="w-full p-1 layout-center" style="color: #0a1429">{{ readyUsers.length }}/{{ processedUsers.length }}</div>
          <div class="w-full p-1 layout-center">
            <template v-if="roomInfo.admin === userInfo.id">
              <a-button style="background: blue; border-color: blue; margin-right: 12px" @click="handleStart(2)" v-if="readyUsers.length === processedUsers.length">开始训练</a-button>
              <a-button style="background: red; border-color: red" @click="handleStatusInspect(0)">取消检测</a-button>
            </template>
            <template v-else>
              <a-button style="background: #2fff00; border-color: #2fff00; color: #0a1429; margin-right: 12px" @click="handleInspectReply(1)"> 确认准备 </a-button>
              <a-button style="background: red; border-color: red" @click="handleInspectReply(2)">拒绝准备</a-button>
            </template>
          </div>
        </div>
      </div>
      <a-modal :destroyOnClose="true" :width="560" class="init_modal_style footer-border-none" destroyOnClose="true" v-model:visible="roomModalVisible" @cancel="cancelRoomModal">
        <template #title>
          <strong>更新房间信息</strong>
        </template>
        <template #footer>
          <div class="w-full layout-right-center">
            <div :class="{ createDrillBtn: true }" @click="updateRoom">更新房间</div>
          </div>
        </template>
        <div class="configurationBox layout-left-center" style="padding-bottom: 0">
          <div class="rowItem" style="margin: 10px 0 0">
            <div class="lab">房间名称：</div>
            <div class="item" style="padding-left: 2px">
              <a-input v-model:value="roomInfo.name" />
            </div>
          </div>
          <div class="rowItem" style="margin: 10px 0 0">
            <div class="lab">训练模式：</div>
            <div class="item layout-left-center" style="padding-left: 2px">
              <a-select v-model:value="roomInfo.type">
                <a-select-option :value="0">一发多收</a-select-option>
                <a-select-option :value="1">多发多收</a-select-option>
                <a-select-option :value="2">抄收竞速</a-select-option>
                <a-select-option :value="3">发报竞速</a-select-option>
              </a-select>
            </div>
          </div>
          <div class="rowItem" style="margin: 10px 0 0">
            <div class="lab">房间密码：</div>
            <div class="item" style="padding-left: 2px">
              <a-input v-model:value="roomInfo.password" />
            </div>
          </div>
          <div class="rowItem" style="margin: 10px 0 0">
            <div class="lab">房间人数：</div>
            <div class="item layout-left-center" style="padding-left: 2px">
              <a-input-number v-model:value="roomInfo.nnt" />
            </div>
          </div>
        </div>
      </a-modal>
      <div class="chartModel layout-center" v-if="messageBox">
        <div class="chartBox">
          <div class="closebox layout-center" @click="closeMessageBox"><div class="closeIco"></div></div>
          <div class="messageBox">
            <template v-for="v of chartContent">
              <div class="messageItem layout-left-top" v-if="!v.type">
                <div class="imgBorder" style="margin-left: 0">
                  <img :src="fileUrl + v.userImg" />
                </div>
                <div class="textBox">
                  <div class="userName">{{ v.userName }}</div>
                  <div class="chartItem">{{ v.data }}</div>
                </div>
              </div>
              <div class="messageItem layout-left-top" v-if="v.type == 'own'">
                <div class="textBox2">
                  <div class="userName">{{ v.userName }}</div>
                  <div class="chartItem">{{ v.data }}</div>
                </div>
                <div class="imgBorder" style="margin-left: 0">
                  <img :src="fileUrl + v.userImg" />
                </div>
              </div>
            </template>
          </div>
          <div class="inputBox">
            <a-input style="width: 70%; height: 34px" v-model:value="chartInput" @keydown.enter="sendMessage" placeholder="请输入聊天内容"></a-input>
            <div class="sendMessage" @click="sendMessage">发送</div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'Room'
}
</script>
<script setup>
import UnionWs, { UnionWsCode } from '../js/UnionWs.js'
import { onMounted, onUnmounted, ref, createVNode, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { PubSub } from '../../../../common/utils/PubSub.js'
import { CloseOutlined, CheckOutlined, LogoutOutlined, RestOutlined, SettingOutlined, MessageOutlined, ArrowDownOutlined } from '@ant-design/icons-vue'
import { Modal, message } from 'ant-design-vue'
import Room from './js/Room.js'

let unionWs, room
const route = useRoute()
const router = useRouter()
const userInfo = ref(JSON.parse(window.localStorage.getItem('userInfo')))
const user = ref({})
const isLoading = ref(true)
const roomInfo = ref({})
const fileUrl = ref(window.fileUrl)
const processedUsers = ref([])
const readyUsers = ref([])
const roomModalVisible = ref(false)
const seats = ref({
  0: [
    {
      key: '发送席',
      type: 1,
      num: 1
    },
    {
      key: '接收席',
      type: 2,
      num: 2
    }
  ],
  1: [
    {
      key: '发送席',
      type: 1,
      num: 2
    },
    {
      key: '接收席',
      type: 2,
      num: 2
    }
  ],
  2: [
    {
      key: '接收席',
      type: 2,
      num: 2
    }
  ],
  3: [
    {
      key: '发送席',
      type: 1,
      num: 2
    }
  ]
})
const isOpenInspectWindow = ref(false)
const messageBox = ref(false)
const messageType = ref(false) //收到消息图标闪烁
const chartContent = ref([])
const chartInput = ref('')
const request = window.indexedDB.open(route.query.id, 1)
let db
request.onupgradeneeded = ev => {
  db = ev.target.result
  if (!db.objectStoreNames.contains(route.query.id)) {
    db.createObjectStore(route.query.id, { autoIncrement: true })
  }
}
request.onsuccess = ev => {
  db = request.result
  const message = db.transaction([route.query.id]).objectStore(route.query.id).getAll()
  message.onsuccess = ev => {
    chartContent.value.push(...message.result)
  }
}
onMounted(() => {
  PubSub.subscribe(UnionWsCode.GET_ROOM_INFO, getRoomInfo)
  PubSub.subscribe(UnionWsCode.ROOM_USER_BROADCAST, roomUserBroadcast)
  PubSub.subscribe(UnionWsCode.REMOVE_ROOM_BROADCAST, removeRoomBroadcast)
  PubSub.subscribe(UnionWsCode.ROOM_MESSAGE, roomMessage)
  PubSub.subscribe(UnionWsCode.UPDATE_ROOM_INFO, updateRoomInfo)
  PubSub.subscribe(UnionWsCode.UPDATE_ROOM_USER_BROADCAST, updateRoomUserBroadcast)
  PubSub.subscribe(UnionWsCode.SEAT_INSPECT_ACCEPT, seatInspectAccept)
  PubSub.subscribe(UnionWsCode.SEAT_INSPECT_BROADCAST, seatInspectBroadcast)
  unionWs = UnionWs.getInstance()
  // room = new Room()
  // room.connect(terminalCallback)
  handleIsOpen()
})

onUnmounted(() => {
  unionWs.sendData(UnionWsCode.EXIT_ROOM, roomInfo.value.id)
  // room.close()
  PubSub.unsubscribe(UnionWsCode.GET_ROOM_INFO)
  PubSub.unsubscribe(UnionWsCode.ROOM_USER_BROADCAST)
  PubSub.unsubscribe(UnionWsCode.REMOVE_ROOM_BROADCAST)
  PubSub.unsubscribe(UnionWsCode.ROOM_MESSAGE)
  PubSub.unsubscribe(UnionWsCode.UPDATE_ROOM_INFO)
  PubSub.unsubscribe(UnionWsCode.UPDATE_ROOM_USER_BROADCAST)
  PubSub.unsubscribe(UnionWsCode.SEAT_INSPECT_ACCEPT)
  PubSub.unsubscribe(UnionWsCode.SEAT_INSPECT_BROADCAST)
})
watch(processedUsers, () => {
  readyUsers.value = []
  processedUsers.value.forEach(p => {
    if (p.status === 1) {
      readyUsers.value.push(p)
    }
  })
})
const showMessageBox = () => {
  messageBox.value = true
  messageType.value = false
  messageBoxScor()
}
const closeMessageBox = () => {
  messageBox.value = false
}
const getRoomInfo = data => {
  handleUpdateRoomInfo(data)
  if (roomInfo.value.users.findIndex(item => item.id === userInfo.value.id) <= -1) {
    unionWs.sendData(UnionWsCode.JOIN_ROOM, route.query.id)
  }
}
const roomUserBroadcast = data => {
  const user = JSON.parse(data)
  if (user.type === 'join') {
    message.success(`用户【${user.user.name}】加入房间`)
  } else {
    message.success(`用户【${user.user.name}】退出房间`)
  }
}
const removeRoomBroadcast = data => {
  window.indexedDB.deleteDatabase(route.query.id)
  router.push('/preview/basicSkill/unionJob/unionTrain')
}
const roomMessage = data => {
  chartContent.value.push(JSON.parse(data))
  db.transaction([route.query.id], 'readwrite').objectStore(route.query.id).add(JSON.parse(data))
  if (messageBox.value == false) {
    messageType.value = true
  }
  messageBoxScor()
}

const messageBoxScor = () => {
  nextTick(() => {
    const dom = document.querySelector('.messageBox')
    dom.scrollTop = dom.scrollHeight
  })
}
const updateRoomInfo = data => {
  if (roomInfo.value.id === JSON.parse(data).id) {
    handleUpdateRoomInfo(data)
  }
}
const updateRoomUserBroadcast = data => {
  handleUpdateRoomInfo(data)
  roomInfo.value.users.forEach(user => {
    if (user.id === userInfo.value.id && user.type > 0) {
      isOpenInspectWindow.value = true
    }
  })
}
const seatInspectAccept = data => {}
const seatInspectBroadcast = data => {
  const u = JSON.parse(data)
  roomInfo.value.users.forEach(user => {
    if (user.id === u.userId) {
      user.status = Number(u.status)
    }
  })
  handleProcessedUsers()
}

const handleUpdateRoomInfo = data => {
  roomInfo.value = JSON.parse(data)
  if (roomInfo.value.admin === userInfo.value.id) {
    isOpenInspectWindow.value = true
  }
  sortUsers()
  handleProcessedUsers()
}
const sortUsers = () => {
  processedUsers.value = []
  roomInfo.value.users.forEach(user => {
    if (user.id === userInfo.value.id) {
      if (user.type > 0) {
        isOpenInspectWindow.value = true
      }
    }
    if (user.type > 0) {
      processedUsers.value.push(user)
    }
  })
}
const handleProcessedUsers = () => {
  processedUsers.value = []
  roomInfo.value.users.forEach(user => {
    if (user.type > 0) {
      processedUsers.value.push(user)
    }
    if (user.id === userInfo.value.id) {
      user.value = user
    }
  })
}
const handleIsOpen = () => {
  setTimeout(() => {
    if (!unionWs.isOpen) {
      handleIsOpen()
    } else {
      isLoading.value = false
      setTimeout(() => {
        unionWs.getRoomInfo(route.query.id)
      }, 0)
    }
  }, 100)
}
const terminalCallback = data => {
  if (data.t !== 3) {
    unionWs.sendReceiveData(UnionWsCode.ROOM_MESSAGE, unionWs.userInfo.id, roomInfo.value.id, JSON.stringify(data))
  }
}
const handleExitRoom = () => {
  if (roomInfo.value.admin === userInfo.value.id) {
    Modal.confirm({
      title: () => '您当前为本房间房主，是否确认离开该房间',
      content: () => createVNode('div', { style: 'color:yellow;' }, '离开该房间后,房主身份会自动转交给予下一位用户'),
      onOk() {
        unionWs.sendData(UnionWsCode.EXIT_ROOM, roomInfo.value.id)
        router.push('/preview/basicSkill/unionJob/unionTrain')
      },
      onCancel() {}
    })
  } else {
    unionWs.sendData(UnionWsCode.EXIT_ROOM, roomInfo.value.id)
    router.push('/preview/basicSkill/unionJob/unionTrain')
  }
}
const sendMessage = () => {
  if (chartInput.value == '') {
    return false
  }
  const chartData = {
    userImg: userInfo.value.userImg,
    userName: userInfo.value.userName,
    data: chartInput.value,
    type: 'own'
  }
  chartContent.value.push(chartData)
  unionWs.sendReceiveData(UnionWsCode.ROOM_MESSAGE, unionWs.userInfo.id, roomInfo.value.id, chartInput.value)
  db.transaction([route.query.id], 'readwrite').objectStore(route.query.id).add(chartData)
  chartInput.value = ''
  messageBoxScor()
}
const handleOpenSetting = () => {
  roomModalVisible.value = true
}
const handleRemoveRoom = () => {
  Modal.confirm({
    title: () => '是否确认解散该房间',
    content: () => createVNode('div', { style: 'color:yellow;' }, '解散该房间后,当前在房间中的所有人员会被移入训练大厅'),
    onOk() {
      window.indexedDB.deleteDatabase(route.query.id)
      unionWs.sendData(UnionWsCode.REMOVE_ROOM, roomInfo.value.id)
    },
    onCancel() {}
  })
}
const cancelRoomModal = () => {
  roomModalVisible.value = false
}
const updateRoom = () => {
  unionWs.sendData(UnionWsCode.UPDATE_ROOM, roomInfo.value)
}
const joinGroup = (flag, type) => {
  let userIndex = roomInfo.value.users.findIndex(u => u.id === userInfo.value.id)
  if (flag) {
    roomInfo.value.users[userIndex].type = type
  } else {
    roomInfo.value.users[userIndex].type = 0
    isOpenInspectWindow.value = false
  }

  unionWs.socket.send(
    JSON.stringify({
      code: UnionWsCode.UPDATE_ROOM_USER,
      sendUser: roomInfo.value.id,
      receiveUser: roomInfo.value.users[userIndex].id,
      data: roomInfo.value.users[userIndex].type
    })
  )
}
const handleStatusInspect = type => {
  const user = roomInfo.value.users
  let type_1 = 0
  let type_2 = 0
  user.forEach(u => {
    if (u.type === 1) {
      type_1++
    } else if (u.type === 2) {
      type_2++
    }
  })
  isOpenInspectWindow.value = true
  unionWs.socket.send(
    JSON.stringify({
      code: UnionWsCode.SEAT_INSPECT,
      sendUser: roomInfo.value.id,
      data: type
    })
  )
  if (type === 1 && roomInfo.value.status < 2) {
    handleInspectReply(1)
  }
}
const handleInspectReply = status => {
  unionWs.socket.send(
    JSON.stringify({
      code: UnionWsCode.SEAT_INSPECT_REPLY,
      sendUser: userInfo.value.id,
      receiveUser: roomInfo.value.id,
      data: status
    })
  )
}
const handleStart = status => {
  unionWs.socket.send(
    JSON.stringify({
      code: UnionWsCode.ROOM_STATUS_CHANGE,
      receiveUser: roomInfo.value.id,
      data: status
    })
  )
}
</script>

<style lang="less" scoped>
img {
  height: 100%;
  width: 100%;
  cursor: pointer;
}
.imgBorder {
  width: 46px;
  height: 46px;
  margin-left: 20px;
  padding: 3px;
  background: url('../../../../assets/HJ/union/imgBorder.png') no-repeat;
  background-size: 100% 100%;
}
.homeowner {
  box-shadow: 0px 0px 20px #fca106 inset;
  position: relative;
  border-radius: 2px;
}
.homeowner:after {
  content: '';
  background: url('../../../../assets/HJ/union/homeowner.png');
  height: 25px;
  width: 25px;
  position: absolute;
  top: -10px;
  right: -10px;
}
.centerBox {
  position: absolute;
  top: calc(50% - 280px);
  z-index: 8;
}
.center {
  background: url('../../../../assets/HJ/union/center-bg.png') no-repeat center;
  background-size: 100% 100%;
  width: 80%;
  max-height: 466px;
  height: calc(100vh - 148px - 276px);
  padding: 30px 20px;
  .leftBox {
    height: 100%;
    width: 35%;
    overflow: auto;
    display: flex;
    flex-wrap: wrap;
    padding-top: 10px;
  }
  .centerBtn {
    flex: 1;
    height: 100%;
    display: flex;
    flex-direction: column;
    justify-content: space-between;
    padding: 50px 35px;
    .send {
      background: url('../../../../assets/HJ/union/send.png');
      height: 66px;
      width: 224px;
      text-align: right;
      padding-right: 5%;
      .text {
        background-image: linear-gradient(180deg, #ffffff 60%, rgba(255, 255, 255, 0.6));
        color: transparent;
        display: inline-block;
        -webkit-background-clip: text;
        font-size: 30px;
        font-weight: bold;
        padding-top: 6px;
      }
    }
    .receive {
      background: url('../../../../assets/HJ/union/receive.png');
      height: 66px;
      width: 224px;
      align-self: flex-end;
      padding-left: 5%;
      .text {
        background-image: linear-gradient(180deg, #ffffff 60%, rgba(255, 255, 255, 0.6));
        color: transparent;
        display: inline-block;
        -webkit-background-clip: text;
        font-size: 30px;
        font-weight: bold;
        padding-top: 6px;
      }
    }
  }
  .rightBox {
    height: 100%;
    width: 35%;
    overflow: auto;
    display: flex;
    flex-wrap: wrap;
    padding-top: 10px;
  }
}
.bottom {
  width: 80%;
  height: 55px;
  position: absolute;
  z-index: 8;
  bottom: 155px;
  left: 10vw;
}
.setup {
  right: 21.5vw;
  position: absolute;
  background-image: url('../../../../assets/HJ/union/setup.png');
  height: 35px;
  width: 111px;
  bottom: 42px;
  z-index: 8;
  cursor: pointer;
}
.setup:hover {
  background-image: url('../../../../assets/HJ/union/setup-hover.png');
}
.signout {
  position: absolute;
  background-image: url('../../../../assets/HJ/union/signout.png');
  height: 35px;
  width: 111px;
  bottom: 42px;
  z-index: 8;
  cursor: pointer;
  left: calc(21.5vw - 102px);
}
.signout:hover {
  background-image: url('../../../../assets/HJ/union/signout-hover.png');
}
.chart {
  position: absolute;
  background-image: url('../../../../assets/HJ/union/chart.png');
  height: 35px;
  width: 111px;
  bottom: 42px;
  z-index: 8;
  right: calc(21.5vw - 102px);
  cursor: pointer;
}
.chart:hover {
  background-image: url('../../../../assets/HJ/union/chart-hover.png');
}
@keyframes twinkle {
  0% {
    background-image: url('../../../../assets/HJ/union/chart.png');
  }
  50% {
    opacity: 0.5;
  }
  100% {
    background-image: url('../../../../assets/HJ/union/chart-hover.png');
  }
}
.twinkle {
  animation: twinkle 1s infinite;
}
.dismiss {
  position: absolute;
  background-image: url('../../../../assets/HJ/union/dismiss.png');
  height: 35px;
  width: 111px;
  bottom: 42px;
  left: 21.5vw;
  z-index: 8;
  cursor: pointer;
}
.dismiss:hover {
  background-image: url('../../../../assets/HJ/union/dismiss-hover.png');
}
.addBtn {
  width: 46px;
  height: 46px;
  cursor: pointer;
  background: url('../../../../assets/HJ/union/add.png') no-repeat;
}
.addBtn-y {
  width: 46px;
  height: 46px;
  cursor: pointer;
  background: url('../../../../assets/HJ/union/add-y.png') no-repeat;
}

.chartModel {
  position: absolute;
  bottom: 160px;
  right: 90px;
  z-index: 11;
  .chartBox {
    background: url('../../../../assets/HJ/union/chartBg.png') no-repeat;
    width: 420px;
    height: 590px;
    position: relative;
    padding-top: 40px;
    .messageBox {
      height: calc(100% - 70px);
      overflow: auto;
      padding: 10px 25px 10px 10px;
      .messageItem {
        margin-bottom: 20px;
        height: max-content;
        .textBox {
          width: calc(100% - 60px);
          padding: 0 60px 0px 10px;
          .userName {
            color: #00c0ff;
          }
          .chartItem {
            background: #071633;
            border: 1px solid #2e5d9b;
            border-radius: 3px;
            min-height: 30px;
            max-width: 100%;
            /*line-height: 30px;*/
            padding: 5px 10px;
            position: relative;
            width: max-content;
            overflow-wrap: anywhere;
          }
          .chartItem::before {
            content: '';
            position: absolute;
            background: url('../../../../assets/HJ/union/triangle-l.png');
            width: 8px;
            height: 12px;
            left: -8px;
            top: 10px;
          }
        }
        .textBox2 {
          width: calc(100% - 60px);
          padding: 0 10px 0px 60px;
          .userName {
            color: #00c0ff;
            text-align: right;
          }
          .chartItem {
            float: right;
            background: #071633;
            border: 1px solid #2e5d9b;
            border-radius: 3px;
            min-height: 30px;
            /*line-height: 30px;*/
            max-width: 100%;
            padding: 5px 10px;
            position: relative;
            width: max-content;
            overflow-wrap: anywhere;
          }
          .chartItem::after {
            content: '';
            position: absolute;
            background: url('../../../../assets/HJ/union/triangle-r.png');
            width: 8px;
            height: 12px;
            right: -8px;
            top: 10px;
          }
        }
      }
    }
    .inputBox {
      width: 100%;
      height: 70px;
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 0 20px 0 50px;
      .sendMessage {
        background: url('../../../../assets/HJ/union/sendBtn.png');
        height: 34px;
        width: 90px;
        text-align: center;
        line-height: 34px;
        color: #6c3700;
        font-size: 18px;
        font-weight: bold;
        cursor: pointer;
      }
    }
  }
}
.closebox {
  position: absolute;
  right: 28px;
  top: 0px;
  cursor: pointer;
  height: 40px;
  width: 40px;
}
.closeIco {
  width: 12px;
  height: 12px;
  background: url('../../../../assets/HJ/basicTheory/close.png') no-repeat center;
}
.closebox:hover .closeIco {
  animation: rotate 0.4s linear;
}

.createDrillBtn {
  width: 96px;
  height: 30px;
  color: #e2f2ff;
  font-size: 15px;
  text-align: center;
  line-height: 28px;
  /*background-image: linear-gradient(#6cebfc, #006ea4);*/
  box-shadow: 2px 2px 3px rgba(0, 0, 0, 0.2);
  border-radius: 2px;
  background-image: linear-gradient(#70a3b8, #4c7595);
  cursor: pointer;
}

.createDrillBtn.loadingBtn {
  cursor: no-drop;
  opacity: 0.8;
}

.configurationBox {
  padding-bottom: 20px;
  color: #fff;
}

.configurationBox .rowItem {
  padding: 8px 0;
  display: flex;
  align-items: center;
}

.configurationBox .rowItem.title {
  padding: 16px 0 0;
}

.configurationBox .rowItem .lab {
  width: 100px;
  flex-shrink: 0;
  font-size: 13px;
  color: #7b90af;
  text-align: right;
}

.configurationBox .rowItem .item {
  font-size: 13px;
  color: #7b90af;
  width: 120px;
  text-align: center;
}

.configurationBox .rowItem .item + .item {
  margin-left: 30px;
}

.configurationBox .rowItem .item .absolute {
  left: 0;
  top: 0;
  line-height: 32px;
  z-index: 9;
  padding: 0 8px;
  font-size: 12px;
  color: #7b90af;
  border-right: 1px solid #354971;
}

.configurationBox .rowItem.mini {
  padding-left: 6px;
}

.configurationBox .rowItem.mini .lab {
  width: 86px;
}

.configurationBox .rowItem.mini .item {
  width: 120px;
  margin-left: 12px;
}

.configurationBox .rowItem.mini .item .close {
  display: flex;
  margin-top: -7px;
  color: #d11d1d;
  position: absolute;
  right: -24px;
  top: 50%;
  cursor: pointer;
}

.configurationBox .rowItem .msg {
  height: 24px;
  font-size: 12px;
  color: #f8cf6f;
  display: flex;
  align-items: center;
  padding: 0 8px;
  background-color: #374a63;
}

.hz {
  height: 52px;
  width: 52px;
  border-radius: 54px;
  border: 1px solid;
}

.hz-1 {
  animation: yy1 2s infinite;
}

.hz-2 {
  animation: yy2 2s infinite;
}

.hz-3 {
  animation: yy3 2s infinite;
}

@keyframes yy1 {
  0% {
    box-shadow: 0 0 6px #fd9f31 inset, 0 0 12px #fd9f31;
  }
  50% {
    box-shadow: 0 0 0 #fd9f31 inset, 0 0 0 #fd9f31;
  }
  100% {
    box-shadow: 0 0 6px #fd9f31 inset, 0 0 12px #fd9f31;
  }
}

@keyframes yy2 {
  0% {
    box-shadow: 0 0 6px #2fff00 inset, 0 0 12px #2fff00;
  }
  50% {
    box-shadow: 0 0 0 #2fff00 inset, 0 0 12px #2fff00;
  }
  100% {
    box-shadow: 0 0 6px #2fff00 inset, 0 0 12px #2fff00;
  }
}

@keyframes yy3 {
  0% {
    box-shadow: 0 0 6px #f60404 inset, 0 0 12px #f60404;
  }
  50% {
    box-shadow: 0 0 0 #f60404 inset, 0 0 12px #f60404;
  }
  100% {
    box-shadow: 0 0 6px #f60404 inset, 0 0 12px #f60404;
  }
}

.unionBox {
  max-width: 1906px;
  margin: 0 auto;
  background: url('../../../../assets/HJ/union/bg.png');
  background-size: 100% auto;
  position: relative;
}
.unionBox:after,
.unionBox:before {
  content: '';
  width: 100%;
  position: absolute;
  left: 0;
  z-index: 6;
}
.unionBox:before {
  height: 148px;
  top: 0;
  background: url('../../../../assets/HJ/union/bg-t.png') no-repeat center top;
  background-size: 100% auto;
}
.unionBox:after {
  height: 276px;
  bottom: 0;
  background: url('../../../../assets/HJ/union/bg-b.png') no-repeat center bottom;
  background-size: 100% 100%;
}
.unionTitle {
  width: 100%;
  height: 64px;
  padding: 0 40px;
  display: flex;
  justify-content: center;
  margin-top: -1px;
  position: absolute;
  top: 1.32vw;
  left: 0;
  z-index: 7;
}
.unionTitle .title {
  width: 48vw;
  max-width: 972px;
  background: url('../../../../assets/HJ/union/title-bg.png') no-repeat center;
  background-size: 100% 100%;
  height: calc(48vw * 64 / 972);
  line-height: 2.4vw;
  color: #6c3700;
  font-size: 25px;
  font-weight: bold;
  text-align: center;
}
.state {
  position: absolute;
  bottom: calc(100% - 140px);
  z-index: 8;
  width: 100%;
}
.dataItem {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 40px;
  max-height: 32px;
  min-width: max-content;
  height: calc(18vw * 47 / 340);
  z-index: 8;
  width: 300px;
}
.dataItem.room {
  right: 4.2vw;
  background: url('../../../../assets/HJ/union/state.png') no-repeat center;
  background-size: 100% 100%;
  min-width: max-content;
}
.dataItem .text {
  font-size: 15px;
  color: #6d98d0;
  font-weight: bolder;
}
.dataItem .num {
  font-size: 22px;
  color: #6fc9fe;
  font-weight: bolder;
  font-style: italic;
}
.establishRoom {
  width: 10.57vw;
  height: calc(10.57vw * 64 / 203);
  max-width: 203px;
  background: url('../../../../assets/HJ/union/btn.png') no-repeat center;
  background-size: 100% auto;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: #7d1a10;
  font-size: 24px;
  font-weight: bold;
  position: absolute;
  left: calc(50% - calc(10.57vw / 2));
  bottom: 1vw;
  z-index: 8;
}
.establishRoom:hover {
  color: #c15327;
}
.establishRoom2 {
  width: 234px;
  height: 85px;
  max-width: 234px;
  background: url('../../../../assets/HJ/union/hall.jpg') no-repeat center;
  background-size: 100% 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: #d8a967;
  font-size: 24px;
  font-weight: bold;
  position: absolute;
  left: calc(50% - calc(10.57vw / 2) - 15px);
  bottom: 0.5vw;
  z-index: 8;
}
.roomBox {
  display: flex;
  flex-wrap: wrap;
  padding: 10px 1.6% 0;
  position: absolute;
  top: 14%;
  left: 4.4%;
  right: 4.6%;
  bottom: 26%;
  z-index: 8;
}
.roomItem {
  width: 334px;
  height: 220px;
  background: url('../../../../assets/HJ/union/room-bg.png') no-repeat center;
  margin: 10px calc((100% - 1336px) / 8) 10px;
  cursor: pointer;
  position: relative;
}
.roomItem:nth-of-type(4n) {
  /*margin-right: 0;*/
}
.roomItem .mode {
  color: #0c1d3b;
  font-weight: bold;
  font-size: 18px;
  width: 128px;
  height: 40px;
  line-height: 40px;
  text-align: center;
  position: absolute;
  left: calc(50% - 64px);
  top: 30px;
}
.roomItem .name {
  color: #6d98d0;
  font-weight: bold;
  font-size: 32px;
  height: 60px;
  line-height: 60px;
  text-align: center;
  position: absolute;
  left: 30px;
  right: 30px;
  top: 84px;
}
.roomItem .data {
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  position: absolute;
  left: 20px;
  right: 20px;
  bottom: 16px;
}
.roomItem .data .num {
  color: #b6d6ff;
  font-weight: bold;
  font-size: 18px;
}
.roomItem:hover {
  background: url('../../../../assets/HJ/union/room-bg-1.png') no-repeat center;
}
.roomItem:hover .name {
  color: #fff;
}

.createDrillBtn {
  width: 96px;
  height: 30px;
  color: #e2f2ff;
  font-size: 15px;
  text-align: center;
  line-height: 28px;
  box-shadow: 2px 2px 3px rgba(0, 0, 0, 0.2);
  border-radius: 2px;
  background-image: linear-gradient(#70a3b8, #4c7595);
  cursor: pointer;
}

.createDrillBtn.loadingBtn {
  cursor: no-drop;
  opacity: 0.8;
}

.configurationBox {
  padding-bottom: 20px;
  color: #fff;
}

.configurationBox .rowItem {
  padding: 8px 0;
  display: flex;
  align-items: center;
}

.configurationBox .rowItem.title {
  padding: 16px 0 0;
}

.configurationBox .rowItem .lab {
  width: 100px;
  flex-shrink: 0;
  font-size: 13px;
  color: #7b90af;
  text-align: right;
}

.configurationBox .rowItem .item {
  font-size: 13px;
  color: #7b90af;
  width: 120px;
  text-align: center;
}

.configurationBox .rowItem .item + .item {
  margin-left: 30px;
}

.configurationBox .rowItem .item .absolute {
  left: 0;
  top: 0;
  line-height: 32px;
  z-index: 9;
  padding: 0 8px;
  font-size: 12px;
  color: #7b90af;
  border-right: 1px solid #354971;
}

.configurationBox .rowItem.mini {
  padding-left: 6px;
}

.configurationBox .rowItem.mini .lab {
  width: 86px;
}

.configurationBox .rowItem.mini .item {
  width: 120px;
  margin-left: 12px;
}

.configurationBox .rowItem.mini .item .close {
  display: flex;
  margin-top: -7px;
  color: #d11d1d;
  position: absolute;
  right: -24px;
  top: 50%;
  cursor: pointer;
}

.configurationBox .rowItem .msg {
  height: 24px;
  font-size: 12px;
  color: #f8cf6f;
  display: flex;
  align-items: center;
  padding: 0 8px;
  background-color: #374a63;
}
</style>
