<template>
  <div class="borrow-browse"><h2>借用浏览</h2>
    <!-- 筛选栏 -->
    <el-card shadow="never" style="margin-bottom:12px">
      <div style="display:flex;gap:8px;flex-wrap:wrap;align-items:center">
        <el-input v-model="keyword" placeholder="搜索设备/借用人" clearable style="width:180px" @keyup.enter="load"/>
        <el-select v-model="statusFilter" placeholder="状态" clearable style="width:140px" @change="load">
          <el-option v-for="s in statusOptions" :key="s.value" :label="s.label" :value="s.value"/>
        </el-select>
        <el-date-picker v-model="dates" type="daterange" range-separator="至" start-placeholder="开始" end-placeholder="结束" format="YYYY-MM-DD" value-format="YYYY-MM-DD" style="width:240px" @change="load"/>
        <el-button type="primary" @click="load">查询</el-button>
        <span style="margin-left:auto;color:#909399;font-size:13px">共 {{ total }} 条记录</span>
      </div>
    </el-card>

    <el-card shadow="never">
      <el-table :data="list" stripe v-loading="loading" @sort-change="onSort" max-height="600">
        <el-table-column prop="id" label="单号" width="75" sortable="custom"/>
        <el-table-column label="设备" min-width="140"><template #default="{row}"><el-link type="primary" @click="$router.push('/devices/'+row.deviceId)">{{ getName(row.deviceId,'d') }}</el-link></template></el-table-column>
        <el-table-column label="借用人" width="100"><template #default="{row}">{{ getName(row.userId,'u') }}</template></el-table-column>
        <el-table-column label="状态" width="110"><template #default="{row}"><el-tag :type="statusType(row.status)" size="small">{{ statusText(row.status) }}</el-tag></template></el-table-column>
        <el-table-column label="开始时间" width="140" sortable="custom" prop="startTime"><template #default="{row}">{{ fmt(row.startTime) }}</template></el-table-column>
        <el-table-column label="结束时间" width="140" sortable="custom" prop="endTime"><template #default="{row}">{{ fmt(row.endTime) }}</template></el-table-column>
        <el-table-column label="逾期天数" width="85" sortable="custom" prop="overdueDays"><template #default="{row}"><span v-if="row.overdueDays" style="color:#F56C6C;font-weight:600">{{ row.overdueDays }}天</span><span v-else>-</span></template></el-table-column>
        <el-table-column label="目的" min-width="120" show-overflow-tooltip prop="purpose"/>
        <el-table-column label="操作" width="200" fixed="right"><template #default="{row}">
          <div style="white-space:nowrap;display:flex;gap:4px">
            <el-button v-if="row.status==='PENDING_APPROVAL'" size="small" type="danger" @click="cancelBorrow(row.id)">取消</el-button>
            <el-button v-if="row.status==='BORROWING'||row.status==='OVERDUE'" size="small" type="warning" @click="returnBorrow(row)">归还</el-button>
            <el-button v-if="row.status==='OVERDUE'" size="small" type="danger" @click="forceReturnBorrow(row)">强制归还</el-button>
          </div>
        </template></el-table-column>
      </el-table>
      <div style="margin-top:12px;display:flex;justify-content:flex-end">
        <el-pagination v-model:current-page="page" :page-size="size" :total="total" layout="total,prev,pager,next" @current-change="load"/>
      </div>
    </el-card>

    <!-- 归还对话框 -->
    <el-dialog v-model="returnVisible" title="归还登记" width="450px">
      <el-form label-width="80px">
        <el-form-item label="借用单号"><el-tag>{{ returnForm.id }}</el-tag></el-form-item>
        <el-form-item label="损坏情况"><el-input v-model="returnForm.damage" type="textarea" :rows="2" placeholder="无损坏可不填"/></el-form-item>
      </el-form>
      <template #footer><el-button @click="returnVisible=false">取消</el-button><el-button type="primary" @click="submitReturn">确认归还</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref,onMounted } from 'vue'
import axios from '@/api/request'
import { ElMessage,ElMessageBox } from 'element-plus'

