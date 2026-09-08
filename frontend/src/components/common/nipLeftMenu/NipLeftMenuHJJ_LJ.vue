<template>
  <div class="h-full menuBg transition-all duration-300" :style="{width: leftMenuWidth+'px'}" style="position: relative;top: -12px">
    <div class="grouping h-full w-full" style="padding-top: 0;padding-left: 2px;">
      <div class="w-full grouping_content la layout-left-top" style="height: 100%">
        <div class="menus w-full "  >
          <template v-for="(p,index) in atRoute.children">
            <div class="menuBox" v-if="p.meta.isMenu" :class="[active==index||activeT==index?'activeBg':'']">
              <div class="menu " :class="[activeT==index?'menuActive':'']" v-if="(p.meta.isMenu&&isShow(p.path))||p.children.length==0"   @click="handleMenuClick(p,index)">
                <div class="firstTitle">
                  {{p.meta.title}}
                </div>
              </div>
              <div class="secondMenu  " v-if="p.meta.isMenu&&p.children.length>0&&(active==index||activeT==index)" >
                <template v-for="(v,key) of p.children">
                  <div class="secondMenuItem"  v-if="v.meta.isMenu" @click="handleItemClick(v,p,index,key)" :class="[activeSec==key&&(route.query.studyType?route.query.studyType==index:true)&&isChildern(v)?'activeScconMenuItem':'']">
                    {{v.meta.title}}
                  </div>
                </template>
              </div>
            </div>
          </template>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
  export default {
    name: "NipLeftMenuHJJ_LJ"
  }
</script>
<script setup>
  import {useRoute, useRouter} from "vue-router";
  import {ref, onMounted, inject,watch,nextTick} from "vue";
  import {createFromIconfontCN} from '@ant-design/icons-vue';
  import firstImg from "../../../assets/HJJ/menus/firstImg.png"
  import {global} from "../../../config/pinia/index.js"

  const useGlobalStore = global.useGlobalStore();
  const IconFont = createFromIconfontCN({
    scriptUrl: window.iconUrl,
  });
  const route = useRoute();
  const router = useRouter();
  let atRoute =route.matched[3]
  //系统管理需要单独处理
  if(route.path.indexOf("systemManage")>-1){
    console.log(route.matched[2])
    atRoute = route.matched[2]
  }else {
    atRoute =route.matched[3]
  }
  // const leftMenuWidth = inject("leftMenuWidth");
  const interfaceStyle = window.interfaceStyle
  const leftMenuWidth = ref(useGlobalStore.leftWidth)
  const openMenu = ref({name: '', childName: ''});
  const children = atRoute.children.find(item=>item.children.length>0)
  //系统管理需要单独处理
  if(route.path.indexOf("systemManage")==-1){
    atRoute.children.forEach(item=>{
      if(item.path=='questionBank'||item.path=='paperBank'||item.path=='structure'){
        item.children = []
      }else if ((item.children==undefined||item.children.length==0)&&children){
        item.children = children.children
      }
    })
  }else {
    router.push(route.matched[2].path+"/"+atRoute.children[0].path)
  }
  watch(route,()=>{
    if (route.matched.length<5) {
      sessionStorage.setItem("activefist",0)
    }
    activeSec.value = route.query.key?route.query.key:activeSec.value
  })
  const active = ref(null)
  const activeT = ref(1)
  onMounted(() => {
    nextTick(()=>{
      initScroolBar()
    })
  });
  const isShow = (path)=>{
    let type = true
    // if(path=="theoryTest"){
    //   const arr = route.path.split("/")
    //   type = !arr[arr.length-2]==path
    //   if(arr[arr.length-3]==path){
    //     type = arr[arr.length-3]==path
    //   }
    // }
    return type
  }

  const isChildern = (v)=>{
    return route.matched[route.matched.length-2].children.some(item=>item.path==v.path)
  }
  const leftMenuEnter =(index,p)=>{
    if(p.children.length==0){
      return
    }
    const dom = document.querySelectorAll('.menuBox'+index )
    const dom2 = document.querySelectorAll('.secondMenu'+index)
    dom[0]?dom[0].style.display = 'none':''
    dom2[0]? dom2[0].style.display = 'flex':''
  }
  const leftMenuLeave =(index)=>{
    const dom = document.querySelectorAll('.menuBox'+index )
    const dom2 = document.querySelectorAll('.secondMenu'+index)
    dom[0]?dom[0].style.display = 'flex':""
    dom2[0]?dom2[0].style.display = 'none':''
  }
  const activeSec = ref(route.query.key?route.query.key:sessionStorage.getItem("activeSec")?sessionStorage.getItem("activeSec"):route.query.key=0)
  activeT.value =sessionStorage.getItem("activefist")?sessionStorage.getItem("activefist"):0
  const handleItemClick = (e,p,index=0,key)=>{
    activeSec.value == key
    sessionStorage.setItem("activeSec",key)

    //系统管理需要单独处理
    let path
    if(route.path.indexOf("systemManage")>-1){
      path = route.matched[2].path+"/"+p.path+"/"+e.path
    }else {
      path = route.matched[3].path+"/"+p.path+"/"+e.path
    }
    if(path.indexOf('basicTheory')>-1){
      router.push({
        path:path,
        query:{
          studyType:index,
          key:key
        }
      })
    }else {
      router.push({
        path:path,
        query:{
          key
        }
      })
    }
    nextTick(()=>{
      initScroolBar()
    })
  }
  //没有二级菜单的点击跳转
  const handleMenuClick = (p,index)=>{
    active.value = index
    sessionStorage.setItem("activefist",index)
    activeT.value = index
    if(p.children.length==0){
      let path
      if(route.path.indexOf("systemManage")>-1){
        path = route.matched[2].path+"/"+p.path
      }else {
        path = route.matched[3].path+"/"+p.path
      }
      router.push(path)
    }
    if (p.children[0]!==undefined){
      handleItemClick(p.children[0],p,index,0)
    }
  }

  //调整滚动条位置
  const initScroolBar = ()=>{
    const bar = document.querySelectorAll('.menus')
    const q = document.querySelectorAll('.activeScconMenuItem')
    if(bar.length>0&&q.length>0){
      bar[0].scrollTop = q[0].offsetTop-200
    }
  }

