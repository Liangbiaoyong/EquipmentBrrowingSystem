import request from './request'

export const categoryApi = {
  list() { return request.get('/categories') },
  topLevel() { return request.get('/categories/top-level') },
  classify(gbName) { return request.get('/categories/classify', { params: { gbName } }) },
  getMappings(params) { return request.get('/categories/mappings', { params }) },
  addMapping(data) { return request.post('/categories/mappings', data) },
  updateMapping(id, data) { return request.put(`/categories/mappings/${id}`, data) },
  deleteMapping(id) { return request.delete(`/categories/mappings/${id}`) },
  toggleMapping(id) { return request.put(`/categories/mappings/${id}/toggle`) }
}
