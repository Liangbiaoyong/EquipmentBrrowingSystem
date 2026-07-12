<template>
  <div class="admin-users"><h2>用户管理</h2>
    <el-card>
      <!-- 工具栏 -->
      <div style="margin-bottom:12px;display:flex;gap:8px;flex-wrap:wrap;align-items:center">
        <el-input v-model="keyword" placeholder="搜索用户名/姓名" clearable style="width:180px" @keyup.enter="load"/>
        <el-input v-model="deptFilter" placeholder="部门" clearable style="width:140px" @keyup.enter="load"/>
        <el-select v-model="roleFilter" placeholder="角色筛选" clearable style="width:140px" @change="load">
          <el-option v-for="r in roleOptions" :key="r.value" :label="r.label" :value="r.value"/>
        </el-select>
        <el-button type="primary" @click="load">查询</el-button>
        <div style="flex:1"/>
        <el-button type="success" @click="openCreate">新建用户</el-button>
        <el-button type="warning" @click="openBatchCreate">批量创建</el-button>
        <el-button type="danger" @click="openBatchDelete" :disabled="!selectedIds.length">批量删除({{selectedIds.length}})</el-button>
        <el-dropdown @command="downloadTemplate">
          <el-button>下载模板<el-icon><ArrowDown/></el-icon></el-button>
          <template #dropdown><el-dropdown-menu><el-dropdown-item command="xlsx">Excel 模板</el-dropdown-item><el-dropdown-item command="csv">CSV 模板</el-dropdown-item></el-dropdown-menu></template>
        </el-dropdown>
        <el-upload :show-file-list="false" :before-upload="beforeImport" :http-request="doImport" accept=".csv,.xlsx,.xls">
          <el-button type="primary">导入模板执行</el-button>
        </el-upload>
      </div>

      <el-table :data="list" v-loading="loading" stripe @selection-change="sel=>selectedIds=sel.map(r=>r.id)">
        <el-table-column type="selection" width="45"/>
        <el-table-column prop="id" label="ID" width="60"/>
        <el-table-column prop="username" label="用户名" width="110"/>
        <el-table-column prop="realName" label="姓名" width="90"/>
        <el-table-column label="角色" width="120"><template #default="{row}"><el-select v-model="row._newType" size="small" style="width:110px" @change="v=>changeRole(row.id,v)"><el-option v-for="r in roleOptions" :key="r.value" :label="r.label" :value="r.value"/></el-select></template></el-table-column>
        <el-table-column prop="department" label="部门" min-width="120" show-overflow-tooltip/>
        <el-table-column label="认证" width="70"><template #default="{row}"><el-tag size="small" :type="row.authSource==='C'?'success':'info'">{{ row.authSource==='C'?'CAS':'本地' }}</el-tag></template></el-table-column>
        <el-table-column label="状态" width="70"><template #default="{row}"><el-tag :type="row.status===1?'success':'danger'" size="small">{{ row.status===1?'正常':'禁用' }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="140" fixed="right"><template #default="{row}"><div style="white-space:nowrap"><el-button size="small" :type="row.status===1?'warning':'success'" @click="toggle(row)">{{ row.status===1?'禁用':'启用' }}</el-button><el-popconfirm title="确定删除?" @confirm="doDelete(row.id)"><template #reference><el-button size="small" type="danger" :disabled="row.username==='admin'">删除</el-button></template></el-popconfirm></div></template></el-table-column>
      </el-table>
      <div style="margin-top:12px;display:flex;justify-content:flex-end"><el-pagination v-model:current-page="page" :page-size="size" :total="total" layout="total,prev,pager,next" @current-change="load"/></div>
    </el-card>

    <!-- 新建用户对话框 -->
    <el-dialog v-model="showCreate" title="新建本地账户" width="460px"><el-form :model="c" label-width="80px" @submit.prevent>
      <el-form-item label="用户名" required><el-input v-model="c.username" placeholder="至少3个字符"/></el-form-item>
      <el-form-item label="姓名"><el-input v-model="c.realName"/></el-form-item>
      <el-form-item label="角色" required><el-select v-model="c.userType" style="width:100%"><el-option v-for="r in roleOptions" :key="r.value" :label="r.label" :value="r.value"/></el-select></el-form-item>
      <el-form-item label="密码" required><el-input v-model="c.password" type="password" placeholder="至少8位字符" show-password/></el-form-item>
      <el-form-item label="部门"><el-input v-model="c.department"/></el-form-item>
    </el-form><template #footer><el-button @click="showCreate=false">取消</el-button><el-button type="primary" @click="doCreate" :loading="submitting">创建</el-button></template></el-dialog>

    <!-- 批量创建对话框 -->
    <el-dialog v-model="showBatch" title="批量创建账户" width="600px">
      <p style="color:#909399;font-size:13px;margin-bottom:10px">输入JSON格式用户列表，每项包含 username/realName/userType/department/password</p>
      <el-input v-model="batchJson" type="textarea" :rows="10" placeholder='[{"username":"user01","realName":"张三","userType":0,"department":"建筑学院","password":"abc12345"}]'/>
      <template #footer><el-button @click="showBatch=false">取消</el-button><el-button type="primary" @click="doBatchCreate" :loading="submitting">批量创建</el-button></template>
    </el-dialog>

    <!-- 导入结果对话框 -->
    <el-dialog v-model="showResult" title="导入结果" width="500px">
      <el-descriptions :column="2" border><el-descriptions-item label="创建成功">{{ importResult.created }}</el-descriptions-item><el-descriptions-item label="销毁成功">{{ importResult.destroyed }}</el-descriptions-item><el-descriptions-item label="失败">{{ importResult.failed }}</el-descriptions-item></el-descriptions>
      <div v-if="importResult.errors?.length" style="margin-top:10px;max-height:200px;overflow:auto"><el-tag v-for="(e,i) in importResult.errors" :key="i" type="danger" size="small" style="margin:2px;display:block">{{ e }}</el-tag></div>
      <template #footer><el-button type="primary" @click="showResult=false;load()">确定</el-button></template>
    </el-dialog>
  </div>
