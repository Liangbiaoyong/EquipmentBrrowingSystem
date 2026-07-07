<template>
  <div class="missing"><h2>缺少图片的设备</h2>
    <el-card><el-table :data="list" v-loading="loading"><el-table-column prop="assetNo" label="资产编号" width="140"/><el-table-column prop="name" label="名称"/><el-table-column label="操作" width="120"><template #default="{row}"><el-upload :show-file-list="false" :before-upload="(f)=>{doUpload(row.id,f);return false}" accept="image/*"><el-button size="small" type="primary">上传图片</el-button></el-upload></template></el-table-column></el-table></el-card>
  </div>
</template>
<script setup>
import { ref,onMounted } from 'vue';import { deviceApi } from '@/api/device';import { ElMessage } from 'element-plus'
const loading=ref(false);const list=ref([])
async function load(){loading.value=true;try{const{data}=await deviceApi.missingImages({page:1,size:100});list.value=data}catch{}finally{loading.value=false}}
async function doUpload(deviceId,file){try{await deviceApi.uploadImage(deviceId,file);ElMessage.success('上传成功');load()}catch{}}
onMounted(load)
</script>
<style scoped>.missing{padding:20px}</style>
