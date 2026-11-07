<template>
  <a-button size="small" @click="visible = true">扣分依据</a-button>
  <a-modal v-model:visible="visible" title="电子键扣分依据" :width="1100" :footer="null" class="init_modal_style footer-border-none noBottomBg electronic-evidence-modal">
    <div class="evidence-content">
      <div class="heading"><h3>整场扣分汇总</h3><span>所有页合计 · 以本次保存结果为准</span></div>
      <div class="summary-grid">
        <div v-for="[key, label] in Object.entries(labels)" :key="key" class="summary-item" :class="{charged: Number(summary?.[key + 'Number']) > 0}">
          <span>{{label}}</span><strong>{{summary?.[key + 'Number'] ?? '—'}} {{unit(key)}}</strong>
          <small>{{summary?.[key + 'Score'] ?? '—'}} 分</small>
        </div>
      </div>
      <p class="notice">速率加减分：{{summary?.speedScore ?? '—'}} 分。<template v-if="rule?.wpm && scoringUnit">计分码率 {{scoringSpeed}} {{scoringUnit}}，冻结基准 {{rule.wpm.base}} {{scoringUnit}}；每高1加 {{rule.wpm.r ?? 0}} 分，每低1扣 {{rule.wpm.l ?? 0}} 分。<template v-if="scoringUnit === '四码组/分'">该基准相当于 {{Number(rule.wpm.base) * 4}} 码/分。</template></template><br>每页独立对照该页报底；翻页后的内容不会移回上一页抵消漏拍。</p>
      <div class="heading"><h3>报文扣分明细</h3><span>位置为本页报底及原始拍发组号</span></div>
      <a-empty v-if="summary?.evidenceVersion !== 1" description="该历史成绩未保存逐组扣分依据，不根据当前规则猜算。" />
      <template v-else>
        <div class="filters"><label>查看页码 <select v-model="selectedPage"><option value="all">全部页</option><option v-for="page in pages" :key="page" :value="String(page)">第 {{page}} 页</option></select></label></div>
        <a-empty v-if="!rows.length" description="所选范围没有扣分项" />
        <div v-else class="table-scroll"><table>
          <thead><tr><th>页 / 位置</th><th>标准报底</th><th>实际拍发</th><th>判定与数量</th><th>扣分计算</th></tr></thead>
          <tbody><tr v-for="(row, index) in rows" :key="index">
            <td><b>第 {{row.pageNumber}} 页</b><div v-if="row.referenceGroup">报底第 {{range(row.referenceGroup, row.referenceThrough)}} 组</div><div v-if="row.capturedGroup">拍发第 {{range(row.capturedGroup, row.capturedThrough)}} 组</div></td>
            <td><div class="telegram reference">{{row.reference || '无对应报底'}}</div></td>
            <td><div class="telegram captured">{{row.captured || '未拍发'}}</div></td>
            <td><strong>{{labels[row.kind] || row.kind}} · {{row.count}} {{unit(row.kind)}}</strong><div class="reason">{{reasons[row.kind]}}</div><div v-if="row.partnerReferenceGroup" class="reason">与报底第 {{row.partnerReferenceGroup}} 组互换，两组共计一次</div></td>
            <td><span v-if="row.kind === 'bunchGroup' && row.count === 0">配对已计分<br>此处不重复扣分</span><template v-else>{{row.count}} × {{row.unitDeduction}}<br><b class="amount">扣 {{row.deduction}} 分</b></template></td>
          </tr></tbody>
        </table></div>
      </template>
    </div>
  </a-modal>
</template>

<script setup>
import {computed, ref} from 'vue'
const props = defineProps({summary: {type: Object, default: null}, rule: {type: Object, default: null}})
const scoringUnit = computed(() => {
  const unit = props.summary?.scoringRateUnit || props.rule?.rateUnit
  return unit === 'CHARACTERS_PER_MINUTE' ? '码/分' : unit === 'FOUR_CHARACTER_GROUPS_PER_MINUTE' ? '四码组/分' : null
})
const scoringSpeed = computed(() => props.summary?.scoringSpeedNumber ?? (scoringUnit.value === '四码组/分' ? props.summary?.speedNumber : '—'))
const visible = ref(false)
const selectedPage = ref('all')
const evidence = computed(() => props.summary?.evidence || [])
const pages = computed(() => [...new Set(evidence.value.map(item => item.pageNumber))].sort((a, b) => a - b))
const rows = computed(() => evidence.value.filter(item => selectedPage.value === 'all' || item.pageNumber === Number(selectedPage.value)))
const labels = {error: '错码', lack: '少码', more: '多码', lackGroup: '少组', moreGroup: '多组', lackLine: '少行', moreLine: '多行', alterError: '改错', bunchGroup: '串组', lackGap: '少间隔'}
const reasons = {error: '拍发内容与本页对应报底不符', lack: '缺失字符后其余内容与报底一致', more: '去除多余字符后与报底一致',
  lackGroup: '未拍发的零散报文组，不重复计入少行', moreGroup: '超出报底的零散报文组', lackLine: '连续缺失完整十组，按行计分，不再按错码计分',
  moreLine: '连续多出完整十组，按行计分', alterError: '改错后内容正确，按改错规则计分', bunchGroup: '两组报文互换', lackGap: '连续正确报文组之间缺少分组间隔'}
const unit = kind => kind.endsWith('Line') ? '行' : kind === 'bunchGroup' ? '对' : kind === 'lackGap' ? '处' : '组'
const range = (start, end) => end && end !== start ? `${start}—${end}` : start
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
.electronic-evidence-modal.ant-modal {max-width:calc(100vw - 32px);top:24px;padding-bottom:0;}
.electronic-evidence-modal.ant-modal .ant-modal-content {display:flex;flex-direction:column;max-height:calc(100vh - 48px);background:#102333;}
.electronic-evidence-modal.ant-modal .ant-modal-header {flex-shrink:0;}
.electronic-evidence-modal.ant-modal .ant-modal-body {min-height:0;overflow:auto;padding:0;scrollbar-gutter:stable;}
/* 主题对正文首个 div 使用了 !important，须在本弹窗内覆盖，保留末行后的滚动留白。 */
.electronic-evidence-modal.ant-modal .ant-modal-body > .evidence-content {padding:24px 24px 32px !important;}
@media(max-width:600px){.electronic-evidence-modal.ant-modal .ant-modal-body > .evidence-content{padding:16px 16px 24px !important;}}
.electronic-evidence-modal .ant-empty-description {color:#b5c8d9;}
.electronic-evidence-modal .ant-empty {margin:24px 0;}
</style>
