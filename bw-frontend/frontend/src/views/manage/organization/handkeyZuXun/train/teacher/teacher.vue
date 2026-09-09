<template>
  <div class="w-full h-full zuXunBox relative">
    <div class="loading" v-show="loading">
      <a-spin size="large" tip="正在努力加载..." />
    </div>
    <div class="loading" v-show="scoreloading">
      <a-spin size="large" tip="学员数据结算中..." />
    </div>
    <TrainLeft :trainData="trainData" @startTest="startTrain" @endTest="endTrain">
      <template v-slot:top>
        <div class="desc">
          {{ trainData.status == 0 ? '请点击下方[开始练习]按钮开启训练' : trainData.status == 1 ? '本次练习正在进行，当前总耗时' : trainData.status == 2 ? '本次练习正在进行，当前总耗时' : '本次练习已结束,总用时' }}
        </div>
        <count-down class="width-100-per layout-center" color="#70c9ff" ref="trainTimeRef" style="height: 55px" />
      </template>
      <template v-slot:bottom>
        <div class="userListBox">
          <div class="title">参训人员 ({{trainData.userInfoList?trainData.userInfoList.length:0}}人)</div>
          <div class="userList overflow-auto" :style="{height: 'calc(100% - '+(trainData.status==2?40:100)+'px)'}">
            <template v-for="(user,u) in trainData.userInfoList">
              <div class="user" :class="[activeUserId==user.userId?'active':'']" @click="seeStudentScore(user)">
                <div class="use">
                  <img :src="fileUrl+user.userImg" class="avatar">
                  {{user.userName}}
                </div>
                <div class="r">
                  <template v-if="trainData.status < 2">
                    <div class="finish" v-if="user.isFinish == 1">已拍完</div>
                    <div class="afoot" v-else-if="trainData.status == 1&&user.userStatus !== 0">进行中</div>
                    <div class="off" v-else-if="user.userStatus == 0">离线</div>
                    <div class="on" v-else-if="user.userStatus == 1">在线</div>
                    <div class="ready" v-else-if="trainData.status == 0">已准备</div>
                  </template>
                  <template v-else>
                    <div class="finish" v-if="user.isFinish == null">未参训</div><!--未参训-->
                    <div class="finish" v-else><strong>{{user.score}}</strong> 分</div>
                  </template>
                </div>
              </div>
            </template>
          </div>
        </div>
      </template>
    </TrainLeft>
    <div class="trainCenter content-mask-bg">
      <template v-if="activeUserId == null">
        <div class="trainMessage">
          <div class="trainName">{{trainData.name}}</div>
          <div class="layout-center">
            <div class="descItem">类型：
              <strong>{{ trainData.type == 2 ? '混合报' : trainData.type == 1 ? '字码报' : trainData.codeSort ? '数字长码' : '数字短码' }}</strong>
            </div>
            <div class="descItem">报文数量：<strong>{{trainData.messageNumber}}</strong>&nbsp;组</div>
          </div>
        </div>
        <div class="trainLogsBox">
          <div class="w-full h-full overflow-auto" v-if="trainData.status < 2">
            <div class="userLogs" v-for="(user, u) in userPatData" :key="u">
              <div class="user">
                <div class="name">{{user.name}}</div>
              </div>
              <div class="logs _top">
                <img :src="topBg" class="bg" />
                <div class="cont">
                  <div class="scale"></div>
                  <div class="vals overflow-auto" :id="'patValBox'+u">
                    <template v-for="(code, i) in user.log" :key="i">
                      <div class="gap" :style="{ width: code.gap / 10 + 'px' }">{{ code.gap > 5000 ? code.gap + 'ms' : '' }}</div>
                      <div class="val" :style="{ width: code.diff / 10 + 'px' }"></div>
                    </template>
                  </div>
                </div>
              </div>
            </div>
          </div>
          <div class="chartBox w-full h-full" v-else>
            <div class="chartHead">
              <div :class="{tabItem:true, on: chartTabIndex == 1}" @click="getChartDataSource(1)">成绩分布</div>
              <div :class="{tabItem:true, on: chartTabIndex == 2}" @click="getChartDataSource(2)">错情统计</div>
              <div :class="{tabItem:true, on: chartTabIndex == 3}" @click="getChartDataSource(3)">训练态势</div>
            </div>
            <div class="chartCont">
              <div class="w-full h-full" id="scoreChart" v-show="chartTabIndex == 1"></div>
              <div class="w-full h-full" id="columnChart" v-show="chartTabIndex == 2"></div>
              <div class="w-full h-full" id="lineChart" v-show="chartTabIndex == 3"></div>
            </div>
          </div>
        </div>
      </template>
      <div class="w-full h-full layout-center" v-else-if="isFinish == null">
        <div style="font-size: 36px">该学员未参加训练！</div>
      </div>
      <div class="w-full h-full" v-else>
        <StudentScore :selfId="activeUserId" :key="activeUserId"></StudentScore>
      </div>
    </div>
  </div>
</template>
<script>
  export default {
    name: 'HandKeyZuXunTeacher'
  }
</script>
<script setup>
  import {ref} from "vue";
  import StudentScore from '../student/score.vue';
  import CountDown from '../../../../../../components/common/CountDown.vue';
  import iconImage from "../../../../postJob/js/iconImage";
  const {topBg} = iconImage()
  import teacher from './js/teacher.js'

  const {
    trainTimeRef,loading,trainData,fileUrl,userPatData,activeUserId,chartTabIndex,isFinish,scoreloading,
    startTrain,endTrain,seeStudentScore,getChartDataSource
  } = teacher()

</script>

<style scoped lang="less">
  @import "./css/teacher.less";
</style>
