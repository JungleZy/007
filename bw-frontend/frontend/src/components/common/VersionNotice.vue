<template>
  <!-- 组网时本机与服务端版本不一致：提示并给出升级入口，用户可以选择继续使用。 -->
  <div v-if="result?.mismatch && !dismissed" class="version-notice">
    <div class="version-notice-panel">
      <h2>软件版本不一致</h2>
      <p class="version-notice-text">{{ versionMismatchMessage(result) }}</p>
      <a-alert v-if="notice" :type="notice.type" :message="notice.text" show-icon class="version-notice-result" />
      <div class="version-notice-actions">
        <a-button v-if="notice?.restart" type="primary" @click="restart">立即重启</a-button>
        <template v-else>
          <a-button v-if="result.state === 'server-newer'" type="primary" :loading="working === 'download'"
                    :disabled="!!working" @click="run('download')">下载并升级</a-button>
          <a-button :loading="working === 'import'" :disabled="!!working" @click="run('import')">导入升级包</a-button>
        </template>
        <a-button :disabled="!!working" @click="dismissed = true">{{ notice?.restart ? '稍后重启' : '继续使用' }}</a-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ipc } from '../../electron/ipcRenderer.js'
import { ipcApiRoute } from '../../electron/api.js'
import { updateResultMessage, versionMismatchMessage } from '../../common/utils/softwareUpdate.js'

const result = ref(null)
const working = ref('')
const notice = ref(null)
const dismissed = ref(false)

async function check() {
  try {
    result.value = await ipc.invoke(ipcApiRoute.updateCheck)
  } catch {
    // 主进程通道异常时无法判断版本，不提示。
    result.value = null
  }
}

async function run(kind) {
  working.value = kind
  notice.value = null
  try {
    notice.value = updateResultMessage(await ipc.invoke(kind === 'download' ? ipcApiRoute.updateDownload : ipcApiRoute.updateImport))
  } finally {
    working.value = ''
  }
}

const restart = () => ipc.invoke(ipcApiRoute.updateRestart)

// 页面每次加载都检查：程序启动，以及保存网络设置后的页面重载。检查期间不遮挡界面。
onMounted(check)
</script>

<style scoped>
.version-notice {
  position: fixed;
  inset: 0;
  z-index: 990;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(4, 12, 28, 0.6);
}
.version-notice-panel {
  width: 560px;
  max-width: 90vw;
  padding: 32px;
  border: 1px solid #1d4b8f;
  border-radius: 8px;
  background: #0c1c3c;
  color: #fff;
}
.version-notice-panel h2 { margin: 0 0 16px; color: #fff; font-size: 22px; }
.version-notice-text { margin-bottom: 20px; font-size: 16px; line-height: 1.7; }
.version-notice-result { margin-bottom: 20px; }
.version-notice-actions { display: flex; flex-wrap: wrap; gap: 12px; }
</style>
