<template>
  <a-button size="small" :disabled="disabled" :loading="copying" :title="pageBreak ? '复制当前页报底，末尾附带翻页符' : '复制当前页报底，组间空格、每行十组'" @click="copyReference">
    复制报底
  </a-button>
</template>

<script setup>
import {ref} from 'vue'
import {message} from 'ant-design-vue'
import {formatReferenceText, copyReferenceText} from '../../common/utils/copyReference.js'

const props = defineProps({groups: {type: Array, default: () => []}, disabled: Boolean, pageBreak: Boolean})
const copying = ref(false)
const copyReference = async () => {
  if (copying.value || props.disabled) return
  try {
    const text = formatReferenceText(props.groups, props.pageBreak)
    if (!text) {
      message.warning('当前页暂无可复制的报底')
      return
    }
    copying.value = true
    await copyReferenceText(text)
    message.success('当前页报底已复制')
  } catch (error) {
    message.error(error.message || '复制报底失败')
  } finally {
    copying.value = false
  }
}
</script>
