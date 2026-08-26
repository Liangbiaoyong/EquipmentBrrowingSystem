<template>
  <div class="missing"><h2>缺少图片的设备</h2>
    <el-card>
      <div style="margin-bottom:12px;display:flex;gap:8px">
        <el-input v-model="keyword" placeholder="搜索名称/资产编号/型号/存放地" clearable style="width:280px" @keyup.enter="search"/>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="keyword='';search()">重置</el-button>
      </div>
      <el-table :data="list" v-loading="loading">
        <el-table-column prop="id" label="设备ID" width="75"/>
        <el-table-column prop="assetNo" label="资产编号" width="140"/>
        <el-table-column prop="name" label="名称"/>
        <el-table-column prop="model" label="型号" min-width="120"/>
        <el-table-column prop="location" label="存放地" min-width="120"/>
        <el-table-column label="操作" width="120"><template #default="{row}"><el-upload :show-file-list="false" :before-upload="(f)=>{doUpload(row.id,f);return false}" accept="image/*"><el-button size="small" type="primary">上传图片</el-button></el-upload></template></el-table-column>
      </el-table>
      <div style="margin-top:15px;display:flex;justify-content:flex-end">
        <el-pagination v-model:current-page="page" v-model:page-size="size" :page-sizes="[20,50,100,500]" :total="total" layout="total,sizes,prev,pager,next,jumper" @current-change="load" @size-change="s=>{size=s;page=1;load()}"/>
      </div>
    </el-card>
  </div>
</template>
<script setup>
import { ref,onMounted } from 'vue';import { deviceApi } from '@/api/device';import { ElMessage } from 'element-plus'
const loading=ref(false);const list=ref([]);const total=ref(0);const page=ref(1);const size=ref(20);const keyword=ref('')
function search(){page.value=1;load()}
async function load(){loading.value=true;try{const{data}=await deviceApi.missingImages({page:page.value,size:size.value,keyword:keyword.value||undefined});list.value=data.records||[];total.value=data.total||0}catch{}finally{loading.value=false}}
async function doUpload(deviceId,file){try{await deviceApi.uploadImage(deviceId,file);ElMessage.success('上传成功');search()}catch{}}
onMounted(load)
</script>
<style scoped>.missing{padding:20px}</style>
