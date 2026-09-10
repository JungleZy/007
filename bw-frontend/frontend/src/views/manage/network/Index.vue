<template>
  <div class="w-full h-full">
    <div v-if="openRoute"  class="w-full h-full overflow-hidden layout-side">
      <NipLeftMenu></NipLeftMenu>
      <div class="w-full h-full grouping" :style="{width: 'calc(100% - '+(leftMenuWidth+10)+'px)'}">
        <div class="w-full grouping_halving_line"></div>
        <div class="w-full h-full grouping_content content-mask-bg">
          <div class="w-full table_search_box" style="padding: 20px 10px 0px 10px;">
            <div class="item_group btn" @click="addDrillModalInfo"><span class="ico add"></span>新增训练</div>
            <div class="item_group btn" v-if="userRole.id != '2'" @click="basicDeploy">评分规则</div>
          </div>

          <div class="w-full h-full" style="max-height: calc(100% - 70px);">
            <div class="table_list_box overflow-auto" style="height: calc(100% - 35px);padding: 0 10px">
              <a-table :columns="columns"
                       :loading="tableLoading"
                       :rowKey="record=>record.id"
                       :pagination="false"
                       :data-source="tableData.data">
                <template #createTime="{ text }">
                  {{'综合组网—' +text }}
                </template>
                <template #action="{ record }">
                  <div class="flex layout-center">
                    <div class="table_action_btn">
                      <div class="table_btn" title="查看成绩"
                           @click="startTrain(record)">
                        <FileTextOutlined />
                      </div>
                      <!--<div class="table_btn" title="删除">
                        <DeleteOutlined/>
                      </div>-->
                    </div>
                  </div>
                </template>
              </a-table>
            </div>
            <div class="table_pagination" v-if="tableData.data && tableData.data.length > 0">
              <div class="total">共{{ tableData.totalNumber }}条数据</div>
              <div class="item prev" @click="selectTablePage('-')"></div>
              <template v-for="(item, i) in tableData.totalPage" :key="i">
                <div :class="{item: true, active: item==currTablePage}"
                     v-if="item>(currTablePage-3)&&item<(currTablePage+3)"
                     @click="selectTablePage(item)">{{ item }}</div>
              </template>
              <div class="item next" @click="selectTablePage('+')"></div>
            </div>
          </div>
        </div>
      </div>

      <!--新增训练-->
      <a-modal :destroyOnClose="true"
               :width="1560"
               class="init_modal_style footer-border-none"
               v-model:visible="addDrillModal"
               @cancel="addDrillModal=false">
        <template #title>
          <strong :style="{fontSize: (fs * 2 + 16) + 'px'}">训练配置</strong>
        </template>
        <template #footer>
          <div class="w-full layout-center">
            <div :class="{createDrillBtn: true,'btn-animate': !loading,loadingBtn: loading}"
                 @click="createDrillInfo" :style="{fontSize: (fs * 2 + 15) + 'px'}">
              <a-spin v-if="loading" size="small"/>
              生成训练
            </div>
          </div>
        </template>
        <div style="height: 700px" class="layout-side">
          <div class="equipment h-full" >
            <div class="equipmentType w-full">
              <a-select style="width: 100%" v-model:value="currentEquipmentType" @change="getDeviceTypeEquipmentInfo">
                <a-select-option v-for="item in equipmentTypeList"  :value="item.id" :key="item.id">{{item.typeName}}</a-select-option>
              </a-select>
            </div>
            <div class="equipmentList overflow-auto">
              <div v-for="(item,idx) in equipmentList" :key="idx" :class="{change_item_active:isActive == idx}" @click="selectEquipment(item,idx)" class="change_item">
                <div class="img_box">
                  <img :src="fileUrl+item.deviceImg" alt="">
                </div>
                <div class="equipment_name">{{item.deviceName}}</div>
              </div>
            </div>
          </div>
          <div class="h-full " style="width: calc(100% - 210px)">
            <div class="w-full layout-center" style="height: 50px;color:#fff;">联络文件</div>
            <div class="w-full overflow-auto" style="height: calc(100% - 50px)">
              <table style="display: flow-root">
                <tr v-for="(item, index) of tableDocData" :key="index">
                  <td class="tableTd" @click="cliceTd(index, num, v.value)" v-for="(v, num) of item" :key="num" :colspan="v.colspan" :rowspan="v.rowspan" :class="[isTitle(v.value) ? 'tableTitle' : '']" style="min-width: 60px" :style="{ height: v.height + 'px', width: v.width + 'px' }">
                    <a-input
                            :placeholder="v.range[0]+'~'+v.range[1]+(v.isParameter=='单台地址'?'，'+v.range[2]+'~'+v.range[3]:'')"
                            style="font-size: 12px;text-align: center"
                            :style="{width:v.isParameter=='单台地址'?'115px':v.isParameter=='信道'?'100px':''}"
                            @blur="blurParam(v)"
                            v-if="v.isParameter"
                            v-model:value="v.value"
                             ></a-input>
                    <span v-else>{{ v.value == 'table' ? '' : v.value }}</span>
                    <table v-if="v.value == 'table'" style="width: 100%">
                      <tr v-for="(item2, index2) of tableDocData2" :key="index2">
                        <td class="tableTd" @click.stop="cliceTd(index2, num2, v2.value)" v-for="(v2, num2) of item2" :key="num2" :colspan="v2.colspan" :rowspan="v2.rowspan" :class="[isTitle(v2.value) ? 'tableTitle' : '']" :style="{ height: v2.height + 'px', width: 75 + 'px' }">
                          <a-input v-if="v2.isParameter" v-model:value="v2.value" ></a-input>
                          <span v-else>{{ v2.value }}</span>
                        </td>
                      </tr>
                    </table>
                  </td>
                </tr>
              </table>
            </div>
          </div>
        </div>
      </a-modal>

      <!--基础练习配置-->
      <a-modal :destroyOnClose="true"
               :width="630"
               class="init_modal_style footer-border-none"
               v-model:visible="basicTrainDeployModal"
               @cancel="basicTrainDeployModal=false">
        <template #title>
          <strong >基础配置</strong>
        </template>
        <template #footer>
          <div class="w-full layout-center">
            <div :class="{createDrillBtn: true,'btn-animate': !loading,loadingBtn: loading}"
                 @click="addRule" :style="{fontSize: (fs * 2 + 15) + 'px'}">
              <a-spin v-if="loading" size="small"/>
              提交规则
            </div>
          </div>
        </template>

        <div style="height: 400px" class="layout-side">
          <div class="equipment h-full" >
            <div class="equipmentType w-full">
              <a-select style="width: 100%" v-model:value="currentEquipmentType" @change="getDeviceTypeEquipmentInfo">
                <a-select-option v-for="item in equipmentTypeList"  :value="item.id" :key="item.id">{{item.typeName}}</a-select-option>
              </a-select>
            </div>
            <div class="equipmentList overflow-auto">
              <div v-for="(item,idx) in equipmentList" :key="idx" :class="{change_item_active:isActiveTwo == idx}" @click="deviceTypeRule(item,idx)" class="change_item">
                <div class="img_box">
                  <img :src="fileUrl+item.deviceImg" alt="">
                </div>
                <div class="equipment_name">{{item.deviceName}}</div>
              </div>
            </div>
          </div>
          <div class="h-full " style="width: calc(100% - 210px);position: relative;">
            <div class="weight_title">设置权重</div>
            <div class="weight_box">
              <div v-for="(item,index) in TJPRule" :key="index" style="margin-top: 20px" class="weight_item">
                <span>{{item.paramName}} :</span>
                <a-input-number style="width: 190px" v-model:value="item.weight"  :min="0" :max="100" placeholder="范围0~100" ></a-input-number><span style="text-align: left;margin-left: 5px">分</span>
              </div>
            </div>
          </div>
        </div>
      </a-modal>
    </div>
    <router-view v-else/>
  </div>
