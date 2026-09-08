<template>
  <!--  理论题库-->
  <div  class="w-full h-full content-mask-bg grouping" style="display: flex; margin-left: 12px;justify-content: space-between;padding-top: 0;padding-bottom: 0;width: calc(100% - 12px)">
    <div class="w-full grouping_halving_line"></div>
    <div class=" h-full" style="width:100%;">
      <div style="height: 65px;display: flex;align-items: center" >
        <div class="item_group btn layout-center"  style="margin-top: 8px;margin-left: 8px;" @click="skipDetails('')"> <IconFont type="icon-tianjia1" style="margin-right: 4px;font-size: 16px"></IconFont> 新增自测 </div>
      </div>
      <div class="w-full overflow-auto  " style="height: calc(100% - 65px);padding: 0 12px 12px 12px">
        <div class="w-full h-full layout-center" v-if="listData.length===0">
            <nomore />
        </div>
        <div v-else class="overflow-auto w-full">
          <a-row >
            <a-col v-for="(d,i) in listData" class="card"  :xs="{span:12,offset:0}" :md="{span:12,offset:0}" :lg="{span:12,offset:0}" :xl="{span:8,offset:0}" :xxl="{span:6,offset:0}">
              <div class=" list relative">
                <img class="imgs" :src="llcy" alt="">
                <div class="listLabel" :title="d.title">
                  <span class="listLine" ></span>{{d.title}}
                </div>
                <div class="listImg relative" >
                  <div style="margin-left: 30px;font-size: 13px;color: #6f7f98">
                    <!--                    <div>总分：{{d.total}}</div>-->
                  </div>
                  <div class="theTitle">
                    <div style="display: flex;" >
<!--                      <div style="height: 40px;width: 40px;border: 1px solid #17233b;display: flex;align-items: center;justify-content: center">-->
<!--                        <IconFont type="icon-kaishishijian1" style="font-size: 35px;color: #70b9ec"></IconFont>-->
<!--                      </div>-->
                      <div style="padding-left: 10px">
                        <div style="font-size: 12px;color: rgb(111, 127, 152);">开始时间</div>
                        <div style="font-size: 16px;color: #70b9ec;font-weight: bold">{{d.startTime}}</div>
                      </div>
                    </div>
                    <div style="display: flex;margin-top: 10px" >
<!--                      <div style="height: 40px;width: 40px;border: 1px solid #17233b;display: flex;align-items: center;justify-content: center">-->
<!--                        <IconFont type="icon-jiandati1" style="font-size: 35px;color: #70b9ec"></IconFont>-->
<!--                      </div>-->
                      <div style="padding-left: 10px">
                        <div style="font-size: 12px;color: rgb(111, 127, 152);">考核分数</div>
                        <div style="font-size: 16px;color: #70b9ec;font-weight: bold">{{d.score}}分</div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
              <div class="btnGrade">
                <div  class="startTest" @click="startGrade(d)">
                  查看详情
                </div>
              </div>
            </a-col>
          </a-row>
        </div>
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
  import {
    FormOutlined,
    PlusOutlined,
    createFromIconfontCN
  } from '@ant-design/icons-vue';
  import knowledgeTabel from './js/knowledgeTabel'
  import useQuestionBank from '../../questionBank/js/useQuestionBank'
  import nomore from '../../../../../../components/nomore/nomore.vue'
  import {ref,onMounted,watch} from "vue";
  import {useRouter,useRoute} from 'vue-router'
  import llcy from '../../../../../../assets/HJ/test/llcy.png'

  const IconFont = createFromIconfontCN({
    scriptUrl: window.iconUrl,
  });
  const router = useRouter();
  const route = useRoute();
  onMounted(() => {
    testPaper()
  })
  const skipDetails = (e) => {
    router.push({
      path: route.matched[4].path + "/studentAddTest",
      query:{
        id: e.id,
      }
    })
  }
  const startGrade = (e)=>{
    router.push({
      path: route.matched[4].path + "/studentGradeDetails",
      query:{
        id: e.examId,
      }
    })
  }
  const {
    listData,
    takeNew,
    userRole,
    testPaper,
  }= knowledgeTabel()
</script>

