<template>
  <div class="mistake">
    <div style="width: 100%;flex-wrap: wrap;justify-content: center;display: flex;margin-bottom: 30px;margin-top: 10px" >
      <div class="mistakeBtn" v-for="(v,index) of btn2" :class="[active2==index?'mistakeBtnActive':'']" @click="selectType2(index)">
        {{v}}
      </div>
    </div>
    <div class="container">
      <div style="width: 100%;flex-wrap: wrap;justify-content: center;display: flex;margin-bottom: 10px;margin-top: 10px;" v-if="active2==0">
        <div class="ZGbtns" v-for="(v,index) of btn" :class="[active==index?'activeBtn1':'']" @click="selectType(index)">
          <div class="left">
            <div class="left-top"></div>
            <div class="left-bottom"></div>
          </div>
          <div class="center layout-center">{{v}}</div>
          <div class="right">
            <div class="right-top"></div>
            <div class="right-bottom"></div>
          </div>
        </div>
      </div>
      <div style="height: calc(100% - 10px);overflow:auto;">
        <div v-if="active2==0" style="height: calc(100% - 70px);overflow: auto">

          <div v-for="v of activeType" style="margin: 10px 0;" >
            <div style="color: #e8a829;font-size: 18px;font-weight: bold" class="layout-left-center">{{v.title}} <div class="trangle"></div></div>
            <div v-for="i of v.example"  style="margin: 10px" class="layout-left-top">
              <div style="height: 5px;width: 5px;background:white;margin: 7px 0px 0px 0px;border-radius: 50%"></div>
            <div style="padding-left: 10px">
              <div style="word-spacing: 10px">{{i.title}}</div>
              <div  class="layout-left-top">
                <div style="margin-right: 10px">例 :</div>
                <div>
                  <div style="word-spacing: 10px" v-for="k of i.example">{{k}}</div>
                </div>
              </div>
            </div>
            </div>
          </div>
        </div>

        <div  v-if="active2==1">
          <div class="questionTitle">{{activeQuestionIndex+1}}、{{activeQuestion.title}}</div>
          <div class="layout-left-top" style="margin-bottom: 10px">
            <div class="codeText">正确报文</div>
            <div v-for="item of activeQuestion.code" v-if="activeQuestion.type!=1" class="layout-center code">{{item}}</div>
            <div style="width: 600px" class="layout-center" v-if="activeQuestion.type==1">
              <div v-for="(item,index) of activeQuestion.code" class="layout-center code" :style="[index%10==9?'border-right:1px solid #70a3b8!important':'']">{{item}}</div>
            </div>
          </div>
          <div class="layout-left-top" style="margin-bottom: 10px">
            <div  class="codeText">已输入报文</div>
            <div v-if="activeQuestion.type!=1" v-for="(item,index) of activeQuestion.compileCode.split(' ')" :style="[item!==activeQuestion.code[index]&&item.length>0?'color:  red':'']" class="layout-center code">{{item}}</div>
            <div style="width: 600px" class="layout-center" v-if="activeQuestion.type==1">
              <div v-for="(item,index) of activeQuestion.compileCode.split(' ')" :style="[item!==activeQuestion.code[index]&&item.length>0?'color:  red':'',index%10==9?'border-right: 1px solid #70a3b8':'']" class="layout-center code">{{item}}</div>
            </div>
          </div>
          <a-input v-model:value="activeQuestion.value" @change="activeQuestion.fun(activeQuestion)" style="font-size: 20px;word-spacing: 10px"></a-input>
          <a-input v-if="activeQuestion.value2!==undefined" v-model:value="activeQuestion.value2" @change="activeQuestion.fun(activeQuestion)" style="font-size: 20px;word-spacing: 10px"></a-input>
          <div class="w-full " style="bottom: 0;padding-top: 10px" >
            <div class="w-full h-full layout-side">
              <div class="pre" @click="prev">
                <div class="text1" >
                  {{(activeQuestionIndex==0)?'无上一题':'上一题'}}
                </div>
              </div>
              <div class="next" @click="next">
                <div class="text2">
                  {{(activeQuestionIndex < questions.length-1)?'下一题':'无下一题'}}
                </div>
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
    name: "MistakeTrain"
  }
</script>
<script setup>
  import mistake from "./js/mistake";
  import {ref} from 'vue'
  const btn = ref(['改错','多少组','多少行','页标错误'])
  const btn2 = ref(['教程','练习'])
  const active = ref(0)
  const active2 = ref(0)
  const activeType = ref(mistake.mistake)
  const questions = ref(mistake.question)
  const activeQuestion = ref(questions.value[0])
  const activeQuestionIndex = ref(0)
  const selectType = (index)=>{
    active.value = index
    switch (index) {
      case 0:
        activeType.value = mistake.mistake
        break;
      case 1:
        activeType.value = mistake.group
        break;
      case 2:
        activeType.value = mistake.line
        break;
      case 3:
        activeType.value = mistake.page
        break;
    }
  }
  const selectType2 = (index)=>{
    active2.value = index
  }
  const next = ()=>{
    if(activeQuestionIndex.value<questions.value.length-1)
      activeQuestionIndex.value++
    activeQuestion.value = questions.value[activeQuestionIndex.value]
  }
  const prev = ()=>{
    if(activeQuestionIndex.value>0)
      activeQuestionIndex.value--
    activeQuestion.value = questions.value[activeQuestionIndex.value]
  }
