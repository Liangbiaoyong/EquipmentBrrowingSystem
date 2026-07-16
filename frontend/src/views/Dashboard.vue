<template>
  <div class="dashboard">
    <div class="dash-header">
      <h2 class="dash-title">仪表盘</h2>
      <div class="dash-scope" v-if="showScopeToggle">
        <span class="scope-label">数据范围：</span>
        <el-radio-group v-model="scope" size="small" @change="onScopeChange">
          <el-radio-button value="personal">个人</el-radio-button>
          <el-radio-button value="global">全局</el-radio-button>
        </el-radio-group>
      </div>
    </div>

    <!-- 借还状态 — 5卡片均匀分布 -->
    <div class="section-header">
      <span class="section-dot section-dot-blue"></span>
      <h3 class="section-title">借还状态</h3>
      <span class="section-hint">设备借用与归还生命周期</span>
    </div>
    <div class="stat-grid five-col" v-loading="loading">
      <div class="stat-card" v-for="s in borrowStatusCards" :key="s.label"
           :style="{cursor:s.status!==undefined?'pointer':'default'}"
           @click="s.status!==undefined&&goDevices({borrowStatus:s.status})">
        <div class="sc-bar" :style="{background:s.color}"></div>
        <div class="sc-body">
          <div class="sc-icon">
            <el-icon :size="20" :color="s.color"><component :is="s.icon"/></el-icon>
          </div>
          <div class="sc-value" :style="{color:s.color}">{{ s.value }}</div>
          <div class="sc-label">{{ s.label }}</div>
        </div>
      </div>
    </div>

    <!-- 设备物理状态 — 5卡片均匀分布 -->
    <div class="section-header">
      <span class="section-dot section-dot-amber"></span>
      <h3 class="section-title">设备物理状态</h3>
      <span class="section-hint">设备硬件健康状况</span>
    </div>
    <div class="stat-grid five-col">
      <div class="stat-card phys-card" v-for="s in deviceStatusCards" :key="s.label"
           style="cursor:pointer" @click="goDevices({deviceStatus:s.status})">
        <div class="sc-bar" :style="{background:s.color}"></div>
        <div class="sc-body">
          <span class="phys-dot" :style="{background:s.color}"></span>
          <div class="sc-value" :style="{color:s.color}">{{ s.value }}</div>
          <div class="sc-label">{{ s.label }}</div>
        </div>
      </div>
    </div>

    <!-- 借用概览 + 趋势 -->
    <el-row :gutter="16" style="margin-top:20px">
      <el-col :span="12">
        <div class="overview-panel">
          <div class="section-header" style="margin-bottom:12px">
            <span class="section-dot section-dot-green"></span>
            <h3 class="section-title">借用概览</h3>
          </div>
          <div class="overview-grid">
            <div class="ov-item" v-for="s in borrowCards" :key="s.label">
              <div class="ov-value" :style="{color:s.color}">{{ s.value }}</div>
              <div class="ov-label">{{ s.label }}</div>
            </div>
          </div>
        </div>
      </el-col>
      <el-col :span="12">
        <div class="overview-panel">
          <div class="section-header" style="margin-bottom:12px">
            <span class="section-dot section-dot-blue"></span>
            <h3 class="section-title">本月借用趋势</h3>
          </div>
          <div class="trend-bars" v-if="trendData.length">
            <div v-for="t in trendData" :key="t.date" class="trend-item">
              <div class="trend-fill" :style="{height:Math.max(3,t.count/maxT*60)+'px'}"></div>
              <span class="trend-date">{{ t.date.substring(5) }}</span>
              <span class="trend-count">{{ t.count }}</span>
            </div>
          </div>
          <el-empty v-else description="暂无数据" :image-size="50"/>
        </div>
      </el-col>
    </el-row>

    <!-- 快捷入口 -->
    <div class="quick-actions">
      <el-button type="primary" @click="$router.push('/devices')"><el-icon><Search/></el-icon> 浏览设备</el-button>
      <el-button type="success" @click="$router.push('/borrows/create')"><el-icon><Plus/></el-icon> 借用申请</el-button>
      <el-button @click="$router.push('/borrows/my')"><el-icon><List/></el-icon> 我的借用</el-button>
    </div>
  </div>
</template>

<script setup>
import {ref,reactive,onMounted,computed,watch} from 'vue'
import {useRouter} from 'vue-router'
import {useUserStore} from '@/store/user'
import {statsApi} from '@/api/statistics'
import {Monitor,CircleCheck,Clock,WarningFilled,RemoveFilled,Cpu,Setting,CircleClose,DeleteFilled,Search,Plus,List} from '@element-plus/icons-vue'

const router=useRouter();const userStore=useUserStore();const loading=ref(true);const trendData=ref([]);const maxT=ref(1)

// 全局/个人切换 — 教师/实验室管理员/系统管理员可见
const userType=computed(()=>userStore.userInfo?.userType)
const showScopeToggle=computed(()=>[1,2,3].includes(userType.value))
// 默认：教师→个人，管理员→全局；优先从localStorage恢复
const scope=ref(localStorage.getItem('statsScope')||(userType.value===1?'personal':'global'))
function onScopeChange(val){localStorage.setItem('statsScope',val);loadAll()}

// 个人模式跳转设备列表时附带custodian筛选
function goDevices(params){
  if(scope.value==='personal'&&userStore.userInfo?.realName){
    params.custodian=userStore.userInfo.realName
  }
  router.push({path:'/devices',query:params})
}

