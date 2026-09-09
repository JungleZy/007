<template>
  <div class="w-full h-full overflow-hidden" style="padding: 0 12px 20px;">
    <div class="loading" v-if="isLoading"><a-spin style="margin-right: 8px;"></a-spin> 正在连接训练大厅 ...</div>
    <div class="w-full h-full unionBox" v-else>
      <div class="unionTitle"><div class="title">联合训练大厅</div></div>
      <div class="dataItem user">
        <div class="text">当前在线人数</div>
        <div class="num">{{ onlineUsers.length }}</div>
      </div>
      <div class="dataItem room">
        <div class="num">{{ onlineRooms.length }}</div>
        <div class="text">联合训练房间数</div>
      </div>
      <div class="roomBox overflow-auto">
        <div class="roomItem" v-for="r in onlineRooms" @click="handleJoinRoom(r)">
          <div class="mode">{{ r.type === 0 ? '一发多收' : r.type === 1 ? '多发多收' : r.type === 2 ? '抄收竞速' : '发报竞速' }}</div>
          <div class="name nobr" :title="r.name">{{ r.name }}</div>
          <div class="data">
            <div class="num">{{ r.users.length }}/{{ r.nnt }}</div>
            <img :src="locking" v-if="r.password !== ''">
          </div>
        </div>
      </div>
      <div class="establishRoom" @click="handleAddRoom">开设房间</div>
    </div>

    <a-modal :destroyOnClose="true"
             :width="560"
             class="init_modal_style footer-border-none"
             destroyOnClose="true"
             v-model:visible="addRoomModalVisible"
             @cancel="cancelRoomModal">
      <template #title>
        <strong>房间配置</strong>
      </template>
      <template #footer>
        <div class="w-full layout-center">
          <div :class="{createDrillBtn: true}" @click="createRoom">
            创建房间
          </div>
        </div>
      </template>
      <div class="configurationBox layout-left-center" style="padding-bottom: 0">
        <div class="rowItem" style="margin: 10px 0 0;">
          <div class="lab">房间名称：</div>
          <div class="item" style="padding-left: 2px;">
            <a-input v-model:value="roomInfo.name" placeholder="房间名称"/>
          </div>
        </div>
        <div class="rowItem" style="margin: 10px 0 0;">
          <div class="lab">训练模式：</div>
          <div class="item layout-left-center" style="padding-left: 2px;">
            <a-select v-model:value="roomInfo.type">
              <a-select-option :value="0">一发多收</a-select-option>
              <a-select-option :value="1">多发多收</a-select-option>
              <a-select-option :value="2">抄收竞速</a-select-option>
              <a-select-option :value="3">发报竞速</a-select-option>
            </a-select>
          </div>
        </div>
        <div class="rowItem" style="margin: 10px 0 0;">
          <div class="lab">房间密码：</div>
          <div class="item" style="padding-left: 2px;">
            <a-input v-model:value="roomInfo.password" placeholder="设置密码"/>
          </div>
        </div>
        <div class="rowItem" style="margin: 10px 0 0;">
          <div class="lab">房间人数：</div>
          <div class="item layout-left-center" style="padding-left: 2px;">
            <a-input-number v-model:value="roomInfo.nnt" :min="2"/>
          </div>
        </div>
      </div>
    </a-modal>
    <a-modal :destroyOnClose="true"
             :width="360"
             v-model:visible="passwordModalVisible"
             @cancel="cancelRoomPasswordModal">
      <template #title>
        <strong>输入房间密码</strong>
      </template>
      <template #footer>
        <div class="w-full layout-center">
          <div :class="{createDrillBtn: true}" @click="verifyPassword">
            确认密码
          </div>
        </div>
      </template>
      <div class="configurationBox layout-left-center" style="padding: 10px 12px !important;">
        <div class="layout-left-center" v-if="checkRoomInfo" style="margin-bottom: 10px;color: #bbcdef;font-size: 12px">
          <div style="color: #7b90af">房间名：</div>{{checkRoomInfo.name}}
        </div>
        <a-input v-model:value="roomPassword" placeholder="房间密码"/>
      </div>
    </a-modal>
  </div>
</template>

<script>
export default {
  name: "UnionTrain"
}
</script>
<script setup>
import {onMounted, onUnmounted, ref} from "vue";
import {CloseOutlined, LogoutOutlined, LockOutlined, UnlockOutlined} from '@ant-design/icons-vue';
import {PubSub} from "../../../../common/utils/PubSub.js";
import UnionWs, {UnionWsCode} from "../js/UnionWs.js";
import {useRouter} from "vue-router";
import {notification} from 'ant-design-vue'
import locking from "../../../../assets/HJ/union/locking.png";

let unionWs = null
const router = useRouter();
const isLoading = ref(true)
const userInfo = ref(JSON.parse(window.localStorage.getItem('userInfo')))
const onlineUsers = ref([])
const onlineRooms = ref([])
const addRoomModalVisible = ref(false)
const passwordModalVisible = ref(false)
const checkRoomInfo = ref(null)
const roomInfo = ref({
  name: "联合训练房间",
  type: 0,
  password: '',
  nnt: 8
})

