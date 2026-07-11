<template>
  <div class="lab"><h2>实验室管理</h2>
    <el-tabs v-model="tab">
      <el-tab-pane label="实验室列表" name="labs">
        <el-card>
          <div style="margin-bottom:12px;display:flex;gap:8px"><el-input v-model="labKeyword" placeholder="搜索名称/编码" style="width:220px" @keyup.enter="loadLabs"/><el-button @click="loadLabs">搜索</el-button><el-button type="primary" @click="labForm={};labVisible=true">+ 新增实验室</el-button></div>
          <el-table :data="labs" stripe>
            <el-table-column prop="id" label="ID" width="80"/><el-table-column prop="name" label="实验室名称" min-width="200"/><el-table-column prop="code" label="编码" width="140"/><el-table-column prop="locationPrefix" label="存放地前缀" width="160"/><el-table-column label="状态" width="80"><template #default="{row}"><el-tag :type="row.status===1?'success':'info'">{{ row.status===1?'启用':'禁用' }}</el-tag></template></el-table-column>
            <el-table-column label="操作" width="140"><template #default="{row}"><el-button size="small" @click="editLab(row)">编辑</el-button><el-button size="small" type="danger" @click="deleteLab(row.id)">删除</el-button></template></el-table-column>
          </el-table>
          <div style="margin-top:12px;display:flex;justify-content:flex-end"><el-pagination v-model:current-page="labPage" :page-size="labSize" :total="labTotal" layout="prev,pager,next" @current-change="loadLabs"/></div>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="地点映射" name="rooms">
        <el-card>
          <div style="margin-bottom:12px;display:flex;gap:8px;flex-wrap:wrap">
            <el-select v-model="roomFilter" placeholder="按实验室筛选" clearable @change="loadRooms" style="width:200px"><el-option v-for="l in allLabs" :key="l.id" :label="l.name" :value="l.id"/></el-select>
            <el-input v-model="roomSearch" placeholder="搜索地点名" style="width:180px" @keyup.enter="loadRooms"/>
            <el-button @click="loadRooms">搜索</el-button>
            <el-button type="primary" @click="roomForm={};roomVisible=true">+ 新增地点</el-button>
            <el-button type="warning" @click="syncDevices" :loading="syncing">同步设备实验室</el-button>
          </div>
          <el-table :data="rooms" stripe>
            <el-table-column prop="id" label="ID" width="80"/><el-table-column label="实验室" width="180"><template #default="{row}">{{ labName(row.laboratoryId) }}</template></el-table-column>
            <el-table-column prop="roomName" label="地点名称" width="150"/><el-table-column prop="fullLocation" label="完整存放地" min-width="280" show-overflow-tooltip/>
            <el-table-column label="操作" width="140"><template #default="{row}"><el-button size="small" @click="editRoom(row)">编辑</el-button><el-button size="small" type="danger" @click="deleteRoom(row.id)">删除</el-button></template></el-table-column>
          </el-table>
          <div style="margin-top:12px;display:flex;justify-content:flex-end"><el-pagination v-model:current-page="roomPage" :page-size="roomSize" :total="roomTotal" layout="prev,pager,next" @current-change="loadRooms"/></div>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- 实验室编辑对话框 -->
    <el-dialog v-model="labVisible" :title="labForm.id?'编辑实验室':'新增实验室'" width="500px"><el-form :model="labForm" label-width="100px">
      <el-form-item label="实验室名称" required><el-input v-model="labForm.name"/></el-form-item>
      <el-form-item label="编码"><el-input v-model="labForm.code"/></el-form-item>
      <el-form-item label="存放地前缀"><el-input v-model="labForm.locationPrefix"/></el-form-item>
      <el-form-item label="描述"><el-input v-model="labForm.description" type="textarea" :rows="2"/></el-form-item>
    </el-form><template #footer><el-button @click="labVisible=false">取消</el-button><el-button type="primary" @click="saveLab">保存</el-button></template></el-dialog>

    <!-- 地点编辑对话框 -->
    <el-dialog v-model="roomVisible" :title="roomForm.id?'编辑地点':'新增地点'" width="500px"><el-form :model="roomForm" label-width="100px">
      <el-form-item label="实验室" required><el-select v-model="roomForm.laboratoryId" style="width:100%"><el-option v-for="l in allLabs" :key="l.id" :label="l.name" :value="l.id"/></el-select></el-form-item>
      <el-form-item label="地点名称" required><el-input v-model="roomForm.roomName" placeholder="如：工程南501"/></el-form-item>
      <el-form-item label="完整存放地"><el-input v-model="roomForm.fullLocation" placeholder="如：工程实验楼南楼＞5＞绿色智慧建筑实验室1室-520"/></el-form-item>
    </el-form><template #footer><el-button @click="roomVisible=false">取消</el-button><el-button type="primary" @click="saveRoom">保存</el-button></template></el-dialog>
  </div>
