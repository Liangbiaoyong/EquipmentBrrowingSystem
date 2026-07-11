<template>
  <div class="device-detail" v-loading="loading">
    <el-page-header @back="$router.back()" content="设备详情" style="margin-bottom:15px"/>
    <el-row :gutter="20" v-if="detail">
      <el-col :span="16">
        <el-card>
          <template #header><span style="font-size:18px;font-weight:bold">{{ detail.device?.name }}</span><el-tag style="margin-left:10px" :type="detail.device?.status===2?'warning':detail.device?.status===3?'danger':'success'">{{ detail.device?.status===1?'可借用':detail.device?.status===2?'借用中':detail.device?.status===3?'维修中':'待报废' }}</el-tag></template>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="资产编号">{{ detail.device.assetNo }}</el-descriptions-item>
            <el-descriptions-item label="型号">{{ detail.device.model }}</el-descriptions-item>
            <el-descriptions-item label="规格">{{ detail.device.specs }}</el-descriptions-item>
            <el-descriptions-item label="分类">{{ detail.categoryName }}</el-descriptions-item>
            <el-descriptions-item label="存放地">{{ detail.device.location }}</el-descriptions-item>
            <el-descriptions-item label="使用单位">{{ detail.device.department }}</el-descriptions-item>
            <el-descriptions-item label="总数量">{{ detail.device.totalQty }}</el-descriptions-item>
            <el-descriptions-item label="可借数量">{{ detail.device.availableQty }}</el-descriptions-item>
            <el-descriptions-item label="单价">¥{{ detail.device.unitPrice }}</el-descriptions-item>
            <el-descriptions-item label="状态"><el-tag :type="detail.device.status===1?'success':detail.device.status===2?'warning':detail.device.status===3?'danger':'info'">{{ detail.device.status===1?'可借用':detail.device.status===2?'借用中':detail.device.status===3?'维修中':'待报废' }}</el-tag></el-descriptions-item>
            <el-descriptions-item label="国标分类">{{ detail.device.gbCategoryName }}</el-descriptions-item>
            <el-descriptions-item label="购置日期">{{ detail.device.purchaseDate }}</el-descriptions-item>
            <el-descriptions-item label="厂家">{{ detail.device.manufacturer }}</el-descriptions-item>
            <el-descriptions-item label="供货商">{{ detail.device.supplier }}</el-descriptions-item>
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
    <div style="margin-top:20px;text-align:center" v-if="detail?.device?.status===1">
      <el-button type="primary" size="large" @click="$router.push(`/borrows/create?deviceId=${detail.device.id}`)" :disabled="detail.isBorrowing||!detail.device.availableQty">申请借用</el-button>
    </div>
  </div>
</template>
<script setup>
import { ref,onMounted } from 'vue';import { useRoute } from 'vue-router';import { deviceApi } from '@/api/device'
const route=useRoute();const loading=ref(true);const detail=ref(null)
onMounted(async()=>{try{const{data}=await deviceApi.getById(route.params.id);detail.value=data}catch(e){console.error('加载设备详情失败',e)}finally{loading.value=false}})
</script>
<style scoped>.device-detail{padding:20px}.img-error{width:100%;height:200px;display:flex;align-items:center;justify-content:center;background:#f0f2f5;border-radius:4px}</style>
