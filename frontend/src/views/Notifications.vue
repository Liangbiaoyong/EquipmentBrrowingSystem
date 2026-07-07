<template>
  <div class="notify"><h2>消息中心</h2>
    <el-card>
      <div style="margin-bottom:10px;display:flex;justify-content:space-between"><span>共 {{ total }} 条</span><el-button size="small" @click="markAll">全部已读</el-button></div>
      <el-timeline v-loading="loading"><el-timeline-item v-for="n in list" :key="n.id" :timestamp="fmt(n.createTime)" :color="n.isRead?'#909399':'#409EFF'" placement="top"><el-card :class="{unread:!n.isRead}" @click="readOne(n)" style="cursor:pointer"><strong>{{ n.title }}</strong><p style="margin-top:5px;color:#606266;font-size:13px">{{ n.content }}</p></el-card></el-timeline-item></el-timeline>
      <div style="margin-top:15px;display:flex;justify-content:flex-end"><el-pagination v-model:current-page="page" :page-size="size" :total="total" layout="prev,pager,next" @current-change="load"/></div>
    </el-card>
  </div>
</template>
<script setup>
import { ref,onMounted } from 'vue';import { notifyApi } from '@/api/notification';import { ElMessage } from 'element-plus'
const loading=ref(false);const list=ref([]);const page=ref(1);const size=ref(20);const total=ref(0)
function fmt(t){return t||''}
async function load(){loading.value=true;try{const{data}=await notifyApi.list({page:page.value,size:size.value});list.value=data;total.value=data.length>=size.value?page.value*size.value+1:page.value*size.value}catch{}finally{loading.value=false}}
async function readOne(n){if(!n.isRead)try{await notifyApi.markRead(n.id);n.isRead=true}catch{}}
async function markAll(){try{await notifyApi.markAllRead();load();ElMessage.success('已全部标为已读')}catch{}}
onMounted(load)
</script>
<style scoped>.notify{padding:20px}.unread{background:#ecf5ff}</style>
