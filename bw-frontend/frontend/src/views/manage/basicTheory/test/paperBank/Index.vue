<template>
  <div class="w-full h-full overflow-hidden layout-side">
    <NipLeftMenu />
    <div class="h-full transition-all duration-300" style="flex: 1">
      <!--  理论题库-->
      <div v-if="openRoute" class="w-full h-full p-2" style="display: flex; justify-content: space-between; padding-top: 0">
        <div class="h-full grouping bgColor" style="width: 280px;">
          <div class="w-full grouping_halving_line"></div>
          <div style="height: 20px">
            <div class="w-full h-full" style="padding: 0 8px">
              <div class="item_group input search" style="margin-top: 8px">
                <a-input @change="searchForKnowledge" placeholder="请输入关键字"></a-input>
                <div class="icon"></div>
              </div>
            </div>
          </div>
          <div class="p-1" style="padding-top: 16px; height: calc(100% - 20px)">
            <div class="w-full overflow-y-auto h-full" style="">
              <div class="Cmenus">
                <div class="Cmenus_title Cmenus_titles title_item1" style="padding: 0px;" @click="clickKnowledge('知识总览', -1)">
                  <div class="layout-center-top w-full h-full bg" style="padding-top: 12px">
                    <div class="title_zl fs_dispose" :class="{ activeKnowledge: activeKnowledge.index === -1 }">知识总览</div>
                    <IconFont type="icon-xiangshangshousuo1" style="color: #ffffff" v-if="openMenu" @click.stop="openMenu = !openMenu"></IconFont>
                    <IconFont type="icon-xiangxiazhankai1" style="color: #ffffff" v-else @click.stop="openMenu = !openMenu"></IconFont>
                  </div>
                </div>
                <div class="Cmenus" v-for="(item, index) in knowledgeList" :class="{ animate__animated: true,animate__fadeOutUp: !openMenu,animate__fadeInDown: openMenu,}">
                  <div class="Cmenus_title Cmenus_title" :class="{   activeKnowledge2: activeKnowledge.index === index, }" @click="clickKnowledge(item, index)">
                    <span class="actives">{{ item.title }}</span>
                    <span @click.stop="clickMenu(item, index)" :class="{ ico: true, open: item.isOpen }"></span>
                  </div>
                  <div style="transition: height 0.5s" :style="{
                      height: openMenu
                        ? item.isOpen
                          ? (item.children ? item.children.length : 0) * 40 +
                            'px'
                          : '0'
                        : '0',
                    }"
                       :class="{
                      'Cmenus_content animate__animated': true,
                      animate__fadeOutUp: !item.isOpen,
                      animate__fadeInDown: item.isOpen,
                    }"
                  >
                    <template v-for="(sub, s) in item.children">
                      <div
                        :class="{
                          Cmenu_item: true,
                          active: activeKnowledge.index === index + String(s),
                        }"
                        @click="clickKnowledge(sub, index + String(s))"
                      >
                        {{ sub.title }}
                      </div>
                    </template>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
        <div class="h-full grouping bgColor" style="width: calc(100% - 290px)">
          <div class="w-full grouping_halving_line"></div>
          <div
            class="w-full h-full content-mask-bg p-2"
            style="padding-top: 0; padding-bottom: 0"
          >
            <div style="height: 65px; display: flex; padding: 10px 0">
              <div
                class="item_group btn layout-center"
                style="margin-top: 8px;"
                @click="skipDetails('')"
              >
                新建试卷 <PlusOutlined style="margin-left: 4px" />
              </div>
            </div>
            <div
              class="w-full layout-center"
              style="height: calc(100% - 65px)"
              v-if="listData.length === 0"
            >
              <div class="w-full layout-center">
                <div class="nomore">
                  <p style="color: #7b90af">暂无数据！</p>
                </div>
              </div>
            </div>
            <div
              v-else
              class="overflow-auto w-full overflow-auto"
              style="height: calc(100% - 65px)"
            >
              <a-row>
                <a-col v-for="(d, i) in listData" :key="i" :xs="{ span: 12, offset: 0 }" :md="{ span: 12, offset: 0 }" :lg="{ span: 12, offset: 0 }" :xl="{ span: 8, offset: 0 }" :xxl="{ span: 6, offset: 0 }">
                  <div class="list relative"  :class="[selectedPaper === d.id ? 'listActive' : '']">
                    <div class="absolute listBg" style="z-index: 2; top: 1px; left: 1px; right: 1px; bottom: 1px">
                      <div class="theLabel" @click.stop="skipDetails(d)"></div>
                      <a-popconfirm
                          title="是否删除该试卷?"
                          ok-text="是"
                          cancel-text="否"
                          @confirm="deletePaper(d)"
                      >
                        <DeleteOutlined  class="deleteIcon" style="color: red;" title="删除" />
                      </a-popconfirm>
                      <img class="imgs" :src="llcy" alt="" v-if="interfaceStyle!=='LJ'" />
                      <div class="listLabel" :title="d.name"><span class="listLine"></span>{{ d.name }}</div>
                      <div class="listImg relative" :class="[selectedPaper === d.id ? 'activeList' : '']">
                        <div style="margin-left: 30px; font-size: 13px; color: #bfcde0">
                          <div>题目数量：{{ d['completion'].length + d.judge.length + d.multipleChoice.length + d.singleChoice.length + d.shortAnswer.length }}</div>
                          <div>总分：{{ d.total }}</div>
                        </div>
                        <div class="theTitle" :style="{ paddingTop: 40 - fs * 8 + 'px' }">
                          <div class="theTitleItem">
                            <div style="color: #63c1ff; font-weight: bold" :style="{ fontSize: fs * 2 + 18 + 'px' }">
                              {{ d.singleChoice.length }}
                            </div>
                            <div style="color: #a5b6d0" :style="{ fontSize: fs + 13 + 'px' }">
                              单选题
                            </div>
                            <IconFont
                              type="icon-xuanzeti1"
                              style="font-size: 30px; color: #6ebdff"
                            ></IconFont>
                          </div>
                          <div class="theTitleItem">
                            <div style="color: #63c1ff; font-weight: bold" :style="{ fontSize: fs * 2 + 18 + 'px' }">
                              {{ d.multipleChoice.length }}
                            </div>
                            <div style="color: #a5b6d0" :style="{ fontSize: fs + 13 + 'px' }">
                              多选题
                            </div>
                            <IconFont type="icon-duoxuan1" style="font-size: 30px; color: #6ebdff"
                            ></IconFont>
                          </div>
                          <div class="theTitleItem">
                            <div style="color: #63c1ff; font-weight: bold" :style="{ fontSize: fs * 2 + 18 + 'px' }">
                              {{ d.judge.length }}
                            </div>
                            <div style="color: #a5b6d0" :style="{ fontSize: fs + 13 + 'px' }">
                              判断题
                            </div>
                            <IconFont type="icon-panduanti1" style="font-size: 30px; color: #6ebdff"></IconFont>
                          </div>
                          <div class="theTitleItem">
                            <div style="color: #63c1ff; font-weight: bold" :style="{ fontSize: fs * 2 + 18 + 'px' }">
                              {{ d['completion'].length }}
                            </div>
                            <div style="color: #a5b6d0" :style="{ fontSize: fs + 13 + 'px' }">
                              填空题
                            </div>
                            <IconFont type="icon-tiankongti1" style="font-size: 30px; color: #6ebdff"></IconFont>
                          </div>
                          <div class="theTitleItem">
                            <div style="color: #63c1ff;font-weight: bold" :style="{fontSize: (fs * 2 + 18) + 'px'}">
                              {{d.shortAnswer.length}}
                            </div>
                            <div style="color: #a5b6d0" :style="{fontSize: (fs * 1 + 13) + 'px'}">
                              简答题
                            </div>
                            <IconFont type="icon-jiandati1" style="font-size: 30px;color: #6ebdff"></IconFont>
                          </div>
                        </div>
                      </div>
                      <div class="trangle" v-if="selectedPaper === d.id"></div>
                      <div class="icon" v-if="selectedPaper === d.id">
                        <IconFont type="icon-gou1" style="font-size: 20px; color: #06b60b"></IconFont>
                      </div>
                    </div>
                  </div>
                </a-col>
              </a-row>
            </div>
          </div>
        </div>
      </div>
      <router-view v-else />
    </div>
  </div>
