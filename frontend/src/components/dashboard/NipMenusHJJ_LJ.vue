<template>
  <div class="w-full" style="animation: grow 0.3s linear">
    <div class="w-full h-full relative layout-center">
      <div
        class="w-full h-full bgbox layout-center"
        style="flex-wrap: nowrap !important"
      >
        <div
          class="relative menu"
          v-for="(m, index) in menus"
          :key="index"
          :class="[
            m.path != 'equipmentOperation' && m.path != 'networkUsing'
              ? 'homeMenu'
              : 'homeMenu2',
          ]"
        >
          <div
            class="h-full cursor-pointer-def animate__animated animate__zoomIn bgbox"
            style="display: flex; justify-content: center"
          >
            <div class="menuBox">
              <div
                class="w-full layout-center pt-2 text-base text-bg layout-center"
                @click="handleMenuClick(m)"
              >
                {{ m.title }}
              </div>
              <div class="menu-item" style=""></div>
            </div>
          </div>
          <div
            class="itemsBox animate__animated animate__zoomIn bgbox"
            v-if="m.path != 'equipmentOperation' && m.path != 'networkUsing'"
          >
            <div class="items">
              <template v-for="v of m.children" :key="v">
                <div
                  class="item layout-center"
                  v-if="v.meta.isMenu"
                  @click="handleItemClick(v, m)"
                >
                  {{ v.meta.title }}
                </div>
              </template>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
  export default {
    name: "NipMenus",
  };
</script>
<script setup>
  import dashboardBottomBg from "../../assets/HJJ/main/dashboardBottomBg.png";
  import menuBg from "../../assets/HJJ/main/menu-bg.png";
  import menuBg2 from "../../assets/HJJ/menus/menu-bg.png";
  import { onMounted, ref, nextTick } from "vue";
  import { useRouter, useRoute } from "vue-router";
  const router = useRouter();
  const route = useRoute();
  const menus = ref([]);
  const fileUrl = ref(window.fileUrl);
  onMounted(() => {
    const routerData = JSON.parse(window.localStorage.getItem("userRouter"));
    routerData.forEach((r) => {
      if (r.name !== "Dashboard" && r.name !== "SystemManage")
        menus.value.push({
          key: r.key,
          path: r.path,
          title: r.meta.title,
          icon: fileUrl.value + r.meta.icon,
          iconF: fileUrl.value + r.meta.iconF,
          in: null,
          height: r.meta.height,
          children: r.children,
        });
    });
  });
  const move = ref({});
  const emit = defineEmits(["handleMenuClick"]);
  const handleMenuClick = (e) => {
    handleItemClick(e.children[0], e);
    // let r =null
    // router.getRoutes().forEach(item=>{
    //   if(("/preview/"+e.path) == item.path){
    //     r = item
    //   }
    // })
    // emit("handleMenuClick", e);
  };
  const handleItemClick = (e, m) => {
    emit("handleMenuClick", e, m);
  };
</script>

