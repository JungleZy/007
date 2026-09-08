<template>
  <div class="w-full h-full overflow-hidden layout-side" style="padding:0px 12px 12px 0px">
    <nip-left-menu/>
    <div class="h-full transition-all duration-300" :style="{width: 'calc(100% - '+(leftMenuWidth+12)+'px)'}">
      <router-view v-slot="{Component}">
      </router-view>
    </div>
  </div>
</template>

<script>
  export default {
    name: "Index"
  }
</script>
<script setup>
  import NipLeftMenu from "../../../components/common/NipLeftMenu.vue";
  import {global} from "../../../config/pinia/index.js"
  import {provide, ref} from "vue";
  import {useRoute} from 'vue-router'
  const route = useRoute()
  const useGlobalStore = global.useGlobalStore()
  const leftMenuWidth = ref(useGlobalStore.leftWidth);
  provide('atRoute', route.matched[4]);
</script>

<style scoped>

</style>