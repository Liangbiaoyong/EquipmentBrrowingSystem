<template>
  <div class="repair"><h2>维修管理</h2>
    <el-card style="margin-bottom:15px">
      <el-radio-group v-model="deviceStatusFilter" @change="loadDevices">
        <el-radio-button :value="null">全部</el-radio-button>
        <el-radio-button :value="2">待维修</el-radio-button>
        <el-radio-button :value="3">无法维修</el-radio-button>
        <el-radio-button :value="4">待报废</el-radio-button>
      </el-radio-group>
      <el-button type="primary" style="margin-left:15px" @click="createVisible=true">+ 创建维修记录</el-button>
    </el-card>

    <el-card><el-table :data="devices" stripe v-loading="devLoading">
      <el-table-column prop="id" label="设备ID" width="80"/>
      <el-table-column label="设备名称" min-width="160"><template #default="{row}"><el-link type="primary" @click="$router.push('/devices/'+row.id)">{{ row.name }}</el-link></template></el-table-column>
      <el-table-column prop="assetNo" label="资产编号" width="130"/>
      <el-table-column prop="location" label="存放地" width="130"/>
      <el-table-column label="设备状态" width="100"><template #default="{row}"><el-tag :type="dsType(row.deviceStatus)">{{ dsText(row.deviceStatus) }}</el-tag></template></el-table-column>
      <el-table-column label="操作" width="250" fixed="right"><template #default="{row}">
        <el-button size="small" type="primary" @click="quickCreate(row)">创建记录</el-button>
        <el-button v-if="row.deviceStatus===2" size="small" type="warning" @click="startRepair(row.id)">开始维修</el-button>
        <el-button v-if="row.deviceStatus===3" size="small" type="success" @click="fixDevice(row.id)">修复完成</el-button>
      </template></el-table-column>
    </el-table>
    <div style="margin-top:12px;display:flex;justify-content:flex-end"><el-pagination v-model:current-page="devPage" :page-size="devSize" :total="devTotal" layout="prev,pager,next" @current-change="loadDevices"/></div></el-card>

    <el-card style="margin-top:15px" header="维修记录"><el-table :data="records" stripe v-loading="recLoading">
      <el-table-column prop="id" label="ID" width="70"/>
      <el-table-column label="设备" width="180"><template #default="{row}"><el-link type="primary" @click="$router.push('/devices/'+row.deviceId)">{{ getDeviceName(row.deviceId) }}</el-link></template></el-table-column>
      <el-table-column prop="faultDescription" label="故障描述" min-width="180"/>
      <el-table-column label="状态" width="90"><template #default="{row}"><el-tag :type="row.status==='PENDING'?'warning':row.status==='REPAIRING'?'danger':row.status==='FIXED'?'success':'info'">{{ {PENDING:'待维修',REPAIRING:'维修中',FIXED:'已修复',UNREPAIRABLE:'无法维修'}[row.status]||row.status }}</el-tag></template></el-table-column>
      <el-table-column prop="repairComment" label="备注" min-width="140"/><el-table-column prop="fixedTime" label="完成时间" width="150"/>
    </el-table>
    <div style="margin-top:12px;display:flex;justify-content:flex-end"><el-pagination v-model:current-page="recPage" :page-size="recSize" :total="recTotal" layout="prev,pager,next" @current-change="loadRecords"/></div></el-card>

    <el-dialog v-model="createVisible" title="创建维修记录" width="500px"><el-form :model="cf" label-width="100px">
      <el-form-item label="设备ID" required><el-input-number v-model="cf.deviceId" :min="1"/></el-form-item>
      <el-form-item label="关联借用单"><el-input-number v-model="cf.borrowId" :min="1" placeholder="可选"/></el-form-item>
      <el-form-item label="故障描述" required><el-input v-model="cf.faultDescription" type="textarea" :rows="3"/></el-form-item>
    </el-form><template #footer><el-button @click="createVisible=false">取消</el-button><el-button type="primary" @click="submitCreate">创建</el-button></template></el-dialog>

    <el-dialog v-model="commentVisible" title="备注" width="400px"><el-input v-model="commentText" type="textarea" :rows="2" placeholder="备注(可选)"/><template #footer><el-button @click="commentVisible=false">取消</el-button><el-button type="primary" @click="commentOk">确定</el-button></template></el-dialog>
  </div>
