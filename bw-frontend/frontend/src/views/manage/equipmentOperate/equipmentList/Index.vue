<template>
  <div class="w-full h-full overflow-hidden layout-side" style="padding: 0px 12px 12px 0px">
    <div v-if="isTrain" class=" w-full h-full transition-all duration-300 overflow-auto listBox" style="margin-left: 12px; padding-bottom: 20px" >
      <template v-for="v of euqipments">
        <div class="card" @click="selectEquipment(v)" v-if="v.isEnable == 1">
          <div class="equipmentCard layout-center">
            <img :src="fileUrl + '/' + v.image" alt="" />
          </div>
          <div class="title">{{ v.name }}</div>
        </div>
      </template>
    </div>
    <Score v-else :trainDataP="trainData"></Score>

    <!--新增训练-->
    <a-modal :destroyOnClose="true" :width="560" class="init_modal_style footer-border-none" destroyOnClose="true" v-model:visible="addDrillModal" @cancel="cancelTrainModal">
      <template #title>
        <strong>训练配置</strong>
      </template>
      <template #footer>
        <div class="w-full layout-center">
          <a-button @click="openUnity"> 生成训练 </a-button>
        </div>
      </template>
      <div class="configurationBox" style="padding-bottom: 0">
        <div class="rowItem" v-if="trainData.deviceName == '400W'">
          <div class="lab">类型：</div>
          <div class="item" style="padding-left: 2px">
            <a-radio-group v-model:value="trainData.type" @change="selectType">
              <a-radio :value="0">接收机</a-radio>
              <a-radio :value="1">发射机</a-radio>
              <a-radio :value="2">控制器</a-radio>
            </a-radio-group>
          </div>
        </div>
        <div class="rowItem" v-if="trainData.deviceName == '137' || trainData.deviceName == '134A' || trainData.deviceName == '171' || trainData.deviceName == '173' || trainData.deviceName == '121C'">
          <div class="lab">设备模式：</div>
          <div class="item" style="padding-left: 2px">
            <a-radio-group v-model:value="trainData.trainType">
              <a-radio :value="1">定频 </a-radio>
              <a-radio :value="2">跳频</a-radio>
              <!--              <a-radio :value="3">自动控制</a-radio>-->
              <a-radio :value="4" v-if="trainData.deviceName != '173' && trainData.deviceName != '121C'">自适应</a-radio>
            </a-radio-group>
          </div>
        </div>
      </div>
    </a-modal>
  </div>
</template>

<script>
export default {
  name: 'Index'
}
</script>
<script setup>
import NipLeftMenu from '../../../../components/common/NipLeftMenu.vue'
import Score from '../trainScore/Index.vue'
import { useRoute, useRouter } from 'vue-router'
import { ref, onUnmounted, provide, onMounted } from 'vue'
import Paho from '../../../../common/mqtt/paho-mqtt'
import { getEquipmentAll, addTrain } from '../../../../common/api/equipment'
import equipmentFore from './js/400w'
import equipment_125W from './js/125w'
import equipment_171 from './js/171'
import equipment_134A from './js/134A'
import equipment_173 from './js/173'
import equipment_121C from './js/121C'
import moment from 'moment'
const route = useRoute()
const router = useRouter()
const leftMenuWidth = ref(215)
provide('atRoute', route.matched[4])
provide('leftMenuWidth', leftMenuWidth)
const euqipments = ref([])
const fileUrl = window.fileUrl
const addDrillModal = ref(false)
const formData = ref({})
const isTrain = ref(true)
const trainData = ref({
  type: 0,
  trainType: 0,
  trainName: '',
  content: '',
  deviceName: '',
  deviceId: ''
})
const trainID = ref(JSON.parse(window.localStorage.getItem('userInfo')))
onMounted(() => {
  getEquipmentAll().then(res => {
    euqipments.value = res.data
  })
})
const { generateData_400W } = equipmentFore()
const { generateData_125W } = equipment_125W()
const { dp_171 } = equipment_171()
const { data_173 } = equipment_173()
const { dp_134A, tp_134A, zsy_134A } = equipment_134A()
const { generateData_121C } = equipment_121C()
const selectType = () => {
  if (trainData.value.type == 0) {
    trainData.value.deviceId = '0203020909'
  } else if (trainData.value.type == 1) {
    trainData.value.deviceId = '0203030909'
  } else {
    trainData.value.deviceId = '0203010909'
  }
  formData.value = generateData_400W(trainData.value.type)
}
let arr = []
const cancelTrainModal = () => {
  addDrillModal.value = false
}
const selectEquipment = v => {
  trainData.value.deviceId = v.deviceId
  trainData.value.deviceName = v.name
  if (v.name == '400W') {
    addDrillModal.value = true
  }else {
    console.log(route.matched[3].path+"/equipmentUnityHJJ")
    router.push({
      path:route.matched[3].path+"/equipmentUnityHJJ",
      query:{
        name:v.name
      }
    })
  }
  return;
  if (v.name == '400W') {
    addDrillModal.value = true
    formData.value = generateData_400W(trainData.value.type)
  } else if (v.name == '125W') {
    formData.value = generateData_125W()
    addEquipmentTrain()
  } else if (v.name == '171') {
    trainData.value.trainType = 1
    //生成定频数据
    formData.value = dp_171()
    addDrillModal.value = true
    addEquipmentTrain()
  } else if (v.name == '134A') {
    addDrillModal.value = true
    trainData.value.trainType = 1
    return
    // // return
    // addEquipmentTrain()
  } else if (v.name == '173') {
    trainData.value.trainType = 1
    addDrillModal.value = true
  } else if (v.name == '121C') {
    trainData.value.trainType = 1
    addDrillModal.value = true
  }
}
const openUnity = () => {
  // let objShell = new ActiveXObject("wscript.shell")
  // objShell.Run('C:\\Users\\Administrator\\Desktop\\火箭军软件\\新建文件夹\\134Start.exe')
  // objShell = null
  let name = "接收机"
  if(trainData.value.type==0){
    name="JS"
  }else if(trainData.value.type==1){
    name="FS"
  }else {
    name="KZ"
  }
  router.push({
    path:route.matched[3].path+"/equipmentUnityHJJ",
    query:{
      name:name
    }
  })
  return

  if (trainData.value.deviceName == '134A') {
    if (trainData.value.trainType == 1) {
      formData.value = dp_134A()
    } else if (trainData.value.trainType == 2) {
      formData.value = tp_134A()
    } else if (trainData.value.trainType == 4) {
      formData.value = zsy_134A()
    }
  }
  if (trainData.value.deviceName == '173') {
    if (trainData.value.trainType == 1) {
      formData.value = data_173(1)
    } else {
      formData.value = data_173(2)
    }
  }
  if (trainData.value.deviceName == '121C') {
    if (trainData.value.trainType == 1) {
      formData.value = generateData_121C(1)
    } else {
      formData.value = generateData_121C(2)
    }
  }
  addEquipmentTrain()
}
//生成训练
const addEquipmentTrain = () => {
  trainData.value.content = JSON.stringify(formData.value)
  trainName()
  const data = trainData.value
  isTrain.value = false
  addDrillModal.value = false
  openEquipment()
  // addTrain(data).then(res => {
  //   openEquipment()
  //   router.push({
  //     path: route.matched[3].path + '/equipmentScore',
  //     query: {
  //       id: res.data
  //     }
  //   })
  // })
}

