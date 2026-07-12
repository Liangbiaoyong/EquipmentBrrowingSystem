<template>
  <div class="settings"><h2>系统设置</h2>
    <p style="color:#909399;margin-bottom:16px">运行时修改系统参数，修改后立即生效。删除配置项将恢复默认值。</p>

    <!-- 借用规则 -->
    <el-card shadow="never" header="借用规则" style="margin-bottom:14px">
      <div class="cfg-grid">
        <div class="cfg-item" v-for="c in borrowConfigs" :key="c.key">
          <div class="cfg-label">{{ c.label }}</div>
          <div style="display:flex;align-items:center;gap:6px">
            <el-input v-model="c.value" size="small" style="width:120px"/>
            <span class="cfg-unit">{{ c.unit }}</span>
            <el-button size="small" type="primary" @click="saveCfg(c)">保存</el-button>
            <el-button size="small" @click="resetCfg(c)">重置</el-button>
          </div>
          <div class="cfg-desc">{{ c.desc }}</div>
        </div>
      </div>
    </el-card>

    <!-- 审批规则 -->
    <el-card shadow="never" header="审批规则" style="margin-bottom:14px">
      <div class="cfg-grid">
        <div class="cfg-item" v-for="c in approvalConfigs" :key="c.key">
          <div class="cfg-label">{{ c.label }}</div>
          <div style="display:flex;align-items:center;gap:6px">
            <el-select v-if="c.type==='select'" v-model="c.value" size="small" style="width:120px">
              <el-option v-for="o in c.options" :key="o.value" :label="o.label" :value="o.value"/>
            </el-select>
            <el-input v-else v-model="c.value" size="small" style="width:120px"/>
            <span class="cfg-unit">{{ c.unit }}</span>
            <el-button size="small" type="primary" @click="saveCfg(c)">保存</el-button>
            <el-button size="small" @click="resetCfg(c)">重置</el-button>
          </div>
          <div class="cfg-desc">{{ c.desc }}</div>
        </div>
      </div>
    </el-card>

    <!-- 清理规则 -->
    <el-card shadow="never" header="定时清理规则" style="margin-bottom:14px">
      <div class="cfg-grid">
        <div class="cfg-item" v-for="c in cleanupConfigs" :key="c.key">
          <div class="cfg-label">{{ c.label }}</div>
          <div style="display:flex;align-items:center;gap:6px">
            <el-input v-model="c.value" size="small" style="width:120px"/>
            <span class="cfg-unit">{{ c.unit }}</span>
            <el-button size="small" type="primary" @click="saveCfg(c)">保存</el-button>
            <el-button size="small" @click="resetCfg(c)">重置</el-button>
          </div>
          <div class="cfg-desc">{{ c.desc }}</div>
        </div>
      </div>
    </el-card>

    <!-- 自定义配置 -->
    <el-card shadow="never" header="自定义配置">
      <div style="margin-bottom:12px;display:flex;gap:8px">
        <el-input v-model="newKey" placeholder="配置键" size="small" style="width:200px"/>
        <el-input v-model="newVal" placeholder="值" size="small" style="width:200px"/>
        <el-input v-model="newDesc" placeholder="说明(可选)" size="small" style="width:200px"/>
        <el-button size="small" type="success" @click="addCustom">新增</el-button>
      </div>
      <el-table :data="customConfigs" stripe size="small">
        <el-table-column prop="configKey" label="配置键" width="220"/>
        <el-table-column label="当前值" width="200"><template #default="{row}"><el-input v-model="row.configValue" size="small" style="width:160px"/></template></el-table-column>
        <el-table-column prop="description" label="说明" min-width="200"/>
        <el-table-column label="操作" width="160"><template #default="{row}"><div style="white-space:nowrap"><el-button size="small" type="primary" @click="saveCfgRaw(row)">保存</el-button><el-button size="small" type="danger" @click="deleteCfg(row.configKey)">删除</el-button></div></template></el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref,onMounted } from 'vue'
import { adminApi } from '@/api/admin'
import { ElMessage } from 'element-plus'

// 预定义配置项
const borrowConfigs = ref([
  {key:'borrow.max_days',label:'最大借用天数',value:'7',unit:'天',desc:'超出此天数的借用申请将被拒绝',original:'7'},
  {key:'borrow.overdue_fine_per_day',label:'逾期罚款(每天)',value:'0',unit:'元',desc:'0表示不罚款',original:'0'}
])

const approvalConfigs = ref([
  {key:'borrow.default_approval_steps',label:'默认审批级数',value:'2',unit:'级',desc:'1=仅初审 2=初审+终审 3=三级审批',type:'select',options:[{label:'1级(仅初审)',value:'1'},{label:'2级(默认)',value:'2'},{label:'3级(三级)',value:'3'}],original:'2'}
])

const cleanupConfigs = ref([
  {key:'cleanup.small_record_days',label:'小记录保留天数',value:'15',unit:'天',desc:'通知/操作日志/审批记录的最大保留时间',original:'15'},
  {key:'cleanup.large_file_days',label:'大文件保留天数',value:'30',unit:'天',desc:'借用归还图片等附件最大保留时间',original:'30'}
])

const customConfigs = ref([])
const newKey = ref(''); const newVal = ref(''); const newDesc = ref('')

async function loadAll(){
  try{
    const{data}=await adminApi.getConfigs()
    const map={}; (data||[]).forEach(c=>{map[c.configKey]=c})
    // 回填预定义配置
    for(const list of [borrowConfigs,approvalConfigs,cleanupConfigs]){
      for(const c of list.value){
        if(map[c.key]){ c.value = map[c.key].configValue; delete map[c.key] }
      }
    }
    // 剩余的作为自定义配置
    customConfigs.value = Object.values(map).filter(c=>c.configKey)
  }catch{}
}

async function saveCfg(c){await adminApi.setConfig(c.key,c.value,c.desc);ElMessage.success('已保存')}

function resetCfg(c){c.value=c.original;saveCfg(c)}

async function saveCfgRaw(row){await adminApi.setConfig(row.configKey,row.configValue,row.description);ElMessage.success('已保存')}

async function deleteCfg(key){try{await adminApi.deleteConfig(key);ElMessage.success('已删除，恢复默认值');loadAll()}catch{}}

async function addCustom(){
  if(!newKey.value){ElMessage.warning('请输入配置键');return}
  await adminApi.setConfig(newKey.value,newVal.value,newDesc.value);ElMessage.success('已新增');newKey.value='';newVal.value='';newDesc.value='';loadAll()
}

onMounted(loadAll)
</script>

<style scoped>
.settings{padding:20px;max-width:900px;margin:0 auto}
.cfg-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(340px,1fr));gap:14px}
.cfg-item{background:#F8FAFC;padding:12px 16px;border-radius:8px}
.cfg-label{font-size:14px;font-weight:600;color:#303133;margin-bottom:6px}
.cfg-unit{font-size:12px;color:#909399}
.cfg-desc{font-size:11px;color:#C0C4CC;margin-top:4px}
</style>