</template>
<script setup>
import { ref,reactive,onMounted } from 'vue';import { adminApi } from '@/api/admin';import { ElMessage,ElMessageBox } from 'element-plus';import { ArrowDown } from '@element-plus/icons-vue'

const loading=ref(false);const list=ref([]);const page=ref(1);const size=ref(20);const total=ref(0)
const keyword=ref('');const deptFilter=ref('');const roleFilter=ref(null)
const selectedIds=ref([]);const submitting=ref(false)

const showCreate=ref(false);const c=reactive({username:'',realName:'',userType:2,password:'',department:''})
const showBatch=ref(false);const batchJson=ref('')
const showResult=ref(false);const importResult=reactive({created:0,destroyed:0,failed:0,errors:[]})

const roleOptions=[{label:'学生',value:0},{label:'教师',value:1},{label:'实验室管理员',value:2},{label:'系统管理员',value:3}]
function ut(t){const m={0:'学生',1:'教师',2:'实验室管理员',3:'系统管理员'};return m[t]||'未知'}

async function load(){loading.value=true;try{const{data}=await adminApi.getUsers({page:page.value,size:size.value,keyword:keyword.value||undefined,department:deptFilter.value||undefined,userType:roleFilter.value});list.value=(data.records||[]).map(u=>({...u,_newType:u.userType}));total.value=data.total||0}catch(e){ElMessage.error('加载用户列表失败')}finally{loading.value=false}}

// 单个操作
function openCreate(){Object.assign(c,{username:'',realName:'',userType:2,password:'',department:''});showCreate.value=true}
async function doCreate(){
  if(!c.username||c.username.length<3){ElMessage.warning('用户名至少3个字符');return}
  if(!c.password||c.password.length<8){ElMessage.warning('密码至少8位字符');return}
  submitting.value=true
  try{await adminApi.createUser({...c});ElMessage.success('已创建');showCreate.value=false;load()}catch(e){ElMessage.error(e?.response?.data?.msg||e?.message||'创建失败')}finally{submitting.value=false}
}

async function changeRole(id,t){try{await adminApi.updateRole(id,t);ElMessage.success('角色已变更');load()}catch(e){ElMessage.error(e?.response?.data?.msg||e?.message||'操作失败')}}
async function toggle(row){try{await adminApi.toggleStatus(row.id);ElMessage.success(row.status===1?'已禁用':'已启用');load()}catch(e){ElMessage.error(e?.response?.data?.msg||e?.message||'操作失败')}}
async function doDelete(id){try{await ElMessageBox.confirm('确定删除该用户？','警告',{type:'warning'});await adminApi.deleteUser(id);ElMessage.success('已删除');load()}catch(e){if(e!=='cancel'&&e?.response?.data?.msg)ElMessage.error(e.response.data.msg)}}

// 批量操作
function openBatchCreate(){batchJson.value='';showBatch.value=true}
async function doBatchCreate(){
  let users; try{users=JSON.parse(batchJson.value)}catch{ElMessage.warning('JSON格式不正确');return}
  if(!Array.isArray(users)||!users.length){ElMessage.warning('请至少输入一个用户');return}
  submitting.value=true
  try{const{data}=await adminApi.batchCreate(users);ElMessage.success(`创建成功${data.success}个, 失败${data.fail}个`);if(data.errors?.length){importResult.created=data.success;importResult.destroyed=0;importResult.failed=data.fail;importResult.errors=data.errors;showResult.value=true}showBatch.value=false;load()}catch(e){ElMessage.error(e?.response?.data?.msg||e?.message||'批量创建失败')}finally{submitting.value=false}
}

function openBatchDelete(){if(!selectedIds.value.length){ElMessage.warning('请选择用户');return}
  ElMessageBox.confirm(`将删除${selectedIds.value.length}个用户，不可恢复！`,'危险操作',{type:'error'}).then(async()=>{
    try{const{data}=await adminApi.batchDelete(selectedIds.value);ElMessage.success(`已删除${data.deleted}个`);load()}catch(e){ElMessage.error(e?.response?.data?.msg||e?.message||'批量删除失败')}
  }).catch(()=>{})
}

// 模板下载
function downloadTemplate(format){
  adminApi.downloadTemplate(format).then(r=>{
    const ext=format==='csv'?'csv':'xlsx'
    const mime=ext==='xlsx'?'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet':'text/csv'
    const blob=new Blob([r.data],{type:mime});const a=document.createElement('a')
    a.href=URL.createObjectURL(blob);a.download=`用户批量操作模板.${ext}`;a.click()
  }).catch(e=>ElMessage.error('模板下载失败'))
}

// 模板导入
function beforeImport(file){const ext=file.name.split('.').pop().toLowerCase();if(!['csv','xlsx','xls'].includes(ext)){ElMessage.warning('仅支持CSV/XLSX格式');return false};return true}
async function doImport(options){
  try{const{data}=await adminApi.importUsers(options.file);Object.assign(importResult,data);showResult.value=true}catch(e){ElMessage.error('导入失败: '+(e?.response?.data?.msg||e?.message))}
}

onMounted(load)
</script>
<style scoped>.admin-users{padding:20px}</style>
