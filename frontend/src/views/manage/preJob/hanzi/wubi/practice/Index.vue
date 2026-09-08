<template>
  <div class="content-mask-bg w-full h-full">
    <div class=" w-full h-full relative" style="display: flex;flex-direction: column;justify-content: space-between;">
      <div class="statisticsBox statisticalBox">
        <div class="lineBox">
          <div class="box">
            <img :src="clock" alt="" />
            <span>总时长</span>
          </div>
          <div class="box">{{ computationTime(trainData.totalTime) }}</div>
        </div>
        <div class="lineBox">
          <div class="box"><img :src="countp" alt="" /> <span>总拍发数</span></div>
          <div class="box">{{ trainData.totalNum }}次</div>
        </div>
        <div class="lineBox">
          <div class="box"><img :src="errorp" alt="" /> <span>总错误</span></div>
          <div class="box">{{ trainData.totalError }}次</div>
        </div>
        <div class="lineBox">
          <div class="box"><img :src="successp" alt="" /> <span>本次正确</span></div>
          <div class="box">{{ theTimeData.totalSuccess }} 次</div>
        </div>
        <div class="lineBox">
          <div class="box"><img :src="errorp" alt="" /> <span>本次错误</span></div>
          <div class="box">{{ theTimeData.totalError }} 次</div>
        </div>
        <div class="linebtns">
          <div class="exerciseBtn btn">
            <div class="layout-center" @click="ClearRecods"><IconFont type="icon-clear" style="margin-right: 5px"></IconFont> 清空</div>
          </div>
          <div class="exerciseBtn btn">
            <div class="layout-center" @click="goback"><IconFont type="icon-rollback" style="margin-right: 5px"></IconFont> 退出</div>
          </div>
        </div>
      </div>
      <div style="width: 278px">
        <CutDown :nowTime="nowTime"></CutDown>
      </div>
      <div class="w-full textBoxs" style="height: calc(100% - 480px);overflow: auto">
        <div class="layout-center" v-if="trainType==1||trainType==0">
          <div class="tabsTypeBox"
               style="width: 500px;flex-wrap: wrap;justify-content: center;display: flex;margin-bottom: 30px;margin-top: 10px">
            <div class="ZGbtns "
                 :class="[activeBtn==index?index<5?'activeBtn'+(index+1):'activeBtn6':'',index<5?'btn'+(index+1):'btn6']"
                 v-for="(v,index) of btn" @click="changetype(v,index)">
              <div class="left">
                <div class="left-top"></div>
                <div class="left-bottom"></div>
              </div>
              <div class="center layout-center" :style="{fontSize: (fs * 1 + 13) + 'px'}">{{ v.text }}</div>
              <div class="right">
                <div class="right-top"></div>
                <div class="right-bottom"></div>
              </div>
            </div>
            <!--           <div  class="btns" :class="[activeBtn==index?'activeBtn':'']" v-for="(v,index) of btn"  @click="changetype(v,index)">{{v.text}}</div>-->
          </div>
        </div>
        <div class="layout-center" v-if="trainType==2">
          <div class="tabsTypeBox"
               style="width: 600px;flex-wrap: wrap;justify-content: center;display: flex;margin-bottom: 15px;margin-top: 10px">
            <div class="btns" :class="[activeBtn==index?'activeBtn':'']" v-for="(v,index) of btn"
                 @click="changetype(v,index)"
                 :style="{fontSize: (fs * 1 + 13) + 'px'}">{{ v.text }}
            </div>
          </div>
        </div>
        <div class="layout-center showTextBox">
          <div v-if="trainType==0" style="font-size: 80px;text-align: center;width: 100%">
            {{ KJActiveKye }}
          </div>
          <div>
            <canvas v-if="trainType==1" id="wbzg" width="200" height="200"></canvas>
            <canvas v-if="trainType==2" id="wbzg" width="200" height="220"></canvas>
            <div class="layout-side"
                 style="background: #25415f;min-width: 200px;margin-top: 10px;font-size: 16px;font-weight: bold;padding: 0 5px">
              <div id="viewinbm" style="color: #6ebdff"></div>
              <div id="targetText" style=""></div>
            </div>
          </div>
        </div>
      </div>
      <Keyboard :ZG_key='ZG_key' :activeBtn="activeBtn" @operationRecord="operationRecord"
                @InputTxt="InputTxt"></Keyboard>
    </div>
  </div>
