<template>
  <div class="w-full h-full layout-side">
    <div class="w-full h-full relative layout-center" style="min-width: 768px">
      <div v-for="(item,i) in trainData.total" class="trainItem" @click="startExamTrain(item.type)">
        <div class="title fs_dispose" v-if="interfaceStyle!=='HJ'">{{ item.type == 0 ? '基础练习' : item.type == 1 ? '单字练习' : '综合练习' }}</div>
        <img :src="itemImg5" v-if="item.type==0" class="itemImg">
        <img :src="itemImg1" v-else-if="item.type==1" class="itemImg">
        <img :src="itemImg14" v-else class="itemImg">
        <div class="title fs_dispose"  v-if="interfaceStyle==='HJ'">{{ item.type == 0 ? '基础练习' : item.type == 1 ? '单字练习' : '综合练习' }}</div>
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
          <div class="item" v-if="item.type == 2">
            <img :src="itemLab3" class="itemLab">
            <img :src="itemLabOn3" class="itemLab hover">
            <div>
              <div class="lab fs_dispose_1">平均码率</div>
              <div class="val fs_dispose_1" v-if="item.avgSpeed==0">--</div>
              <div class="val fs_dispose_1" v-else>{{ parseFloat(item.avgSpeed).toFixed(2) }} <span
                class="text">码/分</span></div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!--新增训练-->
    <a-modal :destroyOnClose="true" :width="560" class="init_modal_style footer-border-none"
             destroyOnClose="true" v-model:visible="addDrillModal" @cancel="addDrillModal=false">
      <template #title>
        <strong :style="{fontSize: (fs * 2 + 16) + 'px'}">电子键综合拍发练习</strong>
      </template>
      <template #footer>
        <div class="w-full layout-center">
          <div v-if="trainData.prev.id&&trainData.prev.id!=''&&trainData.prev.status<3" @click="startPrevTrain"
               :class="{'createDrillBtn prev': true,'btn-animate': !loading,loadingBtn: loading}" style="width: 120px"
               :style="{fontSize: (fs * 2 + 15) + 'px'}">
            继续上次练习
          </div>
          <div @click="generateMessage" :class="{createDrillBtn: true,'btn-animate': !loading,loadingBtn: loading}"
               style="margin-left: 15px" :style="{fontSize: (fs * 2 + 15) + 'px'}">
            {{ trainData.prev.status == 2 ? '重新生成' : '开始练习' }}
          </div>
        </div>
      </template>
      <a-spin :spinning="loading">
        <div class="configurationBox" style="padding-bottom: 10px">
          <div class="groupBoxs">
            <div class="groupTitle">报文配置</div>
            <div class="rowItem" style="margin: 20px 0 0;">
              <div class="lab">报文类型：</div>
              <div class="item" style="padding-left: 2px;">
                <a-radio-group v-model:value="trainData.bwType">
                  <a-radio :value="0">数码报</a-radio>
                  <a-radio :value="1">字码报</a-radio>
                  <a-radio :value="2">混合报</a-radio>
                </a-radio-group>
              </div>
            </div>
<!--            <div class="rowItem">-->
<!--              <div class="lab">报文组数：</div>-->
<!--              <div class="item layout-left-center" style="width: auto">-->
<!--                <a-input-number v-model:value="trainData.count"-->
<!--                                style="width: 120px;margin-right: 10px"></a-input-number>-->
<!--              </div>-->
<!--            </div>-->
            <!--<div class="rowItem" style="padding-top: 0">
              <div class="lab"></div>
              <div class="msg">
                <WarningOutlined style="margin-right: 8px;font-size: 16px;"/>
                纯数字组成报文的电子键综合练习！
              </div>
            </div>-->
          </div>
        </div>
      </a-spin>
    </a-modal>

  </div>
</template>

