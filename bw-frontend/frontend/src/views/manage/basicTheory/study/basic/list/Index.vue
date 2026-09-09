<template>
  <div class="w-full h-full overflow-auto content-mask-bg  layout-left-top grouping ">
    <div class="w-full grouping_halving_line"></div>
    <div class="searchBox">
      <div class="card">
        <div class="layout-side title fs_dispose">专业岗位</div>
        <div class="cardBox fs_dispose">
          <div class="item layout-center relative" v-for="v of searchList.specialtyList" :class="[v.active?'active':'']" @click="selectItem(v)">
            <div class="className" :title="v.name">{{v.name}}</div>
          </div>
        </div>
      </div>
      <div class="card">
        <div class="layout-side title fs_dispose">人员类别</div>
        <div class="cardBox fs_dispose">
          <div class="item layout-center relative" v-for="v of searchList.difficultyList" :class="[v.active?'active':'']" @click="selectItem(v)">
            <div class="className" :title="v.name">{{v.name}}</div>
          </div>
        </div>

      </div>
    </div>
    <div style="width: calc(100% - 300px)" class="h-full tableBox">
      <div class="overflow-auto w-full pt-1 layout-left-top" style="padding-top: 20px;padding-left: 10px;">
        <a-table :columns="columns"
                 :rowKey="record=>record.id"
                 :pagination="false"
                 size="small"
                 :customRow="rowClick"
                 :data-source="showData">
          <template #createTime="{ text }">
            {{ getDayjs(text) }}
          </template>
          <template #swfs="{ text }">
            {{ text }} 个
          </template>
          <template #doneCount="{ record }">
            <span v-if="record.doneCount==0">未学习</span>
            <span v-else style="color: #0ca77c">{{'已学习'+record.doneCount+'/'+record.swfs}}</span>
          </template>doneCount
          <template #action="{ record }">
            <div title="开始学习" style="font-size: 24px;cursor: pointer;padding-bottom: 10px">
              <PlayCircleOutlined @click="skipDetails(record)"/>
            </div>
          </template>
        </a-table>
        <!--         之前的卡片布局-->
        <!--         <div v-for="(d,i) in showData" style="width: calc(100% / 5);padding: 12px;min-width: 245px">-->
        <!--           <div  class=" list relative"-->
        <!--                 style="background-image: linear-gradient(0deg,rgb(15 45 95), rgb(7 28 63)); border: solid 1px #22416d; height: 100%;width: calc(100%)"  @click="skipDetails(d)" :ref="list" :style="'height:'+height+'px'">-->
        <!--             <div class="theLabel" :class="[d.doneCount>0? 'theLabel1':'']">-->
        <!--               <div class="theLabelInfo" v-if="d.doneCount==0">-->
        <!--                 学习还<br/>未开始-->
        <!--               </div>-->
        <!--               <div class="theLabelInfo" v-if="d.doneCount>0">-->
        <!--                 已学习<br/>{{d.doneCount}}/{{d.swfs}}-->
        <!--               </div>-->
        <!--             </div>-->
        <!--             <div class="listLabel " style="overflow: hidden;padding-right: 70px;text-overflow: ellipsis;white-space: nowrap;">-->
        <!--               <span class="listLine"></span>{{d.title}}-->
        <!--             </div>-->
        <!--             &lt;!&ndash;              fileUrl+d.cover&ndash;&gt;-->
        <!--             <div class="listImg relative" >-->
        <!--               <img :src="fileUrl+d.cover" class="infoimg"/>-->
        <!--               <div class="listCard layout-side ">-->
        <!--                 <div class="createMan">-->
        <!--                   <p class="create">创建人：{{d.createUserName}}</p>-->
        <!--                   <p class="create">创建时间：{{d.cday}}</p>-->
        <!--                 </div>-->
        <!--                 <div class="createImg">-->
        <!--                   <p class="createImgNum createImgNumTwo">{{d.swfs}}</p>-->
        <!--                   <p class="createImgNum"  style="font-size: 12px">课件数量</p>-->
        <!--                 </div>-->
        <!--                 <div class="sjx"></div>-->
        <!--                 <div  class="sjxs"></div>-->
        <!--               </div>-->
        <!--             </div>-->
        <!--           </div>-->
        <!--         </div>-->
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

</template>

<script>
  export default {
    name: "BasicTheoryList"
  }
