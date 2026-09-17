<!-- 服务端分页组件：父组件持有数据与页码，翻页经 selectTablePage 事件通知父组件拉取。客户端全量分页见 components/pagination/Pagination.vue -->
<template>
  <div class="table_pagination">
    <div class="total">共{{ totalNumber }}条数据</div>
    <div class="item prev" @click="selectTablePage(currentPage-1)"></div>
    <template v-for="(item, i) in Math.ceil(pageAll)" :key="i">
      <div :class="{ item: true, active: item == currentPage }"
           v-if="(currentPage===1?i<5:
           currentPage===pageAll?i>pageAll-5:
           i>(currentPage-3>pageAll-5?pageAll-5:currentPage-3)&&i<(currentPage+3>pageAll?pageAll:currentPage+3))" @click="selectTablePage(item)">
        {{ item }}
      </div>
    </template>
    <div class="item next" @click="selectTablePage(currentPage+1)"></div>
  </div>
</template>

<script>
  export default {
    name: "Pagination"
  }
</script>
<script setup>
  defineProps({
    totalNumber: {
      type: Number,
    },
    pageAll: {
      type: Number,
    },
    currentPage: {
      type: Number,
    },
  })
  const emit = defineEmits(['selectTablePage'])
  const selectTablePage = (pag) => emit('selectTablePage', pag)
</script>

<style scoped>

</style>