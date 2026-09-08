<template>
  <div class="w-full h-full overflow-hidden layout-side">
    <div class="w-full h-full relative layout-center" style="min-width: 768px;">
      <div v-for="(item,i) in totalList" class="trainItem" @click="verifyPrevTrain(item.type)">
        <div class="title fs_dispose" v-if="interfaceStyle!=='HJ'">
          {{ item.type == 0 ? '口诀训练' : item.type == 1 ? '字根训练' : item.type == 2 ? '拆字训练' : '连音词组' }}
        </div>
        <img :src="itemImg11" v-if="item.type==0" class="itemImg">
        <img :src="itemImg12" v-else-if="item.type==1" class="itemImg">
        <img :src="itemImg13" v-else-if="item.type==2" class="itemImg">
        <img :src="itemImg6" v-else class="itemImg" >
        <div class="title fs_dispose" v-if="interfaceStyle==='HJ'">
          {{ item.type == 0 ? '口诀训练' : item.type == 1 ? '字根训练' : item.type == 2 ? '拆字训练' : '连音词组' }}
        </div>
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
          <div class="item" v-if="item.type == 3">
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
import telegramList from './js/telex'
import { findWuBiTrainTotal, hanziAdd, findPrevPYTrainTotal } from '../../../../../../common/api/TelegramApi.js'

import iconImage from "../../../js/iconImage";
const {itemLabOn1,itemLabOn2,itemLabOn3,itemLab1,itemLab2,itemLab3,itemImg6,itemImg11,itemImg12,itemImg13} = iconImage()
const interfaceStyle = window.interfaceStyle
const router = useRouter()
const route = useRoute()
const totalList = ref([])

onMounted(() => {
  setTimeout(() => {
    findWuBiTrainTotal().then(res => {
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
  if (type === 3) {
    findPrevPYTrainTotal({ type: 9 }).then(res => {
      if (res.code === 200) {
        if (res.data && res.data.status === 3) {
          Modal.confirm({
            title: () => '上次训练还未结束，是否重新生成训练？',
            icon: () => createVNode(ExclamationCircleOutlined),
            okText: '重新练习',
            cancelText: '继续上次练习',
            maskClosable: true,
            onOk() {
              addTelexTrain(9)
            },
            onCancel() {
              startPrevTrain(res.data.id)
            }
          })
        } else {
          addTelexTrain(9)
        }
      }
    })
  } else {
    router.push({
      path: route.matched[4].path + '/wbPractice',
      query: {
        type: type
      }
    })
  }
}

/**
 * 继续上一次练习
 * @param id
 */
const startPrevTrain = id => {
  router.push({
    path: route.matched[4].path + '/wbPracticeTwo',
    query: { id: id, status: 3 }
  })
}

/**
 * 开始新的练习
 * @param type
 */
const addTelexTrain = type => {
  let time = moment().format('YYMMDDhhmmss')
  let name = '连音词组-' + time
  hanziAdd({ type, name }).then(res => {
    if (res.code === 200) {
      router.push({
        path: route.matched[4].path + '/wbPracticeTwo',
        query: { id: res.data.id }
      })
    }
  })
}
</script>
<style scoped>
  .hanziItems {
    width: 175px;
    height: 118px;
    margin: 10px;
    cursor: pointer;
  }

  .KJ {
    background-image: url("../../../../../../assets/HJ/hanzi/KJ.png");
  }

  .KJ:hover {
    background-image: url("../../../../../../assets/HJ/hanzi/KJ-active.png");
  }

  .ZG {
    background-image: url("../../../../../../assets/HJ/hanzi/ZG.png");
  }

  .ZG:hover {
    background-image: url("../../../../../../assets/HJ/hanzi/ZG-active.png");
  }

  .CZ {
    background-image: url("../../../../../../assets/HJ/hanzi/CZ.png");
  }

  .CZ:hover {
    background-image: url("../../../../../../assets/HJ/hanzi/CZ-active.png");
  }

  .LYCZ {
    background-image: url("../../../../../../assets/HJ/hanzi/LYCZ.png");
  }

  .LYCZ:hover {
    background-image: url("../../../../../../assets/HJ/hanzi/LYCZ-active.png");
  }

  .grouping_content {
    display: flex;
    flex-direction: column;
    padding: 0;
  }

  .createDrillBtn {
    width: 96px;
    height: 30px;
    color: #e2f2ff;
    font-size: 15px;
    text-align: center;
    line-height: 28px;
    /*background-image: linear-gradient(#6cebfc, #006ea4);*/
    box-shadow: 2px 2px 3px rgba(0, 0, 0, .2);
    border-radius: 2px;
    background-image: linear-gradient(#22acff, #0068de);

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

  .groupBoxs {
    position: relative;
    border: 1px solid #354971;
    margin-top: 20px;
    padding-bottom: 8px;
  }

  .groupBoxs .groupTitle {
    font-size: 13px;
    color: #bbcdef;
    line-height: 20px;
    padding: 0 10px;
    background-color: #1f3a61;
    position: absolute;
    left: 10px;
    top: -10px;
  }

  .init_modal_style >>> .ant-modal-footer {
    border-top: none !important;
  }
</style>
