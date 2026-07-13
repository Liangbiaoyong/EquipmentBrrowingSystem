<template>
  <div class="overdue-page"><h2>逾期管理</h2>

    <!-- 逾期检测提示 -->
    <el-alert v-if="stats.overdueTotal===0" type="warning" :closable="false" show-icon style="margin-bottom:14px">
      <template #title>当前无逾期记录</template>
      点击「检测逾期」按钮扫描所有到期未归还的借用记录。系统每天凌晨3点自动检测。
      <el-button type="warning" size="small" style="margin-left:12px" @click="doRefresh" :loading="refreshing">立即检测逾期</el-button>
    </el-alert>

    <!-- 统计卡片 -->
    <div class="stats-row">
      <div class="stat-card s-red"><div class="s-num">{{ stats.overdueTotal }}</div><div class="s-label">当前逾期</div></div>
      <div class="stat-card s-orange"><div class="s-num">{{ stats.avgDays }}</div><div class="s-label">平均逾期天数</div></div>
      <div class="stat-card s-blue"><div class="s-num">{{ stats.notified }}</div><div class="s-label">已催还</div></div>
      <div class="stat-card s-green"><div class="s-num">{{ stats.collected }}</div><div class="s-label">已强制归还</div></div>
    </div>

    <!-- 筛选栏 -->
    <el-card shadow="never" style="margin-bottom:12px">
      <div class="filter-row">
        <el-input v-model="keyword" placeholder="搜索设备/借用人" clearable style="width:180px" @keyup.enter="load"/>
        <el-button type="primary" @click="load">查询</el-button>
        <el-button type="warning" @click="doRefresh" :loading="refreshing" style="margin-left:auto">检测逾期</el-button>
        <el-button type="danger" @click="batchNotify" :disabled="!selectedIds.length">批量催还({{selectedIds.length}})</el-button>
      </div>
    </el-card>

    <!-- 逾期列表 -->
    <el-card shadow="never">
      <el-table :data="list" stripe v-loading="loading" @selection-change="sel=>selectedIds=sel.map(r=>r.id)" @sort-change="onSort">
        <el-table-column type="selection" width="45"/>
        <el-table-column prop="id" label="单号" width="75" sortable="custom"/>
        <el-table-column label="设备" min-width="150"><template #default="{row}"><el-link type="primary" @click="$router.push('/devices/'+row.deviceId)">{{ getDN(row.deviceId) }}</el-link></template></el-table-column>
        <el-table-column label="借用人" width="100"><template #default="{row}">{{ getUN(row.userId) }}</template></el-table-column>
        <el-table-column prop="overdueDays" label="逾期天数" width="100" sortable="custom"><template #default="{row}"><el-tag :type="row.overdueDays>7?'danger':'warning'" size="large">{{ row.overdueDays||'即将' }}天</el-tag></template></el-table-column>
        <el-table-column prop="startTime" label="开始时间" width="140" sortable="custom"><template #default="{row}">{{ fmt(row.startTime) }}</template></el-table-column>
        <el-table-column prop="endTime" label="应归还" width="140" sortable="custom"><template #default="{row}">{{ fmt(row.endTime) }}</template></el-table-column>
        <el-table-column label="操作" width="280" fixed="right"><template #default="{row}">
          <div class="action-btns">
            <el-button size="small" type="primary" @click="doNotify(row)">催还</el-button>
            <el-button size="small" type="warning" @click="doReturn(row)">归还</el-button>
            <el-button size="small" type="danger" @click="doForceReturn(row)">强制归还</el-button>
          </div>
        </template></el-table-column>
      </el-table>
      <div v-if="!list.length&&!loading" style="text-align:center;padding:40px;color:#909399">暂无逾期记录，点击「检测逾期」扫描到期未还的借用</div>
      <div style="margin-top:12px;display:flex;justify-content:flex-end">
        <el-pagination v-model:current-page="page" :page-size="size" :total="total" layout="total,prev,pager,next" @current-change="load"/>
      </div>
    </el-card>

    <!-- 对话框 -->
    <el-dialog v-model="dlg.show" :title="dlg.force?'强制归还':'归还登记'" width="460px">
      <el-form label-width="80px"><el-form-item label="单号"><el-tag>{{ dlg.row?.id }}</el-tag></el-form-item>
        <el-form-item label="损坏情况"><el-input v-model="dlg.damage" type="textarea" :rows="2" placeholder="无损坏可不填"/></el-form-item>
        <el-form-item v-if="dlg.force" label="原因" required><el-input v-model="dlg.remark" type="textarea" :rows="2"/></el-form-item>
      </el-form>
      <template #footer><el-button @click="dlg.show=false">取消</el-button><el-button :type="dlg.force?'danger':'primary'" @click="submitDlg" :loading="dlg.loading">确认</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref,reactive,onMounted } from 'vue'
import axios from '@/api/request'
import { ElMessage } from 'element-plus'

