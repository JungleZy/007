<template>
  <div class="w-full h-full" style="display: flex;justify-content: space-between; padding:0 0 0 12px; width: calc(100% - 0px)">
    <div class="h-full " style="width: 100%">
      <div class="w-full h-full overflow-auto content-mask-bg">
        <div class="w-full h-full layout-center" v-if="listData.length === 0">
          <nomore />
        </div>
        <div v-else class="overflow-auto w-full h-full">
          <a-row v-if="userRole.id == 1">
            <a-col v-for="(d, i) in listData" class="card" :xs="{ span: 12, offset: 0 }" :md="{ span: 12, offset: 0 }" :lg="{ span: 12, offset: 0 }" :xl="{ span: 8, offset: 0 }" :xxl="{ span: 6, offset: 0 }">
              <div class="list relative">
                <a-popconfirm
                    title="是否删除该场测试?"
                    ok-text="是"
                    cancel-text="否"
                    @confirm="deleteTest(d)"
                >
                  <DeleteOutlined  class="deleteIcon" style="color: red;" title="删除" />
                </a-popconfirm>
                <div class="listBg">
                  <img class="imgs" :src="llcy" alt="" />
                  <div class="listLabel" :title="d.title"><span class="listLine"></span>{{ d.title }}</div>
                  <div class="listImg relative">
                    <div style="margin-left: 30px; font-size: 13px; color: #bfcde0">
                      <div>监考人：{{ userRole.id != 2 ? d.userName : d.teacherName }}</div>
                    </div>
                    <div class="theTitle">
                      <div style="display: flex">
                        <div style="padding-left: 10px">
                          <div style="font-size: 16px; color: #e9deb2; font-weight: bold">{{ d['start_time'] }}</div>
                        </div>
                      </div>
                      <div style="display: flex; margin-top: 10px">
                        <div style="padding-left: 10px">
                          <div style="font-size: 12px; color: #bfcde0">考核时长</div>
                          <div style="font-size: 16px; color: #e9deb2; font-weight: bold">{{ d.duration }}分钟</div>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
              <div class="btnGrade">
                <div class="startTest" v-if="d.state == 3 && userRole.id != 2" @click="startGrade(d)">开始评分</div>
                <div v-if="d.state == 4 && userRole.id != 2" class="startTest" :class="[d.state == 4 && userRole.id != 2 ? 'startTestEnd' : '']" @click="startGrade(d)">已评分</div>
                <div v-if="d.state == 4 && userRole.id == 2" class="startTest" @click="startGrade(d)">查看详情</div>
              </div>
            </a-col>
          </a-row>
          <div class="w-full h-full" v-if="userRole.id == 2">
            <a-select style="width: 120px; margin-left: 10px" placeholder="请选择类型" v-model:value="searchType">
              <a-select-option style="color: #7b90af" value="0">全部</a-select-option>
              <a-select-option style="color: #7b90af" value="1">测试</a-select-option>
              <a-select-option style="color: #7b90af" value="2">自测</a-select-option>
            </a-select>
            <div class="overflow-auto" style="height: calc(100% - 50px)">
              <a-row>
                <a-col v-if="searchType != 2" v-for="(d, i) in listData.exam" class="card" :xs="{ span: 12, offset: 0 }" :md="{ span: 12, offset: 0 }" :lg="{ span: 12, offset: 0 }" :xl="{ span: 8, offset: 0 }" :xxl="{ span: 6, offset: 0 }">
                  <div class="list relative">
                    <div class="listBg">
                      <img class="imgs" :src="llcy" alt="" />
                      <div class="listLabel" :title="d.title"><span class="listLine"></span>{{ d.title }}</div>
                      <div class="listImg relative">
                        <div style="margin-left: 30px; font-size: 13px; color: #bfcde0">
                          <div>监考人：{{ userRole.id != 2 ? d.userName : d.teacherName }}</div>
                          <!--                    <div>总分：{{d.total}}</div>-->
                        </div>
                        <div class="theTitle">
                          <div style="display: flex">
                            <div style="padding-left: 10px">
                              <div style="font-size: 12px; color: #bfcde0">开始时间</div>
                              <div style="font-size: 16px; color: #e9deb2; font-weight: bold">{{ d['start_time'] }}</div>
                            </div>
                          </div>
                          <div style="display: flex; margin-top: 10px">
                            <div style="padding-left: 10px">
                              <div style="font-size: 12px; color: #bfcde0">考核时长</div>
                              <div style="font-size: 16px; color: #e9deb2; font-weight: bold">{{ d.duration }}分钟</div>
                            </div>
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>
                  <div class="btnGrade">
                    <div class="startTest" v-if="d.state == 3 && userRole.id != 2" @click="startGrade(d)">开始评分</div>
                    <div v-if="d.state == 4 && userRole.id != 2" class="startTest" :class="[d.state == 4 && userRole.id != 2 ? 'startTestEnd' : '']" @click="startGrade(d)">已评分</div>
                    <div v-if="d.state == 4 && userRole.id == 2" class="startTest" @click="startGrade(d)">查看详情</div>
                  </div>
                </a-col>
                <a-col v-if="searchType != 1" v-for="(d, i) in listData.examSelf" class="card" :xs="{ span: 12, offset: 0 }" :md="{ span: 12, offset: 0 }" :lg="{ span: 12, offset: 0 }" :xl="{ span: 8, offset: 0 }" :xxl="{ span: 6, offset: 0 }">
                  <div class="list relative">
                    <div class="listBg">
                      <img class="imgs" :src="llcy" alt="" />
                      <div class="listLabel" :title="d.title"><span class="listLine"></span>{{ d.title }}</div>
                      <div class="listImg relative">
                        <div style="margin-left: 30px; font-size: 13px; color: #6f7f98"><!--                    <div>总分：{{d.total}}</div>-->&nbsp;</div>
                        <div class="theTitle">
                          <div style="display: flex">

                            <div style="padding-left: 10px">
                              <div style="font-size: 12px; color: #bfcde0">开始时间</div>
                              <div style="font-size: 16px; color: #e9deb2; font-weight: bold">{{ d['start_time'] }}</div>
                            </div>
                          </div>
                          <div style="display: flex; margin-top: 10px">
                            <div style="padding-left: 10px">
                              <div style="font-size: 12px; color: #bfcde0">考核分数</div>
                              <div style="font-size: 16px; color: #e9deb2; font-weight: bold">{{ d.score }}分</div>
                            </div>
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>
                  <div class="btnGrade">
                    <div class="startTest" @click="golist(d)">查看详情</div>
                  </div>
                </a-col>
              </a-row>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'StartGrade'
}
</script>

