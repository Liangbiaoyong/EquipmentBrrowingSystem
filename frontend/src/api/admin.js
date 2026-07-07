import request from './request'

export const adminApi = {
  // 用户管理
  getUsers(params) { return request.get('/admin/users', { params }) },
  createUser(params) { return request.post('/admin/users', null, { params }) },
  updateRole(id, userType) { return request.put(`/admin/users/${id}/role`, null, { params: { userType } }) },
  toggleStatus(id) { return request.put(`/admin/users/${id}/status`) },
  // 配置管理
  getConfigs() { return request.get('/admin/config') },
  getConfig(key) { return request.get(`/admin/config/${key}`) },
  setConfig(key, value, description) { return request.put(`/admin/config/${key}`, null, { params: { value, description } }) },
  deleteConfig(key) { return request.delete(`/admin/config/${key}`) },
  // 日志
  getLogs(params) { return request.get('/admin/logs', { params }) }
}
