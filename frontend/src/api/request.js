import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'

const request = axios.create({
  baseURL: '/api/v1',
  timeout: 15000
})

// 请求拦截器：注入 JWT
request.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

// 响应拦截器：统一处理错误
request.interceptors.response.use(
  (response) => {
    // 二进制响应（blob/arraybuffer）直接透传，不解析JSON
    if (response.config.responseType === 'blob' || response.config.responseType === 'arraybuffer') {
      return response
    }
    const res = response.data
    if (res.code !== 200) {
      // 保留 response.data.msg 结构，兼容业务层 e?.response?.data?.msg 取值
      const err = new Error(res.msg || '请求失败')
      err.response = { data: { msg: res.msg }, status: res.code }
      return Promise.reject(err)
    }
    return res
  },
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('token')
      localStorage.removeItem('permissions')
      router.push('/login')
    }
    // 非401错误静默，由页面自行处理
    return Promise.reject(error)
  }
)

export default request
