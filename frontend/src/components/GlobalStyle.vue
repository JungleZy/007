<template>
  <component :is="activeStyle" ></component>
</template>

<script>
  export default {
    name: "GlobalStyle"
  }
</script>
<script setup>
  import {onMounted,shallowRef,defineAsyncComponent} from 'vue'
  const interfaceStyle = window.interfaceStyle
  const activeStyle = shallowRef(null)
  onMounted(()=>{
    let componentPromise
    if(interfaceStyle=="HJ"){
      componentPromise = ()=>import('./style/HJStyle.vue')
    }else if(interfaceStyle=="HJJ"){
      componentPromise = ()=>import('./style/HJJStyle.vue')
    }else if(interfaceStyle=="LJ"){
      componentPromise = ()=>import('./style/LJStyle.vue')
    }else if(interfaceStyle=="KJ"){
      componentPromise = ()=>import('./style/KJStyle.vue')
    }
    else {
      componentPromise = ()=>import('./style/GDStyle.vue')
    }
    activeStyle.value = defineAsyncComponent(componentPromise)
  })
</script>

<style  lang="less">
  @import '../common/styles/HJ/messsageBody.css';
</style>