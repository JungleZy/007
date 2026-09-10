<template>
  <div class="w-full h-full overflow-hidden relative">
    <div class="loading" v-show="loading">
      <a-spin size="large" tip="正在努力加载..."/>
    </div>
    <div class="w-full h-full trainBoxs content-mask-bg">
      <TrainLeft @startTest="startTest" @endTest="endTest" :trainData="trainData">
        <template v-slot:top>
          <div class="desc">
            {{
            trainData.status == 0 ? '请点击下方[开始练习]按钮开启训练' : trainData.status == 1 ? '本次练习正在进行，当前总耗时' : trainData.status == 2 ? '本次练习正在进行，当前总耗时' : '本次练习已结束,总用时'
            }}
          </div>
          <count-down class="width-100-per layout-center" color="#70c9ff" ref="countDown" style="height: 55px"/>
        </template>
        <template v-slot:bottom>
          <div class="stateBox" style="display: flex; align-items: center"></div>
          <div :class="{ flipContainer: true }" style="height: calc(100% - 82px)">
            <div :class="{ 'h-full overflow-auto totalBoxs': true }" style="padding: 0 8px">
              <div class="basicInit" style="height: 100%">
                <div class=" relative" style="padding: 10px 0 10px 20px">
                  <span style="font-weight: bold">是否倒计时:</span>  <a-switch v-model:checked="isCountdown" checked-children="是" un-checked-children="否" @change="changeCountdown"></a-switch>
                </div>
                <div class=" relative" style="padding: 10px 0 10px 20px" v-if="isCountdown">
                  <span style="font-weight: bold">倒计时时长:</span>
                  <a-input-number v-model:value="duration" :min="0" :step="1" @focus="getFocus" @blur="lackFocus" @change="changeCountdown"/>
                  <span style="font-weight: bold">分钟</span>
                </div>
                <div class="item relative">
                  <img :src="labNum" class="ico"/>
                  <div>
                    <div class="title">数量</div>
                    <div class="tags nobr">{{ trainData.groupNumber }}个</div>
                  </div>
                </div>
                <div class="item relative">
                  <img :src="labSpeed" class="ico"/>
                  <div>
                    <div class="title">速度</div>
                    <div class="tags nobr">{{ trainData.speed }}组/分</div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </template>
      </TrainLeft>
      <div class="trainCenter" style="width: calc(100% - 290px)">
        <div class="_main" style="padding: 0">
          <div class="cont">
            <NipMagicSpace>
              <template v-slot:one>
                <div class="relative">
                  <img :src="keyBg" class="bg" style="height: 310px"/>
                  <div class="w-full h-full overflow-auto absolute" ref="patKeyBoxRef" style="display: block;top: 0">
                    <a-textarea v-model:value="pageCodes[page.current - 1]" @change="textareaChange" spellcheck="false"
                                style="height: 100%; resize: none; font-size: 22px;font-family: Consolas, Monaco, 'Andale Mono', 'Ubuntu Mono', monospace;line-height: 30px;color: white"></a-textarea>
                  </div>
                </div>
              </template>
              <template v-slot:two>
                <div style="display: flex;align-items: center;height: 100%">
                  <div class="switch"><img :src="prevIMG" @click="prev" class="img"/></div>
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
                      <div class="keyBox">
                        <template v-for="(key, index) in 100" :key="index">
                          <!--                          <div :class="{ key: true, curr: activeIndex == index && trainData.status != 0 }"-->
                          <div :class="{ key: true }"
                               v-if="trainData.content[index]?trainData.content[index].pageNumber === page.current:true">
                            {{ trainData.content[index] ? trainData.content[index].key : '' }}
                          </div>
                        </template>
                      </div>
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
                  <div class="switch"><img :src="nextIMG" @click="next" class="img"/></div>
                </div>
              </template>
            </NipMagicSpace>
          </div>
          <div class="_bottom">
            <div class="pag">
              <div class="curr">
                当前：<span class="num">{{ page.current }}</span>
              </div>
              <div class="line"></div>
              <div class="total">
                总数：<span class="num">{{ page.pageAll }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
  export default {
    name: 'HandKeyPostTrain'
  }
</script>
<script setup>
  import {ref, onMounted, onUnmounted, watch} from 'vue'
  import {useRoute} from 'vue-router'
  import {message} from 'ant-design-vue'
  import CountDown from '../../../../../components/common/CountDown.vue'
  import Number from '../../../../../components/number/Number.vue'
  import TrainLeft from '../../../../../components/postJob/trainLeft/TrainLeft.vue'
  import telexTrain from './js/telexTrain.js'
  import {getPostTelegramTrainById} from '../../../../../common/api/TelegramApi.js'
  import NipMagicSpace from "../../../../../components/common/NipMagicSpace.vue";


  import iconImage from "../../js/iconImage";

  const imgs = iconImage()
  const {labSpeed, labNum, labType, topBg, keyBg} = imgs
  const prevIMG = imgs.prev
  const nextIMG = imgs.next
  const route = useRoute()
  const loading = ref(true)
  const minSpeed = ref(60)
  const countDown = ref(null)
  const isfocus = ref(true)
  const isTips = ref(false)
  const {
    trainData,
    code,
    activeIndex,
    page,
    pageCodes,
    isCountdown,
    duration,
    changeCountdown,
    next,
    prev,
    startTest,
    endTest,
    textareaChange,
    getFocus,
    lackFocus
  } = telexTrain(countDown, loading)
</script>

<style scoped lang="less">
  @import './js/handkey.css';

  :deep(.ant-input) {
    background: rgba(0, 0, 0, 0) !important;
    border-color: rgba(0, 0, 0, 0) !important;
  }

  .active {
    color: #70c9ff !important;
  }

  .errorCode {
    color: #fa4545 !important;
  }

  .indentCont {
    padding-left: 12px;
  }

  .indent {
    text-indent: 2em;
  }

  .indent2 {
    text-indent: 4.5em;
  }

  .gardtitle {
    color: #ffa800;
    padding-left: 20px;
    margin-top: 16px;
    font-size: 15px;
    font-weight: bolder;
    background: url('../../../../../assets/HJ/train/tips-lab.png') no-repeat left center;
  }

  /*::-webkit-scrollbar{*/
  /*  display: block!important;*/
  /*}*/
</style>
