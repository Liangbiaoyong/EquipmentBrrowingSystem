<template>
  <div class="device-detail" v-loading="loading">
    <el-page-header @back="$router.back()" content="设备详情" style="margin-bottom:15px"/>
    <el-row :gutter="20" v-if="detail">
      <el-col :span="16">
        <el-card>
          <template #header>
            <span style="font-size:18px;font-weight:bold">{{ detail.device?.name }}</span>
            <el-tag style="margin-left:6px" :type="detail.device?.borrowType===1?'warning':''" effect="plain">{{ detail.device?.borrowType===1?'仅现场借用':'可借出' }}</el-tag>
            <el-tag style="margin-left:6px" :type="borrowStatusTagType(detail.device?.borrowStatus)">{{ borrowStatusText(detail.device?.borrowStatus) }}</el-tag>
            <el-tag style="margin-left:6px" :type="deviceStatusTagType(detail.device?.deviceStatus)">{{ deviceStatusText(detail.device?.deviceStatus) }}</el-tag>
          </template>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="资产编号">{{ detail.device.assetNo }}</el-descriptions-item>
            <el-descriptions-item label="型号">{{ detail.device.model }}</el-descriptions-item>
            <el-descriptions-item label="规格">{{ detail.device.specs }}</el-descriptions-item>
            <el-descriptions-item label="分类">{{ detail.categoryName }}</el-descriptions-item>
            <el-descriptions-item label="存放地">{{ detail.device.location }}</el-descriptions-item>
            <el-descriptions-item label="所属实验室">{{ detail.laboratoryName||'未分配' }}</el-descriptions-item>
            <el-descriptions-item label="使用单位">{{ detail.device.department }}</el-descriptions-item>
            <el-descriptions-item label="总数量">{{ detail.device.totalQty }}</el-descriptions-item>
            <el-descriptions-item label="可借数量">{{ detail.device.availableQty }}</el-descriptions-item>
            <el-descriptions-item label="单价">¥{{ detail.device.unitPrice }}</el-descriptions-item>
            <el-descriptions-item label="借还状态"><el-tag :type="borrowStatusTagType(detail.device?.borrowStatus)">{{ borrowStatusText(detail.device?.borrowStatus) }}</el-tag></el-descriptions-item>
            <el-descriptions-item label="设备状态"><el-tag :type="deviceStatusTagType(detail.device?.deviceStatus)">{{ deviceStatusText(detail.device?.deviceStatus) }}</el-tag></el-descriptions-item>
            <el-descriptions-item label="借用类型"><el-tag :type="detail.device?.borrowType===1?'warning':''" effect="plain">{{ detail.device?.borrowType===1?'仅现场借用':'可借出' }}</el-tag></el-descriptions-item>
            <el-descriptions-item label="国标分类">{{ detail.device.gbCategoryName }}</el-descriptions-item>
            <el-descriptions-item label="购置日期">{{ detail.device.purchaseDate }}</el-descriptions-item>
            <el-descriptions-item label="厂家">{{ detail.device.manufacturer }}</el-descriptions-item>
            <el-descriptions-item label="供货商">{{ detail.device.supplier }}</el-descriptions-item>
            <el-descriptions-item label="使用人">{{ detail.device.custodian }}</el-descriptions-item>
            <el-descriptions-item label="历史借用次数">{{ detail.borrowCount }} 次</el-descriptions-item>
          </el-descriptions>
          <div style="margin-top:15px" v-if="detail.isBorrowing"><el-alert title="当前借用人" :description="detail.currentBorrower" type="warning" show-icon :closable="false"/><p style="margin-top:5px;color:#909399">预计归还: {{ detail.expectedReturnTime }}</p></div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card header="设备图片">
          <el-empty v-if="!detail.images?.length" description="暂无图片"><template #image><svg width="80" height="80" viewBox="0 0 80 80"><rect width="80" height="80" rx="8" fill="#f0f2f5"/><text x="40" y="45" text-anchor="middle" fill="#909399" font-size="12">暂无图片</text></svg></template></el-empty>
          <el-image v-for="img in detail.images" :key="img.id" :src="`/api/v1/files/${img.imageUrl}`" fit="cover" style="width:100%;height:200px;margin-bottom:10px;border-radius:4px" :preview-src-list="detail.images.map(i=>`/api/v1/minio/${i.imageUrl}`)"><template #error><div class="img-error"><svg width="80" height="80" viewBox="0 0 80 80"><rect width="80" height="80" rx="4" fill="#f0f2f5"/><text x="40" y="45" text-anchor="middle" fill="#909399" font-size="12">暂无图片</text></svg></div></template></el-image>
        </el-card>
      </el-col>
    </el-row>
    <div style="margin-top:20px;text-align:center" v-if="detail?.device">
      <div v-if="detail.device.borrowStatus===1&&detail.device.deviceStatus===1&&detail.device.availableQty>0">
        <el-button type="primary" size="large" @click="$router.push(`/borrows/create?deviceId=${detail.device.id}`)">申请借用</el-button>
        <span v-if="detail.device.borrowType===1" style="margin-left:10px;color:#e6a23c;font-size:13px"><el-icon><Warning/></el-icon> 此设备仅限现场使用，不可带走</span>
      </div>
      <div v-else style="color:#909399">当前设备不可借用（借还状态：{{borrowStatusText(detail.device.borrowStatus)}}，设备状态：{{deviceStatusText(detail.device.deviceStatus)}}）</div>
    </div>
  </div>
</template>
<script setup>
import { ref,onMounted } from 'vue';import { useRoute } from 'vue-router';import { deviceApi } from '@/api/device';import { Warning } from '@element-plus/icons-vue'
const route=useRoute();const loading=ref(true);const detail=ref(null)

const borrowStatusMap={1:'success',2:'warning',3:'danger',4:'danger'}
const borrowStatusTextMap={1:'可借用',2:'借用中',3:'不可借',4:'逾期'}
const deviceStatusMap={1:'success',2:'warning',3:'danger',4:'info',5:'info'}
const deviceStatusTextMap={1:'正常',2:'待维修',3:'维修中',4:'待报废',5:'已报废'}
function borrowStatusTagType(v){return borrowStatusMap[v]||'info'}
function borrowStatusText(v){return borrowStatusTextMap[v]||'未知'}
function deviceStatusTagType(v){return deviceStatusMap[v]||'info'}
function deviceStatusText(v){return deviceStatusTextMap[v]||'未知'}

onMounted(async()=>{try{const{data}=await deviceApi.getById(route.params.id);detail.value=data}catch(e){console.error('加载设备详情失败',e)}finally{loading.value=false}})
</script>
<style scoped>.device-detail{padding:20px}.img-error{width:100%;height:200px;display:flex;align-items:center;justify-content:center;background:#f0f2f5;border-radius:4px}</style>
