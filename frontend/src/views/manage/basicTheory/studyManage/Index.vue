<template>
  <div class="w-full h-full overflow-hidden layout-side" style="padding-top: 0;">
    <nip-left-menu v-if="interfaceStyle!=='HJ'"/>
    <div class="h-full transition-all duration-300"
         :style="{width: 'calc(100% - '+(interfaceStyle!=='HJ'?leftMenuWidth:0)+'px)'}"
         style="height: 100%;margin-bottom: 20px"
    >
      <router-view/>
    </div>
  </div>
</template>

<script>
  export default {
    name: "TheoryStudy"
  }
</script>
<script setup>
  import NipLeftMenu from "../../../../components/common/NipLeftMenu.vue";
  import {useRoute, useRouter} from 'vue-router'
  import {provide, ref} from "vue";
  import {global} from "../../../../config/pinia/index.js"
  const interfaceStyle = window.interfaceStyle
  const useGlobalStore = global.useGlobalStore();
  const route = useRoute();
  const router = useRouter();
  const leftMenuWidth = ref(useGlobalStore.leftWidth)
  provide('atRoute', route.matched[4])
  provide('leftMenuWidth', leftMenuWidth);
</script>

<style scoped>

</style>