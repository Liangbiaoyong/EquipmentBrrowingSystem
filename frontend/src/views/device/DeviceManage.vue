<template>
  <div class="manage"><h2>设备管理</h2>
    <el-card><el-row :gutter="10"><el-col :span="6"><el-input v-model="q.keyword" placeholder="搜索" clearable @keyup.enter="load"/></el-col><el-col :span="3"><el-button type="primary" @click="load">搜索</el-button></el-col></el-row></el-card>
    <el-card style="margin-top:15px">
      <el-table :data="list" v-loading="loading" stripe><el-table-column prop="assetNo" label="资产编号" width="140"/><el-table-column prop="name" label="名称" min-width="150"/><el-table-column prop="location" label="存放地" min-width="120"/><el-table-column label="状态" width="90"><template #default="{row}"><el-tag :type="row.status===1?'success':row.status===2?'warning':'danger'">{{ row.status===1?'正常':row.status===2?'维修中':'报废' }}</el-tag></template></el-table-column><el-table-column label="默认审批人" width="120"><template #default="{row}"><span>{{ getApproverName(row.defaultApproverId) }}</span><el-button size="small" style="margin-left:4px" @click="openApprover(row)">修改</el-button></template></el-table-column><el-table-column label="操作" width="160" fixed="right"><template #default="{row}"><el-button size="small" @click="openEdit(row)">编辑</el-button><el-popconfirm title="确定删除?" @confirm="doDelete(row.id)"><template #reference><el-button size="small" type="danger">删除</el-button></template></el-popconfirm></template></el-table-column></el-table>
      <div style="margin-top:15px;display:flex;justify-content:flex-end"><el-pagination v-model:current-page="q.page" :page-size="q.size" :total="total" layout="total,prev,pager,next" @current-change="load"/></div>
    </el-card>
    <el-dialog v-model="editVisible" title="编辑设备" width="600px"><el-form :model="form" label-width="80px" v-if="editVisible"><el-form-item label="名称"><el-input v-model="form.name"/></el-form-item><el-form-item label="型号"><el-input v-model="form.model"/></el-form-item><el-form-item label="存放地"><el-input v-model="form.location"/></el-form-item><el-form-item label="状态"><el-select v-model="form.status"><el-option label="正常" :value="1"/><el-option label="维修中" :value="2"/><el-option label="报废" :value="3"/></el-select></el-form-item><el-form-item label="描述"><el-input v-model="form.description" type="textarea"/></el-form-item></el-form><template #footer><el-button @click="editVisible=false">取消</el-button><el-button type="primary" @click="doEdit">保存</el-button></template></el-dialog>
    <el-dialog v-model="approverVisible" title="设置默认审批人" width="400px"><el-select v-model="approverId" placeholder="选择审批人" filterable style="width:100%"><el-option v-for="u in users" :key="u.id" :label="`${u.realName||u.username} (${u.userType===1?'教师':u.userType===2?'管理员':'系统'})`" :value="u.id"/></el-select><template #footer><el-button @click="approverVisible=false">取消</el-button><el-button type="primary" @click="doSetApprover">保存</el-button></template></el-dialog>
  </div>
</template>
<script setup>
import { ref,reactive,onMounted } from 'vue';import { deviceApi } from '@/api/device';import { ElMessage } from 'element-plus';import axios from '@/api/request'
const loading=ref(false);const list=ref([]);const total=ref(0);const editVisible=ref(false);const approverVisible=ref(false);const approverCurrentId=ref(null);const approverId=ref(null);const users=ref([])
const q=reactive({page:1,size:20,keyword:''});const form=reactive({id:null,name:'',model:'',location:'',status:1,description:''})
function getApproverName(id){if(!id)return'未设置';const u=users.value.find(x=>x.id===id);return u?u.realName||u.username:`ID:${id}`}
async function load(){loading.value=true;try{const{data}=await deviceApi.list({...q});list.value=data.records;total.value=data.total}catch{}finally{loading.value=false}}
async function loadUsers(){try{const{data}=await axios.get('/admin/users',{params:{page:1,size:500}});users.value=data.records}catch{}}
function openEdit(row){Object.assign(form,{id:row.id,name:row.name||'',model:row.model||'',location:row.location||'',status:row.status||1,description:row.description||''});editVisible.value=true}
function openApprover(row){approverCurrentId.value=row.id;approverId.value=row.defaultApproverId;approverVisible.value=true}
async function doEdit(){try{await deviceApi.update(form.id,{name:form.name,model:form.model,location:form.location,status:form.status,description:form.description});ElMessage.success('已更新');editVisible.value=false;load()}catch{}}
async function doSetApprover(){try{await axios.put(`/devices/${approverCurrentId.value}/default-approver`,null,{params:{approverId:approverId.value}});ElMessage.success('已更新');approverVisible.value=false;load()}catch{}}
async function doDelete(id){try{await deviceApi.delete(id);ElMessage.success('已删除');load()}catch{}}
onMounted(()=>{loadUsers();load()})
</script>
<style scoped>.manage{padding:20px}</style>
