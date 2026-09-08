<template>
  <div>
    <div>
      <div class="time" v-if="state === '1' || state === '2'">
        <template v-if="h.length === 3">
          <div :class="changeNum(converStr(h).split(',')[0])">
            <span :style="{ backgroundColor: c, borderColor: c }" v-for="(i, index) in 7" :key="index" :class="'d' + (index + 1)"></span>
          </div>
          <div :class="changeNum(converStr(h).split(',')[1])">
            <span :style="{ backgroundColor: c, borderColor: c }" v-for="(i, index) in 7" :key="index" :class="'d' + (index + 1)"></span>
          </div>
          <div :class="changeNum(converStr(h).split(',')[2])">
            <span :style="{ backgroundColor: c, borderColor: c }" v-for="(i, index) in 7" :key="index" :class="'d' + (index + 1)"></span>
          </div>
        </template>
        <template v-else-if="h.length === 2">
          <div :class="changeNum(converStr(h).split(',')[0])">
            <span :style="{ backgroundColor: c, borderColor: c }" v-for="(i, index) in 7" :key="index" :class="'d' + (index + 1)"></span>
          </div>
          <div :class="changeNum(converStr(h).split(',')[1])">
            <span :style="{ backgroundColor: c, borderColor: c }" v-for="(i, index) in 7" :key="index" :class="'d' + (index + 1)"></span>
          </div>
        </template>
        <div class="dots">
          <div :style="{ backgroundColor: c, borderColor: c }"></div>
          <div :style="{ backgroundColor: c, borderColor: c }"></div>
        </div>
        <div :class="changeNum(converStr(m).split(',')[0])">
          <span :style="{ backgroundColor: c, borderColor: c }" v-for="(i, index) in 7" :key="index" :class="'d' + (index + 1)"></span>
        </div>
        <div :class="changeNum(converStr(m).split(',')[1])">
          <span :style="{ backgroundColor: c, borderColor: c }" v-for="(i, index) in 7" :key="index" :class="'d' + (index + 1)"></span>
        </div>
        <div class="dots">
          <div :style="{ backgroundColor: c, borderColor: c }"></div>
          <div :style="{ backgroundColor: c, borderColor: c }"></div>
        </div>
        <div :class="changeNum(converStr(s).split(',')[0])">
          <span :style="{ backgroundColor: c, borderColor: c }" v-for="(i, index) in 7" :key="index" :class="'d' + (index + 1)"></span>
        </div>
        <div :class="changeNum(converStr(s).split(',')[1])">
          <span :style="{ backgroundColor: c, borderColor: c }" v-for="(i, index) in 7" :key="index" :class="'d' + (index + 1)"></span>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import moment from 'moment/moment'
import * as R from 'ramda'

