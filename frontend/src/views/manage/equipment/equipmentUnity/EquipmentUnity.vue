<template>
<div class="h-full w-full">
  <iframe style="width: 100%;height: 100%;z-index: 9999" :src="equipmentUrl" ></iframe>
</div>
</template>

<script>
export default {
  name: "EquipmentUnity"
}
</script>
<script setup>
import {ref,onMounted} from "vue";
import {useRoute} from 'vue-router'
const fileUrl = ref(window.fileUrl)
const route = useRoute()
const equipmentName = ref("")
const equipmentUrl = ref("")
onMounted(()=>{
  console.log(route)
  equipmentName.value = route.query.name
  // equipmentUrl.value = `${fileUrl.value}/equipment/${equipmentName.value}/index.html`
  equipmentUrl.value = `./equipment/${equipmentName.value}/index.html`
  console.log(`${fileUrl.value}/equipment/${equipmentName.value}/index.html`);
})

</script>

<style scoped>

</style>