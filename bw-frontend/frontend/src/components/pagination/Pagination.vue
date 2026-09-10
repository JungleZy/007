<template>
  <div class="table_pagination">
    <div class="total">共{{ tableAllData.length }}条数据</div>
    <div class="item prev" @click="selectTablePage('-')"></div>
    <template v-for="(item, i) in Math.ceil(tableAllData.length / 10)" :key="i">
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
import { onMounted, onUpdated, ref, watch } from 'vue'

const props = defineProps(['tableAllData'])
const emit = defineEmits(['getTableList'])

const tableList = ref([]) //表格展示列表
const tableAllData = ref([]) //总数据
const currentPage = ref(1) //当前页

const selectTablePage = pag => {
  if (pag === '-' && currentPage.value === 1) return false
  else if (pag === '+' && currentPage.value === Math.ceil(tableAllData.value.length / 10)) return false
  if (pag === '-') {
    currentPage.value--
  } else if (pag === '+') {
    currentPage.value++
  } else {
    currentPage.value = pag
  }
  tableList.value = tableAllData.value.filter((item, i) => i >= (currentPage.value - 1) * 10 && i < currentPage.value * 10)
  emit('getTableList', tableList.value)
}

watch(
  props,
  () => {
    tableAllData.value = props.tableAllData
    selectTablePage(currentPage.value)
  },
  { immediate: true }
)
</script>

<style></style>
