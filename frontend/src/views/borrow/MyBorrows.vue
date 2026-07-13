<template>
  <div class="my-borrows">
    <!-- 页头 -->
    <div class="mb-header">
      <h2 class="mb-title">我的借用</h2>
      <el-button type="primary" :icon="Plus" @click="$router.push('/borrows/create')">借用申请</el-button>
    </div>

    <!-- 统计卡片 -->
    <div class="mb-stats" v-if="stats.total > 0">
      <div class="stat-card s-pending" @click="activeTab='PENDING_APPROVAL';load()">
        <div class="sc-icon"><el-icon :size="20"><Clock/></el-icon></div>
        <div class="sc-body"><div class="sc-num">{{ stats.pending }}</div><div class="sc-label">待审批</div></div>
        <div class="sc-bar"></div>
      </div>
      <div class="stat-card s-borrowing" @click="activeTab='BORROWING';load()">
        <div class="sc-icon"><el-icon :size="20"><Loading/></el-icon></div>
        <div class="sc-body"><div class="sc-num">{{ stats.borrowing }}</div><div class="sc-label">借用中</div></div>
        <div class="sc-bar"></div>
      </div>
      <div class="stat-card s-returned" @click="activeTab='RETURNED';load()">
        <div class="sc-icon"><el-icon :size="20"><CircleCheck/></el-icon></div>
        <div class="sc-body"><div class="sc-num">{{ stats.returned }}</div><div class="sc-label">已归还</div></div>
        <div class="sc-bar"></div>
      </div>
      <div class="stat-card s-overdue" @click="activeTab='OVERDUE';load()">
        <div class="sc-icon"><el-icon :size="20"><WarningFilled/></el-icon></div>
        <div class="sc-body"><div class="sc-num">{{ stats.overdue }}</div><div class="sc-label">已逾期</div></div>
        <div class="sc-bar"></div>
      </div>
    </div>

    <!-- 筛选栏 -->
    <div class="mb-toolbar">
      <el-radio-group v-model="activeTab" size="small" @change="load">
        <el-radio-button value="">全部</el-radio-button>
        <el-radio-button value="PENDING_APPROVAL">待审批</el-radio-button>
        <el-radio-button value="APPROVED">已通过</el-radio-button>
        <el-radio-button value="BORROWING">借用中</el-radio-button>
        <el-radio-button value="RETURNED">已归还</el-radio-button>
        <el-radio-button value="REJECTED">已驳回</el-radio-button>
        <el-radio-button value="OVERDUE">逾期</el-radio-button>
      </el-radio-group>
    </div>

    <!-- 表格 -->
    <el-card shadow="never" class="mb-table-card">
      <el-table :data="list" stripe v-loading="loading" @sort-change="onSort" @row-click="openDetail" style="cursor:pointer">
        <el-table-column prop="id" label="单号" width="75" sortable="custom"/>
        <el-table-column label="设备" min-width="150">
          <template #default="{row}">
            <div class="device-info">
              <span class="di-name">{{ getDevName(row.deviceId) }}</span>
              <span class="di-asset" v-if="devAssets[row.deviceId]">{{ devAssets[row.deviceId] }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="purpose" label="目的" min-width="120" show-overflow-tooltip/>
        <el-table-column label="状态" width="100">
          <template #default="{row}">
            <el-tag :type="statusTag(row.status)" effect="dark" size="small">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="startTime" label="开始时间" width="145" sortable="custom">
          <template #default="{row}">{{ fmt(row.startTime) }}</template>
        </el-table-column>
        <el-table-column prop="endTime" label="结束时间" width="145" sortable="custom">
          <template #default="{row}">{{ fmt(row.endTime) }}</template>
        </el-table-column>
        <el-table-column label="逾期" width="75" sortable="custom" prop="overdueDays">
          <template #default="{row}">
            <span v-if="row.overdueDays" class="overdue-num">{{ row.overdueDays }}天</span>
            <span v-else style="color:#C0C4CC">-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{row}">
            <div class="action-btns" @click.stop>
              <el-button size="small" text type="primary" @click="openDetail(row)">详情</el-button>
              <el-button v-if="row.status==='APPROVED'||row.status==='BORROWING'" size="small" text type="success" @click="openPickup(row)">
                {{ row.pickupTime ? '补传照片' : '取走登记' }}
              </el-button>
              <el-button v-if="row.status==='BORROWING'||row.status==='OVERDUE'" size="small" text type="warning" @click="$router.push(`/borrows/${row.id}/return`)">归还</el-button>
              <el-popconfirm v-if="row.status==='PENDING_APPROVAL'" title="确定取消?" @confirm="doCancel(row.id)">
                <template #reference><el-button size="small" text type="danger">取消</el-button></template>
              </el-popconfirm>
            </div>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="!list.length && !loading" class="mb-empty">
        <el-empty description="暂无借用记录">
          <el-button type="primary" @click="$router.push('/devices')">去浏览设备</el-button>
        </el-empty>
      </div>
      <div class="mb-pagination" v-if="total>size">
        <el-pagination v-model:current-page="page" :page-size="size" :total="total" layout="total,prev,pager,next" @current-change="load"/>
      </div>
    </el-card>

    <!-- 详情抽屉 -->
    <el-drawer v-model="drawer.visible" title="借用详情" size="500px" destroy-on-close>
      <template v-if="drawer.row">
        <!-- 状态标签 -->
        <div class="dr-status">
          <el-tag :type="statusTag(drawer.row.status)" effect="dark" size="large">{{ statusText(drawer.row.status) }}</el-tag>
          <span v-if="drawer.row.overdueDays" class="dr-overdue">逾期 {{ drawer.row.overdueDays }} 天</span>
        </div>

        <!-- 基本信息 -->
        <el-descriptions :column="1" border size="small" class="dr-desc">
          <el-descriptions-item label="借用单号">{{ drawer.row.id }}</el-descriptions-item>
          <el-descriptions-item label="设备">{{ getDevName(drawer.row.deviceId) }}</el-descriptions-item>
          <el-descriptions-item label="目的">{{ drawer.row.purpose || '-' }}</el-descriptions-item>
          <el-descriptions-item label="目的大类">{{ drawer.row.purposeCategory || '-' }}</el-descriptions-item>
          <el-descriptions-item label="事由">{{ drawer.row.reason || '-' }}</el-descriptions-item>
          <el-descriptions-item label="计划开始">{{ fmt(drawer.row.startTime) }}</el-descriptions-item>
          <el-descriptions-item label="计划归还">{{ fmt(drawer.row.endTime) }}</el-descriptions-item>
        </el-descriptions>

        <!-- 借用生命周期时间线 -->
        <div class="dr-timeline">
          <h4 class="dr-section-title">借用进度</h4>
          <div class="tl-item" :class="{active: drawer.row.createTime}">
            <div class="tl-dot"></div>
            <div class="tl-content">
              <div class="tl-label">提交申请</div>
              <div class="tl-time">{{ fmt(drawer.row.createTime) }}</div>
            </div>
          </div>
          <div class="tl-item" :class="{active: drawer.row.status!=='PENDING_APPROVAL'&&drawer.row.status!=='REJECTED'}">
            <div class="tl-dot"></div>
            <div class="tl-content">
              <div class="tl-label">审批{{ drawer.row.status==='REJECTED'?'驳回':'通过' }}</div>
              <div class="tl-time">{{ drawer.row.status==='REJECTED'?'已驳回':(drawer.row.status==='PENDING_APPROVAL'?'待审批':'已通过') }}</div>
            </div>
          </div>
          <div class="tl-item" :class="{active: drawer.row.pickupTime || drawer.row.status==='BORROWING'||drawer.row.status==='RETURNED'||drawer.row.status==='OVERDUE'}">
            <div class="tl-dot"></div>
            <div class="tl-content">
              <div class="tl-label">取走设备</div>
              <div class="tl-time">{{ drawer.row.pickupTime ? fmt(drawer.row.pickupTime) : '尚未取走' }}</div>
              <!-- 取走照片 -->
              <div v-if="drawer.row.pickupImage" class="tl-photo">
                <el-image :src="imgUrl(drawer.row.pickupImage)" fit="cover" style="width:120px;height:80px;border-radius:6px" :preview-src-list="[imgUrl(drawer.row.pickupImage)]"/>
              </div>
              <el-upload v-if="canPickup(drawer.row)" :show-file-list="false" :http-request="(opt)=>doUpload(opt,'BORROW',drawer.row)" accept="image/*" style="margin-top:6px">
                <el-button size="small" text type="primary" :loading="drawer.uploading">📷 {{ drawer.row.pickupImage?'更换照片':'上传取走照片' }}</el-button>
              </el-upload>
              <el-button v-if="!drawer.row.pickupTime && canPickup(drawer.row)" size="small" type="success" @click="doPickup(drawer.row)" :loading="drawer.picking" style="margin-top:6px">确认取走</el-button>
            </div>
          </div>
          <div class="tl-item" :class="{active: drawer.row.status==='RETURNED'}">
            <div class="tl-dot"></div>
            <div class="tl-content">
              <div class="tl-label">归还设备</div>
              <div class="tl-time">{{ drawer.row.realReturnTime ? fmt(drawer.row.realReturnTime) : (drawer.row.status==='BORROWING'||drawer.row.status==='OVERDUE'?'待归还':'未到归还阶段') }}</div>
              <!-- 归还照片 -->
              <div v-if="drawer.returnImages.length" class="tl-photos">
                <el-image v-for="(url,i) in drawer.returnImages" :key="i" :src="imgUrl(url)" fit="cover" style="width:80px;height:60px;border-radius:6px;margin-right:6px;margin-top:6px" :preview-src-list="drawer.returnImages.map(u=>imgUrl(u))"/>
              </div>
            </div>
          </div>
        </div>
      </template>
    </el-drawer>

    <!-- 取走登记对话框 -->
    <el-dialog v-model="pickupDlg.visible" title="取走登记" width="440px" :close-on-click-modal="false" destroy-on-close>
      <el-form label-width="80px">
        <el-form-item label="借用单号"><el-tag>{{ pickupDlg.row?.id }}</el-tag></el-form-item>
        <el-form-item label="设备">{{ getDevName(pickupDlg.row?.deviceId) }}</el-form-item>
        <el-form-item label="取走照片">
          <el-upload :show-file-list="true" :http-request="(opt)=>doUploadSingle(opt,'BORROW')" :before-upload="checkFileSize" accept="image/*" list-type="picture-card" :limit="1">
            <el-icon><Plus/></el-icon>
          </el-upload>
          <div class="upload-hint">支持 jpg/png，自动压缩至 1MB 以内</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pickupDlg.visible=false">取消</el-button>
        <el-button type="primary" @click="confirmPickup" :loading="pickupDlg.loading">确认取走</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref,reactive,onMounted } from 'vue'
