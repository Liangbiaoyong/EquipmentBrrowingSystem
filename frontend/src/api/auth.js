import request from './request'

export const authApi = {
  /** CAS 登录回调 */
  casLogin(ticket) {
    return request.post('/auth/cas/login', { ticket })
  },

  /** 本地账号登录 */
  login(data) {
    return request.post('/auth/local/login', data)
  },

  /** 获取当前用户信息 */
  getUserInfo() {
    return request.get('/auth/info')
  }
}
