import request from './request'

export const borrowApi = {
  create(data) { return request.post('/borrows', data) },
  getMyBorrows(params) { return request.get('/borrows/my', { params }) },
  getById(id) { return request.get(`/borrows/${id}`) },
  cancel(id) { return request.post(`/borrows/${id}/cancel`) },
  getPendingFirst(params) { return request.get('/borrows/pending/first', { params }) },
  getPendingSecond(params) { return request.get('/borrows/pending/second', { params }) },
  approve(data) { return request.post('/borrows/approve', data) },
  returnDevice(id, data) {
    const fd = new FormData()
    if (data.damageReport) fd.append('damageReport', data.damageReport)
    if (data.file) fd.append('file', data.file)
    return request.post(`/borrows/${id}/return`, fd, { headers: { 'Content-Type': 'multipart/form-data' } })
  },
  getOverdue(params) { return request.get('/borrows/overdue', { params }) }
}
