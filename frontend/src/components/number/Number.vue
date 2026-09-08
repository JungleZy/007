<template>
  <div class="num">
    <template v-for="(val,i) in num">
      <span v-if="val == '.'">.</span>
      <span :data="val" class="box" v-else>
        <span :class="{numT: true, show: val==0, hide: _num[i]==0&&val!=0}">0</span>
        <span :class="{numT: true, show: val==1, hide: _num[i]==1&&val!=1}">1</span>
        <span :class="{numT: true, show: val==2, hide: _num[i]==2&&val!=2}">2</span>
        <span :class="{numT: true, show: val==3, hide: _num[i]==3&&val!=3}">3</span>
        <span :class="{numT: true, show: val==4, hide: _num[i]==4&&val!=4}">4</span>
        <span :class="{numT: true, show: val==5, hide: _num[i]==5&&val!=5}">5</span>
        <span :class="{numT: true, show: val==6, hide: _num[i]==6&&val!=6}">6</span>
        <span :class="{numT: true, show: val==7, hide: _num[i]==7&&val!=7}">7</span>
        <span :class="{numT: true, show: val==8, hide: _num[i]==8&&val!=8}">8</span>
        <span :class="{numT: true, show: val==9, hide: _num[i]==9&&val!=9}">9</span>
      </span>
    </template>
  </div>
</template>

<script>
  export default {
    name: "Number"
  }
</script>
<script setup>
  import {ref, onMounted, watch, defineProps} from "vue";

  const num = ref([0]);
  const _num = ref([0]);
  const props = defineProps({
    value: {
      default: 0,
      type: Number
    }
  });
  watch(props, () => {
    _num.value = num.value;
    num.value = props.value.toString().split('');
  },{
    immediate:true
  });

</script>

<style lang="less" scoped>
  .box {
    width: 16px;
    height: 40px;
    position: relative;
    overflow: hidden;
  }
  .numT {
    position: absolute;
    top: -40px;
    left: 0;
  }
  .numT.show {
    animation: num-move .5s linear;
    -webkit-animation: num-move .5s linear;
    top: 0;
  }
  .numT.hide {
    animation: num-move-hide .5s linear;
    -webkit-animation: num-move-hide .5s linear;
    top: 40px;
  }
  @keyframes num-move {
    0% {top: -40px;}
    50% {top: -20px;}
    100% {top: 0;}
  }
  @keyframes num-move-hide {
    0% {top: 0;}
    50% {top: 20px;}
    100% {top: 40px;}
  }
</style>