<template>
  <div class="w-full h-full content-mask-bg relative">
    <div class="loading" v-show="loading">
      <a-spin size="large" tip="正在努力加载..." />
    </div>
    <div class="main w-full h-full">
      <div class="dataBox">
        <div class="top">
          <template v-if="interfaceStyle==='HJ'">
            <img v-if="scoreData.score>=80" :src="scoreSuperb" class="img">
            <img v-else-if="scoreData.score>=60" :src="scoreSuffice" class="img">
            <img v-else :src="scoreFailed" class="img">
          </template>
          <img v-else :src="scoreSuperb" class="img" />
          <div :class="{score:true,superb: scoreData.score>=80,suffice: scoreData.score>=60}">{{ scoreData.score }}</div>
        </div>
        <div class="bottom">
          <div class="resGroup">
            <img :src="resSpeed" class="ico">
            <div class="cont">
              <div class="desc">速度</div>
              <div class="num">{{ scoreData.rate }}<strong style="font-size: 16px;">{{wpmTOmm?'码/分':'WPM'}}</strong></div>
            </div>
          </div>
          <div class="resGroup">
            <img :src="resTime" class="ico">
            <div class="cont">
              <div class="desc">时间</div>
              <div class="num">{{ scoreData.validTime }}</div>
            </div>
          </div>
        </div>
        <div class="dataTotalBox">
          <div class="trainTotalBox">
            <div class="title">抄收信息</div>
            <div class="list">
              <div class="overflow-auto w-full h-full" style="padding: 10px;">
                <div class="dataTotalItem">
                  <div class="l">
                    <span class="lab" style="flex-shrink: 0">名称：</span>
                    <span class="val nobr" :title="scoreData.name" style="font-size: 17px">{{scoreData.name}}</span>
                  </div>
                  <div class="r"></div>
                </div>
                <div class="dataTotalItem">
                  <div class="l">
                    <span class="lab">类型：</span>
                    <span class="val" style="font-size: 17px">{{scoreData.type==1?'字码报':scoreData.type==0?'数码报':'混合报'}}</span>
                  </div>
                  <div class="r"></div>
                </div>
                <div class="dataTotalItem">
                  <div class="l">
                    <span class="lab">多码：</span>
                    <span class="val">{{scoreData.moreCode?scoreData.moreCode:0}}</span>
                  </div>
                  <div class="r">码</div>
                </div>
                <div class="dataTotalItem">
                  <div class="l">
                    <span class="lab">少码：</span>
                    <span class="val">{{scoreData.lackCode?scoreData.lackCode:0}}</span>
                  </div>
                  <div class="r">码</div>
                </div>
                <div class="dataTotalItem">
                  <div class="l">
                    <span class="lab">错码：</span>
                    <span class="val">{{scoreData.errorCode?scoreData.errorCode:0}}</span>
                  </div>
                  <div class="r">码</div>
                </div>
                <div class="dataTotalItem">
                  <div class="l">
                    <span class="lab">少组：</span>
                    <span class="val">{{scoreData.lackGroup?scoreData.lackGroup:0}}</span>
                  </div>
                  <div class="r">组</div>
                </div>
                <div class="dataTotalItem">
                  <div class="l">
                    <span class="lab">多组：</span>
                    <span class="val">{{scoreData.moreGroup?scoreData.moreGroup:0}}</span>
                  </div>
                  <div class="r">组</div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
      <div class="telegraphBox">
        <div class="totalBox">
          <div class="layout-left-center w-full">
            <div class="total">
              电报纸数：<span class="num">{{ scoreData.totalPage }}</span>
            </div>
            <div class="total">
              错误总数：<span class="num">{{ scoreData.errorCode + scoreData.lackCode + scoreData.moreCode }}</span>
            </div>
            <div class="total" style="width: max-content;background: none">
              报头：<span class="num">{{ header||'无' }}</span>
            </div>
          </div>
        </div>
        <div class="patTelegraph">
          <div class="telegraph relative">
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
            <div class="keyBox" v-if="content[currTelegram]">
              <template v-for="(key, index) in content[currTelegram]" :key="index">
                <div class="key">
                  <div class="title">{{ key.key }}</div>
                  <div
                    :class="{
                      val: true,
                      error: key.key != key.value && key.value,
                      omission: !key.value
                    }"
                  >
                    <div class="w-full nobr" :title="key.value">
                      {{ key.value ? key.value : '--' }}
                    </div>
                  </div>
                </div>
              </template>
            </div>
            <div v-else class="w-full h-full layout-center" style="position: absolute;z-index: 99;top: 0;background: rgba(255,255,255,0.2);">
              <a-spin ></a-spin>
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
        <div class="thumbBox" ref="thumbImageRef">
          <template v-for="(img, i) in thumbNumber" :key="i">
            <div :class="{ item: true, on: currTelegram == i+1 }" @click="switchTelegram(i + 1)">
              <div class="index">{{ i + 1 }}</div>
              <template v-if="scoreData.images[i]">
                <img :src="scoreData.images[i]" class="img" />
              </template>
              <template v-else>
                <img :src="thumbEmptyOn" v-if="currTelegram == i+1" class="img" />
                <img :src="thumbEmpty" v-else class="img" />
              </template>
            </div>
          </template>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
  export default {
    name: 'ReceivePostScore'
  }
</script>
<script setup>
  import { ref, onMounted, onUnmounted, watch, inject } from 'vue'
  import { useRoute } from 'vue-router'
  import trainScore from './js/trainScore.js'
  import thumbEmpty from '../../../../../assets/HJ/postTrain/thume-empty.png'
  import thumbEmptyOn from '../../../../../assets/HJ/postTrain/thume-empty-on.png'
  import scoreSuffice from '../../../../../assets/HJ/postTrain/score-suffice.png'
  import scoreFailed from '../../../../../assets/HJ/postTrain/score-failed.png'
  import resSpeed from '../../../../../assets/HJ/postTrain/speed.png'
  import resTime from '../../../../../assets/HJ/postTrain/time.png'
  import scoreSuperb from '../../../../../assets/HJ/postTrain/topBox-after.png'
  const wpmTOmm = inject('wpmTOmm')
  const interfaceStyle = window.interfaceStyle
  const { scoreData, loading, currTelegram, thumbImageRef, switchTelegram, content, thumbNumber,header } = trainScore()
</script>

<style scoped lang="less">
  @import "./css/receiveScore.less";
</style>
