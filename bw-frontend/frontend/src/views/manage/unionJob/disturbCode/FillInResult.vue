<template>
  <div class="fillTrainResultBox w-full h-full">
    <div class="main">
      <div class="keyTableHead">
        <div class="key">1</div>
        <div class="key">2</div>
        <div class="key">3</div>
        <div class="key">4</div>
        <div class="key">5</div>
        <div class="key">6</div>
        <div class="key">7</div>
        <div class="key">8</div>
        <div class="key">9</div>
        <div class="key">10</div>
      </div>
      <div class="keyTable">
        <div class="keyRow" v-for="(tr, i) in trainResult" :key="i">
          <a-input v-for="(td, j) in tr" :key="j" v-model:value="td[0]" class="keyCol" @keydown="keyDownStart" @focus="getFocus((i*10)+j)"></a-input>
          <div class="ser">{{ i + 1 }}</div>
        </div>
      </div>
      <div class="layout-center pt-3">
        <div class="operBtn max" @click="saveFillInScore" v-if="trainResultIndex == -1"><img :src="saveIco" class="ico" />确认提交</div>
      </div>
    </div>
    <div class="thumb">
      <div class="listBox">
        <div class="list overflow-auto">
          <div v-for="(item, i) in emitResult" :key="i" @click="editResult(i)" :class="{ item: true, on: trainResultIndex == i }">
            <div class="index">{{ i + 1 }}</div>
            <img :src="thume" class="img" />
          </div>
        </div>
        <div class="add">
          <img :src="addNext" class="img" @click="addResult()" />
        </div>
      </div>
      <div class="btn oper max" :aria-disabled="submitting" @click="confirmResult()">{{ submitting ? '正在上传…' : '上传结果' }}</div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'FillInResult'
}
</script>
<script setup>
import { ref, onMounted, nextTick, defineEmits, watch } from 'vue'
import { message, Modal } from 'ant-design-vue'
import saveIco from '../../../../assets/HJ/postTrain/btn-ico-save.png'

import iconImage from "../../postJob/js/iconImage"
const {thume,addNext} = iconImage()

const props = defineProps({ submitting: Boolean })
const emits = defineEmits(['result'])
const trainResult = ref([])
const trainResultIndex = ref(-1)
const emitResult = ref([])
const focusIndex = ref(0)
const getFocus = (index)=>{
  focusIndex.value = index
}
const keyDownStart = e=>{
  if(e.code === 'Space'||e.key==='Enter') {
    if(e.preventDefault){
      e.preventDefault()
    }else {
      window.event.returnValue == false
    }
  }
  if(e.key==='Enter'&&focusIndex.value%10===9&&focusIndex.value!==99){
    focusIndex.value++
    const dom = document.querySelectorAll('.keyCol')
    nextTick(()=>{
      dom[focusIndex.value].focus()
    })
  }
  if(e.code === 'Space'&&focusIndex.value%10!==9){
    focusIndex.value++
    const dom = document.querySelectorAll('.keyCol')
    nextTick(()=>{
      dom[focusIndex.value].focus()
    })
  }
}
onMounted(() => {
  addResult()
  nextTick(() => {
    document.getElementsByClassName('keyCol')[0].focus()
  })
})

const saveFillInScore = () => {
  emitResult.value.push(trainResult.value)
  trainResultIndex.value = emitResult.value.length - 1
}

const editResult = i => {
  trainResult.value = emitResult.value[i]
  trainResultIndex.value = i
}

const addResult = () => {
  trainResult.value = []
  for (let i = 0; i < 10; i++) {
    trainResult.value.push([[''], [''], [''], [''], [''], [''], [''], [''], [''], ['']])
  }
  trainResultIndex.value = -1
}

const confirmResult = () => {
  if (props.submitting) return
  if (emitResult.value.length == 0) {
    message.error('您还未填写您的抄报结果！')
    return false
  }
  let flag = false
  trainResult.value.map(row => {
    row.map(col => {
      if (col[0] != '') {
        flag = true
      }
    })
  })
  if (trainResultIndex.value == -1 && flag) {
    Modal.confirm({
      content: '您还有未提交的抄报数据，是否确认上传抄报结果！',
      onOk() {
        emits('result', emitResult.value)
      }
    })
  } else {
    emits('result', emitResult.value)
  }
}
</script>

