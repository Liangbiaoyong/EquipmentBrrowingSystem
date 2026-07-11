<template>
  <div class="logs"><h2>操作日志</h2>
    <el-card><el-row :gutter="10" style="margin-bottom:15px"><el-col :span="6"><el-input v-model="uname" placeholder="搜索用户名" clearable @keyup.enter="load"/></el-col><el-col :span="3"><el-select v-model="ustatus" placeholder="状态" clearable @change="load"><el-option label="成功" :value="1"/><el-option label="失败" :value="0"/></el-select></el-col><el-col :span="3"><el-button type="primary" @click="load">搜索</el-button></el-col><el-col :span="3" :offset="15"><el-button @click="doExport">导出CSV</el-button></el-col></el-row>
      <el-table :data="list" v-loading="loading" stripe size="small"><el-table-column prop="username" label="用户" width="90"/><el-table-column prop="operation" label="操作" width="120"/><el-table-column prop="method" label="方法" min-width="180" show-overflow-tooltip/><el-table-column prop="ip" label="IP" width="130"/><el-table-column prop="duration" label="耗时" width="80"><template #default="{row}">{{ row.duration }}ms</template></el-table-column><el-table-column label="状态" width="70"><template #default="{row}"><el-tag :type="row.status===1?'success':'danger'" size="small">{{ row.status===1?'成功':'失败' }}</el-tag></template></el-table-column><el-table-column prop="createTime" label="时间" width="170"/></el-table>
      <div style="margin-top:15px;display:flex;justify-content:flex-end"><el-pagination v-model:current-page="page" :page-size="size" :total="total" layout="total,prev,pager,next" @current-change="load"/></div></el-card>
  </div>
</template>
<script setup>
import { ref,onMounted } from 'vue';import { adminApi } from '@/api/admin';import request from '@/api/request';import { ElMessage } from 'element-plus'
const loading=ref(false);const list=ref([]);const page=ref(1);const size=ref(20);const total=ref(0);const uname=ref('');const ustatus=ref(null)
async function load(){loading.value=true;try{const{data}=await adminApi.getLogs({page:page.value,size:size.value,username:uname.value||undefined,status:ustatus.value});list.value=data.records||[];total.value=data.total||0}catch(e){console.error('加载日志失败',e)}finally{loading.value=false}}
onMounted(load)
async function doExport(){
  try{const r=await request.get('/admin/logs/export',{responseType:'blob'});const blob=new Blob([r.data],{type:'text/csv'});const a=document.createElement('a');a.href=URL.createObjectURL(blob);a.download='logs.csv';a.click()}catch(e){ElMessage.error('导出失败')}
}
</script>
<style scoped>.logs{padding:20px}</style>
