import request from './request'

export const statsApi = {
  overview() { return request.get('/statistics/overview') },
  trend() { return request.get('/statistics/trend') },
  topDevices() { return request.get('/statistics/top-devices') },
  topUsers() { return request.get('/statistics/top-users') },
  utilization() { return request.get('/statistics/utilization') },
  exportCsv() { return request.get('/statistics/export', { responseType: 'blob' }) },
  purposes(startDate, endDate, categoryId) {
    return request.get('/statistics/purposes', { params: { startDate, endDate, categoryId } })
  },
  purposeDetail(startDate, endDate, categoryId) {
    return request.get('/statistics/purposes/detail', { params: { startDate, endDate, categoryId } })
  },
  outcomeStats(deviceId, startDate, endDate) {
    return request.get('/statistics/outcomes/stats', { params: { deviceId, startDate, endDate } })
  },
  deviceOutcomes(deviceId) {
    return request.get('/statistics/device-outcomes', { params: { deviceId } })
  }
}