const roomPassword = ref('')
const fileUrl = ref(window.fileUrl)
onMounted(() => {
  PubSub.subscribe(UnionWsCode.USER_LIST, (data) => {
    onlineUsers.value = JSON.parse(data)
  })
  PubSub.subscribe(UnionWsCode.USER_JOIN, (data) => {
    onlineUsers.value.push(JSON.parse(data))
  })
  PubSub.subscribe(UnionWsCode.USER_EXIT, (data) => {
    onlineUsers.value.splice(onlineUsers.value.findIndex(item => item.id === JSON.parse(data).id), 1)
  })
  PubSub.subscribe(UnionWsCode.ROOM_LIST, (data) => {
    onlineRooms.value = JSON.parse(data)
    //清楚多余的数据
    window.indexedDB.databases().then(res=>{
      for (let v of res){
        const type = onlineRooms.value.some(item=>item.id==v.name)
        if(!type){
          window.indexedDB.deleteDatabase(v.name)
        }
      }
    })
  })
  PubSub.subscribe(UnionWsCode.ADD_ROOM_SUCCESS, (data) => {
    router.push({path: '/preview/basicSkill/unionJob/unionTrainRoom', query: {id: JSON.parse(data).id}})
  })
  PubSub.subscribe(UnionWsCode.UPDATE_ROOM_INFO, (data) => {
    let index = onlineRooms.value.findIndex(item => item.id === JSON.parse(data).id)
    if (index === -1) {
      onlineRooms.value.push(JSON.parse(data))
    } else {
      onlineRooms.value[index] = JSON.parse(data)
    }
  })
  PubSub.subscribe(UnionWsCode.JOIN_ROOM_SUCCESS, (data) => {
    router.push({path: '/preview/basicSkill/unionJob/unionTrainRoom', query: {id: JSON.parse(data).id}})
  })
  unionWs = UnionWs.getInstance()
  handleIsOpen()
})
const handleIsOpen = () => {
  setTimeout(() => {
    if (!unionWs.isOpen) {
      handleIsOpen()
    } else {
      isLoading.value = false
      setTimeout(() => {
        unionWs.getUnionInfo()
      }, 0)
    }
  }, 100)
}

onUnmounted(() => {
  PubSub.unsubscribe(UnionWsCode.USER_LIST)
  PubSub.unsubscribe(UnionWsCode.ROOM_LIST)
  PubSub.unsubscribe(UnionWsCode.USER_EXIT)
  PubSub.unsubscribe(UnionWsCode.ROOM_LIST)
  PubSub.unsubscribe(UnionWsCode.ADD_ROOM_SUCCESS)
  PubSub.unsubscribe(UnionWsCode.UPDATE_ROOM_INFO)
  PubSub.unsubscribe(UnionWsCode.JOIN_ROOM_SUCCESS)
})

const handleAddRoom = () => {
  addRoomModalVisible.value = true
}
const cancelRoomModal = () => {
  addRoomModalVisible.value = false
}
const createRoom = () => {
  unionWs.sendData(UnionWsCode.ADD_ROOM, roomInfo.value)
}
const handleJoinRoom = (room) => {
  if (room.password !== '') {
    roomPassword.value = ''
    passwordModalVisible.value = true
    checkRoomInfo.value = room
  } else {
    unionWs.sendData(UnionWsCode.JOIN_ROOM, room.id)
  }
}
const cancelRoomPasswordModal = () => {
  roomPassword.value = ''
  passwordModalVisible.value = false
  checkRoomInfo.value = null
}
const verifyPassword = () => {
  if (roomPassword.value === checkRoomInfo.value.password) {
    notification['success']({
      message: "提示",
      description: '房间密码正确，进入房间'
    })
    unionWs.sendData(UnionWsCode.JOIN_ROOM, checkRoomInfo.value.id)
  } else {
    notification['error']({
      message: "提示",
      description: '您输入的房间密码错误，请重试'
    })
  }
}
</script>

<style scoped>
  .unionBox {
    max-width: 1906px;
    margin: 0 auto;
    background: url("../../../../assets/HJ/union/bg.png");
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
    background: url("../../../../assets/HJ/union/bg-t.png") no-repeat center top;
    background-size: 100% auto;
  }
  .unionBox:after {
    height: 276px;
    bottom: 0;
    background: url("../../../../assets/HJ/union/bg-b.png") no-repeat center bottom;
    background-size: 100% auto;
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
    background: url("../../../../assets/HJ/union/title-bg.png") no-repeat center;
    background-size: 100% 100%;
    height: calc(48vw * 64 / 972);
    line-height: 2.4vw;
    color: #6c3700;
    font-size: 25px;
    font-weight: bold;
    text-align: center;
  }
  .dataItem {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 0 40px;
    max-width: 340px;
    max-height: 47px;
    width: 18vw;
    height: calc(18vw * 47 / 340);
    position: absolute;
    top: 2.6vw;
    z-index: 8;
  }
  .dataItem.user {
    left: 4vw;
    background: url("../../../../assets/HJ/union/data-bg-1.png") no-repeat center;
    background-size: 100% auto;
  }
  .dataItem.room {
    right: 4.2vw;
    background: url("../../../../assets/HJ/union/data-bg-2.png") no-repeat center;
    background-size: 100% auto;
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
    background: url("../../../../assets/HJ/union/btn.png") no-repeat center;
    background-size: 100% auto;
    display: flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;
    color: #7d1a10;
    font-size: 24px;
    font-weight: bold;
    position: absolute;
    left: calc(50% - 102px);
    bottom: 1vw;
    z-index: 8;
  }
  .establishRoom:hover {
    color: #c15327;
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
    background: url("../../../../assets/HJ/union/room-bg.png") no-repeat center;
    margin: 10px  calc((100% - 1336px) / 8) 10px;
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
    background: url("../../../../assets/HJ/union/room-bg-1.png") no-repeat center;
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
    box-shadow: 2px 2px 3px rgba(0, 0, 0, .2);
    border-radius: 2px;
    background-image: linear-gradient(#70a3b8, #4c7595);
    cursor: pointer;
  }

  .createDrillBtn.loadingBtn {
    cursor: no-drop;
    opacity: .8;
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