</script>
<script setup>
  import {
    SettingOutlined, EditOutlined, EllipsisOutlined, createFromIconfontCN,PlayCircleOutlined
  } from '@ant-design/icons-vue';
  import useList from "./js/useList.js";
  import useTable from "../../../../../../common/mixin/useTable.js";
  import {useRouter, useRoute} from 'vue-router'
  import {ref, onMounted, nextTick} from "vue";
  import {fontSizeDispose} from "../../../../../../common/utils/Utils";
  const router = useRouter();
  const route = useRoute();
  const IconFont = createFromIconfontCN({
    scriptUrl: window.iconUrl,
  });
  const myref=ref([]);//存储dom数组；
  const list=(el)=>{
    // console.log(el);
    myref.value.push(el)
  }
  const height=ref();
  const computeCardWidth=()=>{
    // console.log(myref.value[0].offsetWidth)
    let width = myref.value[0].offsetWidth;
    let a =parseInt(width/11) ;
    height.value=a*16
  }
  const rowClick = (record)=>{
    return{
      onClick:()=>{
        skipDetails(record)
      }
    }
  }
  onMounted(()=>{
    // if()
    // computeCardWidth()
    // window.onresize = () =>{
    //   return(()=>{
    //   computeCardWidth();
    //   })();
    // }
    nextTick(() => {
      fontSizeDispose();
    })
  })
  const getDayjs = (text) => {
    return dayjs(Number(text)).format('YYYY-MM-DD HH:mm:ss')
  }
  const fileUrl = ref(window.fileUrl);
  const {
    listData,
    getList,
    selectItem,
    showData,
    currTablePage,
    columns,
    selectTablePage,
    searchList,difficulty,specialty
  } = useList(computeCardWidth);

  const clearSearch = ()=>{
    difficulty.value = []
    specialty.value = []
    getList()
  }
  const skipDetails = (e) => {
    router.push({
      path: route.matched[4].path + "/theoryDetails",
      query: {
        id: e.id,
        studyType:route.query.studyType
      }
    })
  }

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
    background: url("../../../../../../assets/HJ/basicTheory/theLabelTwo.png");
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
    background: url("../../../../../../assets/HJ/basicTheory/theLabel.png");
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
    background: url("../../../../../../assets/HJ/basicTheory/kjsl.png");
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
    background: url('../../../../../../assets/HJ/train/nomore.png') no-repeat 100% ;
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
    padding-top: 20px;
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

  .card .item:hover .iconBox{
    display: block;
  }
  .card .item .iconBox{
    position: absolute;top: 0;right: 15px;display: none;
  }
  .HJ{
    .card .title{
      padding: 10px;
      font-size: 18px;
      color: #70c9ff;
      font-weight: bold;
      background: url("../../../../../../assets/HJ/basicTheory/studyManage/biaoti.png") no-repeat;
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
      background: url("../../../../../../assets/HJ/basicTheory/studyManage/classType.png") no-repeat;
      background-size: 100% 100%;
    }
    .card .active{
      background: url("../../../../../../assets/HJ/basicTheory/studyManage/classType_active.png") no-repeat;
      background-size: 100% 100%;
      color: #40a9ff;
    }
    .card .item:hover{
      background: url("../../../../../../assets/HJ/basicTheory/studyManage/classType_active.png") no-repeat;
      background-size: 100% 100%;
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
      background: url('../../../../../../assets/HJJ/basicTheory/studyManage/biaoti.png')
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
      background: url('../../../../../../assets/HJJ/basicTheory/studyManage/classType.png') no-repeat;
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
      background: url('../../../../../../assets/HJJ/basicTheory/studyManage/classType_active.png') no-repeat;
      background-size: 100% 100%;
      color: #e9deb2;
    }
    .card .item:hover {
      background: url('../../../../../../assets/HJJ/basicTheory/studyManage/classType_active.png') no-repeat;
      background-size: 100% 100%;
      color: #e9deb2;
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
      background: url('../../../../../../assets/LJ/basicTheory/studyManage/biaoti.png')
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
      background: url('../../../../../../assets/LJ/basicTheory/studyManage/classType.png')
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
      background: url('../../../../../../assets/LJ/basicTheory/studyManage/classType_active.png')
      no-repeat;
      background-size: 100% 100%;
      color: #e9deb2;
    }
    .card .item:hover {
      background: url('../../../../../../assets/LJ/basicTheory/studyManage/classType_active.png')
      no-repeat;
      background-size: 100% 100%;
      color: #e9deb2;
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
      background: url('../../../../../../assets/GD/basicTheory/studyManage/biaoti.png')
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
      background: url('../../../../../../assets/GD/basicTheory/studyManage/classType.png')
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
      background: url('../../../../../../assets/GD/basicTheory/studyManage/classType_active.png')
      no-repeat;
      background-size: 100% 100%;
      color: #e9deb2;
    }
    .card .item:hover {
      background: url('../../../../../../assets/GD/basicTheory/studyManage/classType_active.png')
      no-repeat;
      background-size: 100% 100%;
      color: #e9deb2;
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
      background: url('../../../../../../assets/KJ/basicTheory/studyManage/biaoti.png')
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
      background: url('../../../../../../assets/KJ/basicTheory/studyManage/classType.png')
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
      background: url('../../../../../../assets/KJ/basicTheory/studyManage/classType_active.png')
      no-repeat;
      background-size: 100% 100%;
      color: #e9deb2;
    }
    .card .item:hover {
      background: url('../../../../../../assets/KJ/basicTheory/studyManage/classType_active.png')
      no-repeat;
      background-size: 100% 100%;
      color: #e9deb2;
    }
    .cardBox {
      background: rgba(31,67,99,0.6);
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
      border-image: linear-gradient(to bottom, #1f4363, #234a70) 1;
      /*box-shadow: 0px 0px 1px #000000;*/
      text-shadow: 1px 1px 1px #000000;
    }
  }

</style>