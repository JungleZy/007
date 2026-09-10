<template>
  <div class="w-full h-full relative" ref="mainZone">
    <div ref="firstZone" :class="['absolute']" style="width: 900px" @mousemove="dragMove" @mouseup="upOFF"
         @mouseleave="dragLeavel">
      <slot name="one"></slot>
      <div class="adjust-zone" style="top: -28px;position: absolute">
        <div class="layout-left-center drag" @mousedown="downON" @mouseup="upOFF" @mousemove="dragMove">
          <DragOutlined/>
        </div>
      </div>
    </div>
    <div ref="secondZone" class="absolute" :style="{width: twoWidth+'px',height: twoHeight+'px'}" @mousemove="dragMove2"
         @mouseleave="dragLeavel2" @mouseup="upOFF2">
      <slot name="two"></slot>
      <div style="position: absolute;left: calc(5% - 20px);top: 0;width: 40px;">
        <div class="adjust-zone layout-right-center" style="cursor: default" @click="onReduce(true)">
          <div class="layout-left-center drag2">
            <MinusOutlined/>
          </div>
        </div>
        <div class="adjust-zone layout-right-center" style="cursor: default;margin-top: 12px" @click="onMagnify(true)">
          <div class="layout-left-center drag2">
            <PlusOutlined/>
          </div>
        </div>
        <div class="adjust-zone layout-right-center" style="margin-top: 12px">
          <div class="layout-left-center drag2" @mousedown="downON2" @mouseup="upOFF2" @mousemove="dragMove2">
            <DragOutlined/>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: "NipMagicSpace"
}
</script>
<script setup>
import {onMounted, onBeforeUnmount, useTemplateRef, ref, nextTick, defineModel} from 'vue'
import {watchDeep} from '@vueuse/core'
import {DragOutlined, PlusOutlined, MinusOutlined} from '@ant-design/icons-vue'
import {useRoute} from 'vue-router'
import {message} from "ant-design-vue";

const mainZone = useTemplateRef('mainZone')
const firstZone = useTemplateRef('firstZone')
const secondZone = useTemplateRef('secondZone')
const firstZoneSize = ref([0, 0])      // 插槽内容宽度
const secondZoneSize = ref([0, 0])     // 插槽内容高度
const route = useRoute()
const routerName = route.name
const dragLocalOne = routerName + 'One'
const dragLocalTwo = routerName + 'Two'
const twoWidth = ref(1000)
const twoHeight = ref(450)
const scale = ref(0.45)

const onReduce = (flag) => {
  twoWidth.value = twoWidth.value - 10
  twoHeight.value = twoWidth.value * scale.value
  localStorage.setItem(dragLocalTwo, JSON.stringify({
    left: secondZone.value.style.left,
    top: secondZone.value.style.top,
    width: twoWidth.value,
    height: twoHeight.value,
  }))
}
const onMagnify = () => {
  if (mainZone.value.offsetWidth > twoWidth.value + 10) {
    twoWidth.value = twoWidth.value + 10
    twoHeight.value = twoWidth.value * scale.value
    localStorage.setItem(dragLocalTwo, JSON.stringify({
      left: secondZone.value.style.left,
      top: secondZone.value.style.top,
      width: twoWidth.value,
      height: twoHeight.value,
    }))
  } else {
    message.warning({content: '已最大', key: 'maxMagicSpace'})
  }
}
const firstZoneResizeObserver = new ResizeObserver(entries => {
  for (const entry of entries) {
    const {width, height} = entry.contentRect
    firstZoneSize.value = [width, height]
  }
})
const secondZoneResizeObserver = new ResizeObserver(entries => {
  for (const entry of entries) {
    const {width, height} = entry.contentRect
    secondZoneSize.value = [width, height]
  }
})
//初始化drag1的位置
const dragCoordinate = JSON.parse(localStorage.getItem(dragLocalOne))
if(dragCoordinate){
  if((dragCoordinate.left.replace('px','')*1)<0){
    dragCoordinate.left = '254px'
    dragCoordinate.top = '0px'
  }
  if((dragCoordinate.top.replace('px','')*1)<0){
    dragCoordinate.left = '254px'
    dragCoordinate.top = '0px'
  }
}

//初始化drag2的位置
const dragCoordinateTwo = JSON.parse(localStorage.getItem(dragLocalTwo))
if(dragCoordinateTwo){
  if((dragCoordinateTwo.left.replace('px','')*1)<0){
    dragCoordinateTwo.left = '149px'
    dragCoordinateTwo.top = '324px'
  }
  if((dragCoordinateTwo.top.replace('px','')*1)<0){
    dragCoordinateTwo.left = '149px'
    dragCoordinateTwo.top = '324px'
  }
  if(dragCoordinateTwo.width>1400){
    dragCoordinateTwo.width = 1020
    dragCoordinateTwo.height = 459
  }
}