import { borrowApi } from '@/api/borrow'
import axios from '@/api/request'
import { ElMessage } from 'element-plus'
import { Plus,Clock,Loading,CircleCheck,WarningFilled } from '@element-plus/icons-vue'

// 状态
const loading=ref(false);const list=ref([]);const page=ref(1);const size=ref(20);const total=ref(0)
const activeTab=ref('')
const sortBy=ref('');const sortOrder=ref('desc')
const devNames=ref({});const devAssets=ref({})

// 统计
const stats=reactive({total:0,pending:0,borrowing:0,returned:0,overdue:0})

// 详情抽屉
const drawer=reactive({visible:false,row:null,returnImages:[],uploading:false,picking:false})

// 取走对话框
const pickupDlg=reactive({visible:false,row:null,loading:false})
const pickupFile=ref(null)

// MinIO图片URL（通过后端代理或直接访问）
function imgUrl(path){ return path ? `/api/v1/files/${encodeURIComponent(path)}` : '' }

// 状态映射
const stTags={PENDING_APPROVAL:'warning',APPROVED:'success',BORROWING:'',RETURNED:'info',REJECTED:'danger',CANCELLED:'info',OVERDUE:'danger'}
const stTexts={PENDING_APPROVAL:'待审批',APPROVED:'已通过',BORROWING:'借用中',RETURNED:'已归还',REJECTED:'已驳回',CANCELLED:'已取消',OVERDUE:'逾期'}
function statusTag(s){return stTags[s]||'info'}
function statusText(s){return stTexts[s]||s}
function fmt(t){return t?t.replace('T',' ').substring(0,16):''}
function getDevName(id){return devNames.value[id]||'设备#'+id}
function canPickup(row){return row&&(row.status==='APPROVED'||row.status==='BORROWING')}
function onSort({prop,order}){sortBy.value=prop;sortOrder.value=order||'desc';load()}

