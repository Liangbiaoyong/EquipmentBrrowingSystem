<template>
  <div class="settings"><h2>系统设置</h2>
    <el-card><el-table :data="list" v-loading="loading"><el-table-column prop="configKey" label="配置键" width="220"/><el-table-column prop="configValue" label="当前值"/><el-table-column prop="description" label="说明"/><el-table-column label="操作" width="100"><template #default="{row}"><el-button size="small" @click="edit(row)">修改</el-button></template></el-table-column></el-table></el-card>
    <el-dialog v-model="dialog.visible" title="修改配置" width="400px"><el-form label-width="80px"><el-form-item label="配置键"><el-input :model-value="dialog.key" disabled/></el-form-item><el-form-item label="新值"><el-input v-model="dialog.value"/></el-form-item><el-form-item label="说明"><el-input v-model="dialog.desc"/></el-form-item></el-form><template #footer><el-button @click="dialog.visible=false">取消</el-button><el-button type="primary" @click="doSave">保存</el-button></template></el-dialog>
  </div>
</template>
<script setup>
import { ref,reactive,onMounted } from 'vue';import { adminApi } from '@/api/admin';import { ElMessage } from 'element-plus'
const loading=ref(false);const list=ref([])
const dialog=reactive({visible:false,key:'',value:'',desc:''})
async function load(){loading.value=true;try{const{data}=await adminApi.getConfigs();list.value=data}catch{}finally{loading.value=false}}
function edit(row){dialog.key=row.configKey;dialog.value=row.configValue;dialog.desc=row.description||'';dialog.visible=true}
async function doSave(){try{await adminApi.setConfig(dialog.key,dialog.value,dialog.desc);ElMessage.success('已保存');dialog.visible=false;load()}catch{}}
onMounted(load)
</script>
<style scoped>.settings{padding:20px}</style>
