<template>
  <div class="scrap-page"><h2>报废管理</h2>
    <el-tabs v-model="tab">
      <el-tab-pane label="报废评估" name="devices">
        <!-- 筛选栏 -->
        <el-card shadow="never" style="margin-bottom:12px">
          <div style="display:flex;gap:10px;flex-wrap:wrap;align-items:center">
            <el-input v-model="keyword" placeholder="搜索名称/资产编号/国标分类" clearable style="width:240px" @keyup.enter="loadDevices"/>
            <el-select v-model="catFilter" placeholder="设备分类" clearable style="width:160px" @change="loadDevices">
              <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id"/>
            </el-select>
            <el-select v-model="eligibleFilter" placeholder="可报废状态" clearable style="width:140px" @change="loadDevices">
              <el-option label="可报废" :value="true"/><el-option label="未达年限" :value="false"/>
            </el-select>
            <el-button type="primary" @click="loadDevices">查询</el-button>
            <span style="margin-left:auto;color:#909399;font-size:13px">共 {{ total }} 台设备</span>
          </div>
        </el-card>

        <!-- 设备表格 -->
        <el-card shadow="never">
          <el-table :data="devices" stripe v-loading="loading" @sort-change="onSort">
            <el-table-column prop="id" label="ID" width="60" sortable="custom"/>
            <el-table-column prop="name" label="名称" min-width="140" show-overflow-tooltip/>
            <el-table-column prop="assetNo" label="资产编号" width="120"/>
            <el-table-column prop="gbCategoryName" label="国标分类" min-width="140" show-overflow-tooltip/>
            <el-table-column prop="purchaseDate" label="购置日期" width="110" sortable="custom"/>
            <el-table-column label="使用年限" width="90" sortable="custom"><template #default="{row}"><span :style="{color:row.yearsUsed>=row.minYears?'#67C23A':'#909399'}">{{ row.yearsUsed }}年</span></template></el-table-column>
            <el-table-column label="最低报废年限" width="120" sortable="custom"><template #default="{row}"><el-tag size="small" :type="row.minYears<=6?'success':row.minYears<=10?'warning':'danger'">{{ row.minYears }}年</el-tag><span style="font-size:11px;color:#909399;margin-left:4px">{{ row.ruleMatch }}</span></template></el-table-column>
            <el-table-column label="可报废" width="80"><template #default="{row}"><el-tag :type="row.scrapEligible?'success':'info'">{{ row.scrapEligible?'是':'否' }}</el-tag></template></el-table-column>
            <el-table-column label="操作" width="120" fixed="right"><template #default="{row}">
              <el-button v-if="row.scrapEligible" size="small" type="danger" @click="scrapDevice(row)">报废</el-button>
              <el-tooltip v-else :content="'还需'+row.remainingYears+'年'"><el-button size="small" disabled>报废</el-button></el-tooltip>
            </template></el-table-column>
          </el-table>
          <div style="margin-top:12px;display:flex;justify-content:flex-end">
            <el-pagination v-model:current-page="page" :page-size="size" :total="total" layout="total,prev,pager,next" @current-change="loadDevices"/>
          </div>
        </el-card>
      </el-tab-pane>

      <!-- 报废规则管理 -->
      <el-tab-pane label="报废规则" name="rules">
        <el-card shadow="never">
          <div style="margin-bottom:12px;display:flex;gap:8px">
            <el-button type="primary" @click="ruleForm={};ruleVisible=true">+ 新增规则</el-button>
            <span style="color:#909399;font-size:12px;margin-left:8px;align-self:center">规则按优先级排序，关键词与国标分类名包含匹配即生效</span>
          </div>
          <el-table :data="rules" stripe>
            <el-table-column prop="id" label="ID" width="60"/>
            <el-table-column prop="gbKeyword" label="国标关键词" min-width="180"/>
            <el-table-column prop="minYears" label="最低年限" width="90"><template #default="{row}"><el-tag :type="row.minYears<=6?'success':row.minYears<=10?'warning':'danger'">{{ row.minYears }}年</el-tag></template></el-table-column>
            <el-table-column prop="priority" label="优先级" width="80"/>
            <el-table-column prop="remark" label="说明" min-width="140"/>
            <el-table-column label="状态" width="70"><template #default="{row}"><el-tag :type="row.status===1?'success':'info'" size="small">{{ row.status===1?'启用':'禁用' }}</el-tag></template></el-table-column>
            <el-table-column label="操作" width="180" fixed="right"><template #default="{row}"><div style="white-space:nowrap"><el-button size="small" @click="editRule(row)">编辑</el-button><el-button size="small" @click="toggleRule(row)">{{ row.status===1?'禁用':'启用' }}</el-button><el-button size="small" type="danger" @click="deleteRule(row.id)">删除</el-button></div></template></el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- 规则编辑对话框 -->
    <el-dialog v-model="ruleVisible" :title="ruleForm.id?'编辑规则':'新增规则'" width="450px">
      <el-form :model="ruleForm" label-width="100px">
        <el-form-item label="国标关键词" required><el-input v-model="ruleForm.gbKeyword" placeholder="如: 计算机设备"/></el-form-item>
        <el-form-item label="最低年限" required><el-input-number v-model="ruleForm.minYears" :min="1" :max="50"/></el-form-item>
        <el-form-item label="优先级"><el-input-number v-model="ruleForm.priority" :min="1" :max="999"/></el-form-item>
        <el-form-item label="说明"><el-input v-model="ruleForm.remark"/></el-form-item>
      </el-form>
      <template #footer><el-button @click="ruleVisible=false">取消</el-button><el-button type="primary" @click="saveRule">保存</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref,onMounted } from 'vue'
