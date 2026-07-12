import request from './request'

export const adminApi = {
  // 用户管理
  getUsers(params) { return request.get('/admin/users', { params }) },
  createUser(params) { return request.post('/admin/users', null, { params }) },
  batchCreate(users) { return request.post('/admin/users/batch', users) },
  batchDelete(ids) { return request.delete('/admin/users/batch', { data: ids }) },
  importUsers(file) {
    const fd = new FormData(); fd.append('file', file)
    return request.post('/admin/users/import', fd, { headers: { 'Content-Type': 'multipart/form-data' } })
  },
  downloadTemplate(format = 'xlsx') { return request.get('/admin/users/template', { params: { format }, responseType: 'blob' }) },
  updateRole(id, userType) { return request.put(`/admin/users/${id}/role`, null, { params: { userType } }) },
  toggleStatus(id) { return request.put(`/admin/users/${id}/status`) },
  resetPassword(id, newPassword) { return request.put(`/admin/users/${id}/password`, null, { params: { newPassword } }) },
  deleteUser(id) { return request.delete(`/admin/users/${id}`) },
  // 角色/部门列表（下拉用）
  getRoles() {
    return Promise.resolve([
      { label: '学生', value: 0 }, { label: '教师', value: 1 },
      { label: '实验室管理员', value: 2 }, { label: '系统管理员', value: 3 }
    ])
  },
  // 配置管理
  getConfigs() { return request.get('/admin/config') },
  getConfig(key) { return request.get(`/admin/config/${key}`) },
  setConfig(key, value, description) { return request.put(`/admin/config/${key}`, null, { params: { value, description } }) },
  deleteConfig(key) { return request.delete(`/admin/config/${key}`) },
  // 日志
  getLogs(params) { return request.get('/admin/logs', { params }) }
}
