<template>
  <ActionBtn></ActionBtn>
  <component :is="Component"/>
  <div style="position: absolute;left: 50%;bottom: 0px;font-size: 16px;font-weight: bold">V4.0</div>
</template>
<script>
  export default {
    name: 'Login'
  }
</script>
<script setup>
  import {onMounted, shallowRef} from 'vue'
  import LoginHJ from "./components/LoginHJ.vue";
  import LoginHJJ from "./components/LoginHJJ.vue";
  import LoginLJ from "./components/LoginLJ.vue";
  import LoginGD from "./components/LoginGD.vue";
  import LoginKJ from "./components/LoginKJ.vue";
  import ActionBtn from "../../../components/common/ActionBtn.vue";
  const interfaceStyle = window.interfaceStyle
  const Component = shallowRef(null)
  onMounted(()=>{
    // 清楚nipLeftMenu中的缓存
    sessionStorage.removeItem("activefist");
    sessionStorage.removeItem("activeSec");
    if(interfaceStyle=="HJ"){
      Component.value = LoginHJ
    }else if(interfaceStyle=="HJJ"){
      Component.value = LoginHJJ
    }else if(interfaceStyle=="LJ"){
      Component.value = LoginLJ
    }else if(interfaceStyle=="KJ"){
      Component.value = LoginKJ
    }else {
      Component.value = LoginGD
    }
  })
</script>

<style scoped lang="less">

</style>
