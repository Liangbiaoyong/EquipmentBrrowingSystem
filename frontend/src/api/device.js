import request from './request'

export const deviceApi = {
  list(params) { return request.get('/devices', { params }) },
  getById(id) { return request.get(`/devices/${id}`) },
  update(id, data) { return request.put(`/devices/${id}`, data) },
  delete(id) { return request.delete(`/devices/${id}`) },
  importFile(file) { const fd = new FormData(); fd.append('file', file); return request.post('/devices/import', fd, { headers: { 'Content-Type': 'multipart/form-data' } }) },
  dryRun(file) { const fd = new FormData(); fd.append('file', file); return request.post('/devices/import/dry-run', fd, { headers: { 'Content-Type': 'multipart/form-data' } }) },
  exportCsv(params) { return request.get('/devices/export/csv', { params, responseType: 'blob' }) },
  getImages(deviceId) { return request.get(`/devices/${deviceId}/images`) },
  uploadImage(deviceId, file, sort = 0) { const fd = new FormData(); fd.append('file', file); fd.append('sort', sort); return request.post(`/devices/${deviceId}/images/upload`, fd, { headers: { 'Content-Type': 'multipart/form-data' } }) },
  deleteImage(imageId) { return request.delete(`/devices/images/${imageId}`) },
  missingImages(params) { return request.get('/devices/missing-images', { params }) },
  getBatches() { return request.get('/devices/batches') },
  deleteBatch(batchId) { return request.delete(`/devices/batches/${batchId}`) }
}
