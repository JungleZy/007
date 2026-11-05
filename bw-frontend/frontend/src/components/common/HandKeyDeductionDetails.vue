<template>
  <a-button size="small" @click="visible = true">扣分依据</a-button>
  <a-modal v-model:visible="visible" title="扣分依据" :width="1080" :footer="null" class="init_modal_style footer-border-none deduction-modal" :body-style="{padding: '20px'}">
    <div class="deduction-content">
    <div class="evidence-heading"><div><span class="eyebrow">评分明细</span><h3>第 {{page}} 页 · 报文扣分依据</h3></div><span class="page-badge">第 {{page}} 页</span></div>
    <section class="summary-section">
      <div class="section-heading"><h4>整场扣分汇总</h4><span>已应用各项封顶</span></div>
      <div class="summary-grid">
        <div v-for="[key, label] in Object.entries(labels)" :key="key" class="summary-item" :class="{'has-deduction': Number(summary?.[key]) > 0}">
          <span>{{label}}</span><strong>{{summary?.[key] ?? '—'}} <small>分</small></strong>
        </div>
        <div class="summary-item rate-item"><span>速率加减分</span><strong>{{summary?.wpmScore ?? '—'}} <small>分</small></strong></div>
      </div>
    </section>
    <div class="evidence-note">下方为当前页封顶前的规则扣分；实际扣分以整场汇总为准。速率与整页未提交的扣分计入整场，不归属某一个报文组。</div>
    <section class="detail-section">
    <div class="section-heading"><h4>报文对照</h4><span>缺失或多余字符可出现在组内任意位置</span></div>
    <a-empty v-if="!Array.isArray(resolver?.deductionEvidence)" description="该页尚无已保存的扣分依据。历史成绩不会据现有规则重新推算。" />
    <a-empty v-else-if="!resolver.deductionEvidence.length" description="该页没有逐组扣分" />
    <div v-else class="table-scroll"><table class="evidence-table">
      <thead><tr><th>位置</th><th>标准报底</th><th>实际拍发（解析后）</th><th>扣分依据（封顶前）</th></tr></thead>
      <tbody>
        <tr v-for="(item, index) in resolver.deductionEvidence" :key="index">
          <td>
            <template v-if="item.referenceGroup > 0">报底第 {{item.referenceGroup}} 组<br>拍发第 {{item.capturedGroup}}<template v-if="item.capturedThrough !== item.capturedGroup">—{{item.capturedThrough}}</template> 组</template>
            <template v-else>本页整体</template>
          </td>
          <td class="telegram reference-text">{{item.reference || '无对应报底'}}</td>
          <td class="telegram captured-text">{{item.captured || '未拍发'}}</td>
          <td><div v-for="(amount, key) in item.deductions" :key="key" class="deduction-entry">{{labels[key] || key}}：{{amount}} 分</div>
            <div v-if="item.partnerReferenceGroup" class="row-reason">与报底第 {{item.partnerReferenceGroup}} 组串组；两组共同计一次串组<template v-if="!item.deductions.bunchGroup">，此处不重复扣分</template></div>
            <div v-if="item.deductions.quantoCode" class="row-reason">跳过缺失或多余位置后，与本组报底一致</div>
            <div v-else-if="item.deductions.errorWord" class="row-reason">拍发内容与本组报底不符，按错码计分</div>
          </td>
        </tr>
      </tbody>
    </table></div>
    </section>
    <section v-if="resolver?.timingEvidence?.length" class="detail-section">
    <div class="section-heading"><h4>点划与间隔依据</h4><span>以下为上表扣分原因，不重复计分</span></div>
    <div class="table-scroll timing-scroll"><table class="evidence-table">
      <thead><tr><th>拍发位置</th><th>项目</th><th>实际时长</th><th>允许范围</th><th>规则扣分</th></tr></thead>
      <tbody><tr v-for="(item, index) in resolver.timingEvidence" :key="index">
        <td>第 {{item.capturedGroup}} 组 / 第 {{item.character}} 字 / 第 {{item.signal}} 段</td>
        <td>{{labels[item.item] || item.item}}</td><td>{{item.actualMillis}} ms</td>
        <td>{{item.minimumMillis}}—{{item.maximumMillis}} ms</td><td>{{item.deduction}} 分</td>
      </tr></tbody>
    </table></div>
    </section>
    <p class="evidence-footnote">组、行移动类错误显示识别起点及连续拍发组范围；每段时长可结合成绩页拍发波形查看。</p>
    </div>
  </a-modal>
