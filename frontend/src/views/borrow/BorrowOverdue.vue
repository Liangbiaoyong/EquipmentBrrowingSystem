<template>
  <div class="overdue-page"><h2>逾期管理</h2>
    <!-- 统计概览 -->
    <div class="overdue-stats" v-if="stats.overdueTotal !== undefined">
      <div class="od-stat od-danger"><span class="od-num">{{ stats.overdueTotal }}</span><span class="od-label">当前逾期</span></div>
      <div class="od-stat od-info"><span class="od-num">{{ stats.collectedTotal }}</span><span class="od-label">已强制归还</span></div>
    </div>

    <el-card shadow="never">
      <!-- 筛选 -->
      <div style="margin-bottom:16px;display:flex;gap:10px;flex-wrap:wrap;align-items:center">
        <el-input v-model="keyword" placeholder="搜索设备名称/借用人" clearable style="width:220px" @clear="load" @keyup.enter="load"/>
        <el-button type="primary" @click="load">查询</el-button>
        <el-button @click="loadDevices" :loading="loadingNames">加载名称</el-button>
      </div>

      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="id" label="借用单号" width="90"/>
        <el-table-column label="设备" min-width="160">
          <template #default="{row}">{{ getDeviceName(row.deviceId) }}</template>
        </el-table-column>
        <el-table-column label="借用人" width="100">
          <template #default="{row}">{{ getUserName(row.userId) }}</template>
        </el-table-column>
        <el-table-column label="借用时间" width="170">
          <template #default="{row}">{{ formatTime(row.startTime) }} ~<br/>{{ formatTime(row.endTime) }}</template>
        </el-table-column>
        <el-table-column label="逾期天数" width="100" align="center">
          <template #default="{row}"><el-tag type="danger" size="large">{{ row.overdueDays }} 天</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{row}">
            <el-button size="small" type="primary" @click="handleNotify(row)">催还通知</el-button>
            <el-button size="small" type="warning" @click="handleReturn(row)">归还登记</el-button>
            <el-button size="small" type="danger" @click="handleForceReturn(row)">强制归还</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div style="margin-top:16px;display:flex;justify-content:flex-end">
        <el-pagination v-model:current-page="page" v-model:page-size="size" :page-sizes="[20,100,500]" :total="total" layout="total,sizes,prev,pager,next,jumper" @current-change="load" @size-change="s=>{size=s;page=1;load()}"/>
      </div>
    </el-card>

    <!-- 归还登记对话框 -->
    <el-dialog v-model="returnVisible" title="归还登记" width="500px">
      <el-form :model="returnForm" label-width="100px">
        <el-form-item label="借用单号"><el-tag>{{ returnForm.borrowId }}</el-tag></el-form-item>
        <el-form-item label="设备"><el-tag type="primary">{{ returnDeviceName }}</el-tag></el-form-item>
        <el-form-item label="逾期天数"><el-tag type="danger">{{ returnForm.overdueDays }} 天</el-tag></el-form-item>
        <el-form-item label="损坏情况"><el-input v-model="returnForm.damageReport" type="textarea" :rows="3" placeholder="无损坏可不填"/></el-form-item>
      </el-form>
      <template #footer><el-button @click="returnVisible=false">取消</el-button><el-button type="primary" @click="submitReturn" :loading="submitting">确认归还</el-button></template>
    </el-dialog>

    <!-- 强制归还对话框 -->
    <el-dialog v-model="forceVisible" title="强制归还" width="500px">
      <el-alert title="强制归还将由管理员代为操作，设备状态将立即变更为已归还" type="warning" show-icon :closable="false" style="margin-bottom:16px"/>
      <el-form :model="forceForm" label-width="100px">
        <el-form-item label="借用单号"><el-tag>{{ forceForm.borrowId }}</el-tag></el-form-item>
        <el-form-item label="设备"><el-tag type="primary">{{ forceDeviceName }}</el-tag></el-form-item>
        <el-form-item label="逾期天数"><el-tag type="danger">{{ forceForm.overdueDays }} 天</el-tag></el-form-item>
        <el-form-item label="损坏情况"><el-input v-model="forceForm.damageReport" type="textarea" :rows="2" placeholder="无损坏可不填"/></el-form-item>
        <el-form-item label="强制归还原因" required><el-input v-model="forceForm.remark" type="textarea" :rows="2" placeholder="请填写强制归还原因"/></el-form-item>
      </el-form>
      <template #footer><el-button @click="forceVisible=false">取消</el-button><el-button type="danger" @click="submitForceReturn" :loading="submitting">确认强制归还</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref,reactive,onMounted } from 'vue'
import axios from '@/api/request'
import { ElMessage,ElMessageBox } from 'element-plus'

const list = ref([])
const loading = ref(false)
const loadingNames = ref(false)
const keyword = ref('')
const page = ref(1); const size = ref(20); const total = ref(0)
const deviceNames = ref({}); const userNames = ref({})

