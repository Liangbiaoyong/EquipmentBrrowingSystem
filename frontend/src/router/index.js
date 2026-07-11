import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/store/user'

const routes = [
  { path: '/login', name: 'Login', component: () => import('@/views/Login.vue'), meta: { title: '登录', noAuth: true } },
  { path: '/403', name: 'Forbidden', component: () => import('@/views/403.vue'), meta: { title: '无权限', noAuth: true } },
  {
    path: '/', component: () => import('@/views/Layout.vue'), redirect: '/dashboard',
    children: [
      { path: 'dashboard', name: 'Dashboard', component: () => import('@/views/Dashboard.vue'), meta: { title: '仪表盘', perm: 'dashboard:view' } },
      { path: 'profile', name: 'Profile', component: () => import('@/views/Profile.vue'), meta: { title: '个人中心', perm: 'profile:view' } },
      // 设备
      { path: 'devices', name: 'DeviceList', component: () => import('@/views/device/DeviceList.vue'), meta: { title: '设备列表', perm: 'device:view' } },
      // 设备管理子路由（必须放在 devices/:id 之前，避免被 :id 吞掉）
      { path: 'devices/manage/list', name: 'DeviceManage', component: () => import('@/views/device/DeviceManage.vue'), meta: { title: '设备管理', perm: 'device:manage' } },
      { path: 'devices/manage/import', name: 'DeviceImport', component: () => import('@/views/device/DeviceImport.vue'), meta: { title: '批量导入', perm: 'device:manage' } },
      { path: 'devices/manage/missing-images', name: 'MissingImages', component: () => import('@/views/device/MissingImages.vue'), meta: { title: '缺少图片', perm: 'device:manage' } },
      { path: 'devices/:id', name: 'DeviceDetail', component: () => import('@/views/device/DeviceDetail.vue'), meta: { title: '设备详情', perm: 'device:view' } },
      // 借用
      { path: 'borrows/create', name: 'BorrowCreate', component: () => import('@/views/borrow/BorrowCreate.vue'), meta: { title: '借用申请', perm: 'borrow:create' } },
      { path: 'borrows/my', name: 'MyBorrows', component: () => import('@/views/borrow/MyBorrows.vue'), meta: { title: '我的借用', perm: 'borrow:my' } },
      { path: 'borrows/:id', name: 'BorrowDetail', component: () => import('@/views/borrow/BorrowDetail.vue'), meta: { title: '借用详情', perm: 'borrow:view' } },
      { path: 'borrows/:id/return', name: 'BorrowReturn', component: () => import('@/views/borrow/BorrowReturn.vue'), meta: { title: '归还登记', perm: 'borrow:return' } },
      // 审批
      { path: 'approvals/first', name: 'ApprovalFirst', component: () => import('@/views/approval/ApprovalFirst.vue'), meta: { title: '一级审批', perm: 'approval:first' } },
      { path: 'approvals/second', name: 'ApprovalSecond', component: () => import('@/views/approval/ApprovalSecond.vue'), meta: { title: '二级审批', perm: 'approval:second' } },
      // 归还管理
      { path: 'returns/overdue', name: 'OverdueList', component: () => import('@/views/borrow/OverdueList.vue'), meta: { title: '逾期管理', perm: 'return:manage' } },
      { path: 'returns/verify', name: 'ReturnVerify', component: () => import('@/views/borrow/ReturnVerify.vue'), meta: { title: '归还核验', perm: 'return:manage' } },
      // 统计
      { path: 'statistics', name: 'Statistics', component: () => import('@/views/Statistics.vue'), meta: { title: '数据统计', perm: 'statistics:view' } },
      // 通知
      { path: 'notifications', name: 'Notifications', component: () => import('@/views/Notifications.vue'), meta: { title: '消息中心', perm: 'notification:view' } },
      // 系统管理
      { path: 'admin/users', name: 'AdminUsers', component: () => import('@/views/admin/AdminUsers.vue'), meta: { title: '用户管理', perm: 'admin:user' } },
      { path: 'admin/settings', name: 'AdminSettings', component: () => import('@/views/admin/AdminSettings.vue'), meta: { title: '系统设置', perm: 'admin:config' } },
      { path: 'admin/logs', name: 'AdminLogs', component: () => import('@/views/admin/AdminLogs.vue'), meta: { title: '操作日志', perm: 'admin:log' } },
      { path: 'repairs', name: 'RepairManage', component: () => import('@/views/repair/RepairManage.vue'), meta: { title: '维修管理', perm: 'repair:manage' } }
    ]
  }
]

const router = createRouter({ history: createWebHistory(), routes })

router.beforeEach((to, from, next) => {
  document.title = to.meta.title ? `${to.meta.title} - 设备借用系统` : '设备借用系统'
  const token = localStorage.getItem('token')
  if (!to.meta.noAuth && !token) return next({ name: 'Login' })
  if (to.meta.perm && token) {
    const store = useUserStore()
    if (!store.hasPermission(to.meta.perm)) return next({ name: 'Forbidden' })
  }
  next()
})

export default router
