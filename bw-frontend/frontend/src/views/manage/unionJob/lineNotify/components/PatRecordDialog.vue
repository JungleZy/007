<template>
  <a-modal class="noBottomBg" v-model:visible="patRecordVisible" :title="`${selectedSender?.userName || ''}的拍发记录`"
    width="min(1200px, calc(100vw - 48px))" :style="{top: '24px', paddingBottom: 0}"
    wrap-class-name="line-pat-record-dialog" :footer="null" :destroyOnClose="true" @cancel="close">
    <div v-if="patRecordLoading" role="status">正在读取拍发记录…</div>
    <div v-else-if="patRecordError" role="alert">{{ patRecordError }} <a-button @click="open(selectedSender)">重试</a-button></div>
    <PatRecordPanel v-else :strokes="senderRecords" :bw-type="bwType" :title="`${selectedSender?.userName || ''}的拍发记录`" />
  </a-modal>
</template>

<script setup>
import {ref, onBeforeUnmount} from 'vue'
import {getLinePatRecord} from '../../../../../common/api/UserApi'
import PatRecordPanel from './PatRecordPanel.vue'

const props = defineProps({roomId: {type: [String, Number], required: true}, bwType: {type: Number, default: 1}})
const patRecordVisible = ref(false)
const patRecordLoading = ref(false)
const patRecordError = ref('')
const selectedSender = ref(null)
const senderRecords = ref([])
let requestId = 0
let disposed = false
const close = () => { ++requestId; patRecordVisible.value = false }
const open = async sender => {
  if (!sender || disposed) return
  const request = ++requestId
  selectedSender.value = sender
  senderRecords.value = []
  patRecordVisible.value = true
  patRecordLoading.value = true
  patRecordError.value = ''
  try {
    const response = await getLinePatRecord(props.roomId, sender.id)
    if (disposed || request !== requestId) return
    if (response?.code !== 200 || !Array.isArray(response.data)) throw new Error(response?.message || '拍发记录读取失败')
    senderRecords.value = response.data
  } catch (error) {
    if (!disposed && request === requestId) patRecordError.value = error.message || '拍发记录读取失败'
  } finally {
    if (!disposed && request === requestId) patRecordLoading.value = false
  }
}
onBeforeUnmount(() => { disposed = true; close() })
defineExpose({open})
</script>

<style>
.line-pat-record-dialog .ant-modal .ant-modal-content {
  display: flex;
  flex-direction: column;
  height: 780px;
  max-height: calc(100vh - 48px);
  overflow: hidden;
}
.line-pat-record-dialog .ant-modal .ant-modal-header { flex-shrink: 0; }
.line-pat-record-dialog .ant-modal .ant-modal-body {
  display: flex;
  flex-direction: column;
  flex: 1 1 0;
  min-height: 0;
  overflow: hidden;
  padding: 16px 16px 24px;
  box-sizing: border-box;
}
.line-pat-record-dialog .pat-record-panel {
  display: flex;
  flex-direction: column;
  flex: 1 1 0;
  width: 100%;
  min-height: 0;
  overflow: hidden;
}
.line-pat-record-dialog .pat-record-panel > .record-heading,
.line-pat-record-dialog .pat-record-panel > .record-wave,
.line-pat-record-dialog .pat-record-panel > .decode-settings { flex-shrink: 0; }
.line-pat-record-dialog .pat-record-panel .decoded-record {
  flex: 1 1 0;
  min-height: 0;
  height: auto;
  max-height: none;
  overflow: auto;
}
</style>
