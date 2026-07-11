<template>
  <div class="import"><h2>批量导入</h2>
    <el-card><el-upload drag :auto-upload="false" :on-change="onFileChange" accept=".csv,.xlsx,.xls" :limit="1"><el-icon :size="50"><UploadFilled/></el-icon><div>拖拽或点击上传 CSV / XLSX 文件</div></el-upload>
      <div style="margin-top:15px;display:flex;gap:10px"><el-button type="warning" @click="doDryRun" :disabled="!file" :loading="dryLoading">预览</el-button><el-button type="primary" @click="doImport" :disabled="!file" :loading="impLoading">导入</el-button></div>
      <el-alert v-if="result" style="margin-top:15px" :title="`总行数:${result.totalRows} | 新增:${result.successCount} | 更新:${result.updateCount} | 失败:${result.failCount} | 自动分类:${result.autoCategoryCount} | 未分类:${result.uncategorizedCount}`" type="info" :closable="false"/>
    </el-card>
    <el-card header="导入历史" style="margin-top:15px"><el-table :data="batches" v-loading="batchLoading"><el-table-column prop="batchId" label="批次号"/><el-table-column label="操作" width="100"><template #default="{row}"><el-popconfirm title="确定清除该批次?" @confirm="delBatch(row)"><template #reference><el-button size="small" type="danger">清除</el-button></template></el-popconfirm></template></el-table-column></el-table></el-card>
  </div>
</template>
<script setup>
import { ref,onMounted } from 'vue';import { deviceApi } from '@/api/device';import { ElMessage } from 'element-plus';import { UploadFilled } from '@element-plus/icons-vue'
const file=ref(null);const dryLoading=ref(false);const impLoading=ref(false);const result=ref(null);const batches=ref([]);const batchLoading=ref(false)
function onFileChange(f){file.value=f.raw}
async function doDryRun(){if(!file.value)return;dryLoading.value=true;try{const{data}=await deviceApi.dryRun(file.value);result.value=data;ElMessage.success('预览完成')}catch(e){ElMessage.error(e?.response?.data?.msg||e?.message||'预览失败')}finally{dryLoading.value=false}}
async function doImport(){if(!file.value)return;impLoading.value=true;try{const{data}=await deviceApi.importFile(file.value);result.value=data;ElMessage.success('导入完成');loadBatches()}catch(e){ElMessage.error(e?.response?.data?.msg||e?.message||'导入失败')}finally{impLoading.value=false}}
async function loadBatches(){batchLoading.value=true;try{const{data}=await deviceApi.getBatches();batches.value=(data||[]).map(b=>({batchId:b}))}catch{}finally{batchLoading.value=false}}
async function delBatch(row){try{await deviceApi.deleteBatch(row.batchId);ElMessage.success('已清除');loadBatches()}catch{}}
onMounted(loadBatches)
</script>
<style scoped>.import{padding:20px}</style>
