<template>
  <div class="testdata"><h2>测试数据管理</h2>

    <!-- 快速操作卡片 -->
    <div class="action-grid">
      <div class="act-card act-generate">
        <div class="act-icon"><el-icon :size="32"><MagicStick/></el-icon></div>
        <div class="act-info">
          <div class="act-title">一键生成测试数据</div>
          <div class="act-desc">生成50名教师 + 100名学生账户，以及最近30天内每天5~100条借用/审批/归还/逾期/维修记录</div>
        </div>
        <el-button type="primary" size="large" :loading="generating" @click="doGenerate">立即生成</el-button>
      </div>

      <div class="act-card act-cleanup">
        <div class="act-icon"><el-icon :size="32"><DeleteFilled/></el-icon></div>
        <div class="act-info">
          <div class="act-title">一键清除测试数据</div>
          <div class="act-desc">删除所有测试账户(teacher/student)及关联的借用单、审批记录、逾期记录、维修记录</div>
        </div>
        <el-button type="danger" size="large" :loading="cleaning" @click="doCleanup">立即清除</el-button>
      </div>
    </div>

    <!-- 账户预览 -->
    <el-card shadow="never" header="测试账户列表" style="margin-top:16px">
      <div style="margin-bottom:12px;display:flex;gap:8px">
        <el-radio-group v-model="userFilter" @change="loadUsers">
          <el-radio-button value="all">全部</el-radio-button>
          <el-radio-button value="teacher">教师(50)</el-radio-button>
          <el-radio-button value="student">学生(100)</el-radio-button>
        </el-radio-group>
        <span style="margin-left:auto;color:#909399;font-size:13px">共 {{ total }} 个测试账户，密码统一: <el-tag size="small">test</el-tag></span>
      </div>
      <el-table :data="users" stripe size="small" v-loading="userLoading" max-height="400">
        <el-table-column prop="username" label="用户名" width="150"/>
        <el-table-column prop="realName" label="姓名" width="150"/>
        <el-table-column label="角色" width="100"><template #default="{row}">{{ row.userType===1?'教师':row.userType===0?'学生':'其他' }}</template></el-table-column>
        <el-table-column prop="department" label="部门" min-width="150"/>
        <el-table-column label="认证" width="70"><template #default="{row}"><el-tag size="small" type="info">本地</el-tag></template></el-table-column>
        <el-table-column label="状态" width="70"><template #default="{row}"><el-tag size="small" :type="row.status===1?'success':'danger'">{{ row.status===1?'正常':'禁用' }}</el-tag></template></el-table-column>
      </el-table>
      <div style="margin-top:12px;display:flex;justify-content:flex-end">
        <el-pagination v-model:current-page="page" v-model:page-size="size" :page-sizes="[20,100,500]" :total="total" layout="total,sizes,prev,pager,next,jumper" @current-change="loadUsers" @size-change="s=>{size=s;page=1;loadUsers()}"/>
      </div>
    </el-card>

    <!-- 操作日志 -->
    <el-card shadow="never" header="操作日志" style="margin-top:16px" v-if="logs.length">
      <el-timeline>
        <el-timeline-item v-for="(l,i) in logs" :key="i" :timestamp="l.time" :type="l.type">{{ l.msg }}</el-timeline-item>
      </el-timeline>
    </el-card>
  </div>
</template>

<script setup>
import { ref,onMounted } from 'vue'
import axios from '@/api/request'
import { ElMessage,ElMessageBox } from 'element-plus'
import { MagicStick,DeleteFilled } from '@element-plus/icons-vue'

const generating=ref(false);const cleaning=ref(false)
const users=ref([]);const userLoading=ref(false);const total=ref(0)
const page=ref(1);const size=ref(50);const userFilter=ref('all')
const logs=ref([])

function addLog(msg,type='primary'){logs.value.unshift({msg,time:new Date().toLocaleString(),type});if(logs.value.length>50)logs.value.pop()}

async function loadUsers(){
  userLoading.value=true
  try{
    const params={page:page.value,size:size.value}
    if(userFilter.value==='teacher'){params.keyword='testTeacher';params.userType=1}
    else if(userFilter.value==='student'){params.keyword='testStudent';params.userType=0}
    else params.keyword='test'
    const{data}=await axios.get('/admin/users',{params})
    users.value=data.records||[];total.value=data.total||0
  }catch{}finally{userLoading.value=false}
}

async function doGenerate(){
  generating.value=true
  try{
    const{data}=await axios.post('/admin/test-data/generate',null,{timeout:120000})
    addLog(`生成完成: 教师${data.teachers}个 学生${data.students}个 借用${data.borrows}条`,'success')
    ElMessage.success('测试数据生成完成')
    loadUsers()
  }catch(e){addLog('生成失败: '+(e?.response?.data?.msg||e.message),'danger');ElMessage.error(e?.response?.data?.msg||'生成失败')}
  finally{generating.value=false}
}

async function doCleanup(){
  try{await ElMessageBox.confirm('将删除所有测试数据(账户+借用+审批+维修)，不可恢复！','确认清除',{type:'error',confirmButtonText:'确认清除',confirmButtonClass:'el-button--danger'})}
  catch{return}
  cleaning.value=true
  try{
    const{data}=await axios.delete('/admin/test-data/cleanup',{timeout:120000})
    addLog(`清除完成: 用户${data.users}个 借用${data.borrows}条 维修${data.repairs}条`,'warning')
    ElMessage.success('测试数据已清除')
    loadUsers()
  }catch(e){addLog('清除失败: '+(e?.response?.data?.msg||e.message),'danger');ElMessage.error('清除失败')}
  finally{cleaning.value=false}
}

onMounted(loadUsers)
</script>

<style scoped>
.testdata{padding:20px;max-width:1000px;margin:0 auto}
.action-grid{display:grid;grid-template-columns:1fr 1fr;gap:16px}
.act-card{display:flex;align-items:center;gap:16px;padding:20px 24px;border-radius:12px;background:#fff;box-shadow:0 2px 8px rgba(0,0,0,0.06)}
.act-generate{border-left:4px solid #409EFF}
.act-cleanup{border-left:4px solid #F56C6C}
.act-icon{width:56px;height:56px;border-radius:12px;display:flex;align-items:center;justify-content:center;flex-shrink:0}
.act-generate .act-icon{background:#ECF5FF;color:#409EFF}
.act-cleanup .act-icon{background:#FEF0F0;color:#F56C6C}
.act-info{flex:1;min-width:0}
.act-title{font-size:16px;font-weight:600;color:#303133;margin-bottom:4px}
.act-desc{font-size:12px;color:#909399;line-height:1.5}
@media(max-width:768px){.action-grid{grid-template-columns:1fr}}
</style>
