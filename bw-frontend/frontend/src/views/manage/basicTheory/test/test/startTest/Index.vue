<template>
  <div style="height: 100%; width: 100%; display: flex" >
    <div class="left">
      <div class="line" v-if="interfaceStyle==='HJ'">
        <div style="width: 100%;height: 8px;background: #253554"></div>
      </div>
      <div class="cutDown">
        <div class="text">距离考试结束还有</div>
        <count-down class="width-100-per layout-center"  ref="countDown" @commitTest="commitTest" style="height: 55px" />
      </div>
      <div class="line"  v-if="interfaceStyle==='HJ'">
        <div style="width: 100%;height: 8px;background: #253554"></div>
      </div>
      <!--      userRole.id==='2'-->
      <div v-if="userRole.id === '2'" style="height: calc(100% - 201px)">
        <div class="stuInfo">学员信息</div>
        <div class="studentDetail">
          <img :src="fileUrl + userInfo.userImg" alt="" style="height: 100px" />
          <div style="height: 100px; flex: 1; padding-left: 10px">
            <div style="height: 50px; line-height: 50px"><span>姓名: </span>{{ userInfo.userName }}</div>
            <div style="height: 50px"><span>证件号: </span>{{ userInfo.idCard }}</div>
          </div>
        </div>
        <div class="question">
          <template v-if="questions">
            <div v-for="v of bankList" :key="v.key">
              <div class="questionTitle">
                <div style="display: flex">
                  <div class="quesTypebg" style="height: 100%; line-height: 30px; padding: 0 30px 0 10px">{{ v.name }}</div>
                  <div class="triangle"></div>
                </div>
                <div style="font-weight: bold; line-height: 35px">{{ questions[v.key].length }}题</div>
              </div>
              <div style="display: flex; flex-wrap: wrap; padding: 10px 0px">
                <div class="questionItem" :class="[item.isAnswer ? 'questionItemActive' : '']" v-for="(item, index) of questions[v.key]" :key="index">{{ index + 1 }}</div>
                <!--            <div class="questionItem questionItemActive">2</div>-->
              </div>
            </div>
          </template>
        </div>
        <div class="commit btn-animate btn-animate-orange" @click="commitTest">交卷退出</div>
      </div>
      <div v-else style="height: calc(100% - 201px); position: relative">
        <div class="stuInfo" style="position: absolute">学员列表</div>
        <div style="height: calc(100% - 40px); position: absolute; top: 45px; width: 100%">
          <div style="height: calc(100% - 60px); overflow-y: auto">
            <div v-for="(item, index) in userList" :key="index" class="list" :class="{ listTwo: active == index }" @click="getStudentInfo(item, index)">
              <div>
                <img :src="fileUrl + item.user_img" alt="" />
              </div>
              <div class="userInfo">
                <div>{{ item.userName }}</div>
              </div>
              <div class="score">
                <span v-if="item.state == 1">离线</span>
                <span v-else-if="item.state == 2" style="color: chartreuse">在线</span>
                <span v-else style="color: #fff5d4">已提交</span>
              </div>
            </div>
          </div>
          <div class="commit btn-animate btn-animate-orange" @click="commitTest">结束考试</div>
        </div>
      </div>
    </div>
    <div class="right grouping layout-center">
      <div class="w-full grouping_halving_line"></div>
      <perviewTest v-if="questions && isShow" :time="testTime" :paperData="questions" :height="'100%'" :clearAnswer="userRole.id == 2" :isAnswer="true"></perviewTest>
    </div>
  </div>
  <div class="w-full h-full layout-center" style="position: absolute;z-index: 9;top: 0;background: rgba(255,255,255,0.1)" v-if="paperLoding">
    <a-spin tip="加载中"/>
  </div>
</template>

