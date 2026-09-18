<template>
  <div class="w-full h-full layout-center" style="flex-direction: column; color: #7b90af">
    <div style="font-size: 72px; font-weight: bold; line-height: 1">404</div>
    <div style="font-size: 16px; margin: 12px 0 24px">页面不存在或当前账号无权访问</div>
    <a-button @click="goBack">返回上一页</a-button>
  </div>
</template>

<script setup>
  import { useRouter } from 'vue-router'

  const router = useRouter()
  // 路由表把所有未匹配路径重定向到 /404（config/router/index.js:31,35），
  // 而本页原先是空 <template>，用户走到任何未知路径只会看到一片空白。
  const goBack = () => router.back()
</script>

<style scoped></style>