</template>
<script setup>
import { ref,onMounted } from 'vue';import { useRouter } from 'vue-router';import axios from '@/api/request';import { ElMessage,ElMessageBox } from 'element-plus'
const router=useRouter();const devices=ref([]);const records=ref([]);const devLoading=ref(false);const recLoading=ref(false)
const devPage=ref(1);const devSize=ref(20);const devTotal=ref(0);const recPage=ref(1);const recSize=ref(20);const recTotal=ref(0)
const deviceStatusFilter=ref(null)
const dsMap={1:'success',2:'warning',3:'danger',4:'info',5:'info'};const dsTxt={1:'正常',2:'待维修',3:'无法维修',4:'待报废',5:'已报废'}
function dsType(v){return dsMap[v]||'info'}function dsText(v){return dsTxt[v]||'未知'}
const createVisible=ref(false);const cf=ref({deviceId:null,borrowId:null,faultDescription:''})
const commentVisible=ref(false);const commentText=ref('');let pa=null
const deviceNameCache=ref({})
function getDeviceName(id){if(!id)return'';return deviceNameCache.value[id]||'设备#'+id}

async function loadDevices(){devLoading.value=true;try{const{data}=await axios.get('/repairs/devices',{params:{page:devPage.value,size:devSize.value,deviceStatus:deviceStatusFilter.value}});devices.value=data.records||[];devTotal.value=data.total||0;loadNames(data.records)}catch{}finally{devLoading.value=false}}
async function loadRecords(){recLoading.value=true;try{const{data}=await axios.get('/repairs',{params:{page:recPage.value,size:recSize.value}});records.value=data.records||[];recTotal.value=data.total||0;loadNames(data.records)}catch{}finally{recLoading.value=false}}
async function loadNames(list){if(!list)return;for(const r of list){const id=r.deviceId||r.id;if(id&&!deviceNameCache.value[id]){try{const{data}=await axios.get(`/devices/${id}`);deviceNameCache.value[id]=data?.name||data?.device?.name||('设备#'+id)}catch{deviceNameCache.value[id]='设备#'+id}}}}

async function submitCreate(){try{await axios.post('/repairs',null,{params:cf.value});ElMessage.success('已创建');createVisible.value=false;loadDevices();loadRecords()}catch(e){ElMessage.error(e?.response?.data?.msg||'失败')}}

function quickCreate(row){cf.value={deviceId:row.id,borrowId:null,faultDescription:''};createVisible.value=true}

function findRec(deviceId,status){return records.value.find(r=>r.deviceId===deviceId&&r.status===status)}
function startRepair(deviceId){ElMessageBox.confirm('确认开始维修？').then(async()=>{const rec=findRec(deviceId,'PENDING');if(rec){await axios.put(`/repairs/${rec.id}/start`);ElMessage.success('维修已开始');loadDevices();loadRecords()}else ElMessage.warning('未找到待维修记录')}).catch(()=>{})}

function fixDevice(deviceId){commentText.value='';commentVisible.value=true;pa=async()=>{const rec=findRec(deviceId,'REPAIRING');if(rec){await axios.put(`/repairs/${rec.id}/fix`,null,{params:{comment:commentText.value}});ElMessage.success('设备已恢复正常');loadDevices();loadRecords()}else ElMessage.warning('未找到维修中记录')}}

function commentOk(){commentVisible.value=false;if(pa)pa();pa=null}
onMounted(()=>{loadDevices();loadRecords()})
</script>
<style scoped>.repair{padding:20px}</style>
