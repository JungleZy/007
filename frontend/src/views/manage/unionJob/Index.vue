<template>
  <div class="w-full h-full  overflow-hidden layout-side" style="padding:0px 12px 12px 0px">
    <nip-left-menu/>
    <div class="h-full transition-all duration-300" :style="{width: 'calc(100% - '+(leftMenuWidth+10)+'px)'}" >
      <router-view v-slot="{Component}">
<!--        <transition name="scale-slide">-->
          <component :is="Component"/>
<!--        </transition>-->
      </router-view>
    </div>
  </div>
</template>

<script>
export default {
  name: "UnionJob"
}
</script>
<script setup>
  import {useRoute, useRouter} from "vue-router";
  import {provide, ref} from "vue";
  import {global} from "../../../config/pinia/index.js"

  const useGlobalStore = global.useGlobalStore()
  const leftMenuWidth = ref(useGlobalStore.leftWidth);
  const route = useRoute();
  const router = useRouter();
  const atMenus = ref({});
  const atRoute = ref({});
  const openMenu = ref(true);
  provide('atRoute', route.matched[route.matched.length-1]);
  provide('leftMenuWidth', leftMenuWidth);
</script>
