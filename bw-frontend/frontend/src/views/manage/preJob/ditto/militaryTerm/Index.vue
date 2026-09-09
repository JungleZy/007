<template>
  <div class="w-full h-full relative">
    <div class="w-full h-full basicBody relative">
      <div class="keyCodeBody">
        <div class="w-full h-full layout-side">
          <div class="h-full lev_1" style="width: 220px;border-right:1px solid #4172a9">
            <a-input @change="searchMilitaryTerm" placeholder="请输入关键字"
                     style="width: 97%;margin-bottom: 10px"></a-input>
            <div class="w-full overflow-auto relative scoreBoxL" style="height: calc(100% - 40px);padding-left: 2px">
              <a-spin class="spin" size="large" v-if="militaryTerm.length==0"/>
              <template v-for="(values,index) in militaryTerm">
                <div class="w-full item boxl"
                     style="padding-left: 10px"
                     :style="{fontSize: (fs * 2 + 17) + 'px'}"
                     :title="values.key"
                     @click="checkedOneItem(values,index)">
                  <div class="mark" :style="{fontSize: (fs * 2 + 12) + 'px'}">{{index+1}}</div>
                  <div class="text" :class="{active:index===selectedOneKey}">{{ values.key }}</div>
                </div>
              </template>
            </div>
          </div>
          <div class="h-full overflow-auto lev_2" style="width: 250px;border-right:1px solid #4172a9">
            <a-input @change="searchTwoMilitaryTerm" placeholder="请输入关键字" v-model:value="searchStr"
                     style="width: 97%;margin-bottom: 10px"></a-input>
            <div class="w-full overflow-auto scoreBox relative" style="height: calc(100% - 40px);padding-left: 2px">
              <a-spin class="spin" size="large" v-if="militaryTerm.length==0"/>
              <template v-for="(two,index) in twoMilitaryTerm">
                <div class="w-full item boxl"
                     :style="{fontSize: (fs * 2 + 16) + 'px'}"
                     :title="two.key"
                     @click="checkedTwoItem(two,index)">
                  <div class="mark" :style="{fontSize: (fs * 2 + 12) + 'px'}">{{index+1}}</div>
                  <div class="text" :class="{active:index===selectedTwoKey}">{{ two.key }}</div>
                </div>
              </template>
            </div>
          </div>
          <div class="h-full  layout-center-v relative lev_3" style="width: calc(100% - 490px);padding-left: 10px">
            <div class="deployBtn fs_dispose_1" v-if="userRole.id != '2'" @click="jumpDeploy">军语配置</div>
            <IconFont type="icon-info-circle" class="icon" @mouseenter="iconHover" @mouseleave="iconHide"></IconFont>
            <div v-if="tipIsShow" class="tip">
              <div class="tips"> 左一菜单切换 ↑ ↓</div>
              <div class="tips"> 左二菜单切换 ← →</div>
            </div>
            <div class="w-full layout-center mb-3 fs_dispose" style="font-size: 36px">
              {{ threeMilitaryTerm.title }}
            </div>
            <div class="w-full layout-center mb-3 fs_dispose" style="font-size: 30px;text-indent: 2em;text-align: justify">
              {{ threeMilitaryTerm.content }}
            </div>
            <div class="w-full p-1 absolute" style="bottom: 0">
              <div class="w-full h-full layout-side">
                <div class="pre" @click="handlePrevious()" :style="{fontSize: (fs * 2 + 16) + 'px'}">
                  <div class="text1" :title="selectedTwoIndex>0?twoMilitaryTerm[selectedTwoIndex-1].key:'无上一条'">
                    {{ selectedTwoIndex > 0 ? twoMilitaryTerm[selectedTwoIndex - 1].key : '无上一条' }}
                  </div>
                </div>
                <div class="next" @click="handleNext()" :style="{fontSize: (fs * 2 + 16) + 'px'}">
                  <div class="text2"
                       :title="(twoMilitaryTerm.length-1>selectedTwoIndex)?twoMilitaryTerm[selectedTwoIndex+1].key:'无下一条'">
                    {{ (twoMilitaryTerm.length - 1 > selectedTwoIndex) ? twoMilitaryTerm[selectedTwoIndex + 1].key : '无下一条' }}
                  </div>
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
  name: "Index"
}
</script>
<script setup>
import {onBeforeUnmount, onMounted, ref,nextTick} from 'vue'
import {useRouter} from 'vue-router'
import {createFromIconfontCN} from "@ant-design/icons-vue";
import {getMilitaryAll} from "../../../../../common/api/MilitaryTermApi";
import {getPreTermTrainTotal,savePreTermTrainTotal} from "../../../../../common/api/TelegramApi";

