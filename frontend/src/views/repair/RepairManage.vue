<template>
  <div class="repair"><h2>维修管理</h2>
    <el-tabs v-model="tab" @tab-change="load"><el-tab-pane label="待维修" name="PENDING"/><el-tab-pane label="维修中" name="REPAIRING"/><el-tab-pane label="已修复" name="FIXED"/></el-tabs>
    <el-card><el-table :data="list" v-loading="loading"><el-table-column label="ID" prop="id" width="80"/><el-table-column label="设备ID" prop="deviceId" width="80"/><el-table-column label="借用单ID" prop="borrowId" width="90"/><el-table-column label="故障描述" prop="faultDescription" min-width="160" show-overflow-tooltip/><el-table-column label="状态" width="90"><template #default="{row}"><el-tag :type="row.status==='PENDING'?'warning':row.status==='REPAIRING'?'':'success'">{{ row.status==='PENDING'?'待维修':row.status==='REPAIRING'?'维修中':'已修复' }}</el-tag></template></el-table-column>
      <el-table-column label="操作" width="160" fixed="right"><template #default="{row}"><el-button v-if="row.status==='PENDING'" size="small" type="warning" @click="start(row.id)">开始维修</el-button><el-button v-if="row.status==='REPAIRING'" size="small" type="success" @click="fix(row.id)">修复完成</el-button><el-button v-if="row.status==='FIXED'" size="small" disabled>已修复</el-button></template></el-table-column></el-table>
      <div style="margin-top:15px;display:flex;justify-content:flex-end"><el-pagination v-model:current-page="page" :page-size="size" :total="total" layout="total,prev,pager,next" @current-change="load"/></div></el-card>
    <el-dialog v-model="fixVisible" title="修复备注" width="400px"><el-input v-model="fixComment" type="textarea" placeholder="维修方案/备注"/><template #footer><el-button @click="fixVisible=false">取消</el-button><el-button type="primary" @click="doFix">确认修复</el-button></template></el-dialog>
  </div>
</template>
<script setup>
import { ref,onMounted } from 'vue';import { ElMessage } from 'element-plus'
const axios=await import('@/api/request').then(m=>m.default)
const tab=ref('PENDING');const loading=ref(false);const list=ref([]);const page=ref(1);const size=ref(20);const total=ref(0)
const fixVisible=ref(false);const fixId=ref(null);const fixComment=ref('')
async function load(){loading.value=true;try{const res=await axios.get('/repairs',{params:{page:page.value,size:size.value,status:tab.value==='FIXED'?'FIXED':tab.value==='REPAIRING'?'REPAIRING':'PENDING'}});list.value=res.data.records;total.value=res.data.total}catch{}finally{loading.value=false}}
async function start(id){try{await axios.put(`/repairs/${id}/start`);ElMessage.success('已开始维修');load()}catch{}}
function fix(id){fixId.value=id;fixComment.value='';fixVisible.value=true}
async function doFix(){try{await axios.put(`/repairs/${fixId.value}/fix`,null,{params:{comment:fixComment.value}});ElMessage.success('设备已恢复正常');fixVisible.value=false;load()}catch{}}
onMounted(load)
</script>
<style scoped>.repair{padding:20px}</style>
