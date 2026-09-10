<template>
  <!--  <div class="w-full dashboard-page-bottom absolute"-->
  <!--       :style="{background:'url('+dashboardBottomBg+') center center / contain no-repeat'}">-->
  <!--    <div class="w-full h-full relative">-->
  <!--      <div class="w-full overflow-hidden dashboard-page-bottom-content absolute layout-center" style="bottom: 0;">-->
  <!--        <div class="w-3/4 h-full overflow-hidden layout-center">-->
  <!--          <div class="h-3/5 pl-4 pr-4" v-for="m in menus"  :style="{width: 'calc(100% / '+menus.length+')'}">-->
  <!--            <div class="h-full layout-center cursor-pointer-def"-->
  <!--                 @click="handleMenuClick(m)"-->
  <!--                 @mouseenter="handleMouseStatus(m,true)"-->
  <!--                 @mouseleave="handleMouseStatus(m,false)">-->
  <!--              <div class="h-3/4  w-full layout-center-h relative menu-icon" :style="{background:'url('+menuBg+')'}"-->
  <!--                   style="background-repeat: no-repeat;background-size: contain;background-position: bottom;">-->
  <!--&lt;!&ndash;                  <img :src="m.icon" class="h-full"/>&ndash;&gt;-->
  <!--                    <div class="absolute menu-item" style="height: 120px;width: 120px" :style="{background:'transparent url('+m.icon+') 0 0 no-repeat'}"></div>-->
  <!--&lt;!&ndash;                <div class="absolute menu-item"&ndash;&gt;-->
  <!--&lt;!&ndash;                     :style="{background:'transparent url('+m.iconF+') 0 0 no-repeat'}">&ndash;&gt;-->
  <!--&lt;!&ndash;                </div>&ndash;&gt;-->
  <!--              </div>-->
  <!--              <div class="h-1/4 w-full layout-center pt-2 text-base" style="color: #bce8f1;">-->
  <!--                {{ m.title }}-->
  <!--              </div>-->
  <!--            </div>-->
  <!--          </div>-->
  <!--        </div>-->
  <!--      </div>-->
  <!--    </div>-->
  <!--  </div>-->

  <div class="w-full " style="animation: grow 0.3s linear">
    <div class="w-full h-full relative layout-center">
      <div class="w-full h-full  layout-left-center" style="padding: 0 2%;">
        <img id="qwe" src="../../assets/HJ/canBg.png" style="display: none" alt="">
        <div class="h-3/5 pl-4 pr-4 relative menu homeMenu" v-for="(m ,index) in menus" ><!--:style="[index>0?'margin-left:-30px':'']"-->
          <div class="h-full  cursor-pointer-def" style="display: flex;justify-content: center"
               @click="handleMenuClick(m)"
               @mouseenter="handleMouseStatus(m,true,index)"
               @mouseleave="handleMouseStatus(m,false,index)">
            <div class=" menu-item" style="height: 120px;width: 120px;margin-top: 55px;margin-left: 10px" :style="{background:'transparent url('+m.icon+') 0 0 no-repeat'}"></div>
            <div class="h-1/4 w-full layout-center pt-2 text-base absolute text-bg fs_dispose" >
              {{ m.title }}
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
  export default {
    name: "NipMenus"
  }
</script>
<script setup>
  import dashboardBottomBg from '../../assets/HJ/main/dashboardBottomBg.png';
  import menuBg from '../../assets/HJ/main/menu-bg.png';
  import menuBg2 from '../../assets/HJ/menus/menu-bg.png';
  import {nextTick, onMounted, ref} from "vue";
  import CanvasMove from "../../views/manage/dashboard/js/canvas";
  import {fontSizeDispose} from "../../common/utils/Utils";

  const menus = ref([]);
  const fileUrl = ref(window.fileUrl);
  onMounted(() => {
    const routerData = JSON.parse(window.localStorage.getItem("userRouter"));
    routerData.forEach(r => {
      if(r.name!=='Dashboard' && r.name!=='SystemManage')
        menus.value.push({
          key: r.key,
          path: r.path,
          title: r.meta.title,
          icon: fileUrl.value + r.meta.icon,
          iconF: fileUrl.value + r.meta.iconF,
          in: null,
          height: r.meta.height
        })
    })
    nextTick(() => {
      fontSizeDispose();
    })
  })
  const move = ref({})
  const emit = defineEmits(['handleMenuClick'])
  const handleMouseStatus = (m, status,index) => {
    m.in = status;
    //
    // if(status){
    //   anime({
    //     targets:['.menu-item'],
    //     duration:1000,
    //     scale:1.1,
    //   })
    // }else {
    //   anime({
    //     targets:['.menu-item'],
    //     duration:1000,
    //     scale:1,
    //   })
    //   clearInterval(qwe)
    // }
  }
  const handleMenuClick = (e) => {
    emit("handleMenuClick", e);
  }

</script>

<style scoped>
  .text-bg{
    color: #ffffff;bottom: 75px;font-weight: bold;font-size: 22px;text-shadow:1px 3px 2px #010e22;letter-spacing: 3px;padding-left: 15px
  }
  .homeMenu{
    background-image: url("../../assets/HJ/menus/menu-bg.png");
    width: 300px;height: 300px;
    background-repeat: no-repeat;
    margin: 0 30px;
    transform: scale(1.2);
  }
  .homeMenu:hover{
    background-image: url("../../assets/HJ/menu.png");
    animation: icon2 1.5s steps(45) infinite;
  }
  .homeMenu:hover .text-bg{
    color: #da5d34;
  }

  .dashboard-page-bottom {
    bottom: 0;
    /*height: calc((450 / 1918) * 100vw);*/
    min-height: 235px;
    animation: grow 0.3s linear
  }

  .dashboard-page-bottom .dashboard-page-bottom-content {
    height: calc(((450 / 1918) * 100vw) * 0.6);
  }

  .homeMenu:hover .menu-item {
    animation: icon 1.5s steps(50) infinite;
  }

  @keyframes icon2 {
    from {
      background-position: 0px 0px;
    }
    to {
      background-position: -13500px  0px;
    }
  }

  @keyframes icon {
    from {
      background-position: 0 0;
    }
    to {
      background-position: -6000px 0;
    }
  }

  @keyframes grow {
    0% {
      bottom: calc(((450 / 1918) * 100vw) * -1);
    }
    100% {
      bottom: 0;
    }
  }

  @keyframes growBack {
    0% {
      bottom: 0;
    }
    100% {
      bottom: calc(((450 / 1918) * 100vw) * -1);
    }
  }

  @keyframes turn {
    0% {
      transform: rotate(0deg);
    }
    25% {
      transform: rotate(90deg);
    }
    50% {
      transform: rotate(180deg);
    }
    75% {
      transform: rotate(270deg);
    }
    100% {
      transform: rotate(360deg);
    }
  }

  @keyframes turnBack {
    0% {
      transform: rotate(360deg);
    }
    25% {
      transform: rotate(270deg);
    }
    50% {
      transform: rotate(180deg);
    }
    75% {
      transform: rotate(90deg);
    }
    100% {
      transform: rotate(0deg);
    }
  }
</style>