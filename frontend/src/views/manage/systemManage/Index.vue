<template>
  <div class="w-full h-full">
    <BreadCrumb v-show="false"/>
    <div class="w-full  pb-2 overflow-hidden layout-side" style="height: calc(100% - 0px)"><!--42-->
      <nip-left-menu/>
      <div class="h-full content-mask-bg transition-all duration-300" :style="{width: 'calc(100% - '+leftMenuWidth+'px)'}">
        <router-view/>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: "SystemManage"
}
</script>
<script setup>
import {useRoute} from 'vue-router'
import {provide, ref} from "vue";
import BreadCrumb from "../../../components/breadCrumb/Index.vue"
import NipLeftMenu from "../../../components/common/NipLeftMenu.vue";
import {global} from "../../../config/pinia";

const interfaceStyle = window.interfaceStyle
const route = useRoute();
const useGlobalStore = global.useGlobalStore();
const leftMenuWidth = ref(useGlobalStore.leftWidth)
provide('atRoute',route.matched[2])
provide('leftMenuWidth', leftMenuWidth);
</script>

<style scoped>

</style>