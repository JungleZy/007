<template>
  <div class="database-page">
    <h2>数据备份</h2>
    <a-spin :spinning="loading">
      <a-alert v-if="notice" :type="noticeType" :message="notice" show-icon class="notice" />
      <a-alert type="info" show-icon class="notice"
               message="请在无人训练、无人编辑数据的维护时段操作。导入完成后，请重新登录；服务器应重新启动后再恢复使用。" />
      <p>当前数据库：<strong>{{ status?.database || '未获取' }}</strong>
        <a-button type="link" :disabled="!!working" @click="loadStatus">刷新状态</a-button>
      </p>
      <a-alert v-if="status && (!status.backupAvailable || !status.importAvailable)" type="warning" show-icon
               class="notice" message="服务器尚未配置完整的 MySQL 客户端工具，请联系管理员安装 mysql、mysqldump 并配置路径。" />
      <a-alert v-if="status?.busy" type="warning" show-icon class="notice"
               message="服务器正在执行数据库任务，请稍后刷新状态。" />
      <a-card title="备份数据库" class="transfer-card">
        <p>将当前数据库的表结构和数据下载为 SQL 文件。文件包含账号及业务数据，请妥善保存。</p>
        <a-button type="primary" :loading="working === 'backup'"
                  :disabled="!status?.backupAvailable || status?.busy || !!working" @click="backup">
          下载 SQL 备份
        </a-button>
      </a-card>
      <a-card title="导入 SQL 文件" class="transfer-card">
        <p>支持 UTF-8 编码的 MySQL SQL 文件，最大 512 MiB。建议使用本系统导出的同版本备份。</p>
        <a-alert type="warning" show-icon class="notice"
                 message="导入会执行文件内的 SQL，可能覆盖现有表、账号、权限及训练记录。失败时可能已写入部分数据，不能自动回滚。请先下载备份，并确认文件来源可信。" />
        <input ref="fileInput" type="file" accept=".sql" :disabled="!!working" @change="selectFile" />
        <p v-if="file" class="selected-file">已选择：{{ file.name }}（{{ (file.size / 1024 / 1024).toFixed(2) }} MiB）</p>
        <p class="confirm-label">输入当前数据库名称 <strong>{{ status?.database }}</strong> 确认导入：</p>
        <a-input v-model:value="confirmation" :disabled="!!working" placeholder="当前数据库名称" class="confirmation" />
        <a-button type="primary" danger :loading="working === 'import'"
                  :disabled="!file || !status?.importAvailable || status?.busy || !!working || confirmation !== status?.database"
                  @click="confirmImport">导入 SQL</a-button>
      </a-card>
    </a-spin>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { onBeforeRouteLeave, useRouter } from 'vue-router'
import { Modal } from 'ant-design-vue'
import { backupDatabase, getDatabaseStatus, importDatabase } from '../../../../common/api/DatabaseApi.js'

const router = useRouter()
const status = ref(null)
const loading = ref(false)
const working = ref('')
const file = ref(null)
const fileInput = ref(null)
const confirmation = ref('')
const notice = ref('')
const noticeType = ref('info')

function showNotice(text, type = 'error') {
  notice.value = text
  noticeType.value = type
}

async function loadStatus() {
  loading.value = true
  try {
    const result = await getDatabaseStatus()
    status.value = result.code === 200 ? result.data : null
    if (result.code !== 200) showNotice(result.message || '无法读取数据库状态')
  } catch {
    status.value = null
    showNotice('无法读取数据库状态，请检查服务器连接')
  } finally {
    loading.value = false
  }
}

function selectFile(event) {
  file.value = null
  const selected = event.target.files[0]
  if (!selected) return
  if (!/\.sql$/i.test(selected.name) || selected.size === 0 || selected.size > 512 * 1024 * 1024) {
    showNotice('请选择非空的 .sql 文件，大小不能超过 512 MiB')
    event.target.value = ''
    return
  }
  file.value = selected
  confirmation.value = ''
}

async function backup() {
  working.value = 'backup'
  showNotice('正在生成数据库备份，请等待下载完成。', 'info')
  try {
    const blob = await backupDatabase()
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `${status.value.database}-${new Date().toISOString().replace(/[:.]/g, '-')}.sql`
    document.body.appendChild(link)
    link.click()
    link.remove()
    setTimeout(() => URL.revokeObjectURL(url), 60000)
    showNotice('备份已生成并发起下载，请确认 SQL 文件已保存。', 'success')
  } catch (error) {
    showNotice(error.message || '数据库备份失败')
  } finally {
    working.value = ''
  }
}

function confirmImport() {
  Modal.confirm({
    title: '确认导入数据库？',
    content: `将执行 ${file.value.name} 中的 SQL。请确认已备份当前数据，且所有人员已停止训练和编辑。失败时不能自动回滚。`,
    okText: '确认导入', cancelText: '取消', okType: 'danger',
    onOk: restore
  })
}

async function restore() {
  working.value = 'import'
  showNotice('正在导入，请勿关闭或刷新页面。', 'info')
  try {
    const result = await importDatabase(file.value, confirmation.value)
    if (result.code !== 200) throw new Error(result.message || 'SQL 导入失败，可能已写入部分数据')
    file.value = null
    fileInput.value.value = ''
    confirmation.value = ''
    showNotice('SQL 导入完成，请重启服务器并重新登录。账号和权限以导入的数据为准。', 'success')
    Modal.success({
      title: '导入完成', content: '请重启服务器后，使用导入数据中的账号重新登录。',
      okText: '返回登录', onOk: () => router.push('/login')
    })
  } catch (error) {
    showNotice(`${error.message || '导入请求失败'}。如连接中断，服务器可能仍在执行；请检查数据库状态，勿立即重复导入。`)
  } finally {
    working.value = ''
  }
}

onBeforeRouteLeave(() => {
  if (!working.value) return true
  showNotice('数据库任务正在执行，请等待完成后离开页面。', 'warning')
  return false
})
onMounted(loadStatus)
</script>

<style scoped>
.database-page { height: 100%; padding: 24px; overflow: auto; }
.database-page h2 { font-size: 20px; margin-bottom: 20px; color: inherit; }
.notice, .transfer-card { margin-bottom: 20px; }
.confirm-label, .selected-file { margin-top: 16px; }
.confirmation { width: 280px; max-width: 100%; margin-right: 16px; }
</style>
