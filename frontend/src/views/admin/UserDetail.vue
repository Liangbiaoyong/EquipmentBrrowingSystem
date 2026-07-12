<template>
  <div class="user-detail" v-loading="loading"><el-page-header @back="$router.back()" content="用户详情" style="margin-bottom:16px"/>
    <el-row :gutter="16" v-if="user">
      <el-col :span="16">
        <el-card shadow="never" header="基本信息">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="用户ID">{{ user.id }}</el-descriptions-item>
            <el-descriptions-item label="用户名">{{ user.username }}</el-descriptions-item>
            <el-descriptions-item label="姓名">{{ user.realName }}</el-descriptions-item>
            <el-descriptions-item label="角色"><el-tag>{{ roleText(user.userType) }}</el-tag></el-descriptions-item>
            <el-descriptions-item label="部门">{{ user.department||'-' }}</el-descriptions-item>
            <el-descriptions-item label="邮箱">{{ user.email||'-' }}</el-descriptions-item>
            <el-descriptions-item label="手机号">{{ user.phone||'-' }}</el-descriptions-item>
            <el-descriptions-item label="班级">{{ user.className||'-' }}</el-descriptions-item>
            <el-descriptions-item label="认证方式"><el-tag :type="user.authSource==='C'?'success':'info'" size="small">{{ user.authSource==='C'?'CAS认证':'本地账户' }}</el-tag></el-descriptions-item>
            <el-descriptions-item label="状态"><el-tag :type="user.status===1?'success':'danger'" size="small">{{ user.status===1?'正常':'禁用' }}</el-tag></el-descriptions-item>
            <el-descriptions-item label="CAS UUID" v-if="user.casUuid">{{ user.casUuid }}</el-descriptions-item>
            <el-descriptions-item label="一卡通号" v-if="user.cardNo">{{ user.cardNo }}</el-descriptions-item>
            <el-descriptions-item label="注册时间">{{ fmt(user.createTime) }}</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never" header="借用统计">
          <div class="stat-grid">
            <div class="mini-stat"><div class="ms-num">{{ borrowStats.total }}</div><div class="ms-label">总借用</div></div>
            <div class="mini-stat"><div class="ms-num" style="color:#409EFF">{{ borrowStats.borrowing }}</div><div class="ms-label">借用中</div></div>
            <div class="mini-stat"><div class="ms-num" style="color:#F56C6C">{{ borrowStats.overdue }}</div><div class="ms-label">逾期</div></div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" header="借用历史" style="margin-top:14px" v-if="user">
      <el-table :data="borrows" size="small" v-loading="bLoading" empty-text="暂无借用记录">
        <el-table-column prop="id" label="单号" width="70"/><el-table-column prop="deviceName" label="设备" min-width="140"/><el-table-column label="状态" width="90"><template #default="{row}"><el-tag size="small" :type="statusTag(row.status)">{{ statusText(row.status) }}</el-tag></template></el-table-column>
        <el-table-column label="开始" width="140"><template #default="{row}">{{ fmt(row.startTime) }}</template></el-table-column>
        <el-table-column label="结束" width="140"><template #default="{row}">{{ fmt(row.endTime) }}</template></el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref,reactive,onMounted } from 'vue'
import { useRoute } from 'vue-router'
import axios from '@/api/request'

const route=useRoute();const loading=ref(true);const bLoading=ref(false);const user=ref(null)
const borrows=ref([]);const borrowStats=reactive({total:0,borrowing:0,overdue:0})

const statusTagMap={PENDING_APPROVAL:'warning',APPROVED:'success',BORROWING:'',RETURNED:'info',OVERDUE:'danger',CANCELLED:'info'}
const statusTextMap={PENDING_APPROVAL:'待审批',APPROVED:'已通过',BORROWING:'借用中',RETURNED:'已归还',OVERDUE:'逾期',CANCELLED:'已取消'}
function statusTag(s){return statusTagMap[s]||'info'}
function statusText(s){return statusTextMap[s]||s}
function roleText(t){const m={0:'学生',1:'教师',2:'实验室管理员',3:'系统管理员'};return m[t]||'未知'}
function fmt(t){return t?t.replace('T',' ').substring(0,16):''}

onMounted(async()=>{
  const id=route.params.id
  try{const{data}=await axios.get(`/admin/users/${id}`);user.value=data;loading.value=false}catch{loading.value=false}
  bLoading.value=true
  try{
    const{data}=await axios.get('/borrows/browse',{params:{page:1,size:50}})
    const all=data.records||[]
    borrows.value=all.filter(r=>r.userId===Number(id))
    const ub=all.filter(r=>r.userId===Number(id))
    borrowStats.total=ub.length
    borrowStats.borrowing=ub.filter(r=>r.status==='BORROWING').length
    borrowStats.overdue=ub.filter(r=>r.status==='OVERDUE').length
  }catch{}finally{bLoading.value=false}
})
</script>

<style scoped>
.user-detail{padding:20px;max-width:1000px;margin:0 auto}
.stat-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:12px;text-align:center}
.ms-num{font-size:24px;font-weight:700}.ms-label{font-size:12px;color:#909399;margin-top:4px}
</style>
