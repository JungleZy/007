<template>
<!--  ReceiveTrain-->
  <div class="w-full h-full layout-center relative scoreBody">
    <div class="basicTitle">
      <div class="trainBtnBox" v-if="receiveData.status < 3">
        <div class="btns">
          <div :class="{btnIco: true,active: showSetting=='freq'}" @click="showSetting = showSetting=='freq'?'':'freq'">
              <div :class="{ico1: true,active: showSetting=='freq'}"></div>
          </div>
          <div :class="{btnIco: true,active: showSetting=='volume'}" @click="showSetting = showSetting=='volume'?'':'volume'">
              <div :class="{ico2: true,active: showSetting=='volume'}"></div>
          </div>
          <div :class="{btnIco: true,active: showSetting=='rate'}" v-if="receiveData.isLowRate==1" @click="showSetting = showSetting=='rate'?'':'rate'">
            <div :class="{ico3: true,active: showSetting=='rate'}"></div>
          </div>
          <div :class="{btnIco: true,active: showSetting=='disturb'}" @click="showSetting = showSetting=='disturb'?'':'disturb'">
            <div :class="{ico4: true,active: showSetting=='disturb'}"></div>
          </div>
        </div>
        <div class="configureBox" v-if="showSetting != ''">
          <template v-if="showSetting=='freq'">
              <div class="flex" style="justify-content: space-between">
                  <div class="flex">
                      <div v-for="(item, i) in freqGather.gather" :key="i"
                           :class="{typeBtn: true, active: item.type == freqGather.curr.type}"
                           @click="freqGather.curr=item;frequency=item.min">{{item.name}}</div>
                  </div>
                  <div class="auditionBtn" @click="auditionInfo" v-if="receiveData.status!=1">试听</div>
              </div>
              <div class="volumeSlider voice">
                  <a-slider v-model:value="frequency" :min="freqGather.curr.min" :max="freqGather.curr.max" :step="freqGather.curr.step"></a-slider>
              </div>
              <div class="flex text" style="justify-content: space-between">
                  <div>{{freqGather.curr.min}}</div>
                  <div>{{freqGather.curr.max}}</div>
              </div>
          </template>
          <template v-if="showSetting=='volume'">
              <div class="flex" style="justify-content: space-between">
                  <img v-if="audioVolume==0" :src="volumeNone" alt="">
                  <img v-else-if="audioVolume<50" :src="volumeMini" alt="">
                  <img v-else-if="audioVolume==100" :src="volumeBig" alt="">
                  <img v-else :src="volumeSmall" alt="">
                  <span>{{audioVolume}}%</span>
              </div>
              <div class="volumeSlider voice" style="margin-top: 16px;">
                  <a-slider v-model:value="audioVolume" :min="0" :max="100" :step="5" :tooltipVisible="false"></a-slider>
              </div>
              <div class="flex text" style="justify-content: space-between">
                  <div>0</div><div>100</div>
              </div>
          </template>
          <template v-if="showSetting=='rate'">
            <div class="flex" style="justify-content: space-between">
              <span>码率</span>
              <span>{{ rateWpm }}{{wpmTOmm?'码/分':'WPM'}}</span>
            </div>
            <div class="volumeSlider voice" style="margin-top: 16px;">
              <a-slider v-model:value="rateWpm" :min="20" :max="39" :step="1" :tooltipVisible="false"></a-slider>
            </div>
            <div class="flex text" style="justify-content: space-between">
              <div>20</div>
              <div>39</div>
            </div>
          </template>
          <template v-if="showSetting=='disturb'">
            <div class="flex" style="justify-content: space-between">
              <span>播报干扰</span>
              <div class="auditionBtn" @click="onOffDisturb()" v-if="checkedDisturb.length > 0"
                   style="background-color: #692609;color: #fff">停止干扰</div>
            </div>
            <div class="disturbBox">
              <a-checkbox-group v-model:value="checkedDisturb" style="display: flex;align-items: flex-end;flex-wrap: wrap" >
                <a-checkbox v-for="item of disturbList" :key="item" :value="item.type" @change="changeDisturbInfo(item)"
                            style="margin: 8px 0 0 0;width: 100px;">{{item.name}}</a-checkbox>
              </a-checkbox-group>
            </div>
            <div class="flex" style="justify-content: space-between;margin-top: 15px">
              <span>干扰强度</span>
              <span>{{ disturbVol }} db</span>
            </div>
            <div class="volumeSlider voice" style="margin-top: 16px;">
              <a-slider v-model:value="disturbVol" :min="0" :max="100" :step="5" :tooltipVisible="false"></a-slider>
            </div>
            <div class="flex text" style="justify-content: space-between">
              <div>0</div>
              <div>100</div>
            </div>
          </template>
        </div>
      </div>
      <div class="codeSwitch" :style="{paddingLeft: receiveData.isLowRate==1?'180px':'138px'}">
        隐藏电码：
        <div class="switchBox" @click="showCode = !showCode">
          <div class="switch">
            <div :style="{left:showCode?'0':'51px'}" class="slider hideCode"></div>
            <div :class="{light: true, active: !showCode}"></div>
          </div>
        </div>
      </div>
      <div class="codeSwitch" :style="{paddingLeft: receiveData.isLowRate==1?'28px':'28px'}">
        隐藏报底：
        <div class="switchBox" @click="showMessage = !showMessage">
          <div class="switch">
            <div :style="{left:showMessage?'0':'51px'}" class="slider hideCode"></div>
            <div :class="{light: true, active: !showMessage}"></div>
          </div>
        </div>
      </div>
      <div class="dataBox">
        <div class="item">
          <img :src="labType" class="ico" v-if="interfaceStyle==='HJ'">
          <span>类型：</span>
            <div class="type">
                {{receiveData.type == 0 ? '字码报':
                receiveData.type == 1 ? '数码报':
                receiveData.type == 2 ? '混合报':
                receiveData.type == 11 ? '点报':
                receiveData.type == 12 ? '划报':
                receiveData.type == 13 ? '点划报':
                receiveData.type == 14 ? '点划连接报':'' }}
            </div>
        </div>
        <div class="item" v-if="receiveData.isLowRate==0">
          <img :src="labRate" class="ico" v-if="interfaceStyle==='HJ'">
          <span>码率：</span>
            <div class="type">{{receiveData.rate}}{{wpmTOmm?'码/分':'WPM'}}</div>
        </div>
      </div>
        <CutDown :nowTime="validTime"></CutDown>
    </div>
    <div class="w-full h-full layout-center"  style="height: 230px" >
      <template v-if="!isNaN(voicePlayData.curr[0])">
        <div  v-for="(code,index) in receiveData.codeMessageBody[activeIndex+((plugData.curr-1)*100)]" :key="index" class="activeNumber layout-center" :class="{current:voicePlayData.currentCode==index}">
            <div class="value">
              <template v-if="showCode">
                <div v-for="(v,j) in code.value" :key="j" :class="{val: true, line: v==1}"></div>
              </template>
              <div v-else class="hideCode">*****</div>
            </div>
        </div>
      </template>
    </div>
    <div style="padding:0 44px;height: calc(100% - 380px);width: 100%;flex-shrink: 0">
      <div class="patTelegraphBox">
        <div class="patTelegraph">
          <div class="telegraph">
            <div class="rowHead">
              <div class="key">1</div>
              <div class="key">2</div>
              <div class="key">3</div>
              <div class="key">4</div>
              <div class="key">5</div>
              <div class="key">6</div>
              <div class="key">7</div>
              <div class="key">8</div>
              <div class="key">9</div>
              <div class="key">10</div>
            </div>
            <div class="keyBox codeBody" >
              <template v-for="(item, index) in  plugData.body" :key="index">
                <div class="key"
                     :style="{color:index<=voicePlayData.curr[0]&&receiveData.status>0&&receiveData.status<3?'#fff':'#4c7595'}"
                     :class="{item: true,active: index==activeIndex&&receiveData.status>=1}">
                  <div v-for="i in item" :key="i">
                      {{showMessage?i.key:"*"}}
                  </div>
                </div>
              </template>
              <template v-if=" plugData.body.length < 100">
                <div class="key item" v-for="(item, index) in (100 -  plugData.body.length)" :key="index"></div>
              </template>
            </div>
          </div>
          <div class="pagIndex" v-if="plugData.pag > 1">
            <span style="color: #b4d5f0">页码：</span>【{{plugData.curr}} / {{plugData.pag}}】
          </div>
          <div class="serial">
            <div class="ser head"></div>
            <div class="ser">10</div>
            <div class="ser">20</div>
            <div class="ser">30</div>
            <div class="ser">40</div>
            <div class="ser">50</div>
            <div class="ser">60</div>
            <div class="ser">70</div>
            <div class="ser">80</div>
            <div class="ser">90</div>
            <div class="ser">100</div>
          </div>
        </div>
      </div>
    </div>
      <div class="trainOpenBtn layout-center" v-if="receiveData.status < 3" style="font-size: 20px">
        <div class="layout-center letter" v-if="receiveData.status>0">
          <div class="btn oper" v-if="receiveData.status == 2 " @click="continueTrainInfo">继续</div>
          <div class="btn oper" v-if="receiveData.status == 1" @click="pauseTrainInfo">暂停</div>
          <div class="btn oper" @click="repeatTrainInfo(0)">重听</div>
          <div class="btn oper end" @click="endTrainInfo">结束</div>
        </div>

        <template v-else >
          <div class="btn oper" v-if="plugData.curr > 1" @click="changePage(-1)">上一页</div>
          <div class="btn oper" style="font-size: 20px" @click="startTrainInfo">开始</div>
          <div class="btn oper" v-if="plugData.curr < plugData.pag" @click="changePage(1)">下一页</div>
        </template>
      </div>
  </div>
