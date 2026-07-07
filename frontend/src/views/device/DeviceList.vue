<template>
  <div class="device-list">
    <h2>设备列表</h2>
    <el-card class="search-card"><el-row :gutter="10"><el-col :span="6"><el-input v-model="q.keyword" placeholder="名称/编号/型号" clearable @keyup.enter="search"/></el-col><el-col :span="4"><el-select v-model="q.categoryId" placeholder="分类" clearable @change="search"><el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id"/></el-select></el-col><el-col :span="3"><el-select v-model="q.status" placeholder="状态" clearable @change="search"><el-option label="正常" :value="1"/><el-option label="维修中" :value="2"/><el-option label="报废" :value="3"/></el-select></el-col><el-col :span="3"><el-button type="primary" @click="search">搜索</el-button></el-col></el-row></el-card>
    <el-card style="margin-top:15px">
      <el-table :data="list" v-loading="loading" stripe @row-click="toDetail" style="cursor:pointer">
        <el-table-column prop="assetNo" label="资产编号" width="140"/><el-table-column prop="name" label="名称" min-width="160"/><el-table-column prop="model" label="型号" min-width="120"/><el-table-column label="分类" width="120"><template #default="{row}">{{ catName(row.categoryId) }}</template></el-table-column><el-table-column label="状态" width="90"><template #default="{row}"><el-tag :type="st(row.status)">{{ stx(row.status) }}</el-tag></template></el-table-column><el-table-column prop="location" label="存放地" min-width="150" show-overflow-tooltip/>
      </el-table>
      <div style="margin-top:15px;display:flex;justify-content:flex-end"><el-pagination v-model:current-page="q.page" :page-size="q.size" :total="total" layout="total,prev,pager,next" @current-change="search"/></div>
    </el-card>
  </div>
</template>
<script setup>
import { ref,reactive,onMounted } from 'vue';import { useRouter } from 'vue-router';import { deviceApi } from '@/api/device';import { categoryApi } from '@/api/category'
const router=useRouter();const loading=ref(false);const list=ref([]);const total=ref(0);const categories=ref([])
const q=reactive({page:1,size:20,keyword:'',categoryId:null,status:null})
const sm={1:'success',2:'warning',3:'danger'};const sx={1:'正常',2:'维修中',3:'报废'}
function st(v){return sm[v]||'info'}function stx(v){return sx[v]||'未知'}function catName(id){const c=categories.value.find(x=>x.id===id);return c?c.name:''}function toDetail(row){router.push(`/devices/${row.id}`)}
async function search(){loading.value=true;try{const{data}=await deviceApi.list({...q});list.value=data.records;total.value=data.total}catch{}finally{loading.value=false}}
onMounted(async()=>{try{const{data}=await categoryApi.topLevel();categories.value=data}catch{};search()})
</script>
<style scoped>.device-list{padding:20px}</style>