export default {
  name: 'dountDown.vue',
  props: ['color'],
  components: {},
  data() {
    return {
      state: '1',
      h: '00',
      m: '00',
      s: '00',
      c: '#235C46',
      cutdown: null
    }
  },
  mounted() {
    const interfaceStyle = window.interfaceStyle
    if(interfaceStyle==="HJ"){
      this.c = "#70c9ff"
    }else if(interfaceStyle==="HJJ"){
      this.c = "#e9deb2"
    }else {
      this.c = "#e9deb2"
    }

    // this.color !== undefined ? (this.c = this.color) : ''
  },
  methods: {
    converStr(str) {
      //给数字字符串添加逗号分隔符
      str = str + ''
      if (/\./.test(str)) {
        return str
          .replace(/\d(?=(\d{1})+\.)/g, '$&,')
          .split('')
          .reverse()
          .join('')
          .replace(/\d(?=(\d{1})+\.)/g, '$&,')
          .split('')
          .reverse()
          .join('')
      } else {
        return str.replace(/\d(?=(\d{1})+$)/g, '$&,')
      }
    },
    changeNum(num) {
      return num === '1' ? 'one' : num === '2' ? 'two' : num === '3' ? 'three' : num === '4' ? 'four' : num === '5' ? 'five' : num === '6' ? 'six' : num === '7' ? 'seven' : num === '8' ? 'eight' : num === '9' ? 'nine' : 'zero'
    },
    countDown(startTime, endTime, state, examineDays, symbol) {
      if (symbol === 0) {
        this.$parent.changeStuState()
      }
      setTimeout(() => {
        let that = this
        let lastTime = Date.parse(new Date(startTime))
        let oldTime = null
        that.intOne = setInterval(() => {
          if (null === oldTime) {
            oldTime = Date.parse(moment().format())
          } else {
            let nowTime = Date.parse(moment().format())
            if (1000 === nowTime - oldTime) {
              oldTime = nowTime
              lastTime += 1000
            }
          }
          //获取当前时间
          const now = lastTime
          //开始时间
          const start = Date.parse(new Date(endTime))
          //时间差
          const notTime = start - now
          //倒计时
          if (notTime > 0) {
            let day = 0
            let hour
            let mininate
            let second
            hour = Math.floor((notTime / 1000 / 60 / 60) % 24)
            mininate = Math.floor((notTime / 1000 / 60) % 60)
            second = Math.floor((notTime / 1000) % 60)
            if (state === '1') {
              day = Math.floor(notTime / 1000 / 60 / 60 / 24)
              hour = hour + day * 24
            } else if (state === '2') {
              if (examineDays > 1) {
                hour = hour + (examineDays - 1) * 24
              } else if (examineDays <= 1) {
                day = Math.floor(notTime / 1000 / 60 / 60 / 24)
                hour = hour + day * 24
              }
              that.state = state
            }
            that.h = hour > 9 ? hour + '' : '0' + hour
            that.m = mininate > 9 ? mininate + '' : '0' + mininate
            that.s = second > 9 ? second + '' : '0' + second
          } else {
            that.h = '00'
            that.m = '00'
            that.s = '00'
            that.clearInterval()
            if (state === '1') {
              that.$parent.handleOk1 !== undefined ? that.$parent.handleOk1() : null
            }
            if (state === '2') {
              symbol === 0 && that.$parent.endCountDownStu !== undefined ? that.$parent.endCountDownStu() : symbol === 1 && that.$parent.endCountDownAndupdateState !== undefined ? that.$parent.endCountDownAndupdateState() : null
              symbol === 1 && that.$parent.handleOk2 !== undefined ? that.$parent.handleOk2() : null
              symbol === 0 && that.$parent.stuEndCountDown !== undefined ? that.$parent.stuEndCountDown() : null
              that.state = '3'
            }
            //  考核系统倒计时结束
            this.$emit('endCountDown')
          }
        }, 200)
      }, 100)
    },

    clearInterval() {
      this.h = '00'
      this.m = '00'
      this.s = '00'
      clearInterval(this.intOne)
      if (this.cutdown) {
        clearInterval(this.cutdown)
      }
    },
    changeState() {
      this.state = '3'
    },
    // 给时间长度(多少秒),自动计算
    autoSetTimeNew(total,id) {
      if (total == 0) {
        clearInterval(this.cutdown)
        if (id != 2){
          this.$emit('commitTest')
        }
      }
      let hour
      let min
      let sec
      let day
      hour = Math.floor((total / 60 / 60) % 24)
      min = Math.floor((total / 60) % 60)
      sec = Math.floor(total % 60)
      day = Math.floor(total / 60 / 60 / 24)
      // 计算总小时数
      hour = hour + day * 24
      if (hour < 10 && hour >= 0) {
        this.h = '0' + hour
      } else {
        this.h = hour.toString()
      }
      if (min < 10 && min >= 0) {
        this.m = '0' + min
      } else {
        this.m = min
      }
      if (sec < 10 && sec >= 0) {
        this.s = '0' + sec
      } else {
        this.s = sec
      }
      if (this.cutdown == null) {
        this.cutdown = setInterval(() => {
          this.autoSetTimeNew(total--,id)
        }, 1000)
      }
    },
    // 给时间长度(多少秒),自动计算
    autoSetTimeAdd(total) {
      let hour
      let min
      let sec
      let day
      hour = Math.floor((total / 60 / 60) % 24)
      min = Math.floor((total / 60) % 60)
      sec = Math.floor(total % 60)
      day = Math.floor(total / 60 / 60 / 24)
      // 计算总小时数
      hour = hour + day * 24
      if (hour < 10 && hour >= 0) {
        this.h = '0' + hour
      } else {
        this.h = hour.toString()
      }
      if (min < 10 && min >= 0) {
        this.m = '0' + min
      } else {
        this.m = min
      }
      if (sec < 10 && sec >= 0) {
        this.s = '0' + sec
      } else {
        this.s = sec
      }
    },
    // 给时间长度(多少秒),自动计算
    autoSetTime(total) {
      this.clearInterval()
      let hour
      let min
      let sec
      let day
      hour = Math.floor((total / 1000 / 60 / 60) % 24)
      min = Math.floor((total / 1000 / 60) % 60)
      sec = Math.floor((total / 1000) % 60)
      day = Math.floor(total / 1000 / 60 / 60 / 24)
      // 计算总小时数
      hour = hour + day * 24
      if (hour < 10 && hour >= 0) {
        this.h = '0' + hour
      } else {
        this.h = hour
      }
      if (min < 10 && min >= 0) {
        this.m = '0' + min
      } else {
        this.m = min
      }
      if (sec < 10 && sec >= 0) {
        this.s = '0' + sec
      } else {
        this.s = sec
      }
    }
  },
  destroyed() {
    this.clearInterval()
  }
}
</script>
<style scoped>
.time div {
  text-align: left;
  position: relative;
  width: 28px;
  height: 50px;
  display: inline-block;
  margin: 0 4px;
}

