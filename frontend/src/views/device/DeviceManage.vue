<template>
  <div class="manage"><h2>设备管理</h2>
    <el-card>
      <el-row :gutter="10">
        <el-col :span="5"><el-input v-model="q.keyword" placeholder="搜索ID/名称/资产编号" clearable @keyup.enter="load"/></el-col>
        <el-col :span="5"><el-input v-model="q.location" placeholder="搜索存放地" clearable @keyup.enter="load"/></el-col>
        <el-col :span="3"><el-button type="primary" @click="load">搜索</el-button></el-col>
      </el-row>
    </el-card>
    <el-card style="margin-top:15px">
      <el-table :data="list" v-loading="loading" stripe @sort-change="onSort">
        <el-table-column prop="id" label="设备ID" width="75" sortable="custom"/>
        <el-table-column prop="assetNo" label="资产编号" width="130" sortable="custom"/>
        <el-table-column prop="name" label="名称" min-width="160" sortable="custom"/>
        <el-table-column prop="location" label="存放地" min-width="120" show-overflow-tooltip sortable="custom"/>
        <el-table-column prop="borrowStatus" label="借还状态" width="90" sortable="custom"><template #default="{row}"><el-tag :type="borrowStatusTag(row.borrowStatus)">{{ borrowStatusText(row.borrowStatus) }}</el-tag></template></el-table-column>
        <el-table-column prop="deviceStatus" label="设备状态" width="90" sortable="custom"><template #default="{row}"><el-tag :type="deviceStatusTag(row.deviceStatus)">{{ deviceStatusText(row.deviceStatus) }}</el-tag></template></el-table-column>
        <el-table-column label="借用类型" width="100"><template #default="{row}"><el-tag :type="row.borrowType===1?'warning':''" effect="plain">{{ row.borrowType===1?'仅现场':'可借出' }}</el-tag></template></el-table-column>
        <el-table-column label="默认审批人" width="140"><template #default="{row}"><span style="font-size:13px">{{ getApproverName(row.defaultApproverId) }}</span><el-button size="small" style="margin-left:4px" @click="openApprover(row)">修改</el-button></template></el-table-column>
        <el-table-column label="操作" width="160" fixed="right"><template #default="{row}"><el-button size="small" @click="openEdit(row)">编辑</el-button><el-popconfirm title="确定删除?" @confirm="doDelete(row.id)"><template #reference><el-button size="small" type="danger">删除</el-button></template></el-popconfirm></template></el-table-column>
      </el-table>
      <div style="margin-top:15px;display:flex;justify-content:flex-end"><el-pagination v-model:current-page="q.page" :page-size="q.size" :total="total" layout="total,prev,pager,next" @current-change="load"/></div>
    </el-card>

    <!-- 编辑设备对话框 -->
    <el-dialog v-model="editVisible" title="编辑设备" width="560px"><el-form :model="form" label-width="90px" v-if="editVisible">
      <el-form-item label="名称"><el-input v-model="form.name"/></el-form-item>
      <el-form-item label="型号"><el-input v-model="form.model"/></el-form-item>
      <el-form-item label="存放地"><el-input v-model="form.location"/></el-form-item>
      <el-form-item label="借还状态"><el-select v-model="form.borrowStatus" style="width:100%">
        <el-option label="可借用" :value="1"/><el-option label="借用中" :value="2"/>
        <el-option label="不可借" :value="3"/><el-option label="逾期" :value="4"/>
      </el-select></el-form-item>
      <el-form-item label="设备物理状态"><el-select v-model="form.deviceStatus" style="width:100%">
        <el-option label="正常" :value="1"/><el-option label="待维修" :value="2"/>
        <el-option label="无法维修" :value="3"/><el-option label="待报废" :value="4"/>
        <el-option label="已报废" :value="5"/>
      </el-select></el-form-item>
      <el-form-item label="借用类型"><el-select v-model="form.borrowType" style="width:100%">
        <el-option label="可借出" :value="2"/><el-option label="仅现场借用" :value="1"/>
      </el-select></el-form-item>
      <el-form-item label="描述"><el-input v-model="form.description" type="textarea" :rows="2"/></el-form-item>
    </el-form><template #footer><el-button @click="editVisible=false">取消</el-button><el-button type="primary" @click="doEdit">保存</el-button></template></el-dialog>

    <!-- 审批人对话框 -->
    <el-dialog v-model="approverVisible" title="设置默认审批人" width="400px"><el-select v-model="approverId" placeholder="选择审批人" filterable style="width:100%"><el-option v-for="u in users" :key="u.id" :label="`${u.realName||u.username} (${roleName(u.userType)})`" :value="u.id"/></el-select><template #footer><el-button @click="approverVisible=false">取消</el-button><el-button type="primary" @click="doSetApprover">保存</el-button></template></el-dialog>
  </div>
