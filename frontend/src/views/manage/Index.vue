<template>
  <div class="w-full h-full overflow-hidden">
    <router-view/>
  </div>
</template>
<script setup>
import {useRouter} from "vue-router";

const router = useRouter();
const token = window.localStorage.getItem("token");
if (token === null) {
  router.replace("/login").then();
} else {
  router.replace("/preview").then();
}
</script>
<style>

</style>
