<template>
  <a-button size="small" @click="visible = true">扣分依据</a-button>
  <a-modal v-model:visible="visible" title="数据报扣分依据" :width="1100" :footer="null" class="init_modal_style footer-border-none noBottomBg datagram-evidence-modal">
    <div class="evidence-content">
      <div class="heading"><h3>整场扣分汇总</h3><span>所有页合计 · 以本次保存结果为准</span></div>
      <div class="summary-grid">
        <div v-for="[key, label] in Object.entries(labels)" :key="key" class="summary-item" :class="{charged: Number(summary?.[key + 'Number']) > 0}">
          <span>{{label}}</span><strong>{{summary?.[key + 'Number'] ?? '—'}} {{unit(key)}}</strong>
          <small>{{summary?.[key + 'Score'] ?? '—'}} 分</small>
        </div>
      </div>
      <p class="notice">速率加减分：{{summary?.speedScore ?? '—'}} 分。<br>每页独立对照该页报底；扣分单价取自本场训练冻结的评分规则，规则后续调整不影响本成绩。</p>
      <div class="heading"><h3>报文扣分明细</h3><span>位置为本页报底组号</span></div>
      <a-empty v-if="summary?.evidenceVersion !== 1" description="该历史成绩未保存逐项扣分依据，不根据当前规则猜算。" />
      <template v-else>
        <div class="filters"><label>查看页码 <select v-model="selectedPage"><option value="all">全部页</option><option v-for="page in pages" :key="page" :value="String(page)">第 {{page}} 页</option></select></label></div>
        <a-empty v-if="!rows.length" description="所选范围没有扣分项" />
        <div v-else class="table-scroll"><table>
          <thead><tr><th>页 / 位置</th><th>标准报底</th><th>实际拍发</th><th>判定与数量</th><th>扣分计算</th></tr></thead>
          <tbody><tr v-for="(row, index) in rows" :key="index">
            <td><b>第 {{row.pageNumber}} 页</b><div v-if="row.referenceGroup">报底第 {{row.referenceGroup}} 组</div></td>
            <td><div class="telegram reference">{{row.reference || '无对应报底'}}</div></td>
            <td><div class="telegram captured">{{row.captured || '未拍发'}}</div></td>
            <td><strong>{{labels[row.kind] || row.kind}} · {{row.count}} {{unit(row.kind)}}</strong><div class="reason">{{reasons[row.kind]}}</div></td>
            <td>{{row.count}} × {{row.unitDeduction}}<br><b class="amount">扣 {{row.deduction}} 分</b></td>
          </tr></tbody>
        </table></div>
      </template>
    </div>
  </a-modal>
</template>

<script setup>
import {computed, ref} from 'vue'
const props = defineProps({summary: {type: Object, default: null}})
const visible = ref(false)
const selectedPage = ref('all')
const evidence = computed(() => props.summary?.evidence || [])
const pages = computed(() => [...new Set(evidence.value.map(item => item.pageNumber))].sort((a, b) => a - b))
const rows = computed(() => evidence.value.filter(item => selectedPage.value === 'all' || item.pageNumber === Number(selectedPage.value)))
const labels = {errorCode: '错码', muchLessLine: '多少行', muchLessGroups: '多少组', muchLessCode: '多少码',
  lessReturnLine: '少回行', lessPage: '少页标', errorPage: '页标错', nonStandart: '不归', correctMistakes: '改错'}
const reasons = {errorCode: '拍发内容与本页对应报底不符', muchLessLine: '整行（连续十组）多拍或漏拍',
  muchLessGroups: '多拍或漏拍的零散报文组', muchLessCode: '拍发比报底多码或少码，去除后与报底一致',
  lessReturnLine: '一行超过十组未回行', lessPage: '报文末尾缺少页标', errorPage: '页标与当前页号不符',
  nonStandart: '改错或增删组格式不符合规范', correctMistakes: '改错后内容正确，按改错规则计分'}
const unit = kind => kind === 'muchLessLine' ? '行' : kind === 'muchLessGroups' || kind === 'muchLessCode' ? '组' : '个'
</script>