</template>

<script>
  export default {
    name: 'paperBank',
  }
</script>
<script setup>
  import {
    FormOutlined,
    PlusOutlined,
    DeleteOutlined,
    createFromIconfontCN,
  } from '@ant-design/icons-vue'
  import knowledgeTabel from './js/knowledgeTabel'
  import useQuestionBank from '../questionBank/js/useQuestionBank'
  import { ref, onMounted, watch } from 'vue'
  import { useRouter, useRoute } from 'vue-router'
  import llcy from '../../../../../assets/HJ/test/llcy.png'
  import NipLeftMenu from "../../../../../components/common/NipLeftMenu.vue";

  const IconFont = createFromIconfontCN({
    scriptUrl: window.iconUrl,
  })
  const selectedPaper = ref('')
  const fs = ref(JSON.parse(localStorage.getItem('fs')));
  const leftMenuWidth = ref(215)
  const interfaceStyle = window.interfaceStyle
  if(interfaceStyle==="HJJ"){
    leftMenuWidth.value = 210
  }else if (interfaceStyle==="HJ"){
    leftMenuWidth.value = 215
  }else {
    leftMenuWidth.value = 170
  }
  const openMenu = ref(true)
  const openRoute = ref(true)
  const fileUrl = ref(window.fileUrl)
  const router = useRouter()
  const route = useRoute()
  const {
    typeCheckList,
    difficulty,
    listData,
    takeNew,
    knowledgeShow,
    params,
    takeNoTestVisible,
    testPaper,
    deletePaper
  } = knowledgeTabel()
  watch(route, () => {
    if (route.matched[route.matched.length - 1].path === '/preview/basicTheoretical/theoryTestMenu/paperBank') {
      openRoute.value = true
      testPaper()
    }
  },{immediate:true})
  onMounted(() => {
    if (
        route.matched[route.matched.length - 1].path ===
        '/preview/basicTheoretical/theoryTest/paperBank'
    ) {
      openRoute.value = true
    } else if (
        route.matched[route.matched.length - 1].path ===
        '/preview/basicTheoretical/theoryTest/paperBank/theNewTest'
    ) {
      openRoute.value = false
    }
    queryKnowledgeTree()
    clickKnowledge('知识总览', -1)
    testPaper()
  })
  const skipDetails = (e) => {
    openRoute.value = false
    router.push({
      path: route.matched[4].path + '/theNewTest',
      query: {
        id: e.id,
      },
    })
  }



  const {
    knowledgeList,
    activeKnowledge,
    queryKnowledgeTree,
    clickMenu,
    clickKnowledge,
    searchForKnowledge,
  } = useQuestionBank(listData)
