<template>
  <div class="h-full w-full layout-left-top wz">
    <div class="wzLine" style="font-size: 16px; padding: 10px 10px; width: 100%" v-for="(v, index) of message" :style="[v.isFirst ? 'margin-left: 3em;width:calc( 100% - 3em)' : '', trainData.status === 2 ? 'border-bottom: 1px solid #555252;' : '']">
      <div style="text-align: center; display: flex; align-items: center; margin-bottom: 10px">
        {{ v.font }}
      </div>
      <div style="font-size: 24px; text-align: center" class="layout-left-top">
        <div v-if="trainData.status === 2">
          <span v-for="m of v.tfArr" style="padding-right: 5px; font-size: 16px" :style="[m.type ? '' : 'color:red']">
            {{ m.text }}
          </span>
        </div>
        <a-input v-else style="font-size: 16px" :disabled="trainData.status != 1" v-model:value="v.value" @focus="activeIndex = index"></a-input>
      </div>
    </div>
  </div>
</template>

<script>
import { nextTick, ref, toRefs, onMounted, watch, onBeforeUnmount } from 'vue'
export default {
  name: 'WZTrain',
  props: {
    message: Object,
    trainData: Object,
    activeIndex: Number
  },
  setup(props, content) {
    const activeIndex = ref(0)
    const inputIndex = ref(0)
    const activeMessage = ref(null)
    const { message, trainData } = toRefs(props)
    // activeMessage.value = message.value[0]
    onMounted(() => {
      if (trainData.value.status === 1) {
        window.addEventListener('keydown', keyCodeDown)
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
      if (v.keyCode === 9) {
        if (v.preventDefault) {
          v.preventDefault()
        } else {
          window.event.returnValue === false
        }
      }
      if (v.keyCode === 13) {
        if (activeIndex.value < message.value.length - 1) {
          activeIndex.value++
        }
        statistics()
        nextTick(() => {
          document.querySelectorAll('.wz input')[activeIndex.value].focus()
        })
      }
      if (v.keyCode === 32) {
        statistics2()
      }
      if (v.keyCode === 8 && message.value[activeIndex.value].value === '') {
        if (v.preventDefault) {
          v.preventDefault()
        } else {
          window.event.returnValue === false
        }
        activeIndex.value--
        nextTick(() => {
          document.querySelectorAll('.wz input')[activeIndex.value].focus()
          statistics2()
        })
      }
      // content.emit('statistics')
    }
    //统计正确，错误，码率 文章
    const statistics = () => {
      let correctNum = 0
      let errorNum = 0
      let valueLen = 0
      message.value.forEach(item => {
        valueLen = valueLen + item.value.length
        const arr = item.font.split(' ')
        const valueArr = item.value.split(' ')
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
    //统计正确，错误，码率 文章
    const statistics2 = () => {
      let correctNum = 0
      let errorNum = 0
      let valueLen = 0
      message.value.forEach(item => {
        valueLen = valueLen + item.value.length
        const arr = item.font.split(' ')
        const valueArr = item.value.split(' ')
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
    }
    onBeforeUnmount(() => {
      window.removeEventListener('keydown', keyCodeDown)
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
/*input{*/
/*  width: 30px;*/
/*  background: rgba(0,0,0,0)!important;*/
/*  border-color: rgba(0,0,0,0)!important;*/
/*  text-align: center;*/
/*}*/
:deep {
  .ant-input {
    padding: 0px !important;
  }
  .ant-input-disabled {
    border-bottom: 1px solid rgba(198, 187, 187, 0.5) !important;
    color: #b8a5a5 !important;
  }
  .wz [type='text']:focus {
    background: red !important;
  }
  [type='text']:focus {
    --tw-ring-color: rgba(0, 0, 0, 0);
  }
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
  background: url('../../../../../../assets/HJ/train/key-bg.jpg') no-repeat top center;
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
</style>
