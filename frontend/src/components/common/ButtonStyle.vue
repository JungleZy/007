<template>
  <div class="relative nip-button-shadow click-pitch-none">
    <div class="button-corner absolute button-corner-left button-corner-top"></div>
    <div class="button-corner absolute button-corner-left button-corner-bottom"></div>
    <div class="button-corner absolute button-corner-right button-corner-top"></div>
    <div class="button-corner absolute button-corner-right button-corner-bottom"></div>
    <slot name="content"></slot>
  </div>
</template>

<script>
  export default {
    name: "ButtonStyle"
  }
</script>

<style scoped>
  .button-corner {
    width: 10px;
    height: 10px;
    z-index: 2;
  }

  .button-corner-top {
    border-top: 2px solid #4c7595;
    top: -4px;
  }

  .button-corner-left {
    border-left: 2px solid #4c7595;
    left: -4px;
  }

  .button-corner-bottom {
    border-bottom: 2px solid #4c7595;
    bottom: -4px;
  }

  .button-corner-right {
    border-right: 2px solid #4c7595;
    right: -4px;
  }
</style>