</script>

<style lang="less" scoped>
  @import "../css/paperCard";
  @import './css/KJ';
  .HJ{
    .bgColor{background: rgba(24, 45, 86, 0.7)}
    .bg {
      background-image: url("../../../../../assets/HJ/basicTheory/test/question_bank_bg.png");
      color: #ffffff!important;
    }
    .activeKnowledge {
      color: #814200;
      font-weight: bolder;
      font-size: 18px!important;
    }
    .activeKnowledge2 {
      font-weight: bolder;
      font-size: 16px;
      background-image: linear-gradient(0deg, #115197, #3169b0);
    }
    .listBg{z-index: 2;background-color: #0e1c38;top: 1px;left: 1px;right: 1px;bottom: 1px;inset: 2px;}
    .theTitleItem {
      background:  url('../../../../../assets/HJJ/basicTheory/test/theTitleItemBg.png')
      no-repeat bottom;
      background-size: 80%;
      text-align: center;
      padding: 14px 12px 6px;
    }
    .nomore {
      background: url('../../../../../assets/HJ/train/nomore.png') no-repeat center 100%!important;
      height: 293px;
      width: 290px;
      display: flex;
      align-items: flex-end;
      justify-content: center;
      font-size: 20px;
      color: #00a0e9;
    }
  }
  .HJJ{
    .bgColor{background: rgba(23, 31, 41, 0.7)}
    .bg {
      background-image: url('../../../../../assets/HJJ/basicTheory/test/question_bank_bg.png');
      background-repeat: no-repeat;
      background-size: 100% 100%;
      color: #bfcde0 !important;
      font-size: 20px;
      font-weight: bold;
      text-align: center;
    }
    .activeKnowledge {
      color: #ffffff;
      font-weight: bolder;
      font-size: 18px!important;
    }
    .activeKnowledge2 {
      font-weight: bolder;
      font-size: 16px;
      background-image: linear-gradient(0deg, #344a5d, rgba(100, 141, 177, 0.99));
    }
    .listBg {
      background-image: linear-gradient(to bottom, #394d66, #192533);
      top: 1px;left: 1px;right: 1px;bottom: 1px;
      border: 1px solid transparent;inset:2px;
    }
    .theTitleItem {
      background:  url('../../../../../assets/HJJ/basicTheory/test/theTitleItemBg.png')
      no-repeat bottom;
      background-size: 80%;
      text-align: center;
      padding: 14px 12px 6px;
    }
  }
  .LJ{
    .bgColor{background: rgba(38,41,36,0.3)}
    .bg {
      background-image: url('../../../../../assets/LJ/basicTheory/test/question_bank_bg.png');
      background-repeat: no-repeat;
      background-size: 100% 100%;
      color: #fff !important;
      font-size: 20px;
      font-weight: bold;
      text-align: center;
    }
    .activeKnowledge {
      color: #ffffff;
      font-weight: bolder;
      font-size: 18px!important;
    }
    .activeKnowledge2 {
      font-weight: bolder;
      font-size: 16px;
      background: #533e1a;
    }
    .listBg {
      background: url('../../../../../assets/LJ/test/testBg.png') no-repeat;
      top: 1px;left: 1px;right: 1px;bottom: 1px;
      background-size: 100% 100%;inset:2px;
    }
    .theTitleItem {
      background:  url('../../../../../assets/LJ/basicTheory/test/theTitleItemBg.png')
      no-repeat bottom;
      background-size: 80%;
      text-align: center;
      padding: 14px 12px 6px;
    }
    .theLabel{
      top: 40px!important;
    }
  }
  .deleteIcon{
    height: 30px!important;
    width: 30px!important;
    font-size: 18px;
    position: absolute;
    right: 2px!important;
    z-index: 2;
    top: 12px;
    opacity: 0.7;
    display: flex;
    align-items: center;
    justify-content: center;
  }
  .deleteIcon:hover{
    opacity: 1;
    background: #0b1f18;
  }
  .theLabel {
    height: 30px!important;
    width: 30px!important;
    position: absolute;
    right: 30px!important;
    z-index: 2;
    display: flex;
    align-items: center;
    justify-content: center;
    white-space: nowrap;
    text-overflow: ellipsis;
    border: 0px!important;
    overflow: hidden;
    background-image: url('../../../../../assets/LJ/basicTheory/test/theLabelBg.png');
    background-repeat: no-repeat;
    background-position: center;
    .theLabelInfo {
      text-align: center;
      word-break: break-all;
      word-break: break-word;
      position: absolute;
      right: 6px;
      font-size: 12px;
      top: 3px;
      width: 45px;
      height: 40px;
    }
  }
  .theLabel:hover {
    background: #0b1f18
    url('../../../../../assets/LJ/basicTheory/test/theLabelBg-hover.png') no-repeat
    center!important;
  }
  .theLabel1 {
    background: url('../../../../../assets/LJ/basicTheory/theLabel.png');
    background-size: 100%;
    background-repeat: no-repeat;
    height: 50px;
    width: 65px;
    position: absolute;
    /*display: flex;*/
    /*justify-content: right;*/
    top: 6px;
    right: 21px;
    z-index: 2;
    white-space: nowrap;
    text-overflow: ellipsis;
    overflow: hidden;
    display: flex;
    .theLabelInfo {
      text-align: center;
      word-break: break-all;
      word-break: break-word;
      position: absolute;
      right: 6px;
      font-size: 12px;
      top: 3px;
      width: 45px;
      height: 40px;
    }
  }
  .nomore {
    background: url('../../../../../assets/LJ/train/nomore.png') no-repeat center 100%;
    height: 293px;
    width: 290px;
    display: flex;
    align-items: flex-end;
    justify-content: center;
    font-size: 20px;
    color: #00a0e9;
  }
  .title_item1 {
    margin-bottom: 1px;
    height: 64px;
    border-top: 1px solid #2a4562;
  }
  .title_item2 {
    width: 125px;
    height: 0;
    border-top: 5px solid #2a4163;
    border-left: 5px solid transparent;
    border-right: 5px solid transparent;
  }
</style>
