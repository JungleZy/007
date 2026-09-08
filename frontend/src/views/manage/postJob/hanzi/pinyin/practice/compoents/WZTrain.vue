<template>
  <div class="h-full w-full layout-left-top wz" >
    <div class="w-full layout-center" v-if="isfocus" style="padding: 40px 0;height: 330px">
      <div style="width: 50%;display: flex;height: 100%" class="focust">
        <div class="focusBox" style="height: 100%;">
          <div class="imgBox" style="font-size: 80px!important;height: 80%">{{activeMessage.font}}</div>
          <div class="layout-center focus" style="text-align: center;">
            <a-input style="text-align: center;max-width: 300px;height: 40px;font-size: 18px"
                     v-model:value="activeMessage.value"
                     @blur="statistics2"
                     @change="inputFocus(activeIndex)"></a-input>
          </div>
        </div>
      </div>
    </div>
    <div v-if="trainData.type!=4" id="scoreBox" :style="[isfocus?'height: calc(100% - 330px)':'height:100%']" style="display: flex;flex-wrap: wrap;overflow: auto">
      <div  class="cardBox" style="display: flex;height: max-content;min-width: max-content"
           v-for=" (v,index) of message" :style="[trainData.type>1?'min-width:14%':'width: 10%']" >
        <div class="messageBox"
             :class="[activeIndex===index&&trainData.status===1?'activeBox':'']">
          <div class="imgBox" style="" >{{v.font}}</div>
          <div class="layout-center" style="text-align: center;">
            <a-input style="text-align: center"
                     :disabled="trainData.status!=1||isfocus"
                     :style="[v.value!=v.font&&trainData.status===2?'color:red!important':'']"
                     v-model:value="v.value"
                     @blur="statistics2"
                     @change="inputFocus(index)"
                     @focus="activeIndex=index"></a-input>
          </div>
        </div>
      </div>
    </div>
    <div v-else class="wzLine" style="font-size: 16px;padding: 10px 10px;width: 100%" v-for=" (v,index) of message" :style="[v.isFirst?'margin-left: 3em;width:calc( 100% - 3em)':'',trainData.status===2?'border-bottom: 1px solid #555252;':'']">
      <div style="text-align: center;display: flex;align-items: center;justify-content: flex-start;margin-bottom: 10px;word-spacing: 10px;min-width: 10px" >
        {{v.font}}
<!--        <div v-for="t of v.font">-->
<!--           {{ t }}-->
<!--        </div>-->
      </div>
      <div style="font-size: 24px;text-align: center" class="layout-left-top">
        <div v-if="trainData.status===2" style="min-height: 20px;word-spacing: 10px">
          <span v-for="m of v.tfArr" style="font-size: 16px;" :style="[m.type?'':'color:red']">
            {{m.text}}
          </span>
        </div>
        <a-input v-else style="font-size: 16px;border-color: transparent;word-spacing: 10px" @change="statisticsSelect" :disabled="trainData.status!=1" v-model:value="v.value" @focus="activeIndex=index"></a-input>
      </div>
    </div>
  </div>
</template>