</template>
<script setup>
import { ref,reactive,onMounted } from 'vue';import { deviceApi } from '@/api/device';import { ElMessage } from 'element-plus';import axios from '@/api/request'

const loading=ref(false);const list=ref([]);const total=ref(0)
const editVisible=ref(false);const approverVisible=ref(false)
const approverCurrentId=ref(null);const approverId=ref(null);const users=ref([])
const q=reactive({page:1,size:20,keyword:'',location:''})
const form=reactive({id:null,name:'',model:'',location:'',borrowStatus:1,deviceStatus:1,borrowType:2,description:''})
const sortBy=ref('');const sortOrder=ref('desc');function onSort({prop,order}){sortBy.value=prop;sortOrder.value=order||'desc';load()}

const borrowStatusMap={1:'success',2:'warning',3:'danger',4:'danger'}
const borrowStatusTextMap={1:'可借用',2:'借用中',3:'不可借',4:'逾期'}
const deviceStatusMap={1:'success',2:'warning',3:'danger',4:'info',5:'info'}
const deviceStatusTextMap={1:'正常',2:'待维修',3:'无法维修',4:'待报废',5:'已报废'}
const roleNameMap={0:'学生',1:'教师',2:'实验室管理员',3:'系统管理员'}

function borrowStatusTag(v){return borrowStatusMap[v]||'info'}
function borrowStatusText(v){return borrowStatusTextMap[v]||v}
function deviceStatusTag(v){return deviceStatusMap[v]||'info'}
function deviceStatusText(v){return deviceStatusTextMap[v]||v}
function roleName(t){return roleNameMap[t]||t}
function getApproverName(id){if(!id)return'未设置';const u=users.value.find(x=>x.id===id);return u?u.realName||u.username:`ID:${id}`}

async function load(){loading.value=true;try{const{data}=await deviceApi.list({page:q.page,size:q.size,keyword:q.keyword||undefined,location:q.location||undefined,sort:sortBy.value||undefined,order:sortOrder.value});list.value=data.records||[];total.value=data.total||0}catch(e){console.error(e)}finally{loading.value=false}}
async function loadUsers(){try{const{data}=await axios.get('/admin/users',{params:{page:1,size:500}});users.value=data.records||[]}catch{}}

function openEdit(row){
  Object.assign(form,{
    id:row.id,name:row.name||'',model:row.model||'',location:row.location||'',
    borrowStatus:row.borrowStatus??1,deviceStatus:row.deviceStatus??1,
    borrowType:row.borrowType??2,description:row.description||''
  });editVisible.value=true
}

async function doEdit(){
  try{
    await deviceApi.update(form.id,{
      name:form.name,model:form.model,location:form.location,
      borrowStatus:form.borrowStatus,deviceStatus:form.deviceStatus,
      borrowType:form.borrowType,description:form.description
    });ElMessage.success('已更新');editVisible.value=false;load()
  }catch(e){ElMessage.error(e?.response?.data?.msg||'更新失败')}
}

function openApprover(row){approverCurrentId.value=row.id;approverId.value=row.defaultApproverId;approverVisible.value=true}
async function doSetApprover(){try{await axios.put(`/devices/${approverCurrentId.value}/default-approver`,null,{params:{approverId:approverId.value}});ElMessage.success('已更新');approverVisible.value=false;load()}catch(e){ElMessage.error(e?.response?.data?.msg||'操作失败')}}
async function doDelete(id){try{await deviceApi.delete(id);ElMessage.success('已删除');load()}catch(e){ElMessage.error(e?.response?.data?.msg||'删除失败')}}

onMounted(()=>{loadUsers();load()})
</script>
<style scoped>.manage{padding:20px}</style>
