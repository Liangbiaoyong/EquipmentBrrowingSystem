<template>
  <div class="detail" v-loading="loading"><el-page-header @back="$router.back()" content="借用详情" style="margin-bottom:15px"/>
    <el-card v-if="record"><el-descriptions :column="2" border><el-descriptions-item label="借用单ID">{{ record.id }}</el-descriptions-item><el-descriptions-item label="设备ID">{{ record.deviceId }}</el-descriptions-item><el-descriptions-item label="开始时间">{{ fmt(record.startTime) }}</el-descriptions-item><el-descriptions-item label="结束时间">{{ fmt(record.endTime) }}</el-descriptions-item><el-descriptions-item label="状态"><el-tag :type="stc(record.status)">{{ record.status }}</el-tag></el-descriptions-item><el-descriptions-item label="当前步骤">第{{ record.currentStep }}步</el-descriptions-item><el-descriptions-item label="事由" :span="2">{{ record.reason }}</el-descriptions-item><el-descriptions-item label="实际归还" v-if="record.realReturnTime">{{ fmt(record.realReturnTime) }}</el-descriptions-item><el-descriptions-item label="逾期天数" v-if="record.overdueDays">{{ record.overdueDays }}天</el-descriptions-item><el-descriptions-item label="损坏报告" :span="2" v-if="record.damageReport">{{ record.damageReport }}</el-descriptions-item></el-descriptions>
    </el-card>
    <el-card header="审批记录" style="margin-top:15px" v-if="logs.length"><el-timeline><el-timeline-item v-for="l in logs" :key="l.id" :timestamp="l.operateTime" :color="l.result==='APPROVED'?'#67C23A':l.result==='REJECTED'?'#F56C6C':'#909399'"><p><strong>第{{ l.step }}步</strong> · 审批人ID:{{ l.approverId }} · <el-tag size="small" :type="l.result==='APPROVED'?'success':l.result==='REJECTED'?'danger':'warning'">{{ l.result==='APPROVED'?'通过':l.result==='REJECTED'?'驳回':'待审批' }}</el-tag></p><p v-if="l.comment" style="color:#606266;font-size:13px;margin-top:4px">{{ l.comment }}</p></el-timeline-item></el-timeline></el-card>
  </div>
</template>
<script setup>
import { ref,onMounted } from 'vue';import { useRoute } from 'vue-router';import { borrowApi } from '@/api/borrow';import axios from '@/api/request'
const route=useRoute();const loading=ref(true);const record=ref(null);const logs=ref([])
function stc(s){const m={'PENDING_APPROVAL':'warning','APPROVED':'success','BORROWING':'','RETURNED':'info','REJECTED':'danger','CANCELLED':'info','OVERDUE':'danger'};return m[s]||'info'}
function fmt(t){return t?t.replace('T',' ').substring(0,16):''}
onMounted(async()=>{try{const{data}=await borrowApi.getById(route.params.id);record.value=data;const r=await axios.get(`/borrows/${route.params.id}/approval-logs`);logs.value=r.data||[]}catch{}finally{loading.value=false}})
</script>
<style scoped>.detail{padding:20px}</style>
