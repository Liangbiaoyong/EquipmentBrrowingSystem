<template>
  <div class="backup-page"><h2>数据备份管理</h2>

    <!-- 状态概览卡片 -->
    <div class="status-grid">
      <div class="status-card"><el-icon :size="22"><Coin/></el-icon><div><div class="sc-big">{{ backupStatus.dbName || '-' }}</div><div class="sc-sm">数据库</div></div></div>
      <div class="status-card"><el-icon :size="22"><Files/></el-icon><div><div class="sc-big">{{ backupStatus.fileCount ?? '-' }}</div><div class="sc-sm">备份文件数</div></div></div>
      <div class="status-card"><el-icon :size="22"><DataLine/></el-icon><div><div class="sc-big">{{ backupStatus.totalSize || '-' }}</div><div class="sc-sm">占用空间</div></div></div>
      <div class="status-card"><el-icon :size="22"><Cpu/></el-icon><div><div class="sc-big">{{ backupStatus.tool || '-' }}</div><div class="sc-sm">备份工具</div></div></div>
    </div>

    <!-- 操作按钮栏 -->
    <div class="action-bar">
      <el-button type="primary" :icon="Download" @click="doExport" :loading="exporting">立即备份</el-button>
      <el-button type="warning" :icon="Upload" @click="triggerImport">导入恢复</el-button>
      <el-button @click="refreshAll" :icon="RefreshRight" style="margin-left:auto">刷新</el-button>
    </div>

    <!-- 备份文件列表 -->
    <el-card shadow="never" header="备份文件">
      <el-table :data="backupFiles" stripe v-loading="loadingBackups" empty-text="暂无备份文件">
        <el-table-column prop="fileName" label="文件名" min-width="280" show-overflow-tooltip/>
        <el-table-column prop="fileSize" label="大小" width="100"/>
        <el-table-column prop="lastModified" label="时间" width="180"/>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{row}">
            <div style="white-space:nowrap;display:flex;gap:4px">
              <el-button size="small" type="primary" @click="doDownload(row.fileName)">下载</el-button>
              <el-button size="small" type="warning" @click="doRestore(row.fileName)">恢复</el-button>
              <el-popconfirm title="永久删除此备份？" @confirm="doDelete(row.fileName)"><template #reference><el-button size="small" type="danger">删除</el-button></template></el-popconfirm>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 操作日志 -->
    <el-card shadow="never" header="操作日志" style="margin-top:16px">
      <el-timeline v-if="logs.length">
        <el-timeline-item v-for="(l,i) in logs" :key="i" :timestamp="l.time" :type="l.type" placement="top">{{ l.msg }}</el-timeline-item>
      </el-timeline>
      <el-empty v-else description="暂无操作记录" :image-size="40"/>
    </el-card>

    <!-- 恢复确认对话框 -->
    <el-dialog v-model="restoreVisible" title="确认恢复数据库" width="440px">
      <el-alert title="恢复操作将覆盖当前数据库的所有数据，此操作不可撤销！" type="error" show-icon :closable="false" style="margin-bottom:12px"/>
      <p>备份文件：<el-tag>{{ restoreFile }}</el-tag></p>
      <template #footer><el-button @click="restoreVisible=false">取消</el-button><el-button type="danger" @click="confirmRestore" :loading="restoring">确认恢复</el-button></template>
    </el-dialog>

    <!-- 导入恢复对话框 -->
    <el-dialog v-model="importVisible" title="导入恢复数据库" width="440px">
      <el-alert title="上传SQL文件将覆盖当前数据库，操作不可撤销！" type="error" show-icon :closable="false" style="margin-bottom:12px"/>
      <input type="file" ref="fileInputRef" accept=".sql,.gz" @change="onImportFile" style="display:block;width:100%;padding:8px;border:1px dashed #dcdfe6;border-radius:6px;cursor:pointer"/>
      <div v-if="importFileName" style="margin-top:8px;font-size:13px;color:#409EFF">已选择: {{ importFileName }}</div>
      <template #footer><el-button @click="importVisible=false">取消</el-button><el-button type="danger" @click="confirmImport" :loading="restoring" :disabled="!importFile">确认导入恢复</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref,reactive,onMounted } from 'vue'
import axios from '@/api/request'
import { ElMessage,ElMessageBox } from 'element-plus'
import { Download,Upload,RefreshRight,Coin,Files,DataLine,Cpu,UploadFilled } from '@element-plus/icons-vue'

const backupFiles = ref([])
const loadingBackups = ref(false)
const exporting = ref(false); const restoring = ref(false)
const restoreVisible = ref(false); const restoreFile = ref('')
const importVisible = ref(false); const importFile = ref(null)
const backupStatus = reactive({})

