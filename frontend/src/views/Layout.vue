<template>
  <div class="app-shell">
    <!-- 侧边栏 -->
    <aside class="sidebar" :class="{ collapsed: isCollapse, mobile: isMobile }">
      <div class="brand">
        <div class="brand-mark">仪</div>
        <transition name="fade">
          <span v-if="!isCollapse" class="brand-text">仪器共享平台</span>
        </transition>
      </div>

      <el-scrollbar class="menu-scroll">
        <el-menu
          class="side-menu"
          :default-active="route.path"
          :collapse="isCollapse"
          :collapse-transition="false"
          router
          background-color="transparent"
          text-color="#475569"
          active-text-color="#2563eb"
        >
          <template v-for="item in menuItems" :key="item.index">
            <el-sub-menu v-if="item.children" :index="item.index">
              <template #title>
                <el-icon><component :is="item.icon" /></el-icon>
                <span>{{ item.title }}</span>
              </template>
              <el-menu-item v-for="sub in item.children" :key="sub.index" :index="sub.index">
                {{ sub.title }}
              </el-menu-item>
            </el-sub-menu>
            <el-menu-item v-else :index="item.index">
              <el-icon><component :is="item.icon" /></el-icon>
              <span>{{ item.title }}</span>
            </el-menu-item>
          </template>
        </el-menu>
      </el-scrollbar>
    </aside>

    <!-- 主工作区 -->
    <div class="workspace">
      <header class="topbar">
        <div class="topbar-left">
          <button class="collapse-btn" @click="isCollapse = !isCollapse">
            <el-icon :size="20"><Fold v-if="!isCollapse" /><Expand v-else /></el-icon>
          </button>
          <h1 class="page-title">{{ route.meta.title || '仪器共享平台' }}</h1>
        </div>

        <div class="topbar-right">
          <el-badge :value="unread" :hidden="!unread" class="notify-badge" @click="$router.push('/notifications')">
            <el-icon :size="20"><Bell /></el-icon>
          </el-badge>
          <el-dropdown trigger="click">
            <span class="user-chip">
              <span class="user-avatar">{{ (userStore.userInfo?.realName || '用').slice(0, 1) }}</span>
              <span class="user-name">{{ userStore.userInfo?.realName || '用户' }}</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="$router.push('/profile')">个人中心</el-dropdown-item>
                <el-dropdown-item divided @click="handleLogout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <main class="content">
        <router-view v-slot="{ Component }">
          <transition name="page" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </main>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { notifyApi } from '@/api/notification'
import {
  DataAnalysis, Monitor, Reading, EditPen, TrendCharts, Setting, User,
  Bell, Fold, Expand, ArrowDown
} from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const isCollapse = ref(false)
const unread = ref(0)
const isMobile = ref(window.innerWidth < 768)
let ws = null

const allMenus = [
  { index: '/dashboard', title: '仪表盘', icon: DataAnalysis, perm: 'dashboard:view' },
  { index: '/devices', title: '设备浏览', icon: Monitor, perm: 'device:view' },
  {
    index: 'device-manage', title: '设备管理', icon: Setting, perm: 'device:manage',
    children: [
      { index: '/devices/manage/list', title: '设备列表' },
      { index: '/devices/manage/import', title: '批量导入', perm: 'device:import' },
      { index: '/devices/manage/missing-images', title: '缺少图片' },
      { index: '/devices/manage/categories', title: '分类管理', perm: 'category:manage' },
      { index: '/devices/manage/laboratories', title: '实验室管理', perm: 'laboratory:manage' },
      { index: '/repairs', title: '维修管理', perm: 'repair:manage' },
      { index: '/scrap', title: '报废管理', perm: 'repair:manage' }
    ]
  },
  { index: '/borrows/create', title: '借用申请', icon: Reading, perm: 'borrow:create' },
  { index: '/borrows/my', title: '我的借用', icon: Reading, perm: 'borrow:my' },
  {
    index: 'approval', title: '借用管理', icon: EditPen, perm: 'approval:first',
    children: [
      { index: '/borrows/browse', title: '借用浏览', perm: 'borrow:view' },
      { index: '/approvals/first', title: '初审', perm: 'approval:first' },
      { index: '/approvals/second', title: '终审', perm: 'approval:second' },
      { index: '/returns/overdue', title: '逾期管理', perm: 'return:manage' },
      { index: '/returns/approvals', title: '归还审批', perm: 'return:manage' }
    ]
  },
  { index: '/statistics', title: '数据统计', icon: TrendCharts, perm: 'statistics:view' },
  { index: '/notifications', title: '消息中心', icon: Bell, perm: 'notification:view' },
  {
    index: 'admin', title: '系统管理', icon: Setting, perm: 'admin:user',
    children: [
      { index: '/admin/users', title: '用户管理' },
      { index: '/admin/settings', title: '系统设置' },
      { index: '/admin/logs', title: '操作日志' },
      { index: '/admin/data-tables', title: '数据表管理' },
      { index: '/admin/backup', title: '数据备份', perm: 'admin:backup' },
      { index: '/admin/test-data', title: '测试数据', perm: 'admin:user' }
    ]
  }
]

const menuItems = computed(() =>
  allMenus
    .filter(m => !m.perm || userStore.hasPermission(m.perm))
    .map(m => m.children ? { ...m, children: m.children.filter(c => !c.perm || userStore.hasPermission(c.perm)) } : m)
    .filter(m => !m.children || m.children.length > 0)
)

