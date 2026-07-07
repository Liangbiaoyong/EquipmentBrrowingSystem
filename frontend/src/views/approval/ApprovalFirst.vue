<template>
  <div class="approval"><h2>{{ isSecond ? '二级审批' : '一级审批' }}</h2>
    <el-card><el-table :data="list" v-loading="loading" stripe><el-table-column label="ID" width="70" prop="id"/><el-table-column label="设备" min-width="140"><template #default="{row}">{{ getDeviceName(row.deviceId) }}</template></el-table-column><el-table-column label="借用人" width="100"><template #default="{row}">{{ getUserName(row.userId) }}</template></el-table-column><el-table-column label="开始" width="130"><template #default="{row}">{{ fmt(row.startTime) }}</template></el-table-column><el-table-column label="结束" width="130"><template #default="{row}">{{ fmt(row.endTime) }}</template></el-table-column><el-table-column label="事由" prop="reason" min-width="120" show-overflow-tooltip/><el-table-column label="操作" width="200" fixed="right"><template #default="{row}"><el-button size="small" type="success" @click="doApprove(row.id,true)">通过</el-button><el-button size="small" type="danger" @click="doApprove(row.id,false)">驳回</el-button></template></el-table-column></el-table>
      <div style="margin-top:15px;display:flex;justify-content:flex-end"><el-pagination v-model:current-page="page" :page-size="size" :total="total" layout="total,prev,pager,next" @current-change="load"/></div></el-card>
    <el-dialog v-model="d.visible" :title="d.approved?'审批通过':'驳回申请'" width="400px"><el-input v-model="d.comment" type="textarea" :placeholder="d.approved?'审批意见(可选)':'驳回原因(必填)'"/><template #footer><el-button @click="d.visible=false">取消</el-button><el-button type="primary" @click="confirmApprove">确认</el-button></template></el-dialog>
  </div>
</template>
<script setup>
import { ref,reactive,onMounted } from 'vue';import { borrowApi } from '@/api/borrow';import { ElMessage } from 'element-plus';import axios from '@/api/request'
const props=defineProps({isSecond:{type:Boolean,default:false}})
const loading=ref(false);const list=ref([]);const page=ref(1);const size=ref(20);const total=ref(0)
const d=reactive({visible:false,borrowId:null,approved:true,comment:''})
const deviceNames=ref({});const userNames=ref({})
function fmt(t){return t?t.replace('T',' ').substring(0,16):''}
function getDeviceName(id){return deviceNames.value[id]||`设备#${id}`}
function getUserName(id){return userNames.value[id]||`用户#${id}`}
async function fetchNames(){try{const[devs,users]=await Promise.all([axios.get('/devices',{params:{page:1,size:2000}}),axios.get('/admin/users',{params:{page:1,size:2000}})]);devs.data.records.forEach(d=>deviceNames.value[d.id]=d.name);users.data.records.forEach(u=>userNames.value[u.id]=u.realName||u.username)}catch{}}
async function load(){loading.value=true;try{const fn=props.isSecond?borrowApi.getPendingSecond:borrowApi.getPendingFirst;const{data}=await fn({page:page.value,size:size.value});list.value=data.records;total.value=data.total}catch{}finally{loading.value=false}}
function doApprove(borrowId,approved){d.borrowId=borrowId;d.approved=approved;d.comment='';d.visible=true}
async function confirmApprove(){try{await borrowApi.approve({borrowId:d.borrowId,approved:d.approved,comment:d.comment});ElMessage.success(d.approved?'已通过':'已驳回');d.visible=false;load()}catch{}}
onMounted(async()=>{await fetchNames();load()})
</script>
<style scoped>.approval{padding:20px}</style>
