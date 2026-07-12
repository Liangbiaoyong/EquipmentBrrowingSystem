<template>
  <div class="stats-dashboard">
    <!-- 页面标题 + 筛选栏 -->
    <div class="stats-toolbar">
      <h2 class="stats-title">数据统计</h2>
      <div class="stats-filters">
        <el-select v-model="filterCategory" placeholder="设备分类" clearable style="width:160px" @change="onFilterChange">
          <el-option v-for="c in categoryOptions" :key="c.id" :label="c.name" :value="c.id"/>
        </el-select>
        <el-date-picker v-model="filterDates" type="daterange" range-separator="至"
          start-placeholder="开始日期" end-placeholder="结束日期" format="YYYY-MM-DD" value-format="YYYY-MM-DD"
          style="width:260px" @change="onFilterChange"/>
        <el-dropdown @command="doExport" style="margin-left:8px">
          <el-button type="primary" :icon="Download">导出报表<el-icon><ArrowDown/></el-icon></el-button>
          <template #dropdown><el-dropdown-menu><el-dropdown-item command="csv">CSV 格式</el-dropdown-item><el-dropdown-item command="xlsx">Excel 格式</el-dropdown-item></el-dropdown-menu></template>
        </el-dropdown>
      </div>
    </div>

    <!-- KPI 统计卡片 -->
    <div class="kpi-row">
      <div class="kpi-card kpi-total">
        <div class="kpi-icon"><el-icon :size="28"><Monitor/></el-icon></div>
        <div class="kpi-body"><div class="kpi-value">{{ overview.deviceTotal || 0 }}</div><div class="kpi-label">设备总数</div></div>
      </div>
      <div class="kpi-card kpi-available">
        <div class="kpi-icon"><el-icon :size="28"><CircleCheck/></el-icon></div>
        <div class="kpi-body"><div class="kpi-value">{{ overview.borrowAvailable || 0 }}</div><div class="kpi-label">可借用设备</div></div>
      </div>
      <div class="kpi-card kpi-borrowing">
        <div class="kpi-icon"><el-icon :size="28"><Clock/></el-icon></div>
        <div class="kpi-body"><div class="kpi-value">{{ overview.borrowing || 0 }}</div><div class="kpi-label">借用中</div></div>
      </div>
      <div class="kpi-card kpi-pending">
        <div class="kpi-icon"><el-icon :size="28"><Bell/></el-icon></div>
        <div class="kpi-body"><div class="kpi-value">{{ overview.pendingApproval || 0 }}</div><div class="kpi-label">待审批</div></div>
      </div>
    </div>

    <!-- Tab 切换 -->
    <el-card shadow="never" class="tab-card">
      <el-tabs v-model="activeTab" @tab-change="switchTab">
        <el-tab-pane label="概览" name="overview"/>
        <el-tab-pane label="借用趋势" name="trend"/>
        <el-tab-pane label="热门设备" name="topDevices"/>
        <el-tab-pane label="高频用户" name="topUsers"/>
        <el-tab-pane label="分类利用率" name="utilization"/>
        <el-tab-pane label="目的分布" name="purposes"/>
        <el-tab-pane label="成果统计" name="outcomes"/>
      </el-tabs>

      <div v-loading="loading" class="tab-content">
        <!-- 概览 -->
        <div v-if="activeTab==='overview'" class="overview-grid">
          <div class="ov-section">
            <h3 class="section-title"><span class="dot dot-blue"></span>借还状态分布</h3>
            <div class="status-grid">
              <div class="status-item"><span class="s-dot s-green"></span><span class="s-label">可借用</span><span class="s-val">{{ overview.borrowAvailable || 0 }}</span></div>
              <div class="status-item"><span class="s-dot s-blue"></span><span class="s-label">借用中</span><span class="s-val">{{ overview.borrowBorrowing || 0 }}</span></div>
              <div class="status-item"><span class="s-dot s-amber"></span><span class="s-label">不可借</span><span class="s-val">{{ overview.borrowUnavailable || 0 }}</span></div>
              <div class="status-item"><span class="s-dot s-red"></span><span class="s-label">逾期</span><span class="s-val">{{ overview.borrowOverdue || 0 }}</span></div>
            </div>
          </div>
          <div class="ov-section">
            <h3 class="section-title"><span class="dot dot-amber"></span>设备物理状态</h3>
            <div class="status-grid">
              <div class="status-item"><span class="s-dot s-green"></span><span class="s-label">正常</span><span class="s-val">{{ overview.deviceNormal || 0 }}</span></div>
              <div class="status-item"><span class="s-dot s-amber"></span><span class="s-label">待维修</span><span class="s-val">{{ overview.devicePendingRepair || 0 }}</span></div>
              <div class="status-item"><span class="s-dot s-blue"></span><span class="s-label">维修中</span><span class="s-val">{{ overview.deviceRepairing || 0 }}</span></div>
              <div class="status-item"><span class="s-dot s-red"></span><span class="s-label">待报废</span><span class="s-val">{{ overview.devicePendingScrap || 0 }}</span></div>
              <div class="status-item"><span class="s-dot s-grey"></span><span class="s-label">已报废</span><span class="s-val">{{ overview.deviceScrapped || 0 }}</span></div>
            </div>
          </div>
          <div class="ov-section ov-full">
            <h3 class="section-title"><span class="dot dot-blue"></span>借用概况</h3>
            <div class="borrow-summary">
              <div class="bs-card"><div class="bs-num primary">{{ overview.borrowing || 0 }}</div><div class="bs-label">借出中</div></div>
              <div class="bs-card"><div class="bs-num danger">{{ overview.overdue || 0 }}</div><div class="bs-label">逾期未还</div></div>
              <div class="bs-card"><div class="bs-num warning">{{ overview.pendingApproval || 0 }}</div><div class="bs-label">待审批</div></div>
              <div class="bs-card"><div class="bs-num info">{{ overview.totalBorrows || overview.borrowTotal || 0 }}</div><div class="bs-label">总借用次数</div></div>
            </div>
          </div>
        </div>

        <!-- 借用趋势 / 分类利用率 — 柱状图 -->
        <div v-else-if="activeTab==='trend'" class="chart-section">
          <h3 class="section-title"><span class="dot dot-blue"></span>本月每日借用趋势</h3>
          <div v-if="chartData.length" class="bar-chart-vertical">
            <div class="bar-col" v-for="(d,i) in chartData" :key="i"
                 :style="{height:Math.max(d.pct,2)+'%'}" :title="d.label + ': ' + d.value">
              <span class="bar-val">{{ d.value }}</span>
            </div>
            <div class="bar-labels"><span v-for="(d,i) in chartData" :key="i">{{ d.label.slice(-2) }}</span></div>
          </div>
          <el-empty v-else description="暂无数据"/>
        </div>

        <!-- 排行 — 横向柱状图 -->
        <div v-else-if="['topDevices','topUsers','utilization'].includes(activeTab)" class="chart-section">
          <h3 class="section-title">
            <span class="dot" :class="activeTab==='utilization'?'dot-amber':'dot-blue'"></span>
            {{ {topDevices:'热门设备借用排行',topUsers:'高频用户借用排行',utilization:'设备分类利用率'}[activeTab] }}
          </h3>
          <div v-if="chartData.length" class="h-bar-list">
            <div class="h-bar-row" v-for="(d,i) in chartData" :key="i">
              <span class="h-bar-rank" :class="'rank-'+(i+1)">{{ i+1 }}</span>
              <span class="h-bar-label">{{ d.label }}</span>
              <div class="h-bar-track"><div class="h-bar-fill" :style="{width:d.pct+'%',background:barColor(i)}"></div></div>
              <span class="h-bar-value">{{ d.value }}</span>
            </div>
          </div>
          <el-empty v-else description="暂无数据"/>
        </div>

        <!-- 目的分布 — 环形图 + 子分类 -->
        <div v-else-if="activeTab==='purposes'" class="chart-section">
          <div class="dual-chart">
            <div class="dc-left">
              <h3 class="section-title"><span class="dot dot-blue"></span>目的大类分布</h3>
              <DonutChart v-if="chartData.length" :data="chartData" :colors="donutColors"/>
              <el-empty v-else description="暂无数据"/>
            </div>
            <div class="dc-right">
              <h3 class="section-title"><span class="dot dot-amber"></span>{{ purposeDetailCat || '子分类' }}</h3>
              <div v-if="purposeDetail.length" class="h-bar-list compact">
                <div class="h-bar-row" v-for="(d,i) in purposeDetail" :key="i">
                  <span class="h-bar-label">{{ d.name }}</span>
                  <div class="h-bar-track"><div class="h-bar-fill" :style="{width:Math.max(d.pct||5,2)+'%',background:barColor(i)}"></div></div>
                  <span class="h-bar-value">{{ d.value }}</span>
                </div>
              </div>
              <el-empty v-else description="暂无详细数据"/>
            </div>
          </div>
        </div>

        <!-- 成果统计 — 环形图 + 设备排行 + 月度趋势 -->
        <div v-else-if="activeTab==='outcomes'" class="chart-section">
          <div class="outcome-total" v-if="outcomeTotal > 0">
            <span class="ot-badge">累计成果</span>
            <span class="ot-num">{{ outcomeTotal }}</span>
            <span class="ot-unit">项</span>
          </div>
          <div class="dual-chart">
            <div class="dc-left">
              <h3 class="section-title"><span class="dot dot-blue"></span>成果类型分布</h3>
              <DonutChart v-if="chartData.length" :data="chartData" :colors="donutColors"/>
              <el-empty v-else description="暂无成果数据"/>
            </div>
            <div class="dc-right">
              <h3 class="section-title"><span class="dot dot-green"></span>成果设备排行</h3>
              <div v-if="outcomeTopDevices.length" class="h-bar-list compact">
                <div class="h-bar-row" v-for="(d,i) in outcomeTopDevices" :key="i">
                  <span class="h-bar-rank" :class="'rank-'+(i+1)">{{ i+1 }}</span>
                  <span class="h-bar-label">{{ d.name }}</span>
                  <div class="h-bar-track"><div class="h-bar-fill" :style="{width:Math.max(topDevPct(d.value),2)+'%',background:barColor(i)}"></div></div>
                  <span class="h-bar-value">{{ d.value }}</span>
                </div>
              </div>
              <el-empty v-else description="暂无排行数据"/>
            </div>
          </div>
          <!-- 月度趋势 -->
          <div v-if="outcomeMonthTrend.length" style="margin-top:24px">
            <h3 class="section-title"><span class="dot dot-amber"></span>成果月度趋势</h3>
            <div class="bar-chart-vertical">
              <div class="bar-col" v-for="(d,i) in outcomeMonthTrend" :key="i"
                   :style="{height:Math.max(monthBarPct(d.value),2)+'%'}" :title="d.name + ': ' + d.value">
                <span class="bar-val">{{ d.value }}</span>
              </div>
              <div class="bar-labels"><span v-for="(d,i) in outcomeMonthTrend" :key="i">{{ d.name.slice(-2) }}月</span></div>
            </div>
          </div>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref,reactive,computed,onMounted } from 'vue'
