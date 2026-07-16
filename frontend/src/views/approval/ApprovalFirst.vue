<template>
  <div class="approval"><h2>{{ isSecond ? '终审' : '初审' }}</h2>
    <el-card><el-table :data="list" v-loading="loading" stripe size="small">
      <el-table-column label="单号" width="75"><template #default="{row}"><el-link type="primary" @click="openDetail(row.id)">{{ row.id }}</el-link></template></el-table-column>
      <el-table-column label="设备" min-width="130"><template #default="{row}"><el-link type="primary" @click="$router.push('/devices/'+row.deviceId)">{{ row.deviceName }}</el-link></template></el-table-column>
      <el-table-column label="持有者" width="80"><template #default="{row}">{{ row.custodian||'-' }}</template></el-table-column>
      <el-table-column label="借用人" width="80"><template #default="{row}">{{ row.userName }}</template></el-table-column>
      <el-table-column label="目的" min-width="120" show-overflow-tooltip><template #default="{row}">{{ row.purpose||'-' }}</template></el-table-column>
      <el-table-column label="分类" width="80"><template #default="{row}"><el-tag size="small" type="info">{{ row.purposeCategory||'-' }}</el-tag></template></el-table-column>
      <el-table-column label="初审人" width="80"><template #default="{row}">{{ row.approver1Name||'-' }}</template></el-table-column>
      <el-table-column label="终审人" width="80"><template #default="{row}">{{ row.approver2Name||'(待分配)' }}</template></el-table-column>
      <el-table-column label="开始" width="130"><template #default="{row}">{{ fmt(row.startTime) }}</template></el-table-column>
      <el-table-column label="结束" width="130"><template #default="{row}">{{ fmt(row.endTime) }}</template></el-table-column>
      <el-table-column label="操作" width="180" fixed="right"><template #default="{row}"><el-button size="small" type="success" @click="doApprove(row.id,true)">通过</el-button><el-button size="small" type="danger" @click="doApprove(row.id,false)">驳回</el-button></template></el-table-column>
    </el-table>
    <div style="margin-top:15px;display:flex;justify-content:flex-end"><el-pagination v-model:current-page="page" v-model:page-size="size" :page-sizes="[20,100,500]" :total="total" layout="total,sizes,prev,pager,next,jumper" @current-change="load" @size-change="s=>{size=s;page=1;load()}"/></div></el-card>

    <!-- 借用详情抽屉 -->
    <el-drawer v-model="detailVisible" title="借用详情" size="480px">
      <template v-if="detail">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="借用单号">{{ detail.id }}</el-descriptions-item>
          <el-descriptions-item label="设备">{{ detail.deviceName||detail.deviceId }}</el-descriptions-item>
          <el-descriptions-item label="借用人">{{ detail.userName||detail.userId }}</el-descriptions-item>
          <el-descriptions-item label="状态"><el-tag :type="st(detail.status)">{{ stx(detail.status) }}</el-tag></el-descriptions-item>
          <el-descriptions-item label="目的">{{ detail.purpose||'-' }}</el-descriptions-item>
          <el-descriptions-item label="目的分类">{{ detail.purposeCategory||'-' }}</el-descriptions-item>
          <el-descriptions-item label="事由">{{ detail.reason||'-' }}</el-descriptions-item>
          <el-descriptions-item label="开始时间">{{ fmt(detail.startTime) }}</el-descriptions-item>
          <el-descriptions-item label="结束时间">{{ fmt(detail.endTime) }}</el-descriptions-item>
          <el-descriptions-item label="实际归还">{{ detail.realReturnTime?fmt(detail.realReturnTime):'-' }}</el-descriptions-item>
          <el-descriptions-item label="逾期天数">{{ detail.overdueDays||0 }}天</el-descriptions-item>
        </el-descriptions>
      </template>
    </el-drawer>

    <!-- 审批意见对话框 -->
    <el-dialog v-model="d.visible" :title="d.approved?'审批通过':'驳回申请'" width="420px" :close-on-click-modal="false" destroy-on-close>
      <el-form @submit.prevent="confirmApprove">
        <el-form-item>
          <el-input v-model="d.comment" type="textarea" :rows="4"
            :placeholder="d.approved?'审批意见（可选）':'驳回原因（必填）'"
            @keyup.enter.ctrl="confirmApprove"/>
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="d.visible = false">取 消</el-button>
          <el-button type="primary" @click="confirmApprove" :loading="d.submitting">
            {{ d.approved ? '确认通过' : '确认驳回' }}
          </el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref,reactive,onMounted } from 'vue';import { borrowApi } from '@/api/borrow';import { ElMessage } from 'element-plus';import axios from '@/api/request'
const props=defineProps({isSecond:{type:Boolean,default:false}})
const loading=ref(false);const list=ref([]);const page=ref(1);const size=ref(20);const total=ref(0)
const d=reactive({visible:false,borrowId:null,approved:true,comment:'',submitting:false})
const detailVisible=ref(false);const detail=ref(null)
const sm={PENDING_APPROVAL:'warning',APPROVED:'success',REJECTED:'danger',BORROWING:'',RETURNED:'info',OVERDUE:'danger',CANCELLED:'info'}
const sx={PENDING_APPROVAL:'待审批',APPROVED:'已通过',REJECTED:'已驳回',BORROWING:'借用中',RETURNED:'已归还',OVERDUE:'逾期',CANCELLED:'已取消'}
function st(s){return sm[s]||'info'}function stx(s){return sx[s]||s}
function fmt(t){return t?t.replace('T',' ').substring(0,16):''}

async function load(){
  loading.value=true
  try{const fn=props.isSecond?borrowApi.getPendingSecond:borrowApi.getPendingFirst;const{data}=await fn({page:page.value,size:size.value});list.value=data.records||[];total.value=data.total||0}catch(e){console.error(e)}finally{loading.value=false}
}

async function openDetail(id){
  detailVisible.value=true;detail.value=null
  try{const{data}=await axios.get(`/borrows/${id}`);detail.value=data}catch(e){ElMessage.error('加载详情失败')}
}

function doApprove(borrowId,approved){d.borrowId=borrowId;d.approved=approved;d.comment='';d.submitting=false;d.visible=true}
async function confirmApprove(){
  if(!d.approved&&!d.comment.trim()){ElMessage.warning('驳回时必须填写驳回原因');return}
  d.submitting=true
  try{await borrowApi.approve({borrowId:d.borrowId,approved:d.approved,comment:d.comment});d.visible=false;ElMessage.success(d.approved?'审批已通过':'已驳回')}catch(e){ElMessage.error(e?.response?.data?.msg||e?.message||'操作失败');d.submitting=false;return}
  load()
  d.submitting=false
}
onMounted(load)
</script>
<style scoped>.approval{padding:20px}</style>
