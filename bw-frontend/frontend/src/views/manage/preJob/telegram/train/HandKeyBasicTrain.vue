<template>
  <div class="w-full h-full content-mask-bg overflow-hidden">
    <div class="trainCenter overflow-auto relative">
      <div class="statisticsBox statisticalBox">
        <div class="lineBox">
          <div class="box">  <img :src="countLab" alt="">
            <span>拍发总数</span></div>
          <div class="box"> {{trainLogData.chartData.dot.length + trainLogData.chartData.line.length}}</div>
        </div>
        <div class="lineBox"  v-for="(item,i) in totalData">
          <div class="box">
            <img :src="errorLab" v-if="item.type==0">
            <img :src="gcLab" v-else-if="item.type==1">
            <img :src="successLab" v-else-if="item.type==2">
            <img :src="gxLab" v-else>
            <span>{{item.name}}</span></div>
          <div class="box"> {{item.num}}次</div>
        </div>
        <div class="linebtns">
          <div @click="goBack" class="layout-center btn">
            <IconFont type="icon-rollback" style="margin-right: 5px"></IconFont> 退出
          </div>
        </div>
      </div>
      <CutDown :nowTime="nowTime"></CutDown>
      <div class="codeBodyBox">
        <div class="codeBody">
          <div v-for="(item,i) in totalData" class="item">
            <div class="key" :style="{fontSize: (fs * 1 + 12) + 'px'}">{{ item.name }}区间</div>
            <div class="value" v-if="currTrainTab=='dot'" :style="{fontSize: (fs * 1 + 13) + 'px'}">
              {{ standard.dot.filter(d => d.value.type == item.type)[0].value.min }}
              {{ item.type == 0 ? '或' : '-' }}
              {{ standard.dot.filter(d => d.value.type == item.type)[0].value.max }}(ms)
            </div>
            <div class="value" v-if="currTrainTab=='line'" :style="{fontSize: (fs * 1 + 13) + 'px'}">
              {{ standard.line.filter(d => d.value.type == item.type)[0].value.min }}
              {{ item.type == 0 ? '或' : '-' }}
              {{ standard.line.filter(d => d.value.type == item.type)[0].value.max }}(ms)
            </div>
          </div>
        </div>
      </div>
      <div class="head">
        <div :class="{'tab fs_dispose_1':true, on: currTrainTab=='dot'}" @click="changeTrainWay('dot')">
          点练习
          <div class="range fs_dispose_1">{{ range.dot[0] }}ms - {{ range.dot[1] }}ms</div>
        </div>
        <div :class="{'tab fs_dispose_1':true, on: currTrainTab=='line'}" @click="changeTrainWay('line')">
          划练习
          <div class="range fs_dispose_1">{{ range.line[0] }}ms - {{ range.line[1] }}ms</div>
        </div>
      </div>
      <div class="barrageBox" ref="barrageBoxRef" v-show="showBarrageBox">
        <template v-for="(log,i) in trainLogData.barrage[currTrainTab]">
          <div :class="{barrage:true, perfect: log.type==2,abnormal: log.type==0}"
               :style="{top: (log.top+'px'),zIndex: (i+1)}">
            <strong class="lab">{{ log.name }}</strong>
            <strong class="mark">{{ log.value }} ms</strong>
            <div class="msg">{{ log.msg }}</div>
          </div>
        </template>
      </div>
      <div class="handKeyImg w-full">
        <div class="handKey" style="margin-left: -500px">
          <div :class="['handShank',handKeyDown&&trainData.status==1?'up':'down']"></div>
        </div>
        <div class="patLogs">
          <img src="../../../../../assets/HJ/train/zhanwei.png" v-if="trainLogData.logData[currTrainTab].length == 0">
          <template v-for="(item, index) in dotLineLog">
            <div
                 :class="{log: true,
                          abnormal: item.type==0,
                          perfect: item.type==2}">
              <div class="val" :style="{fontSize: (fs * 1 + 20) + 'px'}">
                <strong>{{ item.value }}</strong>
                <span style="font-size: 14px;margin-left: 6px;" :style="{fontSize: (fs * 1 + 14) + 'px'}">ms</span>
              </div>
              <div class="status" :style="{fontSize: (fs * 1 + 15) + 'px'}">{{ item.name }}</div>
            </div>
          </template>
        </div>
      </div>
      <div class="chartLogBox">
        <div class="chartTitle">
          <div class="flex">
            <div class="item">
              <div class="lab" :style="{fontSize: (fs * 1 + 12) + 'px'}">平均值</div>
              <strong class="num">{{ trainLogData[currTrainTab].average }}ms</strong>
            </div>
            <div class="item">
              <div class="lab" :style="{fontSize: (fs * 1 + 12) + 'px'}">最小值</div>
              <strong class="num">{{ trainLogData[currTrainTab].min }}ms</strong>
            </div>
            <div class="item">
              <div class="lab" :style="{fontSize: (fs * 1 + 12) + 'px'}">最大值</div>
              <strong class="num">{{ trainLogData[currTrainTab].max }}ms</strong>
            </div>
          </div>
        </div>
        <div :class="{patChartLog: true, empty: trainLogData.chartData[currTrainTab].length == 0}">
          <div class="text" :style="{fontSize: (fs * 1 + 12) + 'px'}">单位(ms)</div>
          <div class="handKeyChart" id="patChartLog"></div>
        </div>
      </div>
    </div>
  </div>