// 逾期统计
const stats = reactive({ overdueTotal: undefined, collectedTotal: undefined })

// 归还对话框
const returnVisible = ref(false)
const returnForm = reactive({ borrowId: null, damageReport: '', overdueDays: 0 })
const returnDeviceName = ref('')

// 强制归还对话框
const forceVisible = ref(false)
const forceForm = reactive({ borrowId: null, damageReport: '', overdueDays: 0, remark: '' })
const forceDeviceName = ref('')
const submitting = ref(false)

// ===== 数据加载 =====
async function load() {
  loading.value = true
  try {
    const { data } = await axios.get('/borrows/overdue', { params: { page: page.value, size: size.value } })
    list.value = data.records || []; total.value = data.total || 0
  } catch (e) {
    ElMessage.error('加载逾期列表失败')
  } finally { loading.value = false }
}

async function loadStats() {
  try {
    const { data } = await axios.get('/borrows/overdue/stats')
    Object.assign(stats, data)
  } catch {}
}

async function loadDevices() {
  loadingNames.value = true
  try {
    const dIds = [...new Set(list.value.map(r => r.deviceId))]
    for (const id of dIds) {
      if (!deviceNames.value[id]) {
        try { const { data } = await axios.get(`/devices/${id}`); deviceNames.value[id] = data?.name || data?.device?.name || ('设备#' + id) }
        catch { deviceNames.value[id] = '设备#' + id }
      }
    }
    const uIds = [...new Set(list.value.map(r => r.userId))]
    for (const id of uIds) {
      if (!userNames.value[id]) {
        try { const { data } = await axios.get(`/auth/user/${id}`); userNames.value[id] = data?.realName || data?.username || ('用户#' + id) }
        catch { userNames.value[id] = '用户#' + id }
      }
    }
  } catch {} finally { loadingNames.value = false }
}

function getDeviceName(id) { return deviceNames.value[id] || '设备#' + id }
function getUserName(id) { return userNames.value[id] || '用户#' + id }
function formatTime(t) { return t ? t.replace('T', ' ').substring(0, 16) : '' }

// ===== 催还通知 =====
async function handleNotify(row) {
  try {
    await axios.post(`/borrows/${row.id}/overdue-notify`)
    ElMessage.success(`已向借用人发送催还通知（第${(row.notifyCount||0)+1}次）`)
  } catch (e) {
    ElMessage.error('催还通知发送失败: ' + (e?.response?.data?.msg || e.message))
  }
}

// ===== 归还登记 =====
function handleReturn(row) {
  returnForm.borrowId = row.id
  returnForm.damageReport = ''
  returnForm.overdueDays = row.overdueDays
  returnDeviceName.value = getDeviceName(row.deviceId)
  returnVisible.value = true
}

async function submitReturn() {
  submitting.value = true
  try {
    await axios.post(`/borrows/${returnForm.borrowId}/return`, null, {
      params: { damageReport: returnForm.damageReport || '' }
    })
    ElMessage.success('归还成功')
    returnVisible.value = false
    load(); loadStats()
  } catch (e) {
    ElMessage.error('归还失败: ' + (e?.response?.data?.msg || e.message))
  } finally { submitting.value = false }
}

// ===== 强制归还 =====
function handleForceReturn(row) {
  forceForm.borrowId = row.id
  forceForm.damageReport = ''
  forceForm.overdueDays = row.overdueDays
  forceForm.remark = ''
  forceDeviceName.value = getDeviceName(row.deviceId)
  forceVisible.value = true
}

async function submitForceReturn() {
  if (!forceForm.remark.trim()) { ElMessage.warning('请填写强制归还原因'); return }
  submitting.value = true
  try {
    await axios.put(`/borrows/${forceForm.borrowId}/force-return`, null, {
      params: {
        damageReport: forceForm.damageReport || '',
        remark: forceForm.remark
      }
    })
    ElMessage.success('强制归还完成')
    forceVisible.value = false
    load(); loadStats()
  } catch (e) {
    ElMessage.error('强制归还失败: ' + (e?.response?.data?.msg || e.message))
  } finally { submitting.value = false }
}

onMounted(() => { load(); loadStats() })
</script>

<style scoped>
.overdue-page { padding: 20px; }
.overdue-stats { display: flex; gap: 16px; margin-bottom: 16px; }
.od-stat { display: flex; flex-direction: column; align-items: center; padding: 14px 28px; border-radius: 10px; background: #fff; box-shadow: 0 1px 6px rgba(0,0,0,0.06); }
.od-num { font-size: 28px; font-weight: 700; }
.od-label { font-size: 13px; color: #909399; margin-top: 2px; }
.od-danger .od-num { color: #F56C6C; }
.od-info .od-num { color: #409EFF; }
</style>