function connectWs() {
  if (!userStore.userInfo) return
  const proto = location.protocol === 'https:' ? 'wss' : 'ws'
  ws = new WebSocket(`${proto}://${location.host}/api/v1/ws/notification/${userStore.userInfo.id}`)
  ws.onmessage = () => { unread.value++; setTimeout(fetchUnread, 1000) }
  ws.onclose = () => setTimeout(connectWs, 10000)
}

async function fetchUnread() {
  try { const r = await notifyApi.unreadCount(); unread.value = r.data.unreadCount } catch {}
}

function handleLogout() {
  if (ws) ws.close()
  userStore.logout()
  router.push('/login')
}

function onResize() {
  isMobile.value = window.innerWidth < 768
  if (isMobile.value) isCollapse.value = true
}

onMounted(async () => {
  if (!userStore.userInfo) try { await userStore.fetchUserInfo() } catch (e) { console.error(e) }
  fetchUnread()
  connectWs()
  window.addEventListener('resize', onResize)
  onResize()
})

onUnmounted(() => {
  if (ws) ws.close()
  window.removeEventListener('resize', onResize)
})
</script>

<style scoped>
.app-shell {
  display: flex;
  height: 100vh;
  background: transparent;
}

/* ===== 侧边栏 ===== */
.sidebar {
  width: 240px;
  background: rgba(255, 255, 255, 0.86);
  backdrop-filter: blur(14px);
  border-right: 1px solid var(--app-border);
  display: flex;
  flex-direction: column;
  transition: width 0.25s ease, background 0.25s ease;
  overflow: hidden;
  flex-shrink: 0;
}

.sidebar.collapsed {
  width: 72px;
}

.sidebar.mobile {
  position: fixed;
  z-index: 100;
  height: 100vh;
  box-shadow: var(--app-shadow-hover);
}

.brand {
  height: 72px;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 20px;
  font-weight: 700;
  color: var(--app-text);
}

.brand-mark {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  background: linear-gradient(135deg, #2563eb, #3b82f6);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  font-weight: 800;
  box-shadow: 0 8px 20px rgba(37, 99, 235, 0.28);
  flex-shrink: 0;
}

.brand-text {
  font-size: 16px;
  white-space: nowrap;
}

.menu-scroll {
  flex: 1;
}

.side-menu {
  background: transparent !important;
  padding: 8px;
}

.side-menu :deep(.el-menu-item),
.side-menu :deep(.el-sub-menu__title) {
  border-radius: 12px;
  margin-bottom: 4px;
  height: 44px;
  line-height: 44px;
  font-weight: 500;
}

.side-menu :deep(.el-menu-item:hover),
.side-menu :deep(.el-sub-menu__title:hover) {
  background: #f1f5f9 !important;
}

.side-menu :deep(.el-menu-item.is-active) {
  background: var(--app-primary-soft) !important;
  color: var(--app-primary) !important;
  font-weight: 600;
  box-shadow: inset 0 0 0 1px rgba(37, 99, 235, 0.12);
}

.side-menu :deep(.el-sub-menu .el-menu-item) {
  padding-left: 46px !important;
}

.side-menu :deep(.el-menu--popup .el-menu-item) {
  padding-left: 20px !important;
}

/* ===== 主工作区 ===== */
.workspace {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.topbar {
  height: 72px;
  background: rgba(255, 255, 255, 0.8);
  backdrop-filter: blur(14px);
  border-bottom: 1px solid var(--app-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  gap: 16px;
  flex-shrink: 0;
}

.topbar-left {
  display: flex;
  align-items: center;
  gap: 16px;
  min-width: 0;
}

.collapse-btn {
  width: 40px;
  height: 40px;
  border: none;
  border-radius: 12px;
  background: #f1f5f9;
  color: #475569;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: background 0.2s ease, transform 0.2s ease;
}

.collapse-btn:hover {
  background: #e2e8f0;
  transform: translateY(-1px);
}

.page-title {
  font-size: 18px;
  font-weight: 700;
  color: var(--app-text);
  margin: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.topbar-right {
  display: flex;
  align-items: center;
  gap: 20px;
}

.notify-badge {
  cursor: pointer;
  color: #475569;
  transition: transform 0.2s ease;
}

.notify-badge:hover {
  transform: translateY(-1px);
  color: var(--app-primary);
}

.user-chip {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 10px 6px 6px;
  border-radius: 999px;
  background: #f8fafc;
  cursor: pointer;
  transition: background 0.2s ease, box-shadow 0.2s ease;
}

.user-chip:hover {
  background: #f1f5f9;
  box-shadow: 0 4px 12px rgba(31, 45, 61, 0.06);
}

.user-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: linear-gradient(135deg, #2563eb, #3b82f6);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
}

.user-name {
  font-weight: 500;
  color: var(--app-text-secondary);
}

/* ===== 内容区 ===== */
.content {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
  min-height: calc(100vh - 72px);
}

.page-enter-active,
.page-leave-active {
  transition: opacity 0.18s ease, transform 0.18s ease;
}

.page-enter-from {
  opacity: 0;
  transform: translateY(6px);
}

.page-leave-to {
  opacity: 0;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

@media (max-width: 768px) {
  .sidebar {
    width: 72px;
  }
  .sidebar:not(.mobile) {
    display: none;
  }
  .topbar {
    padding: 0 14px;
    height: 60px;
  }
  .content {
    padding: 14px;
  }
}
</style>
