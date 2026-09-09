<template>
  <div class="w-full h-full layout-left-center ">
    <div  style="width: 85%;height: 100%" class="relative">
      <div style="position: absolute;left: 10px;top: 0px;z-index: 2">
        <a-tooltip placement="right" color="#47421e" >
          <template #title>支持导入 docx 和 txt 文件，文档内容为正确格式报文,系统会对报文进行重新编排。</template>&nbsp;<QuestionCircleOutlined style="color: orange;font-size: 24px" />
        </a-tooltip>
      </div>
      <div style="height: 100px;width: 100%" class="layout-left-center relative">
        <div class="rowItem" >
          <div class="lab">报底名称：</div>
          <div class="item" style="width: 100px">
            <a-input v-model:value="messageData.title" style="height: 36px"></a-input>
          </div>
        </div>
        <div class="rowItem" >
          <div class="lab">报底类型：</div>
          <div class="item" style="width: 100px">
            <a-select v-model:value="messageData.codeType"  placeholder="请选择报底类型" style="width: 100px; text-align: left">
              <a-select-option :value="0">数码报</a-select-option>
              <a-select-option :value="1">字码报</a-select-option>
              <a-select-option :value="2">混合报</a-select-option>
            </a-select>
          </div>
        </div>
        <div class="rowItem" >
          <div class="lab">用途：</div>
          <div class="item" style="width: 100px">
            <a-select v-model:value="messageData.scope" placeholder="请选择用途" style="width: 100px; text-align: left">
              <a-select-option :value="0">收报</a-select-option>
              <a-select-option :value="1">发报</a-select-option>
              <a-select-option :value="2">收发报</a-select-option>
            </a-select>
          </div>
        </div>
        <div class="rowItem" >
          <div class="lab">报底所属类型：</div>
          <div class="item" style="width: 100px">
            <a-select v-model:value="messageData.typeId" placeholder="请选所属类型" style="width: 100px; text-align: left">
              <a-select-option v-for="(v,index) of cableType" :value="v.id" :key="index">{{v.title}}</a-select-option>
            </a-select>
          </div>
        </div>
        <div class="rowItem" >
          <div class="lab">备注信息：</div>
          <div class="item" style="width: 200px">
            <a-input v-model:value="messageData.remark" style="height: 36px"></a-input>
          </div>
        </div>
        <div class="rowItem" v-if="messageData.codeType===0">
          <div class="lab">长短码：</div>
          <div class="item" style="width: 100px">
            <a-select v-model:value="messageData.codeSort" style="width: 100px; text-align: left">
              <a-select-option :value="0">短码</a-select-option>
              <a-select-option :value="1">长码</a-select-option>
            </a-select>
          </div>
        </div>
        <div class="importBtn">
          <a-upload name="file" :showUploadList="false" accept=".docx,.txt" :customRequest="uploadChange" >
            <a-button>导入报底</a-button>
          </a-upload>
        </div>
      </div>
      <div class="relative cornerBox w-full"  style="height: calc(100% - 100px)">
        <div class="w-full layout-center animate-div-in absolute overflow-auto corner" style="padding: 2px; height: calc(100% - 56px)">
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
            <div class="keyRow" v-for="(tr, i) in pageData" :key="i">
              <a-input v-for="(td, j) in tr" :key="j" v-model:value="td[0]" class="keyCol" @keydown="keyDownStart" @focus="getFocus((i*10)+j)"></a-input>
              <div class="ser">{{ i + 1 }}</div>
            </div>
          </div>
        </div>
        <div style="height: 60px;width: 100%" class="layout-center">
          <div class="item_group btn fs_dispose" style="width: 80px;margin-right: 20px" @click="goBack"> <IconFont type="icon-rollback" style="margin-right: 5px"></IconFont>返回</div>
          <div class="item_group btn fs_dispose" style="width: 80px" @click="commitMessage"> <CheckOutlined style="margin-right: 5px"/>提交</div>
        </div>
      </div>

    </div>
    <div class="rightBox listBox layout-left-center" >
      <div class="list" style="overflow: auto">
        <div v-for="(img, i) in pageAllData" :key="i"  :class="{ item: true, on: activePage==i }" @click="selectPage(i)">
          <div class="index">{{ i + 1 }}</div>
          <img :src="url" class="img" />
          <IconFont type="icon-shanchu1" style="margin-right: 10px;color: red" class="icon" @click="deleteModel(i)"></IconFont>
        </div>
      </div>
      <div class="add">
        <img :src="addNext" class="img" @click="addPageMessage" />
      </div>
    </div>


  </div>
