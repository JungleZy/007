<template>
  <!-- 组网时本机与服务端版本不一致：遮住整个界面，只保留改网络设置、升级、退出三个出口。 -->
  <div v-if="checking" class="version-gate">
    <div class="version-gate-panel"><p class="version-gate-text">正在与服务端核对软件版本…</p></div>
  </div>
  <div v-else-if="result?.blocked" class="version-gate">
    <div class="version-gate-panel">
      <h2>软件版本不一致</h2>
      <p class="version-gate-text">{{ versionGateMessage(result) }}</p>
      <a-alert v-if="notice" :type="notice.type" :message="notice.text" show-icon class="version-gate-notice" />
      <div class="version-gate-actions">
        <a-button v-if="notice?.restart" type="primary" @click="restart">立即重启</a-button>
        <template v-else>
          <a-button v-if="result.state === 'server-newer'" type="primary" :loading="working === 'download'"
                    :disabled="!!working" @click="run('download')">下载并升级</a-button>
          <a-button :loading="working === 'import'" :disabled="!!working" @click="run('import')">导入升级包</a-button>
        </template>
        <a-button :disabled="!!working" @click="openSettingWindow">网络设置</a-button>
        <a-button :disabled="!!working" @click="recheck">重新检查</a-button>
        <a-button danger :disabled="!!working" @click="quit">退出</a-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { inject, onMounted, ref } from 'vue'
import { ipc } from '../../electron/ipcRenderer.js'
import { ipcApiRoute } from '../../electron/api.js'
import { updateResultMessage, versionGateMessage } from '../../common/utils/softwareUpdate.js'

const openSettingWindow = inject('openSettingWindow')
const result = ref(null)
const working = ref('')
const notice = ref(null)
// 核对完成前同样遮住界面：否则服务端应答慢时，用户可以抢在结论出来之前登录。
const checking = ref(true)

async function recheck() {
  notice.value = null
  checking.value = true
  try {
    result.value = await ipc.invoke(ipcApiRoute.updateCheck)
  } catch {
    // 主进程通道异常时无法判断版本，按「不可达」处理，不阻断。
    result.value = null
  } finally {
    checking.value = false
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
const quit = () => ipc.invoke(ipcApiRoute.closeApp)

// 页面每次加载都检查：程序启动，以及保存网络设置后的页面重载。
onMounted(recheck)
</script>

<style scoped>
.version-gate {
  position: fixed;
  inset: 0;
  /* 低于 ant-design 弹窗（1000），网络设置弹窗才能显示在遮罩之上。 */
  z-index: 990;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(4, 12, 28, 0.96);
}
.version-gate-panel {
  width: 560px;
  max-width: 90vw;
  padding: 32px;
  border: 1px solid #1d4b8f;
  border-radius: 8px;
  background: #0c1c3c;
  color: #fff;
}
.version-gate-panel h2 { margin: 0 0 16px; color: #fff; font-size: 22px; }
.version-gate-text { margin-bottom: 20px; font-size: 16px; line-height: 1.7; }
.version-gate-notice { margin-bottom: 20px; }
.version-gate-actions { display: flex; flex-wrap: wrap; gap: 12px; }
</style>
