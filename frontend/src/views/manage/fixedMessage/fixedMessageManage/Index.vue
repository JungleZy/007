<template>
  <div class="w-full h-full overflow-auto content-mask-bg  layout-left-top grouping">
    <div class="item_group btn" @click="addMessage"><PlusOutlined />&nbsp;新增报底</div>
    <div class="w-full layout-left-top" style="height: calc(100% - 32px)">
      <div class="searchBox">
        <div class="card">
          <div class="layout-side title fs_dispose">报底类型<IconFont type="icon-tianjia1" class="icon1" @click="openModel()"></IconFont></div>
          <div class="cardBox fs_dispose">
            <div class="item layout-center relative" v-for="v of cableType" :class="[v.active?'active':'']" @click="selectType(v,'type')">
              <!--            @click="selectItem(v)"-->
              <div class="className" :title="v.title">{{v.title}}</div>
              <div class="iconBox">
                <IconFont type="icon-shanchu1" style="margin-right: 10px;color: red" class="icon" @click.stop="deleteModel(v)"></IconFont>
                <IconFont type="icon-edit" style="color: #05b65b" class="icon"  @click.stop="openModel2(v)"></IconFont>
              </div>
            </div>
          </div>
        </div>
        <div class="card">
          <div class="layout-side title fs_dispose" style="padding-left: 4%">用途</div>
          <div class="cardBox fs_dispose">
            <div class="item layout-center relative" v-for="(v,index) of useType" :class="[v.active?'active':'']" @click="selectType(v)">
              <!--            @click="selectItem(v)"-->
              <div class="className" :title="v.title">{{v.title}}</div>
            </div>
          </div>
        </div>
      </div>
      <div style="width: calc(100% - 300px)" class="h-full tableBox">
        <div class="overflow-auto w-full pt-1 layout-left-top table_list_box " style="padding-top: 10px;padding-left: 10px;">
          <a-table :columns="columns"
                   :rowKey="record=>record.id"
                   :pagination="false"
                   :data-source="showData">
            <template #scope="{ text }">
              {{ text===0?'收报':text==1?'发报':'收发报' }}
            </template>
            <template #codeType="{ text }">
              {{ text===0?'数码':text===1?'字码':'混合码' }}
            </template>
            <template #codeSort="{ record }">
              {{ record.codeType===0?record.codeSort===0?'短码':'长码':'--' }}
            </template>
            <template #action="{ record }">
              <div class="flex layout-center">
                <div class="table_action_btn">
                  <div class="table_btn" title="报文详情" style="cursor: pointer;" @click="findMessageDetails(record)">
                    <FormOutlined/>
                  </div>
                  <div class="table_btn"  >
                    <DeleteOutlined style="color: red;" @click="deleteModel2(record.id)" />
                  </div>
                </div>
              </div>
            </template>
          </a-table>
        </div>
        <div v-if="listData.length!==0" class="table_pagination" style="width: 100%;">
          <div class="total">共{{listData.length>0? listData.length:0}}条数据</div>
          <div class="item prev" @click="selectTablePage('-')"></div>
          <template
            v-for="(item, i) in Math.ceil(listData.length/10)">
            <div :class="{item: true, active: item==currTablePage}"
                 v-if="item>(currTablePage-3)&&item<(currTablePage+3)"
                 @click="selectTablePage(item)">{{ item }}
            </div>
          </template>
          <div class="item next" @click="selectTablePage('+')"></div>
        </div>
      </div>
    </div>

    <!--新增类型-->
    <a-modal :destroyOnClose="true"
             :width="350"
             class="init_modal_style footer-border-none"
             destroyOnClose="true"
             v-model:visible="addDrillModal">
      <template #title>
        <strong>{{cableTypeTitle===null?'新增':'修改'}}报底类型</strong>
      </template>
      <template #footer>
        <div>
          <div class="layout-right-center" >
            <a-button @click="cancelModal">取消</a-button>
            <a-button @click="addCableType">确定</a-button>
          </div>
        </div>
      </template>
      <div  style="padding: 20px 20px;color: white">
        <span> 名称：</span><a-input
        :maxlength="10"
        onkeyup="value=value.replace(/[^\a-z\/A-Z\0-9\u4E00-\u9FA5]/g,'')"
        v-model:value="cableTypeTitle" style="width: 200px"></a-input>
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
  import {
    FileTextOutlined,
    FormOutlined,
    PlusOutlined, createFromIconfontCN, ExclamationCircleOutlined,DeleteOutlined
  } from '@ant-design/icons-vue';
  import fixedMessageManage from "./js/index";
  const IconFont = createFromIconfontCN({
    scriptUrl: window.iconUrl,
  });
  import {createVNode, ref} from 'vue'
  import {Modal} from "ant-design-vue";
  const deleteModel = (v)=>{
    Modal.confirm({
      class: 'init_modal_style',
      content: '是否删除该类型？',
      icon: () => createVNode(ExclamationCircleOutlined),
      okType: 'danger',
      okText: () => '确定',
      cancelText: () => '取消',
      maskClosable: true,
      onOk: () => {
        deleteCableType(v)
      }
    })
  }
  const deleteModel2 = (v)=>{
    Modal.confirm({
      class: 'init_modal_style',
      content: '是否删除该报文？',
      icon: () => createVNode(ExclamationCircleOutlined),
      okType: 'danger',
      okText: () => '确定',
      cancelText: () => '取消',
      maskClosable: true,
      onOk: () => {
        deleteHistory(v)
      }
    })
  }
  const {
    cableType,
    selectTablePage,
    showData,
    currTablePage,
    columns,
    listData,addDrillModal,cableTypeTitle,
    cableTypeTitleID,openModel,cancelModal,
    addCableType,deleteCableType,findMessageDetails,selectType,useType,addMessage,deleteHistory
  } = fixedMessageManage()