<style scoped lang="less">
  @media (max-width: 1500px) {
    .bgbox {
      transform: scale(0.9);
    }
  }
  @media (max-width: 1400px) {
    .bgbox {
      transform: scale(0.8);
    }
    .menuBox {
      transform: scale(1.2);
    }
    .itemsBox {
      padding-bottom: 15% !important;
    }
  }
  @keyframes icon2 {
    from {
      background-position: 0px 0px;
    }
    to {
      background-position: -13500px 0px;
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
  .menuBox {
    display: flex;
    flex-direction: column;
    justify-content: center;
    align-items: center;
    padding-bottom: 10%;
  }
  .animate__animated.animate__zoomIn {
    animation-duration: 0.3s;
    backface-visibility: hidden;
  }
  .cursor-pointer-def {
    display: flex !important;
  }

  .homeMenu:hover .cursor-pointer-def {
    display: none !important;
  }
  .itemsBox {
    display: none;
    width: 100%;
    height: 100%;
    padding-bottom: 1%;
  }
  .items {
    display: flex;
    height: 100%;
    align-items: center;
    justify-content: center;
    flex-direction: column;
    padding-bottom: 40px;
  }
  .homeMenu:hover .itemsBox {
    display: block !important;
  }
  .homeMenu:hover .text-bg {
    color: #da5d34;
  }

  .dashboard-page-bottom {
    bottom: 0;
    /*height: calc((450 / 1918) * 100vw);*/
    min-height: 235px;
    animation: grow 0.3s linear;
  }
  .dashboard-page-bottom .dashboard-page-bottom-content {
    height: calc(((450 / 1918) * 100vw) * 0.6);
  }
  .HJJ{
    .text-bg {
      color: #ffffff;
      top: 50px;
      font-weight: bold;
      font-size: 22px;
      text-shadow: 1px 3px 2px #010e22;
      letter-spacing: 3px;
      background: url("../../assets/HJJ/menus/itemBG.png");
      width: 172px;
      height: 62px;
      padding-left: 8px;
      padding-bottom: 15px;
    }
    .homeMenu {
      background-image: url("../../assets/HJJ/menus/menu-bg.png");
      background-size: 100%;
      width: 347px;
      height: 310px;
      background-repeat: no-repeat;
      /*margin: 0 30px;*/
      /*transform: scale(1.2);*/
      margin-right: 1%;
    }
    .homeMenu2 {
      background-image: url("../../assets/HJJ/menus/menu-bg.png");
      background-size: 100%;
      width: 347px;
      height: 310px;
      background-repeat: no-repeat;
      /*margin: 0 30px;*/
      margin-right: 1%;
    }
    .item {
      cursor: pointer;
      background: url("../../assets/HJJ/menus/itemBG.png");
      width: 172px;
      height: 56px;
      font-size: 18px;
      font-weight: bold;
      padding-top: 4px;
      margin: 5px 0;
    }
    .item:hover {
      background: url("../../assets/HJJ/menus/itemBG-active.png");
    }
    .menu-item {
      height: 117px;
      width: 148px;
      background: transparent url("../../assets/HJJ/menus/icon.png") 0 0 no-repeat;
    }
  }
  .LJ{
    .text-bg {
      color: #ffffff;
      top: 50px;
      font-weight: bold;
      font-size: 22px;
      text-shadow: 1px 3px 2px #010e22;
      letter-spacing: 3px;
      background: url("../../assets/LJ/menus/itemBG.png");
      width: 172px;
      height: 62px;
      padding-left: 8px;
      padding-bottom: 15px;
    }
    .homeMenu {
      background-image: url("../../assets/LJ/menus/menu-bg.png");
      background-size: 100%;
      width: 347px;
      height: 310px;
      background-repeat: no-repeat;
      /*margin: 0 30px;*/
      /*transform: scale(1.2);*/
      margin-right: 1%;
    }
    .homeMenu2 {
      background-image: url("../../assets/LJ/menus/menu-bg.png");
      background-size: 100%;
      width: 347px;
      height: 310px;
      background-repeat: no-repeat;
      /*margin: 0 30px;*/
      margin-right: 1%;
    }
    .item {
      cursor: pointer;
      background: url("../../assets/LJ/menus/itemBG.png");
      width: 172px;
      height: 56px;
      font-size: 18px;
      font-weight: bold;
      padding-top: 4px;
      margin: 5px 0;
    }
    .item:hover {
      background: url("../../assets/LJ/menus/itemBG-active.png");
    }
    .menu-item {
      height: 117px;
      width: 148px;
      background: transparent url("../../assets/LJ/menus/icon.png") 0 0 no-repeat;
    }
  }
  .GD{
    .text-bg {
      color: #ffffff;
      top: 50px;
      font-weight: bold;
      font-size: 22px;
      text-shadow: 1px 3px 2px #010e22;
      letter-spacing: 3px;
      background: url("../../assets/GD/menus/itemBG.png");
      width: 172px;
      height: 62px;
      padding-left: 8px;
      padding-bottom: 15px;
    }
    .homeMenu {
      background-image: url("../../assets/GD/menus/menu-bg.png");
      background-size: 100%;
      width: 347px;
      height: 360px;
      background-repeat: no-repeat;
      /*margin: 0 30px;*/
      /*transform: scale(1.2);*/
      margin-right: 1%;
    }
    .homeMenu2 {
      background-image: url("../../assets/GD/menus/menu-bg.png");
      background-size: 100%;
      width: 347px;
      height: 360px;
      background-repeat: no-repeat;
      /*margin: 0 30px;*/
      margin-right: 1%;
    }
    .item {
      cursor: pointer;
      background: url("../../assets/GD/menus/itemBG.png");
      width: 172px;
      height: 56px;
      font-size: 18px;
      font-weight: bold;
      padding-top: 4px;
      margin: 5px 0;
    }
    .item:hover {
      background: url("../../assets/GD/menus/itemBG-active.png");
    }
    .menu-item {
      height: 117px;
      width: 148px;
      background: transparent url("../../assets/GD/menus/icon.png") 0 0 no-repeat;
    }
  }
  .KJ{
    .text-bg {
      color: #ffffff;
      top: 50px;
      font-weight: bold;
      font-size: 22px;
      text-shadow: 1px 3px 2px #010e22;
      letter-spacing: 3px;
      background: url("../../assets/KJ/menus/itemBG.png");
      width: 172px;
      height: 62px;
      padding-left: 8px;
      padding-bottom: 15px;
    }
    .homeMenu {
      background-image: url("../../assets/KJ/menus/menu-bg.png");
      background-size: 100%;
      width: 347px;
      height: 310px;
      background-repeat: no-repeat;
      /*margin: 0 30px;*/
      /*transform: scale(1.2);*/
      margin-right: 1%;
    }
    .homeMenu2 {
      background-image: url("../../assets/KJ/menus/menu-bg.png");
      background-size: 100%;
      width: 347px;
      height: 310px;
      background-repeat: no-repeat;
      /*margin: 0 30px;*/
      margin-right: 1%;
    }
    .item {
      cursor: pointer;
      background: url("../../assets/KJ/menus/itemBG.png");
      width: 172px;
      height: 56px;
      font-size: 18px;
      font-weight: bold;
      padding-top: 4px;
      margin: 5px 0;
    }
    .item:hover {
      background: url("../../assets/KJ/menus/itemBG-active.png");
    }
    .menu-item {
      height: 117px;
      width: 148px;
      background: transparent url("../../assets/KJ/menus/icon.png") 0 0 no-repeat;
    }
  }
</style>
