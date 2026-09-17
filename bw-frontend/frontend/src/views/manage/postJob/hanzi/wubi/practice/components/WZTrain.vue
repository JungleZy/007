<template>
  <div class="h-full w-full layout-left-top wz" style="justify-content: space-between">
    <div class="w-full layout-center" v-if="isfocus" style="padding: 40px 0; height: 330px">
      <div style="width: 50%; display: flex; height: 100%" class="focust">
        <div class="focusBox" style="height: 100%">
          <div class="imgBox" style="font-size: 80px !important; height: 80%">{{ activeMessage.font }}</div>
          <div class="layout-center containerBox" style="text-align: center">
            <div style="height: 40px; font-size: 24px" v-if="!inputeMethod">{{ activeMessage.value }}</div>
            <div class="relative active" style="width: 1px; height: 20px" v-if="trainData.status === 1 && isfocus && !inputeMethod">
              <div class="writingBox" v-if="wubiData.show">
                <div style="width: calc(100% - 50px); height: 100%">
                  <div style="width: 100%; height: 50%; text-align: left; display: flex; align-items: center; padding-left: 10px; font-size: 18px">{{ wubiData.text }}</div>
                  <div style="width: 100%; height: 50%; display: flex; justify-content: flex-start; align-items: center">
                    <div v-for="(font, index2) of wubiData.activeArr" style="cursor: pointer; padding-left: 15px" :style="[activeText === index2 ? 'color:#84f749' : '']" @click.stop="selectText(font.text, $event)">{{ index2 + 1 }}.{{ font.text }}</div>
                  </div>
                </div>
                <div style="width: 50px; height: 100%" class="rightbtn">
                  <IconFont type="icon-left" style="font-size: 20px; color: white" @click="prevData"></IconFont>
                  <IconFont type="icon-right" style="font-size: 20px; color: white" @click="nextData"></IconFont>
                </div>
              </div>
            </div>
            <a-input style="text-align: center; height: 40px" v-if="inputeMethod" :disabled="trainData.status != 1" v-model:value="activeMessage.value" @blur="statistics2"></a-input>
          </div>
        </div>
      </div>
    </div>
    <div v-if="trainData.type != 0" id="scoreBox" :style="[isfocus ? 'height: calc(100% - 330px)' : 'height:100%']" style="display: flex; flex-wrap: wrap; overflow: auto; justify-content: space-between">
      <div class="cardBox" style="display: flex; height: max-content; min-width: max-content" v-for="(v, index) of message" :style="[trainData.type > 1 ? 'min-width:16%' : 'width: 10%']">
        <div :class="{ messageBox: true, activeBox: activeIndex === index && trainData.status === 1 }" @click="inputFocus(index)">
          <div class="imgBox" style="">{{ v.font }}</div>
          <div class="layout-center containerBox" style="text-align: center">
            <div style="height: 20px" v-if="!inputeMethod || isfocus || trainData.status === 2" :style="[v.font != v.value && trainData.status === 2 ? 'color:red' : '']">{{ v.value }}</div>
            <div class="relative" style="width: 1px; height: 20px" v-if="trainData.status === 1 && isfocus === false && !inputeMethod" :class="[activeIndex === index ? 'active' : '']">
              <div class="writingBox" v-if="activeIndex === index && wubiData.show">
                <div style="width: calc(100% - 50px); height: 100%">
                  <div style="width: 100%; height: 50%; text-align: left; display: flex; align-items: center; padding-left: 10px; font-size: 18px">{{ wubiData.text }}</div>
                  <div style="width: 100%; height: 50%; display: flex; justify-content: flex-start; align-items: center">
                    <div v-for="(font, index2) of wubiData.activeArr" style="cursor: pointer; padding-left: 15px" :style="[activeText === index2 ? 'color:#84f749' : '']" @click.stop="selectText(font.text, $event)">{{ index2 + 1 }}.{{ font.text }}</div>
                  </div>
                </div>
                <div style="width: 50px; height: 100%" class="rightbtn">
                  <IconFont type="icon-left" style="font-size: 20px; color: white" @click="prevData"></IconFont>
                  <IconFont type="icon-right" style="font-size: 20px; color: white" @click="nextData"></IconFont>
                </div>
              </div>
            </div>
            <a-input style="text-align: center" v-if="inputeMethod && !isfocus && trainData.status === 1" :disabled="trainData.status != 1" v-model:value="v.value" @blur="statistics2" @change="inputFocus(index)" @focus="activeIndex = index"></a-input>
          </div>
        </div>
      </div>
    </div>

    <div v-else style="overflow: auto; height: 100%" class="wzBox">
      <div class="wzLine" style="font-size: 16px; border-bottom: 1px solid #555252; padding: 10px 10px; width: 100%" v-for="(v, index) of message" :style="[v.isFirst ? 'margin-left: 3em;width:calc( 100% - 3em)' : '']">
        <div style="text-align: center; display: flex; align-items: center; justify-content: flex-start; margin-bottom: 10px; word-spacing: 10px">
          {{ v.font }}
          <!--          <div v-for="t of v.font">-->
          <!--            {{t}}-->
          <!--          </div>-->
        </div>
        <div style="text-align: center" class="layout-left-bottom containerBox">
          <div style="font-size: 16px; height: 26px; text-align: left" v-if="!inputeMethod || trainData.status === 2">
            <span v-for="(f, index) of v.value" style="word-spacing: 10px; display: inline-block; min-width: 16px" :style="[f != v.font[index] && trainData.status === 2 ? 'color:red' : '']">
              {{ f }}
            </span>
          </div>
          <div class="relative" style="width: 1px; height: 26px; margin-left: 5px" v-if="trainData.status === 1 && !inputeMethod" :class="[activeIndex === index ? 'active' : '']">
            <div class="writingBox" v-if="activeIndex === index && wubiData.show">
              <div style="width: calc(100% - 50px); height: 100%">
                <div style="width: 100%; height: 50%; text-align: left; display: flex; align-items: center; padding-left: 10px; font-size: 18px">{{ wubiData.text }}</div>
                <div style="width: 100%; height: 50%; display: flex; justify-content: flex-start; align-items: center">
                  <span v-for="(font, index2) of wubiData.activeArr" style="cursor: pointer; padding-left: 12px" :style="[activeText === index2 ? 'color:#84f749' : '']" @click.prevent="selectText(font.text)">{{ index2 + 1 }}.{{ font.text }}</span>
                </div>
              </div>
              <div style="width: 50px; height: 100%" class="rightbtn">
                <IconFont type="icon-left" style="font-size: 20px; color: white" @click="prevData(true)"></IconFont>
                <IconFont type="icon-right" style="font-size: 20px; color: white" @click="nextData"></IconFont>
              </div>
            </div>
          </div>
          <a-input style="font-size: 16.7px; word-spacing: 10px" v-if="inputeMethod && trainData.status === 1" @change="statisticsSelcet" :disabled="trainData.status != 1" v-model:value="v.value" @focus="activeIndex = index"></a-input>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { nextTick, ref, toRefs, onMounted, watch, onBeforeUnmount } from 'vue'