const list=ref([]);const loading=ref(false);const page=ref(1);const size=ref(20);const total=ref(0)
const keyword=ref('');const statusFilter=ref(null);const dates=ref([])
const sortBy=ref('');const sortOrder=ref('desc')
const nameCache=ref({})

const statusOptions=[
  {label:'待审批',value:'PENDING_APPROVAL'},{label:'已通过',value:'APPROVED'},
  {label:'已驳回',value:'REJECTED'},{label:'借用中',value:'BORROWING'},
  {label:'已归还',value:'RETURNED'},{label:'逾期',value:'OVERDUE'},{label:'已取消',value:'CANCELLED'}
]

const statusMap={PENDING_APPROVAL:'warning',APPROVED:'success',REJECTED:'danger',BORROWING:'',RETURNED:'info',OVERDUE:'danger',CANCELLED:'info'}
const statusTextMap={PENDING_APPROVAL:'待审批',APPROVED:'已通过',REJECTED:'已驳回',BORROWING:'借用中',RETURNED:'已归还',OVERDUE:'逾期',CANCELLED:'已取消'}

function statusType(s){return statusMap[s]||'info'}
function statusText(s){return statusTextMap[s]||s}
function fmt(t){return t?t.replace('T',' ').substring(0,16):''}

const returnVisible=ref(false);const returnForm=ref({})

function onSort({prop,order}){sortBy.value=prop;sortOrder.value=order||'desc';load()}

async function load(){
  loading.value=true
  try{
    const params={page:page.value,size:size.value,keyword:keyword.value||undefined,status:statusFilter.value,sort:sortBy.value||undefined,order:sortOrder.value}
    if(dates.value?.length===2){params.startDate=dates.value[0];params.endDate=dates.value[1]}
    const{data}=await axios.get('/borrows/browse',{params})
    const records=data.records||[];total.value=data.total||0;list.value=records
    loadNames(records)
  }catch(e){console.error(e)}finally{loading.value=false}
}

async function loadNames(records){
  for(const r of records){
    if(r.deviceId&&!nameCache.value['d'+r.deviceId]){try{const{data}=await axios.get('/devices/'+r.deviceId);nameCache.value['d'+r.deviceId]=data?.name||data?.device?.name||('设备#'+r.deviceId)}catch{nameCache.value['d'+r.deviceId]='设备#'+r.deviceId}}
    if(r.userId&&!nameCache.value['u'+r.userId]){try{const{data}=await axios.get('/auth/info');nameCache.value['u'+r.userId]=data?.realName||('用户#'+r.userId)}catch{nameCache.value['u'+r.userId]='用户#'+r.userId}}
  }
}

function getName(id,prefix){return nameCache.value[prefix+id]||(prefix==='d'?'设备#':'用户#')+id}

async function cancelBorrow(id){try{await axios.post(`/borrows/${id}/cancel`);ElMessage.success('已取消');load()}catch(e){ElMessage.error(e?.response?.data?.msg||'取消失败')}}

function returnBorrow(row){returnForm.value={id:row.id,damage:''};returnVisible.value=true}
async function submitReturn(){try{await axios.post(`/borrows/${returnForm.value.id}/return`,null,{params:{damageReport:returnForm.value.damage||''}});ElMessage.success('归还成功');returnVisible.value=false;load()}catch(e){ElMessage.error(e?.response?.data?.msg||'归还失败')}}

function forceReturnBorrow(row){
  ElMessageBox.prompt('强制归还原因','强制归还',{confirmButtonText:'确认强制归还',confirmButtonClass:'el-button--danger'}).then(async({value})=>{
    try{await axios.put(`/borrows/${row.id}/force-return`,null,{params:{remark:value,damageReport:''}});ElMessage.success('强制归还完成');load()}catch(e){ElMessage.error(e?.response?.data?.msg||'操作失败')}
  }).catch(()=>{})
}

onMounted(load)
</script>
<style scoped>.borrow-browse{padding:20px;max-width:1280px;margin:0 auto}</style>
