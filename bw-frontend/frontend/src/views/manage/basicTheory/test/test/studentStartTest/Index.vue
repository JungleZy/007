<template>
  <div style="height: 100%; width: 100%; display: flex">
    <div class="left">
      <div class="line"  v-if="interfaceStyle==='HJ'">
        <div style="width: 100%;height: 8px;background: #253554"></div>
      </div>
      <div class="cutDown">
        <div class="text">距离考试结束还有</div>
        <count-down class="width-100-per layout-center" color="#70c9ff" ref="countDown" style="height: 55px" />
      </div>
      <div class="line"  v-if="interfaceStyle==='HJ'">
        <div style="width: 100%;height: 8px;background: #253554"></div>
      </div>
      <!--      userRole.id==='2'-->
      <div style="height: calc(100% - 157px)">
        <div class="stuInfo">学员信息</div>
        <div class="studentDetail">
          <img :src="fileUrl + userInfo.userImg" alt="" style="width: 80px" />
          <div style="height: 100px; flex: 1; padding-left: 10px">
            <div style="height: 50px; line-height: 50px"><span>姓名: </span>{{ userInfo.userName }}</div>
            <div style="height: 50px"><span>证件号: </span>{{ userInfo.idCard }}</div>
          </div>
        </div>
        <div class="question">
          <div v-if="questions" v-for="v of bankList">
            <div class="questionTitle">
              <div style="display: flex">
                <div class="quesTypebg" style="height: 100%; line-height: 30px; padding: 0 30px 0 10px">{{ v.name }}</div>
                <div class="triangle"></div>
              </div>
              <div style="font-weight: bold; line-height: 35px">{{ questions[v.key].length }}题</div>
            </div>
            <div style="display: flex; flex-wrap: wrap; padding: 10px 0px">
              <div class="questionItem" :class="[item.isAnswer ? 'questionItemActive' : '']" v-for="(item, index) of questions[v.key]">{{ index + 1 }}</div>
              <!--            <div class="questionItem questionItemActive">2</div>-->
            </div>
          </div>
        </div>
        <div class="commit btn-animate btn-animate-orange" @click="commitTest">交卷评分</div>
      </div>
    </div>
    <div class="right grouping">
      <div class="w-full grouping_halving_line"></div>
      <StudentPerviewTest v-if="questions && isShow" :time="testTime" :paperData="questions" :height="'100%'" :clearAnswer="true" :isAnswer="true"></StudentPerviewTest>
    </div>
  </div>
</template>

<script>
export default {
  name: 'Index'
}
</script>
<script setup>
import CountDown from '../../../../../../components/common/CountDown.vue'
import StudentPerviewTest from '../../../../../../components/test/studentPerviewTest/StudentPerviewTest.vue'
import { ref, onMounted } from 'vue'
import startTest from './js/startTest'
const countDown = ref('')
const interfaceStyle = window.interfaceStyle
const { userInfo, fileUrl, questions, bankList, userList, userRole, testTime, active, isShow, clearAnswer, commitTest, getStudentInfo } = startTest(countDown)
</script>