<style lang="less" scoped>
.HJJ,.HJ{
  .fillTrainResultBox {
    display: flex;
  }
  .main {
    width: 100%;
    padding: 10px 10px 6px;
  }
  .operBtn {
    width: 110px;
    height: 34px;
    background: url('../../../../assets/HJ/postTrain/operBtnBg.png') no-repeat center;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 15px;
    color: #fff;
    cursor: pointer;
    pointer-events: auto;
  }
  .keyTableHead {
    width: 100%;
    height: 36px;
    line-height: 36px;
    display: flex;
    align-items: stretch;
    padding-right: 30px;
    z-index: 10;
    position: relative;
  }
  .keyTableHead .key {
    background-color: #4c7595;
    width: 10%;
    color: #161e29;
    font-weight: bold;
    font-size: 18px;
    text-align: center;
  }
  .keyRow .ser {
    width: 20px;
    margin-left: 10px;
    display: flex;
    align-items: center;
    flex-shrink: 0;
    color: #8eafca;
  }
  .keyTable {
    height: calc(100% - 100px);
    display: flex;
    flex-direction: column;
    /*border: 1px solid #354971;*/
    .keyRow {
      display: flex;
      flex: 1;
      .keyCol {
        text-align: center;
        font-size: 18px;
      }
    }
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
    background: url('../../../../assets/HJ/postTrain/thumeBg.png') no-repeat;
    padding: 20px 8px 10px;
    position: relative;
  }
  .listBox .list .item.on {
    background: url('../../../../assets/HJ/postTrain/thumeBg-on.png') no-repeat;
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
    .fillTrainResultBox {
      display: flex;
    }
    .main {
      width: 100%;
      padding: 10px 10px 6px;
    }
    .operBtn {
      width: 110px;
      height: 34px;
      background: url('../../../../assets/LJ/postTrain/operBtnBg.png') no-repeat center;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 15px;
      color: #fff;
      cursor: pointer;
      pointer-events: auto;
    }
    .keyTableHead {
      width: 100%;
      height: 36px;
      line-height: 36px;
      display: flex;
      align-items: stretch;
      padding-right: 30px;
      z-index: 10;
      position: relative;
    }
    .keyTableHead .key {
      background-color: #38403d;
      width: 10%;
      color: #a9abaa;
      font-weight: bold;
      font-size: 18px;
      text-align: center;
    }
    .keyRow .ser {
      width: 20px;
      margin-left: 10px;
      display: flex;
      align-items: center;
      flex-shrink: 0;
      color: #8eafca;
    }
    .keyTable {
      height: calc(100% - 100px);
      display: flex;
      flex-direction: column;
      /*border: 1px solid #354971;*/
      .keyRow {
        display: flex;
        flex: 1;
        .keyCol {
          text-align: center;
          font-size: 18px;
        }
      }
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
      background: url('../../../../assets/LJ/postTrain/thumeBg.png') no-repeat;
      padding: 20px 8px 10px;
      position: relative;
    }
    .listBox .list .item.on {
      background: url('../../../../assets/LJ/postTrain/thumeBg-on.png') no-repeat;
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
  .fillTrainResultBox {
    display: flex;
  }
  .main {
    width: 100%;
    padding: 10px 10px 6px;
  }
  .operBtn {
    width: 110px;
    height: 34px;
    background: url('../../../../assets/KJ/postTrain/operBtnBg.png') no-repeat center;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 15px;
    color: #fff;
    cursor: pointer;
    pointer-events: auto;
  }
  .keyTableHead {
    width: 100%;
    height: 36px;
    line-height: 36px;
    display: flex;
    align-items: stretch;
    padding-right: 30px;
    z-index: 10;
    position: relative;
  }
  .keyTableHead .key {
    background-color: rgba(80,141,230,0.6);
    width: 10%;
    color: #a9abaa;
    font-weight: bold;
    font-size: 18px;
    text-align: center;
  }
  .keyRow .ser {
    width: 20px;
    margin-left: 10px;
    display: flex;
    align-items: center;
    flex-shrink: 0;
    color: #8eafca;
  }
  .keyTable {
    height: calc(100% - 100px);
    display: flex;
    flex-direction: column;
    /*border: 1px solid #354971;*/
    .keyRow {
      display: flex;
      flex: 1;
      .keyCol {
        text-align: center;
        font-size: 18px;
      }
    }
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
    background: url('../../../../assets/KJ/postTrain/thumeBg.png') no-repeat;
    padding: 20px 8px 10px;
    position: relative;
  }
  .listBox .list .item.on {
    background: url('../../../../assets/KJ/postTrain/thumeBg-on.png') no-repeat;
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
