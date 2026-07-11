<template>
  <div class="dashboard"><h2>仪表盘</h2>
    <h3 style="margin:10px 0 8px">借还状态</h3>
    <el-row :gutter="20" v-loading="loading">
      <el-col :span="6" v-for="s in borrowStatusCards" :key="s.label"><el-card shadow="hover" class="stat-card" :style="{borderTop:`3px solid ${s.color}`}"><div class="stat-value" :style="{color:s.color}">{{s.value}}</div><div class="stat-label">{{s.label}}</div></el-card></el-col>
    </el-row>
    <h3 style="margin:15px 0 8px">设备状态</h3>
    <el-row :gutter="20">
      <el-col :span="4" v-for="s in deviceStatusCards" :key="s.label" v-if="s.label!=='总设备'"><el-card shadow="hover" class="stat-card" :style="{borderTop:`3px solid ${s.color}`}"><div class="stat-value" :style="{color:s.color}">{{s.value}}</div><div class="stat-label">{{s.label}}</div></el-card></el-col>
    </el-row>
    <el-row :gutter="20" style="margin-top:20px">
      <el-col :span="8"><el-card header="借用概览"><el-row :gutter="10"><el-col :span="6" v-for="s in borrowCards" :key="s.label"><div class="mini-stat"><div class="mini-value" :style="{color:s.color}">{{s.value}}</div><div class="mini-label">{{s.label}}</div></div></el-col></el-row></el-card></el-col>
      <el-col :span="8"><el-card header="本月借用趋势"><div class="trend-bars" v-if="trendData.length"><div v-for="t in trendData" :key="t.date" class="trend-item"><div class="trend-fill" :style="{height:Math.max(3,t.count/maxT*100)+'px'}"/><span class="trend-date">{{t.date.substring(5)}}</span></div></div><el-empty v-else description="暂无数据" :image-size="50"/></el-card></el-col>
      <el-col :span="8"><el-card header="快捷入口"><el-space wrap><el-button type="primary" @click="$router.push('/devices')">浏览设备</el-button><el-button type="success" @click="$router.push('/borrows/create')">借用申请</el-button><el-button @click="$router.push('/borrows/my')">我的借用</el-button></el-space></el-card></el-col>
    </el-row>
  </div>
</template>
<script setup>
import {ref,reactive,onMounted} from 'vue';import {statsApi} from '@/api/statistics'
const loading=ref(true);const trendData=ref([]);const maxT=ref(1)
const borrowStatusCards=reactive([{label:'总设备',value:'-',color:'#909399'},{label:'可借用',value:'-',color:'#67C23A'},{label:'借用中',value:'-',color:'#409EFF'},{label:'不可借',value:'-',color:'#E6A23C'},{label:'逾期',value:'-',color:'#F56C6C'}])
const deviceStatusCards=reactive([{label:'正常',value:'-',color:'#67C23A'},{label:'待维修',value:'-',color:'#E6A23C'},{label:'维修中',value:'-',color:'#F56C6C'},{label:'待报废',value:'-',color:'#909399'},{label:'已报废',value:'-',color:'#DCDFE6'}])
const borrowCards=reactive([{label:'借出中',value:'-',color:'#409EFF'},{label:'逾期未还',value:'-',color:'#F56C6C'},{label:'待审批',value:'-',color:'#E6A23C'},{label:'总借用',value:'-',color:'#67C23A'}])
onMounted(async()=>{try{const{data:ov}=await statsApi.overview();const ds=ov.deviceStats;const bs=ov.borrowStats;
  borrowStatusCards[0].value=ds.total||0;borrowStatusCards[1].value=ds.borrowAvailable||ds.available||0;borrowStatusCards[2].value=ds.borrowBorrowing||ds.borrowing||0;borrowStatusCards[3].value=ds.borrowUnavailable||0;borrowStatusCards[4].value=ds.borrowOverdue||0;
  deviceStatusCards[0].value=ds.deviceNormal||0;deviceStatusCards[1].value=ds.devicePendingRepair||0;deviceStatusCards[2].value=ds.deviceRepairing||ds.repair||0;deviceStatusCards[3].value=ds.devicePendingScrap||0;deviceStatusCards[4].value=ds.deviceScrapped||0;
  borrowCards[0].value=bs.borrowing;borrowCards[1].value=bs.overdue;borrowCards[2].value=bs.pendingApproval;borrowCards[3].value=bs.total}catch{};try{const{data:td}=await statsApi.trend();trendData.value=td||[];maxT.value=Math.max(1,...trendData.value.map(t=>t.count||0))}catch{}finally{loading.value=false}})
</script>
<style scoped>
.dashboard{padding:20px}.stat-card{text-align:center}.stat-value{font-size:28px;font-weight:bold}.stat-label{font-size:13px;color:#909399;margin-top:8px}
.mini-stat{text-align:center;padding:5px 0}.mini-value{font-size:22px;font-weight:bold}.mini-label{font-size:11px;color:#909399}
.trend-bars{display:flex;align-items:flex-end;justify-content:space-around;height:80px;padding-top:5px}.trend-item{display:flex;flex-direction:column;align-items:center;flex:1}.trend-fill{width:16px;background:#409EFF;border-radius:3px 3px 0 0;min-height:3px;transition:height 0.5s}.trend-date{font-size:9px;color:#909399;margin-top:3px;writing-mode:vertical-rl}
</style>