import axios from '@/api/request'
import { ElMessage,ElMessageBox } from 'element-plus'

const tab = ref('devices')
const devices = ref([]); const loading = ref(false)
const page = ref(1); const size = ref(20); const total = ref(0)
const keyword = ref(''); const catFilter = ref(null); const eligibleFilter = ref(null)
const categories = ref([]); const sortBy = ref(''); const sortOrder = ref('desc')

const rules = ref([])
const ruleForm = ref({}); const ruleVisible = ref(false)

function onSort({prop,order}){sortBy.value=prop;sortOrder.value=order||'desc';loadDevices()}

async function loadDevices(){
  loading.value=true
  try{
    const{data}=await axios.get('/scrap/devices',{params:{page:page.value,size:size.value,keyword:keyword.value||undefined,categoryId:catFilter.value,sortBy:sortBy.value||undefined,order:sortOrder.value}})
    const records = data.records || []; total.value = data.total || 0
    devices.value = eligibleFilter.value === null ? records : eligibleFilter.value ? records.filter(r=>r.scrapEligible) : records.filter(r=>!r.scrapEligible)
  }catch(e){console.error(e)}finally{loading.value=false}
}

async function scrapDevice(row){
  try{
    const{value: remark} = await ElMessageBox.prompt('报废原因（可选）', '确认报废', {confirmButtonText:'确认报废',confirmButtonClass:'el-button--danger',inputPlaceholder:'报废原因'})
    await axios.put(`/scrap/${row.id}/confirm`,null,{params:{remark}})
    ElMessage.success('设备已报废')
    loadDevices()
  }catch(e){if(e!=='cancel')ElMessage.error(e?.response?.data?.msg||'报废失败')}
}

async function loadRules(){try{const{data}=await axios.get('/scrap/rules');rules.value=data||[]}catch{}}

function editRule(row){ruleForm.value={...row};ruleVisible.value=true}
async function saveRule(){try{if(ruleForm.value.id){await axios.put('/scrap/rules/'+ruleForm.value.id,ruleForm.value)}else{await axios.post('/scrap/rules',ruleForm.value)};ElMessage.success('已保存');ruleVisible.value=false;loadRules()}catch(e){ElMessage.error(e?.response?.data?.msg||'保存失败')}}
async function deleteRule(id){try{await ElMessageBox.confirm('确认删除？');await axios.delete('/scrap/rules/'+id);ElMessage.success('已删除');loadRules()}catch{}}
async function toggleRule(row){try{await axios.put('/scrap/rules/'+row.id+'/toggle');ElMessage.success('已切换');loadRules()}catch{}}

onMounted(async()=>{
  try{const{data}=await axios.get('/categories');categories.value=data||[]}catch{}
  loadDevices(); loadRules()
})
</script>

<style scoped>
.scrap-page{padding:20px;max-width:1280px;margin:0 auto}
</style>