</template>

<script>
  export default {
    name: "Index"
  }
</script>
<script setup>
  import details from "./js/index";

  import addNextHJ from '../../../../assets/HJ/postTrain/addNext.png'
  import addNextHJJ from '../../../../assets/HJJ/postTrain/addNext.png'
  import addNextLJ from '../../../../assets/LJ/postTrain/addNext.png'
  import addNextKJ from '../../../../assets/KJ/postTrain/addNext.png'
  import {createFromIconfontCN, CheckOutlined, ExclamationCircleOutlined,QuestionCircleOutlined} from "@ant-design/icons-vue";
  import {Modal} from "ant-design-vue";
  import {createVNode} from "vue";
  const url = window.fileUrl+'/006/img/thume-manual.png'
  const IconFont = createFromIconfontCN({
    scriptUrl: window.iconUrl,
  });
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
  const deleteModel = (index)=>{
    Modal.confirm({
      class: 'init_modal_style',
      content: '是否删除该页报底？',
      icon: () => createVNode(ExclamationCircleOutlined),
      okType: 'danger',
      okText: () => '确定',
      cancelText: () => '取消',
      maskClosable: true,
      onOk: () => {
        deletePage(index)
      }
    })
  }
  const {pageData,messageData,cableType,commitMessage,pageAllData,addPageMessage,deletePage,
    selectPage,activePage,goBack,getFocus,uploadChange,keyDownStart} = details()
</script>