function checkFileSize(file){
  const maxSize=10*1024*1024 // 10MB before compression
  if(file.size>maxSize){ElMessage.warning('图片不能超过10MB');return false}
  return true
}

// 数据加载
async function load(){
  loading.value=true
  try{
    const{data}=await borrowApi.getMyBorrows({page:page.value,size:size.value,status:activeTab.value||undefined,sort:sortBy.value||undefined,order:sortOrder.value})
    list.value=data.records||[];total.value=data.total||0
    await loadDevNames(data.records||[])
  }catch(e){console.error('加载失败',e)}finally{loading.value=false}
}

async function loadDevNames(records){
  const ids=[...new Set((records||[]).map(r=>r.deviceId).filter(id=>id&&!devNames.value[id]))]
  if(!ids.length)return
  try{
    const{data}=await axios.get('/devices',{params:{page:1,size:200}});
    (data.records||[]).forEach(d=>{devNames.value[d.id]=d.name;devAssets.value[d.id]=d.assetNo})
  }catch{}
}

// 统计
async function loadStats(){
  try{
    const res=await borrowApi.getMyBorrows({page:1,size:1,status:''})
    stats.total=res.data?.total||0
    for(const st of ['PENDING_APPROVAL','BORROWING','RETURNED','OVERDUE']){
      const r=await borrowApi.getMyBorrows({page:1,size:1,status:st})
      stats[st==='PENDING_APPROVAL'?'pending':st.toLowerCase()]=r.data?.total||0
    }
  }catch{}
}

