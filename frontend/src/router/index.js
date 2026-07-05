import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { title: '登录', noAuth: true }
  },
  {
    path: '/',
    component: () => import('@/views/Layout.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/Dashboard.vue'),
        meta: { title: '仪表盘' }
      },
      {
        path: 'devices',
        name: 'DeviceList',
        component: () => import('@/views/device/DeviceList.vue'),
        meta: { title: '设备列表' }
      },
      {
        path: 'borrows',
        name: 'MyBorrows',
        component: () => import('@/views/borrow/MyBorrows.vue'),
        meta: { title: '我的借用' }
      },
      {
        path: 'approvals',
        name: 'PendingApprovals',
        component: () => import('@/views/approval/PendingApprovals.vue'),
        meta: { title: '待审批' }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫：检查登录状态
router.beforeEach((to, from, next) => {
  document.title = to.meta.title ? `${to.meta.title} - 设备借用系统` : '设备借用系统'
  const token = localStorage.getItem('token')
  if (!to.meta.noAuth && !token) {
    next({ name: 'Login' })
  } else {
    next()
  }
})

export default router
