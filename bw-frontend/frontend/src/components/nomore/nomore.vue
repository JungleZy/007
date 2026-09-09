<template>
  <div class="w-full layout-center">
    <div class="nomore">
      <p style="color: #7b90af">暂无数据！</p>
    </div>
  </div>
</template>

<script>
export default {
  name: 'nomore'
}
</script>

<style scoped lang="less">
.nomore {
  background: url('../../assets/HJ/train/nomore.png') no-repeat 100%;
  height: 293px;
  width: 290px;
  padding: 20px 0;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  font-size: 20px;
  color: #00a0e9;
}
.HJJ,.LJ{
  .nomore {
    background: url('../../assets/LJ/train/nomore.png') no-repeat 100%;
    height: 293px;
    width: 290px;
    padding: 20px 0;
    display: flex;
    align-items: flex-end;
    justify-content: center;
    font-size: 20px;
    color: #00a0e9;
  }
}

</style>