import fontBank from '../../../../../../../common/utils/fontBank'
import { createFromIconfontCN } from '@ant-design/icons-vue'
import QQwubi from 'qq-wubi'
const IconFont = createFromIconfontCN({
  scriptUrl: window.iconUrl
})
export default {
  name: 'WZTrain',
  props: {
    message: Object,
    trainData: Object,
    activeIndex: Number,
    isfocus: Boolean,
    inputeMethod: Boolean
  },
  components: {
    IconFont
  },
  setup(props, content) {
    const activeIndex = ref(0)
    const inputIndex = ref(0)
    const activeMessage = ref(null)
    const { message, trainData, isfocus, inputeMethod } = toRefs(props)
    const wubiData = ref({
      text: '',
      mateArr: [],
      activeArr: [],
      page: 0,
      activeIndex: 7,
      show: false
    })
    const activeText = ref(0)
    const fonts = []
    const CZShow = ref(false)
    const WuBiZgBank = {
      code: fontBank.code.split(';'),
      font: fontBank.font.split(';')
    }
    WuBiZgBank.code.forEach((item, index) => {
      fonts.push({
        zg: item,
        text: WuBiZgBank.font[index]
      })
    })
    // activeMessage.value = message.value[0]
    onMounted(() => {
      if (trainData.value.status === 1) {
        window.addEventListener('keydown', keyCodeDown)
      }
    })
    watch(inputeMethod, newData => {
      if (newData && trainData.value.status === 1) {
        if (!isfocus.value) {
          nextTick(() => {
            document.querySelectorAll('.containerBox input')[activeIndex.value].focus()
          })
        } else {
          nextTick(() => {
            document.querySelectorAll('.containerBox input')[0].focus()
          })
        }
      }
    })
    watch(isfocus, newData => {
      if (newData) {
        activeMessage.value = message.value[activeIndex.value]
      }
      if (newData && inputeMethod.value) {
        nextTick(() => {
          document.querySelectorAll('.containerBox input')[0].focus()
        })
      }
      if (!newData && inputeMethod.value) {
        nextTick(() => {
          document.querySelectorAll('.containerBox input')[activeIndex.value].focus()
        })
      }
    })
    watch(
      trainData,
      () => {
        if (trainData.value.status === 1) {
          window.addEventListener('keydown', keyCodeDown)
        }
      },
      {
        deep: true
      }
    )
    const keyCodeDown = v => {
      if (!inputeMethod.value) {
        keyCodeDown1(v)
      } else {
        keyCodeDown2(v)
      }
    }
    //软件自带输入法时逻辑
    const keyCodeDown1 = v => {
      if (v.preventDefault) {
        v.preventDefault()
      } else {
        window.event.returnValue === false
      }
      //文章换行
      if (trainData.value.type === 0 && wubiData.value.show === false && v.keyCode === 13) {
        if (activeIndex.value < message.value.length - 1) {
          activeIndex.value++
          if (activeIndex.value > 4) {
            const box = document.querySelectorAll('.wzBox')
            box[0].scrollTop += 82
          }
        }
        statistics()
        return
      }
      //删除键
      if (v.keyCode === 8) {
        if (wubiData.value.show === false) {
          if (message.value[activeIndex.value].value === '') {
            if (activeIndex.value === 0) {
              trainData.value.errorNum = 0
              trainData.value.speed = 0
              return
            }
            message.value[activeIndex.value].isFocus = null
            activeIndex.value--
            if (trainData.value.type === 0) {
              statistics()
              statisticsSelcet()
            } else {
              statistics2()
            }
          } else {
            message.value[activeIndex.value].value = message.value[activeIndex.value].value.substring(0, message.value[activeIndex.value].value.length - 1)
            if (activeIndex.value === 0) {
              statisticsSelcet()
            }
          }
          return
        }
        wubiData.value.text = wubiData.value.text.substring(0, wubiData.value.text.length - 1)
        if (wubiData.value.text === '') {
          wubiData.value.show = false
        }
        wubiData.value.mateArr = fonts.filter(item => {
          const str = item.zg.substring(0, wubiData.value.text.length)
          activeText.value = 0
          if (str === wubiData.value.text) {
            return true
          } else {
            return false
          }
        })
        //当自己字体库未找到相应文字时，去qq-wubi查找
        if (wubiData.value.mateArr.length === 0) {
          const str = QQwubi.search(wubiData.value.text)
          if (str != wubiData.value.text) {
            wubiData.value.mateArr.push({
              code: wubiData.value.mateArr,
              text: str
            })
          }
        }
        assemble()
        return
      }
      //处理特殊符号
      if (!isNaN(Number(v.key)) && wubiData.value.show === false) {
        if (v.keyCode === 32 && trainData.value.type === 1) {
        } else {
          message.value[activeIndex.value].value += v.key
          return false
        }
      }
      //选择某个字
      if (!isNaN(Number(v.key)) || v.keyCode === 32) {
        if (wubiData.value.show === false) {
          if (trainData.value.type != 0) {
            if (activeIndex.value < message.value.length - 1) {
              activeIndex.value++
            }
            activeMessage.value = message.value[activeIndex.value]
            if (activeIndex.value % 10 === 0) {
              const box = document.querySelectorAll('#scoreBox')
              box[0].scrollTop = 92 * (activeIndex.value / 10 - 1)
            }
            statistics2()
          } else {
            message.value[activeIndex.value].value += ' '
            statisticsSelcet()
          }
          return
        }
        const num = v.keyCode === 32 ? activeText.value : Number(v.key) - 1
        message.value[activeIndex.value].value += wubiData.value.activeArr.length > 0 ? wubiData.value.activeArr[num].text : ''
        wubiData.value.show = false
        wubiData.value.text = ''
        activeText.value = 0
        message.value[activeIndex.value].isFocus = true
        if (trainData.value.type === 0) {
          statisticsSelcet()
        }
        return
      }
      //上下箭头选择文字
      if (v.keyCode === 38 && wubiData.value.show) {
        if (activeText.value === 0) {
          activeText.value = wubiData.value.page === 0 ? 0 : 6
          prevData()
        } else {
          activeText.value--
        }
      }
      if (v.keyCode === 40 && wubiData.value.show) {
        if (activeText.value === 6) {
          nextData()
          activeText.value = 0
        } else if (wubiData.value.activeArr.length - 1 != activeText.value) {
          activeText.value++
        }
      }
      //当输入不是五笔字根时结束逻辑
      if (v.key.length > 1) {
        return
      }
      wubiData.value.activeIndex = 7
      activeText.value = 0
      wubiData.value.page = 0
      //处理特殊符号
      const reg = /^[a-z]/
      if (!reg.test(v.key) || (!isNaN(Number(v.key)) && wubiData.value.show === false)) {
        // const arr = [",",".","'","\"","?",":",";"]
        switch (v.key) {
          case ',':
            message.value[activeIndex.value].value += '，'
            break
          case '.':
            message.value[activeIndex.value].value += '。'
            break
          case '?':
            message.value[activeIndex.value].value += '？'
            break
          case ':':
            message.value[activeIndex.value].value += '：'
            break
          case ';':
            message.value[activeIndex.value].value += '；'
            break
          case "'":
            message.value[activeIndex.value].value += '‘'
            break
          case '\\':
            message.value[activeIndex.value].value += '、'
            break
          case '\/':
            message.value[activeIndex.value].value += '、'
            break
          case '"':
            message.value[activeIndex.value].value += '”'
            break
          case '!':
            message.value[activeIndex.value].value += '！'
            break
        }
        // if(v.key=="."){
        //   message.value[activeIndex.value].value+="。"
        // }else {
        //   message.value[activeIndex.value].value+=v.key
        // }

        return false
      }
      wubiData.value.show = true
      nextTick(() => {
        const box = document.querySelectorAll('.writingBox')[0]
        const boxP = box.getBoundingClientRect()
        const screen = document.querySelectorAll('body')[0]
        const screenHeight = screen.clientHeight
        const screenWidth = screen.clientWidth
        // box.style.top = 20+"px"
        if (boxP.right > screenWidth) {
          box.style.right = 60 + 'px'
        }
        if (screenHeight - boxP.top < 100) {
          box.style.bottom = 60 + 'px'
        } else {
          box.style.top = 20 + 'px'
        }
      })
      wubiData.value.text += v.key
      wubiData.value.mateArr = fonts.filter(item => {
        const str = item.zg.substring(0, wubiData.value.text.length)
        if (str === wubiData.value.text) {
          return true
        } else {
          return false
        }
      })
      //当自己字体库未找到相应文字时，去qq-wubi查找
      if (wubiData.value.mateArr.length === 0) {
        const str = QQwubi.search(wubiData.value.text)
        if (str != wubiData.value.text) {
          wubiData.value.mateArr.push({
            code: wubiData.value.mateArr,
            text: str
          })
        }
      }
      assemble()
    }
    //使用系统安装的五笔输入发
    const keyCodeDown2 = v => {
      //文章
      if (trainData.value.type === 0 && v.keyCode === 13) {
        if (activeIndex.value < message.value.length - 1) {
          activeIndex.value++
          nextTick(() => {
            document.querySelectorAll('.containerBox input')[activeIndex.value].focus()
          })
        }
        statistics()
      }
      //词组
      if (v.keyCode === 32 && trainData.value.type === 1) {
        if (v.preventDefault) {
          v.preventDefault()
        } else {
          window.event.returnValue === false
        }
        message.value[activeIndex.value].isFocus = true
        activeIndex.value++
        activeMessage.value = message.value[activeIndex.value]
        statistics2()
        if (!isfocus.value) {
          nextTick(() => {
            document.querySelectorAll('.containerBox input')[activeIndex.value].focus()
          })
        }
      }
    }
    //统计正确，错误，码率 文章
    const statistics = () => {
      let correctNum = 0
      let errorNum = 0
      let valueLen = 0
      message.value.forEach(item => {
        valueLen = valueLen + item.value.length
        const arr = item.font.trim().split('')
        const valueArr = item.value.split('')
        if (item.value === '') {
          return false
        }
        for (let i in arr) {
          if (arr[i] === valueArr[i]) {
            correctNum++
          } else {
            errorNum++
          }
        }
      })
      trainData.value.accuracy = ((correctNum / (correctNum + errorNum)) * 100).toFixed(2)
      trainData.value.speed = (valueLen / (trainData.value.duration / 60)).toFixed(2)
      trainData.value.correctNum = correctNum
      trainData.value.errorNum = errorNum
    }
    //统计 文章选择字
    const statisticsSelcet = () => {
      let correctNum = 0
      let errorNum = 0
      let valueLen = 0
      message.value.forEach(item => {
        valueLen = valueLen + item.value.length
        const arr = item.font.trim().split('')
        const valueArr = item.value.split('')
        if (item.value === '') {
          return false
        }
        for (let i in valueArr) {
          if (arr[i] === valueArr[i]) {
            correctNum++
          } else {
            errorNum++
          }
        }
      })
      trainData.value.accuracy = ((correctNum / (correctNum + errorNum)) * 100).toFixed(2)
      trainData.value.speed = (valueLen / (trainData.value.duration / 60)).toFixed(2)
      trainData.value.correctNum = correctNum
      trainData.value.errorNum = errorNum
      if (correctNum === 0 && errorNum === 0) {
        trainData.value.accuracy = 0
      }
    }
    //统计正确，错误，码率 词组
    const statistics2 = () => {
      let errorNum = 0
      let correctNum = 0
      let fontLen = 0
      message.value.forEach(item => {
        if (item.isFocus === true && item.value.trim() != '') {
          fontLen += item.value.length
        }
        if (item.isFocus === true && item.value === item.font) {
          correctNum++
        } else if (item.isFocus === true && item.value != item.font) {
          errorNum++
        }
      })
      trainData.value.accuracy = correctNum != 0 ? ((correctNum / (errorNum + correctNum)) * 100).toFixed(2) : 0
      trainData.value.errorNum = errorNum
      trainData.value.correctNum = correctNum
      trainData.value.speed = fontLen != 0 ? (fontLen / (trainData.value.duration / 60)).toFixed(2) : 0
    }
    const inputFocus = index => {
      if (trainData.value.status === 1 && index != activeIndex.value) {
        activeMessage.value = message.value[index]
        activeIndex.value = index
        wubiData.value.text = ''
        wubiData.value.show = false
      }
    }
    const prevData = (type = false) => {
      if (wubiData.value.page === 0) {
        return false
      }
      if (type) {
        activeText.value = 0
      }
      wubiData.value.page--
      wubiData.value.activeIndex -= 7
      assemble()
    }
    const nextData = () => {
      if (wubiData.value.activeIndex > wubiData.value.mateArr.length) {
        return false
      }
      activeText.value = 0
      wubiData.value.page++
      wubiData.value.activeIndex += 7
      assemble()
    }
    //
    const selectText = (text, event) => {
      const e = arguments.callee.caller.arguments[0] || event
      if (window.event) {
        e.cancelBubble = true
      } else if (e && e.stopPropagation) {
        e.stopPropagation()
      }
      message.value[activeIndex.value].value += text
      // activeMessage.value =
      wubiData.value.show = false
      wubiData.value.text = ''
    }
    //组装输入法展示数据
    const assemble = () => {
      wubiData.value.activeArr = wubiData.value.mateArr.filter((item, index) => {
        if (index >= wubiData.value.activeIndex - 7 && index < wubiData.value.activeIndex) {
          return true
        } else {
          return false
        }
      })
    }
    onBeforeUnmount(() => {
      window.removeEventListener('keydown', keyCodeDown)
    })
    return {
      activeIndex,
      inputIndex,
      message,
      trainData,
      wubiData,
      activeText,
      activeMessage,
      isfocus,
      CZShow,
      inputeMethod,
      statisticsSelcet,
      prevData,
      nextData,
      selectText,
      inputFocus,
      statistics2,
      keyCodeDown
    }
  }
}
</script>