</script>
<style lang="less" scoped>
  .questionTitle{
    font-size: 16px;
    margin-bottom: 30px;
  }
  .trangle{
    background-image: url("../../../assets/HJ/preTrain/trangle.png");
    height: 14px;
    width: 16px;
    margin: 10px;
  }
  .mistakeBtn{
    background-image: url("../../../assets/HJ/preTrain/mistakeBtn.png");
    width: 152px;
    height: 43px;
    line-height: 43px;
    font-weight: bold;
    color: white;
    text-align: center;
    margin-right: 10px;
    font-size: 18px;
    cursor: pointer;
  }
  .mistakeBtnActive{
    background-image: url("../../../assets/HJ/preTrain/mistakeBtn-hover.png");
    color: #814200;
  }
  .container{
    box-shadow: 0px 0px 8px #253d50 inset;
    border: solid 1px #253d50;
    width: 100%;
    height: calc(100% - 43px - 74px);
    padding: 20px;
    background-image: url("../../../assets/HJ/preTrain/left-bottom.png"),url("../../../assets/HJ/preTrain/right-top.png");
    background-position: left bottom,right top;
    background-repeat: no-repeat;
  }
  .pre {
    width: 288px;
    height: 40px;
    font-size: 16px;
    cursor: pointer;
    color: #a6c9f2;
    line-height: 40px;
    position: relative;
    background-image: url("../../../assets/HJ/basicTheory/studyManage/analyze/pre.png");
    background-size: 100%;
  }

  .pre:hover {
    color: #ffffff;
    /*background-image: url("../../../assets/HJ/basicTheory/studyManage/analyze/pre-hover.png");*/
  }

  .next {
    width: 288px;
    height: 40px;
    color: #a6c9f2;
    line-height: 40px;
    font-size: 16px;
    position: relative;
    cursor: pointer;
    background-image: url("../../../assets/HJ/basicTheory/studyManage/analyze/next.png");
  }

  .next:hover {
    color: #ffffff;
    /*background-image: url("../../../assets/HJ/basicTheory/studyManage/analyze/next-hover.png");*/
  }
  .text1 {
    position: absolute;
    left: 90px;
    width: 170px;
    text-align: center;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .text2 {
    position: absolute;
    right: 90px;
    width: 170px;
    text-align: center;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .code{
    height: 30px;width: 60px;
    border-top: 1px solid #70a3b8;
    border-bottom: 1px solid #70a3b8;
    border-left: 1px solid #70a3b8;
    font-size: 16px;
  }
  .code:last-child{
    border-right: 1px solid #70a3b8;
  }
  .codeText{
    width: 100px;
    text-align: right;
    padding-right: 20px;
  }
  .mistake{
    width: 80%;
    margin: 0 auto;
    height: calc(100% - 20px - 74px);
    overflow: hidden;
    padding: 10px;
  }
  .ZGbtns{
    padding: 0px 0px;
    height: 29px;
    margin: 4px 4px 10px 4px;
    cursor:pointer;
    display: flex;
    align-items: center;
    justify-content: center;
    .left{
      position: relative;
      width: 8px;
      height: 29px;
      .left-top{
        height: 19px;
        position: absolute;
        top: -2px;
        right: 5px;
        border-left: 1px solid #70a3b8;
        transform: rotateZ(40deg);
      }
      .left-bottom{
        height: 19px;
        position: absolute;
        bottom: -2px;
        right: 5px;
        border-left: 1px solid #70a3b8;
        transform: rotate(-40deg);
      }
    }
    .center{
      border-top: 1px solid #70a3b8;
      border-bottom: 1px solid #70a3b8;
      width: 67px;
      height: 29px;
      font-weight: bold;
    }
    .right{
      position: relative;
      width: 8px;
      height: 29px;
      .right-top{
        height: 19px;
        position: absolute;
        top: -2px;
        left: 5px;
        border-left: 1px solid #70a3b8;
        transform: rotateZ(-40deg);
      }
      .right-bottom{
        height: 19px;
        position: absolute;
        bottom: -2px;
        left: 5px;
        border-left: 1px solid #70a3b8;
        transform: rotate(40deg);
      }
    }
  }
  .activeBtn1 div,.activeBtn1 .left div,.activeBtn1 .right div,.btn1:hover div,.btn1:hover .left div,.btn1:hover .right div{
    border-color: #e8a829!important;
    color: #e8a829!important;
  }

</style>