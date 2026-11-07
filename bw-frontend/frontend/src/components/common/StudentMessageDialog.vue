<template>
  <a-button size="small" class="student-message-trigger" @click="open">报文</a-button>
  <a-modal v-model:visible="visible" :title="`${name} · 拍发报文`" :width="900" :footer="null"
           class="init_modal_style footer-border-none noBottomBg student-message-modal">
    <div class="student-message">
      <div class="toolbar">
        <span>已提交页以服务端保存为准；当前页尚未提交，按本页面打开后收到的实时拍发识别。</span>
        <a-button size="small" :loading="loading" @click="loadPages">刷新</a-button>
      </div>
      <div v-if="error" class="state error">{{ error }}</div>
      <template v-else>
        <section v-for="page in pages" :key="page.pageNumber" class="page">
          <h4>第 {{ page.pageNumber }} 页 <small>已提交</small></h4>
          <pre class="telegram">{{ page.text || '（本页无有效拍发内容）' }}</pre>
        </section>
        <div v-if="!loading && !pages.length" class="state">暂无已提交的页</div>
        <section class="page live">
          <h4>当前页 <small>实时</small></h4>
          <pre class="telegram">{{ liveText || '（打开本页面后尚未收到当前页拍发）' }}</pre>
        </section>
      </template>
    </div>
  </a-modal>
</template>

<script setup>
import {computed, ref} from 'vue'

const props = defineProps({
  name: {type: String, default: ''},
  // async () => [{pageNumber, text}]，只返回已提交且有内容的页
  loadSubmittedPages: {type: Function, required: true},
  // () => string，读取实时推送并识别；仅在弹框打开时求值
  live: {type: Function, required: true}
})

const visible = ref(false)
const loading = ref(false)
const error = ref('')
const pages = ref([])
const liveText = computed(() => visible.value ? props.live() : '')

const loadPages = async () => {
  loading.value = true
  error.value = ''
  try {
    pages.value = await props.loadSubmittedPages()
  } catch (cause) {
    error.value = cause?.message || '读取已提交报文失败，请刷新重试'
  } finally {
    loading.value = false
  }
}

const open = () => {
  visible.value = true
  loadPages()
}
</script>

<style scoped>
.student-message-trigger {margin-left: 8px; align-self: center;}
.student-message {padding: 20px 24px 28px; color: #e5edf5; font-size: 14px; line-height: 1.7;}
.toolbar {display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 16px; color: #a5bad0; font-size: 12px;}
.page {margin-bottom: 18px;}
.page h4 {margin: 0 0 8px; color: #eff6ff; font-size: 15px;}
.page h4 small {margin-left: 8px; color: #8fb3d4; font-size: 12px; font-weight: normal;}
.live h4 small {color: #ffbf77;}
.telegram {margin: 0; padding: 12px 16px; background: #142839; border: 1px solid #344b60; border-radius: 6px;
  color: #b7e2ff; font-family: Consolas, monospace; font-size: 16px; line-height: 1.9; white-space: pre-wrap; word-spacing: 6px;}
.live .telegram {color: #ffcc91;}
.state {padding: 16px 0; color: #b5c8d9;}
.state.error {color: #ff9b8a;}
</style>
<style>
.student-message-modal.ant-modal .ant-modal-content {background: #102333;}
.student-message-modal.ant-modal .ant-modal-body {max-height: calc(100vh - 160px); overflow: auto; padding: 0;}
.student-message-modal.ant-modal .ant-modal-body > .student-message {padding: 20px 24px 28px !important;}
</style>
