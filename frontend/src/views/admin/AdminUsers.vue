<template>
  <div class="admin-users"><h2>用户管理</h2>
    <el-card><el-row :gutter="10" style="margin-bottom:15px"><el-col :span="6"><el-input v-model="keyword" placeholder="搜索用户名/姓名" clearable @keyup.enter="load"/></el-col><el-col :span="3"><el-button type="primary" @click="load">搜索</el-button></el-col><el-col :span="3" :offset="12"><el-button type="success" @click="showCreate=true">新建用户</el-button></el-col></el-row>
      <el-table :data="list" v-loading="loading" stripe><el-table-column prop="username" label="用户名" width="120"/><el-table-column prop="realName" label="姓名" width="100"/><el-table-column label="角色" width="120"><template #default="{row}">{{ ut(row.userType) }}</template></el-table-column><el-table-column prop="department" label="部门" min-width="100"/><el-table-column label="状态" width="80"><template #default="{row}"><el-tag :type="row.status===1?'success':'danger'">{{ row.status===1?'正常':'禁用' }}</el-tag></template></el-table-column><el-table-column label="操作" width="200" fixed="right"><template #default="{row}"><el-select v-model="row._newType" size="small" style="width:100px" @change="v=>changeRole(row.id,v)"><el-option label="学生" :value="0"/><el-option label="教师" :value="1"/><el-option label="实验室管理员" :value="2"/><el-option label="系统管理员" :value="3"/></el-select><el-button size="small" :type="row.status===1?'danger':'success'" @click="toggle(row)">{{ row.status===1?'禁用':'启用' }}</el-button></template></el-table-column></el-table>
      <div style="margin-top:15px;display:flex;justify-content:flex-end"><el-pagination v-model:current-page="page" :page-size="size" :total="total" layout="total,prev,pager,next" @current-change="load"/></div></el-card>
    <el-dialog v-model="showCreate" title="新建本地账户" width="450px"><el-form :model="c" label-width="80px"><el-form-item label="用户名"><el-input v-model="c.username"/></el-form-item><el-form-item label="姓名"><el-input v-model="c.realName"/></el-form-item><el-form-item label="角色"><el-select v-model="c.userType"><el-option label="学生" :value="0"/><el-option label="教师" :value="1"/><el-option label="实验室管理员" :value="2"/><el-option label="系统管理员" :value="3"/></el-select></el-form-item><el-form-item label="密码"><el-input v-model="c.password" type="password"/></el-form-item><el-form-item label="部门"><el-input v-model="c.department"/></el-form-item></el-form><template #footer><el-button @click="showCreate=false">取消</el-button><el-button type="primary" @click="doCreate">创建</el-button></template></el-dialog>
  </div>
</template>
<script setup>
import { ref,reactive,onMounted } from 'vue';import { adminApi } from '@/api/admin';import { ElMessage } from 'element-plus'
const loading=ref(false);const list=ref([]);const page=ref(1);const size=ref(20);const total=ref(0);const keyword=ref('');const showCreate=ref(false)
const c=reactive({username:'',realName:'',userType:2,password:'',department:''})
function ut(t){const m={0:'学生',1:'教师',2:'实验室管理员',3:'系统管理员'};return m[t]||'未知'}
async function load(){loading.value=true;try{const{data}=await adminApi.getUsers({page:page.value,size:size.value,keyword:keyword.value});list.value=data.records.map(u=>({...u,_newType:u.userType}));total.value=data.total}catch{}finally{loading.value=false}}
async function changeRole(id,t){try{await adminApi.updateRole(id,t);ElMessage.success('角色已变更');load()}catch{}}
async function toggle(row){try{await adminApi.toggleStatus(row.id);ElMessage.success('已更新');load()}catch{}}
async function doCreate(){try{await adminApi.createUser({...c});ElMessage.success('已创建');showCreate.value=false;load()}catch{}}
onMounted(load)
</script>
<style scoped>.admin-users{padding:20px}</style>
