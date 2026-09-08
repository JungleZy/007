<template>
  <component :is="activeDashBoard" ></component>
  <div style="position: absolute;left: 50%;bottom: 0px;font-size: 16px;font-weight: bold">V4.0</div>
</template>
<script>
  export default {
    name: 'Dashboard'
  }
</script>
<script setup>
  import {onMounted,shallowRef} from 'vue'
  import HJJ_LJ from "./components/HJJ_LJ.vue";
  import HJ from "./components/HJ.vue";
  import {findReceivePointsByType} from "../../../common/api/ExplainApi";
  const interfaceStyle = window.interfaceStyle
  const activeDashBoard = shallowRef(null)
  //检测账号异地登陆
  findReceivePointsByType({type:0})
  onMounted(()=>{
    if(interfaceStyle=="HJ"){
      activeDashBoard.value = HJ
    }else{
      activeDashBoard.value = HJJ_LJ
    }
  })
</script>