import { statsApi } from '@/api/statistics'
import axios from '@/api/request'
import { Download,Monitor,CircleCheck,Clock,Bell,ArrowDown } from '@element-plus/icons-vue'

// ==================== 状态 ====================
const activeTab = ref('overview')
const loading = ref(false)
const chartData = ref([])
const filterCategory = ref(null)
const filterDates = ref([])
const categoryOptions = ref([])

// 概览数据
const overview = reactive({
  deviceTotal:0, borrowAvailable:0, borrowBorrowing:0, borrowUnavailable:0, borrowOverdue:0,
  deviceNormal:0, devicePendingRepair:0, deviceRepairing:0, devicePendingScrap:0, deviceScrapped:0,
  borrowing:0, overdue:0, pendingApproval:0, totalBorrows:0
})

// 目的分布详情
const purposeDetail = ref([])
const purposeDetailCat = ref('子分类分布')

// 成果统计
const outcomeTotal = ref(0)
const outcomeTopDevices = ref([])
const outcomeMonthTrend = ref([])

// 环形图配色（16种）
const donutColors = ['#409EFF','#67C23A','#E6A23C','#F56C6C','#909399','#337ECC','#95D475','#F3D19E',
  '#F89898','#B3B3B3','#529B2E','#B88230','#C45656','#73767A','#5A9CF8','#85CE61']

