<template>
  <ListenIn tips="接受" v-if="route.query.roomState == 'putAway'"></ListenIn>
  <Issue tips="发出" v-else></Issue>
</template>

<script>
export default {
  name: '1'
}
</script>

<script setup>
import { useRouter, useRoute } from 'vue-router'
import { onMounted } from 'vue'
import ListenIn from './components/ListenIn.vue'
import Issue from './components/Issue.vue'
const router = useRouter()
const route = useRoute()
</script>

<style scoped>

</style>
