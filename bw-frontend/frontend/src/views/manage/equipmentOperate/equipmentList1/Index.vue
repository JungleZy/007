<template>
  <div class="w-full h-full  overflow-hidden layout-side " style="padding:0px 12px 12px 0px">
    <div class="h-full transition-all duration-300 overflow-auto listBox" style="margin-left: 12px;padding-bottom: 20px;width: 100%" >
      <template v-for="v of euqipments" >
        <div class="card" @click="selectEquipment(v)" v-if="v.isEnable==1">
          <div class="equipmentCard layout-center">
            <img :src="fileUrl+'/'+v.image" alt="">
          </div>
          <div class="title">{{v.name}}</div>
        </div>
      </template>
    </div>
    <!--新增训练-->
    <a-modal :destroyOnClose="true"
             :width="560"
             class="init_modal_style footer-border-none"
             destroyOnClose="true"
             v-model:visible="addDrillModal"
             @cancel="cancelTrainModal">
      <template #title>
        <strong>训练配置</strong>
      </template>
      <template #footer>
        <div class="w-full layout-center">
          <a-button @click="openUnity">
            开始训练
          </a-button>
        </div>
      </template>
      <div class="configurationBox" style="padding-bottom: 0">
        <div class="rowItem" v-if="trainData.deviceName=='400W'">
          <div class="lab">类型：</div>
          <div class="item" style="padding-left: 2px;">
            <a-radio-group v-model:value="trainData.type" @change="selectType">
              <a-radio :value="'0203020909'">接收机</a-radio>
              <a-radio :value="'0203030909'">发射机</a-radio>
              <a-radio :value="'0203010909'">控制器</a-radio>
            </a-radio-group>
          </div>
        </div>
      </div>
    </a-modal>
  </div>
</template>

<script>
  export default {
    name: "Index"
  }
</script>
<script setup>
  import {useRoute,useRouter} from 'vue-router'
  import {ref, onUnmounted, provide,onMounted} from 'vue'
  import {getEquipmentAll} from '../../../../common/api/equipment'
  import moment from "moment";
  const route = useRoute()
  const router = useRouter()
  const euqipments =ref([])
  const fileUrl = window.fileUrl
  const addDrillModal = ref(false)
  const trainData = ref({
    trainType:0,
    type:'0203010909',
    deviceName:'',
  })
  onMounted(()=>{
    getEquipmentAll().then(res=>{
      euqipments.value = res.data
    })
  })
  const selectType = ()=>{
    trainData.value.deviceId =trainData.value.type
  }
  const selectEquipment = (v)=>{
    trainData.value.deviceId = v.deviceId
    trainData.value.deviceName = v.name
    // addDrillModal.value = true
    if(v.name=="400W"){
      addDrillModal.value = true
    }else {
      openUnity(v)
    }
  }
  let arr = []
  const cancelTrainModal = ()=>{
    addDrillModal.value = false
  }
  const mqttUrl = window.mqttUrl;
  const openUnity = ()=>{
    const v = trainData.value
    if(v.deviceName==='173'){
      window.open('equipment173://'+mqttUrl+','+'1234')
    }else if(v.deviceName==='171'){
      window.open('equipment171://'+mqttUrl+','+'1234')
    }else if(v.deviceName==='121C'){
      window.open('equipment121C://'+mqttUrl+','+'1234')
    }
    else if(v.deviceName==='134A'){
      window.open('equipment134A://'+mqttUrl+','+'1234')
    }
    else if(v.deviceName==='125W'){
      window.open('equipment125W://'+mqttUrl+','+'1234')
    }else if(v.deviceName==='400W'){

      if(trainData.value.type=='0203020909'){window.open('equipment400WJ://'+mqttUrl+','+'1234')}
      else if(trainData.value.type=='0203030909'){window.open('equipment400WF://'+mqttUrl+','+'1234')}
      else if(trainData.value.type=='0203010909'){
        window.open('equipment400WK://'+mqttUrl+','+'1234')
      }
    }


    // mqttClint.subscribe("0000/0001",{qos:1})
    // mqttClint2.subscribe("0002/0003/0006/0007",{qos:1})
    // const userInfo = JSON.parse(localStorage.getItem("userInfo"))
    // const User = {ID:userInfo.id,equipmentID:"01"}
    // mqttClint.send("0000/0001",JSON.stringify(User),1)
  }
</script>

<style scoped>
  .listBox{
    display: grid;
    grid-template-columns: repeat(auto-fill,492px);
    justify-content: space-around;
  }
  .card{
    /*margin: auto;*/
    font-size: 18px;
    font-weight: bold;
  }
  .equipmentCard{
    background: url("../../../../assets/HJ/equipment/equipmentBg.png");
    width: 492px;
    height: 349px;
  }
  .equipmentCard img{
    width: 80%;
    /*height: 60%;*/
  }
  .title{
    width: 95%;
    height: 32px;
    background: rgba(165,186,202,0.3);
    margin: 10px auto;
    text-align: center;
    color: #bfcde0;
  }
  .card:hover .equipmentCard{
    background: url("../../../../assets/HJ/equipment/equipmentBg-hover.png");
  }
  .card:hover .title{
    background: rgba(233,222,178,0.3);
    color: #e9deb2;
  }


  .configurationBox {
    padding-bottom: 20px;
    color: #fff;
  }
  .configurationBox .rowItem {
    padding: 8px 0;
    display: flex;
    align-items: center;
  }
  .configurationBox .rowItem.title {
    padding: 16px 0 0;
  }
  .configurationBox .rowItem .lab {
    width: 100px;
    flex-shrink: 0;
    font-size: 13px;
    color: #7b90af;
    text-align: right;
  }
  .configurationBox .rowItem .item {
    font-size: 13px;
    color: #7b90af;
    width: 120px;
    text-align: center;
  }
  .configurationBox .rowItem .item + .item {
    margin-left: 30px;
  }
  .configurationBox .rowItem .item .absolute {
    left: 0;
    top: 0;
    line-height: 32px;
    z-index: 9;
    padding: 0 8px;
    font-size: 12px;
    color: #7b90af;
    border-right: 1px solid #354971;
  }
  .configurationBox .rowItem.mini {
    padding-left: 6px;
  }
  .configurationBox .rowItem.mini .lab {
    width: 86px;
  }
  .configurationBox .rowItem.mini .item {
    width: 120px;
    margin-left: 12px;
  }
  .configurationBox .rowItem.mini .item .close {
    display: flex;
    margin-top: -7px;
    color: #d11d1d;
    position: absolute;
    right: -24px;
    top: 50%;
    cursor: pointer;
  }
  .configurationBox .rowItem .msg {
    height: 24px;
    font-size: 12px;
    color: #f8cf6f;
    display: flex;
    align-items: center;
    padding: 0 8px;
    background-color: #374a63;
  }
</style>