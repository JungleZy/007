<template>
  <!--  ReceivePostResult-->
  <div class="w-full h-full layout-center content-mask-bg">
    <div class="w-full h-full updateTelegraphBox">
      <div class="main">
        <ocr-comp :type="true" :editTelegraph="editTelegraph" @save="saveTelegraph" v-if="hackReset"></ocr-comp>
      </div>
      <div class="thumb">
        <div class="listBox">
          <div class="list overflow-auto " >
            <div v-for="(img, i) in thumeImg" :key="i" @click="editTelegraphInfo(i)" :class="{ item: true, on: editTelegraphIndex == i }">
              <div class="index">{{ i + 1 }}</div>
              <img :src="img" class="img" />
            </div>
          </div>
          <div class="add" >
            <img :style="{border:editTelegraphIndex===-1?'1px solid rgba(255,255,255,0.6)':'1px solid rgba(255,255,255,0)'}" :src="addNext" class="img" @click="againPhotoGraph" />
          </div>
        </div>
        <div class="btn oper max" @click="startScoreInfo()">开始评分</div>
      </div>
    </div>
  </div>
</template>

<script>
  export default {
    name: 'ReceivePostResult'
  }
</script>
<script setup>
  import { ref, onMounted, onUnmounted, watch, nextTick } from 'vue'
  import { useRoute, useRouter } from 'vue-router'
  import { message } from 'ant-design-vue'
  import receiveTrain from './js/receiveTrain.js'
  import OcrComp from '../../../../../common/utils/ocr/OcrComp.vue'
  import addNextHJ from '../../../../../assets/HJ/postTrain/addNext.png'
  import addNextHJJ from '../../../../../assets/HJJ/postTrain/addNext.png'
  import addNextLJ from '../../../../../assets/LJ/postTrain/addNext.png'
  import addNextKJ from '../../../../../assets/KJ/postTrain/addNext.png'
  import { uploadReceiveResult } from '../../../../../common/api/ReceiveApi.js'
  let addNext
  const interfaceStyle = window.interfaceStyle
  if(interfaceStyle==='HJ'){
    addNext = addNextHJ
  }else if(interfaceStyle==='HJJ'){
    addNext = addNextHJJ
  }else if(interfaceStyle==='KJ'){
    addNext = addNextKJ
  }else {
    addNext = addNextLJ
  }
  const thumeImg = ref([])
  const hackReset = ref(true)
  const editTelegraphIndex = ref(-1)
  const editTelegraph = ref([])
  const copyTelegram = ref([])
  const list = ref([])
  const route = useRoute()
  const router = useRouter()
  const drillPath = ref('')
  router.getRoutes().forEach(r => {
    if (r.name === 'ReceivePostScore') {
      drillPath.value = r.path
    }
  })

  /**
   * 保存电报纸回调
   * @param img
   * @param data
   */
  const saveTelegraph = (img, data) => {
    editTelegraph.value = []
    if (editTelegraphIndex.value > -1) {
      copyTelegram.value[editTelegraphIndex.value] = data
      editTelegraphIndex.value = -1
    } else {
      copyTelegram.value.push(data)
      thumeImg.value.push(img)
    }
    list.value = copyTelegram.value.map((item, index) => {
      const newItem = []
      item.forEach(i => {
        i.forEach(j => {
          newItem.push(j.join(''))
        })
      })
      return newItem
    })
    againPhotoGraph()
  }

  /**
   * 编辑电报纸字码
   * @param index
   */
  const editTelegraphInfo = index => {
    editTelegraphIndex.value = index
    editTelegraph.value = copyTelegram.value[index]
  }

  /**
   * 重新拍照
   */
  const againPhotoGraph = () => {
    hackReset.value = false
    editTelegraph.value = []
    editTelegraphIndex.value = -1
    nextTick(() => {
      hackReset.value = true
    })
  }

  /**
   * 开始评分
   */
  const startScoreInfo = () => {
    if (copyTelegram.value.length === 0) {
      message.error('请确认提交您的抄报结果！')
      return false
    }
    // let data
    // data = copyTelegram.value.map(pag => pag.map(row => row.map(key => key[0])))
    let data = {
      id: route.query.id,
      result: list.value,
      images: thumeImg.value
    }
    uploadReceiveResult(data).then(res => {
      if (res.code === 200) {
        message.success('抄收电报纸结果上传成功！')
        setTimeout(() => {
          router.push({ path: drillPath.value, query: { id: route.query.id } })
        }, 1000)
      } else {
        message.error(res.message)
      }
    })
  }
