<template>
  <div class="w-full h-full layout-center p-3">
    <a-button @click="change">change</a-button>
    <div class="blue"></div>
    <div class="green"></div>
    <div class="blue"></div>
    <div class="green"></div>
    <div class="blue"></div>
    <div class="green"></div>
    <a-upload action="http://10.10.0.210:8000/api/file/upload" :multiple="true">
      <a-button> Upload </a-button>
    </a-upload>
    <a-upload v-model:file-list="fileList" name="avatar" list-type="picture-card" class="avatar-uploader" :headers="{ currentPath: 'userImages' }" :show-upload-list="false" action="http://10.10.0.210:8000/api/file/upload" :before-upload="beforeUpload" @change="handleChange">
      <img v-if="userImg" :src="fileUrl + userImg" alt="avatar" />
      <div v-else>
        <div class="ant-upload-text">上传</div>
      </div>
    </a-upload>
    <div class="shadow-j"></div>
  </div>
</template>

<script>
export default {
  name: 'DivDemo'
}
</script>
<script setup>
import * as R from 'ramda'
import { message } from 'ant-design-vue'
import { ref } from 'vue'
import One from '../../../common/worker/One?worker'

const worker = new One()
worker.addEventListener('message', e => {
  if (e.data % 2) {
    worker.postMessage(e.data)
  }
})
let a = R.add(3, 4)
const fileList = ref([])
const fileUrl = ref(window.fileUrl)
const userImg = ref('')

const change = () => {
  anime({
    targets: ['.blue', '.green'],
    translateX: 250,
    rotate: 180,
    borderRadius: 100,
    delay: (el, i) => {
      return i * 10
    },
    direction: 'alternate',
    loop: true,
    easing: 'easeInOutQuart'
  })
}
const beforeUpload = file => {
  const isJpgOrPng = file.type === 'image/jpeg' || file.type === 'image/png'
  if (!isJpgOrPng) {
    message.error('上传文件格式错误，只支持jpg或者png图片!')
  }
  const isLt2M = file.size / 1024 / 1024 < 4
  if (!isLt2M) {
    message.error('上传文件超出4MB大小限制！')
  }
  return isJpgOrPng && isLt2M
}
const handleChange = info => {
  if (info.file.status === 'uploading') {
    return
  }
  if (info.file.status === 'done') {
  }
  if (info.file.status === 'error') {
    message.error('上传错误')
  }
}
</script>

<style scoped>
.shadow-j {
  width: 200px;
  height: 200px;
  box-shadow: 0 0 0.5px hsla(170deg, 95%, 80%, 1), 0 0 1px hsla(170deg, 95%, 80%, 0.95), 0 0 2px hsla(170deg, 95%, 80%, 0.95), 0 0 3px hsla(170deg, 95%, 80%, 0.95), 0 0 4px hsla(170deg, 95%, 80%, 0.9), 0 0 5px hsla(170deg, 95%, 80%, 0.9), 0 0 10px hsla(170deg, 95%, 80%, 0.9),
    0 0 20px hsla(170deg, 95%, 80%, 0.85), 0 0 40px hsla(170deg, 95%, 80%, 0.85), 0 0 60px hsla(170deg, 95%, 80%, 0.85);
}

.blue {
  width: 200px;
  height: 200px;
  background: blue;
}

.green {
  width: 200px;
  height: 200px;
  background: green;
}
</style>
