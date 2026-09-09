<template>
  <!--  理论题库-->
  <div class="h-full grouping" style="display: flex; margin-left: 12px; justify-content: space-between; padding-top: 0; width: calc(100% - 12px)">
    <div class="w-full grouping_halving_line"></div>
    <div class="h-full" style="width: 100%">
      <div class="w-full h-full content-mask-bg" style="padding-top: 0; padding-bottom: 0">
        <div style="height: 65px; display: flex; align-items: center">
          <div class="item_group btn layout-center" style="margin-top: 8px; margin-left: 8px" @click="skipDetails('')"><IconFont type="icon-tianjia1" style="margin-right: 4px; font-size: 16px"></IconFont> 新增测试</div>
        </div>
        <div class="w-full layout-center" style="height: calc(100% - 65px)" v-if="listData.length === 0">
          <nomore />
        </div>
        <div v-else class="overflow-auto w-full" style="height: calc(100% - 45px)">
          <a-row >
            <a-col v-for="(d,i) in listData"  :xs="{span:12,offset:0}" :md="{span:12,offset:0}" :lg="{span:12,offset:0}" :xl="{span:8,offset:0}" :xxl="{span:6,offset:0}">
              <div class=" list relative" >
<!--                <div class="theLabel" v-if="d.state==1"  v-per="'edit'" @click.stop="skipDetails(d)">-->
                <div class="theLabel" v-if="d.state==1&& userRole.id == 1" @click.stop="skipDetails(d)">
                  <FormOutlined  style="color: #dd994e"/>
                  <span style="margin-left: 5px;color: #dd994e" >编辑</span>
                </div>
                <a-popconfirm
                    title="是否删除该场测试?"
                    ok-text="是"
                    cancel-text="否"
                    @confirm.stop="deleteTest(d)"
                >
                  <DeleteOutlined class="deleteIcon" v-if="d.state==1&& userRole.id == 1" style="color: red"/>
                </a-popconfirm>
                <img class="imgs" :src="llcy" alt="">
                <div class="listLabel" :title="d.title" :style="{fontSize: (18 + fs * 2)+'px'}">
                  <span class="listLine" ></span>{{d.title}}
                </div>
                <div class="listImg relative" >
                  <div style="margin-left: 30px;color: #a5b6d0" :style="{fontSize: (13 + fs)+'px'}">
                    <div>监考人：{{d.teacherName?d.teacherName:d.userName}}</div>
                    <!--                    <div>总分：{{d.total}}</div>-->
                  </div>
                  <div class="theTitle">
                    <div style="display: flex;" >
                      <div style="height: 40px;width: 40px;border: 1px solid #17233b;display: flex;align-items: center;justify-content: center">
                        <IconFont type="icon-kaishishijian1" class="iconColor" style="font-size: 35px;"></IconFont>
                      </div>
                      <div style="padding-left: 10px">
                        <div style="color: #a5b6d0;" :style="{fontSize: (12 + fs)+'px'}">开始时间</div>
                        <div  class="iconColor" style="font-weight: bold" :style="{fontSize: (16 + fs)+'px'}">{{d["start_time"]}}</div>
                      </div>
                    </div>
                    <div style="display: flex;margin-top: 10px" >
                      <div style="height: 40px;width: 40px;border: 1px solid #17233b;display: flex;align-items: center;justify-content: center">
                        <IconFont  class="iconColor" type="icon-kaoheshichang1" style="font-size: 35px;"></IconFont>
                      </div>
                      <div style="padding-left: 10px">
                        <div style="color: #a5b6d0;" :style="{fontSize: (12 + fs)+'px'}">考核时长</div>
                        <div  class="iconColor" style="font-weight: bold" :style="{fontSize: (16 + fs)+'px'}">{{d.duration}}分钟</div>
                      </div>
                    </div>
                  </div>
                </div>
                <div class="startTest" @click="startTest(d)">
                  {{d.state==1?"开始考试":"进行中"}}
                </div>
              </div>
            </a-col>
          </a-row>
        </div>
      </div>
    </div>
  </div>
  <div></div>
</template>

<script>
export default {
  name: 'TheoryTest'
}
</script>

