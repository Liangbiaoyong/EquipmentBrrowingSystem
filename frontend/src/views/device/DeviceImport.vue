<template>
  <div class="import"><h2>批量导入</h2>

    <!-- 导入区域 -->
    <el-card>
      <el-upload ref="uploadRef" drag :auto-upload="false" :on-change="onFileChange" accept=".csv,.xlsx,.xls" :limit="1" :file-list="fileList">
        <el-icon :size="50"><UploadFilled/></el-icon>
        <div>拖拽或点击上传 CSV / XLSX 文件</div>
      </el-upload>
      <div style="margin-top:15px;display:flex;gap:10px;flex-wrap:wrap;align-items:center">
        <el-button type="warning" @click="doDryRun" :disabled="!file" :loading="dryLoading">预览</el-button>
        <el-button type="primary" @click="doImport" :disabled="!file" :loading="impLoading">导入</el-button>
        <el-radio-group v-model="importMode" size="small" style="margin-left:8px">
          <el-radio-button value="append">追加模式（保留旧数据）</el-radio-button>
          <el-radio-button value="replace">替换模式（删除旧数据）</el-radio-button>
        </el-radio-group>
        <span v-if="file" style="color:#909399;font-size:12px">{{ file.name }}</span>
        <el-button v-if="file" type="info" plain @click="resetFile" style="margin-left:auto">重置文件</el-button>
        <el-dropdown @command="downloadTemplate" style="margin-left:auto">
          <el-button type="success" plain :loading="tplLoading">📥 下载模板 <el-icon><ArrowDown/></el-icon></el-button>
          <template #dropdown><el-dropdown-menu>
            <el-dropdown-item command="csv">CSV 格式</el-dropdown-item>
            <el-dropdown-item command="xlsx">Excel 格式 (XLSX)</el-dropdown-item>
          </el-dropdown-menu></template>
        </el-dropdown>
      </div>
      <el-alert v-if="importMode==='replace'&&file" title="替换模式会删除数据库中不在此文件中的设备记录，请谨慎操作！" type="warning" :closable="false" show-icon style="margin-top:10px"/>
      <el-alert v-if="result" style="margin-top:15px" :title="importResultText" :type="result.failCount > 0 ? 'warning' : 'success'" :closable="false" show-icon/>
      <el-collapse v-if="result?.errors?.length" style="margin-top:10px">
        <el-collapse-item :title="`错误详情 (${result.errors.length}条)`"><div style="max-height:300px;overflow:auto">
          <el-tag v-for="(e,i) in result.errors" :key="i" type="danger" size="small" style="display:block;margin:2px;white-space:normal;height:auto;line-height:1.5;padding:4px 8px">行{{e.row}}: {{ e.assetNo }} {{ e.name }} — {{ e.reason }}</el-tag>
        </div></el-collapse-item>
      </el-collapse>
    </el-card>

    <!-- 导入历史 -->
    <el-card header="导入历史" style="margin-top:15px">
      <div v-if="!batches.length && !batchLoading" style="text-align:center;padding:20px;color:#909399;font-size:13px">暂无导入记录</div>
      <el-table :data="batches" v-loading="batchLoading" stripe v-else>
        <el-table-column prop="batchId" label="批次号" width="130"/>
        <el-table-column label="导入时间" width="160"><template #default="{row}">{{ row.createTime || '-' }}</template></el-table-column>
        <el-table-column label="导入设备数" width="100"><template #default="{row}">{{ row.successCount || row.deviceCount || '-' }}台</template></el-table-column>
        <el-table-column label="操作" width="220">
          <template #default="{row}">
            <el-button size="small" @click="previewBatch(row)">查看</el-button>
            <el-popconfirm title="「清除」将删除该批次导入的所有设备记录，不可恢复！确认？" @confirm="delBatch(row)">
              <template #reference><el-button size="small" type="danger">清除设备</el-button></template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 批次设备预览 -->
    <el-dialog v-model="batchDlg.visible" :title="'批次: '+batchDlg.batchId" width="800px">
      <el-table :data="batchDlg.devices" v-loading="batchDlg.loading" max-height="400" stripe size="small">
        <el-table-column prop="assetNo" label="资产编号" width="130"/>
        <el-table-column prop="name" label="名称" min-width="200"/>
        <el-table-column prop="model" label="型号" width="120"/>
        <el-table-column prop="location" label="存放地" width="120" show-overflow-tooltip/>
      </el-table>
      <template #footer><el-button @click="batchDlg.visible=false">关闭</el-button></template>
    </el-dialog>
  </div>
