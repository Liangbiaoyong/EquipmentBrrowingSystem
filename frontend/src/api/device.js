import request from './request'

export const deviceApi = {
  /** 设备列表（分页+筛选） */
  list(params) {
    return request.get('/devices', { params })
  },

  /** 设备详情 */
  getById(id) {
    return request.get(`/devices/${id}`)
  },

  /** 新增设备 */
  create(data) {
    return request.post('/devices', data)
  },

  /** 更新设备 */
  update(id, data) {
    return request.put(`/devices/${id}`, data)
  },

  /** 上传设备图片 */
  uploadImages(id, files) {
    const formData = new FormData()
    files.forEach(f => formData.append('files', f))
    return request.post(`/devices/${id}/images`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  },

  /** 删除设备图片 */
  deleteImage(id, imageId) {
    return request.delete(`/devices/${id}/images/${imageId}`)
  }
}
