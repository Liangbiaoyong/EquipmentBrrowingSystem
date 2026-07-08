<template>
  <div class="create"><h2>借用申请</h2>
    <el-card style="max-width:700px"><el-form :model="f" label-width="100px" ref="formRef">
      <el-form-item label="设备" required><el-select v-model="f.deviceIds" filterable multiple placeholder="支持多选设备" style="width:100%"><el-option v-for="d in devices" :key="d.id" :label="`${d.name} (${d.assetNo})`" :value="d.id"/></el-select></el-form-item>
      <el-form-item label="开始时间" required><el-date-picker v-model="f.startTime" type="datetime" placeholder="选择开始时间" format="YYYY-MM-DD HH:mm" value-format="YYYY-MM-DDTHH:mm:ss" style="width:100%"/></el-form-item>
      <el-form-item label="结束时间" required><el-date-picker v-model="f.endTime" type="datetime" placeholder="选择结束时间" format="YYYY-MM-DD HH:mm" value-format="YYYY-MM-DDTHH:mm:ss" style="width:100%"/></el-form-item>
      <el-form-item label="借用事由"><el-input v-model="f.reason" type="textarea" :rows="3"/></el-form-item>
      <el-form-item label="审批人"><el-select v-model="f.approverId" placeholder="选择审批教师" style="width:100%"><el-option v-for="u in teachers" :key="u.id" :label="u.realName||u.username" :value="u.id"/></el-select></el-form-item>
      <el-form-item><el-button type="primary" @click="submit" :loading="submitting">提交申请</el-button></el-form-item>
    </el-form></el-card>
  </div>
</template>
<script setup>
import { ref,reactive,onMounted } from 'vue';import { useRoute,useRouter } from 'vue-router';import { borrowApi } from '@/api/borrow';import { deviceApi } from '@/api/device';import { adminApi } from '@/api/admin';import { ElMessage } from 'element-plus'
const route=useRoute();const router=useRouter();const devices=ref([]);const teachers=ref([]);const submitting=ref(false)
const f=reactive({deviceIds:[],startTime:'',endTime:'',reason:'',approverId:null})
onMounted(async()=>{
  try{const{data}=await deviceApi.list({page:1,size:200,status:1});devices.value=data.records}
  catch{}
  try{const{data}=await adminApi.getUsers({page:1,size:500});teachers.value=data.records}
  catch{}
  if(route.query.deviceId) f.deviceIds=[Number(route.query.deviceId)]
})
async function submit(){
  if(!f.deviceIds.length||!f.startTime||!f.endTime){ElMessage.warning('请填写必填项');return}
  submitting.value=true
  try{await borrowApi.create({...f});ElMessage.success('申请已提交');router.push('/borrows/my')}catch{}finally{submitting.value=false}
}
</script>
<style scoped>.create{padding:20px}</style>