<script setup>
import { FormOutlined, PlusOutlined, createFromIconfontCN,DeleteOutlined } from '@ant-design/icons-vue'
import knowledgeTabel from './js/knowledgeTabel'
import useQuestionBank from '../../questionBank/js/useQuestionBank'
import nomore from '../../../../../../components/nomore/nomore.vue'
import { ref, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import llcy from '../../../../../../assets/HJJ/test/llcy.png'

const IconFont = createFromIconfontCN({
  scriptUrl: window.iconUrl
})
const router = useRouter()
const route = useRoute()
onMounted(() => {
  testPaper()
})
const searchType = ref('0')
const skipDetails = e => {
  router.push({
    path: route.matched[4].path + '/addTest',
    query: {
      id: e.id
    }
  })
}
const startGrade = e => {
  router.push({
    path: route.matched[4].path + '/startGrade',
    query: {
      id: e.id,
      state: e.state
    }
  })
}
const golist = e => {
  router.push({
    path: route.matched[4].path + '/studentGradeDetails',
    query: {
      id: e.examId
    }
  })
}
const { listData, takeNew, userRole, testPaper , deleteTest} = knowledgeTabel()
</script>

<style lang="less" scoped>
.deleteIcon{
  position: absolute;
  top: 5px;
  right: 5px;
  z-index: 9;
  font-size: 18px;
}
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
  .list{
    /*height: 260px;*/
    min-width:222px;
    overflow: hidden;
    margin:10px;
    transition: all 0.5s;
    z-index: 1;
    border: 1px solid #2c3b5a;
    padding: 1px;
  }
  .card{
    margin-bottom: 20px;
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
  .startTestEnd{
    background-image: url("../../../../../../assets/HJ/basicTheory/test/khEnd.png");
    color: #94a0b6;
  }

  .btnGrade{
    margin: 0 10px;
    padding-top: 4px;
    font-size: 15px;
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
    background: url('../../../../../../assets/HJ/train/nomore.png') no-repeat 100% ;
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
  .HJJ,.HJ{
    .card:hover .list{
      border: 1px solid transparent;
    }
    .card:hover .listImg{
      background-image: linear-gradient(0deg,rgba(163,114,29,0.05) ,transparent);
    }
    .card:hover .list::before {
      transform:translate3d(0,0,0);
    }
  }
  .HJ{
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
    .startTest{
      height: 34px;
      width: 122px;
      margin: 0 auto;
      background-image: url("../../../../../../assets/HJ/basicTheory/test/kh.png");
      background-size: 100% ;
      text-align: center;
      line-height: 34px;
      font-weight: 600;
      transition: all 0.5s;
      cursor: pointer;
      color: #c6e5ff;
    }
    .card:hover .startTest{
      background-image: url("../../../../../../assets/HJ/basicTheory/test/khActive.png");
      color: #fff4e3;
    }
  }
  .HJJ{
    .listLabel{
      height: 40px;
      margin: auto 0;
      position: relative;
      background: #394d66;
      font-size: 18px;
      font-weight: bold;
      line-height: 19px;
      padding-right: 90px;
      overflow: hidden;
      white-space: nowrap;
      text-overflow: ellipsis;
    }
    .listImg{
      height: calc(100% - 40px - 40px);
      width: 100%;
      background-repeat: no-repeat;
      background: #394d66;
      .infoimg{
        width: 100%;
        height: 100%;
      }
    }
    .startTest{
      height: 34px;
      width: 122px;
      margin: 0 auto;
      background-image: url("../../../../../../assets/HJJ/basicTheory/test/kh.png");
      background-size: 100% ;
      text-align: center;
      line-height: 34px;
      font-weight: 600;
      transition: all 0.5s;
      cursor: pointer;
      color: #c6e5ff;
    }
    .card:hover .startTest{
      background-image: url("../../../../../../assets/HJJ/basicTheory/test/khActive.png");
      color: #fff4e3;
    }
  }
  .LJ{
    .listLabel{
      height: 40px;
      margin: auto 0;
      position: relative;
      font-size: 18px;
      font-weight: bold;
      line-height: 19px;
      padding-right: 90px;
      overflow: hidden;
      white-space: nowrap;
      text-overflow: ellipsis;
    }
    .listImg{
      height: calc(100% - 40px - 40px);
      width: 100%;
      .infoimg{
        width: 100%;
        height: 100%;
      }
    }
    .startTest {
      height: 34px;
      width: 122px;
      margin: 0 auto;
      background-image: url('../../../../../../assets/LJ/basicTheory/test/kh.png');
      background-size: 100%;
      text-align: center;
      line-height: 34px;
      font-size: 15px;
      font-weight: 600;
      transition: all 0.5s;
      cursor: pointer;
      color: white;
    }
    .card:hover .startTest {
      // background-image: url('../../../../../../assets/basicTheory/test/khActive.png');
      color: #fff4e3;
    }
    .startTestEnd {
      background-image: url('../../../../../../assets/LJ/basicTheory/test/khEnd.png');
      color: #333;
    }
    .listBg {
      transition: all 0.5s;
      background: url('../../../../../../assets/LJ/test/testBg.png') no-repeat;
      background-size: 100% 100%;
    }
    .list{
      border: 0px solid transparent;
    }
    .listBg img{
      display: none;
    }
    .listLabel span{
      display: none;

    }
    .listLabel{
      text-align: center;
      padding: 0px;
      line-height: 40px;
    }
  }
  .KJ{
    .listLabel{
      height: 40px;
      margin: auto 0;
      position: relative;
      font-size: 18px;
      font-weight: bold;
      line-height: 19px;
      padding-right: 90px;
      overflow: hidden;
      white-space: nowrap;
      text-overflow: ellipsis;
    }
    .listImg{
      height: calc(100% - 40px - 40px);
      width: 100%;
      .infoimg{
        width: 100%;
        height: 100%;
      }
    }
    .startTest {
      height: 34px;
      width: 122px;
      margin: 0 auto;
      background-image: url('../../../../../../assets/KJ/basicTheory/test/kh.png');
      background-size: 100%;
      text-align: center;
      line-height: 34px;
      font-size: 15px;
      font-weight: 600;
      transition: all 0.5s;
      cursor: pointer;
      color: white;
    }
    .card:hover .startTest {
      // background-image: url('../../../../../../assets/basicTheory/test/khActive.png');
      color: #fff4e3;
    }
    .startTestEnd {
      background-image: url('../../../../../../assets/KJ/basicTheory/test/khEnd.png');
      color: #333;
    }
    .listBg {
      transition: all 0.5s;
      background: url('../../../../../../assets/KJ/test/testBg.png') no-repeat;
      background-size: 100% 100%;
    }
    .list{
      border: 0px solid transparent;
    }
    .listBg img{
      display: none;
    }
    .listLabel span{
      display: none;

    }
    .listLabel{
      text-align: center;
      padding: 0px;
      line-height: 40px;
    }
  }
</style>
