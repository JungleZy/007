<template>
  <div class="w-full h-full overflow-hidden relative">
    <div class="w-full h-full trainBoxs content-mask-bg">
      <TrainLeft @startTest="startTest" @endTest="endTest" :trainData="trainData">
        <template v-slot:top>
          <div class="desc">
            {{ trainData.status == 0 ? '请点击下方[开始练习]按钮开启训练' : trainData.status == 1 ? '本次练习正在进行，当前总耗时' : trainData.status == 2 ? '本次练习正在进行，当前总耗时' : '本次练习已结束,总用时' }}
          </div>
          <count-down class="width-100-per layout-center" color="#e9deb2" ref="countDown" style="height: 55px" />
        </template>
        <template v-slot:bottom>
          <div class="stateBox" style="display: flex; align-items: center">
            <div :class="{ tipText: true, active: isTips }">【改错方法】</div>
            <div :class="{ tipBtn: true, active: isfocus }" @click="isfocus = !isfocus"></div>
          </div>
          <div :class="{ flipContainer: true }" style="height: calc(100% - 82px)">
            <div :class="{ 'h-full overflow-auto totalBoxs': true }" style="padding: 0 8px">
              <div class="basicInit" style="height: 100%">
                <div class="tipSymbol" v-if="isfocus">
                  <div class="symItem">
                    <div>换组：</div>
                    <div>空格</div>
                  </div>
                  <div class="symItem">
                    <div>换行：</div>
                    <div>Enter</div>
                  </div>
                  <div class="symItem">
                    <div>分页：</div>
                    <div>最后一组+"-"+页码 例如：3214-1</div>
                  </div>
                  <div class="symItem">
                    <div>换页：</div>
                    <div>Enter+Enter</div>
                  </div>
                </div>
                <template v-if="!isfocus">
                  <div class="item relative">
                    <img :src="labNumber" class="ico" />
                    <div>
                      <div class="title">数量</div>
                      <div class="tags nobr">{{ trainData.groupNumber }}个</div>
                    </div>
                  </div>
                  <div class="item relative">
                    <img :src="labspeed" class="ico" />
                    <div>
                      <div class="title">速度</div>
                      <div class="tags nobr">{{ trainData.speed }}码/分</div>
                    </div>
                  </div>
                </template>
                <div v-if="isfocus" class="overflow-auto" :style="{ height: isfocus ? 'calc(100% - 126px)' : '100%' }">
                  <div class="gardtitle" style="margin-top: 0">改错：</div>
                  <div class="indentCont">
                    <div>1、剩余字码 替换成 / + 空格 + 正确内容 <br /></div>
                    <div class="indent">例如: 5/// 1234</div>
                    <div class="indent2">12// 1234</div>
                    <div class="indent2">123/ 1234</div>
                    <div class="indent">如第4码发错了马上拍发/接着输入正确内容 例： 1233/1234</div>
                    <div class="indent">第4码发错且发送了间隔 拍发4个/ 再拍发空格再输入正确内容 例：1233 //// 1234</div>
                    <div>2、行尾修改：</div>
                    <div class="indent">拍发本行最后一组时拍发"空格"输入"组数"再拍发"空格"输入"更正的内容"然后正常换行 例如：1234 4566 7890 2 4565</div>
                    <div>3、页尾修改</div>
                    <div class="indent">拍发页标后拍发Enter再拍发修改的组数再拍发下"空格"输入正确的内容再拍发Enter 如还有需要修改的内容再按上诉步骤 例如：1234 5678-1 Enter 1 4321 Enter 2 98765 Enter</div>
                    <div>4、隔页修改</div>
                    <div class="indent">同上，但需要在组数前加页码例如： 1234 5678-1Enter 1P 1 4321 Enter</div>
                  </div>
                  <div class="gardtitle">多组少组处理:</div>
                  <div class="indentCont">
                    <div class="indent">页尾或行尾 输入"取消"或"添加"单词后见跟着组数例如:</div>
                    <div class="indent">qta 2 表示删除第2组</div>
                    <div class="indent">add 3 1234 表示添加第3组内容是1234</div>
                  </div>
                  <div class="gardtitle">多行少行的处理:</div>
                  <div class="indentCont">
                    <div class="indent">在页尾更正，输入输入"取消"或"添加"单词后见跟着组数然后三个"-"例如:</div>
                    <div class="indent">qta 2---4 表示：删除第2组到第4组</div>
                    <div class="indent">add 3---5 1111 1111 1111 表示依次添加3组到5组</div>
                  </div>
                  <div class="gardtitle">页标错误的处理:</div>
                  <div class="indentCont">
                    <div class="indent">1.漏页标符号: 按 改错中的-立即修改 处理</div>
                    <div class="indent">2.漏页标符号(已回动换行): 输入100 空格 再输入100组的内容和页标 例如：100 1024-1</div>
                    <div class="indent">3.标错页: 1234 .... 7890-2/1 表示将第二页修改成第一页</div>
                    <div class="indent">4.隔页修改页标: 采用上述的隔页修改</div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </template>
      </TrainLeft>
      <div class="trainCenter" style="width: calc(100% - 290px)">
        <div class="_top">
          <img :src="topBg" class="bg" style="width: 100%" />
          <div class="cont">
            <div class="scale"></div>
            <div class="vals overflow-auto" ref="patValBoxRef">
              <div v-for="v of code" :key="v" :style="['margin-left:' + (v.time * 60) / 10 + 'px']">{{ v.text }}</div>
            </div>
          </div>
        </div>
        <div class="_main">
          <div class="cont">
            <div class="patKey">
              <img :src="keyBg" class="bg" style="height: 180px" />
              <div class="keys overflow-auto" ref="patKeyBoxRef" style="display: block">
                <div v-for="com of compileCodePage" :key="com">
                  <div class="layout-left-top" v-for="v of com" :key="v">
                    <div class="key layout-left-top" v-for="t of v" :key="t">
                      {{ t }}
                    </div>
                  </div>
                </div>
                <div class="layout-left-top" v-for="v of compileCode" :key="v">
                  <div class="key layout-left-top" v-for="t of v" :key="t">
                    {{ t }}
                  </div>
                </div>
              </div>
            </div>
            <div class="patTelegraphBox">
              <div class="switch"><img :src="prevImg" @click="prev" class="img" /></div>
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
                    <div v-for="(key, index) in trainData.content" :key="index" class="key">
                      {{ key.text.join('') }}
                    </div>
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
              <div class="switch"><img :src="nextImg" @click="next" class="img" /></div>
            </div>
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
import { ref, onMounted, onUnmounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import CountDown from '../../../../../components/common/CountDown.vue'
import Number from '../../../../../components/number/Number.vue'
import TrainLeft from '../../../../../components/postJob/trainLeft/TrainLeft.vue'
import telexTrain from './js/telexTrain.js'
import { getPostTelegramTrainById } from '../../../../../common/api/TelegramApi.js'
import topBg from '../../../../../assets/HJ/postTrain/top.png'
import botBg from '../../../../../assets/HJ/postTrain/bottom.png'
import keyBg from '../../../../../assets/HJ/postTrain/keys.png'
import prevImg from '../../../../../assets/HJ/postTrain/prev.png'
import nextImg from '../../../../../assets/HJ/postTrain/next.png'
import labnum from '../../../../../assets/HJ/train/lab-num.png'
import startexercise from '../../../../../assets/HJ/train/start-exercise.png'
import endexercise from '../../../../../assets/HJ/train/end-exercise.png'
import detailexercise from '../../../../../assets/HJ/train/detail-exercise.png'
import tipsLabIco from '../../../../../assets/HJ/train/tips-lab.png'
import labNumber from '../../../../../assets/HJ/train/lab-number.png'
import labspeed from '../../../../../assets/HJ/train/new-lab-speed.png'

const route = useRoute()
const loading = ref(true)
const minSpeed = ref(60)
const countDown = ref(null)
const isfocus = ref(true)
const isTips = ref(false)
const { trainData, code, compileCode, activeIndex, page, compileCodePage, next, prev, startTest, endTest } = telexTrain(countDown)
</script>

<style scoped>
@import './js/handkey.css';
.active {
  color: #70c9ff !important;
}
.errorCode {
  color: #fa4545 !important;
}
.indentCont {
  padding-left: 20px;
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