const list=ref([]);const loading=ref(false);const page=ref(1);const size=ref(20);const total=ref(0)
const keyword=ref('');const selectedIds=ref([]);const refreshing=ref(false)
const nameCache=ref({});const stats=reactive({overdueTotal:0,avgDays:0,notified:0,collected:0})
const sortBy=ref('');const sortOrder=ref('desc')

const dlg=reactive({show:false,force:false,row:null,damage:'',remark:'',loading:false})

function fmt(t){return t?t.replace('T',' ').substring(0,16):''}
function getDN(id){return nameCache.value['d'+id]||'设备#'+id}
function getUN(id){return nameCache.value['u'+id]||'用户#'+id}
function onSort({prop,order}){sortBy.value=prop;sortOrder.value=order||'desc';load()}

async function load(){
  loading.value=true
  try{const{data}=await axios.get('/borrows/overdue',{params:{page:page.value,size:size.value,keyword:keyword.value||undefined,sort:sortBy.value||undefined,order:sortOrder.value}});list.value=data.records||[];total.value=data.total||0;await loadNames(data.records)}catch(e){console.error(e)}finally{loading.value=false}
}

async function loadNames(records){
  if(!records||!records.length)return
  // 批量收集需要查询的ID
  const deviceIds=[...new Set(records.map(r=>r.deviceId).filter(id=>id&&!nameCache.value['d'+id]))]
  const userIds=[...new Set(records.map(r=>r.userId).filter(id=>id&&!nameCache.value['u'+id]))]
  // 并行查询
  await Promise.all([
    ...deviceIds.map(async id=>{try{const{data}=await axios.get('/devices/'+id);nameCache.value['d'+id]=data?.name||data?.device?.name||('设备#'+id)}catch{}}),
    ...userIds.map(async id=>{try{const{data}=await axios.get('/admin/users/'+id);nameCache.value['u'+id]=data?.realName||data?.username||('用户#'+id)}catch{}})
  ])
}

async function loadStats(){try{const{data}=await axios.get('/borrows/overdue/stats');stats.overdueTotal=data.overdueTotal||0;stats.avgDays=Math.round((data.avgDays||0)*10)/10;stats.notified=data.notified||0;stats.collected=data.collected||0}catch{}}

async function doRefresh(){refreshing.value=true;try{const{data}=await axios.post('/borrows/overdue/refresh');ElMessage.success(`检测完成: 发现${data}条逾期记录`);load();loadStats()}catch(e){ElMessage.error('检测失败')}finally{refreshing.value=false}}

async function doNotify(row){try{await axios.post(`/borrows/${row.id}/overdue-notify`);ElMessage.success('催还通知已发送');load();loadStats()}catch(e){ElMessage.error(e?.response?.data?.msg||'失败')}}

function doReturn(row){dlg.row=row;dlg.damage='';dlg.remark='';dlg.force=false;dlg.show=true}
function doForceReturn(row){dlg.row=row;dlg.damage='';dlg.remark='';dlg.force=true;dlg.show=true}

async function submitDlg(){
  if(dlg.force&&!dlg.remark){ElMessage.warning('请填写强制归还原因');return}
  dlg.loading=true
  try{
    if(dlg.force){await axios.put(`/borrows/${dlg.row.id}/force-return`,null,{params:{damageReport:dlg.damage,remark:dlg.remark}})}
    else{await axios.post(`/borrows/${dlg.row.id}/return`,null,{params:{damageReport:dlg.damage}})}
    ElMessage.success(dlg.force?'强制归还完成':'归还成功');dlg.show=false;load();loadStats()
  }catch(e){ElMessage.error(e?.response?.data?.msg||'操作失败')}finally{dlg.loading=false}
}

async function batchNotify(){for(const id of selectedIds.value){try{await axios.post(`/borrows/${id}/overdue-notify`)}catch{}}ElMessage.success('批量催还完成');load();loadStats()}

onMounted(()=>{load();loadStats()})
</script>

<style scoped>
.overdue-page{padding:20px;max-width:1200px;margin:0 auto}
.stats-row{display:grid;grid-template-columns:repeat(4,1fr);gap:12px;margin-bottom:16px}
.stat-card{background:#fff;padding:18px 20px;border-radius:10px;text-align:center;box-shadow:0 1px 6px rgba(0,0,0,0.06);border-top:3px solid;cursor:default;transition:transform 0.15s,box-shadow 0.15s}
.stat-card:hover{transform:translateY(-2px);box-shadow:0 4px 12px rgba(0,0,0,0.1)}
.s-num{font-size:28px;font-weight:700}.s-label{font-size:12px;color:#909399;margin-top:4px}
.s-red{border-color:#F56C6C}.s-red .s-num{color:#F56C6C}.s-orange{border-color:#E6A23C}.s-orange .s-num{color:#E6A23C}
.s-blue{border-color:#409EFF}.s-blue .s-num{color:#409EFF}.s-green{border-color:#67C23A}.s-green .s-num{color:#67C23A}
.filter-row{display:flex;gap:8px;align-items:center;flex-wrap:wrap}
.action-btns{display:flex;gap:4px}
@media(max-width:768px){.stats-row{grid-template-columns:repeat(2,1fr)}}
</style>
