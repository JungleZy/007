<template>
  <component :is="activeLeftMenu" ></component>
</template>

<script>
  export default {
    name: "NipLeftMenu"
  }
</script>
<script setup>
  import {onMounted,shallowRef} from 'vue'
  import NipLeftMenuHJ from "./nipLeftMenu/NipLeftMenuHJ.vue";
  import NipLeftMenuHJJ_LJ from "./nipLeftMenu/NipLeftMenuHJJ_LJ.vue";
  const interfaceStyle = window.interfaceStyle
  const activeLeftMenu = shallowRef(null)
  onMounted(()=>{
    if(interfaceStyle=="HJ"){
      activeLeftMenu.value = NipLeftMenuHJ
    }else{
      activeLeftMenu.value = NipLeftMenuHJJ_LJ
    }
  })

</script>


