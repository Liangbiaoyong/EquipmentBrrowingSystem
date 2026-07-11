<template>
  <div class="overdue"><h2>逾期管理</h2>
    <el-card>
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="id" label="单号" width="80"/>
        <el-table-column label="设备" min-width="160"><template #default="{row}">{{ getDeviceName(row.deviceId) }}</template></el-table-column>
        <el-table-column label="借用人" width="100"><template #default="{row}">{{ getUserName(row.userId) }}</template></el-table-column>
        <el-table-column label="借用时间" width="160"><template #default="{row}">{{ formatTime(row.startTime) }} ~ {{ formatTime(row.endTime) }}</template></el-table-column>
        <el-table-column prop="overdueDays" label="逾期天数" width="100"><template #default="{row}"><el-tag type="danger">{{ row.overdueDays }} 天</el-tag></template></el-table-column>
        <el-table-column label="操作" width="200"><template #default="{row}"><el-button size="small" type="warning" @click="handleReturn(row)">归还登记</el-button><el-button size="small" @click="handleNotify(row)">催还通知</el-button></template></el-table-column>
      </el-table>
      <div style="margin-top:15px;display:flex;justify-content:flex-end"><el-pagination v-model:current-page="page" :page-size="size" :total="total" layout="total,prev,pager,next" @current-change="load"/></div>
    </el-card>

    <!-- 归还对话框 -->
    <el-dialog v-model="returnVisible" title="归还登记" width="500px">
      <el-form :model="returnForm" label-width="100px">
        <el-form-item label="设备"><el-tag>{{ returnDeviceName }}</el-tag></el-form-item>
        <el-form-item label="逾期天数"><el-tag type="danger">{{ returnForm.overdueDays }} 天</el-tag></el-form-item>
        <el-form-item label="损坏情况"><el-input v-model="returnForm.damageReport" type="textarea" :rows="3" placeholder="无损坏可不填"/></el-form-item>
      </el-form>
      <template #footer><el-button @click="returnVisible=false">取消</el-button><el-button type="primary" @click="submitReturn" :loading="submitting">确认归还</el-button></template>
    </el-dialog>
  </div>
</template>
<script setup>
import { ref,onMounted } from 'vue';import axios from '@/api/request';import { ElMessage } from 'element-plus'
const list=ref([]);const loading=ref(false);const page=ref(1);const size=ref(20);const total=ref(0)
const returnVisible=ref(false);const returnForm=ref({});const returnDeviceName=ref('');const submitting=ref(false)
const deviceNames=ref({});const userNames=ref({})

async function load(){loading.value=true;try{const{data}=await axios.get('/borrows/overdue',{params:{page:page.value,size:size.value}});list.value=data.records||[];total.value=data.total||0;loadNames(data.records)}catch(e){ElMessage.error('加载逾期列表失败')}finally{loading.value=false}}
async function loadNames(records){if(!records)return;const dIds=[...new Set(records.map(r=>r.deviceId))];const uIds=[...new Set(records.map(r=>r.userId))];for(const id of dIds)if(!deviceNames.value[id]){try{const{data}=await axios.get(`/devices/${id}`);deviceNames.value[id]=data?.device?.name||'设备#'+id}catch{deviceNames.value[id]='设备#'+id}};for(const id of uIds)if(!userNames.value[id]){try{const{data}=await axios.get('/auth/info');userNames.value[id]=id+''}catch{userNames.value[id]='用户#'+id}}}
function getDeviceName(id){return deviceNames.value[id]||'设备#'+id}
function getUserName(id){return userNames.value[id]||'用户#'+id}
function formatTime(t){return t?t.replace('T',' ').substring(0,16):''}

function handleReturn(row){returnForm.value={borrowId:row.id,damageReport:'',overdueDays:row.overdueDays};returnDeviceName.value=getDeviceName(row.deviceId);returnVisible.value=true}
async function submitReturn(){submitting.value=true;try{await axios.post(`/borrows/${returnForm.value.borrowId}/return`,null,{params:{damageReport:returnForm.value.damageReport||''}});ElMessage.success('归还成功');returnVisible.value=false;load()}catch(e){ElMessage.error('归还失败:'+(e?.response?.data?.msg||e.message))}finally{submitting.value=false}}
function handleNotify(row){ElMessage.success('催还通知已发送')}
onMounted(load)
</script>
<style scoped>.overdue{padding:20px}</style>
