<template>
  <div class="device-list"><h2>设备列表</h2>
    <el-card class="search-card"><el-row :gutter="10">
      <el-col :span="6"><el-input v-model="q.assetNo" placeholder="资产编号" clearable @keyup.enter="search"/></el-col>
      <el-col :span="6"><el-input v-model="q.name" placeholder="名称" clearable @keyup.enter="search"/></el-col>
      <el-col :span="6"><el-input v-model="q.model" placeholder="型号" clearable @keyup.enter="search"/></el-col>
      <el-col :span="6"><el-select v-model="q.categoryId" placeholder="分类" clearable @change="search"><el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id"/></el-select></el-col>
    </el-row>
    <el-row :gutter="10" style="margin-top:10px">
      <el-col :span="6"><el-input v-model="q.gbCategoryName" placeholder="国标分类" clearable @keyup.enter="search"/></el-col>
      <el-col :span="6"><el-select v-model="q.laboratoryId" placeholder="所属实验室" clearable @change="search">
        <el-option v-for="lab in laboratories" :key="lab.id" :label="lab.name" :value="lab.id"/>
      </el-select></el-col>
      <el-col :span="4"><el-select v-model="q.borrowStatus" placeholder="借还状态" clearable @change="search">
        <el-option label="可借用" :value="1"/><el-option label="借用中" :value="2"/><el-option label="不可借" :value="3"/><el-option label="逾期" :value="4"/>
      </el-select></el-col>
      <el-col :span="4"><el-select v-model="q.deviceStatus" placeholder="设备状态" clearable @change="search">
        <el-option label="正常" :value="1"/><el-option label="待维修" :value="2"/><el-option label="维修中" :value="3"/><el-option label="待报废" :value="4"/><el-option label="已报废" :value="5"/>
      </el-select></el-col>
      <el-col :span="2"><el-button type="primary" @click="search">搜索</el-button></el-col>
      <el-col :span="2"><el-button @click="resetSearch">重置</el-button></el-col>
    </el-row></el-card>
    <el-card style="margin-top:15px">
      <el-table :data="list" v-loading="loading" stripe @row-click="toDetail" style="cursor:pointer">
        <el-table-column prop="assetNo" label="资产编号" width="140"/>
        <el-table-column prop="name" label="名称" min-width="160"/>
        <el-table-column prop="model" label="型号" min-width="120"/>
        <el-table-column label="分类" width="120"><template #default="{row}">{{ catName(row.categoryId) }}</template></el-table-column>
        <el-table-column label="使用人" width="90" prop="custodian"/>
        <el-table-column label="借还状态" width="90"><template #default="{row}"><el-tag :type="borrowStatusType(row.borrowStatus)">{{ borrowStatusText(row.borrowStatus) }}</el-tag></template></el-table-column>
        <el-table-column label="设备状态" width="90"><template #default="{row}"><el-tag :type="deviceStatusType(row.deviceStatus)">{{ deviceStatusText(row.deviceStatus) }}</el-tag></template></el-table-column>
        <el-table-column label="借用类型" width="100"><template #default="{row}"><el-tag :type="row.borrowType===1?'warning':''" effect="plain">{{ row.borrowType===1?'仅现场借用':'可借出' }}</el-tag></template></el-table-column>
        <el-table-column label="所属实验室" width="120"><template #default="{row}">{{ labName(row.laboratoryId) }}</template></el-table-column>
        <el-table-column prop="location" label="存放地" min-width="130" show-overflow-tooltip/>
      </el-table>
      <div style="margin-top:15px;display:flex;justify-content:flex-end"><el-pagination v-model:current-page="q.page" :page-size="q.size" :total="total" layout="total,prev,pager,next" @current-change="search"/></div></el-card>
  </div>
</template>
<script setup>
import { ref,reactive,onMounted } from 'vue';import { useRouter } from 'vue-router';import axios from '@/api/request';import { categoryApi } from '@/api/category'
const router=useRouter();const loading=ref(false);const list=ref([]);const total=ref(0);const categories=ref([]);const laboratories=ref([])
const q=reactive({page:1,size:20,assetNo:'',name:'',model:'',categoryId:null,gbCategoryName:'',location:'',borrowStatus:null,deviceStatus:null,borrowType:null,laboratoryId:null})

const borrowStatusMap={1:'success',2:'warning',3:'danger',4:'danger'}
const borrowStatusTextMap={1:'可借用',2:'借用中',3:'不可借',4:'逾期'}
const deviceStatusMap={1:'success',2:'warning',3:'danger',4:'info',5:'info'}
const deviceStatusTextMap={1:'正常',2:'待维修',3:'维修中',4:'待报废',5:'已报废'}

function borrowStatusType(v){return borrowStatusMap[v]||'info'}
function borrowStatusText(v){return borrowStatusTextMap[v]||'未知'}
function deviceStatusType(v){return deviceStatusMap[v]||'info'}
function deviceStatusText(v){return deviceStatusTextMap[v]||'未知'}
function catName(id){const c=categories.value.find(x=>x.id===id);return c?c.name:''}
function labName(id){if(!id)return'';const l=laboratories.value.find(x=>x.id===id);return l?l.name:''}
function toDetail(row){router.push(`/devices/${row.id}`)}
async function search(){loading.value=true;try{const{data}=await axios.get('/devices',{params:{page:q.page,size:q.size,assetNo:q.assetNo||undefined,name:q.name||undefined,model:q.model||undefined,categoryId:q.categoryId,gbCategoryName:q.gbCategoryName||undefined,location:q.location||undefined,borrowStatus:q.borrowStatus,deviceStatus:q.deviceStatus,borrowType:q.borrowType,laboratoryId:q.laboratoryId}});list.value=data.records||[];total.value=data.total||0}catch(e){console.error('搜索设备失败',e)}finally{loading.value=false}}
function resetSearch(){q.assetNo='';q.name='';q.model='';q.categoryId=null;q.gbCategoryName='';q.location='';q.borrowStatus=null;q.deviceStatus=null;q.borrowType=null;q.laboratoryId=null;search()}
onMounted(async()=>{try{const{data}=await categoryApi.topLevel();categories.value=data}catch{};try{const{data}=await axios.get('/laboratories/list');laboratories.value=data||[]}catch{};search()})
</script>
<style scoped>.device-list{padding:20px}</style>
