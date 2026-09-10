<template>
  <div class="w-full h-full">
    <div class="w-full h-full relative layout-center">
      <div v-for="(item, i) in totalList" :key="i" class="trainItem" @click="addDrillModalInfo(item.type)">
        <img :src="itemImg5" v-if="item.type == 0" class="itemImg" />
        <img :src="itemImg7" v-else-if="item.type == 1" class="itemImg" />
        <img :src="itemImg1" v-else class="itemImg" />
        <div class="title">{{ item.type == 0 ? '勤务用语' : item.type == 1 ? '军语密语' : '单字练习' }}</div>
        <div class="totalData">
          <div class="item">
            <img :src="itemLab1" class="itemLab" />
            <img :src="itemLabOn1" class="itemLab hover" />
            <div>
              <div class="lab">训练时长</div>
              <div class="val">{{ item.totalTime }}</div>
            </div>
          </div>
          <div class="item">
            <img :src="itemLab2" class="itemLab" />
            <img :src="itemLabOn2" class="itemLab hover" />
            <div>
              <div class="lab">练习次数</div>
              <div class="val">{{ item.totalCount }} <span class="text">/次</span></div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'DittoTerm'
}
</script>
<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getPreTermTrainTotal } from '../../../../../common/api/TelegramApi.js'
import itemImg5 from '../../../../../assets/HJ/preTrain/item-img-5.png'
import itemImg7 from '../../../../../assets/HJ/preTrain/item-img-7.png'
import itemImg1 from '../../../../../assets/HJ/preTrain/item-img-1.png'
import itemLab1 from '../../../../../assets/HJ/preTrain/total-data-1.png'
import itemLab2 from '../../../../../assets/HJ/preTrain/total-data-2.png'
import itemLab3 from '../../../../../assets/HJ/preTrain/total-data-3.png'
import itemLab4 from '../../../../../assets/HJ/preTrain/total-data-4.png'
import itemLabOn1 from '../../../../../assets/HJ/preTrain/total-data-on-1.png'
import itemLabOn2 from '../../../../../assets/HJ/preTrain/total-data-on-2.png'
import itemLabOn3 from '../../../../../assets/HJ/preTrain/total-data-on-3.png'
import itemLabOn4 from '../../../../../assets/HJ/preTrain/total-data-on-4.png'

const router = useRouter()
const totalList = ref([])
const drillPath = ref('')
const armyPath = ref('')
router.getRoutes().forEach(r => {
  if (r.name === 'Wording') {
    drillPath.value = r.path
  }
  if (r.name === 'MilitaryTerm') {
    armyPath.value = r.path
  }
})

onMounted(() => {
  getPreTermTrainTotal().then(res => {
    let hour, min, sec
    if (res.code === 200) {
      res.data.forEach(item => {
        hour = Math.floor((item.totalTime / 1000 / 60 / 60) % 24)
        min = Math.floor((item.totalTime / 1000 / 60) % 60)
        sec = Math.floor((item.totalTime / 1000) % 60)
        item.totalTime = (hour >= 10 ? hour : '0' + hour) + '：' + (min >= 10 ? min : '0' + min) + '：' + (sec >= 10 ? sec : '0' + sec)
      })
      totalList.value = res.data
    }
  })
})

/**
 * 路由页面跳转
 * @param type
 */
const addDrillModalInfo = type => {
  if (type === 0) {
    router.push({ path: drillPath.value })
  }
  if (type === 1) {
    router.push({ path: armyPath.value })
  }
}
</script>
<style scoped></style>
