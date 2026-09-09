<template>
  <div class="shadow fade-in" v-if="showMode" >
    <div class="shadowInfo">
      <div class="title">
        <div class="text">
          {{title}}
        </div>
      </div>
      <div class="bigbox relative">
        <slot name="center"></slot>
      </div>
      <div class="w-full bottominfo">
        <div class="close" @click="takeNoTestVisible"></div>
      </div>
    </div>
  </div>
</template>

<script>
  import {ref} from 'vue'
  export default {
    name: "ModelSelf",
    props:{
      title:String
    },
    setup(props,content){
      const showMode = ref(false)
      const takeNoTestVisible = ()=>{
        showMode.value = false
        // content.emit("takeNoTestVisible")
      }
      const openMode = ()=>{
        showMode.value = true
      }
      return{
        showMode,
        openMode,
        takeNoTestVisible,
      }
    }
  }
</script>

<style  lang="less" scoped >
  .shadow{
    position: fixed;
    z-index: 1000;
    top: 0;
    left:0 ;
    width: 100%;
    height: 100%;
    background: rgba(0,0,0,.5);
  .shadowTwo{
    height: 350px !important;
  .bigboxTwo{
    height: calc(350px - 56px) !important;
  }
  }
  .shadowInfo{
    box-shadow:inset 0 -120px 200px -120px rgba(22, 59, 100, 0.8) ;
    position: absolute;
    margin: auto;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    background-color: #172943;
    width: 1000px;
    height: 660px;
    background-image:url("../../assets/HJ/train/modal-bg.jpg");
    background-size: 1000px 160px;
    background-repeat:no-repeat ;
  .title{
    margin: 0 auto;
    width: 338px;
    height: 56px;
    background-image:url("../../assets/HJ/train/title-bg.png");
    background-size: 338px 56px;
    background-repeat:no-repeat ;
    position: relative;

  .text{
    width: 100%;
    height: 100%;
    background-image: -webkit-linear-gradient(bottom,#44aaff,#e2f2ff);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    text-align: center;
    letter-spacing: 10px;
    line-height: 56px;
    font-size: 30px;
    font-family:"Microsoft Yahei";
    font-weight: bold;
  }
  }
  .bigbox{
    /*background: linear-gradient(to bottom right ,#172842,#163a63);*/
    display: flex;
    height: calc(100% - 56px);
    padding-top: 20px;
    border-right: 1px solid #2d4f72;
    border-left: 1px solid #2d4f72;
    border-bottom: 1px solid #2d4f72;
  .left{
    position: relative;
    width: 120px;
    height:calc(650px - 47px) ;
  .border-right{
    position: absolute;
    height: 100%;
    top: 0;
    right: -5px;
    width: 10px;
  }
  .topBox{
    overflow-y: auto;
    margin-top: 20px;
    height:calc(100% - 70px) ;
  .leftNocheck{
  .leftBorder{
    height: 100%;
    position: absolute;
    left: 0;
    top: 0;
    width:2px;
    background: #6ebdff;
  }
  position: relative;
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 10px 0 20px;
  font-size: 15px;
  height: 37px;
  .leftText{
    /*margin-left: 10px;*/
  }
  .bg1{
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius:16px;
    padding: 0 4px;
    min-width:16px ;
    height: 16px;
    background: #354971;
    font-size: 14px;
  }
  .bg2{
    background: #6ebdff;
    color: #274666;
  }
  }
  .leftCheck{
    background-image:url("../../assets/HJ/train/tabs-check.jpg");
    background-size: 100%;
  }
  }
  .botBox{
    display: flex;
    justify-content: center;
    align-items: center;
    height: 50px;

  }
  /*border-right: ;*/
  }
  }
  .bottominfo{
    /*position: absolute;*/
    /*bottom:-100px;*/
    /*left: 0;*/
    height: 100px;
    display: flex;
    justify-content: center;
    align-items: center;
  .close{
    width: 31px;
    height: 31px;
    border: 2px solid #7b90af;
    border-radius: 50%;
    background: url("../../assets/HJ/ico/ico-clear.png") no-repeat center;
    cursor: pointer;
  }
  .close:hover{
    background: url("../../assets/HJ/ico/ico-clear-1.png") no-repeat center;
    animation: rotate 0.4s linear;
    border-color: #6ebdff;
  }
  }
  .bottominfo::before{
    content: '';
    width: 1px;
    height: 33px;
    background: url("../../assets/HJ/ico/ico-lone.png");
    position: absolute;
    top: 660px;
    left: 50%;
  }
  .bottominfoTwo{
    display: flex;
    justify-content: center;
    align-items: center;
    height: 100px;
  .close{
    width: 31px;
    height: 31px;
    border: 2px solid #7b90af;
    border-radius: 50%;
    background: url("../../assets/HJ/ico/ico-clear.png") no-repeat center;
    cursor: pointer;
  }
  .close:hover{
    background: url("../../assets/HJ/ico/ico-clear-1.png") no-repeat center;
    animation: rotate 0.4s linear;
    border-color: #6ebdff;
  }
  }
  .bottominfoTwo::before{
    content: '';
    width: 1px;
    height: 33px;
    background: url("../../assets/HJ/ico/ico-lone.png");
    position: absolute;
    top: 350px;
    left: 50%;
  }
  .center{
  .addClass{
    width: 100px;
    /*position: absolute;*/
    /*bottom: 0px;*/
    /*right: -230px;*/
  }
  /*width: calc(100% - 120px);*/
  /*padding-top: 11px;*/
  display: flex;
  flex-direction: column;
  .right{
    overflow-y: auto;
    width:calc(100%);
    /*margin-top: 20px;*/
    height: calc(565px - 40px);
    padding: 0 40px 0 30px;
    position: relative;
    border-right: 1px solid #2d4f72;
    border-left: 1px solid #2d4f72;
  }
  }
  .bottom{
    border-right: 1px solid #2d4f72;
    border-left: 1px solid #2d4f72;
    border-bottom: 1px solid #2d4f72;
  .botBtn{
    width: 110px;
    height: 32px;
    background-image:url("../../assets/HJ/train/button.png");
  }
  }
  .rights{
    border-right: 1px solid #2d4f72;
    border-left: 1px solid #2d4f72;
    border-bottom: 1px solid #2d4f72;
    overflow-y: auto;
    width:calc(100%);
    /*margin-top: 20px;*/
    height: calc( 350px - 56px );
    padding: 20px 40px 0 30px;
    position: relative;
  }
  }
  }
</style>