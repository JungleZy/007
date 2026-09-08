<template>
  <div class="h-full w-full layout-left-top wz">
    <div style="font-size: 16px;border-bottom: 1px solid #555252;padding: 20px 0;" v-for=" (v,index) of message">
      <div style="text-align: center;">{{ v.font }}</div>
      <div style="font-size: 24px;padding:0px 3px;text-align: center" class="layout-left-top">
        <div class="relative layout-left-top" v-for="(zm,i) of v.pys"
             :style="[zm.trueOrfalse==false?'color:red':zm.trueOrfalse==true?'color:rgb(178 253 177)':'']">
          <!--                    <input v-if="activeIndex==index&&inputIndex==i&&trainData.status==1" type="text"  @keydown.prevent="keyCodeDown($event,zm,i,v.pys)"-->
          <!--                           style="width: 1px;padding: 0px;margin:0 2px;height: 30px;display: inline-block;bottom: 2px;-->
          <!--                            outline: none;border: 0px none #b8aeae;background: rgba(0,0,0,0);position: absolute;left: 0">-->
          <div style="font-size: 24px;padding-left: 2px;border-left: 1px solid transparent"
               :class="[activeIndex==index&&inputIndex==i&&trainData.status==1?'active':'']">
            {{ zm.py }}
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import {nextTick, ref, toRefs, onMounted, watch, onBeforeUnmount} from 'vue'

export default {
  name: "WZTrain",
  props: {
    message: Array,
    trainData: Object
  },
  setup(props, content) {
    const activeIndex = ref(0)
    const inputIndex = ref(0)
    const activeMessage = ref(null)
    const {message, trainData} = toRefs(props)
    watch(message, (newD) => {
      if (newD !== null) {
        activeMessage.value = message.value[0]
        activeMessage.value.isFocus = true
      }
    }, {
      immediate: true
    })
    // activeMessage.value = message.value[0]
    onMounted(() => {
      window.addEventListener('keydown', keyCodeDown)
    })
    const keyCodeDown = (v, item, index, pys) => {
      if (v.preventDefault) {
        v.preventDefault()
      } else {
        window.event.returnValue == false
      }
      if (v.keyCode == 8 && inputIndex.value == 0 && activeIndex.value != 0) {
        //删除键
        activeIndex.value--
        activeMessage.value = message.value[activeIndex.value]
        inputIndex.value = activeMessage.value.pys.length - 1
        activeMessage.value.pys[inputIndex.value].trueOrfalse = null
        activeMessage.value.trueOrfalse = null
        activeMessage.value.isFocus = true
      } else if (v.keyCode == 8 && inputIndex.value != 0) {
        //删除键
        inputIndex.value--
        activeMessage.value.pys[inputIndex.value].trueOrfalse = null
        activeMessage.value.isFocus = true
      } else if (v.keyCode != 8) {
        let key = v.key
        if (v.key == "Process") {
          key = v.code.substring(3, 4)
        }

        if (key.toLowerCase() == activeMessage.value.pys[inputIndex.value].py) {
          activeMessage.value.pys[inputIndex.value].trueOrfalse = true
        } else {
          activeMessage.value.pys[inputIndex.value].trueOrfalse = false
        }
        inputIndex.value++
      }
      if (inputIndex.value == activeMessage.value.pys.length) {
        const trueOrfalse = activeMessage.value.pys.some(p => p.trueOrfalse == false)
        if (trueOrfalse) {
          activeMessage.value.trueOrfalse = false
        } else {
          activeMessage.value.trueOrfalse = true
        }
        activeIndex.value++
        inputIndex.value = 0
        activeMessage.value = message.value[activeIndex.value]
        activeMessage.value.isFocus = true
      }
      content.emit('statistics')
    }
    //统计正确，错误，码率
    const statistics = () => {
      let errorNum = 0
      let correctNum = 0
      message.value.forEach(item => {
        if (item.isFocus) {
          switch (item.trueOrfalse) {
            case true:
              correctNum++
              break;
            case false:
              errorNum++
              break;
          }
        }
      })
      trainData.value.accuracy = correctNum != 0 ? (correctNum / (errorNum + correctNum) * 100).toFixed(2) : 0
      trainData.value.errorNum = errorNum
      trainData.value.correctNum = correctNum
    }
    onBeforeUnmount(() => {
      window.removeEventListener("keydown", keyCodeDown)
    })
    return {
      activeIndex,
      inputIndex,
      message,
      trainData,
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

:deep(.wz [type='text']:focus) {
  background: red !important;
}

:deep([type='text']:focus) {
  --tw-ring-color: rgba(0, 0, 0, 0);
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

</style>