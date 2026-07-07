import { defineStore } from 'pinia'
import { authApi } from '@/api/auth'
import { ref, computed } from 'vue'

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('token') || '')
  const userInfo = ref(null)
  const permissions = ref([])

  const isLoggedIn = computed(() => !!token.value)

  function hasPermission(perm) {
    return permissions.value.includes(perm)
  }

  async function login(credentials) {
    const res = await authApi.login(credentials)
    token.value = res.data.accessToken
    localStorage.setItem('token', token.value)
    userInfo.value = res.data.userInfo
    permissions.value = res.data.userInfo.permissions || []
  }

  async function fetchUserInfo() {
    if (!token.value) return
    try {
      const res = await authApi.getUserInfo()
      userInfo.value = res.data
      permissions.value = res.data.permissions || []
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
  }

  return { token, userInfo, permissions, isLoggedIn, hasPermission, login, fetchUserInfo, logout }
})
