<template>
  <component :is="activeDashBoard" ></component>
</template>
<script>
  export default {
    name: 'Preview'
  }
</script>
<script setup>
  import {onMounted,shallowRef} from 'vue'
  import PreviewHJ from "./components/PreviewHJ.vue";
  import PreviewHJJ_LJ from "./components/PreviewHJJ_LJ.vue";
  const interfaceStyle = window.interfaceStyle
  const activeDashBoard = shallowRef(null)
  onMounted(()=>{
    if(interfaceStyle==="HJ"){
      activeDashBoard.value = PreviewHJ
    }else{
      activeDashBoard.value = PreviewHJJ_LJ
    }
  })

</script>



