<template>
  <div class="w-full dashboard-page-center overflow-hidden absolute">
    <div class="w-full h-full relative overflow-hidden">
      <div class="w-full h-full layout-side overflow-hidden absolute"
           style="z-index: 2">
        <div :style="{width: 'calc(100% / '+slogans.length+')'}"
             v-for="(s,index) of slogans"
             class="layout-center">
          <img class="animate__animated w-3/4"
               :class="[changeNum===slogans.length+NumSignBase-1?s.out:'',changeNum<slogans.length+NumSignBase+2?s.in:'']"
               :src="s.slogan"
               v-show="changeNum<NumSignBase+3 && changeNum>index"/>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: "NipCenter"
}
</script>
<script setup>
import {onMounted, onUnmounted, ref} from "vue";
import slogan1 from "../../assets/HJ/slogn/slogn-1.png";
import slogan2 from "../../assets/HJ/slogn/slogn-2.png";
import slogan3 from "../../assets/HJ/slogn/slogn-3.png";

const slogans = ref([
  {
    slogan: slogan1,
    in: 'animate__backInLeft',
    out: 'animate__fadeOutUp'
  },
  {
    slogan: slogan2,
    in: 'animate__backInUp',
    out: 'animate__fadeOutUp'
  },
  {
    slogan: slogan3,
    in: 'animate__backInRight',
    out: 'animate__fadeOutUp'
  }
]);
const changeNum = ref(0);
const NumSignBase = ref(8)

onMounted(() => {
  changeSlogan();
})
const changeSlogan = () => {
  setTimeout(() => {
    if (changeNum.value >= slogans.value.length + NumSignBase.value) {
      changeNum.value = 0;
    }
    changeNum.value++;
    changeSlogan();
  }, 1000)
}
</script>
<style>
.dashboard-page-center {
  height: calc(100% - ((450 / 1918) * 100vw) - 250px + (((450 / 1918) * 100vw) * 0.38));
}
</style>