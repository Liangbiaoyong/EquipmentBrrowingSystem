import request from './request'

export const statsApi = {
  overview(scope = 'auto') { return request.get('/statistics/overview', { params: { scope } }) },
  trend(scope = 'auto') { return request.get('/statistics/trend', { params: { scope } }) },
  topDevices(scope = 'auto') { return request.get('/statistics/top-devices', { params: { scope } }) },
  topUsers(scope = 'auto') { return request.get('/statistics/top-users', { params: { scope } }) },
  utilization(scope = 'auto') { return request.get('/statistics/utilization', { params: { scope } }) },
  exportCsv(format = 'csv') { return request.get('/statistics/export', { params: { format }, responseType: 'blob' }) },
  purposes(startDate, endDate, categoryId, scope = 'auto') {
    return request.get('/statistics/purposes', { params: { startDate, endDate, categoryId, scope } })
  },
  purposeDetail(startDate, endDate, categoryId, scope = 'auto') {
    return request.get('/statistics/purposes/detail', { params: { startDate, endDate, categoryId, scope } })
  },
  outcomeStats(deviceId, startDate, endDate, scope = 'auto') {
    return request.get('/statistics/outcomes/stats', { params: { deviceId, startDate, endDate, scope } })
  },
  deviceOutcomes(deviceId) {
    return request.get('/statistics/device-outcomes', { params: { deviceId } })
  }
}
