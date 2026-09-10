<template>
  <div class="w-full h-full" style="position: absolute;z-index: 999;top: 0">
    <div class="w-full h-full relative">
      <div class="rightTop layout-right-center " >
        <CloseOutlined @click="onClose" class="cursor-pointer-def" />
      </div>
      <iframe id="iframeId"
              name="iframeId"
              :src="file"
              width="100%"
              height="100%"
              frameborder="0"/>
    </div>
  </div>
</template>

<script>
export default {
  name: "instructions"
}
</script>

<script setup>
import {ref,inject} from "vue"
import {CloseOutlined} from "@ant-design/icons-vue"
let str = ''
const interfaceStyle = window.interfaceStyle
if(interfaceStyle==='HJ'){
  str = '/006/instructions/海军报务兵综合训练系统使用说明书.pdf'
} else if(interfaceStyle==='LJ'){
  str = '/006/instructions/陆军版本多功能报务综合训练系统使用说明书.pdf'
} else if(interfaceStyle==='HJJ') {
  str = '/006/instructions/多功能报务综合训练系统使用说明书.pdf'
} else if(interfaceStyle==='KJ') {
  str = '/006/instructions/空军版本多功能报务综合训练系统使用说明书.pdf'
}else {
  str = '/006/instructions/国动版本多功能报务综合训练系统使用说明书.pdf'
}
const file = ref(window.fileUrl + str)
const noOff = inject("noOff")
const onClose = ()=>{
  noOff.value = false
}
</script>

<style>
.rightTop {
  position: fixed;
  bottom: 10px;
  right: calc((100% - 300px) / 2 - 25px );
  height: 50px;
  width: 50px;
  font-size: 24px;
  color: #ffffff;
  background: rgba(255,0,0,0.3);
  border-radius: 50%;
  z-index: 999;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
