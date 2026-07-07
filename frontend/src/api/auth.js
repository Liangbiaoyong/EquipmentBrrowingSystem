import request from './request'

export const authApi = {
  casLogin(token, cookies) {
    return request.post('/auth/cas/login', { token, cookies })
  },
  login(data) {
    return request.post('/auth/local/login', data)
  },
  getUserInfo() {
    return request.get('/auth/info')
  },
  logout() {
    return request.post('/auth/logout')
  }
}
