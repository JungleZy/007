<template>
  <div class="h-full w-full"></div>
</template>

<script>
export default {
  name:""
}
</script>
<script setup>
import Homophone from '../../../common/utils/Homophone';
const hp = new Homophone();
const ws = new WebSocket('ws://10.10.0.210:3333/ws')
ws.addEventListener('open',(e)=>{
  ws.send('Hello Server!')
})
ws.addEventListener('message',(e)=>{
  console.log(e)
})
</script>