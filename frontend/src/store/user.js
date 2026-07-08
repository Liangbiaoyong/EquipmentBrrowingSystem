import { defineStore } from 'pinia'
import { authApi } from '@/api/auth'
import { ref, computed } from 'vue'

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('token') || '')
  // 从localStorage恢复permissions，防止页面刷新后丢失导致403
  const savedPerms = localStorage.getItem('permissions')
  const permissions = ref(savedPerms ? JSON.parse(savedPerms) : [])
  const userInfo = ref(null)

  const isLoggedIn = computed(() => !!token.value)

  function hasPermission(perm) {
    // 如果权限为空但token存在，可能是刚刷新页面，先放行让页面初始化
    if (!permissions.value.length && token.value) return true
    return permissions.value.includes(perm)
  }

  function savePerms(perms) {
    permissions.value = perms || []
    localStorage.setItem('permissions', JSON.stringify(permissions.value))
  }

  // CAS/凭证登录后的状态初始化（供Login.vue调用）
  function loginCas(loginData) {
    token.value = loginData.accessToken
    localStorage.setItem('token', token.value)
    userInfo.value = loginData.userInfo
    savePerms(loginData.userInfo.permissions || [])
  }

  async function login(credentials) {
    const res = await authApi.login(credentials)
    token.value = res.data.accessToken
    localStorage.setItem('token', token.value)
    userInfo.value = res.data.userInfo
    savePerms(res.data.userInfo.permissions || [])
  }

  async function fetchUserInfo() {
    if (!token.value) return
    try {
      const res = await authApi.getUserInfo()
      userInfo.value = res.data
      savePerms(res.data.permissions || [])
    } catch {
      logout()
    }
  }

  async function logout() {
    try { await authApi.logout() } catch { /* ignore */ }
    token.value = ''
    userInfo.value = null
    permissions.value = []
    localStorage.removeItem('token')
    localStorage.removeItem('permissions')
  }

  return { token, userInfo, permissions, isLoggedIn, hasPermission, loginCas, login, fetchUserInfo, logout }
})