.time div span {
  opacity: 0;
  position: absolute;
  -webkit-transition: 0.25s;
  -moz-transition: 0.25s;
  transition: 0.25s;
}

.time div span:before,
.time div span:after {
  content: '';
  position: absolute;
  width: 0;
  height: 0;
  border: 5px solid transparent;
}

.time .d1 {
  height: 5px;
  width: 16px;
  top: 0;
  left: 6px;
}

.time .d1:before {
  border-width: 0 5px 5px 0;
  border-right-color: inherit;
  left: -5px;
}

.time .d1:after {
  border-width: 0 0 5px 5px;
  border-left-color: inherit;
  right: -5px;
}

.time .d2 {
  height: 5px;
  width: 16px;
  top: 24px;
  left: 6px;
}

.time .d2:before {
  border-width: 3px 4px 2px;
  border-right-color: inherit;
  left: -8px;
}

.time .d2:after {
  border-width: 3px 4px 2px;
  border-left-color: inherit;
  right: -8px;
}

.time .d3 {
  height: 5px;
  width: 16px;
  top: 48px;
  left: 6px;
}

.time .d3:before {
  border-width: 5px 5px 0 0;
  border-right-color: inherit;
  left: -5px;
}

.time .d3:after {
  border-width: 5px 0 0 5px;
  border-left-color: inherit;
  right: -5px;
}

.time .d4 {
  width: 5px;
  height: 14px;
  top: 7px;
  left: 0;
}

.time .d4:before {
  border-width: 0 5px 5px 0;
  border-bottom-color: inherit;
  top: -5px;
}

.time .d4:after {
  border-width: 0 0 5px 5px;
  border-left-color: inherit;
  bottom: -5px;
}

.time .d5 {
  width: 5px;
  height: 14px;
  top: 7px;
  right: 0;
}

.time .d5:before {
  border-width: 0 0 5px 5px;
  border-bottom-color: inherit;
  top: -5px;
}

.time .d5:after {
  border-width: 5px 0 0 5px;
  border-top-color: inherit;
  bottom: -5px;
}

.time .d6 {
  width: 5px;
  height: 14px;
  top: 32px;
  left: 0;
}

.time .d6:before {
  border-width: 0 5px 5px 0;
  border-bottom-color: inherit;
  top: -5px;
}

.time .d6:after {
  border-width: 0 0 5px 5px;
  border-left-color: inherit;
  bottom: -5px;
}

.time .d7 {
  width: 5px;
  height: 14px;
  top: 32px;
  right: 0;
}

.time .d7:before {
  border-width: 0 0 5px 5px;
  border-bottom-color: inherit;
  top: -5px;
}

.time .d7:after {
  border-width: 5px 0 0 5px;
  border-top-color: inherit;
  bottom: -5px;
}

/* 1 */

.time div.one .d5,
.time div.one .d7 {
  opacity: 1;
}

/* 2 */

.time div.two .d1,
.time div.two .d5,
.time div.two .d2,
.time div.two .d6,
.time div.two .d3 {
  opacity: 1;
}

/* 3 */

.time div.three .d1,
.time div.three .d5,
.time div.three .d2,
.time div.three .d7,
.time div.three .d3 {
  opacity: 1;
}

/* 4 */

.time div.four .d5,
.time div.four .d2,
.time div.four .d4,
.time div.four .d7 {
  opacity: 1;
}

/* 5 */

.time div.five .d1,
.time div.five .d2,
.time div.five .d4,
.time div.five .d3,
.time div.five .d7 {
  opacity: 1;
}

/* 6 */

.time div.six .d1,
.time div.six .d2,
.time div.six .d4,
.time div.six .d3,
.time div.six .d6,
.time div.six .d7 {
  opacity: 1;
}

/* 7 */

.time div.seven .d1,
.time div.seven .d5,
.time div.seven .d7 {
  opacity: 1;
}

/* 8 */

.time div.eight .d1,
.time div.eight .d2,
.time div.eight .d3,
.time div.eight .d4,
.time div.eight .d5,
.time div.eight .d6,
.time div.eight .d7 {
  opacity: 1;
}

/* 9 */

.time div.nine .d1,
.time div.nine .d2,
.time div.nine .d3,
.time div.nine .d4,
.time div.nine .d5,
.time div.nine .d7 {
  opacity: 1;
}

/* 0 */

.time div.zero .d1,
.time div.zero .d3,
.time div.zero .d4,
.time div.zero .d5,
.time div.zero .d6,
.time div.zero .d7 {
  opacity: 1;
}

/* The dots */

.time div.dots {
  width: 5px;
}

.time div.dots div {
  width: 5px;
  height: 5px;
  position: absolute;
  left: 0;
  top: 14px;
  margin: 0;
}

.time div.dots div:last-child {
  top: 34px;
}
</style>
