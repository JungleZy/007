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
  const props = defineProps({
    totalNumber: {
      type: Number,
    },
    pageAll: {
      type: Number,
    },
    currentPage: {
      type: Number,
    },
    selectTablePage: {
      type: Function,
      default: () => {
      },
    },
  })
</script>

<style scoped>

</style>