</script>
<style scoped>

</style>

<style lang="less" scoped>
  @keyframes letterA {
    0%{
      transform: rotateY(0deg);
    }
    100%{
      transform: rotateY(360deg);
    }
  }
  .animate__animated.animate__zoomIn {
    animation-duration: .3s;
    backface-visibility: hidden;
    /*transition: all ;*/
    /*animation: letterA 2s;*/
  }
  .menuBox{
    width: 174px;
    margin: 10px 0px 30px 10px;
  }
  .secondMenu{
    width: 174px;
    flex-direction: column;
    justify-content: center;
    align-items: center;
    padding: 10px 0;
  }
  .secondMenuItem {
    text-align: left;
    font-size: 18px;
    line-height: 50px;
    height: 50px;
    width: 171px;
    cursor: pointer;
    font-weight: bold;
    padding-left: 20px;
    color: #c2c0be;
  }
   .HJJ{
   .secondMenuItem {
     text-align: left;
     font-size: 18px;
     line-height: 34px;
     height: 34px;
     width: 171px;
     cursor: pointer;
     font-weight: bold;
     padding-left: 20px;
     color: #c2c0be;
   }
   .activeBg{
     background: #403e40;
   }
   .menus{
     padding-bottom: 10px;
     overflow: auto;
     width: 210px;
     height: calc(100% - 0px);
     padding-top: 100px;
     background: url("../../../assets/HJJ/menus/menusBg.png");
     background-repeat: no-repeat;
     background-size: 100% 100%;
     position: fixed
   }

   .menu{
     width: 184px;
     height: 68px;
     display: flex;
     align-items: center;
     justify-content: center;
     background: url("../../../assets/HJJ/menus/firstMenuBG.png");
     position: relative;
     cursor: pointer;
     left: -10px;
   }
   .menuActive{
     background: url("../../../assets/HJJ/menus/firstMenuBg-active.png");
   }
   .menuActive .firstTitle{
     color: #e9deb2;
   }
   .firstTitle{
     position: relative;
     text-align: center;
     font-weight: bold;
     letter-spacing: 2px;
     font-size: 24px;
     padding-bottom: 15px;
     color: #b5c6d4;
     text-shadow:-3px 3px 2px black;
   }
   .firstTitle::before{
     content: attr(text);
     position: absolute;
     width: 100%;
     height: 100%;
     top: 0px;
     text-shadow: 5px 5px 3px #000000;
   }

   .activeScconMenuItem{
     color: #ffffff;
     background: url("../../../assets/HJJ/menus/secondMenuActive.png");
   }
   .secondMenuItem:hover{
     color: #ffffff;
     background: url("../../../assets/HJJ/menus/secondMenuActive.png");
   }
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
     background: #0a1429 url("../../../assets/HJJ/menus/menus-title-bg-active.png") no-repeat center;
   }
 }
  .LJ{
    .activeBg {
      background: rgba(15, 26, 22, 0.7);
    }
    .menus {
      padding-bottom: 10px;
      overflow: auto;
      /*width: 210px;*/
      width: 170px;
      height: calc(100vh - 82px);
      padding-top: 100px;
      background: url("../../../assets/LJ/menus/menusBg.png");
      background-repeat: no-repeat;
      background-size: 100% 100%;
      position: fixed;
      overflow: hidden;
    }
    .menu {
      width: 170px;
      height: 71px;
      display: flex;
      align-items: center;
      justify-content: center;
      background: url("../../../assets/LJ/menus/firstMenuBG.png") no-repeat;
      position: relative;
      cursor: pointer;
      left: -10px;
      background-size: 100%;
    }
    .menuActive {
      background: url("../../../assets/LJ/menus/firstMenuBg-active.png");
      position: relative;
    }
    .menuActive::after {
      content: "";
      background: url("../../../assets/LJ/menus/activeBefore.png");
      width: 17px;
      height: 33px;
      position: absolute;
      right: 0px;
      top: calc(50% - 16px);
    }
    .menuActive .firstTitle {
      color: #e9deb2;
    }
    .firstTitle {
      position: relative;
      text-align: center;
      font-weight: bold;
      letter-spacing: 2px;
      font-size: 24px;
      /*padding-bottom: 15px;*/
      color: #afd4c9;
      text-shadow: -3px 3px 2px black;
    }
    .firstTitle::before {
      content: attr(text);
      position: absolute;
      width: 100%;
      height: 100%;
      top: 0px;
      text-shadow: 5px 5px 3px #000000;
    }
    .activeScconMenuItem {
      color: #ffffff;
      background: url("../../../assets/LJ/menus/secondMenuActive.png");
    }
    .secondMenuItem:hover {
      color: #ffffff;
      background: url("../../../assets/LJ/menus/secondMenuActive.png");
    }

    .border-top-style {
      border-top: 1px solid;
      /*border-image: linear-gradient(90deg, rgba(24, 45, 86, 0.7) 15%, rgba(69, 92, 138, 1) 50%, rgba(31, 46, 75, 0.7) 85%) 2 2 2 2;*/
      border-image: linear-gradient(
        90deg,
        rgb(36, 45, 41) 15%,
        #4d5652 50%,
        rgb(36, 45, 41) 85%
      )
      2 2 2 2;
    }
    .menusProp {
      width: 120px;
      background-color: #223c5d;
    }
    .menusProp .menu_item {
      cursor: pointer;
      height: 32px;
      padding: 0 16px;
      color: #ffffff;
    }
    .menusProp .menu_item:hover,
    .menusProp .menu_item.active {
      font-weight: bolder;
      background-color: #34b34c;
      color: #223c5d;
    }
    .active {
      background: #0a1429 url("../../../assets/LJ/menus/menus-title-bg-active.png")
      no-repeat center;
    }
  }
  .GD{
    .activeBg {
      background: rgba(15, 26, 22, 0.7);
    }
    .menus {
      padding-bottom: 10px;
      overflow: auto;
      /*width: 210px;*/
      width: 170px;
      height: calc(100vh - 82px);
      padding-top: 100px;
      background: url("../../../assets/GD/menus/menusBg.png");
      background-repeat: no-repeat;
      background-size: 100% 100%;
      position: fixed;
      overflow: hidden;
    }
    .menu {
      width: 170px;
      height: 71px;
      display: flex;
      align-items: center;
      justify-content: center;
      background: url("../../../assets/GD/menus/firstMenuBG.png") no-repeat;
      position: relative;
      cursor: pointer;
      left: -10px;
      background-size: 100%;
    }
    .menuActive {
      background: url("../../../assets/GD/menus/firstMenuBg-active.png");
      position: relative;
    }
    .menuActive::after {
      content: "";
      background: url("../../../assets/GD/menus/activeBefore.png");
      width: 17px;
      height: 33px;
      position: absolute;
      right: 0px;
      top: calc(50% - 16px);
    }
    .menuActive .firstTitle {
      color: #e9deb2;
    }
    .firstTitle {
      position: relative;
      text-align: center;
      font-weight: bold;
      letter-spacing: 2px;
      font-size: 24px;
      /*padding-bottom: 15px;*/
      color: #afd4c9;
      text-shadow: -3px 3px 2px black;
    }
    .firstTitle::before {
      content: attr(text);
      position: absolute;
      width: 100%;
      height: 100%;
      top: 0px;
      text-shadow: 5px 5px 3px #000000;
    }
    .activeScconMenuItem {
      color: #ffffff;
      background: url("../../../assets/GD/menus/secondMenuActive.png");
    }
    .secondMenuItem:hover {
      color: #ffffff;
      background: url("../../../assets/GD/menus/secondMenuActive.png");
    }

    .border-top-style {
      border-top: 1px solid;
      /*border-image: linear-gradient(90deg, rgba(24, 45, 86, 0.7) 15%, rgba(69, 92, 138, 1) 50%, rgba(31, 46, 75, 0.7) 85%) 2 2 2 2;*/
      border-image: linear-gradient(
        90deg,
        rgb(36, 45, 41) 15%,
        #4d5652 50%,
        rgb(36, 45, 41) 85%
      )
      2 2 2 2;
    }
    .menusProp {
      width: 120px;
      background-color: #223c5d;
    }
    .menusProp .menu_item {
      cursor: pointer;
      height: 32px;
      padding: 0 16px;
      color: #ffffff;
    }
    .menusProp .menu_item:hover,
    .menusProp .menu_item.active {
      font-weight: bolder;
      background-color: #34b34c;
      color: #223c5d;
    }
    .active {
      background: #0a1429 url("../../../assets/GD/menus/menus-title-bg-active.png")
      no-repeat center;
    }
  }
  .KJ{
    .menuBg{
      top: 0px!important;
    }
    .activeBg {
      background: rgba(31, 67, 99, 1);
    }
    .menus {
      padding-bottom: 10px;
      overflow: auto;
      /*width: 210px;*/
      width: 170px;
      height: calc(100vh - 82px);
      padding-top: 100px;
      background: url("../../../assets/KJ/menus/menusBg.png");
      background-repeat: no-repeat;
      background-size: 100% 100%;
      position: fixed;
      overflow: hidden;
    }
    .menu {
      width: 170px;
      height: 71px;
      display: flex;
      align-items: center;
      justify-content: center;
      background: url("../../../assets/KJ/menus/firstMenuBG.png") no-repeat;
      position: relative;
      cursor: pointer;
      left: -10px;
      background-size: 100%;
    }
    .menuActive {
      background: url("../../../assets/KJ/menus/firstMenuBg-active.png");
      position: relative;
    }
    .menuActive::after {
      content: "";
      background: url("../../../assets/KJ/menus/activeBefore.png");
      width: 17px;
      height: 33px;
      position: absolute;
      right: 0px;
      top: calc(50% - 16px);
    }
    .menuActive .firstTitle {
      color: #e9deb2;
    }
    .firstTitle {
      position: relative;
      text-align: center;
      font-weight: bold;
      letter-spacing: 2px;
      font-size: 24px;
      /*padding-bottom: 15px;*/
      color: #afd4c9;
      text-shadow: -3px 3px 2px black;
    }
    .firstTitle::before {
      content: attr(text);
      position: absolute;
      width: 100%;
      height: 100%;
      top: 0px;
      text-shadow: 5px 5px 3px #000000;
    }
    .activeScconMenuItem {
      color: #ffffff;
      background: rgba(80,141,230,0.6);
    }
    .secondMenuItem:hover {
      color: #ffffff;
      background:  rgba(80,141,230,0.6);
    }

    .border-top-style {
      border-top: 1px solid;
      /*border-image: linear-gradient(90deg, rgba(24, 45, 86, 0.7) 15%, rgba(69, 92, 138, 1) 50%, rgba(31, 46, 75, 0.7) 85%) 2 2 2 2;*/
      border-image: linear-gradient(
        90deg,
        rgb(36, 45, 41) 15%,
        #4d5652 50%,
        rgb(36, 45, 41) 85%
      )
      2 2 2 2;
    }
    .menusProp {
      width: 120px;
      background-color: #223c5d;
    }
    .menusProp .menu_item {
      cursor: pointer;
      height: 32px;
      padding: 0 16px;
      color: #ffffff;
    }
    .menusProp .menu_item:hover,
    .menusProp .menu_item.active {
      font-weight: bolder;
      background-color: #34b34c;
      color: #223c5d;
    }
    .active {
      background: #0a1429 url("../../../assets/KJ/menus/menus-title-bg-active.png")
      no-repeat center;
    }
  }
</style>