</script>

<style scoped lang="less">
  .list{
    min-width:222px;
    cursor: pointer;
    height: 100%;
  }
  .listLabel{
    height: 26px;
    margin: auto 0;
    position: relative;
    /*background: #0a1529;*/
  }
  .theLabel{
    background: url("../../../../assets/HJ/basicTheory/theLabelTwo.png");
    background-size: 100% ;
    background-repeat: no-repeat;
    height: 50px;
    width: 65px;
    position: absolute;
    /*display: flex;*/
    /*justify-content: right;*/
    top: -9px;
    right: 11px;
    z-index: 2;
    white-space: nowrap;
    text-overflow: ellipsis;
    overflow: hidden;
    .theLabelInfo{
      text-align: center;
      word-break: break-all;
      word-break: break-word;
      position: absolute;
      right: 6px;
      font-size: 12px;
      top: 3px;
      width: 45px;
      height: 40px;
    }
  }
  .theLabel1{
    background: url("../../../../assets/HJ/basicTheory/theLabel.png");
    background-size: 100% ;
    background-repeat: no-repeat;
    height: 50px;
    width: 65px;
    position: absolute;
    /*display: flex;*/
    /*justify-content: right;*/
    top: 6px;
    right: 21px;
    z-index: 2;
    white-space: nowrap;
    text-overflow: ellipsis;
    overflow: hidden;
    .theLabelInfo{
      text-align: center;
      word-break: break-all;
      word-break: break-word;
      position: absolute;
      right: 6px;
      font-size: 12px;
      top: 3px;
      width: 45px;
      height: 40px;
    }
  }
  .listImg{
    height: 300px;
    width: 100%;
    background-repeat: no-repeat;
    border: 10px solid rgba(0,0,0,0);
    overflow: hidden;
    border-bottom: 0px ;
    /*background: #00d1f4*/
    .infoimg{
      width: calc(100% * 49 / 36);
      height: 100%;
    }
  }
  .create{
    margin: 0;
    color: #667793;
    font-size: 10px;
    white-space: nowrap;
    text-overflow: ellipsis;
    overflow: hidden
  }
  .createMan{
    width:calc(100% - 80px)
  }
  .createImg{
    background: url("../../../../assets/HJ/basicTheory/kjsl.png");
    background-size: 100% ;
    background-repeat: no-repeat;
    width: 80px;
    height: 42px;
  }
  .createImgNum{
    margin: 0;
    color: #65b7f3;
    text-align: center;
    line-height: 20px
  }
  .createImgNumTwo{
    font-size: 20px;
    font-weight: 600;
  }
  .listCard{
    position: absolute;
    background: rgba(0,0,0,.5);
    width: calc(100%);
    height: 56px;
    left: 0px;
    bottom: 0px;
    padding: 6px;
    /*margin-bottom: 10px;*/
    border-bottom: 1px solid #5f96ca;
  }
  .listLine{
    display: inline-block;
    height: 14px;
    width: 3px;
    background: #6ebdff;
    vertical-align: middle;
    margin: 13px 10px 13px 10px;
  }
  .sjx{
    width: 0;
    height: 0;
    border-right: 3px solid transparent;
    border-left: 3px solid transparent;
    border-top: 3px solid #5f96ca;
    transform: rotate(45deg);
    position: absolute;
    left: -2px;
    bottom: -1px;
  }
  .sjxs{
    width: 0;
    height: 0;
    border-right: 3px solid transparent;
    border-left: 3px solid transparent;
    border-bottom: 3px solid #5f96ca;
    transform: rotate(135deg);
    position: absolute;
    right: -2px;
    bottom: -1px;
  }
  .nomore{
    background: url('../../../../assets/HJ/train/nomore.png') no-repeat 100% ;
    height: 293px;
    width: 290px;
    display: flex;
    align-items: flex-end;
    justify-content: center;
    font-size: 20px;
    color: #00a0e9;
  }
  .searchBox{
    width: 300px;height: 100%;
    padding-top: 10px;
  }
  .card{
    width: 100%;
    height: 50%;
  }
  .icon1{
    font-size: 26px;
    font-weight: bold;
    cursor: pointer;
  }
  .icon{
    font-size: 18px;
    font-weight: bold;
    cursor: pointer;
  }
  .className{
    width: 80%;
    overflow: hidden;
    white-space: nowrap;
    text-overflow: ellipsis;
    text-align: center;
  }
  .HJ{
    .card .title{
      padding: 10px;
      font-size: 18px;
      color: #70c9ff;
      font-weight: bold;
      background: url("../../../../assets/HJ/basicTheory/studyManage/biaoti.png") no-repeat;
      background-size: 100% 100%;
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
    }
    .card .item{
      margin: 10px;
      height: 83px;
      width: calc((100% - 40px)/ 2);
      font-weight: bold;
      cursor: pointer;
      box-sizing: border-box;
      background: url("../../../../assets/HJ/basicTheory/studyManage/classType.png") no-repeat;
      background-size: 100% 100%;
    }
    .card .active{
      background: url("../../../../assets/HJ/basicTheory/studyManage/classType_active.png") no-repeat;
      background-size: 100% 100%;
      color: #40a9ff;
    }
    .card .item:hover{
      background: url("../../../../assets/HJ/basicTheory/studyManage/classType_active.png") no-repeat;
      background-size: 100% 100%;
    }
    .card .item:hover .iconBox{
      display: block;
    }
    .card .item .iconBox{
      position: absolute;top: 0;right: 15px;display: none;
    }
    .cardBox{
      background: linear-gradient(180deg,#1f3a5e,rgba(21,41,76,0));
      height:calc(100% - 51px) ;
      overflow: auto;
      display: flex;
      flex-wrap: wrap;
      font-size: 16px;
      align-content: flex-start;
      color: #9bccff;
    }
  }
  .HJJ{
    .card .title {
      padding: 10px;
      font-size: 18px;
      color: #fdfeff;
      font-weight: bold;
      height: 45px;
      background: url('../../../../assets/HJJ/basicTheory/studyManage/biaoti.png')
      no-repeat;
      background-size: 100% 100%;
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      line-height: 15px;
    }
    .card .item {
      margin: 10px;
      height: 85px;
      width: calc((100% - 40px) / 2);
      font-size: 16px;
      font-weight: bold;
      cursor: pointer;
      box-sizing: border-box;
      background: url('../../../../assets/HJJ/basicTheory/studyManage/classType.png') no-repeat;
      background-size: 100% 100%;
    }
    .className {
      width: 80%;
      overflow: hidden;
      white-space: nowrap;
      text-overflow: ellipsis;
      text-align: center;
    }
    .card .active {
      background: url('../../../../assets/HJJ/basicTheory/studyManage/classType_active.png') no-repeat;
      background-size: 100% 100%;
      color: #e9deb2;
    }
    .card .item:hover {
      background: url('../../../../assets/HJJ/basicTheory/studyManage/classType_active.png') no-repeat;
      background-size: 100% 100%;
      color: #e9deb2;
    }
    .card .item:hover .iconBox {
      display: block;
    }
    .card .item .iconBox {
      position: absolute;
      top: 0;
      right: 15px;
      display: none;
    }
    .cardBox {
      background: #182431;
      height: calc(100% - 51px);
      overflow: auto;
      display: flex;
      flex-wrap: wrap;
      align-content: flex-start;
      color: #bdc9da;
      border-top: 0px;
      font-size: 16px;
      border-bottom: 1px solid;
      border-left: 1px solid;
      border-right: 1px solid;
      border-image: linear-gradient(to bottom, #2d3f51, #202a36) 1;
      /*box-shadow: 0px 0px 1px #000000;*/
      text-shadow: 1px 1px 1px #000000;
    }
  }
  .LJ{
    .card .title {
      padding: 10px;
      font-size: 18px;
      color: #fdfeff;
      font-weight: bold;
      height: 45px;
      background: url('../../../../assets/LJ/basicTheory/studyManage/biaoti.png')
      no-repeat;
      background-size: 100% 100%;
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      padding-left: 38%;
    }
    .card .item {
      margin: 10px;
      height: 85px;
      width: calc((100% - 40px) / 2);
      font-size: 16px;
      font-weight: bold;
      cursor: pointer;
      box-sizing: border-box;
      background: url('../../../../assets/LJ/basicTheory/studyManage/classType.png')
      no-repeat;
      background-size: 100% 100%;
    }
    .className {
      width: 80%;
      overflow: hidden;
      white-space: nowrap;
      text-overflow: ellipsis;
      text-align: center;
      color: #ffffff;
    }
    .card .active {
      background: url('../../../../assets/LJ/basicTheory/studyManage/classType_active.png')
      no-repeat;
      background-size: 100% 100%;
      color: #e9deb2;
    }
    .card .item:hover {
      background: url('../../../../assets/LJ/basicTheory/studyManage/classType_active.png')
      no-repeat;
      background-size: 100% 100%;
      color: #e9deb2;
    }
    .card .item:hover .iconBox {
      display: block;
    }
    .card .item .iconBox {
      position: absolute;
      top: 0;
      right: 15px;
      display: none;
    }
    .cardBox {
      background: #111715;
      height: calc(100% - 51px);
      overflow: auto;
      display: flex;
      flex-wrap: wrap;
      align-content: flex-start;
      color: #bddac5;
      border-top: 0px;
      font-size: 16px;
      border-bottom: 1px solid;
      border-left: 1px solid;
      border-right: 1px solid;
      border-image: linear-gradient(to bottom, #2d513a, #20362c) 1;
      /*box-shadow: 0px 0px 1px #000000;*/
      text-shadow: 1px 1px 1px #000000;
    }
  }
  .GD{
    .card .title {
      padding: 10px;
      font-size: 18px;
      color: #fdfeff;
      font-weight: bold;
      height: 45px;
      background: url('../../../../assets/GD/basicTheory/studyManage/biaoti.png')
      no-repeat;
      background-size: 100% 100%;
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      padding-left: 38%;
    }
    .card .item {
      margin: 10px;
      height: 85px;
      width: calc((100% - 40px) / 2);
      font-size: 16px;
      font-weight: bold;
      cursor: pointer;
      box-sizing: border-box;
      background: url('../../../../assets/GD/basicTheory/studyManage/classType.png')
      no-repeat;
      background-size: 100% 100%;
    }
    .className {
      width: 80%;
      overflow: hidden;
      white-space: nowrap;
      text-overflow: ellipsis;
      text-align: center;
      color: #ffffff;
    }
    .card .active {
      background: url('../../../../assets/GD/basicTheory/studyManage/classType_active.png')
      no-repeat;
      background-size: 100% 100%;
      color: #e9deb2;
    }
    .card .item:hover {
      background: url('../../../../assets/GD/basicTheory/studyManage/classType_active.png')
      no-repeat;
      background-size: 100% 100%;
      color: #e9deb2;
    }
    .card .item:hover .iconBox {
      display: block;
    }
    .card .item .iconBox {
      position: absolute;
      top: 0;
      right: 15px;
      display: none;
    }
    .cardBox {
      background: #245a7c2e;
      height: calc(100% - 51px);
      overflow: auto;
      display: flex;
      flex-wrap: wrap;
      align-content: flex-start;
      color: #bddac5;
      border-top: 0px;
      font-size: 16px;
      border-bottom: 1px solid;
      border-left: 1px solid;
      border-right: 1px solid;
      border-image: linear-gradient(to bottom, #20362c, #024f8d) 1;
      /*box-shadow: 0px 0px 1px #000000;*/
      text-shadow: 1px 1px 1px #000000;
    }
  }
  .KJ{
    .card .title {
      padding: 10px;
      font-size: 18px;
      color: #fdfeff;
      font-weight: bold;
      height: 45px;
      background: url('../../../../assets/KJ/basicTheory/studyManage/biaoti.png')
      no-repeat;
      background-size: 100% 100%;
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      padding-left: 38%;
    }
    .card .item {
      margin: 10px;
      height: 85px;
      width: calc((100% - 40px) / 2);
      font-size: 16px;
      font-weight: bold;
      cursor: pointer;
      box-sizing: border-box;
      background: url('../../../../assets/KJ/basicTheory/studyManage/classType.png')
      no-repeat;
      background-size: 100% 100%;
    }
    .className {
      width: 80%;
      overflow: hidden;
      white-space: nowrap;
      text-overflow: ellipsis;
      text-align: center;
      color: #ffffff;
    }
    .card .active {
      background: url('../../../../assets/KJ/basicTheory/studyManage/classType_active.png')
      no-repeat;
      background-size: 100% 100%;
      color: #e9deb2;
    }
    .card .item:hover {
      background: url('../../../../assets/KJ/basicTheory/studyManage/classType_active.png')
      no-repeat;
      background-size: 100% 100%;
      color: #e9deb2;
    }
    .card .item:hover .iconBox {
      display: block;
    }
    .card .item .iconBox {
      position: absolute;
      top: 0;
      right: 15px;
      display: none;
    }
    .cardBox {
      background: rgba(31, 67, 99, 0.3);
      height: calc(100% - 51px);
      overflow: auto;
      display: flex;
      flex-wrap: wrap;
      align-content: flex-start;
      color: #bddac5;
      border-top: 0px;
      font-size: 16px;
      border-bottom: 1px solid;
      border-left: 1px solid;
      border-right: 1px solid;
      border-image: linear-gradient(to bottom, rgba(80,141,230,0.6), #508de6) 1;
      /*box-shadow: 0px 0px 1px #000000;*/
      text-shadow: 1px 1px 1px #000000;
    }
  }
</style>