<script setup>
import { FormOutlined, PlusOutlined, createFromIconfontCN,DeleteOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import knowledgeTabel from './js/knowledgeTabel'
import useQuestionBank from '../../questionBank/js/useQuestionBank'
import nomore from '../../../../../../components/nomore/nomore.vue'
import { ref, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import llcy from '../../../../../../assets/HJJ/test/llcy.png'

const IconFont = createFromIconfontCN({
  scriptUrl: window.iconUrl
})
const fs = ref(JSON.parse(localStorage.getItem('fs')));
const router = useRouter()
const route = useRoute()
onMounted(() => {
  testPaper()
})
const skipDetails = e => {
  const userRole = JSON.parse(localStorage.getItem('userRole'))

  if (e.state == 1 || e == '') {
    if (userRole.remark == '普通人员') {
      router.push({
        path: route.matched[4].path + '/studentAddTest',
        query: {
          id: e.id
        }
      })
    } else {
      router.push({
        path: route.matched[4].path + '/addTest',
        query: {
          id: e.id
        }
      })
    }
  } else {
    message.error('考核已开始，不能在修改本堂考核！')
  }
}
const startTest = e => {
  if (e.state == 1 && userRole.value.id == 2) {
    message.error('考试暂未开始！')
    return false
  }
  router.push({
    path: route.matched[4].path + '/startTest',
    query: {
      id: e.id,
      state: e.state
    }
  })
}
const { listData, userRole, takeNew, testPaper ,deleteTest} = knowledgeTabel()
</script>

<style lang="less" scoped>

  .theTitle{
    width: 100%;
    height: 160px;

    padding-top: 30px;
    align-items: center;
    padding-left: 20px;
  }
  .imgs{
    position: absolute;
    top: 0;
    right: 0;
    z-index: 1;
    opacity: 0.7;
  }
  .listLabel{
    height: 40px;
    margin: auto 0;
    position: relative;
    /*background: #0e1c38;*/
    font-size: 18px;
    font-weight: bold;
    line-height: 19px;
    padding-right: 90px;
    overflow: hidden;
    white-space: nowrap;
    text-overflow: ellipsis;
  }
  .theLabel:hover{
    background: #1f212d;
  }
  .deleteIcon{
    height: 22px;
    width: 22px;
    position: absolute;
    top: 12px;
    right: 6px;
    font-size: 22px;
    z-index: 2;
    display: flex;
    align-items: center;
    justify-content: center;
    white-space: nowrap;
    text-overflow: ellipsis;
    overflow: hidden;
  }
  .deleteIcon:hover{
    background: #1f212d;
  }
  .theLabel{
    height: 22px;
    width: 70px;
    position: absolute;
    border: 1px solid rgba(221,153,78,0.3);
    top: 12px;
    right: 32px;
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
  .listImg{
    height: calc(100% - 40px - 40px);
    width: 100%;
    background-repeat: no-repeat;
    .infoimg{
      width: 100%;
      height: 100%;
    }
  }
  .nomore {
    background: url('../../../../../assets/HJ/train/nomore.png') no-repeat 100%;
    height: 293px;
    width: 290px;
    display: flex;
    align-items: flex-end;
    justify-content: center;
    font-size: 20px;
    color: #00a0e9;
  }


  .title_zl{
    line-height: 18px;
    font-size: 18px;
    margin-top: 18px;
    margin-bottom: 8px;
  }
  .HJ{
    .iconColor{color: #70b9ec!important;}
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
      background-image: linear-gradient(#6cebfc, #006ea4);
      box-shadow: 2px 2px 3px rgba(0,0,0,.2);
      border-radius: 2px;
    }
    .listLine{
      display: inline-block;
      height: 14px;
      width: 3px;
      background: #6ebdff;
      vertical-align: middle;
      margin: 13px 12px 13px 12px;
    }
    .list{
      height: 260px;
      min-width:222px;
      margin:0 10px 10px 10px;
      cursor: pointer;
      transition: all 0.5s;
      border: 1px solid #2c3b5a;
      /*background: #0e1c38;*/
      background-image: linear-gradient(50deg, #0e1c38 75%, rgba(37, 83, 128, 0.2));
      background-size: 300% auto;
    }
    .list:hover{
      border:1px solid #354971!important;
      box-shadow:  0 0 15px #364772;
      background-position: right center;color: #ffffff;
    }
    .list:hover .startTest{
      background-image: url("../../../../../../assets/HJ/basicTheory/test/khActive.jpg");
      color: #000000;
    }
    .startTest{
      height: 40px;
      width: 100%;
      background-image: url("../../../../../../assets/HJ/basicTheory/test/kh.jpg");
      text-align: center;
      line-height: 40px;
      font-size: 18px;
      font-weight: 600;
      transition: all 0.5s;
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
  }
  .HJJ{
    .iconColor{color: #e9deb2!important;}
    .Cmenus_title:hover .actives {
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
      box-shadow: 2px 2px 3px rgba(0, 0, 0, 0.2);
      border-radius: 2px;
    }
    .listLine {
      display: inline-block;
      height: 21px;
      width: 2px;
      background: #6f7986;
      vertical-align: middle;
      margin: 12px 12px 13px 12px;
    }
    .list{
      height: 260px;
      min-width:222px;
      margin:0 10px 10px 10px;
      cursor: pointer;
      transition: all 0.5s;
      border: 1px solid #3c4b5e;
      background-image: linear-gradient(50deg, rgba(59, 87, 110,0.2) 75%, rgba(59, 87, 110,0.9));
      background-size: 300% auto;
    }
    .list:hover{
      border:1px solid #3c4b5e!important;
      box-shadow:  0 0 15px #3c4b5e;
      background-position: right center;color: #ffffff;
    }
    .list:hover .listBg {
      border: 1px solid #b0a58a !important;
      box-shadow: 0 0 15px #3c4b5e;
      background-position: right center;
      color: #ffffff;
    }
    .startTest {
      height: 40px;
      width: 100%;
      background-image: url('../../../../../../assets/HJJ/test/kh.png');
      text-align: center;
      line-height: 40px;
      font-size: 18px;
      font-weight: 600;
      transition: all 0.5s;
      margin-top: 2px;
    }
    .list:hover .startTest {
      background-image: url('../../../../../../assets/HJJ/test/khActive.png');
      color: #ffffff;
    }
    .title_item1 {
      margin-bottom: 1px;
      height: 64px;
      border-top: 1px solid #2a4562;
    }
    .title_item2 {
      width: 125px;
      height: 0;
      border-top: 5px solid #2a4163;
      border-left: 5px solid transparent;
      border-right: 5px solid transparent;
    }
  }
  .LJ{
    .iconColor{color: #ff9213!important;}
    .Cmenus_title:hover .actives {
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
      box-shadow: 2px 2px 3px rgba(0, 0, 0, 0.2);
      border-radius: 2px;
    }
    .listLine {
      display: inline-block;
      height: 21px;
      width: 2px;
      background: #6f7986;
      vertical-align: middle;
      margin: 12px 12px 13px 12px;
    }
    .list{
      height: 260px;
      min-width:222px;
      margin:0 10px 10px 10px;
      cursor: pointer;
      transition: all 0.5s;
      border: 1px solid #5d6f6a;
      background-image: linear-gradient(50deg, rgba(26, 51, 44,0.8) 75%, rgba(57, 246, 193, 0.2));
      background-size: 300% auto;
    }
    .list:hover{
      border:1px solid #5d6f6a!important;
      box-shadow:  0 0 15px #5d6f6a;
      background-position: right center;color: #ffffff;
    }
    .list:hover .listBg {
      border: 1px solid #b0a58a !important;
      box-shadow: 0 0 15px #3c4b5e;
      background-position: right center;
      color: #ffffff;
    }
    .startTest {
      height: 40px;
      width: 100%;
      background-image: url('../../../../../../assets/HJJ/test/kh.png');
      text-align: center;
      line-height: 40px;
      font-size: 18px;
      font-weight: 600;
      transition: all 0.5s;
      margin-top: 2px;
    }
    .list:hover .startTest {
      background-image: url('../../../../../../assets/HJJ/test/khActive.png');
      color: #ffffff;
    }
    .title_item1 {
      margin-bottom: 1px;
      height: 64px;
      border-top: 1px solid #2a4562;
    }
    .title_item2 {
      width: 125px;
      height: 0;
      border-top: 5px solid #2a4163;
      border-left: 5px solid transparent;
      border-right: 5px solid transparent;
    }
  }
  .KJ{
    .iconColor{color: #ff9213!important;}
    .Cmenus_title:hover .actives {
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
      box-shadow: 2px 2px 3px rgba(0, 0, 0, 0.2);
      border-radius: 2px;
    }
    .listLine {
      display: inline-block;
      height: 21px;
      width: 2px;
      background: #6f7986;
      vertical-align: middle;
      margin: 12px 12px 13px 12px;
    }
    .list{
      height: 260px;
      min-width:222px;
      margin:0 10px 10px 10px;
      cursor: pointer;
      transition: all 0.5s;
      border: 1px solid #07283c;
      background-image: linear-gradient(50deg, rgba(80,141,230,0.3) 75%, rgba(80,141,230,0.2));
      background-size: 300% auto;
    }
    .list:hover{
      border:1px solid #508de6!important;
      box-shadow:  0 0 15px #508de6;
      background-position: right center;color: #ffffff;
    }
    .list:hover .listBg {
      border: 1px solid #b0a58a !important;
      box-shadow: 0 0 15px #3c4b5e;
      background-position: right center;
      color: #ffffff;
    }
    .startTest {
      height: 40px;
      width: 100%;
      background-image: url('../../../../../../assets/KJ/test/kh.png');
      text-align: center;
      line-height: 40px;
      font-size: 18px;
      font-weight: 600;
      transition: all 0.5s;
      margin-top: 2px;
    }
    .list:hover .startTest {
      background-image: url('../../../../../../assets/KJ/test/khActive.png');
      color: #ffffff;
    }
    .title_item1 {
      margin-bottom: 1px;
      height: 64px;
      border-top: 1px solid #2a4562;
    }
    .title_item2 {
      width: 125px;
      height: 0;
      border-top: 5px solid #2a4163;
      border-left: 5px solid transparent;
      border-right: 5px solid transparent;
    }
  }
</style>
