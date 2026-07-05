import { defineStore } from 'pinia'
import { authApi } from '@/api/auth'
import { ref } from 'vue'

export const useUserStore = defineStore('user', () => {
  const token = ref('')
  const userInfo = ref(null)
  const permissions = ref([])

  async function login(credentials) {
    const res = await authApi.login(credentials)
    token.value = res.data.token
    localStorage.setItem('token', token.value)
    await getUserInfo()
  }

  async function getUserInfo() {
    const res = await authApi.getUserInfo()
    userInfo.value = res.data
  }

  function logout() {
    token.value = ''
    userInfo.value = null
    permissions.value = []
    localStorage.removeItem('token')
  }

  return { token, userInfo, permissions, login, getUserInfo, logout }
})