<script>
  import {nextTick, ref, toRefs, onMounted, watch, onBeforeUnmount} from 'vue'
  export default {
    name: "WZTrain",
    props:{
      message:Object,
      trainData:Object,
      activeIndex:Number,
      isfocus:Boolean,
    },
    setup(props,content){
      const activeIndex = ref(0)
      const inputIndex = ref(0)
      const activeMessage = ref({})
      const {message,trainData,isfocus} = toRefs(props)
      activeMessage.value = message.value[0]
      onMounted(()=>{
        if(trainData.value.status===1){
          window.addEventListener('keydown',keyCodeDown)
        }
      })
      watch(isfocus,(newData)=>{
        if(newData){
          activeMessage.value = message.value[activeIndex.value]
          nextTick(()=>{
            document.querySelectorAll(".wz input")[0].focus()
          })
        }
      })
      watch(trainData,()=>{
        if(trainData.value.status===1){
          window.addEventListener('keydown',keyCodeDown)
        }
      },{
        deep:true
      })
      const keyCodeDown = (v) =>{
        if(trainData.value.type===4){
          if(v.keyCode===9){
            if(v.preventDefault){
              v.preventDefault()
            }else {
              window.event.returnValue === false
            }
          }
          if(v.keyCode===13){
            if(activeIndex.value<message.value.length-1){
              activeIndex.value++
            }
            statistics()
            nextTick(()=>{
              document.querySelectorAll(".wz input")[activeIndex.value].focus()
            })
          }
          if(v.keyCode===8&&message.value[activeIndex.value].value===""){
            if(v.preventDefault){
              v.preventDefault()
            }else {
              window.event.returnValue === false
            }
            activeIndex.value--
            nextTick(()=>{
              document.querySelectorAll(".wz input")[activeIndex.value].focus()
            })
          }
        }else if(trainData.value.type===3){
          if(v.keyCode===32){
            if(v.preventDefault){
              v.preventDefault()
            }else {
              window.event.returnValue === false
            }
            statistics2()
            if(activeIndex.value<message.value.length-1){
              activeIndex.value++
            }
            activeMessage.value = message.value[activeIndex.value]
            if(isfocus.value&&activeIndex.value%7===0){
              const box = document.querySelectorAll("#scoreBox")
              box[0].scrollTop = 94*(activeIndex.value/7-1)
            }
            nextTick(()=>{
              document.querySelectorAll(".activeBox input")[0].focus()
            })
          }
          if(v.keyCode===8&&message.value[activeIndex.value-1]&&message.value[activeIndex.value].value===""){
            if(v.preventDefault){
              v.preventDefault()
            }else {
              window.event.returnValue === false
            }
            message.value[activeIndex.value].isFocus = false
            activeIndex.value--
            nextTick(()=>{
              document.querySelectorAll(".wz input")[activeIndex.value].focus()
              statistics2()
            })
          }
        }
        // content.emit('statistics')
      }
      //统计正确，错误，码率 文章
      const statistics = ()=>{
        let correctNum = 0
        let errorNum = 0
        let valueLen = 0
        message.value.forEach(item=>{
          valueLen = valueLen+item.value.length
          const arr = (item.font.trim()).split("")
          const valueArr = item.value.split("")
          if(item.value===""){
            return false
          }
          for (let i in arr){
            if(arr[i]===valueArr[i]){
              correctNum++
            }else {
              errorNum++
            }
          }
        })
        trainData.value.accuracy =(correctNum/(correctNum+errorNum)*100).toFixed(2)
        trainData.value.speed = (valueLen/(trainData.value.duration/60)).toFixed(2)
        trainData.value.correctNum = correctNum
        trainData.value.errorNum = errorNum
      }
      const statisticsSelect = ()=>{
        let correctNum = 0
        let errorNum = 0
        let valueLen = 0
        message.value.forEach(item=>{
          valueLen = valueLen+item.value.length
          const arr = (item.font.trim()).split("")
          const valueArr = item.value.split("")
          if(item.value===""){
            return false
          }
          for (let i in valueArr){

            if(arr[i]===valueArr[i]){
              correctNum++
            }else {
              errorNum++
            }
          }
        })
        trainData.value.accuracy =(correctNum/(correctNum+errorNum)*100).toFixed(2)
        trainData.value.speed = (valueLen/(trainData.value.duration/60)).toFixed(2)
        trainData.value.correctNum = correctNum
        trainData.value.errorNum = errorNum
        if(correctNum===0&&errorNum===0){
          trainData.value.accuracy = 0
        }
      }
      //统计正确，错误，码率 词组
      const statistics2 = ()=>{
        let errorNum = 0
        let correctNum = 0
        let fontLen = 0
        message.value.forEach(item=>{
          if(item.isFocus===true&&item.value.trim()!=""){
            fontLen+=item.value.length
          }
          if(item.isFocus===true&&item.value===item.font){
            correctNum++
          }else if(item.isFocus===true&&item.value!=item.font){
            errorNum++
          }
        })
        trainData.value.accuracy = correctNum!=0?(correctNum/(errorNum+correctNum)*100).toFixed(2):0
        trainData.value.errorNum= errorNum
        trainData.value.correctNum= correctNum
        trainData.value.speed= fontLen!=0?(fontLen/(trainData.value.duration/60)).toFixed(2) :0
      }
      const inputFocus = (index)=>{
        message.value[index].isFocus = true
      }
      onBeforeUnmount(()=>{
        window.removeEventListener("keydown", keyCodeDown)
      })
      return{
        activeIndex,
        inputIndex,
        message,
        trainData,
        isfocus,
        activeMessage,
        inputFocus,
        statistics2,
        keyCodeDown,
        statisticsSelect,
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

  :deep(.focust) {
    background: url('../../../../../../../assets/HJ/telexTrain/focus.png') no-repeat;
    background-size: 100% 100%;
    padding: 20px;
  }
  :deep(.focusBox) {
    width: 100%;
    height: max-content;
  }
 .HJ{
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
     background: url('../../../../../../../assets/HJ/train/key-bg.jpg') no-repeat top
     center;
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
    .messageBox{
      width: 100%;
      padding: 0 5px 10px;
      margin: 4px 6px 8px 6px;
      height: max-content;
      background: url("../../../../../../../assets/HJJ/postTrain/hanzi/messageBoxBg.png") no-repeat ;
      background-size: 100% 100%;
    }
    .activeBox{
      background: url("../../../../../../../assets/HJJ/postTrain/hanzi/messageBoxBg-active.png") no-repeat ;
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