<template>
  <div class="addTestContainer">
    <div class="left" style="position: relative" v-if="userRole.id != 2">
      <div class="top" style="position: absolute; z-index: 99; display: flex; justify-content: space-between; padding: 0 12px">
        <div>学员列表</div>
        <div v-if="examState == 4 || userRole.id == 2" class="analyseBtn" :style="{ fontSize: fs * 1 + 13 + 'px' }">
          <a-button class="layout-center btns" @click="analyseVisible = true" style="padding: 0 10px"><LineChartOutlined />考核分析</a-button>
        </div>
      </div>
      <div style="position: absolute; height: calc(100% - 58px); top: 45px; width: 100%">
        <div class="stubox" style="height: calc(100% - 42px); overflow: auto">
          <div class="list" v-for="v of students" :key="v.id" @click="selelctStu(v)" :class="[activeUser.id == v.id ? 'activeUser' : '']">
            <div>
              <img :src="fileUrl + v.user_img" alt="" />
            </div>
            <div class="userInfo">
              <div>{{ v.userName }}</div>
              <div>{{ v.wkno }}</div>
            </div>
            <div class="score">
              {{ v.score }}
            </div>
          </div>
        </div>
        <div v-if="examState != 4" class="gradeEnd btn-animate btn-animate-orange" @click="endGrade">{{ userRole.id != 2 ? '结束阅卷' : '退出' }}</div>
      </div>
    </div>
    <div class="right">
      <div class="top" v-if="testExam">
        <div class="text">参考人数: {{ testExam.userLen }}人</div>
        <div class="splitLine">/</div>
        <div class="text">监考人: {{ testExam.userName }}</div>
        <div class="splitLine">/</div>
        <div class="text">开始时间: {{ testExam.start_time }}</div>
        <div class="splitLine">/</div>
        <div class="text">结束时间: {{ testExam.end_time }}</div>
        <div class="splitLine">/</div>
        <div class="text">考核名称: {{ testExam.title }}</div>
      </div>
      <div class="bottom">
        <div class="bottomLeft">
          <perviewTest v-if="questions && isShow" :time="testExam.duration" :paperData="questions" :isGarde="true" :height="'100%'" :clearAnswer="false" :isAnswer="true"></perviewTest>
        </div>
        <div class="bottomRight">
          <div class="studentInfo">
            <div class="photobg">
              <div style="border: 1px solid #354971; padding: 2px">
                <img v-if="activeUser" :src="fileUrl + activeUser.user_img" alt="" style="height: 92px; width: 80px" />
              </div>
            </div>
            <div class="infoText" style="padding-top: 20px">
              <div class="iconfont">
                <IconFont type="icon-xingming" style="font-size: 20px; padding-top: 0"></IconFont>
              </div>
              <div>
                <div>姓名</div>
                <div v-if="activeUser" style="color: #e2f2ff">{{ activeUser.userName }}</div>
              </div>
            </div>
            <div class="infoText">
              <div class="iconfont">
                <IconFont type="icon-zhengjianhaoma" style="font-size: 20px; padding-top: 0"></IconFont>
              </div>
              <div>
                <div>证件号码</div>
                <div v-if="activeUser" style="color: #e2f2ff">{{ activeUser.wkno ? activeUser.wkno : '暂无' }}</div>
              </div>
            </div>
            <div class="infoText">
              <div class="iconfont">
                <IconFont type="icon-shichang" style="font-size: 20px; padding-top: 0"></IconFont>
              </div>
              <div>
                <div>考试时长</div>
                <div v-if="testExam" style="color: #e2f2ff">{{ testExam.testime }} 分钟</div>
              </div>
            </div>
          </div>
          <div style="padding-bottom: 20px">
            <div style="color: #727881; padding-left: 6px">
              <span style="display: inline-block; width: 40px; border-bottom: 1px solid #263657; margin-bottom: 4px; margin-right: 4px"></span>
              得分：<span v-if="activeUser" style="font-size: 24px; color: #e9deb2">{{ activeUser.score }}分 </span><span style="display: inline-block; width: 40px; border-bottom: 1px solid #263657; margin-bottom: 4px; margin-left: 4px"></span>
            </div>
            <div class="commitScore" @click="commitScore" v-if="userRole.id != 2 && examState != 4">提交分数</div>
            <div class="commitScore layout-center" @click="goback" v-else-if="examState == 4 || userRole.id == 2"><IconFont type="icon-fanhui1" style="font-size: 20px; padding-right: 10px"></IconFont> 返回</div>
            <!--            <div class="computerScore">-->
            <!--              <IconFont type="icon-diannao" style="font-size: 28px;" ></IconFont>-->
            <!--              机评分：<a-input-number />-->
            <!--            </div>-->
          </div>
        </div>
      </div>
    </div>
    <div class="achievementMasking overflow-auto" v-if="analyseVisible&&analyseData.scoreList">
      <div class="achievement">
        <test-analyse :analyseData="analyseData"></test-analyse>
        <div class="resClose">
          <div class="closeInfo">
            <div class="close" @click="closeModel"></div>
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
import { LineChartOutlined, createFromIconfontCN } from '@ant-design/icons-vue'
import { onMounted, ref } from 'vue'
import startGrade from './js/startGrade'
import perviewTest from '../../../../../../components/test/perviewTest/perviewTest.vue'
import testAnalyse from '../../../../../../components/test/testAnalyse/testAnalyse.vue'
const IconFont = createFromIconfontCN({
  scriptUrl: window.iconUrl
})
const fs = ref(JSON.parse(localStorage.getItem('fs')))
const analyseVisible = ref(false)
const closeModel = ()=>{
  analyseVisible.value = false
}
onMounted(() => {
  initPaper()
})
const { initPaper, questions, students, fileUrl, examState, isShow, activeUser, userRole, testExam, analyseData, endGrade, goback, commitScore, selelctStu, isInvigilator } = startGrade()
</script>