</template>

<script>
export default {
  name: "ReceivePostPractise"
}
</script>
<script setup>
  import {ref,watch,onMounted} from "vue";
  import {PlayCircleOutlined, DeleteOutlined,FileTextOutlined,PlusOutlined,FundViewOutlined,
    CloseCircleOutlined,WarningOutlined} from '@ant-design/icons-vue';
  import NipLeftMenu from '../../../components/common/NipLeftMenu.vue'
  import {global} from "../../../config/pinia/index.js"
  import telegramList from "./js/telegram";
  import network from "./js/network";
  import {useRouter,useRoute} from "vue-router";
  const useGlobalStore = global.useGlobalStore()
  const leftMenuWidth = ref(useGlobalStore.leftWidth);

  const router = useRouter()
  const route = useRoute()
  const userRole = ref(JSON.parse(localStorage.getItem('userRole')));
  const fs = ref(JSON.parse(localStorage.getItem('fs')));
  const loading = ref(false);
  const fileUrl = ref(window.fileUrl );
  const openRoute=ref(true);
  watch(route,()=>{
    if(route.matched[route.matched.length-1].path==='/preview/networkUsing/equipmentNetwork/equipmentTrainListHJBW/equipmentUnitysHJBW' || route.matched[route.matched.length-1].path==='/preview/networkUsing/equipmentNetwork/equipmentTrainListHJBW/testDetailsHJBW'){
      openRoute.value=false;
    } else {
      openRoute.value=true;
    }
  })
  onMounted(()=>{
    if(route.matched[route.matched.length-1].path==='/preview/networkUsing/equipmentNetwork/equipmentTrainListHJBW/equipmentUnitysHJBW' || route.matched[route.matched.length-1].path==='/preview/networkUsing/equipmentNetwork/equipmentTrainListHJBW/testDetailsHJBW'){
      openRoute.value=false;
    } else {
      openRoute.value=true;
    }
  })
  const {
    columns,tableData,currTablePage,selectTablePage,tableLoading
  } = telegramList();
  const {
    addDrillModal,
    equipmentTypeList,
    currentEquipmentType,
    equipmentList,
    tableDocData,
    tableDocData2,
    basicTrainDeployModal,
    TJPRule,
    isActive,
    isActiveTwo,

    getDeviceTypeEquipmentInfo,
    addDrillModalInfo,
    selectEquipment,
    cliceTd,
    isTitle,
    createDrillInfo,
    basicDeploy,
    addRule,
    deviceTypeRule,
    startTrain,
    blurParam,
  } = network(selectTablePage);