<style lang="less" scoped>
  .list::before,.list::after {
    content:'';
    position:absolute;
    z-index:-1;
  }
  .list::before {
    background-image: linear-gradient(0deg,#fbab44 10%,#47526d 50%) ;
    top:0;
    left:0;
    width:100%;
    height:100%;
    transform:translate3d(0,100%,0);
    transition:transform .5s;
  }
  .card:hover .list::before {
    transform:translate3d(0,0,0);
  }












  .Cmenus_title:hover .actives{
    color: #70b9ec;
    font-weight: bolder;
  }
  .createDrillBtn {
    width: 96px;
    height: 30px;
    color: #e2f2ff;
    cursor: pointer;
    font-size: 15px;
    text-align: center;
    line-height: 28px;
    background-image: linear-gradient(#70a3b8, #4c7595);
    box-shadow: 2px 2px 3px rgba(0,0,0,.2);
    border-radius: 2px;
  }
  .theTitle{
    width: 100%;
    height: 160px;

    padding-top: 30px;
    align-items: center;
    padding-left: 20px;
  }
  .imgs{
    position: absolute;
    top: 60px;
    right: 0;
    z-index: 1;
    opacity: 0.7;
  }
  .list{
    /*height: 260px;*/
    min-width:222px;
    overflow: hidden;
    margin:0px 10px 10px 10px;
    transition: all 0.5s;
    z-index: 1;
    border: 1px solid #2c3b5a;
    padding: 1px;
  }
  .card{
    margin-bottom: 20px;
  }

  .listLabel{
    height: 40px;
    margin: auto 0;
    position: relative;
    background: #0e1c38;
    font-size: 18px;
    font-weight: bold;
    line-height: 19px;
    padding-right: 90px;
    overflow: hidden;
    white-space: nowrap;
    text-overflow: ellipsis;
  }
  .theLabel{
    height: 22px;
    width: 70px;
    position: absolute;
    border: 1px solid rgba(221,153,78,0.3);
    top: 12px;
    right: 12px;
    z-index: 2;
    display: flex;
    align-items: center;
    justify-content: center;
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
  .startTest{
    height: 34px;
    width: 122px;
    margin: 0 auto;
    background-image: url("../../../../../../assets/HJ/basicTheory/test/kh.png");
    background-size: 100% ;
    text-align: center;
    line-height: 34px;
    font-size: 15px;
    font-weight: 600;
    transition: all 0.5s;
    cursor: pointer;
    color: #c6e5ff;
  }
  .startTestEnd{
    background-image: url("../../../../../../assets/HJ/basicTheory/test/khEnd.png");
    color: #94a0b6;
  }
  .card:hover .startTest{
    background-image: url("../../../../../../assets/HJ/basicTheory/test/khActive.png");
    color: #fff4e3;
  }
  .card:hover .list{
   border: 1px solid transparent;
  }
  .card:hover .listImg{
   background-image: linear-gradient(0deg,rgba(163,114,29,0.05) ,transparent);
  }
   .btnGrade{
     margin: 0 10px;
     padding-top: 4px;
   }


  .listImg{
    height: calc(100% - 40px - 40px);
    width: 100%;
    background-repeat: no-repeat;
    background: #0e1c38;
    .infoimg{
      width: 100%;
      height: 100%;
    }
  }
  .listLine{
    display: inline-block;
    height: 14px;
    width: 3px;
    background: #6ebdff;
    vertical-align: middle;
    margin: 13px 12px 13px 12px;
  }
  .nomore{
    background: url('../../../../../assets/HJ/train/nomore.png') no-repeat 100% ;
    height: 293px;
    width: 290px;
    display: flex;
    align-items: flex-end;
    justify-content: center;
    font-size: 20px;
    color: #00a0e9;
  }
  .title_item1{
    margin-bottom: 1px;
    height: 64px;
    border-top: 1px solid #2a4562
  }
  .title_item2{
    width: 125px;
    height: 0;
    border-top: 5px solid #2a4163;
    border-left: 5px solid transparent;
    border-right: 5px solid transparent;
  }
  .title_zl{
    line-height: 18px;
    font-size: 18px;
    margin-top: 18px;
    margin-bottom: 8px;
  }
</style>