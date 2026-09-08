<template>
  <div class="w-full h-full overflow-hidden layout-side" style="padding: 0px 12px 12px 0px">
    <nip-left-menu v-if="isShow" />
    <div class="h-full transition-all duration-300" style="flex: 1">
      <router-view />
    </div>
  </div>
</template>

<script>
export default {
  name: 'TheoryTest'
}
</script>
<script setup>
import NipLeftMenu from '../../../../../components/common/NipLeftMenu.vue'
import { useRoute, useRouter } from 'vue-router'
import { provide, ref, watch, nextTick } from 'vue'

const route = useRoute()
const router = useRouter()
const leftMenuWidth = ref(215)
const isShow = ref(true)
provide('atRoute', route.matched[4])
if (route.path.indexOf('addTest') !== -1 || route.path.indexOf('startTest') !== -1 || route.path.indexOf('startGrade') !== -1 || route.path.indexOf('studentStartTest') !== -1) {
  leftMenuWidth.value = 0
  isShow.value = false
} else {
  isShow.value = true
  leftMenuWidth.value = 215
}
watch(route, () => {
  if (route.path.indexOf('addTest') !== -1 || route.path.indexOf('startTest') !== -1 || route.path.indexOf('startGrade') !== -1 || route.path.indexOf('studentStartTest') !== -1) {
    leftMenuWidth.value = 0
    isShow.value = false
  } else {
    isShow.value = true
    leftMenuWidth.value = 215
  }
})
provide('leftMenuWidth', leftMenuWidth)
</script>
