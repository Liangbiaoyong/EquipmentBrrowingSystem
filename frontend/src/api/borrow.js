import request from './request'

export const borrowApi = {
  /** 提交借用申请 */
  create(data) {
    return request.post('/borrows', data)
  },

  /** 我的申请列表 */
  getMyBorrows(params) {
    return request.get('/borrows/my', { params })
  },

  /** 待审批列表 */
  getPendingApprovals(params) {
    return request.get('/borrows/pending', { params })
  },

  /** 审批操作 */
  approve(id, data) {
    return request.post(`/borrows/${id}/approve`, data)
  },

  /** 归还操作 */
  returnDevice(id, data) {
    return request.post(`/borrows/${id}/return`, data, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  },

  /** 借用详情 */
  getById(id) {
    return request.get(`/borrows/${id}`)
  }
}
