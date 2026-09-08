<template>
  <div class="addTestContainer">
    <div class="right" style="margin-left: 10px">
      <div class="top" v-if="testExam">
        <div class="text">考核名称: {{ testExam.title }}</div>
        <div class="splitLine">/</div>
        <div class="text">开始时间: {{ testExam.start_time }}</div>
      </div>
      <div class="bottom">
        <div class="bottomLeft">
          <StudentPerviewTest v-if="questions && isShow" :time="testExam.duration" :paperData="questions" :isGarde="true" :height="'100%'" :clearAnswer="false" :isAnswer="true"></StudentPerviewTest>
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
          </div>
          <div style="padding-bottom: 10px">
            <div style="color: #5ea0da; padding-left: 6px">
              <span style="display: inline-block; width: 40px; border-bottom: 1px solid #263657; margin-bottom: 4px; margin-right: 4px"></span>
              得分：<span v-if="activeUser" style="font-size: 24px; color: #e9deb2"
                >{{ activeUser.score }}分
                <div class="commitScore layout-center" @click="goback"><IconFont type="icon-fanhui1" style="font-size: 20px; padding-right: 10px"></IconFont> 返回</div> </span
              ><span style="display: inline-block; width: 40px; border-bottom: 1px solid #263657; margin-bottom: 4px; margin-left: 4px"></span>
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
import { createFromIconfontCN } from '@ant-design/icons-vue'
import { onMounted } from 'vue'
import startGrade from './js/startGrade'
import StudentPerviewTest from '../../../../../../components/test/studentPerviewTest/StudentPerviewTest.vue'
const IconFont = createFromIconfontCN({
  scriptUrl: window.iconUrl
})
onMounted(() => {
  initPaper()
})
const { initPaper, questions, students, fileUrl, examState, isShow, activeUser, userRole, testExam, endGrade, goback, commitScore, selelctStu } = startGrade()
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
          background: #0a1429;
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
            color: #768aa9;
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
            color: #37465f;
          }
        }
        .list:hover{
          background-image: linear-gradient(to bottom,#0f1d39,#213a6d);
        }
        .list:hover .userInfo{
          color: #c4dafb;
        }
        .list:hover .score{
          background-image: url("../../../../../../assets/HJ/basicTheory/test/scoreHover.png");
          color: #93ceff;
        }
        .activeUser{
          background-image: linear-gradient(to bottom,#0f1d39,#213a6d);
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
        height: 35px;
        width: 100%;
        display: flex;
        color: #c4dafb;
        line-height: 35px;
        padding-left: 20px;
        .splitLine{
          padding: 0 40px;
          color: #424f63
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
            color:#768ba9;
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
      background: rgba(24, 45, 86, 0.7);
      margin-right: 10px;
      display: flex;
      flex-direction: column;
      padding-bottom: 20px;
      align-items: center;
      .top {
        /*background: #0a1429;*/
        background-image: url('../../../../../../assets/HJJ/test/addTop.png');
        width: 100%;
        background-repeat: no-repeat;
        height: 40px;
        color: #70b9ec;
        text-align: center;
        font-weight: bold;
        margin-top: 8px;
      }
      .stubox {
        flex: 1;
        width: 100%;
        height: calc(100% - 42px);
        .list {
          height: 88px;
          width: 100%;
          background: #0a1429;
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
            font-weight: 550;
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
            font-size: 23px;
            color: #37465f;
          }
        }
        .list:hover {
          background-image: linear-gradient(to bottom, #0f1d39, #213a6d);
        }
        .list:hover .userInfo {
          color: #c4dafb;
        }
        .list:hover .score {
          background-image: url('../../../../../../assets/HJJ/basicTheory/test/scoreHover.png');
          color: #93ceff;
        }
        .activeUser {
          background-image: linear-gradient(to bottom, #0f1d39, #213a6d);
          .score {
            background-image: url('../../../../../../assets/HJJ/basicTheory/test/scoreHover.png');
            color: #93ceff;
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
      background: rgba(24,45,86,0.7) ;
      margin-right: 10px;
      display: flex;
      flex-direction: column;
      padding-bottom: 20px;
      align-items: center;
      .top{
        /*background: #0a1429;*/
        background-image: url("../../../../../../assets/LJ/test/addTop.png");
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
          background: #0a1429;
          margin-bottom: 1px;
          position: relative;
          padding: 0px 10px;
          display: flex;
          align-items: center;
          cursor: pointer;
          img{
            border: 1px solid #26332e;
            height: 58px;
            width: 54px;
          }
          .userInfo{
            padding-left: 10px;
            color: #768aa9;
            font-weight:550;
            flex: 1;
          }
          .score{
            width: 66px;
            height: 58px;
            background-image: url("../../../../../../assets/LJ/basicTheory/test/score.png");
            position: absolute;
            right: 10px;
            top: 0;
            text-align: center;
            line-height: 45px;
            font-weight: bold;
            font-size: 23px;
            color: #37465f;
          }
        }
        .list:hover{
          background-image: linear-gradient(to bottom,#0f1d39,#213a6d);
        }
        .list:hover .userInfo{
          color: #a9abaa;
        }
        .list:hover .score{
          background-image: url("../../../../../../assets/LJ/basicTheory/test/scoreHover.png");
          color: #93ceff;
        }
        .activeUser{
          background-image: linear-gradient(to bottom,#0f1d39,#213a6d);
          .score{
            background-image: url("../../../../../../assets/LJ/basicTheory/test/scoreHover.png");
            color: #93ceff;
          }
        }
      }
      .gradeEnd{
        cursor: pointer;
        width: 260px;
        height: 42px;
        margin: 0 auto;
        background: url("../../../../../../assets/LJ/assets/test/newBottom.png") no-repeat 100%;
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
      background: rgba(24,45,86,0.7) ;
      margin-right: 10px;
      display: flex;
      flex-direction: column;
      padding-bottom: 20px;
      align-items: center;
      .top{
        /*background: #0a1429;*/
        background-image: url("../../../../../../assets/KJ/test/addTop.png");
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
          background: #0a1429;
          margin-bottom: 1px;
          position: relative;
          padding: 0px 10px;
          display: flex;
          align-items: center;
          cursor: pointer;
          img{
            border: 1px solid #26332e;
            height: 58px;
            width: 54px;
          }
          .userInfo{
            padding-left: 10px;
            color: #768aa9;
            font-weight:550;
            flex: 1;
          }
          .score{
            width: 66px;
            height: 58px;
            background-image: url("../../../../../../assets/KJ/basicTheory/test/score.png");
            position: absolute;
            right: 10px;
            top: 0;
            text-align: center;
            line-height: 45px;
            font-weight: bold;
            font-size: 23px;
            color: #37465f;
          }
        }
        .list:hover{
          background-image: linear-gradient(to bottom,#0f1d39,#213a6d);
        }
        .list:hover .userInfo{
          color: #a9abaa;
        }
        .list:hover .score{
          background-image: url("../../../../../../assets/KJ/basicTheory/test/scoreHover.png");
          color: #93ceff;
        }
        .activeUser{
          background-image: linear-gradient(to bottom,#0f1d39,#213a6d);
          .score{
            background-image: url("../../../../../../assets/KJ/basicTheory/test/scoreHover.png");
            color: #93ceff;
          }
        }
      }
      .gradeEnd{
        cursor: pointer;
        width: 260px;
        height: 42px;
        margin: 0 auto;
        background: url("../../../../../../assets/KJ/assets/test/newBottom.png") no-repeat 100%;
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
  }
</style>
