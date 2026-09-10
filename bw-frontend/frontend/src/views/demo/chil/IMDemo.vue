<template>
  <div class="w-full h-full bg-white">
    <a-button @click="login">连接</a-button>
    <a-button @click="inventoryExport">导出库存</a-button>
    <div class="w-full" style="height: calc(100% - 32px)">
      <div class="w-full layout-side"
           style="color: #0a1429">
        <div class="layout-center" style="width: 15%">物料代码</div>
        <div class="layout-center" style="width: 15%">物料名称</div>
        <div class="layout-center" style="width: 15%">规格型号</div>
        <div class="layout-center" style="width: 10%">仓库代码</div>
        <div class="layout-center" style="width: 10%">仓库名称</div>
        <div class="layout-center" style="width: 9%">计量单位</div>
        <div class="layout-center" style="width: 9%">数量</div>
        <div class="layout-center" style="width: 7%">换算率</div>
        <div class="layout-center" style="width: 10%">综合数量</div>
      </div>
      <div class="w-full overflow-auto" style="height: calc(100% - 21px)">
        <div v-for="(il,index) in inventoryList" :key="'il_'+index" class="w-full layout-side"
             style="color: #0a1429;padding: 6px 0;border-bottom: 1px solid #ccc;">
          <div class="layout-center" style="width: 15%">{{ il.c0 }}</div>
          <div class="layout-center" style="width: 15%">{{ il.c1 }}</div>
          <div class="layout-center" style="width: 15%">{{ il.c2 }}</div>
          <div class="layout-center" style="width: 10%">{{ il.c5 }}</div>
          <div class="layout-center" style="width: 10%">{{ il.c6 }}</div>
          <div class="layout-center" style="width: 9%">{{ il.c7 }}</div>
          <div class="layout-center" style="width: 9%">{{ il.c8 }}</div>
          <div class="layout-center" style="width: 7%">{{ il.c11 }}</div>
          <div class="layout-center" style="width: 10%">{{ il.c12 }}</div>
          <div class="layout-center" style="width: 100%">
            <a-button @click="warehouseIM(il)">入库</a-button>
            <a-button @click="warehouseEX(il)">出库</a-button>
          </div>
        </div>
      </div>
    </div>
    <a-modal v-model:visible="imVisible" title="入库" @ok="handleIMOk">
      <div style="color: #fff">
        <div class="py-1 layout-left-center">单据编号
          <a-input v-model:value="imData.djbh"></a-input>
        </div>
        <div class="py-1 layout-left-center">单据日期
          <a-input v-model:value="imData.djrq"></a-input>
        </div>
        <div class="py-1 layout-left-center">供应商
          <a-select class="w-full" v-model:value="imData.gys" :options="gysList"></a-select>
        </div>
        <div class="py-1 layout-left-center">采购方式
          <a-select class="w-full" v-model:value="imData.cgfs" :options="cgfsList"></a-select>
        </div>
        <div class="py-1 layout-left-center">物料代码
          <a-input v-model:value="imData.wldm"></a-input>
        </div>
        <div class="py-1 layout-left-center">单位
          <a-input v-model:value="imData.dw"></a-input>
        </div>
        <div class="py-1 layout-left-center">单价
          <a-input v-model:value="imData.dj"></a-input>
        </div>
        <div class="py-1 layout-left-center">收料仓库
          <a-input v-model:value="imData.slck"></a-input>
        </div>
        <div class="py-1 layout-left-center">实收数量
          <a-input v-model:value="imData.sl"></a-input>
        </div>
      </div>
    </a-modal>
    <a-modal v-model:visible="exVisible" title="出库" @ok="handleEXOk">

    </a-modal>
  </div>
</template>

<script>
export default {
  name: 'IMDemo'
}
</script>
<script setup>
import {ref} from 'vue'
import * as event from './event.js'
import {message} from 'ant-design-vue'

const inventoryList = ref([])
const selectedUser = ref({})
const imVisible = ref(false)
const exVisible = ref(false)
const cgfsList = ref([
  {
    value: '现购',
    label: '现购',
  }, {
    value: '赊购',
    label: '赊购',
  }
])
const gysList = ref([
  {
    value: '重庆木易包装制品有限公司',
    label: '重庆木易包装制品有限公司',
  }, {
    value: '重庆卓联仪器仪表有限公司',
    label: '重庆卓联仪器仪表有限公司',
  }, {
    value: '重庆讯嵩科技有限公司',
    label: '重庆讯嵩科技有限公司',
  }, {
    value: '重庆峰蜡科技有限公司',
    label: '重庆峰蜡科技有限公司',
  }, {
    value: '重庆安峰包装制品有限公司',
    label: '重庆安峰包装制品有限公司',
  }, {
    value: '重庆华社进出口有限公司',
    label: '重庆华社进出口有限公司',
  }, {
    value: '重庆格锐欧科技有限责任公司',
    label: '重庆格锐欧科技有限责任公司',
  }, {
    value: '重庆劲凯机电设备有限公司',
    label: '重庆劲凯机电设备有限公司',
  }, {
    value: '重庆市旺特机械制造有限公司',
    label: '重庆市旺特机械制造有限公司',
  }, {
    value: '重庆果实电子科技有限公司',
    label: '重庆果实电子科技有限公司',
  }
])
const imData = ref({
  djbh: 'WIN007792',
  djrq: '2024-4-22',
  gys: '北京龙科兴业电子科技有限公司',
  cgfs: '赊购',
  wldm: '01.05.BGYP.007',
  dw: '个',
  dj: '1',
  slck: '办公用品',
  sl: 1
})

let socket
const login = () => {
  socket = new WebSocket(`ws://10.10.0.210:18766/erp-ws`)
  socket.onopen = e => {
  }
  socket.onerror = e => {
  }
  socket.onclose = e => {
  }
  socket.onmessage = e => {
    console.log(e)
    const data = JSON.parse(e.data)
    console.log(data)
    switch (data.code) {
      case "inventoryExport":
        inventoryList.value = []
        inventoryList.value = data.data
    }
  }
}
const inventoryExport = data => {
  socket.send(
      JSON.stringify({
        cmd: 'inventoryExport',
      })
  )
}
const warehouseIM = (e) => {
  imData.value = {
    djbh: `WJ${dayjs().format('YYYYMMDD')}001`,
    djrq: dayjs().format('YYYY-MM-DD'),
    gys: gysList.value[0].value,
    cgfs: '赊购',
    wldm: e.c0,
    dw: e.c7,
    dj: '0',
    slck: e.c6,
    sl: 1
    // djbh: `WJ${dayjs().format('YYYYMMDD')}001`,
    // djrq:  dayjs().format('YYYY-MM-DD'),
    // gys: '北京龙科兴业电子科技有限公司',
    // cgfs: '赊购',
    // wldm: '01.05.BGYP.007',
    // dw: '个',
    // dj: '1',
    // slck: '办公用品',
    // sl: 1
  }
  imVisible.value = true
}
const warehouseEX = (e) => {
  exVisible.value = true
}
const handleIMOk = () => {
  socket.send(
      JSON.stringify({
        cmd: 'stockIn',
        data: {
          ...imData.value,

        }
      })
  )
  imVisible.value = false
}
const handleEXOk = () => {
  exVisible.value = false
}
</script>
<style scoped>
.selected-user {
  background: rgba(236, 236, 236, 0.7) !important;
  color: #001529;
}
</style>
