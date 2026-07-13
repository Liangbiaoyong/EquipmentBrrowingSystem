<template>
  <div class="browse-page"><h2 class="page-title">借用浏览</h2>

    <!-- 统计卡片 -->
    <div class="kpi-row">
      <div class="kpi kpi-total" @click="statusFilter='';load()">
        <div class="kpi-bar"></div>
        <div class="kpi-body"><div class="kpi-num">{{ stats.total }}</div><div class="kpi-label">总借用</div></div>
      </div>
      <div class="kpi kpi-active" @click="statusFilter='BORROWING';load()">
        <div class="kpi-bar"></div>
        <div class="kpi-body"><div class="kpi-num">{{ stats.borrowing }}</div><div class="kpi-label">借用中</div></div>
      </div>
      <div class="kpi kpi-overdue" @click="statusFilter='OVERDUE';load()">
        <div class="kpi-bar"></div>
        <div class="kpi-body"><div class="kpi-num">{{ stats.overdue }}</div><div class="kpi-label">逾期</div></div>
      </div>
      <div class="kpi kpi-pending" @click="statusFilter='PENDING_APPROVAL';load()">
        <div class="kpi-bar"></div>
        <div class="kpi-body"><div class="kpi-num">{{ stats.pending }}</div><div class="kpi-label">待审批</div></div>
      </div>
    </div>

    <!-- 筛选栏 -->
    <div class="filter-bar">
      <el-input v-model="keyword" placeholder="搜索设备名称/借用人" clearable style="width:220px" @keyup.enter="load">
        <template #prefix><el-icon><Search/></el-icon></template>
      </el-input>
      <el-select v-model="statusFilter" placeholder="全部状态" clearable style="width:140px" @change="load">
        <el-option v-for="s in statusOptions" :key="s.value" :label="s.label" :value="s.value"/>
      </el-select>
      <el-date-picker v-model="dates" type="daterange" range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期" format="YYYY-MM-DD" value-format="YYYY-MM-DD" style="width:250px" @change="load"/>
      <el-button type="primary" @click="load">查询</el-button>
      <el-button @click="keyword='';statusFilter='';dates=[];load()">重置</el-button>
      <el-dropdown @command="doExport" style="margin-left:auto">
        <el-button>导出报表<el-icon><ArrowDown/></el-icon></el-button>
        <template #dropdown><el-dropdown-menu><el-dropdown-item command="csv">CSV 格式</el-dropdown-item><el-dropdown-item command="xlsx">Excel 格式</el-dropdown-item></el-dropdown-menu></template>
      </el-dropdown>
    </div>

    <!-- 数据表格 -->
    <el-card shadow="never" class="table-card">
      <el-table :data="list" stripe v-loading="loading" @sort-change="onSort" @row-click="(row)=>$router.push('/devices/'+row.deviceId)" style="cursor:pointer">
        <el-table-column prop="id" label="单号" width="75" sortable="custom"/>
        <el-table-column label="设备" min-width="160">
          <template #default="{row}">
            <div class="device-cell">
              <span class="d-name">{{ row.deviceName || '设备#'+row.deviceId }}</span>
              <span class="d-asset">{{ row.deviceAssetNo }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="借用人" width="110" prop="userName">
          <template #default="{row}">{{ row.userName || '用户#'+row.userId }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{row}">
            <el-tag :type="statusTag(row.status)" size="small" effect="dark">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="开始时间" width="145" sortable="custom" prop="startTime">
          <template #default="{row}">{{ fmt(row.startTime) }}</template>
        </el-table-column>
        <el-table-column label="结束时间" width="145" sortable="custom" prop="endTime">
          <template #default="{row}">{{ fmt(row.endTime) }}</template>
        </el-table-column>
        <el-table-column label="逾期" width="70" sortable="custom" prop="overdueDays">
          <template #default="{row}">
            <span v-if="row.overdueDays" class="overdue-badge">{{ row.overdueDays }}天</span>
            <span v-else style="color:#C0C4CC">-</span>
          </template>
        </el-table-column>
        <el-table-column label="目的" min-width="140" show-overflow-tooltip prop="purpose"/>
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{row}">
            <div class="action-btns" @click.stop>
              <el-button v-if="row.status==='PENDING_APPROVAL'" size="small" type="danger" plain @click="cancelBorrow(row.id)">取消</el-button>
              <el-button v-if="row.status==='BORROWING'||row.status==='OVERDUE'" size="small" type="warning" plain @click="openReturn(row)">归还</el-button>
              <el-button v-if="row.status==='OVERDUE'" size="small" type="danger" plain @click="openForce(row)">强制归还</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrap">
        <el-pagination v-model:current-page="page" :page-size="size" :total="total" layout="total,prev,pager,next" @current-change="load"/>
      </div>
    </el-card>

    <!-- 归还对话框 -->
    <el-dialog v-model="returnDlg.show" title="归还登记" width="440px">
      <el-form label-width="80px">
        <el-form-item label="单号"><el-tag>{{ returnDlg.id }}</el-tag></el-form-item>
        <el-form-item label="损坏情况"><el-input v-model="returnDlg.damage" type="textarea" :rows="2" placeholder="无损坏可不填"/></el-form-item>
      </el-form>
      <template #footer><el-button @click="returnDlg.show=false">取消</el-button><el-button type="primary" @click="submitReturn" :loading="returnDlg.loading">确认归还</el-button></template>
    </el-dialog>

    <!-- 强制归还对话框 -->
    <el-dialog v-model="forceDlg.show" title="强制归还" width="440px">
      <el-alert type="warning" :closable="false" show-icon style="margin-bottom:12px">管理员代为操作，设备状态立即变更为已归还</el-alert>
      <el-form label-width="80px">
        <el-form-item label="单号"><el-tag>{{ forceDlg.id }}</el-tag></el-form-item>
        <el-form-item label="原因" required><el-input v-model="forceDlg.remark" type="textarea" :rows="2" placeholder="强制归还原因"/></el-form-item>
      </el-form>
      <template #footer><el-button @click="forceDlg.show=false">取消</el-button><el-button type="danger" @click="submitForce" :loading="forceDlg.loading">确认强制归还</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref,reactive,onMounted } from 'vue'
import axios from '@/api/request'
import { ElMessage,ElMessageBox } from 'element-plus'
import { Search,ArrowDown } from '@element-plus/icons-vue'

const list=ref([]);const loading=ref(false)
const page=ref(1);const size=ref(20);const total=ref(0)
const keyword=ref('');const statusFilter=ref(null);const dates=ref([])
const sortBy=ref('');const sortOrder=ref('desc')
const stats=reactive({total:0,borrowing:0,overdue:0,pending:0})

const statusOptions=[
  {label:'待审批',value:'PENDING_APPROVAL'},{label:'已通过',value:'APPROVED'},{label:'已驳回',value:'REJECTED'},
  {label:'借用中',value:'BORROWING'},{label:'已归还',value:'RETURNED'},{label:'逾期',value:'OVERDUE'},{label:'已取消',value:'CANCELLED'}
]

const statusTagMap={PENDING_APPROVAL:'warning',APPROVED:'success',REJECTED:'danger',BORROWING:'',RETURNED:'info',OVERDUE:'danger',CANCELLED:'info'}
const statusLabelMap={PENDING_APPROVAL:'待审批',APPROVED:'已通过',REJECTED:'已驳回',BORROWING:'借用中',RETURNED:'已归还',OVERDUE:'逾期',CANCELLED:'已取消'}
function statusTag(s){return statusTagMap[s]||'info'}
function statusLabel(s){return statusLabelMap[s]||s}
function fmt(t){return t?t.replace('T',' ').substring(0,16):''}
function onSort({prop,order}){sortBy.value=prop;sortOrder.value=order||'desc';load()}

const returnDlg=reactive({show:false,id:null,damage:'',loading:false})
const forceDlg=reactive({show:false,id:null,remark:'',loading:false})

function openReturn(row){returnDlg.id=row.id;returnDlg.damage='';returnDlg.show=true}
function openForce(row){forceDlg.id=row.id;forceDlg.remark='';forceDlg.show=true}

async function submitReturn(){
  returnDlg.loading=true
  try{await axios.post(`/borrows/${returnDlg.id}/return`,null,{params:{damageReport:returnDlg.damage||''}});ElMessage.success('归还成功');returnDlg.show=false;load();loadStats()}
  catch(e){ElMessage.error(e?.response?.data?.msg||'归还失败')}finally{returnDlg.loading=false}
}

async function submitForce(){
  if(!forceDlg.remark){ElMessage.warning('请填写原因');return}
  forceDlg.loading=true
  try{await axios.put(`/borrows/${forceDlg.id}/force-return`,null,{params:{remark:forceDlg.remark}});ElMessage.success('强制归还完成');forceDlg.show=false;load();loadStats()}
  catch(e){ElMessage.error(e?.response?.data?.msg||'操作失败')}finally{forceDlg.loading=false}
}

async function cancelBorrow(id){
  try{await ElMessageBox.confirm('确认取消此申请？');await axios.post(`/borrows/${id}/cancel`);ElMessage.success('已取消');load()}catch{}
}

async function load(){
  loading.value=true
  try{
    const p={page:page.value,size:size.value,keyword:keyword.value||undefined,status:statusFilter.value,sort:sortBy.value||undefined,order:sortOrder.value}
    if(dates.value?.length===2){p.startDate=dates.value[0];p.endDate=dates.value[1]}
    const{data}=await axios.get('/borrows/browse',{params:p})
    list.value=data.records||[];total.value=data.total||0
  }catch(e){console.error(e)}finally{loading.value=false}
}

async function loadStats(){
  try{
    const{data}=await axios.get('/statistics/overview')
    const bs=data.borrowStats||{}
    stats.total=bs.total||0;stats.borrowing=bs.borrowing||0
    stats.overdue=bs.overdue||0;stats.pending=bs.pendingApproval||0
  }catch{}
}

function doExport(format){
  const ext=format==='xlsx'?'xlsx':'csv'
  const mime=ext==='xlsx'?'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet':'text/csv'
  axios.get('/borrows/browse/export',{params:{format,keyword:keyword.value||undefined,status:statusFilter.value},responseType:'blob'}).then(r=>{
    const blob=new Blob([r.data],{type:mime});const a=document.createElement('a')
    a.href=URL.createObjectURL(blob);a.download=`借用浏览_${new Date().toISOString().slice(0,10)}.${ext}`;a.click()
  }).catch(e=>ElMessage.error('导出失败'))
}

onMounted(()=>{load();loadStats()})
</script>

<style scoped>
.browse-page{padding:24px;max-width:1340px;margin:0 auto}
.page-title{font-size:20px;font-weight:600;color:#303133;margin:0 0 18px 0}

/* KPI */
.kpi-row{display:grid;grid-template-columns:repeat(4,1fr);gap:14px;margin-bottom:16px}
.kpi{display:flex;background:#fff;border-radius:10px;overflow:hidden;box-shadow:0 1px 6px rgba(0,0,0,0.05);cursor:pointer;transition:transform 0.15s,box-shadow 0.15s}
.kpi:hover{transform:translateY(-2px);box-shadow:0 4px 12px rgba(0,0,0,0.1)}
.kpi-bar{width:4px;flex-shrink:0}
.kpi-total .kpi-bar{background:#409EFF}.kpi-active .kpi-bar{background:#67C23A}
.kpi-overdue .kpi-bar{background:#F56C6C}.kpi-pending .kpi-bar{background:#E6A23C}
.kpi-body{padding:16px 20px}.kpi-num{font-size:26px;font-weight:700;color:#303133;line-height:1.2}
.kpi-label{font-size:12px;color:#909399;margin-top:2px}
.kpi-total .kpi-num{color:#409EFF}.kpi-active .kpi-num{color:#67C23A}
.kpi-overdue .kpi-num{color:#F56C6C}.kpi-pending .kpi-num{color:#E6A23C}

/* 筛选栏 */
.filter-bar{display:flex;gap:10px;align-items:center;margin-bottom:14px;flex-wrap:wrap;padding:12px 16px;background:#fff;border-radius:8px;box-shadow:0 1px 4px rgba(0,0,0,0.04)}

/* 表格 */
.table-card{border-radius:10px;overflow:hidden}
.device-cell{display:flex;flex-direction:column}
.d-name{font-size:13px;color:#303133}.d-asset{font-size:11px;color:#909399}
.overdue-badge{color:#F56C6C;font-weight:600;font-size:13px}
.action-btns{display:flex;gap:4px}
.pagination-wrap{display:flex;justify-content:flex-end;margin-top:14px}

@media(max-width:768px){.kpi-row{grid-template-columns:repeat(2,1fr)}}
</style>