const borrowStatusCards=reactive([
  {label:'设备总数',value:'-',color:'#909399',icon:Monitor,status:null},
  {label:'可借用',value:'-',color:'#67C23A',icon:CircleCheck,status:1},
  {label:'借用中',value:'-',color:'#409EFF',icon:Clock,status:2},
  {label:'不可借',value:'-',color:'#E6A23C',icon:WarningFilled,status:3},
  {label:'逾期',value:'-',color:'#F56C6C',icon:RemoveFilled,status:4}
])

const deviceStatusCards=reactive([
  {label:'正常',value:'-',color:'#67C23A',status:1},
  {label:'待维修',value:'-',color:'#E6A23C',status:2},
  {label:'无法维修',value:'-',color:'#F56C6C',status:3},
  {label:'待报废',value:'-',color:'#909399',status:4},
  {label:'已报废',value:'-',color:'#C0C4CC',status:5}
])

const borrowCards=reactive([
  {label:'借出中',value:'-',color:'#409EFF'},
  {label:'逾期未还',value:'-',color:'#F56C6C'},
  {label:'待审批',value:'-',color:'#E6A23C'},
  {label:'总借用',value:'-',color:'#67C23A'}
])

async function loadAll(){
  loading.value=true
  try{
    const{data:ov}=await statsApi.overview(scope.value)
    const ds=ov.deviceStats;const bs=ov.borrowStats
    borrowStatusCards[0].value=ds.total||0
    borrowStatusCards[1].value=ds.borrowAvailable||ds.available||0
    borrowStatusCards[2].value=ds.borrowBorrowing||ds.borrowing||0
    borrowStatusCards[3].value=ds.borrowUnavailable||0
    borrowStatusCards[4].value=ds.borrowOverdue||0
    deviceStatusCards[0].value=ds.deviceNormal||0
    deviceStatusCards[1].value=ds.devicePendingRepair||0
    deviceStatusCards[2].value=ds.deviceRepairing||ds.repair||0
    deviceStatusCards[3].value=ds.devicePendingScrap||0
    deviceStatusCards[4].value=ds.deviceScrapped||0
    borrowCards[0].value=bs.borrowing||0
    borrowCards[1].value=bs.overdue||0
    borrowCards[2].value=bs.pendingApproval||0
    borrowCards[3].value=bs.total||0
  }catch{}finally{loading.value=false}
  try{const{data:td}=await statsApi.trend(scope.value);trendData.value=td||[];maxT.value=Math.max(1,...trendData.value.map(t=>t.count||0))}catch{}
}
onMounted(loadAll)
</script>

<style scoped>
.dashboard{padding:24px;max-width:1100px;margin:0 auto}
.dash-header{display:flex;align-items:center;justify-content:space-between;margin-bottom:20px;flex-wrap:wrap;gap:12px}
.dash-title{font-size:22px;font-weight:600;color:#303133;margin:0}
.dash-scope{display:flex;align-items:center;gap:8px}
.scope-label{font-size:13px;color:#606266;white-space:nowrap}

/* 区域标题 */
.section-header{display:flex;align-items:center;gap:8px;margin-bottom:12px}
.section-dot{width:8px;height:8px;border-radius:50%;flex-shrink:0}
.section-dot-blue{background:#409EFF}.section-dot-amber{background:#E6A23C}.section-dot-green{background:#67C23A}
.section-title{font-size:15px;font-weight:600;color:#303133;margin:0}
.section-hint{font-size:12px;color:#909399}

/* 5列均匀网格 */
.stat-grid.five-col{display:grid;grid-template-columns:repeat(5,1fr);gap:12px;margin-bottom:4px}

/* 状态卡片 */
.stat-card{background:#fff;border-radius:8px;overflow:hidden;box-shadow:0 1px 4px rgba(0,0,0,0.06);transition:transform 0.15s,box-shadow 0.15s}
.stat-card:hover{transform:translateY(-2px);box-shadow:0 4px 10px rgba(0,0,0,0.1)}
.sc-bar{height:3px}
.sc-body{padding:16px 12px 14px;text-align:center}
.sc-icon{margin-bottom:6px}
.sc-value{font-size:28px;font-weight:700;line-height:1.2}
.sc-label{font-size:12px;color:#909399;margin-top:4px}

/* 物理状态卡片微差异 */
.phys-card .sc-bar{height:3px;opacity:0.6}
.phys-dot{width:10px;height:10px;border-radius:50%;display:inline-block;margin-bottom:8px}

/* 借用概览 */
.overview-panel{background:#fff;border-radius:8px;padding:16px 20px;box-shadow:0 1px 4px rgba(0,0,0,0.06);height:100%}
.overview-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:8px}
.ov-item{text-align:center;padding:10px 4px}
.ov-value{font-size:24px;font-weight:700}
.ov-label{font-size:12px;color:#909399;margin-top:4px}

/* 趋势图 */
.trend-bars{display:flex;align-items:flex-end;justify-content:space-around;height:80px;gap:2px}
.trend-item{display:flex;flex-direction:column;align-items:center;flex:1;min-width:0}
.trend-fill{width:14px;background:linear-gradient(180deg,#409EFF,#66B1FF);border-radius:3px 3px 0 0;min-height:2px;transition:height 0.4s}
.trend-date{font-size:9px;color:#909399;margin-top:3px;white-space:nowrap}
.trend-count{font-size:10px;color:#606266;font-weight:600;margin-top:1px}

/* 快捷入口 */
.quick-actions{display:flex;gap:10px;justify-content:center;margin-top:20px;padding-top:16px;border-top:1px solid #EBEEF5}

/* 响应式 */
@media(max-width:768px){
  .stat-grid.five-col{grid-template-columns:repeat(3,1fr)}
  .overview-grid{grid-template-columns:repeat(2,1fr)}
}
@media(max-width:480px){
  .stat-grid.five-col{grid-template-columns:repeat(2,1fr)}
}
</style>
