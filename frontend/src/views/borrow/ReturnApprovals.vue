<template>
  <div class="return-approvals"><h2>归还审批</h2>
    <el-card>
      <div style="margin-bottom:12px;display:flex;gap:8px;align-items:center">
        <span style="color:#909399;font-size:13px">待审批的归还申请列表 — 请确认设备完好后点击「通过」</span>
      </div>
      <el-table :data="list" v-loading="loading" stripe @sort-change="onSort">
        <el-table-column prop="id" label="单号" width="75" sortable="custom"/>
        <el-table-column label="设备" min-width="150">
          <template #default="{row}">
            <div>
              <span>{{ row.deviceName || '设备#'+row.deviceId }}</span>
              <span v-if="row.deviceAssetNo" style="font-size:11px;color:#909399;margin-left:6px">{{ row.deviceAssetNo }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="借用人" width="100"><template #default="{row}">{{ row.userName || '用户#'+row.userId }}</template></el-table-column>
        <el-table-column prop="startTime" label="借用时间" width="160"><template #default="{row}">{{ fmt(row.startTime) }} ~ {{ fmt(row.endTime) }}</template></el-table-column>
        <el-table-column label="逾期" width="70"><template #default="{row}"><span v-if="row.overdueDays" style="color:#F56C6C">{{row.overdueDays}}天</span><span v-else style="color:#C0C4CC">-</span></template></el-table-column>
        <el-table-column label="归还照片" width="100">
          <template #default="{row}">
            <el-button size="small" @click="previewPhotos(row)">查看</el-button>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{row}">
            <el-button size="small" type="success" @click="doApprove(row,true)" :loading="row._loading">通过</el-button>
            <el-button size="small" type="danger" @click="doApprove(row,false)" :loading="row._loading">驳回</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="!list.length&&!loading" style="text-align:center;padding:40px;color:#909399">暂无待审批的归还申请</div>
    </el-card>

    <!-- 照片预览对话框 -->
    <el-dialog v-model="photoDlg.visible" title="归还照片" width="500px">
      <div v-if="photoDlg.images.length" style="display:flex;flex-wrap:wrap;gap:10px">
        <el-image v-for="(url,i) in photoDlg.images" :key="i" :src="url" fit="cover" style="width:200px;height:150px;border-radius:8px" :preview-src-list="photoDlg.images"/>
      </div>
      <div v-else style="text-align:center;padding:20px;color:#909399">暂无照片</div>
    </el-dialog>

    <!-- 驳回理由对话框 -->
    <el-dialog v-model="rejectDlg.visible" title="驳回理由" width="400px">
      <el-input v-model="rejectDlg.comment" type="textarea" :rows="3" placeholder="请填写驳回原因（必填）"/>
      <template #footer>
        <el-button @click="rejectDlg.visible=false">取消</el-button>
        <el-button type="danger" @click="confirmReject" :loading="rejectDlg.loading">确认驳回</el-button>
      </template>
    </el-dialog>
  </div>
</template>
<script setup>
import { ref,reactive,onMounted } from 'vue'
import axios from '@/api/request'
import { ElMessage } from 'element-plus'

const list=ref([]);const loading=ref(false)
const photoDlg=reactive({visible:false,images:[]})
const rejectDlg=reactive({visible:false,row:null,comment:'',loading:false})

function fmt(t){return t?t.replace('T',' ').substring(0,16):''}
function onSort(){}

async function load(){
  loading.value=true
  try{
    const{data}=await axios.get('/borrows/return-pending')
    list.value=data||[]
  }catch(e){console.error(e)}finally{loading.value=false}
}

async function previewPhotos(row){
  try{
    const{data}=await axios.get(`/borrows/${row.id}/images`)
    photoDlg.images=data.returnImages||[]
    photoDlg.visible=true
  }catch{ElMessage.error('加载照片失败')}
}

async function doApprove(row,approved){
  if(!approved){
    rejectDlg.row=row;rejectDlg.comment='';rejectDlg.visible=true;return
  }
  row._loading=true
  try{
    await axios.post(`/borrows/${row.id}/approve-return`,null,{params:{approved:true}})
    ElMessage.success('归还已确认')
    load()
  }catch(e){ElMessage.error(e?.response?.data?.msg||'操作失败')}finally{row._loading=false}
}

async function confirmReject(){
  if(!rejectDlg.comment.trim()){ElMessage.warning('请填写驳回原因');return}
  rejectDlg.loading=true
  try{
    await axios.post(`/borrows/${rejectDlg.row.id}/approve-return`,null,{params:{approved:false,comment:rejectDlg.comment.trim()}})
    ElMessage.success('已驳回')
    rejectDlg.visible=false;load()
  }catch(e){ElMessage.error(e?.response?.data?.msg||'操作失败')}finally{rejectDlg.loading=false}
}

onMounted(load)
</script>
<style scoped>.return-approvals{padding:20px}</style>
