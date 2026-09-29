<template>
  <div class="admin-export">
    <h2>业务数据导出</h2>
    <el-alert type="info" :closable="false" show-icon style="margin-bottom:14px;line-height:1.7">
      <template #title>
        导出的数据集已按业务口径整理好（中文列名、外键已展开成名称），可直接用 Excel 做透视表和图表。<br/>
        CSV 带 UTF-8 BOM，Excel 直接打开不会乱码；需要多工作表时用 Excel 格式。
      </template>
    </el-alert>

    <el-card shadow="never">
      <el-table :data="datasets" v-loading="loading" stripe>
        <el-table-column prop="name" label="数据集" width="140"/>
        <el-table-column prop="description" label="说明" min-width="300" show-overflow-tooltip/>
        <el-table-column label="包含列" min-width="280">
          <template #default="{row}">
            <span style="font-size:12px;color:#909399">{{ (row.columns||[]).join('、') }}</span>
          </template>
        </el-table-column>
        <el-table-column label="导出" width="180" fixed="right">
          <template #default="{row}">
            <el-button size="small" type="primary" :loading="busy===row.key+'xlsx'" @click="doExport(row,'xlsx')">Excel</el-button>
            <el-button size="small" :loading="busy===row.key+'csv'" @click="doExport(row,'csv')">CSV</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="!datasets.length && !loading" style="text-align:center;padding:30px;color:#909399">暂无可导出的数据集</div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import axios from '@/api/request'
import { ElMessage } from 'element-plus'
import { toLocalDate } from '@/utils/datetime'

const datasets = ref([])
const loading = ref(false)
const busy = ref('')

async function load() {
  loading.value = true
  try {
    const { data } = await axios.get('/admin/export/datasets')
    datasets.value = data || []
  } catch (e) { ElMessage.error('加载数据集列表失败') }
  finally { loading.value = false }
}

async function doExport(row, format) {
  busy.value = row.key + format
  try {
    const r = await axios.get(`/admin/export/${row.key}`, { params: { format }, responseType: 'blob' })
    const mime = format === 'xlsx'
      ? 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
      : 'text/csv'
    const a = document.createElement('a')
    a.href = URL.createObjectURL(new Blob([r.data], { type: mime }))
    a.download = `${row.name}_${toLocalDate()}.${format}`
    a.click()
    URL.revokeObjectURL(a.href)
    ElMessage.success('导出完成')
  } catch (e) {
    ElMessage.error('导出失败：' + (e?.response?.data?.msg || e?.message || ''))
  } finally { busy.value = '' }
}

onMounted(load)
</script>

<style scoped>.admin-export{padding:20px;max-width:1200px;margin:0 auto}</style>