</template>

<script>
export default {
  name: "ReceiveTrain"
}
</script>
<script setup>
  import {ref, onMounted, onUnmounted, watch, inject, nextTick,computed} from "vue";
  import {useRoute} from "vue-router";
  import receiveTrain from "./js/receiveTrain.js";
  import Number from '../../../../../components/number/Number.vue';
  import receiveBgMP4 from "../../../../../assets/HJ/receive/receiveBg.mp4";
  import receiveBgPng from "../../../../../assets/HJ/receive/receiveBg.png";
  import labType from "../../../../../assets/HJ/receive/lab-type.png";
  import labRate from "../../../../../assets/HJ/receive/lab-rate.png";
  import volumeNone from "../../../../../assets/HJ/train/volume-none.png";
  import volumeMini from "../../../../../assets/HJ/train/volume-mini.png";
  import volumeBig from "../../../../../assets/HJ/train/volume-big.png";
  import volumeSmall from "../../../../../assets/HJ/train/volume-small.png";
  import CutDown from "../../../../../components/cutDown/CutDown.vue";
  const showSetting = ref('');
  const cool = inject('cool');
  const showCode = ref(true);
  const wpmTOmm = inject('wpmTOmm')
  const showMessage = ref(true)
  const interfaceStyle = window.interfaceStyle
  const {
    receiveBgRef,downTimeRef,trainKeyCodeRef,receiveData,validTime,frequency,freqGather,audioVolume,voicePlayData,rateWpm,
    plugData,disturbList,checkedDisturb,disturbVol,auditionInfo,startTrainInfo,pauseTrainInfo,continueTrainInfo,
    endTrainInfo,repeatTrainInfo,changePage,onOffDisturb,changeDisturbInfo,activeIndex
  } = receiveTrain(wpmTOmm);

  watch(cool, () => {
    nextTick(() => {
      if (receiveData.value.status === 1 && receiveBgRef.value) {
        receiveBgRef.value.play();
      }
    })
  });

</script>

<style scoped lang="less">
  @import "./css/receiveTrain";
  @import "./css/icon";
</style>
