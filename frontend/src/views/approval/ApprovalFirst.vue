<template>
  <div class="approval"><h2>{{ isSecond ? '二级审批' : '一级审批' }}</h2>
    <el-card><el-table :data="list" v-loading="loading" stripe><el-table-column label="ID" width="70" prop="id"/><el-table-column label="设备ID" width="80" prop="deviceId"/><el-table-column label="借用人ID" width="90" prop="userId"/><el-table-column label="开始" width="130"><template #default="{row}">{{ fmt(row.startTime) }}</template></el-table-column><el-table-column label="结束" width="130"><template #default="{row}">{{ fmt(row.endTime) }}</template></el-table-column><el-table-column label="事由" prop="reason" min-width="120" show-overflow-tooltip/><el-table-column label="操作" width="200" fixed="right"><template #default="{row}"><el-button size="small" type="success" @click="approve(row.id,true)">通过</el-button><el-button size="small" type="danger" @click="approve(row.id,false)">驳回</el-button></template></el-table-column></el-table>
      <div style="margin-top:15px;display:flex;justify-content:flex-end"><el-pagination v-model:current-page="page" :page-size="size" :total="total" layout="total,prev,pager,next" @current-change="load"/></div></el-card>
    <el-dialog v-model="dialog.visible" :title="dialog.approved?'审批通过':'驳回申请'" width="400px"><el-input v-model="dialog.comment" type="textarea" :placeholder="dialog.approved?'审批意见(可选)':'驳回原因(必填)'"/><template #footer><el-button @click="dialog.visible=false">取消</el-button><el-button type="primary" @click="doApprove">确认</el-button></template></el-dialog>
  </div>
</template>
<script setup>
import { ref,reactive,onMounted } from 'vue';import { borrowApi } from '@/api/borrow';import { ElMessage } from 'element-plus'
const props=defineProps({isSecond:{type:Boolean,default:false}})
const loading=ref(false);const list=ref([]);const page=ref(1);const size=ref(20);const total=ref(0)
const dialog=reactive({visible:false,borrowId:null,approved:true,comment:''})
function fmt(t){return t?t.replace('T',' ').substring(0,16):''}
async function load(){loading.value=true;try{const fn=props.isSecond?borrowApi.getPendingSecond:borrowApi.getPendingFirst;const{data}=await fn({page:page.value,size:size.value});list.value=data.records;total.value=data.total}catch{}finally{loading.value=false}}
function approve(borrowId,approved){dialog.borrowId=borrowId;dialog.approved=approved;dialog.comment='';dialog.visible=true}
async function doApprove(){try{await borrowApi.approve({borrowId:dialog.borrowId,approved:dialog.approved,comment:dialog.comment});ElMessage.success(dialog.approved?'已通过':'已驳回');dialog.visible=false;load()}catch{}}
onMounted(load)
</script>
<style scoped>.approval{padding:20px}</style>