watchDeep([firstZoneSize, secondZoneSize], () => {
  if (dragCoordinate === null) {
    handleZone()
  }
  if (dragCoordinateTwo === null) {
    handleZone2()
  }
})
onMounted(() => {
  dragleft1 = document.querySelectorAll('.drag')[0].offsetParent.offsetLeft
  dragleft2 = document.querySelectorAll('.drag2')[0].offsetParent.offsetLeft
  if (firstZone.value) {
    firstZoneResizeObserver.observe(firstZone.value)
  }
  if (secondZone.value) {
    secondZoneResizeObserver.observe(secondZone.value)
  }
//初始化drag1的位置
  if (dragCoordinate) {
    firstZone.value.style.left = dragCoordinate.left
    firstZone.value.style.top = dragCoordinate.top
    firstZoneleft = dragCoordinate.left.replace("px", "") * 1
  }
  //初始化drag2的位置
  if (dragCoordinateTwo) {
    console.log(dragCoordinateTwo.width)
    secondZone.value.style.left = dragCoordinateTwo.left
    secondZone.value.style.top = dragCoordinateTwo.top
    twoWidth.value = dragCoordinateTwo.width || 1000
    twoHeight.value = dragCoordinateTwo.height || 450
    if (twoWidth.value >= mainZone.value.offsetWidth) {
      twoWidth.value = 1000
      twoHeight.value = 450
    }
    secondZoneleft = dragCoordinateTwo.left.replace("px", "") * 1
  }
})
onBeforeUnmount(() => {
  firstZoneResizeObserver.disconnect()
  secondZoneResizeObserver.disconnect()
})
let firstZoneleft = 0//初始化left值
let dragleft1 = 0 //按钮到容器的left值
let top = null //按钮、容器到屏幕顶部的值
let screenLeft = 0 //最外层容器到屏幕左侧的值
const isDrag = ref(false)
//鼠标按下
const downON = (e) => {
  isDrag.value = true
  if (top === null) {
    top = e.y - firstZone.value.offsetTop
    screenLeft = e.x - firstZoneleft - dragleft1
  }
}
//鼠标释放
const upOFF = () => {
  isDrag.value = false
  localStorage.setItem(dragLocalOne, JSON.stringify({left: firstZone.value.style.left, top: firstZone.value.style.top}))
}
//鼠标移动
const dragMove = (e) => {
  if (isDrag.value) {
    let left = e.x - screenLeft - dragleft1
    let screentop = e.y - top
    if (left < 0) {
      left = 0
    }
    if (left > (mainZone.value.offsetWidth - firstZone.value.offsetWidth)) {
      left = mainZone.value.offsetWidth - firstZone.value.offsetWidth
    }
    if (screentop < 0) {
      screentop = 0
    }
    if (screentop > (mainZone.value.offsetHeight - firstZone.value.offsetHeight)) {
      screentop = mainZone.value.offsetHeight - firstZone.value.offsetHeight
    }
    firstZone.value.style.left = left + 'px'
    firstZone.value.style.top = screentop + 'px'
  }
}
//鼠标离开
const dragLeavel = (e) => {
  isDrag.value = false
  localStorage.setItem(dragLocalOne, JSON.stringify({left: firstZone.value.style.left, top: firstZone.value.style.top}))
}
const isDrag2 = ref(false)
let secondZoneleft = 0//初始化left值
let dragleft2 = 0 //按钮到容器的left值
let top2 = 0 //按钮、容器到屏幕顶部的值
let screenLeft2 = 0 //最外层容器到屏幕左侧的值
//鼠标按下
const downON2 = (e) => {
  console.log(e)
  isDrag2.value = true
  if (top2 === 0) {
    screenLeft2 = e.x - secondZoneleft - dragleft2
  }
  top2 = e.y - secondZone.value.offsetTop
  console.log(secondZone)
}
//鼠标释放
const upOFF2 = () => {
  isDrag2.value = false
  localStorage.setItem(dragLocalTwo, JSON.stringify({
    left: secondZone.value.style.left,
    top: secondZone.value.style.top,
    width: twoWidth.value,
    height: twoHeight.value,
  }))
}
//鼠标移动
const dragMove2 = (e) => {
  if (isDrag2.value) {
    let left = e.x - screenLeft2 - dragleft2
    let screentop = e.y - top2
    if (left < 0) {
      left = 0
    }
    if (left > (mainZone.value.offsetWidth - secondZone.value.offsetWidth)) {
      left = mainZone.value.offsetWidth - secondZone.value.offsetWidth
    }
    if (screentop < 0) {
      screentop = 0
    }
    if (screentop > (mainZone.value.offsetHeight - secondZone.value.offsetHeight)) {
      screentop = mainZone.value.offsetHeight - secondZone.value.offsetHeight
    }
    secondZone.value.style.left = left + 'px'
    secondZone.value.style.top = screentop + 'px'
  }
}
//鼠标离开
const dragLeavel2 = (e) => {
  isDrag2.value = false
  localStorage.setItem(dragLocalTwo, JSON.stringify({
    left: secondZone.value.style.left,
    top: secondZone.value.style.top,
    width: twoWidth.value,
    height: twoHeight.value,
  }))
}
const handleZone = () => {
  firstZone.value.style.left = mainZone.value.offsetWidth / 2 - (firstZoneSize.value[0] / 2) + 'px'
  firstZoneleft = mainZone.value.offsetWidth / 2 - (firstZoneSize.value[0] / 2)

}
const handleZone2 = () => {
  secondZone.value.style.left = mainZone.value.offsetWidth / 2 - (secondZoneSize.value[0] / 2) + 'px'
  secondZone.value.style.top = 430 + 'px'
  secondZoneleft = mainZone.value.offsetWidth / 2 - (secondZoneSize.value[0] / 2)
}
</script>

<style scoped lang="less">
.adjust-zone {
  border: 1px solid #fff;
  border-radius: 6px;
  padding: 6px 12px;
  cursor: move;
}
</style>