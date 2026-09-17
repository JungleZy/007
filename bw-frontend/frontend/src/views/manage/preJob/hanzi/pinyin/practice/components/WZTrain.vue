<template>
  <div class="h-full w-full layout-left-top wz" >
    <div style="font-size: 16px;border-bottom: 1px solid #555252;padding: 20px 0;" v-for=" (v,index) of message">
      <div style="text-align: center;">{{v.font}}</div>
      <div style="font-size: 24px;padding:0px 3px;text-align: center" class="layout-left-top">
                  <div class="relative layout-left-top"   v-for="(zm,i) of v.pys" :style="[zm.trueOrfalse==false?'color:red':zm.trueOrfalse==true?'color:rgb(178 253 177)':'']" >
                  <div style="font-size: 24px;padding-left: 2px;border-left: 1px solid transparent"  :class="[activeIndex==index&&inputIndex==i&&trainData.status==1?'active':'']" >
                    {{zm.py}}</div>
                  </div>
      </div>
    </div>
  </div>
</template>

<script setup>
defineProps({ message: Array, trainData: Object, activeIndex: Number, inputIndex: Number })
</script>

<style lang="less" scoped>
  :deep{
    .ant-input{
      padding: 0px!important;
    }
    .ant-input-disabled{
      border-bottom: 1px solid rgba(198, 187, 187, 0.5) !important;
      color: #b8a5a5!important;
    }
    .wz [type='text']:focus{
      background: red!important;
    }
    [type='text']:focus{
      --tw-ring-color: rgba(0,0,0,0);
    }
  }
  @keyframes letterA {
    0%{
      border-left: 1px solid white;
    }
    100%{
      border-left: 1px solid rgba(0,0,0,0);
    }
  }
  .active{
    transition: all 1.5s ;
    animation: letterA 1.5s;
    animation-iteration-count: infinite;
  }

</style>