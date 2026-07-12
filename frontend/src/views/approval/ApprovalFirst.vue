<template>
  <div class="approval"><h2>{{ isSecond ? '终审' : '初审' }}</h2>
    <el-card><el-table :data="list" v-loading="loading" stripe size="small">
      <el-table-column label="ID" width="65" prop="id"/>
      <el-table-column label="设备" min-width="130"><template #default="{row}"><el-link type="primary" @click="$router.push('/devices/'+row.deviceId)">{{ row.deviceName }}</el-link></template></el-table-column>
      <el-table-column label="设备持有者" width="90"><template #default="{row}">{{ row.custodian||'-' }}</template></el-table-column>
      <el-table-column label="借用人" width="90"><template #default="{row}">{{ row.userName }}</template></el-table-column>
      <el-table-column label="初审人" width="90"><template #default="{row}">{{ row.approver1Name||'-' }}</template></el-table-column>
      <el-table-column label="终审人" width="90"><template #default="{row}">{{ row.approver2Name||'(待分配)' }}</template></el-table-column>
      <el-table-column label="开始" width="130"><template #default="{row}">{{ fmt(row.startTime) }}</template></el-table-column>
      <el-table-column label="结束" width="130"><template #default="{row}">{{ fmt(row.endTime) }}</template></el-table-column>
      <el-table-column label="事由" min-width="100" show-overflow-tooltip><template #default="{row}">{{ row.purpose||row.reason||'-' }}</template></el-table-column>
      <el-table-column label="操作" width="180" fixed="right"><template #default="{row}"><el-button size="small" type="success" @click="doApprove(row.id,true)">通过</el-button><el-button size="small" type="danger" @click="doApprove(row.id,false)">驳回</el-button></template></el-table-column>
    </el-table>
    <div style="margin-top:15px;display:flex;justify-content:flex-end"><el-pagination v-model:current-page="page" :page-size="size" :total="total" layout="total,prev,pager,next" @current-change="load"/></div></el-card>
    <el-dialog v-model="d.visible" :title="d.approved?'审批通过':'驳回申请'" width="400px"><el-input v-model="d.comment" type="textarea" :placeholder="d.approved?'审批意见(可选)':'驳回原因(必填)'"/><template #footer><el-button @click="d.visible=false">取消</el-button><el-button type="primary" @click="confirmApprove">确认</el-button></template></el-dialog>
  </div>
</template>
<script setup>
import { ref,reactive,onMounted } from 'vue';import { borrowApi } from '@/api/borrow';import { ElMessage } from 'element-plus'
const props=defineProps({isSecond:{type:Boolean,default:false}})
const loading=ref(false);const list=ref([]);const page=ref(1);const size=ref(20);const total=ref(0)
const d=reactive({visible:false,borrowId:null,approved:true,comment:''})
function fmt(t){return t?t.replace('T',' ').substring(0,16):''}
async function load(){
  loading.value=true
  try{
    const fn=props.isSecond?borrowApi.getPendingSecond:borrowApi.getPendingFirst
    const{data}=await fn({page:page.value,size:size.value})
    list.value=data.records||[];total.value=data.total||0
  }catch(e){console.error(e)}finally{loading.value=false}
}
function doApprove(borrowId,approved){d.borrowId=borrowId;d.approved=approved;d.comment='';d.visible=true}
async function confirmApprove(){
  if(!d.approved&&!d.comment.trim()){ElMessage.warning('驳回时必须填写审批意见');return}
  d.visible=false
  try{await borrowApi.approve({borrowId:d.borrowId,approved:d.approved,comment:d.comment});ElMessage.success(d.approved?'已通过':'已驳回');load()}catch(e){ElMessage.error(e?.response?.data?.msg||'操作失败')}
}
onMounted(load)
</script>
<style scoped>.approval{padding:20px}</style>
