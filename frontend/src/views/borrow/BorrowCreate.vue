<template>
  <div class="create">
    <div v-if="fromDetailDeviceId" style="margin-bottom:15px;display:flex;align-items:center;gap:8px">
      <el-button text @click="router.push('/devices/'+fromDetailDeviceId)"><el-icon><ArrowLeft/></el-icon> 返回设备详情</el-button>
    </div>
    <h2>借用申请</h2>
    <el-card style="max-width:750px"><el-form :model="f" label-width="100px">
      <!-- 设备选择 -->
      <el-form-item label="选择设备" required>
        <el-select v-model="f.deviceIds" filterable multiple placeholder="输入关键词搜索设备..." style="width:100%"
          :filter-method="deviceSearch" :loading="deviceLoading" @change="onDeviceChange"
          @visible-change="onPickerOpen" popper-class="device-picker-dropdown">
          <el-option v-for="d in deviceOptions" :key="d.id" :label="`${d.name} (${d.assetNo}) — ${d.location||''}`" :value="d.id">
            <div style="display:flex;justify-content:space-between;align-items:center">
              <span>{{ d.name }} <small style="color:#909399">{{ d.assetNo }}</small></span>
              <span>
                <el-tag size="small" :type="d.borrowType===1?'warning':''" effect="plain">{{ d.borrowType===1?'仅现场':'可借出' }}</el-tag>
                <el-tag size="small" :type="d.borrowStatus===1?'success':'danger'" style="margin-left:4px">{{ d.borrowStatus===1?'可借':'已借' }}</el-tag>
              </span>
            </div>
          </el-option>
        </el-select>
        <div style="margin-top:4px;font-size:12px;color:#909399">输入设备名称或资产编号搜索，支持多选 | 仅显示可借用设备</div>
      </el-form-item>

      <!-- 现场借用标识 -->
      <el-form-item v-if="hasOnsiteDevice" label="借用方式">
        <el-alert title="您选择了「仅现场借用」设备" type="warning" :closable="false" show-icon>
          <template #default>此类设备仅限实验室内现场使用，不可带走。借用时间系统自动设为当天。</template>
        </el-alert>
      </el-form-item>

      <!-- 时间选择（仅可借出设备显示） -->
      <template v-if="!allOnsite">
        <el-form-item label="开始时间" required>
          <el-date-picker v-model="f.startTime" type="datetime" placeholder="选择开始时间" format="YYYY-MM-DD HH:mm" value-format="YYYY-MM-DDTHH:mm:ss" style="width:100%"/>
        </el-form-item>
        <el-form-item label="结束时间" required>
          <el-date-picker v-model="f.endTime" type="datetime" placeholder="选择结束时间" format="YYYY-MM-DD HH:mm" value-format="YYYY-MM-DDTHH:mm:ss" style="width:100%"/>
        </el-form-item>
      </template>
      <template v-else>
        <el-form-item label="使用时间"><el-tag type="warning">现场借用默认为当天使用</el-tag></el-form-item>
      </template>

      <el-form-item label="借用事由"><el-input v-model="f.reason" type="textarea" :rows="2"/></el-form-item>
      <el-divider content-position="left">审批流程</el-divider>
      <el-form-item label="初审人"><el-tag type="primary">{{ approverLevel1 || '设备使用人（自动匹配）' }}</el-tag></el-form-item>
      <el-form-item label="终审人"><el-tag type="success">{{ approverLevel2 || '实验室管理员（自动分配）' }}</el-tag></el-form-item>
      <el-form-item><el-button type="primary" @click="submit" :loading="submitting">提交申请</el-button></el-form-item>
    </el-form></el-card>
  </div>
</template>
<script setup>
import { ref,reactive,onMounted,computed } from 'vue';import { useRoute,useRouter } from 'vue-router';import { borrowApi } from '@/api/borrow';import axios from '@/api/request';import { ElMessage } from 'element-plus'
const route=useRoute();const router=useRouter();const deviceOptions=ref([]);const deviceLoading=ref(false);const submitting=ref(false)
const approverLevel1=ref('');const approverLevel2=ref('');const fromDetailDeviceId=ref(null)
const f=reactive({deviceIds:[],startTime:'',endTime:'',reason:'',approverId:null})

const hasOnsiteDevice=computed(()=>f.deviceIds.some(id=>{const d=deviceOptions.value.find(x=>x.id===id);return d&&d.borrowType===1}))
const allOnsite=computed(()=>f.deviceIds.length>0&&f.deviceIds.every(id=>{const d=deviceOptions.value.find(x=>x.id===id);return d&&d.borrowType===1}))

onMounted(async()=>{
  await loadDevices()
  try{const{data}=await axios.get('/auth/approvers');const labAdmins=(data||[]).filter(u=>u.userType===2);if(labAdmins.length)approverLevel2.value=labAdmins[0].realName||labAdmins[0].username}catch{}
  if(route.query.deviceId){const id=Number(route.query.deviceId);fromDetailDeviceId.value=id;f.deviceIds=[id];updateApproverInfo()}
})

async function loadDevices(keyword){
  deviceLoading.value=true
  try{const{data}=await axios.get('/devices/picker',{params:{page:1,size:200,keyword:keyword||undefined}});deviceOptions.value=data.records||[]}catch(e){console.error('加载设备列表失败',e)}finally{deviceLoading.value=false}
}

function deviceSearch(keyword){loadDevices(keyword||'')}
function onPickerOpen(visible){if(visible&&deviceOptions.value.length===0)loadDevices()}

function onDeviceChange(ids){
  if(ids&&ids.length){
    const d=deviceOptions.value.find(x=>x.id===ids[0])
    if(d){f.approverId=d.defaultApproverId||null;approverLevel1.value=d.custodian||'设备使用人（自动匹配）'}
    // 纯现场借用设备自动设当天时间
    if(allOnsite.value){const now=new Date();f.startTime=now.toISOString().slice(0,19);f.endTime=new Date(now.getTime()+8*3600000).toISOString().slice(0,19)}
  }
}

function updateApproverInfo(){if(f.deviceIds.length){const d=deviceOptions.value.find(x=>x.id===f.deviceIds[0]);if(d)approverLevel1.value=d.custodian||'设备使用人（自动匹配）'}}

async function submit(){
  if(!f.deviceIds.length){ElMessage.warning('请选择设备');return}
  if(!allOnsite.value&&(!f.startTime||!f.endTime)){ElMessage.warning('请选择借用时间');return}
  if(allOnsite.value){const now=new Date();f.startTime=now.toISOString().slice(0,19);f.endTime=new Date(now.getTime()+8*3600000).toISOString().slice(0,19)}
  submitting.value=true
  try{await borrowApi.create({...f});ElMessage.success('申请已提交');router.push('/borrows/my')}catch(e){ElMessage.error(e?.response?.data?.msg||e?.message||'提交失败')}finally{submitting.value=false}
}
</script>
<style scoped>.create{padding:20px}</style>
