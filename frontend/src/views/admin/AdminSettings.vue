<template>
  <div class="settings"><h2>系统配置</h2>
    <p style="color:#909399;margin-bottom:16px">运行时修改系统参数，修改后立即生效。删除配置项将恢复默认值。</p>

    <!-- 全部配置统一表格 -->
    <el-card shadow="never">
      <div style="margin-bottom:12px;display:flex;gap:8px">
        <el-input v-model="newKey" placeholder="配置键" size="small" style="width:220px"/>
        <el-input v-model="newVal" placeholder="值" size="small" style="width:180px"/>
        <el-input v-model="newDesc" placeholder="说明" size="small" style="width:200px"/>
        <el-button size="small" type="success" @click="addConfig">新增配置</el-button>
      </div>
      <el-table :data="allConfigs" stripe size="small">
        <el-table-column prop="configKey" label="配置键" width="280"/>
        <el-table-column label="当前值" width="280">
          <template #default="{row}">
            <el-input v-model="row.configValue" size="small" style="width:240px"/>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="说明" min-width="200"/>
        <el-table-column label="操作" width="160">
          <template #default="{row}"><div style="white-space:nowrap"><el-button size="small" type="primary" @click="saveRow(row)">保存</el-button><el-button size="small" type="danger" @click="deleteRow(row.configKey)">删除</el-button></div></template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref,onMounted } from 'vue'
import { adminApi } from '@/api/admin'
import { ElMessage,ElMessageBox } from 'element-plus'

const allConfigs = ref([])
const newKey=ref('');const newVal=ref('');const newDesc=ref('')

const defaults = [
  {configKey:'borrow.max_days',configValue:'7',description:'借用最大天数（超出拒绝）'},
  {configKey:'borrow.default_approval_steps',configValue:'2',description:'默认审批级数（1/2/3）'},
  {configKey:'cleanup.small_record_days',configValue:'15',description:'小记录保留天数（通知/日志/审批记录）'},
  {configKey:'cleanup.large_file_days',configValue:'30',description:'大文件保留天数（附件/借用归还图片）'},
  {configKey:'notification.unread_cleanup_days',configValue:'-1',description:'未读消息保留天数（-1=永久）'},
  {configKey:'notification.read_cleanup_days',configValue:'180',description:'已读消息保留天数（默认半年）'},
  {configKey:'data.record_retention_days',configValue:'-1',description:'数据库基础数据保留天数（-1=永久）'},
]

async function loadAll(){
  try{
    const{data}=await adminApi.getConfigs()
    const map={}
    // 确保默认配置始终显示
    defaults.forEach(d=>{map[d.configKey]={...d}})
    ;(data||[]).forEach(c=>{map[c.configKey]={configKey:c.configKey,configValue:c.configValue,description:c.description||''}})
    allConfigs.value=Object.values(map).sort((a,b)=>a.configKey.localeCompare(b.configKey))
  }catch{}
}

async function saveRow(row){await adminApi.setConfig(row.configKey,row.configValue,row.description);ElMessage.success('已保存')}

async function deleteRow(key){try{await ElMessageBox.confirm('删除此配置？将恢复默认值');await adminApi.deleteConfig(key);ElMessage.success('已删除');loadAll()}catch{}}

async function addConfig(){
  if(!newKey.value){ElMessage.warning('请输入配置键');return}
  await adminApi.setConfig(newKey.value,newVal.value,newDesc.value);ElMessage.success('已新增')
  newKey.value='';newVal.value='';newDesc.value='';loadAll()
}

onMounted(loadAll)
</script>
<style scoped>.settings{padding:20px;max-width:1000px;margin:0 auto}</style>