<style lang="less" scoped>
  :deep(.ant-input) {
    padding: 0px !important;
  }

  :deep(.ant-input-disabled) {
    border-bottom: 1px solid rgba(198, 187, 187, 0.5) !important;
    color: #b8a5a5 !important;
  }

  :deep([type='text']:focus) {
    --tw-ring-color: rgba(0, 0, 0, 0);
  }
  .writingBox{
    height: 83px;
    width: 347px;
    font-weight: 500;
    background: url("../../../../../../../assets/HJ/telexTrain/writeBg.png");
    position: absolute;
    z-index: 99;
    display: flex;
    color: white;
  }
  .HJ{
    .focust {
      background: url('../../../../../../../assets/HJ/telexTrain/focus.png') no-repeat;
      background-size: 100% 100%;
      padding: 20px;
    }
    .focusBox {
      width: 100%;
      height: max-content;
    }
    .writingBox {
      height: 83px;
      width: 347px;
      font-weight: 500;
      background: url('../../../../../../../assets/HJ/telexTrain/writeBg.png');
      position: absolute;
      z-index: 99;
      display: flex;
      color: white;
    }
    .lastwritingBox {
      bottom: 60px;
      height: 83px;
      width: 347px;
      font-weight: 500;
      background: url('../../../../../../../assets/HJ/telexTrain/writeBg.png');
      position: absolute;
      z-index: 99;
      display: flex;
      color: white;
    }
    .rightbtn {
      display: flex;
      justify-content: center;
      align-items: flex-end;
      padding-bottom: 10px;
    }
    .messageBox {
      width: 100%;
      border: 1px solid #354971;
      padding: 0 5px 10px;
      margin: 4px 6px 8px 6px;
      height: max-content;
      background-color: #122548;
      box-shadow: inset 0 60px 30px -60px rgb(26 53 107);
    }
    .activeBox {
      background: url('../../../../../../../assets/HJ/train/key-bg.jpg') no-repeat top center;
      background-size: 100% 100%;
      animation: glint 2s linear infinite;
      -webkit-animation: glint 2s linear infinite;
    }
    @keyframes letterA {
      0% {
        border-left: 1px solid white;
      }
      100% {
        border-left: 1px solid rgba(0, 0, 0, 0);
      }
    }
    .active {
      transition: all 1.5s;
      animation: letterA 1.5s;
      animation-iteration-count: infinite;
    }
    .imgBox {
      /*height: 60px;*/
      height: 48px;
      font-weight: bold;
      font-size: 16px;
      width: 100%;
      padding: 0 6px;
      display: flex;
      align-items: center;
      justify-content: center;
    }
    .imgBox img {
      width: 30%;
      margin-left: -4px;
    }
    .value {
      width: 100%;
      height: 26px;
      border: 1px solid #354971;
      background-color: #0d1c38;
      display: flex;
      border-radius: 2px;
      justify-content: center;
      align-items: center;
      cursor: pointer;
      text-align: center;
    }
  }
  .HJJ{
    .focust {
      background: url('../../../../../../../assets/HJJ/telexTrain/focus.png') no-repeat;
      background-size: 100% 100%;
      padding: 20px;
    }
    .focusBox {
      width: 100%;
      height: max-content;
    }
    .writingBox {
      height: 83px;
      width: 347px;
      font-weight: 500;
      background: url('../../../../../../../assets/HJJ/telexTrain/writeBg.png');
      position: absolute;
      z-index: 99;
      display: flex;
      color: white;
    }
    .lastwritingBox {
      bottom: 60px;
      height: 83px;
      width: 347px;
      font-weight: 500;
      background: url('../../../../../../../assets/HJJ/telexTrain/writeBg.png');
      position: absolute;
      z-index: 99;
      display: flex;
      color: white;
    }
    .rightbtn {
      display: flex;
      justify-content: center;
      align-items: flex-end;
      padding-bottom: 10px;
    }
    .messageBox {
      width: 100%;
      padding: 0 5px 10px;
      margin: 4px 6px 8px 6px;
      height: max-content;
      background: url('../../../../../../../assets/HJJ/postTrain/hanzi/messageBoxBg.png') no-repeat;
      background-size: 100% 100%;
    }

    .activeBox {
      background: url('../../../../../../../assets/HJJ/postTrain/hanzi/messageBoxBg-active.png') no-repeat;
      background-size: 100% 100%;
    }
    @keyframes letterA {
      0% {
        border-left: 1px solid white;
      }
      100% {
        border-left: 1px solid rgba(0, 0, 0, 0);
      }
    }
    .active {
      transition: all 1.5s;
      animation: letterA 1.5s;
      animation-iteration-count: infinite;
    }
    .imgBox {
      /*height: 60px;*/
      height: 48px;
      font-weight: bold;
      font-size: 16px;
      width: 100%;
      padding: 0 6px;
      display: flex;
      align-items: center;
      justify-content: center;
    }
    .imgBox img {
      width: 30%;
      margin-left: -4px;
    }
    .value {
      width: 100%;
      height: 26px;
      border: 1px solid #354971;
      background-color: #0d1c38;
      display: flex;
      border-radius: 2px;
      justify-content: center;
      align-items: center;
      cursor: pointer;
      text-align: center;
    }
  }
  .LJ{
    .focust{
      background: url("../../../../../../../assets/LJ/train/bigCardBg.png") no-repeat;
      background-size: 100% 100%;
      padding: 20px;
    }
    .focusBox{
      width: 100%;
      height: max-content;
    }
    .writingBox{
      height: 83px;
      width: 347px;
      font-weight: 500;
      background: url("../../../../../../../assets/LJ/telexTrain/writeBg.png");
      position: absolute;
      z-index: 99;
      display: flex;
      color: white;
    }
    .lastwritingBox{
      bottom: 60px;
      height: 83px;
      width: 347px;
      font-weight: 500;
      background: url("../../../../../../../assets/LJ/telexTrain/writeBg.png");
      position: absolute;
      z-index: 99;
      display: flex;
      color: white;
    }
    .rightbtn{
      display: flex;
      justify-content: center;
      align-items: flex-end;
      padding-bottom: 10px;
    }
    .messageBox{
      width: 100%;
      padding: 0 5px 10px;
      margin: 4px 6px 8px 6px;
      height: max-content;
      background: url("../../../../../../../assets/LJ/postTrain/hanzi/messageBoxBg.png") no-repeat ;
      background-size: 100% 100%;
    }

    .activeBox{
      background: url("../../../../../../../assets/LJ/postTrain/hanzi/messageBoxBg-active.png") no-repeat ;
      background-size: 100% 100%;
    }
    @keyframes letterA {
      0%{
        border-left: 1px solid white;
      }
      100%{
        border-left: 1px solid rgba(0,0,0,0);
      }
    }
    .active{
      transition: all 1.5s ;
      animation: letterA 1.5s;
      animation-iteration-count: infinite;
    }
    .imgBox{
      /*height: 60px;*/
      height: 48px;
      font-weight: bold;font-size: 16px;
      width: 100%;
      padding: 0 6px;
      display: flex;
      align-items: center;
      justify-content: center;
    }
    .imgBox img{
      width: 30%;
      margin-left: -4px;
    }
    .value{
      width: 100%;
      height: 26px;
      border: 1px solid #26332e;
      background-color: #0d1c38;
      display: flex;
      border-radius: 2px;
      justify-content: center;
      align-items: center;
      cursor: pointer;
      text-align: center;
    }
  }
  .KJ{
    .focust{
      background: url("../../../../../../../assets/KJ/train/bigCardBg.png") no-repeat;
      background-size: 100% 100%;
      padding: 20px;
    }
    .focusBox{
      width: 100%;
      height: max-content;
    }
    .writingBox{
      height: 83px;
      width: 347px;
      font-weight: 500;
      background: url("../../../../../../../assets/KJ/telexTrain/writeBg.png");
      position: absolute;
      z-index: 99;
      display: flex;
      color: white;
    }
    .lastwritingBox{
      bottom: 60px;
      height: 83px;
      width: 347px;
      font-weight: 500;
      background: url("../../../../../../../assets/KJ/telexTrain/writeBg.png");
      position: absolute;
      z-index: 99;
      display: flex;
      color: white;
    }
    .rightbtn{
      display: flex;
      justify-content: center;
      align-items: flex-end;
      padding-bottom: 10px;
    }
    .messageBox{
      width: 100%;
      padding: 0 5px 10px;
      margin: 4px 6px 8px 6px;
      height: max-content;
      background: url("../../../../../../../assets/KJ/postTrain/hanzi/messageBoxBg.png") no-repeat ;
      background-size: 100% 100%;
    }

    .activeBox{
      background: url("../../../../../../../assets/KJ/postTrain/hanzi/messageBoxBg-active.png") no-repeat ;
      background-size: 100% 100%;
    }
    @keyframes letterA {
      0%{
        border-left: 1px solid white;
      }
      100%{
        border-left: 1px solid rgba(0,0,0,0);
      }
    }
    .active{
      transition: all 1.5s ;
      animation: letterA 1.5s;
      animation-iteration-count: infinite;
    }
    .imgBox{
      /*height: 60px;*/
      height: 48px;
      font-weight: bold;font-size: 16px;
      width: 100%;
      padding: 0 6px;
      display: flex;
      align-items: center;
      justify-content: center;
    }
    .imgBox img{
      width: 30%;
      margin-left: -4px;
    }
    .value{
      width: 100%;
      height: 26px;
      border: 1px solid #26332e;
      background-color: #0d1c38;
      display: flex;
      border-radius: 2px;
      justify-content: center;
      align-items: center;
      cursor: pointer;
      text-align: center;
    }
  }
</style>
