<!-- 客户端全量分页组件：传入完整数组 tableAllData，组件内部切片并经 getTableList 事件抛出当前页。服务端分页见 components/common/Pagination.vue -->
<template>
  <div class="table_pagination">
    <div class="total">共{{ rows.length }}条数据</div>
    <div class="item prev" @click="selectTablePage('-')"></div>
    <template v-for="(item, i) in Math.ceil(rows.length / 10)" :key="i">
      <div
        :class="{ item: true, active: item == currentPage }"
        v-if="item > currentPage - 3 && item < currentPage + 3"
        @click="selectTablePage(item)"
      >
        {{ item }}
      </div>
    </template>
    <div class="item next" @click="selectTablePage('+')"></div>
  </div>
</template>

<script setup>
import {ref, watch} from 'vue'

const props = defineProps(['tableAllData'])
const emit = defineEmits(['getTableList'])

const tableList = ref([]) //表格展示列表
// 本地副本，watch 里从 props.tableAllData 同步。原先这个 ref 也叫 tableAllData，
// 与同名 prop 撞车（setup 绑定会盖住 prop，vue/no-dupe-keys），模板里指哪个要读代码才知道。
const rows = ref([]) //总数据
const currentPage = ref(1) //当前页

const selectTablePage = pag => {
  if (pag === '-' && currentPage.value === 1) return false
  else if (pag === '+' && currentPage.value === Math.ceil(rows.value.length / 10)) return false
  if (pag === '-') {
    currentPage.value--
  } else if (pag === '+') {
    currentPage.value++
  } else {
    currentPage.value = pag
  }
  tableList.value = rows.value.filter((item, i) => i >= (currentPage.value - 1) * 10 && i < currentPage.value * 10)
  emit('getTableList', tableList.value)
}

watch(
  props,
  () => {
    rows.value = props.tableAllData
    selectTablePage(currentPage.value)
  },
  { immediate: true }
)
</script>

<style></style>
