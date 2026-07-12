import request from './request'

export const descriptionApi = {
  /** 按类型获取分类描述列表 (PURPOSE/OUTCOME) */
  listByType(type) { return request.get('/category-descriptions', { params: { type } }) },

  /** 获取所有分类描述（按类型分组） */
  listGrouped() { return request.get('/category-descriptions/grouped') },

  /** 按类型+名称查找单个描述 */
  lookup(type, name) { return request.get('/category-descriptions/lookup', { params: { type, name } }) }
}