</script>

<style scoped lang="less">
  .HJ{
    .updateTelegraphBox {
      /*background: url("../../../../../assets/HJ/postTrain/update-bg.jpg");*/
      background-size: auto 100%;
      border-bottom: 2px solid #364555;
      padding: 10px 6px;
      display: flex;
    }
    .main {
      width: 100%;
      padding: 10px 10px 6px;
    }
    .thumb {
      width: 200px;
      height: 100%;
      flex-shrink: 0;
      display: flex;
      flex-direction: column;
    }
    .listBox {
      height: calc(100% - 64px);
    }
    .listBox .add {
      padding-top: 10px;
    }
    .listBox .add .img {
      width: 191px;
      height: 80px;
      cursor: pointer;
    }
    .listBox .list {
      max-height: calc(100% - 110px);
    }
    .listBox .list .item {
      width: 192px;
      height: 106px;
      background: url('../../../../../assets/HJ/postTrain/thumeBg.png') no-repeat;
      padding: 20px 8px 10px;
      position: relative;
    }
    .listBox .list .item.on {
      background: url('../../../../../assets/HJ/postTrain/thumeBg-on.png') no-repeat;
    }
    .listBox .list .item + .item {
      margin-top: 10px;
    }
    .listBox .list .index {
      font-size: 12px;
      color: #fff;
      width: 30px;
      text-align: center;
      line-height: 1.2;
      position: absolute;
      right: 0;
      top: 0;
    }
    .listBox .list .img {
      width: 100%;
      height: 100%;
    }
    .btn.oper.max {
      width: 192px;
      height: calc(192px * 73 / 268);
      font-size: 22px;
      flex-shrink: 0;
      margin: 6px 0;
    }
  }
  .HJJ{
    .btn.oper.max {
      background: url('../../../../../assets/HJJ/postTrain/receiveBtn.png') no-repeat;
      background-size: 100% 100%;
      color: #ffffff;
      font-size: 14px;
      font-weight: revert;
      width: 154px;
      height: 50px;
      align-self: flex-start;
      padding-bottom: 5px;
    }
    .updateTelegraphBox {
      /*background: url("../../../../../assets/HJJ/postTrain/update-bg.jpg");*/
      background-size: auto 100%;
      border-bottom: 2px solid #364555;
      padding: 10px 6px;
      display: flex;
    }
    .main {
      width: 100%;
      padding: 10px 10px 6px;
    }
    .thumb {
      width: 200px;
      height: 100%;
      flex-shrink: 0;
      display: flex;
      flex-direction: column;
    }
    .listBox {
      height: calc(100% - 64px);
    }
    .listBox .add {
      padding-top: 10px;
    }
    .listBox .add .img {
      width: 191px;
      height: 80px;
      cursor: pointer;
    }
    .listBox .list {
      max-height: calc(100% - 110px);
    }
    .listBox .list .item {
      width: 192px;
      height: 106px;
      background: url('../../../../../assets/HJJ/postTrain/thumeBg.png') no-repeat;
      padding: 20px 8px 10px;
      position: relative;
    }
    .listBox .list .item.on {
      background: url('../../../../../assets/HJJ/postTrain/thumeBg-on.png') no-repeat;
    }
    .listBox .list .item + .item {
      margin-top: 10px;
    }
    .listBox .list .index {
      font-size: 12px;
      color: #fff;
      width: 30px;
      text-align: center;
      line-height: 1.2;
      position: absolute;
      right: 0;
      top: 0;
    }
    .listBox .list .img {
      width: 100%;
      height: 100%;
    }
    .btn.oper.max {
      width: 192px;
      height: calc(192px * 73 / 268);
      font-size: 22px;
      flex-shrink: 0;
      margin: 6px 0;
    }
  }
  .LJ{
    .btn.oper.max {
      background: url('../../../../../assets/LJ/postTrain/receiveBtn123.png') no-repeat;
      background-size: 100% 100%;
      color: #ffffff;
      font-size: 14px;
      font-weight: revert;
      width: 154px;
      height: 50px;
      align-self: flex-start;
      padding-bottom: 10px;
    }
    .updateTelegraphBox {
      /*background: url("../../../../../assets/LJ/postTrain/update-bg.jpg");*/
      background-size: auto 100%;
      /*border-bottom: 2px solid #364555;*/
      padding: 10px 6px;
      display: flex;
    }
    .main {
      width: 100%;
      padding: 10px 10px 6px;
    }
    .thumb {
      width: 200px;
      height: 100%;
      flex-shrink: 0;
      display: flex;
      flex-direction: column;
    }
    .listBox {
      height: calc(100% - 64px);
    }
    .listBox .add {
      padding-top: 10px;
    }
    .listBox .add .img {
      width: 191px;
      height: 80px;
      cursor: pointer;
    }
    .listBox .list {
      max-height: calc(100% - 110px);
    }
    .listBox .list .item {
      width: 192px;
      height: 106px;
      background: url('../../../../../assets/LJ/postTrain/thumeBg.png') no-repeat;
      padding: 20px 8px 10px;
      position: relative;
    }
    .listBox .list .item.on {
      background: url('../../../../../assets/LJ/postTrain/thumeBg-on.png') no-repeat;
    }
    .listBox .list .item + .item {
      margin-top: 10px;
    }
    .listBox .list .index {
      font-size: 12px;
      color: #fff;
      width: 30px;
      text-align: center;
      line-height: 1.2;
      position: absolute;
      right: 0;
      top: 0;
    }
    .listBox .list .img {
      width: 100%;
      height: 100%;
    }
    .btn.oper.max {
      width: 192px;
      height: calc(192px * 73 / 268);
      font-size: 22px;
      flex-shrink: 0;
      margin: 6px 0;
    }
  }
  .KJ{
    .btn.oper.max {
      background: url('../../../../../assets/KJ/postTrain/receiveBtn123.png') no-repeat;
      background-size: 100% 100%;
      color: #ffffff;
      font-size: 14px;
      font-weight: revert;
      width: 154px;
      height: 50px;
      align-self: flex-start;
      padding-bottom: 10px;
    }
    .updateTelegraphBox {
      /*background: url("../../../../../assets/KJ/postTrain/update-bg.jpg");*/
      background-size: auto 100%;
      /*border-bottom: 2px solid #364555;*/
      padding: 10px 6px;
      display: flex;
    }
    .main {
      width: 100%;
      padding: 10px 10px 6px;
    }
    .thumb {
      width: 200px;
      height: 100%;
      flex-shrink: 0;
      display: flex;
      flex-direction: column;
    }
    .listBox {
      height: calc(100% - 64px);
    }
    .listBox .add {
      padding-top: 10px;
    }
    .listBox .add .img {
      width: 191px;
      height: 80px;
      cursor: pointer;
    }
    .listBox .list {
      max-height: calc(100% - 110px);
    }
    .listBox .list .item {
      width: 192px;
      height: 106px;
      background: url('../../../../../assets/KJ/postTrain/thumeBg.png') no-repeat;
      padding: 20px 8px 10px;
      position: relative;
    }
    .listBox .list .item.on {
      background: url('../../../../../assets/KJ/postTrain/thumeBg-on.png') no-repeat;
    }
    .listBox .list .item + .item {
      margin-top: 10px;
    }
    .listBox .list .index {
      font-size: 12px;
      color: #fff;
      width: 30px;
      text-align: center;
      line-height: 1.2;
      position: absolute;
      right: 0;
      top: 0;
    }
    .listBox .list .img {
      width: 100%;
      height: 100%;
    }
    .btn.oper.max {
      width: 192px;
      height: calc(192px * 73 / 268);
      font-size: 22px;
      flex-shrink: 0;
      margin: 6px 0;
    }
  }
</style>
