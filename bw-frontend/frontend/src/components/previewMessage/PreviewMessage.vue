<template>
  <div class="pb-2">
    <div class="telegraphBox">
      <div class="totalBox">
        <div class="layout-left-center w-full">
          <div class="total">
            页数：<span class="num">{{ page.current }}/{{ allPage }}</span>
          </div>
        </div>
        <div class="page">
          <div class="cursor-pointer-def" :class="{ pag: true, disabled: page.current == 1 }"
               @click="changePage(-1)">上一页
          </div>
          <div class="cursor-pointer-def" :class="{ pag: true, disabled: page.current == page.pageNumber }"
               @click="changePage(1)">下一页
          </div>
        </div>
      </div>
    </div>
    <div class="patTelegraphBox relative">
      <div class="patTelegraph">
        <div class="telegraph">
          <div class="rowHead">
            <div class="key">1</div>
            <div class="key">2</div>
            <div class="key">3</div>
            <div class="key">4</div>
            <div class="key">5</div>
            <div class="key">6</div>
            <div class="key">7</div>
            <div class="key">8</div>
            <div class="key">9</div>
            <div class="key">10</div>
          </div>
          <div class="keyBox" v-if="pageRows.length>0">
            <template v-for="index in 100" :key="index">
              <div :class="{ key: true}">
                <span>{{ pageRows[index-1].key }}</span>
              </div>
            </template>
          </div>
        </div>
      </div>
      <div v-if="loading" class="w-full h-full layout-center" style="position: absolute;z-index: 99;top: 0;background: rgba(255,255,255,0.2);">
        <a-spin ></a-spin>
      </div>
    </div>
  </div>
</template>

<script>
  export default {
    name: "PreviewMessage"
  }
</script>
<script setup>
  import {ref, onMounted} from "vue"
  import {apiPostTickerTapeTrainFindPage} from "../../common/api/ReceiveApi"
  import {useRoute} from "vue-router"
  const props = defineProps({
    pageData:{
      type:Array,
      default:()=>[]
    },
    allPage:{
      type: Number,
      default: 1,
    }
  })
  const page = ref({
    current: 1,
    pageNumber: 0
  })
  const route = useRoute()

  const loading = ref(true);
  onMounted(()=>{
    if(route.path.indexOf('ReceivePostTrain')>-1){
      getPage()
    }else {
      getMessage()
    }

    // getMessage()
  })
// 当前页的 100 格视图，由接口结果或 props.pageData[当前页] 展开而来 —— 不是 prop 的副本。
// 原先这个 ref 也叫 pageData，与同名 prop 撞车（vue/no-dupe-keys），
// 而两者语义不同（prop 是全部页，本地是当前页），同名尤其误导，故改名。
const pageRows = ref([])
  const changePage = (num) => {
    loading.value = true
    page.value.current = page.value.current + num
    if (page.value.current < 1) {
      page.value.current = 1
    }
    if (page.value.current > props.allPage.value) {
      page.value.current = props.allPage.value
    }
    if(route.path.indexOf('ReceivePostTrain')>-1){
      getPage()
    }else {
      getMessage()
    }
  }
  const getPage = ()=>{
    apiPostTickerTapeTrainFindPage({
      pageNumber: page.value.current,
      trainId: route.query.id
    }).then(res=>{
      loading.value = false
      pageRows.value = res.data.messageBody;
    })
  }
  const getMessage = ()=>{
    pageRows.value = []
    const message = props.pageData[page.value.current-1]
    loading.value = false
    // console.log(message);
    message.forEach((item,index)=>{
      pageRows.value.push({key:item.join('')})
    })
  }
</script>

<style scoped>
  @import "../../common/styles/css/score.less";
</style>