// ==================== 工具函数 ====================
function barColor(i) {
  const cols = ['#409EFF','#67C23A','#E6A23C','#F56C6C','#909399','#337ECC','#95D475','#F3D19E','#F89898','#B3B3B3']
  return cols[i % cols.length]
}

function topDevPct(val) {
  const max = Math.max(...outcomeTopDevices.value.map(d=>d.value||0), 1)
  return Math.round(val / max * 100)
}

function monthBarPct(val) {
  const max = Math.max(...outcomeMonthTrend.value.map(d=>d.value||0), 1)
  return Math.round(val / max * 100)
}

function getFilterParams() {
  const p = {}
  if (filterDates.value && filterDates.value.length === 2) {
    p.startDate = filterDates.value[0]
    p.endDate = filterDates.value[1]
  }
  if (filterCategory.value) p.categoryId = filterCategory.value
  return p
}

function onFilterChange() { switchTab(activeTab.value) }

// ==================== 数据加载 ====================
async function switchTab(tab) {
  loading.value = true
  try {
    if (tab === 'overview') {
      const { data } = await statsApi.overview()
      const ds = data.deviceStats || {}, bs = data.borrowStats || {}
      Object.assign(overview, {
        deviceTotal: ds.total || 0, borrowAvailable: ds.borrowAvailable || 0,
        borrowBorrowing: ds.borrowBorrowing || 0, borrowUnavailable: ds.borrowUnavailable || 0,
        borrowOverdue: ds.borrowOverdue || 0, deviceNormal: ds.deviceNormal || 0,
        devicePendingRepair: ds.devicePendingRepair || 0, deviceRepairing: ds.deviceRepairing || 0,
        devicePendingScrap: ds.devicePendingScrap || 0, deviceScrapped: ds.deviceScrapped || 0,
        borrowing: bs.borrowing || 0, overdue: bs.overdue || 0,
        pendingApproval: bs.pendingApproval || 0, totalBorrows: bs.total || 0
      })
    } else if (tab === 'trend') {
      const { data } = await statsApi.trend()
      const arr = Array.isArray(data) ? data : []
      const max = Math.max(...arr.map(r => r.count || 0), 1)
      chartData.value = arr.map(r => ({ label: r.date || '', value: r.count || 0, pct: Math.round((r.count || 0) / max * 100) }))
    } else if (tab === 'topDevices') {
      const { data } = await statsApi.topDevices()
      const arr = Array.isArray(data) ? data : []
      const max = Math.max(...arr.map(r => r.borrowCount || 0), 1)
      chartData.value = arr.slice(0, 10).map(r => ({ label: r.deviceName || '未知', value: r.borrowCount || 0, pct: Math.round((r.borrowCount || 0) / max * 100) }))
    } else if (tab === 'topUsers') {
      const { data } = await statsApi.topUsers()
      const arr = Array.isArray(data) ? data : []
      const max = Math.max(...arr.map(r => r.borrowCount || 0), 1)
      chartData.value = arr.slice(0, 10).map(r => ({ label: r.userName || '未知', value: r.borrowCount || 0, pct: Math.round((r.borrowCount || 0) / max * 100) }))
    } else if (tab === 'utilization') {
      const { data } = await statsApi.utilization()
      const arr = Array.isArray(data) ? data : []
      const max = Math.max(...arr.map(r => r.borrowCount || 0), 1)
      chartData.value = arr.slice(0, 10).map(r => ({ label: r.categoryName || '未知', value: r.borrowCount || 0, pct: Math.round((r.borrowCount || 0) / max * 100) }))
    } else if (tab === 'purposes') {
      const p = getFilterParams()
      const { data } = await statsApi.purposes(p.startDate, p.endDate, p.categoryId)
      const arr = Array.isArray(data) ? data : []
      const max = Math.max(...arr.map(r => r.value || 0), 1)
      chartData.value = arr.slice(0, 10).map(r => ({ name: r.name, value: r.value || 0, pct: Math.round((r.value || 0) / max * 100) }))
      // 加载详细子分类
      try {
        const detail = await statsApi.purposeDetail(p.startDate, p.endDate, p.categoryId)
        const sub = (detail.data?.subcategories || []).map(r => ({ name: r.name, value: r.value || 0 }))
        const maxSub = Math.max(...sub.map(r => r.value || 0), 1)
        const catData = detail.data?.byDeviceCategory || []
        if (catData.length > 0) {
          purposeDetailCat.value = '按设备分类'
          purposeDetail.value = catData.map(r => ({ name: r.name, value: r.value || 0, pct: Math.round((r.value || 0) / Math.max(...catData.map(x=>x.value||0), 1) * 100) }))
        } else {
          purposeDetailCat.value = '子分类分布'
          purposeDetail.value = sub.map(r => ({ name: r.name, value: r.value || 0, pct: Math.round((r.value || 0) / maxSub * 100) }))
        }
      } catch { purposeDetail.value = [] }
    } else if (tab === 'outcomes') {
      const p = getFilterParams()
      const { data } = await statsApi.outcomeStats(null, p.startDate, p.endDate)
      outcomeTotal.value = data.outcomeTotal || 0
      const dist = (data.distribution || []).map(r => ({ name: r.name, value: r.value || 0 }))
      const maxDist = Math.max(...dist.map(r => r.value || 0), 1)
      chartData.value = dist.map(r => ({ name: r.name, value: r.value || 0, pct: Math.round((r.value || 0) / maxDist * 100) }))
      outcomeTopDevices.value = (data.deviceTop10 || []).slice(0, 10)
      outcomeMonthTrend.value = data.monthTrend || []
    }
  } catch (e) {
    console.error('加载统计数据失败', e)
  } finally {
    loading.value = false
  }
}