<style lang="less" scoped>
  .HJ{
    .left{
      width: 280px;
      height: 100%;
      margin-right: 10px;
      background: rgba(24,45,86,0.7) ;
      display: flex;
      flex-direction: column;
      .cutDown{
        height: 156px;
        padding: 24px 8px;
        background: url("../../../../../../assets/HJ/train/train-time-bg.jpg") no-repeat center bottom;
        .text{
          text-align: center;
          color: #5e80b2;
          font-size: 15px;
          padding-bottom: 15px;
        }
      }
      .stuInfo{
        background: url("../../../../../../assets/HJ/test/addTop.png");
        width: 100%;
        background-repeat: no-repeat;
        height: 40px;
        z-index: 99;
        color: #70b9ec;
        line-height: 40px;
        text-align: center;
        font-weight: bold;
      }
      .studentDetail{
        display: flex;
        padding: 10px;
        border-bottom:1px solid rgba(123, 152, 180, 0.2);
      }
      .questionItem{
        height: 30px;
        width: 30px;
        border: 1px solid #354971;
        text-align: center;
        line-height: 30px;
        font-weight: bold;
        color: #a5b6d0;
        margin: 5px;
        background-image: linear-gradient(#192d4e,#17243d);
      }
      .questionItemActive{
        color: #eab110;
        border: 1px solid #eab110;
      }
      .questionTitle{
        display: flex;
        justify-content: space-between;
        border-bottom: 1px solid rgba(123, 152, 180, 0.2);
        height: 30px
      }
      .question{
        flex: 1;
        overflow: auto;
        height: calc(100% - 230px);
        padding: 10px;
        .quesTypebg{
          background:  rgba(110,189,255,.2);
        }
        .triangle{
          border-bottom: 0px solid transparent;
          border-top: 29px solid transparent;
          border-left: 10px solid  rgba(110,189,255,.2);
          border-right: 0px solid transparent;

        }
      }
      .commit{
        height: 40px;
        width: 260px;
        margin-left: 10px;
        text-align: center;
        line-height: 40px;
        font-weight: 700;
        font-size: 20px;
        padding-right: 40px;
        cursor: pointer;
        text-shadow: 2px 2px 2px #ad5e24;
        background: url("../../../../../../assets/HJ/test/bottom.png") no-repeat 100%;
      }
    }
    .line{
      padding: 0 2px;
      border-right: 2px solid #a2a887;
      border-left: 2px solid #a2a887;
    }
    .right{
      flex: 1;
      height: 100%;
      background: rgba(24,45,86,0.7) ;
    }
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
        border: 1px solid #354971;
        height: 58px;
        width: 54px;
      }
      .userInfo{
        padding-left: 10px;
        color: #a5b6d0;
        font-weight:600;
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
        font-size: 18px;
        color: #37465f;
      }
    }
    .list:hover{
      background: linear-gradient(to bottom,#0f1d39,#213a6d);
    }
    .list:hover .userInfo{
      color: #c4dafb;
    }
    .listTwo{
      background: linear-gradient(to bottom,#0f1d39,#213a6d);
    }
    .list:hover .score{
      background-image: url("../../../../../../assets/HJ/basicTheory/test/scoreHover.png");
      color: #93ceff;
    }
  }
  .HJJ{
    .left {
      width: 280px;
      height: 100%;
      margin-right: 10px;
      background: rgba(23, 31, 41, 0.7);
      display: flex;
      flex-direction: column;
      .cutDown {
        height: 186px;
        padding: 24px 8px;
        background: url('../../../../../../assets/HJJ/train/train-time-bg.png') no-repeat center bottom;
        .text {
          text-align: center;
          color: #bfcde0;
          font-size: 15px;
          padding-bottom: 15px;
        }
      }
      .stuInfo {
        background: url('../../../../../../assets/HJJ/test/addTop.png');
        width: 100%;
        background-repeat: no-repeat;
        height: 40px;
        z-index: 99;
        color: #bfcde0;
        text-align: center;
        font-weight: bold;
        font-size: 20px;
      }
      .studentDetail {
        display: flex;
        padding: 10px;
        border-bottom: 1px solid rgba(123, 152, 180, 0.2);
      }
      .questionItem {
        height: 30px;
        width: 30px;
        border: 1px solid #354971;
        text-align: center;
        line-height: 30px;
        font-weight: bold;
        color: #768baa;
        margin: 5px;
        background-image: linear-gradient(#192d4e, #17243d);
      }
      .questionItemActive {
        color: #eab110;
        border: 1px solid #eab110;
      }
      .questionTitle {
        display: flex;
        justify-content: space-between;
        border-bottom: 1px solid rgba(123, 152, 180, 0.2);
        height: 30px;
      }
      .question {
        flex: 1;
        overflow: auto;
        height: calc(100% - 230px);
        padding: 10px;
        .quesTypebg{
          background: rgba(110, 189, 255, 0.2);
        }
        .triangle {
          border-bottom: 0px solid transparent;
          border-top: 29px solid transparent;
          border-left: 10px solid rgba(110, 189, 255, 0.2);
          border-right: 0px solid transparent;
        }
      }
      .commit {
        height: 40px;
        width: 260px;
        margin-left: 10px;
        text-align: center;
        line-height: 40px;
        font-weight: 700;
        font-size: 20px;
        padding-right: 40px;
        cursor: pointer;
        text-shadow: 2px 2px 2px #ad5e24;
        background: url('../../../../../../assets/HJJ/test/bottom.png') no-repeat 100%;
      }
    }
    .line {
      padding: 0 2px;
      border-right: 2px solid #a2a887;
      border-left: 2px solid #a2a887;
    }
    .right {
      flex: 1;
      height: 100%;
      background: rgba(23, 31, 41, 0.7);
    }
    .list {
      height: 88px;
      width: 100%;
      background: #0e1c38;
      margin-bottom: 1px;
      position: relative;
      padding: 0px 10px;
      display: flex;
      align-items: center;
      cursor: pointer;
      img {
        border: 1px solid #354971;
        height: 58px;
        width: 54px;
      }
      .userInfo {
        padding-left: 10px;
        color: #768aa9;
        font-weight: 600;
        flex: 1;
      }
      .score {
        width: 66px;
        height: 58px;
        background-image: url('../../../../../../assets/HJJ/basicTheory/test/score.png');
        position: absolute;
        right: 10px;
        top: 0;
        text-align: center;
        line-height: 45px;
        font-weight: bold;
        font-size: 18px;
        color: #37465f;
      }
    }
    .list:hover {
      background: linear-gradient(to bottom, #0f1d39, #213a6d);
    }
    .list:hover .userInfo {
      color: #c4dafb;
    }
    .listTwo {
      background: linear-gradient(to bottom, #0f1d39, #213a6d);
    }
    .list:hover .score {
      background-image: url('../../../../../../assets/HJJ/basicTheory/test/scoreHover.png');
      color: #93ceff;
    }
  }
  .LJ{
    .left {
      width: 280px;
      height: 100%;
      margin-right: 10px;
      background: rgba(38,41,36,0.3);
      display: flex;
      flex-direction: column;
      .cutDown {
        height: 186px;
        padding: 24px 8px;
        background: url('../../../../../../assets/LJ/train/train-time-bg.png') no-repeat center bottom;
        .text {
          text-align: center;
          color: #fff;
          font-size: 15px;
          padding-bottom: 15px;
        }
      }
      .stuInfo {
        background: url('../../../../../../assets/LJ/test/addTop.png');
        width: 100%;
        background-repeat: no-repeat;
        height: 40px;
        z-index: 99;
        color: #fff;
        text-align: center;
        font-weight: bold;
        font-size: 20px;
      }
      .studentDetail {
        display: flex;
        padding: 10px;
        /*border-bottom: 1px solid rgba(123, 152, 180, 0.2);*/
      }
      .questionItem {
        height: 30px;
        width: 30px;
        border: 1px solid #26332e;
        text-align: center;
        line-height: 30px;
        font-weight: bold;
        color: #a9abaa;
        margin: 5px;
        background-image: linear-gradient(#1f2f27, #18211d);
      }
      .questionItemActive {
        color: #eab110;
        border: 1px solid #eab110;
      }
      .questionTitle {
        display: flex;
        justify-content: space-between;
        border-bottom: 1px solid rgba(38, 66, 51, 0.5);
        height: 30px;
      }
      .question {
        flex: 1;
        overflow: auto;
        height: calc(100% - 230px);
        padding: 10px;
        .quesTypebg{
          background: rgb(38, 66, 51);
        }
        .triangle {
          border-bottom: 0px solid transparent;
          border-top: 29px solid transparent;
          border-left: 10px solid rgb(38, 66, 51);
          border-right: 0px solid transparent;
        }
      }
      .commit {
        height: 40px;
        width: 260px;
        margin-left: 10px;
        text-align: center;
        line-height: 40px;
        font-weight: 700;
        font-size: 20px;
        padding-right: 40px;
        cursor: pointer;
        text-shadow: 2px 2px 2px #ad5e24;
        background: url('../../../../../../assets/LJ/test/bottom.png') no-repeat 100%;
      }
    }
    .line {
      padding: 0 2px;
      border-right: 2px solid #a2a887;
      border-left: 2px solid #a2a887;
    }
    .right {
      flex: 1;
      height: 100%;
      background: rgba(38,41,36,0.3);
    }
    .list {
      height: 88px;
      width: 100%;
      background: #0e1c38;
      margin-bottom: 1px;
      position: relative;
      padding: 0px 10px;
      display: flex;
      align-items: center;
      cursor: pointer;
      img {
        border: 1px solid #26332e;
        height: 58px;
        width: 54px;
      }
      .userInfo {
        padding-left: 10px;
        color: #768aa9;
        font-weight: 600;
        flex: 1;
      }
      .score {
        width: 66px;
        height: 58px;
        background-image: url('../../../../../../assets/LJ/basicTheory/test/score.png');
        position: absolute;
        right: 10px;
        top: 0;
        text-align: center;
        line-height: 45px;
        font-weight: bold;
        font-size: 18px;
        color: #37465f;
      }
    }
    .list:hover {
      background: linear-gradient(to bottom, #0f1d39, #213a6d);
    }
    .list:hover .userInfo {
      color: #a9abaa;
    }
    .listTwo {
      background: linear-gradient(to bottom, #0f1d39, #213a6d);
    }
    .list:hover .score {
      background-image: url('../../../../../../assets/LJ/basicTheory/test/scoreHover.png');
      color: #93ceff;
    }
  }

  .KJ{
    .left {
      width: 280px;
      height: 100%;
      margin-right: 10px;
      background: rgba(38,41,36,0.3);
      display: flex;
      flex-direction: column;
      .cutDown {
        height: 186px;
        padding: 24px 8px;
        background: url('../../../../../../assets/KJ/train/train-time-bg.png') no-repeat center bottom;
        .text {
          text-align: center;
          color: #fff;
          font-size: 15px;
          padding-bottom: 15px;
        }
      }
      .stuInfo {
        background: url('../../../../../../assets/KJ/test/addTop.png');
        width: 100%;
        background-repeat: no-repeat;
        height: 40px;
        z-index: 99;
        color: #fff;
        text-align: center;
        font-weight: bold;
        font-size: 20px;
      }
      .studentDetail {
        display: flex;
        padding: 10px;
        /*border-bottom: 1px solid rgba(123, 152, 180, 0.2);*/
      }
      .questionItem {
        height: 30px;
        width: 30px;
        border: 1px solid #26332e;
        text-align: center;
        line-height: 30px;
        font-weight: bold;
        color: #a9abaa;
        margin: 5px;
        background-image: linear-gradient(#508de6, rgba(80,141,230,0.6));
      }
      .questionItemActive {
        color: #ffffff;
        border: 1px solid #eab110;
      }
      .questionTitle {
        display: flex;
        justify-content: space-between;
        border-bottom: 1px solid rgba(38, 66, 51, 0.5);
        height: 30px;
      }
      .question {
        flex: 1;
        overflow: auto;
        height: calc(100% - 230px);
        padding: 10px;
        .quesTypebg{
          background: rgba(80,141,230,0.6);
        }
        .triangle {
          border-bottom: 0px solid transparent;
          border-top: 29px solid transparent;
          border-left: 10px solid rgba(80,141,230,0.6);
          border-right: 0px solid transparent;
        }
      }
      .commit {
        height: 40px;
        width: 260px;
        margin-left: 10px;
        text-align: center;
        line-height: 40px;
        font-weight: 700;
        font-size: 20px;
        padding-right: 40px;
        cursor: pointer;
        text-shadow: 2px 2px 2px #ad5e24;
        background: url('../../../../../../assets/KJ/test/bottom.png') no-repeat 100%;
      }
    }
    .line {
      padding: 0 2px;
      border-right: 2px solid #a2a887;
      border-left: 2px solid #a2a887;
    }
    .right {
      flex: 1;
      height: 100%;
      background: rgba(38,41,36,0.3);
    }
    .list {
      height: 88px;
      width: 100%;
      background: #0e1c38;
      margin-bottom: 1px;
      position: relative;
      padding: 0px 10px;
      display: flex;
      align-items: center;
      cursor: pointer;
      img {
        border: 1px solid #26332e;
        height: 58px;
        width: 54px;
      }
      .userInfo {
        padding-left: 10px;
        color: #768aa9;
        font-weight: 600;
        flex: 1;
      }
      .score {
        width: 66px;
        height: 58px;
        background-image: url('../../../../../../assets/KJ/basicTheory/test/score.png');
        position: absolute;
        right: 10px;
        top: 0;
        text-align: center;
        line-height: 45px;
        font-weight: bold;
        font-size: 18px;
        color: #37465f;
      }
    }
    .list:hover {
      background: linear-gradient(to bottom, #0f1d39, #213a6d);
    }
    .list:hover .userInfo {
      color: #a9abaa;
    }
    .listTwo {
      background: linear-gradient(to bottom, #0f1d39, #213a6d);
    }
    .list:hover .score {
      background-image: url('../../../../../../assets/KJ/basicTheory/test/scoreHover.png');
      color: #93ceff;
    }
  }

</style>