<script>
export default {
  name: 'Index'
}
</script>
<script setup>
import CountDown from '../../../../../../components/common/CountDown.vue'
import perviewTest from '../../../../../../components/test/perviewTest/perviewTest.vue'
import { ref, onMounted } from 'vue'
import startTest from './js/startTest'
const countDown = ref('')
const interfaceStyle = window.interfaceStyle
const { userInfo, fileUrl, questions, bankList, userList, userRole, testTime, active, isShow, clearAnswer, commitTest, getStudentInfo,paperLoding } = startTest(countDown)
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
        height: calc(100% - 181px);
        margin-bottom: 10px;
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
        background-size: 100% 100%;
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
        height: 54px;
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
        color: #536f9e;
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
        background: url('../../../../../../assets/HJJ/train/train-time-bg.png') no-repeat center bottom;
        .text {
          text-align: center;
          color: #bfcde0;
          font-size: 15px;
          padding-bottom: 40px;
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
        margin-bottom: 10px;
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
        margin-top: 8px;
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
        font-weight: 600;
        flex: 1;
      }
      .score {
        width: 66px;
        height: 58px;
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
      border: 1px solid #afa48a;
      /*background: linear-gradient(to bottom,#0f1d39,#213a6d);*/
    }
    .list:hover .userInfo {
      color: #c4dafb;
    }
    .listTwo {
      border: 1px solid #afa48a;
      /*background: linear-gradient(to bottom,#0f1d39,#213a6d);*/
    }
    .list:hover .score {
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
        // background: url('../../../../../../assets/LJ/train/train-time-bg.png') no-repeat center bottom;
        background: url('../../../../../../assets/LJ/train/new-train-time-bg.png') no-repeat center bottom;
        margin-bottom: 15px;
        .text {
          text-align: center;
          color: #fff;
          font-size: 15px;
          padding-top: 5px;
          padding-bottom: 50px;
        }
      }
      .stuInfo {
        background: url('../../../../../../assets/LJ/test/new-addTop.png');
        width: 100%;
        background-repeat: no-repeat;
        height: 40px;
        z-index: 99;
        color: #8A908E;
        text-align: center;
        font-weight: bold;
        font-size: 18px;
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
        border-bottom: 1px solid rgba(123, 152, 180, 0.2);
        height: 30px;
      }
      .question {
        flex: 1;
        overflow: auto;
        height: calc(100% - 230px);
        margin-bottom: 10px;
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
        margin-top: 8px;
        text-align: center;
        line-height: 40px;
        font-weight: 700;
        font-size: 20px;
        cursor: pointer;
        text-shadow: 2px 2px 2px #ad5e24;
        background: url('../../../../../../assets/LJ/test/newBottom.png') no-repeat 100%;
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
      height: 70px;
      width: 93%;
      margin: auto;
      background: url('../../../../../../assets/LJ/test/userCard.png') no-repeat;
      background-size: cover;
      margin-bottom: 5px;
      position: relative;
      padding: 0px 10px;
      display: flex;
      align-items: center;
      cursor: pointer;
      img {
        border: 1px solid #98835A;
        height: 45px;
        width: 42px;
      }
      .userInfo {
        padding-left: 10px;
        color: #768aa9;
        font-weight: 600;
        flex: 1;
        &>div:first-child{
          color: #FBFEFD;
          font-size: 16px;
          font-weight: 700;
        }
        &>div:last-child{
          color: #a9abaa;
          font-size: 13px;
        }
      }
      .score {
        width: 66px;
        height: 58px;
        position: absolute;
        right: 10px;
        top: 0;
        text-align: center;
        line-height: 45px;
        font-weight: bold;
        font-size: 18px;
        color: #37465f;
        &>span:first-child{
          display: inline-block;
          width: 45px;
          height: 45px;
          font-size: 15px;
          color: #D0D6D4;
          background: url('../../../../../../assets/LJ/test/outLine.png') no-repeat;
          background-size: cover;
        }
        &>span:nth-child(2){
          display: inline-block;
          width: 45px;
          height: 45px;
          font-size: 15px;
          color: #D0D6D4;
          background: url('../../../../../../assets/LJ/test/onLine.png') no-repeat;
          background-size: cover;
        }
      }
    }
    .listTwo,.list:hover {
      background: url('../../../../../../assets/LJ/test/userCardHover.png') no-repeat;
      background-size: cover;
      /*background: linear-gradient(to bottom,#0f1d39,#213a6d);*/
    }
    .list:hover img{
      border: 1px solid #98835A;
    }
    .list:hover .userInfo {
      // color: #a9abaa;
    }
    .list:hover .score {
      // color: #93ceff;
    }
  }
  .KJ{
    .left {
      width: 280px;
      height: 100%;
      margin-right: 10px;
      background: rgba(31,67,99,0.3);
      display: flex;
      flex-direction: column;
      .cutDown {
        height: 186px;
        // background: url('../../../../../../assets/KJ/train/train-time-bg.png') no-repeat center bottom;
        background: url('../../../../../../assets/KJ/train/new-train-time-bg.png') no-repeat center bottom;
        margin-bottom: 15px;
        .text {
          text-align: center;
          color: #fff;
          font-size: 15px;
          padding-top: 5px;
          padding-bottom: 50px;
        }
      }
      .stuInfo {
        background: url('../../../../../../assets/KJ/test/new-addTop.png');
        width: 100%;
        background-repeat: no-repeat;
        height: 40px;
        z-index: 99;
        color: #8A908E;
        text-align: center;
        font-weight: bold;
        font-size: 18px;
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
        border-bottom: 1px solid rgba(123, 152, 180, 0.2);
        height: 30px;
      }
      .question {
        flex: 1;
        overflow: auto;
        height: calc(100% - 230px);
        margin-bottom: 10px;
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
        margin-top: 8px;
        text-align: center;
        line-height: 40px;
        font-weight: 700;
        font-size: 20px;
        cursor: pointer;
        text-shadow: 2px 2px 2px #ad5e24;
        background: url('../../../../../../assets/KJ/test/newBottom.png') no-repeat 100%;
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
      background: rgba(31,67,99,0.3);
    }

    .list {
      height: 70px;
      width: 93%;
      margin: auto;
      background: url('../../../../../../assets/KJ/test/userCard.png') no-repeat;
      background-size: cover;
      margin-bottom: 5px;
      position: relative;
      padding: 0px 10px;
      display: flex;
      align-items: center;
      cursor: pointer;
      img {
        border: 1px solid #98835A;
        height: 45px;
        width: 42px;
      }
      .userInfo {
        padding-left: 10px;
        color: #768aa9;
        font-weight: 600;
        flex: 1;
        &>div:first-child{
          color: #FBFEFD;
          font-size: 16px;
          font-weight: 700;
        }
        &>div:last-child{
          color: #a9abaa;
          font-size: 13px;
        }
      }
      .score {
        width: 66px;
        height: 58px;
        position: absolute;
        right: 10px;
        top: 0;
        text-align: center;
        line-height: 45px;
        font-weight: bold;
        font-size: 18px;
        color: #37465f;
        &>span:first-child{
          display: inline-block;
          width: 45px;
          height: 45px;
          font-size: 15px;
          color: #D0D6D4;
          background: url('../../../../../../assets/KJ/test/outLine.png') no-repeat;
          background-size: cover;
        }
        &>span:nth-child(2){
          display: inline-block;
          width: 45px;
          height: 45px;
          font-size: 15px;
          color: #D0D6D4;
          background: url('../../../../../../assets/KJ/test/onLine.png') no-repeat;
          background-size: cover;
        }
      }
    }
    .listTwo,.list:hover {
      background: url('../../../../../../assets/KJ/test/userCardHover.png') no-repeat;
      background-size: cover;
      /*background: linear-gradient(to bottom,#0f1d39,#213a6d);*/
    }
    .list:hover img{
      border: 1px solid #98835A;
    }
    .list:hover .userInfo {
      // color: #a9abaa;
    }
    .list:hover .score {
      // color: #93ceff;
    }
  }

  @media (max-width: 1260px) {
    .left {
      width: 260px;
      .commit {
        width: 240px;
      }
    }
  }
</style>