</script>

<style scoped>
  .grouping_content {
    display: flex;
    flex-direction: column;
    padding: 0;
  }
  .createDrillBtn {
    width: 96px;
    height: 30px;
    color: #e2f2ff;
    font-size: 15px;
    text-align: center;
    line-height: 28px;
    /*background-image: linear-gradient(#6cebfc, #006ea4);*/
    box-shadow: 2px 2px 3px rgba(0,0,0,.2);
    border-radius: 2px;
    background-image: linear-gradient(#22acff, #0068de);

  }
  .createDrillBtn.loadingBtn {
    cursor: no-drop;
    opacity: .8;
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
    color: #7b90af;
    text-align: right;
  }
  .configurationBox .rowItem .item {
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
  .groupBoxs {
    position: relative;
    border: 1px solid #354971;
    margin-top: 20px;
    padding-bottom: 8px;
  }
  .groupBoxs .groupTitle {
    color: #bbcdef;
    line-height: 20px;
    padding: 0 10px;
    background-color: #1f3a61;
    position: absolute;
    left: 10px;
    top: -10px;
  }
  .init_modal_style >>> .ant-modal-footer {
    border-top: none !important;
  }
  .auditionBtn {
    background-color: #009afd;
    line-height: 20px;
    flex-shrink: 0;
    font-size: 12px;
    padding: 0 10px;
    border-radius: 2px;
    cursor: pointer;
    margin-left: 20px;
  }
  .equipment{
    width: 190px;
    margin-top: 5px;
    margin-right: 20px;
  }
  .equipmentType{
    height: 40px;
  }
  .equipmentList{
    height: calc(100% - 40px);
    /* padding: 5px; */
    margin-top: 10px;
  }
  .change_item{
    height: 140px;
    border: 1px solid #2F5488;
    margin-bottom: 5px;
    display: flex;
    flex-direction: column;
    justify-content: space-between;
    align-items: center;
    background: url("../../../assets/HJ/home/menu_bg.png")no-repeat 0 0/100% 100%;
  }
  .change_item .img_box{
    display: flex;
    align-items: center;
    justify-content: center;
    width: 100%;
    height: 100%;
    overflow: hidden;
  }
  .change_item img{
    width: 100%;
    padding: 5px;
    /* margin-top: 25px; */
  }
  .change_item .equipment_name{
    width: 100%;
    text-align: center;
    line-height: 24px;
    background: #2F5488;
    color: #A1B4D2;
  }
  .change_item_active {
    border: 1px solid #6EBDFF;
    background: url("../../../assets/HJ/home/menu_bg_active.png")no-repeat 0 0/100% 100%;
  }
  .change_item_active .equipment_name {
    background: #6EBDFF;
    color: #081734;
  }
  .tableTd {
    border: 1px solid #33466D;
    text-align: center;
    background: #071633;
    color: #bfcde0;
    font-size: 14px;
  }
  .weight_title{
    position: absolute;
    top: 8px;
    left: 15px;
    padding: 0 8px;
    background: #203A61;
    color:#B4C9EC;
  }
  .weight_box{
    display: flex;
    flex-direction: column;
    padding-bottom: 30px;
    border: 1px solid #354871;
    margin-top: 20px;
    padding-top: 10px;
  }
  .weight_item{
    display: flex;
  }
  .weight_item span{
    display: inline-block;
    width: 80px;
    color: #7287A6;
    text-align: right;
    line-height: 32px;
    margin-right: 10px;
  }
</style>