const logs = ref([])
function addLog(msg, type = 'primary') {
  logs.value.unshift({ msg, time: new Date().toLocaleString(), type })
  if (logs.value.length > 30) logs.value.pop()
}

async function refreshAll() {
  await Promise.all([loadStatus(), loadBackups()])
}

async function loadStatus() {
  try {
    const { data } = await axios.get('/admin/backup/status')
    Object.assign(backupStatus, data)
  } catch { addLog('状态加载失败', 'danger') }
}

async function loadBackups() {
  loadingBackups.value = true
  try {
    const { data } = await axios.get('/admin/backup/list')
    backupFiles.value = data || []
  } catch { addLog('备份列表加载失败', 'danger') }
  finally { loadingBackups.value = false }
}

async function doExport() {
  exporting.value = true
  try {
    const { data } = await axios.get('/admin/backup/export')
    addLog(`备份成功: ${data.fileName} (${data.fileSize})`, 'success')
    ElMessage.success(`备份完成: ${data.fileName}`)
    loadBackups(); loadStatus()
  } catch (e) {
    addLog('备份失败: ' + (e?.response?.data?.msg || e.message), 'danger')
    ElMessage.error(e?.response?.data?.msg || '备份失败')
  } finally { exporting.value = false }
}

function doDownload(fileName) {
  axios.get(`/admin/backup/download/${encodeURIComponent(fileName)}`, { responseType: 'blob' }).then(r => {
    const blob = new Blob([r.data])
    const a = document.createElement('a')
    a.href = URL.createObjectURL(blob)
    a.download = fileName; a.click()
    addLog(`下载完成: ${fileName}`, 'primary')
  }).catch(e => ElMessage.error('下载失败'))
}

function doRestore(fileName) {
  restoreFile.value = fileName; restoreVisible.value = true
}

async function confirmRestore() {
  restoring.value = true
  try {
    await axios.post(`/admin/backup/restore/${encodeURIComponent(restoreFile.value)}`)
    addLog(`数据库恢复成功: ${restoreFile.value}`, 'success')
    ElMessage.success('数据库已恢复')
    restoreVisible.value = false
  } catch (e) {
    addLog('恢复失败: ' + (e?.response?.data?.msg || e.message), 'danger')
    ElMessage.error(e?.response?.data?.msg || '恢复失败')
  } finally { restoring.value = false }
}

const fileInputRef = ref(null); const importFileName = ref('')

function triggerImport() { importVisible.value = true; importFile.value = null; importFileName.value = '' }

function onImportFile(e) { const f = e.target?.files?.[0]; if (f) { importFile.value = f; importFileName.value = f.name } }

async function confirmImport() {
  if (!importFile.value) { ElMessage.warning('请选择SQL文件'); return }
  restoring.value = true
  try {
    const fd = new FormData(); fd.append('file', importFile.value)
    const { data } = await axios.post('/admin/backup/import', fd, { headers: { 'Content-Type': 'multipart/form-data' } })
    addLog(`导入恢复成功: ${data}`, 'success')
    ElMessage.success(data.msg || '导入恢复完成')
    importVisible.value = false; importFile.value = null; loadStatus()
  } catch (e) {
    addLog('导入恢复失败: ' + (e?.response?.data?.msg || e.message), 'danger')
    ElMessage.error(e?.response?.data?.msg || '导入恢复失败')
  } finally { restoring.value = false }
}

async function doDelete(fileName) {
  try {
    await axios.delete(`/admin/backup/${encodeURIComponent(fileName)}`)
    addLog(`已删除: ${fileName}`, 'warning')
    ElMessage.success('已删除')
    loadBackups(); loadStatus()
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || '删除失败')
  }
}

onMounted(refreshAll)
</script>

<style scoped>
.backup-page{padding:20px;max-width:1000px;margin:0 auto}

/* 状态卡片 */
.status-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:12px;margin-bottom:16px}
.status-card{display:flex;align-items:center;gap:12px;background:#fff;padding:16px 18px;border-radius:8px;box-shadow:0 1px 4px rgba(0,0,0,0.06)}
.sc-big{font-size:20px;font-weight:700;color:#303133}
.sc-sm{font-size:12px;color:#909399;margin-top:2px}

/* 操作栏 */
.action-bar{display:flex;align-items:center;gap:8px;margin-bottom:14px}

/* 响应式 */
@media(max-width:768px){.status-grid{grid-template-columns:repeat(2,1fr)}}
</style>
