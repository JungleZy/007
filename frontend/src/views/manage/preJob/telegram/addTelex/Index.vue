<template>
  <div style="text-align: center">
    <canvas id="wbhz" width="200" height="200" style="background: #f6bd70"></canvas>
    <img id="qwe" src="../../../../../assets/HJ/canBg.png" alt="" />
    <canvas id="canvas" width="600" height="600" style="background: #131313"></canvas>
  </div>
  <!--  <video id="videos" src="../../../../../assets/HJ/pageBg.mp4" style="height: 100%;width: 100%;object-fit: fill" autoplay muted  loop></video>-->
</template>

<script>
import Canvas from './js/canvas'
import canBg from '../../../../../assets/HJ/canBg.png'
export default {
  name: 'Index',
  data() {
    return {
      ballS: [],
      ctx2: '',
      can: '',
      mouseX: '',
      mouseY: '',
      arr: []
    }
  },
  mounted() {
    const can = document.getElementById('canvas')
    const ctx = can.getContext('2d')
    let img = document.getElementById('qwe')
    let arr = []
    img.onload = () => {
      for (let i = 0; i < 360; i++) {
        arr.push(new Canvas(ctx))
      }
      ctx.clearRect(0, 0, 600, 600)
      ctx.translate(300, 300)
      for (let i in arr) {
        arr[i].x++

        arr[i].y++
        ctx.rotate(i)
        ctx.drawImage(img, arr[i].x, arr[i].y)
        ctx.save()
      }
      const qwe = setInterval(() => {
        ctx.clearRect(-300, -300, 600, 600)
        for (let i in arr) {
          arr[i].x++
          arr[i].y++
          // arr[i].y+=5
          // arr[i].y+=5
          // v.y++
          ctx.rotate(i)
          ctx.drawImage(img, arr[i].x, arr[i].y)
        }
      }, 100)
    }
  },
  methods: {}
}
</script>

<style scoped>
#mycanvas {
  color: #fff;
  height: 220px;
  width: 740px;
  display: flex;
  flex-direction: column;
  background: rgba(24, 45, 86, 0.7) !important;
  overflow: auto;
}
</style>
