<template>
  <div class="h-full w-full overflow-auto dashboard-page relative">
    <div class="w-full h-full absolute overflow-hidden layout-center"
         style="z-index: 2">
      <div class="homeTitle">
        <NipMenus @handleMenuClick="handleMenuClick"/>
      </div>
    </div>
  </div>
</template>
<script>
  export default {
    name: 'HJ'
  }
</script>
<script setup>
  import useNumRain from '../../../../common/utils/useNumRain.js'
  import NipMenus from "../../../../components/dashboard/NipMenus.vue";
  import {useRouter} from 'vue-router'

  const router = useRouter()
  useNumRain('numRain')
  const handleMenuClick = (e) => {
    router.push(`/preview/${e.path}`)
  }
</script>

<style scoped>
  .homeTitle {
    height: 680px;
    width: 100%;
    position: absolute;
    top: 8%;
    left: 0;
    background: url('../../../../assets/HJ/homeTitle.png') no-repeat;
    display: flex;
    align-items: flex-end;
    padding-bottom: 50px;
  }

</style>