</template>
<script setup>
import { ref, reactive, computed } from 'vue'
import { deviceApi } from '@/api/device'
import axios from '@/api/request'
import { ElMessage, ElMessageBox } from 'element-plus'
import { UploadFilled, ArrowDown } from '@element-plus/icons-vue'

const uploadRef = ref(null)
const file = ref(null)
const fileList = ref([])
const dryLoading = ref(false)
const impLoading = ref(false)
const tplLoading = ref(false)
const result = ref(null)
const importMode = ref('append')
const batches = ref([])
const batchLoading = ref(false)
const batchDlg = reactive({ visible: false, batchId: '', devices: [], loading: false })

function onFileChange(f) {
  file.value = f.raw
  result.value = null
}

async function doDryRun() {
  if (!file.value) return
  dryLoading.value = true
  try {
    const { data } = await deviceApi.dryRun(file.value)
    result.value = data
    ElMessage.success(`预览完成：文件共${data.totalRows}行`)
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || e?.message || '预览失败')
  } finally { dryLoading.value = false }
}

async function doImport() {
  if (!file.value) return
  if (importMode.value === 'replace') {
    try {
      await ElMessageBox.confirm(
        '替换模式将删除数据库中所有不在导入文件中的设备记录！\n这可能导致大量数据丢失，确认要继续吗？',
        '危险操作确认',
        { confirmButtonText: '确认替换', cancelButtonText: '取消', type: 'warning' }
      )
    } catch { return }
  }
  impLoading.value = true
  try {
    const fd = new FormData()
    fd.append('file', file.value)
    fd.append('mode', importMode.value)
    const { data } = await axios.post('/devices/import', fd, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    result.value = data
    ElMessage.success(importMode.value === 'replace' ? '替换导入完成' : '追加导入完成')
    loadBatches()
    resetFile()
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || e?.message || '导入失败')
  } finally { impLoading.value = false }
}

function resetFile() {
  file.value = null
  fileList.value = []
  result.value = null
  if (uploadRef.value) uploadRef.value.clearFiles()
}

async function loadBatches() {
  batchLoading.value = true
  try {
    const { data } = await deviceApi.getBatches()
    batches.value = data || []
  } catch {} finally { batchLoading.value = false }
}

async function previewBatch(row) {
  batchDlg.batchId = row.batchId
  batchDlg.visible = true
  batchDlg.loading = true
  try {
    const { data } = await axios.get(`/devices/batches/${row.batchId}`)
    batchDlg.devices = data || []
  } catch { ElMessage.error('加载失败') }
  finally { batchDlg.loading = false }
}

async function delBatch(row) {
  try {
    const { data } = await deviceApi.deleteBatch(row.batchId)
    ElMessage.success(data.msg || '已清除')
    loadBatches()
  } catch { ElMessage.error('清除失败') }
}

async function downloadTemplate(format = 'csv') {
  tplLoading.value = true
  try {
    const r = await axios.get('/devices/import/template', { params: { format }, responseType: 'blob' })
    const ext = format === 'xlsx' ? 'xlsx' : 'csv'
    const mime = ext === 'xlsx' ? 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' : 'text/csv;charset=UTF-8'
    const blob = new Blob([r.data], { type: mime })
    const a = document.createElement('a')
    a.href = URL.createObjectURL(blob)
    a.download = `设备导入模板.${ext}`
    a.click()
    ElMessage.success(`模板已下载 (${ext.toUpperCase()})`)
  } catch (e) {
    ElMessage.error('下载失败')
  } finally { tplLoading.value = false }
}

const importResultText = computed(() => {
  if (!result.value) return ''
  const r = result.value
  let text = `文件共 ${r.totalRows} 行`
  if (r.successCount > 0) text += ` | 新增 ${r.successCount}`
  if (r.updateCount > 0) text += ` | 更新 ${r.updateCount}`
  if (r.failCount > 0) text += ` | 失败 ${r.failCount}`
  if (r.autoCategoryCount > 0) text += ` | 自动分类 ${r.autoCategoryCount}`
  if (r.uncategorizedCount > 0) text += ` | 未分类 ${r.uncategorizedCount}`
  if (r.deleteCount > 0) text += ` | 清除旧数据 ${r.deleteCount}`
  return text
})

loadBatches()
</script>
<style scoped>.import{padding:20px}</style>
