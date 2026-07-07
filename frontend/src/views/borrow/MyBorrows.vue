<template>
  <div class="my"><h2>我的借用</h2>
    <el-card><el-tabs v-model="activeTab" @tab-change="load"><el-tab-pane label="全部" name=""/><el-tab-pane label="待审批" name="PENDING_APPROVAL"/><el-tab-pane label="已通过" name="APPROVED"/><el-tab-pane label="借用中" name="BORROWING"/><el-tab-pane label="已归还" name="RETURNED"/><el-tab-pane label="已驳回" name="REJECTED"/></el-tabs>
      <el-table :data="list" v-loading="loading" stripe><el-table-column label="借用单ID" width="80" prop="id"/><el-table-column label="设备" min-width="140"><template #default="{row}">{{ getDeviceName(row.deviceId) }}</template></el-table-column><el-table-column label="开始时间" width="170"><template #default="{row}">{{ fmt(row.startTime) }}</template></el-table-column><el-table-column label="结束时间" width="170"><template #default="{row}">{{ fmt(row.endTime) }}</template></el-table-column><el-table-column label="状态" width="110"><template #default="{row}"><el-tag :type="stc(row.status)">{{ stx(row.status) }}</el-tag></template></el-table-column><el-table-column label="事由" prop="reason" min-width="120" show-overflow-tooltip/>
        <el-table-column label="操作" width="160" fixed="right"><template #default="{row}"><el-button size="small" @click="$router.push(`/borrows/${row.id}`)">详情</el-button><el-button v-if="row.status==='BORROWING'" size="small" type="warning" @click="$router.push(`/borrows/${row.id}/return`)">归还</el-button><el-popconfirm v-if="row.status==='PENDING_APPROVAL'" title="确定取消?" @confirm="doCancel(row.id)"><template #reference><el-button size="small" type="danger">取消</el-button></template></el-popconfirm></template></el-table-column></el-table>
      <div style="margin-top:15px;display:flex;justify-content:flex-end"><el-pagination v-model:current-page="page" :page-size="size" :total="total" layout="total,prev,pager,next" @current-change="load"/></div></el-card>
  </div>
</template>
<script setup>
import { ref,onMounted } from 'vue';import { borrowApi } from '@/api/borrow';import axios from '@/api/request';import { ElMessage } from 'element-plus'
const loading=ref(false);const list=ref([]);const page=ref(1);const size=ref(20);const total=ref(0);const activeTab=ref('');const deviceNames=ref({})
function stc(s){const m={'PENDING_APPROVAL':'warning','APPROVED':'success','BORROWING':'','RETURNED':'info','REJECTED':'danger','CANCELLED':'info','OVERDUE':'danger'};return m[s]||'info'}
function stx(s){const m={'PENDING_APPROVAL':'待审批','APPROVED':'已通过','BORROWING':'借用中','RETURNED':'已归还','REJECTED':'已驳回','CANCELLED':'已取消','OVERDUE':'逾期'};return m[s]||s}
function fmt(t){return t?t.replace('T',' ').substring(0,16):''}
function getDeviceName(id){return deviceNames.value[id]||`设备#${id}`}
async function fetchDeviceNames(){try{const{data}=await axios.get('/devices',{params:{page:1,size:2000}});data.records.forEach(d=>deviceNames.value[d.id]=d.name)}catch{}}
async function load(){loading.value=true;try{const{data}=await borrowApi.getMyBorrows({page:page.value,size:size.value,status:activeTab.value||undefined});list.value=data.records;total.value=data.total}catch{}finally{loading.value=false}}
async function doCancel(id){try{await borrowApi.cancel(id);ElMessage.success('已取消');load()}catch{}}
onMounted(async()=>{await fetchDeviceNames();load()})
</script>
<style scoped>.my{padding:20px}</style>
