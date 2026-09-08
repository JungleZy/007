<template>
  <div class="absolute top-border-mask t l"></div>
  <div class="absolute top-border-mask t r"></div>
  <div class="absolute top-border-mask b l"></div>
  <div class="absolute top-border-mask b r"></div>
</template>

<script>
export default {
  name: "NipBorderMask"
}
</script>

<style scoped>
.top-border-mask {
  background-color: #2E5C8C;
  height: 2px;
  width: 15px;
  animation: greenPulse 5s infinite;
}

.t {
  top: 0;
}

.b {
  bottom: 0;
}

.l {
  left: 0;
}

.r {
  right: 0;
}

@-webkit-keyframes greenPulse {
  from { background-color: #6ebdff; -webkit-box-shadow: 0 0 9px #333; }
  50% { background-color: #172642; -webkit-box-shadow: 0 0 18px #172642; }
  to { background-color: #6ebdff; -webkit-box-shadow: 0 0 9px #333; }
}
</style>