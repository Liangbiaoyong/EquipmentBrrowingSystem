<template>
  <div class="overdue"><h2>逾期管理</h2>
    <el-card><el-table :data="list" v-loading="loading"><el-table-column label="借用单ID" prop="id" width="80"/><el-table-column label="设备ID" prop="deviceId" width="80"/><el-table-column label="借用人ID" prop="userId" width="90"/><el-table-column label="逾期天数" prop="overdueDays" width="100"><template #default="{row}"><el-tag type="danger">{{ row.overdueDays }}天</el-tag></template></el-table-column><el-table-column label="应归还时间" width="170"><template #default="{row}">{{ fmt(row.endTime) }}</template></el-table-column></el-table>
      <div style="margin-top:15px;display:flex;justify-content:flex-end"><el-pagination v-model:current-page="page" :page-size="size" :total="total" layout="total,prev,pager,next" @current-change="load"/></div></el-card>
  </div>
</template>
<script setup>
import { ref,onMounted } from 'vue';import { borrowApi } from '@/api/borrow'
const loading=ref(false);const list=ref([]);const page=ref(1);const size=ref(20);const total=ref(0)
function fmt(t){return t?t.replace('T',' ').substring(0,16):''}
async function load(){loading.value=true;try{const{data}=await borrowApi.getOverdue({page:page.value,size:size.value});list.value=data.records;total.value=data.total}catch{}finally{loading.value=false}}
onMounted(load)
</script>
<style scoped>.overdue{padding:20px}</style>
