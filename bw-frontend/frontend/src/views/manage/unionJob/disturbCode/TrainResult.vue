<template>
  <div class="w-full h-full aligned-result">
    <div class="result-header">
      <div class="result-page-info">
        <span>页码：【{{ props.curr }}/{{ props.all }}】</span>
      </div>
      <div v-if="details?.name" class="result-name">{{ details.name }}</div>
      <div v-if="props.all > 1" class="result-pagination">
        <button type="button" :disabled="props.curr <= 1" @click="pageTurn('prev')">上一页</button>
        <button type="button" :disabled="props.curr >= props.all" @click="pageTurn('next')">下一页</button>
      </div>
    </div>
    <div class="result-legend">
      <span>上行报底 / 下行答案</span>
    </div>
    <div v-if="!props.result?.pageVos && !props.result?.value" class="result-notice" role="status">正在加载本页结果…</div>
    <div v-else-if="alignment.error" class="result-raw" role="alert">
      <div>{{ alignment.error }}</div>
      <div>报底：{{ props.result?.pageVos?.map(group => group.key).join(' ') || '无' }}</div>
      <div>答案：{{ props.result?.value?.join(' ') || '无' }}</div>
    </div>
    <div v-else class="result-scroll">
      <table class="result-table" aria-label="报底与抄收答案对照">
        <colgroup>
          <col v-for="column in 10" :key="column" />
          <col class="result-row-number-column" />
        </colgroup>
        <thead>
          <tr>
            <th v-for="column in 10" :key="column" scope="col">{{ column }}</th>
            <th scope="col" class="result-row-number">组号</th>
          </tr>
        </thead>
        <tbody>
          <template v-for="(row, rowIndex) in displayRows" :key="rowIndex">
            <tr class="result-expected-row">
              <td v-for="(group, columnIndex) in row" :key="columnIndex" class="result-cell" :class="{ 'result-empty': group.type === 'empty' }">
                <div class="result-expected">
                  <template v-for="(character, charIndex) in group.characters" :key="charIndex">
                    <span v-if="character.expected" class="result-character">{{ character.expected }}</span>
                  </template>
                </div>
              </td>
              <th scope="row" rowspan="2" class="result-row-number">{{ (rowIndex + 1) * 10 }}</th>
            </tr>
            <tr class="result-actual-row">
              <td v-for="(group, columnIndex) in row" :key="columnIndex" class="result-cell" :class="{ 'result-empty': group.type === 'empty' }">
                <div class="result-actual">
                  <span v-if="!group.actual" class="result-placeholder">--</span>
                  <template v-for="(character, charIndex) in group.characters" :key="charIndex">
                    <span v-if="character.actual" class="result-character" :class="{ 'result-error': character.type !== 'equal' }">{{ character.actual }}</span>
                  </template>
                </div>
              </td>
            </tr>
          </template>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script>
export default { name: 'TrainResult' }
</script>
<script setup>
import {computed} from 'vue'
import {alignResultPage} from '../../../../common/utils/resultAlignment.js'

const props = defineProps({
  result: {type: Object, default: () => ({})},
  curr: {type: Number, default: 1},
  all: {type: Number, default: 1},
  details: {type: Object}
})
const emit = defineEmits(['switchPage'])
const alignment = computed(() => {
  try {
    return alignResultPage(props.result)
  } catch (error) {
    if (!(error instanceof RangeError)) throw error
    return {groups: [], extraPage: false, error: error.message}
  }
})
const displayRows = computed(() => {
  const groups = alignment.value.groups.slice()
  while (groups.length < 100 || groups.length % 10) {
    groups.push({type: 'empty', expected: '', actual: '', characters: []})
  }
  const rows = []
  for (let index = 0; index < groups.length; index += 10) rows.push(groups.slice(index, index + 10))
  return rows
})
const pageTurn = type => {
  if ((type === 'next' && props.curr >= props.all) || (type === 'prev' && props.curr <= 1)) return
  emit('switchPage', type)
}
</script>

<style lang="less" scoped>
.aligned-result {
  --result-border: #354b60;
  --result-surface: #213141;
  --result-heading: #4c7595;
  --result-expected: #463e2f;
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  color: #e2f2ff;
}
.LJ .aligned-result, .GD .aligned-result {
  --result-border: #45534c;
  --result-surface: #1c302c;
  --result-heading: #384b42;
}
.KJ .aligned-result {
  --result-border: #345674;
  --result-surface: #1f4363;
  --result-heading: #365f97;
}
.result-header {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  flex-shrink: 0;
  gap: 8px 16px;
  padding: 8px 12px;
}
.result-page-info {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 16px;
  min-width: 0;
}
.result-name { flex: 1; text-align: center; overflow-wrap: anywhere; }
.result-pagination {
  display: flex;
  flex-shrink: 0;
  gap: 8px;
  margin-left: auto;
  button {
    padding: 4px 14px;
    border: 1px solid #70a3b8;
    border-radius: 3px;
    background: transparent;
    color: #c8e4ef;
    cursor: pointer;
    &:hover:not(:disabled) { background: #4c7595; color: #fff; }
    &:disabled { opacity: 0.35; cursor: not-allowed; }
  }
}
.result-legend {
  display: flex;
  flex-wrap: wrap;
  flex-shrink: 0;
  gap: 6px 18px;
  padding: 4px 12px 10px;
  font-size: 12px;
  color: #bfcde0;
}
.result-notice, .result-raw { padding: 16px; overflow: auto; overflow-wrap: anywhere; }
.result-scroll {
  flex: 1;
  width: 100%;
  min-height: 0;
  min-width: 0;
  overflow: auto;
  border: 1px solid var(--result-border);
}
.result-table {
  width: 100%;
  height: 100%;
  min-width: 960px;
  table-layout: fixed;
  border-collapse: separate;
  border-spacing: 0;
  background: var(--result-surface);
  th, td {
    padding: 0;
    border-right: 1px solid var(--result-border);
    border-bottom: 1px solid var(--result-border);
    text-align: center;
  }
  thead tr > :last-child, tbody .result-row-number { border-right: none; }
  tbody tr:last-child > * { border-bottom: none; }
  thead th {
    position: sticky;
    top: 0;
    z-index: 1;
    height: 32px;
    background: var(--result-heading);
    color: #fff;
    font-size: 15px;
    font-weight: 600;
  }
  .result-cell { vertical-align: middle; color: #fff; }
  .result-expected-row > td { background: var(--result-expected); }
  .result-expected-row > td.result-empty { background: rgba(255, 255, 255, 0.025); }
  .result-row-number-column { width: 42px; }
  .result-row-number { color: #bfcde0; font-size: 12px; font-weight: normal; }
}
.result-expected {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-wrap: wrap;
  min-height: 30px;
  padding: 4px;
  overflow-wrap: anywhere;
  font: 15px/22px Consolas, Monaco, monospace;
}
.result-actual {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  min-height: 34px;
  padding: 5px 4px;
  font: 15px/22px Consolas, Monaco, monospace;
}
.result-character { white-space: nowrap; }
.result-error { color: #ff8b8b; }
</style>
