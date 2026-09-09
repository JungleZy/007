<template>
  <div class="h-full menuBg transition-all duration-300" :style="{width: leftMenuWidth+'px'}">
    <div class="grouping h-full w-full" style="padding-top: 0;">
      <div class="w-full grouping_content la layout-left-top" style="height: 100%;padding-top: 35px">
        <div class="menus w-full " style="padding-right: 15px">
          <template v-for="(p,index) in atRoute.children">
            <div class="w-full layout-center" v-if="p.meta.isMenu" style="margin-bottom: 1px;">
              <a-popover v-if="isShrink && p.children.length>0" placement="rightTop">
                <template #content>
                  <div class="menusProp">
                    <template v-for="c in p.children">
                      <div v-if="c.meta.isMenu"
                           :class="{'w-full layout-left-center menu_item fs_dispose': true, active: c.name==openMenu.childName}"
                           @click="handleMenuClick(p,c)">
                        <span>{{ c.meta.title }}</span>
                      </div>
                    </template>
                  </div>
                </template>
                <div :class="{'menus_title w-full fs_dispose':true, active: p.name==selectMenu.name}"
                     @click="handleParentMenuClick(p)">
                  <div class="layout-side w-full truncate overflow-hidden" style="height: 40px">
                    <div class="h-full layout-left-center">
                    </div>
                  </div>
                </div>
              </a-popover>
              <div :class="{'menus_title w-full fs_dispose':true, active: p.name==selectMenu.name}"
                   @click="handleParentMenuClick(p)"
                   v-else>
                <div class="layout-center w-full truncate overflow-hidden " style="height: 40px">
                  <div class="h-full layout-left-center">
                    <a-tooltip placement="right" v-if="isShrink">
                      <template #title>{{ p.meta.title }}</template>
                      <div class="menus_icon">
                        <icon-font :type="p.meta.icon" class="mr-1" style="font-size: 25px;margin: 0;color: #6ebdff;font-weight: bold;"/>
                      </div>
                    </a-tooltip>
                    <div v-show="!isShrink" class="transition-all duration-700">{{ p.meta.title }}</div>
                  </div>
                  <!--                  <span v-show="!isShrink" :class="{ico:true, open: openMenu.name === p.name}" v-if="p.children.length>0"></span>-->
                </div>
              </div>

              <template v-if="p.children.length>0 && isShrink">
                <div :class="{'w-full menus_content animate__animated':true, animate__fadeOutUp: openMenu.name!=p.name, animate__fadeInDown: openMenu.name==p.name}">
                  <template v-for="c in p.children">
                    <div v-if="c.meta.isMenu"
                         :class="{'w-full layout-left-center menu_item fs_dispose': true, active: c.name==openMenu.childName}"
                         @click="handleMenuClick(p,c)">
                      <span>{{ c.meta.title }}</span>
                    </div>
                  </template>
                </div>
              </template>
            </div>
          </template>
        </div>
      </div>
      <!--      <div class="w-full layout-center cursor-pointer-def border-top-style shrinkBox"-->
      <!--           @click="handleMenuShrink"-->
      <!--           style="height: 46px;">-->
      <!--        <div :class="{shrinkIco:true, shrink: isShrink}"></div>-->
      <!--      </div>-->
    </div>
  </div>
</template>

<script>
  export default {
    name: "NipLeftMenuHJ"
  }
</script>
<script setup>
  import {useRoute, useRouter} from "vue-router";
  import {ref, onMounted, inject, nextTick} from "vue";
  import {createFromIconfontCN} from '@ant-design/icons-vue';
  import {fontSizeDispose} from "../../../common/utils/Utils";
  import {global} from "../../../config/pinia/index.js"

  const useGlobalStore = global.useGlobalStore();
  const IconFont = createFromIconfontCN({
    scriptUrl: window.iconUrl,
  });

  const route = useRoute();
  const router = useRouter();
  let atRoute = inject('atRoute');
  if(route.path.indexOf('unionJob')>-1||route.path.indexOf('networkUsing')>-1){
    atRoute = route.matched[route.matched.length-2]
  }

  // console.log("atRoute")
  // console.log(route.path)
  // console.log(route.matched)
  // console.log("atRoute")
  const leftMenuWidth = ref(useGlobalStore.leftWidth);
  const selectMenu = ref({});
  const openMenu = ref({name: '', childName: ''});
  const isShrink = ref(false);
  if (atRoute.path === route.fullPath) {
    selectMenu.value = atRoute.children.filter(item => item.meta.isMenu)[0];
  } else {
    atRoute.children.map(item => {
      if (route.fullPath.indexOf(item.path) > -1) {
        selectMenu.value = item;
      }
    })
  }

  onMounted(() => {
    window.addEventListener("resize", () => {
      if (window.innerWidth < 1260) {
        leftMenuWidth.value = 200
      } else {
        leftMenuWidth.value = 232
      }
    });
    handleParentMenuClick(selectMenu.value, true);
    nextTick(() => {
      fontSizeDispose();
    })
  });

  /**
   * 主菜单选择回调
   * @param e
   * @param refresh
   */
  const handleParentMenuClick = (e,refresh) => {
    selectMenu.value = e;
    if (e.children.length > 0&&e.path!=="equipmentTrainListHJBW") {
      openMenu.value.name = e.name;
      e.children.map(item => {
        if (route.fullPath.indexOf(item.path) > -1) {
          openMenu.value.childName = item.name;
        }
      });
    }
    else {
      openMenu.value = {name: '', childName: ''};
      if (refresh) {
        router.push({path:atRoute.path + "/" + e.path,query:route.query})
      } else if(atRoute.path.indexOf('theoryStudy')!==-1){
        const studyType = route.query.studyType
        router.push({
          path:atRoute.path + "/" + e.path,
          query:{
            studyType
          }}
        )
      }else {
        router.push(atRoute.path + "/" + e.path)
      }
    }
  };

  /**
   * 子菜单选择回调
   * @param p
   * @param sub
   */
  const handleMenuClick = (p, sub) => {
    selectMenu.value = p;
    openMenu.value.childName = sub.name;
    router.push(atRoute.path + "/" + p.path + "/" + sub.path)
  };

  /**
   * 收起/展开左侧菜单
   */
  const handleMenuShrink = () => {
    isShrink.value = !isShrink.value;
    leftMenuWidth.value = isShrink.value ? 56 : 232;
  }

</script>

<style lang="less" scoped>
  .border-top-style {
    border-top: 1px solid;
    border-image: linear-gradient(90deg, rgba(24, 45, 86, 0.7) 15%, rgba(69, 92, 138, 1) 50%, rgba(31, 46, 75, 0.7) 85%) 2 2 2 2;
  }
  .menusProp {
    width: 120px;
    background-color: #223c5d;
  }
  .menusProp .menu_item {
    cursor: pointer;
    height: 32px;
    padding: 0 16px;
    color: #e2f2ff;
  }
  .menusProp .menu_item:hover,
  .menusProp .menu_item.active {
    font-weight: bolder;
    background-color: #6ebdff;
    color: #223c5d;
  }
  .active{
    background: #0a1429 url("../../../assets/HJ/menus/menus-title-bg-active.png") no-repeat center;
  }
  .menuBg{
    background:url("../../../assets/HJ/menus/bg.png") no-repeat ;
    background-size: 100% 100%;
  }
  @media (max-width: 1260px) {
    .menus .menus_title {
      transform: scale(.82);
      margin: 8px 0;
    }
  }
</style>