</template>
<script setup>
import { ref,onMounted } from 'vue';import axios from '@/api/request';import { ElMessage,ElMessageBox } from 'element-plus'
const tab=ref('labs');const labs=ref([]);const rooms=ref([]);const allLabs=ref([])
const labPage=ref(1);const labSize=ref(20);const labTotal=ref(0);const labKeyword=ref('')
const roomPage=ref(1);const roomSize=ref(20);const roomTotal=ref(0);const roomFilter=ref(null);const roomSearch=ref('')
const labForm=ref({});const labVisible=ref(false);const roomForm=ref({});const roomVisible=ref(false);const syncing=ref(false)
function labName(id){const l=allLabs.value.find(x=>x.id===id);return l?l.name:'实验室#'+id}

async function loadLabs(){try{const{data}=await axios.get('/laboratories',{params:{page:labPage.value,size:labSize.value,keyword:labKeyword.value}});labs.value=data.records||[];labTotal.value=data.total||0}catch{}}
async function loadAllLabs(){try{const{data}=await axios.get('/laboratories/list');allLabs.value=data||[]}catch{}}
async function loadRooms(){try{const{data}=await axios.get('/laboratories/rooms',{params:{page:roomPage.value,size:roomSize.value,laboratoryId:roomFilter.value,roomName:roomSearch.value}});rooms.value=data.records||[];roomTotal.value=data.total||0}catch{}}

function editLab(row){labForm.value={...row};labVisible.value=true}
async function saveLab(){try{if(labForm.value.id){await axios.put('/laboratories/'+labForm.value.id,labForm.value)}else{await axios.post('/laboratories',labForm.value)};ElMessage.success('已保存');labVisible.value=false;loadLabs();loadAllLabs()}catch(e){ElMessage.error(e?.response?.data?.msg||'失败')}}
async function deleteLab(id){try{await ElMessageBox.confirm('删除实验室将同时删除关联的地点映射，确认？');await axios.delete('/laboratories/'+id);loadLabs();loadAllLabs()}catch{}}

function editRoom(row){roomForm.value={...row};roomVisible.value=true}
async function saveRoom(){try{if(roomForm.value.id){await axios.put('/laboratories/rooms/'+roomForm.value.id,roomForm.value)}else{await axios.post('/laboratories/rooms',roomForm.value)};ElMessage.success('已保存');roomVisible.value=false;loadRooms()}catch(e){ElMessage.error(e?.response?.data?.msg||'失败')}}
async function deleteRoom(id){try{await ElMessageBox.confirm('确认删除？');await axios.delete('/laboratories/rooms/'+id);loadRooms()}catch{}}
async function syncDevices(){syncing.value=true;try{const{data}=await axios.post('/laboratories/sync-devices');ElMessage.success(data.msg||'同步完成，'+data.data+'台设备已更新')}catch(e){ElMessage.error('同步失败')}finally{syncing.value=false}}

onMounted(()=>{loadLabs();loadAllLabs();loadRooms()})
</script>
<style scoped>.lab{padding:20px}</style>
