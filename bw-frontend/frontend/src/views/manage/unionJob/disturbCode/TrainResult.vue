<template>
  <div class="w-full h-full aligned-result">
    <div class="telegrapHead">
      <div>页码：【{{ props.curr }}/{{ props.all }}】<span style="padding-left: 20px" v-if="header">报头：{{header}}</span></div>
      <div class="title_name">{{ details?.name }}</div>
      <div class="page" v-if="props.all > 1">
        <div :class="{ pag: true, disabled: props.curr == 1 }" @click="pageTurn('prev')">上一页</div>
        <div :class="{ pag: true, disabled: props.curr == props.all }" @click="pageTurn('next')">下一页</div>
      </div>
    </div>
    <div class="result-legend">上行报底 / 下行答案 · − 漏码（删除） · + 多码（插入） · → 替换<span v-if="alignment.extraPage"> · 超出报底</span></div>
    <div v-if="!props.result?.pageVos && !props.result?.value" class="result-notice" role="status">正在加载本页结果…</div>
    <div v-else-if="alignment.error" class="result-raw" role="alert">
      <div>{{ alignment.error }}</div>
      <div>报底：{{ props.result?.pageVos?.map(group => group.key).join(' ') || '无' }}</div>
      <div>答案：{{ props.result?.value?.join(' ') || '无' }}</div>
    </div>
    <div v-else class="w-full result-scroll">
      <div class="patTelegraph">
        <div class="telegraph" style="height: auto">
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
          <div class="keyBox" style="height: auto; min-height: 0">
            <div v-for="(group, index) in displayGroups" :key="index" class="key" style="height: 64px" :title="groupTitle(group)">
              <div class="title" style="overflow: auto; justify-content: flex-start; padding: 0 4px">
                <span v-if="group.type === 'insert'" class="result-insert">{{ alignment.extraPage ? '超出报底' : '多组' }}</span>
                <span v-else>{{ group.expected || '—' }}</span>
                <small v-if="group.type === 'delete'" class="result-delete">（漏组）</small>
              </div>
              <div class="val" style="overflow: auto; justify-content: flex-start">
                <span v-if="!group.characters.length">—</span>
                <strong v-for="(character, charIndex) in group.characters" :key="charIndex" :class="'result-' + character.type" style="white-space: nowrap" :title="characterTitle(character)">{{ characterText(character) }}</strong>
              </div>
            </div>
          </div>
        </div>
        <div class="serial">
          <div class="ser head" style="flex-grow: 0"></div>
          <div v-for="row in Math.ceil(displayGroups.length / 10)" :key="row" class="ser" style="height: 64px; flex-grow: 0">{{ row * 10 }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
  export default {
    name: 'TrainResult'
  }
</script>
<script setup>
  import { onMounted, ref, computed, defineEmits, defineProps } from 'vue'
  import { alignResultPage } from '../../../../common/utils/resultAlignment.js'
  import {findHeader} from "../../../../common/api/ReceiveApi.js";
  import {useRoute} from "vue-router"
  const route = useRoute();
  const props = defineProps({
    result: {
      type: Object,
      default: () => ({})
    },
    curr: {
      type: Number,
      default: 1
    },
    all: {
      type: Number,
      default: 1
    },
    details: {
      type: Object
    }
  })
  const emit = defineEmits(['switchPage'])
  const header = ref('')
  onMounted(()=>{
    findHeader(route.query.id).then(res=>{
      if(res.data!==null){
        const arr = res.data.content.split(' ')
        arr[3] = arr[3].replaceAll("T",'0')
        arr[4] = arr[4].replaceAll("T",'0')
        arr.forEach(item=>{
          header.value +=" "+item
        })
      }
    })
  })
  const alignment = computed(() => {
    try {
      return alignResultPage(props.result)
    } catch (error) {
      if (!(error instanceof RangeError)) throw error
      return { groups: [], extraPage: false, error: error.message }
    }
  })
  const displayGroups = computed(() => {
    const groups = alignment.value.groups.slice()
    while (groups.length < 100 || groups.length % 10) {
      groups.push({ type: 'empty', expected: '', actual: '', characters: [] })
    }
    return groups
  })
  const groupTitle = group => group.type === 'empty' ? '' : `报底组 ${group.expectedIndex === null ? '无' : group.expectedIndex + 1} / 答案组 ${group.actualIndex === null ? '无' : group.actualIndex + 1}`
  const characterText = character => character.type === 'delete' ? `−${character.expected}` : character.type === 'insert' ? `+${character.actual}` : character.type === 'replace' ? `${character.expected}→${character.actual}` : character.actual
  const characterTitle = character => character.type === 'delete' ? `漏字符：${character.expected}` : character.type === 'insert' ? `多字符：${character.actual}` : character.type === 'replace' ? `替换：${character.expected} → ${character.actual}` : '正确'

  const pageTurn = type => {
    if ((type == 'next' && props.curr == props.all) || (type == 'prev' && props.curr == 1)) return false
    emit('switchPage', type)
  }
</script>

<style lang="less" scoped>
  .aligned-result {
    .result-legend { min-height: 28px; padding: 2px 16px; color: #ffcb7c; }
    .result-notice, .result-raw { padding: 16px; overflow-wrap: anywhere; }
    .result-scroll { height: calc(100% - 68px); overflow: auto; }
    .patTelegraph .telegraph { height: auto; }
    .patTelegraph .serial .ser.head { flex-grow: 0; }
    .result-equal { color: #fff; }
    .result-delete { color: #ffcb7c; }
    .result-insert { color: #81d9ff; }
    .result-replace { color: #ff8b8b; }
  }
  .HJ,.HJJ{
    .telegrapHead {
      height: 40px;
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 0 36px 10px 16px;
      .page {
        height: 30px;
        display: flex;
        align-items: center;
        flex-shrink: 0;
        .pag {
          height: 26px;
          display: flex;
          align-items: center;
          justify-content: center;
          color: #70a3b8;
          font-size: 13px;
          position: relative;
          padding: 0 20px;
          border: 1px solid #70a3b8;
          cursor: pointer;
          &:hover {
            background-color: #4c7595;
            color: #fff;
          }
          &.disabled {
            opacity: 0.3;
            cursor: no-drop;
            &:hover {
              background-color: transparent;
              color: #4c7595;
            }
          }
          & + .pag {
            margin-left: 10px;
          }
        }
      }
    }
    .patTelegraph {
      width: 100%;
      display: flex;
      position: relative;
    }
    .patTelegraph .serial {
      width: 30px;
      margin-left: 5px;
      display: flex;
      flex-direction: column;
      color: #8eafca;
      align-content: stretch;
    }
    .patTelegraph .serial .ser {
      height: calc((100% - 30px) / 10);
      flex-grow: 1;
      flex-shrink: 0;
      display: flex;
      align-items: center;
    }
    .patTelegraph .serial .ser.head {
      height: 30px;
      flex-grow: 1;
      flex-shrink: 0;
      display: flex;
      align-items: center;
    }
    .patTelegraph .telegraph {
      width: 100%;
      height: 100%;
      border: 1px solid #171e27;
      background-color: #213141;
      display: flex;
      flex-direction: column;
    }
    .patTelegraph .telegraph .rowHead {
      width: 100%;
      height: 30px;
      display: flex;
      flex-shrink: 0;
    }
    .patTelegraph .telegraph .keyBox {
      display: flex;
      flex-wrap: wrap;
      height: 100%;
      min-height: 480px;
    }
    .patTelegraph .telegraph .keyBox .key {
      min-height: 48px;
    }
    .patTelegraph .telegraph .key {
      color: #fff;
      width: 10%;
      height: 10%;
      text-align: center;
      border-right: 1px solid #171e27;
    }
    .patTelegraph .telegraph .key:nth-of-type(10n) {
      border-right: none;
    }
    .patTelegraph .telegraph .key:nth-of-type(n + 11) {
      border-top: 1px solid #171e27;
    }
    .patTelegraph .telegraph .rowHead .key {
      color: #161e29;
      height: 100%;
      font-weight: bold;
      font-size: 18px;
      background-color: #4c7595;
      border: none;
    }
    .patTelegraph .telegraph .key .title {
      font-size: 14px;
      height: 50%;
      background-color: #463e2f;
      color: #fff;
      font-weight: normal;
      display: flex;
      align-items: center;
      justify-content: center;
    }
    .patTelegraph .telegraph .key .val {
      font-size: 15px;
      height: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 0 3px;
    }
    .patTelegraph .telegraph .key .error {
      color: #d11d1d;
      font-weight: bold;
    }
    .patTelegraph .telegraph .key .omission {
      color: #4c7595;
      font-weight: bold;
    }
  }
  .LJ{
    .telegrapHead {
      height: 40px;
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 0 36px 10px 16px;
      .page {
        height: 30px;
        display: flex;
        align-items: center;
        flex-shrink: 0;
        .pag {
          height: 26px;
          display: flex;
          align-items: center;
          justify-content: center;
          color: #70a3b8;
          font-size: 13px;
          position: relative;
          padding: 0 20px;
          border: 1px solid #70a3b8;
          cursor: pointer;
          &:hover {
            background-color: #4c7595;
            color: #fff;
          }
          &.disabled {
            opacity: 0.3;
            cursor: no-drop;
            &:hover {
              background-color: transparent;
              color: #4c7595;
            }
          }
          & + .pag {
            margin-left: 10px;
          }
        }
      }
    }
    .patTelegraph {
      width: 100%;
      display: flex;
      position: relative;
    }
    .patTelegraph .serial {
      width: 30px;
      margin-left: 5px;
      display: flex;
      flex-direction: column;
      color: #a9abaa;
      align-content: stretch;
    }
    .patTelegraph .serial .ser {
      height: calc((100% - 30px) / 10);
      flex-grow: 1;
      flex-shrink: 0;
      display: flex;
      align-items: center;
    }
    .patTelegraph .serial .ser.head {
      height: 30px;
      flex-grow: 1;
      flex-shrink: 0;
      display: flex;
      align-items: center;
    }
    .patTelegraph .telegraph {
      width: 100%;
      height: 100%;
      border: 1px solid #171e27;
      background-color: rgba(21,39,39,0.5);
      display: flex;
      flex-direction: column;
    }
    .patTelegraph .telegraph .rowHead {
      width: 100%;
      height: 30px;
      display: flex;
      flex-shrink: 0;
    }
    .patTelegraph .telegraph .keyBox {
      display: flex;
      flex-wrap: wrap;
      height: 100%;
      min-height: 480px;
    }
    .patTelegraph .telegraph .keyBox .key {
      min-height: 48px;
    }
    .patTelegraph .telegraph .key {
      color: #fff;
      width: 10%;
      height: 10%;
      text-align: center;
      border-right: 1px solid #171e27;
    }
    .patTelegraph .telegraph .key:nth-of-type(10n) {
      border-right: none;
    }
    .patTelegraph .telegraph .key:nth-of-type(n + 11) {
      border-top: 1px solid #171e27;
    }
    .patTelegraph .telegraph .rowHead .key {
      color: #a9abaa;
      height: 100%;
      font-weight: bold;
      font-size: 18px;
      background-color: #38403d;
      border: none;
    }
    .patTelegraph .telegraph .key .title {
      font-size: 14px;
      height: 50%;
      background-color: #463e2f;
      color: #fff;
      font-weight: normal;
      display: flex;
      align-items: center;
      justify-content: center;
    }
    .patTelegraph .telegraph .key .val {
      font-size: 15px;
      height: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 0 3px;
    }
    .patTelegraph .telegraph .key .error {
      color: #d11d1d;
      font-weight: bold;
    }
    .patTelegraph .telegraph .key .omission {
      color: #4c7595;
      font-weight: bold;
    }
  }
  .KJ{
    .telegrapHead {
      height: 40px;
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 0 36px 10px 16px;
      .page {
        height: 30px;
        display: flex;
        align-items: center;
        flex-shrink: 0;
        .pag {
          height: 26px;
          display: flex;
          align-items: center;
          justify-content: center;
          color: #70a3b8;
          font-size: 13px;
          position: relative;
          padding: 0 20px;
          border: 1px solid #70a3b8;
          cursor: pointer;
          &:hover {
            background-color: #4c7595;
            color: #fff;
          }
          &.disabled {
            opacity: 0.3;
            cursor: no-drop;
            &:hover {
              background-color: transparent;
              color: #4c7595;
            }
          }
          & + .pag {
            margin-left: 10px;
          }
        }
      }
    }
    .patTelegraph {
      width: 100%;
      display: flex;
      position: relative;
    }
    .patTelegraph .serial {
      width: 30px;
      margin-left: 5px;
      display: flex;
      flex-direction: column;
      color: #a9abaa;
      align-content: stretch;
    }
    .patTelegraph .serial .ser {
      height: calc((100% - 30px) / 10);
      flex-grow: 1;
      flex-shrink: 0;
      display: flex;
      align-items: center;
    }
    .patTelegraph .serial .ser.head {
      height: 30px;
      flex-grow: 1;
      flex-shrink: 0;
      display: flex;
      align-items: center;
    }
    .patTelegraph .telegraph {
      width: 100%;
      height: 100%;
      border: 1px solid #171e27;
      background-color: rgba(31, 67, 99, 0.3);
      display: flex;
      flex-direction: column;
    }
    .patTelegraph .telegraph .rowHead {
      width: 100%;
      height: 30px;
      display: flex;
      flex-shrink: 0;
    }
    .patTelegraph .telegraph .keyBox {
      display: flex;
      flex-wrap: wrap;
      height: 100%;
      min-height: 480px;
      background: rgba(31, 67, 99, 0.6);
    }
    .patTelegraph .telegraph .keyBox .key {
      min-height: 48px;
    }
    .patTelegraph .telegraph .key {
      color: #fff;
      width: 10%;
      height: 10%;
      text-align: center;
      border-right: 1px solid #171e27;
    }
    .patTelegraph .telegraph .key:nth-of-type(10n) {
      border-right: none;
    }
    .patTelegraph .telegraph .key:nth-of-type(n + 11) {
      border-top: 1px solid #171e27;
    }
    .patTelegraph .telegraph .rowHead .key {
      color: #a9abaa;
      height: 100%;
      font-weight: bold;
      font-size: 18px;
      background-color: rgba(80,141,230,0.6);
      border: none;
    }
    .patTelegraph .telegraph .key .title {
      font-size: 14px;
      height: 50%;
      background-color: #463e2f;
      color: #fff;
      font-weight: normal;
      display: flex;
      align-items: center;
      justify-content: center;
    }
    .patTelegraph .telegraph .key .val {
      font-size: 15px;
      height: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 0 3px;
    }
    .patTelegraph .telegraph .key .error {
      color: #d11d1d;
      font-weight: bold;
    }
    .patTelegraph .telegraph .key .omission {
      color: #4c7595;
      font-weight: bold;
    }
  }
</style>