</template>

<script setup>
import {ref} from 'vue'
defineProps({page: {type: Number, required: true}, resolver: {type: Object, default: null}, summary: {type: Object, default: null}})
const visible = ref(false)
const labels = {
  errorWord: '错码', quantoCode: '多少码', quantoGroup: '多少组', quantoRow: '多少行',
  bunchGroup: '串组', alterErrorScore: '改错', dotMinScore: '点虚/点粗', lineScore: '划虚/划粗',
  codeGapScore: '码间隔', wordGapScore: '字间隔', groupGapScore: '组间隔'
}
</script>

<style scoped>
.deduction-content {color: #e5edf5; font-size: 14px; line-height: 1.6; max-height: 76vh; overflow-y: auto; padding: 0 6px 0 0;}
.evidence-heading, .section-heading {display: flex; align-items: center; justify-content: space-between; gap: 16px;}
.evidence-heading {margin-bottom: 20px;}
.eyebrow {font-size: 12px; color: #a5bad0; letter-spacing: 2px;}
.evidence-heading h3 {color: #f1f7ff; font-size: 21px; margin: 4px 0 0; font-weight: 600;}
.page-badge {color: #8ed5ff; border: 1px solid #416987; background: #19364b; border-radius: 4px; padding: 4px 12px; white-space: nowrap;}
.section-heading {margin-bottom: 12px; flex-wrap: wrap;}
.section-heading h4 {color: #e5edf5; font-size: 16px; margin: 0; font-weight: 600;}
.section-heading > span {color: #a5bad0; font-size: 12px;}
.summary-grid {display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 8px;}
.summary-item {padding: 10px 14px; border: 1px solid #324a60; border-radius: 5px; background: #172c3d; display: flex; justify-content: space-between; align-items: center; gap: 8px;}
.summary-item > span {color: #b0c1d1; font-size: 13px;}
.summary-item strong {color: #c7d4e0; font-variant-numeric: tabular-nums; font-size: 19px; white-space: nowrap;}
.summary-item small {font-size: 12px; font-weight: normal;}
.summary-item.has-deduction {border-color: #6e5843; background: #302d2b;}
.summary-item.has-deduction strong {color: #ffbf77;}
.rate-item strong {color: #8ed5ff;}
.evidence-note {margin: 16px 0 22px; padding: 10px 14px; background: #19364b; border-left: 3px solid #6abdec; color: #c3d9ec; font-size: 13px;}
.detail-section {margin-top: 20px;}
.table-scroll {max-height: 340px; overflow: auto; border: 1px solid #3b536b; border-radius: 5px;}
.timing-scroll {max-height: 260px;}
.evidence-table {width: 100%; border-collapse: separate; border-spacing: 0; table-layout: fixed;}
.evidence-table th {position: sticky; top: 0; z-index: 1; background: #29445c; color: #e1edf8; font-weight: 500;}
.evidence-table th, .evidence-table td {padding: 12px 14px; text-align: left; border-bottom: 1px solid #344b60; vertical-align: top; word-break: break-word;}
.evidence-table td {background: #142839;}
.evidence-table tbody tr:nth-child(even) td {background: #1b3042;}
.evidence-table tbody tr:hover td {background: #243d52;}
.evidence-table tr:last-child td {border-bottom: 0;}
.telegram {font-family: Consolas, monospace; white-space: pre-wrap; word-break: break-all; font-size: 17px; letter-spacing: 1px;}
.reference-text {color: #b7e2ff;}
.captured-text {color: #ffcc91;}
.deduction-entry {color: #ffbf77; font-variant-numeric: tabular-nums;}
.row-reason {margin-top: 6px; color: #aabfd1; font-size: 12px;}
.evidence-footnote {color: #9fb3c7; font-size: 12px; margin: 16px 0 0;}
@media (max-width: 760px) {
  .summary-grid {grid-template-columns: repeat(2, minmax(0, 1fr));}
  .evidence-table {min-width: 660px;}
  .evidence-heading h3 {font-size: 18px;}
}
</style>

<style>
.deduction-modal.ant-modal {max-width: calc(100vw - 32px);}
.deduction-modal .ant-modal-content {background: #102333;}
.deduction-modal .ant-modal-body {background: transparent;}
.deduction-modal .ant-empty-description {color: #b5c8d9;}
</style>