<script>
export default {
  name: 'TelexPat'
}
</script>
<script setup>
import { useRouter, useRoute } from 'vue-router'
import { ref, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import moment from 'moment'
import { WarningOutlined } from '@ant-design/icons-vue'
import { totalExamComplexTrainInfo, addExamComplexTrainInfo, findPrevExamTrainInfo } from '../../../../../common/api/examApi.js'

import iconImage from "../../js/iconImage";
const {itemImg5,itemLab1,itemLab2,itemLab3,itemLabOn1,itemLabOn3,itemLabOn2,itemImg1,itemImg14} = iconImage()
import useMorse from '../../../../../common/mixin/useMorse'
const interfaceStyle = window.interfaceStyle
const fs = ref(JSON.parse(localStorage.getItem('fs')));
const router = useRouter()
const route = useRoute()
const {baseCode} = useMorse();
const loading = ref(false)
const addDrillModal = ref(false)
const floorContent = ref([])
const trainData = ref({
  type: 2,
  bwType: 0,
  count: 100,
  prev: {
    status: 0,
    id: ''
  },
  total: []
})

onMounted(() => {
  let hour, min, sec
  totalExamComplexTrainInfo().then(res => {
    if (res.code === 200) {
      res.data.forEach(item => {
        hour = Math.floor((item.totalTime / 1000 / 60 / 60) % 24)
        min = Math.floor((item.totalTime / 1000 / 60) % 60)
        sec = Math.floor((item.totalTime / 1000) % 60)
        item.totalTime = (hour >= 10 ? hour : '0' + hour) + '：' + (min >= 10 ? min : '0' + min) + '：' + (sec >= 10 ? sec : '0' + sec)
      })
      trainData.value.total = res.data
    }
  })
})

/**
 * 获取上一次综合练习数据
 */
const findPrevTrainMsg = () => {
  findPrevExamTrainInfo({ type: trainData.value.type - 1 }).then(res => {
    if (res.code === 200 && res.data && res.data.id && res.data.id != '') {
      trainData.value.prev.status = res.data.status
      trainData.value.prev.id = res.data.id
    } else {
      trainData.value.prev.status = 0
      trainData.value.prev.id = ''
    }
  })
}

/**
 * 继续上一次练习
 */
const startPrevTrain = () => {
  router.push({
    path: route.matched[4].path + '/examComplexTrain',
    query: { id: trainData.value.prev.id }
  })
}

/**
 * 开始进行练习
 */
const startExamTrain = type => {
  if (type === 2) {
    addDrillModal.value = true;
    trainData.value.count = 100;
    findPrevTrainMsg();
  } else {
    router.push({
      path: route.matched[4].path + '/examBasicExam',
      query: { type: type }
    })
  }
}

/**
 * 生成随机报文
 */
const generateMessage = () => {
  let ctAll = []
  for (let i = 0; i < trainData.value.count; i++) {
    let ct = ''
    for (let j = 0; j < 4; j++) {
      if (trainData.value.bwType == 0) {
        ct += Math.floor(Math.random() * 10)
      } else if (trainData.value.bwType == 1) {
        ct+= baseCode['A_Z'][Math.floor(Math.random()*26)]
      } else {
        ct+= baseCode['mix'][Math.floor(Math.random()*36)]
      }
    }
    ctAll.push({ value: '', text: ct, type: true, isFocus: false })
  }
  floorContent.value = ctAll

  addExamComplexTrain()
}

/**
 * 开始进行综合练习
 */
const addExamComplexTrain = () => {
  let title = '综合练习-' + moment().format('YYMMDDhhmmss')
  addExamComplexTrainInfo({
    title: title,
    totalNumber: trainData.value.count,
    content: JSON.stringify(floorContent.value),
    messageType: trainData.value.bwType
  }).then(res => {
    if (res.code === 200) {
      // message.success("生成练习成功！");
      router.push({
        path: route.matched[4].path + '/examComplexTrain',
        query: { id: res.data.id }
      })
    } else {
      message.error('生成练习失败！')
    }
  })
}
</script>

<style scoped>
  .createDrillBtn {
    width: 96px;
    height: 30px;
    color: #e2f2ff;
    font-size: 15px;
    text-align: center;
    line-height: 28px;
    box-shadow: 2px 2px 3px rgba(0, 0, 0, .2);
    border-radius: 2px;
    background-image: linear-gradient(#22acff, #0068de);
  }

  .createDrillBtn.prev {
    background-image: linear-gradient(#e6c165, #bf6c2a);
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
