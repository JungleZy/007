<template>
    <div>
        <audio loop preload="auto" id="audio" :src="url"></audio>
        <div class="audio">
            <div style="padding: 115px 50px 0 50px;position: relative">
                <div class="progress">
                    <div class="volumeSlider voice">
                     <a-slider :tooltipVisible="false" v-model:value="currentTimeTwo" @change="progress" @afterChange="mouseupProgress"  :min="0" :max="maxCurrentTime" :step="1" ></a-slider>
                   </div>
                    <i class="labelTime" :style="{left:'calc('+(currentTimeTwo/maxCurrentTime*100)+'%'+' - 25px )'}">{{hms}}</i>
                </div>
            </div>
            <div class="audioButton" @click="audioButton">
                <div :class="{stop:playStop,play:!playStop}" ></div>
            </div>
        </div>
    </div>
</template>

<script>
   import {ref,onMounted,nextTick} from 'vue'
   import moment from 'moment'
   export default {
      name: "playAudio",
      props:{
         url:String
      },
      setup(props,content){
         const audio=ref(null);
         const playStop=ref(false);
         const maxCurrentTime=ref(0);
         const hms=ref('');
         const currentTimeTwo=ref(0);
         const interval=ref(null);
         onMounted(()=>{
            audio.value=document.getElementById("audio");
            let setInterVal = setInterval(function () {
               if (!isNaN(audio.value.duration)) {
                  maxCurrentTime.value=audio.value.duration;
                  intervalTime();
                  clearInterval(setInterVal);
               }
            }, 100);
         });
         const audioButton=()=>{
            playStop.value=!playStop.value;
            if (playStop.value) {
               audio.value.play();
            }else {
               audio.value.pause()
            }
         };
         const progress=()=>{
            clearInterval(interval.value);
            hms.value=moment.utc(currentTimeTwo.value*1000).format('mm:ss');
         };
         const intervalTime=()=>{
            interval.value=setInterval(()=>{
               currentTimeTwo.value=parseInt(audio.value.currentTime);
               hms.value=moment.utc(audio.value.currentTime*1000).format('mm:ss');
               if (currentTimeTwo.value==parseInt(audio.value.duration)){
                  currentTimeTwo.value=0
               }
            },200)
         };
         const mouseupProgress=()=>{
            audio.value.currentTime=Number(currentTimeTwo.value);
            if ('seekable' in audio.value){
               audio.value.seekable.start(0)
               audio.value.seekable.end(0)
            }
            intervalTime()
         };
         return{
            hms,
            audio,
            playStop,
            maxCurrentTime,
            currentTimeTwo,
            audioButton,
            progress,
            mouseupProgress
         }
      }
   }
</script>

<style  lang="less" scoped >
    .volumeSlider.voice {
        width: 100%;
        padding-bottom: 8px;
    }
    .progress{position: relative;margin-top: 5px}
    input[type=range] {
        -webkit-appearance: none;
        width: 100%;
        height:8px;
        border-radius: 100px; /*将轨道设为圆角的*/
        background: #354971;
        z-index: 5;
    }

    input[type=range]::-webkit-slider-thumb {
        -webkit-appearance: none;
        background-image: url("../../assets/HJ/mainPoints/hk.png");
        margin-top: -21px;
        height: 16px;
        width: 12px;
        border:none;
    }
    .fill{
        position: absolute;
        left:0;
        top:7px;
        width: 0;
        height: 8px;
        background-image: linear-gradient(90deg,#00d1f4,#008dff);
        border-radius: 100px;
    }
    .labelTime{
        position: absolute;
        width: 50px;
        color: #537ca6;
        text-align: center;
    }
    .audio{
        background-image: url("../../assets/HJ/mainPoints/back.png");
        height: 218px;
        width: 609px;
        margin: auto;
    }
    .audioButton{
        background-image: url("../../assets/HJ/mainPoints/button.png");
        height: 38px;
        width: 62px;
        margin: 20px auto;
        padding-left: 16px;
        padding-top: 2px;
    }
    .play{
        background-image: url("../../assets/HJ/mainPoints/paly.png");
        height: 34px;
        width: 34px;
    }
    .audioButton:hover .play{
        background-image: url("../../assets/HJ/mainPoints/playhover.png");
    }
    .stop{
        background-image: url("../../assets/HJ/mainPoints/stop.png");
        height: 34px;
        width: 34px;
    }
    .audioButton:hover .stop{
        background-image: url("../../assets/HJ/mainPoints/stophover.png");
    }
</style>