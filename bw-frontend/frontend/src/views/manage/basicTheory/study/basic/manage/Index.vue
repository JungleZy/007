<template>
  <div class="w-full h-full overflow-hidden layout-side">
    <div class="w-full h-full grouping">
      <div class="w-full grouping_halving_line"></div>
      <div class="w-full h-full grouping_content content-mask-bg">
        <div class="w-full table_search_box" style="padding: 10px 10px 0px 0px">
          <div class="item_group btn fs_dispose" @click="showModal(0)"><span class="ico add"></span>新增理论</div>
        </div>
        <div class="w-full layout-left-top" style="height: calc(100% - 62px);">
          <div class="searchBox">
            <div class="card">
              <div class="title fs_dispose">专业岗位<IconFont type="icon-tianjia1" class="icon1" @click="openModel(0)"></IconFont></div>
              <div class="cardBox fs_dispose">
                <div class="item layout-center relative" v-for="v of searchList.specialtyList" :class="[v.active?'active':'']" @click="selectItem(v)">
                  <div class="className" :title="v.name">{{v.name}}</div>
                  <div class="iconBox">
                    <IconFont type="icon-shanchu1" style="margin-right: 10px;color: red" class="icon" @click.stop="deleteModel(v)"></IconFont>
                    <IconFont type="icon-edit" style="color: #05b65b" class="icon"  @click.stop="openModel(0,v)"></IconFont>
                  </div>
                </div>
              </div>
            </div>
            <div class="card mt-[6px]">
              <div class="layout-side title fs_dispose">人员类别<IconFont type="icon-tianjia1" class="icon1" @click="openModel(1)"></IconFont></div>
              <div class="cardBox fs_dispose">
                <div class="item layout-center relative" v-for="v of searchList.difficultyList" :class="[v.active?'active':'']" @click="selectItem(v)">
                  <div class="className" :title="v.name">{{v.name}}</div>
                  <div class="iconBox">
                    <IconFont type="icon-shanchu1" style="margin-right: 10px;color: red" class="icon"  @click.stop="deleteModel(v)"></IconFont>
                    <IconFont type="icon-edit" style="color: #05b65b" class="icon"  @click.stop="openModel(1,v)"></IconFont>
                  </div>
                </div>
              </div>

            </div>
          </div>
          <div class="tableBox fs_dispose" style="width: calc(100% - 300px);max-height: 100%;font-size: 14px">
            <div class="table_list_box overflow-auto" style="height: calc(100% - 35px);padding: 0 10px;overflow: auto">
              <a-table :columns="columns"
                       :rowKey="record=>record.id"
                       :pagination="false"
                       :data-source="tableList">
                <template #status="{ text }">
                  {{text===0?'关闭':'开启'}}
                </template>
                <template #createTime="{ text }">
                  {{ getDayjs(text) }}
                </template>
                <template #swfs="{ text }">
                  {{ text }} 个
                </template>
                <template #action="{ record }">
                  <div class="flex layout-center">
                    <div class="table_action_btn">
                      <!--            <div class="table_btn" title="理论详情" @click="showModal(1,record)">-->
                      <!--              <FileTextOutlined/>-->
                      <!--            </div>-->
                      <div class="table_btn" title="理论详情" @click="showModal(2,record)">
                        <FormOutlined/>
                      </div>
                    </div>
                  </div>
                </template>
              </a-table>
            </div>
            <div class="table_pagination" v-if="tableList.length > 0">
              <div class="total">共{{ tableData.length }}条数据</div>
              <div class="item prev" @click="selectTablePage('-')"></div>
              <template v-for="(item, i) in Math.ceil(tableData.length/10)">
                <div :class="{item: true, active: item==currTablePage}"
                     v-if="item>(currTablePage-3)&&item<(currTablePage+3)"
                     @click="selectTablePage(item)">{{ item }}</div>
              </template>
              <div class="item next" @click="selectTablePage('+')"></div>
            </div>
          </div>
        </div>
      </div>
    </div>
    <!--新增训练-->
    <a-modal :destroyOnClose="true"
             :width="350"
             class="init_modal_style footer-border-none"
             destroyOnClose="true"
             v-model:visible="addDrillModal">
      <template #title>
        <strong>{{modelTitle}}{{addtype==0?'专业岗位':'人员类别'}}</strong>
      </template>
      <template #footer>
        <div>
          <div class="layout-right-center" >
            <a-button @click="cancelClassifyModal">取消</a-button>
            <a-button @click="addClassify">确定</a-button>
          </div>
        </div>
      </template>
      <div  style="padding: 20px 20px;color: white">
        <span> 名称：</span><a-input
        :maxlength="10"
        onkeyup="value=value.replace(/[^\a-z\/A-Z\0-9\u4E00-\u9FA5]/g,'')"
        v-model:value="classifyName" style="width: 200px"></a-input>
      </div>
    </a-modal>
  </div>
</template>

<script>
  export default {
    name: "BasicTheoryManage"
  }
</script>
<script setup>
  import {
    FileTextOutlined,
    FormOutlined,
    PlusOutlined, createFromIconfontCN,
  } from '@ant-design/icons-vue';
  import useList from "./js/useList.js";
  import useTable from "../../../../../../common/mixin/useTable.js";
  import {useRouter, useRoute} from 'vue-router'
  import {Modal} from "ant-design-vue";
  import {ExclamationCircleOutlined} from "@ant-design/icons-vue";
  import {ref,createVNode} from 'vue'

  const router = useRouter();
  const route = useRoute();
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
        deleteClassify(v)
      }
    })
  }
  const IconFont = createFromIconfontCN({
    scriptUrl: window.iconUrl,
  });
  const {
    isTableStriped,
    tableSize,
    page,
    changeTableSize
  } = useTable();

  const {
    columns,tableData,tableList,currTablePage,selectTablePage,tableLoading,searchList,addDrillModal,addtype,addClassify,classifyName,classifyID,deleteClassify,selectItem
  } = useList();
  const modelTitle=ref('新增')
  const openModel = (type,v)=>{
    modelTitle.value = "新增"
    if(v){
      classifyID.value = v.id
      classifyName.value = v.name
      modelTitle.value = "修改"
    }
    if(type==0){
      addtype.value = 0
    }else {
      addtype.value = 1
    }
    addDrillModal.value = true
  }
  const cancelClassifyModal = ()=>{
    addDrillModal.value = false
  }

  const showModal = (e, r) => {
    router.push({
      path: route.matched[4].path + "/theoryEdit",
      query: e === 0 ? {
        type: e,
        studyType: route.query.studyType
      } : {
        type: e,
        id: r.id,
        studyType: route.query.studyType
      }
    })
  }
  const getDayjs = (text) => {
    return dayjs(Number(text)).format('YYYY-MM-DD HH:mm:ss')
  }
</script>

<style scoped lang="less">
  .card{
    width: 100%;
    height: 50%;
  }
  .searchBox{
    width: 300px;height: 100%;
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
      background: rgba(31,67,99,0.6);
      height: calc(100% - 48px);
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