</template>
<script>
export default {
  name: 'Index'
}
</script>
<script setup>
import Keyboard from '../../../../../../components/preJob/hanzi/SelfKeyboard.vue'
import WB_ColorSHow from '../../../../../../common/utils/wb_color.js'
import { findByUserIdAndType, hanziSaveRecods, hanziClearRecods } from '../../../../../../common/api/hanzi.js'
import { nextTick, onMounted, ref, provide, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import WBZGK from './js/WBZGK.js'
import { KJ } from '../../../../../../components/preJob/hanzi/js/enum.js'
import wblx from './js/wbTest'
import obj from './js/wbColor'
import { createFromIconfontCN } from '@ant-design/icons-vue'
import clock from '../../../../../../assets/HJJ/telexTrain/clock.png'
import countp from '../../../../../../assets/HJJ/telexTrain/count.png'
import errorp from '../../../../../../assets/HJJ/telexTrain/error.png'
import successp from '../../../../../../assets/HJJ/telexTrain/success.png'
import CutDown from '../../../../../../components/cutDown/CutDown.vue'
const IconFont = createFromIconfontCN({
  scriptUrl: window.iconUrl
})
const fs = ref(JSON.parse(localStorage.getItem('fs')));
let font, code
let wb_ColoK = obj.wb_ColoK
let ctx
let d_Z = WBZGK.WBZGK.split(';'),
  d_r = 0,
  d_AZ = ''
let d_Ao, d_U
let d_H = [],
  d_g = [],
  d_l = 0,
  d_e = 0
let kuw_TestText = wblx.cyz1
let kuw_Count_RightNumber = 0
const btn = ref([
  {
    text: '横区',
    index: 0,
    big: 58,
    color: '#e8a829'
  },
  {
    text: '竖区',
    index: 59,
    big: 95,
    color: '#00b882'
  },
  {
    text: '撇区',
    index: 96,
    big: 161,
    color: '#4258f9'
  },
  {
    text: '捺区',
    index: 162,
    big: 213,
    color: '#b266ff'
  },
  { text: '折区', index: 214, big: d_Z.length - 1, color: '#0078b8' },
  { text: '混合' },
  { text: '乱序' }
])
const czbtn = ref([
  {
    text: '成字字根',
    type: 'czzg'
  },
  {
    text: '键名字',
    type: 'jmz'
  },
  {
    text: '常用字1',
    type: 'cyz1'
  },
  {
    text: '常用字2',
    type: 'cyz2'
  },
  {
    text: '一级简码',
    type: 'jm1'
  },
  {
    text: '二级简码',
    type: 'jm2'
  },
  {
    text: '三级简码',
    type: 'jm3'
  },
  {
    text: '四级简码',
    type: 'jm4'
  },
  {
    text: '识别码',
    type: 'sbm'
  },
  {
    text: '难拆字',
    type: 'ncz'
  }
])
const route = useRoute()
const router = useRouter()
const ZG_key = ref('G')
const activeBtn = ref(0)
const trainType = route.query.type
const KJData = ref([]) //口诀练习数组
const KJIndex = ref(0) //口诀练习数组 选中下标
const KJActiveKye = ref(0) //口诀练习数组 选中下标
//统计时间
const nowTime = ref({})
const timer = ref(null)
let time = 0
let maxStr = {
  text: '',
  type: 0
}
const trainData = ref({}) //训练记录
const theTimeData = ref({
  totalError: 0,
  totalSuccess: 0
})
onMounted(() => {
  initRecods()
  if (route.query.type == 0) {
    KJData.value = KJ.filter(item => item.type == activeBtn.value)
    ZG_key.value = KJData.value[0].key
    KJActiveKye.value = KJData.value[0].text
  }
  if (route.query.type == 1) {
    d_Ao = new WB_ColorSHow('wbzg')
    d_Ao.BackgroundColor = 'rgba(0,0,0,0)'
    d_n()
  }
  //拆字训练
  if (route.query.type == 2) {
    btn.value = czbtn.value
    ZG_key.value = ''
    nextTick(() => {
      d_U = new WB_ColorSHow('wbzg')
      // d_U.BackgroundColor = '#f60'
      CZinit()
    })
  }
  changetype(btn.value[0], 0)
})
provide('maxStr', maxStr)
const changetype = (v, index) => {
  activeBtn.value = index
  if (route.query.type == 0) {
    if (index < 5) {
      KJData.value = KJ.filter(item => item.type == activeBtn.value)
      KJIndex.value = 0
      ZG_key.value = KJData.value[KJIndex.value].key
      KJActiveKye.value = KJData.value[KJIndex.value].text
    } else if (index == 6) {
      KJData.value = KJ
      KJIndex.value = Math.floor(Math.random() * KJ.length)
      ZG_key.value = KJData.value[KJIndex.value].key
      KJActiveKye.value = KJData.value[KJIndex.value].text
    } else {
      KJData.value = KJ
      KJIndex.value = 0
      ZG_key.value = KJData.value[KJIndex.value].key
      KJActiveKye.value = KJData.value[KJIndex.value].text
    }
  }
  if (route.query.type == 1) {
    if (index < 5) {
      d_r = v.index
    }
    d_n()
  }
  if (route.query.type == 2) {
    document.getElementById('viewinbm').innerHTML = ''
    kuw_TestText = wblx[v.type]
    d_l = 0
    nextTick(() => {
      CZinit()
    })
  }
}
const InputTxt = v => {
  route.query.type == 0 ? KJinit() : ''
  route.query.type == 1 && v.toLowerCase() == d_AZ ? (d_n(), kuw_Count_RightNumber++) : ''
  route.query.type == 2 ? kuw_InputTxt(v) : ''
}
const KJinit = () => {
  if (KJIndex.value < KJData.value.length - 1 && activeBtn.value != 6) {
    KJIndex.value++
  } else if (activeBtn.value == 6) {
    KJIndex.value = Math.floor(Math.random() * KJData.value.length)
  } else {
    KJIndex.value = 0
  }
  KJActiveKye.value = KJData.value[KJIndex.value].text
  ZG_key.value = KJData.value[KJIndex.value].key
}
const d_n = () => {
  if (d_r >= d_Z.length) {
    d_r = 0
  } else if (activeBtn.value == 6) {
    d_r = Math.floor(Math.random() * d_Z.length)
  } else if (d_r > btn.value[activeBtn.value].big) {
    d_r = btn.value[activeBtn.value].index
  }
  for (let v of btn.value) {
    if (d_r < v.big && d_r >= v.index) {
      d_Ao.FillColorZG = v.color
    }
  }
  d_r == d_Z.length && (d_r = 0)
  //控制字根的
  d_Ao.ZG_Show(d_Z[d_r])
  d_AZ = d_Z[d_r].charAt(0)
  ZG_key.value = d_AZ.toUpperCase()
  //控制键盘的
  // d_I.setxy(d_AZ);
  d_r++
}

//拆字训练方法
const CZinit = () => {
  d_l = Math.floor(Math.random() * kuw_TestText.length)
  d_l == kuw_TestText.length && (d_l = 0)
  d_e = 0
  d_U.init()
  d_U.d_AE(kuw_TestText.charAt(d_l))
  let targetText = document.getElementById('targetText')
  var a = d_A9(kuw_TestText.charAt(d_l))
  '' != a
    ? ((d_H = a.split(':')), '' != d_H[1] && d_U.Line(d_H[1]), '' != d_H[2] && d_U.d_AO(d_H[2], d_e + 1), '' != d_H[3] && ((d_g = d_H[3].split(',')), wbbm_Next_ts(), (targetText.innerHTML = d_g)), (maxStr.text = d_g.sort((a, b) => b.length - a.length)[0]), (maxStr.type = 0))
    : // "" != d_H[4] && d_U.d_Ac(d_H[4]),//类型
      // "" != d_H[5] && d_U.d_Aw(d_H[5])//拼音
      ((d_H.length = 0), (targetText.innerHTML = ''), (d_g.length = 0))
  d_l++
}
const d_A9 = a => {
  var b = ''
  a = wb_ColoK.indexOf(a)
  ;-1 < a && ((b = wb_ColoK.indexOf(';', a)), (b = wb_ColoK.substring(a, b)))
  return b
}
const kuw_InputTxt = a => {
  maxStr.type++
  if (1 > d_g.length) CZinit()
  else {
    let viewinbm = document.getElementById('viewinbm')
    var b = a.toLowerCase()
    b == d_g[0].charAt(d_e) || b == d_g[1].charAt(d_e) ? ((viewinbm.innerHTML += b), maxStr.text.length - 1 == d_e ? ((viewinbm.innerHTML = ''), CZinit(), kuw_Count_RightNumber++) : (d_e++, wbbm_Next_ts())) : ' '
  }
}
const wbbm_Next_ts = () => {
  '1' == d_H[4] && 1 == d_e && (d_U.init(), d_U.d_AE(kuw_TestText.charAt(d_l - 1)), '' != d_H[1] && d_U.Line(d_H[1]))
  d_U.d_AO(d_H[2], d_e + 1)
}
//页面信息操作记录
const operationRecord = type => {
  trainData.value.totalNum++
  if (type) {
    theTimeData.value.totalSuccess++
  } else {
    trainData.value.totalError++
    theTimeData.value.totalError++
  }
}
//初始化操作记录
const initRecods = () => {
  findByUserIdAndType({ type: route.query.type }).then(res => {
    trainData.value = res.data
    time = 0
    computationTime2(time)
    timer.value = setInterval(() => {
      time++
      trainData.value.totalTime++
      computationTime2(time)
    }, 1000)
  })
}
//清空操作记录
const ClearRecods = () => {
  hanziClearRecods({ type: route.query.type }).then(res => {
    theTimeData.value.totalError = 0
    theTimeData.value.totalSuccess = 0
    clearInterval(timer.value)
    initRecods()
  })
}
//格式化时间
//格式化时间
const computationTime = total => {
  let hour
  let min
  let sec
  let day
  let h
  let m
  let s
  hour = Math.floor((total / 60 / 60) % 24)
  min = Math.floor((total / 60) % 60)
  sec = Math.floor(total % 60)
  day = Math.floor(total / 60 / 60 / 24)
  // 计算总小时数
  hour = hour + day * 24
  if (hour < 10 && hour >= 0) {
    h = '0' + hour
  } else {
    h = hour.toString()
  }
  if (min < 10 && min >= 0) {
    m = '0' + min
  } else {
    m = min
  }
  if (sec < 10 && sec >= 0) {
    s = '0' + sec
  } else {
    s = sec
  }
  return h + ' : ' + m + ' : ' + s
}
const computationTime2 = total => {
  let hour
  let min
  let sec
  let day
  let h
  let m
  let s
  hour = Math.floor((total / 60 / 60) % 24)
  min = Math.floor((total / 60) % 60)
  sec = Math.floor(total % 60)
  day = Math.floor(total / 60 / 60 / 24)
  // 计算总小时数
  hour = hour + day * 24
  if (hour < 10 && hour >= 0) {
    h = '0' + hour
  } else {
    h = hour.toString()
  }
  if (min < 10 && min >= 0) {
    m = '0' + min
  } else {
    m = min.toString()
  }
  if (sec < 10 && sec >= 0) {
    s = '0' + sec
  } else {
    s = sec.toString()
  }
  nowTime.value.h1 = h.substring(0, 1) * 1
  nowTime.value.h2 = h.substring(1, 2) * 1
  nowTime.value.m1 = m.substring(0, 1) * 1
  nowTime.value.m2 = m.substring(1, 2) * 1
  nowTime.value.s1 = s.substring(0, 1) * 1
  nowTime.value.s2 = s.substring(1, 2) * 1
}
const goback = () => {
  router.go(-1)
}
onBeforeUnmount(() => {
  clearInterval(timer.value)
  hanziSaveRecods(trainData.value)
})
</script>
<style lang="less" scoped>
  @import "./css/index";
</style>
