<template>
  <div class="verify"><h2>归还核验</h2>
    <el-card><el-table :data="list" v-loading="loading" stripe><el-table-column label="借用单ID" prop="id" width="80"/><el-table-column label="设备" min-width="140"><template #default="{row}">{{ getDeviceName(row.deviceId) }}</template></el-table-column><el-table-column label="借用人" width="100"><template #default="{row}">{{ getUserName(row.userId) }}</template></el-table-column><el-table-column label="归还时间" width="170"><template #default="{row}">{{ fmt(row.realReturnTime) }}</template></el-table-column><el-table-column label="损坏报告" prop="damageReport" min-width="150" show-overflow-tooltip/><el-table-column label="逾期" width="80"><template #default="{row}"><el-tag v-if="row.overdueDays" type="danger">{{row.overdueDays}}天</el-tag><span v-else>无</span></template></el-table-column><el-table-column label="操作" width="100"><template #default="{row}"><el-popconfirm title="确认核验?" @confirm="doVerify(row.id)"><template #reference><el-button size="small" type="success">核验通过</el-button></template></el-popconfirm></template></el-table-column></el-table>
      <div style="margin-top:15px;display:flex;justify-content:flex-end"><el-pagination v-model:current-page="page" :page-size="size" :total="total" layout="total,prev,pager,next" @current-change="load"/></div></el-card>
  </div>
</template>
<script setup>
import { ref,onMounted } from 'vue';import axios from '@/api/request';import { ElMessage } from 'element-plus'
const loading=ref(false);const list=ref([]);const page=ref(1);const size=ref(20);const total=ref(0)
const deviceNames=ref({});const userNames=ref({})
function fmt(t){return t?t.replace('T',' ').substring(0,16):''}
function getDeviceName(id){return deviceNames.value[id]||`设备#${id}`}
function getUserName(id){return userNames.value[id]||`用户#${id}`}
async function fetchNames(){try{const[devs,users]=await Promise.all([axios.get('/devices',{params:{page:1,size:2000}}),axios.get('/admin/users',{params:{page:1,size:2000}})]);devs.data.records.forEach(d=>deviceNames.value[d.id]=d.name);users.data.records.forEach(u=>userNames.value[u.id]=u.realName||u.username)}catch{}}
async function load(){loading.value=true;try{const{data}=await axios.get('/borrows/my',{params:{page:page.value,size:size.value,status:'RETURNED'}});list.value=data.records.filter(r=>!r._verified);total.value=list.value.length}catch{}finally{loading.value=false}}
async function doVerify(id){try{await axios.post(`/borrows/${id}/verify`);ElMessage.success('已核验');load()}catch{}}
onMounted(async()=>{await fetchNames();load()})
</script>
<style scoped>.verify{padding:20px}</style>
