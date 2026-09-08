<template>
  <div class="w-full h-full overflow-hidden layout-side" style="padding: 0px 12px 12px 0px">
        <nip-left-menu v-show="isShow"/>
    <!--    :style="{width: 'calc(100% - '+(leftMenuWidth+12)+'px)'}" margin-left: 10px-->
    <div class="h-full transition-all duration-300 w-full" style="height: 100%" :style="{width: 'calc(100% - '+(leftMenuWidth+12)+'px)'}">
      <router-view :key="route.query.studyType" />
    </div>
  </div>
</template>

<script>
export default {
  name: 'TheoryStudy'
}
</script>
<script setup>
import NipLeftMenu from '../../../../components/common/NipLeftMenu.vue'
import { useRoute, useRouter } from 'vue-router'
import { provide, ref } from 'vue'
import {global} from "../../../../config/pinia/index.js"

const useGlobalStore = global.useGlobalStore();
let isShow = true
const route = useRoute()
const router = useRouter()
const leftMenuWidth = ref(useGlobalStore.leftWidth)
const interfaceStyle = window.interfaceStyle
provide('atRoute', route.matched[4])
provide('leftMenuWidth', leftMenuWidth)
</script>

<style scoped></style>