function doExport(format) {
  const ext = format === 'xlsx' ? 'xlsx' : 'csv'
  const mime = ext === 'xlsx' ? 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' : 'text/csv'
  statsApi.exportCsv(format).then((r) => {
    const blob = new Blob([r.data], { type: mime })
    const a = document.createElement('a')
    a.href = URL.createObjectURL(blob)
    a.download = `统计报表_${new Date().toISOString().slice(0,10)}.${ext}`
    a.click()
  }).catch(() => {})
}

// ==================== 初始化 ====================
onMounted(async () => {
  switchTab('overview')
  // 加载设备分类列表
  try {
    const { data } = await axios.get('/categories?page=1&size=100')
    categoryOptions.value = (data?.records || [])
  } catch {}
})
</script>

<!-- ==================== 环形图组件 ==================== -->
<script>
import { h, defineComponent } from 'vue'
const DonutChart = defineComponent({
  name: 'DonutChart',
  props: { data: Array, colors: Array },
  setup(props) {
    return () => {
      const total = props.data.reduce((s, d) => s + (d.value || 0), 0) || 1
      const cx = 120, cy = 120, r = 90, strokeWidth = 28
      const circumference = 2 * Math.PI * r
      let offset = 0
      const slices = []
      const labels = []

      props.data.forEach((d, i) => {
        const pct = d.value / total
        const dashLen = pct * circumference
        slices.push(h('circle', {
          cx, cy, r, fill: 'none',
          stroke: props.colors[i % props.colors.length],
          'stroke-width': strokeWidth,
          'stroke-dasharray': `${dashLen} ${circumference - dashLen}`,
          'stroke-dashoffset': -offset,
          style: { transform: 'rotate(-90deg)', transformOrigin: `${cx}px ${cy}px`, transition: 'stroke-dasharray 0.5s ease' },
          key: i
        }))
        // 中心文字
        if (i === 0) {
          labels.push(h('text', { x: cx, y: cy - 10, 'text-anchor': 'middle', fill: '#303133', 'font-size': '22', 'font-weight': '700' }, total))
          labels.push(h('text', { x: cx, y: cy + 16, 'text-anchor': 'middle', fill: '#909399', 'font-size': '13' }, '总计'))
        }
        offset += dashLen
      })

      // 图例
      const legendItems = props.data.slice(0, 8).map((d, i) =>
        h('div', { class: 'donut-legend-item', key: 'l' + i }, [
          h('span', { class: 'donut-legend-dot', style: { background: props.colors[i % props.colors.length] } }),
          h('span', { class: 'donut-legend-label' }, d.name),
          h('span', { class: 'donut-legend-val' }, `${d.value} (${Math.round(d.value / total * 100)}%)`)
        ])
      )

      return h('div', { class: 'donut-chart-wrap' }, [
        h('svg', { viewBox: '0 0 240 240', class: 'donut-svg' }, [...slices, ...labels]),
        h('div', { class: 'donut-legend' }, legendItems)
      ])
    }
  }
})
</script>