const IconFont = createFromIconfontCN({
  scriptUrl: window.iconUrl,
});
const searchStr = ref('')//
const militaryTerm = ref([])
const militaryTermSearch = ref([])
const twoMilitaryTerm = ref([])
const twoMilitaryTermSearch = ref([])
const threeMilitaryTerm = ref({})
const selectedOneKey = ref(0)
const selectedTwoKey = ref(0)
const selectedTwoIndex = ref(0)
const militaryTermIndex = ref(0)
const totalTime = ref(0)
let timer = null
const tipIsShow = ref(true)
const router = useRouter();
const drillPath = ref('');
const userRole = ref(JSON.parse(localStorage.getItem('userRole')));
const fs = ref(JSON.parse(localStorage.getItem('fs')));
router.getRoutes().forEach(r => {
  if (r.name === 'MilitaryDeploy') {
    drillPath.value = r.path;
  }
});
onMounted(() => {
  window.addEventListener('keydown', onKeyDown)
  getPreTermTrainTotal({type:1}).then(res=>{
    timer= setInterval(()=>{
      totalTime.value++
    },1000)
  })
  init()
})
const init = ()=>{
  getMilitaryAll().then(res=>{
    militaryTerm.value = res.data
    militaryTermSearch.value = res.data
    checkedOneItem(militaryTerm.value[0], 0)
  })
}
const iconHover = () => {
  tipIsShow.value = true
}
const iconHide = () => {
  tipIsShow.value = false
}

//左侧菜单切换（点击）
const checkedOneItem = (key, index) => {
  if (index) {
    militaryTermIndex.value = index
  }
  selectedOneKey.value = index
  twoMilitaryTerm.value = []
  twoMilitaryTerm.value = key.child
  twoMilitaryTermSearch.value = key.child
  checkedTwoItem(twoMilitaryTerm.value[0], 0)
  nextTick(()=>{
    document.querySelectorAll('.scoreBox')[0].scrollTop = 0
  })
}
//左侧菜单拆查询
const searchMilitaryTerm = (e) => {
  if (e.target.value !== "") {
    militaryTerm.value =  militaryTermSearch.value.filter(item=>item.key.indexOf(e.target.value)!=-1)
    militaryTerm.value.length>0?checkedOneItem(militaryTerm.value[0], 0):''
  } else {
    militaryTerm.value = militaryTermSearch.value
    checkedOneItem(militaryTerm.value[0], 0)
  }
}
//右侧菜单查询
const searchTwoMilitaryTerm = (e) => {
  if (e.target.value !== "") {
    twoMilitaryTerm.value = twoMilitaryTermSearch.value.filter(item=>item.key.indexOf(e.target.value)!=-1)
    twoMilitaryTerm.value.length > 0 ? checkedTwoItem( twoMilitaryTerm.value[0], 0) : ''
  } else {
    twoMilitaryTerm.value = twoMilitaryTermSearch.value
    checkedTwoItem(twoMilitaryTerm.value[0], 0)
  }
}

const checkedTwoItem = (key, index) => {
  selectedTwoIndex.value = index
  selectedTwoKey.value = index
  threeMilitaryTerm.value = {
    title: key.key,
    content: key.value
  }
}
const handlePrevious = () => {
  if (selectedTwoIndex.value > 0) {
    selectedTwoIndex.value--
    checkedTwoItem(twoMilitaryTerm.value[selectedTwoIndex.value], selectedTwoIndex.value)
  }
}
const handleNext = () => {
  if (selectedTwoIndex.value < twoMilitaryTerm.value.length - 1) {
    selectedTwoIndex.value++
    checkedTwoItem(twoMilitaryTerm.value[selectedTwoIndex.value], selectedTwoIndex.value)
  }
}
const onKeyDown = (v) => {
  if (v.key == 'ArrowUp' || v.key == 'ArrowDown' || v.key == 'ArrowLeft' || v.key == 'ArrowRight') {
    if (v.preventDefault) {
      v.preventDefault()
    } else {
      window.event.returnValue == false
    }
    switch (v.key) {
      case 'ArrowUp':
        if (militaryTermIndex.value != 0) {
          searchStr.value = ""
          militaryTermIndex.value--
          checkedOneItem(militaryTerm.value[militaryTermIndex.value],militaryTermIndex.value)
          document.querySelectorAll('.scoreBoxL')[0].scrollTop -=58
        }
        break;
      case "ArrowDown":
        if (militaryTermIndex.value < militaryTerm.value.length - 1) {
          searchStr.value = ""
          militaryTermIndex.value++
          checkedOneItem(militaryTerm.value[militaryTermIndex.value], militaryTermIndex.value)
        }
        if(selectedOneKey.value>10){
          nextTick(()=>{
            document.querySelectorAll('.scoreBoxL')[0].scrollTop +=58
          })
        }
        break;
      case "ArrowLeft":
        handlePrevious()
        nextTick(()=>{
          document.querySelectorAll('.scoreBox')[0].scrollTop -=58
        })
        break;
      case "ArrowRight":
        handleNext()
        if(selectedTwoIndex.value>10){
          nextTick(()=>{
            document.querySelectorAll('.scoreBox')[0].scrollTop +=58
          })
        }
        break;
    }
  }
}
onBeforeUnmount(() => {
  savePreTermTrainTotal({type:1,totalTime:totalTime.value*1000}).then();
  clearInterval(timer)
  window.removeEventListener("keydown", onKeyDown)
})
const jumpDeploy = () => {
  router.push({path: drillPath.value})
}
</script>

<style scoped lang="less">
@import "./css/index.less";
</style>