</template>
<script>
export default {
  name: "handKeyBasicTrain"
}
</script>
<script setup>
import {ref, onMounted, onUnmounted, watch} from "vue";
import {QuestionCircleOutlined,createFromIconfontCN} from '@ant-design/icons-vue';
import CountDown from '../../../../../components/common/CountDown.vue';
import Number from '../../../../../components/number/Number.vue';
import abnormalImg from '../../../../../assets/HJJ/train/lab-abnormal.png';
import perfectImg from '../../../../../assets/HJJ/train/lab-perfect.png';
import normalImg from '../../../../../assets/HJJ/train/lab-normal.png';
import clockLab from '../../../../../assets/HJJ/telexTrain/clock.png';
import countLab from '../../../../../assets/HJJ/telexTrain/count.png';
import errorLab from '../../../../../assets/HJJ/telexTrain/error.png';
import successLab from '../../../../../assets/HJJ/telexTrain/success.png';
import gcLab from '../../../../../assets/HJJ/telexTrain/gc.png';
import gxLab from '../../../../../assets/HJJ/telexTrain/gx.png';
import useControl from "./js/useControl.js";
import basicTrain from "./js/basicTrain.js";
import {PubSub} from "../../../../../common/utils/PubSub";
import {wsCode} from "../../../../../common/ws/Ws";
import CutDown from "../../../../../components/cutDown/CutDown.vue";
const fs = ref(JSON.parse(localStorage.getItem('fs')));
const trainData = ref({
  status: 1
});
const IconFont = createFromIconfontCN({
  scriptUrl: window.iconUrl,
});
const {
  handKeyDown, patStandard, handKeyValue, diffTime, gapTime, wsOnline,devOnline,audioVolume,init
} = useControl(trainData);

onMounted(()=>{
  init();
});

const {
  nowTime, barrageBoxRef, range, showBarrageBox, currTrainTab, trainLogData, totalData, standard,dotLineLog,
  changeChartData, changeTrainWay, goBack
} = basicTrain();

watch(handKeyValue, () => {
  if (handKeyValue.value !== null && handKeyValue.value > -1) {
    changeChartData(diffTime.value[1]-diffTime.value[0]);
  }
});
onUnmounted(() => {
  PubSub.unsubscribe('message')
});


</script>

<style scoped lang="less">
  @import "./css/HandKeyBasicTrain";
</style>