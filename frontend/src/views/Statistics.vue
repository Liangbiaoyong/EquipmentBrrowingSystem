<template>
  <div class="stats"><h2>数据统计</h2>
    <el-tabs v-model="tab" @tab-change="switchTab">
      <el-tab-pane label="概览" name="overview"/>
      <el-tab-pane label="借用趋势" name="trend"/>
      <el-tab-pane label="热门设备 TOP10" name="topDevices"/>
      <el-tab-pane label="高频用户 TOP10" name="topUsers"/>
      <el-tab-pane label="分类利用率" name="utilization"/>
    </el-tabs>
    <el-card v-loading="loading">
      <div v-if="tab==='overview'"><el-descriptions :column="2" border v-if="Object.keys(overview).length"><el-descriptions-item v-for="(v,k) in overview" :key="k" :label="overviewLabels[k]||k">{{ v }}</el-descriptions-item></el-descriptions><el-empty v-else description="暂无数据" :image-size="60"/></div>
      <div v-else>
        <div v-for="(row,i) in chartData" :key="i" style="display:flex;align-items:center;margin-bottom:8px"><span style="width:200px;text-align:right;margin-right:10px;font-size:13px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis">{{ row.label }}</span><div style="flex:1;background:#f0f2f5;border-radius:4px;height:24px;overflow:hidden"><div :style="{width:row.pct+'%',background:colors[i%colors.length],height:'100%',transition:'width 0.5s'}"/></div><span style="margin-left:10px;font-weight:bold;font-size:13px;min-width:40px">{{ row.value }}</span></div>
      </div>
    </el-card>
    <el-button style="margin-top:15px" @click="doExport">导出 CSV</el-button>
  </div>
</template>
<script setup>
import { ref,reactive,onMounted } from 'vue';import { statsApi } from '@/api/statistics'
const tab=ref('overview');const loading=ref(false);const overview=reactive({});const chartData=ref([]);const colors=['#409EFF','#67C23A','#E6A23C','#F56C6C','#909399','#409EFF','#67C23A','#E6A23C','#F56C6C','#909399']
const overviewLabels={total:'设备总数',available:'可借用',borrowing:'借用中',repair:'维修中',scrap:'待报废',overdue:'逾期未还',pendingApproval:'待审批'}
async function switchTab(t){loading.value=true;try{const fn={overview:statsApi.overview,trend:statsApi.trend,topDevices:statsApi.topDevices,topUsers:statsApi.topUsers,utilization:statsApi.utilization}[t];if(!fn)return;const{data}=await fn();if(t==='overview'){Object.assign(overview,data.deviceStats,data.borrowStats)}else{const arr=Array.isArray(data)?data:[];const max=Math.max(...arr.map(r=>Object.values(r)[1]||0),1);chartData.value=arr.map(r=>{const[k,v]=Object.entries(r);return{label:k==='date'?v:v===Object.values(r)[0]?Object.values(r)[0]:k,value:v===Object.values(r)[0]?Object.values(r)[1]:v,pct:Math.round((v===Object.values(r)[0]?Object.values(r)[1]:v)/max*100)}}).slice(0,10)}}catch{}finally{loading.value=false}}
function doExport(){statsApi.exportCsv().then(({data})=>{const blob=new Blob([data],{type:'text/csv'});const a=document.createElement('a');a.href=URL.createObjectURL(blob);a.download='统计报表.csv';a.click()})}
onMounted(()=>switchTab('overview'))
</script>
<style scoped>.stats{padding:20px}</style>