<style scoped>
.evidence-content {box-sizing:border-box;color:#e5edf5;font-size:14px;line-height:1.7;}
.heading {display:flex;align-items:baseline;justify-content:space-between;gap:8px 20px;margin-bottom:16px;flex-wrap:wrap;}
.heading h3 {margin:0;color:#eff6ff;font-size:17px;line-height:1.5;}.heading span,.reason {color:#a5bad0;font-size:12px;}
.summary-grid {display:grid;grid-template-columns:repeat(5,minmax(0,1fr));gap:12px;}
.summary-item {display:flex;flex-direction:column;gap:6px;padding:14px 16px;background:#172c3d;border:1px solid #324a60;border-radius:6px;}
.summary-item span,.summary-item small {color:#b0c1d1;}.summary-item strong {font-size:21px;line-height:1.4;}.summary-item small {font-size:12px;}.charged strong,.amount {color:#ffbf77;}
.notice {padding:16px 20px;margin:24px 0 28px;background:#19364b;border-left:3px solid #6abdec;border-radius:0 6px 6px 0;color:#c3d9ec;line-height:1.9;}
.filters {margin:0 0 16px;}.filters label {display:inline-flex;gap:12px;align-items:center;}.filters select {min-width:120px;min-height:36px;background:#19364b;color:#e5edf5;border:1px solid #56718b;border-radius:4px;padding:6px 12px;}.filters select:focus-visible {outline:2px solid #6abdec;outline-offset:2px;}
.table-scroll {overflow-x:auto;border:1px solid #3b536b;border-radius:6px;}
table {width:100%;border-collapse:separate;border-spacing:0;table-layout:fixed;min-width:820px;}
th,td {padding:16px;text-align:left;vertical-align:top;border-bottom:1px solid #344b60;overflow-wrap:anywhere;}
th {padding-top:12px;padding-bottom:12px;background:#29445c;color:#d7e8f7;font-weight:500;}th:first-child {width:17%;}th:nth-child(2),th:nth-child(3) {width:23%;}th:nth-child(4) {width:24%;}th:last-child {width:13%;}
td {background:#142839;}tr:nth-child(even) td {background:#1b3042;}tbody tr:last-child td {border-bottom:0;}td:first-child b {display:block;margin-bottom:8px;}td:first-child div + div {margin-top:4px;}
.telegram {font-family:Consolas,monospace;white-space:pre-wrap;font-size:15px;line-height:1.9;overflow-wrap:anywhere;}
.reference {color:#b7e2ff;}.captured {color:#ffcc91;}.reason {margin-top:8px;line-height:1.75;}.amount {display:inline-block;margin-top:8px;}
@media(max-width:900px){.summary-grid{grid-template-columns:repeat(3,minmax(0,1fr));}}
@media(max-width:600px){.summary-grid{grid-template-columns:repeat(2,minmax(0,1fr));gap:10px;}.summary-item{padding:12px;}.notice{padding:12px 14px;margin:20px 0 24px;}.heading{gap:6px;}}
</style>
<style>
.datagram-evidence-modal.ant-modal {max-width:calc(100vw - 32px);top:24px;padding-bottom:0;}
.datagram-evidence-modal.ant-modal .ant-modal-content {display:flex;flex-direction:column;max-height:calc(100vh - 48px);background:#102333;}
.datagram-evidence-modal.ant-modal .ant-modal-header {flex-shrink:0;}
.datagram-evidence-modal.ant-modal .ant-modal-body {min-height:0;overflow:auto;padding:0;scrollbar-gutter:stable;}
/* 主题对正文首个 div 使用了 !important，须在本弹窗内覆盖，保留末行后的滚动留白。 */
.datagram-evidence-modal.ant-modal .ant-modal-body > .evidence-content {padding:24px 24px 32px !important;}
@media(max-width:600px){.datagram-evidence-modal.ant-modal .ant-modal-body > .evidence-content{padding:16px 16px 24px !important;}}
.datagram-evidence-modal .ant-empty-description {color:#b5c8d9;}
.datagram-evidence-modal .ant-empty {margin:24px 0;}
</style>
