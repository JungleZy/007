<template>
  <div class="w-full h-full p-2" style="padding-top: 0">
    <div class="w-full h-full paper">
      <div class="w-full paperHead textColor">
        <div style="width: calc(100% - 584px);">
          <div class="w-full">
            考卷名称：
            <a-input
              style="
                width: calc(100% - 200px);
                min-width: 150px; "
              v-model:value="paperData.name"
              maxLength="30"
              placeholder="请输入考卷标题"
            />
          </div>
          <div class="w-full" style="margin-top: 10px">
            考卷节点：
            <a-tree-select
              v-model:value="paperData.levelId"
              style="
                max-width: 200px;
                min-width: 150px;
                width: calc(100% - 600px);
              "
              :treeData="knowledgeList"
              treeDefaultExpandAll
              @select="selectTree"
            />
          </div>
        </div>
        <div class="layout-left-center" style="width: 584px">
          <div class="arrow">
            <div class="score">总分</div>
            <div
              style="
          width: 100px;
                height: 40px;
                text-align: center;
                line-height: 35px;
                cursor: not-allowed;
                font-weight: bold;
                font-size: 20px;
                color: #e9deb2;
              "
            >
              {{ paperData.total }}
            </div>
            <!-- <a-input-number
              :min="0"
              step="10"
              @change="modifyTheScores"
              v-model:value="paperData.total"
            /> -->
          </div>
          <div class="arrow" style="margin-left: 20px; margin-right: 20px">
            <div class="score">及格分比</div>
            <a-input-number
              :max="1"
              :min="0.1"
              step="0.01"
             class="inputself"
              @change="modifyTheScores"
              v-model:value="paperData.passTheExamThan"
            />
          </div>
          <div class="arrow">
            <div class="score">及格分</div>
            <div
              style="
               width: 100px;
                height: 40px;
                text-align: center;
                line-height: 35px;
                cursor: not-allowed;
                font-weight: bold;
                font-size: 20px;
                color: #e9deb2;
              "
            >
              {{ paperData.passMark }}
            </div>
          </div>
        </div>
      </div>
      <div class="w-full layout-left-top paperBody">
        <div
          class="h-full"
          style="width: 264px; padding-right: 12px; overflow-y: auto"
        >
          <div
            class="Cmenus"
            style="position: relative"
            v-for="(item, index) in listData"
            :class="{
              animate__fadeOutUp: !item.isOpen,
              animate__fadeInDown: item.isOpen,
            }"
          >
            <div class="Cmenus_title" @click="positionScroll('title' + index)">
              <span :class="{ actives: true }">{{ item.title }}</span>
              <span class="numberA">{{ item.children.length }}</span>
              <IconFont
                class="iconAdd"
                type="icon-congtikuzhongxuanze1"
                style="color: #7b90af; margin-left: 50px; font-size: 22px"
                title="选择"
                @click.stop="selectTopic(item)"
              ></IconFont>
              <IconFont
                class="iconAdd"
                type="icon-tianjiadaotiku1"
                color="#f60"
                style="color: #7b90af; margin: 0 5px; font-size: 22px"
                title="添加"
                @click.stop="addTopic(item)"
              ></IconFont>
              <span
                @click.stop="clickMenu(index)"
                :class="{ ico: true, open: item.isOpen }"
              ></span>
            </div>
            <div
              style="transition: height 0.5s;position: relative"
              :style="{
                height: item.isOpen
                  ? item.isOpen
                    ? (item.children ? item.children.length : 0) * 40 + 'px'
                    : '0'
                  : '0',
              }"
              :class="{
                Cmenus_content: true,
                animate__fadeOutUp: !item.isOpen,
                animate__fadeInDown: item.isOpen,
              }"
            >
              <template v-for="(sub, s) in item.children">
                <div
                  style="
                    white-space: nowrap;
                    text-overflow: ellipsis;
                    overflow: hidden;
                    user-select: none;
                  "
                  :class="{ Cmenu_item: true }"
                  @click="positionScroll('gd' + index + s)"
                >
                  {{ s + 1 + '、' + sub.topic }}
                </div>
              </template>
            </div>
          </div>
        </div>
        <div class="h-full" style="width: calc(100% - 264px)">
          <div
            class="w-full h-full rightBg">
            <div
              style="
                display: flex;
                justify-content: left;
                height: 61px;
                position: sticky;
                top: 0px;
                z-index: 99;
                padding-top: 16px;
              "
            >
              <div style="text-align: center">
                <div class="theTitle">题目数量</div>
                <div class="theTitle2">
                  {{
                    paperData.singleChoice.length +
                    paperData.multipleChoice.length +
                    paperData.judge.length +
                    paperData.completion.length +
                    paperData.shortAnswer.length
                  }}
                </div>
              </div>
              <div class="variableLine"></div>
              <div style="text-align: center">
                <div class="theTitle">题目总分</div>
                <div class="theTitle2">{{ paperData.total }}</div>
              </div>
            </div>
            <div style="overflow: auto; height: calc(100% - 61px)">
              <div v-for="(i, xb) in bankList" :id="'title' + xb">
                <div
                  v-if="paperData && paperData[i.key].length !== 0"
                  style="
                    font-size: 18px;
                    font-weight: 600;
                    display: flex;
                    padding: 20px 0;
                    align-items: center;
                  "
                >
                  {{ i.name }}
                  <span
                    style="
                      flex: 1;
                      display: inline-block;
                      border-bottom: 1px dashed #364763;
                      margin: 5px 10px;
                    "
                  ></span>
                  <span class="titleColor"
                    >( 共{{ paperData[i.key].length }}小题，每题 </span
                  ><a-input-number
                    @change="inputNumber(i.key, paperData[i.key][0].score)"
                    v-model:value="paperData[i.key][0].score"
                    style="width: 60px; margin: 0 5px"
                  /><span class="titleColor" >
                    分，共{{ calculateScore(paperData[i.key]) }}分 )</span
                  >
                </div>
                <div
                  v-for="(j, index) in paperData[i.key]"
                  :id="'gd' + xb + index"
                  class="layout-left-top paperList"
                >
                  <div style="width: 20px">
                    <span>{{ calculateTitle(i.key, paperData, index) }}、</span>
                  </div>
                  <div style="width: calc(100% - 20px); display: flex">
                    <PreviewTheTopic
                      style="width: calc(100% - 250px)"
                      :params="j"
                      :bool="true"
                    />
                    <div
                      style="
                        width: 250px;
                        display: flex;
                        flex-direction: column;
                        justify-content: space-between;
                      "
                    >
                      <div class="layout-right-center">
                        <a-input-number
                          v-model:value="j.score"
                          :disabled="true"
                        />
                      </div>
                      <div class="layout-right-center">
                        <div
                          v-for="(s, indexx) in iconList"
                          class="layout-center border"
                          style="width: 26px; height: 26px"
                        >
                          <IconFont
                            :type="s"
                            class="icon"
                            style="color: #7b90af; font-size: 20px"
                            @click="actionBar(indexx, i.key, j, index)"
                          ></IconFont>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
              <div
                class="w-full h-full layout-center"
                v-if="
                  paperData.singleChoice.length +
                    paperData.multipleChoice.length +
                    paperData.judge.length +
                    paperData.completion.length +
                    paperData.shortAnswer.length ===
                  0
                "
              >
                <nomore />
              </div>
            </div>
          </div>
        </div>
        <div class="shadow fade-in" v-if="knowledgeShow || updateBank.bool">
          <div class="shadowInfo">
            <div class="title">
              <div class="text">
                {{ updateBank.bool ? '修改' : '添加' }}题目
              </div>
            </div>
            <div
              class="newClosePopup"
              @click=";(knowledgeShow = false), (updateBank.bool = false)"
            >
              <div class="closeIco"></div>
            </div>
            <div class="bigbox relative">
              <div class="center w-full" style="height: calc(100%)">
                <div class="right">
                  <room-test
                    ref="roomtest"
                    :selectTree="knowledgeList"
                    :selectValue="paperData.levelId"
                    :indexShow="false"
                    :params="updateBank.bool ? updateBank.data : params"
                    class=""
                  >
                  </room-test>
                </div>
                <div class="bottom layout-center" style="height: 60px">
                  <div
                    class="botBtn layout-center cursor-pointer-def"
                    @click="bornTest"
                  >
                    {{ updateBank.bool ? '修改' : '添加' }}题目
                  </div>
                </div>
              </div>
            </div>
            <!--<div class="w-full bottominfo">
                            <div class="close" @click="knowledgeShow=false,updateBank.bool=false"></div>
                        </div>-->
          </div>
        </div>
        <a-modal
          :destroyOnClose="true"
          :width="1280"
          class="init_modal_style footer-border-none"
          destroyOnClose="true"
          v-model:visible="selectDrillModal"
          @cancel=""
        >
          <template #title>
            <strong>{{ randomValue ? '随机选题' : '从题库中选择' }}</strong>
          </template>
          <template #footer>
            <div class="w-full"></div>
          </template>
          <div v-if="!randomValue" style="height: 650px; overflow-y: auto">
            <questionBank
              @clickActive="clickActive"
              :activeList="activeList"
              :params1="true"
              :topicType="topicType"
            />
          </div>
          <div v-else style="height: 660px; overflow-y: auto; position: relative">
            <div class="w-full h-full layout-center" @click.stop="" v-if="!randomLoading"
              style="position: absolute;z-index: 999;background: rgba(0, 141, 200, 0.1);">
              <a-spin tip="试题更新中..." />
            </div>
            <div class="h-full" style="padding: 0px 0px 0px 0px">
              <div style="height: calc(100% - 38px); overflow-y: auto">
                <div v-for="i in randomBankList" @click="randomActive(i)">
                  <div style="font-size: 18px; font-weight: 600">
                    <span style="color: #f1f4f5">{{ i.name }}</span>
                    <span
                      style="
                        width: 850px;
                        display: inline-block;
                        border-bottom: 1px dashed #364763;
                        margin: 5px 10px;
                      "
                    ></span>
                    <span class="titleColor"
                      >( 共{{ i.list ? i.list.length : 0 }}题 / 已选择{{
                        paperData[i.key].length
                      }}题 )</span
                    >
                  </div>
                  <div style="padding: 20px 40px 40px">
                    <div class="layout-side">
                      <div class="layout-left-top" style="color: #f1f4f5">
                        <div style="margin-right: 60px">
                          题目节点
                          <a-tree-select
                            style="
                              max-width: 200px;
                              min-width: 150px;
                              width: calc(100% - 600px);
                            "
                            :treeData="knowledgeList"
                            placeholder="请选择节点"
                            treeDefaultExpandAll
                            @select="selectNode"
                          />
                        </div>
                        <div>
                          题目数量
                          <a-input-number
                            :max="i.list ? i.list.length : 0"
                            :min="0"
                            v-model:value="i.activeBank"
                          />
                        </div>
                      </div>
                      <div class="layout-left-top">
                        <div
                          class="item_group btn layout-center"
                          style="margin-top: 2px; margin-left: 8px"
                          @click="randomAdd"
                        >
                          添加
                          <IconFont
                            type="icon-tianjia1"
                            color="#f60"
                            class="iconAdd"
                            style="color: #7b90af; font-size: 18px"
                          ></IconFont>
                        </div>
                        <div
                          class="item_group btn layout-center"
                          style="margin-top: 2px; margin-left: 8px"
                          @click="randomUpdate"
                        >
                          刷新
                          <IconFont
                            type="icon-shuaxin1"
                            color="#f60"
                            class="iconAdd"
                            style="color: #7b90af; font-size: 18px"
                          ></IconFont>
                        </div>
                        <div
                          class="item_group btn layout-center"
                          style="margin-top: 2px; margin-left: 8px"
                          @click="randomEmpty"
                        >
                          清空
                          <IconFont
                            type="icon-clear"
                            color="#f60"
                            class="iconAdd"
                            style="color: #7b90af; font-size: 18px"
                          ></IconFont>
                        </div>
                      </div>
                    </div>
                    <div style="padding: 10px 0">
                      <div
                        class="layout-side topicList"
                        v-for="(item, index) in paperData[i.key]"
                      >
                        <div
                          style="
                            max-width: 1000px;
                            white-space: nowrap;
                            text-overflow: ellipsis;
                            overflow: hidden;
                            user-select: none;
                          "
                          :title="index + 1 + '、' + item.topic"
                        >
                          {{ index + 1 + '、' + item.topic }}
                        </div>
                        <div class="layout-center">
                          <IconFont
                            type="icon-close"
                            style="font-size: 22px"
                            class="iconAdd"
                            @click="deletePaper(item)"
                          ></IconFont>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </a-modal>
        <div class="shadowFind fade-in" v-if="preview">
          <div class="shadowInfo">
            <perviewTest :paperData="paperData"></perviewTest>
            <div class="w-full bottominfo">
              <div class="close" @click="preview = !preview"></div>
            </div>
          </div>
        </div>
      </div>
      <div class="w-full paperFooter" style="">
        <div
          class="item_group btn layout-center"
          style="margin-top: 8px; margin-left: 8px"
          @click="skipDetails"
        >
          返回
          <IconFont
            type="icon-rollback"
            color="#f60"
            class="iconAdd"
            style="color: #7b90af; font-size: 22px"
          ></IconFont>
        </div>
        <div
          class="item_group btn layout-center"
          style="margin-top: 8px; margin-left: 8px"
          @click="randomlySelected"
        >
          随机选题
          <IconFont
            type="icon-congtikuzhongxuanze1"
            color="#f60"
            class="iconAdd"
            style="color: #7b90af; font-size: 22px"
          ></IconFont>
        </div>
        <div
          class="item_group btn layout-center"
          style="margin-top: 8px; margin-left: 8px"
          @click="preview = !preview"
        >
          预览
          <IconFont
            type="icon-eye-fill"
            color="#f60"
            class="iconAdd"
            style="color: #7b90af; font-size: 22px"
          ></IconFont>
        </div>
        <div
          class="item_group btn layout-center"
          style="margin-top: 8px; margin-left: 8px"
          @click="submitTest"
        >
          提交
          <IconFont
            type="icon-check"
            color="#f60"
            class="iconAdd"
            style="color: #7b90af; font-size: 22px"
          ></IconFont>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'Index',
}
</script>
<script setup>
import { PlusOutlined, createFromIconfontCN } from '@ant-design/icons-vue'
import theNewTest from './js/theNewTest'
import addAndSelectTopic from './js/addAndSelectTopic'
import perviewTest from '../../../../../components/test/perviewTest/perviewTest.vue'
import nomore from '../../../../../components/nomore/nomore.vue'
import PreviewTheTopic from '../../../../../components/test/previewTheTopic/PreviewTheTopic.vue'
import questionBank from '../questionBank/Index.vue'
import { ref, onMounted, toRefs, getCurrentInstance, provide } from 'vue'
const IconFont = createFromIconfontCN({
  scriptUrl: window.iconUrl,
})