// 详情
async function openDetail(row){
  drawer.row=row;drawer.visible=true;drawer.returnImages=[]
  try{
    const{data}=await axios.get(`/borrows/${row.id}/images`)
    drawer.returnImages=data.returnImages||[]
    if(data.pickupImage && !row.pickupImage){row.pickupImage=data.pickupImage}
  }catch{}
}

// 取走登记
function openPickup(row){
  pickupDlg.row=row;pickupDlg.visible=true;pickupFile.value=null
}

async function doUploadSingle(opt, bizType){
  pickupFile.value=opt.file
  // 不做实际上传，等确认取走时一起上传
  return {url:''}
}

async function confirmPickup(){
  if(!pickupDlg.row)return
  pickupDlg.loading=true
  try{
    const fd=new FormData()
    if(pickupFile.value)fd.append('file',pickupFile.value)
    await axios.post(`/borrows/${pickupDlg.row.id}/pickup`,fd,{headers:{'Content-Type':'multipart/form-data'}})
    ElMessage.success('取走登记完成')
    pickupDlg.visible=false
    load();loadStats()
  }catch(e){ElMessage.error(e?.response?.data?.msg||'操作失败')}finally{pickupDlg.loading=false}
}

async function doPickup(row){
  drawer.picking=true
  try{
    await axios.post(`/borrows/${row.id}/pickup`)
    ElMessage.success('已确认取走')
    row.pickupTime=new Date().toISOString()
    row.status='BORROWING'
    load();loadStats()
  }catch(e){ElMessage.error(e?.response?.data?.msg||'操作失败')}finally{drawer.picking=false}
}

async function doUpload(opt, bizType, row){
  drawer.uploading=true
  try{
    const fd=new FormData();fd.append('file',opt.file);fd.append('bizType',bizType)
    const{data}=await axios.post(`/borrows/${row.id}/upload-image`,fd,{headers:{'Content-Type':'multipart/form-data'}})
    if(bizType==='BORROW'){row.pickupImage=data}
    ElMessage.success('照片已上传（自动压缩至1MB以内）')
  }catch(e){ElMessage.error(e?.response?.data?.msg||'上传失败')}finally{drawer.uploading=false}
}