<style scoped lang="less">
  .rightBox{
    width: 15%;height: 100%;
    padding: 20px 10px;
    display: flex;
    flex-direction: column;
  }
  .item:hover .icon{
    display: block;
  }
  .importBtn{
    position: absolute;right: 40px;top: 40px
  }
  .icon{
    position: absolute;
    top:30px;
    right: 10px;
    cursor: pointer;
    display: none;
    font-size: 24px;
  }
  .HJ{
    .keyTable {
      height: calc(100% - 36px);
      display: flex;
      flex-direction: column;
      /*border: 1px solid #354971;*/
    }
    .keyRow {
      display: flex;
      flex: 1;
    }
    .keyRow .ser {
      width: 20px;
      margin-left: 10px;
      display: flex;
      align-items: center;
      flex-shrink: 0;
      color: #8eafca;
    }
    .keyCol {
      text-align: center;
      font-size: 18px;
    }
    .animate-div-in {
      animation: fadeIn 0.3s;
    }
    .animate-div-out {
      animation: fadeOut 1s;
    }
    @keyframes fadeIn {
      0% {
        top: -300px;
        opacity: 0;
      }
      100% {
        top: 0;
        opacity: 1;
      }
    }
    @keyframes fadeOut {
      0% {
        left: 0;
      }
      100% {
        left: -300px;
      }
    }
    .corner {
      position: relative;
    }
    .cornerBox:after,
    .cornerBox:before,
    .corner:after,
    .corner:before {
      content: '';
      width: 10px;
      height: 10px;
      position: absolute;
      z-index: 9;
    }
    .cornerBox:before {
      background: url('../../../../assets/HJ/postTrain/ico-lt.png') no-repeat center;
      left: 0;
      top: 0;
    }
    .cornerBox:after {
      background: url('../../../../assets/HJ/postTrain/ico-rt.png') no-repeat center;
      right: 30px;
      top: 0;
    }
    .corner:after {
      background: url('../../../../assets/HJ/postTrain/ico-lb.png') no-repeat center;
      left: 0;
      bottom: 0;
    }
    .corner:before {
      background: url('../../../../assets/HJ/postTrain/ico-rb.png') no-repeat center;
      right: 30px;
      bottom: 0;
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
      background-color: #70c9ff;
      width: 10%;
      color: #3f6089;
      font-weight: bold;
      font-size: 18px;
      text-align: center;
    }

    .rowItem {
      padding: 8px 8px 0px 0px;
      display: flex;
      align-items: center;
    }
    .rowItem.title {
      padding: 16px 0 0;
    }
    .rowItem .lab {
      width: max-content;
      padding: 0 5px;
      flex-shrink: 0;
      font-size: 13px;
      color: #7b90af;
      text-align: right;
    }
    .rowItem .item {
      font-size: 13px;
      color: #7b90af;
      width: 120px;
      text-align: center;
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
      width: 190px;
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
  }
  .HJJ{
    .keyTable {
      height: calc(100% - 36px);
      display: flex;
      flex-direction: column;
      /*border: 1px solid #354971;*/
    }
    .keyRow {
      display: flex;
      flex: 1;
    }
    .keyRow .ser {
      width: 20px;
      margin-left: 10px;
      display: flex;
      align-items: center;
      flex-shrink: 0;
      color: #8eafca;
    }
    .keyCol {
      text-align: center;
      font-size: 18px;
    }

    .animate-div-in {
      animation: fadeIn 0.3s;
    }
    .animate-div-out {
      animation: fadeOut 1s;
    }
    @keyframes fadeIn {
      0% {
        top: -300px;
        opacity: 0;
      }
      100% {
        top: 0;
        opacity: 1;
      }
    }
    @keyframes fadeOut {
      0% {
        left: 0;
      }
      100% {
        left: -300px;
      }
    }
    .corner {
      position: relative;
    }
    .cornerBox:after,
    .cornerBox:before,
    .corner:after,
    .corner:before {
      content: '';
      width: 10px;
      height: 10px;
      position: absolute;
      z-index: 9;
    }
    .cornerBox:before {
      background: url('../../../../assets/HJJ/postTrain/ico-lt.png') no-repeat center;
      left: 0;
      top: 0;
    }
    .cornerBox:after {
      background: url('../../../../assets/HJJ/postTrain/ico-rt.png') no-repeat center;
      right: 30px;
      top: 0;
    }
    .corner:after {
      background: url('../../../../assets/HJJ/postTrain/ico-lb.png') no-repeat center;
      left: 0;
      bottom: 0;
    }
    .corner:before {
      background: url('../../../../assets/HJJ/postTrain/ico-rb.png') no-repeat center;
      right: 30px;
      bottom: 0;
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
    .rowItem {
      padding: 8px 8px 0px 0px;
      display: flex;
      align-items: center;
    }
    .rowItem.title {
      padding: 16px 0 0;
    }
    .rowItem .lab {
      width: max-content;
      padding: 0 5px;
      flex-shrink: 0;
      font-size: 13px;
      color: #7b90af;
      text-align: right;
    }
    .rowItem .item {
      font-size: 13px;
      color: #7b90af;
      width: 120px;
      text-align: center;
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
      background: url('../../../../assets/HJJ/postTrain/thumeBg.png') no-repeat;
      padding: 20px 8px 10px;
      position: relative;
    }
    .listBox .list .item.on {
      background: url('../../../../assets/HJJ/postTrain/thumeBg-on.png') no-repeat;
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
  }
  .LJ{
    .keyTable {
      height: calc(100% - 36px);
      display: flex;
      flex-direction: column;
      /*border: 1px solid #26332e;*/
    }
    .keyRow {
      display: flex;
      flex: 1;
    }
    .keyRow .ser {
      width: 20px;
      margin-left: 10px;
      display: flex;
      align-items: center;
      flex-shrink: 0;
      color: #a9abaa;
    }
    .keyCol {
      text-align: center;
      font-size: 18px;
    }
    .animate-div-in {
      animation: fadeIn 0.3s;
    }
    .animate-div-out {
      animation: fadeOut 1s;
    }
    @keyframes fadeIn {
      0% {
        top: -300px;
        opacity: 0;
      }
      100% {
        top: 0;
        opacity: 1;
      }
    }
    @keyframes fadeOut {
      0% {
        left: 0;
      }
      100% {
        left: -300px;
      }
    }
    .corner {
      position: relative;
    }
    .cornerBox:after,
    .cornerBox:before,
    .corner:after,
    .corner:before {
      content: '';
      width: 10px;
      height: 10px;
      position: absolute;
      z-index: 9;
    }
    .cornerBox:before {
      background: url('../../../../assets/LJ/postTrain/ico-lt.png') no-repeat center;
      left: 0;
      top: 0;
    }
    .cornerBox:after {
      background: url('../../../../assets/LJ/postTrain/ico-rt.png') no-repeat center;
      right: 30px;
      top: 0;
    }
    .corner:after {
      background: url('../../../../assets/LJ/postTrain/ico-lb.png') no-repeat center;
      left: 0;
      bottom: 0;
    }
    .corner:before {
      background: url('../../../../assets/LJ/postTrain/ico-rb.png') no-repeat center;
      right: 30px;
      bottom: 0;
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
    .rowItem {
      padding: 8px 8px 0px 0px;
      display: flex;
      align-items: center;
    }
    .rowItem .lab {
      width: max-content;
      padding: 0 5px;
      flex-shrink: 0;
      font-size: 13px;
      color: white;
      text-align: right;
    }
    .rowItem .item {
      font-size: 13px;
      color: #a9abaa;
      width: 120px;
      text-align: center;
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
  }
  .KJ{
    .keyTable {
      height: calc(100% - 36px);
      display: flex;
      flex-direction: column;
      /*border: 1px solid #26332e;*/
    }
    .keyRow {
      display: flex;
      flex: 1;
    }
    .keyRow .ser {
      width: 20px;
      margin-left: 10px;
      display: flex;
      align-items: center;
      flex-shrink: 0;
      color: #a9abaa;
    }
    .keyCol {
      text-align: center;
      font-size: 18px;
    }
    .animate-div-in {
      animation: fadeIn 0.3s;
    }
    .animate-div-out {
      animation: fadeOut 1s;
    }
    @keyframes fadeIn {
      0% {
        top: -300px;
        opacity: 0;
      }
      100% {
        top: 0;
        opacity: 1;
      }
    }
    @keyframes fadeOut {
      0% {
        left: 0;
      }
      100% {
        left: -300px;
      }
    }
    .corner {
      position: relative;
    }
    .cornerBox:after,
    .cornerBox:before,
    .corner:after,
    .corner:before {
      content: '';
      width: 10px;
      height: 10px;
      position: absolute;
      z-index: 9;
    }
    .cornerBox:before {
      background: url('../../../../assets/KJ/postTrain/ico-lt.png') no-repeat center;
      left: 0;
      top: 0;
    }
    .cornerBox:after {
      background: url('../../../../assets/KJ/postTrain/ico-rt.png') no-repeat center;
      right: 30px;
      top: 0;
    }
    .corner:after {
      background: url('../../../../assets/KJ/postTrain/ico-lb.png') no-repeat center;
      left: 0;
      bottom: 0;
    }
    .corner:before {
      background: url('../../../../assets/KJ/postTrain/ico-rb.png') no-repeat center;
      right: 30px;
      bottom: 0;
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
    .rowItem {
      padding: 8px 8px 0px 0px;
      display: flex;
      align-items: center;
    }
    .rowItem .lab {
      width: max-content;
      padding: 0 5px;
      flex-shrink: 0;
      font-size: 13px;
      color: white;
      text-align: right;
    }
    .rowItem .item {
      font-size: 13px;
      color: #a9abaa;
      width: 120px;
      text-align: center;
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
  }
</style>