provide('realTimeAnwser', '')
const openMenu = ref(true)
const preview = ref(false)
const roomtest = ref()
const activeKnowledge = ref({})
const iconList = ref([
  'icon-zhiyudingceng1',
  'icon-a-houyiyicengfuben1',
  'icon-zhiyudiceng1',
  'icon-houyiyiceng1',
  'icon-bianji1',
  'icon-shanchu1',
])
const bankList = ref([
  {
    name: '一、单选题',
    key: 'singleChoice',
  },
  {
    name: '二、多选题',
    key: 'multipleChoice',
  },
  {
    name: '三、判断题',
    key: 'judge',
  },
  {
    name: '四、填空题',
    key: 'completion',
  },
  {
    name: '五、简答题',
    key: 'shortAnswer',
  },
])
onMounted(() => {
  queryKnowledgeTree()
  modifyTest()
})
const calculateScore = (e) => {
  let score = 0
  for (let j of e) {
    score = score + j.score
  }
  return score
}
const calculateTitle = (ee, e, index) => {
  let ind = 0
  if (ee === 'singleChoice') {
    ind = index
  } else if (ee === 'multipleChoice') {
    ind = e.singleChoice.length + index
  } else if (ee === 'judge') {
    ind = e.multipleChoice.length + e.singleChoice.length + index
  } else if (ee === 'completion') {
    ind =
      e.judge.length + e.multipleChoice.length + e.singleChoice.length + index
  } else if (ee === 'shortAnswer') {
    ind =
      e.shortAnswer.length +
      e.judge.length +
      e.multipleChoice.length +
      e.singleChoice.length +
      index
  }
  return ind + 1
}

const positionScroll = (e) => {
  let target=document.getElementById(e);
  if (e.indexOf('title')===-1){
    target.parentNode.parentNode.scrollTop=target.offsetTop-70
  }else {
    target.parentNode.scrollTop=target.offsetTop-60
  }
}
const {
  knowledgeList,
  paperData,
  listData,
  params,
  knowledgeShow,
  updateBank,
  queryKnowledgeTree,
  clickMenu,
  selectTree,
  modifyTheScores,
  bornTest,
  addTopic,
  clickActive,
  submitTest,
  modifyTest,
  skipDetails,
  actionBar,
  findAllQuestion,
} = theNewTest(roomtest)
const {
  selectDrillModal,
  topicType,
  activeList,
  randomValue,
  randomBankList,
  randomLoading,
  selectTopic,
  randomlySelected,
  selectNode,
  randomActive,
  randomAdd,
  randomUpdate,
  randomEmpty,
  deletePaper,
  inputNumber,
} = addAndSelectTopic(paperData, findAllQuestion)
</script>

<style lang="less" scoped>

  @import "./css/KJ";
  @import "./css/LJ";
  @import "./css/HJ";
  @import "./css/HJJ";





</style>
