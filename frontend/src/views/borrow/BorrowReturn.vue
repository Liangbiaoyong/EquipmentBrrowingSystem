<template>
  <div class="return"><h2>归还登记</h2>
    <el-card style="max-width:600px">
      <el-descriptions v-if="record" :column="1" border style="margin-bottom:15px"><el-descriptions-item label="借用单ID">{{ record.id }}</el-descriptions-item><el-descriptions-item label="设备ID">{{ record.deviceId }}</el-descriptions-item><el-descriptions-item label="借用时间">{{ fmt(record.startTime) }} ~ {{ fmt(record.endTime) }}</el-descriptions-item></el-descriptions>
      <el-form label-width="100px">
        <el-form-item label="损坏描述"><el-input v-model="damage" type="textarea" :rows="3" placeholder="如有损坏请描述（可选）"/></el-form-item>
        <el-form-item label="归还照片"><el-upload :auto-upload="false" :on-change="onFile" :limit="1" accept="image/*" list-type="picture"><el-button size="small">选择照片</el-button></el-upload></el-form-item>
        <el-form-item><el-button type="primary" @click="submit" :loading="sub">确认归还</el-button></el-form-item>
      </el-form>
    </el-card>
  </div>
</template>
<script setup>
import { ref,onMounted } from 'vue';import { useRoute,useRouter } from 'vue-router';import { borrowApi } from '@/api/borrow';import { ElMessage } from 'element-plus'
const route=useRoute();const router=useRouter();const record=ref(null);const damage=ref('');const file=ref(null);const sub=ref(false)
function onFile(f){file.value=f.raw}
function fmt(t){return t?t.replace('T',' ').substring(0,16):''}
onMounted(async()=>{try{const{data}=await borrowApi.getById(route.params.id);record.value=data}catch(e){console.error('加载借用单失败',e)}})
async function submit(){sub.value=true;try{await borrowApi.returnDevice(route.params.id,{damageReport:damage.value,file:file.value});ElMessage.success('归还成功');router.push('/borrows/my')}catch(e){ElMessage.error(e?.response?.data?.msg||e?.message||'归还失败')}finally{sub.value=false}}
</script>
<style scoped>.return{padding:20px}</style>