async function doCancel(id){
  try{await borrowApi.cancel(id);ElMessage.success('已取消');load();loadStats()}catch(e){ElMessage.error(e?.response?.data?.msg||'取消失败')}
}

onMounted(()=>{load();loadStats()})
</script>

<style scoped>
.my-borrows{padding:24px;max-width:1200px;margin:0 auto}

/* 页头 */
.mb-header{display:flex;align-items:center;justify-content:space-between;margin-bottom:20px}
.mb-title{font-size:22px;font-weight:600;color:#303133;margin:0}

/* 统计卡片 */
.mb-stats{display:grid;grid-template-columns:repeat(4,1fr);gap:14px;margin-bottom:16px}
.stat-card{display:flex;align-items:center;gap:12px;background:#fff;padding:18px 20px;border-radius:10px;cursor:pointer;transition:transform 0.15s,box-shadow 0.15s;box-shadow:0 1px 6px rgba(0,0,0,0.05);position:relative;overflow:hidden}
.stat-card:hover{transform:translateY(-2px);box-shadow:0 4px 12px rgba(0,0,0,0.1)}
.stat-card .sc-bar{position:absolute;left:0;top:0;bottom:0;width:4px}
.s-pending .sc-bar{background:#E6A23C}.s-pending .sc-num{color:#E6A23C}
.s-borrowing .sc-bar{background:#409EFF}.s-borrowing .sc-num{color:#409EFF}
.s-returned .sc-bar{background:#67C23A}.s-returned .sc-num{color:#67C23A}
.s-overdue .sc-bar{background:#F56C6C}.s-overdue .sc-num{color:#F56C6C}
.sc-icon{width:42px;height:42px;border-radius:10px;display:flex;align-items:center;justify-content:center;background:#F5F7FA;color:#909399}
.sc-body{flex:1}.sc-num{font-size:28px;font-weight:700;line-height:1.2}.sc-label{font-size:12px;color:#909399;margin-top:2px}

/* 工具栏 */
.mb-toolbar{margin-bottom:14px}

/* 表格 */
.mb-table-card{border-radius:10px;overflow:hidden}
.device-info{display:flex;flex-direction:column}
.di-name{font-size:13px;color:#303133}.di-asset{font-size:11px;color:#909399}
.overdue-num{color:#F56C6C;font-weight:600}
.action-btns{display:flex;gap:2px;flex-wrap:wrap}
.mb-empty{padding:60px 0}
.mb-pagination{display:flex;justify-content:flex-end;margin-top:14px}

/* 抽屉 */
.dr-status{display:flex;align-items:center;gap:10px;margin-bottom:18px}
.dr-overdue{color:#F56C6C;font-weight:600;font-size:14px}
.dr-desc{margin-bottom:20px}
.dr-section-title{font-size:15px;font-weight:600;color:#303133;margin:0 0 14px 0}

/* 时间线 */
.dr-timeline{margin-top:8px}
.tl-item{display:flex;gap:14px;padding-bottom:20px;position:relative}
.tl-item:not(:last-child)::after{content:'';position:absolute;left:7px;top:22px;bottom:0;width:2px;background:#E4E7ED}
.tl-item.active:not(:last-child)::after{background:#409EFF}
.tl-dot{width:16px;height:16px;border-radius:50%;border:2px solid #DCDFE6;background:#fff;flex-shrink:0;margin-top:2px;transition:all 0.3s}
.tl-item.active .tl-dot{border-color:#409EFF;background:#409EFF;box-shadow:0 0 0 4px rgba(64,158,255,0.15)}
.tl-content{flex:1;min-width:0}
.tl-label{font-size:14px;font-weight:500;color:#303133}
.tl-time{font-size:12px;color:#909399;margin-top:2px}
.tl-photo,.tl-photos{margin-top:8px}
.tl-photos{display:flex;flex-wrap:wrap;gap:6px}

/* 上传提示 */
.upload-hint{font-size:11px;color:#909399;margin-top:4px}

/* 响应式 */
@media(max-width:768px){
  .mb-stats{grid-template-columns:repeat(2,1fr)}
  .mb-header{flex-direction:column;align-items:flex-start;gap:10px}
}
</style>
