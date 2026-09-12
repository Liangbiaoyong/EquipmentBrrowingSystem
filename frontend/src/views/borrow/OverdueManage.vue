<template>
  <div class="overdue-page"><h2>逾期管理</h2>

    <!-- 统计卡片 -->
    <div class="stats-row">
      <div class="stat-card s-red"><div class="s-num">{{ stats.overdueTotal }}</div><div class="s-label">当前逾期</div></div>
      <div class="stat-card s-orange"><div class="s-num">{{ stats.avgDays }}</div><div class="s-label">平均逾期天数</div></div>
      <div class="stat-card s-blue"><div class="s-num">{{ stats.notified }}</div><div class="s-label">催还中</div></div>
      <div class="stat-card s-green"><div class="s-num">{{ stats.collected }}</div><div class="s-label">已强制归还</div></div>
    </div>

    <!-- 筛选栏 -->
    <el-card shadow="never" style="margin-bottom:12px">
      <div class="filter-row">
        <el-input v-model="keyword" placeholder="搜索设备/借用人" clearable style="width:180px" @keyup.enter="load"/>
        <el-button type="primary" @click="load">查询</el-button>
        <el-button type="danger" @click="batchNotify" :disabled="!selectedIds.length" style="margin-left:auto">批量催还({{selectedIds.length}})</el-button>
      </div>
    </el-card>

    <!-- 逾期列表 -->
    <el-card shadow="never">
      <el-table :data="list" stripe v-loading="loading" @selection-change="sel=>selectedIds=sel.map(r=>r.id)" @sort-change="onSort">
        <el-table-column type="selection" width="45"/>
        <el-table-column prop="id" label="单号" width="75" sortable="custom"/>
        <el-table-column label="设备" min-width="150"><template #default="{row}">
          <div>
            <el-link type="primary" @click="$router.push('/devices/'+row.deviceId)">{{ row.deviceName || '设备#'+row.deviceId }}</el-link>
            <span v-if="row.deviceAssetNo" style="font-size:11px;color:#909399;margin-left:6px">{{ row.deviceAssetNo }}</span>
          </div>
        </template></el-table-column>
        <el-table-column label="借用人" width="100"><template #default="{row}">{{ row.userName || '用户#'+row.userId }}</template></el-table-column>
        <el-table-column prop="overdueDays" label="逾期天数" width="100" sortable="custom"><template #default="{row}"><el-tag :type="row.overdueDays>7?'danger':'warning'" size="large">{{ row.overdueDays }}天</el-tag></template></el-table-column>
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
      <div v-if="!list.length&&!loading" style="text-align:center;padding:40px;color:#909399">暂无逾期记录</div>
      <div style="margin-top:12px;display:flex;justify-content:flex-end">
        <el-pagination v-model:current-page="page" v-model:page-size="size" :page-sizes="[20,100,500]" :total="total" layout="total,sizes,prev,pager,next,jumper" @current-change="load" @size-change="s=>{size=s;page=1;load()}"/>
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
const keyword=ref('');const selectedIds=ref([])
const stats=reactive({overdueTotal:0,avgDays:0,notified:0,collected:0})
const sortBy=ref('');const sortOrder=ref('')

const dlg=reactive({show:false,force:false,row:null,damage:'',remark:'',loading:false})

function fmt(t){return t?t.replace('T',' ').substring(0,16):''}
function onSort({prop,order}){sortBy.value=order?prop:'';sortOrder.value=order==='ascending'?'asc':order==='descending'?'desc':'';load()}

async function load(){
  loading.value=true
  try{
    const{data}=await axios.get('/borrows/overdue',{params:{page:page.value,size:size.value,keyword:keyword.value||undefined,sort:sortBy.value||undefined,order:sortOrder.value||undefined}})
    list.value=data.records||[];total.value=data.total||0
  }catch(e){console.error(e)}finally{loading.value=false}
}

async function loadStats(){
  try{
    const{data}=await axios.get('/borrows/overdue/stats')
    stats.overdueTotal=data.overdueTotal||0
    stats.avgDays=data.avgDays||0
    stats.notified=data.notified||0
    stats.collected=data.collected||0
  }catch(e){console.error(e)}
}

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

async function batchNotify(){
  if(!selectedIds.value.length)return
  const ids=[...selectedIds.value]
  const results=await Promise.allSettled(ids.map(id=>axios.post(`/borrows/${id}/overdue-notify`)))
  const ok=results.filter(r=>r.status==='fulfilled').length
  if(ok===ids.length)ElMessage.success(`批量催还完成（${ok}条）`)
  else ElMessage.warning(`批量催还完成：成功${ok}条，失败${ids.length-ok}条`)
  load();loadStats()
}

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