<script>
export default { components: { DonutChart } }
</script>

<style scoped>
/* ===== 容器 ===== */
.stats-dashboard { padding: 20px; max-width: 1280px; margin: 0 auto; }

/* ===== 工具栏 ===== */
.stats-toolbar { display: flex; align-items: center; gap: 16px; margin-bottom: 20px; flex-wrap: wrap; }
.stats-title { font-size: 22px; font-weight: 600; color: #303133; margin: 0; white-space: nowrap; }
.stats-filters { display: flex; align-items: center; gap: 10px; margin-left: auto; flex-wrap: wrap; }

/* ===== KPI 卡片 ===== */
.kpi-row { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; margin-bottom: 20px; }
.kpi-card { display: flex; align-items: center; gap: 14px; padding: 20px 22px; border-radius: 10px; background: #fff; box-shadow: 0 1px 6px rgba(0,0,0,0.06); transition: transform 0.2s, box-shadow 0.2s; cursor: default; }
.kpi-card:hover { transform: translateY(-2px); box-shadow: 0 4px 12px rgba(0,0,0,0.1); }
.kpi-icon { width: 52px; height: 52px; border-radius: 12px; display: flex; align-items: center; justify-content: center; color: #fff; flex-shrink: 0; }
.kpi-total .kpi-icon { background: linear-gradient(135deg, #409EFF, #337ECC); }
.kpi-available .kpi-icon { background: linear-gradient(135deg, #67C23A, #529B2E); }
.kpi-borrowing .kpi-icon { background: linear-gradient(135deg, #409EFF, #5A9CF8); }
.kpi-pending .kpi-icon { background: linear-gradient(135deg, #E6A23C, #D08C1A); }
.kpi-body { flex: 1; min-width: 0; }
.kpi-value { font-size: 30px; font-weight: 700; color: #303133; line-height: 1.1; }
.kpi-label { font-size: 13px; color: #909399; margin-top: 2px; }

/* ===== Tab 卡片 ===== */
.tab-card { border-radius: 10px; box-shadow: 0 1px 6px rgba(0,0,0,0.06); }
.tab-content { min-height: 300px; padding: 8px 0; }

/* ===== 概览网格 ===== */
.overview-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 20px; }
.ov-section { background: #F8FAFC; border-radius: 10px; padding: 18px 20px; }
.ov-full { grid-column: 1 / -1; }
.section-title { font-size: 15px; font-weight: 600; color: #303133; margin: 0 0 14px 0; display: flex; align-items: center; gap: 8px; }
.dot { width: 8px; height: 8px; border-radius: 50%; display: inline-block; flex-shrink: 0; }
.dot-blue { background: #409EFF; }
.dot-green { background: #67C23A; }
.dot-amber { background: #E6A23C; }
.dot-red { background: #F56C6C; }
.dot-grey { background: #909399; }

/* 状态网格 */
.status-grid { display: flex; flex-wrap: wrap; gap: 8px; }
.status-item { display: flex; align-items: center; gap: 6px; background: #fff; padding: 8px 14px; border-radius: 8px; box-shadow: 0 0 0 1px #EBEEF5; }
.s-dot { width: 10px; height: 10px; border-radius: 50%; flex-shrink: 0; }
.s-green { background: #67C23A; } .s-blue { background: #409EFF; } .s-amber { background: #E6A23C; } .s-red { background: #F56C6C; } .s-grey { background: #909399; }
.s-label { font-size: 13px; color: #606266; }
.s-val { font-size: 15px; font-weight: 700; color: #303133; margin-left: auto; }

/* 借用概况 */
.borrow-summary { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; }
.bs-card { text-align: center; background: #fff; padding: 16px 12px; border-radius: 8px; box-shadow: 0 0 0 1px #EBEEF5; }
.bs-num { font-size: 28px; font-weight: 700; }
.bs-num.primary { color: #409EFF; } .bs-num.danger { color: #F56C6C; } .bs-num.warning { color: #E6A23C; } .bs-num.info { color: #909399; }
.bs-label { font-size: 13px; color: #909399; margin-top: 4px; }

/* ===== 竖向柱状图 ===== */
.bar-chart-vertical { display: flex; align-items: flex-end; gap: 6px; height: 200px; padding: 0 4px; position: relative; }
.bar-col { flex: 1; background: linear-gradient(180deg, #409EFF, #66B1FF); border-radius: 4px 4px 0 0; min-width: 20px; position: relative; transition: height 0.5s ease; display: flex; justify-content: center; }
.bar-col:nth-child(odd) { background: linear-gradient(180deg, #67C23A, #95D475); }
.bar-val { position: absolute; top: -20px; font-size: 11px; color: #606266; font-weight: 600; }
.bar-labels { display: flex; gap: 6px; margin-top: 8px; }
.bar-labels span { flex: 1; text-align: center; font-size: 11px; color: #909399; min-width: 20px; }

/* ===== 横向柱状图排行 ===== */
.h-bar-list { max-width: 700px; }
.h-bar-list.compact { max-width: 100%; }
.h-bar-row { display: flex; align-items: center; gap: 8px; margin-bottom: 10px; }
.h-bar-rank { width: 22px; height: 22px; border-radius: 6px; display: flex; align-items: center; justify-content: center; font-size: 12px; font-weight: 700; color: #fff; background: #C0C4CC; flex-shrink: 0; }
.h-bar-rank.rank-1 { background: #F56C6C; } .h-bar-rank.rank-2 { background: #E6A23C; } .h-bar-rank.rank-3 { background: #409EFF; }
.h-bar-label { width: 120px; text-align: right; font-size: 13px; color: #606266; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; flex-shrink: 0; }
.h-bar-track { flex: 1; height: 22px; background: #F2F3F5; border-radius: 4px; overflow: hidden; }
.h-bar-fill { height: 100%; border-radius: 4px; transition: width 0.6s ease; min-width: 2px; }
.h-bar-value { width: 36px; font-size: 13px; font-weight: 600; color: #303133; text-align: left; flex-shrink: 0; }

/* ===== 双栏布局 ===== */
.dual-chart { display: grid; grid-template-columns: 1fr 1fr; gap: 24px; }
.dc-left, .dc-right { min-width: 0; }

/* ===== 环形图组件 ===== */
.donut-chart-wrap { display: flex; align-items: flex-start; gap: 20px; flex-wrap: wrap; }
.donut-svg { width: 220px; height: 220px; flex-shrink: 0; }
.donut-legend { display: flex; flex-wrap: wrap; gap: 6px; align-content: flex-start; }
.donut-legend-item { display: flex; align-items: center; gap: 6px; font-size: 12px; width: 100%; }
.donut-legend-dot { width: 10px; height: 10px; border-radius: 3px; flex-shrink: 0; }
.donut-legend-label { color: #606266; flex: 1; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.donut-legend-val { color: #909399; flex-shrink: 0; }

/* ===== 成果总计 ===== */
.outcome-total { display: flex; align-items: baseline; gap: 8px; margin-bottom: 16px; padding: 14px 18px; background: linear-gradient(135deg, #F0F9FF, #ECF5FF); border-radius: 10px; }
.ot-badge { font-size: 13px; color: #409EFF; font-weight: 500; }
.ot-num { font-size: 36px; font-weight: 700; color: #303133; }
.ot-unit { font-size: 14px; color: #909399; }

/* ===== 响应式 ===== */
@media (max-width: 768px) {
  .kpi-row { grid-template-columns: repeat(2, 1fr); }
  .overview-grid, .dual-chart { grid-template-columns: 1fr; }
  .borrow-summary { grid-template-columns: repeat(2, 1fr); }
}
</style>
