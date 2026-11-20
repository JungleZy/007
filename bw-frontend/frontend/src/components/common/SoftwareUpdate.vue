<template>
  <a-card title="软件升级" class="software-update">
    <template v-if="status?.available">
      <p>当前版本：<strong>{{ status.version }}</strong>（{{ status.target }}，{{ status.style }} 风格）</p>
      <a-alert v-if="status.pendingVersion" type="success" show-icon class="notice"
               :message="`版本 ${status.pendingVersion} 已准备就绪，重启软件后生效。`" />
      <a-alert v-if="status.lastFailure" type="warning" show-icon class="notice"
               :message="`上次升级到 ${status.lastFailure.version} 未成功：${status.lastFailure.reason}`" />
      <a-alert v-if="notice" :type="notice.type" :message="notice.text" show-icon class="notice" />
      <p>选择升级包文件（.nipupd）。升级不会改动账号、训练记录等数据；若新版本需要更新数据库，会先自动备份。</p>
      <a-alert type="info" show-icon class="notice"
               message="请在无人训练时操作。本机作为服务端时，重启期间所有客户端会断开；重启后，版本不同的客户端需要升级才能继续使用。" />
      <a-button type="primary" :loading="working" @click="importPackage">导入升级包</a-button>
      <a-button v-if="notice?.restart || status.pendingVersion" class="restart" :disabled="working" @click="confirmRestart">立即重启</a-button>
      <template v-if="status.packages.length">
        <p class="stored-title">本机留存、可分发给客户端的升级包：</p>
        <ul>
          <li v-for="item in status.packages" :key="item.fileName">
            {{ item.target }} / {{ item.style }}：版本 {{ item.version }}（{{ (item.size / 1024 / 1024).toFixed(1) }} MiB）
          </li>
        </ul>
      </template>
    </template>
    <p v-else>当前运行方式不支持软件升级。</p>
  </a-card>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { Modal } from 'ant-design-vue'
import { ipc } from '../../electron/ipcRenderer.js'
import { ipcApiRoute } from '../../electron/api.js'
import { updateResultMessage } from '../../common/utils/softwareUpdate.js'

const status = ref(null)
const working = ref(false)
const notice = ref(null)

const load = async () => { status.value = await ipc.invoke(ipcApiRoute.updateStatus) }

async function importPackage() {
  working.value = true
  notice.value = null
  try {
    notice.value = updateResultMessage(await ipc.invoke(ipcApiRoute.updateImport))
    await load()
  } finally {
    working.value = false
  }
}

function confirmRestart() {
  Modal.confirm({
    title: '重启软件',
    content: '重启后切换到新版本。本机作为服务端时，所有客户端会断开连接。确定现在重启吗？',
    okText: '重启',
    cancelText: '取消',
    onOk: () => ipc.invoke(ipcApiRoute.updateRestart)
  })
}

onMounted(load)
</script>

<style scoped>
.software-update { margin-top: 16px; }
.notice { margin-bottom: 12px; }
.restart { margin-left: 12px; }
.stored-title { margin: 16px 0 4px; }
</style>
