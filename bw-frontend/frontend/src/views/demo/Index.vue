<template>
  <div class="w-full h-full layout-side">
    <div class="h-full border-r-2 divide-y" style="width: 250px">
      <div
        class="w-full p-2 cursor-pointer"
        v-for="(path, index) in paths"
        @click="goPath(path)"
      >
        {{ path.name }}
      </div>
    </div>
    <div class="h-full layout-center" style="width: calc(100% - 250px)">
      <router-view />
    </div>
  </div>
</template>

<script>
export default {
  name: 'Index'
}
</script>
<script setup>
import { useRouter, useRoute } from 'vue-router'

const route = useRoute()
const router = useRouter()
const paths = [
  {
    name: '即时通讯',
    path: '/demo/im'
  },
  {
    name: '布局',
    path: '/demo/div'
  },
  {
    name: '动画',
    path: '/demo/orc'
  },
  {
    name: '声音生成',
    path: '/demo/vico'
  },
  {
    name: '视频拍照',
    path: '/demo/photo'
  },
  {
    name: '汉字练习',
    path: '/demo/hanzi'
  }
]
const goPath = (path) => {
  router.push(path.path)
}
</script>

<style scoped></style>
