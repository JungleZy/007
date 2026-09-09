<template>
  <div class="w-full h-full  overflow-hidden layout-side" style="padding:0px 12px 12px 0px">
    <nip-left-menu/>
    <div class="h-full transition-all duration-300" :style="{width: 'calc(100% - '+(leftMenuWidth+12)+'px)'}">
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
    name: "TelegramPost"
  }
</script>
<script setup>
  import {useRoute, useRouter} from "vue-router";
  import {ref, provide} from "vue";
  import NipLeftMenu from "../../../../components/common/NipLeftMenu.vue";
  import {global} from "../../../../config/pinia/index.js"

  const useGlobalStore = global.useGlobalStore()
  const route = useRoute();
  const router = useRouter();
  const atMenus = ref({});
  const atRoute = ref({});
  const openMenu = ref(true);
  const leftMenuWidth = ref(useGlobalStore.leftWidth);
  provide('atRoute', route.matched[4]);
  provide('leftMenuWidth', leftMenuWidth);

  /** 菜单数据初始化 */
  router.getRoutes().forEach(r => {
    if (r.path === route.matched[4].path) {
      atMenus.value = r;
    }
    if (r.path === route.fullPath) {
      atRoute.value = r;
    }
  });

  /**
   * 菜单选择切换
   * @param sub
   */
  const handleMenuClick = (sub) => {
    atRoute.value = sub;
    router.push(atMenus.value.path + "/" + sub.path);
  }


</script>

<style scoped lang="less">
  :deep{
  .menus .menus_title{
    z-index: 0!important;
  }
  }
</style>