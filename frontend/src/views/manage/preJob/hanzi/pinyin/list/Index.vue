<template>
  <div class="w-full h-full overflow-hidden layout-side">
    <div class="w-full h-full relative layout-center" style="min-width: 768px;">
      <div v-for="(item,i) in totalList" class="trainItem" @click="verifyPrevTrain(item.type)">
        <div class="title fs_dispose" v-if="interfaceStyle!=='HJ'">{{ item.type == 0 ? '同字异音' : item.type == 1 ? '同音异字' : '连音词组' }}</div>
        <img :src="itemImg9" v-if="item.type==0" class="itemImg">
        <img :src="itemImg10" v-else-if="item.type==1" class="itemImg">
        <img :src="itemImg6" v-else class="itemImg">
        <div class="title fs_dispose" v-if="interfaceStyle==='HJ'">{{ item.type == 0 ? '同字异音' : item.type == 1 ? '同音异字' : '连音词组' }}</div>
        <div class="totalData">
          <div class="item">
            <img :src="itemLab1" class="itemLab">
            <img :src="itemLabOn1" class="itemLab hover">
            <div>
              <div class="lab fs_dispose_1">训练时长</div>
              <div class="val fs_dispose_1">{{ item.totalTime }}</div>
            </div>
          </div>
          <div class="item">
            <img :src="itemLab2" class="itemLab">
            <img :src="itemLabOn2" class="itemLab hover">
            <div>
              <div class="lab fs_dispose_1">练习次数</div>
              <div class="val fs_dispose_1">{{ item.totalCount }} <span class="text">/次</span></div>
            </div>
          </div>
          <div class="item">
            <img :src="itemLab3" class="itemLab">
            <img :src="itemLabOn3" class="itemLab hover">
            <div>
              <div class="lab fs_dispose_1">平均码率</div>
              <div class="val fs_dispose_1" v-if="item.avgSpeed==0">--</div>
              <div class="val fs_dispose_1" v-else>{{ parseFloat(item.avgSpeed).toFixed(2) }} <span
                class="text">{{ '组/分' }}</span></div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'Index'
}
</script>
<script setup>
import { useRouter, useRoute } from 'vue-router'
import { ref, onMounted, createVNode } from 'vue'
import { ExclamationCircleOutlined } from '@ant-design/icons-vue'
import { message, Modal } from 'ant-design-vue'
import moment from 'moment'
import { findPinYinTrainTotal, hanziAdd, findPrevPYTrainTotal } from '../../../../../../common/api/TelegramApi.js'



import iconImage from "../../../js/iconImage";

const {itemLabOn1,itemLabOn2,itemLabOn3,itemLab1,itemLab2,itemLab3,itemImg6,itemImg9,itemImg10} = iconImage()
const interfaceStyle = window.interfaceStyle
const router = useRouter()
const route = useRoute()
const totalList = ref([])
const fs = ref(JSON.parse(localStorage.getItem('fs')));
onMounted(() => {
  setTimeout(() => {
    findPinYinTrainTotal().then(res => {
      let hour, min, sec
      if (res.code === 200) {
        res.data.forEach(item => {
          hour = Math.floor((item.totalTime / 60 / 60) % 24)
          min = Math.floor((item.totalTime / 60) % 60)
          sec = Math.floor(item.totalTime % 60)
          item.totalTime = (hour >= 10 ? hour : '0' + hour) + '：' + (min >= 10 ? min : '0' + min) + '：' + (sec >= 10 ? sec : '0' + sec)
        })
        totalList.value = res.data
      } else {
        message.error(res.message)
      }
    })
  }, 500)
})

/**
 * 验证上一次训练是否结束
 * @param type
 */
const verifyPrevTrain = type => {
  findPrevPYTrainTotal({ type: type }).then(res => {
    if (res.code === 200) {
      if (res.data && res.data.status === 3) {
        Modal.confirm({
          title: () => '上次训练还未结束，是否重新生成训练？',
          icon: () => createVNode(ExclamationCircleOutlined),
          okText: '重新练习',
          cancelText: '继续上次练习',
          maskClosable: true,
          onOk() {
            addTelexTrain(type)
          },
          onCancel() {
            startPrevTrain(res.data.id)
          }
        })
      } else {
        addTelexTrain(type)
      }
    }
  })
}

/**
 * 继续上一次练习
 * @param id
 */
const startPrevTrain = id => {
  router.push({
    path: route.matched[4].path + '/practice',
    query: { id: id }
  })
}

/**
 * 开始新的练习
 * @param type
 */
const addTelexTrain = type => {
  let time = moment().format('YYMMDDhhmmss')
  let name = (type === 0 ? '同字异音-' : type === 1 ? '同音异字-' : '连音词组-') + time
  hanziAdd({ type, name }).then(res => {
    if (res.code === 200) {
      router.push({
        path: route.matched[4].path + '/practice',
        query: { id: res.data.id }
      })
    }
  })
}
</script>
<style scoped>
  .init_modal_style >>> .ant-modal-footer {
    border-top: none !important;
  }
</style>