//组装训练名称
const trainName = () => {
  let name = ''
  if (trainData.value.deviceName == '400W') {
    const eq = trainData.value.type == 0 ? '接收机' : trainData.value.type == 1 ? '发射器' : '控制器'
    trainData.value.trainName = trainData.value.deviceName + '-' + eq + '-' + moment().format('YYMMDDhhmmss')
  } else {
    switch (trainData.value.trainType) {
      case 1:
        name = '定频'
        break
      case 2:
        name = '跳频'
        break
      case 3:
        name = '自动控制'
        break
      case 4:
        name = '自适应'
        break
    }
    trainData.value.trainName = trainData.value.deviceName + '-' + name + '-' + moment().format('YYYYMMDD-hh:mm:ss')
  }
}

//打开设备
const openEquipment = () => {
  const v = trainData.value
  if (v.deviceName === '173') {
    window.open('equipment173://' + mqttUrl + ',' + trainID.value.id)
  } else if (v.deviceName === '171') {
    window.open('equipment171://' + mqttUrl + ',' + trainID.value.id)
  } else if (v.deviceName === '121C') {
    window.open('equipment121C://' + mqttUrl + ',' + trainID.value.id)
  } else if (v.deviceName === '134A') {
    window.open('equipment134A://' + mqttUrl + ',' + trainID.value.id)
  } else if (v.deviceName === '125W') {
    window.open('equipment125W://' + mqttUrl + ',' + trainID.value.id)
  } else if (v.deviceName === '400W') {
    if (trainData.value.type == '0') {
      window.open('equipment400WJ://' + mqttUrl + ',' + trainID.value.id)
    } else if (trainData.value.type == '1') {
      window.open('equipment400WF://' + mqttUrl + ',' + trainID.value.id)
    } else if (trainData.value.type == '2') {
      window.open('equipment400WK://' + mqttUrl + ',' + trainID.value.id)
    }
  }

  // mqttClint.subscribe("0000/0001",{qos:1})
  // mqttClint2.subscribe("0002/0003/0006/0007",{qos:1})
  // const userInfo = JSON.parse(localStorage.getItem("userInfo"))
  // const User = {ID:userInfo.id,equipmentID:"01"}
  // mqttClint.send("0000/0001",JSON.stringify(User),1)
}
</script>

<style scoped lang="less">
  .HJJ{
    .listBox {
      display: grid;
      grid-template-columns: repeat(auto-fill, 492px);
      justify-content: space-around;
    }
    .card {
      /*margin: auto;*/
      font-size: 18px;
      font-weight: bold;
    }
    .equipmentCard {
      background: url('../../../../assets/HJJ/equipment/equipmentBg.png');
      width: 492px;
      height: 349px;
    }
    .equipmentCard img {
      width: 80%;
      /*height: 60%;*/
    }
    .title {
      width: 95%;
      height: 32px;
      background: rgba(165, 186, 202, 0.3);
      margin: 10px auto;
      text-align: center;
      color: #bfcde0;
    }
    .card:hover .equipmentCard {
      background: url('../../../../assets/HJJ/equipment/equipmentBg-hover.png');
    }
    .card:hover .title {
      background: rgba(233, 222, 178, 0.3);
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
  }
.LJ{
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
    background: url("../../../../assets/LJ/equipment/equipmentBg.png");
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
    background: url("../../../../assets/LJ/equipment/equipmentBg-hover.png");
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
}
  .KJ{
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
      background: url("../../../../assets/KJ/equipment/equipmentBg.png");
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
      background: url("../../../../assets/KJ/equipment/equipmentBg-hover.png");
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
  }
</style>
