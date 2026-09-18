<template>
  <div class="radioSlider" :style="{'width': width+'px'}">
    <div :class="{bg:true, big: type=='max'}">
      <div class="text">
        <template v-if="type=='max'">
<!--          <div>{{min}}</div>-->
          <div class="on" :style="{left: 'calc(' + ((current-min)/(max-min)*100)+'% - 10px)'}">{{current}}</div>
<!--          <div>{{max}}</div>-->
        </template>
        <template v-else>
          <div v-for="(item,i) in list" :key="i" :class="{on: item.value==current}">{{item.label}}</div>
        </template>
      </div>
    </div>
    <div style="display: flex;justify-content: space-between;align-items: center;">
      <div @click="changeSliderMin" v-if="type!='max'" style="width: 20px;height: 10px;cursor: pointer;flex-shrink: 0;position: relative;"></div>
      <a-slider v-model:value="current" :min="min" :max="max" :step="step" @change="changeSliderVal" style="width: 100%"></a-slider>
      <div @click="changeSliderMax" v-if="type!='max'" style="width: 20px;height: 10px;cursor: pointer;flex-shrink: 0;position: relative;"></div>
    </div>
  </div>
</template>

<script>
  export default {
    name: "Slider"
  }
</script>
<script setup>
  import {ref, defineProps, defineEmits, watch} from "vue"

  const emits = defineEmits(['callback']);
  const props = defineProps({
    type: {
      type: String,
      default: ''
    },
    width: {
      default: 0,
      type: Number
    },
    list: {
      default: () => [],
      type: Array
    },
    val: {
      default: 0,
      type: Number
    },
    min: {
      default: 0,
      type: Number
    },
    max: {
      default: 100,
      type: Number
    },
    step: {
      default: 1,
      type: Number
    }
  });
  // 本地当前值，初值与后续变更都从 props.val 同步过来。
  // 原先这个 ref 也叫 val，与同名 prop 撞车（setup 绑定会盖住 prop，vue/no-dupe-keys），
  // 模板里的 val 到底指哪个要靠读代码才能确定，故改名。
  const current = ref(0);
  watch(props, () => {
    current.value = props.val;
  },{
    immediate:true
  });

  const changeSliderMin = () => {
    current.value = props.min;
    emits('callback', props.min);
  };

  const changeSliderMax = () => {
    current.value = props.max;
    emits('callback', props.max);
  };

  const changeSliderVal = () => {
    emits('callback', current.value)
  };

</script>

<style lang="less" scoped>
  .box {
    width: 16px;
    height: 40px;
  }
  .numT {
    opacity: 0;
    position: absolute;
    top: -20px;
  }
  .numT.show {
    position: relative;
    animation: num-move .5s linear;
    -webkit-animation: num-move .5s linear;
    opacity: 1;
    top: 0;
  }
  @keyframes num-move {
    0% {top: -40px;opacity: 0;}
    40% {top: -6px;opacity: .8}
    100% {top: 0;opacity: 1}
  }

  .item {
    width: 16px;
    display: flex;
    flex-direction: column;
    transition: all .5s linear;
  }
</style>