<style lang="less" scoped>
  .HJ{
    .addTestContainer{
      width: 100%;
      height: 100%;
      box-sizing: border-box;
      display: flex;
    }
    .left{
      width: 280px;
      height: 100%;
      background: rgba(24,45,86,0.7) ;
      margin-right: 10px;
      display: flex;
      flex-direction: column;
      padding-bottom: 20px;
      align-items: center;
      .top{
        /*background: #0a1429;*/
        background-image: url("../../../../../../assets/HJ/test/addTop.png");
        width: 100%;
        background-repeat: no-repeat;
        height: 40px;
        color: #70b9ec;
        text-align: center;
        font-weight: bold;
        margin-top: 8px;
      }
      .stubox{
        flex: 1;
        width: 100%;
        height: calc(100% - 42px);
        .list{
          height: 88px;
          width: 100%;
          background: #0e1c38;
          margin-bottom: 1px;
          position: relative;
          padding: 0px 10px;
          display: flex;
          align-items: center;
          cursor: pointer;
          img{
            height: 54px;
            width: 54px;
          }
          .userInfo{
            padding-left: 10px;
            color: #a5b6d0;
            font-weight:550;
            flex: 1;
          }
          .score{
            width: 66px;
            height: 58px;
            background-image: url("../../../../../../assets/HJ/basicTheory/test/score.png");
            position: absolute;
            right: 10px;
            top: 0;
            text-align: center;
            line-height: 45px;
            font-weight: bold;
            font-size: 23px;
            color: #536f9e;
          }
        }
        .list:hover{
          background-image: linear-gradient(to bottom,#0f1d39,#1b3d68);
        }
        .list:hover .userInfo{
          color: #c4dafb;
        }
        .list:hover .score{
          background-image: url("../../../../../../assets/HJ/basicTheory/test/scoreHover.png");
          color: #93ceff;
        }
        .activeUser{
          background-image: linear-gradient(to bottom,#0f1d39,#1b3d68);
          .score{
            background-image: url("../../../../../../assets/HJ/basicTheory/test/scoreHover.png");
            color: #93ceff;
          }
        }
      }
      .gradeEnd{
        cursor: pointer;
        width: 260px;
        height: 42px;
        margin: 0 auto;
        background-image: url("../../../../../../assets/HJ/basicTheory/test/gardeEnd.png");
        font-size: 22px;
        font-weight: bold;
        line-height: 38px;
        padding-left: 50px;
        text-shadow: 2px 2px 2px #ad5e24;
      }
    }
    .right{
      width: calc(100% - 280px - 10px);
      flex: 1;
      height: 100%;
      background: rgba(24,45,86,0.7) ;
      .top{
        background: #25456d;
        min-height: 48px;
        width: 100%;
        display: flex;
        align-items: center;
        justify-content: space-between;
        color: #c4dafb;
        line-height: 26px;
        padding: 4px 12px;
        .splitLine{
          padding: 0 12px;
          color: #424f63
        }
      }
      .bottom{
        display: flex;
        width: 100%;
        height: calc(100% - 60px);
        .bottomLeft{
          flex: 1;
        }
        .bottomRight{
          width: 200px;
          border-left: 1px solid #263757;
          display: flex;
          flex-direction: column;
          justify-content: space-between;
          .studentInfo{
            padding: 20px 5px;
            display: flex;
            flex-direction: column;
            justify-content: center;
            align-items: center;
            .photobg{
              width: 130px;
              height: 104px;
              display: flex;
              align-items: center;
              justify-content: center;
              background-image: url("../../../../../../assets/HJ/basicTheory/test/photoBg.png");
            }
          }
          .infoText{
            display: flex;
            padding-top: 5px;
            width: 100%;
            color:#a5b6d0;
            .iconfont{
              padding-right: 10px;
              display: flex;
              align-items: flex-start;
              padding-top:2px ;
            }
          }
          .commitScore{
            width: 160px;
            height: 32px;
            background-image: linear-gradient(to bottom,#00ddf9,#00669e);
            color: #ffffff;
            font-size: 15px;
            text-align: center;
            line-height: 32px;
            margin: 15px 20px 0 20px;
            cursor: pointer;


          }
          .computerScore{
            height: 40px;
            margin: 20px 10px 10px 0;
            text-align: right;
          }
        }
      }
    }
    .achievementMasking {
      display: flex;
      align-items: center;
      justify-content: center;
      background-color: rgba(3, 19, 47, .9);
      position: fixed;
      top: 0;
      left: 0;
      right: 0;
      bottom: 0;
      z-index: 999;
      .achievement {
        min-width: 1024px;
        width: 100%;
        position: relative;
        padding-top: 1px;
        .resClose {
          width: 100%;
          height: 100px;
          display: flex;
          justify-content: center;
          align-items: center;
          position: absolute;
          bottom: -32px;
          .close {
            width: 31px;
            height: 31px;
            border: 2px solid #7b90af;
            border-radius: 50%;
            background: url("../../../../../../assets/HJ/ico/ico-clear.png") no-repeat center;
            cursor: pointer;
            position: relative;
            &:hover {
              border-color: #6ebdff;
              background: url("../../../../../../assets/HJ/ico/ico-clear-1.png") no-repeat center;
              animation: rotate 0.4s linear;
            }
          }
          .closeInfo {
            position: relative;
            &:before {
              content: '';
              width: 1px;
              height: 30px;
              background: url("../../../../../../assets/HJ/ico/ico-lone.png");
              position: absolute;
              top: -32px;
              left: 15px;
            }
          }
        }
      }
    }
  }
  .HJJ{
    .addTestContainer {
      width: 100%;
      height: 100%;
      box-sizing: border-box;
      display: flex;
    }
    .left {
      width: 280px;
      height: 100%;
      background: rgba(23, 31, 41, 0.7);
      margin-right: 10px;
      display: flex;
      flex-direction: column;
      padding-bottom: 20px;
      align-items: center;
      .top {
        /*background: #0a1429;*/
        background-image: url('../../../../../../assets/HJJ/test/addTop.png');
        background-size: 100% 100%;
        width: 100%;
        background-repeat: no-repeat;
        height: 40px;
        color: #bfcde0;
        text-align: center;
        font-weight: bold;
        font-size: 20px;
      }
      .stubox {
        flex: 1;
        width: 100%;
        height: calc(100% - 42px);
        .list {
          height: 60px;
          width: 100%;
          background: rgba(76, 117, 149, 0.2);
          border: 1px solid #364555;
          margin-bottom: 5px;
          position: relative;
          padding: 0px 10px;
          display: flex;
          align-items: center;
          cursor: pointer;
          img {
            border: 1px solid #354971;
            height: 45px;
            width: 42px;
          }
          .userInfo {
            padding-left: 10px;
            color: #768aa9;
            font-weight: 550;
            flex: 1;
            padding-top: 20px;
          }
          .score {
            width: 86px;
            height: 25px;
            background-image: url('../../../../../../assets/HJJ/basicTheory/test/score.png');
            position: absolute;
            left: 62px;
            top: 5px;
            text-align: center;
            line-height: 25px;
            font-weight: bold;
            font-size: 20px;
            color: #bfcde0;
          }
        }
        .list:hover {
          border: 1px solid #afa48a;
        }
        .list:hover .userInfo {
          color: #c4dafb;
        }
        .list:hover .score {
          background-image: url('../../../../../../assets/HJJ/basicTheory/test/scoreHover.png');
          color: #ffffff;
        }
        .activeUser {
          border: 1px solid #afa48a;
          .score {
            background-image: url('../../../../../../assets/HJJ/basicTheory/test/scoreHover.png');
            color: #ffffff;
          }
        }
      }
      .gradeEnd {
        cursor: pointer;
        width: 260px;
        height: 42px;
        margin: 0 auto;
        background-image: url('../../../../../../assets/HJJ/basicTheory/test/gardeEnd.png');
        font-size: 22px;
        font-weight: bold;
        line-height: 38px;
        padding-left: 50px;
        text-shadow: 2px 2px 2px #ad5e24;
      }
    }
    .right {
      width: calc(100% - 280px - 10px);
      flex: 1;
      height: 100%;
      background: rgba(23, 31, 41, 0.7);
      .top {
        background: #2a3b4b;
        height: 35px;
        width: 100%;
        display: flex;
        color: #c4dafb;
        line-height: 35px;
        padding-left: 20px;
        .splitLine {
          padding: 0 2%;
          color: #424f63;
        }
        .text {
          flex-shrink: 0;
        }
      }
      .bottom {
        display: flex;
        width: 100%;
        height: calc(100% - 35px);
        .bottomLeft {
          flex: 1;
        }
        .bottomRight {
          width: 200px;
          border-left: 1px solid #263757;
          display: flex;
          flex-direction: column;
          justify-content: space-between;
          .studentInfo {
            padding: 20px 5px;
            display: flex;
            flex-direction: column;
            justify-content: center;
            align-items: center;
            .photobg {
              width: 130px;
              height: 104px;
              display: flex;
              align-items: center;
              justify-content: center;
              background-image: url('../../../../../../assets/HJJ/basicTheory/test/photoBg.png');
            }
          }
          .infoText {
            display: flex;
            padding-top: 5px;
            width: 100%;
            color: #768ba9;
            .iconfont {
              padding-right: 10px;
              display: flex;
              align-items: flex-start;
              padding-top: 2px;
            }
          }
          .commitScore {
            width: 160px;
            height: 32px;
            background-image: url('../../../../../../assets/HJJ/basicTheory/test/commitBtn.png');
            color: #e9deb2;
            font-size: 18px;
            text-align: center;
            font-weight: bold;
            line-height: 32px;
            margin: 15px 20px 0 20px;
            cursor: pointer;
          }
          .computerScore {
            height: 40px;
            margin: 20px 10px 10px 0;
            text-align: right;
          }
        }
      }
    }
    .achievementMasking {
      display: flex;
      align-items: center;
      justify-content: center;
      background-color: rgba(23, 34, 41, 0.9);
      position: fixed;
      top: 0;
      left: 0;
      right: 0;
      bottom: 0;
      z-index: 999;
      .achievement {
        min-width: 1024px;
        width: 100%;
        position: relative;
        padding-top: 1px;
        .resClose {
          width: 100%;
          height: 100px;
          display: flex;
          justify-content: center;
          align-items: center;
          position: absolute;
          bottom: -32px;
          .close {
            width: 31px;
            height: 31px;
            border: 2px solid #7b90af;
            border-radius: 50%;
            background: url('../../../../../../assets/HJJ/ico/ico-clear.png') no-repeat center;
            cursor: pointer;
            position: relative;
            &:hover {
              border-color: #6ebdff;
              background: url('../../../../../../assets/HJJ/ico/ico-clear-1.png') no-repeat center;
              animation: rotate 0.4s linear;
            }
          }
          .closeInfo {
            position: relative;
            &:before {
              content: '';
              width: 1px;
              height: 30px;
              background: url('../../../../../../assets/HJJ/ico/ico-lone.png');
              position: absolute;
              top: -32px;
              left: 15px;
            }
          }
        }
      }
    }
  }
  .LJ{
    .addTestContainer{
      width: 100%;
      height: 100%;
      box-sizing: border-box;
      display: flex;
    }
    .left{
      width: 280px;
      height: 100%;
      background: rgba(38,41,36,0.3) ;
      margin-right: 10px;
      display: flex;
      flex-direction: column;
      padding-bottom: 20px;
      align-items: center;
      .top{
        /*background: #0a1429;*/
        background-image: url("../../../../../../assets/LJ/test/addTop.png");
        background-size: 100% 100%;
        width: 100%;
        background-repeat: no-repeat;
        height: 40px;
        color: #fff;
        text-align: center;
        font-weight: bold;
        font-size: 20px;
      }
      .stubox{
        flex: 1;
        width: 100%;
        height: calc(100% - 42px);
        .list{
          height: 60px;
          width: 100%;
          background: rgba(53, 61, 58, 0.3);
          border: 1px solid #384a42;
          color: #a9abaa;
          margin-bottom: 5px;
          position: relative;
          padding: 0px 10px;
          display: flex;
          align-items: center;
          cursor: pointer;
          img{
            border: 1px solid #26332e;
            height: 45px;
            width: 42px;
          }
          .userInfo{
            padding-left: 10px;
            color: #a9abaa;
            font-weight:550;
            flex: 1;
            padding-top: 20px;
          }
          .score{
            width: 86px;
            height: 25px;
            background-image: url("../../../../../../assets/LJ/basicTheory/test/score.png");
            position: absolute;
            left: 62px;
            top: 5px;
            text-align: center;
            line-height: 25px;
            font-weight: bold;
            font-size: 20px;
            color: #a9abaa;
          }
        }
        .list:hover{
          border: 1px solid #afa48a;
        }
        .list:hover .userInfo{
          color: #a9abaa;
        }
        .list:hover .score{
          background-image: url("../../../../../../assets/LJ/basicTheory/test/scoreHover.png");
          color: #a9abaa;
        }
        .activeUser{
          border: 1px solid #afa48a;
          .score{
            background-image: url("../../../../../../assets/LJ/basicTheory/test/scoreHover.png");
            color: #a9abaa;
          }
        }
      }
      .gradeEnd{
        cursor: pointer;
        width: 260px;
        height: 42px;
        margin: 0 auto;
        background: url("../../../../../../assets/LJ/test/newBottom.png") no-repeat 100%;
        font-size: 22px;
        font-weight: bold;
        line-height: 38px;
        text-align: center;
        text-shadow: 2px 2px 2px #ad5e24;
      }
    }
    .right{
      width: calc(100% - 280px - 10px);
      flex: 1;
      height: 100%;
      background: rgba(38,41,36,0.3) ;
      .top{
        background: #353d3a;
        height: 35px;
        width: 100%;
        display: flex;
        color: #a9abaa;
        line-height: 35px;
        padding-left: 20px;
        .splitLine{
          padding: 0 2%;
          color: #424f63
        }
        .text {
          flex-shrink: 0;
        }
      }
      .bottom{
        display: flex;
        width: 100%;
        height: calc(100% - 35px);
        .bottomLeft{
          flex: 1;
        }
        .bottomRight{
          width: 200px;
          border-left: 1px solid #26332e;
          display: flex;
          flex-direction: column;
          justify-content: space-between;
          .studentInfo{
            padding: 20px 5px;
            display: flex;
            flex-direction: column;
            justify-content: center;
            align-items: center;
            .photobg{
              width: 130px;
              height: 104px;
              display: flex;
              align-items: center;
              justify-content: center;
              background-image: url("../../../../../../assets/LJ/basicTheory/test/photoBg.png");
            }
          }
          .infoText{
            display: flex;
            padding-top: 5px;
            width: 100%;
            color:#a9abaa;
            .iconfont{
              padding-right: 10px;
              display: flex;
              align-items: flex-start;
              padding-top:2px ;
            }
          }
          .commitScore{
            width: 160px;
            height: 32px;
            background-image: url("../../../../../../assets/LJ/basicTheory/test/commitBtn.png");
            color: #e9deb2;
            font-size: 18px;
            text-align: center;
            font-weight: bold;
            line-height: 32px;
            margin: 15px 20px 0 20px;
            cursor: pointer;
          }
          .computerScore{
            height: 40px;
            margin: 20px 10px 10px 0;
            text-align: right;
          }
        }
      }
    }
    .achievementMasking {
      display: flex;
      align-items: center;
      justify-content: center;
      background-color: rgb(10 17 14 / 0.8);
      position: fixed;
      top: 0;
      left: 0;
      right: 0;
      bottom: 0;
      z-index: 999;
      .achievement {
        min-width: 1024px;
        width: 100%;
        position: relative;
        padding-top: 1px;
        .resClose {
          width: 100%;
          height: 100px;
          display: flex;
          justify-content: center;
          align-items: center;
          position: absolute;
          bottom: -32px;
          .close {
            width: 31px;
            height: 31px;
            border: 2px solid #a9abaa;
            border-radius: 50%;
            background: url("../../../../../../assets/LJ/ico/ico-clear.png") no-repeat center;
            cursor: pointer;
            position: relative;
            &:hover {
              border-color: #34b34c;
              background: url("../../../../../../assets/LJ/ico/ico-clear-1.png") no-repeat center;
              animation: rotate 0.4s linear;
            }
          }
          .closeInfo {
            position: relative;
            &:before {
              content: '';
              width: 1px;
              height: 30px;
              background: url("../../../../../../assets/LJ/ico/ico-lone.png");
              position: absolute;
              top: -32px;
              left: 15px;
            }
          }
        }
      }
    }
  }
  .KJ{
    .addTestContainer{
      width: 100%;
      height: 100%;
      box-sizing: border-box;
      display: flex;
    }
    .left{
      width: 280px;
      height: 100%;
      background:rgba(31, 67, 99, 0.3) ;
      margin-right: 10px;
      display: flex;
      flex-direction: column;
      padding-bottom: 20px;
      align-items: center;
      .top{
        /*background: #0a1429;*/
        background-image: url("../../../../../../assets/KJ/test/addTop.png");
        background-size: 100% 100%;
        width: 100%;
        background-repeat: no-repeat;
        height: 40px;
        color: #fff;
        text-align: center;
        font-weight: bold;
        font-size: 20px;
      }
      .stubox{
        flex: 1;
        width: 100%;
        height: calc(100% - 42px);
        .list{
          height: 60px;
          width: 100%;
          background: rgba(53, 61, 58, 0.3);
          border: 1px solid #384a42;
          color: #a9abaa;
          margin-bottom: 5px;
          position: relative;
          padding: 0px 10px;
          display: flex;
          align-items: center;
          cursor: pointer;
          img{
            border: 1px solid #26332e;
            height: 45px;
            width: 42px;
          }
          .userInfo{
            padding-left: 10px;
            color: #a9abaa;
            font-weight:550;
            flex: 1;
            padding-top: 20px;
          }
          .score{
            width: 86px;
            height: 25px;
            background-image: url("../../../../../../assets/KJ/basicTheory/test/score.png");
            position: absolute;
            left: 62px;
            top: 5px;
            text-align: center;
            line-height: 25px;
            font-weight: bold;
            font-size: 20px;
            color: #a9abaa;
          }
        }
        .list:hover{
          border: 1px solid #afa48a;
        }
        .list:hover .userInfo{
          color: #a9abaa;
        }
        .list:hover .score{
          background-image: url("../../../../../../assets/KJ/basicTheory/test/scoreHover.png");
          color: #a9abaa;
        }
        .activeUser{
          border: 1px solid #afa48a;
          .score{
            background-image: url("../../../../../../assets/KJ/basicTheory/test/scoreHover.png");
            color: #a9abaa;
          }
        }
      }
      .gradeEnd{
        cursor: pointer;
        width: 260px;
        height: 42px;
        margin: 0 auto;
        background: url("../../../../../../assets/KJ/test/newBottom.png") no-repeat 100%;
        font-size: 22px;
        font-weight: bold;
        line-height: 38px;
        text-align: center;
        text-shadow: 2px 2px 2px #ad5e24;
      }
    }
    .right{
      width: calc(100% - 280px - 10px);
      flex: 1;
      height: 100%;
      background: rgba(31, 67, 99, 0.3) ;
      .top{
        background: rgba(80,141,230,0.6);
        height: 35px;
        width: 100%;
        display: flex;
        color: #ffffff;
        line-height: 35px;
        padding-left: 20px;
        .splitLine{
          padding: 0 2%;
          color: #424f63
        }
        .text {
          flex-shrink: 0;
        }
      }
      .bottom{
        display: flex;
        width: 100%;
        height: calc(100% - 35px);
        .bottomLeft{
          flex: 1;
        }
        .bottomRight{
          width: 200px;
          border-left: 1px solid #26332e;
          display: flex;
          flex-direction: column;
          justify-content: space-between;
          .studentInfo{
            padding: 20px 5px;
            display: flex;
            flex-direction: column;
            justify-content: center;
            align-items: center;
            .photobg{
              width: 130px;
              height: 104px;
              display: flex;
              align-items: center;
              justify-content: center;
              background-image: url("../../../../../../assets/KJ/basicTheory/test/photoBg.png");
            }
          }
          .infoText{
            display: flex;
            padding-top: 5px;
            width: 100%;
            color:#a9abaa;
            .iconfont{
              padding-right: 10px;
              display: flex;
              align-items: flex-start;
              padding-top:2px ;
            }
          }
          .commitScore{
            width: 160px;
            height: 32px;
            background-image: url("../../../../../../assets/KJ/basicTheory/test/commitBtn.png");
            color: #e9deb2;
            font-size: 18px;
            text-align: center;
            font-weight: bold;
            line-height: 32px;
            margin: 15px 20px 0 20px;
            cursor: pointer;
          }
          .computerScore{
            height: 40px;
            margin: 20px 10px 10px 0;
            text-align: right;
          }
        }
      }
    }
    .achievementMasking {
      display: flex;
      align-items: center;
      justify-content: center;
      background-color: rgb(10 17 14 / 0.8);
      position: fixed;
      top: 0;
      left: 0;
      right: 0;
      bottom: 0;
      z-index: 999;
      .achievement {
        min-width: 1024px;
        width: 100%;
        position: relative;
        padding-top: 1px;
        .resClose {
          width: 100%;
          height: 100px;
          display: flex;
          justify-content: center;
          align-items: center;
          position: absolute;
          bottom: -32px;
          .close {
            width: 31px;
            height: 31px;
            border: 2px solid #a9abaa;
            border-radius: 50%;
            background: url("../../../../../../assets/KJ/ico/ico-clear.png") no-repeat center;
            cursor: pointer;
            position: relative;
            &:hover {
              border-color: #34b34c;
              background: url("../../../../../../assets/KJ/ico/ico-clear-1.png") no-repeat center;
              animation: rotate 0.4s linear;
            }
          }
          .closeInfo {
            position: relative;
            &:before {
              content: '';
              width: 1px;
              height: 30px;
              background: url("../../../../../../assets/KJ/ico/ico-lone.png");
              position: absolute;
              top: -32px;
              left: 15px;
            }
          }
        }
      }
    }
  }
  @media (max-width: 1200px) {
    .left {
      width: 240px;
    }
    .left .gradeEnd {
      width: 220px;
      background-size: 100% 100%;
      font-size: 20px;
    }
  }
</style>
