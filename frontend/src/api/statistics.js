import request from './request'

export const statsApi = {
  overview() { return request.get('/statistics/overview') },
  trend() { return request.get('/statistics/trend') },
  topDevices() { return request.get('/statistics/top-devices') },
  topUsers() { return request.get('/statistics/top-users') },
  utilization() { return request.get('/statistics/utilization') },
  exportCsv() { return request.get('/statistics/export', { responseType: 'blob' }) }
}
