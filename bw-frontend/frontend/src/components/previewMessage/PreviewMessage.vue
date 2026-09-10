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
          <div class="keyBox" v-if="pageData.length>0">
            <template v-for="index in 100" :key="index">
              <div :class="{ key: true}">
                <span>{{ pageData[index-1].key }}</span>
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
  import {ref,watch,onMounted} from "vue";
  import {apiPostTickerTapeTrainFindPage} from "../../common/api/ReceiveApi";
  import {useRoute} from "vue-router";
  const props = defineProps({
    pageData:{
      type:Array,
      default:[]
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
  const pageData = ref([])
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
      pageData.value = res.data.messageBody;
    })
  }
  const getMessage = ()=>{
    pageData.value = []
    const message = props.pageData[page.value.current-1]
    loading.value = false
    // console.log(message);
    message.forEach((item,index)=>{
      pageData.value.push({key:item.join('')})
    })
  }
</script>

<style scoped>
  @import "../../common/styles/css/score.less";
</style>