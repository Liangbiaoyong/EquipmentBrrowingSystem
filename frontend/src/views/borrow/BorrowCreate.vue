<template>
  <div class="create"><h2>借用申请</h2>
    <el-card style="max-width:700px"><el-form :model="f" label-width="100px">
      <el-form-item label="设备" required><el-select v-model="f.deviceIds" filterable multiple placeholder="支持多选设备" style="width:100%" @change="onDeviceChange"><el-option v-for="d in devices" :key="d.id" :label="`${d.name} (${d.assetNo})`" :value="d.id"/></el-select></el-form-item>
      <el-form-item label="开始时间" required><el-date-picker v-model="f.startTime" type="datetime" placeholder="选择开始时间" format="YYYY-MM-DD HH:mm" value-format="YYYY-MM-DDTHH:mm:ss" style="width:100%"/></el-form-item>
      <el-form-item label="结束时间" required><el-date-picker v-model="f.endTime" type="datetime" placeholder="选择结束时间" format="YYYY-MM-DD HH:mm" value-format="YYYY-MM-DDTHH:mm:ss" style="width:100%"/></el-form-item>
      <el-form-item label="借用事由"><el-input v-model="f.reason" type="textarea" :rows="3"/></el-form-item>
      <el-divider content-position="left">审批流程</el-divider>
      <el-form-item label="初审人"><el-tag type="primary">{{ approverLevel1 || '设备使用人（自动匹配）' }}</el-tag><span style="font-size:12px;color:#909399;margin-left:8px">默认为设备登记的使用人</span></el-form-item>
      <el-form-item label="终审人"><el-tag type="success">{{ approverLevel2 || '实验室管理员（自动分配）' }}</el-tag><span style="font-size:12px;color:#909399;margin-left:8px">系统自动分配</span></el-form-item>
      <el-form-item><el-button type="primary" @click="submit" :loading="submitting">提交申请</el-button></el-form-item>
    </el-form></el-card>
  </div>
</template>
<script setup>
import { ref,reactive,onMounted,watch } from 'vue';import { useRoute,useRouter } from 'vue-router';import { borrowApi } from '@/api/borrow';import { deviceApi } from '@/api/device';import axios from '@/api/request';import { ElMessage } from 'element-plus'
const route=useRoute();const router=useRouter();const devices=ref([]);const submitting=ref(false)
const approverLevel1=ref('');const approverLevel2=ref('')
const f=reactive({deviceIds:[],startTime:'',endTime:'',reason:'',approverId:null})
onMounted(async()=>{
  try{const{data}=await deviceApi.list({page:1,size:200,status:1});devices.value=data.records}catch{}
  try{const{data}=await axios.get('/auth/approvers');const labAdmins=(data||[]).filter(u=>u.userType===2);if(labAdmins.length)approverLevel2.value=labAdmins[0].realName||labAdmins[0].username}catch{}
  if(route.query.deviceId){f.deviceIds=[Number(route.query.deviceId)];updateApproverInfo()}
})
function onDeviceChange(ids){
  if(ids&&ids.length){const d=devices.value.find(x=>x.id===ids[0]);if(d){f.approverId=d.defaultApproverId||null;approverLevel1.value=d.custodian||'设备使用人（自动匹配）'}}}
function updateApproverInfo(){if(f.deviceIds.length){const d=devices.value.find(x=>x.id===f.deviceIds[0]);if(d)approverLevel1.value=d.custodian||'设备使用人（自动匹配）'}}
async function submit(){
  if(!f.deviceIds.length||!f.startTime||!f.endTime){ElMessage.warning('请填写必填项');return}
  submitting.value=true
  try{await borrowApi.create({...f});ElMessage.success('申请已提交');router.push('/borrows/my')}catch(e){console.error(e)}finally{submitting.value=false}
}
</script>
<style scoped>.create{padding:20px}</style>
