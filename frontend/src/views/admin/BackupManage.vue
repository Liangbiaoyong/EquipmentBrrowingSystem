<template>
  <div class="backup"><h2>数据备份管理</h2>
    <el-alert title="备份操作将对服务器性能产生影响，请在低峰时段操作" type="warning" show-icon :closable="false" style="margin-bottom:20px"/>
    <el-row :gutter="20">
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header><div style="display:flex;align-items:center;gap:8px"><el-icon :size="20"><FolderOpened/></el-icon><span style="font-size:16px;font-weight:600">数据库备份</span></div></template>
          <el-descriptions :column="1" border style="margin-bottom:15px">
            <el-descriptions-item label="备份方式">mysqldump 完整导出（含存储过程/触发器）</el-descriptions-item>
            <el-descriptions-item label="备份范围">全库（device_borrow）</el-descriptions-item>
            <el-descriptions-item label="文件位置">服务器 /tmp/ 目录</el-descriptions-item>
            <el-descriptions-item label="状态"><el-tag type="success">{{ backupStatus }}</el-tag></el-descriptions-item>
          </el-descriptions>
          <el-button type="primary" :loading="exporting" @click="doExport" :icon="Download">立即备份</el-button>
          <el-button @click="checkStatus">刷新状态</el-button>
          <div v-if="lastBackup" style="margin-top:12px;color:#909399;font-size:13px">最近备份: {{ lastBackup }}</div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="hover" header="操作日志">
          <el-timeline>
            <el-timeline-item v-for="(log,i) in logs" :key="i" :timestamp="log.time" :type="log.type" placement="top">{{ log.msg }}</el-timeline-item>
          </el-timeline>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>
<script setup>
import { ref,onMounted } from 'vue';import axios from '@/api/request';import { ElMessage } from 'element-plus';import { Download,FolderOpened } from '@element-plus/icons-vue'
const backupStatus=ref('就绪');const exporting=ref(false);const lastBackup=ref('')
const logs=ref([])
function addLog(msg,type='primary'){logs.value.unshift({msg,time:new Date().toLocaleString(),type});if(logs.value.length>20)logs.value.pop()}
async function checkStatus(){try{const{data}=await axios.get('/admin/backup/status');backupStatus.value='就绪';addLog('状态检查: '+data,'success')}catch(e){backupStatus.value='异常';addLog('状态检查失败: '+(e?.response?.data?.msg||e.message),'danger')}}
async function doExport(){exporting.value=true;try{const{data}=await axios.get('/admin/backup/export');lastBackup.value=new Date().toLocaleString();backupStatus.value='已备份';addLog('备份成功: '+data,'success');ElMessage.success('数据库备份完成')}catch(e){ElMessage.error('备份失败: '+(e?.response?.data?.msg||e.message));addLog('备份失败','danger')}finally{exporting.value=false}}
onMounted(checkStatus)
</script>
<style scoped>.backup{padding:20px}</style>
