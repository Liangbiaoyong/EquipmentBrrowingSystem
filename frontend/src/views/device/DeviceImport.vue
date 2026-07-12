<template>
  <div class="import"><h2>批量导入</h2>
    <el-card>
      <el-upload ref="uploadRef" drag :auto-upload="false" :on-change="onFileChange" accept=".csv,.xlsx,.xls" :limit="1" :file-list="fileList">
        <el-icon :size="50"><UploadFilled/></el-icon>
        <div>拖拽或点击上传 CSV / XLSX 文件</div>
      </el-upload>
      <div style="margin-top:15px;display:flex;gap:10px;flex-wrap:wrap">
        <el-button type="warning" @click="doDryRun" :disabled="!file" :loading="dryLoading">预览</el-button>
        <el-button type="primary" @click="doImport" :disabled="!file" :loading="impLoading">导入</el-button>
        <el-button v-if="file" type="info" plain @click="resetFile">重置文件</el-button>
      </div>
      <el-alert v-if="result" style="margin-top:15px"
        :title="importResultText"
        :type="result.failCount > 0 ? 'warning' : 'success'"
        :closable="false" show-icon/>
    </el-card>
    <el-card header="导入历史" style="margin-top:15px">
      <el-table :data="batches" v-loading="batchLoading">
        <el-table-column prop="batchId" label="批次号"/>
        <el-table-column label="操作" width="100">
          <template #default="{row}">
            <el-popconfirm title="确定清除该批次?" @confirm="delBatch(row)">
              <template #reference><el-button size="small" type="danger">清除</el-button></template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>
<script setup>
import { ref, computed } from 'vue'
import { deviceApi } from '@/api/device'
import { ElMessage } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'

const uploadRef = ref(null)
const file = ref(null)
const fileList = ref([])
const dryLoading = ref(false)
const impLoading = ref(false)
const result = ref(null)
const batches = ref([])
const batchLoading = ref(false)

function onFileChange(f) {
  file.value = f.raw
  result.value = null // 切换文件时清除上次结果
}

async function doDryRun() {
  if (!file.value) return
  dryLoading.value = true
  try {
    const { data } = await deviceApi.dryRun(file.value)
    result.value = data
    ElMessage.success('预览完成')
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || e?.message || '预览失败')
  } finally { dryLoading.value = false }
}

async function doImport() {
  if (!file.value) return
  impLoading.value = true
  try {
    const { data } = await deviceApi.importFile(file.value)
    result.value = data
    ElMessage.success('导入完成')
    loadBatches()
    // 导入成功后自动重置文件
    resetFile()
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || e?.message || '导入失败')
  } finally { impLoading.value = false }
}

function resetFile() {
  file.value = null
  fileList.value = []
  result.value = null
  // 清除el-upload内部文件列表
  if (uploadRef.value) {
    uploadRef.value.clearFiles()
  }
}

async function loadBatches() {
  batchLoading.value = true
  try {
    const { data } = await deviceApi.getBatches()
    batches.value = (data || []).map(b => ({ batchId: b }))
  } catch {} finally { batchLoading.value = false }
}

async function delBatch(row) {
  try {
    await deviceApi.deleteBatch(row.batchId)
    ElMessage.success('已清除')
    loadBatches()
  } catch {}
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

// 直接调用loadBatches（不要在setup顶层调onMounted, 改用loadBatches())
loadBatches()
</script>
<style scoped>.import{padding:20px}</style>
